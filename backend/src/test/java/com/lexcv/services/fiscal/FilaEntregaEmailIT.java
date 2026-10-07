package com.lexcv.services.fiscal;

import com.lexcv.models.EstadoEntregaEmail;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 137 (ENTR-03, ENTR-04, ENTR-05; T-137-24..27): provas em PostgreSQL real da fila de entrega
 * por email -- criação no máximo uma vez, reclamação {@code FOR UPDATE SKIP LOCKED} disjunta entre
 * workers, lease que expira, limite de 5 tentativas no predicado, encerramento das esgotadas com a
 * mensagem que conta as tentativas, guarda de versão e de tenant, renovação do lease, novo episódio
 * no reenvio manual e snapshot preso ao tenant.
 *
 * <p>Andaime de {@code FilaComunicacaoFiscalIT}; relógio móvel. A reclamação é multi-tenant, por isso
 * cada teste começa com a fila vazia.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({EntregaEmailTransacoes.class, FilaEntregaEmail.class, FilaEntregaEmailIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FilaEntregaEmailIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant T0 = Instant.parse("2026-06-15T13:00:00Z");
    static final Duration LEASE = Duration.ofMinutes(5);
    static final RelogioMovel RELOGIO = new RelogioMovel(T0);

    /** Relógio de teste que avança quando o teste manda. */
    static final class RelogioMovel extends Clock {
        private final AtomicReference<Instant> agora;

        RelogioMovel(Instant inicio) {
            this.agora = new AtomicReference<>(inicio);
        }

        void definir(Instant instante) {
            agora.set(instante);
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

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return RELOGIO;
        }
    }

    private static final AtomicLong SEQUENCIA = new AtomicLong(1);

    @Autowired
    private EntregaEmailTransacoes transacoes;

    @Autowired
    private FilaEntregaEmail fila;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        RELOGIO.definir(T0);
        jdbc.update("DELETE FROM t_entrega_email_fiscal");
    }

    // ------------------------------------------------------------------ apoio

    private <T> T emTransacao(Supplier<T> trabalho) {
        return new TransactionTemplate(transactionManager).execute(s -> trabalho.get());
    }

    private UUID documento(UUID tenantId, String tipo, UUID origemId) {
        UUID id = UUID.randomUUID();
        long n = SEQUENCIA.getAndIncrement();
        String serie = "SIM-" + tipo + "-2026";
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia, documento_origem_id) "
                        + "VALUES (?, ?, ?, 'SIMULADO', ?, ?, 2026, ?, ?, ?, ?, '512345679', 'Firma', 'Morada', "
                        + "'NORMAL', '234567891', 'Cliente', 'Morada cliente', ?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', "
                        + "15, 100, 15, 0, 115, 115, ?, ?)",
                id, tenantId, tipo, UUID.randomUUID(), serie, n, tipo + " " + serie + "/" + n,
                java.sql.Date.valueOf("2026-06-15"), Timestamp.from(T0), UUID.randomUUID(), UUID.randomUUID(),
                (int) -n, UUID.randomUUID(), origemId);
        return id;
    }

    private UUID entrega(UUID tenantId, UUID documentoId, String estado, int tentativas, Instant proxima,
                         Instant criadaEm) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_entrega_email_fiscal (id, tenant_id, documento_fiscal_id, estado, destinatario, "
                        + "tentativas, proxima_tentativa_em, created_at, versao, reenvios) "
                        + "VALUES (?, ?, ?, ?, 'cliente@exemplo.cv', ?, ?, ?, 0, 0)",
                id, tenantId, documentoId, estado, tentativas, proxima == null ? null : Timestamp.from(proxima),
                Timestamp.from(criadaEm));
        return id;
    }

    private UUID pendente(UUID tenantId, Instant criadaEm) {
        return entrega(tenantId, documento(tenantId, "FR", null), "PENDENTE", 0, null, criadaEm);
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_entrega_email_fiscal WHERE id = ?", id);
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : ((Timestamp) valor).toInstant();
    }

    private static Set<UUID> ids(List<EntregaEmailReclamada> itens) {
        return itens.stream().map(EntregaEmailReclamada::id).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ criação

    @Test
    void criarSeAusenteCriaUmaSoVezENuncaReclamaDesligadoNemSemEmail() {
        UUID t = UUID.randomUUID();
        UUID doc = documento(t, "FR", null);

        assertEquals(1, emTransacao(() -> fila.criarSeAusente(UUID.randomUUID(), t, doc, EstadoEntregaEmail.PENDENTE,
                "ana@exemplo.cv", T0)));
        assertEquals(0, emTransacao(() -> fila.criarSeAusente(UUID.randomUUID(), t, doc, EstadoEntregaEmail.FALHOU,
                "outro@exemplo.cv", T0)));
        Map<String, Object> l = jdbc.queryForMap("SELECT * FROM t_entrega_email_fiscal WHERE documento_fiscal_id = ?", doc);
        assertEquals("PENDENTE", l.get("estado"));
        assertEquals("ana@exemplo.cv", l.get("destinatario"));
        assertEquals(0, ((Number) l.get("tentativas")).intValue());
        assertEquals(0, ((Number) l.get("reenvios")).intValue());
        assertEquals(0L, ((Number) l.get("versao")).longValue());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM t_entrega_email_fiscal WHERE documento_fiscal_id = ?", Integer.class, doc));

        jdbc.update("DELETE FROM t_entrega_email_fiscal");
        UUID desligado = documento(t, "FR", null);
        UUID semEmail = documento(t, "FR", null);
        emTransacao(() -> fila.criarSeAusente(UUID.randomUUID(), t, desligado, EstadoEntregaEmail.DESLIGADO, null, T0));
        emTransacao(() -> fila.criarSeAusente(UUID.randomUUID(), t, semEmail, EstadoEntregaEmail.SEM_EMAIL, null, T0));
        entrega(t, documento(t, "FR", null), "ENVIADO", 1, null, T0.minusSeconds(10));
        entrega(t, documento(t, "FR", null), "FALHOU", 5, null, T0.minusSeconds(10));
        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "só PENDENTE é reclamada");
    }

    // ------------------------------------------------------------------ reclamação

    @Test
    void pendentesComProximaNulaSaoReclamadasDasMaisAntigasParaAsMaisRecentes() {
        UUID t = UUID.randomUUID();
        UUID terceira = pendente(t, T0.minusSeconds(60));
        UUID primeira = pendente(t, T0.minusSeconds(180));
        UUID segunda = pendente(t, T0.minusSeconds(120));
        UUID futura = entrega(t, documento(t, "FR", null), "PENDENTE", 0, T0.plusSeconds(60), T0.minusSeconds(600));

        List<EntregaEmailReclamada> r = transacoes.reclamar(10, LEASE);

        assertEquals(List.of(primeira, segunda, terceira), r.stream().map(EntregaEmailReclamada::id).toList());
        EntregaEmailReclamada item = r.get(0);
        assertEquals(t, item.tenantId());
        assertEquals("cliente@exemplo.cv", item.destinatario());
        assertEquals(1, item.tentativas());
        assertEquals(1L, item.versao());
        assertEquals(0, item.reenvios());
        assertTrue(!item.toString().contains("cliente@exemplo.cv"), "destinatário fora do toString");
        Map<String, Object> l = linha(primeira);
        assertEquals(1, ((Number) l.get("tentativas")).intValue());
        assertEquals(1L, ((Number) l.get("versao")).longValue());
        assertEquals(T0.plus(LEASE), instante(l.get("lease_ate")));
        assertEquals(T0, instante(l.get("ultima_tentativa_em")));
        assertEquals("PENDENTE", l.get("estado"));
        assertEquals(0, ((Number) linha(futura).get("tentativas")).intValue());
    }

    @Test
    void esgotadaNaoEReclamadaEEFechadaUmaSoVezComAMensagemDasTentativas() {
        UUID t = UUID.randomUUID();
        UUID esgotada = entrega(t, documento(t, "FR", null), "PENDENTE", 5, null, T0.minusSeconds(60));

        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "tentativas = 5 não é reclamada");

        List<EntregaEmailReclamada> fechadas = transacoes.encerrarEsgotadas();
        assertEquals(List.of(esgotada), fechadas.stream().map(EntregaEmailReclamada::id).toList());
        assertEquals(t, fechadas.get(0).tenantId());
        assertEquals(5, fechadas.get(0).tentativas());
        Map<String, Object> l = linha(esgotada);
        assertEquals("FALHOU", l.get("estado"));
        assertEquals("TENTATIVAS_ESGOTADAS", l.get("ultimo_erro_codigo"));
        assertEquals("O envio do email falhou após 5 tentativas.", l.get("ultimo_erro"));
        assertNull(l.get("lease_ate"));
        assertNull(l.get("proxima_tentativa_em"));
        assertEquals(1L, ((Number) l.get("versao")).longValue());

        assertTrue(transacoes.encerrarEsgotadas().isEmpty(), "devolvida uma só vez");
    }

    @Test
    void esgotadaComLeaseAtivoSoFechaDepoisDeOLeaseExpirar() {
        UUID t = UUID.randomUUID();
        UUID id = entrega(t, documento(t, "FR", null), "PENDENTE", 4, null, T0.minusSeconds(60));
        EntregaEmailReclamada quinta = transacoes.reclamar(10, LEASE).get(0);
        assertEquals(id, quinta.id());
        assertEquals(5, quinta.tentativas());

        assertTrue(transacoes.encerrarEsgotadas().isEmpty(), "lease ativo: o worker ainda pode registar");
        RELOGIO.definir(T0.plus(LEASE).plusSeconds(1));
        assertEquals(Set.of(id), ids(transacoes.encerrarEsgotadas()));
    }

    @Test
    void mensagemNoSingularComUmaTentativa() {
        UUID t = UUID.randomUUID();
        UUID id = entrega(t, documento(t, "FR", null), "PENDENTE", 1, null, T0.minusSeconds(60));

        List<EntregaEmailReclamada> fechadas = emTransacao(() ->
                fila.encerrarEsgotadas(T0, 1, EntregaEmailTransacoes.TENTATIVAS_ESGOTADAS));

        assertEquals(Set.of(id), ids(fechadas));
        assertEquals("O envio do email falhou após 1 tentativa.", linha(id).get("ultimo_erro"));
    }

    @Test
    void duasReclamacoesConcorrentesNuncaDevolvemAMesmaLinha() throws Exception {
        UUID t = UUID.randomUUID();
        Set<UUID> todas = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            todas.add(pendente(t, T0.minusSeconds(100 - i)));
        }
        CompletableFuture<List<EntregaEmailReclamada>> doA = new CompletableFuture<>();
        CountDownLatch libertarA = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> fimA = CompletableFuture.runAsync(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                        doA.complete(transacoes.reclamar(3, LEASE));
                        try {
                            // Mantém a transação aberta (e as linhas bloqueadas) enquanto B reclama.
                            assertTrue(libertarA.await(30, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }), executor);
            List<EntregaEmailReclamada> a = doA.get(30, TimeUnit.SECONDS);

            List<EntregaEmailReclamada> b = transacoes.reclamar(10, LEASE);

            libertarA.countDown();
            fimA.get(30, TimeUnit.SECONDS);
            assertEquals(3, a.size());
            assertEquals(3, b.size());
            Set<UUID> intersecao = new HashSet<>(ids(a));
            intersecao.retainAll(ids(b));
            assertTrue(intersecao.isEmpty(), "linhas reclamadas por dois workers: " + intersecao);
            Set<UUID> uniao = new HashSet<>(ids(a));
            uniao.addAll(ids(b));
            assertEquals(todas, uniao);
            assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "leases ativos: nada mais a reclamar");
        } finally {
            libertarA.countDown();
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ lease, versão e tenant

    @Test
    void leaseExpiradoVoltaASerReclamavelEAVersaoAntigaJaNaoGrava() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        EntregaEmailReclamada primeira = transacoes.reclamar(10, LEASE).get(0);

        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "lease ativo");
        RELOGIO.definir(T0.plus(LEASE).plusSeconds(1));
        EntregaEmailReclamada segunda = transacoes.reclamar(10, LEASE).get(0);
        assertEquals(id, segunda.id());
        assertEquals(primeira.versao() + 1, segunda.versao());
        assertEquals(2, segunda.tentativas());

        assertEquals(0, transacoes.registarResultado(primeira, EstadoEntregaEmail.ENVIADO, null, null, null),
                "versão antiga não grava");
        EntregaEmailReclamada outroTenant = new EntregaEmailReclamada(segunda.id(), UUID.randomUUID(),
                segunda.documentoFiscalId(), segunda.destinatario(), segunda.tentativas(), segunda.versao(),
                segunda.reenvios());
        assertEquals(0, transacoes.registarResultado(outroTenant, EstadoEntregaEmail.ENVIADO, null, null, null),
                "tenant errado não grava");
        assertEquals(1, transacoes.registarResultado(segunda, EstadoEntregaEmail.ENVIADO, null, null, null));
        assertEquals("ENVIADO", linha(id).get("estado"));
    }

    @Test
    void registarResultadoEnviadoEPendente() {
        UUID t = UUID.randomUUID();
        UUID enviada = pendente(t, T0.minusSeconds(20));
        UUID adiada = pendente(t, T0.minusSeconds(10));
        jdbc.update("UPDATE t_entrega_email_fiscal SET ultimo_erro = 'erro anterior', ultimo_erro_codigo = 'X'");
        List<EntregaEmailReclamada> r = transacoes.reclamar(10, LEASE);

        RELOGIO.definir(T0.plusSeconds(30));
        assertEquals(1, transacoes.registarResultado(r.get(0), EstadoEntregaEmail.ENVIADO, "IGNORADO", "ignorado",
                null));
        Map<String, Object> e = linha(enviada);
        assertEquals("ENVIADO", e.get("estado"));
        assertEquals(T0.plusSeconds(30), instante(e.get("enviado_em")));
        assertNull(e.get("ultimo_erro"));
        assertNull(e.get("ultimo_erro_codigo"));
        assertNull(e.get("lease_ate"));

        Instant proxima = T0.plusSeconds(30).plus(BackoffEntregaEmail.atraso(1));
        assertEquals(1, transacoes.registarResultado(r.get(1), EstadoEntregaEmail.PENDENTE, "SMTP_TEMPORARIO",
                "O servidor de email não respondeu. Nova tentativa automática.", proxima));
        Map<String, Object> p = linha(adiada);
        assertEquals("PENDENTE", p.get("estado"));
        assertNull(p.get("enviado_em"));
        assertEquals(proxima, instante(p.get("proxima_tentativa_em")));
        assertEquals("SMTP_TEMPORARIO", p.get("ultimo_erro_codigo"));
        assertNull(p.get("lease_ate"));
    }

    @Test
    void registarResultadoTruncaCodigoEMensagem() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        EntregaEmailReclamada item = transacoes.reclamar(10, LEASE).get(0);

        assertEquals(1, transacoes.registarResultado(item, EstadoEntregaEmail.FALHOU, "C".repeat(80), "m".repeat(700),
                null));
        Map<String, Object> l = linha(id);
        assertEquals(64, ((String) l.get("ultimo_erro_codigo")).length());
        assertEquals(500, ((String) l.get("ultimo_erro")).length());
    }

    @Test
    void renovarLeaseSoEnquantoPendenteEDono() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        EntregaEmailReclamada item = transacoes.reclamar(10, LEASE).get(0);

        RELOGIO.definir(T0.plusSeconds(60));
        assertTrue(transacoes.renovarLease(item, LEASE));
        assertEquals(T0.plusSeconds(60).plus(LEASE), instante(linha(id).get("lease_ate")));
        assertEquals(item.versao(), ((Number) linha(id).get("versao")).longValue(), "renovar não muda a versão");

        assertEquals(1, transacoes.registarResultado(item, EstadoEntregaEmail.FALHOU, "SMTP_PERMANENTE",
                "O servidor de email recusou o destinatário.", null));
        EntregaEmailReclamada mesmaVersao = new EntregaEmailReclamada(item.id(), item.tenantId(),
                item.documentoFiscalId(), item.destinatario(), item.tentativas(), item.versao() + 1, item.reenvios());
        assertTrue(!transacoes.renovarLease(mesmaVersao, LEASE), "fora de PENDENTE não renova");
        assertTrue(!transacoes.renovarLease(item, LEASE), "versão antiga não renova");
    }

    // ------------------------------------------------------------------ reenvio manual

    @Test
    void reporPendenteComecaNovoEpisodioSoAPartirDosEstadosReenviaveis() {
        UUID t = UUID.randomUUID();
        for (String estado : List.of("FALHOU", "ENVIADO", "SEM_EMAIL")) {
            UUID doc = documento(t, "FR", null);
            UUID id = entrega(t, doc, estado, 3, null, T0.minusSeconds(60));
            jdbc.update("UPDATE t_entrega_email_fiscal SET ultimo_erro = 'x', ultimo_erro_codigo = 'Y', "
                    + "lease_ate = ?, ultima_tentativa_em = ?, proxima_tentativa_em = ? WHERE id = ?",
                    Timestamp.from(T0), Timestamp.from(T0), Timestamp.from(T0), id);

            assertEquals(0, emTransacao(() -> fila.reporPendente(UUID.randomUUID(), doc, "novo@exemplo.cv", T0)),
                    estado + ": outro tenant");
            assertEquals(1, emTransacao(() -> fila.reporPendente(t, doc, "novo@exemplo.cv", T0)), estado);

            Map<String, Object> l = linha(id);
            assertEquals("PENDENTE", l.get("estado"), estado);
            assertEquals("novo@exemplo.cv", l.get("destinatario"));
            assertEquals(0, ((Number) l.get("tentativas")).intValue());
            assertEquals(1, ((Number) l.get("reenvios")).intValue());
            assertEquals(1L, ((Number) l.get("versao")).longValue());
            assertNull(l.get("ultimo_erro"));
            assertNull(l.get("ultimo_erro_codigo"));
            assertNull(l.get("lease_ate"));
            assertNull(l.get("ultima_tentativa_em"));
            assertNull(l.get("proxima_tentativa_em"));
        }
        for (String estado : List.of("PENDENTE", "DESLIGADO")) {
            UUID doc = documento(t, "FR", null);
            entrega(t, doc, estado, 0, T0.plusSeconds(3600), T0.minusSeconds(60));
            assertEquals(0, emTransacao(() -> fila.reporPendente(t, doc, "novo@exemplo.cv", T0)), estado);
        }
        // Os três repostos ficam devidos já.
        assertEquals(3, transacoes.reclamar(10, LEASE).size());
    }

    // ------------------------------------------------------------------ snapshot

    @Test
    void snapshotDeNcTrazONumeroDaFrENuncaAtravessaTenants() {
        UUID t = UUID.randomUUID();
        UUID fr = documento(t, "FR", null);
        UUID nc = documento(t, "NC", fr);
        String numeroFr = jdbc.queryForObject("SELECT numero_formatado FROM t_documento_fiscal WHERE id = ?",
                String.class, fr);
        jdbc.update("INSERT INTO t_configuracao_fiscal (id, tenant_id, nif, firma, morada, localidade, pais_codigo, "
                        + "email_contacto, telefone_contacto, regime_iva, ativa, envio_email_automatico, created_at) "
                        + "VALUES (?, ?, '512345679', 'Firma', 'Morada', 'Praia', 'CV', 'geral@firma.cv', '+238 260', "
                        + "'NORMAL', true, true, ?)",
                UUID.randomUUID(), t, Timestamp.from(T0));

        Optional<SnapshotEntregaEmail> s = transacoes.carregarSnapshot(t, nc);
        assertTrue(s.isPresent());
        assertEquals(nc, s.get().documento().getId());
        assertEquals(Optional.of(numeroFr), s.get().numeroOrigem());
        assertEquals(Optional.of("geral@firma.cv"), s.get().replyTo());
        assertTrue(s.get().xml().isEmpty());

        Optional<SnapshotEntregaEmail> fs = transacoes.carregarSnapshot(t, fr);
        assertNotNull(fs.orElse(null));
        assertTrue(fs.get().numeroOrigem().isEmpty(), "FR não tem origem");

        assertTrue(transacoes.carregarSnapshot(UUID.randomUUID(), nc).isEmpty(), "outro tenant");
        UUID semConfig = UUID.randomUUID();
        UUID doc = documento(semConfig, "FR", null);
        assertTrue(transacoes.carregarSnapshot(semConfig, doc).orElseThrow().replyTo().isEmpty());
    }
}
