package com.lexcv.repositories;

import com.lexcv.models.ConflictCheckDecisao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConflictCheckDecisaoRepository extends JpaRepository<ConflictCheckDecisao, UUID> {
    List<ConflictCheckDecisao> findByTenantId(UUID tenantId);
    Optional<ConflictCheckDecisao> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
}
