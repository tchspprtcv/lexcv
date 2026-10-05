package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.RegimeIva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 135 (NCRD-02, NCRD-03; PITFALLS P-13): {@link NotaCreditoService#emitir} sob concorrência
 * real em PostgreSQL.
 * <ul>
 *   <li>Teto cumulativo: 8 NC parciais de 30 000 em simultâneo sobre uma FR de 120 000 -- só 4
 *       passam, as outras são 409 NC_EXCEDE_ORIGINAL; a soma nunca excede a FR, a numeração da NC
 *       não tem lacunas e o saldo bate certo com as NC confirmadas.</li>
 *   <li>Mesma chave em 4 pedidos simultâneos: uma só NC, um só estorno, o mesmo id para todos.</li>
 *   <li>NC e FR do mesmo cliente intercaladas, 10 vezes: tudo passa, as duas séries ficam sem
 *       lacunas e nenhuma falha é um deadlock (40P01).</li>
 * </ul>
 *
 * <p>Andaime de {@link PagamentoFaturadoConcorrenciaIT}: latch de partida, pool fixo de 8 threads,
 * testes {@code NOT_SUPPORTED} (commits reais). Cada resultado é classificado: sucesso, recusa
 * fiscal pelo código, ou falha inesperada (que faz falhar o teste).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({NotaCreditoService.class, PagamentoFaturadoService.class, NumeracaoService.class,
        ParametroFiscalService.class, AuditoriaFiscalService.class, NotaCreditoConcorrenciaIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotaCreditoConcorrenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T13:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final int THREADS = 8;
    private static final String SQLSTATE_DEADLOCK = "40P01";
    private static final BigDecimal VALOR_FR = new BigDecimal("120000.00");

    @Autowired
    private NotaCreditoService service;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private JdbcTemplate jdbc;

    private FixturaEmissaoFiscal fixtura;
    private ExecutorService executor;

    /** Todas as falhas observadas em todas as corridas (para a verificação de deadlock). */
    private final List<Throwable> falhas = new CopyOnWriteArrayList<>();

    private record Cenario(UUID tenantId, UUID clienteId, UUID processoId, Integer honorarioId, UUID frId) {
    }

    /** Resultado de uma tarefa: o valor devolvido ou a recusa fiscal (nunca as duas). */
    private record Desfecho<T>(T valor, RecusaFiscalException recusa) {
        boolean sucesso() {
            return recusa == null;
        }
    }

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
        executor = Executors.newFixedThreadPool(THREADS);
        falhas.clear();
    }

    @AfterEach
    void terminar() {
        executor.shutdownNow();
        semDeadlock();
    }

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private ResultadoPagamentoFaturado emitirFr(UUID tenant, Integer honorario, BigDecimal valor) {
        return pagamentoFaturado.registar(tenant, autor(tenant),
                new PagamentoRequest(honorario, valor, null, "DINHEIRO", null, UUID.randomUUID()));
    }

    /** Tenant com um cliente, um processo, um honorário de 120 000 e uma FR de 120 000 emitida. */
    private Cenario cenarioComFr() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-1");
        Integer honorario = fixtura.criarHonorario(processo, VALOR_FR, "Honorário");
        ResultadoPagamentoFaturado fr = emitirFr(tenant, honorario, VALOR_FR);
        return new Cenario(tenant, cliente, processo, honorario, fr.resposta().documentoFiscal().id());
    }

    private static NotaCreditoRequest parcial(String valor, UUID chave) {
        return new NotaCreditoRequest("PARCIAL", new BigDecimal(valor), "CORRECAO_VALOR", "Acerto de valor", chave);
    }

    private Callable<ResultadoNotaCredito> nc(Cenario c, NotaCreditoRequest req) {
        return () -> service.emitir(c.tenantId(), autor(c.tenantId()), c.frId(), req);
    }

    /**
     * Liberta todas as tarefas ao mesmo tempo (latch de partida) e devolve os desfechos por ordem
     * de submissão. Uma {@link RecusaFiscalException} com um código de {@code recusasAceites} é um
     * desfecho; qualquer outra falha é registada (verificação de deadlock) e faz falhar o teste.
     */
    private <T> List<Desfecho<T>> emParalelo(List<Callable<T>> tarefas, Set<String> recusasAceites)
            throws Exception {
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (Callable<T> tarefa : tarefas) {
            futuros.add(executor.submit(() -> {
                partida.await();
                return tarefa.call();
            }));
        }
        partida.countDown();
        List<Desfecho<T>> desfechos = new ArrayList<>();
        for (Future<T> f : futuros) {
            try {
                desfechos.add(new Desfecho<>(f.get(60, TimeUnit.SECONDS), null));
            } catch (ExecutionException e) {
                Throwable causa = e.getCause();
                falhas.add(causa);
                if (causa instanceof RecusaFiscalException r && recusasAceites.contains(r.getCodigo())) {
                    desfechos.add(new Desfecho<>(null, r));
                } else {
                    fail("emissão concorrente falhou de forma inesperada: " + causa, causa);
                }
            }
        }
        return desfechos;
    }

    /** Nenhuma falha, em nenhuma corrida, foi um deadlock detetado pelo PostgreSQL (SQLState 40P01). */
    private void semDeadlock() {
        for (Throwable t : falhas) {
            for (Throwable c = t; c != null; c = c.getCause()) {
                if (c instanceof SQLException sql && SQLSTATE_DEADLOCK.equals(sql.getSQLState())) {
                    fail("deadlock detetado: " + t);
                }
                if (c.getMessage() != null && c.getMessage().contains("deadlock")) {
                    fail("deadlock detetado: " + t);
                }
            }
        }
    }

    private static List<Long> sequencia(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    private BigDecimal somaNotas(UUID tenant) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(total_documento), 0) FROM t_documento_fiscal "
                + "WHERE tenant_id = ? AND tipo = 'NC'", BigDecimal.class, tenant);
    }

    // ------------------------------------------------------------------------------- a

    @Test
    void oitoNcParciaisEmSimultaneoNuncaExcedemAFr() throws Exception {
        Cenario c = cenarioComFr();
        List<Callable<ResultadoNotaCredito>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tarefas.add(nc(c, parcial("30000.00", UUID.randomUUID())));
        }

        List<Desfecho<ResultadoNotaCredito>> r = emParalelo(tarefas, Set.of("NC_EXCEDE_ORIGINAL", "FATURACAO_OCUPADA"));

        long sucessos = r.stream().filter(Desfecho::sucesso).count();
        long excedidas = r.stream().filter(d -> !d.sucesso() && "NC_EXCEDE_ORIGINAL".equals(d.recusa().getCodigo()))
                .count();
        long ocupadas = r.stream().filter(d -> !d.sucesso() && "FATURACAO_OCUPADA".equals(d.recusa().getCodigo()))
                .count();
        assertEquals(THREADS, sucessos + excedidas + ocupadas);
        assertTrue(r.stream().filter(Desfecho::sucesso).allMatch(d -> d.valor().novo()), "cada sucesso é 201");
        r.stream().filter(d -> !d.sucesso()).forEach(d -> assertEquals(
                "NC_EXCEDE_ORIGINAL".equals(d.recusa().getCodigo()) ? HttpStatus.CONFLICT
                        : HttpStatus.SERVICE_UNAVAILABLE, d.recusa().getStatus()));
        if (ocupadas == 0) {
            assertEquals(4, sucessos, "exatamente 4 x 30 000 cabem em 120 000");
            assertEquals(4, excedidas);
        } else {
            assertTrue(sucessos <= 4, "teto mantido mesmo com " + ocupadas + " pedidos ocupados: " + sucessos);
        }

        int k = (int) sucessos;
        BigDecimal creditado = new BigDecimal("30000.00").multiply(BigDecimal.valueOf(k));
        assertEquals(0, creditado.compareTo(somaNotas(c.tenantId())), "soma das NC = NC confirmadas");
        assertTrue(somaNotas(c.tenantId()).compareTo(VALOR_FR) <= 0, "soma das NC nunca excede a FR");
        assertEquals(sequencia(k), fixtura.numerosNotasCredito(c.tenantId()), "números da NC 1..k sem lacunas");
        assertEquals((long) k, fixtura.ultimoNumeroNotaCredito(c.tenantId()));
        assertEquals(0, VALOR_FR.subtract(creditado).compareTo(fixtura.saldo(c.clienteId())),
                "saldo = FR - soma(NC)");
        assertEquals(0, VALOR_FR.subtract(creditado).compareTo(fixtura.totalPagoHonorario(c.honorarioId())));
        assertEquals(k, fixtura.contarEstornos(c.honorarioId()));
    }

    // ------------------------------------------------------------------------------- b

    @Test
    void mesmaChaveEmQuatroPedidosSimultaneosDaUmaSoNc() throws Exception {
        Cenario c = cenarioComFr();
        UUID chave = UUID.randomUUID();
        List<Callable<ResultadoNotaCredito>> tarefas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tarefas.add(nc(c, parcial("20000.00", chave)));
        }

        List<Desfecho<ResultadoNotaCredito>> r = emParalelo(tarefas, Set.of());

        assertEquals(1, fixtura.contarNotasCredito(c.tenantId()), "uma só NC");
        assertEquals(1, fixtura.contarEstornos(c.honorarioId()), "um só estorno");
        UUID id = r.get(0).valor().resposta().id();
        assertTrue(r.stream().allMatch(d -> id.equals(d.valor().resposta().id())), "o mesmo id para todos");
        assertEquals(1, r.stream().filter(d -> d.valor().novo()).count(), "201 uma vez, 200 nas outras");
        assertEquals(0, new BigDecimal("100000.00").compareTo(fixtura.saldo(c.clienteId())), "debitado uma vez");
    }

    // ------------------------------------------------------------------------------- c

    @Test
    void ncEFrDoMesmoClienteIntercaladasSemDeadlock() throws Exception {
        Cenario c = cenarioComFr();
        // Um honorário por processo (UNIQUE processo_id): dois processos novos do MESMO cliente.
        UUID processo2 = fixtura.criarProcesso(c.tenantId(), c.clienteId(), "P-2");
        Integer outroHonorario = fixtura.criarHonorario(processo2, new BigDecimal("900000.00"), "Outro");
        UUID processo3 = fixtura.criarProcesso(c.tenantId(), c.clienteId(), "P-3");
        Integer honorarioOutroProcesso = fixtura.criarHonorario(processo3, new BigDecimal("900000.00"), "Outro P");
        int corridas = 10;

        for (int corrida = 0; corrida < corridas; corrida++) {
            Integer alvoFr = corrida % 2 == 0 ? outroHonorario : honorarioOutroProcesso;
            List<Callable<Object>> tarefas = List.of(
                    () -> service.emitir(c.tenantId(), autor(c.tenantId()), c.frId(),
                            parcial("1000.00", UUID.randomUUID())),
                    () -> emitirFr(c.tenantId(), alvoFr, new BigDecimal("500.00")));

            List<Desfecho<Object>> r = emParalelo(tarefas, Set.of());

            assertTrue(r.stream().allMatch(Desfecho::sucesso), "corrida " + corrida + ": as duas emissões passam");
        }

        assertEquals(sequencia(corridas), fixtura.numerosNotasCredito(c.tenantId()), "série NC sem lacunas");
        assertEquals(sequencia(corridas + 1), fixtura.numerosFaturasRecibo(c.tenantId()), "série FR sem lacunas");
        assertEquals(corridas, fixtura.contarEstornos(c.honorarioId()));
        // 120 000 (FR inicial) - 10 x 1 000 (NC) + 10 x 500 (FR novas)
        assertEquals(0, new BigDecimal("115000.00").compareTo(fixtura.saldo(c.clienteId())));
        assertTrue(falhas.isEmpty(), "nenhuma falha (logo nenhum " + SQLSTATE_DEADLOCK + "): " + falhas);
    }
}
