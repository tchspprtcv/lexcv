package com.lexcv.repositories;

import com.lexcv.models.ClienteNota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClienteNotaRepository extends JpaRepository<ClienteNota, UUID> {
    List<ClienteNota> findByTenantIdAndClienteIdOrderByUpdatedAtDesc(UUID tenantId, UUID clienteId);
}

