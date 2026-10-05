package com.lexcv.services.fiscal;

import com.lexcv.dtos.NotaCreditoResponse;

/**
 * Phase 135 (NCRD-01): resultado da emissão de uma Nota de Crédito.
 *
 * @param novo     {@code true} quando esta chamada emitiu a NC (o controlador responde 201);
 *                 {@code false} quando a chave de idempotência já tinha sido usada com o mesmo
 *                 pedido e se devolve a NC guardada (200)
 * @param resposta a NC com o seu estorno
 */
public record ResultadoNotaCredito(boolean novo, NotaCreditoResponse resposta) {

    public static ResultadoNotaCredito novo(NotaCreditoResponse resposta) {
        return new ResultadoNotaCredito(true, resposta);
    }

    public static ResultadoNotaCredito repetido(NotaCreditoResponse resposta) {
        return new ResultadoNotaCredito(false, resposta);
    }
}
