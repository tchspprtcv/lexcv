package com.lexcv.models;

/**
 * Phase 133 (CFG-04): códigos dos parâmetros fiscais guardados em {@code t_parametro_fiscal}
 * (persistidos como {@link #name()} na coluna {@code codigo}).
 *
 * <p>Os valores são percentagens guardadas como DADOS, nunca como constantes no código. Uma
 * mudança legal é uma NOVA linha com {@code vigente_desde} posterior, nunca um UPDATE de uma
 * linha existente.
 */
public enum CodigoParametroFiscal {
    /** Taxa normal de IVA (percentagem). */
    IVA_TAXA_NORMAL,
    /** Taxa de retenção na fonte sugerida (percentagem; sugestão sujeita a confirmação do contabilista). */
    RETENCAO_SUGERIDA
}
