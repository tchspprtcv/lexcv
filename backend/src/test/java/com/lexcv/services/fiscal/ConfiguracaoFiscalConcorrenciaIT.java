package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-01 da revisão da Phase 133: as mutações de {@link ConfiguracaoFiscalService} serializam-se no
 * lock {@code PESSIMISTIC_WRITE} da linha de {@code t_configuracao_fiscal}
 * ({@code ConfiguracaoFiscalRepository.bloquearPorTenant}).
 *
 * <p>Cada cenário segura a transação T1 aberta depois da mutação (o lock fica retido até ao
 * commit), arranca T2 noutra thread e prova que (a) T2 fica à espera e (b) depois do commit de T1
 * decide sobre o estado já comprometido. Sem o lock, T2 leria a cópia anterior ao commit de T1 e
 * o UPDATE completo de T2 reescreveria a linha (perda de atualização): as asserções sobre o estado
 * final falhariam.
 *
 * <p>Mesmo andaime de {@link NumeracaoServiceConcorrenciaIT}: {@code @DataJpaTest} +
 * {@code Replace.NONE} + {@code @ServiceConnection}, testes {@code NOT_SUPPORTED} e uma
 * {@link TransactionTemplate} por chamada, tenants aleatórios.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ConfiguracaoFiscalService.class, AuditoriaFiscalService.class,
        ConfiguracaoFiscalConcorrenciaIT.Apoio.class})
class ConfiguracaoFiscalConcorrenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** Phase 137: ConfiguracaoFiscalService lê se o SMTP está configurado; aqui não está. */
        @Bean
        com.lexcv.fiscal.email.EmailProperties emailProperties() {
            return new com.lexcv.fiscal.email.EmailProperties(
                    new com.lexcv.fiscal.email.EmailProperties.Smtp(null, 587, null, null, null, true,
                            java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(20)),
                    new com.lexcv.fiscal.email.EmailProperties.Outbox(java.time.Duration.ofSeconds(30),
                            java.time.Duration.ofSeconds(40), 10, java.time.Duration.ofMinutes(2)));
        }
    }

    /** Tempo durante o qual T2 tem de continuar bloqueada enquanto T1 retém o lock. */
    private static final long ESPERA_BLOQUEADA_MS = 1_500;

    @Autowired
    private ConfiguracaoFiscalService service;

    @Autowired
    private ConfiguracaoFiscalRepository configuracaoFiscalRepository;

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

    private static UserPrincipal autor(UUID tenantId, String nome) {
        return UserPrincipal.create(UUID.randomUUID(), tenantId, nome, nome.toLowerCase() + "@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private static ConfiguracaoFiscalRequest pedido(String morada) {
        return new ConfiguracaoFiscalRequest("512345678", "Escritório Silva", morada, "Praia",
                "geral@silva.cv", "+238 260 00 00", RegimeIva.NORMAL, null);
    }

    /** Linha completa e comprometida, com o estado de ativação pedido. */
    private void criarConfiguracao(UUID tenantId, boolean ativa) {
        tx().executeWithoutResult(status -> configuracaoFiscalRepository.saveAndFlush(ConfiguracaoFiscal.builder()
                .tenantId(tenantId)
                .nif("512345678")
                .firma("Escritório Silva")
                .morada("Rua 5 de Julho, 12")
                .localidade("Praia")
                .paisCodigo("CV")
                .emailContacto("geral@silva.cv")
                .telefoneContacto("+238 260 00 00")
                .regimeIva(RegimeIva.NORMAL)
                .ativa(ativa)
                .envioEmailAutomatico(false)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build()));
    }

    private Map<String, Object> linha(UUID tenantId) {
        return jdbcTemplate.queryForMap(
                "select ativa, envio_email_automatico, morada from t_configuracao_fiscal where tenant_id = ?",
                tenantId);
    }

    private int eventos(UUID tenantId, String acao) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(*) from t_audit_log where tenant_id = ? and acao = ?", Integer.class, tenantId, acao);
        return n == null ? 0 : n;
    }

    /**
     * T1 corre {@code primeira} e mantém a transação aberta (lock retido); T2 corre
     * {@code segunda} noutra thread. Prova que T2 não termina enquanto T1 não faz commit e devolve
     * o resultado de T2 (exceção incluída) depois do commit de T1.
     */
    private Future<?> serializar(Consumer<Void> primeira, Runnable segunda, ExecutorService executor)
            throws Exception {
        CountDownLatch t1Mutou = new CountDownLatch(1);
        CountDownLatch libertarT1 = new CountDownLatch(1);
        try {
            Future<?> futuroT1 = executor.submit(() -> tx().executeWithoutResult(status -> {
                primeira.accept(null);
                t1Mutou.countDown();
                try {
                    if (!libertarT1.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("T1 nunca foi libertada");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertTrue(t1Mutou.await(20, TimeUnit.SECONDS), "T1 não chegou a mutar a configuração");

            Future<?> futuroT2 = executor.submit(() -> tx().executeWithoutResult(status -> segunda.run()));
            assertThrows(TimeoutException.class, () -> futuroT2.get(ESPERA_BLOQUEADA_MS, TimeUnit.MILLISECONDS),
                    "T2 terminou enquanto T1 retinha o lock da configuração");

            libertarT1.countDown();
            futuroT1.get(30, TimeUnit.SECONDS);
            return futuroT2;
        } finally {
            libertarT1.countDown();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Cenários
    // -----------------------------------------------------------------------------------------

    /**
     * Interleaving 2 da revisão: desativar (T1) e ligar o email (T2). Com o lock, T2 vê a
     * faturação já desligada e é recusada; sem ele, T2 reescreveria {@code ativa=true,
     * envio=true} sem evento {@code faturacao_ativar}.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void desativarELigarEmailConcorrentesNaoReativamAFaturacao() throws Exception {
        UUID tenantId = UUID.randomUUID();
        criarConfiguracao(tenantId, true);
        UserPrincipal ana = autor(tenantId, "Ana");
        UserPrincipal rui = autor(tenantId, "Rui");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> t2 = serializar(
                    v -> service.desativar(tenantId, ana),
                    () -> service.definirEmailAutomatico(tenantId, rui, new EmailAutomaticoRequest(true, true)),
                    executor);

            ExecutionException falha = assertThrows(ExecutionException.class, () -> t2.get(30, TimeUnit.SECONDS));
            RecusaFiscalException recusa = assertInstanceOf(RecusaFiscalException.class, falha.getCause());
            assertEquals("FATURACAO_DESLIGADA", recusa.getCodigo());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Map<String, Object> estado = linha(tenantId);
        assertFalse((Boolean) estado.get("ativa"));
        assertFalse((Boolean) estado.get("envio_email_automatico"));
        assertEquals(1, eventos(tenantId, AuditoriaFiscalService.ACAO_DESATIVAR));
        assertEquals(0, eventos(tenantId, AuditoriaFiscalService.ACAO_EMAIL_LIGAR));
    }

    /**
     * Interleaving 1 da revisão: ativar (T1) e guardar dados (T2). Com o lock, T2 parte da linha
     * já ativa e a faturação continua ligada; sem ele, T2 reescreveria {@code ativa=false} apesar
     * do evento {@code faturacao_ativar}.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ativarEGuardarConcorrentesNaoPerdemAAtivacao() throws Exception {
        UUID tenantId = UUID.randomUUID();
        criarConfiguracao(tenantId, false);
        UserPrincipal ana = autor(tenantId, "Ana");
        UserPrincipal rui = autor(tenantId, "Rui");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> t2 = serializar(
                    v -> service.ativar(tenantId, ana),
                    () -> service.guardar(tenantId, rui, pedido("Avenida Nova, 1")),
                    executor);
            t2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Map<String, Object> estado = linha(tenantId);
        assertTrue((Boolean) estado.get("ativa"), "a ativação comprometida por T1 foi perdida");
        assertEquals("Avenida Nova, 1", estado.get("morada"));
        assertEquals(1, eventos(tenantId, AuditoriaFiscalService.ACAO_ATIVAR));
        assertEquals(1, eventos(tenantId, AuditoriaFiscalService.ACAO_DADOS_ALTERAR));
    }
}
