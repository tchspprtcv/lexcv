package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalService;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.FaturacaoSubscricaoEscritorioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaturacaoSubscricaoControllerTest {

    @Mock
    private FaturacaoSubscricaoEscritorioService faturacaoSubscricaoEscritorioService;

    @InjectMocks
    private FaturacaoSubscricaoController controller;

    private UUID officeTenantId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String... perms) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(officeTenantId)
                .nome("Dr. Teste")
                .email("advogado@escritorio.cv")
                .authorities(java.util.Arrays.stream(perms).map(SimpleGrantedAuthority::new).toList())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void listarSubscricoesUsaTenantDoContexto() {
        autenticarComo("financeiro:view");
        Page<DocumentoFiscalResumoResponse> page = new PageImpl<>(List.of());
        when(faturacaoSubscricaoEscritorioService.listar(officeTenantId, 0, 10)).thenReturn(page);

        ResponseEntity<?> response = controller.listarSubscricoes(0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(faturacaoSubscricaoEscritorioService).listar(officeTenantId, 0, 10);
    }

    @Test
    void detalheSubscricaoUsaTenantDoContexto() {
        autenticarComo("financeiro:view");
        UUID docId = UUID.randomUUID();
        DocumentoFiscalDetalheResponse detalhe = mock(DocumentoFiscalDetalheResponse.class);
        when(faturacaoSubscricaoEscritorioService.detalhe(officeTenantId, docId)).thenReturn(detalhe);

        ResponseEntity<DocumentoFiscalDetalheResponse> response = controller.detalheSubscricao(docId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(detalhe, response.getBody());
        verify(faturacaoSubscricaoEscritorioService).detalhe(officeTenantId, docId);
    }

    @Test
    void descarregarPdfUsaTenantDoContexto() {
        autenticarComo("financeiro:view");
        UUID docId = UUID.randomUUID();
        DescargaDocumentoFiscalService.DescargaPdf pdf = new DescargaDocumentoFiscalService.DescargaPdf("https://pdf", "FR-1.pdf", 300L);
        when(faturacaoSubscricaoEscritorioService.descarregarPdf(eq(officeTenantId), any(), eq(docId))).thenReturn(pdf);

        ResponseEntity<?> response = controller.descarregarPdf(docId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(faturacaoSubscricaoEscritorioService).descarregarPdf(eq(officeTenantId), any(), eq(docId));
    }

    @Test
    void descarregarXmlUsaTenantDoContexto() {
        autenticarComo("financeiro:view");
        UUID docId = UUID.randomUUID();
        DescargaDocumentoFiscalTransacoes.XmlDescarregavel xml = new DescargaDocumentoFiscalTransacoes.XmlDescarregavel("<xml/>".getBytes(), "FR-1.xml");
        when(faturacaoSubscricaoEscritorioService.descarregarXml(eq(officeTenantId), any(), eq(docId))).thenReturn(xml);

        ResponseEntity<?> response = controller.descarregarXml(docId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(faturacaoSubscricaoEscritorioService).descarregarXml(eq(officeTenantId), any(), eq(docId));
    }
}
