package com.lexcv.repositories;

import com.lexcv.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);
}
