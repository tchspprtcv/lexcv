package com.lexcv.services.fiscal;

import com.lexcv.fiscal.email.MensagemEmailFiscal;
import com.lexcv.fiscal.pdf.FormatacaoFiscal;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 137-14 (ENTR-03, DFE-06): composição do email ao cliente a partir do snapshot guardado
 * (137-UI-SPEC, superfície 6b).
 */
class ComposicaoEmailFiscalTest {

    private static final String MARCA = "SIMULAÇÃO — SEM VALIDADE FISCAL";
    private static final String DESTINATARIO = "cliente@exemplo.cv";
    private static final LocalDate DATA = LocalDate.of(2026, 3, 7);
    private static final BigDecimal TOTAL = new BigDecimal("12345.00");
    private static final String XML = "<Dfe numero=\"ção\"/>";
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', 1, 2, 3};

    private final ComposicaoEmailFiscal composicao = new ComposicaoEmailFiscal();

    // ---- fixtures ----

    private static DocumentoFiscal documento(TipoDocumentoFiscal tipo, String numero, String adquirente) {
        return documento(tipo, numero, adquirente, BigDecimal.ZERO, TOTAL);
    }

    private static DocumentoFiscal documento(TipoDocumentoFiscal tipo, String numero, String adquirente,
                                             BigDecimal retencao, BigDecimal liquido) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).tipo(tipo).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo("2026A").ano(2026).numero(123L).numeroFormatado(numero)
                .dataEmissao(DATA).emitidoEm(Instant.parse("2026-03-07T10:00:00Z"))
                .emitenteNif("512345679").emitenteFirma("Silva & Associados").emitenteMorada("Avenida Amílcar Cabral, 1")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome(adquirente).adquirenteMorada("Rua 2")
                .meioPagamentoCodigo("10").moeda("CVE")
                .documentoOrigemId(tipo == TipoDocumentoFiscal.NC ? UUID.randomUUID() : null)
                .motivoCodigo(tipo == TipoDocumentoFiscal.NC ? MotivoNotaCredito.CORRECAO_VALOR : null)
                .totalBase(new BigDecimal("10734.78")).totalIva(new BigDecimal("1610.22"))
                .totalRetencao(retencao).totalDocumento(TOTAL).valorLiquido(liquido)
                .build();
    }

    private static DocumentoFiscalXml linhaXml() {
        DocumentoFiscalXml row = mock(DocumentoFiscalXml.class);
        when(row.getXml()).thenReturn(XML);
        return row;
    }

    private static SnapshotEntregaEmail fr(String adquirente, Optional<String> replyTo) {
        return new SnapshotEntregaEmail(documento(TipoDocumentoFiscal.FR, "FR 2026A/000123", adquirente),
                Optional.of(linhaXml()), Optional.empty(), replyTo);
    }

    private static SnapshotEntregaEmail nc() {
        return new SnapshotEntregaEmail(documento(TipoDocumentoFiscal.NC, "NC 2026A/000007", "Cliente Teste"),
                Optional.of(linhaXml()), Optional.of("FR 2026A/000123"), Optional.of("geral@silva.cv"));
    }

    // ---- assunto e corpo ----

    @Test
    void frAssuntoECorpoSimplesSeguemAUiSpec() {
        MensagemEmailFiscal m = composicao.compor(fr("Cliente Teste", Optional.of("geral@silva.cv")), DESTINATARIO, PDF);

        assertThat(m.assunto()).isEqualTo("[" + MARCA + "] Fatura-Recibo FR 2026A/000123 — Silva & Associados");
        assertThat(m.destinatario()).isEqualTo(DESTINATARIO);
        String texto = m.textoSimples();
        assertThat(texto).startsWith(MARCA + "\n");
        assertThat(texto).contains("Este documento foi emitido em modo de simulação e não tem validade fiscal. "
                + "Não foi comunicado à administração fiscal (DNRE).");
        assertThat(texto).contains("Exmo(a). Senhor(a) Cliente Teste,");
        assertThat(texto).contains("Enviamos em anexo a Fatura-Recibo FR 2026A/000123, emitida em 07/03/2026, "
                + "no valor total de 12\u202F345,00 CVE.");
        assertThat(texto).contains("Anexos: FR-2026A-000123.pdf (documento) e FR-2026A-000123.xml (formato eletrónico).");
        assertThat(texto).contains("Com os melhores cumprimentos,\nSilva & Associados\nNIF 512345679 · Avenida Amílcar Cabral, 1");
        assertThat(texto).doesNotContain("Esta nota de crédito corrige");
    }

    @Test
    void ncTemALinhaDaFaturaReciboDeOrigem() {
        MensagemEmailFiscal m = composicao.compor(nc(), DESTINATARIO, PDF);

        assertThat(m.assunto()).isEqualTo("[" + MARCA + "] Nota de Crédito NC 2026A/000007 — Silva & Associados");
        assertThat(m.textoSimples()).contains("Enviamos em anexo a Nota de Crédito NC 2026A/000007, emitida em 07/03/2026");
        assertThat(m.textoSimples()).contains("Esta nota de crédito corrige a Fatura-Recibo FR 2026A/000123.");
        assertThat(m.html()).contains("Esta nota de crédito corrige a Fatura-Recibo FR 2026A/000123.");
    }

    @Test
    void replyToValidoUsaAVarianteResponda() {
        MensagemEmailFiscal m = composicao.compor(fr("Cliente Teste", Optional.of(" geral@silva.cv ")), DESTINATARIO, PDF);

        assertThat(m.replyTo()).contains("geral@silva.cv");
        assertThat(m.textoSimples()).endsWith("Mensagem enviada automaticamente. Para qualquer questão, "
                + "responda a este email ou contacte Silva & Associados.");
    }

    @Test
    void replyToInvalidoOuAusenteUsaAOutraVariante() {
        for (Optional<String> replyTo : java.util.List.of(Optional.<String>empty(), Optional.of("   "),
                Optional.of("nao-e-email"), Optional.of("a@b.cv, c@d.cv"))) {
            MensagemEmailFiscal m = composicao.compor(fr("Cliente Teste", replyTo), DESTINATARIO, PDF);

            assertThat(m.replyTo()).isEmpty();
            assertThat(m.textoSimples()).endsWith("Mensagem enviada automaticamente. Para qualquer questão, "
                    + "contacte Silva & Associados.");
            assertThat(m.textoSimples()).doesNotContain("responda a este email");
        }
    }

    // ---- anexos ----

    @Test
    void exatamenteDoisAnexosComOsBytesGuardados() {
        MensagemEmailFiscal m = composicao.compor(fr("Cliente Teste", Optional.empty()), DESTINATARIO, PDF);

        assertThat(m.anexos()).hasSize(2);
        MensagemEmailFiscal.Anexo pdf = m.anexos().get(0);
        MensagemEmailFiscal.Anexo xml = m.anexos().get(1);
        assertThat(pdf.nome()).isEqualTo(NomesFicheiroFiscal.pdf("FR 2026A/000123")).isEqualTo("FR-2026A-000123.pdf");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(pdf.conteudo()).isEqualTo(PDF);
        assertThat(xml.nome()).isEqualTo(NomesFicheiroFiscal.xml("FR 2026A/000123")).isEqualTo("FR-2026A-000123.xml");
        assertThat(xml.contentType()).isEqualTo("application/xml");
        assertThat(xml.conteudo()).isEqualTo(XML.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void semXmlGuardadoRecusaComMensagemFixa() {
        SnapshotEntregaEmail semXml = new SnapshotEntregaEmail(
                documento(TipoDocumentoFiscal.FR, "FR 2026A/000123", "Cliente Teste"),
                Optional.empty(), Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> composicao.compor(semXml, DESTINATARIO, PDF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(ComposicaoEmailFiscal.MSG_SEM_XML);
    }

    // ---- HTML ----

    @Test
    void htmlEscapaOTextoDoSnapshotETextoSimplesNao() {
        MensagemEmailFiscal m = composicao.compor(fr("<b>X</b> & Y", Optional.of("geral@silva.cv")), DESTINATARIO, PDF);

        assertThat(m.html()).contains("&lt;b&gt;X&lt;/b&gt; &amp; Y");
        assertThat(m.html()).doesNotContain("<b>X</b>");
        assertThat(m.html()).contains("Silva &amp; Associados");
        assertThat(m.textoSimples()).contains("Exmo(a). Senhor(a) <b>X</b> & Y,");
    }

    @Test
    void htmlSemLigacoesImagensNemConteudoRemotoEComAMarcaNumaCaixaCinzenta() {
        MensagemEmailFiscal m = composicao.compor(fr("Cliente <a href=x><img src=y>", Optional.of("geral@silva.cv")),
                DESTINATARIO, PDF);
        String html = m.html();

        assertThat(html).doesNotContain("<a ").doesNotContain("<img").doesNotContain("http");
        assertThat(html).contains("#e5e7eb").contains("#6b7280");
        int caixa = html.indexOf("#e5e7eb");
        int marca = html.indexOf(MARCA);
        int saudacao = html.indexOf("Exmo(a).");
        assertThat(caixa).isPositive();
        assertThat(marca).isGreaterThan(caixa).isLessThan(saudacao);
    }

    @Test
    void htmlTemOMesmoTextoQueOCorpoSimples() {
        MensagemEmailFiscal m = composicao.compor(nc(), DESTINATARIO, PDF);

        for (String linha : m.textoSimples().split("\n")) {
            if (!linha.isBlank()) {
                assertThat(m.html()).contains(linha.replace("&", "&amp;"));
            }
        }
    }

    @Test
    void semPalavrasDeAutorizacao() {
        for (SnapshotEntregaEmail s : java.util.List.of(fr("Cliente Teste", Optional.of("geral@silva.cv")), nc())) {
            MensagemEmailFiscal m = composicao.compor(s, DESTINATARIO, PDF);
            for (String corpo : java.util.List.of(m.assunto(), m.textoSimples(), m.html())) {
                assertThat(corpo).doesNotContain("Autorizado").doesNotContain("Aprovado").doesNotContain("Validado");
            }
        }
    }

    // ---- formatação partilhada com o PDF ----

    @Test
    void totalEDataUsamAFormatacaoPartilhadaComOPdf() {
        MensagemEmailFiscal m = composicao.compor(fr("Cliente Teste", Optional.empty()), DESTINATARIO, PDF);
        String total = FormatacaoFiscal.dinheiro(TOTAL, "CVE");
        String data = FormatacaoFiscal.data(DATA);

        assertThat(total).isEqualTo("12\u202F345,00 CVE");
        assertThat(m.textoSimples()).contains("emitida em " + data + ", no valor total de " + total + ".");
        assertThat(m.html()).contains("emitida em " + data + ", no valor total de " + total + ".");
    }

    // ---- valor líquido com retenção (mesma regra do PDF, 137-06 desvio 9) ----

    private static final BigDecimal RETENCAO = new BigDecimal("1610.22");
    private static final BigDecimal LIQUIDO = new BigDecimal("10734.78");

    private static SnapshotEntregaEmail comRetencao(TipoDocumentoFiscal tipo, String numero) {
        DocumentoFiscal d = documento(tipo, numero, "Cliente Teste", RETENCAO, LIQUIDO);
        return new SnapshotEntregaEmail(d, Optional.of(linhaXml()),
                tipo == TipoDocumentoFiscal.NC ? Optional.of("FR 2026A/000123") : Optional.empty(), Optional.empty());
    }

    @Test
    void frComRetencaoMostraOValorRecebidoDepoisDoTotal() {
        MensagemEmailFiscal m = composicao.compor(comRetencao(TipoDocumentoFiscal.FR, "FR 2026A/000123"), DESTINATARIO, PDF);
        String total = FormatacaoFiscal.dinheiro(TOTAL, "CVE");
        String liquido = FormatacaoFiscal.dinheiro(LIQUIDO, "CVE");

        assertThat(m.textoSimples()).contains("no valor total de " + total + ".\nValor recebido: " + liquido + ".");
        assertThat(m.html()).contains("no valor total de " + total + ".<br>Valor recebido: " + liquido + ".");
        assertThat(m.textoSimples()).doesNotContain("Valor líquido a crédito");
    }

    @Test
    void ncComRetencaoMostraOValorLiquidoACredito() {
        MensagemEmailFiscal m = composicao.compor(comRetencao(TipoDocumentoFiscal.NC, "NC 2026A/000007"), DESTINATARIO, PDF);
        String liquido = FormatacaoFiscal.dinheiro(LIQUIDO, "CVE");

        assertThat(m.textoSimples()).contains(".\nValor líquido a crédito: " + liquido + ".\nEsta nota de crédito corrige");
        assertThat(m.html()).contains("Valor líquido a crédito: " + liquido + ".");
        assertThat(m.textoSimples()).doesNotContain("Valor recebido");
    }

    @Test
    void semRetencaoNaoHaLinhaDeValorLiquido() {
        for (SnapshotEntregaEmail s : java.util.List.of(fr("Cliente Teste", Optional.empty()), nc())) {
            MensagemEmailFiscal m = composicao.compor(s, DESTINATARIO, PDF);
            assertThat(m.textoSimples()).doesNotContain("Valor recebido").doesNotContain("Valor líquido a crédito");
            assertThat(m.html()).doesNotContain("Valor recebido").doesNotContain("Valor líquido a crédito");
        }
    }
}
