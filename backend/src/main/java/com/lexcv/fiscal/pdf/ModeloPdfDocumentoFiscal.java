package com.lexcv.fiscal.pdf;

import com.lexcv.models.TipoDocumentoFiscal;

import java.math.BigDecimal;

/**
 * Phase 137 (ENTR-01, DFE-06): monta o XHTML do PDF de uma Fatura-Recibo ou Nota de Crédito, pela
 * ordem e com o texto de 137-UI-SPEC Surface 6a.
 *
 * <p>Regras:
 * <ul>
 *   <li>Todo o texto da fotografia passa por um único {@link #escapar(String)} antes de entrar no
 *       documento (T-137-20); não há outro caminho de inserção.</li>
 *   <li>Dinheiro e datas passam sempre por {@link FormatacaoFiscal} (sem cópias privadas).</li>
 *   <li>A única referência externa é a folha de estilo {@link ClasspathPdfResolver#CSS}; as fontes
 *       são registadas pelo renderer a partir dos bytes do classpath.</li>
 *   <li>Enquanto {@code simulado}, a banda do cabeçalho e a marca d'água levam
 *       {@link #MARCA_SIMULACAO} em todas as páginas (T-137-21). Sem QR, sem código de barras,
 *       sem logótipo e sem qualquer palavra que sugira aceitação pela administração.</li>
 * </ul>
 */
public final class ModeloPdfDocumentoFiscal {

    /** Texto exato da banda e da marca d'água (U+2014). */
    public static final String MARCA_SIMULACAO = "SIMULAÇÃO — SEM VALIDADE FISCAL";

    static final String BANDA_LINHA_2 = "Documento emitido em ambiente de teste e comunicado a um serviço de "
            + "simulação, não à administração fiscal (DNRE).";
    static final String NOTA_IUD = "Ambiente de teste — sem validade fiscal";
    static final String NOTA_FINAL = "Processado por computador. Documento simulado para testes, sem validade "
            + "fiscal; não substitui o documento emitido no software de faturação homologado.";
    static final String CONSUMIDOR_FINAL = "Consumidor final";

    private ModeloPdfDocumentoFiscal() {
    }

    /** Título dos metadados do PDF: "{tipo} {número} — Simulação sem validade fiscal". */
    public static String titulo(DadosPdfDocumentoFiscal d) {
        String base = d.tipo().rotulo() + " " + d.numeroFormatado();
        return d.simulado() ? base + " — Simulação sem validade fiscal" : base;
    }

    public static String xhtml(DadosPdfDocumentoFiscal d) {
        boolean nc = d.tipo() == TipoDocumentoFiscal.NC;
        boolean isento = d.emitente().isento();
        String moeda = d.moeda();
        StringBuilder h = new StringBuilder(8192);

        h.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"pt-CV\" xml:lang=\"pt-CV\">");
        h.append("<head><meta charset=\"UTF-8\"/>");
        h.append("<title>").append(escapar(titulo(d))).append("</title>");
        h.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(ClasspathPdfResolver.CSS).append("\"/>");
        h.append("</head><body>");

        // Elementos repetidos em todas as páginas (banda, rodapé e marca d'água).
        if (d.simulado()) {
            h.append("<div id=\"banda\"><div class=\"banda-l1\">").append(escapar(MARCA_SIMULACAO))
                    .append("</div><div class=\"banda-l2\">").append(escapar(BANDA_LINHA_2)).append("</div></div>");
        }
        h.append("<div id=\"rodape\">").append(escapar(d.tipo().rotulo() + " " + d.numeroFormatado()));
        if (d.simulado()) {
            h.append(" · Documento simulado, sem validade fiscal");
        }
        h.append("</div>");
        if (d.simulado()) {
            h.append("<div class=\"marca-agua\">").append(escapar(MARCA_SIMULACAO)).append("</div>");
        }

        // 1. Emitente + caixa do documento.
        DadosPdfDocumentoFiscal.Emitente e = d.emitente();
        h.append("<table class=\"topo\"><tr><td class=\"emitente\">");
        h.append("<div class=\"rotulo\">Emitente</div>");
        h.append("<div class=\"firma\">").append(escapar(e.firma())).append("</div>");
        h.append("<div>NIF: <span class=\"mono\">").append(escapar(e.nif())).append("</span></div>");
        paragrafoSePresente(h, e.morada());
        paragrafoSePresente(h, e.localidade());
        if (e.regimeIvaRotulo() != null) {
            h.append("<div>Regime de IVA: ").append(escapar(e.regimeIvaRotulo())).append("</div>");
        }
        h.append("</td><td class=\"caixa-documento\">");
        h.append("<div class=\"titulo\">").append(escapar(d.tipo().rotulo())).append("</div>");
        if (d.serieCodigo() != null) {
            h.append("<div>Série: <span class=\"mono\">").append(escapar(d.serieCodigo())).append("</span></div>");
        }
        h.append("<div>Número: <span class=\"mono\">").append(escapar(d.numeroFormatado())).append("</span></div>");
        h.append("<div>Data de emissão: ").append(escapar(FormatacaoFiscal.data(d.dataEmissao()))).append("</div>");
        h.append("<div class=\"rotulo-iud\">IUD</div><div class=\"mono iud\">").append(escapar(d.iud())).append("</div>");
        if (d.simulado()) {
            h.append("<div class=\"pequeno\">").append(escapar(NOTA_IUD)).append("</div>");
        }
        h.append("</td></tr></table>");

