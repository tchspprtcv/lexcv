package com.lexcv.fiscal.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Phase 137 (RELF-01): formatação das células do CSV para o contabilista, no formato que o Excel
 * em português abre diretamente -- BOM UTF-8, separador {@code ;}, fim de linha CRLF, vírgula
 * decimal sem separador de milhares e datas {@code dd/MM/yyyy}. Não depende do {@code Locale}
 * por omissão da JVM.
 *
 * <p>Porte de {@code web/src/lib/csv.ts} ({@code guardCsvFormula} + {@code escapeCsvValue}). A guarda
 * de fórmulas (OWASP, T-137-16) é por campo, nunca global (lição da v2.13 Phase 104): quem chama
 * aplica {@link #textoLivre} só ao Cliente e ao Motivo de isenção. NIF, IUD, série, número, datas e
 * valores passam por {@link #estruturado}, {@link #valor} e {@link #data} -- os valores das Notas de
 * Crédito são negativos e não podem ganhar um apóstrofo (T-137-17).
 */
public final class CsvFiscal {

    public static final String BOM = "﻿";
    public static final String SEPARADOR = ";";
    public static final String FIM_LINHA = "\r\n";

    private static final String GATILHOS_FORMULA = "=+-@\t\r";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CsvFiscal() {
    }

    /** Texto livre, influenciável pelo utilizador: guarda de fórmula e depois escape. */
    public static String textoLivre(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String guardado = GATILHOS_FORMULA.indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        return escapar(guardado);
    }

    /** Valor estruturado (NIF, IUD, série, número, telefone): nunca guardado, só escapado. */
    public static String estruturado(String valor) {
        if (valor == null) {
            return "";
        }
        return escapar(valor);
    }

    /** Montante com 2 casas (HALF_EVEN), vírgula decimal, sem milhares; o sinal negativo mantém-se. */
    public static String valor(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',');
    }

    /** Data {@code dd/MM/yyyy}. */
    public static String data(LocalDate data) {
        return data == null ? "" : DATA.format(data);
    }

    /** Junta células já formatadas com {@link #SEPARADOR} (sem escape adicional nem fim de linha). */
    public static String linha(List<String> celulas) {
        return String.join(SEPARADOR, celulas);
    }

    private static String escapar(String valor) {
        boolean aspar = valor.indexOf('"') >= 0 || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0
                || valor.contains(SEPARADOR);
        if (!aspar) {
            return valor;
        }
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}
