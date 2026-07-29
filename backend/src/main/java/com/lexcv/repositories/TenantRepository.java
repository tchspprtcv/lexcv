package com.lexcv.repositories;

import com.lexcv.models.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Devolve a tenant mais antiga por {@code createdAt}.
     *
     * <p><strong>ATENÇÃO (WR-01, Phase 98 code review):</strong> esta query assume um deployment
     * single-tenant — não é uma query genérica de "tenant mais recente". É usada por
     * {@code PublicController.getBranding()} sob a suposição de que existe no máximo uma
     * {@code Tenant}, garantido hoje apenas por {@code SetupService.initializeSystem()} e
     * {@code DatabaseSeeder} (nenhum unique/check constraint no schema). Se uma futura feature de
     * onboarding multi-tenant permitir uma 2ª {@code Tenant}, este call site tem de ser revisitado.
     */
    Optional<Tenant> findFirstByOrderByCreatedAtAsc();

    /**
     * Lookup idempotente da tenant reservada de plataforma (nome literal {@code "LexCV"}),
     * usado por {@code DatabaseSeeder} para garantir find-or-create sem duplicar a tenant
     * em arranques sucessivos da aplicação (Phase 119, PROV-01).
     */
    Optional<Tenant> findByNome(String nome);
}
