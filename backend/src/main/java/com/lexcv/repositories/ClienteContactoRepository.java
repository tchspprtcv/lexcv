package com.lexcv.repositories;

import com.lexcv.models.ClienteContacto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClienteContactoRepository extends JpaRepository<ClienteContacto, UUID> {
    List<ClienteContacto> findByTenantIdAndClienteIdOrderByCreatedAtAsc(UUID tenantId, UUID clienteId);
}

