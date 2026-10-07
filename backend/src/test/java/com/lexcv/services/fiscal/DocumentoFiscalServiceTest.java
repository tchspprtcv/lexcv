package com.lexcv.services.fiscal;

import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLigacaoClienteRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.dtos.EntregaEmailResumo;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.Cliente;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.EntregaEmailFiscalRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (EMIS-09, EMIS-11, EMIS-12, D-14..D-19): leitura, guardas e fusão dos documentos fiscais.
 * Phase 135 (NCRD-01..03): NC no detalhe e na listagem, FR vs. estorno nos pagamentos, sonda do estorno.
 */
class DocumentoFiscalServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private DocumentoFiscalRepository documentoRepo;
    private DocumentoFiscalLinhaRepository linhaRepo;
    private ComunicacaoFiscalRepository comunicacaoRepo;
    private DocumentoFiscalLigacaoClienteRepository ligacaoRepo;
    private DocumentoFiscalXmlRepository xmlRepo;
    private EntregaEmailFiscalRepository entregaRepo;
    private ConfiguracaoFiscalRepository configRepo;
    private ClienteRepository clienteRepo;
    private DocumentoFiscalService servico;

    @BeforeEach
    void preparar() {
        documentoRepo = mock(DocumentoFiscalRepository.class);
        linhaRepo = mock(DocumentoFiscalLinhaRepository.class);
        comunicacaoRepo = mock(ComunicacaoFiscalRepository.class);
        ligacaoRepo = mock(DocumentoFiscalLigacaoClienteRepository.class);
        xmlRepo = mock(DocumentoFiscalXmlRepository.class);
        entregaRepo = mock(EntregaEmailFiscalRepository.class);
        configRepo = mock(ConfiguracaoFiscalRepository.class);
        clienteRepo = mock(ClienteRepository.class);
        servico = servico(true);
    }

    /** Phase 137: o mesmo serviço com o SMTP configurado ou não (registo construído diretamente). */
    private DocumentoFiscalService servico(boolean smtp) {
        EmailProperties.Smtp s = new EmailProperties.Smtp(smtp ? "smtp.example.cv" : "", 587, null, null,
                smtp ? "faturacao@example.cv" : "", true, Duration.ofSeconds(10), Duration.ofSeconds(20));
        EmailProperties email = new EmailProperties(s, new EmailProperties.Outbox(Duration.ofSeconds(30),
                Duration.ofSeconds(40), 10, Duration.ofMinutes(2)));
        return new DocumentoFiscalService(documentoRepo, linhaRepo, comunicacaoRepo, ligacaoRepo, xmlRepo,
                entregaRepo, configRepo, clienteRepo, email);
    }

    private DocumentoFiscal doc(long numero, Integer pagamentoId) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(tenant).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-FR-2026").ano(2026).numero(numero)
                .numeroFormatado("SIM-FR-2026/" + numero).dataEmissao(LocalDate.of(2026, 10, 4))
                .emitidoEm(Instant.parse("2026-10-04T12:00:00Z"))
                .emitenteNif("123456789").emitenteFirma("Silva").emitenteMorada("Av. 1").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("234567891").adquirenteNome("Maria").adquirenteMorada("Rua 2")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(3).pagamentoId(pagamentoId)
                .metodoPagamento("TRANSFERENCIA").meioPagamentoCodigo("30").moeda("CVE")
                .taxaIva(new BigDecimal("15")).totalBase(new BigDecimal("100.00")).totalIva(new BigDecimal("15.00"))
                .totalRetencao(new BigDecimal("0.00")).totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00")).chaveIdempotencia(UUID.randomUUID())
                .emitidoPorId(UUID.randomUUID()).emitidoPorNome("Ana")
                .build();
    }

    /** Phase 135: NC sobre {@code origem}, com o seu próprio pagamento de estorno. */
    private DocumentoFiscal nc(long numero, Integer pagamentoEstornoId, UUID origem, String total) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(tenant).tipo(TipoDocumentoFiscal.NC).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-NC-2026").ano(2026).numero(numero)
                .numeroFormatado("SIM-NC-2026/" + numero).dataEmissao(LocalDate.of(2026, 10, 5))
                .emitidoEm(Instant.parse("2026-10-05T12:00:00Z"))
                .emitenteNif("123456789").emitenteFirma("Silva").emitenteMorada("Av. 1").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("234567891").adquirenteNome("Maria").adquirenteMorada("Rua 2")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(3).pagamentoId(pagamentoEstornoId)
                .metodoPagamento("TRANSFERENCIA").meioPagamentoCodigo("30").moeda("CVE")
                .taxaIva(new BigDecimal("15")).totalBase(new BigDecimal("10.00")).totalIva(new BigDecimal("1.50"))
                .totalRetencao(new BigDecimal("0.00")).totalDocumento(new BigDecimal(total))
                .valorLiquido(new BigDecimal(total)).chaveIdempotencia(UUID.randomUUID())
                .documentoOrigemId(origem).motivoCodigo(MotivoNotaCredito.CORRECAO_VALOR).motivoTexto("Valor a mais")
                .build();
    }

    private ComunicacaoFiscal comunicacao(DocumentoFiscal d) {
        return ComunicacaoFiscal.builder().tenantId(tenant).documentoFiscalId(d.getId())
                .ambiente(AmbienteFiscal.SIMULADO).estado(EstadoComunicacaoFiscal.PENDENTE).build();
    }

    // ------------------------------------------------------------------ listar

    @Test
    void listarPassaOsFiltrosComoTextoEUmaPaginaSemOrdenacao() {
        UUID cliente = UUID.randomUUID();
        LocalDate de = LocalDate.of(2026, 1, 1);
        LocalDate ate = LocalDate.of(2026, 12, 31);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        servico.listar(tenant, cliente, TipoDocumentoFiscal.FR, EstadoComunicacaoFiscal.PENDENTE, de, ate, 2, 25);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(documentoRepo).buscar(eq(tenant), eq(cliente.toString()), eq("FR"), eq("PENDENTE"), eq(de), eq(ate),
                pagina.capture());
        assertEquals(2, pagina.getValue().getPageNumber());
        assertEquals(25, pagina.getValue().getPageSize());
        assertTrue(pagina.getValue().getSort().isUnsorted(), "a ordem é a da query nativa");
    }

    @Test
    void listarSemFiltrosPassaNulos() {
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        servico.listar(tenant, null, null, null, null, null, 0, 10);
        verify(documentoRepo).buscar(eq(tenant), eq(null), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarHidrataEstadosNumaSoChamadaEMantemAOrdem() {
        DocumentoFiscal d1 = doc(3, 30);
        DocumentoFiscal d2 = doc(2, 20);
        DocumentoFiscal d3 = doc(1, 10);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(d1, d2, d3), org.springframework.data.domain.PageRequest.of(0, 3), 7));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), any()))
                .thenReturn(List.of(comunicacao(d1), comunicacao(d3)));

        Page<DocumentoFiscalResumoResponse> r = servico.listar(tenant, null, null, null, null, null, 0, 3);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(comunicacaoRepo, times(1)).findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), ids.capture());
        assertEquals(Set.of(d1.getId(), d2.getId(), d3.getId()), Set.copyOf(ids.getValue()));
        assertEquals(List.of(d1.getId(), d2.getId(), d3.getId()),
                r.getContent().stream().map(DocumentoFiscalResumoResponse::id).toList());
        assertEquals("PENDENTE", r.getContent().get(0).estadoComunicacao());
        assertNull(r.getContent().get(1).estadoComunicacao());
        assertEquals(7, r.getTotalElements());
        assertEquals("SIM-FR-2026/3", r.getContent().get(0).numeroFormatado());
    }

    @Test
    void listarSemNcNaoConsultaOrigens() {
        DocumentoFiscal d1 = doc(1, 10);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(d1)));
        Page<DocumentoFiscalResumoResponse> r = servico.listar(tenant, null, null, null, null, null, 0, 10);
        verify(documentoRepo, never()).findByTenantIdAndIdIn(any(), any());
        assertNull(r.getContent().get(0).documentoOrigemNumero());
        assertNull(r.getContent().get(0).documentoOrigemId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarCarregaOsNumerosDeOrigemDasNcNumaSoChamada() {
        DocumentoFiscal fr1 = doc(1, 10);
        DocumentoFiscal fr2 = doc(2, 20);
        DocumentoFiscal ncA = nc(1, 11, fr1.getId(), "5.00");
        DocumentoFiscal ncB = nc(2, 12, fr1.getId(), "5.00");
        DocumentoFiscal ncC = nc(3, 21, fr2.getId(), "5.00");
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(ncC, ncB, ncA, fr1)));
        when(documentoRepo.findByTenantIdAndIdIn(eq(tenant), any())).thenReturn(List.of(fr1, fr2));

        Page<DocumentoFiscalResumoResponse> r = servico.listar(tenant, null, null, null, null, null, 0, 10);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(documentoRepo, times(1)).findByTenantIdAndIdIn(eq(tenant), ids.capture());
        assertEquals(Set.of(fr1.getId(), fr2.getId()), Set.copyOf(ids.getValue()));
        assertEquals(2, ids.getValue().size(), "ids de origem distintos");
        assertEquals("SIM-FR-2026/2", r.getContent().get(0).documentoOrigemNumero());
        assertEquals(fr2.getId(), r.getContent().get(0).documentoOrigemId());
        assertEquals("SIM-FR-2026/1", r.getContent().get(1).documentoOrigemNumero());
        assertEquals("SIM-FR-2026/1", r.getContent().get(2).documentoOrigemNumero());
        assertNull(r.getContent().get(3).documentoOrigemNumero(), "uma FR não tem origem");
    }

    @Test
    void listarPaginaVaziaNaoConsultaEstados() {
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        assertTrue(servico.listar(tenant, null, null, null, null, null, 0, 10).isEmpty());
        verifyNoInteractions(comunicacaoRepo);
    }

    @Test
    void listarRecusaPaginacaoForaDosLimites() {
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> servico.listar(tenant, null, null, null, null, null, 0, 101));
        verifyNoInteractions(documentoRepo);
    }

    // ------------------------------------------------------------------ detalhe

    @Test
    void detalheDeOutroTenantOuInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(documentoRepo.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, () -> servico.detalhe(tenant, id));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("DOCUMENTO_FISCAL_NAO_ENCONTRADO", e.getCodigo());
        assertEquals("Documento fiscal não encontrado.", e.getMessage());
        verifyNoInteractions(linhaRepo, comunicacaoRepo, xmlRepo);
    }

    // Phase 136 (DFE-04, DFE-06): resumo da comunicação e IUD do mesmo tenant.

    @Test
    void detalheTrazOResumoDaComunicacaoComOIudDoSateliteDoMesmoTenant() {
        DocumentoFiscal d = doc(2, 20);
        ComunicacaoFiscal c = comunicacao(d);
        c.setTentativas(3);
        DocumentoFiscalXml xml = mock(DocumentoFiscalXml.class);
        when(xml.getIud()).thenReturn("CV3260615512345679999990200000000212345678901");
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.of(c));
        when(xmlRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.of(xml));

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, d.getId());

        assertNotNull(r.comunicacao());
        assertEquals("PENDENTE", r.comunicacao().estado());
        assertEquals("SIMULADO", r.comunicacao().ambiente());
        assertEquals(3, r.comunicacao().tentativas());
        assertEquals("CV3260615512345679999990200000000212345678901", r.comunicacao().iud());
        assertEquals("PENDENTE", r.estadoComunicacao());
        verify(xmlRepo).findByTenantIdAndDocumentoFiscalId(tenant, d.getId());
    }

    @Test
    void detalheSemXmlAindaTemIudNulo() {
        DocumentoFiscal d = doc(3, 30);
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId()))
                .thenReturn(Optional.of(comunicacao(d)));
        when(xmlRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.empty());

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, d.getId());

        assertNotNull(r.comunicacao());
        assertNull(r.comunicacao().iud());
    }

    @Test
    void detalheSemLinhaDeComunicacaoNaoTemResumoNemConsultaOXml() {
        DocumentoFiscal d = doc(4, 40);
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.empty());

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, d.getId());

        assertNull(r.comunicacao());
        assertNull(r.estadoComunicacao());
        verifyNoInteractions(xmlRepo);
    }

    @Test
    void detalheCarregaLinhasEEstadoComOMesmoTenant() {
        DocumentoFiscal d = doc(1, 10);
        DocumentoFiscalLinha linha = DocumentoFiscalLinha.builder().tenantId(tenant).documentoFiscalId(d.getId())
                .numeroLinha(1).descricao("Honorários por serviços jurídicos").quantidade(BigDecimal.ONE)
                .precoUnitario(new BigDecimal("100.00")).valorBase(new BigDecimal("100.00"))
                .taxaIva(new BigDecimal("15")).valorIva(new BigDecimal("15.00")).valorRetencao(new BigDecimal("0.00"))
                .totalLinha(new BigDecimal("115.00")).build();
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        when(linhaRepo.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(tenant, d.getId())).thenReturn(List.of(linha));
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.of(comunicacao(d)));

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, d.getId());

        assertEquals(d.getId(), r.id());
        assertEquals("PENDENTE", r.estadoComunicacao());
        assertEquals(1, r.linhas().size());
        assertEquals("Honorários por serviços jurídicos", r.linhas().get(0).descricao());
        assertEquals(com.lexcv.models.MetodoPagamento.TRANSFERENCIA.rotulo(), r.metodoPagamentoRotulo());
        assertEquals("Ana", r.emitidoPorNome());
        assertEquals("NORMAL", r.emitenteRegimeIva());
    }

    @Test
    void detalheDaFrTrazAsNcEOValorCreditavel() {
        DocumentoFiscal fr = doc(7, 70);
        DocumentoFiscal nc2 = nc(2, 72, fr.getId(), "15.00");
        DocumentoFiscal nc1 = nc(1, 71, fr.getId(), "20.00");
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(fr));
        when(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenant, fr.getId()))
                .thenReturn(List.of(nc2, nc1));

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, fr.getId());

        assertEquals(List.of("SIM-NC-2026/2", "SIM-NC-2026/1"),
                r.notasCredito().stream().map(DocumentoFiscalDetalheResponse.NotaCreditoResumo::numeroFormatado).toList());
        assertEquals(new BigDecimal("35.00"), r.totalCreditado());
        assertEquals(new BigDecimal("80.00"), r.valorCreditavelRestante());
        assertNull(r.documentoOrigem());
        // Nunca procura uma origem para uma FR.
        verify(documentoRepo, times(1)).findByIdAndTenantId(any(), any());
    }

    @Test
    void detalheDaNcTrazAOrigemDoMesmoTenantENaoProcuraNcs() {
        DocumentoFiscal fr = doc(7, 70);
        DocumentoFiscal n = nc(1, 71, fr.getId(), "20.00");
        when(documentoRepo.findByIdAndTenantId(n.getId(), tenant)).thenReturn(Optional.of(n));
        when(documentoRepo.findByIdAndTenantId(fr.getId(), tenant)).thenReturn(Optional.of(fr));

        DocumentoFiscalDetalheResponse r = servico.detalhe(tenant, n.getId());

        assertEquals(new DocumentoFiscalRef(fr.getId(), "SIM-FR-2026/7"), r.documentoOrigem());
        assertEquals("CORRECAO_VALOR", r.motivoCodigo());
        assertEquals("Correção de valor", r.motivoRotulo());
        assertEquals("Valor a mais", r.motivoTexto());
        assertNull(r.totalCreditado());
        assertNull(r.valorCreditavelRestante());
        assertTrue(r.notasCredito().isEmpty());
        verify(documentoRepo).findByIdAndTenantId(fr.getId(), tenant);
        verify(documentoRepo, never()).findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(any(), any());
    }

    @Test
    void detalheDaNcComOrigemForaDoTenantNaoExpoeOrigem() {
        UUID origemDeOutro = UUID.randomUUID();
        DocumentoFiscal n = nc(1, 71, origemDeOutro, "20.00");
        when(documentoRepo.findByIdAndTenantId(n.getId(), tenant)).thenReturn(Optional.of(n));
        when(documentoRepo.findByIdAndTenantId(origemDeOutro, tenant)).thenReturn(Optional.empty());

        assertNull(servico.detalhe(tenant, n.getId()).documentoOrigem());
        verify(documentoRepo).findByIdAndTenantId(origemDeOutro, tenant);
    }

    @Test
    void detalheNaoExpoeAChaveNemOIdDeQuemEmitiu() {
        Set<String> componentes = java.util.Arrays.stream(DocumentoFiscalDetalheResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(componentes.contains("chaveIdempotencia"));
        assertFalse(componentes.contains("emitidoPorId"));
        Set<String> resumo = java.util.Arrays.stream(DocumentoFiscalResumoResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertFalse(resumo.contains("chaveIdempotencia"));
        assertFalse(resumo.contains("emitidoPorId"));
    }

    // ------------------------------------------------------------------ referências por pagamento

    @Test
    void referenciasSemIdsNaoConsultam() {
        assertTrue(servico.referenciasPorPagamento(tenant, List.of()).isEmpty());
        assertTrue(servico.referenciasPorPagamento(tenant, null).isEmpty());
        verifyNoInteractions(documentoRepo);
    }

    @Test
    void referenciasNumaSoConsulta() {
        DocumentoFiscal d = doc(5, 50);
        when(documentoRepo.findByTenantIdAndPagamentoIdIn(eq(tenant), any())).thenReturn(List.of(d));

        Map<Integer, DocumentoFiscalRef> r = servico.referenciasPorPagamento(tenant, List.of(50, 51));

        verify(documentoRepo, times(1)).findByTenantIdAndPagamentoIdIn(eq(tenant), any());
        assertEquals(Map.of(50, new DocumentoFiscalRef(d.getId(), "SIM-FR-2026/5")), r);
        assertNull(r.get(51), "pagamento sem faturação fica sem documento");
    }

    @Test
    void referenciasDevolvemSoFaturasRecibo() {
        DocumentoFiscal fr = doc(5, 50);
        DocumentoFiscal estorno = nc(1, 51, fr.getId(), "10.00");
        when(documentoRepo.findByTenantIdAndPagamentoIdIn(eq(tenant), any())).thenReturn(List.of(fr, estorno));

        Map<Integer, DocumentoFiscalRef> r = servico.referenciasPorPagamento(tenant, List.of(50, 51));

        assertEquals(Map.of(50, DocumentoFiscalRef.de(fr)), r);
    }

    @Test
    void estornosDevolvemSoNotasDeCreditoPeloPagamentoDeEstorno() {
        DocumentoFiscal fr = doc(5, 50);
        DocumentoFiscal estorno = nc(1, 51, fr.getId(), "10.00");
        when(documentoRepo.findByTenantIdAndPagamentoIdIn(eq(tenant), any())).thenReturn(List.of(fr, estorno));

        Map<Integer, DocumentoFiscalRef> r = servico.estornosPorPagamento(tenant, List.of(50, 51));

        verify(documentoRepo, times(1)).findByTenantIdAndPagamentoIdIn(eq(tenant), any());
        assertEquals(Map.of(51, new DocumentoFiscalRef(estorno.getId(), "SIM-NC-2026/1")), r);
    }

    @Test
    void estornosSemIdsNaoConsultam() {
        assertTrue(servico.estornosPorPagamento(tenant, List.of()).isEmpty());
        assertTrue(servico.estornosPorPagamento(tenant, null).isEmpty());
        verifyNoInteractions(documentoRepo);
    }

    @Test
    void eEstornoDeNotaCreditoDelegaComTipoNc() {
        when(documentoRepo.existsByTenantIdAndPagamentoIdAndTipo(tenant, 51, TipoDocumentoFiscal.NC)).thenReturn(true);
        assertTrue(servico.eEstornoDeNotaCredito(tenant, 51));
        assertFalse(servico.eEstornoDeNotaCredito(tenant, 50));
        verify(documentoRepo).existsByTenantIdAndPagamentoIdAndTipo(tenant, 50, TipoDocumentoFiscal.NC);
    }

    @Test
    void existeParaPagamentoContinuaVerdadeiroParaUmEstorno() {
        // Uma NC tem documento no seu pagamento de estorno: a guarda D-14 já o bloqueia.
        when(documentoRepo.existsByTenantIdAndPagamentoId(tenant, 51)).thenReturn(true);
        assertTrue(servico.existeParaPagamento(tenant, 51));
        verify(documentoRepo, never()).existsByTenantIdAndPagamentoIdAndTipo(any(), any(), any());
    }

    // ------------------------------------------------------------------ guardas e fusão

    @Test
    void existeParaDelegaNosExistsDoTenant() {
        UUID cliente = UUID.randomUUID();
        UUID processo = UUID.randomUUID();
        when(documentoRepo.existsByTenantIdAndPagamentoId(tenant, 1)).thenReturn(true);
        when(documentoRepo.existsByTenantIdAndClienteId(tenant, cliente)).thenReturn(true);
        when(documentoRepo.existsByTenantIdAndProcessoId(tenant, processo)).thenReturn(false);
        when(documentoRepo.existsByTenantIdAndHonorarioId(tenant, 2)).thenReturn(true);

        assertTrue(servico.existeParaPagamento(tenant, 1));
        assertTrue(servico.existeParaCliente(tenant, cliente));
        assertFalse(servico.existeParaProcesso(tenant, processo));
        assertTrue(servico.existeParaHonorario(tenant, 2));
        assertFalse(servico.existeParaPagamento(tenant, 99));
    }

    @Test
    void repontarClienteDelegaEExigeTransacaoExistente() throws NoSuchMethodException {
        UUID antigo = UUID.randomUUID();
        UUID novo = UUID.randomUUID();
        when(ligacaoRepo.repontarCliente(tenant, antigo, novo)).thenReturn(4);
        assertEquals(4, servico.repontarCliente(tenant, antigo, novo));
        verify(ligacaoRepo).repontarCliente(tenant, antigo, novo);

        Method m = DocumentoFiscalService.class.getMethod("repontarCliente", UUID.class, UUID.class, UUID.class);
        Transactional t = m.getAnnotation(Transactional.class);
        assertNotNull(t);
        assertEquals(Propagation.MANDATORY, t.propagation());
    }

    @Test
    void leiturasSaoReadOnlyETodoMetodoPublicoRecebeTenantPrimeiro() {
        for (Method m : DocumentoFiscalService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }
            assertTrue(m.getParameterCount() > 0 && m.getParameterTypes()[0].equals(UUID.class),
                    m.getName() + " tem de receber o tenantId (UUID) como primeiro parâmetro");
            Transactional t = m.getAnnotation(Transactional.class);
            assertNotNull(t, m.getName() + " sem @Transactional");
            if (!m.getName().equals("repontarCliente")) {
                assertTrue(t.readOnly(), m.getName() + " tem de ser readOnly");
            }
        }
    }

    @Test
    void naoLeOContextoDeSeguranca() throws IOException {
        String fonte = Files.readString(Path.of("src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java"));
        assertFalse(fonte.contains("SecurityContextHolder"));
    }

    // ------------------------------------------------------------------ Phase 137: entrega por email

    private EntregaEmailFiscal entrega(DocumentoFiscal d, EstadoEntregaEmail estado) {
        return EntregaEmailFiscal.builder().id(UUID.randomUUID()).tenantId(tenant).documentoFiscalId(d.getId())
                .estado(estado).destinatario("antigo@exemplo.cv").tentativas(2)
                .ultimaTentativaEm(Instant.parse("2026-10-04T12:05:00Z"))
                .proximaTentativaEm(Instant.parse("2026-10-04T12:20:00Z"))
                .ultimoErro("O servidor de email não respondeu. Nova tentativa automática.")
                .build();
    }

    private void cenario(DocumentoFiscal d, EstadoEntregaEmail estado, EstadoComunicacaoFiscal comunicacao,
                         boolean envioAutomatico, String emailCliente) {
        when(documentoRepo.findByIdAndTenantId(d.getId(), tenant)).thenReturn(Optional.of(d));
        ComunicacaoFiscal c = ComunicacaoFiscal.builder().tenantId(tenant).documentoFiscalId(d.getId())
                .ambiente(AmbienteFiscal.SIMULADO).estado(comunicacao).build();
        when(comunicacaoRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId())).thenReturn(Optional.of(c));
        if (estado != null) {
            when(entregaRepo.findByTenantIdAndDocumentoFiscalId(tenant, d.getId()))
                    .thenReturn(Optional.of(entrega(d, estado)));
        }
        ConfiguracaoFiscal cfg = ConfiguracaoFiscal.builder().tenantId(tenant).envioEmailAutomatico(envioAutomatico)
                .build();
        when(configRepo.findByTenantId(tenant)).thenReturn(Optional.of(cfg));
        Cliente cliente = Cliente.builder().id(d.getClienteId()).tenantId(tenant).email(emailCliente).build();
        when(clienteRepo.findById(d.getClienteId())).thenReturn(Optional.of(cliente));
    }

    @Test
    void detalheFalhouComSmtpEEnvioLigadoEReenviavel() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.FALHOU, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo.cv");

        EntregaEmailResumo e = servico.detalhe(tenant, d.getId()).entregaEmail();

        assertNotNull(e);
        assertEquals("FALHOU", e.estado());
        assertEquals("O servidor de email não respondeu. Nova tentativa automática.", e.ultimoErro());
        assertTrue(e.reenviavel());
        assertEquals("ana@exemplo.cv", e.emailDestinatario());
        assertEquals("antigo@exemplo.cv", e.destinatario());
        assertEquals(2, e.tentativas());
        assertEquals(Instant.parse("2026-10-04T12:05:00Z"), e.ultimaTentativaEm());
    }

    @Test
    void detalheSemSmtpPendenteApareceNaoConfiguradoSemErroNemReenvio() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.PENDENTE, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo.cv");

        EntregaEmailResumo e = servico(false).detalhe(tenant, d.getId()).entregaEmail();

        assertEquals("NAO_CONFIGURADO", e.estado());
        assertFalse(e.reenviavel());
        assertNull(e.ultimoErro());
        assertNull(e.destinatario());
        assertNull(e.proximaTentativaEm());
    }

    @Test
    void detalheSemEmailSoEReenviavelDepoisDeOClienteTerEmail() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.SEM_EMAIL, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, null);
        EntregaEmailResumo antes = servico.detalhe(tenant, d.getId()).entregaEmail();
        assertEquals("SEM_EMAIL", antes.estado());
        assertNull(antes.emailDestinatario());
        assertNull(antes.destinatario());
        assertFalse(antes.reenviavel());

        cenario(d, EstadoEntregaEmail.SEM_EMAIL, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, " ana@exemplo.cv ");
        EntregaEmailResumo depois = servico.detalhe(tenant, d.getId()).entregaEmail();
        assertEquals("ana@exemplo.cv", depois.emailDestinatario());
        assertTrue(depois.reenviavel());
    }

    @Test
    void detalheComEmailInvalidoDoClienteNaoOExpoeNemPermiteReenvio() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.FALHOU, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo\r\nBcc:x@y.cv");
        EntregaEmailResumo e = servico.detalhe(tenant, d.getId()).entregaEmail();
        assertNull(e.emailDestinatario());
        assertFalse(e.reenviavel());
    }

    @Test
    void detalheDesligadoNuncaEReenviavel() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.DESLIGADO, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo.cv");
        EntregaEmailResumo e = servico.detalhe(tenant, d.getId()).entregaEmail();
        assertEquals("DESLIGADO", e.estado());
        assertFalse(e.reenviavel());
        assertNull(e.ultimoErro());
    }

    @Test
    void detalheSemLinhaDeEntregaTemEntregaNulaENaoLeClienteNemConfiguracao() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, null, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo.cv");
        assertNull(servico.detalhe(tenant, d.getId()).entregaEmail());
        verify(clienteRepo, never()).findById(any());
        verify(configRepo, never()).findByTenantId(any());
    }

    @Test
    void detalheIgnoraClienteDeOutroTenantComOMesmoId() {
        DocumentoFiscal d = doc(1, 10);
        cenario(d, EstadoEntregaEmail.FALHOU, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true, "ana@exemplo.cv");
        Cliente outro = Cliente.builder().id(d.getClienteId()).tenantId(UUID.randomUUID()).email("x@outro.cv").build();
        when(clienteRepo.findById(d.getClienteId())).thenReturn(Optional.of(outro));

        EntregaEmailResumo e = servico.detalhe(tenant, d.getId()).entregaEmail();

        assertNull(e.emailDestinatario());
        assertFalse(e.reenviavel());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarCarregaOsEstadosDeEntregaNumaSoChamada() {
        DocumentoFiscal d1 = doc(3, 30);
        DocumentoFiscal d2 = doc(2, 20);
        DocumentoFiscal d3 = doc(1, 10);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(d1, d2, d3)));
        when(entregaRepo.findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), any()))
                .thenReturn(List.of(entrega(d1, EstadoEntregaEmail.ENVIADO), entrega(d2, EstadoEntregaEmail.PENDENTE)));

        Page<DocumentoFiscalResumoResponse> r = servico.listar(tenant, null, null, null, null, null, 0, 10);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(entregaRepo, times(1)).findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), ids.capture());
        assertEquals(Set.of(d1.getId(), d2.getId(), d3.getId()), Set.copyOf(ids.getValue()));
        assertEquals("ENVIADO", r.getContent().get(0).estadoEntregaEmail());
        assertEquals("PENDENTE", r.getContent().get(1).estadoEntregaEmail());
        assertNull(r.getContent().get(2).estadoEntregaEmail());
    }

    @Test
    void listarSemSmtpApresentaNaoConfiguradoMasMantemOHistorico() {
        DocumentoFiscal d1 = doc(3, 30);
        DocumentoFiscal d2 = doc(2, 20);
        when(documentoRepo.buscar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(d1, d2)));
        when(entregaRepo.findByTenantIdAndDocumentoFiscalIdIn(eq(tenant), any()))
                .thenReturn(List.of(entrega(d1, EstadoEntregaEmail.ENVIADO), entrega(d2, EstadoEntregaEmail.PENDENTE)));

        Page<DocumentoFiscalResumoResponse> r = servico(false).listar(tenant, null, null, null, null, null, 0, 10);

        assertEquals("ENVIADO", r.getContent().get(0).estadoEntregaEmail());
        assertEquals("NAO_CONFIGURADO", r.getContent().get(1).estadoEntregaEmail());
    }
}
