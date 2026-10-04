package com.lexcv.models;

/**
 * Phase 133 (CFG-05): ambiente em que uma série fiscal numera documentos.
 *
 * <p>{@link #SIMULADO} é o ÚNICO valor nesta fase: nenhum documento tem validade fiscal e o
 * código da série leva o prefixo {@code SIM-} (ex.: {@code SIM-FR-2026}). A Phase 136
 * ({@code EFATURA_MODE}) acrescenta a escolha de um ambiente real sem alteração de esquema,
 * porque a coluna {@code t_serie_fiscal.ambiente} é {@code varchar(32)} sem {@code CHECK}.
 */
public enum AmbienteFiscal {
    SIMULADO("Simulado", "SIM-");

    private final String rotulo;
    private final String prefixoSerie;

    AmbienteFiscal(String rotulo, String prefixoSerie) {
        this.rotulo = rotulo;
        this.prefixoSerie = prefixoSerie;
    }

    public String rotulo() {
        return rotulo;
    }

    public String prefixoSerie() {
        return prefixoSerie;
    }
}
