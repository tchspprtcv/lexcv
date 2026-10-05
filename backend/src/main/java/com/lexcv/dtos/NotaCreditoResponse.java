package com.lexcv.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 135 (NCRD-01, NCRD-03): resultado da emissão de uma Nota de Crédito.
 *
 * <p>{@code totalDocumento} e {@code valorCreditavelRestante} são magnitudes POSITIVAS; só o
 * {@code valorPago} do {@code estorno} é negativo. {@code estorno} traz a referência à NC em
 * {@code estorno.estorno} e {@code documentoFiscal = null}. A chave de idempotência nunca é exposta.
 */
public record NotaCreditoResponse(
        UUID id,
        String numeroFormatado,
        String tipo,
        UUID documentoOrigemId,
        String documentoOrigemNumero,
        LocalDate dataEmissao,
        BigDecimal totalDocumento,
        PagamentoComDocumentoResponse estorno,
        BigDecimal valorCreditavelRestante
) {
}
