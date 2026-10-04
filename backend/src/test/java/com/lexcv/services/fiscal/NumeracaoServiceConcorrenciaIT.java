package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.repositories.SerieFiscalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 133 (CFG-05): prova exigida pelo ROADMAP ("provar a concorrência com teste de integração
 * em PostgreSQL real") de que {@link NumeracaoService} atribui números consecutivos, sem lacunas
 * nem duplicados, por série (tenant, tipo, ano civil de Cabo Verde, ambiente).
 *
 * <p>Mesmo andaime de {@code ParecerVersaoConcorrenciaIT}: {@code @DataJpaTest} (nunca
 * o arranque completo da aplicação, que instanciaria {@code MinioConfig}/{@code SecurityConfig}) +
 * {@code Replace.NONE} + {@code @ServiceConnection} sobre {@code postgres:16-alpine}. Cada teste
 * corre com {@code @Transactional(propagation = NOT_SUPPORTED)} e cada chamada ao serviço numa
 * {@link TransactionTemplate} própria, para que os commits sejam reais e independentes. Cada teste
 * usa tenants aleatórios, pelo que não interferem entre si.
 *
 * <p>O {@link Clock} vem de {@link RelogioFixo} (não de {@code ClockConfig}, para não haver dois
 * beans {@code Clock}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({NumeracaoService.class, NumeracaoServiceConcorrenciaIT.RelogioFixo.class})
class NumeracaoServiceConcorrenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final TipoDocumentoFiscal FR = TipoDocumentoFiscal.FR;
    private static final AmbienteFiscal SIMULADO = AmbienteFiscal.SIMULADO;

    @Autowired
    private NumeracaoService numeracaoService;

    @Autowired
    private SerieFiscalRepository serieFiscalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -----------------------------------------------------------------------------------------
    // Auxiliares
    // -----------------------------------------------------------------------------------------

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private long alocar(NumeracaoService service, UUID tenantId) {
        return tx().execute(status -> service.proximoNumero(tenantId, FR, SIMULADO).numero());
    }

    private int contarSeries(UUID tenantId, int ano) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(*) from t_serie_fiscal where tenant_id = ? and tipo_documento = 'FR' "
                        + "and ano = ? and ambiente = 'SIMULADO'", Integer.class, tenantId, ano);
        return n == null ? 0 : n;
    }

    private long ultimoNumero(UUID tenantId, int ano) {
        Long n = jdbcTemplate.queryForObject(
                "select ultimo_numero from t_serie_fiscal where tenant_id = ? and tipo_documento = 'FR' "
                        + "and ano = ? and ambiente = 'SIMULADO'", Long.class, tenantId, ano);
        return n == null ? -1 : n;
    }

    private static List<Long> sequencia(int n) {
        return LongStream.rangeClosed(1, n).boxed().toList();
    }

    /**
     * Corre cada tarefa numa thread própria, todas libertadas ao mesmo tempo por um latch, e
     * devolve os resultados por ordem de submissão.
     */
    private static List<Long> emParalelo(List<Callable<Long>> tarefas) throws Exception {
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tarefas.size());
        try {
            List<Future<Long>> futuros = new ArrayList<>();
            for (Callable<Long> tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    partida.await();
                    return tarefa.call();
                }));
            }
            partida.countDown();
            List<Long> resultados = new ArrayList<>();
            for (Future<Long> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            // shutdownNow (WR-03 da Phase 91): interrompe um worker preso num lock em vez de o
            // deixar vazar para a JVM partilhada pelo Failsafe.
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Cenários
    // -----------------------------------------------------------------------------------------

    /** (1) 8 transações concorrentes na mesma série recebem exatamente 1..8. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concorrenciaMesmaSerie() throws Exception {
        UUID tenantId = UUID.randomUUID();
        // A série já existe (1 número comprometido), para isolar a concorrência no FOR UPDATE.
        assertEquals(1L, alocar(numeracaoService, tenantId));

        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            tarefas.add(() -> alocar(numeracaoService, tenantId));
        }
        List<Long> numeros = new ArrayList<>(emParalelo(tarefas));
        numeros.add(1L);
        numeros.sort(null);

        assertEquals(sequencia(8), numeros);
        assertEquals(1, contarSeries(tenantId, 2026));
        assertEquals(8L, ultimoNumero(tenantId, 2026));
    }

    /**
     * (1b) WR-02 da revisão: o chamador já tem a série no persistence context (lida sem lock),
     * outra transação compromete um número entretanto, e só depois o chamador pede o próximo.
     * O FOR UPDATE devolveria a instância gerida desatualizada; o refresh garante o número certo
     * em vez de um duplicado.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serieJaCarregadaNoPersistenceContextNaoDuplicaNumero() throws Exception {
        UUID tenantId = UUID.randomUUID();
        assertEquals(1L, alocar(numeracaoService, tenantId));

        CountDownLatch carregada = new CountDownLatch(1);
        CountDownLatch outraComprometeu = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Long> chamador = executor.submit(() -> tx().execute(status -> {
                List<SerieFiscal> lidas = serieFiscalRepository.findByTenantIdOrderByAnoDescTipoDocumentoAsc(tenantId);
                assertEquals(1L, lidas.get(0).getUltimoNumero());
                carregada.countDown();
                try {
                    if (!outraComprometeu.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("a outra transação não comprometeu");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return numeracaoService.proximoNumero(tenantId, FR, SIMULADO).numero();
            }));

            assertTrue(carregada.await(20, TimeUnit.SECONDS), "o chamador não carregou a série");
            assertEquals(2L, alocar(numeracaoService, tenantId));
            outraComprometeu.countDown();

            assertEquals(3L, chamador.get(30, TimeUnit.SECONDS));
        } finally {
            outraComprometeu.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        assertEquals(3L, ultimoNumero(tenantId, 2026));
    }

    /** (2) Primeiro uso concorrente: ON CONFLICT DO NOTHING cria uma única série. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void primeiroUsoConcorrente() throws Exception {
        UUID tenantId = UUID.randomUUID();
        assertEquals(0, contarSeries(tenantId, 2026));

        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tarefas.add(() -> alocar(numeracaoService, tenantId));
        }
        List<Long> numeros = new ArrayList<>(emParalelo(tarefas));
        numeros.sort(null);

        assertEquals(sequencia(8), numeros);
        assertEquals(1, contarSeries(tenantId, 2026));
        assertEquals(8L, ultimoNumero(tenantId, 2026));
    }

    /** (3) Um rollback liberta o número: o próximo chamador recebe o mesmo número. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollbackNaoDeixaLacuna() {
        UUID tenantId = UUID.randomUUID();

        Long revertido = tx().execute(status -> {
            long n = numeracaoService.proximoNumero(tenantId, FR, SIMULADO).numero();
            status.setRollbackOnly();
            return n;
        });
        assertEquals(1L, revertido);

        assertEquals(1L, alocar(numeracaoService, tenantId));
        assertEquals(2L, alocar(numeracaoService, tenantId));
        assertEquals(2L, ultimoNumero(tenantId, 2026));
    }

    /** (4) Dois tenants na mesma série (tipo/ano/ambiente) têm sequências independentes. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void isolamentoEntreTenants() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tarefas.add(() -> alocar(numeracaoService, tenantA));
            tarefas.add(() -> alocar(numeracaoService, tenantB));
        }
        List<Long> resultados = emParalelo(tarefas);

        List<Long> numerosA = new ArrayList<>();
        List<Long> numerosB = new ArrayList<>();
        for (int i = 0; i < resultados.size(); i++) {
            (i % 2 == 0 ? numerosA : numerosB).add(resultados.get(i));
        }
        numerosA.sort(null);
        numerosB.sort(null);

        assertEquals(sequencia(4), numerosA);
        assertEquals(sequencia(4), numerosB);
        assertEquals(4L, ultimoNumero(tenantA, 2026));
        assertEquals(4L, ultimoNumero(tenantB, 2026));
    }

    /** (5) A numeração recomeça em 1 em cada ano civil. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reinicioAnual() {
        UUID tenantId = UUID.randomUUID();
        // Instâncias manuais sem proxy MANDATORY: chamadas na mesma dentro de uma transação.
        NumeracaoService em2026 = new NumeracaoService(serieFiscalRepository,
                Clock.fixed(Instant.parse("2026-12-31T12:00:00Z"), ZoneOffset.UTC));
        NumeracaoService em2027 = new NumeracaoService(serieFiscalRepository,
                Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneOffset.UTC));

        assertEquals(1L, alocar(em2026, tenantId));
        assertEquals(2L, alocar(em2026, tenantId));
        assertEquals(3L, alocar(em2026, tenantId));

        NumeroFiscalAtribuido primeiro2027 = tx().execute(status ->
                em2027.proximoNumero(tenantId, FR, SIMULADO));
        assertEquals(1L, primeiro2027.numero());
        assertEquals(2027, primeiro2027.ano());
        assertEquals("SIM-FR-2027", primeiro2027.serieCodigo());

        assertEquals(3L, ultimoNumero(tenantId, 2026));
        assertEquals(1L, ultimoNumero(tenantId, 2027));
    }

    /** (6) 00:30 UTC de 1 de janeiro ainda é 31 de dezembro em Cabo Verde (UTC-1). */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void fronteiraDeAnoEmCaboVerde() {
        UUID tenantId = UUID.randomUUID();
        NumeracaoService antesDaMeiaNoite = new NumeracaoService(serieFiscalRepository,
                Clock.fixed(Instant.parse("2027-01-01T00:30:00Z"), ZoneOffset.UTC));
        NumeracaoService depoisDaMeiaNoite = new NumeracaoService(serieFiscalRepository,
                Clock.fixed(Instant.parse("2027-01-01T01:30:00Z"), ZoneOffset.UTC));

        NumeroFiscalAtribuido antes = tx().execute(status ->
                antesDaMeiaNoite.proximoNumero(tenantId, FR, SIMULADO));
        assertEquals(2026, antes.ano());
        assertEquals(LocalDate.of(2026, 12, 31), antes.dataEmissao());
        assertEquals("SIM-FR-2026", antes.serieCodigo());
        assertEquals(1L, antes.numero());

        NumeroFiscalAtribuido depois = tx().execute(status ->
                depoisDaMeiaNoite.proximoNumero(tenantId, FR, SIMULADO));
        assertEquals(2027, depois.ano());
        assertEquals(LocalDate.of(2027, 1, 1), depois.dataEmissao());
        assertEquals("SIM-FR-2027", depois.serieCodigo());
        assertEquals(1L, depois.numero());
    }

    /** (7) Fora de uma transação o serviço (proxy Spring) recusa correr. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void foraDeTransacaoRecusa() {
        UUID tenantId = UUID.randomUUID();
        assertThrows(IllegalTransactionStateException.class,
                () -> numeracaoService.proximoNumero(tenantId, FR, SIMULADO));
        assertEquals(0, contarSeries(tenantId, 2026));
    }

    /**
     * (8) Lock retido por outra transação numa série nova (B fica à espera da linha ainda não
     * comprometida no INSERT ON CONFLICT): o lock_timeout de 5s dispara SERIE_INDISPONIVEL e a
     * tentativa falhada não consome número.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockTimeoutFalhaRapido() throws Exception {
        UUID tenantId = UUID.randomUUID();
        verificarLockTimeout(tenantId, 1L);
    }

    /**
     * (8b) Igual a (8), mas com a série já existente: B fica à espera no SELECT ... FOR UPDATE.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockTimeoutFalhaRapidoComSerieExistente() throws Exception {
        UUID tenantId = UUID.randomUUID();
        assertEquals(1L, alocar(numeracaoService, tenantId));
        verificarLockTimeout(tenantId, 2L);
    }

    private void verificarLockTimeout(UUID tenantId, long numeroEsperadoDeA) throws Exception {
        CountDownLatch aBloqueou = new CountDownLatch(1);
        CountDownLatch libertarA = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Long> futuroA = executor.submit(() -> tx().execute(status -> {
                long n = numeracaoService.proximoNumero(tenantId, FR, SIMULADO).numero();
                // O lock (FOR UPDATE ou a linha não comprometida do INSERT) fica retido até ao fim
                // desta transação.
                aBloqueou.countDown();
                try {
                    if (!libertarA.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("A nunca foi libertada");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return n;
            }));

            assertTrue(aBloqueou.await(20, TimeUnit.SECONDS), "A não obteve o lock da série");

            long inicio = System.nanoTime();
            RecusaFiscalException recusa = assertThrows(RecusaFiscalException.class,
                    () -> alocar(numeracaoService, tenantId));
            long decorridoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

            assertEquals("SERIE_INDISPONIVEL", recusa.getCodigo());
            assertTrue(decorridoMs >= 4_000, "lock_timeout disparou cedo demais: " + decorridoMs + "ms");
            assertTrue(decorridoMs < 15_000, "lock_timeout não disparou a tempo: " + decorridoMs + "ms");

            libertarA.countDown();
            assertEquals(numeroEsperadoDeA, futuroA.get(30, TimeUnit.SECONDS));
        } finally {
            libertarA.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(1, contarSeries(tenantId, 2026));
        // A tentativa falhada de B não consumiu nenhum número.
        assertEquals(numeroEsperadoDeA, ultimoNumero(tenantId, 2026));
        assertEquals(numeroEsperadoDeA + 1, alocar(numeracaoService, tenantId));
    }
}
