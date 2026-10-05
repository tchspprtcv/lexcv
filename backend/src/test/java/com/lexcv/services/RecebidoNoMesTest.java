package com.lexcv.services;

import com.lexcv.models.Pagamento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 135 (NCRD-03, P-14): soma do recebido no mês (visão de caixa) usada pelo KPI do dashboard.
 * Corrige o defeito antigo (só o mês, sem ano, sem fuso, sem null-check) e prova que o estorno de
 * uma Nota de Crédito subtrai no mês da sua emissão.
 */
class RecebidoNoMesTest {

    private static final YearMonth JUNHO_2026 = YearMonth.of(2026, 6);

    private static Pagamento pag(String data, String valor) {
        return Pagamento.builder()
                .dataPagamento(data == null ? null : LocalDate.parse(data))
                .valorPago(valor == null ? null : new BigDecimal(valor))
                .build();
    }

    @Test
    void pagamentoDoMesEAnoContaOutrosNao() {
        List<Pagamento> pags = List.of(
                pag("2026-06-03", "100"),
                pag("2025-06-03", "900"),   // mesmo mês do ano anterior: o defeito antigo contava-o
                pag("2026-07-01", "50"),
                pag("2026-05-31", "70"));
        assertEquals(0, new BigDecimal("100").compareTo(RecebidoNoMes.somar(pags, JUNHO_2026)));
    }

    @Test
    void dataOuValorNulosSaoIgnorados() {
        List<Pagamento> pags = new ArrayList<>();
        pags.add(pag("2026-06-03", "100"));
        pags.add(pag(null, "500"));
        pags.add(pag("2026-06-04", null));
        pags.add(null);
        assertEquals(0, new BigDecimal("100").compareTo(RecebidoNoMes.somar(pags, JUNHO_2026)));
    }

    @Test
    void estornoSubtraiNoMesDaEmissaoDaNc() {
        List<Pagamento> pags = List.of(pag("2026-06-03", "100"), pag("2026-06-15", "-40"));
        assertEquals(0, new BigDecimal("60").compareTo(RecebidoNoMes.somar(pags, JUNHO_2026)));
    }

    @Test
    void estornoNoutroMesNaoAfetaOMesDoPagamentoOriginal() {
        List<Pagamento> pags = List.of(pag("2026-06-03", "100"), pag("2026-07-02", "-100"));
        assertEquals(0, new BigDecimal("100").compareTo(RecebidoNoMes.somar(pags, JUNHO_2026)));
        assertEquals(0, new BigDecimal("-100").compareTo(RecebidoNoMes.somar(pags, YearMonth.of(2026, 7))));
    }

    @Test
    void semPagamentosOuColecaoNulaDaZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(RecebidoNoMes.somar(List.of(), JUNHO_2026)));
        assertEquals(0, BigDecimal.ZERO.compareTo(RecebidoNoMes.somar(null, JUNHO_2026)));
    }

    @Test
    void fusoEhCaboVerde() {
        assertEquals(ZoneId.of("Atlantic/Cape_Verde"), RecebidoNoMes.FUSO_CABO_VERDE);
    }
}
