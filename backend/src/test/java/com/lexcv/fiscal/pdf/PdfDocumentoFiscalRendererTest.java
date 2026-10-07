package com.lexcv.fiscal.pdf;

import com.lexcv.models.TipoDocumentoFiscal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 137 (ENTR-01, DFE-06; T-137-19..21): conteúdo, marca de simulação, fontes embebidas e
 * ausência de acesso externo do PDF fiscal, provados por extração de texto e de fontes.
 */
class PdfDocumentoFiscalRendererTest {

    private static final String MARCA = "SIMULAÇÃO — SEM VALIDADE FISCAL";
    private static final String IUD = "CV3260105512345679999990200000000112345678904";
    private static final String NNBSP = " ";

    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final PdfDocumentoFiscalRenderer renderer =
            new PdfDocumentoFiscalRenderer(ClasspathPdfResolver.FOLHA_ESTILO, pedidos::add);

    // ---- fixtures ----

    private static DadosPdfDocumentoFiscal.Emitente emitenteNormal() {
        return new DadosPdfDocumentoFiscal.Emitente("Silva & Associados, Sociedade de Advogados", "512345679",
                "Avenida Cidade de Lisboa, 12", "Praia", "Normal", false, null, null);
    }

    private static DadosPdfDocumentoFiscal.Emitente emitenteIsento() {
        return new DadosPdfDocumentoFiscal.Emitente("Ana Lopes, Advogada", "512345679",
                "Rua 5 de Julho, 3", "Mindelo", "Isento", true, "02", "Isento ao abrigo do artigo 9.º");
    }

    private static DadosPdfDocumentoFiscal.Adquirente adquirente(String nome) {
        return new DadosPdfDocumentoFiscal.Adquirente(nome, "123456789", "Achada Santo António, 7", "Praia");
    }

    private static DadosPdfDocumentoFiscal.Linha linha(String descricao, String base, String taxa, String iva,
                                                        String motivo, String total) {
        return new DadosPdfDocumentoFiscal.Linha(descricao, BigDecimal.ONE, new BigDecimal(base), new BigDecimal(base),
                new BigDecimal(taxa), new BigDecimal(iva), motivo, null, BigDecimal.ZERO, new BigDecimal(total));
    }

    private static DadosPdfDocumentoFiscal fr(String nomeAdquirente, List<DadosPdfDocumentoFiscal.Linha> linhas) {
        return new DadosPdfDocumentoFiscal(TipoDocumentoFiscal.FR, "FR SIM-FR-2026/000123", "SIM-FR-2026",
                LocalDate.of(2026, 3, 7), emitenteNormal(), adquirente(nomeAdquirente), linhas,
                new DadosPdfDocumentoFiscal.Totais(new BigDecimal("10000.00"), new BigDecimal("1500.00"),
                        new BigDecimal("2000.00"), new BigDecimal("11500.00"), new BigDecimal("15.0000"),
                        new BigDecimal("20.0000"), new BigDecimal("9500.00")),
                "CVE", IUD, null, null, null, true);
    }

    private static DadosPdfDocumentoFiscal frNormal() {
        return fr("Maria Fernandes", List.of(linha("Honorários de mandato forense", "10000.00", "15.0000",
                "1500.00", null, "11500.00")));
    }

