package com.lexcv.dtos;

import com.lexcv.models.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phase 134 (D-19, EMIS-12): um pagamento com o seu documento fiscal.
 *
 * <p>Superconjunto do JSON da entidade {@link Pagamento}: os cinco primeiros componentes têm os
 * mesmos nomes e tipos JSON ({@code id, honorarioId, valorPago, dataPagamento, metodo}), por isso
 * o tipo {@code Pagamento} do frontend continua a funcionar (RESEARCH Pitfall 10).
 * {@code documentoFiscal} é {@code null} para pagamentos registados sem faturação (antes da
 * ativação ou com a faturação desligada).
 *
 * <p>Phase 135 (NCRD-03): {@code estorno} referencia a Nota de Crédito quando este pagamento é o
 * estorno (negativo) dessa NC; nesse caso {@code documentoFiscal} é {@code null} -- um estorno
 * nunca aparece como Fatura-Recibo. A UI mostra "Estorno (NC n.º …)" e não oferece apagar.
 */
public record PagamentoComDocumentoResponse(
        Integer id,
        Integer honorarioId,
        BigDecimal valorPago,
        LocalDate dataPagamento,
        String metodo,
        DocumentoFiscalRef documentoFiscal,
        DocumentoFiscalRef estorno
) {

    /** Pagamento sem estorno (FR ou nenhum documento). */
    public static PagamentoComDocumentoResponse de(Pagamento pagamento, DocumentoFiscalRef documentoOuNulo) {
        return de(pagamento, documentoOuNulo, null);
    }

    /** Phase 135: {@code estornoOuNulo} é a NC de que este pagamento é o estorno. */
    public static PagamentoComDocumentoResponse de(Pagamento pagamento, DocumentoFiscalRef documentoOuNulo,
                                                   DocumentoFiscalRef estornoOuNulo) {
        return new PagamentoComDocumentoResponse(
                pagamento.getId(),
                pagamento.getHonorarioId(),
                pagamento.getValorPago(),
                pagamento.getDataPagamento(),
                pagamento.getMetodo(),
                documentoOuNulo,
                estornoOuNulo);
    }
}
