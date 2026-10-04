package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PagamentoRequest;
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
 * Phase 134 (EMIS-01, EMIS-07): {@link PagamentoFaturadoService#registar} sob concorrência real em
 * PostgreSQL -- numeração sem lacunas nem duplicados com 8 emissões em simultâneo, saldo exato da
 * conta corrente (sem perda de atualização), corrida de duas submissões com a MESMA chave (um só
 * pagamento, um só documento, um só crédito) e dois tenants independentes; nenhuma falha é um
 * deadlock (40P01).
 *
 * <p>Andaime de {@link NumeracaoServiceConcorrenciaIT}: cada thread espera um latch de partida e
 * chama o serviço diretamente (o serviço é dono da transação); pool fixo de 8 threads; resultados
 * recolhidos com timeout de 60 s.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PagamentoFaturadoService.class, NumeracaoService.class, ParametroFiscalService.class,
        AuditoriaFiscalService.class, PagamentoFaturadoConcorrenciaIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PagamentoFaturadoConcorrenciaIT {

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

    @Autowired
    private PagamentoFaturadoService service;

    @Autowired
    private JdbcTemplate jdbc;

    private FixturaEmissaoFiscal fixtura;
    private ExecutorService executor;

    /** Todas as falhas observadas em todas as corridas (para a verificação de deadlock). */
    private final List<Throwable> falhas = new CopyOnWriteArrayList<>();

    private record Cenario(UUID tenantId, UUID clienteId, Integer honorarioId) {
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

    private Cenario cenario(UUID tenant, int i) {
        UUID cliente = fixtura.criarCliente(tenant, "23456789" + (i % 10), "Cliente Número " + i, "Rua " + i);
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-" + i);
        Integer honorario = fixtura.criarHonorario(processo, new BigDecimal("900000.00"), "Honorário " + i);
        return new Cenario(tenant, cliente, honorario);
    }

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private static PagamentoRequest pedido(Integer honorarioId, BigDecimal valor, UUID chave) {
        return new PagamentoRequest(honorarioId, valor, null, "DINHEIRO", null, chave);
    }

    private Callable<ResultadoPagamentoFaturado> emissao(Cenario c, BigDecimal valor, UUID chave) {
        return () -> service.registar(c.tenantId(), autor(c.tenantId()), pedido(c.honorarioId(), valor, chave));
    }

    /**
     * Liberta todas as tarefas ao mesmo tempo (latch de partida) e devolve os resultados por ordem
     * de submissão. Qualquer falha é registada (verificação de deadlock) e faz falhar o teste.
     */
    private <T> List<T> emParalelo(List<Callable<T>> tarefas) throws Exception {
        CountDownLatch partida = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (Callable<T> tarefa : tarefas) {
            futuros.add(executor.submit(() -> {
                partida.await();
                return tarefa.call();
            }));
        }
        partida.countDown();
        List<T> resultados = new ArrayList<>();
        for (Future<T> f : futuros) {
            try {
                resultados.add(f.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                falhas.add(e.getCause());
                fail("emissão concorrente falhou: " + e.getCause(), e.getCause());
            }
        }
        return resultados;
    }

    /** e. Nenhuma falha, em nenhuma corrida, foi um deadlock detetado pelo PostgreSQL. */
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

    private int contarPorChave(UUID tenant, UUID chave) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM t_documento_fiscal WHERE tenant_id = ? "
                + "AND chave_idempotencia = ?", Integer.class, tenant, chave);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------------------- a

    @Test
    void oitoEmissoesParalelasMesmoTenantClientesDiferentes() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        List<Cenario> cenarios = new ArrayList<>();
        List<Callable<ResultadoPagamentoFaturado>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Cenario c = cenario(tenant, i);
            cenarios.add(c);
            tarefas.add(emissao(c, new BigDecimal(1000 + i + ".00"), UUID.randomUUID()));
        }

        List<ResultadoPagamentoFaturado> r = emParalelo(tarefas);

        assertTrue(r.stream().allMatch(ResultadoPagamentoFaturado::novo));
        assertEquals(sequencia(THREADS), fixtura.numerosEmitidos(tenant), "números 1..8 sem lacunas nem duplicados");
        assertEquals((long) THREADS, fixtura.ultimoNumero(tenant));
        assertEquals(THREADS, fixtura.contarDocumentos(tenant));
        for (int i = 0; i < THREADS; i++) {
            Cenario c = cenarios.get(i);
            assertEquals(0, new BigDecimal(1000 + i + ".00").compareTo(fixtura.saldo(c.clienteId())),
                    "saldo do cliente " + i);
            assertEquals(1, fixtura.contarPagamentos(c.honorarioId()));
        }
    }

    // ------------------------------------------------------------------------------- b

    @Test
    void oitoEmissoesParalelasMesmoCliente() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, 0);
        List<Callable<ResultadoPagamentoFaturado>> tarefas = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < THREADS; i++) {
            BigDecimal valor = new BigDecimal((i + 1) * 100 + ".25");
            soma = soma.add(valor);
            tarefas.add(emissao(c, valor, UUID.randomUUID()));
        }

        emParalelo(tarefas);

        assertEquals(0, soma.compareTo(fixtura.saldo(c.clienteId())), "saldo = soma exata (sem perda de atualização)");
        assertEquals(THREADS, fixtura.contarPagamentos(c.honorarioId()));
        assertEquals(sequencia(THREADS), fixtura.numerosEmitidos(tenant));
    }

    // ------------------------------------------------------------------------------- c

    @Test
    void mesmaChaveDoisPedidosEmSimultaneo() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        BigDecimal saldoEsperado = BigDecimal.ZERO;
        for (int corrida = 0; corrida < 5; corrida++) {
            Cenario c = cenario(tenant, corrida);
            UUID chave = UUID.randomUUID();
            BigDecimal valor = new BigDecimal("2500.00");

            List<ResultadoPagamentoFaturado> r = emParalelo(List.of(emissao(c, valor, chave), emissao(c, valor, chave)));

            assertEquals(1, fixtura.contarPagamentos(c.honorarioId()), "corrida " + corrida + ": um só pagamento");
            assertEquals(1, contarPorChave(tenant, chave), "corrida " + corrida + ": um só documento");
            assertEquals(r.get(0).resposta().documentoFiscal().id(), r.get(1).resposta().documentoFiscal().id());
            assertEquals(r.get(0).resposta().id(), r.get(1).resposta().id());
            assertEquals(1, r.stream().filter(ResultadoPagamentoFaturado::novo).count(), "exatamente um novo");
            assertEquals(0, valor.compareTo(fixtura.saldo(c.clienteId())), "conta corrente creditada uma vez");
            saldoEsperado = saldoEsperado.add(valor);
        }
        assertEquals(sequencia(5), fixtura.numerosEmitidos(tenant));
        assertEquals(5, fixtura.contarDocumentos(tenant));
        assertTrue(saldoEsperado.signum() > 0);
    }

    // ------------------------------------------------------------------------------- d

    @Test
    void doisTenantsEmParalelo() throws Exception {
        UUID tenantA = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID tenantB = fixtura.criarTenantComFaturacao(RegimeIva.ISENTO);
        List<Callable<ResultadoPagamentoFaturado>> tarefas = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            tarefas.add(emissao(cenario(tenantA, i), new BigDecimal("100.00"), UUID.randomUUID()));
            tarefas.add(emissao(cenario(tenantB, i), new BigDecimal("200.00"), UUID.randomUUID()));
        }

        emParalelo(tarefas);

        assertEquals(sequencia(4), fixtura.numerosEmitidos(tenantA));
        assertEquals(sequencia(4), fixtura.numerosEmitidos(tenantB));
        assertEquals(4L, fixtura.ultimoNumero(tenantA));
        assertEquals(4L, fixtura.ultimoNumero(tenantB));
    }
}
