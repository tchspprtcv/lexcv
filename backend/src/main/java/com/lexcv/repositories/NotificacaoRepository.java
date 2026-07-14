package com.lexcv.repositories;

import com.lexcv.models.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    // First use of Spring Data Pageable/Page in this backend — deliberately scoped to the
    // /notificacoes history endpoint only (bell/unread-count stay on simpler List/count shapes).
    // tenant_id AND destinatario_id are non-optional predicates (never CAST-null-guarded) —
    // this is the first per-recipient-private resource in the codebase, so every finder must
    // carry both scoping dimensions. Only categoria/lida are optional filters, wrapped in
    // CAST(:param AS type) because PostgreSQL cannot infer the type of a bare null bind inside
    // "(:param IS NULL OR ...)" (same idiom as ParecerSolicitacaoRepository.pesquisar).
    @Query(value = "SELECT n.* FROM t_notificacao n " +
            "WHERE n.tenant_id = :tenantId " +
            "AND n.destinatario_id = :destinatarioId " +
            "AND (CAST(:categoria AS text) IS NULL OR n.categoria = CAST(:categoria AS text)) " +
            "AND (CAST(:lida AS boolean) IS NULL OR n.lida = CAST(:lida AS boolean)) " +
            "ORDER BY n.created_at DESC",
            countQuery = "SELECT count(*) FROM t_notificacao n " +
                    "WHERE n.tenant_id = :tenantId " +
                    "AND n.destinatario_id = :destinatarioId " +
                    "AND (CAST(:categoria AS text) IS NULL OR n.categoria = CAST(:categoria AS text)) " +
                    "AND (CAST(:lida AS boolean) IS NULL OR n.lida = CAST(:lida AS boolean))",
            nativeQuery = true)
    Page<Notificacao> buscarPorFiltros(@Param("tenantId") UUID tenantId,
                                        @Param("destinatarioId") UUID destinatarioId,
                                        @Param("categoria") String categoria,
                                        @Param("lida") Boolean lida,
                                        Pageable pageable);

    // Feeds the unread-count endpoint (bell badge).
    long countByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);

    // Feeds the "mark all read" load-mutate-saveAll path — no @Modifying bulk update
    // (the codebase has zero @Modifying queries, keep it that way).
    List<Notificacao> findByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);

    // Feeds single mark-read: empty for a row owned by a different destinatario in the same
    // tenant is what lets the controller answer 404 (not 403) without leaking existence across
    // the recipient boundary.
    Optional<Notificacao> findByIdAndTenantIdAndDestinatarioId(UUID id, UUID tenantId, UUID destinatarioId);

    // Feeds the daily alertas job's (Plan 88-02) edge-triggered idempotency check: called before
    // every criar(...) to skip creating a notification that already exists for this exact
    // (tenant, recipient, entity, categoria) tuple.
    boolean existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
            UUID tenantId, UUID destinatarioId, String entidadeTipo, String entidadeId, String categoria);

    // CR-01 (Phase 94 code review): atomic native upsert backing NotificacaoService.criar(),
    // mirroring NotificacaoPreferenciaRepository.upsertSilenciar (Phase 93 CR-01, same bug
    // class). A plain save()/saveAndFlush() + catch(DataIntegrityViolationException) does NOT
    // protect the caller's ambient @Transactional business transaction here: Postgres aborts
    // the ENTIRE transaction the instant any statement violates a constraint, and this method
    // is invoked from NotificacaoService.criarComFanOutAdmin() via self-invocation
    // (this.criar(...) within the same class) -- which bypasses Spring's proxy-based
    // @Transactional/propagation advice entirely, so neither a try/catch nor a
    // @Transactional(REQUIRES_NEW) annotation on criar() actually isolates a failure from the
    // ambient transaction on the real call paths this guards (ResourceController
    // .atribuirResponsavel, ParecerController.createSolicitacao/atribuirAdvogado). ON CONFLICT
    // DO NOTHING sidesteps the problem at the SQL level instead: on the "duplicate" path, no
    // constraint violation -- and therefore no exception -- is ever raised, so there is nothing
    // for the ambient transaction to abort on. The id and created_at are generated in Java
    // (NotificacaoService.criar()), not via gen_random_uuid()/now() in SQL, so the entity
    // returned to the caller on the success path is byte-for-byte consistent with what was
    // persisted. Returns the number of rows actually inserted (0 = skipped as a duplicate of
    // uk_notificacao_dedup, 1 = inserted) so criar() can report back through its existing
    // Optional<Notificacao> contract without an extra SELECT-after-INSERT round trip.
    //
    // CR-01 (Phase 94 code review, iteration 2): @Transactional added directly on THIS
    // repository method -- @Modifying queries are required by the JPA spec to run inside an
    // active transaction (Query.executeUpdate() throws TransactionRequiredException
    // otherwise), and Spring Data JPA does not synthesize a fallback transaction for a custom
    // @Query method with no @Transactional anywhere on its declaration. Most real callers
    // (ResourceController.createProcesso/createProcessoFase/uploadDocumento, the entirety of
    // AlertasDiariosJob) have NO ambient transaction, so without this annotation every one of
    // those call paths threw TransactionRequiredException at runtime. The annotation is placed
    // here -- not on NotificacaoService.criar()/criarComFanOutAdmin() -- specifically because
    // criarComFanOutAdmin() invokes criar() via self-invocation (this.criar(...) within the
    // same class), which bypasses Spring's proxy-based @Transactional advice entirely; a
    // repository method call, in contrast, always goes through Spring Data's own dedicated
    // proxy, so @Transactional here takes effect regardless of how the caller reached this
    // method. Default propagation (REQUIRED) joins an already-open ambient transaction where
    // one exists (atribuirResponsavel, ParecerController) and opens its own short-lived one
    // otherwise (createProcesso, createProcessoFase, uploadDocumento, AlertasDiariosJob).
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO t_notificacao (id, tenant_id, destinatario_id, categoria, entidade_tipo, entidade_id,
                                        titulo, mensagem, link_url, lida, created_at)
            VALUES (:id, :tenantId, :destinatarioId, :categoria, :entidadeTipo, :entidadeId,
                    :titulo, :mensagem, :linkUrl, false, :createdAt)
            ON CONFLICT (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria) DO NOTHING
            """, nativeQuery = true)
    int inserirSeNaoDuplicado(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                               @Param("destinatarioId") UUID destinatarioId, @Param("categoria") String categoria,
                               @Param("entidadeTipo") String entidadeTipo, @Param("entidadeId") String entidadeId,
                               @Param("titulo") String titulo, @Param("mensagem") String mensagem,
                               @Param("linkUrl") String linkUrl, @Param("createdAt") LocalDateTime createdAt);
}
