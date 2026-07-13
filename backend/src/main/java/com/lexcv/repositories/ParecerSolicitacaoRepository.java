package com.lexcv.repositories;

import com.lexcv.models.ParecerSolicitacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParecerSolicitacaoRepository extends JpaRepository<ParecerSolicitacao, UUID> {
    List<ParecerSolicitacao> findByTenantId(UUID tenantId);

    // WR-02 (Phase 90 code review, iteration 3): used by ResourceController.mergeClientes to
    // migrate clienteId off the secondary client before it is deleted, tenant-scoped for
    // isolation consistency with every other finder in this class.
    List<ParecerSolicitacao> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);

    // WR-04 (Phase 87 code review, iteration 3): row-level PESSIMISTIC_WRITE lock, held for
    // the duration of the caller's @Transactional method (same convention as
    // SystemSettingRepository.findByIdForUpdate / SetupService.initializeSystem) -- used by
    // ParecerController.createVersao so the numeroVersao increment-and-insert is atomic across
    // concurrent requests for the same solicitacaoId, not just within one JVM.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParecerSolicitacao s where s.id = :id")
    Optional<ParecerSolicitacao> findByIdForUpdate(@Param("id") UUID id);

    // T-64-04/T-64-05: all filters bound via @Param (never string-concatenated); WHERE always
    // starts with s.tenant_id = :tenantId for tenant isolation. ILIKE requires nativeQuery = true
    // (Postgres-specific, not portable JPQL). The correlated MAX(numero_versao) subquery restricts
    // the text match to each solicitacao's most recent version only, avoiding duplicate rows.
    // CR-01: LEFT JOIN (not INNER JOIN) — a solicitacao with zero versions (e.g. newly created,
    // PENDENTE) must still match when texto is null; it naturally won't match when texto is
    // provided since there's no conteudo to search against.
    // Every nullable param is wrapped in an explicit CAST — PostgreSQL cannot infer the type of
    // a bare null bind inside "(:param IS NULL OR ...)" and fails with "could not determine data
    // type of parameter" at runtime.
    @Query(value = "SELECT s.* FROM t_parecer_solicitacao s " +
            "LEFT JOIN t_parecer_versao v ON v.solicitacao_id = s.id " +
            "AND v.numero_versao = (SELECT MAX(v2.numero_versao) FROM t_parecer_versao v2 WHERE v2.solicitacao_id = s.id) " +
            "WHERE s.tenant_id = :tenantId " +
            "AND (CAST(:clienteId AS uuid) IS NULL OR s.cliente_id = CAST(:clienteId AS uuid)) " +
            "AND (CAST(:advogadoId AS uuid) IS NULL OR s.advogado_id = CAST(:advogadoId AS uuid)) " +
            "AND (CAST(:status AS text) IS NULL OR s.status = CAST(:status AS text)) " +
            "AND (CAST(:dataInicio AS timestamp) IS NULL OR s.created_at >= CAST(:dataInicio AS timestamp)) " +
            "AND (CAST(:dataFim AS timestamp) IS NULL OR s.created_at <= CAST(:dataFim AS timestamp)) " +
            "AND (CAST(:texto AS text) IS NULL OR v.conteudo ILIKE '%' || CAST(:texto AS text) || '%')",
            nativeQuery = true)
    List<ParecerSolicitacao> pesquisar(@Param("tenantId") UUID tenantId,
                                        @Param("texto") String texto,
                                        @Param("clienteId") UUID clienteId,
                                        @Param("advogadoId") UUID advogadoId,
                                        @Param("status") String status,
                                        @Param("dataInicio") LocalDateTime dataInicio,
                                        @Param("dataFim") LocalDateTime dataFim);
}
