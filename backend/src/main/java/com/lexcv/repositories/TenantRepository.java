package com.lexcv.repositories;

import com.lexcv.models.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Lookup idempotente da tenant reservada de plataforma (nome literal {@code "LexCV"}),
     * usado por {@code DatabaseSeeder} para garantir find-or-create sem duplicar a tenant
     * em arranques sucessivos da aplicação (Phase 119, PROV-01).
     */
    Optional<Tenant> findByNome(String nome);
}
