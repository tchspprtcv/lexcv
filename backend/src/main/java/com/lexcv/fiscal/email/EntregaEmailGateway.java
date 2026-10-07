package com.lexcv.fiscal.email;

/**
 * Phase 137 (ENTR-03, ENTR-06): a ÚNICA porta por onde um email fiscal sai do sistema.
 *
 * <p>Duas implementações: {@link SmtpEntregaEmailGateway} (SMTP configurado) e
 * {@link NaoConfiguradoEntregaEmailGateway} (sem SMTP). Escolhida em arranque (137-09).
 *
 * <p>{@link #enviar} nunca lança uma exceção: qualquer problema vira um {@link ResultadoEnvioEmail}
 * com código e mensagem fixos. Nunca é chamada dentro de uma transação.
 */
public interface EntregaEmailGateway {

    /** {@code true} quando há um servidor SMTP configurado. */
    boolean configurado();

    /** Envia uma mensagem a um destinatário. */
    ResultadoEnvioEmail enviar(MensagemEmailFiscal mensagem);
}
