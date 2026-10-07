package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalService;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.FaturacaoSubscricaoEscritorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 138 (SUBS-04): Endpoints de consulta e descarga de faturas de subscrição emitidas pela plataforma LexCV
 * para o escritório autenticado.
 *
 * <p>Protegido ao nível de método por {@code hasAuthority('financeiro:view')}.
 * <p>Isolamento estrito: o tenantId do adquirente é obtido exclusivamente a partir do {@link UserPrincipal} autenticado.
 */
@RestController
@RequestMapping("/api/v1/faturacao/subscricoes")
@RequiredArgsConstructor
public class FaturacaoSubscricaoController {

    private static final String MSG_PAGINACAO = "page deve ser >= 0 e size deve estar entre 1 e 100";
    private static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";
    private static final String CODIGO_NAO_ENCONTRADO = "DOCUMENTO_FISCAL_NAO_ENCONTRADO";

    private final FaturacaoSubscricaoEscritorioService faturacaoSubscricaoEscritorioService;

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    private UUID getTenantId() {
        return getPrincipal().getTenantId();
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping
    public ResponseEntity<?> listarSubscricoes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.badRequest().body(Map.of("message", MSG_PAGINACAO));
        }

        Page<DocumentoFiscalResumoResponse> resultado =
                faturacaoSubscricaoEscritorioService.listar(getTenantId(), page, size);

        return ResponseEntity.ok(Map.of(
                "content", resultado.getContent(),
                "totalElements", resultado.getTotalElements(),
                "totalPages", resultado.getTotalPages(),
                "page", page,
                "size", size));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentoFiscalDetalheResponse> detalheSubscricao(@PathVariable UUID id) {
        return ResponseEntity.ok(faturacaoSubscricaoEscritorioService.detalhe(getTenantId(), id));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> descarregarPdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal autor) {
        DescargaDocumentoFiscalService.DescargaPdf pdf =
                faturacaoSubscricaoEscritorioService.descarregarPdf(getTenantId(), autor != null ? autor : getPrincipal(), id);
        return ResponseEntity.ok(Map.of(
                "url", pdf.url(),
                "nomeFicheiro", pdf.nomeFicheiro(),
                "expiresIn", pdf.expiresIn()));
    }

    @PreAuthorize("hasAuthority('financeiro:view')")
    @GetMapping("/{id}/xml")
    public ResponseEntity<?> descarregarXml(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal autor) {
        DescargaDocumentoFiscalTransacoes.XmlDescarregavel xml =
                faturacaoSubscricaoEscritorioService.descarregarXml(getTenantId(), autor != null ? autor : getPrincipal(), id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(xml.nomeFicheiro()).build().toString())
                .body(xml.conteudo());
    }
}
