package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 134 (EMIS-08, D-07, D-17): repositório ESTREITO dos documentos fiscais.
 *
 * <p>Estende apenas o marcador {@link Repository} (padrão de {@code AuditLogRepository}) -- nunca
 * as interfaces de repositório completas do Spring Data nem o executor de especificações (este
 * último declara {@code delete(Specification)} no Spring Data 3.4). Um documento fiscal só pode ser
 * inserido e lido: delete/update não existem como métodos Java, é um erro de compilação e não
 * uma questão de disciplina. A única escrita além do INSERT é o re-apontamento de
 * {@code cliente_id} na fusão, isolado em {@link DocumentoFiscalLigacaoClienteRepository}.
 *
 * <p>Todos os finders recebem {@code tenantId} como primeiro argumento (isolamento entre
 * escritórios). O conjunto de métodos está fixado por {@code DocumentoFiscalImutabilidadeTest}:
 * acrescentar um método exige atualizar esse teste deliberadamente.
 */
public interface DocumentoFiscalRepository extends Repository<DocumentoFiscal, UUID> {

    DocumentoFiscal save(DocumentoFiscal documento);

    Optional<DocumentoFiscal> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DocumentoFiscal> findByTenantIdAndChaveIdempotencia(UUID tenantId, UUID chaveIdempotencia);

    List<DocumentoFiscal> findByTenantIdAndPagamentoIdIn(UUID tenantId, Collection<Integer> pagamentoIds);

    boolean existsByTenantIdAndPagamentoId(UUID tenantId, Integer pagamentoId);

    boolean existsByTenantIdAndClienteId(UUID tenantId, UUID clienteId);

    boolean existsByTenantIdAndProcessoId(UUID tenantId, UUID processoId);

    boolean existsByTenantIdAndHonorarioId(UUID tenantId, Integer honorarioId);

    /**
     * Listagem filtrada e paginada no servidor (D-17). {@code tenant_id} é o primeiro predicado,
     * nunca opcional. Os filtros opcionais usam o idioma {@code CAST(:p AS text) IS NULL OR ...}
     * (o PostgreSQL não consegue tipar um null "nu"); o {@code clienteId} chega como String pelo
     * mesmo motivo. O filtro de estado usa um LEFT JOIN ao satélite {@code t_comunicacao_fiscal}
     * (mesmo tenant): com o filtro ausente, documentos sem linha de comunicação continuam
     * incluídos. Tudo é ligado por {@code @Param}; nada é concatenado.
     */
    @Query(value = "SELECT d.* FROM t_documento_fiscal d "
            + "LEFT JOIN t_comunicacao_fiscal c ON c.documento_fiscal_id = d.id AND c.tenant_id = d.tenant_id "
            + "WHERE d.tenant_id = :tenantId "
            + "AND (CAST(:clienteId AS text) IS NULL OR d.cliente_id = CAST(CAST(:clienteId AS text) AS uuid)) "
            + "AND (CAST(:tipo AS text) IS NULL OR d.tipo = CAST(:tipo AS text)) "
            + "AND (CAST(:estado AS text) IS NULL OR c.estado = CAST(:estado AS text)) "
            + "AND (CAST(:de AS date) IS NULL OR d.data_emissao >= CAST(:de AS date)) "
            + "AND (CAST(:ate AS date) IS NULL OR d.data_emissao <= CAST(:ate AS date)) "
            + "ORDER BY d.data_emissao DESC, d.ano DESC, d.numero DESC",
            countQuery = "SELECT count(*) FROM t_documento_fiscal d "
                    + "LEFT JOIN t_comunicacao_fiscal c ON c.documento_fiscal_id = d.id AND c.tenant_id = d.tenant_id "
                    + "WHERE d.tenant_id = :tenantId "
                    + "AND (CAST(:clienteId AS text) IS NULL OR d.cliente_id = CAST(CAST(:clienteId AS text) AS uuid)) "
                    + "AND (CAST(:tipo AS text) IS NULL OR d.tipo = CAST(:tipo AS text)) "
                    + "AND (CAST(:estado AS text) IS NULL OR c.estado = CAST(:estado AS text)) "
                    + "AND (CAST(:de AS date) IS NULL OR d.data_emissao >= CAST(:de AS date)) "
                    + "AND (CAST(:ate AS date) IS NULL OR d.data_emissao <= CAST(:ate AS date))",
            nativeQuery = true)
    Page<DocumentoFiscal> buscar(@Param("tenantId") UUID tenantId,
                                 @Param("clienteId") String clienteId,
                                 @Param("tipo") String tipo,
                                 @Param("estado") String estado,
                                 @Param("de") LocalDate de,
                                 @Param("ate") LocalDate ate,
                                 Pageable pageable);
}
