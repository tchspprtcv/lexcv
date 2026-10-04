package com.lexcv.repositories;

import com.lexcv.models.ComunicacaoFiscal;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 134 (D-08): satélite de comunicação de cada documento fiscal. Repositório estreito (sem
 * delete): nesta fase só se cria a linha PENDENTE e se lê por tenant. A Phase 136 acrescenta os
 * seus próprios finders.
 */
public interface ComunicacaoFiscalRepository extends Repository<ComunicacaoFiscal, UUID> {

    ComunicacaoFiscal save(ComunicacaoFiscal comunicacao);

    Optional<ComunicacaoFiscal> findByTenantIdAndDocumentoFiscalId(UUID tenantId, UUID documentoFiscalId);

    List<ComunicacaoFiscal> findByTenantIdAndDocumentoFiscalIdIn(UUID tenantId, Collection<UUID> documentoFiscalIds);
}
