package com.lexcv.repositories;

import com.lexcv.models.NotificacaoPreferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificacaoPreferenciaRepository extends JpaRepository<NotificacaoPreferencia, UUID> {

    // Read contract for the criar() mute guard (Plan 93-02). Dual-scoped by
    // tenant_id + user_id to prevent cross-tenant/cross-user preference leaks
    // (Pitfall 10).
    boolean existsByTenantIdAndUserIdAndCategoria(UUID tenantId, UUID userId, String categoria);

    // Feeds the GET listing endpoint (Plan 93-03) and the service's
    // listarCategoriasSilenciadas method (Plan 93-02).
    List<NotificacaoPreferencia> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    // Derived delete used to "re-activate" a category; called from a
    // @Transactional service method (Plan 93-02). No @Modifying/@Query --
    // derived delete is the established convention in this codebase.
    void deleteByTenantIdAndUserIdAndCategoria(UUID tenantId, UUID userId, String categoria);
}
