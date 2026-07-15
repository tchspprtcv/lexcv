package com.lexcv.controllers;

import com.lexcv.dtos.TenantPublicInfoResponse;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
@Slf4j
public class PublicController {

    private final TenantRepository tenantRepository;

    @GetMapping("/branding")
    @Transactional(readOnly = true)
    // Sem esta anotação, a leitura de Tenant.logoDataUrl (@Lob) falha em runtime com
    // "Large Objects may not be used in auto-commit mode" (PostgreSQL exige que o streaming
    // de Large Objects ocorra dentro de uma transação explícita) — só descoberto pela
    // verificação ao vivo da Phase 100 (100-04) contra uma tenant real com logo persistido;
    // não reproduzível com uma tenant sem logoDataUrl, daí ter escapado à Phase 98.
    public ResponseEntity<?> getBranding() {
        // WR-01 (Phase 98 code review): a suposição de "tenant singleton" só se mantém porque
        // SetupService.initializeSystem() e DatabaseSeeder impedem, de forma independente e não
        // coordenada, a criação de uma 2ª Tenant -- não há unique/check constraint no schema nem
        // neste endpoint. Se essa suposição for violada (onboarding de tenant, insert manual,
        // migração), este endpoint continuaria a servir silenciosamente a marca da tenant mais
        // antiga a todos os visitantes anónimos, sem qualquer sinal de erro. Este log torna a
        // violação observável em vez de silenciosa.
        long tenantCount = tenantRepository.count();
        if (tenantCount > 1) {
            log.warn("PublicController.getBranding(): {} tenants encontrados; suposição de tenant singleton violada, a devolver a mais antiga por createdAt", tenantCount);
        }

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
