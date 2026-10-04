package com.lexcv.dtos;

import java.math.BigDecimal;

/**
 * Phase 134 (D-03): o que o formulário de pagamento precisa de saber antes de abrir. Com a
 * faturação desligada, {@code ambiente} e {@code taxaRetencaoSugerida} são nulos. A taxa
 * sugerida vem do parâmetro {@code RETENCAO_SUGERIDA} vigente hoje, nunca de uma constante.
 */
public record EstadoEmissaoResponse(
        boolean ativa,
        String ambiente,
        BigDecimal taxaRetencaoSugerida
) {

    public static EstadoEmissaoResponse desligada() {
        return new EstadoEmissaoResponse(false, null, null);
    }
}
