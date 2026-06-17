package com.lexcv.repositories;

import com.lexcv.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    List<Cliente> findByTenantId(UUID tenantId);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome);
    List<Cliente> findByTenantIdAndNif(UUID tenantId, String nif);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCaseAndNif(UUID tenantId, String nome, String nif);
}
