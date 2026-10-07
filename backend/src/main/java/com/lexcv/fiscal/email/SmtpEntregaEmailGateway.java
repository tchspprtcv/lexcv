package com.lexcv.fiscal.email;

import com.lexcv.services.fiscal.RegrasEntregaEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.util.MailConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Phase 137 (ENTR-03): envia o email fiscal por SMTP.
 *
 * <p>Constrói o seu próprio {@link JavaMailSenderImpl} privado a partir de
 * {@link EmailProperties.Smtp}; nunca é exposto como bean nem construído pela auto-configuração do
 * Spring Boot (137-SPIKE.md C). STARTTLS ligado e exigido por omissão, com verificação da
 * identidade do servidor (T-137-30); timeouts de ligação, leitura e escrita.
 *
 * <p>Destinatário, remetente e Reply-To passam por {@link InternetAddress} em modo estrito antes de
 * abrir qualquer ligação; o destinatário também por {@link RegrasEntregaEmail#emailValido} (sem CR/LF,
 * sem separadores, um único endereço sem nome -- T-137-29). Um Reply-To inválido é omitido.
 *
 * <p>Classificação das falhas (códigos e mensagens fixos, T-137-31): autenticação ->
 * transitória {@code SMTP_AUTENTICACAO}; destinatário recusado com 5xx -> permanente
 * {@code DESTINATARIO_RECUSADO}; 4xx, ligação ou timeout -> transitória {@code SMTP_INDISPONIVEL};
 * qualquer outra -> transitória {@code FALHA_ENVIO}. Os logs só levam nomes de classes de exceção.
 * Nunca lança uma exceção.
 */
