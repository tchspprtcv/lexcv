package com.lexcv.repositories;

import com.lexcv.models.NotificacaoPreferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificacaoPreferenciaRepository extends JpaRepository<NotificacaoPreferencia, UUID> {

    // Read contract for the criar() mute guard (Plan 93-02). Dual-scoped by
    // tenant_id + user_id to prevent cross-tenant/cross-user preference leaks
    // (Pitfall 10).
    boolean existsByTenantIdAndUserIdAndCategoria(UUID tenantId, UUID userId, String categoria);

    // Feeds the GET listing endpoint (Plan 93-03) and the service's
    // listarCategoriasSilenciadas method (Plan 93-02).
    List<NotificacaoPreferencia> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    // Derived delete used to "re-activate" a category; called from a
    // @Transactional service method (Plan 93-02). No @Modifying/@Query --
    // derived delete is the established convention in this codebase.
    void deleteByTenantIdAndUserIdAndCategoria(UUID tenantId, UUID userId, String categoria);

    // CR-01 (Phase 93 code review, iteration 3): atomic native upsert backing
    // NotificacaoService.silenciarCategoria(). Replaces the prior
    // existsBy...+saveAndFlush(...)+catch(DataIntegrityViolationException) pattern, which
    // does not actually work against real PostgreSQL: Postgres aborts the ENTIRE
    // transaction as soon as any single statement violates a constraint, and
    // silenciarCategoria() is itself the outermost @Transactional boundary, so catching the
    // translated exception locally cannot stop the subsequent implicit COMMIT from failing
    // (or silently no-op'ing) against the already-poisoned transaction. ON CONFLICT DO
    // NOTHING sidesteps the whole problem: on the "already silenced" path, no constraint
    // violation -- and therefore no exception -- is ever raised at the SQL level. This
    // single statement also IS the idempotency check, making the old existsBy... pre-check
    // redundant. This is the first @Modifying query in the codebase (see the now-stale
    // "no @Modifying" comments on NotificacaoRepository/this repository's derived delete) --
    // introduced deliberately here because a derived/JPQL upsert cannot express
    // ON CONFLICT DO NOTHING; a native query is required.
    @Modifying
    @Query(value = """
            INSERT INTO t_notificacao_preferencia (id, tenant_id, user_id, categoria, created_at)
            VALUES (gen_random_uuid(), :tenantId, :userId, :categoria, now())
            ON CONFLICT (tenant_id, user_id, categoria) DO NOTHING
            """, nativeQuery = true)
    void upsertSilenciar(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                          @Param("categoria") String categoria);
}
