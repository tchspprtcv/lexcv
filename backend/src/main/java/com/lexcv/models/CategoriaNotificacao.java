package com.lexcv.models;

import java.util.Optional;

// NOTF-24: canonical enum of the 9 notification categories and the single
// source of truth for "silenciabilidade" (whether a category can be muted)
// on the backend. This enum exists ONLY to validate preference toggles --
// it deliberately does NOT retrofit the 9 existing hardcoded String call
// sites elsewhere in the codebase (e.g. Notificacao.categoria), which stay
// as-is per 93-CONTEXT.md.
public enum CategoriaNotificacao {

    FASE_ENTRADA(true),
    DOCUMENTO_NOVO(true),
    PROCESSO_ATRIBUIDO(true),
    PARECER_ATRIBUIDO(true),
    PRAZO_PROXIMO(true),
    PRAZO_VENCIDO(false),
    EVENTO_PROXIMO(true),
    EVENTO_VENCIDO(true),
    HONORARIO_ATRASADO(true);

    private final boolean silenciavel;

    CategoriaNotificacao(boolean silenciavel) {
        this.silenciavel = silenciavel;
    }

    public boolean isSilenciavel() {
        return silenciavel;
    }

    // Never throws -- callers (Plan 93-02 mute guard, Plan 93-03 endpoint
    // validation) rely on this contract to distinguish "unknown category"
    // from "known but non-silenciable".
    public static Optional<CategoriaNotificacao> fromString(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        for (CategoriaNotificacao categoria : values()) {
            if (categoria.name().equals(valor)) {
                return Optional.of(categoria);
            }
        }
        return Optional.empty();
    }

    // Returns false for unknown categories AND for PRAZO_VENCIDO. This is
    // the single method the criar() mute guard (Plan 93-02) and the
    // preferences endpoint validation (Plan 93-03) consume.
    public static boolean isSilenciavelCategoria(String valor) {
        return fromString(valor).map(CategoriaNotificacao::isSilenciavel).orElse(false);
    }
}
