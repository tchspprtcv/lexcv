package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.NotaCreditoResponse;
import com.lexcv.dtos.PreVisualizacaoNotaCreditoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.ContaCorrente;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 135 (NCRD-01..03): contrato de {@link NotaCreditoService}.
 *
 * <p>Emissão atómica e idempotente da Nota de Crédito sobre uma Fatura-Recibo, com a ordem de
 * locks da Phase 134 (configuração → cliente → processo → conta corrente → série), nenhuma leitura
 * sem lock das linhas bloqueadas (OSIV), todas as recusas antes de qualquer escrita, o estorno
 * negativo no mesmo honorário e a repetição pela chave de idempotência. A FR de referência é a dos
 * vetores do plano 03: 120 000 CVE, IVA 15%, retenção 20%.
 */
class NotaCreditoServiceTest {

    /** 13:00Z = 12:00 em Cabo Verde (UTC-1): "hoje" é 15 de junho. */
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-06-15T13:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    private final UUID tenant = UUID.randomUUID();
    private final UUID chave = UUID.randomUUID();
    private final UUID serieId = UUID.randomUUID();
    private final UUID ncId = UUID.randomUUID();

    private ConfiguracaoFiscalRepository configuracaoRepo;
    private SerieFiscalRepository serieRepo;
    private HonorarioRepository honorarioRepo;
    private ProcessoRepository processoRepo;
    private ClienteRepository clienteRepo;
    private ContaCorrenteRepository ccRepo;
    private PagamentoRepository pagamentoRepo;
    private DocumentoFiscalRepository documentoRepo;
    private DocumentoFiscalLinhaRepository linhaRepo;
    private ComunicacaoFiscalRepository comunicacaoRepo;
    private NumeracaoService numeracao;
    private AuditoriaFiscalService auditoria;

    private UserPrincipal autor;
    private Cliente cliente;
    private Processo processo;
    private ContaCorrente cc;
    private DocumentoFiscal fr;

    @BeforeEach
    void preparar() {
        configuracaoRepo = mock(ConfiguracaoFiscalRepository.class);
        serieRepo = mock(SerieFiscalRepository.class);
        honorarioRepo = mock(HonorarioRepository.class);
        processoRepo = mock(ProcessoRepository.class);
        clienteRepo = mock(ClienteRepository.class);
        ccRepo = mock(ContaCorrenteRepository.class);
        pagamentoRepo = mock(PagamentoRepository.class);
        documentoRepo = mock(DocumentoFiscalRepository.class);
        linhaRepo = mock(DocumentoFiscalLinhaRepository.class);
        comunicacaoRepo = mock(ComunicacaoFiscalRepository.class);
        numeracao = mock(NumeracaoService.class);
        auditoria = mock(AuditoriaFiscalService.class);

        autor = UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
        // Dados ATUAIS do cliente, diferentes dos da FR: a NC tem de usar a fotografia da FR.
        cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(tenant).nif("999999999")
                .nome("Nome Atual Diferente").morada("Morada Atual").localidade("Mindelo").build();
        processo = Processo.builder().id(UUID.randomUUID()).tenantId(tenant).clienteId(cliente.getId())
                .numeroProcesso("P-1").build();
        cc = ContaCorrente.builder().id(3).clienteId(cliente.getId()).saldo(new BigDecimal("120500.00")).build();
        fr = frDeReferencia().build();
    }

    private DocumentoFiscal.DocumentoFiscalBuilder frDeReferencia() {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(tenant).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-FR-2026").ano(2026).numero(7L)
                .numeroFormatado("SIM-FR-2026/7").dataEmissao(LocalDate.of(2026, 3, 2))
                .emitidoEm(Instant.parse("2026-03-02T10:00:00Z"))
                .emitenteNif("123456789").emitenteFirma("Silva & Associados").emitenteMorada("Av. 1")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("234567891").adquirenteNome("Maria Lopes").adquirenteMorada("Rua da Praia 5")
                .adquirenteLocalidade(null)
                .clienteId(cliente.getId()).processoId(processo.getId()).honorarioId(7).pagamentoId(55)
                .metodoPagamento("TRANSFERENCIA").meioPagamentoCodigo("TB").moeda("CVE")
                .taxaIva(new BigDecimal("15.0000")).taxaRetencao(new BigDecimal("20.0000"))
                .totalBase(new BigDecimal("104347.83")).totalIva(new BigDecimal("15652.17"))
                .totalRetencao(new BigDecimal("20869.57")).totalDocumento(new BigDecimal("120000.00"))
                .valorLiquido(new BigDecimal("99130.43")).chaveIdempotencia(UUID.randomUUID());
    }

    private DocumentoFiscal nc(UUID id, String numero, String base, String iva, String ret, String total,
                               MotivoNotaCredito motivo, String texto, UUID chaveNc) {
        return DocumentoFiscal.builder().id(id).tenantId(tenant).tipo(TipoDocumentoFiscal.NC)
                .ambiente(AmbienteFiscal.SIMULADO).numeroFormatado(numero).dataEmissao(HOJE)
                .documentoOrigemId(fr.getId()).honorarioId(7).pagamentoId(56)
                .totalBase(new BigDecimal(base)).totalIva(new BigDecimal(iva)).totalRetencao(new BigDecimal(ret))
                .totalDocumento(new BigDecimal(total)).motivoCodigo(motivo).motivoTexto(texto)
                .chaveIdempotencia(chaveNc).build();
    }

