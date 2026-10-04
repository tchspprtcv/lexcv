package com.lexcv.services.fiscal;

import com.lexcv.dtos.PagamentoComDocumentoResponse;

/**
 * Phase 134 (D-10, D-11): resultado de {@link PagamentoFaturadoService#registar}.
 *
 * @param novo     {@code true} quando esta chamada emitiu o documento (o controlador responde 201);
 *                 {@code false} quando a chave de idempotência já tinha sido usada com o mesmo
 *                 pedido e se devolve o resultado guardado (200)
 * @param resposta o pagamento com a referência ao seu documento fiscal
 */
public record ResultadoPagamentoFaturado(boolean novo, PagamentoComDocumentoResponse resposta) {

    public static ResultadoPagamentoFaturado novo(PagamentoComDocumentoResponse resposta) {
        return new ResultadoPagamentoFaturado(true, resposta);
    }

    public static ResultadoPagamentoFaturado repetido(PagamentoComDocumentoResponse resposta) {
        return new ResultadoPagamentoFaturado(false, resposta);
    }
}
