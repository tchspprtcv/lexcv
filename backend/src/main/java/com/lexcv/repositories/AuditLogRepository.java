package com.lexcv.repositories;

import com.lexcv.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

// Phase 128 (AUDT-04), Decision 6: extends the bare Spring Data Commons marker interface
// Repository<AuditLog, Long> -- NOT JpaRepository, NOT CrudRepository, NOT
// PagingAndSortingRepository. Every other repository in this codebase extends JpaRepository;
// this is the first (and, by design, only) narrowed repository. The reason is structural, not
// stylistic: JpaRepository brings delete/deleteAll/deleteById/saveAll/saveAndFlush on existing
// rows, and any future code path could call one of them on an append-only audit log. Extending
// Repository<T, ID> instead means those methods simply do not exist as callable Java methods
// -- a compile error, not a discipline problem. Spring Data JPA still detects and proxies the
// three methods declared below (query derivation for findByTenantId..., @Query for
// buscarEventosRbac) exactly as it would under JpaRepository; only the free inherited
// convenience methods disappear from the compiled API.
//
// Full caller inventory (128-PATTERNS.md, verified by grep across backend/src, main AND test):
//   - save(AuditLog): ParecerController (5 sites: :188,345,394,448,572) and ResourceController
//     (5 sites: :1168,1430,1629,2927,2972)
//   - findByTenantIdAndProcessoIdOrderByTimestampDesc: ResourceController.getAuditLog (the
//     GET /processos/{id}/audit endpoint)
//   - save(any()) stubs (Mockito @Mock): ParecerControllerEntregaProveniencaTest,
//     ParecerControllerProveniencaPapelTest, ResourceControllerProveniencaPapelTest,
//     ResourceControllerUploadDocumentoTest
// No caller anywhere uses findAll/findById/saveAll/delete*/count or any other inherited
// JpaRepository method -- confirmed by grep, not assumed.
//
// The method set below is pinned by AuditLogImutabilidadeTest (Test 2): it asserts
// getMethods() equals exactly {save, findByTenantIdAndProcessoIdOrderByTimestampDesc,
// buscarEventosRbac}. Adding a method here requires updating that test deliberately -- the
// test failing because of a legitimate new read method is the intended way to notice and
// review the change, never a reason to loosen the assertion.
//
// This is the SECOND use of Spring Data Pageable/Page in this codebase, after
// NotificacaoRepository.buscarPorFiltros (the native @Query + paired countQuery idiom copied
// verbatim below).
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);

    // Phase 128 (AUDT-01/AUDT-02/AUDT-03/AUDT-05), Decision 5: tenant-scoped, paginated finder
    // for the RBAC audit query endpoint (Plan 07). Restricted to the two RBAC entidade_tipo
    // values -- processo/parecer events already have their own view and must never leak into
    // this one. Both optional filters use the CAST(:param AS text) IS NULL OR ... idiom (same as
    // NotificacaoRepository.buscarPorFiltros / ParecerSolicitacaoRepository.pesquisar) because
    // PostgreSQL cannot infer the type of a bare null bind. utilizadorAlvoId matches only
    // atribuicao_papel rows (entidade_id = target User.id). papelId matches by role ID, never by
    // name, on BOTH entidade_tipo values: directly for papel_escritorio rows (entidade_id =
    // TenantRole.id) and via the detalhe->>'papelId' JSON key for atribuicao_papel rows (which
    // record the target user's id in entidade_id, not the role's) -- this is what lets a query
    // for a role find its assignment events too, and keeps working even if the role is renamed
    // later, reconciling the UI-SPEC's name-based filter with an id-stable backend query (see
    // Plan 08). ORDER BY timestamp DESC, id DESC gives a stable order for same-instant events.
    @Query(value = "SELECT a.* FROM t_audit_log a " +
            "WHERE a.tenant_id = :tenantId " +
            "AND a.entidade_tipo IN ('papel_escritorio', 'atribuicao_papel') " +
            "AND (CAST(:utilizadorAlvoId AS text) IS NULL OR " +
            "     (a.entidade_tipo = 'atribuicao_papel' AND a.entidade_id = CAST(:utilizadorAlvoId AS text))) " +
            "AND (CAST(:papelId AS text) IS NULL OR " +
            "     (a.entidade_tipo = 'papel_escritorio' AND a.entidade_id = CAST(:papelId AS text)) OR " +
            "     (a.entidade_tipo = 'atribuicao_papel' AND CAST(a.detalhe AS jsonb) ->> 'papelId' = CAST(:papelId AS text))) " +
            "ORDER BY a.timestamp DESC, a.id DESC",
            countQuery = "SELECT count(*) FROM t_audit_log a " +
                    "WHERE a.tenant_id = :tenantId " +
                    "AND a.entidade_tipo IN ('papel_escritorio', 'atribuicao_papel') " +
                    "AND (CAST(:utilizadorAlvoId AS text) IS NULL OR " +
                    "     (a.entidade_tipo = 'atribuicao_papel' AND a.entidade_id = CAST(:utilizadorAlvoId AS text))) " +
                    "AND (CAST(:papelId AS text) IS NULL OR " +
                    "     (a.entidade_tipo = 'papel_escritorio' AND a.entidade_id = CAST(:papelId AS text)) OR " +
                    "     (a.entidade_tipo = 'atribuicao_papel' AND CAST(a.detalhe AS jsonb) ->> 'papelId' = CAST(:papelId AS text)))",
            nativeQuery = true)
    Page<AuditLog> buscarEventosRbac(@Param("tenantId") UUID tenantId,
                                      @Param("utilizadorAlvoId") String utilizadorAlvoId,
                                      @Param("papelId") String papelId,
                                      Pageable pageable);
}
