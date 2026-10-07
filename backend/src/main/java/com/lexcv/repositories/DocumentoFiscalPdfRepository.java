package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscalPdf;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-01): registo do PDF de cada documento fiscal. INSERT-ONLY: não há save, update
 * nem delete (fixado por {@code DocumentoFiscalImutabilidadeTest}).
 *
 * <p>{@link #inserirSeAusente} é um INSERT nativo que ignora conflitos, sem alvo, por isso cobre as
 * duas chaves únicas (documento e {@code object_key}): devolve 1 se a linha foi escrita e 0 se já
 * existia -- nesse caso o PDF já registado é o que continua a ser servido.
 */
public interface DocumentoFiscalPdfRepository extends Repository<DocumentoFiscalPdf, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO t_documento_fiscal_pdf (id, tenant_id, documento_fiscal_id, object_key, sha256,
                                                tamanho_bytes, versao_modelo, gerado_em)
            VALUES (:id, :tenantId, :documentoFiscalId, :objectKey, :sha256,
                    :tamanhoBytes, :versaoModelo, :geradoEm)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                         @Param("documentoFiscalId") UUID documentoFiscalId, @Param("objectKey") String objectKey,
                         @Param("sha256") String sha256, @Param("tamanhoBytes") long tamanhoBytes,
                         @Param("versaoModelo") String versaoModelo, @Param("geradoEm") Instant geradoEm);

    Optional<DocumentoFiscalPdf> findByTenantIdAndDocumentoFiscalId(UUID tenantId, UUID documentoFiscalId);
}
