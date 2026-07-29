package com.lexcv.controllers;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.TenantProvisionResponse;
import com.lexcv.models.Tenant;
import com.lexcv.services.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Superfície de administração de plataforma (Phase 119/120, PROV-01/PROV-06). Gated a
 * {@code PLATAFORMA_ADMIN} ao nível da classe -- nunca acessível a um {@code ADMIN} de um
 * escritório normal (tenant comum); o gate de classe cobre automaticamente qualquer handler
 * futuro que a Phase 120 venha a acrescentar aqui.
 *
 * <p>Ao contrário de {@link AdminController}, este controller NÃO é tenant-scoped: nunca lê
 * SecurityContextHolder/UserPrincipal, porque cada pedido provisiona um tenant NOVO, em vez de
 * agir dentro do tenant do chamador -- não há {@code getTenantId()} nenhum para ler.
 *
 * <p>{@code POST /api/v1/setup/initialize} ({@link SetupController}) continua a existir,
 * público e com o seu gate singleton intacto; este controller é um caminho totalmente
 * distinto -- nunca lhe chama, nunca reutiliza {@code initializeSystem}, e nunca consulta
 * {@code isInitialized()}.
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final SetupService setupService;

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody SetupInitializeRequest request) {
        try {
            Tenant tenant = setupService.provisionTenant(request);
            TenantProvisionResponse response = TenantProvisionResponse.builder()
                    .id(tenant.getId())
                    .nome(tenant.getNome())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        }
    }
}
