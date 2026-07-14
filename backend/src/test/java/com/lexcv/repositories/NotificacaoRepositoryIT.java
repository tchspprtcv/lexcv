package com.lexcv.repositories;

import com.lexcv.models.Notificacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Primeiro teste de integração com PostgreSQL real (Testcontainers) deste backend.
 * {@code NotificacaoRepository.buscarPorFiltros} é a primeira combinação nativeQuery=true +
 * Pageable do projeto, com o idioma {@code CAST(:param AS text/boolean) IS NULL} exigido pelo
 * Postgres para inferir o tipo de um bind nulo — um comportamento específico do motor que o H2
 * não reproduz com fidelidade suficiente (ver .planning/research/STACK.md).
 *
 * {@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio
 * MINIO_ENDPOINT: esta fatia de contexto nunca instancia MinioConfig/SecurityConfig, logo os
 * respetivos placeholders de ambiente nunca são avaliados.
 *
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} — sem ele o {@code @DataJpaTest} troca silenciosamente a
 * DataSource por uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificacaoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Persiste uma linha válida via o builder, variando sempre entidadeId para nunca colidir
     * com uk_notificacao_dedup (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)
     * entre fixtures do mesmo teste.
     */
    private Notificacao persistir(UUID tenantId, UUID destinatarioId, String categoria, String entidadeId, boolean lida) {
        Notificacao notificacao = Notificacao.builder()
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .entidadeTipo("processo")
                .entidadeId(entidadeId)
                .titulo("Título " + entidadeId)
                .mensagem("Mensagem " + entidadeId)
                .lida(lida)
                .build();
        return notificacaoRepository.save(notificacao);
    }

    /**
     * NOTF-26: variante de {@link #persistir} que também define {@code snoozedUntil} no momento
     * da persistência (via o builder), para os testes de visibilidade de snooze abaixo. Um
     * {@code snoozedUntil} nulo é equivalente a nunca ter sido adiada.
     */
    private Notificacao persistirComSnooze(UUID tenantId, UUID destinatarioId, String categoria, String entidadeId,
                                            boolean lida, LocalDateTime snoozedUntil) {
        Notificacao notificacao = Notificacao.builder()
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .entidadeTipo("processo")
                .entidadeId(entidadeId)
                .titulo("Título " + entidadeId)
                .mensagem("Mensagem " + entidadeId)
                .lida(lida)
                .snoozedUntil(snoozedUntil)
                .build();
        return notificacaoRepository.save(notificacao);
    }

    @Test
    void buscarPorFiltros_escopaPorTenantEDestinatario_nuncaVazaOutroTenantOuOutroDestinatario() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID destinatarioA = UUID.randomUUID();
        UUID destinatarioB = UUID.randomUUID();

        persistir(tenantA, destinatarioA, "FASE_ENTRADA", "id-1", false);
        persistir(tenantA, destinatarioB, "FASE_ENTRADA", "id-2", false);
        persistir(tenantB, destinatarioA, "FASE_ENTRADA", "id-3", false);

        Page<Notificacao> page = notificacaoRepository.buscarPorFiltros(
                tenantA, destinatarioA, null, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(destinatarioA, page.getContent().get(0).getDestinatarioId());
        assertEquals(tenantA, page.getContent().get(0).getTenantId());
    }

    @Test
    void buscarPorFiltros_categoriaNula_devolveTodas_categoriaConcreta_filtraApenasEssa() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-10", false);
        persistir(tenantId, destinatarioId, "DOCUMENTO_NOVO", "id-11", false);

        // Bind nulo: prova CAST(:categoria AS text) IS NULL contra Postgres real.
        Page<Notificacao> todas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 10));
        assertEquals(2, todas.getTotalElements());

        Page<Notificacao> apenasFase = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, "FASE_ENTRADA", null, PageRequest.of(0, 10));
        assertEquals(1, apenasFase.getTotalElements());
        assertEquals("FASE_ENTRADA", apenasFase.getContent().get(0).getCategoria());
    }

    @Test
    void buscarPorFiltros_lidaNula_devolveTodas_lidaConcreta_filtraApenasEssaFlag() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-20", false);
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-21", true);

        // Bind nulo: prova CAST(:lida AS boolean) IS NULL contra Postgres real.
        Page<Notificacao> todas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 10));
        assertEquals(2, todas.getTotalElements());

        Page<Notificacao> naoLidas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, false, PageRequest.of(0, 10));
        assertEquals(1, naoLidas.getTotalElements());
        assertFalse(naoLidas.getContent().get(0).getLida());

        Page<Notificacao> lidas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, true, PageRequest.of(0, 10));
        assertEquals(1, lidas.getTotalElements());
        assertTrue(lidas.getContent().get(0).getLida());
    }

    @Test
    void buscarPorFiltros_paginacao_totalElementsCorretoETamanhoDePaginaRespeitado_ordemDescPorCreatedAt() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        Notificacao n1 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-30", false);
        Notificacao n2 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-31", false);
        Notificacao n3 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-32", false);

        // WR-01 (Phase 91 code review): created_at é preenchido por @PrePersist com
        // LocalDateTime.now() e a coluna é updatable=false (a entidade não expõe setter), pelo
        // que não dá para pré-atribuir o valor antes do save(). Em vez de depender de
        // Thread.sleep() entre inserções -- um padrão de teste inerentemente instável, já que
        // duas inserções podem cair no mesmo milissegundo sob carga de CI/GC -- força-se aqui
        // timestamps estritamente crescentes via UPDATE nativo pós-persistência.
        entityManager.flush();
        LocalDateTime base = LocalDateTime.now();
        forcarCreatedAt(n1.getId(), base.minusSeconds(2));
        forcarCreatedAt(n2.getId(), base.minusSeconds(1));
        forcarCreatedAt(n3.getId(), base);
        // Descarta o 1º-level cache: sem isto, a query nativa abaixo devolveria as instâncias
        // já geridas (com os createdAt originais, quase simultâneos entre si) em vez dos valores
        // reais agora persistidos na BD.
        entityManager.clear();

        Page<Notificacao> page = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());

        List<Notificacao> content = page.getContent();
        assertEquals(n3.getId(), content.get(0).getId());
        assertEquals(n2.getId(), content.get(1).getId());
        assertTrue(content.get(0).getCreatedAt().isAfter(content.get(1).getCreatedAt()));
    }

    private void forcarCreatedAt(UUID id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE t_notificacao SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    /**
     * CR-01 (Phase 94 code review, iteration 2): {@code inserirSeNaoDuplicado} is a
     * {@code @Modifying} native query -- the JPA spec requires an active transaction for
     * {@code Query.executeUpdate()}, or it throws {@code TransactionRequiredException}.
     * {@code @DataJpaTest} wraps every test method in its own ambient transaction by default
     * (rolled back afterwards), which would mask a missing {@code @Transactional} on the
     * repository method itself: every other test in this class runs inside that ambient
     * transaction and would pass regardless of whether the fix is present. This test explicitly
     * disables that ambient transaction via {@code propagation = Propagation.NOT_SUPPORTED},
     * mirroring the real call paths that have NO ambient transaction of their own
     * (the entirety of {@code AlertasDiariosJob}, {@code ResourceController.createProcesso}/
     * {@code createProcessoFase}/{@code uploadDocumento}) -- proving {@code inserirSeNaoDuplicado}
     * supplies its own transaction rather than depending on one from the caller. Without the
     * {@code @Transactional} fix on the repository method, this test fails with
     * {@code TransactionRequiredException} instead of asserting successfully.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void inserirSeNaoDuplicado_semTransacaoAmbienteDoChamador_insereComSucesso() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        int linhasInseridas = notificacaoRepository.inserirSeNaoDuplicado(id, tenantId, destinatarioId,
                "FASE_ENTRADA", "processo", "id-sem-tx-ambiente", "Título", "Mensagem", "/link", createdAt);

        assertEquals(1, linhasInseridas);
        // Sem transação de teste a fazer rollback automático (NOT_SUPPORTED suspendeu-a) --
        // limpar explicitamente para não deixar a linha residual no contentor Postgres partilhado
        // pelos restantes testes desta classe.
        notificacaoRepository.deleteById(id);
    }

    /**
     * Complementa o teste acima provando o segundo ramo do contrato (ON CONFLICT DO NOTHING)
     * sob as mesmas condições de ausência de transação ambiente: uma segunda chamada para o
     * mesmo tuplo de dedup devolve 0 linhas inseridas, nunca lança exceção.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void inserirSeNaoDuplicado_semTransacaoAmbienteDoChamador_duplicadoDevolveZeroLinhas() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        UUID primeiroId = UUID.randomUUID();
        UUID segundoId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        int primeiraInsercao = notificacaoRepository.inserirSeNaoDuplicado(primeiroId, tenantId, destinatarioId,
                "FASE_ENTRADA", "processo", "id-sem-tx-dup", "Título", "Mensagem", "/link", createdAt);
        int segundaInsercao = notificacaoRepository.inserirSeNaoDuplicado(segundoId, tenantId, destinatarioId,
                "FASE_ENTRADA", "processo", "id-sem-tx-dup", "Título", "Mensagem", "/link", createdAt);

        assertEquals(1, primeiraInsercao);
        assertEquals(0, segundaInsercao);
        notificacaoRepository.deleteById(primeiroId);
    }

    /**
     * NOTF-26 (Plan 96-02): prova, contra Postgres real, que
     * {@code countByTenantIdAndDestinatarioIdAndLidaFalse} (fonte do contador/badge do sino)
     * esconde uma linha adiada para o futuro e continua a contar tanto a linha nunca adiada
     * (snoozedUntil nulo) como a linha cujo snooze já expirou (snoozedUntil no passado) --
     * provando simultaneamente o critério 2 (desaparece enquanto adiada) e o critério 3
     * (reaparece automaticamente uma vez ultrapassado o snoozedUntil, sem qualquer intervenção
     * do job diário) numa única asserção.
     */
    @Test
    void countByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_contaNuncaAdiadaEAdiadaJaExpirada() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();

        persistirComSnooze(tenantId, destinatarioId, "FASE_ENTRADA", "id-snooze-nula", false, null);
        persistirComSnooze(tenantId, destinatarioId, "FASE_ENTRADA", "id-snooze-futuro", false, agora.plusDays(3));
        persistirComSnooze(tenantId, destinatarioId, "FASE_ENTRADA", "id-snooze-passado", false, agora.minusHours(1));

        long naoLidas = notificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse(tenantId, destinatarioId, agora);

        assertEquals(2, naoLidas);
    }

    /**
     * NOTF-26 (Plan 96-02): prova que {@code findByTenantIdAndDestinatarioIdAndLidaFalse} (a
     * origem de dados de {@code marcarTodasLidas}) também exclui uma linha adiada para o futuro
     * -- garantindo que "marcar todas como lidas" nunca marca essa linha como lida, o que é o
     * que permite que ela reapareça como não lida assim que o snooze expirar (critério 3, lado
     * da escrita).
     */
    @Test
    void findByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_devolveApenasNuncaAdiada() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();

        Notificacao nuncaAdiada = persistirComSnooze(tenantId, destinatarioId, "FASE_ENTRADA", "id-marcar-nula", false, null);
        persistirComSnooze(tenantId, destinatarioId, "FASE_ENTRADA", "id-marcar-futuro", false, agora.plusDays(3));

        List<Notificacao> naoLidas = notificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse(tenantId, destinatarioId, agora);

        assertEquals(1, naoLidas.size());
        assertEquals(nuncaAdiada.getId(), naoLidas.get(0).getId());
    }
}
