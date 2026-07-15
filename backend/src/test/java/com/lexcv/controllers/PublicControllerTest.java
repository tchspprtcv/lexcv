package com.lexcv.controllers;

import com.lexcv.dtos.TenantPublicInfoResponse;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Nenhum MockMvc/@SpringBootTest existe neste codebase (ver NotificacaoServiceTest,
 * ResourceControllerUploadDocumentoTest) — PublicController é instanciado diretamente com
 * TenantRepository mockado por Mockito, e getBranding() é chamado como um método Java simples.
 */
@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void getBranding_semTenant_devolve404ComMensagemSistemaNaoInicializado() {
        when(tenantRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        PublicController controller = new PublicController(tenantRepository);
        ResponseEntity<?> response = controller.getBranding();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertEquals("Sistema não inicializado.", body.get("message"));
        // Nunca lança exceção nem devolve 500 — Optional.empty() é tratado explicitamente.
    }

    @Test
    void getBranding_comTenantELogo_devolve200ComNomeELogoDataUrl() {
        Tenant tenant = Tenant.builder()
                .nome("Escritório X")
                .logoDataUrl("data:image/png;base64,AAA")
                .build();
        when(tenantRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(tenant));

        PublicController controller = new PublicController(tenantRepository);
        ResponseEntity<?> response = controller.getBranding();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TenantPublicInfoResponse body = (TenantPublicInfoResponse) response.getBody();
        assertEquals("Escritório X", body.getNome());
        assertEquals("data:image/png;base64,AAA", body.getLogoDataUrl());
    }

    @Test
    void getBranding_comTenantSemLogo_devolve200ComLogoDataUrlNullExplicito() {
        Tenant tenant = Tenant.builder()
                .nome("Escritório Y")
                .logoDataUrl(null)
                .build();
        when(tenantRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(tenant));

        PublicController controller = new PublicController(tenantRepository);
        ResponseEntity<?> response = controller.getBranding();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TenantPublicInfoResponse body = (TenantPublicInfoResponse) response.getBody();
        assertEquals("Escritório Y", body.getNome());
        assertNull(body.getLogoDataUrl());
        // logoDataUrl null é serializado como JSON null explícito, sem exceção, status 200.
    }
}
