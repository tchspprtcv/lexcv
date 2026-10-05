package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ClienteMergeRequest;
import com.lexcv.models.Cliente;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (D-14, D-15, R-01, EMIS-09): guardas 409 dos caminhos de eliminação e re-apontamento
 * dos documentos fiscais na fusão de clientes. Os locks de linha (cliente/processo) têm de ser a
 * primeira leitura da linha e anteceder a verificação de existência; a fusão bloqueia os dois
 * clientes por ordem ascendente de UUID. A prova sob concorrência real está em
 * {@code GuardasDocumentoFiscalConcorrenciaIT}.
 */
class ResourceControllerDocumentoFiscalGuardasTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    static final String MSG_PAGAMENTO = "Este pagamento tem uma fatura-recibo emitida e não pode ser apagado.";
    static final String MSG_CLIENTE = "Não é possível apagar este cliente porque tem documentos fiscais emitidos.";
    static final String MSG_PROCESSO = "Não é possível apagar este processo porque tem documentos fiscais emitidos.";
    static final String MSG_HONORARIO = "Não é possível apagar este honorário porque tem documentos fiscais emitidos.";

    private ClienteRepository clienteRepository;
    private ContaCorrenteRepository contaCorrenteRepository;
    private ProcessoRepository processoRepository;
    private HonorarioRepository honorarioRepository;
    private PagamentoRepository pagamentoRepository;
    private DocumentoFiscalService documentoFiscalService;
    private ResourceController controller;

    private Processo processo;
    private Honorario honorario;
    private ContaCorrente contaCorrente;

    @BeforeEach
    void preparar() {
        clienteRepository = mock(ClienteRepository.class);
        contaCorrenteRepository = mock(ContaCorrenteRepository.class);
        processoRepository = mock(ProcessoRepository.class);
        honorarioRepository = mock(HonorarioRepository.class);
        pagamentoRepository = mock(PagamentoRepository.class);
        documentoFiscalService = mock(DocumentoFiscalService.class);
        controller = new ResourceController(
                clienteRepository, mock(ClienteContactoRepository.class), mock(ClienteNotaRepository.class),
                contaCorrenteRepository, processoRepository, mock(ParteRepository.class),
                mock(FaseProcessualRepository.class), mock(ProcessoFaseRepository.class), mock(EventoRepository.class),
                mock(DocumentoRepository.class), mock(MovimentacaoRepository.class), honorarioRepository,
                pagamentoRepository, mock(ConflictCheckDecisaoRepository.class), mock(PrazoRepository.class),
                mock(UserRepository.class), mock(AuditLogRepository.class), mock(StorageService.class),
                mock(RiscoPrazoService.class), mock(NotificacaoService.class), mock(ClienteAdvogadoRepository.class),
                mock(ClienteAdministrativoRepository.class), mock(DecisaoRepository.class),
                mock(TestemunhaRepository.class), mock(FactoRepository.class),
                mock(ParecerSolicitacaoRepository.class), mock(ResolucaoPapeisService.class),
                mock(PagamentoFaturadoService.class), documentoFiscalService);

        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), TENANT_ID, "Ana", "ana@example.cv",
                Set.of(), Set.of("financeiro:manage", "clientes:edit", "processos:edit"), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        processo = Processo.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).clienteId(UUID.randomUUID()).build();
        honorario = new Honorario();
        honorario.setId(5);
        honorario.setProcessoId(processo.getId());
        contaCorrente = ContaCorrente.builder().id(9).clienteId(processo.getClienteId())
                .saldo(new BigDecimal("500.00")).build();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    // ---------------------------------------------------------------- deletePagamento

    private void pagamentoExistente() {
        Pagamento pag = Pagamento.builder().id(42).honorarioId(5).valorPago(new BigDecimal("100.00")).build();
        when(pagamentoRepository.findById(42)).thenReturn(Optional.of(pag));
        when(honorarioRepository.findById(5)).thenReturn(Optional.of(honorario));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(contaCorrenteRepository.debitar(processo.getClienteId(), new BigDecimal("100.00"))).thenReturn(1);
    }

    @Test
    void apagarPagamentoFaturadoDevolve409SemTocarNaContaCorrente() {
        pagamentoExistente();
        when(documentoFiscalService.existeParaPagamento(TENANT_ID, 42)).thenReturn(true);

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_PAGAMENTO, corpo(r).get("message"));
        assertEquals("PAGAMENTO_FATURADO", corpo(r).get("code"));
        assertEquals(new BigDecimal("500.00"), contaCorrente.getSaldo());
        verify(contaCorrenteRepository, never()).save(any());
        verify(contaCorrenteRepository, never()).debitar(any(), any());
        verify(pagamentoRepository, never()).deleteById(anyInt());
    }

    static final String MSG_ESTORNO = "Este estorno pertence a uma nota de crédito emitida e não pode ser apagado.";

    @Test
    void apagarEstornoDeNotaDeCreditoDevolve409PagamentoEstornoSemTocarNaContaCorrente() {
        pagamentoExistente();
        when(documentoFiscalService.eEstornoDeNotaCredito(TENANT_ID, 42)).thenReturn(true);
        // O estorno também é "faturado" (a NC tem pagamento_id = estorno): a guarda própria vem antes.
        when(documentoFiscalService.existeParaPagamento(TENANT_ID, 42)).thenReturn(true);

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_ESTORNO, corpo(r).get("message"));
        assertEquals("PAGAMENTO_ESTORNO", corpo(r).get("code"));
        assertEquals(new BigDecimal("500.00"), contaCorrente.getSaldo());
        verify(contaCorrenteRepository, never()).save(any());
        verify(contaCorrenteRepository, never()).debitar(any(), any());
        verify(pagamentoRepository, never()).deleteById(anyInt());
        verify(documentoFiscalService, never()).existeParaPagamento(any(), any());
    }

    @Test
    void apagarPagamentoFaturadoQueNaoEhEstornoMantem409PagamentoFaturado() {
        pagamentoExistente();
        when(documentoFiscalService.eEstornoDeNotaCredito(TENANT_ID, 42)).thenReturn(false);
        when(documentoFiscalService.existeParaPagamento(TENANT_ID, 42)).thenReturn(true);

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("PAGAMENTO_FATURADO", corpo(r).get("code"));
        verify(contaCorrenteRepository, never()).debitar(any(), any());
    }

    @Test
    void apagarEstornoDeOutroTenantNaoConsultaAGuardaDoEstorno() {
        pagamentoExistente();
        processo.setTenantId(UUID.randomUUID());

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verify(documentoFiscalService, never()).eEstornoDeNotaCredito(any(), any());
    }

    @Test
    void postPagamentoComValorNegativoEFaturacaoDesligadaEhRecusado() {
        // CONTEXT: o estorno só é criado pelo NotaCreditoService, nunca por POST /pagamentos.
        when(honorarioRepository.findById(5)).thenReturn(Optional.of(honorario));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));

        ResponseEntity<?> r = controller.createPagamento(new com.lexcv.dtos.PagamentoRequest(5,
                new BigDecimal("-100.00"), java.time.LocalDate.of(2026, 10, 5), "DINHEIRO", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        verify(pagamentoRepository, never()).save(any());
        verify(contaCorrenteRepository, never()).debitar(any(), any());
    }

    @Test
    void apagarPagamentoSemDocumentoMantemComportamento() {
        pagamentoExistente();
        when(documentoFiscalService.existeParaPagamento(TENANT_ID, 42)).thenReturn(false);

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        // WR-01 da revisão: débito atómico e relativo, sem ler nem gravar o saldo absoluto.
        verify(contaCorrenteRepository).debitar(processo.getClienteId(), new BigDecimal("100.00"));
        verify(contaCorrenteRepository, never()).findByClienteId(any());
        verify(contaCorrenteRepository, never()).save(any());
        verify(processoRepository, never()).clienteIdPorIdETenant(any(), any());
        verify(pagamentoRepository).deleteById(42);
    }

    @Test
    void apagarPagamentoDepoisDeUmaFusaoDebitaOClienteAtualDoProcesso() {
        pagamentoExistente();
        UUID novoCliente = UUID.randomUUID();
        when(contaCorrenteRepository.debitar(processo.getClienteId(), new BigDecimal("100.00"))).thenReturn(0);
        when(processoRepository.clienteIdPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(novoCliente));

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        verify(contaCorrenteRepository).debitar(novoCliente, new BigDecimal("100.00"));
        verify(pagamentoRepository).deleteById(42);
    }

    @Test
    void apagarPagamentoDeClienteSemContaCorrenteNaoRepete() {
        pagamentoExistente();
        when(contaCorrenteRepository.debitar(processo.getClienteId(), new BigDecimal("100.00"))).thenReturn(0);
        when(processoRepository.clienteIdPorIdETenant(processo.getId(), TENANT_ID))
                .thenReturn(Optional.of(processo.getClienteId()));

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        verify(contaCorrenteRepository, times(1)).debitar(any(), any());
        verify(pagamentoRepository).deleteById(42);
    }

    @Test
    void apagarPagamentoDeOutroTenantNaoConsultaDocumentos() {
        pagamentoExistente();
        processo.setTenantId(UUID.randomUUID());

        ResponseEntity<?> r = controller.deletePagamento(42);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verify(documentoFiscalService, never()).existeParaPagamento(any(), any());
    }

    // ---------------------------------------------------------------- getClienteContaCorrente

    @Test
    void contaCorrenteEmFaltaEhCriadaSemCorridaENuncaComSave() {
        Cliente cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        ContaCorrente criada = ContaCorrente.builder().id(9).clienteId(cliente.getId()).saldo(BigDecimal.ZERO).build();
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(contaCorrenteRepository.findByClienteId(cliente.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(criada));

        ResponseEntity<?> r = controller.getClienteContaCorrente(cliente.getId());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(BigDecimal.ZERO, corpo(r).get("saldo"));
        verify(contaCorrenteRepository).criarSeNaoExiste(cliente.getId());
        verify(contaCorrenteRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- deleteHonorario

    private void honorarioExistente() {
        when(honorarioRepository.findById(5)).thenReturn(Optional.of(honorario));
        when(processoRepository.findById(processo.getId())).thenReturn(Optional.of(processo));
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));
    }

    @Test
    void apagarHonorarioComDocumentosDevolve409AntesDaGuardaDePagamentos() {
        honorarioExistente();
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(Pagamento.builder().id(1).build()));
        when(documentoFiscalService.existeParaHonorario(TENANT_ID, 5)).thenReturn(true);

        ResponseEntity<?> r = controller.deleteHonorario(5);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_HONORARIO, corpo(r).get("message"));
        assertEquals("HONORARIO_COM_DOCUMENTOS_FISCAIS", corpo(r).get("code"));
        verify(honorarioRepository, never()).deleteById(anyInt());
        InOrder ordem = inOrder(processoRepository, documentoFiscalService);
        ordem.verify(processoRepository).bloquearPorIdETenant(processo.getId(), TENANT_ID);
        ordem.verify(documentoFiscalService).existeParaHonorario(TENANT_ID, 5);
    }

    @Test
    void apagarHonorarioSemDocumentosComPagamentosMantem409Antigo() {
        honorarioExistente();
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of(Pagamento.builder().id(1).build()));

        ResponseEntity<?> r = controller.deleteHonorario(5);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("Não é possível eliminar um honorário com pagamentos registados", corpo(r).get("message"));
        verify(honorarioRepository, never()).deleteById(anyInt());
    }

    @Test
    void apagarHonorarioSemDocumentosNemPagamentosDevolve204() {
        honorarioExistente();
        when(pagamentoRepository.findByHonorarioId(5)).thenReturn(List.of());

        ResponseEntity<?> r = controller.deleteHonorario(5);

        assertEquals(HttpStatus.NO_CONTENT, r.getStatusCode());
        verify(processoRepository).bloquearPorIdETenant(processo.getId(), TENANT_ID);
        verify(honorarioRepository).deleteById(5);
    }

    @Test
    void apagarHonorarioDeOutroTenantDevolve404SemLock() {
        honorarioExistente();
        processo.setTenantId(UUID.randomUUID());

        ResponseEntity<?> r = controller.deleteHonorario(5);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verify(processoRepository, never()).bloquearPorIdETenant(any(), any());
        verify(documentoFiscalService, never()).existeParaHonorario(any(), any());
    }

    // ---------------------------------------------------------------- deleteCliente

    @Test
    void apagarClienteInexistenteOuDeOutroTenantDevolve404PeloLock() {
        UUID id = UUID.randomUUID();
        when(clienteRepository.bloquearPorIdETenant(id, TENANT_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.deleteCliente(id);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Cliente não encontrado", corpo(r).get("message"));
        verify(clienteRepository, never()).findById(any());
        verify(clienteRepository, never()).delete(any());
    }

    @Test
    void apagarClienteComDocumentosDevolve409SemApagarNada() {
        Cliente cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(clienteRepository.bloquearPorIdETenant(cliente.getId(), TENANT_ID)).thenReturn(Optional.of(cliente));
        when(documentoFiscalService.existeParaCliente(TENANT_ID, cliente.getId())).thenReturn(true);

        ResponseEntity<?> r = controller.deleteCliente(cliente.getId());

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_CLIENTE, corpo(r).get("message"));
        assertEquals("CLIENTE_COM_DOCUMENTOS_FISCAIS", corpo(r).get("code"));
        verify(clienteRepository, never()).delete(any());
        verify(contaCorrenteRepository, never()).delete(any());
        verify(contaCorrenteRepository, never()).findByClienteId(any());
    }

    @Test
    void apagarClienteSemDocumentosBloqueiaPrimeiroEApagaComoAntes() {
        Cliente cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        ContaCorrente cc = ContaCorrente.builder().id(3).clienteId(cliente.getId()).saldo(BigDecimal.ZERO).build();
        when(clienteRepository.bloquearPorIdETenant(cliente.getId(), TENANT_ID)).thenReturn(Optional.of(cliente));
        when(contaCorrenteRepository.findByClienteId(cliente.getId())).thenReturn(Optional.of(cc));

        ResponseEntity<?> r = controller.deleteCliente(cliente.getId());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("Cliente removido com sucesso!", corpo(r).get("message"));
        InOrder ordem = inOrder(clienteRepository, documentoFiscalService, contaCorrenteRepository);
        ordem.verify(clienteRepository).bloquearPorIdETenant(cliente.getId(), TENANT_ID);
        ordem.verify(documentoFiscalService).existeParaCliente(TENANT_ID, cliente.getId());
        ordem.verify(contaCorrenteRepository).delete(cc);
        ordem.verify(clienteRepository).delete(cliente);
        verify(clienteRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------- deleteProcesso

    @Test
    void apagarProcessoInexistenteDevolve404PeloLock() {
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.deleteProcesso(processo.getId());

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Processo não encontrado", corpo(r).get("message"));
        verify(processoRepository, never()).findById(any());
        verify(processoRepository, never()).delete(any());
    }

    @Test
    void apagarProcessoComDocumentosDevolve409() {
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));
        when(documentoFiscalService.existeParaProcesso(TENANT_ID, processo.getId())).thenReturn(true);

        ResponseEntity<?> r = controller.deleteProcesso(processo.getId());

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_PROCESSO, corpo(r).get("message"));
        assertEquals("PROCESSO_COM_DOCUMENTOS_FISCAIS", corpo(r).get("code"));
        verify(processoRepository, never()).delete(any());
    }

    @Test
    void apagarProcessoSemDocumentosBloqueiaPrimeiroEApaga() {
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));

        ResponseEntity<?> r = controller.deleteProcesso(processo.getId());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("Processo removido com sucesso!", corpo(r).get("message"));
        InOrder ordem = inOrder(processoRepository, documentoFiscalService);
        ordem.verify(processoRepository).bloquearPorIdETenant(processo.getId(), TENANT_ID);
        ordem.verify(documentoFiscalService).existeParaProcesso(TENANT_ID, processo.getId());
        ordem.verify(processoRepository).delete(processo);
        verify(processoRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------- updateProcesso (CR-01 da 135)

    static final String MSG_PROCESSO_MUDAR_CLIENTE =
            "Não é possível mudar o cliente deste processo porque tem documentos fiscais emitidos.";

    private Processo pedidoProcesso(UUID clienteId) {
        Cliente destino = Cliente.builder().id(clienteId).tenantId(TENANT_ID).nome("Destino").build();
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(destino));
        return Processo.builder().clienteId(clienteId).numeroProcesso("P-2").build();
    }

    @Test
    void mudarClienteDeProcessoComDocumentosFiscaisDevolve409SemAlterar() {
        UUID clienteOriginal = processo.getClienteId();
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));
        when(documentoFiscalService.existeParaProcesso(TENANT_ID, processo.getId())).thenReturn(true);

        ResponseEntity<?> r = controller.updateProcesso(processo.getId(), pedidoProcesso(UUID.randomUUID()));

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals(MSG_PROCESSO_MUDAR_CLIENTE, corpo(r).get("message"));
        assertEquals("PROCESSO_COM_DOCUMENTOS_FISCAIS", corpo(r).get("code"));
        assertEquals(clienteOriginal, processo.getClienteId());
        verify(processoRepository, never()).save(any());
        verify(processoRepository, never()).findById(any());
    }

    @Test
    void editarProcessoComDocumentosFiscaisSemMudarClienteGrava() {
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));
        when(processoRepository.save(any(Processo.class))).thenAnswer(i -> i.getArgument(0));
        when(documentoFiscalService.existeParaProcesso(TENANT_ID, processo.getId())).thenReturn(true);

        ResponseEntity<?> r = controller.updateProcesso(processo.getId(), pedidoProcesso(processo.getClienteId()));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("P-2", processo.getNumeroProcesso());
        verify(documentoFiscalService, never()).existeParaProcesso(any(), any());
    }

    @Test
    void mudarClienteDeProcessoSemDocumentosFiscaisBloqueiaPrimeiroEGrava() {
        UUID novoCliente = UUID.randomUUID();
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.of(processo));
        when(processoRepository.save(any(Processo.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> r = controller.updateProcesso(processo.getId(), pedidoProcesso(novoCliente));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(novoCliente, processo.getClienteId());
        InOrder ordem = inOrder(processoRepository, documentoFiscalService);
        ordem.verify(processoRepository).bloquearPorIdETenant(processo.getId(), TENANT_ID);
        ordem.verify(documentoFiscalService).existeParaProcesso(TENANT_ID, processo.getId());
        ordem.verify(processoRepository).save(processo);
    }

    @Test
    void atualizarProcessoInexistenteDevolve404PeloLock() {
        when(processoRepository.bloquearPorIdETenant(processo.getId(), TENANT_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> r = controller.updateProcesso(processo.getId(), pedidoProcesso(UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        verify(processoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- mergeClientes

    private static final UUID MENOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MAIOR = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");

    private Map<String, Object> fundir(UUID primaryId, UUID secondaryId) {
        Cliente primary = Cliente.builder().id(primaryId).tenantId(TENANT_ID).nome("P").build();
        Cliente secondary = Cliente.builder().id(secondaryId).tenantId(TENANT_ID).nome("S").build();
        when(clienteRepository.bloquearPorIdETenant(primaryId, TENANT_ID)).thenReturn(Optional.of(primary));
        when(clienteRepository.bloquearPorIdETenant(secondaryId, TENANT_ID)).thenReturn(Optional.of(secondary));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(documentoFiscalService.repontarCliente(TENANT_ID, secondaryId, primaryId)).thenReturn(3);

        ResponseEntity<?> r = controller.mergeClientes(new ClienteMergeRequest(primaryId, secondaryId));

        assertEquals(HttpStatus.OK, r.getStatusCode());
        InOrder ordem = inOrder(clienteRepository, documentoFiscalService);
        ordem.verify(clienteRepository).bloquearPorIdETenant(MENOR, TENANT_ID);
        ordem.verify(clienteRepository).bloquearPorIdETenant(MAIOR, TENANT_ID);
        ordem.verify(documentoFiscalService).repontarCliente(TENANT_ID, secondaryId, primaryId);
        ordem.verify(clienteRepository).delete(secondary);
        verify(clienteRepository, never()).findById(any());
        return corpo(r);
    }

    @Test
    void fusaoBloqueiaPorOrdemAscendenteERepontaDocumentos_primarioMenor() {
        Map<String, Object> corpo = fundir(MENOR, MAIOR);
        assertEquals(3, corpo.get("moved_documentos_fiscais"));
        assertEquals(MENOR.toString(), corpo.get("primary_id"));
        for (String chave : List.of("moved_processos", "moved_contactos", "moved_notas", "moved_documentos",
                "merged_saldo", "moved_pareceres")) {
            assertTrue(corpo.containsKey(chave), chave);
        }
    }

    @Test
    void fusaoBloqueiaPorOrdemAscendenteERepontaDocumentos_primarioMaior() {
        Map<String, Object> corpo = fundir(MAIOR, MENOR);
        assertEquals(3, corpo.get("moved_documentos_fiscais"));
        assertEquals(MAIOR.toString(), corpo.get("primary_id"));
    }

    @Test
    void fusaoComClientePrincipalOuDuplicadoAusenteDevolve404SemReapontar() {
        when(clienteRepository.bloquearPorIdETenant(any(), any())).thenReturn(Optional.empty());
        ResponseEntity<?> r = controller.mergeClientes(new ClienteMergeRequest(MENOR, MAIOR));
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Cliente principal não encontrado", corpo(r).get("message"));

        when(clienteRepository.bloquearPorIdETenant(MENOR, TENANT_ID))
                .thenReturn(Optional.of(Cliente.builder().id(MENOR).tenantId(TENANT_ID).build()));
        r = controller.mergeClientes(new ClienteMergeRequest(MENOR, MAIOR));
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("Cliente duplicado não encontrado", corpo(r).get("message"));

        verify(documentoFiscalService, never()).repontarCliente(any(), any(), any());
        verify(clienteRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- reflection

    private static Method metodo(String nome) {
        for (Method m : ResourceController.class.getDeclaredMethods()) {
            if (m.getName().equals(nome)) {
                return m;
            }
        }
        throw new AssertionError("método não encontrado: " + nome);
    }

    @Test
    void fronteirasTransacionaisEGatesInalterados() {
        for (String nome : List.of("deleteCliente", "deleteProcesso", "deleteHonorario", "mergeClientes",
                "updateProcesso")) {
            assertNotNull(metodo(nome).getAnnotation(Transactional.class), nome + " tem de ser @Transactional");
        }
        // P-02: estes dois engolem a falha da conta corrente e não podem partilhar uma transação.
        assertNull(metodo("deletePagamento").getAnnotation(Transactional.class));
        assertNull(metodo("createPagamento").getAnnotation(Transactional.class));

        assertEquals("hasAuthority('clientes:edit')", metodo("deleteCliente").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('clientes:edit')", metodo("mergeClientes").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('processos:edit')", metodo("deleteProcesso").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('financeiro:manage')", metodo("deleteHonorario").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('financeiro:manage')", metodo("deletePagamento").getAnnotation(PreAuthorize.class).value());
        assertFalse(metodo("deletePagamento").isAnnotationPresent(Transactional.class));
    }
}
