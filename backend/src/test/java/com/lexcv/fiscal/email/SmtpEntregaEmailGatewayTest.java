package com.lexcv.fiscal.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 137 (ENTR-03; T-137-28..32): o adaptador SMTP contra servidores locais -- GreenMail em
 * processo (137-SPIKE.md D) e um servidor SMTP mínimo com respostas fixas para as recusas 550/450.
 * Nunca um SMTP real.
 */
class SmtpEntregaEmailGatewayTest {

    private static final String DESTINATARIO = "cliente.teste@exemplo.cv";
    private static final String REMETENTE = "faturacao@lexcv.exemplo.cv";
    private static final String PASSWORD = "segredo-muito-secreto";
    private static final String ASSUNTO = "Fatura-Recibo FR SIM-FR-2026/000123 — SIMULAÇÃO — SEM VALIDADE FISCAL";
    private static final byte[] PDF = "%PDF-1.7 conteúdo".getBytes(StandardCharsets.UTF_8);
    private static final byte[] XML = "<Dfe>conteúdo</Dfe>".getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @RegisterExtension
    static GreenMailExtension greenMailComAuth = new GreenMailExtension(
            new ServerSetup(3026, "127.0.0.1", ServerSetup.PROTOCOL_SMTP))
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("lexcv", "certa"));

    private ListAppender<ILoggingEvent> registos;
    private Logger logger;

    @BeforeEach
    void capturarLogs() {
        logger = (Logger) LoggerFactory.getLogger(SmtpEntregaEmailGateway.class);
        registos = new ListAppender<>();
        registos.start();
        logger.addAppender(registos);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(registos);
        // Nenhum registo do adaptador expõe o destinatário, a palavra-passe ou texto de exceção.
        for (ILoggingEvent e : registos.list) {
            String texto = e.getFormattedMessage();
            assertThat(texto).doesNotContain(DESTINATARIO, PASSWORD, "5.1.1", "4.2.0", "Connection refused",
                    "utilizador desconhecido", "temporariamente", "535");
            assertThat(e.getThrowableProxy()).as("sem stack trace no log").isNull();
        }
    }

    private static EmailProperties.Smtp smtp(int porta, String username, String password, Duration timeout) {
        return new EmailProperties.Smtp("127.0.0.1", porta, username, password, REMETENTE, false, timeout, timeout);
    }

    private static MensagemEmailFiscal mensagem(String destinatario, Optional<String> replyTo) {
        return new MensagemEmailFiscal(destinatario, replyTo, ASSUNTO,
                "Segue em anexo o documento.\nSIMULAÇÃO — SEM VALIDADE FISCAL",
                "<p style=\"margin:0\">Segue em anexo o documento.</p><p>SIMULAÇÃO — SEM VALIDADE FISCAL</p>",
                List.of(new MensagemEmailFiscal.Anexo("FR-SIM-FR-2026-000123.pdf", "application/pdf", PDF),
                        new MensagemEmailFiscal.Anexo("FR-SIM-FR-2026-000123.xml", "application/xml", XML)));
    }

    private static int portaLivre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    // ---- sucesso ----

    @Test
    void enviaMultipartComDoisAnexosEReplyTo() throws Exception {
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(
                smtp(greenMail.getSmtp().getPort(), null, null, Duration.ofSeconds(5)));
        assertThat(gateway.configurado()).isTrue();

        ResultadoEnvioEmail r = gateway.enviar(mensagem(DESTINATARIO, Optional.of("geral@escritorio.cv")));

        assertThat(r).isEqualTo(new ResultadoEnvioEmail.Enviado());
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] recebidas = greenMail.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        MimeMessage m = recebidas[0];
        assertThat(m.getSubject()).isEqualTo(ASSUNTO);
        assertThat(enderecos(m.getAllRecipients())).containsExactly(DESTINATARIO);
        assertThat(enderecos(m.getFrom())).containsExactly(REMETENTE);
        assertThat(enderecos(m.getReplyTo())).containsExactly("geral@escritorio.cv");

        List<Part> partes = new ArrayList<>();
        recolher(m, partes);
        List<Part> anexos = partes.stream().filter(p -> {
            try {
                return Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        assertThat(anexos).hasSize(2);
        assertThat(anexos.get(0).getFileName()).isEqualTo("FR-SIM-FR-2026-000123.pdf");
        assertThat(anexos.get(0).getContentType()).startsWith("application/pdf");
        assertThat(anexos.get(0).getInputStream().readAllBytes()).isEqualTo(PDF);
        assertThat(anexos.get(1).getFileName()).isEqualTo("FR-SIM-FR-2026-000123.xml");
        assertThat(anexos.get(1).getContentType()).startsWith("application/xml");
        assertThat(anexos.get(1).getInputStream().readAllBytes()).isEqualTo(XML);
        assertThat(partes.stream().anyMatch(p -> tipo(p, "text/plain"))).isTrue();
        assertThat(partes.stream().anyMatch(p -> tipo(p, "text/html"))).isTrue();
        String texto = (String) partes.stream().filter(p -> tipo(p, "text/plain")).findFirst().orElseThrow()
                .getContent();
        assertThat(texto).contains("SIMULAÇÃO — SEM VALIDADE FISCAL");
    }

    @Test
    void semReplyToNaoHaCabecalhoReplyTo() throws Exception {
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(
                smtp(greenMail.getSmtp().getPort(), null, null, Duration.ofSeconds(5)));

        assertThat(gateway.enviar(mensagem(DESTINATARIO, Optional.empty()))).isInstanceOf(ResultadoEnvioEmail.Enviado.class);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage m = greenMail.getReceivedMessages()[0];
        assertThat(m.getHeader("Reply-To")).isNull();
    }

    // ---- recusas ----

    @Test
    void destinatarioRecusadoCom550EPermanente() throws Exception {
        try (ServidorSmtpFixo servidor = new ServidorSmtpFixo("550 5.1.1 utilizador desconhecido")) {
            ResultadoEnvioEmail r = new SmtpEntregaEmailGateway(smtp(servidor.porta(), null, null,
                    Duration.ofSeconds(5))).enviar(mensagem(DESTINATARIO, Optional.empty()));

            assertThat(r).isInstanceOf(ResultadoEnvioEmail.ErroPermanente.class);
            ResultadoEnvioEmail.ErroPermanente p = (ResultadoEnvioEmail.ErroPermanente) r;
            assertThat(p.codigo()).isEqualTo("DESTINATARIO_RECUSADO");
            assertThat(p.mensagem()).isEqualTo(SmtpEntregaEmailGateway.MSG_DESTINATARIO_RECUSADO);
            assertThat(servidor.mensagensAceites()).isZero();
        }
    }

    @Test
    void destinatarioRecusadoCom450ETransitorio() throws Exception {
        try (ServidorSmtpFixo servidor = new ServidorSmtpFixo("450 4.2.0 caixa temporariamente indisponível")) {
            ResultadoEnvioEmail r = new SmtpEntregaEmailGateway(smtp(servidor.porta(), null, null,
                    Duration.ofSeconds(5))).enviar(mensagem(DESTINATARIO, Optional.empty()));

            assertThat(r).isEqualTo(new ResultadoEnvioEmail.ErroTransitorio("SMTP_INDISPONIVEL",
                    SmtpEntregaEmailGateway.MSG_SMTP_INDISPONIVEL));
        }
    }

    @Test
    void semNadaAEscutarETransitorioDentroDoTimeout() throws Exception {
        int porta = portaLivre();
        long inicio = System.nanoTime();

        ResultadoEnvioEmail r = new SmtpEntregaEmailGateway(smtp(porta, null, null, Duration.ofSeconds(2)))
                .enviar(mensagem(DESTINATARIO, Optional.empty()));

        assertThat(r).isEqualTo(new ResultadoEnvioEmail.ErroTransitorio("SMTP_INDISPONIVEL",
                SmtpEntregaEmailGateway.MSG_SMTP_INDISPONIVEL));
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void credenciaisErradasSaoTransitorias() {
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(
                smtp(greenMailComAuth.getSmtp().getPort(), "lexcv", PASSWORD, Duration.ofSeconds(5)));

        ResultadoEnvioEmail r = gateway.enviar(mensagem(DESTINATARIO, Optional.empty()));

        assertThat(r).isEqualTo(new ResultadoEnvioEmail.ErroTransitorio("SMTP_AUTENTICACAO",
                SmtpEntregaEmailGateway.MSG_SMTP_AUTENTICACAO));
        assertThat(greenMailComAuth.getReceivedMessages()).isEmpty();
    }

    @Test
    void credenciaisCertasEnviam() {
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(
                smtp(greenMailComAuth.getSmtp().getPort(), "lexcv", "certa", Duration.ofSeconds(5)));

        assertThat(gateway.enviar(mensagem(DESTINATARIO, Optional.empty())))
                .isInstanceOf(ResultadoEnvioEmail.Enviado.class);
        assertThat(greenMailComAuth.waitForIncomingEmail(5000, 1)).isTrue();
    }

    @Test
    void destinatarioInvalidoEPermanenteSemAbrirLigacao() throws Exception {
        try (ServidorSmtpFixo servidor = new ServidorSmtpFixo("250 OK")) {
            SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(smtp(servidor.porta(), null, null,
                    Duration.ofSeconds(5)));
            for (String invalido : List.of("x@y.cv\r\nBcc: z@w.cv", "a@b@c.cv", "Ana <ana@exemplo.cv>",
                    "ana@exemplo.cv, b@exemplo.cv", "sem-arroba")) {
                ResultadoEnvioEmail r = gateway.enviar(mensagem(invalido, Optional.empty()));
                assertThat(r).as(invalido).isEqualTo(new ResultadoEnvioEmail.ErroPermanente("DESTINATARIO_INVALIDO",
                        SmtpEntregaEmailGateway.MSG_DESTINATARIO_INVALIDO));
            }
            assertThat(servidor.ligacoes()).as("nenhuma ligação aberta").isZero();
        }
    }

    @Test
    void replyToInvalidoEOmitidoEOEnvioSegue() throws Exception {
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(
                smtp(greenMail.getSmtp().getPort(), null, null, Duration.ofSeconds(5)));

        assertThat(gateway.enviar(mensagem(DESTINATARIO, Optional.of("x@y.cv\r\nBcc: z@w.cv"))))
                .isInstanceOf(ResultadoEnvioEmail.Enviado.class);
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage m = greenMail.getReceivedMessages()[0];
        assertThat(m.getHeader("Reply-To")).isNull();
        assertThat(m.getHeader("Bcc")).isNull();
        assertThat(enderecos(m.getAllRecipients())).containsExactly(DESTINATARIO);
    }

    @Test
    void credenciaisNuncaAparecemNoToString() {
        EmailProperties.Smtp s = smtp(587, "utilizador.secreto", PASSWORD, Duration.ofSeconds(5));
        assertThat(s.toString()).doesNotContain(PASSWORD, "utilizador.secreto").contains("password=***");
        SmtpEntregaEmailGateway gateway = new SmtpEntregaEmailGateway(s);
        assertThat(gateway.toString()).doesNotContain(PASSWORD, "utilizador.secreto");
        assertThat(mensagem(DESTINATARIO, Optional.empty()).toString()).doesNotContain(DESTINATARIO);
    }

    // ---- apoio ----

    private static List<String> enderecos(Address[] enderecos) {
        List<String> r = new ArrayList<>();
        if (enderecos != null) {
            for (Address a : enderecos) {
                r.add(((InternetAddress) a).getAddress());
            }
        }
        return r;
    }

    private static void recolher(Part p, List<Part> partes) throws Exception {
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                recolher(bp, partes);
            }
        } else {
            partes.add(p);
        }
    }

    private static boolean tipo(Part p, String mime) {
        try {
            return p.isMimeType(mime) && !Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Servidor SMTP mínimo: responde 250 a tudo exceto ao {@code RCPT TO}, que recebe a resposta
     * fixa dada. Conta ligações e mensagens aceites ({@code DATA} completo).
     */
    static final class ServidorSmtpFixo implements AutoCloseable {
        private final ServerSocket socket;
        private final String respostaRcpt;
        private final AtomicInteger ligacoes = new AtomicInteger();
        private final AtomicInteger aceites = new AtomicInteger();
        private final AtomicBoolean ativo = new AtomicBoolean(true);
        private final Thread thread;

        ServidorSmtpFixo(String respostaRcpt) throws IOException {
            this.socket = new ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress());
            this.respostaRcpt = respostaRcpt;
            this.thread = new Thread(this::servir, "smtp-fixo");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int porta() {
            return socket.getLocalPort();
        }

        int ligacoes() {
            return ligacoes.get();
        }

        int mensagensAceites() {
            return aceites.get();
        }

        private void servir() {
            while (ativo.get()) {
                try (Socket s = socket.accept()) {
                    ligacoes.incrementAndGet();
                    s.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(),
                            StandardCharsets.US_ASCII));
                    OutputStream out = s.getOutputStream();
                    escrever(out, "220 fixo ESMTP");
                    String linha;
                    while ((linha = in.readLine()) != null) {
                        String cmd = linha.toUpperCase(java.util.Locale.ROOT);
                        if (cmd.startsWith("EHLO")) {
                            escrever(out, "250-fixo\r\n250 8BITMIME");
                        } else if (cmd.startsWith("RCPT")) {
                            escrever(out, respostaRcpt);
                        } else if (cmd.startsWith("DATA")) {
                            escrever(out, "354 envie");
                            String d;
                            while ((d = in.readLine()) != null && !d.equals(".")) {
                                // consome o corpo
                            }
                            aceites.incrementAndGet();
                            escrever(out, "250 aceite");
                        } else if (cmd.startsWith("QUIT")) {
                            escrever(out, "221 adeus");
                            break;
                        } else {
                            escrever(out, "250 OK");
                        }
                    }
                } catch (IOException e) {
                    if (!ativo.get()) {
                        return;
                    }
                }
            }
        }

        private static void escrever(OutputStream out, String linha) throws IOException {
            out.write((linha + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        @Override
        public void close() throws IOException {
            ativo.set(false);
            socket.close();
        }
    }
}