    private static DadosPdfDocumentoFiscal frIsenta() {
        return new DadosPdfDocumentoFiscal(TipoDocumentoFiscal.FR, "FR SIM-FR-2026/000124", "SIM-FR-2026",
                LocalDate.of(2026, 3, 8), emitenteIsento(), adquirente("João Tavares"),
                List.of(linha("Honorários de consulta jurídica", "5000.00", "0.0000", "0.00", "02", "5000.00")),
                new DadosPdfDocumentoFiscal.Totais(new BigDecimal("5000.00"), BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), new BigDecimal("5000.00"), new BigDecimal("0.0000"), null,
                        new BigDecimal("5000.00")),
                "CVE", IUD, null, null, null, true);
    }

    private static DadosPdfDocumentoFiscal nc() {
        return nc(new DadosPdfDocumentoFiscal.Totais(new BigDecimal("1000.00"), new BigDecimal("150.00"),
                BigDecimal.ZERO.setScale(2), new BigDecimal("1150.00"), new BigDecimal("15.0000"), null,
                new BigDecimal("1150.00")));
    }

    private static DadosPdfDocumentoFiscal ncComRetencao() {
        return nc(new DadosPdfDocumentoFiscal.Totais(new BigDecimal("1000.00"), new BigDecimal("150.00"),
                new BigDecimal("200.00"), new BigDecimal("1150.00"), new BigDecimal("15.0000"),
                new BigDecimal("20.0000"), new BigDecimal("950.00")));
    }

    private static DadosPdfDocumentoFiscal nc(DadosPdfDocumentoFiscal.Totais totais) {
        return new DadosPdfDocumentoFiscal(TipoDocumentoFiscal.NC, "NC SIM-NC-2026/000007", "SIM-NC-2026",
                LocalDate.of(2026, 4, 2), emitenteNormal(), adquirente("Maria Fernandes"),
                List.of(linha("Correção de honorários", "1000.00", "15.0000", "150.00", null, "1150.00")),
                totais,
                "CVE", IUD, new DadosPdfDocumentoFiscal.Origem("FR SIM-FR-2026/000123", LocalDate.of(2026, 3, 7)),
                "Correção de valor", "Valor faturado em excesso no mandato de março", true);
    }

    // ---- helpers ----

    private static List<String> textoPorPagina(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<String> paginas = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                paginas.add(stripper.getText(doc));
            }
            return paginas;
        }
    }

    private static String texto(byte[] pdf) throws IOException {
        return String.join("\n", textoPorPagina(pdf));
    }

    /** Normaliza espaços para comparar frases que o extrator pode partir em linhas. */
    private static String plano(String s) {
        return s.replaceAll("\\s+", " ");
    }

    // ---- testes ----

    @Test
    void frContemTodosOsElementosLegais() throws IOException {
        byte[] pdf = renderer.renderizar(frNormal());
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String t = plano(texto(pdf));

        assertThat(t).contains("Silva & Associados, Sociedade de Advogados", "512345679", "123456789",
                "Avenida Cidade de Lisboa, 12", "Maria Fernandes", "Fatura-Recibo", "FR SIM-FR-2026/000123",
                "Data de emissão: 07/03/2026", "Base tributável", "IVA (15%)", "Retenção na fonte (20%)",
                "Total do documento", "Regime de IVA: Normal", "Achada Santo António, 7");
        assertThat(t.replace(" ", "")).contains(IUD);
        assertThat(t).contains(plano(FormatacaoFiscal.dinheiro(new BigDecimal("11500.00"), "CVE")));
        assertThat(t).contains("- " + plano(FormatacaoFiscal.dinheiro(new BigDecimal("2000.00"), "CVE")));
        assertThat(t).contains("Processado por computador.");
        assertThat(t).contains("Ambiente de teste — sem validade fiscal");
        assertThat(t).doesNotContain("Documento de origem");
    }

    @Test
    void dinheiroUsaEspacoEstreitoInseparavel() throws IOException {
        String t = texto(renderer.renderizar(frNormal()));
        assertThat(t).contains("11" + NNBSP + "500,00 CVE");
    }

    @Test
    void frComRetencaoTerminaNoValorRecebido() throws IOException {
        String t = plano(texto(renderer.renderizar(frNormal())));
        String total = plano(FormatacaoFiscal.dinheiro(new BigDecimal("11500.00"), "CVE"));
        String retencao = "- " + plano(FormatacaoFiscal.dinheiro(new BigDecimal("2000.00"), "CVE"));
        String liquido = plano(FormatacaoFiscal.dinheiro(new BigDecimal("9500.00"), "CVE"));
        assertThat(t).contains("Total do documento " + total, retencao, "Valor recebido " + liquido);
        assertThat(t.indexOf("Total do documento")).isLessThan(t.indexOf("Valor recebido"));
        assertThat(t.indexOf(retencao)).isLessThan(t.indexOf("Valor recebido"));
        assertThat(t.indexOf("Valor recebido")).isLessThan(t.indexOf("Processado por computador."));
        assertThat(t).doesNotContain("Valor líquido a crédito");
    }

    @Test
    void semRetencaoNaoHaLinhaDeValorLiquido() throws IOException {
        assertThat(plano(texto(renderer.renderizar(frIsenta())))).doesNotContain("Valor recebido");
        assertThat(plano(texto(renderer.renderizar(nc())))).doesNotContain("Valor líquido a crédito", "Valor recebido");
    }

    @Test
    void ncComRetencaoTerminaNoValorLiquidoACredito() throws IOException {
        String t = plano(texto(renderer.renderizar(ncComRetencao())));
        String total = plano(FormatacaoFiscal.dinheiro(new BigDecimal("1150.00"), "CVE"));
        String liquido = plano(FormatacaoFiscal.dinheiro(new BigDecimal("950.00"), "CVE"));
        assertThat(t).contains("Total a crédito " + total,
                "- " + plano(FormatacaoFiscal.dinheiro(new BigDecimal("200.00"), "CVE")),
                "Valor líquido a crédito " + liquido);
        assertThat(t.indexOf("Total a crédito")).isLessThan(t.indexOf("Valor líquido a crédito"));
        assertThat(t).doesNotContain("Valor recebido");
    }

    @Test
    void frIsentaMostraIsentoEMotivoSemLinhaDeTaxaIva() throws IOException {
        String t = plano(texto(renderer.renderizar(frIsenta())));
        assertThat(t).contains("Isento", "IVA: isento", "Motivo de isenção: 02 — Isento ao abrigo do artigo 9.º.");
        assertThat(t).doesNotContain("IVA (");
        assertThat(t).doesNotContain("Retenção na fonte");
    }

    @Test
    void ncMostraOrigemMotivoETotalACredito() throws IOException {
        String t = plano(texto(renderer.renderizar(nc())));
        assertThat(t).contains("Nota de Crédito", "NC SIM-NC-2026/000007", "Documento de origem",
                "Corrige a Fatura-Recibo FR SIM-FR-2026/000123", "de 07/03/2026", "Motivo: Correção de valor",
                "Descrição: Valor faturado em excesso no mandato de março", "Total a crédito");
        assertThat(t).contains(plano(FormatacaoFiscal.dinheiro(new BigDecimal("1150.00"), "CVE")));
        assertThat(t).doesNotContain("Total do documento");
    }

    @Test
    void marcaDeSimulacaoERodapeEmTodasAsPaginas() throws IOException {
        List<DadosPdfDocumentoFiscal.Linha> linhas = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            linhas.add(linha("Linha de teste número " + i, "100.00", "15.0000", "15.00", null, "115.00"));
        }
        List<String> paginas = textoPorPagina(renderer.renderizar(fr("Cliente com muitas linhas", linhas)));

        assertThat(paginas).hasSizeGreaterThanOrEqualTo(2);
        for (int p = 0; p < paginas.size(); p++) {
            String t = plano(paginas.get(p));
            assertThat(t).as("página " + (p + 1)).contains(MARCA);
            assertThat(t).as("página " + (p + 1)).contains("Página " + (p + 1) + " de " + paginas.size());
            assertThat(t).as("página " + (p + 1))
                    .contains("Fatura-Recibo FR SIM-FR-2026/000123 · Documento simulado, sem validade fiscal");
            assertThat(t).as("página " + (p + 1)).contains("Descrição");
        }
    }

    @Test
    void soFontesDejaVuEmbebidas() throws IOException {
        byte[] pdf = renderer.renderizar(nc());
        int fontes = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                for (COSName nome : res.getFontNames()) {
                    PDFont f = res.getFont(nome);
                    fontes++;
                    assertThat(f.isEmbedded()).as(f.getName()).isTrue();
                    assertThat(f.getName()).contains("DejaVu");
                }
            }
        }
        assertThat(fontes).isPositive();
    }

    @Test
    void metadadosTituloEProdutor() throws IOException {
        try (PDDocument doc = Loader.loadPDF(renderer.renderizar(frNormal()))) {
            assertThat(doc.getDocumentInformation().getTitle())
                    .isEqualTo("Fatura-Recibo FR SIM-FR-2026/000123 — Simulação sem validade fiscal");
            assertThat(doc.getDocumentInformation().getProducer()).isEqualTo("LexCV");
        }
    }

    @Test
    void htmlNaFotografiaEApenasTextoENadaExternoEPedido() throws IOException {
        String malicioso = "<img src=\"http://127.0.0.1:9/x.png\"/> & Filhos";
        DadosPdfDocumentoFiscal d = fr(malicioso, List.of(linha(
                "<link rel=\"stylesheet\" href=\"file:///etc/hostname\"/>", "100.00", "15.0000", "15.00", null,
                "115.00")));
        String t = plano(texto(renderer.renderizar(d)));

        assertThat(t).contains(malicioso);
        assertThat(t).contains("<link rel=\"stylesheet\" href=\"file:///etc/hostname\"/>");
        assertThat(pedidos).isNotEmpty();
        for (String uri : pedidos) {
            assertThat(uri).doesNotContain("127.0.0.1", "file:", "http");
        }
        assertThat(pedidos).allSatisfy(uri ->
                assertThat(ClasspathPdfResolver.resolver(uri, ClasspathPdfResolver.PREFIXO)).as(uri).isNotNull());
    }

    @Test
    void semPalavrasDeAutorizacao() throws IOException {
        for (DadosPdfDocumentoFiscal d : List.of(frNormal(), frIsenta(), nc())) {
            String t = texto(renderer.renderizar(d)).toLowerCase(java.util.Locale.ROOT);
            assertThat(t).doesNotContain("autorizado", "aprovado", "validado");
        }
    }

    @Test
    void naoUsaOMapeadorDeFontesDoSistema() {
        assertThat(FontMappers.instance().getClass().getName())
                .isEqualTo(PdfDocumentoFiscalRenderer.class.getName() + "$MapeadorFontesClasspath");
    }

    @Test
    void cssEmFaltaFalhaNoArranque() {
        assertThatThrownBy(() -> new PdfDocumentoFiscalRenderer("inexistente.css", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(PdfDocumentoFiscalRenderer.MENSAGEM_ARRANQUE);
    }

    @Test
    void construtorPublicoCarregaOsRecursos() throws IOException {
        byte[] pdf = new PdfDocumentoFiscalRenderer().renderizar(frIsenta());
        assertThat(plano(texto(pdf))).contains(MARCA);
    }
}
