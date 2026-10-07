package com.lexcv.services.fiscal;

import java.time.YearMonth;
import java.util.Locale;

/**
 * Phase 137 (ENTR-02, 137-UI-SPEC): nomes dos ficheiros fiscais descarregados, derivados da
 * série/número formatado. Qualquer carácter fora de {@code [A-Za-z0-9._-]} (espaço, "/", acentos,
 * aspas, CR/LF) passa a "-", pelo que o nome é seguro dentro de um cabeçalho
 * {@code Content-Disposition}. Ex.: {@code "FR 2026A/000123"} -> {@code FR-2026A-000123.pdf}.
 */
public final class NomesFicheiroFiscal {

    /** Nome usado quando o documento não tem número formatado (não acontece num documento emitido). */
    static final String BASE_POR_OMISSAO = "documento-fiscal";

    private NomesFicheiroFiscal() {
    }

    /** Série/número com todos os caracteres fora de {@code [A-Za-z0-9._-]} substituídos por "-". */
    public static String base(String numeroFormatado) {
        if (numeroFormatado == null || numeroFormatado.isBlank()) {
            return BASE_POR_OMISSAO;
        }
        StringBuilder sb = new StringBuilder(numeroFormatado.length());
        String texto = numeroFormatado.strip();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            boolean permitido = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            sb.append(permitido ? c : '-');
        }
        return sb.toString();
    }

    public static String pdf(String numeroFormatado) {
        return base(numeroFormatado) + ".pdf";
    }

    public static String xml(String numeroFormatado) {
        return base(numeroFormatado) + ".xml";
    }

    /** {@code documentos-fiscais-simulacao-AAAA-MM.csv}. */
    public static String csv(YearMonth mes) {
        return String.format(Locale.ROOT, "documentos-fiscais-simulacao-%04d-%02d.csv", mes.getYear(),
                mes.getMonthValue());
    }
}
