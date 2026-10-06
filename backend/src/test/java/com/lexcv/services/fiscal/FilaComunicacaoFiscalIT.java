package com.lexcv.services.fiscal;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.repositories.FilaComunicacaoFiscal;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 136 (DFE-04, DFE-06): provas em PostgreSQL real da fila de comunicação fiscal --
 * linhas com {@code proxima_tentativa_em} NULL (as das Phases 134/135) são devidas, reclamação
 * {@code FOR UPDATE SKIP LOCKED} disjunta entre workers concorrentes, lease que expira, guarda de
 * versão e de tenant no resultado, tenants suspensos não saltados, XML insert-only e o IUD da FR
 * no snapshot de uma NC.
 *
 * <p>Andaime de {@link NotaCreditoServiceIT}; o relógio é móvel para simular a expiração do
 * lease. A reclamação é multi-tenant, por isso cada teste começa com a fila vazia.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ComunicacaoFiscalTransacoes.class, FilaComunicacaoFiscal.class, FilaComunicacaoFiscalIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FilaComunicacaoFiscalIT {

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
    private ComunicacaoFiscalTransacoes transacoes;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        RELOGIO.definir(T0);
        jdbc.update("DELETE FROM t_comunicacao_fiscal");
    }

    // ------------------------------------------------------------------ apoio JDBC

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
                id, tenantId, tipo, UUID.randomUUID(), serie, n, serie + "/" + n,
                java.sql.Date.valueOf("2026-06-15"), Timestamp.from(T0), UUID.randomUUID(), UUID.randomUUID(),
                (int) -n, UUID.randomUUID(), origemId);
        jdbc.update("INSERT INTO t_documento_fiscal_linha (id, tenant_id, documento_fiscal_id, numero_linha, "
                        + "descricao, quantidade, preco_unitario, valor_base, taxa_iva, valor_iva, valor_retencao, "
                        + "total_linha) VALUES (?, ?, ?, 1, 'Honorários', 1, 100, 100, 15, 15, 0, 115)",
                UUID.randomUUID(), tenantId, id);
        return id;
    }

    private UUID documento(UUID tenantId) {
        return documento(tenantId, "FR", null);
    }

    private UUID comunicacao(UUID tenantId, UUID documentoId, String estado, Instant proxima, Instant criadaEm) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_comunicacao_fiscal (id, tenant_id, documento_fiscal_id, ambiente, estado, "
                        + "tentativas, proxima_tentativa_em, created_at, versao, reprocessamentos) "
                        + "VALUES (?, ?, ?, 'SIMULADO', ?, 0, ?, ?, 0, 0)",
                id, tenantId, documentoId, estado, proxima == null ? null : Timestamp.from(proxima),
                Timestamp.from(criadaEm));
        return id;
    }

    private UUID pendente(UUID tenantId, Instant criadaEm) {
        return comunicacao(tenantId, documento(tenantId), "PENDENTE", null, criadaEm);
    }

    private Map<String, Object> linha(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_comunicacao_fiscal WHERE id = ?", id);
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : ((Timestamp) valor).toInstant();
    }

    private static Set<UUID> ids(List<ComunicacaoReclamada> itens) {
        return itens.stream().map(ComunicacaoReclamada::id).collect(Collectors.toSet());
    }

    private static String iud(long n) {
        return "CV3260615512345679" + String.format("%027d", n);
    }

    // ------------------------------------------------------------------ reclamação

    @Test
    void pendentesComProximaNulaSaoReclamadasDasMaisAntigasParaAsMaisRecentes() {
        UUID t = UUID.randomUUID();
        UUID terceira = pendente(t, T0.minusSeconds(60));
        UUID primeira = pendente(t, T0.minusSeconds(180));
        UUID segunda = pendente(t, T0.minusSeconds(120));

        List<ComunicacaoReclamada> r = transacoes.reclamar(10, LEASE);

        assertEquals(List.of(primeira, segunda, terceira), r.stream().map(ComunicacaoReclamada::id).toList());
        ComunicacaoReclamada item = r.get(0);
        assertEquals(t, item.tenantId());
        assertEquals(AmbienteFiscal.SIMULADO, item.ambiente());
        assertEquals(1, item.tentativas());
        assertEquals(1L, item.versao());
        assertEquals(0, item.reprocessamentos());
        Map<String, Object> l = linha(primeira);
        assertEquals(1, ((Number) l.get("tentativas")).intValue());
        assertEquals(1L, ((Number) l.get("versao")).longValue());
        assertEquals(T0.plus(LEASE), instante(l.get("lease_ate")));
        assertEquals(T0, instante(l.get("ultima_tentativa_em")));
        assertEquals("PENDENTE", l.get("estado"));
    }

    @Test
    void loteLimitaAsLinhasReclamadas() {
        UUID t = UUID.randomUUID();
        UUID a = pendente(t, T0.minusSeconds(30));
        UUID b = pendente(t, T0.minusSeconds(20));
        pendente(t, T0.minusSeconds(10));

        assertEquals(List.of(a, b), transacoes.reclamar(2, LEASE).stream().map(ComunicacaoReclamada::id).toList());
    }

    @Test
    void naoReclamaFuturasNemTerminais() {
        UUID t = UUID.randomUUID();
        UUID futura = comunicacao(t, documento(t), "PENDENTE", T0.plusSeconds(60), T0.minusSeconds(600));
        comunicacao(t, documento(t), "ACEITE_SIMULADO", null, T0.minusSeconds(500));
        comunicacao(t, documento(t), "REJEITADO", null, T0.minusSeconds(400));
        comunicacao(t, documento(t), "ERRO", null, T0.minusSeconds(300));
        UUID agora = comunicacao(t, documento(t), "PENDENTE", T0, T0.minusSeconds(200));
        UUID passada = comunicacao(t, documento(t), "PENDENTE", T0.minusSeconds(1), T0.minusSeconds(100));

        assertEquals(List.of(agora, passada), transacoes.reclamar(10, LEASE).stream()
                .map(ComunicacaoReclamada::id).toList());
        assertEquals(0, ((Number) linha(futura).get("tentativas")).intValue());

        RELOGIO.definir(T0.plusSeconds(60));
        assertEquals(Set.of(futura), ids(transacoes.reclamar(10, LEASE)));
    }

    @Test
    void duasReclamacoesConcorrentesNuncaDevolvemAMesmaLinha() throws Exception {
        UUID t = UUID.randomUUID();
        Set<UUID> todas = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            todas.add(pendente(t, T0.minusSeconds(100 - i)));
        }
        CompletableFuture<List<ComunicacaoReclamada>> doA = new CompletableFuture<>();
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
            List<ComunicacaoReclamada> a = doA.get(30, TimeUnit.SECONDS);

            List<ComunicacaoReclamada> b = transacoes.reclamar(10, LEASE);

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

    @Test
    void leaseExpiradoVoltaASerReclamavelEAVersaoAntigaJaNaoGrava() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        ComunicacaoReclamada primeira = transacoes.reclamar(10, LEASE).get(0);

        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "lease ativo");
        RELOGIO.definir(T0.plus(LEASE).minusSeconds(1));
        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "ainda dentro do lease");

        RELOGIO.definir(T0.plus(LEASE).plusSeconds(1));
        List<ComunicacaoReclamada> outra = transacoes.reclamar(10, LEASE);

        assertEquals(1, outra.size());
        ComunicacaoReclamada segunda = outra.get(0);
        assertEquals(id, segunda.id());
        assertEquals(2, segunda.tentativas());
        assertEquals(primeira.versao() + 1, segunda.versao());
        assertEquals(0, transacoes.registarResultado(primeira, EstadoComunicacaoFiscal.ACEITE_SIMULADO,
                null, null, null), "o worker que perdeu o lease não grava");
        assertEquals(1, transacoes.registarResultado(segunda, EstadoComunicacaoFiscal.ACEITE_SIMULADO,
                null, null, null));
        assertEquals("ACEITE_SIMULADO", linha(id).get("estado"));
    }

    @Test
    void renovarLeaseSoParaODonoEProtegeOEnvioDeOutraReclamacao() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        ComunicacaoReclamada item = transacoes.reclamar(10, LEASE).get(0);

        // O item é processado tarde (lote lento): renova antes de enviar e fica com lease novo.
        RELOGIO.definir(T0.plus(LEASE).minusSeconds(5));
        assertTrue(transacoes.renovarLease(item, LEASE));
        assertEquals(T0.plus(LEASE).minusSeconds(5).plus(LEASE), instante(linha(id).get("lease_ate")));
        assertEquals(item.versao(), ((Number) linha(id).get("versao")).longValue(), "a versão não muda");
        RELOGIO.definir(T0.plus(LEASE).plusSeconds(1));
        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "lease renovado: ninguém mais a reclama");

        // Lease expirado E reclamado por outro worker: o antigo dono já não pode renovar (nem enviar).
        RELOGIO.definir(T0.plus(LEASE.multipliedBy(3)));
        ComunicacaoReclamada outra = transacoes.reclamar(10, LEASE).get(0);
        assertEquals(id, outra.id());
        assertEquals(false, transacoes.renovarLease(item, LEASE));
        assertTrue(transacoes.renovarLease(outra, LEASE));

        ComunicacaoReclamada outroTenant = new ComunicacaoReclamada(outra.id(), UUID.randomUUID(),
                outra.documentoFiscalId(), outra.ambiente(), outra.tentativas(), outra.versao(),
                outra.reprocessamentos());
        assertEquals(false, transacoes.renovarLease(outroTenant, LEASE));
    }

    @Test
    void resultadoComTenantErradoNaoAtualizaNada() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        ComunicacaoReclamada item = transacoes.reclamar(10, LEASE).get(0);
        ComunicacaoReclamada outroTenant = new ComunicacaoReclamada(item.id(), UUID.randomUUID(),
                item.documentoFiscalId(), item.ambiente(), item.tentativas(), item.versao(), item.reprocessamentos());

        assertEquals(0, transacoes.registarResultado(outroTenant, EstadoComunicacaoFiscal.ERRO, "X", "x", null));
        assertEquals("PENDENTE", linha(id).get("estado"));
        assertEquals(1, transacoes.registarResultado(item, EstadoComunicacaoFiscal.ERRO, "X", "x", null));
    }

    @Test
    void resultadoTerminalPreencheConcluidoELimpaOLease() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        ComunicacaoReclamada item = transacoes.reclamar(10, LEASE).get(0);
        RELOGIO.definir(T0.plusSeconds(3));
        String longa = "m".repeat(600);
        String codigoLongo = "C".repeat(80);

        assertEquals(1, transacoes.registarResultado(item, EstadoComunicacaoFiscal.ERRO, codigoLongo, longa, null));

        Map<String, Object> l = linha(id);
        assertEquals("ERRO", l.get("estado"));
        assertEquals(T0.plusSeconds(3), instante(l.get("concluido_em")));
        assertNull(l.get("lease_ate"));
        assertNull(l.get("proxima_tentativa_em"));
        assertEquals(500, ((String) l.get("ultimo_erro")).length());
        assertEquals(64, ((String) l.get("ultimo_erro_codigo")).length());
        assertEquals(item.versao() + 1, ((Number) l.get("versao")).longValue());
        RELOGIO.definir(T0.plus(Duration.ofDays(1)));
        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "um estado terminal nunca é reclamado");
    }

    @Test
    void resultadoPendenteAgendaAProximaTentativaSemConcluir() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        ComunicacaoReclamada item = transacoes.reclamar(10, LEASE).get(0);
        Instant proxima = T0.plusSeconds(120);

        assertEquals(1, transacoes.registarResultado(item, EstadoComunicacaoFiscal.PENDENTE, "GATEWAY_INDISPONIVEL",
                "Serviço indisponível", proxima));

        Map<String, Object> l = linha(id);
        assertEquals("PENDENTE", l.get("estado"));
        assertNull(l.get("concluido_em"));
        assertNull(l.get("lease_ate"));
        assertEquals(proxima, instante(l.get("proxima_tentativa_em")));
        assertEquals("GATEWAY_INDISPONIVEL", l.get("ultimo_erro_codigo"));
        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "ainda não chegou a próxima tentativa");
        RELOGIO.definir(proxima);
        List<ComunicacaoReclamada> outra = transacoes.reclamar(10, LEASE);
        assertEquals(Set.of(id), ids(outra));
        assertEquals(2, outra.get(0).tentativas());
    }

    @Test
    void linhaQueNuncaRegistaResultadoDeixaDeSerReclamadaEFechaEmErro() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        Instant agora = T0;
        // Cada reclamação "morre" sem registar resultado: o lease expira e a linha volta à fila.
        for (int i = 1; i <= EstadoComunicacaoMapper.MAX_TENTATIVAS; i++) {
            RELOGIO.definir(agora);
            assertTrue(transacoes.encerrarEsgotadas().isEmpty(), "ainda há tentativas na reclamação " + i);
            List<ComunicacaoReclamada> r = transacoes.reclamar(10, LEASE);
            assertEquals(Set.of(id), ids(r));
            assertEquals(i, r.get(0).tentativas());
            agora = agora.plus(LEASE).plusSeconds(1);
        }
        RELOGIO.definir(agora);

        assertTrue(transacoes.reclamar(10, LEASE).isEmpty(), "esgotada: nunca mais é reclamada");
        List<ComunicacaoReclamada> fechadas = transacoes.encerrarEsgotadas();

        assertEquals(Set.of(id), ids(fechadas));
        assertEquals(t, fechadas.get(0).tenantId());
        Map<String, Object> l = linha(id);
        assertEquals("ERRO", l.get("estado"));
        assertEquals("FALHA_INTERNA", l.get("ultimo_erro_codigo"));
        assertEquals("Falha interna ao comunicar o documento.", l.get("ultimo_erro"));
        assertNull(l.get("lease_ate"));
        assertEquals(agora, instante(l.get("concluido_em")));
        assertTrue(transacoes.encerrarEsgotadas().isEmpty(), "só fecha uma vez");
    }

    @Test
    void esgotadaComLeaseAtivoNaoEFechada() {
        UUID t = UUID.randomUUID();
        UUID id = pendente(t, T0.minusSeconds(10));
        jdbc.update("UPDATE t_comunicacao_fiscal SET tentativas = ? WHERE id = ?",
                EstadoComunicacaoMapper.MAX_TENTATIVAS - 1, id);
        ComunicacaoReclamada ultima = transacoes.reclamar(10, LEASE).get(0);
        assertEquals(EstadoComunicacaoMapper.MAX_TENTATIVAS, ultima.tentativas());

        assertTrue(transacoes.encerrarEsgotadas().isEmpty(), "o worker ainda tem o lease");
        assertEquals("PENDENTE", linha(id).get("estado"));
        assertEquals(1, transacoes.registarResultado(ultima, EstadoComunicacaoFiscal.ACEITE_SIMULADO,
                null, null, null), "o dono do lease grava o resultado");
    }

    @Test
    void suspensoNaoESaltadoLinhaDoTenantEReclamadaComoQualquerOutra() {
        UUID suspenso = UUID.randomUUID();
        jdbc.update("INSERT INTO t_tenant (id, nome, plano, ativo, created_at) VALUES (?, 'Suspenso', 'STARTER', "
                + "false, now())", suspenso);
        UUID id = pendente(suspenso, T0.minusSeconds(10));

        List<ComunicacaoReclamada> r = transacoes.reclamar(10, LEASE);

        assertEquals(Set.of(id), ids(r));
        assertEquals(suspenso, r.get(0).tenantId());
    }

    // ------------------------------------------------------------------ XML e snapshot

    @Test
    void gravarXmlDuasVezesDevolveSempreAPrimeiraLinha() {
        UUID t = UUID.randomUUID();
        UUID doc = documento(t);
        String sha1 = "a".repeat(64);
        String sha2 = "b".repeat(64);

        Optional<DocumentoFiscalXml> primeira = transacoes.gravarXml(t, doc, iud(1), AmbienteFiscal.SIMULADO, 3,
                99999, "1.0", "<Dfe/>", sha1);
        Optional<DocumentoFiscalXml> segunda = transacoes.gravarXml(t, doc, iud(2), AmbienteFiscal.SIMULADO, 3,
                99999, "1.0", "<Outro/>", sha2);

        assertTrue(primeira.isPresent());
        assertTrue(segunda.isPresent());
        assertEquals(iud(1), primeira.get().getIud());
        assertEquals(iud(1), segunda.get().getIud());
        assertEquals(sha1, segunda.get().getXmlSha256());
        assertEquals("<Dfe/>", segunda.get().getXml());
        assertEquals(T0, segunda.get().getGeradoEm());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM t_documento_fiscal_xml WHERE documento_fiscal_id = ?",
                Integer.class, doc));
    }

    @Test
    void gravarXmlComIudJaUsadoPorOutroDocumentoDevolveVazio() {
        UUID t = UUID.randomUUID();
        UUID a = documento(t);
        UUID b = documento(t);
        transacoes.gravarXml(t, a, iud(10), AmbienteFiscal.SIMULADO, 3, 99999, "1.0", "<Dfe/>", "c".repeat(64));

        assertTrue(transacoes.gravarXml(t, b, iud(10), AmbienteFiscal.SIMULADO, 3, 99999, "1.0", "<Dfe/>",
                "d".repeat(64)).isEmpty());
    }

    @Test
    void snapshotDeNcTrazOIudDaFrSoDepoisDeEleExistir() {
        UUID t = UUID.randomUUID();
        UUID fr = documento(t, "FR", null);
        UUID nc = documento(t, "NC", fr);
        String numeroFr = jdbc.queryForObject("SELECT numero_formatado FROM t_documento_fiscal WHERE id = ?",
                String.class, fr);

        SnapshotComunicacao antes = transacoes.carregarSnapshot(t, nc).orElseThrow();

        assertEquals(nc, antes.documento().getId());
        assertEquals(1, antes.linha().getNumeroLinha());
        assertTrue(antes.xmlExistente().isEmpty());
        assertTrue(antes.iudOrigem().isEmpty(), "a FR ainda não tem XML");
        assertEquals(Optional.of(numeroFr), antes.numeroFormatadoOrigem());

        transacoes.gravarXml(t, fr, iud(20), AmbienteFiscal.SIMULADO, 3, 99999, "1.0", "<Dfe/>", "e".repeat(64));
        SnapshotComunicacao depois = transacoes.carregarSnapshot(t, nc).orElseThrow();
        assertEquals(Optional.of(iud(20)), depois.iudOrigem());

        SnapshotComunicacao daFr = transacoes.carregarSnapshot(t, fr).orElseThrow();
        assertTrue(daFr.iudOrigem().isEmpty());
        assertTrue(daFr.numeroFormatadoOrigem().isEmpty());
        assertEquals(Optional.of(iud(20)), daFr.xmlExistente().map(DocumentoFiscalXml::getIud));
        assertNotNull(daFr.linha());
    }

    @Test
    void snapshotNoutroTenantEVazio() {
        UUID t = UUID.randomUUID();
        UUID doc = documento(t);

        assertTrue(transacoes.carregarSnapshot(UUID.randomUUID(), doc).isEmpty());
        assertTrue(transacoes.carregarSnapshot(t, UUID.randomUUID()).isEmpty());
        assertTrue(transacoes.carregarSnapshot(t, doc).isPresent());
    }
}
