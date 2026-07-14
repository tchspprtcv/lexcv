package com.lexcv.repositories;

import com.lexcv.models.NotificacaoPreferencia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terceiro teste de integração com PostgreSQL real (Testcontainers) deste backend, reutilizando a
 * infraestrutura de 91-01/91-02. Fecha o gap IN-03 (nenhuma cobertura direta de
 * {@code NotificacaoPreferenciaRepository} contra uma base de dados real) e prova o fix de WR-01
 * (iteração 2, re-review): {@code NotificacaoService.silenciarCategoria()} usava um
 * {@code repository.save(...)} simples dentro de um try/catch(DataIntegrityViolationException) --
 * mas {@code NotificacaoPreferencia.id} usa {@code GenerationType.UUID} (gerador em memória, sem
 * round-trip à BD), pelo que {@code save()} só chama {@code entityManager.persist()}; o INSERT real
 * (e a violação da constraint única) só aconteceria no commit da transação envolvente, DEPOIS do
 * método (e do seu try/catch) já ter retornado -- exatamente o defeito original de WR-01
 * reaparecendo. Um teste Mockito (ver {@code NotificacaoServiceTest}) não consegue detetar isto
 * porque um repositório mockado não reproduz a semântica de flush diferido do Hibernate.
 *
 * {@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio
 * MINIO_ENDPOINT: esta fatia de contexto nunca instancia MinioConfig/SecurityConfig.
 *
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} -- sem ele o {@code @DataJpaTest} troca silenciosamente a DataSource
 * por uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificacaoPreferenciaRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificacaoPreferenciaRepository notificacaoPreferenciaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void uniqueConstraint_insercaoDuplicada_rejeitadaComDataIntegrityViolationException() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // saveAndFlush (não save) garante que o primeiro INSERT é mesmo escrito no contentor
        // antes da tentativa de colisão -- ver o mesmo raciocínio em ParecerVersaoConcorrenciaIT.
        notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userId).categoria("FASE_ENTRADA").build());

        assertThrows(DataIntegrityViolationException.class, () ->
                notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                        .tenantId(tenantId).userId(userId).categoria("FASE_ENTRADA").build()));
    }

    @Test
    void existsByTenantIdAndUserIdAndCategoria_escopadoPorTenantEUser_nuncaVazaOutroTenantOuOutroUser() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantA).userId(userA).categoria("FASE_ENTRADA").build());

        assertTrue(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantA, userA, "FASE_ENTRADA"));
        assertFalse(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantB, userA, "FASE_ENTRADA"));
        assertFalse(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantA, userB, "FASE_ENTRADA"));
    }

    @Test
    void findByTenantIdAndUserId_devolveApenasAsLinhasDoUserNesseTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userA).categoria("FASE_ENTRADA").build());
        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userA).categoria("DOCUMENTO_NOVO").build());
        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userB).categoria("FASE_ENTRADA").build());

        List<NotificacaoPreferencia> preferencias =
                notificacaoPreferenciaRepository.findByTenantIdAndUserId(tenantId, userA);

        assertEquals(2, preferencias.size());
        assertTrue(preferencias.stream().allMatch(p -> p.getUserId().equals(userA)));
    }

    @Test
    void deleteByTenantIdAndUserIdAndCategoria_removeApenasALinhaAlvo_naoAfetaOutroUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userA).categoria("FASE_ENTRADA").build());
        notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userB).categoria("FASE_ENTRADA").build());

        notificacaoPreferenciaRepository.deleteByTenantIdAndUserIdAndCategoria(tenantId, userA, "FASE_ENTRADA");

        assertFalse(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userA, "FASE_ENTRADA"));
        assertTrue(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userB, "FASE_ENTRADA"));
    }

    /**
     * Prova principal do fix de WR-01 (iteração 2): duas transações concorrentes, cada uma
     * independentemente comprometida, tentam silenciar a MESMA (tenant, user, categoria) em
     * simultâneo, replicando exatamente a sequência de
     * {@code NotificacaoService.silenciarCategoria()} -- {@code existsBy...} seguido de
     * {@code saveAndFlush(...)} dentro de um try/catch(DataIntegrityViolationException).
     *
     * {@code @Transactional(propagation = NOT_SUPPORTED)} desliga o embrulho automático de
     * transação (rollback-only) que o {@code @DataJpaTest} aplicaria por omissão a este método de
     * teste -- a prova exige duas transações reais e independentemente comprometidas.
     *
     * Com {@code saveAndFlush} (o fix), nenhum {@code futureX.get(...)} deve lançar
     * {@code ExecutionException}: a transação perdedora apanha a
     * {@code DataIntegrityViolationException} DENTRO do try/catch e trata-a como sucesso
     * idempotente, exatamente como a transação vencedora. Antes do fix (com um {@code save()}
     * simples), a exceção só surgiria no commit -- fora do try/catch -- e escaparia como
     * {@code ExecutionException} aqui, tal como escaparia para {@code GlobalExceptionHandler} em
     * produção.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void silenciarCategoria_duasTransacoesConcorrentes_perdedoraApanhaExcecaoSemEscaparDoCatch()
            throws InterruptedException, ExecutionException, TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String categoria = "FASE_ENTRADA";

        CountDownLatch latch = new CountDownLatch(1);
        Runnable silenciar = () -> transactionTemplate.executeWithoutResult(status -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            try {
                if (!notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)) {
                    notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                            .tenantId(tenantId)
                            .userId(userId)
                            .categoria(categoria)
                            .build());
                }
            } catch (DataIntegrityViolationException ex) {
                // Réplica exata do catch de NotificacaoService.silenciarCategoria(): perdeu a
                // corrida, trata como sucesso idempotente -- nunca deve escapar do Runnable.
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = executor.submit(silenciar);
            Future<?> futureB = executor.submit(silenciar);
            // Liberta as duas threads o mais simultaneamente possível para maximizar a contenção
            // no índice único.
            latch.countDown();
            // Timeout limitado: um deadlock falha o teste rapidamente em vez de bloquear a suite.
            futureA.get(20, TimeUnit.SECONDS);
            futureB.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<NotificacaoPreferencia> preferencias =
                notificacaoPreferenciaRepository.findByTenantIdAndUserId(tenantId, userId);
        assertEquals(1, preferencias.size());
        assertEquals(categoria, preferencias.get(0).getCategoria());
    }
}
