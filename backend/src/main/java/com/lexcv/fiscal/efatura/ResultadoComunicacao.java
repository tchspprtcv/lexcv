package com.lexcv.fiscal.efatura;

/**
 * Phase 136 (DFE-03, DFE-06): resultado selado de uma comunicação.
 *
 * <ul>
 *   <li>{@link AceiteSimulado}: o adaptador simulado aceitou um XML válido. Só pode tornar-se
 *       {@code ACEITE_SIMULADO} (nunca uma autorização).</li>
 *   <li>{@link Rejeitado}: recusa definitiva; o estado fica {@code REJEITADO} de imediato.</li>
 *   <li>{@link ErroTransitorio}: nova tentativa com recuo, até ao máximo de tentativas.</li>
 * </ul>
 *
 * <p>{@code codigo} e {@code mensagem} são SEMPRE texto fixo, seguro para mostrar ao utilizador
 * e para gravar em {@code ultimo_erro}: nunca o texto de uma exceção nem valores do documento.
 */
public sealed interface ResultadoComunicacao {

    /** Aceitação pelo adaptador simulado, com uma referência sintética. */
    record AceiteSimulado(String referencia) implements ResultadoComunicacao {
    }

    /** Recusa definitiva. */
    record Rejeitado(String codigo, String mensagem) implements ResultadoComunicacao {
    }

    /** Falha temporária; o job volta a tentar. */
    record ErroTransitorio(String codigo, String mensagem) implements ResultadoComunicacao {
    }
}
