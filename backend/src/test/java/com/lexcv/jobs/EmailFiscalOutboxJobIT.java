package com.lexcv.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.EfaturaConfig;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.email.EntregaEmailGateway;
import com.lexcv.fiscal.email.MensagemEmailFiscal;
import com.lexcv.fiscal.email.NaoConfiguradoEntregaEmailGateway;
import com.lexcv.fiscal.email.ResultadoEnvioEmail;
import com.lexcv.fiscal.email.SmtpEntregaEmailGateway;
import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.User;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import com.lexcv.repositories.FilaEntregaEmail;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.StorageService;
import com.lexcv.services.fiscal.AuditoriaFiscalService;
import com.lexcv.services.fiscal.ComposicaoEmailFiscal;
import com.lexcv.services.fiscal.ComunicacaoFiscalTransacoes;
import com.lexcv.services.fiscal.EnfileiramentoEntregaEmail;
import com.lexcv.services.fiscal.EntregaEmailTransacoes;
import com.lexcv.services.fiscal.FixturaEmissaoFiscal;
import com.lexcv.services.fiscal.NotaCreditoService;
import com.lexcv.services.fiscal.NotificacaoComunicacaoFiscal;
import com.lexcv.services.fiscal.NotificacaoEntregaEmailFiscal;
import com.lexcv.services.fiscal.NumeracaoService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import com.lexcv.services.fiscal.ParametroFiscalService;
import com.lexcv.services.fiscal.PdfDocumentoFiscalService;
import com.lexcv.services.fiscal.PdfDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import com.lexcv.services.fiscal.ProcessadorComunicacaoFiscal;
import com.lexcv.services.fiscal.ProcessadorEntregaEmail;
import com.lexcv.services.fiscal.ReenvioEmailFiscalService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Phase 137 (ENTR-03, ENTR-05, ENTR-06; T-137-81..85): a cadeia de entrega por email de ponta a
 * ponta em PostgreSQL real com um servidor de correio local (GreenMail em processo, 137-SPIKE.md D)
 * -- nunca um SMTP real.
 *
 * <p>Pagamento -> (nada de email) -> job da comunicação aceita -> linha de entrega PENDENTE -> job do
 * email -> mensagem com o PDF e o XML. Também: SMTP não configurado (nada reclamado, nenhuma tentativa
 * gasta), envio desligado, cliente sem email, cinco falhas transitórias até FALHOU com uma notificação
 * por destinatário e por episódio, reenvio manual (novo episódio) e duas execuções em paralelo.
 *
 * <p>Andaime de {@link FiscalOutboxJobIT}: os jobs são chamados com {@code executarUmaVez()} e o
 * relógio avança por cima dos atrasos do backoff em vez de dormir. O MinIO não corre em contentor:
 * {@link StorageService} é um duplo em memória. O gateway de email é comutável entre o adaptador SMTP
 * real (GreenMail, ou uma porta fechada) e o adaptador "não configurado".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({EmailFiscalOutboxJob.class, FiscalOutboxJob.class, ProcessadorEntregaEmail.class,
        EntregaEmailTransacoes.class, FilaEntregaEmail.class, EnfileiramentoEntregaEmail.class,
        ComposicaoEmailFiscal.class, PdfDocumentoFiscalService.class, PdfDocumentoFiscalTransacoes.class,
        PdfDocumentoFiscalRenderer.class, NotificacaoEntregaEmailFiscal.class, ReenvioEmailFiscalService.class,
        ProcessadorComunicacaoFiscal.class, ComunicacaoFiscalTransacoes.class, FilaComunicacaoFiscal.class,
        DfeXmlBuilder.class, DfeMarshaller.class, DfeValidador.class, IudGerador.class, EfaturaConfig.class,
        NotificacaoComunicacaoFiscal.class, NotificacaoService.class, PagamentoFaturadoService.class,
        NotaCreditoService.class, PreVisualizacaoFaturaService.class, NumeracaoService.class,
        ParametroFiscalService.class, AuditoriaFiscalService.class, EmailFiscalOutboxJobIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailFiscalOutboxJobIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final int PORTA_SMTP = 3225;
    static final String REMETENTE = "faturacao@lexcv.exemplo.cv";
    static final String MARCA = "[SIMULAÇÃO — SEM VALIDADE FISCAL] ";
    static final String CATEGORIA = NotificacaoEntregaEmailFiscal.CATEGORIA;
    static final Instant AGORA = Instant.parse("2026-09-15T13:00:00Z");
    static final RelogioMovel RELOGIO = new RelogioMovel(AGORA);
    static final BigDecimal VALOR = new BigDecimal("120000.00");
    static final GatewayComutavel GATEWAY = new GatewayComutavel();

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(
            new ServerSetup(PORTA_SMTP, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

    /** Relógio que avança quando o teste manda (por cima do backoff e do lease). */
    static final class RelogioMovel extends Clock {
        private final AtomicReference<Instant> agora;

        RelogioMovel(Instant inicio) {
            this.agora = new AtomicReference<>(inicio);
        }

        void definir(Instant instante) {
            agora.set(instante);
        }

        void avancar(Duration d) {
            agora.updateAndGet(i -> i.plus(d));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(agora.get(), zone);
        }

        @Override
        public Instant instant() {
            return agora.get();
        }
    }

    /** O gateway de email do contexto: delega no adaptador escolhido pelo teste. */
    static final class GatewayComutavel implements EntregaEmailGateway {
        private final AtomicReference<EntregaEmailGateway> alvo = new AtomicReference<>();

        void usar(EntregaEmailGateway g) {
            alvo.set(g);
        }

        @Override
        public boolean configurado() {
            return alvo.get().configurado();
        }

        @Override
        public ResultadoEnvioEmail enviar(MensagemEmailFiscal mensagem) {
            return alvo.get().enviar(mensagem);
        }
    }

    static EmailProperties.Smtp smtp(int porta) {
        return new EmailProperties.Smtp("127.0.0.1", porta, null, null, REMETENTE, false, Duration.ofSeconds(5),
                Duration.ofSeconds(5));
    }

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return RELOGIO;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EmailProperties emailProperties() {
            return new EmailProperties(smtp(PORTA_SMTP),
                    new EmailProperties.Outbox(Duration.ofSeconds(30), Duration.ofSeconds(40), 10,
                            Duration.ofMinutes(2)));
        }

        @Bean
        EntregaEmailGateway entregaEmailGateway() {
            return GATEWAY;
        }
    }

    @Autowired
    private EmailFiscalOutboxJob emailJob;

    @Autowired
    private FiscalOutboxJob comunicacaoJob;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private NotaCreditoService notaCredito;

    @Autowired
    private ReenvioEmailFiscalService reenvio;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ResolucaoPapeisService resolucaoPapeis;

    /** MinIO não corre em contentor: duplo em memória por trás de uploadBytes / lerBytes / delete. */
    @MockitoBean
    private StorageService storage;

    private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> permissoes = new ConcurrentHashMap<>();
    private FixturaEmissaoFiscal fixtura;

    @BeforeEach
    void preparar() {
        RELOGIO.definir(AGORA);
        GATEWAY.usar(new SmtpEntregaEmailGateway(smtp(PORTA_SMTP)));
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
        // Reclamações multi-tenant: cada teste começa com as duas filas vazias.
        jdbc.update("DELETE FROM t_entrega_email_fiscal");
        jdbc.update("DELETE FROM t_comunicacao_fiscal");
        objetos.clear();
        doAnswer(inv -> {
            objetos.put(inv.getArgument(0), ((byte[]) inv.getArgument(1)).clone());
            return null;
        }).when(storage).uploadBytes(anyString(), any(byte[].class), anyString());
        when(storage.lerBytes(anyString())).thenAnswer(inv -> {
            byte[] b = objetos.get(inv.<String>getArgument(0));
            if (b == null) {
                throw new IllegalStateException("objeto inexistente");
            }
            return b.clone();
        });
        doAnswer(inv -> objetos.remove(inv.<String>getArgument(0))).when(storage).delete(anyString());
        when(resolucaoPapeis.resolverNomesPapeis(any())).thenReturn(Set.of());
        when(resolucaoPapeis.resolverPermissoesEfectivas(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return permissoes.getOrDefault(u.getId(), Set.of());
        });
    }

    // ------------------------------------------------------------------ apoio

    private record Fr(UUID tenantId, UUID id, String numero, UUID clienteId) {
    }

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    /** Escritório com faturação ativa e o envio automático ligado ou não. */
    private UUID escritorio(boolean envioAutomatico) {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        jdbc.update("UPDATE t_configuracao_fiscal SET envio_email_automatico = ? WHERE tenant_id = ?",
                envioAutomatico, tenant);
        return tenant;
    }

    private Fr emitirFr(UUID tenant, String emailCliente) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        if (emailCliente != null) {
            jdbc.update("UPDATE t_cliente SET email = ? WHERE id = ?", emailCliente, cliente);
        }
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-2026/" + UUID.randomUUID().toString().substring(0, 6));
        Integer honorario = fixtura.criarHonorario(processo, VALOR, "Defesa no caso X");
        var r = pagamentoFaturado.registar(tenant, autor(tenant),
                new PagamentoRequest(honorario, VALOR, null, "TRANSFERENCIA", new BigDecimal("20"), UUID.randomUUID()));
        return new Fr(tenant, r.resposta().documentoFiscal().id(), r.resposta().documentoFiscal().numeroFormatado(),
                cliente);
    }

    private UUID emitirNc(Fr fr) {
        RELOGIO.avancar(Duration.ofSeconds(1));
        return notaCredito.emitir(fr.tenantId(), autor(fr.tenantId()), fr.id(),
                new NotaCreditoRequest("PARCIAL", new BigDecimal("20000.00"), "CORRECAO_VALOR",
                        "Desconto acordado com o cliente", UUID.randomUUID())).resposta().id();
    }

    private UUID utilizador(UUID tenant, String... perms) {
        UUID id = fixtura.criarUtilizador(tenant, UUID.randomUUID() + "@example.cv", true);
        permissoes.put(id, Set.of(perms));
        return id;
    }

    private Map<String, Object> entrega(UUID documentoId) {
        return jdbc.queryForMap("SELECT * FROM t_entrega_email_fiscal WHERE documento_fiscal_id = ?", documentoId);
    }

    private int contarEntregas(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM t_entrega_email_fiscal WHERE tenant_id = ?", Integer.class,
                tenant);
    }

    private int notificacoes(UUID destinatario, String entidadeId) {
        return jdbc.queryForObject("SELECT count(*) FROM t_notificacao WHERE destinatario_id = ? AND categoria = ? "
                + "AND entidade_id = ?", Integer.class, destinatario, CATEGORIA, entidadeId);
    }

    private int notificacoesDoTenant(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM t_notificacao WHERE tenant_id = ? AND categoria = ?",
                Integer.class, tenant, CATEGORIA);
    }

    /** Uma passagem do email com o relógio já por cima de qualquer backoff e lease. */
    private int passagemEmail() {
        RELOGIO.avancar(Duration.ofHours(2));
        return emailJob.executarUmaVez();
    }

    private static int portaFechada() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Anexos (nome -> bytes) e corpos de texto de uma mensagem recebida. */
    private record Partes(Map<String, byte[]> anexos, List<String> textos) {
    }

    private static Partes partes(MimeMessage m) throws Exception {
        Map<String, byte[]> anexos = new ConcurrentHashMap<>();
        List<String> textos = new ArrayList<>();
        recolher(m, anexos, textos);
        return new Partes(anexos, textos);
    }

    private static void recolher(Part p, Map<String, byte[]> anexos, List<String> textos) throws Exception {
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                recolher(bp, anexos, textos);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition()) || p.getFileName() != null) {
            try (InputStream in = p.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                anexos.put(p.getFileName(), out.toByteArray());
            }
        } else if (p.isMimeType("text/*")) {
            textos.add(String.valueOf(p.getContent()));
        }
    }

    // ------------------------------------------------------------------ testes

    @Test
    void frPagaChegaAoClienteComPdfEXmlSoDepoisDoAceite() throws Exception {
        UUID tenant = escritorio(true);
        Fr fr = emitirFr(tenant, "maria.lopes@exemplo.cv");

        // T-137-85: o pagamento não criou nenhuma entrega nem fez trabalho de email.
        assertThat(contarEntregas(tenant)).isZero();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
        assertThat(emailJob.executarUmaVez()).isZero();

        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);
        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("ACEITE_SIMULADO");
        Map<String, Object> pendente = entrega(fr.id());
        assertThat(pendente.get("estado")).isEqualTo("PENDENTE");
        assertThat(pendente.get("destinatario")).isEqualTo("maria.lopes@exemplo.cv");
        assertThat(greenMail.getReceivedMessages()).isEmpty();

        assertThat(emailJob.executarUmaVez()).isEqualTo(1);

        MimeMessage[] recebidas = greenMail.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        MimeMessage m = recebidas[0];
        assertThat(m.getAllRecipients()).extracting(Object::toString).containsExactly("maria.lopes@exemplo.cv");
        assertThat(m.getSubject()).startsWith(MARCA + "Fatura-Recibo");
        assertThat(m.getSubject()).contains(fr.numero());
        Partes p = partes(m);
        String nome = fr.numero().replace('/', '-').replace(' ', '-');
        assertThat(p.anexos()).containsOnlyKeys(nome + ".pdf", nome + ".xml");
        assertThat(new String(Arrays.copyOf(p.anexos().get(nome + ".pdf"), 4), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        String xml = jdbc.queryForObject("SELECT xml FROM t_documento_fiscal_xml WHERE documento_fiscal_id = ?",
                String.class, fr.id());
        assertThat(new String(p.anexos().get(nome + ".xml"), StandardCharsets.UTF_8)).isEqualTo(xml);

        Map<String, Object> enviada = entrega(fr.id());
        assertThat(enviada.get("estado")).isEqualTo("ENVIADO");
        assertThat(enviada.get("tentativas")).isEqualTo(1);
        assertThat(enviada.get("enviado_em")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_documento_fiscal_pdf WHERE documento_fiscal_id = ?",
                Integer.class, fr.id())).isEqualTo(1);

    }

    @Test
    void ncSobreAFrSegueOMesmoCaminho() throws Exception {
        UUID tenant = escritorio(true);
        Fr fr = emitirFr(tenant, "maria.lopes@exemplo.cv");
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);
        assertThat(passagemEmail()).isEqualTo(1);
        assertThat(greenMail.getReceivedMessages()).hasSize(1);

        UUID nc = emitirNc(fr);
        // A emissão da NC também não cria trabalho de email.
        assertThat(contarEntregas(tenant)).isEqualTo(1);
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);
        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ACEITE_SIMULADO");
        assertThat(entrega(nc).get("estado")).isEqualTo("PENDENTE");
        assertThat(passagemEmail()).isEqualTo(1);

        MimeMessage[] recebidas = greenMail.getReceivedMessages();
        assertThat(recebidas).hasSize(2);
        String numeroNc = jdbc.queryForObject("SELECT numero_formatado FROM t_documento_fiscal WHERE id = ?",
                String.class, nc);
        assertThat(recebidas[1].getSubject()).startsWith(MARCA + "Nota de Crédito " + numeroNc);
        Partes p = partes(recebidas[1]);
        String nome = numeroNc.replace('/', '-').replace(' ', '-');
        assertThat(p.anexos()).containsOnlyKeys(nome + ".pdf", nome + ".xml");
        assertThat(String.join("\n", p.textos())).contains("corrige a Fatura-Recibo " + fr.numero());
        Map<String, Object> e = entrega(nc);
        assertThat(e.get("estado")).isEqualTo("ENVIADO");
        assertThat(e.get("tentativas")).isEqualTo(1);
    }

    @Test
    void semSmtpNadaEReclamadoENenhumaTentativaEGastaDepoisEnviaAoConfigurar() {
        GATEWAY.usar(new NaoConfiguradoEntregaEmailGateway());
        UUID tenant = escritorio(true);
        Fr fr = emitirFr(tenant, "maria.lopes@exemplo.cv");

        // A emissão e a comunicação correm normalmente sem SMTP.
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);
        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("ACEITE_SIMULADO");
        for (int i = 0; i < 3; i++) {
            assertThat(passagemEmail()).isZero();
        }
        Map<String, Object> e = entrega(fr.id());
        assertThat(e.get("estado")).isEqualTo("PENDENTE");
        assertThat(e.get("tentativas")).isEqualTo(0);
        assertThat(greenMail.getReceivedMessages()).isEmpty();

        GATEWAY.usar(new SmtpEntregaEmailGateway(smtp(PORTA_SMTP)));
        assertThat(passagemEmail()).isEqualTo(1);
        assertThat(entrega(fr.id()).get("estado")).isEqualTo("ENVIADO");
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
    }

    @Test
    void envioDesligadoFicaDesligadoEClienteSemEmailFicaSemEmailNuncaEnviados() {
        UUID desligado = escritorio(false);
        Fr frDesligado = emitirFr(desligado, "maria.lopes@exemplo.cv");
        UUID ligado = escritorio(true);
        Fr frSemEmail = emitirFr(ligado, null);

        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(2);
        for (int i = 0; i < 3; i++) {
            assertThat(passagemEmail()).isZero();
        }

        Map<String, Object> d = entrega(frDesligado.id());
        assertThat(d.get("estado")).isEqualTo("DESLIGADO");
        assertThat(d.get("tentativas")).isEqualTo(0);
        Map<String, Object> s = entrega(frSemEmail.id());
        assertThat(s.get("estado")).isEqualTo("SEM_EMAIL");
        assertThat(s.get("tentativas")).isEqualTo(0);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void cincoFalhasTransitoriasDaoFalhouUmaNotificacaoPorDestinatarioEReenvioAbreNovoEpisodio() throws Exception {
        UUID tenant = escritorio(true);
        UUID gestor = utilizador(tenant, "financeiro:view", "financeiro:edit", "financeiro:manage");
        UUID editor = utilizador(tenant, "financeiro:view", "financeiro:edit");
        UUID leitor = utilizador(tenant, "financeiro:view");
        Fr fr = emitirFr(tenant, "maria.lopes@exemplo.cv");
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);

        GATEWAY.usar(new SmtpEntregaEmailGateway(smtp(portaFechada())));
        for (int i = 1; i <= 4; i++) {
            assertThat(passagemEmail()).isEqualTo(1);
            Map<String, Object> e = entrega(fr.id());
            assertThat(e.get("estado")).as("tentativa " + i).isEqualTo("PENDENTE");
            assertThat(e.get("tentativas")).isEqualTo(i);
            assertThat(notificacoesDoTenant(tenant)).isZero();
        }
        assertThat(passagemEmail()).isEqualTo(1);

        Map<String, Object> falhou = entrega(fr.id());
        assertThat(falhou.get("estado")).isEqualTo("FALHOU");
        assertThat(falhou.get("tentativas")).isEqualTo(5);
        assertThat(falhou.get("ultimo_erro_codigo")).isEqualTo(SmtpEntregaEmailGateway.SMTP_INDISPONIVEL);
        String episodio0 = fr.id() + ":0";
        assertThat(notificacoes(gestor, episodio0)).isEqualTo(1);
        assertThat(notificacoes(editor, episodio0)).isEqualTo(1);
        assertThat(notificacoes(leitor, episodio0)).isZero();
        assertThat(notificacoesDoTenant(tenant)).isEqualTo(2);

        // Mais execuções não criam nada.
        for (int i = 0; i < 3; i++) {
            assertThat(passagemEmail()).isZero();
        }
        assertThat(notificacoesDoTenant(tenant)).isEqualTo(2);
        assertThat(entrega(fr.id()).get("tentativas")).isEqualTo(5);

        // Reenvio manual: novo episódio; se voltar a esgotar, notifica outra vez.
        var r = reenvio.reenviar(tenant, autor(tenant), fr.id());
        assertThat(r.estado()).isEqualTo("PENDENTE");
        assertThat(entrega(fr.id()).get("reenvios")).isEqualTo(1);
        for (int i = 0; i < 5; i++) {
            assertThat(passagemEmail()).isEqualTo(1);
        }
        assertThat(entrega(fr.id()).get("estado")).isEqualTo("FALHOU");
        String episodio1 = fr.id() + ":1";
        assertThat(notificacoes(gestor, episodio1)).isEqualTo(1);
        assertThat(notificacoes(editor, episodio1)).isEqualTo(1);
        assertThat(notificacoes(leitor, episodio1)).isZero();
        assertThat(notificacoesDoTenant(tenant)).isEqualTo(4);

        // Com o servidor alcançável, o reenvio seguinte envia.
        GATEWAY.usar(new SmtpEntregaEmailGateway(smtp(PORTA_SMTP)));
        reenvio.reenviar(tenant, autor(tenant), fr.id());
        assertThat(entrega(fr.id()).get("reenvios")).isEqualTo(2);
        assertThat(passagemEmail()).isEqualTo(1);
        assertThat(entrega(fr.id()).get("estado")).isEqualTo("ENVIADO");
        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        assertThat(notificacoesDoTenant(tenant)).isEqualTo(4);
    }

    @Test
    void duasExecucoesEmParaleloEnviamCadaEmailUmaVez() throws Exception {
        UUID tenant = escritorio(true);
        for (int i = 0; i < 3; i++) {
            emitirFr(tenant, "cliente" + i + "@exemplo.cv");
        }
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_entrega_email_fiscal WHERE tenant_id = ? "
                + "AND estado = 'PENDENTE'", Integer.class, tenant)).isEqualTo(3);
        RELOGIO.avancar(Duration.ofHours(2));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch partida = new CountDownLatch(1);
        try {
            List<Future<Integer>> execucoes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                execucoes.add(pool.submit(() -> {
                    partida.await();
                    return emailJob.executarUmaVez();
                }));
            }
            partida.countDown();
            int reclamadas = 0;
            for (Future<Integer> f : execucoes) {
                reclamadas += f.get(60, TimeUnit.SECONDS);
            }
            assertThat(reclamadas).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }

        assertThat(greenMail.getReceivedMessages()).hasSize(3);
        assertThat(Arrays.stream(greenMail.getReceivedMessages()).map(m -> {
            try {
                return m.getAllRecipients()[0].toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        })).containsExactlyInAnyOrder("cliente0@exemplo.cv", "cliente1@exemplo.cv", "cliente2@exemplo.cv");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_entrega_email_fiscal WHERE tenant_id = ? "
                + "AND estado = 'ENVIADO' AND tentativas = 1", Integer.class, tenant)).isEqualTo(3);
    }

    @Test
    void semSmtpOJobNaoTocaNasLinhasNemNasEsgotadas() {
        GATEWAY.usar(new NaoConfiguradoEntregaEmailGateway());
        UUID tenant = escritorio(true);
        Fr fr = emitirFr(tenant, "maria.lopes@exemplo.cv");
        assertThat(comunicacaoJob.executarUmaVez()).isEqualTo(1);
        // Uma linha que já gastou todas as reclamações continua intacta enquanto não houver SMTP.
        jdbc.update("UPDATE t_entrega_email_fiscal SET tentativas = 5 WHERE documento_fiscal_id = ?", fr.id());
        Map<String, Object> antes = entrega(fr.id());

        for (int i = 0; i < 3; i++) {
            assertThat(passagemEmail()).isZero();
        }

        Map<String, Object> depois = entrega(fr.id());
        assertThat(depois.get("estado")).isEqualTo("PENDENTE");
        assertThat(depois.get("versao")).isEqualTo(antes.get("versao"));
        assertThat(depois.get("updated_at")).isEqualTo(antes.get("updated_at"));
        assertThat(notificacoesDoTenant(tenant)).isZero();
    }
}
