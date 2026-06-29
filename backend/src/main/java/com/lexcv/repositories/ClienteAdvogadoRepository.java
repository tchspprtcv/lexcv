package com.lexcv.repositories;

import com.lexcv.models.ClienteAdvogado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteAdvogadoRepository extends JpaRepository<ClienteAdvogado, UUID> {

    List<ClienteAdvogado> findByClienteIdAndTenantId(UUID clienteId, UUID tenantId);

    Optional<ClienteAdvogado> findByClienteIdAndUserIdAndTenantId(UUID clienteId, UUID userId, UUID tenantId);

    void deleteByClienteIdAndUserIdAndTenantId(UUID clienteId, UUID userId, UUID tenantId);
}
