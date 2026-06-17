package com.lexcv.repositories;

import com.lexcv.models.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {
    List<Documento> findByTenantId(UUID tenantId);
    List<Documento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
    List<Documento> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);
}
