package com.lexcv.services.fiscal;

import com.lexcv.models.EntregaEmailFiscal;

import java.time.Duration;
import java.util.List;

/**
 * Phase 137 (ENTR-04): recuo determinístico (sem jitter) entre tentativas automáticas de envio do
 * email de um documento fiscal. Espelha {@link BackoffComunicacao}.
 *
 * <p>Depois da tentativa n (1..4) falhar de forma transitória, a próxima é agendada para
 * {@code agora + atraso(n)}: 1 min, 5 min, 15 min, 1 h. A 5.ª falha
 * ({@link EntregaEmailFiscal#MAX_TENTATIVAS}) passa a {@code FALHOU}, não tem atraso e abre a
 * notificação {@code EMAIL_FISCAL_FALHOU} (137-14).
 */
public final class BackoffEntregaEmail {

    private static final List<Duration> TABELA = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1));

    private BackoffEntregaEmail() {
    }

    /**
     * @param tentativas tentativas já feitas, 1..{@code MAX_TENTATIVAS - 1}
     * @throws IllegalArgumentException fora desse intervalo
     */
    public static Duration atraso(int tentativas) {
        if (tentativas < 1 || tentativas >= EntregaEmailFiscal.MAX_TENTATIVAS) {
            throw new IllegalArgumentException("Tentativa fora da tabela de recuo: " + tentativas);
        }
        return TABELA.get(tentativas - 1);
    }

    /** Tamanho da tabela; os testes afirmam que é {@code MAX_TENTATIVAS - 1}. */
    static int tamanhoTabela() {
        return TABELA.size();
    }
}