        // 2. Adquirente.
        DadosPdfDocumentoFiscal.Adquirente a = d.adquirente();
        h.append("<div class=\"bloco\"><div class=\"seccao\">Adquirente</div>");
        paragrafoSePresente(h, a.nome());
        if (a.nif() == null || a.nif().isBlank()) {
            h.append("<div>").append(escapar(CONSUMIDOR_FINAL)).append("</div>");
        } else {
            h.append("<div>NIF: <span class=\"mono\">").append(escapar(a.nif())).append("</span></div>");
        }
        paragrafoSePresente(h, a.morada());
        paragrafoSePresente(h, a.localidade());
        h.append("</div>");

        // 3. NC: documento de origem.
        if (nc) {
            h.append("<div class=\"bloco\"><div class=\"seccao\">Documento de origem</div>");
            if (d.origem() != null) {
                h.append("<div>Corrige a ").append(escapar(TipoDocumentoFiscal.FR.rotulo())).append(' ')
                        .append("<span class=\"mono\">").append(escapar(d.origem().numeroFormatado())).append("</span>");
                if (d.origem().dataEmissao() != null) {
                    h.append(" de ").append(escapar(FormatacaoFiscal.data(d.origem().dataEmissao())));
                }
                h.append("</div>");
            }
            if (d.motivoRotulo() != null) {
                h.append("<div>Motivo: ").append(escapar(d.motivoRotulo())).append("</div>");
            }
            if (d.motivoTexto() != null && !d.motivoTexto().isBlank()) {
                h.append("<div>Descrição: ").append(escapar(d.motivoTexto())).append("</div>");
            }
            h.append("</div>");
        }

        // 4. Linhas.
        h.append("<table class=\"linhas\"><thead><tr>")
                .append("<th class=\"descricao\">Descrição</th><th class=\"num\">Base</th>")
                .append("<th class=\"num\">Taxa IVA</th><th class=\"num\">IVA</th><th class=\"num\">Total</th>")
                .append("</tr></thead><tbody>");
        for (DadosPdfDocumentoFiscal.Linha l : d.linhas()) {
            boolean linhaIsenta = l.motivoIsencaoCodigo() != null && !l.motivoIsencaoCodigo().isBlank();
            h.append("<tr><td class=\"descricao\">").append(escapar(l.descricao())).append("</td>");
            h.append("<td class=\"num\">").append(escapar(dinheiroOuVazio(l.valorBase(), moeda))).append("</td>");
            if (linhaIsenta) {
                h.append("<td class=\"num\">Isento</td><td class=\"num\">")
                        .append(escapar(l.motivoIsencaoCodigo())).append("</td>");
            } else {
                h.append("<td class=\"num\">").append(escapar(percentagem(l.taxaIva()))).append("</td>");
                h.append("<td class=\"num\">").append(escapar(dinheiroOuVazio(l.valorIva(), moeda))).append("</td>");
            }
            h.append("<td class=\"num\">").append(escapar(dinheiroOuVazio(l.totalLinha(), moeda))).append("</td></tr>");
        }
        h.append("</tbody></table>");

        // 5. Totais.
        DadosPdfDocumentoFiscal.Totais t = d.totais();
        h.append("<table class=\"totais\">");
        linhaTotal(h, "Base tributável", dinheiroOuVazio(t.base(), moeda), false);
        if (isento) {
            linhaTotal(h, "IVA: isento", "", false);
        } else {
            linhaTotal(h, "IVA (" + percentagem(t.taxaIva()) + ")", dinheiroOuVazio(t.iva(), moeda), false);
        }
        if (t.retencao() != null && t.retencao().signum() > 0) {
            linhaTotal(h, "Retenção na fonte (" + percentagem(t.taxaRetencao()) + ")",
                    "- " + FormatacaoFiscal.dinheiro(t.retencao(), moeda), false);
        }
        linhaTotal(h, nc ? "Total a crédito" : "Total do documento", dinheiroOuVazio(t.totalDocumento(), moeda), true);
        h.append("</table>");

        // 6. Isenção.
        if (isento && e.motivoIsencaoCodigo() != null) {
            h.append("<div class=\"pequeno bloco\">Motivo de isenção: ").append(escapar(e.motivoIsencaoCodigo()));
            if (e.motivoIsencaoDescricao() != null && !e.motivoIsencaoDescricao().isBlank()) {
                h.append(" — ").append(escapar(e.motivoIsencaoDescricao()));
            }
            h.append(".</div>");
        }

        // 7. Nota final.
        h.append("<div class=\"pequeno bloco nota-final\">").append(escapar(NOTA_FINAL)).append("</div>");

        h.append("</body></html>");
        return h.toString();
    }

    private static void paragrafoSePresente(StringBuilder h, String texto) {
        if (texto != null && !texto.isBlank()) {
            h.append("<div>").append(escapar(texto)).append("</div>");
        }
    }

    private static void linhaTotal(StringBuilder h, String rotulo, String valor, boolean finalLinha) {
        h.append(finalLinha ? "<tr class=\"total-final\">" : "<tr>")
                .append("<td>").append(escapar(rotulo)).append("</td>")
                .append("<td class=\"num\">").append(escapar(valor)).append("</td></tr>");
    }

    private static String dinheiroOuVazio(BigDecimal valor, String moeda) {
        return valor == null ? "" : FormatacaoFiscal.dinheiro(valor, moeda);
    }

    /** Taxa em percentagem sem zeros à direita: {@code 15.0000} -> {@code "15%"}, {@code 7.5} -> {@code "7,5%"}. */
    static String percentagem(BigDecimal taxa) {
        if (taxa == null) {
            return "";
        }
        return taxa.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    /**
     * Único ponto de inserção de texto no XHTML: escapa {@code & < > " '} e retira os caracteres
     * de controlo que o XML não admite (o documento continua bem formado com qualquer fotografia).
     */
    static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        if (c != '￾' && c != '￿') {
                            sb.append(c);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}
