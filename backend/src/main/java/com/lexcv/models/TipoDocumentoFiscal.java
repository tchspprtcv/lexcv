package com.lexcv.models;

/**
 * Phase 133 (CFG-05): tipos de documento fiscal emitidos pelo LexCV. O nome da constante
 * ({@code FR}, {@code NC}) é o código usado no código da série (ex.: {@code SIM-FR-2026}).
 */
public enum TipoDocumentoFiscal {
    FR("Fatura-Recibo"),
    NC("Nota de Crédito");

    private final String rotulo;

    TipoDocumentoFiscal(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
