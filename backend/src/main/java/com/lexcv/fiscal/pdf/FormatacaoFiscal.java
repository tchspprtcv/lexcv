package com.lexcv.fiscal.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Phase 137 (ENTR-01, ENTR-03): fonte única da formatação de dinheiro e datas mostrados ao cliente
 * -- usada pelo PDF ({@link ModeloPdfDocumentoFiscal}) e pelo email ({@code ComposicaoEmailFiscal},
 * 137-14). O CSV do contabilista ({@code CsvFiscal}) tem um contrato diferente (sem milhares, sem
 * moeda) e não depende desta classe.
 *
 * <p>Formato pt-CV (137-UI-SPEC): 2 casas, vírgula decimal, milhares separados por U+202F (espaço
 * estreito inseparável), sufixo {@code " CVE"}; datas {@code dd/MM/yyyy}. Construído sem
 * {@code DecimalFormat} nem {@code Locale} por omissão, para dar o mesmo resultado em qualquer JVM.
 */
public final class FormatacaoFiscal {

    private static final char SEPARADOR_MILHARES = ' ';
    private static final String MOEDA_OMISSAO = "CVE";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FormatacaoFiscal() {
    }

    /**
     * Ex.: {@code 12345} -> {@code "12 345,00 CVE"} (com U+202F). Negativos mantêm o {@code "-"}.
     *
     * @param moeda código da moeda; {@code "CVE"} quando nulo ou em branco
     * @throws IllegalArgumentException quando {@code valor} é nulo
     */
    public static String dinheiro(BigDecimal valor, String moeda) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor monetário em falta");
        }
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_EVEN);
        String simples = arredondado.abs().toPlainString();
        int ponto = simples.indexOf('.');
        String inteiro = simples.substring(0, ponto);
        String decimal = simples.substring(ponto + 1);

        StringBuilder sb = new StringBuilder();
        if (arredondado.signum() < 0) {
            sb.append('-');
        }
        int primeiro = inteiro.length() % 3 == 0 ? 3 : inteiro.length() % 3;
        sb.append(inteiro, 0, primeiro);
        for (int i = primeiro; i < inteiro.length(); i += 3) {
            sb.append(SEPARADOR_MILHARES).append(inteiro, i, i + 3);
        }
        sb.append(',').append(decimal).append(' ')
                .append(moeda == null || moeda.isBlank() ? MOEDA_OMISSAO : moeda.strip());
        return sb.toString();
    }

    /**
     * Ex.: {@code 2026-03-07} -> {@code "07/03/2026"}.
     *
     * @throws IllegalArgumentException quando {@code data} é nula
     */
    public static String data(LocalDate data) {
        if (data == null) {
            throw new IllegalArgumentException("Data em falta");
        }
        return DATA.format(data);
    }
}
