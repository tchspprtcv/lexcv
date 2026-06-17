package com.lexcv.repositories;

import com.lexcv.models.Prazo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PrazoRepository extends JpaRepository<Prazo, UUID> {
    List<Prazo> findByTenantIdAndProcessoIdOrderByDataLimiteAsc(UUID tenantId, UUID processoId);
    List<Prazo> findByTenantId(UUID tenantId);
}
