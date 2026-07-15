package com.lexcv.controllers;

import com.lexcv.dtos.TenantPublicInfoResponse;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoint público, não-autenticado. Stateless — nunca lê SecurityContextHolder (não há
 * chamador autenticado). Devolve exclusivamente nome+logoDataUrl da tenant singleton, via
 * cópia explícita getter-para-setter (TenantPublicInfoResponse) — nunca a entidade Tenant.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final TenantRepository tenantRepository;

    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        Optional<Tenant> tenant = tenantRepository.findFirstByOrderByCreatedAtAsc();

        if (tenant.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Sistema não inicializado."));
        }

        Tenant t = tenant.get();
        return ResponseEntity.ok(
                TenantPublicInfoResponse.builder()
                        .nome(t.getNome())
                        .logoDataUrl(t.getLogoDataUrl())
                        .build()
        );
    }
}
