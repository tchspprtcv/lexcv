package com.lexcv.services.fiscal;

import com.lexcv.fiscal.email.MensagemEmailFiscal;
import com.lexcv.fiscal.pdf.FormatacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.TipoDocumentoFiscal;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 137 (ENTR-03, DFE-06): compõe o email ao cliente a partir do snapshot guardado do documento
 * (137-UI-SPEC, superfície 6b).
 *
 * <ul>
 *   <li>Assunto e primeiro bloco dos dois corpos com a marca "SIMULAÇÃO — SEM VALIDADE FISCAL"
 *       (T-137-56); nunca palavras de autorização.</li>
 *   <li>Texto simples e HTML gerados da MESMA lista ordenada de parágrafos. No HTML todo o texto passa
 *       por um único escape ({@link #escapar}); só estilos inline, sem ligações, imagens nem conteúdo
 *       remoto (T-137-55).</li>
 *   <li>Dinheiro e datas por {@link FormatacaoFiscal}, a mesma formatação do PDF.</li>
 *   <li>Anexos: o PDF recebido e o XML guardado (bytes UTF-8), com os nomes de
 *       {@link NomesFicheiroFiscal}.</li>
 *   <li>Reply-To: o {@code emailContacto} do escritório só quando válido
 *       ({@link RegrasEntregaEmail#emailValido}).</li>
 * </ul>
 */
@Component
public class ComposicaoEmailFiscal {

    static final String MARCA = "SIMULAÇÃO — SEM VALIDADE FISCAL";
    static final String MSG_SEM_XML = "O documento fiscal ainda não tem o XML guardado";

    private static final String AVISO = "Este documento foi emitido em modo de simulação e não tem validade fiscal. "
            + "Não foi comunicado à administração fiscal (DNRE).";
    static final String ROTULO_LIQUIDO_FR = "Valor recebido";
    static final String ROTULO_LIQUIDO_NC = "Valor líquido a crédito";
    private static final String TIPO_PDF = "application/pdf";
    private static final String TIPO_XML = "application/xml";

    /**
     * @param destinatario endereço já validado pelo processador
     * @param pdf          bytes do PDF armazenado do documento
     * @throws IllegalStateException quando o snapshot não tem a linha XML (o chamador garante-a)
     */
    public MensagemEmailFiscal compor(SnapshotEntregaEmail snapshot, String destinatario, byte[] pdf) {
        DocumentoFiscal d = snapshot.documento();
        DocumentoFiscalXml xml = snapshot.xml().orElseThrow(() -> new IllegalStateException(MSG_SEM_XML));
        Optional<String> replyTo = snapshot.replyTo().flatMap(RegrasEntregaEmail::emailValido);

        String tipo = d.getTipo().rotulo();
        String numero = d.getNumeroFormatado();
        String firma = d.getEmitenteFirma();
        String nomePdf = NomesFicheiroFiscal.pdf(numero);
        String nomeXml = NomesFicheiroFiscal.xml(numero);

        String assunto = "[" + MARCA + "] " + tipo + " " + numero + " — " + firma;

        List<List<String>> blocos = new ArrayList<>();
        blocos.add(List.of(MARCA, AVISO));
        blocos.add(List.of("Exmo(a). Senhor(a) " + d.getAdquirenteNome() + ","));
        List<String> envio = new ArrayList<>();
        envio.add("Enviamos em anexo a " + tipo + " " + numero + ", emitida em "
                + FormatacaoFiscal.data(d.getDataEmissao()) + ", no valor total de "
                + FormatacaoFiscal.dinheiro(d.getTotalDocumento(), d.getMoeda()) + ".");
        if (d.getTotalRetencao() != null && d.getTotalRetencao().signum() > 0) {
            // Mesma regra do PDF (137-06): com retenção, o total não é o que muda de mãos; mostra-se o
            // valor líquido guardado. Sem retenção não há linha extra.
            envio.add((d.getTipo() == TipoDocumentoFiscal.NC ? ROTULO_LIQUIDO_NC : ROTULO_LIQUIDO_FR) + ": "
                    + FormatacaoFiscal.dinheiro(d.getValorLiquido(), d.getMoeda()) + ".");
        }
        if (d.getTipo() == TipoDocumentoFiscal.NC && snapshot.numeroOrigem().isPresent()) {
            envio.add("Esta nota de crédito corrige a Fatura-Recibo " + snapshot.numeroOrigem().get() + ".");
        }
        blocos.add(envio);
        blocos.add(List.of("Anexos: " + nomePdf + " (documento) e " + nomeXml + " (formato eletrónico)."));
        blocos.add(List.of("Com os melhores cumprimentos,", firma, "NIF " + d.getEmitenteNif() + " · " + d.getEmitenteMorada()));
        blocos.add(List.of(replyTo.isPresent()
                ? "Mensagem enviada automaticamente. Para qualquer questão, responda a este email ou contacte " + firma + "."
                : "Mensagem enviada automaticamente. Para qualquer questão, contacte " + firma + "."));

        List<MensagemEmailFiscal.Anexo> anexos = List.of(
                new MensagemEmailFiscal.Anexo(nomePdf, TIPO_PDF, pdf),
                new MensagemEmailFiscal.Anexo(nomeXml, TIPO_XML, xml.getXml().getBytes(StandardCharsets.UTF_8)));

        return new MensagemEmailFiscal(destinatario, replyTo, assunto, textoSimples(blocos), html(blocos), anexos);
    }

    private static String textoSimples(List<List<String>> blocos) {
        StringBuilder sb = new StringBuilder();
        for (List<String> bloco : blocos) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(String.join("\n", bloco));
        }
        return sb.toString();
    }

    private static String html(List<List<String>> blocos) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"pt\"><head><meta charset=\"UTF-8\"></head>")
                .append("<body style=\"font-family: Arial, sans-serif; font-size: 14px; color: #111827;\">");
        for (int i = 0; i < blocos.size(); i++) {
            List<String> bloco = blocos.get(i);
            if (i == 0) {
                sb.append("<div style=\"background: #e5e7eb; border: 1px solid #6b7280; padding: 8px 12px; "
                        + "margin-bottom: 16px;\"><p style=\"margin: 0; font-weight: bold;\">")
                        .append(escapar(bloco.get(0))).append("</p>");
                for (String linha : bloco.subList(1, bloco.size())) {
                    sb.append("<p style=\"margin: 4px 0 0 0;\">").append(escapar(linha)).append("</p>");
                }
                sb.append("</div>");
            } else {
                sb.append("<p style=\"margin: 0 0 12px 0;\">");
                for (int j = 0; j < bloco.size(); j++) {
                    if (j > 0) {
                        sb.append("<br>");
                    }
                    sb.append(escapar(bloco.get(j)));
                }
                sb.append("</p>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Único ponto de escape HTML: todo o texto (fixo ou do snapshot) passa por aqui. */
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
