package com.lexcv.repositories;

import com.lexcv.models.Processo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ProcessoRepository extends JpaRepository<Processo, UUID> {
    List<Processo> findByTenantId(UUID tenantId);
    List<Processo> findByClienteId(UUID clienteId);

    // Phase 111 (SRCH-02/SRCH-07): tenant-first, accent-folded, ranked, LIMIT-capped
    // quick-search for GET /api/v1/pesquisa (PesquisaController, Plan 111-02).
    // tenant_id = :tenantId is always the first, non-optional predicate. termo/termoEscapado
    // are bound via @Param and wildcard-wrapped INSIDE the SQL string, never Java
    // string-concatenated (SQLi surface + ASVS L1 gate). Do NOT reach for
    // findByClienteId(UUID) here — it takes no tenantId param (IDOR-adjacent per
    // PITFALLS.md Pitfall 1).
    // numero_processo has no uniqueness constraint/index (Processo.java) — the
    // exact-match tier is a full scan, accepted per the threat model (T-111-06):
    // current per-tenant row counts make this low-single-digit-ms.
    // WR-02: two separate bind params carry the search term, same convention as
    // ClienteRepository#pesquisarGlobal. termoEscapado has ILIKE's wildcard metacharacters
    // (%, _, \) pre-escaped by PesquisaController#escapeLike and is used everywhere the term
    // is wrapped in wildcards (paired with ESCAPE '\'). termo is the original, unescaped value
    // and is used ONLY for the exact (=) match tier below — escaping it would corrupt a
    // literal equality comparison against a stored numero_processo.
    // ORDER BY tiers: 0 = exact (case-insensitive) numero_processo; 1 = prefix match
    // on numero_processo; 2 = everything else (substring match on the accent-folded
    // free-text fields), tie-broken by created_at DESC.
    @Query(value = "SELECT p.* FROM t_processo p " +
            "WHERE p.tenant_id = :tenantId " +
            "AND (p.numero_processo ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\' " +
            "OR unaccent(p.descricao) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
            "OR unaccent(p.tribunal) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
            "OR unaccent(p.area_juridica) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
            "OR unaccent(p.tipo_processo) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\') " +
            "ORDER BY " +
            "CASE WHEN lower(p.numero_processo) = lower(CAST(:termo AS text)) THEN 0 " +
            "WHEN p.numero_processo ILIKE CAST(:termoEscapado AS text) || '%' ESCAPE '\\' THEN 1 " +
            "ELSE 2 END, " +
            "p.created_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Processo> pesquisarGlobal(@Param("tenantId") UUID tenantId, @Param("termo") String termo,
                                    @Param("termoEscapado") String termoEscapado, @Param("limit") int limit);
}
