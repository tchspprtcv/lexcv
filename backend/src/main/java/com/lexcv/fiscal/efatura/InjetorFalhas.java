package com.lexcv.fiscal.efatura;

import java.util.Optional;

/**
 * Phase 136 (D Adaptador: "falhas injetáveis em teste"): permite forçar um resultado no adaptador
 * simulado antes da validação. Escolhido só por {@code EfaturaConfig} (136-12) a partir de uma
 * propriedade explícita que por omissão é {@link #NENHUMA}. Os dois injetores disponíveis em
 * produção ({@link #NENHUMA}, {@link #SEMPRE_TRANSITORIA}) só podem fazer um documento simulado
 * falhar, nunca o aceitar sem XML válido.
 */
@FunctionalInterface
public interface InjetorFalhas {

    /** Comportamento normal: nada é injetado. */
    InjetorFalhas NENHUMA = pedido -> Optional.empty();

    /** Toda a comunicação falha de forma transitória (testes de recuo e de ERRO). */
    InjetorFalhas SEMPRE_TRANSITORIA = pedido -> Optional.of(
            new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA", "Falha simulada do serviço de comunicação."));

    /** O resultado a impor a este pedido, ou vazio para seguir o caminho normal. */
    Optional<ResultadoComunicacao> injetar(PedidoComunicacao pedido);
}
