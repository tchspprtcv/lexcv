package com.lexcv.services.fiscal;

/**
 * Phase 134 (D-05, D-20): textos controlados de um documento fiscal.
 *
 * <p>A descrição da linha é SEMPRE este texto fixo, com o número do processo quando existe --
 * nunca a descrição livre que o advogado escreveu no honorário, que pode conter informação
 * protegida pelo sigilo profissional e acabaria num documento enviado à administração fiscal.
 */
public final class TextoDocumentoFiscal {

    public static final String DESCRICAO_HONORARIOS = "Honorários por serviços jurídicos";

    /** Comprimento de {@code t_documento_fiscal_linha.descricao}. */
    public static final int DESCRICAO_MAX = 200;

    private static final String PREFIXO_PROCESSO = DESCRICAO_HONORARIOS + " — Processo n.º ";

    private TextoDocumentoFiscal() {
    }

    /**
     * {@code "Honorários por serviços jurídicos — Processo n.º {numero}"}, ou só
     * {@link #DESCRICAO_HONORARIOS} sem número. Nunca passa de {@value #DESCRICAO_MAX}
     * caracteres (o número é truncado se for preciso).
     */
    public static String descricaoLinhaHonorarios(String numeroProcesso) {
        if (numeroProcesso == null || numeroProcesso.isBlank()) {
            return DESCRICAO_HONORARIOS;
        }
        String texto = PREFIXO_PROCESSO + numeroProcesso.trim();
        return texto.length() <= DESCRICAO_MAX ? texto : texto.substring(0, DESCRICAO_MAX);
    }

    /** D-20: número visível do documento, ex.: {@code SIM-FR-2026/1}. */
    public static String numeroFormatado(String serieCodigo, long numero) {
        return serieCodigo + "/" + numero;
    }
}
