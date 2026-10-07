package com.lexcv.models;

import java.util.Optional;

// NOTF-24: canonical enum of the 11 notification categories and the single
// source of truth for "silenciabilidade" (whether a category can be muted)
// on the backend. This enum exists ONLY to validate preference toggles --
// it deliberately does NOT retrofit the existing hardcoded String call
// sites elsewhere in the codebase (e.g. Notificacao.categoria), which stay
// as-is per 93-CONTEXT.md.
//
// Three categories are NOT silenciable: PRAZO_VENCIDO, (Phase 136, DFE-07)
// COMUNICACAO_FISCAL_FALHOU -- a persistent eFatura communication failure is
// a fiscal obligation the office must act on, so it can never be muted -- and
// (Phase 137, ENTR-05: não silenciável) EMAIL_FISCAL_FALHOU -- the client did
// not receive the fiscal document by email after the automatic attempts.
// Keep in sync with NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS in
// web/src/lib/notificacao-categoria.ts.
public enum CategoriaNotificacao {

    FASE_ENTRADA(true),
    DOCUMENTO_NOVO(true),
    PROCESSO_ATRIBUIDO(true),
    PARECER_ATRIBUIDO(true),
    PRAZO_PROXIMO(true),
    PRAZO_VENCIDO(false),
    EVENTO_PROXIMO(true),
    EVENTO_VENCIDO(true),
    HONORARIO_ATRASADO(true),
    COMUNICACAO_FISCAL_FALHOU(false),
    // Phase 137 (ENTR-05): falha persistente do envio do documento fiscal por email; não silenciável.
    EMAIL_FISCAL_FALHOU(false);

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

    // Returns false for unknown categories AND for the non-silenciable ones
    // (PRAZO_VENCIDO, COMUNICACAO_FISCAL_FALHOU, EMAIL_FISCAL_FALHOU). This is
    // the single method the criar() mute guard (Plan 93-02) and the
    // preferences endpoint validation (Plan 93-03) consume.
    public static boolean isSilenciavelCategoria(String valor) {
        return fromString(valor).map(CategoriaNotificacao::isSilenciavel).orElse(false);
    }
}
