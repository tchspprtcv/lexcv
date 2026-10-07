package com.lexcv.fiscal.email;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 137 (ENTR-06, ENTR-03): escolhe o adaptador de email a partir da configuração opcional
 * {@code app.email.smtp.*} (variáveis {@code SMTP_*}, todas com valor vazio por omissão).
 *
 * <ul>
 *   <li>{@code SMTP_HOST} vazio: o SMTP não está configurado, a aplicação arranca e emite
 *       normalmente, e o envio aparece como "Não configurado" ({@link NaoConfiguradoEntregaEmailGateway}).
 *       Uma ausência de SMTP <em>nunca</em> impede o arranque.</li>
 *   <li>{@code SMTP_HOST} definido mas {@code SMTP_FROM} vazio ou inválido, ou {@code SMTP_PORT}
 *       fora de 1..65535: configuração a meio, o arranque falha com uma mensagem que nomeia a
 *       variável e nunca o seu valor (T-137-33, T-137-35).</li>
 *   <li>Caso contrário: {@link SmtpEntregaEmailGateway}, com o seu {@code JavaMailSenderImpl} privado.</li>
 * </ul>
 *
 * <p>Não declara nenhum bean {@code JavaMailSender}, e a aplicação nunca define {@code spring.mail.*},
 * pelo que a auto-configuração de mail do Spring Boot não cria nenhum (137-SPIKE.md C).
 * Os logs de arranque só mostram host, porta e STARTTLS.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    static final String MSG_FROM_INVALIDO = "SMTP_HOST está definido mas SMTP_FROM está vazio ou inválido: "
            + "defina SMTP_FROM com um endereço válido ou deixe SMTP_HOST vazio.";
    static final String MSG_PORTA_INVALIDA = "SMTP_HOST está definido mas SMTP_PORT está fora do intervalo "
            + "1..65535: defina SMTP_PORT com uma porta válida ou deixe SMTP_HOST vazio.";

    @Bean
    public EntregaEmailGateway entregaEmailGateway(EmailProperties propriedades) {
        EmailProperties.Smtp smtp = propriedades.smtp();
        if (smtp == null || smtp.host() == null || smtp.host().isBlank()) {
            log.info("Envio de email fiscal: servidor SMTP não configurado (SMTP_HOST vazio); os documentos são "
                    + "emitidos normalmente e o envio aparece como 'Não configurado'.");
            return new NaoConfiguradoEntregaEmailGateway();
        }
        if (!remetenteValido(smtp.from())) {
            throw new IllegalStateException(MSG_FROM_INVALIDO);
        }
        if (smtp.port() < 1 || smtp.port() > 65535) {
            throw new IllegalStateException(MSG_PORTA_INVALIDA);
        }
        log.info("Envio de email fiscal: SMTP {}:{} (STARTTLS {})", smtp.host().strip(), smtp.port(),
                smtp.starttls() ? "on" : "off");
        return new SmtpEntregaEmailGateway(smtp);
    }

    /** Remetente preenchido e aceite por {@link InternetAddress} em modo estrito. */
    static boolean remetenteValido(String from) {
        if (from == null || from.isBlank()) {
            return false;
        }
        try {
            InternetAddress endereco = new InternetAddress(from.strip(), true);
            endereco.validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }
}
