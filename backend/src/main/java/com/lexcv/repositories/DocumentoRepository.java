package com.lexcv.repositories;

import com.lexcv.models.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
    List<Documento> findByTenantId(UUID tenantId);
    List<Documento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
    List<Documento> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);

    // Phase 111 (SRCH-02/SRCH-07): tenant-first, accent-folded, ranked, LIMIT-capped
    // quick-search for GET /api/v1/pesquisa (PesquisaController, Plan 111-02).
    // tenant_id = :tenantId is always the first, non-optional predicate. termo is
    // bound via @Param and wildcard-wrapped INSIDE the SQL string, never Java
    // string-concatenated (SQLi surface + ASVS L1 gate).
    // Searches nome/tipo metadata ONLY — never caminho_arquivo (a MinIO object key,
    // not text content); Documento has no structured-ID field to rank ahead of these.
    // ORDER BY tiers: 0 = prefix match on unaccent(nome); 1 = everything else
    // (substring match), tie-broken by created_at DESC.
    @Query(value = "SELECT d.* FROM t_documento d " +
            "WHERE d.tenant_id = :tenantId " +
            "AND (unaccent(d.nome) ILIKE unaccent('%' || CAST(:termo AS text) || '%') " +
            "OR unaccent(d.tipo) ILIKE unaccent('%' || CAST(:termo AS text) || '%')) " +
            "ORDER BY " +
            "CASE WHEN unaccent(d.nome) ILIKE unaccent(CAST(:termo AS text) || '%') THEN 0 " +
            "ELSE 1 END, " +
            "d.created_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Documento> pesquisarGlobal(@Param("tenantId") UUID tenantId, @Param("termo") String termo, @Param("limit") int limit);
}
