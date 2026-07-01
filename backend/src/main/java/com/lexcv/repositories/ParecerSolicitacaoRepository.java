package com.lexcv.repositories;

import com.lexcv.models.ParecerSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ParecerSolicitacaoRepository extends JpaRepository<ParecerSolicitacao, UUID> {
    List<ParecerSolicitacao> findByTenantId(UUID tenantId);

    // T-64-04/T-64-05: all filters bound via @Param (never string-concatenated); WHERE always
    // starts with s.tenant_id = :tenantId for tenant isolation. ILIKE requires nativeQuery = true
    // (Postgres-specific, not portable JPQL). The correlated MAX(numero_versao) subquery restricts
    // the text match to each solicitacao's most recent version only, avoiding duplicate rows.
    @Query(value = "SELECT s.* FROM t_parecer_solicitacao s " +
            "JOIN t_parecer_versao v ON v.solicitacao_id = s.id " +
            "AND v.numero_versao = (SELECT MAX(v2.numero_versao) FROM t_parecer_versao v2 WHERE v2.solicitacao_id = s.id) " +
            "WHERE s.tenant_id = :tenantId " +
            "AND (:clienteId IS NULL OR s.cliente_id = :clienteId) " +
            "AND (:advogadoId IS NULL OR s.advogado_id = :advogadoId) " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:dataInicio IS NULL OR s.created_at >= :dataInicio) " +
            "AND (:dataFim IS NULL OR s.created_at <= :dataFim) " +
            "AND (:texto IS NULL OR v.conteudo ILIKE '%' || :texto || '%')",
            nativeQuery = true)
    List<ParecerSolicitacao> pesquisar(@Param("tenantId") UUID tenantId,
                                        @Param("texto") String texto,
                                        @Param("clienteId") UUID clienteId,
                                        @Param("advogadoId") UUID advogadoId,
                                        @Param("status") String status,
                                        @Param("dataInicio") LocalDateTime dataInicio,
                                        @Param("dataFim") LocalDateTime dataFim);
}
