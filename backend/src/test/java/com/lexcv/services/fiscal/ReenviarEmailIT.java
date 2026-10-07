package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ReenviarEmailResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.EntregaEmailFiscalRepository;
import com.lexcv.repositories.FilaEntregaEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 137 (ENTR-04): o reenvio manual do email em PostgreSQL real -- todas as recusas com o seu
 * código e sem escrita nem auditoria, a reposição (PENDENTE, tentativas a zero, novo episódio, email
 * ATUAL do cliente), o isolamento por tenant, a auditoria na mesma transação sem o endereço, e dois
 * reenvios concorrentes da mesma linha com exatamente um sucesso.
 *
 * <p>Andaime de {@link ReprocessarComunicacaoIT}. O contexto tem o SMTP configurado; o caso "não
 * configurado" usa uma segunda instância do serviço com outro {@link EmailProperties}, dentro de
 * uma {@link TransactionTemplate}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ReenvioEmailFiscalService.class, FilaEntregaEmail.class, AuditoriaFiscalService.class,
        ReenviarEmailIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReenviarEmailIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant T0 = Instant.parse("2026-06-15T13:00:00Z");
    static final Instant AGORA = Instant.parse("2026-06-16T09:30:00Z");

    static EmailProperties email(boolean smtp) {
        return new EmailProperties(
                new EmailProperties.Smtp(smtp ? "smtp.example.cv" : null, 587, null, null,
                        smtp ? "faturacao@example.cv" : null, true, Duration.ofSeconds(10), Duration.ofSeconds(20)),
                new EmailProperties.Outbox(Duration.ofSeconds(30), Duration.ofSeconds(40), 10, Duration.ofMinutes(2)));
    }

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EmailProperties emailProperties() {
            return email(true);
        }
    }

    private static final AtomicLong SEQUENCIA = new AtomicLong(1);
    private static final String ACAO = "documento_fiscal_reenviar_email";

    @Autowired private ReenvioEmailFiscalService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DocumentoFiscalRepository documentoRepository;
    @Autowired private EntregaEmailFiscalRepository entregaRepository;
    @Autowired private ConfiguracaoFiscalRepository configuracaoRepository;
    @Autowired private ComunicacaoFiscalRepository comunicacaoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FilaEntregaEmail fila;
    @Autowired private AuditoriaFiscalService auditoria;

    private final ObjectMapper json = new ObjectMapper();

    private FixturaEmissaoFiscal fixtura;
    private UUID tenantId;
    private UUID clienteId;
    private UserPrincipal autor;

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        tenantId = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        jdbc.update("UPDATE t_configuracao_fiscal SET envio_email_automatico = true WHERE tenant_id = ?", tenantId);
        clienteId = fixtura.criarCliente(tenantId, "234567891", "Maria Fernandes", "Rua 2");
        emailCliente("maria.atual@exemplo.cv");
        autor = UserPrincipal.create(UUID.randomUUID(), tenantId, "Ana Reenvia", "ana@example.cv",
                Set.of(), Set.of("financeiro:edit"), Set.of());
    }

    // ------------------------------------------------------------------ apoio JDBC

    private void emailCliente(String email) {
        jdbc.update("UPDATE t_cliente SET email = ? WHERE id = ?", email, clienteId);
    }

    private UUID documento(UUID tenant, UUID cliente) {
        UUID id = UUID.randomUUID();
        long n = SEQUENCIA.getAndIncrement();
        String serie = "SIM-FR-2026";
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, ?, 2026, ?, ?, ?, ?, '512345679', 'Firma', 'Morada', "
                        + "'NORMAL', '234567891', 'Cliente', 'Morada cliente', ?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', "
                        + "15, 100, 15, 0, 115, 115, ?)",
                id, tenant, UUID.randomUUID(), serie, n, serie + "/" + n,
                java.sql.Date.valueOf("2026-06-15"), Timestamp.from(T0), cliente, UUID.randomUUID(),
                (int) -n, UUID.randomUUID());
        return id;
    }

    private UUID documento() {
        return documento(tenantId, clienteId);
    }

    private void comunicacao(UUID tenant, UUID documentoId, String estado) {
        jdbc.update("INSERT INTO t_comunicacao_fiscal (id, tenant_id, documento_fiscal_id, ambiente, estado, "
                        + "tentativas, created_at, versao, reprocessamentos) "
                        + "VALUES (?, ?, ?, 'SIMULADO', ?, 1, ?, 2, 0)",
                UUID.randomUUID(), tenant, documentoId, estado, Timestamp.from(T0));
    }

    private void entrega(UUID tenant, UUID documentoId, String estado, int tentativas) {
        boolean semEmail = "SEM_EMAIL".equals(estado) || "DESLIGADO".equals(estado);
        jdbc.update("INSERT INTO t_entrega_email_fiscal (id, tenant_id, documento_fiscal_id, estado, destinatario, "
                        + "tentativas, proxima_tentativa_em, lease_ate, ultima_tentativa_em, enviado_em, ultimo_erro, "
                        + "ultimo_erro_codigo, created_at, versao, reenvios) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 3, 0)",
                UUID.randomUUID(), tenant, documentoId, estado, semEmail ? null : "maria.antigo@exemplo.cv",
                tentativas,
                "PENDENTE".equals(estado) ? Timestamp.from(T0) : null,
                null,
                tentativas > 0 ? Timestamp.from(T0) : null,
                "ENVIADO".equals(estado) ? Timestamp.from(T0) : null,
                "FALHOU".equals(estado) ? "O servidor de email não respondeu. Nova tentativa automática." : null,
                "FALHOU".equals(estado) ? "SMTP_INDISPONIVEL" : null,
                Timestamp.from(T0));
    }

    /** Documento ACEITE_SIMULADO com uma linha de entrega no estado indicado. */
    private UUID aceiteCom(String estadoEntrega, int tentativas) {
        UUID doc = documento();
        comunicacao(tenantId, doc, "ACEITE_SIMULADO");
        entrega(tenantId, doc, estadoEntrega, tentativas);
        return doc;
    }

    private Map<String, Object> linha(UUID documentoId) {
        return jdbc.queryForMap("SELECT * FROM t_entrega_email_fiscal WHERE documento_fiscal_id = ?", documentoId);
    }

    private int eventos(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM t_audit_log WHERE tenant_id = ? AND acao = ?",
                Integer.class, tenant, ACAO);
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : ((Timestamp) valor).toInstant();
    }

    private void assertReposta(UUID documentoId, String destinatario) {
        Map<String, Object> l = linha(documentoId);
        assertEquals("PENDENTE", l.get("estado"));
        assertEquals(0, ((Number) l.get("tentativas")).intValue());
        assertEquals(1, ((Number) l.get("reenvios")).intValue());
        assertEquals(destinatario, l.get("destinatario"));
        assertNull(l.get("proxima_tentativa_em"));
        assertNull(l.get("lease_ate"));
        assertNull(l.get("ultimo_erro"));
        assertNull(l.get("ultimo_erro_codigo"));
        assertNull(l.get("ultima_tentativa_em"));
        assertEquals(4L, ((Number) l.get("versao")).longValue());
        assertEquals(AGORA, instante(l.get("updated_at")));
    }

    private void assertRecusa(HttpStatus status, String codigo, UUID documentoId, Callable<?> chamada) {
        Map<String, Object> antes = documentoId == null ? null : linhaOuNula(documentoId);
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, chamada::call);
        assertEquals(status, e.getStatus());
        assertEquals(codigo, e.getCodigo());
        assertEquals(0, eventos(tenantId));
        if (documentoId != null) {
            assertEquals(antes, linhaOuNula(documentoId), "a linha de entrega não muda");
        }
    }

    private Map<String, Object> linhaOuNula(UUID documentoId) {
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT * FROM t_entrega_email_fiscal WHERE documento_fiscal_id = ?", documentoId);
        return l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------------ sucesso

    @Test
    void falhouVoltaAPendenteComOEmailAtualEAuditaSemEndereco() throws Exception {
        UUID doc = aceiteCom("FALHOU", 5);

        ReenviarEmailResponse r = service.reenviar(tenantId, autor, doc);

        assertEquals(new ReenviarEmailResponse("PENDENTE", 0), r);
        assertReposta(doc, "maria.atual@exemplo.cv");
        assertEquals(1, eventos(tenantId));
        Map<String, Object> ev = jdbc.queryForMap(
                "SELECT entidade_tipo, entidade_id, autor_id, detalhe FROM t_audit_log WHERE tenant_id = ? AND acao = ?",
                tenantId, ACAO);
        assertEquals("documento_fiscal", ev.get("entidade_tipo"));
        assertEquals(doc.toString(), ev.get("entidade_id"));
        assertEquals(autor.getUserId(), ev.get("autor_id"));
        String detalhe = (String) ev.get("detalhe");
        assertFalse(detalhe.contains("@"), detalhe);
        JsonNode d = json.readTree(detalhe);
        Set<String> chaves = new HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "estadoAnterior"), chaves);
        assertEquals("Ana Reenvia", d.get("autorNome").asText());
        assertEquals("FALHOU", d.get("estadoAnterior").asText());
    }

    @Test
    void enviadoTambemEReposto() {
        UUID doc = aceiteCom("ENVIADO", 1);
        assertEquals("PENDENTE", service.reenviar(tenantId, autor, doc).estado());
        assertReposta(doc, "maria.atual@exemplo.cv");
        assertEquals(1, eventos(tenantId));
    }

    @Test
    void semEmailDepoisDeOClienteTerEmailUsaEsseEmail() {
        emailCliente(null);
        UUID doc = aceiteCom("SEM_EMAIL", 0);
        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_EMAIL_CLIENTE", doc,
                () -> service.reenviar(tenantId, autor, doc));

        emailCliente("  maria.nova@exemplo.cv ");
        service.reenviar(tenantId, autor, doc);

        assertReposta(doc, "maria.nova@exemplo.cv");
        assertEquals(1, eventos(tenantId));
    }

    // ------------------------------------------------------------------ recusas

    @Test
    void pendenteEDesligadoDao409SemAuditoria() {
        UUID pendente = aceiteCom("PENDENTE", 2);
        assertRecusa(HttpStatus.CONFLICT, "ENTREGA_ESTADO_INVALIDO", pendente,
                () -> service.reenviar(tenantId, autor, pendente));

        UUID desligado = aceiteCom("DESLIGADO", 0);
        assertRecusa(HttpStatus.CONFLICT, "ENTREGA_ESTADO_INVALIDO", desligado,
                () -> service.reenviar(tenantId, autor, desligado));
    }

    @Test
    void semLinhaDeEntregaDa409() {
        UUID doc = documento();
        comunicacao(tenantId, doc, "ACEITE_SIMULADO");
        assertRecusa(HttpStatus.CONFLICT, "ENTREGA_ESTADO_INVALIDO", doc, () -> service.reenviar(tenantId, autor, doc));
        assertNull(linhaOuNula(doc));
    }

    @Test
    void smtpNaoConfiguradoDa422() {
        UUID doc = aceiteCom("FALHOU", 5);
        ReenvioEmailFiscalService semSmtp = new ReenvioEmailFiscalService(documentoRepository, entregaRepository,
                configuracaoRepository, comunicacaoRepository, clienteRepository, fila, auditoria, email(false),
                Clock.fixed(AGORA, ZoneOffset.UTC));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "SMTP_NAO_CONFIGURADO", doc,
                () -> tx.execute(s -> semSmtp.reenviar(tenantId, autor, doc)));
    }

    @Test
    void envioAutomaticoDesligadoDa422() {
        UUID doc = aceiteCom("FALHOU", 5);
        jdbc.update("UPDATE t_configuracao_fiscal SET envio_email_automatico = false WHERE tenant_id = ?", tenantId);
        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "ENVIO_EMAIL_DESLIGADO", doc,
                () -> service.reenviar(tenantId, autor, doc));
    }

    @Test
    void comunicacaoNaoAceiteDa422() {
        UUID doc = documento();
        comunicacao(tenantId, doc, "ERRO");
        entrega(tenantId, doc, "FALHOU", 5);
        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "COMUNICACAO_NAO_ACEITE", doc,
                () -> service.reenviar(tenantId, autor, doc));
    }

    @Test
    void clienteSemEmailOuComEmailInvalidoDa422() {
        UUID doc = aceiteCom("FALHOU", 5);
        emailCliente(null);
        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_EMAIL_CLIENTE", doc,
                () -> service.reenviar(tenantId, autor, doc));

        emailCliente("maria@exemplo.cv, outro@exemplo.cv");
        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_EMAIL_CLIENTE", doc,
                () -> service.reenviar(tenantId, autor, doc));
    }

    @Test
    void documentoDeOutroTenantDa404ENadaMuda() {
        UUID outroTenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        jdbc.update("UPDATE t_configuracao_fiscal SET envio_email_automatico = true WHERE tenant_id = ?", outroTenant);
        UUID doc = documento(outroTenant, clienteId);
        comunicacao(outroTenant, doc, "ACEITE_SIMULADO");
        entrega(outroTenant, doc, "FALHOU", 5);

        assertRecusa(HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO", doc,
                () -> service.reenviar(tenantId, autor, doc));
        assertEquals(0, eventos(outroTenant));
        assertEquals("FALHOU", linha(doc).get("estado"));
    }

    @Test
    void clienteDeOutroTenantNaoForneceOEmail() {
        UUID outroTenant = UUID.randomUUID();
        UUID clienteAlheio = fixtura.criarCliente(outroTenant, "345678912", "Alheio", "Rua 3");
        jdbc.update("UPDATE t_cliente SET email = 'alheio@outro.cv' WHERE id = ?", clienteAlheio);
        UUID doc = documento(tenantId, clienteAlheio);
        comunicacao(tenantId, doc, "ACEITE_SIMULADO");
        entrega(tenantId, doc, "FALHOU", 5);

        assertRecusa(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_EMAIL_CLIENTE", doc,
                () -> service.reenviar(tenantId, autor, doc));
    }

    // ------------------------------------------------------------------ concorrência

    @Test
    void doisReenviosConcorrentesSoUmPassa() throws Exception {
        UUID doc = aceiteCom("FALHOU", 5);
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReenviarEmailResponse>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futuros.add(pool.submit(() -> {
                    partida.await(10, TimeUnit.SECONDS);
                    return service.reenviar(tenantId, autor, doc);
                }));
            }
            partida.countDown();
            int sucessos = 0;
            int conflitos = 0;
            for (Future<ReenviarEmailResponse> f : futuros) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                    sucessos++;
                } catch (ExecutionException e) {
                    RecusaFiscalException recusa = assertInstanceOf(RecusaFiscalException.class, e.getCause());
                    assertEquals(HttpStatus.CONFLICT, recusa.getStatus());
                    assertEquals("ENTREGA_ESTADO_INVALIDO", recusa.getCodigo());
                    conflitos++;
                }
            }
            assertEquals(1, sucessos);
            assertEquals(1, conflitos);
            assertEquals(1, ((Number) linha(doc).get("reenvios")).intValue());
            assertEquals(1, eventos(tenantId));
        } finally {
            pool.shutdownNow();
        }
    }
}
