package com.lexcv.repositories;

import com.lexcv.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    List<Cliente> findByTenantId(UUID tenantId);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCase(UUID tenantId, String nome);
    List<Cliente> findByTenantIdAndNif(UUID tenantId, String nif);
    List<Cliente> findByTenantIdAndNomeContainingIgnoreCaseAndNif(UUID tenantId, String nome, String nif);
    Optional<Cliente> findByTenantIdAndDocumentoNumero(UUID tenantId, String documentoNumero);

    @Query("SELECT MAX(c.numeroSequencial) FROM Cliente c WHERE c.tenantId = :tenantId")
    Optional<Integer> findMaxNumeroSequencialByTenantId(@Param("tenantId") UUID tenantId);
}
