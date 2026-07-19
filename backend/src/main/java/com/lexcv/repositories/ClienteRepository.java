package com.lexcv.repositories;

import com.lexcv.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    List<Cliente> findByTenantId(UUID tenantId);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome);
    List<Cliente> findByTenantIdAndNif(UUID tenantId, String nif);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCaseAndNif(UUID tenantId, String nome, String nif);
    Optional<Cliente> findByTenantIdAndDocumentoNumero(UUID tenantId, String documentoNumero);

    @Query("SELECT MAX(c.numeroSequencial) FROM Cliente c WHERE c.tenantId = :tenantId")
    Optional<Integer> findMaxNumeroSequencialByTenantId(@Param("tenantId") UUID tenantId);

    // Phase 111 (SRCH-02/SRCH-07): tenant-first, accent-folded, ranked, LIMIT-capped
    // quick-search for GET /api/v1/pesquisa (PesquisaController, Plan 111-02).
    // tenant_id = :tenantId is always the first, non-optional predicate — never
    // CAST-guarded, never skippable (PITFALLS.md Pitfall 1). termo/termoEscapado are bound
    // via @Param and wildcard-wrapped INSIDE the SQL string as
    // '%' || CAST(:termoEscapado AS text) || '%' — never Java string-concatenated
    // (SQLi surface + ASVS L1 gate), mirroring ParecerSolicitacaoRepository.pesquisar().
    // Requires the unaccent extension (backend/migrations/111-enable-search-extensions.sql)
    // to be applied first, or unaccent(...) calls fail at runtime.
    // WR-02: two separate bind params carry the search term. termoEscapado has ILIKE's
    // wildcard metacharacters (%, _, \) pre-escaped by PesquisaController#escapeLike and is
    // used everywhere the term is wrapped in wildcards (paired with ESCAPE '\' so a literal
    // %/_ typed by the user is matched literally). termo is the original, unescaped value and
    // is used ONLY for the exact (=) match tiers below — escaping it would corrupt a literal
    // equality comparison against a stored numero_cliente/nif/documento_numero.
    // ORDER BY tiers: 0 = exact (case-insensitive) numero_cliente/nif/documento_numero;
    // 1 = prefix match on those three structured IDs; 2 = prefix match on unaccent(nome);
    // 3 = everything else (substring match), tie-broken by created_at DESC.
    @Query(value = "SELECT c.* FROM t_cliente c " +
            "WHERE c.tenant_id = :tenantId " +
            "AND (unaccent(c.nome) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
            "OR c.numero_cliente ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\' " +
            "OR c.nif ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\' " +
            "OR c.documento_numero ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\') " +
            "ORDER BY " +
            "CASE WHEN lower(c.numero_cliente) = lower(CAST(:termo AS text)) THEN 0 " +
            "WHEN lower(c.nif) = lower(CAST(:termo AS text)) THEN 0 " +
            "WHEN lower(c.documento_numero) = lower(CAST(:termo AS text)) THEN 0 " +
            "WHEN c.numero_cliente ILIKE CAST(:termoEscapado AS text) || '%' ESCAPE '\\' THEN 1 " +
            "WHEN c.nif ILIKE CAST(:termoEscapado AS text) || '%' ESCAPE '\\' THEN 1 " +
            "WHEN c.documento_numero ILIKE CAST(:termoEscapado AS text) || '%' ESCAPE '\\' THEN 1 " +
            "WHEN unaccent(c.nome) ILIKE unaccent(CAST(:termoEscapado AS text) || '%') ESCAPE '\\' THEN 2 " +
            "ELSE 3 END, " +
            "c.created_at DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Cliente> pesquisarGlobal(@Param("tenantId") UUID tenantId, @Param("termo") String termo,
                                   @Param("termoEscapado") String termoEscapado, @Param("limit") int limit);
}
