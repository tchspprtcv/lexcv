package com.lexcv.models;

/**
 * Phase 137 (ENTR-03, ENTR-04): estado da entrega por email de um documento fiscal ao cliente,
 * guardado no satélite mutável {@code t_entrega_email_fiscal} ({@link EntregaEmailFiscal}).
 *
 * <ul>
 *   <li>{@link #DESLIGADO} -- o envio automático estava desligado quando a comunicação ficou aceite;</li>
 *   <li>{@link #SEM_EMAIL} -- o cliente não tinha endereço de email;</li>
 *   <li>{@link #PENDENTE} -- na fila do job de envio (com tentativas e lease);</li>
 *   <li>{@link #ENVIADO} -- o servidor SMTP aceitou a mensagem;</li>
 *   <li>{@link #FALHOU} -- falha permanente ou tentativas esgotadas ({@link EntregaEmailFiscal#MAX_TENTATIVAS}).</li>
 * </ul>
 *
 * <p>{@code NAO_CONFIGURADO} NÃO é um valor deste enum e nunca é gravado: é um estado de
 * apresentação derivado em leitura quando o SMTP não está configurado (137-05
 * {@code RegrasEntregaEmail}). A coluna é {@code varchar(32)} gravada por
 * {@link EstadoEntregaEmailConverter} (sem {@code CHECK} de enum, como a comunicação).
 */
public enum EstadoEntregaEmail {
    DESLIGADO,
    SEM_EMAIL,
    PENDENTE,
    ENVIADO,
    FALHOU;

    /** {@code true} quando o job de envio já não volta a pegar na linha sozinho. */
    public boolean terminal() {
        return this != PENDENTE;
    }

    /** {@code true} para os estados que nunca chegaram a tentar um envio. */
    public boolean semTentativas() {
        return this == DESLIGADO || this == SEM_EMAIL;
    }

    /**
     * {@code true} para os estados a partir dos quais o "Reenviar email" manual é permitido
     * (UI-SPEC "Decisions"): nunca a partir de {@link #DESLIGADO} nem de {@link #PENDENTE}.
     */
    public boolean reenviavelPorEstado() {
        return this == FALHOU || this == ENVIADO || this == SEM_EMAIL;
    }
}
