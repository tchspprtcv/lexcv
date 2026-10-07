package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscalXml;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 136 (DFE-02): satélite XML de cada documento fiscal. INSERT-ONLY: não há save, update nem
 * delete (fixado por {@code DocumentoFiscalImutabilidadeTest}).
 *
 * <p>{@link #inserirSeAusente} é um INSERT nativo que ignora conflitos, sem alvo, por isso
 * cobre as duas chaves únicas (documento e IUD): devolve 1 se a linha foi escrita e 0 se já
 * existia. Um reprocessamento reutiliza sempre a linha existente (mesmo IUD, mesmos bytes).
 */
public interface DocumentoFiscalXmlRepository extends Repository<DocumentoFiscalXml, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente,
                                                repositorio_codigo, led_codigo, versao_formato, xml,
                                                xml_sha256, gerado_em)
            VALUES (:id, :tenantId, :documentoFiscalId, :iud, :ambiente,
                    :repositorioCodigo, :ledCodigo, :versaoFormato, :xml,
                    :xmlSha256, :geradoEm)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int inserirSeAusente(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                         @Param("documentoFiscalId") UUID documentoFiscalId, @Param("iud") String iud,
                         @Param("ambiente") String ambiente, @Param("repositorioCodigo") int repositorioCodigo,
                         @Param("ledCodigo") int ledCodigo, @Param("versaoFormato") String versaoFormato,
                         @Param("xml") String xml, @Param("xmlSha256") String xmlSha256,
                         @Param("geradoEm") Instant geradoEm);

    Optional<DocumentoFiscalXml> findByTenantIdAndDocumentoFiscalId(UUID tenantId, UUID documentoFiscalId);

    /** Phase 137 (RELF-01): XML (e IUD) de vários documentos do tenant de uma vez, para o CSV mensal. */
    List<DocumentoFiscalXml> findByTenantIdAndDocumentoFiscalIdIn(UUID tenantId, Collection<UUID> documentoFiscalIds);
}
