package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.EstadoEmissaoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (D-01, D-16, D-17, D-18, R-05, EMIS-02, EMIS-11, EMIS-12): comportamento de
 * {@link DocumentoFiscalController} -- tenant só do principal, parâmetros da listagem analisados à
 * mão (400 com mensagem fixa, nunca 500), id inválido no detalhe → 404 sem oráculo, e pinos de
 * reflexão: 4 handlers, gates por método, sem rotas que alterem documentos, sem emissão para
 * pagamentos existentes.
 */
class DocumentoFiscalControllerTest {

    private PreVisualizacaoFaturaService preVisualizacao;
    private DocumentoFiscalService documentos;
    private DocumentoFiscalController controller;
    private UUID tenant;

    @BeforeEach
    void preparar() {
        preVisualizacao = mock(PreVisualizacaoFaturaService.class);
        documentos = mock(DocumentoFiscalService.class);
        controller = new DocumentoFiscalController(preVisualizacao, documentos);
        tenant = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), tenant, "Ana", "ana@example.cv",
                Set.of(), Set.of("financeiro:view", "financeiro:edit"), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(documentos.listar(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(i -> new PageImpl<DocumentoFiscalResumoResponse>(List.of(),
                        PageRequest.of(i.getArgument(6), i.getArgument(7)), 0));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private ResponseEntity<?> listar(String clienteId, String de, String ate, String tipo, String estado,
                                     String page, String size) {
        return controller.listar(clienteId, de, ate, tipo, estado, page, size);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    private void assert400(ResponseEntity<?> r) {
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertNotNull(corpo(r).get("message"));
        verifyNoInteractions(documentos);
    }

    // ------------------------------------------------------------------ estado e pré-visualização

    @Test
    void estadoEmissaoDoTenantDoPrincipal() {
        EstadoEmissaoResponse estado = new EstadoEmissaoResponse(true, "SIMULADO", new BigDecimal("20"));
        when(preVisualizacao.estadoEmissao(tenant)).thenReturn(estado);

        ResponseEntity<?> r = controller.estadoEmissao();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(estado, r.getBody());
    }

    @Test
    void preVisualizacaoDelegaComOTenantDoPrincipal() {
        PagamentoRequest req = new PagamentoRequest(1, BigDecimal.TEN, null, "DINHEIRO", null, null);
        PreVisualizacaoFaturaResponse resposta = mock(PreVisualizacaoFaturaResponse.class);
        when(preVisualizacao.preVisualizar(tenant, req)).thenReturn(resposta);

        ResponseEntity<?> r = controller.preVisualizar(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(resposta, r.getBody());
    }

    @Test
    void preVisualizacaoPropagaARecusaFiscal() {
        RecusaFiscalException recusa = new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA", "x");
        when(preVisualizacao.preVisualizar(any(), any())).thenThrow(recusa);

        assertSame(recusa, assertThrows(RecusaFiscalException.class,
                () -> controller.preVisualizar(new PagamentoRequest(1, BigDecimal.ONE, null, null, null, null))));
    }

    // ------------------------------------------------------------------ listagem

    @Test
    void listagemPorOmissaoPagina0Tamanho10SemFiltros() {
        ResponseEntity<?> r = listar(null, null, null, null, null, "0", "10");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(documentos).listar(tenant, null, null, null, null, null, 0, 10);
        Map<String, Object> c = corpo(r);
        assertEquals(Set.of("content", "totalElements", "totalPages", "page", "size"), c.keySet());
        assertEquals(List.of(), c.get("content"));
        assertEquals(0L, c.get("totalElements"));
        assertEquals(0, c.get("page"));
        assertEquals(10, c.get("size"));
    }

    @Test
    void listagemComTodosOsFiltros() {
        UUID cliente = UUID.randomUUID();

        ResponseEntity<?> r = listar(cliente.toString(), "2026-01-01", "2026-12-31", "FR", "PENDENTE", "2", "25");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(documentos).listar(tenant, cliente, TipoDocumentoFiscal.FR, EstadoComunicacaoFiscal.PENDENTE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 2, 25);
        assertEquals(2, corpo(r).get("page"));
        assertEquals(25, corpo(r).get("size"));
    }

    @Test
    void valoresEmBrancoContamComoAusentes() {
        ResponseEntity<?> r = listar("  ", "", " ", "", "  ", "0", "10");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        verify(documentos).listar(tenant, null, null, null, null, null, 0, 10);
    }

    @Test
    void mesmaDataNosDoisLimitesEhAceite() {
        assertEquals(HttpStatus.OK, listar(null, "2026-05-05", "2026-05-05", null, null, "0", "10").getStatusCode());
    }

    @Test
    void paginaNegativaDevolve400() {
        assert400(listar(null, null, null, null, null, "-1", "10"));
    }

    @Test
    void tamanhoForaDe1a100Devolve400() {
        assert400(listar(null, null, null, null, null, "0", "0"));
        assert400(listar(null, null, null, null, null, "0", "101"));
    }

    @Test
    void paginaOuTamanhoNaoNumericosDevolvem400() {
        assert400(listar(null, null, null, null, null, "x", "10"));
        assert400(listar(null, null, null, null, null, "0", "1e3"));
    }

    @Test
    void clienteIdQueNaoEhUuidDevolve400() {
        assert400(listar("nao-e-uuid", null, null, null, null, "0", "10"));
    }

    @Test
    void datasQueNaoSaoIsoDevolvem400() {
        assert400(listar(null, "04/10/2026", null, null, null, "0", "10"));
        assert400(listar(null, null, "2026-13-01", null, null, "0", "10"));
    }

    @Test
    void dataFinalAnteriorAInicialDevolve400ComMensagem() {
        ResponseEntity<?> r = listar(null, "2026-10-04", "2026-10-03", null, null, "0", "10");

        assert400(r);
        assertEquals("A data final não pode ser anterior à inicial.", corpo(r).get("message"));
    }

    @Test
    void tipoDesconhecidoDevolve400() {
        assert400(listar(null, null, null, "FATURA", null, "0", "10"));
    }

    @Test
    void estadoDesconhecidoDevolve400() {
        assert400(listar(null, null, null, null, "ENVIADO_OU_NAO", "0", "10"));
    }

    // ------------------------------------------------------------------ detalhe

    @Test
    void detalheComIdInvalidoDevolve404SemOraculo() {
        ResponseEntity<?> r = controller.detalhe("123");

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(Map.of("message", "Documento fiscal não encontrado.", "code", "DOCUMENTO_FISCAL_NAO_ENCONTRADO"),
                r.getBody());
        verifyNoInteractions(documentos);
    }

    @Test
    void detalheDelegaComOTenantDoPrincipal() {
        UUID id = UUID.randomUUID();
        DocumentoFiscalDetalheResponse d = mock(DocumentoFiscalDetalheResponse.class);
        when(documentos.detalhe(tenant, id)).thenReturn(d);

        ResponseEntity<?> r = controller.detalhe(id.toString());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(d, r.getBody());
    }

    @Test
    void detalheDeOutroEscritorioPropaga404DoServico() {
        UUID id = UUID.randomUUID();
        RecusaFiscalException nao = new RecusaFiscalException(HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO",
                "Documento fiscal não encontrado.");
        when(documentos.detalhe(eq(tenant), eq(id))).thenThrow(nao);

        assertSame(nao, assertThrows(RecusaFiscalException.class, () -> controller.detalhe(id.toString())));
    }

    // ------------------------------------------------------------------ reflexão

    private static List<Method> handlers() {
        return Arrays.stream(DocumentoFiscalController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(PatchMapping.class)
                        || m.isAnnotationPresent(DeleteMapping.class) || m.isAnnotationPresent(RequestMapping.class))
                .toList();
    }

    private static Method handler(String nome) {
        return handlers().stream().filter(m -> m.getName().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void exatamenteQuatroHandlersSemRotasQueAlterem() {
        List<Method> hs = handlers();
        assertEquals(4, hs.size(), hs.toString());
        for (Method m : hs) {
            assertFalse(m.isAnnotationPresent(PutMapping.class), m.getName());
            assertFalse(m.isAnnotationPresent(PatchMapping.class), m.getName());
            assertFalse(m.isAnnotationPresent(DeleteMapping.class), m.getName());
            assertFalse(m.isAnnotationPresent(RequestMapping.class), m.getName());
        }
        assertEquals("/faturacao/estado-emissao", handler("estadoEmissao").getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/faturacao/pre-visualizacao", handler("preVisualizar").getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/documentos-fiscais", handler("listar").getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/documentos-fiscais/{id}", handler("detalhe").getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/api/v1", DocumentoFiscalController.class.getAnnotation(RequestMapping.class).value()[0]);
    }

    @Test
    void gatesPorMetodoSemGateDeClasse() {
        assertNull(DocumentoFiscalController.class.getAnnotation(PreAuthorize.class));
        assertEquals("hasAnyAuthority('financeiro:view', 'financeiro:edit')",
                handler("estadoEmissao").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('financeiro:view')", handler("listar").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('financeiro:view')", handler("detalhe").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('financeiro:edit')", handler("preVisualizar").getAnnotation(PreAuthorize.class).value());
    }

    private static String nomeDoParametro(Parameter p) {
        RequestParam rp = p.getAnnotation(RequestParam.class);
        if (rp != null && !rp.value().isEmpty()) {
            return rp.value();
        }
        if (rp != null && !rp.name().isEmpty()) {
            return rp.name();
        }
        PathVariable pv = p.getAnnotation(PathVariable.class);
        if (pv != null && !pv.value().isEmpty()) {
            return pv.value();
        }
        return p.getName();
    }

    @Test
    void tenantNuncaVemDoPedidoENenhumHandlerRecebePagamentoId() {
        for (Method m : handlers()) {
            for (Parameter p : m.getParameters()) {
                assertTrue(p.isNamePresent(), "compilar com -parameters");
                String nome = nomeDoParametro(p).toLowerCase();
                assertFalse(nome.contains("tenant"), m.getName() + " recebe " + nome);
                assertFalse(nome.contains("pagamentoid"), m.getName() + " recebe " + nome + " (EMIS-12)");
                if (p.isAnnotationPresent(RequestBody.class)) {
                    assertEquals(PagamentoRequest.class, p.getType(), "o único corpo é o PagamentoRequest");
                }
            }
        }
        assertTrue(Arrays.stream(PagamentoRequest.class.getRecordComponents())
                .noneMatch(c -> c.getName().toLowerCase().contains("tenant")));
    }

    @Test
    void controladorNaoEhTransacional() {
        assertNull(DocumentoFiscalController.class.getAnnotation(Transactional.class));
        for (Method m : handlers()) {
            assertNull(m.getAnnotation(Transactional.class), m.getName());
        }
    }
}
