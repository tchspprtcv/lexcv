package com.lexcv.controllers;

import com.lexcv.dtos.TenantPublicInfoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público, não-autenticado. Stateless — nunca lê SecurityContextHolder (não há
 * chamador autenticado) nem qualquer repositório.
 *
 * <p>CR-02 (119-REVIEW.md) / ISOL-01 (Phase 121, já satisfeito por esta correção): antes desta
 * mudança, este endpoint devolvia nome+logo da tenant mais antiga por {@code createdAt}
 * ({@code TenantRepository.findFirstByOrderByCreatedAtAsc()}), sob a suposição de deployment
 * single-tenant (WR-01, Phase 98 code review). A partir da Phase 119,
 * {@code DatabaseSeeder.seedTenantPlataforma()} passou a criar incondicionalmente, em todo o
 * arranque, a tenant reservada "LexCV" antes de qualquer tenant de cliente real -- tornando-a
 * garantidamente a tenant mais antiga, para sempre, em qualquer instalação nova, e fazendo este
 * endpoint devolver sempre a marca genérica "LexCV" em vez da marca real do cliente,
 * silenciosamente e para sempre. Em vez de tentar excluir a tenant reservada dessa procura (o que
 * continuaria frágil perante qualquer 3ª tenant real futura), este endpoint deixa de resolver "a"
 * tenant: devolve sempre a marca genérica LexCV, tal como decidido em
 * {@code proposta_multitenancy_distribuicao_faturacao.md} (secção 4.2, ponto 2) e adotado como
 * ISOL-01 na Phase 121. Branding por escritório continua disponível dentro da aplicação
 * autenticada via {@code GET /auth/me} (que já resolve o tenantId real do utilizador a partir do
 * JWT) -- nunca por este caminho público.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        return ResponseEntity.ok(
                TenantPublicInfoResponse.builder()
                        .nome("LexCV")
                        .logoDataUrl(null)
                        .build()
        );
    }
}
