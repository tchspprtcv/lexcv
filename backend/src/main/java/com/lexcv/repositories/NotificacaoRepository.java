package com.lexcv.repositories;

import com.lexcv.models.Notificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
