package com.lexcv.repositories;

import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.ParecerVersao;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Segundo teste de integração com PostgreSQL real (Testcontainers) deste backend, reutilizando a
 * infraestrutura de 91-01. Fecha o risco TEST-02: a atomicidade de {@code ParecerVersao.numeroVersao}
 * depende de um lock {@code PESSIMISTIC_WRITE} ({@code ParecerSolicitacaoRepository.findByIdForUpdate})
 * mais uma constraint única na BD {@code (solicitacao_id, numero_versao)} — ambos nunca provados sob
 * concorrência real (WR-04, {@code ParecerController.createVersao}, linhas 472/513/523). Um bloco
 * {@code synchronized} da JVM nunca provaria isto; H2 não reproduz a semântica MVCC do Postgres com
 * fidelidade suficiente.
 *
 * Não passa por {@code ParecerController.createVersao} — isso exigiria {@code @SpringBootTest} e
 * instanciaria {@code MinioConfig}/{@code SecurityConfig} (o bloqueio MINIO_ENDPOINT). Em vez disso,
 * este teste replica diretamente a sequência lock+incremento+insert ao nível do repositório, que é
 * onde a garantia de atomicidade realmente vive.
 *
 * {@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio MINIO_ENDPOINT:
 * esta fatia de contexto nunca instancia MinioConfig/SecurityConfig.
 *
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} — sem ele o {@code @DataJpaTest} troca silenciosamente a DataSource por
 * uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ParecerVersaoConcorrenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    @Autowired
    private ParecerVersaoRepository parecerVersaoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Prova principal do TEST-02: dois threads, cada um na sua própria transação
     * independentemente comprometida, correm a sequência exata que
     * {@code ParecerController.createVersao} executa —
     * {@code findByIdForUpdate} (adquire o lock de linha) → calcula
     * {@code next = findMaxNumeroVersaoBySolicitacaoId(...).orElse(0) + 1} → {@code save(...)}.
     *
     * {@code @Transactional(propagation = NOT_SUPPORTED)} desliga o embrulho automático de
     * transação (com rollback no final) que o {@code @DataJpaTest} aplicaria por omissão a este
     * método de teste — uma concorrência genuína exige duas transações reais e independentemente
     * comprometidas, não uma única transação que nunca comita.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createVersao_duasTransacoesConcorrentes_lockSerializaIncrementoDeNumeroVersao()
            throws InterruptedException, ExecutionException, TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID tenantId = UUID.randomUUID();

        UUID solicitacaoId = transactionTemplate.execute(status -> {
            ParecerSolicitacao solicitacao = ParecerSolicitacao.builder()
                    .tenantId(tenantId)
                    .clienteId(UUID.randomUUID())
                    .descricao("Parecer para teste de concorrência do lock de numeroVersao")
                    .build();
            return parecerSolicitacaoRepository.save(solicitacao).getId();
        });

        CountDownLatch latch = new CountDownLatch(1);
        Runnable criarVersao = () -> transactionTemplate.executeWithoutResult(status -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            // WR-04: adquire o lock PESSIMISTIC_WRITE sobre a solicitacao pai, mantido até esta
            // transação comitar/reverter — é isto que serializa as duas threads.
            parecerSolicitacaoRepository.findByIdForUpdate(solicitacaoId);
            int next = parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(solicitacaoId).orElse(0) + 1;
            parecerVersaoRepository.save(ParecerVersao.builder()
                    .tenantId(tenantId)
                    .solicitacaoId(solicitacaoId)
                    .numeroVersao(next)
                    .build());
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = executor.submit(criarVersao);
            Future<?> futureB = executor.submit(criarVersao);
            // Liberta as duas threads o mais simultaneamente possível para maximizar a contenção
            // no lock.
            latch.countDown();
            // Timeout limitado: um deadlock no lock falha o teste rapidamente em vez de bloquear
            // a suite indefinidamente.
            futureA.get(20, TimeUnit.SECONDS);
            futureB.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        List<ParecerVersao> versoes = parecerVersaoRepository.findBySolicitacaoId(solicitacaoId);
        assertEquals(2, versoes.size());
        Set<Integer> numeros = versoes.stream()
                .map(ParecerVersao::getNumeroVersao)
                .collect(Collectors.toSet());
        // Prova observável: o lock PESSIMISTIC_WRITE (não um monitor da JVM) tornou o
        // incremento-e-insert atómico entre transações — nunca um duplicado {1, 1}.
        assertEquals(Set.of(1, 2), numeros);
    }

    /**
     * Backstop da constraint única {@code (solicitacao_id, numero_versao)} declarada em
     * {@code @Table(uniqueConstraints=...)} de {@code ParecerVersao}. Este método corre sob a
     * transação ambiente (rollback-only) por omissão do {@code @DataJpaTest} com
     * {@code FlushMode.AUTO}; um {@code .save()} simples apenas regista a entidade no persistence
     * context — o Hibernate DIFERE o INSERT e nunca envia SQL ao Postgres antes da asserção correr,
     * pelo que a violação da constraint única nunca surgiria. Por isso usa-se
     * {@code saveAndFlush(...)} (nunca {@code .save()} simples) para forçar o INSERT
     * sincronamente, com a segunda chamada colidente DENTRO do lambda de
     * {@code assertThrows(...)} para o flush disparar de forma síncrona.
     */
    @Test
    void createVersao_numeroVersaoDuplicado_constraintUnicaRejeitaComDataIntegrityViolationException() {
        UUID tenantId = UUID.randomUUID();
        ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.save(ParecerSolicitacao.builder()
                .tenantId(tenantId)
                .clienteId(UUID.randomUUID())
                .descricao("Parecer para teste do backstop da constraint única")
                .build());

        // Primeira versão: saveAndFlush garante que a linha é mesmo escrita no contentor antes da
        // tentativa de colisão.
        parecerVersaoRepository.saveAndFlush(ParecerVersao.builder()
                .tenantId(tenantId)
                .solicitacaoId(solicitacao.getId())
                .numeroVersao(1)
                .build());

        assertThrows(DataIntegrityViolationException.class, () ->
                parecerVersaoRepository.saveAndFlush(ParecerVersao.builder()
                        .tenantId(tenantId)
                        .solicitacaoId(solicitacao.getId())
                        .numeroVersao(1)
                        .build()));
    }
}
