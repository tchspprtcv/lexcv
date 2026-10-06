package com.lexcv.services.fiscal;

import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.InjetorFalhas;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.fiscal.efatura.SimuladoEfaturaGateway;
import com.lexcv.fiscal.efatura.TransmissaoEfatura;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 136-13: o pipeline completo com os componentes REAIS do formato (IudGerador, DfeXmlBuilder,
 * DfeMarshaller, DfeValidador) e o adaptador simulado real; só as transações e a notificação são
 * simuladas.
 */
class ProcessadorComunicacaoFiscalPipelineTest {

    private static final String NS = "urn:cv:efatura:xsd:v1.0";
    private static final Instant AGORA = Instant.parse("2026-06-15T13:00:00Z");
    private static final String NIF = "512345679";

    private final ComunicacaoFiscalTransacoes transacoes = mock(ComunicacaoFiscalTransacoes.class);
    private final NotificacaoComunicacaoFiscal notificacao = mock(NotificacaoComunicacaoFiscal.class);
    private final DfeValidador validador = new DfeValidador();
    private final TransmissaoEfatura transmissao = new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", "3.0.0");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID frId = UUID.randomUUID();
    private final UUID ncId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenReturn(1);
        when(transacoes.renovarLease(any(), any())).thenReturn(true);
        when(transacoes.gravarXml(any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(inv -> {
                    DocumentoFiscalXml row = mock(DocumentoFiscalXml.class);
                    String iud = inv.getArgument(2);
                    String xml = inv.getArgument(7);
                    when(row.getIud()).thenReturn(iud);
                    when(row.getXml()).thenReturn(xml);
                    return Optional.of(row);
                });
    }

    private ProcessadorComunicacaoFiscal processador(InjetorFalhas injetor) {
        return new ProcessadorComunicacaoFiscal(transacoes, new DfeXmlBuilder(), new DfeMarshaller(), validador,
                new IudGerador(), new SimuladoEfaturaGateway(validador, injetor), transmissao, notificacao,
                Clock.fixed(AGORA, ZoneOffset.UTC), java.time.Duration.ofMinutes(2));
    }

    // ---- fixtures ----

    private DocumentoFiscal fr(String firma) {
        return DocumentoFiscal.builder()
                .id(frId).tenantId(tenantId).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo("SIM-FR-2026").ano(2026).numero(1L).numeroFormatado("SIM-FR-2026/1")
                .dataEmissao(LocalDate.of(2026, 6, 15)).emitidoEm(Instant.parse("2026-06-15T12:34:56Z"))
                .emitenteNif(NIF).emitenteFirma(firma).emitenteMorada("Avenida Amílcar Cabral, 12")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Ana Lopes").adquirenteMorada("Rua da Achada")
                .adquirenteLocalidade("Mindelo").meioPagamentoCodigo("10").metodoPagamento("NUMERARIO")
                .moeda("CVE").taxaIva(new BigDecimal("15.0000")).taxaRetencao(new BigDecimal("20.0000"))
                .totalBase(new BigDecimal("104347.83")).totalIva(new BigDecimal("15652.17"))
                .totalRetencao(new BigDecimal("20869.57")).totalDocumento(new BigDecimal("120000.00"))
                .valorLiquido(new BigDecimal("99130.43"))
                .build();
    }

    private DocumentoFiscalLinha linhaFr() {
        return DocumentoFiscalLinha.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).documentoFiscalId(frId).numeroLinha(1)
                .descricao("Honorários do processo").quantidade(new BigDecimal("1.0000"))
                .precoUnitario(new BigDecimal("104347.83")).valorBase(new BigDecimal("104347.83"))
                .taxaIva(new BigDecimal("15.0000")).valorIva(new BigDecimal("15652.17"))
                .taxaRetencao(new BigDecimal("20.0000")).valorRetencao(new BigDecimal("20869.57"))
                .totalLinha(new BigDecimal("120000.00"))
                .build();
    }

    private DocumentoFiscal nc() {
        return DocumentoFiscal.builder()
                .id(ncId).tenantId(tenantId).tipo(TipoDocumentoFiscal.NC).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo("SIM-NC-2026").ano(2026).numero(1L).numeroFormatado("SIM-NC-2026/1")
                .dataEmissao(LocalDate.of(2026, 6, 16)).emitidoEm(Instant.parse("2026-06-16T09:00:00Z"))
                .emitenteNif(NIF).emitenteFirma("Silva & Associados").emitenteMorada("Avenida Amílcar Cabral, 12")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Ana Lopes").adquirenteMorada("Rua da Achada")
                .adquirenteLocalidade("Mindelo").meioPagamentoCodigo("10").moeda("CVE")
                .documentoOrigemId(frId).motivoCodigo(MotivoNotaCredito.CORRECAO_VALOR)
                .taxaIva(new BigDecimal("15.0000"))
                .totalBase(new BigDecimal("50.00")).totalIva(new BigDecimal("7.50"))
                .totalRetencao(new BigDecimal("0.00")).totalDocumento(new BigDecimal("57.50"))
                .valorLiquido(new BigDecimal("57.50"))
                .build();
    }

