package com.lexcv.repositories;

import com.lexcv.models.Processo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessoRepository extends JpaRepository<Processo, UUID> {
    List<Processo> findByTenantId(UUID tenantId);
    List<Processo> findByClienteId(UUID clienteId);
}
