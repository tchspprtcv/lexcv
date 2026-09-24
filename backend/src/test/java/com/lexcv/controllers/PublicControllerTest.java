package com.lexcv.controllers;

import com.lexcv.dtos.TenantPublicInfoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CR-02 (119-REVIEW.md) / ISOL-01 (Phase 121, já satisfeito por esta correção): antes desta
 * mudança, {@code getBranding()} devolvia a marca da tenant mais antiga por {@code createdAt}
 * ({@code TenantRepository.findFirstByOrderByCreatedAtAsc()}) -- o que, a partir da Phase 119
 * (seeding incondicional da tenant reservada "LexCV" em {@code DatabaseSeeder.seedTenantPlataforma()}),
 * passaria a devolver sempre a marca genérica "LexCV" em vez da marca real do primeiro cliente de
 * qualquer instalação nova, silenciosamente e para sempre (ver 119-REVIEW.md, CR-02). O endpoint
 * deixou de resolver qualquer {@code Tenant} -- por isso este teste já não injeta nem mocka
 * {@code TenantRepository} (o controller deixou de o receber no construtor) e limita-se a provar
 * que a resposta genérica é sempre devolvida, independentemente de quantos tenants reais existam
 * ou de qual seja o mais antigo.
 */
class PublicControllerTest {

    @Test
    void getBranding_devolveSempreAMarcaGenericaLexCV() {
        PublicController controller = new PublicController();

        ResponseEntity<?> response = controller.getBranding();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TenantPublicInfoResponse body = (TenantPublicInfoResponse) response.getBody();
        assertEquals("LexCV", body.getNome());
        assertNull(body.getLogoDataUrl());
    }

    // Regressão direta ao CR-02: chamadas repetidas (simulando instalações onde, sob a
    // implementação antiga, a tenant "mais antiga" podia variar) têm de devolver exatamente o
    // mesmo corpo -- a resposta nunca pode voltar a depender de qual tenant é mais antiga, nem de
    // nenhuma outra leitura de estado.
    @Test
    void getBranding_devolveSempreAMesmaRespostaIndependentementeDoEstado() {
        PublicController controller = new PublicController();

        TenantPublicInfoResponse primeira = (TenantPublicInfoResponse) controller.getBranding().getBody();
        TenantPublicInfoResponse segunda = (TenantPublicInfoResponse) controller.getBranding().getBody();

        assertEquals(primeira, segunda);
        assertEquals("LexCV", segunda.getNome());
        assertNull(segunda.getLogoDataUrl());
    }
}
