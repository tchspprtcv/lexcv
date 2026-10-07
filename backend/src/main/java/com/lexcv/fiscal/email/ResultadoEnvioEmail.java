package com.lexcv.fiscal.email;

/**
 * Phase 137 (ENTR-03): resultado selado de um envio. Códigos e mensagens são fixos (nunca o texto
 * do servidor SMTP nem de uma exceção, T-137-31).
 */
public sealed interface ResultadoEnvioEmail
        permits ResultadoEnvioEmail.Enviado, ResultadoEnvioEmail.ErroTransitorio, ResultadoEnvioEmail.ErroPermanente {

    /** O servidor SMTP aceitou a mensagem. */
    record Enviado() implements ResultadoEnvioEmail {
    }

    /** Falha que pode passar (ligação, timeout, 4xx, autenticação): nova tentativa automática. */
    record ErroTransitorio(String codigo, String mensagem) implements ResultadoEnvioEmail {
    }

    /** Falha que não passa com nova tentativa (destinatário recusado ou inválido). */
    record ErroPermanente(String codigo, String mensagem) implements ResultadoEnvioEmail {
    }
}
