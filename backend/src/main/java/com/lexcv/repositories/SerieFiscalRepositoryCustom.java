package com.lexcv.repositories;

import com.lexcv.models.SerieFiscal;

/** Fragmento de {@link SerieFiscalRepository} com operações que precisam do EntityManager. */
public interface SerieFiscalRepositoryCustom {

    /**
     * Relê a série da base de dados para a instância gerida (WR-02 da revisão da Phase 133).
     * Chamado por {@code NumeracaoService} logo depois de {@link SerieFiscalRepository#bloquear}:
     * se a série já estava no persistence context do chamador, o {@code SELECT ... FOR UPDATE}
     * devolve essa instância sem a reidratar; como a linha já está bloqueada, o refresh lê o valor
     * comprometido mais recente de {@code ultimo_numero}.
     */
    void refrescar(SerieFiscal serie);
}
