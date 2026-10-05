package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.PagamentoComDocumentoResponse;
import com.lexcv.models.Honorario;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (D-19, EMIS-12, T-134-37): a lista de pagamentos de um honorário mostra, para cada
 * pagamento, o número da sua Fatura-Recibo -- ou nada, para os pagamentos registados sem
 * faturação (antes da ativação nunca se fatura retroativamente). Uma só consulta em lote, depois
 * da verificação de tenant; nenhum endpoint emite um documento para um pagamento já existente.
 */
class ResourceControllerListaPagamentosTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private HonorarioRepository honorarioRepository;
    private ProcessoRepository processoRepository;
    private PagamentoRepository pagamentoRepository;
    private DocumentoFiscalService documentoFiscalService;
    private ResourceController controller;
    private Processo processo;

    @BeforeEach
    void preparar() {
        honorarioRepository = mock(HonorarioRepository.class);
        processoRepository = mock(ProcessoRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        documentoFiscalService = mock(DocumentoFiscalService.class);
        controller = ResourceControllerPagamentoTest.novoController(honorarioRepository, processoRepository,
                pagamentoRepository, mock(ContaCorrenteRepository.class), mock(PagamentoFaturadoService.class),
                documentoFiscalService);

        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), TENANT_ID, "Ana", "ana@example.cv",
                Set.of(), Set.of("financeiro:view"), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        processo = Processo.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).clienteId(UUID.randomUUID()).build();
        Honorario hon = new Honorario();
        hon.setId(5);
        hon.setProcessoId(processo.getId());
        when(honorarioRepository.findById(5)).thenReturn(Optional.of(hon));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static Pagamento pagamento(int id) {
        return Pagamento.builder().id(id).honorarioId(5).valorPago(new BigDecimal(id + "00.00"))
                .dataPagamento(LocalDate.of(2026, 10, id)).metodo("DINHEIRO").build();
    }

    @SuppressWarnings("unchecked")
    private static List<PagamentoComDocumentoResponse> lista(ResponseEntity<?> r) {
        return (List<PagamentoComDocumentoResponse>) r.getBody();
    }

    @Test
    void cadaPagamentoTrazOSeuDocumentoOuNada() {
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(pagamento(1), pagamento(2), pagamento(3)));
        DocumentoFiscalRef ref = new DocumentoFiscalRef(UUID.randomUUID(), "SIM-FR-2026/4");
        when(documentoFiscalService.referenciasPorPagamento(eq(TENANT_ID), any())).thenReturn(Map.of(2, ref));

        ResponseEntity<?> r = controller.listHonorarioPagamentos(5);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        List<PagamentoComDocumentoResponse> l = lista(r);
        assertEquals(3, l.size());
        assertEquals(List.of(1, 2, 3), l.stream().map(PagamentoComDocumentoResponse::id).toList());
        assertNull(l.get(0).documentoFiscal(), "pagamento anterior à ativação: sem documento fiscal");
        assertEquals(ref, l.get(1).documentoFiscal());
        assertEquals("SIM-FR-2026/4", l.get(1).documentoFiscal().numeroFormatado());
        assertNull(l.get(2).documentoFiscal());
        assertEquals(new BigDecimal("200.00"), l.get(1).valorPago());
        assertEquals("DINHEIRO", l.get(1).metodo());
        assertEquals(LocalDate.of(2026, 10, 2), l.get(1).dataPagamento());
        assertEquals(5, l.get(1).honorarioId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void referenciasLidasNumaSoConsultaComOTenantDoPrincipal() {
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(pagamento(1), pagamento(2), pagamento(3)));
        when(documentoFiscalService.referenciasPorPagamento(any(), any())).thenReturn(Map.of());

        controller.listHonorarioPagamentos(5);

        ArgumentCaptor<Collection<Integer>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(documentoFiscalService, times(1)).referenciasPorPagamento(eq(TENANT_ID), ids.capture());
        assertEquals(Set.of(1, 2, 3), new HashSet<>(ids.getValue()));
    }

    @Test
    void honorarioDeOutroTenantDevolve404SemConsultarDocumentos() {
        Processo alheio = Processo.builder().id(processo.getId()).tenantId(UUID.randomUUID()).build();
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(alheio));

        ResponseEntity<?> r = controller.listHonorarioPagamentos(5);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(Map.of("message", "Honorário não encontrado"), r.getBody());
        verifyNoInteractions(documentoFiscalService);
    }

    @Test
    void honorarioInexistenteDevolve404SemConsultarDocumentos() {
        when(honorarioRepository.findById(6)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.listHonorarioPagamentos(6);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verifyNoInteractions(documentoFiscalService);
    }

    @Test
    void semPagamentosDevolveListaVazia() {
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of());
        when(documentoFiscalService.referenciasPorPagamento(any(), any())).thenReturn(Map.of());

        ResponseEntity<?> r = controller.listHonorarioPagamentos(5);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(lista(r).isEmpty());
    }

    @Test
    void gateDeLeituraMantemSe() throws Exception {
        Method m = ResourceController.class.getMethod("listHonorarioPagamentos", Integer.class);
        PreAuthorize gate = m.getAnnotation(PreAuthorize.class);
        assertNotNull(gate);
        assertEquals("hasAuthority('financeiro:view')", gate.value());
    }

    /** EMIS-12: nenhum handler fatura um pagamento existente (sem backfill, sem "emitir"). */
    @Test
    void nenhumEndpointEmiteDocumentoParaUmPagamentoExistente() {
        List<String> palavras = List.of("fatura", "documento-fiscal", "documentos-fiscais", "emitir");
        List<String> ofensores = new ArrayList<>();
        for (Method m : ResourceController.class.getDeclaredMethods()) {
            List<String> caminhos = new ArrayList<>();
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                caminhos.addAll(List.of(post.value()));
                caminhos.addAll(List.of(post.path()));
            }
            PutMapping put = m.getAnnotation(PutMapping.class);
            if (put != null) {
                caminhos.addAll(List.of(put.value()));
                caminhos.addAll(List.of(put.path()));
            }
            RequestMapping req = m.getAnnotation(RequestMapping.class);
            if (req != null && (List.of(req.method()).contains(RequestMethod.POST)
                    || List.of(req.method()).contains(RequestMethod.PUT))) {
                caminhos.addAll(List.of(req.value()));
                caminhos.addAll(List.of(req.path()));
            }
            for (String c : caminhos) {
                String minusc = c.toLowerCase();
                if (minusc.contains("pagamentos/{") && palavras.stream().anyMatch(minusc::contains)) {
                    ofensores.add(m.getName() + " " + c);
                }
            }
        }
        assertEquals(List.of(), ofensores);
    }
    // ---------------------------------------------------------------- Phase 135 (NCRD-03)

    @Test
    void estornoApareceComAReferenciaDaNotaDeCreditoESemDocumentoFiscal() {
        Pagamento estorno = Pagamento.builder().id(4).honorarioId(5).valorPago(new BigDecimal("-20000.00"))
                .dataPagamento(LocalDate.of(2026, 10, 5)).metodo("DINHEIRO").build();
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(pagamento(1), pagamento(2), estorno));
        DocumentoFiscalRef fr = new DocumentoFiscalRef(UUID.randomUUID(), "SIM-FR-2026/4");
        DocumentoFiscalRef nc = new DocumentoFiscalRef(UUID.randomUUID(), "SIM-NC-2026/1");
        when(documentoFiscalService.referenciasPorPagamento(eq(TENANT_ID), any())).thenReturn(Map.of(2, fr));
        when(documentoFiscalService.estornosPorPagamento(eq(TENANT_ID), any())).thenReturn(Map.of(4, nc));

        List<PagamentoComDocumentoResponse> l = lista(controller.listHonorarioPagamentos(5));

        assertEquals(3, l.size());
        assertNull(l.get(0).documentoFiscal(), "legado: sem documento");
        assertNull(l.get(0).estorno(), "legado: sem estorno");
        assertEquals(fr, l.get(1).documentoFiscal());
        assertNull(l.get(1).estorno(), "pagamento com FR não é estorno");
        assertNull(l.get(2).documentoFiscal(), "um estorno nunca aparece como Fatura-Recibo");
        assertEquals(nc, l.get(2).estorno());
        assertEquals("SIM-NC-2026/1", l.get(2).estorno().numeroFormatado());
        assertEquals(new BigDecimal("-20000.00"), l.get(2).valorPago());
    }

    @Test
    @SuppressWarnings("unchecked")
    void estornosLidosNumaSoConsultaComOTenantDoPrincipal() {
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(pagamento(1), pagamento(2)));
        when(documentoFiscalService.referenciasPorPagamento(any(), any())).thenReturn(Map.of());
        when(documentoFiscalService.estornosPorPagamento(any(), any())).thenReturn(Map.of());

        controller.listHonorarioPagamentos(5);

        ArgumentCaptor<Collection<Integer>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(documentoFiscalService, times(1)).estornosPorPagamento(eq(TENANT_ID), ids.capture());
        assertEquals(Set.of(1, 2), new HashSet<>(ids.getValue()));
    }

    @Test
    void honorarioDeOutroTenantNaoLeEstornos() {
        processo.setTenantId(UUID.randomUUID());

        ResponseEntity<?> r = controller.listHonorarioPagamentos(5);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verify(documentoFiscalService, org.mockito.Mockito.never()).estornosPorPagamento(any(), any());
    }
}