    /** NC parcial de 20 000 já emitida (vetor de referência do plano 03). */
    private DocumentoFiscal ncParcial20000(UUID id, UUID chaveNc) {
        return nc(id, "SIM-NC-2026/1", "17391.30", "2608.70", "3478.26", "20000.00",
                MotivoNotaCredito.CORRECAO_VALOR, "Valor faturado a mais", chaveNc);
    }

    /** NC que fecha a FR depois da parcial de 20 000. */
    private DocumentoFiscal ncRemanescente(UUID id, UUID chaveNc) {
        return nc(id, "SIM-NC-2026/2", "86956.53", "13043.47", "17391.31", "100000.00",
                MotivoNotaCredito.ANULACAO_TOTAL, "Anulação do serviço", chaveNc);
    }

    private NotaCreditoService servico() {
        return new NotaCreditoService(configuracaoRepo, serieRepo, honorarioRepo, processoRepo, clienteRepo,
                ccRepo, pagamentoRepo, documentoRepo, linhaRepo, comunicacaoRepo, numeracao, auditoria, RELOGIO);
    }

    private static ConfiguracaoFiscal cfg(boolean ativa) {
        return ConfiguracaoFiscal.builder().id(UUID.randomUUID()).nif("888888888").firma("Firma Atual Diferente")
                .morada("Morada cfg").localidade("Praia").emailContacto("a@b.cv").telefoneContacto("260")
                .regimeIva(RegimeIva.NORMAL).ativa(ativa).build();
    }

    private NotaCreditoRequest total() {
        return new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", "  Anulação do serviço  ", chave);
    }

    private NotaCreditoRequest parcial(String valor) {
        return new NotaCreditoRequest("PARCIAL", new BigDecimal(valor), "CORRECAO_VALOR", "Valor faturado a mais",
                chave);
    }

