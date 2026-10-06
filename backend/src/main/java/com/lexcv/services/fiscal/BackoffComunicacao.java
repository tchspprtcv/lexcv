package com.lexcv.services.fiscal;

import java.time.Duration;
import java.util.List;

/**
 * Phase 136 (DFE-04): recuo determinístico (sem jitter) entre tentativas automáticas.
 *
 * <p>Depois da tentativa n (1..7) falhar de forma transitória, a próxima é agendada para
 * {@code agora + atraso(n)}: 30 s, 2 min, 5 min, 15 min, 30 min, 1 h, 3 h. A 8.ª falha
 * ({@link EstadoComunicacaoMapper#MAX_TENTATIVAS}) passa a {@code ERRO} e não tem atraso.
 */
public final class BackoffComunicacao {

    private static final List<Duration> TABELA = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(3));

    private BackoffComunicacao() {
    }

    /**
     * @param tentativas tentativas já feitas, 1..{@code MAX_TENTATIVAS - 1}
     * @throws IllegalArgumentException fora desse intervalo
     */
    public static Duration atraso(int tentativas) {
        if (tentativas < 1 || tentativas >= EstadoComunicacaoMapper.MAX_TENTATIVAS) {
            throw new IllegalArgumentException("Tentativa fora da tabela de recuo: " + tentativas);
        }
        return TABELA.get(tentativas - 1);
    }
}
