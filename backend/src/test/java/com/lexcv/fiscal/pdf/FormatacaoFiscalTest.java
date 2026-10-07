package com.lexcv.fiscal.pdf;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 137 (ENTR-01): formatação partilhada de dinheiro e datas do PDF e do email. */
class FormatacaoFiscalTest {

    private static final String NNBSP = " ";

    @Test
    void dinheiro() {
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("12345"), "CVE")).isEqualTo("12" + NNBSP + "345,00 CVE");
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("1234567.891"), null))
                .isEqualTo("1" + NNBSP + "234" + NNBSP + "567,89 CVE");
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("999.5"), "CVE")).isEqualTo("999,50 CVE");
        assertThat(FormatacaoFiscal.dinheiro(BigDecimal.ZERO, "")).isEqualTo("0,00 CVE");
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("-1500"), "CVE")).isEqualTo("-1" + NNBSP + "500,00 CVE");
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("100000"), "EUR")).isEqualTo("100" + NNBSP + "000,00 EUR");
        assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("0.125"), "  ")).isEqualTo("0,12 CVE");
    }

    @Test
    void data() {
        assertThat(FormatacaoFiscal.data(LocalDate.of(2026, 3, 7))).isEqualTo("07/03/2026");
    }

    @Test
    void nulosSaoRecusados() {
        assertThatThrownBy(() -> FormatacaoFiscal.dinheiro(null, "CVE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FormatacaoFiscal.data(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoDependeDoLocalePorOmissao() {
        Locale original = Locale.getDefault();
        try {
            for (Locale l : List.of(Locale.US, Locale.GERMANY)) {
                Locale.setDefault(l);
                assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("1234567.891"), "CVE"))
                        .as(l.toString()).isEqualTo("1" + NNBSP + "234" + NNBSP + "567,89 CVE");
                assertThat(FormatacaoFiscal.dinheiro(new BigDecimal("-1500"), "CVE"))
                        .as(l.toString()).isEqualTo("-1" + NNBSP + "500,00 CVE");
                assertThat(FormatacaoFiscal.data(LocalDate.of(2026, 3, 7))).as(l.toString()).isEqualTo("07/03/2026");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
