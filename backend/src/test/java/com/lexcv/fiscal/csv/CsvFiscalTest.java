package com.lexcv.fiscal.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Phase 137 (RELF-01; T-137-16, T-137-17): células CSV para o Excel PT, guarda de fórmulas só no texto livre. */
class CsvFiscalTest {

    @Test
    void constantes() {
        assertThat(CsvFiscal.BOM).isEqualTo("﻿");
        assertThat(CsvFiscal.SEPARADOR).isEqualTo(";");
        assertThat(CsvFiscal.FIM_LINHA).isEqualTo("\r\n");
    }

    // ---- textoLivre ----

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(1)", "+SUM(A1)", "-2+3", "@cmd", "\tx"})
    void textoLivreGuardaCaracteresDeFormula(String valor) {
        assertThat(CsvFiscal.textoLivre(valor)).isEqualTo("'" + valor);
    }

    @Test
    void textoLivreComCrInicialEGuardadoEDepoisAspado() {
        assertThat(CsvFiscal.textoLivre("\rX")).isEqualTo("\"'\rX\"");
    }

    @Test
    void textoLivreEscapaSeparadorAspasEQuebras() {
        assertThat(CsvFiscal.textoLivre("Ana; Lda")).isEqualTo("\"Ana; Lda\"");
        assertThat(CsvFiscal.textoLivre("Diz \"olá\"")).isEqualTo("\"Diz \"\"olá\"\"\"");
        assertThat(CsvFiscal.textoLivre("linha1\nlinha2")).isEqualTo("\"linha1\nlinha2\"");
        assertThat(CsvFiscal.textoLivre("=a;b")).isEqualTo("\"'=a;b\"");
    }

    @Test
    void textoLivreNuloEVazioEApostrofoInternoInalterado() {
        assertThat(CsvFiscal.textoLivre(null)).isEmpty();
        assertThat(CsvFiscal.textoLivre("")).isEmpty();
        assertThat(CsvFiscal.textoLivre("O'Brien")).isEqualTo("O'Brien");
        assertThat(CsvFiscal.textoLivre("Ana, Lda")).isEqualTo("Ana, Lda");
    }

    // ---- estruturado ----

    @Test
    void estruturadoNuncaGuardaMasEscapa() {
        assertThat(CsvFiscal.estruturado("+238 991")).isEqualTo("+238 991");
        assertThat(CsvFiscal.estruturado("-1")).isEqualTo("-1");
        assertThat(CsvFiscal.estruturado("=x")).isEqualTo("=x");
        assertThat(CsvFiscal.estruturado("a;b")).isEqualTo("\"a;b\"");
        assertThat(CsvFiscal.estruturado("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvFiscal.estruturado(null)).isEmpty();
    }

    // ---- valor ----

    @Test
    void valorVirgulaDecimalSemMilharesSinalMantido() {
        assertThat(CsvFiscal.valor(new BigDecimal("1234.5"))).isEqualTo("1234,50");
        assertThat(CsvFiscal.valor(new BigDecimal("-1234.50"))).isEqualTo("-1234,50");
        assertThat(CsvFiscal.valor(BigDecimal.ZERO)).isEqualTo("0,00");
        assertThat(CsvFiscal.valor(new BigDecimal("1234567.891"))).isEqualTo("1234567,89");
        assertThat(CsvFiscal.valor(new BigDecimal("0.125"))).isEqualTo("0,12");
        assertThat(CsvFiscal.valor(new BigDecimal("1E+3"))).isEqualTo("1000,00");
        assertThat(CsvFiscal.valor(null)).isEmpty();
    }

    @Test
    void valorEDataNaoDependemDoLocalePorOmissao() {
        Locale original = Locale.getDefault();
        try {
            for (Locale l : List.of(Locale.US, Locale.GERMANY, Locale.FRANCE)) {
                Locale.setDefault(l);
                assertThat(CsvFiscal.valor(new BigDecimal("1234567.5"))).as(l.toString()).isEqualTo("1234567,50");
                assertThat(CsvFiscal.valor(new BigDecimal("-1234.50"))).as(l.toString()).isEqualTo("-1234,50");
                assertThat(CsvFiscal.data(LocalDate.of(2026, 9, 3))).as(l.toString()).isEqualTo("03/09/2026");
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    // ---- data ----

    @Test
    void data() {
        assertThat(CsvFiscal.data(LocalDate.of(2026, 9, 3))).isEqualTo("03/09/2026");
        assertThat(CsvFiscal.data(LocalDate.of(2026, 12, 31))).isEqualTo("31/12/2026");
        assertThat(CsvFiscal.data(null)).isEmpty();
    }

    // ---- linha ----

    @Test
    void linhaJuntaCelulasJaFormatadas() {
        assertThat(CsvFiscal.linha(List.of("a", "b", ""))).isEqualTo("a;b;");
        assertThat(CsvFiscal.linha(List.of("\"a;b\"", "c"))).isEqualTo("\"a;b\";c");
        assertThat(CsvFiscal.linha(List.of())).isEmpty();
    }
}
