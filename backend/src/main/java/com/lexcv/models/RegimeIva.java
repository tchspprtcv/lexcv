package com.lexcv.models;

/**
 * Phase 133 (CFG-02): regime de IVA do escritório emitente. Quando {@link #ISENTO}, a
 * configuração fiscal exige um código de {@link MotivoIsencaoIva}.
 */
public enum RegimeIva {
    NORMAL("Normal"),
    ISENTO("Isento");

    private final String rotulo;

    RegimeIva(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