public final class SmtpEntregaEmailGateway implements EntregaEmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpEntregaEmailGateway.class);

    public static final String DESTINATARIO_INVALIDO = "DESTINATARIO_INVALIDO";
    public static final String DESTINATARIO_RECUSADO = "DESTINATARIO_RECUSADO";
    public static final String SMTP_INDISPONIVEL = "SMTP_INDISPONIVEL";
    public static final String SMTP_AUTENTICACAO = "SMTP_AUTENTICACAO";
    public static final String REMETENTE_INVALIDO = "SMTP_REMETENTE_INVALIDO";
    public static final String FALHA_ENVIO = "FALHA_ENVIO";

    public static final String MSG_DESTINATARIO_INVALIDO = "O endereço de email do cliente não é válido.";
    public static final String MSG_DESTINATARIO_RECUSADO = "O servidor de email recusou o endereço do cliente.";
    public static final String MSG_SMTP_INDISPONIVEL =
            "O servidor de email não respondeu. Nova tentativa automática.";
    public static final String MSG_SMTP_AUTENTICACAO =
            "O servidor de email recusou as credenciais configuradas. Nova tentativa automática.";
    public static final String MSG_REMETENTE_INVALIDO =
            "O remetente configurado para o email não é válido. Nova tentativa automática.";
    public static final String MSG_FALHA_ENVIO = "Não foi possível enviar o email. Nova tentativa automática.";

    private final EmailProperties.Smtp smtp;
    private final JavaMailSenderImpl sender;

    public SmtpEntregaEmailGateway(EmailProperties.Smtp smtp) {
        if (smtp == null || !smtp.configurado()) {
            throw new IllegalArgumentException("SMTP não configurado");
        }
        this.smtp = smtp;
        this.sender = construirSender(smtp);
    }

    private static JavaMailSenderImpl construirSender(EmailProperties.Smtp smtp) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(smtp.host().strip());
        s.setPort(smtp.port());
        s.setProtocol("smtp");
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean auth = smtp.comAutenticacao();
        if (auth) {
            s.setUsername(smtp.username());
            s.setPassword(smtp.password());
        }
        Properties p = new Properties();
        p.setProperty("mail.smtp.auth", Boolean.toString(auth));
        p.setProperty("mail.smtp.starttls.enable", Boolean.toString(smtp.starttls()));
        p.setProperty("mail.smtp.starttls.required", Boolean.toString(smtp.starttls()));
        p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        p.setProperty("mail.smtp.connectiontimeout", Long.toString(smtp.ligacaoTimeout().toMillis()));
        p.setProperty("mail.smtp.timeout", Long.toString(smtp.leituraTimeout().toMillis()));
        p.setProperty("mail.smtp.writetimeout", Long.toString(smtp.leituraTimeout().toMillis()));
        s.setJavaMailProperties(p);
        return s;
    }

    @Override
    public boolean configurado() {
        return true;
    }

    @Override
    public ResultadoEnvioEmail enviar(MensagemEmailFiscal mensagem) {
        Optional<InternetAddress> para = destinatario(mensagem.destinatario());
        if (para.isEmpty()) {
            return new ResultadoEnvioEmail.ErroPermanente(DESTINATARIO_INVALIDO, MSG_DESTINATARIO_INVALIDO);
        }
        InternetAddress de;
        try {
            de = estrito(smtp.from());
        } catch (AddressException e) {
            log.warn("Remetente SMTP configurado inválido ({})", e.getClass().getSimpleName());
            return new ResultadoEnvioEmail.ErroTransitorio(REMETENTE_INVALIDO, MSG_REMETENTE_INVALIDO);
        }
        Optional<InternetAddress> responder = mensagem.replyTo().flatMap(SmtpEntregaEmailGateway::replyTo);

        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setTo(para.get());
            h.setFrom(de);
            if (responder.isPresent()) {
                h.setReplyTo(responder.get());
            }
            h.setSubject(mensagem.assunto());
            h.setText(mensagem.textoSimples(), mensagem.html());
            for (MensagemEmailFiscal.Anexo a : mensagem.anexos()) {
                h.addAttachment(a.nome(), new ByteArrayResource(a.conteudo()), a.contentType());
            }
            sender.send(m);
            return new ResultadoEnvioEmail.Enviado();
        } catch (MailAuthenticationException e) {
            log.warn("Envio de email falhou: autenticação SMTP recusada ({})", e.getClass().getSimpleName());
            return new ResultadoEnvioEmail.ErroTransitorio(SMTP_AUTENTICACAO, MSG_SMTP_AUTENTICACAO);
        } catch (Exception e) {
            return classificar(e);
        }
    }

    private ResultadoEnvioEmail classificar(Exception e) {
        Integer codigoRcpt = null;
        boolean ligacao = false;
        for (Throwable t : cadeia(e)) {
            if (t instanceof SMTPAddressFailedException f && codigoRcpt == null) {
                codigoRcpt = f.getReturnCode();
            }
            if (t instanceof MailConnectException || t instanceof ConnectException
                    || t instanceof SocketTimeoutException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException) {
                ligacao = true;
            }
        }
        if (codigoRcpt != null && codigoRcpt >= 500 && codigoRcpt < 600) {
            log.warn("Envio de email falhou: destinatário recusado pelo servidor ({}, {})",
                    e.getClass().getSimpleName(), codigoRcpt);
            return new ResultadoEnvioEmail.ErroPermanente(DESTINATARIO_RECUSADO, MSG_DESTINATARIO_RECUSADO);
        }
        if ((codigoRcpt != null && codigoRcpt >= 400 && codigoRcpt < 500) || ligacao) {
            log.warn("Envio de email falhou: servidor indisponível ({}{})", e.getClass().getSimpleName(),
                    codigoRcpt == null ? "" : ", " + codigoRcpt);
            return new ResultadoEnvioEmail.ErroTransitorio(SMTP_INDISPONIVEL, MSG_SMTP_INDISPONIVEL);
        }
        log.warn("Envio de email falhou ({})", e.getClass().getSimpleName());
        return new ResultadoEnvioEmail.ErroTransitorio(FALHA_ENVIO, MSG_FALHA_ENVIO);
    }

    /**
     * Todas as causas: getCause, MessagingException#getNextException (por onde chegam as
     * SMTPAddressFailedException de uma SendFailedException) e as de MailSendException.
     */
    private static Set<Throwable> cadeia(Throwable raiz) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pendentes = new ArrayDeque<>();
        pendentes.push(raiz);
        while (!pendentes.isEmpty() && vistos.size() < 64) {
            Throwable t = pendentes.pop();
            if (!vistos.add(t)) {
                continue;
            }
            if (t.getCause() != null) {
                pendentes.push(t.getCause());
            }
            if (t instanceof MessagingException me && me.getNextException() != null) {
                pendentes.push(me.getNextException());
            }
            if (t instanceof MailSendException mse) {
                for (Exception x : mse.getMessageExceptions()) {
                    pendentes.push(x);
                }
            }
        }
        return vistos;
    }

    /** Um único endereço, sem nome, que passa as regras partilhadas e o parse estrito. */
    private static Optional<InternetAddress> destinatario(String bruto) {
        Optional<String> valido = RegrasEntregaEmail.emailValido(bruto);
        if (valido.isEmpty()) {
            return Optional.empty();
        }
        try {
            InternetAddress a = estrito(valido.get());
            if (a.getPersonal() != null || !valido.get().equals(a.getAddress())) {
                return Optional.empty();
            }
            return Optional.of(a);
        } catch (AddressException e) {
            return Optional.empty();
        }
    }

    private static Optional<InternetAddress> replyTo(String bruto) {
        Optional<InternetAddress> a = destinatario(bruto);
        if (a.isEmpty()) {
            log.warn("Reply-To do escritório inválido; enviado sem Reply-To");
        }
        return a;
    }

    private static InternetAddress estrito(String endereco) throws AddressException {
        if (endereco == null || endereco.isBlank() || endereco.indexOf('\r') >= 0 || endereco.indexOf('\n') >= 0) {
            throw new AddressException("endereço vazio ou com quebra de linha");
        }
        InternetAddress a = new InternetAddress(endereco.strip(), true);
        a.validate();
        return a;
    }

    @Override
    public String toString() {
        return "SmtpEntregaEmailGateway[" + smtp + "]";
    }
}