    private static void definirId(Object entidade, Object id) {
        try {
            Field f = entidade.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entidade, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stubs do caminho feliz; os testes de recusa sobrepõem um deles. */
    private void tudoPresente(boolean ativa, List<DocumentoFiscal> notas, LocalDate dataNumero) {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(ativa)));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.empty());
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(fr));
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenReturn(Optional.of(cliente));
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.of(processo));
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.of(processo.getId()));
        when(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenant, fr.getId()))
                .thenReturn(notas);
        when(ccRepo.criarSeNaoExiste(cliente.getId())).thenReturn(0);
        when(ccRepo.bloquearPorCliente(cliente.getId())).thenReturn(Optional.of(cc));
        when(ccRepo.save(any(ContaCorrente.class))).thenAnswer(i -> i.getArgument(0));
        when(pagamentoRepo.save(any(Pagamento.class))).thenAnswer(i -> {
            Pagamento p = i.getArgument(0);
            p.setId(91);
            return p;
        });
        when(numeracao.proximoNumero(tenant, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO))
                .thenReturn(new NumeroFiscalAtribuido(serieId, "SIM-NC-2026", 2026, 3, dataNumero));
        when(documentoRepo.save(any(DocumentoFiscal.class))).thenAnswer(i -> {
            DocumentoFiscal d = i.getArgument(0);
            definirId(d, ncId);
            return d;
        });
        when(linhaRepo.save(any(DocumentoFiscalLinha.class))).thenAnswer(i -> i.getArgument(0));
        when(comunicacaoRepo.save(any(ComunicacaoFiscal.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void tudoPresente() {
        tudoPresente(true, List.of(), HOJE);
    }

    private void assertNadaEscrito() {
        verify(pagamentoRepo, never()).save(any());
        verify(ccRepo, never()).criarSeNaoExiste(any());
        verify(ccRepo, never()).save(any());
        verify(numeracao, never()).proximoNumero(any(), any(), any());
        verify(documentoRepo, never()).save(any());
        verify(linhaRepo, never()).save(any());
        verify(comunicacaoRepo, never()).save(any());
        verify(auditoria, never()).registarEmissaoNotaCredito(any(), any(), any(), any(), any());
    }

    private static void assertRecusa(RecusaFiscalException e, HttpStatus status, String codigo) {
        assertEquals(status, e.getStatus(), e.getCodigo());
        assertEquals(codigo, e.getCodigo());
    }

    private RecusaFiscalException recusa(NotaCreditoRequest pedido) {
        return assertThrows(RecusaFiscalException.class,
                () -> servico().emitir(tenant, autor, fr.getId(), pedido));
    }

    private DocumentoFiscal documentoGravado() {
        ArgumentCaptor<DocumentoFiscal> captor = ArgumentCaptor.forClass(DocumentoFiscal.class);
        verify(documentoRepo).save(captor.capture());
        return captor.getValue();
    }

    private Pagamento estornoGravado() {
        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepo).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ caminho feliz

    @Test
    void ordemDosLocksEChamadasNoCaminhoFeliz() {
        tudoPresente();

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), total());

        assertTrue(r.novo());
        InOrder ordem = inOrder(serieRepo, configuracaoRepo, documentoRepo, processoRepo, clienteRepo,
                honorarioRepo, ccRepo, pagamentoRepo, numeracao, linhaRepo, comunicacaoRepo, auditoria);
        ordem.verify(serieRepo).definirLockTimeoutLocal();
        ordem.verify(configuracaoRepo).bloquearPorTenant(tenant);
        ordem.verify(documentoRepo).findByTenantIdAndChaveIdempotencia(tenant, chave);
        ordem.verify(documentoRepo).findByIdAndTenantId(fr.getId(), tenant);
        ordem.verify(clienteRepo).bloquearPorIdETenant(cliente.getId(), tenant);
        ordem.verify(processoRepo).bloquearPorIdETenant(processo.getId(), tenant);
        ordem.verify(honorarioRepo).processoIdPorId(7);
        ordem.verify(documentoRepo).findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenant, fr.getId());
        ordem.verify(ccRepo).criarSeNaoExiste(cliente.getId());
        ordem.verify(ccRepo).bloquearPorCliente(cliente.getId());
        ordem.verify(pagamentoRepo).save(any(Pagamento.class));
        ordem.verify(numeracao).proximoNumero(tenant, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO);
        ordem.verify(documentoRepo).save(any(DocumentoFiscal.class));
        ordem.verify(linhaRepo).save(any(DocumentoFiscalLinha.class));
        ordem.verify(comunicacaoRepo).save(any(ComunicacaoFiscal.class));
        ordem.verify(auditoria).registarEmissaoNotaCredito(tenant, autor, ncId, "SIM-NC-2026/3", "SIM-FR-2026/7");
    }

    @Test
    void emissaoNuncaLeSemLockAsLinhasQueBloqueia() {
        tudoPresente();

        servico().emitir(tenant, autor, fr.getId(), total());

        verify(clienteRepo, never()).findById(any());
        verify(processoRepo, never()).findById(any());
        verify(configuracaoRepo, never()).findByTenantId(any());
        verify(ccRepo, never()).findByClienteId(any());
        verify(honorarioRepo, never()).findById(any());
    }

    @Test
    void estornoNegativoNoHonorarioDaFrComDataDeHojeEMetodoDaFr() {
        tudoPresente();

        servico().emitir(tenant, autor, fr.getId(), total());

        Pagamento estorno = estornoGravado();
        assertEquals(7, estorno.getHonorarioId());
        assertEquals(new BigDecimal("-120000.00"), estorno.getValorPago());
        assertEquals(HOJE, estorno.getDataPagamento());
        assertEquals("TRANSFERENCIA", estorno.getMetodo());
    }

    @Test
    void contaCorrenteDebitadaDoTotalDaNc() {
        tudoPresente(true, List.of(), HOJE);

        servico().emitir(tenant, autor, fr.getId(), parcial("20000.00"));

        assertEquals(new BigDecimal("100500.00"), cc.getSaldo());
        verify(ccRepo).save(cc);
    }

    @Test
    void contaCorrenteComSaldoNuloContaComoZero() {
        cc.setSaldo(null);
        tudoPresente();

        servico().emitir(tenant, autor, fr.getId(), total());

        assertEquals(new BigDecimal("-120000.00"), cc.getSaldo());
    }

    @Test
    void documentoNcFotografaAFrENaoOClienteNemAConfiguracaoAtuais() {
        tudoPresente();

        servico().emitir(tenant, autor, fr.getId(), total());

        DocumentoFiscal d = documentoGravado();
        assertEquals(tenant, d.getTenantId());
        assertEquals(TipoDocumentoFiscal.NC, d.getTipo());
        assertEquals(AmbienteFiscal.SIMULADO, d.getAmbiente());
        assertEquals(serieId, d.getSerieId());
        assertEquals("SIM-NC-2026", d.getSerieCodigo());
        assertEquals(2026, d.getAno());
        assertEquals(3L, d.getNumero());
        assertEquals("SIM-NC-2026/3", d.getNumeroFormatado());
        assertEquals(HOJE, d.getDataEmissao());
        assertEquals(RELOGIO.instant(), d.getEmitidoEm());
        // Emitente e adquirente: fotografia da FR (não a configuração nem o cliente atuais).
        assertEquals("123456789", d.getEmitenteNif());
        assertEquals("Silva & Associados", d.getEmitenteFirma());
        assertEquals("Av. 1", d.getEmitenteMorada());
        assertEquals("Praia", d.getEmitenteLocalidade());
        assertEquals(RegimeIva.NORMAL, d.getEmitenteRegimeIva());
        assertEquals("234567891", d.getAdquirenteNif());
        assertEquals("Maria Lopes", d.getAdquirenteNome());
        assertEquals("Rua da Praia 5", d.getAdquirenteMorada());
        assertNull(d.getAdquirenteLocalidade());
        assertEquals(cliente.getId(), d.getClienteId());
        assertEquals(processo.getId(), d.getProcessoId());
        assertEquals(7, d.getHonorarioId());
        assertEquals(91, d.getPagamentoId(), "pagamento_id da NC = id do seu estorno");
        assertEquals(fr.getId(), d.getDocumentoOrigemId());
        assertEquals(MotivoNotaCredito.ANULACAO_TOTAL, d.getMotivoCodigo());
        assertEquals("Anulação do serviço", d.getMotivoTexto());
        assertEquals("TRANSFERENCIA", d.getMetodoPagamento());
        assertEquals("TB", d.getMeioPagamentoCodigo());
        assertEquals("CVE", d.getMoeda());
        assertEquals(new BigDecimal("15.0000"), d.getTaxaIva());
        assertEquals(new BigDecimal("20.0000"), d.getTaxaRetencao());
        assertEquals(new BigDecimal("104347.83"), d.getTotalBase());
        assertEquals(new BigDecimal("15652.17"), d.getTotalIva());
        assertEquals(new BigDecimal("20869.57"), d.getTotalRetencao());
        assertEquals(new BigDecimal("120000.00"), d.getTotalDocumento());
        assertEquals(new BigDecimal("99130.43"), d.getValorLiquido());
        assertEquals(chave, d.getChaveIdempotencia());
        assertEquals(autor.getUserId(), d.getEmitidoPorId());
        assertEquals("Ana Emissora", d.getEmitidoPorNome());
    }

    @Test
    void ncParcialUsaAComposicaoComATaxaDaFr() {
        tudoPresente();

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), parcial("20000.00"));

        DocumentoFiscal d = documentoGravado();
        assertEquals(new BigDecimal("17391.30"), d.getTotalBase());
        assertEquals(new BigDecimal("2608.70"), d.getTotalIva());
        assertEquals(new BigDecimal("3478.26"), d.getTotalRetencao());
        assertEquals(new BigDecimal("20000.00"), d.getTotalDocumento());
        assertEquals(new BigDecimal("16521.74"), d.getValorLiquido());
        assertEquals(0, new BigDecimal("15").compareTo(d.getTaxaIva()));
        assertEquals(MotivoNotaCredito.CORRECAO_VALOR, d.getMotivoCodigo());
        assertEquals(new BigDecimal("100000.00"), r.resposta().valorCreditavelRestante());
    }

    @Test
    void linhaUnicaEComunicacaoPendente() {
        tudoPresente();

        servico().emitir(tenant, autor, fr.getId(), total());

        ArgumentCaptor<DocumentoFiscalLinha> linha = ArgumentCaptor.forClass(DocumentoFiscalLinha.class);
        verify(linhaRepo).save(linha.capture());
        DocumentoFiscalLinha l = linha.getValue();
        assertEquals(tenant, l.getTenantId());
        assertEquals(ncId, l.getDocumentoFiscalId());
        assertEquals(1, l.getNumeroLinha());
        assertEquals(TextoDocumentoFiscal.descricaoLinhaNotaCredito("SIM-FR-2026/7"), l.getDescricao());
        assertEquals(0, BigDecimal.ONE.compareTo(l.getQuantidade()));
        assertEquals(new BigDecimal("104347.83"), l.getPrecoUnitario());
        assertEquals(new BigDecimal("104347.83"), l.getValorBase());
        assertEquals(new BigDecimal("15652.17"), l.getValorIva());
        assertEquals(new BigDecimal("20869.57"), l.getValorRetencao());
        assertEquals(new BigDecimal("120000.00"), l.getTotalLinha());

        ArgumentCaptor<ComunicacaoFiscal> com = ArgumentCaptor.forClass(ComunicacaoFiscal.class);
        verify(comunicacaoRepo).save(com.capture());
        assertEquals(tenant, com.getValue().getTenantId());
        assertEquals(ncId, com.getValue().getDocumentoFiscalId());
        assertEquals(AmbienteFiscal.SIMULADO, com.getValue().getAmbiente());
        assertEquals(EstadoComunicacaoFiscal.PENDENTE, com.getValue().getEstado());
        assertEquals(0, com.getValue().getTentativas());
        assertEquals(RELOGIO.instant(), com.getValue().getCreatedAt());
    }

    @Test
    void respostaNovaTrazEstornoComReferenciaDaNcEValorRestante() {
        tudoPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), total());

        assertTrue(r.novo());
        NotaCreditoResponse resp = r.resposta();
        assertEquals(ncId, resp.id());
        assertEquals("SIM-NC-2026/3", resp.numeroFormatado());
        assertEquals("NC", resp.tipo());
        assertEquals(fr.getId(), resp.documentoOrigemId());
        assertEquals("SIM-FR-2026/7", resp.documentoOrigemNumero());
        assertEquals(HOJE, resp.dataEmissao());
        assertEquals(new BigDecimal("100000.00"), resp.totalDocumento());
        assertEquals(new BigDecimal("0.00"), resp.valorCreditavelRestante());
        assertEquals(91, resp.estorno().id());
        assertEquals(new BigDecimal("-100000.00"), resp.estorno().valorPago());
        assertNull(resp.estorno().documentoFiscal());
        assertEquals(ncId, resp.estorno().estorno().id());
        assertEquals("SIM-NC-2026/3", resp.estorno().estorno().numeroFormatado());
    }

    // ------------------------------------------------------------------ recusas antes de escrever

    @Test
    void semChaveRecusa422AntesDeQualquerRepositorio() {
        NotaCreditoRequest semChave = new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", "x", null);

        RecusaFiscalException e = recusa(semChave);

        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "CHAVE_IDEMPOTENCIA_OBRIGATORIA");
        verifyNoInteractions(serieRepo, configuracaoRepo, documentoRepo, processoRepo, clienteRepo, honorarioRepo,
                ccRepo, pagamentoRepo, numeracao, auditoria);
    }

    @Test
    void semConfiguracaoRecusaFaturacaoDesligada() {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertNadaEscrito();
    }

    @Test
    void configuracaoInativaSemChaveGuardadaRecusaFaturacaoDesligada() {
        tudoPresente(false, List.of(), HOJE);

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertNadaEscrito();
        verify(documentoRepo, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void origemInexistenteOuDeOutroTenantRecusa404() {
        tudoPresente();
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(total()), HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO");
        assertNadaEscrito();
        verifyNoInteractions(clienteRepo);
    }

    @Test
    void origemQueEhUmaNcRecusaNcSobreNcAntesDosLocks() {
        tudoPresente();
        DocumentoFiscal outraNc = ncParcial20000(fr.getId(), UUID.randomUUID());
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(outraNc));

        assertRecusa(recusa(total()), HttpStatus.UNPROCESSABLE_ENTITY, "NC_SOBRE_NC");
        assertNadaEscrito();
        verifyNoInteractions(clienteRepo);
        verify(processoRepo, never()).bloquearPorIdETenant(any(), any());
    }

    @Test
    void acimaDoTetoRecusaNcExcedeOriginalSemEscrever() {
        tudoPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);

        RecusaFiscalException e = recusa(parcial("100000.01"));

        assertRecusa(e, HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL");
        assertEquals("valor", e.getCampo());
        assertNadaEscrito();
    }

    @Test
    void frJaTotalmenteCreditadaRecusaNcExcedeOriginal() {
        tudoPresente(true, List.of(ncRemanescente(UUID.randomUUID(), UUID.randomUUID()),
                ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL");
        assertNadaEscrito();
    }

    @Test
    void pedidoInvalidoRecusa422SemEscrever() {
        tudoPresente();
        NotaCreditoRequest semTexto = new NotaCreditoRequest("TOTAL", null, "OUTRO", "   ", chave);

        assertRecusa(recusa(semTexto), HttpStatus.UNPROCESSABLE_ENTITY, "MOTIVO_NC_OBRIGATORIO");
        assertNadaEscrito();
    }

    // ------------------------------------------------------------------ WR-01 (valores confirmados)

    private NotaCreditoRequest totalConfirmado(String total, String creditavel) {
        return new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", "Anulação do serviço", chave,
                new BigDecimal(total), new BigDecimal(creditavel));
    }

    @Test
    void totalConfirmadoDiferenteDoRemanescenteRecusaValoresAlteradosSemEscrever() {
        // Outra NC de 20 000 foi emitida depois da pré-visualização: o TOTAL creditaria 100 000.
        tudoPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);

        assertRecusa(recusa(totalConfirmado("120000.00", "120000.00")), HttpStatus.CONFLICT, "NC_VALORES_ALTERADOS");
        assertNadaEscrito();
    }

    @Test
    void parcialComCreditavelConfirmadoDiferenteRecusaValoresAlterados() {
        tudoPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);
        NotaCreditoRequest pedido = new NotaCreditoRequest("PARCIAL", new BigDecimal("1000.00"), "CORRECAO_VALOR",
                "Valor faturado a mais", chave, new BigDecimal("1000.00"), new BigDecimal("120000.00"));

        assertRecusa(recusa(pedido), HttpStatus.CONFLICT, "NC_VALORES_ALTERADOS");
        assertNadaEscrito();
    }

    @Test
    void valoresConfirmadosIguaisEmitem() {
        tudoPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())), HOJE);

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), totalConfirmado("100000", "100000.00"));

        assertTrue(r.novo());
        assertEquals(0, new BigDecimal("100000.00").compareTo(documentoGravado().getTotalDocumento()));
    }

    @Test
    void processoReatribuidoDebitaEGravaOClienteDaFr() {
        // CR-01 da revisão: o processo mudou de cliente depois da FR (antes da guarda do PUT).
        // A NC debita e regista o cliente que a FR creditou, nunca o cliente atual do processo.
        tudoPresente();
        Processo movido = Processo.builder().id(processo.getId()).tenantId(tenant).clienteId(UUID.randomUUID()).build();
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.of(movido));

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), total());

        assertTrue(r.novo());
        verify(clienteRepo).bloquearPorIdETenant(fr.getClienteId(), tenant);
        verify(clienteRepo, never()).bloquearPorIdETenant(movido.getClienteId(), tenant);
        verify(ccRepo).bloquearPorCliente(fr.getClienteId());
        verify(ccRepo, never()).bloquearPorCliente(movido.getClienteId());
        verify(processoRepo, never()).clienteIdPorIdETenant(any(), any());
        assertEquals(fr.getClienteId(), documentoGravado().getClienteId());
    }

    @Test
    void processoDesaparecidoNoLockRecusaProcessoAlterado() {
        tudoPresente();
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE");
        assertNadaEscrito();
    }

    @Test
    void clienteDaFrApagadoPorFusaoNoLockRecusaProcessoAlterado() {
        // WR-02 da revisão: uma fusão absorveu e apagou o cliente entre a leitura da FR e o lock.
        // A FR continua a existir; é uma corrida que se pode repetir (409), nunca um 404.
        tudoPresente();
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE");
        assertNadaEscrito();
    }

    @Test
    void honorarioDeOutroProcessoRecusa404() {
        tudoPresente();
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.of(UUID.randomUUID()));

        assertRecusa(recusa(total()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
    }

    @Test
    void honorarioApagadoRecusa404() {
        tudoPresente();
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.empty());

        assertRecusa(recusa(total()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
    }

    static Stream<RuntimeException> falhasDeLock() {
        return Stream.of(new PessimisticLockingFailureException("x"), new PessimisticLockException("x"),
                new LockTimeoutException("x"));
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDaConfiguracaoDevolve503(RuntimeException falha) {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenThrow(falha);

        assertRecusa(recusa(total()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertNadaEscrito();
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDoClienteDevolve503(RuntimeException falha) {
        tudoPresente();
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenThrow(falha);

        assertRecusa(recusa(total()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertNadaEscrito();
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDoProcessoDevolve503(RuntimeException falha) {
        tudoPresente();
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenThrow(falha);

        assertRecusa(recusa(total()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertNadaEscrito();
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDaContaCorrenteDevolve503(RuntimeException falha) {
        tudoPresente();
        when(ccRepo.bloquearPorCliente(cliente.getId())).thenThrow(falha);

        assertRecusa(recusa(total()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        verify(pagamentoRepo, never()).save(any());
        verify(documentoRepo, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void esperaNoInsertDaContaCorrenteDevolve503(RuntimeException falha) {
        tudoPresente();
        when(ccRepo.criarSeNaoExiste(cliente.getId())).thenThrow(falha);

        assertRecusa(recusa(total()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        verify(pagamentoRepo, never()).save(any());
    }

    @Test
    void numeroDeOutroDiaRecusa409SemGravarDocumento() {
        tudoPresente(true, List.of(), HOJE.plusDays(1));

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "DATA_EMISSAO_ALTERADA");
        verify(documentoRepo, never()).save(any());
        verify(linhaRepo, never()).save(any());
        verify(comunicacaoRepo, never()).save(any());
        verify(auditoria, never()).registarEmissaoNotaCredito(any(), any(), any(), any(), any());
    }

    @Test
    void serieIndisponivelPropagaSemAlteracao() {
        tudoPresente();
        RecusaFiscalException indisponivel = new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE,
                "SERIE_INDISPONIVEL", "x");
        when(numeracao.proximoNumero(tenant, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO)).thenThrow(indisponivel);

        RecusaFiscalException e = recusa(total());

        assertEquals(indisponivel, e);
        verify(documentoRepo, never()).save(any());
    }

    // ------------------------------------------------------------------ idempotência

    /** Configuração bloqueada, a chave já pertence a {@code guardada}; NC atuais da FR = {@code notas}. */
    private void comGuardada(DocumentoFiscal guardada, boolean ativa, List<DocumentoFiscal> notas) {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(ativa)));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.of(guardada));
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(fr));
        when(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenant, fr.getId()))
                .thenReturn(notas);
        Pagamento estorno = Pagamento.builder().id(56).honorarioId(7)
                .valorPago(guardada.getTotalDocumento() == null ? null : guardada.getTotalDocumento().negate())
                .dataPagamento(HOJE).metodo("TRANSFERENCIA").build();
        when(pagamentoRepo.findById(56)).thenReturn(Optional.of(estorno));
    }

    private void assertNadaDepoisDaChave() {
        assertNadaEscrito();
        verifyNoInteractions(clienteRepo, honorarioRepo);
        verify(processoRepo, never()).bloquearPorIdETenant(any(), any());
        verify(ccRepo, never()).bloquearPorCliente(any());
    }

    @Test
    void mesmaChaveMesmoPedidoParcialDevolveANcGuardadaSemEscrever() {
        UUID guardadaId = UUID.randomUUID();
        DocumentoFiscal guardada = ncParcial20000(guardadaId, chave);
        comGuardada(guardada, true, List.of(guardada));

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), parcial("20000"));

        assertFalse(r.novo());
        assertEquals(guardadaId, r.resposta().id());
        assertEquals("SIM-NC-2026/1", r.resposta().numeroFormatado());
        assertEquals("SIM-FR-2026/7", r.resposta().documentoOrigemNumero());
        assertEquals(new BigDecimal("20000.00"), r.resposta().totalDocumento());
        assertEquals(new BigDecimal("100000.00"), r.resposta().valorCreditavelRestante());
        assertEquals(56, r.resposta().estorno().id());
        assertEquals(guardadaId, r.resposta().estorno().estorno().id());
        assertNull(r.resposta().estorno().documentoFiscal());
        assertNadaDepoisDaChave();
    }

    @Test
    void mesmaChaveTotalQueFechouAFrDevolveANcGuardada() {
        UUID guardadaId = UUID.randomUUID();
        DocumentoFiscal guardada = ncRemanescente(guardadaId, chave);
        comGuardada(guardada, true, List.of(guardada, ncParcial20000(UUID.randomUUID(), UUID.randomUUID())));

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), total());

        assertFalse(r.novo());
        assertEquals(guardadaId, r.resposta().id());
        assertEquals(new BigDecimal("0.00"), r.resposta().valorCreditavelRestante());
        assertNadaDepoisDaChave();
    }

    @Test
    void mesmaChaveTotalComAFrAindaCreditavelRecusaChaveReutilizada() {
        // A NC guardada é uma parcial e a FR ainda tem valor creditável: um pedido TOTAL é outro pedido.
        DocumentoFiscal guardada = nc(UUID.randomUUID(), "SIM-NC-2026/1", "17391.30", "2608.70", "3478.26",
                "20000.00", MotivoNotaCredito.ANULACAO_TOTAL, "Anulação do serviço", chave);
        comGuardada(guardada, true, List.of(guardada));

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertNadaDepoisDaChave();
    }

    @Test
    void mesmaChaveTotalDeUmaParcialQueOutraNcPosteriorEsgotouRecusaChaveReutilizada() {
        // Nota do plan-checker (a): a FR está esgotada, mas não por esta NC. A parcial guardada não
        // é a NC mais recente da FR, por isso nunca é a repetição de um pedido TOTAL.
        DocumentoFiscal guardada = nc(UUID.randomUUID(), "SIM-NC-2026/1", "17391.30", "2608.70", "3478.26",
                "20000.00", MotivoNotaCredito.ANULACAO_TOTAL, "Anulação do serviço", chave);
        DocumentoFiscal posterior = ncRemanescente(UUID.randomUUID(), UUID.randomUUID());
        comGuardada(guardada, true, List.of(posterior, guardada));

        assertRecusa(recusa(total()), HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertNadaDepoisDaChave();
    }

    @Test
    void parcialIgualAoRemanescenteQueFechouAFrEhIndistinguivelDeUmTotal() {
        // Limitação documentada: não há coluna tipo_credito. Uma parcial igual ao remanescente
        // segue o mesmo caminho de composição do TOTAL (credita exatamente os remanescentes), por
        // isso os dois documentos são idênticos e a repetição TOTAL devolve-a sem dano.
        UUID guardadaId = UUID.randomUUID();
        DocumentoFiscal guardada = nc(guardadaId, "SIM-NC-2026/1", "104347.83", "15652.17", "20869.57",
                "120000.00", MotivoNotaCredito.ANULACAO_TOTAL, "Anulação do serviço", chave);
        comGuardada(guardada, true, List.of(guardada));

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), total());

        assertFalse(r.novo());
        assertEquals(guardadaId, r.resposta().id());
    }

    static Stream<NotaCreditoRequestVariante> pedidosDiferentes() {
        return Stream.of(
                new NotaCreditoRequestVariante("valor diferente",
                        c -> new NotaCreditoRequest("PARCIAL", new BigDecimal("20000.01"), "CORRECAO_VALOR",
                                "Valor faturado a mais", c)),
                new NotaCreditoRequestVariante("motivo diferente",
                        c -> new NotaCreditoRequest("PARCIAL", new BigDecimal("20000"), "OUTRO",
                                "Valor faturado a mais", c)),
                new NotaCreditoRequestVariante("texto diferente",
                        c -> new NotaCreditoRequest("PARCIAL", new BigDecimal("20000"), "CORRECAO_VALOR",
                                "Outro texto", c)),
                new NotaCreditoRequestVariante("motivo malformado",
                        c -> new NotaCreditoRequest("PARCIAL", new BigDecimal("20000"), "???",
                                "Valor faturado a mais", c)),
                new NotaCreditoRequestVariante("tipo malformado",
                        c -> new NotaCreditoRequest(null, new BigDecimal("20000"), "CORRECAO_VALOR",
                                "Valor faturado a mais", c)),
                new NotaCreditoRequestVariante("parcial sem valor",
                        c -> new NotaCreditoRequest("PARCIAL", null, "CORRECAO_VALOR",
                                "Valor faturado a mais", c)),
                new NotaCreditoRequestVariante("total com valor",
                        c -> new NotaCreditoRequest("TOTAL", new BigDecimal("20000"), "CORRECAO_VALOR",
                                "Valor faturado a mais", c)));
    }

    record NotaCreditoRequestVariante(String nome, java.util.function.Function<UUID, NotaCreditoRequest> pedido) {
        @Override
        public String toString() {
            return nome;
        }
    }

    @ParameterizedTest
    @MethodSource("pedidosDiferentes")
    void mesmaChaveComPedidoDiferenteRecusaChaveReutilizadaSemEscrever(NotaCreditoRequestVariante variante) {
        DocumentoFiscal guardada = ncParcial20000(UUID.randomUUID(), chave);
        comGuardada(guardada, true, List.of(guardada));

        assertRecusa(recusa(variante.pedido().apply(chave)), HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertNadaDepoisDaChave();
    }

    @Test
    void mesmaChaveDeUmaNcDeOutraOrigemRecusaChaveReutilizada() {
        DocumentoFiscal deOutraFr = DocumentoFiscal.builder().id(UUID.randomUUID()).tenantId(tenant)
                .tipo(TipoDocumentoFiscal.NC).numeroFormatado("SIM-NC-2026/1").documentoOrigemId(UUID.randomUUID())
                .pagamentoId(56).totalDocumento(new BigDecimal("20000.00"))
                .motivoCodigo(MotivoNotaCredito.CORRECAO_VALOR).motivoTexto("Valor faturado a mais")
                .chaveIdempotencia(chave).build();
        comGuardada(deOutraFr, true, List.of());

        assertRecusa(recusa(parcial("20000")), HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertNadaDepoisDaChave();
    }

    @Test
    void chaveDeUmaFaturaReciboRecusaChaveReutilizadaComCopiaDaNc() {
        DocumentoFiscal outraFr = frDeReferencia().id(UUID.randomUUID()).chaveIdempotencia(chave).build();
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(true)));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.of(outraFr));

        RecusaFiscalException e = recusa(total());

        assertRecusa(e, HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertEquals("Este pedido já foi usado com valores diferentes. Reveja os dados e emita a nota de crédito de novo.",
                e.getMessage());
        verify(pagamentoRepo, never()).findById(any());
        assertNadaDepoisDaChave();
    }

    @Test
    void repeticaoFuncionaMesmoComAFaturacaoDesligadaEntretanto() {
        UUID guardadaId = UUID.randomUUID();
        DocumentoFiscal guardada = ncParcial20000(guardadaId, chave);
        comGuardada(guardada, false, List.of(guardada));

        ResultadoNotaCredito r = servico().emitir(tenant, autor, fr.getId(), parcial("20000.00"));

        assertFalse(r.novo());
        assertEquals(guardadaId, r.resposta().id());
        assertNadaDepoisDaChave();
    }

    @Test
    void ncGuardadaSemEstornoEhUmEstadoImpossivel() {
        DocumentoFiscal guardada = ncParcial20000(UUID.randomUUID(), chave);
        comGuardada(guardada, true, List.of(guardada));
        when(pagamentoRepo.findById(56)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> servico().emitir(tenant, autor, fr.getId(), parcial("20000.00")));
    }

    // ------------------------------------------------------------------ pré-visualização

    private void previewPresente(boolean ativa, List<DocumentoFiscal> notas) {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.of(cfg(ativa)));
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(fr));
        when(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenant, fr.getId()))
                .thenReturn(notas);
    }

    private RecusaFiscalException recusaPreview(NotaCreditoRequest pedido) {
        return assertThrows(RecusaFiscalException.class,
                () -> servico().preVisualizar(tenant, fr.getId(), pedido));
    }

    private void assertPreviewSemEfeitos() {
        assertNadaEscrito();
        verifyNoInteractions(serieRepo, clienteRepo, ccRepo, pagamentoRepo, numeracao, auditoria);
        verify(configuracaoRepo, never()).bloquearPorTenant(any());
        verify(processoRepo, never()).bloquearPorIdETenant(any(), any());
    }

    @Test
    void preVisualizacaoDevolveOsValoresDaComposicaoSemEfeitos() {
        List<DocumentoFiscal> notas = List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID()));
        previewPresente(true, notas);

        PreVisualizacaoNotaCreditoResponse p = servico().preVisualizar(tenant, fr.getId(), parcial("30000.00"));

        PreVisualizacaoNotaCreditoResponse esperado = PreVisualizacaoNotaCreditoResponse.de(fr,
                ComposicaoNotaCredito.compor(fr, notas, parcial("30000.00"), HOJE));
        assertEquals(esperado, p);
        assertEquals("NC", p.tipo());
        assertEquals(new BigDecimal("30000.00"), p.total());
        assertEquals(new BigDecimal("100000.00"), p.valorCreditavelAntes());
        assertEquals(new BigDecimal("70000.00"), p.valorCreditavelDepois());
        assertEquals(HOJE, p.dataEmissao());
        assertEquals("Maria Lopes", p.adquirenteNome());
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoComFaturacaoDesligadaRecusa409() {
        previewPresente(false, List.of());

        assertRecusa(recusaPreview(total()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoSemConfiguracaoRecusa409() {
        when(configuracaoRepo.findByTenantId(tenant)).thenReturn(Optional.empty());

        assertRecusa(recusaPreview(total()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoDeOrigemInexistenteRecusa404() {
        previewPresente(true, List.of());
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusaPreview(total()), HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO");
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoSobreUmaNcRecusaNcSobreNc() {
        previewPresente(true, List.of());
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant))
                .thenReturn(Optional.of(ncParcial20000(fr.getId(), UUID.randomUUID())));

        assertRecusa(recusaPreview(total()), HttpStatus.UNPROCESSABLE_ENTITY, "NC_SOBRE_NC");
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoAcimaDoTetoRecusaNcExcedeOriginal() {
        previewPresente(true, List.of(ncParcial20000(UUID.randomUUID(), UUID.randomUUID())));

        assertRecusa(recusaPreview(parcial("100000.01")), HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL");
        assertPreviewSemEfeitos();
    }

    @Test
    void preVisualizacaoNaoExigeChaveDeIdempotencia() {
        previewPresente(true, List.of());

        PreVisualizacaoNotaCreditoResponse p = servico().preVisualizar(tenant, fr.getId(),
                new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", "Anulação", null));

        assertNotNull(p);
        assertEquals(new BigDecimal("120000.00"), p.total());
    }

    // ------------------------------------------------------------------ estrutura

    @Test
    void emitirEhTransacionalEPreVisualizarEhReadOnly() throws Exception {
        Method emitir = NotaCreditoService.class.getMethod("emitir", UUID.class, UserPrincipal.class, UUID.class,
                NotaCreditoRequest.class);
        Transactional tx = emitir.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertFalse(tx.readOnly());
        Method preview = NotaCreditoService.class.getMethod("preVisualizar", UUID.class, UUID.class,
                NotaCreditoRequest.class);
        Transactional txp = preview.getAnnotation(Transactional.class);
        assertNotNull(txp);
        assertTrue(txp.readOnly());
    }

    @Test
    void naoDependeDoContextoDeSegurancaNemDaTaxaVigente() throws Exception {
        String fonte = Files.readString(Path.of("src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java"),
                StandardCharsets.UTF_8);
        assertFalse(fonte.contains("SecurityContextHolder"));
        assertFalse(fonte.contains("ParametroFiscalService"), "a taxa é a fotografada na FR, nunca a vigente");
        assertFalse(fonte.contains("catch (DataIntegrityViolationException"));
        List<String> campos = new ArrayList<>();
        for (Field f : NotaCreditoService.class.getDeclaredFields()) {
            campos.add(f.getType().getSimpleName());
        }
        assertFalse(campos.contains("HttpServletRequest"));
    }
}
