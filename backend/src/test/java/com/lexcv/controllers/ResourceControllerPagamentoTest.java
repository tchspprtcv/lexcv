package com.lexcv.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.PagamentoComDocumentoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ContaCorrente;
import com.lexcv.models.Honorario;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.repositories.*;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.RiscoPrazoService;
import com.lexcv.services.StorageService;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import com.lexcv.services.fiscal.ResultadoPagamentoFaturado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (D-11, CFG-03, T-134-33/34): prova comportamental dos dois ramos de
 * {@code POST /api/v1/pagamentos}. Com a faturação desligada, o controlador faz exatamente o que
 * fazia antes (mesmas verificações e mensagens, a falha da conta corrente continua engolida, 201
 * com a entidade); com a faturação ativa, delega no {@link PagamentoFaturadoService} e não toca em
 * repositórios. Complementa o hash fixado em {@link FaturacaoDesligadaPagamentoInalteradoTest}.
 */
class ResourceControllerPagamentoTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private HonorarioRepository honorarioRepository;
    private ProcessoRepository processoRepository;
    private PagamentoRepository pagamentoRepository;
    private ContaCorrenteRepository contaCorrenteRepository;
    private PagamentoFaturadoService pagamentoFaturadoService;
    private DocumentoFiscalService documentoFiscalService;

    private UserPrincipal principal;
    private ResourceController controller;
    private Processo processo;

    @BeforeEach
    void preparar() {
        honorarioRepository = mock(HonorarioRepository.class);
        processoRepository = mock(ProcessoRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        contaCorrenteRepository = mock(ContaCorrenteRepository.class);
        pagamentoFaturadoService = mock(PagamentoFaturadoService.class);
        documentoFiscalService = mock(DocumentoFiscalService.class);
        controller = novoController(honorarioRepository, processoRepository, pagamentoRepository,
                contaCorrenteRepository, pagamentoFaturadoService, documentoFiscalService);

        principal = UserPrincipal.create(UUID.randomUUID(), TENANT_ID, "Ana", "ana@example.cv",
                Set.of(), Set.of("financeiro:edit"), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        processo = Processo.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).clienteId(UUID.randomUUID()).build();
        Honorario hon = new Honorario();
        hon.setId(1);
        hon.setProcessoId(processo.getId());
        when(honorarioRepository.findById(1)).thenReturn(Optional.of(hon));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    /** Controlador com todos os colaboradores mockados (convenção desta codebase, sem MockMvc). */
    static ResourceController novoController(HonorarioRepository honorarioRepository,
                                             ProcessoRepository processoRepository,
                                             PagamentoRepository pagamentoRepository,
                                             ContaCorrenteRepository contaCorrenteRepository,
                                             PagamentoFaturadoService pagamentoFaturadoService,
                                             DocumentoFiscalService documentoFiscalService) {
        return new ResourceController(
                mock(ClienteRepository.class), mock(ClienteContactoRepository.class), mock(ClienteNotaRepository.class),
                contaCorrenteRepository, processoRepository, mock(ParteRepository.class),
                mock(FaseProcessualRepository.class), mock(ProcessoFaseRepository.class), mock(EventoRepository.class),
                mock(DocumentoRepository.class), mock(MovimentacaoRepository.class), honorarioRepository,
                pagamentoRepository, mock(ConflictCheckDecisaoRepository.class), mock(PrazoRepository.class),
                mock(UserRepository.class), mock(AuditLogRepository.class), mock(StorageService.class),
                mock(RiscoPrazoService.class), mock(NotificacaoService.class), mock(ClienteAdvogadoRepository.class),
                mock(ClienteAdministrativoRepository.class), mock(DecisaoRepository.class),
                mock(TestemunhaRepository.class), mock(FactoRepository.class),
                mock(ParecerSolicitacaoRepository.class), mock(ResolucaoPapeisService.class),
                pagamentoFaturadoService, documentoFiscalService);
    }

    private static PagamentoRequest pedido(Integer honorarioId, String valor) {
        return new PagamentoRequest(honorarioId, valor == null ? null : new BigDecimal(valor),
                LocalDate.of(2020, 1, 1), "Transferência", null, null);
    }

    private void contaCorrenteComSaldo(String saldo) {
        ContaCorrente cc = ContaCorrente.builder().id(9).clienteId(processo.getClienteId())
                .saldo(new BigDecimal(saldo)).build();
        when(contaCorrenteRepository.findByClienteId(processo.getClienteId())).thenReturn(Optional.of(cc));
    }

    private void desligada() {
        when(pagamentoFaturadoService.faturacaoAtiva(TENANT_ID)).thenReturn(false);
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(i -> {
            Pagamento p = i.getArgument(0);
            p.setId(77);
            return p;
        });
    }

    @SuppressWarnings("unchecked")
    private static String mensagem(ResponseEntity<?> r) {
        return ((Map<String, Object>) r.getBody()).get("message").toString();
    }

    // ------------------------------------------------------------------ faturação desligada

    @Test
    void desligadaRegistaComoAntesSemRegraDeData() {
        desligada();
        contaCorrenteComSaldo("50.00");

        ResponseEntity<?> r = controller.createPagamento(pedido(1, "100.00"));

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        Pagamento corpo = assertInstanceOf(Pagamento.class, r.getBody());
        assertEquals(77, corpo.getId());
        ArgumentCaptor<Pagamento> pag = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepository).save(pag.capture());
        assertSame(corpo, pag.getValue());
        assertEquals("Transferência", corpo.getMetodo(), "texto livre aceite como antes");
        assertEquals(LocalDate.of(2020, 1, 1), corpo.getDataPagamento(), "sem regra de data com a faturação desligada");
        assertEquals(1, corpo.getHonorarioId());

        ArgumentCaptor<ContaCorrente> cc = ArgumentCaptor.forClass(ContaCorrente.class);
        verify(contaCorrenteRepository).save(cc.capture());
        assertEquals(0, new BigDecimal("150.00").compareTo(cc.getValue().getSaldo()));

        verify(pagamentoFaturadoService).faturacaoAtiva(TENANT_ID);
        verify(pagamentoFaturadoService, never()).registar(any(), any(), any());
        verifyNoInteractions(documentoFiscalService);
    }

    @Test
    void desligadaPassaOIdNuloAoSave() {
        desligada();
        contaCorrenteComSaldo("0");
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(i -> {
            Pagamento p = i.getArgument(0);
            assertNull(p.getId(), "o INSERT gera o id; nunca vem do pedido");
            return p;
        });

        assertEquals(HttpStatus.CREATED, controller.createPagamento(pedido(1, "1.00")).getStatusCode());
    }

    @Test
    void desligadaCriaAContaCorrenteQuandoNaoExiste() {
        desligada();
        when(contaCorrenteRepository.findByClienteId(processo.getClienteId())).thenReturn(Optional.empty());
        when(contaCorrenteRepository.save(any(ContaCorrente.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(HttpStatus.CREATED, controller.createPagamento(pedido(1, "100.00")).getStatusCode());
        verify(contaCorrenteRepository, org.mockito.Mockito.times(2)).save(any(ContaCorrente.class));
    }

    @Test
    void desligadaEngoleAFalhaDaContaCorrente() {
        desligada();
        contaCorrenteComSaldo("50.00");
        when(contaCorrenteRepository.save(any(ContaCorrente.class)))
                .thenThrow(new DataAccessResourceFailureException("base de dados em baixo"));

        ResponseEntity<?> r = controller.createPagamento(pedido(1, "100.00"));

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertInstanceOf(Pagamento.class, r.getBody());
    }

    @Test
    void desligadaSemHonorarioIdDevolve400() {
        desligada();

        ResponseEntity<?> r = controller.createPagamento(pedido(null, "100.00"));

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("honorarioId é obrigatório", mensagem(r));
        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void desligadaValorZeroDevolve400() {
        desligada();

        ResponseEntity<?> r = controller.createPagamento(pedido(1, "0"));

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("valorPago é obrigatório e deve ser positivo", mensagem(r));
        verify(pagamentoRepository, never()).save(any());
    }

    @Test
    void desligadaHonorarioInexistenteDevolve404() {
        desligada();
        when(honorarioRepository.findById(2)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.createPagamento(pedido(2, "10"));

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Honorário não encontrado", mensagem(r));
    }

    @Test
    void desligadaProcessoDeOutroTenantDevolve404() {
        desligada();
        Processo alheio = Processo.builder().id(processo.getId()).tenantId(UUID.randomUUID())
                .clienteId(processo.getClienteId()).build();
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(alheio));

        ResponseEntity<?> r = controller.createPagamento(pedido(1, "100.00"));

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Processo associado não encontrado", mensagem(r));
        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(contaCorrenteRepository);
    }

    @Test
    void desligadaComChaveJaEmitidaDevolveOResultadoGuardadoSemCaminhoLegado() {
        // WR-05 da revisão: a FR foi emitida antes de a faturação ser desligada; a repetição do
        // pedido devolve-a em vez de registar um segundo pagamento sem documento.
        when(pagamentoFaturadoService.faturacaoAtiva(TENANT_ID)).thenReturn(false);
        PagamentoComDocumentoResponse guardada = resposta();
        PagamentoRequest req = new PagamentoRequest(1, new BigDecimal("100.00"), null, "DINHEIRO", null,
                UUID.randomUUID());
        when(pagamentoFaturadoService.resultadoGuardado(TENANT_ID, req))
                .thenReturn(Optional.of(ResultadoPagamentoFaturado.repetido(guardada)));

        ResponseEntity<?> r = controller.createPagamento(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(guardada, r.getBody());
        verify(pagamentoFaturadoService, never()).registar(any(), any(), any());
        verifyNoInteractions(pagamentoRepository, contaCorrenteRepository, honorarioRepository, processoRepository);
    }

    @Test
    void desligadaComChaveSemDocumentoSegueOCaminhoLegado() {
        desligada();
        contaCorrenteComSaldo("50.00");
        PagamentoRequest req = new PagamentoRequest(1, new BigDecimal("100.00"), null, "DINHEIRO", null,
                UUID.randomUUID());
        when(pagamentoFaturadoService.resultadoGuardado(TENANT_ID, req)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.createPagamento(req);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        verify(pagamentoFaturadoService).resultadoGuardado(TENANT_ID, req);
        verify(pagamentoRepository).save(any(Pagamento.class));
    }

    // ------------------------------------------------------------------ faturação ativa

    private PagamentoComDocumentoResponse resposta() {
        return new PagamentoComDocumentoResponse(55, 1, new BigDecimal("100.00"), LocalDate.of(2026, 10, 4),
                "DINHEIRO", new DocumentoFiscalRef(UUID.randomUUID(), "SIM-FR-2026/1"));
    }

    @Test
    void ativaEmissaoNovaDevolve201ComODocumento() {
        when(pagamentoFaturadoService.faturacaoAtiva(TENANT_ID)).thenReturn(true);
        PagamentoComDocumentoResponse resposta = resposta();
        PagamentoRequest req = new PagamentoRequest(1, new BigDecimal("100.00"), null, "DINHEIRO", null,
                UUID.randomUUID());
        when(pagamentoFaturadoService.registar(TENANT_ID, principal, req))
                .thenReturn(ResultadoPagamentoFaturado.novo(resposta));

        ResponseEntity<?> r = controller.createPagamento(req);

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertSame(resposta, r.getBody());
        ArgumentCaptor<UserPrincipal> autor = ArgumentCaptor.forClass(UserPrincipal.class);
        verify(pagamentoFaturadoService).registar(org.mockito.ArgumentMatchers.eq(TENANT_ID), autor.capture(),
                org.mockito.ArgumentMatchers.same(req));
        assertSame(principal, autor.getValue());
        verifyNoInteractions(pagamentoRepository, contaCorrenteRepository, honorarioRepository, processoRepository,
                documentoFiscalService);
    }

    @Test
    void ativaChaveRepetidaDevolve200() {
        when(pagamentoFaturadoService.faturacaoAtiva(TENANT_ID)).thenReturn(true);
        PagamentoComDocumentoResponse resposta = resposta();
        when(pagamentoFaturadoService.registar(any(), any(), any()))
                .thenReturn(ResultadoPagamentoFaturado.repetido(resposta));

        ResponseEntity<?> r = controller.createPagamento(pedido(1, "100.00"));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(resposta, r.getBody());
        verifyNoInteractions(pagamentoRepository, contaCorrenteRepository);
    }

    @Test
    void ativaRecusaFiscalPropagaSemAlteracao() {
        when(pagamentoFaturadoService.faturacaoAtiva(TENANT_ID)).thenReturn(true);
        RecusaFiscalException recusa = new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY,
                "DATA_PAGAMENTO_RETROATIVA", "Com a faturação ativa, a data do pagamento tem de ser a de hoje.",
                "dataPagamento");
        when(pagamentoFaturadoService.registar(any(), any(), any())).thenThrow(recusa);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> controller.createPagamento(pedido(1, "100.00")));

        assertSame(recusa, e);
        verifyNoInteractions(pagamentoRepository, contaCorrenteRepository);
    }

    // ------------------------------------------------------------------ ligação JSON (A2)

    @Test
    void payloadAntigoLigaAoPedidoSemErroENuncaCopiaOId() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        String json = "{\"id\":99,\"honorarioId\":1,\"valorPago\":10,\"dataPagamento\":\"2026-10-04\","
                + "\"metodo\":\"DINHEIRO\",\"extra\":\"x\"}";

        PagamentoRequest req = mapper.readValue(json, PagamentoRequest.class);

        assertEquals(1, req.honorarioId());
        assertEquals(0, BigDecimal.TEN.compareTo(req.valorPago()));
        assertEquals(LocalDate.of(2026, 10, 4), req.dataPagamento());
        assertEquals("DINHEIRO", req.metodo());
        assertNull(req.retencaoPercentagem());
        assertNull(req.chaveIdempotencia());
        Pagamento legado = req.paraPagamentoLegado();
        assertNull(legado.getId(), "o id do corpo nunca chega à entidade");
        assertEquals(1, legado.getHonorarioId());
    }

    @Test
    void ativaUsaSoOTenantDoPrincipal() {
        when(pagamentoFaturadoService.faturacaoAtiva(any())).thenReturn(true);
        when(pagamentoFaturadoService.registar(any(), any(), any()))
                .thenReturn(ResultadoPagamentoFaturado.novo(resposta()));

        controller.createPagamento(pedido(1, "100.00"));

        verify(pagamentoFaturadoService).faturacaoAtiva(TENANT_ID);
        verify(pagamentoFaturadoService).registar(org.mockito.ArgumentMatchers.eq(TENANT_ID), any(), any());
    }
}
