package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.ContaCorrente;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.Honorario;
import com.lexcv.models.MotivoIsencaoIva;
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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * Phase 134 (EMIS-01, EMIS-03..07, EMIS-10, D-06..D-12, R-01, R-03): fluxo de controlo da emissão
 * atómica {@link PagamentoFaturadoService#registar}. Ordem dos locks (InOrder), idempotência sob o
 * lock da configuração, regra da data, falhas de lock → 503, recusas antes de qualquer escrita.
 * As provas em PostgreSQL real (rollback, concorrência, duplo envio) estão nos ITs do plano 07.
 */
class PagamentoFaturadoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 4);
    private static final BigDecimal TAXA_IVA = new BigDecimal("15");

    private final UUID tenant = UUID.randomUUID();
    private final UUID chave = UUID.randomUUID();
    private final UUID serieId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();

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
    private ParametroFiscalService parametros;
    private AuditoriaFiscalService auditoria;

    private UserPrincipal autor;
    private Cliente cliente;
    private Processo processo;
    private Honorario honorario;
    private ContaCorrente cc;

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
        parametros = mock(ParametroFiscalService.class);
        auditoria = mock(AuditoriaFiscalService.class);

        autor = UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
        cliente = Cliente.builder().id(UUID.randomUUID()).tenantId(tenant).nif("234567891")
                .nome("Maria Lopes").morada("Rua da Praia 5").localidade("  ").build();
        processo = Processo.builder().id(UUID.randomUUID()).tenantId(tenant).clienteId(cliente.getId())
                .numeroProcesso("P-1").build();
        honorario = new Honorario();
        honorario.setId(7);
        honorario.setProcessoId(processo.getId());
        honorario.setDescricao("SEGREDO PROFISSIONAL");
        cc = ContaCorrente.builder().id(3).clienteId(cliente.getId()).saldo(new BigDecimal("500.00")).build();
    }

    private PagamentoFaturadoService servico(Clock clock) {
        return new PagamentoFaturadoService(configuracaoRepo, serieRepo, honorarioRepo, processoRepo, clienteRepo,
                ccRepo, pagamentoRepo, documentoRepo, linhaRepo, comunicacaoRepo, numeracao, parametros,
                auditoria, clock);
    }

    private static ConfiguracaoFiscal cfg(RegimeIva regime, boolean ativa) {
        return ConfiguracaoFiscal.builder().id(UUID.randomUUID()).nif("123456789").firma("Silva & Associados")
                .morada("Av. 1").localidade("Praia").emailContacto("a@b.cv").telefoneContacto("260")
                .regimeIva(regime).motivoIsencaoCodigo(regime == RegimeIva.ISENTO ? "3" : null).ativa(ativa)
                .build();
    }

    private PagamentoRequest req() {
        return new PagamentoRequest(7, new BigDecimal("120000.00"), null, "TRANSFERENCIA",
                new BigDecimal("20"), chave);
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
    private void tudoPresente(ConfiguracaoFiscal c, LocalDate dataNumero) {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(c));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.empty());
        when(honorarioRepo.findById(7)).thenReturn(Optional.of(honorario));
        when(processoRepo.clienteIdPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.of(cliente.getId()));
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenReturn(Optional.of(cliente));
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.of(processo));
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.of(processo.getId()));
        when(parametros.valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, dataNumero)).thenReturn(TAXA_IVA);
        when(ccRepo.criarSeNaoExiste(cliente.getId())).thenReturn(1);
        when(ccRepo.bloquearPorCliente(cliente.getId())).thenReturn(Optional.of(cc));
        when(ccRepo.save(any(ContaCorrente.class))).thenAnswer(i -> i.getArgument(0));
        when(pagamentoRepo.save(any(Pagamento.class))).thenAnswer(i -> {
            Pagamento p = i.getArgument(0);
            p.setId(55);
            return p;
        });
        when(numeracao.proximoNumero(tenant, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO))
                .thenReturn(new NumeroFiscalAtribuido(serieId, "SIM-FR-2026", 2026, 7, dataNumero));
        when(documentoRepo.save(any(DocumentoFiscal.class))).thenAnswer(i -> {
            DocumentoFiscal d = i.getArgument(0);
            definirId(d, documentoId);
            return d;
        });
        when(linhaRepo.save(any(DocumentoFiscalLinha.class))).thenAnswer(i -> i.getArgument(0));
        when(comunicacaoRepo.save(any(ComunicacaoFiscal.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void tudoPresente(ConfiguracaoFiscal c) {
        tudoPresente(c, HOJE);
    }

    private void assertNadaEscrito() {
        verify(pagamentoRepo, never()).save(any());
        verify(ccRepo, never()).criarSeNaoExiste(any());
        verify(ccRepo, never()).save(any());
        verify(numeracao, never()).proximoNumero(any(), any(), any());
        verify(documentoRepo, never()).save(any());
        verify(linhaRepo, never()).save(any());
        verify(comunicacaoRepo, never()).save(any());
        verify(auditoria, never()).registarEmissao(any(), any(), any(), any());
    }

    private static void assertRecusa(RecusaFiscalException e, HttpStatus status, String codigo) {
        assertEquals(status, e.getStatus(), e.getCodigo());
        assertEquals(codigo, e.getCodigo());
    }

    private RecusaFiscalException recusa(PagamentoRequest pedido) {
        return assertThrows(RecusaFiscalException.class, () -> servico(RELOGIO).registar(tenant, autor, pedido));
    }

    private DocumentoFiscal documentoGravado() {
        ArgumentCaptor<DocumentoFiscal> captor = ArgumentCaptor.forClass(DocumentoFiscal.class);
        verify(documentoRepo).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ caminho feliz

    @Test
    void ordemDosLocksEChamadasNoCaminhoFeliz() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));

        ResultadoPagamentoFaturado r = servico(RELOGIO).registar(tenant, autor, req());

        assertTrue(r.novo());
        InOrder ordem = inOrder(serieRepo, configuracaoRepo, documentoRepo, honorarioRepo, processoRepo,
                clienteRepo, parametros, ccRepo, pagamentoRepo, numeracao, linhaRepo, comunicacaoRepo, auditoria);
        ordem.verify(serieRepo).definirLockTimeoutLocal();
        ordem.verify(configuracaoRepo).bloquearPorTenant(tenant);
        ordem.verify(documentoRepo).findByTenantIdAndChaveIdempotencia(tenant, chave);
        ordem.verify(honorarioRepo).findById(7);
        ordem.verify(processoRepo).clienteIdPorIdETenant(processo.getId(), tenant);
        ordem.verify(clienteRepo).bloquearPorIdETenant(cliente.getId(), tenant);
        ordem.verify(processoRepo).bloquearPorIdETenant(processo.getId(), tenant);
        ordem.verify(honorarioRepo).processoIdPorId(7);
        ordem.verify(parametros).valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, HOJE);
        ordem.verify(ccRepo).criarSeNaoExiste(cliente.getId());
        ordem.verify(ccRepo).bloquearPorCliente(cliente.getId());
        ordem.verify(pagamentoRepo).save(any(Pagamento.class));
        ordem.verify(numeracao).proximoNumero(tenant, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO);
        ordem.verify(documentoRepo).save(any(DocumentoFiscal.class));
        ordem.verify(linhaRepo).save(any(DocumentoFiscalLinha.class));
        ordem.verify(comunicacaoRepo).save(any(ComunicacaoFiscal.class));
        ordem.verify(auditoria).registarEmissao(tenant, autor, documentoId, "SIM-FR-2026/7");
    }

    @Test
    void caminhoAtivoNuncaLeSemLockAsLinhasQueBloqueia() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));

        servico(RELOGIO).registar(tenant, autor, req());

        verify(clienteRepo, never()).findById(any());
        verify(processoRepo, never()).findById(any());
        verify(configuracaoRepo, never()).findByTenantId(any());
        verify(ccRepo, never()).findByClienteId(any());
    }

    @Test
    void pagamentoGravadoComDataDeHojeEContaCorrenteCreditadaDoTotal() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));

        ResultadoPagamentoFaturado r = servico(RELOGIO).registar(tenant, autor, req());

        ArgumentCaptor<Pagamento> pag = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepo).save(pag.capture());
        assertEquals(new BigDecimal("120000.00"), pag.getValue().getValorPago());
        assertEquals(HOJE, pag.getValue().getDataPagamento());
        assertEquals("TRANSFERENCIA", pag.getValue().getMetodo());
        assertEquals(7, pag.getValue().getHonorarioId());

        ArgumentCaptor<ContaCorrente> conta = ArgumentCaptor.forClass(ContaCorrente.class);
        verify(ccRepo).save(conta.capture());
        // D-09: o TOTAL (120 000,00), nunca o líquido (99 130,43)
        assertEquals(0, new BigDecimal("120500.00").compareTo(conta.getValue().getSaldo()));

        assertEquals(55, r.resposta().id());
        assertEquals(documentoId, r.resposta().documentoFiscal().id());
        assertEquals("SIM-FR-2026/7", r.resposta().documentoFiscal().numeroFormatado());
    }

    @Test
    void documentoFotografaEmitenteAdquirenteEValores() {
        ConfiguracaoFiscal c = cfg(RegimeIva.NORMAL, true);
        tudoPresente(c);

        servico(RELOGIO).registar(tenant, autor, req());

        DocumentoFiscal d = documentoGravado();
        assertEquals(tenant, d.getTenantId());
        assertEquals(TipoDocumentoFiscal.FR, d.getTipo());
        assertEquals(AmbienteFiscal.SIMULADO, d.getAmbiente());
        assertEquals(serieId, d.getSerieId());
        assertEquals("SIM-FR-2026", d.getSerieCodigo());
        assertEquals(2026, d.getAno());
        assertEquals(7L, d.getNumero());
        assertEquals("SIM-FR-2026/7", d.getNumeroFormatado());
        assertEquals(HOJE, d.getDataEmissao());
        assertEquals(RELOGIO.instant(), d.getEmitidoEm());
        assertEquals(new BigDecimal("104347.83"), d.getTotalBase());
        assertEquals(new BigDecimal("15652.17"), d.getTotalIva());
        assertEquals(new BigDecimal("20869.57"), d.getTotalRetencao());
        assertEquals(new BigDecimal("120000.00"), d.getTotalDocumento());
        assertEquals(new BigDecimal("99130.43"), d.getValorLiquido());
        assertEquals(0, TAXA_IVA.compareTo(d.getTaxaIva()));
        assertEquals(0, new BigDecimal("20").compareTo(d.getTaxaRetencao()));
        assertEquals("TRANSFERENCIA", d.getMetodoPagamento());
        assertEquals("30", d.getMeioPagamentoCodigo());
        assertEquals("CVE", d.getMoeda());
        assertEquals(chave, d.getChaveIdempotencia());
        assertEquals(autor.getUserId(), d.getEmitidoPorId());
        assertEquals("Ana Emissora", d.getEmitidoPorNome());
        assertEquals("123456789", d.getEmitenteNif());
        assertEquals("Silva & Associados", d.getEmitenteFirma());
        assertEquals("Av. 1", d.getEmitenteMorada());
        assertEquals("Praia", d.getEmitenteLocalidade());
        assertEquals(RegimeIva.NORMAL, d.getEmitenteRegimeIva());
        assertNull(d.getEmitenteMotivoIsencaoCodigo());
        assertEquals("234567891", d.getAdquirenteNif());
        assertEquals("Maria Lopes", d.getAdquirenteNome());
        assertEquals("Rua da Praia 5", d.getAdquirenteMorada());
        assertNull(d.getAdquirenteLocalidade(), "localidade em branco passa a nula");
        assertEquals(cliente.getId(), d.getClienteId());
        assertEquals(processo.getId(), d.getProcessoId());
        assertEquals(7, d.getHonorarioId());
        assertEquals(55, d.getPagamentoId());
    }

    @Test
    void linhaUnicaComDescricaoControlada() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));

        servico(RELOGIO).registar(tenant, autor, req());

        ArgumentCaptor<DocumentoFiscalLinha> captor = ArgumentCaptor.forClass(DocumentoFiscalLinha.class);
        verify(linhaRepo).save(captor.capture());
        DocumentoFiscalLinha l = captor.getValue();
        assertEquals(tenant, l.getTenantId());
        assertEquals(documentoId, l.getDocumentoFiscalId());
        assertEquals(1, l.getNumeroLinha());
        assertEquals(0, BigDecimal.ONE.compareTo(l.getQuantidade()));
        assertEquals(new BigDecimal("104347.83"), l.getPrecoUnitario());
        assertEquals(new BigDecimal("104347.83"), l.getValorBase());
        assertEquals(new BigDecimal("15652.17"), l.getValorIva());
        assertEquals(new BigDecimal("20869.57"), l.getValorRetencao());
        assertEquals(new BigDecimal("120000.00"), l.getTotalLinha());
        assertEquals("Honorários por serviços jurídicos — Processo n.º P-1", l.getDescricao());
        assertFalse(l.getDescricao().contains("SEGREDO"));
        assertNull(l.getMotivoIsencaoCodigo());
    }

    @Test
    void comunicacaoPendenteNoAmbienteSimulado() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));

        servico(RELOGIO).registar(tenant, autor, req());

        ArgumentCaptor<ComunicacaoFiscal> captor = ArgumentCaptor.forClass(ComunicacaoFiscal.class);
        verify(comunicacaoRepo).save(captor.capture());
        ComunicacaoFiscal c = captor.getValue();
        assertEquals(EstadoComunicacaoFiscal.PENDENTE, c.getEstado());
        assertEquals(AmbienteFiscal.SIMULADO, c.getAmbiente());
        assertEquals(0, c.getTentativas());
        assertEquals(tenant, c.getTenantId());
        assertEquals(documentoId, c.getDocumentoFiscalId());
        assertEquals(RELOGIO.instant(), c.getCreatedAt());
    }

    @Test
    void escritorioIsentoNaoLeTaxaEFotografaOMotivo() {
        tudoPresente(cfg(RegimeIva.ISENTO, true));

        servico(RELOGIO).registar(tenant, autor, req());

        verify(parametros, never()).valorVigente(any(), any());
        DocumentoFiscal d = documentoGravado();
        assertEquals(0, BigDecimal.ZERO.compareTo(d.getTaxaIva()));
        assertEquals(new BigDecimal("0.00"), d.getTotalIva());
        assertEquals(new BigDecimal("120000.00"), d.getTotalBase());
        MotivoIsencaoIva m = MotivoIsencaoIva.porCodigo("3").orElseThrow();
        assertEquals("3", d.getEmitenteMotivoIsencaoCodigo());
        assertEquals(m.descricao(), d.getEmitenteMotivoIsencaoDescricao());
        assertEquals(m.mencao(), d.getEmitenteMotivoIsencaoMencao());
        assertEquals(RegimeIva.ISENTO, d.getEmitenteRegimeIva());

        ArgumentCaptor<DocumentoFiscalLinha> linha = ArgumentCaptor.forClass(DocumentoFiscalLinha.class);
        verify(linhaRepo).save(linha.capture());
        assertEquals("3", linha.getValue().getMotivoIsencaoCodigo());
    }

    // ------------------------------------------------------------------ idempotência

    private DocumentoFiscal existente(BigDecimal taxaRetencao) {
        return DocumentoFiscal.builder().id(documentoId).tenantId(tenant).honorarioId(7).pagamentoId(55)
                .totalDocumento(new BigDecimal("120000.00")).metodoPagamento("TRANSFERENCIA")
                .taxaRetencao(taxaRetencao).dataEmissao(HOJE).numeroFormatado("SIM-FR-2026/7")
                .chaveIdempotencia(chave).build();
    }

    private void comExistente(DocumentoFiscal d) {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, true)));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.of(d));
        Pagamento p = Pagamento.builder().id(55).honorarioId(7).valorPago(new BigDecimal("120000.00"))
                .dataPagamento(HOJE).metodo("TRANSFERENCIA").build();
        when(pagamentoRepo.findById(55)).thenReturn(Optional.of(p));
    }

    private void assertNadaMaisDepoisDaChave() {
        assertNadaEscrito();
        verifyNoInteractions(honorarioRepo, clienteRepo, processoRepo, parametros);
        verify(ccRepo, never()).bloquearPorCliente(any());
    }

    @Test
    void mesmaChaveMesmoPedidoDevolveOMesmoResultadoSemEscrever() {
        comExistente(existente(new BigDecimal("20.00")));

        ResultadoPagamentoFaturado r = servico(RELOGIO).registar(tenant, autor, req());

        assertFalse(r.novo());
        assertEquals(55, r.resposta().id());
        assertEquals(documentoId, r.resposta().documentoFiscal().id());
        assertEquals("SIM-FR-2026/7", r.resposta().documentoFiscal().numeroFormatado());
        assertNadaMaisDepoisDaChave();
    }

    @Test
    void mesmaChaveComValorNoutraEscalaEMetodoEmMinusculasEhOMesmoPedido() {
        comExistente(existente(new BigDecimal("20.00")));
        PagamentoRequest pedido = new PagamentoRequest(7, new BigDecimal("120000"), HOJE, " transferencia ",
                new BigDecimal("20"), chave);

        assertFalse(servico(RELOGIO).registar(tenant, autor, pedido).novo());
        assertNadaMaisDepoisDaChave();
    }

    static Stream<PagamentoRequestVariante> variantesDiferentes() {
        return Stream.of(
                new PagamentoRequestVariante("valor", 7, "120000.01", null, "TRANSFERENCIA", "20"),
                new PagamentoRequestVariante("metodo", 7, "120000.00", null, "DINHEIRO", "20"),
                new PagamentoRequestVariante("retencao", 7, "120000.00", null, "TRANSFERENCIA", "10"),
                new PagamentoRequestVariante("semRetencao", 7, "120000.00", null, "TRANSFERENCIA", null),
                new PagamentoRequestVariante("honorario", 8, "120000.00", null, "TRANSFERENCIA", "20"),
                new PagamentoRequestVariante("data", 7, "120000.00", "2026-10-03", "TRANSFERENCIA", "20"),
                new PagamentoRequestVariante("valorNulo", 7, null, null, "TRANSFERENCIA", "20"),
                new PagamentoRequestVariante("metodoNulo", 7, "120000.00", null, null, "20"));
    }

    record PagamentoRequestVariante(String nome, Integer honorarioId, String valor, String data, String metodo,
                                    String retencao) {
        PagamentoRequest paraPedido(UUID chave) {
            return new PagamentoRequest(honorarioId, valor == null ? null : new BigDecimal(valor),
                    data == null ? null : LocalDate.parse(data), metodo,
                    retencao == null ? null : new BigDecimal(retencao), chave);
        }

        @Override
        public String toString() {
            return nome;
        }
    }

    @ParameterizedTest
    @MethodSource("variantesDiferentes")
    void mesmaChaveComPedidoDiferenteEhRecusadaSemEscrever(PagamentoRequestVariante variante) {
        comExistente(existente(new BigDecimal("20.00")));

        RecusaFiscalException e = recusa(variante.paraPedido(chave));

        assertRecusa(e, HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
        assertEquals("Este pedido já foi usado com valores diferentes. Reveja os dados e registe o pagamento de novo.",
                e.getMessage());
        assertNadaMaisDepoisDaChave();
        verify(pagamentoRepo, never()).findById(any());
    }

    @Test
    void mesmaChaveSemRetencaoNosDoisLadosEhOMesmoPedido() {
        comExistente(existente(null));
        PagamentoRequest pedido = new PagamentoRequest(7, new BigDecimal("120000.00"), null, "TRANSFERENCIA",
                null, chave);

        assertFalse(servico(RELOGIO).registar(tenant, autor, pedido).novo());
    }

    // ------------------------------------------------------------------ recusas antes de escrever

    @Test
    void semChaveRecusa422AntesDeQualquerRepositorio() {
        PagamentoRequest semChave = new PagamentoRequest(7, new BigDecimal("1"), null, "DINHEIRO", null, null);

        RecusaFiscalException e = recusa(semChave);

        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "CHAVE_IDEMPOTENCIA_OBRIGATORIA");
        verifyNoInteractions(configuracaoRepo, serieRepo, honorarioRepo, processoRepo, clienteRepo, ccRepo,
                pagamentoRepo, documentoRepo, linhaRepo, comunicacaoRepo, numeracao, parametros, auditoria);
    }

    @Test
    void semConfiguracaoRecusaFaturacaoDesligada() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(req()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertNadaEscrito();
        verify(documentoRepo, never()).findByTenantIdAndChaveIdempotencia(any(), any());
    }

    @Test
    void configuracaoDesativadaEntretantoRecusaFaturacaoDesligada() {
        tudoPresente(cfg(RegimeIva.NORMAL, false));

        assertRecusa(recusa(req()), HttpStatus.CONFLICT, "FATURACAO_DESLIGADA");
        assertNadaEscrito();
    }

    @Test
    void configuracaoDesativadaEntretantoComChaveJaEmitidaDevolveOResultadoGuardado() {
        // WR-05 da revisão: a idempotência é verificada antes da ativação.
        comExistente(existente(new BigDecimal("20.00")));
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, false)));

        ResultadoPagamentoFaturado r = servico(RELOGIO).registar(tenant, autor, req());

        assertFalse(r.novo());
        assertEquals(55, r.resposta().id());
        assertNadaMaisDepoisDaChave();
    }

    @Test
    void resultadoGuardadoSemChaveNaoTocaEmNada() {
        PagamentoRequest semChave = new PagamentoRequest(7, new BigDecimal("1"), null, "DINHEIRO", null, null);

        assertTrue(servico(RELOGIO).resultadoGuardado(tenant, semChave).isEmpty());
        verifyNoInteractions(configuracaoRepo, serieRepo, documentoRepo, pagamentoRepo);
    }

    @Test
    void resultadoGuardadoSemConfiguracaoDevolveVazio() {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.empty());

        assertTrue(servico(RELOGIO).resultadoGuardado(tenant, req()).isEmpty());
        verify(documentoRepo, never()).findByTenantIdAndChaveIdempotencia(any(), any());
    }

    @Test
    void resultadoGuardadoComFaturacaoDesligadaDevolveODocumentoJaEmitido() {
        comExistente(existente(new BigDecimal("20.00")));
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, false)));

        Optional<ResultadoPagamentoFaturado> r = servico(RELOGIO).resultadoGuardado(tenant, req());

        assertTrue(r.isPresent());
        assertFalse(r.get().novo());
        assertEquals(documentoId, r.get().resposta().documentoFiscal().id());
        InOrder ordem = inOrder(serieRepo, configuracaoRepo, documentoRepo);
        ordem.verify(serieRepo).definirLockTimeoutLocal();
        ordem.verify(configuracaoRepo).bloquearPorTenant(tenant);
        ordem.verify(documentoRepo).findByTenantIdAndChaveIdempotencia(tenant, chave);
        assertNadaEscrito();
    }

    @Test
    void resultadoGuardadoComValoresDiferentesRecusaChaveReutilizada() {
        comExistente(existente(new BigDecimal("20.00")));
        PagamentoRequest outro = new PagamentoRequest(7, new BigDecimal("1.00"), null, "TRANSFERENCIA",
                new BigDecimal("20"), chave);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> servico(RELOGIO).resultadoGuardado(tenant, outro));
        assertRecusa(e, HttpStatus.CONFLICT, "CHAVE_REUTILIZADA");
    }

    @Test
    void resultadoGuardadoSemDocumentoParaAChaveDevolveVazio() {
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenReturn(Optional.of(cfg(RegimeIva.NORMAL, false)));
        when(documentoRepo.findByTenantIdAndChaveIdempotencia(tenant, chave)).thenReturn(Optional.empty());

        assertTrue(servico(RELOGIO).resultadoGuardado(tenant, req()).isEmpty());
        assertNadaEscrito();
    }

    @Test
    void semHonorarioRecusa422() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        PagamentoRequest pedido = new PagamentoRequest(null, new BigDecimal("1"), null, "DINHEIRO", null, chave);

        RecusaFiscalException e = recusa(pedido);

        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "HONORARIO_OBRIGATORIO");
        assertEquals("honorarioId", e.getCampo());
        assertNadaEscrito();
    }

    @Test
    void honorarioInexistenteRecusa404() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(honorarioRepo.findById(7)).thenReturn(Optional.empty());

        assertRecusa(recusa(req()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
        verify(clienteRepo, never()).bloquearPorIdETenant(any(), any());
    }

    @Test
    void honorarioDeOutroEscritorioRecusa404() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(processoRepo.clienteIdPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(req()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
        verify(clienteRepo, never()).bloquearPorIdETenant(any(), any());
    }

    @Test
    void clienteDesaparecidoAntesDoLockRecusa409() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenReturn(Optional.empty());

        RecusaFiscalException e = recusa(req());

        assertRecusa(e, HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE");
        assertEquals("O processo ou o cliente foi alterado entretanto. Tente novamente.", e.getMessage());
        assertNadaEscrito();
    }

    @Test
    void processoDesaparecidoRecusa409() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.empty());

        assertRecusa(recusa(req()), HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE");
        assertNadaEscrito();
    }

    @Test
    void processoMovidoParaOutroClientePorUmaFusaoRecusa409() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        Processo movido = Processo.builder().id(processo.getId()).tenantId(tenant).clienteId(UUID.randomUUID())
                .numeroProcesso("P-1").build();
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenReturn(Optional.of(movido));

        assertRecusa(recusa(req()), HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE");
        assertNadaEscrito();
    }

    @Test
    void honorarioApagadoEnquantoEsperavaOLockDoProcessoRecusa404() {
        // CR-01 da revisão: o honorário foi lido no passo 5, mas uma eliminação concorrente fez
        // commit antes de o lock do processo ser concedido.
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.empty());

        assertRecusa(recusa(req()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
    }

    @Test
    void honorarioDeOutroProcessoDepoisDoLockRecusa404() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(honorarioRepo.processoIdPorId(7)).thenReturn(Optional.of(UUID.randomUUID()));

        assertRecusa(recusa(req()), HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO");
        assertNadaEscrito();
    }

    @Test
    void recusa422DaComposicaoPropagaSemEscrever() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        Cliente semNif = Cliente.builder().id(cliente.getId()).tenantId(tenant).nif("012345678")
                .nome("Maria Lopes").morada("Rua 1").build();
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenReturn(Optional.of(semNif));

        RecusaFiscalException e = recusa(req());

        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "ADQUIRENTE_INCOMPLETO");
        assertEquals("nif", e.getCampo());
        assertNadaEscrito();
    }

    @Test
    void metodoInvalidoRecusa422SemEscrever() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        PagamentoRequest pedido = new PagamentoRequest(7, new BigDecimal("10"), null, "Transferência", null, chave);

        assertRecusa(recusa(pedido), HttpStatus.UNPROCESSABLE_ENTITY, "METODO_PAGAMENTO_INVALIDO");
        assertNadaEscrito();
    }

    // ------------------------------------------------------------------ datas

    @Test
    void hojeEhADataDeCaboVerdeCalculadaUmaSoVez() {
        Clock quaseMeiaNoite = Clock.fixed(Instant.parse("2026-10-05T00:59:59Z"), ZoneOffset.UTC);
        tudoPresente(cfg(RegimeIva.NORMAL, true), HOJE);

        servico(quaseMeiaNoite).registar(tenant, autor, req());

        verify(parametros).valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, HOJE);
        ArgumentCaptor<Pagamento> pag = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepo).save(pag.capture());
        assertEquals(HOJE, pag.getValue().getDataPagamento());
    }

    @Test
    void dataDeOntemOuAmanhaRecusa422() {
        for (LocalDate data : new LocalDate[]{HOJE.minusDays(1), HOJE.plusDays(1)}) {
            preparar();
            tudoPresente(cfg(RegimeIva.NORMAL, true));
            PagamentoRequest pedido = new PagamentoRequest(7, new BigDecimal("10"), data, "DINHEIRO", null, chave);

            RecusaFiscalException e = recusa(pedido);

            assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "DATA_PAGAMENTO_RETROATIVA");
            assertNadaEscrito();
        }
    }

    @Test
    void dataDeHojeExplicitaEhAceite() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        PagamentoRequest pedido = new PagamentoRequest(7, new BigDecimal("10"), HOJE, "DINHEIRO", null, chave);

        assertTrue(servico(RELOGIO).registar(tenant, autor, pedido).novo());
    }

    @Test
    void numeroDeOutroDiaRecusa409SemGravarDocumento() {
        tudoPresente(cfg(RegimeIva.NORMAL, true), HOJE);
        when(numeracao.proximoNumero(tenant, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO))
                .thenReturn(new NumeroFiscalAtribuido(serieId, "SIM-FR-2026", 2026, 1, HOJE.plusDays(1)));

        RecusaFiscalException e = recusa(req());

        assertRecusa(e, HttpStatus.CONFLICT, "DATA_EMISSAO_ALTERADA");
        assertEquals("A data mudou durante a emissão. Tente novamente.", e.getMessage());
        verify(documentoRepo, never()).save(any());
        verify(linhaRepo, never()).save(any());
        verify(comunicacaoRepo, never()).save(any());
        verify(auditoria, never()).registarEmissao(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ falhas de lock

    static Stream<RuntimeException> falhasDeLock() {
        return Stream.of(new PessimisticLockingFailureException("lock"), new PessimisticLockException("lock"),
                new LockTimeoutException("lock"));
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDaConfiguracaoDevolve503(RuntimeException falha) {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(configuracaoRepo.bloquearPorTenant(tenant)).thenThrow(falha);

        RecusaFiscalException e = recusa(req());

        assertRecusa(e, HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertEquals("A faturação está ocupada. Tente novamente dentro de instantes.", e.getMessage());
        assertNadaEscrito();
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDoClienteOuProcessoDevolve503(RuntimeException falha) {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(clienteRepo.bloquearPorIdETenant(cliente.getId(), tenant)).thenThrow(falha);
        assertRecusa(recusa(req()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertNadaEscrito();

        preparar();
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(processoRepo.bloquearPorIdETenant(processo.getId(), tenant)).thenThrow(falha);
        assertRecusa(recusa(req()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        assertNadaEscrito();
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void falhaNoLockDaContaCorrenteDevolve503(RuntimeException falha) {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(ccRepo.bloquearPorCliente(cliente.getId())).thenThrow(falha);

        assertRecusa(recusa(req()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        verify(pagamentoRepo, never()).save(any());
        verify(numeracao, never()).proximoNumero(any(), any(), any());
        verify(documentoRepo, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("falhasDeLock")
    void esperaNoInsertDaContaCorrenteDevolve503(RuntimeException falha) {
        // IN-04 da revisão: o INSERT ... ON CONFLICT espera por um INSERT concorrente sem commit.
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        when(ccRepo.criarSeNaoExiste(cliente.getId())).thenThrow(falha);

        assertRecusa(recusa(req()), HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA");
        verify(ccRepo, never()).bloquearPorCliente(any());
        verify(pagamentoRepo, never()).save(any());
        verify(numeracao, never()).proximoNumero(any(), any(), any());
        verify(documentoRepo, never()).save(any());
    }

    @Test
    void serieIndisponivelPropagaSemAlteracao() {
        tudoPresente(cfg(RegimeIva.NORMAL, true));
        RecusaFiscalException serie = new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE,
                "SERIE_INDISPONIVEL", "A série de numeração está ocupada. Tente novamente dentro de instantes.");
        when(numeracao.proximoNumero(tenant, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO)).thenThrow(serie);

        RecusaFiscalException e = recusa(req());

        assertSame(serie, e);
        verify(documentoRepo, never()).save(any());
    }

    // ------------------------------------------------------------------ faturacaoAtiva

    @Test
    void faturacaoAtivaLeOEscalar() {
        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.empty());
        assertFalse(servico(RELOGIO).faturacaoAtiva(tenant));

        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.of(false));
        assertFalse(servico(RELOGIO).faturacaoAtiva(tenant));

        when(configuracaoRepo.ativaPorTenant(tenant)).thenReturn(Optional.of(true));
        assertTrue(servico(RELOGIO).faturacaoAtiva(tenant));

        verify(configuracaoRepo, never()).findByTenantId(any());
        verify(configuracaoRepo, never()).bloquearPorTenant(any());
    }

    // ------------------------------------------------------------------ reflexão / código-fonte

    @Test
    void registarEhTransacionalRequiredEFaturacaoAtivaEhReadOnly() throws Exception {
        Method registar = PagamentoFaturadoService.class.getMethod("registar", UUID.class, UserPrincipal.class,
                PagamentoRequest.class);
        Transactional tx = registar.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRED, tx.propagation());
        assertFalse(tx.readOnly());

        Method ativa = PagamentoFaturadoService.class.getMethod("faturacaoAtiva", UUID.class);
        Transactional txAtiva = ativa.getAnnotation(Transactional.class);
        assertNotNull(txAtiva);
        assertTrue(txAtiva.readOnly());
    }

    @Test
    void naoDependeDoPedidoHttpNemDoContextoDeSeguranca() throws Exception {
        assertTrue(Arrays.stream(PagamentoFaturadoService.class.getDeclaredFields())
                .noneMatch(f -> HttpServletRequest.class.isAssignableFrom(f.getType())));
        String fonte = Files.readString(
                Path.of("src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java"),
                StandardCharsets.UTF_8);
        assertFalse(fonte.contains("SecurityContextHolder"));
        assertFalse(fonte.contains("LocalDate.now()"));
        assertFalse(fonte.contains("getDescricao"));
        assertFalse(fonte.contains("catch (DataIntegrityViolationException"));
    }
}
