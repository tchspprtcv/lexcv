package com.lexcv.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lexcv.dtos.TenantPublicInfoResponse;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    /**
     * WR-02 (Phase 98 re-review): o único sinal observável de violação da suposição de tenant
     * singleton é o {@code log.warn} em {@code PublicController.getBranding()}. Antes deste
     * teste, nenhum dos 3 testes acima fazia {@code tenantRepository.count()} devolver mais de 1
     * (o mock devolvia o default primitivo {@code 0L}), pelo que o ramo {@code tenantCount > 1}
     * nunca era exercitado — um refactor que o apagasse, invertesse (`<` em vez de `>`) ou
     * removesse passaria despercebido, com todos os testes a continuar a passar. Este teste
     * força {@code count()} a devolver 2, confirma que o endpoint continua a servir a tenant
     * mais antiga sem falhar, e usa um Logback {@link ListAppender} para confirmar que o evento
     * WARN é mesmo emitido, não apenas que o comportamento de fallback é preservado.
     */
    @Test
    void getBranding_comMaisDeUmTenant_devolveTenantMaisAntigaERegistaWarn() {
        Tenant maisAntiga = Tenant.builder()
                .nome("Escritório Mais Antigo")
                .logoDataUrl(null)
                .build();
        when(tenantRepository.count()).thenReturn(2L);
        when(tenantRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(maisAntiga));

        Logger controllerLogger = (Logger) LoggerFactory.getLogger(PublicController.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        controllerLogger.addAppender(logAppender);

        try {
            PublicController controller = new PublicController(tenantRepository);
            ResponseEntity<?> response = controller.getBranding();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            TenantPublicInfoResponse body = (TenantPublicInfoResponse) response.getBody();
            assertEquals("Escritório Mais Antigo", body.getNome());
            assertNull(body.getLogoDataUrl());

            boolean warnEmitido = logAppender.list.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("tenants encontrados"));
            assertTrue(warnEmitido,
                    "Esperava um log WARN a assinalar a violação da suposição de tenant singleton "
                            + "quando tenantRepository.count() > 1");
        } finally {
            controllerLogger.detachAppender(logAppender);
        }
    }
}
