package com.lexcv.models;

/**
 * Phase 134 (D-08): estado da comunicação de um documento fiscal à plataforma eFatura,
 * guardado no satélite mutável {@code t_comunicacao_fiscal}.
 *
 * <p>{@link #PENDENTE} é o ÚNICO valor nesta fase: todo o documento nasce pendente e nada o
 * comunica ainda. A Phase 136 acrescenta os restantes estados (enviado, aceite, rejeitado, ...)
 * sem alteração de esquema, porque a coluna {@code estado} é {@code varchar(32)} sem
 * {@code CHECK} (ver {@link EstadoComunicacaoFiscalConverter}).
 */
public enum EstadoComunicacaoFiscal {
    PENDENTE("Pendente");

    private final String rotulo;

    EstadoComunicacaoFiscal(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
