package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscalLinha;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Phase 134 (EMIS-08, D-07): repositório ESTREITO das linhas dos documentos fiscais -- só
 * inserir e ler, sempre por tenant. Conjunto de métodos fixado por
 * {@code DocumentoFiscalImutabilidadeTest}.
 */
public interface DocumentoFiscalLinhaRepository extends Repository<DocumentoFiscalLinha, UUID> {

    DocumentoFiscalLinha save(DocumentoFiscalLinha linha);

    List<DocumentoFiscalLinha> findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(UUID tenantId,
                                                                                     UUID documentoFiscalId);
}
