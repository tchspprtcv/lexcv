package com.lexcv.fiscal.efatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexcv.fiscal.efatura.xsd.Dfe;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Phase 136 (DFE-01, DFE-02, DFE-06): cada documento construído a partir do snapshot é
 * serializado e validado contra o XSD vendorizado; os valores são verificados por XPath.
 */
class DfeXmlBuilderTest {

    private static final String NS = "urn:cv:efatura:xsd:v1.0";
    private static final String NIF_EMITENTE = "512345679";

    private final DfeXmlBuilder builder = new DfeXmlBuilder();
    private final DfeMarshaller marshaller = new DfeMarshaller();
    private final DfeValidador validador = new DfeValidador();
    private final TransmissaoEfatura transmissao =
            new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", "3.0.0");
    private IudGerador iudGerador;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        SecureRandom semente = SecureRandom.getInstance("SHA1PRNG");
        semente.setSeed(42L);
        iudGerador = new IudGerador(semente);
    }

    // ---- fixtures ----

    private static DocumentoComunicavel.Linha linha(BigDecimal base, BigDecimal taxaIva, String motivoIsencao,
                                                    BigDecimal taxaRetencao, BigDecimal valorRetencao,
                                                    String descricao) {
        return new DocumentoComunicavel.Linha(1, descricao, new BigDecimal("1.0000"), base, base, taxaIva,
                motivoIsencao, taxaRetencao, valorRetencao);
    }

    private static DocumentoComunicavel documento(TipoDocumentoFiscal tipo, long numero, String firma,
                                                  String nomeAdquirente, RegimeIva regime,
                                                  BigDecimal base, BigDecimal iva, BigDecimal retencao,
                                                  BigDecimal total, BigDecimal liquido,
                                                  MotivoNotaCredito motivo, String iudOrigem,
                                                  DocumentoComunicavel.Linha linha) {
        String serie = tipo == TipoDocumentoFiscal.FR ? "SIM-FR-2026" : "SIM-NC-2026";
        return new DocumentoComunicavel(tipo, AmbienteFiscal.SIMULADO, serie, numero, serie + "/" + numero,
                LocalDate.of(2026, 6, 15), LocalTime.of(11, 34, 56),
                NIF_EMITENTE, firma, "Avenida Amílcar Cabral, 12", "Praia", regime,
                "123456789", nomeAdquirente, "Rua da Achada", "Mindelo",
                "10", base, iva, retencao, total, liquido,
                motivo, tipo == TipoDocumentoFiscal.NC ? "SIM-FR-2026/1" : null, iudOrigem, linha);
    }

    private static DocumentoComunicavel frComIr() {
        return documento(TipoDocumentoFiscal.FR, 1, "Silva & Associados", "Ana Lopes", RegimeIva.NORMAL,
                new BigDecimal("104347.83"), new BigDecimal("15652.17"), new BigDecimal("20869.57"),
                new BigDecimal("120000.00"), new BigDecimal("99130.43"), null, null,
                linha(new BigDecimal("104347.83"), new BigDecimal("15.0000"), null,
                        new BigDecimal("20.0000"), new BigDecimal("20869.57"), "Honorários do processo"));
    }

    private static DocumentoComunicavel frSemIr() {
        return documento(TipoDocumentoFiscal.FR, 2, "Silva & Associados", "Ana Lopes", RegimeIva.NORMAL,
                new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("0.00"),
                new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários do processo"));
    }

    private static DocumentoComunicavel frIsento() {
        return documento(TipoDocumentoFiscal.FR, 3, "Silva & Associados", "Ana Lopes", RegimeIva.ISENTO,
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("0.0000"), "1", null,
                        new BigDecimal("0.00"), "Honorários do processo"));
    }

    private String iudFr() {
        return iudGerador.gerar(3, LocalDate.of(2026, 6, 15), NIF_EMITENTE, 99999, 2, 1);
    }

    private static DocumentoComunicavel ncParcial(String iudOrigem) {
        return documento(TipoDocumentoFiscal.NC, 1, "Silva & Associados", "Ana Lopes", RegimeIva.NORMAL,
                new BigDecimal("50.00"), new BigDecimal("7.50"), new BigDecimal("0.00"),
                new BigDecimal("57.50"), new BigDecimal("57.50"), MotivoNotaCredito.CORRECAO_VALOR, iudOrigem,
                linha(new BigDecimal("50.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Nota de crédito"));
    }

    private static DocumentoComunicavel ncTotal(String iudOrigem) {
        return documento(TipoDocumentoFiscal.NC, 2, "Silva & Associados", "Ana Lopes", RegimeIva.NORMAL,
                new BigDecimal("104347.83"), new BigDecimal("15652.17"), new BigDecimal("20869.57"),
                new BigDecimal("120000.00"), new BigDecimal("99130.43"), MotivoNotaCredito.ANULACAO_TOTAL,
                iudOrigem,
                linha(new BigDecimal("104347.83"), new BigDecimal("15.0000"), null,
                        new BigDecimal("20.0000"), new BigDecimal("20869.57"), "Nota de crédito"));
    }

    // ---- helpers ----

    private byte[] construirEValidar(DocumentoComunicavel doc, String iud) {
        Dfe dfe = builder.construir(doc, iud, transmissao);
        byte[] xml = marshaller.marshal(dfe);
        ResultadoValidacao r = validador.validar(xml);
        assertThat(r.valido()).as("XSD: %s linha %s coluna %s\n%s", r.codigo(), r.linha(), r.coluna(),
                new String(xml, StandardCharsets.UTF_8)).isTrue();
        return xml;
    }

    private static Document dom(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static XPath xpath() {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "e".equals(prefix) ? NS : XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String uri) {
                return NS.equals(uri) ? "e" : null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return java.util.List.of("e").iterator();
            }
        });
        return xp;
    }

    private static String v(Document d, String expr) throws Exception {
        return xpath().evaluate(expr, d);
    }

    private static int n(Document d, String expr) throws Exception {
        return ((NodeList) xpath().evaluate(expr, d, XPathConstants.NODESET)).getLength();
    }

    // ---- FR ----

    @Test
    void frNormalComRetencaoValidaECarregaOsValoresDoSnapshot() throws Exception {
        String iud = iudFr();
        Document d = dom(construirEValidar(frComIr(), iud));
        String fr = "/e:Dfe/e:InvoiceReceipt";

        assertThat(v(d, "/e:Dfe/@Id")).isEqualTo(iud);
        assertThat(v(d, "/e:Dfe/@Version")).isEqualTo("1.0");
        assertThat(v(d, "/e:Dfe/@DocumentTypeCode")).isEqualTo("2");
        assertThat(v(d, "/e:Dfe/e:RepositoryCode")).isEqualTo("3");
        assertThat(n(d, "/e:Dfe/e:IsSpecimen")).isZero();
        assertThat(v(d, fr + "/e:LedCode")).isEqualTo("99999");
        assertThat(v(d, fr + "/e:Serie")).isEqualTo("SIM-FR-2026");
        assertThat(v(d, fr + "/e:DocumentNumber")).isEqualTo("1");
        assertThat(v(d, fr + "/e:IssueDate")).isEqualTo("2026-06-15");
        assertThat(v(d, fr + "/e:IssueTime")).isEqualTo("11:34:56");
        assertThat(v(d, fr + "/e:EmitterParty/e:TaxId")).isEqualTo(NIF_EMITENTE);
        assertThat(v(d, fr + "/e:EmitterParty/e:TaxId/@CountryCode")).isEqualTo("CV");
        assertThat(v(d, fr + "/e:EmitterParty/e:Name")).isEqualTo("Silva & Associados");
        assertThat(v(d, fr + "/e:EmitterParty/e:Address/e:AddressDetail")).isEqualTo("Avenida Amílcar Cabral, 12");
        assertThat(v(d, fr + "/e:EmitterParty/e:Address/e:City")).isEqualTo("Praia");
        assertThat(n(d, fr + "/e:EmitterParty/e:Contacts")).isZero();
        assertThat(v(d, fr + "/e:ReceiverParty/e:TaxId")).isEqualTo("123456789");
        assertThat(v(d, fr + "/e:ReceiverParty/e:Name")).isEqualTo("Ana Lopes");

        String l = fr + "/e:Lines/e:Line";
        assertThat(n(d, l)).isEqualTo(1);
        assertThat(v(d, l + "/e:Id")).isEqualTo("1");
        assertThat(v(d, l + "/e:Quantity")).isEqualTo("1");
        assertThat(v(d, l + "/e:Quantity/@UnitCode")).isEqualTo("EA");
        assertThat(new BigDecimal(v(d, l + "/e:Price"))).isEqualByComparingTo("104347.83");
        assertThat(new BigDecimal(v(d, l + "/e:NetTotal"))).isEqualByComparingTo("104347.83");
        assertThat(n(d, l + "/e:Tax")).isEqualTo(2);
        assertThat(v(d, l + "/e:Tax[@TaxTypeCode='IVA']/e:TaxPercentage")).isEqualTo("15");
        assertThat(v(d, l + "/e:Tax[@TaxTypeCode='IR']/e:TaxPercentage")).isEqualTo("20");
        assertThat(v(d, l + "/e:Item/e:Description")).isEqualTo("Honorários do processo");
        assertThat(v(d, l + "/e:Item/e:EmitterIdentification")).isEqualTo("HONORARIOS");

        String t = fr + "/e:Totals";
        assertThat(new BigDecimal(v(d, t + "/e:PriceExtensionTotalAmount"))).isEqualByComparingTo("104347.83");
        assertThat(new BigDecimal(v(d, t + "/e:ChargeTotalAmount"))).isEqualByComparingTo("0");
        assertThat(new BigDecimal(v(d, t + "/e:DiscountTotalAmount"))).isEqualByComparingTo("0");
        assertThat(new BigDecimal(v(d, t + "/e:NetTotalAmount"))).isEqualByComparingTo("104347.83");
        assertThat(new BigDecimal(v(d, t + "/e:TaxTotalAmount"))).isEqualByComparingTo("15652.17");
        assertThat(new BigDecimal(v(d, t + "/e:WithholdingTaxTotalAmount"))).isEqualByComparingTo("20869.57");
        assertThat(new BigDecimal(v(d, t + "/e:PayableAmount"))).isEqualByComparingTo("99130.43");

        assertThat(n(d, fr + "/e:Payments/e:Payment")).isEqualTo(1);
        assertThat(v(d, fr + "/e:Payments/e:Payment/e:PaymentMeansCode")).isEqualTo("10");
        assertThat(v(d, fr + "/e:Payments/e:Payment/e:PaymentDate")).isEqualTo("2026-06-15");
        assertThat(new BigDecimal(v(d, fr + "/e:Payments/e:Payment/e:PaymentAmount"))).isEqualByComparingTo("99130.43");
        assertThat(n(d, fr + "/e:Note")).isZero();

        assertThat(v(d, "/e:Dfe/e:Transmission/e:IssueMode")).isEqualTo("1");
        assertThat(v(d, "/e:Dfe/e:Transmission/e:TransmitterTaxId")).isEqualTo("512345679");
        assertThat(v(d, "/e:Dfe/e:Transmission/e:Software/e:Code")).isEqualTo("LEXCVSIM");
        assertThat(v(d, "/e:Dfe/e:Transmission/e:Software/e:Name")).isEqualTo("LexCV");
        assertThat(v(d, "/e:Dfe/e:Transmission/e:Software/e:Version")).isEqualTo("3.0.0");
    }

    @Test
    void frSemRetencaoTemUmSoImpostoESemTotalDeRetencao() throws Exception {
        Document d = dom(construirEValidar(frSemIr(), iudGerador.gerar(3, LocalDate.of(2026, 6, 15),
                NIF_EMITENTE, 99999, 2, 2)));
        String fr = "/e:Dfe/e:InvoiceReceipt";
        assertThat(n(d, fr + "/e:Lines/e:Line/e:Tax")).isEqualTo(1);
        assertThat(v(d, fr + "/e:Lines/e:Line/e:Tax/@TaxTypeCode")).isEqualTo("IVA");
        assertThat(n(d, fr + "/e:Totals/e:WithholdingTaxTotalAmount")).isZero();
        assertThat(new BigDecimal(v(d, fr + "/e:Totals/e:PayableAmount"))).isEqualByComparingTo("115");
    }

    @Test
    void frIsentoUsaNaComMotivoDeIsencao() throws Exception {
        Document d = dom(construirEValidar(frIsento(), iudGerador.gerar(3, LocalDate.of(2026, 6, 15),
                NIF_EMITENTE, 99999, 2, 3)));
        String l = "/e:Dfe/e:InvoiceReceipt/e:Lines/e:Line";
        assertThat(n(d, l + "/e:Tax")).isEqualTo(1);
        assertThat(v(d, l + "/e:Tax/@TaxTypeCode")).isEqualTo("NA");
        assertThat(v(d, l + "/e:Tax/e:TaxExemptionReasonCode")).isEqualTo("1");
        assertThat(n(d, l + "/e:Tax/e:TaxPercentage")).isZero();
        assertThat(new BigDecimal(v(d, "/e:Dfe/e:InvoiceReceipt/e:Totals/e:TaxTotalAmount"))).isEqualByComparingTo("0");
    }

    // ---- NC ----

    @Test
    void ncParcialReferenciaOIudDaFrEUsaNotaControlada() throws Exception {
        String iudFr = iudFr();
        String iudNc = iudGerador.gerar(3, LocalDate.of(2026, 6, 15), NIF_EMITENTE, 99999, 5, 1);
        Document d = dom(construirEValidar(ncParcial(iudFr), iudNc));
        String nc = "/e:Dfe/e:CreditNote";

        assertThat(v(d, "/e:Dfe/@DocumentTypeCode")).isEqualTo("5");
        assertThat(v(d, "/e:Dfe/e:RepositoryCode")).isEqualTo("3");
        assertThat(v(d, nc + "/e:Serie")).isEqualTo("SIM-NC-2026");
        assertThat(v(d, nc + "/e:LedCode")).isEqualTo("99999");
        assertThat(v(d, nc + "/e:IssueReasonCode")).isEqualTo("2");
        assertThat(v(d, nc + "/e:References/e:Reference/e:FiscalDocument")).isEqualTo(iudFr);
        String nota = v(d, nc + "/e:Note");
        assertThat(nota).isEqualTo("Nota de crédito: Correção de valor — SIM-FR-2026/1");
        assertThat(nota.length()).isGreaterThanOrEqualTo(10);
        assertThat(nota).doesNotContain("  ");
        assertThat(n(d, nc + "/e:Payments")).isZero();
        assertThat(v(d, nc + "/e:Lines/e:Line/e:Item/e:EmitterIdentification")).isEqualTo("NOTACREDITO");
        assertThat(n(d, "/e:Dfe/e:InvoiceReceipt")).isZero();
    }

    @Test
    void ncTotalComRetencaoValida() throws Exception {
        String iudNc = iudGerador.gerar(3, LocalDate.of(2026, 6, 15), NIF_EMITENTE, 99999, 5, 2);
        Document d = dom(construirEValidar(ncTotal(iudFr()), iudNc));
        String nc = "/e:Dfe/e:CreditNote";
        assertThat(v(d, nc + "/e:Note")).isEqualTo("Nota de crédito: Anulação total — SIM-FR-2026/1");
        assertThat(n(d, nc + "/e:Lines/e:Line/e:Tax")).isEqualTo(2);
        assertThat(new BigDecimal(v(d, nc + "/e:Totals/e:WithholdingTaxTotalAmount"))).isEqualByComparingTo("20869.57");
        assertThat(new BigDecimal(v(d, nc + "/e:Totals/e:PayableAmount"))).isEqualByComparingTo("99130.43");
    }

    @Test
    void ncSemIudDeOrigemERecusadaPeloBuilder() {
        DocumentoComunicavel semOrigem = ncParcial(null);
        assertThatThrownBy(() -> builder.construir(semOrigem, iudFr(), transmissao))
                .isInstanceOfSatisfying(RecusaFormatoEfatura.class,
                        r -> assertThat(r.codigo()).isEqualTo("ORIGEM_SEM_IUD"));
    }

    // ---- normalização e recusas ----

    @Test
    void espacosSaoNormalizadosAntesDaSerializacao() throws Exception {
        DocumentoComunicavel doc = documento(TipoDocumentoFiscal.FR, 4, "  Silva  &\tAssociados ",
                "Ana   Lopes\tSilva", RegimeIva.NORMAL,
                new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("0.00"),
                new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários\n  do   processo"));
        Document d = dom(construirEValidar(doc, iudGerador.gerar(3, LocalDate.of(2026, 6, 15),
                NIF_EMITENTE, 99999, 2, 4)));
        String fr = "/e:Dfe/e:InvoiceReceipt";
        assertThat(v(d, fr + "/e:ReceiverParty/e:Name")).isEqualTo("Ana Lopes Silva");
        assertThat(v(d, fr + "/e:EmitterParty/e:Name")).isEqualTo("Silva & Associados");
        assertThat(v(d, fr + "/e:Lines/e:Line/e:Item/e:Description")).isEqualTo("Honorários do processo");
    }

    @Test
    void decimaisSaoEmitidosSemZerosNemExpoente() {
        String xml = new String(marshaller.marshal(builder.construir(frSemIr(), iudFr(), transmissao)),
                StandardCharsets.UTF_8);
        assertThat(xml).contains("<TaxPercentage>15</TaxPercentage>");
        assertThat(xml).contains("<PayableAmount>115</PayableAmount>");
        assertThat(xml).doesNotContain("E+").doesNotContain("15.0000");
    }

    @Test
    void firmaComMaisDe150CaracteresERecusada() {
        DocumentoComunicavel doc = documento(TipoDocumentoFiscal.FR, 5, "F".repeat(151), "Ana Lopes",
                RegimeIva.NORMAL, new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("0.00"),
                new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários"));
        assertThatThrownBy(() -> builder.construir(doc, iudFr(), transmissao))
                .isInstanceOfSatisfying(RecusaFormatoEfatura.class,
                        r -> assertThat(r.codigo()).isEqualTo("FIRMA_EXCEDE_150"));
    }

    @Test
    void firmaDe150CaracteresAposNormalizacaoPassa() {
        DocumentoComunicavel doc = documento(TipoDocumentoFiscal.FR, 5, "  " + "F".repeat(150) + "  ", "Ana Lopes",
                RegimeIva.NORMAL, new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("0.00"),
                new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários"));
        construirEValidar(doc, iudFr());
    }

    @Test
    void numeroForaDoLimiteERecusado() {
        DocumentoComunicavel doc = documento(TipoDocumentoFiscal.FR, 1_000_000_000L, "Silva & Associados",
                "Ana Lopes", RegimeIva.NORMAL, new BigDecimal("100.00"), new BigDecimal("15.00"),
                new BigDecimal("0.00"), new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários"));
        assertThatThrownBy(() -> builder.construir(doc, iudFr(), transmissao))
                .isInstanceOfSatisfying(RecusaFormatoEfatura.class,
                        r -> assertThat(r.codigo()).isEqualTo("NUMERO_FORA_DO_LIMITE"));
    }

    @Test
    void nomeVazioAposNormalizacaoETextoInvalido() {
        DocumentoComunicavel doc = documento(TipoDocumentoFiscal.FR, 6, "Silva & Associados", " \t ",
                RegimeIva.NORMAL, new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("0.00"),
                new BigDecimal("115.00"), new BigDecimal("115.00"), null, null,
                linha(new BigDecimal("100.00"), new BigDecimal("15.0000"), null, null,
                        new BigDecimal("0.00"), "Honorários"));
        assertThatThrownBy(() -> builder.construir(doc, iudFr(), transmissao))
                .isInstanceOfSatisfying(RecusaFormatoEfatura.class,
                        r -> assertThat(r.codigo()).isEqualTo("TEXTO_INVALIDO"));
    }

    @Test
    void motivoTextoNuncaApareceNoXml() {
        String marca = "SEGREDO-PROFISSIONAL-XYZ";
        DocumentoFiscal entidade = DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID())
                .tipo(TipoDocumentoFiscal.NC).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-NC-2026").ano(2026).numero(7L)
                .numeroFormatado("SIM-NC-2026/7").dataEmissao(LocalDate.of(2026, 6, 16))
                .emitidoEm(Instant.parse("2026-06-16T09:00:00Z"))
                .emitenteNif(NIF_EMITENTE).emitenteFirma("Silva & Associados").emitenteMorada("Rua A")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Ana Lopes").adquirenteMorada("Rua B")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(1).pagamentoId(2)
                .documentoOrigemId(UUID.randomUUID()).motivoCodigo(MotivoNotaCredito.OUTRO)
                .motivoTexto(marca)
                .metodoPagamento("DINHEIRO").meioPagamentoCodigo("10").moeda("CVE")
                .taxaIva(new BigDecimal("15.0000")).totalBase(new BigDecimal("100.00"))
                .totalIva(new BigDecimal("15.00")).totalRetencao(new BigDecimal("0.00"))
                .totalDocumento(new BigDecimal("115.00")).valorLiquido(new BigDecimal("115.00"))
                .chaveIdempotencia(UUID.randomUUID())
                .build();
        DocumentoComunicavel doc = DocumentoComunicavel.de(entidade,
                MapeamentoEfaturaTest.linha(new BigDecimal("100.00")), iudFr(), "SIM-FR-2026/1");
        byte[] xml = construirEValidar(doc, iudGerador.gerar(3, LocalDate.of(2026, 6, 16),
                NIF_EMITENTE, 99999, 5, 7));
        assertThat(new String(xml, StandardCharsets.UTF_8)).doesNotContain(marca)
                .contains("Nota de crédito: Outro — SIM-FR-2026/1");
    }
}
