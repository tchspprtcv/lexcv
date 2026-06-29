package com.lexcv.repositories;

import com.lexcv.models.ClienteAdministrativo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteAdministrativoRepository extends JpaRepository<ClienteAdministrativo, UUID> {

    List<ClienteAdministrativo> findByClienteIdAndTenantId(UUID clienteId, UUID tenantId);

    Optional<ClienteAdministrativo> findByClienteIdAndUserIdAndTenantId(UUID clienteId, UUID userId, UUID tenantId);

    void deleteByClienteIdAndUserIdAndTenantId(UUID clienteId, UUID userId, UUID tenantId);
}
