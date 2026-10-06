package com.lexcv.dtos;

import java.math.BigDecimal;

/**
 * Phase 134 (D-03): o que o formulário de pagamento precisa de saber antes de abrir. Com a
 * faturação desligada, {@code ambiente} e {@code taxaRetencaoSugerida} são nulos. A taxa
 * sugerida vem do parâmetro {@code RETENCAO_SUGERIDA} vigente hoje, nunca de uma constante.
 *
 * <p>Phase 136 (DFE-06): {@code modoComunicacao} é o ambiente do gateway eFatura em execução
 * ({@code SIMULADO} neste build), preenchido pelo controlador também com a faturação desligada;
 * a UI mostra o banner "Modo simulado" a partir dele.
 */
public record EstadoEmissaoResponse(
        boolean ativa,
        String ambiente,
        BigDecimal taxaRetencaoSugerida,
        String modoComunicacao
) {

    /** Construtor sem modo (o serviço de pré-visualização não conhece o gateway). */
    public EstadoEmissaoResponse(boolean ativa, String ambiente, BigDecimal taxaRetencaoSugerida) {
        this(ativa, ambiente, taxaRetencaoSugerida, null);
    }

    public static EstadoEmissaoResponse desligada() {
        return new EstadoEmissaoResponse(false, null, null);
    }

    /** Cópia com o modo de comunicação indicado. */
    public EstadoEmissaoResponse comModo(String modo) {
        return new EstadoEmissaoResponse(ativa, ambiente, taxaRetencaoSugerida, modo);
    }
}
