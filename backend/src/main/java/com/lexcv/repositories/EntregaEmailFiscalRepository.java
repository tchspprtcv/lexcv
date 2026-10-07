package com.lexcv.repositories;

import com.lexcv.models.EntregaEmailFiscal;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-03, ENTR-04): leituras do satélite mutável da entrega por email, sempre por
 * tenant. Não há {@code save}, update nem delete aqui: todas as escritas (criar a linha, reclamar
 * com lease, registar tentativa, sucesso, falha, reenvio) passam por SQL nativo em
 * {@code FilaEntregaEmail} (137-07). Conjunto de métodos fixado por
 * {@code DocumentoFiscalImutabilidadeTest}.
 */
public interface EntregaEmailFiscalRepository extends Repository<EntregaEmailFiscal, UUID> {

    Optional<EntregaEmailFiscal> findByTenantIdAndDocumentoFiscalId(UUID tenantId, UUID documentoFiscalId);

    /** Estados de entrega de vários documentos do tenant (listagem, CSV). */
    List<EntregaEmailFiscal> findByTenantIdAndDocumentoFiscalIdIn(UUID tenantId, Collection<UUID> ids);
}