    private DocumentoFiscalLinha linhaNc() {
        return DocumentoFiscalLinha.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).documentoFiscalId(ncId).numeroLinha(1)
                .descricao("Nota de crédito").quantidade(new BigDecimal("1.0000"))
                .precoUnitario(new BigDecimal("50.00")).valorBase(new BigDecimal("50.00"))
                .taxaIva(new BigDecimal("15.0000")).valorIva(new BigDecimal("7.50"))
                .valorRetencao(new BigDecimal("0.00")).totalLinha(new BigDecimal("57.50"))
                .build();
    }

    private ComunicacaoReclamada item(UUID documentoId, int tentativas, int reprocessamentos) {
        return new ComunicacaoReclamada(UUID.randomUUID(), tenantId, documentoId, AmbienteFiscal.SIMULADO,
                tentativas, 1L, reprocessamentos);
    }

    private void snapshotFr(String firma) {
        when(transacoes.carregarSnapshot(tenantId, frId)).thenReturn(Optional.of(new SnapshotComunicacao(
                fr(firma), linhaFr(), Optional.empty(), Optional.empty(), Optional.empty())));
    }

    /** Corre a FR pelo pipeline e devolve {iud, xml} capturados no gravarXml. */
    private List<String> correrFr() {
        snapshotFr("Silva & Associados");
        ComunicacaoReclamada item = item(frId, 1, 0);
        processador(InjetorFalhas.NENHUMA).processar(item);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);
        ArgumentCaptor<String> iud = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
        verify(transacoes).gravarXml(eq(tenantId), eq(frId), iud.capture(), eq(AmbienteFiscal.SIMULADO), eq(3),
                eq(99999), eq("2024-05-27"), xml.capture(), any());
        return List.of(iud.getValue(), xml.getValue());
    }

    private static Document dom(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String v(Document d, String expr) throws Exception {
        XPath x = XPathFactory.newInstance().newXPath();
        x.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "e".equals(prefix) ? NS : XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });
        return x.evaluate(expr, d);
    }

    // ---- behaviours ----

    @Test
    void fr_chegaAAceiteSimuladoComXmlValidoEIudCv3() throws Exception {
        List<String> r = correrFr();
        String iud = r.get(0);
        String xml = r.get(1);

        assertThat(iud).startsWith("CV3").hasSize(45);
        assertThat(validador.validar(xml.getBytes(StandardCharsets.UTF_8)).valido()).isTrue();
        Document d = dom(xml);
        assertThat(v(d, "/e:Dfe/@Id")).isEqualTo(iud);
        assertThat(v(d, "//e:RepositoryCode")).isEqualTo("3");
    }

    @Test
    void nc_comOIudDaFr_chegaAAceiteSimuladoEReferenciaAFr() throws Exception {
        String iudFr = correrFr().get(0);
        when(transacoes.carregarSnapshot(tenantId, ncId)).thenReturn(Optional.of(new SnapshotComunicacao(
                nc(), linhaNc(), Optional.empty(), Optional.of(iudFr), Optional.of("SIM-FR-2026/1"))));
        ComunicacaoReclamada item = item(ncId, 1, 0);

        processador(InjetorFalhas.NENHUMA).processar(item);

        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);
        ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
        verify(transacoes).gravarXml(eq(tenantId), eq(ncId), any(), any(), anyInt(), anyInt(), any(),
                xml.capture(), any());
        assertThat(validador.validar(xml.getValue().getBytes(StandardCharsets.UTF_8)).valido()).isTrue();
        assertThat(v(dom(xml.getValue()), "//e:References/e:Reference/e:FiscalDocument")).isEqualTo(iudFr);
    }

    @Test
    void falhaTransitoriaPersistente_naOitavaTentativa_eErroENotifica() {
        snapshotFr("Silva & Associados");
        ComunicacaoReclamada item = item(frId, 8, 1);

        processador(InjetorFalhas.SEMPRE_TRANSITORIA).processar(item);

        verify(transacoes).registarResultado(eq(item), eq(EstadoComunicacaoFiscal.ERRO), eq("FALHA_SIMULADA"),
                any(), isNull());
        verify(notificacao).notificarFalhaPersistente(tenantId, frId, "SIM-FR-2026/1", 1);
    }

    @Test
    void firmaCom151Caracteres_eRejeitadoSemGravarXml() {
        snapshotFr("F".repeat(151));
        ComunicacaoReclamada item = item(frId, 1, 0);

        processador(InjetorFalhas.NENHUMA).processar(item);

        verify(transacoes).registarResultado(eq(item), eq(EstadoComunicacaoFiscal.REJEITADO),
                eq("FIRMA_EXCEDE_150"), any(), isNull());
        verify(transacoes, never()).gravarXml(any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any());
    }
}
