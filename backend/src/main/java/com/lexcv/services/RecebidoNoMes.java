package com.lexcv.services;

import com.lexcv.models.Pagamento;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;

/**
 * Phase 135 (NCRD-03, PITFALLS P-14): soma pura do valor recebido num mês civil (visão de caixa),
 * partilhada pelo KPI {@code valores_recebidos_mes} do dashboard e pelo teste de coerência.
 *
 * <p>Conta os pagamentos cuja {@code dataPagamento} cai no ano E mês pedidos; ignora pagamentos
 * sem data ou sem valor. O estorno de uma Nota de Crédito é um {@code Pagamento} negativo datado do
 * dia da emissão da NC, por isso subtrai no mês de emissão da NC (CONTEXT: "o estorno conta no mês
 * de emissão da NC"). O mês corrente calcula-se em {@link #FUSO_CABO_VERDE}.
 */
public final class RecebidoNoMes {

    public static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

    private RecebidoNoMes() {
    }

    public static BigDecimal somar(Collection<Pagamento> pagamentos, YearMonth mes) {
        BigDecimal total = BigDecimal.ZERO;
        if (pagamentos == null || mes == null) {
            return total;
        }
        for (Pagamento p : pagamentos) {
            if (p == null || p.getDataPagamento() == null || p.getValorPago() == null) {
                continue;
            }
            if (YearMonth.from(p.getDataPagamento()).equals(mes)) {
                total = total.add(p.getValorPago());
            }
        }
        return total;
    }
}
