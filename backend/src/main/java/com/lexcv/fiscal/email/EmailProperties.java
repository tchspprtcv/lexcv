package com.lexcv.fiscal.email;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Phase 137 (ENTR-03, ENTR-06): configuração {@code app.email.*}. Todos os valores SMTP são
 * opcionais e vêm só de variáveis de ambiente (137-09); sem {@code host} e {@code from} o SMTP não
 * está configurado, a aplicação arranca na mesma e as entregas aparecem como "não configurado".
 *
 * <p>Nunca {@code spring.mail.*}: uma string vazia nessa árvore cria um {@code JavaMailSender} sem
 * host (137-SPIKE.md C). O adaptador SMTP constrói o seu próprio {@code JavaMailSenderImpl} privado.
 *
 * <ul>
 *   <li>{@code smtp}: servidor, credenciais, remetente, STARTTLS (ligado e exigido por omissão) e
 *       timeouts;</li>
 *   <li>{@code outbox}: cadência, atraso inicial, lote e lease do job de envio.</li>
 * </ul>
 */
@ConfigurationProperties("app.email")
public record EmailProperties(
        @DefaultValue Smtp smtp,
        @DefaultValue Outbox outbox) {

    /** {@code true} quando o SMTP tem host e remetente. */
    public boolean configurado() {
        return smtp != null && smtp.configurado();
    }

    /**
     * Servidor SMTP. {@link #toString()} nunca mostra a palavra-passe nem o utilizador
     * (T-137-28).
     */
    public record Smtp(
            String host,
            @DefaultValue("587") Integer port,
            String username,
            String password,
            String from,
            @DefaultValue("true") Boolean starttls,
            @DefaultValue("PT10S") Duration ligacaoTimeout,
            @DefaultValue("PT20S") Duration leituraTimeout) {

        /** Porta por omissão (submission com STARTTLS). */
        public static final int PORTA_POR_OMISSAO = 587;

        /**
         * {@code SMTP_PORT=} e {@code SMTP_STARTTLS=} vazios (o caso do compose/CI) chegam aqui como
         * {@code null}: passam a 587 e STARTTLS ligado, em vez de impedirem o arranque (ENTR-06).
         */
        public Smtp {
            port = port == null ? Integer.valueOf(PORTA_POR_OMISSAO) : port;
            starttls = starttls == null ? Boolean.TRUE : starttls;
        }

        /** Configurado = host e remetente ambos preenchidos. */
        public boolean configurado() {
            return naoVazio(host) && naoVazio(from);
        }

        /** Há credenciais quando o utilizador está preenchido. */
        public boolean comAutenticacao() {
            return naoVazio(username);
        }

        private static boolean naoVazio(String valor) {
            return valor != null && !valor.isBlank();
        }

        @Override
        public String toString() {
            return "Smtp[host=" + host + ", port=" + port + ", starttls=" + starttls
                    + ", username=" + (comAutenticacao() ? "(definido)" : "(vazio)")
                    + ", password=***, from=" + (naoVazio(from) ? "(definido)" : "(vazio)")
                    + ", ligacaoTimeout=" + ligacaoTimeout + ", leituraTimeout=" + leituraTimeout + "]";
        }
    }

    /** Job de envio: intervalo entre execuções, atraso inicial, lote e lease. */
    public record Outbox(
            @DefaultValue("PT30S") Duration intervalo,
            @DefaultValue("PT40S") Duration atrasoInicial,
            @DefaultValue("10") int lote,
            @DefaultValue("PT2M") Duration lease) {
    }
}
