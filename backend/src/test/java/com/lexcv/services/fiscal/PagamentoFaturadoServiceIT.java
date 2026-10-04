package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.DocumentoFiscalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Phase 134 (EMIS-01, EMIS-02, EMIS-07, EMIS-08, D-21): provas em PostgreSQL real do que o Mockito
 * não prova -- emissão tudo-ou-nada (três falhas injetadas: depois do pagamento e do documento, na
 * numeração e no INSERT do documento), paridade da pré-visualização sem efeitos, fotografia
 * imutável depois de editar o cliente e a configuração, mesma chave sequencial e dois emitentes
 * independentes.
 *
 * <p>Andaime de {@link ConfiguracaoFiscalConcorrenciaIT}: {@code @DataJpaTest} +
 * {@code Replace.NONE} + {@code @ServiceConnection}, testes {@code NOT_SUPPORTED} (o serviço é
 * dono da sua transação; os commits são reais) e tenants aleatórios. O relógio é fixo
 * (2026-06-15, meio-dia em Cabo Verde), para que "hoje" e o ano da série sejam determinísticos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PagamentoFaturadoService.class, PreVisualizacaoFaturaService.class, NumeracaoService.class,
        ParametroFiscalService.class, AuditoriaFiscalService.class, PagamentoFaturadoServiceIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PagamentoFaturadoServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AGORA = Instant.parse("2026-06-15T13:00:00Z");
    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

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
    }

    private static final String SEGREDO = "SEGREDO-PROFISSIONAL";
    private static final BigDecimal VALOR = new BigDecimal("120000.00");

    @Autowired
    private PagamentoFaturadoService service;

    @Autowired
    private PreVisualizacaoFaturaService preVisualizacao;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @MockitoSpyBean
    private AuditoriaFiscalService auditoria;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private FixturaEmissaoFiscal fixtura;

    /** Cenário de um tenant pronto a emitir. */
    private record Cenario(UUID tenantId, UUID clienteId, UUID processoId, Integer honorarioId) {
    }

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
    }

    @AfterEach
    void limparSpy() {
        Mockito.reset((Object) AopTestUtils.getUltimateTargetObject(auditoria));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private Cenario cenario(RegimeIva regime) {
        UUID tenant = fixtura.criarTenantComFaturacao(regime);
        return cenarioNoTenant(tenant, "P-2026/17");
    }

    private Cenario cenarioNoTenant(UUID tenant, String numeroProcesso) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, numeroProcesso);
        Integer honorario = fixtura.criarHonorario(processo, new BigDecimal("500000.00"),
                "Defesa no caso X -- " + SEGREDO);
        return new Cenario(tenant, cliente, processo, honorario);
    }

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private static PagamentoRequest pedido(Integer honorarioId, BigDecimal valor, UUID chave) {
        return new PagamentoRequest(honorarioId, valor, null, "TRANSFERENCIA", new BigDecimal("20"), chave);
    }

    private ResultadoPagamentoFaturado emitir(Cenario c, PagamentoRequest req) {
        return service.registar(c.tenantId(), autor(c.tenantId()), req);
    }

    private Map<String, Object> documento(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_documento_fiscal WHERE id = ?", id);
    }

    private static void assertDecimal(String esperado, Object real) {
        assertEquals(0, new BigDecimal(esperado).compareTo((BigDecimal) real), esperado + " != " + real);
    }

    /** Estado observável de um cenário (para provar rollbacks e ausência de efeitos). */
    private record Contadores(int pagamentos, int documentos, int linhas, int comunicacoes, int eventos,
                              BigDecimal saldo, Long ultimoNumero) {
    }

    private Contadores contadores(Cenario c) {
        return new Contadores(fixtura.contarPagamentos(c.honorarioId()), fixtura.contarDocumentos(c.tenantId()),
                fixtura.contarLinhas(c.tenantId()), fixtura.contarComunicacoes(c.tenantId()),
                fixtura.contarEventosEmissao(c.tenantId()), fixtura.saldo(c.clienteId()),
                fixtura.ultimoNumero(c.tenantId()));
    }

    private static void assertMesmoSaldo(BigDecimal antes, BigDecimal depois) {
        if (antes == null) {
            assertNull(depois);
        } else {
            assertEquals(0, antes.compareTo(depois), antes + " != " + depois);
        }
    }

    private static boolean cadeiaContem(Throwable t, String texto) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(texto)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------- a

    @Test
    void emiteFaturaReciboComValoresDoContexto() {
        Cenario c = cenario(RegimeIva.NORMAL);

        ResultadoPagamentoFaturado r = emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID()));

        assertTrue(r.novo());
        Map<String, Object> pag = jdbc.queryForMap("SELECT * FROM t_pagamento WHERE honorario_id = ?",
                c.honorarioId());
        assertDecimal("120000.00", pag.get("valor_pago"));
        assertEquals(HOJE, ((java.sql.Date) pag.get("data_pagamento")).toLocalDate());
        assertEquals("TRANSFERENCIA", pag.get("metodo"));
        assertEquals(r.resposta().id(), pag.get("id"));

        Map<String, Object> d = documento(r.resposta().documentoFiscal().id());
        assertDecimal("104347.83", d.get("total_base"));
        assertDecimal("15652.17", d.get("total_iva"));
        assertDecimal("20869.57", d.get("total_retencao"));
        assertDecimal("120000.00", d.get("total_documento"));
        assertDecimal("99130.43", d.get("valor_liquido"));
        assertEquals("SIM-FR-2026/1", d.get("numero_formatado"));
        assertEquals("FR", d.get("tipo"));
        assertEquals("SIMULADO", d.get("ambiente"));
        assertEquals("30", d.get("meio_pagamento_codigo"));
        assertEquals("Ana Emissora", d.get("emitido_por_nome"));
        assertEquals(c.clienteId(), d.get("cliente_id"));
        assertEquals(pag.get("id"), d.get("pagamento_id"));

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT * FROM t_documento_fiscal_linha WHERE documento_fiscal_id = ?", d.get("id"));
        String descricao = (String) linha.get("descricao");
        assertEquals("Honorários por serviços jurídicos — Processo n.º P-2026/17", descricao);
        assertFalse(descricao.contains(SEGREDO), "a descrição livre do honorário nunca chega ao documento");
        assertEquals(1, linha.get("numero_linha"));

        assertEquals("PENDENTE", jdbc.queryForObject(
                "SELECT estado FROM t_comunicacao_fiscal WHERE documento_fiscal_id = ?", String.class, d.get("id")));
        Contadores depois = contadores(c);
        assertEquals(1, depois.pagamentos());
        assertEquals(1, depois.documentos());
        assertEquals(1, depois.comunicacoes());
        assertEquals(1, depois.eventos());
        assertDecimal("120000.00", depois.saldo());
        assertEquals(1L, depois.ultimoNumero());
        assertEquals(d.get("id").toString(), jdbc.queryForObject(
                "SELECT entidade_id FROM t_audit_log WHERE tenant_id = ? AND acao = 'documento_fiscal_emitir'",
                String.class, c.tenantId()));
    }

    // ------------------------------------------------------------------------------- b

    @Test
    void escritorioIsento() {
        Cenario c = cenario(RegimeIva.ISENTO);

        ResultadoPagamentoFaturado r = emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID()));

        Map<String, Object> d = documento(r.resposta().documentoFiscal().id());
        assertDecimal("120000.00", d.get("total_base"));
        assertDecimal("0.00", d.get("total_iva"));
        assertDecimal("0", d.get("taxa_iva"));
        assertEquals("1", d.get("emitente_motivo_isencao_codigo"));
        assertEquals(MotivoIsencaoIva.porCodigo("1").orElseThrow().mencao(), d.get("emitente_motivo_isencao_mencao"));
        assertEquals("ISENTO", d.get("emitente_regime_iva"));
    }

    // ------------------------------------------------------------------------------- c

    @Test
    void rollbackQuandoAuditoriaFalha() {
        Cenario c = cenario(RegimeIva.NORMAL);
        fixtura.criarContaCorrente(c.clienteId(), new BigDecimal("500.00"));
        Contadores antes = contadores(c);
        // O spy fica atrás do proxy transacional (MANDATORY): o stub é feito no alvo, fora do proxy.
        AuditoriaFiscalService alvo = AopTestUtils.getUltimateTargetObject(auditoria);
        doThrow(new IllegalStateException("falha injetada na auditoria"))
                .when(alvo).registarEmissao(any(), any(), any(), any());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID())));

        assertEquals("falha injetada na auditoria", e.getMessage());
        Contadores depois = contadores(c);
        assertEquals(0, depois.pagamentos());
        assertEquals(0, depois.documentos());
        assertEquals(0, depois.linhas());
        assertEquals(0, depois.comunicacoes());
        assertEquals(0, depois.eventos());
        assertDecimal("500.00", depois.saldo());
        assertEquals(antes.ultimoNumero(), depois.ultimoNumero());
    }

    // ------------------------------------------------------------------------------- d

    @Test
    void rollbackQuandoNumeracaoFalha() throws Exception {
        Cenario c = cenario(RegimeIva.NORMAL);
        emitir(c, pedido(c.honorarioId(), new BigDecimal("1000.00"), UUID.randomUUID()));
        Contadores antes = contadores(c);
        assertEquals(1L, antes.ultimoNumero());

        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> segurador = executor.submit(() -> tx().executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM t_serie_fiscal WHERE tenant_id = ? FOR UPDATE", c.tenantId());
                bloqueada.countDown();
                try {
                    assertTrue(libertar.await(60, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(bloqueada.await(30, TimeUnit.SECONDS));

            RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                    () -> emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID())));

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            assertTrue(Set.of("SERIE_INDISPONIVEL", "FATURACAO_OCUPADA").contains(e.getCodigo()), e.getCodigo());
            libertar.countDown();
            segurador.get(30, TimeUnit.SECONDS);
        } finally {
            libertar.countDown();
            executor.shutdownNow();
        }

        // O pagamento foi gravado ANTES da numeração e tem de ter desaparecido no rollback.
        Contadores depois = contadores(c);
        assertEquals(antes, depois);
    }

    // ------------------------------------------------------------------------------- e

    @Test
    void rollbackQuandoInsertDoDocumentoFalha() {
        Cenario c = cenario(RegimeIva.NORMAL);
        emitir(c, pedido(c.honorarioId(), new BigDecimal("1000.00"), UUID.randomUUID()));
        UUID serieId = jdbc.queryForObject("SELECT id FROM t_serie_fiscal WHERE tenant_id = ?", UUID.class,
                c.tenantId());
        // Ocupa (tenant, série, 2): o próximo INSERT do documento viola uk_documento_fiscal_numero.
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia) VALUES (?, ?, 'FR', 'SIMULADO', ?, "
                        + "'SIM-FR-2026', 2026, 2, 'SIM-FR-2026/2', ?, ?, '512345679', 'X', 'Y', 'NORMAL', "
                        + "'234567891', 'Z', 'W', ?, ?, ?, -999, 'DINHEIRO', '10', 'CVE', 15, 1, 0, 0, 1, 1, ?)",
                UUID.randomUUID(), c.tenantId(), serieId, java.sql.Date.valueOf(HOJE), Timestamp.from(AGORA),
                c.clienteId(), c.processoId(), c.honorarioId(), UUID.randomUUID());
        Contadores antes = contadores(c);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID())));

        assertTrue(cadeiaContem(e, "uk_documento_fiscal_numero"), "esperada a violação do UNIQUE: " + e);
        Contadores depois = contadores(c);
        assertEquals(antes, depois, "só a linha pré-inserida existe; o resto voltou atrás");
        assertEquals(1, depois.pagamentos());
        assertEquals(1L, depois.ultimoNumero());
    }

    // ------------------------------------------------------------------------------- f

    @Test
    void preVisualizacaoIgualAEmissaoESemEfeitos() {
        Cenario c = cenario(RegimeIva.NORMAL);
        PagamentoRequest req = pedido(c.honorarioId(), VALOR, UUID.randomUUID());
        Contadores antes = contadores(c);

        PreVisualizacaoFaturaResponse p = preVisualizacao.preVisualizar(c.tenantId(), req);

        assertEquals(antes, contadores(c), "a pré-visualização não escreve nada");
        assertNull(fixtura.ultimoNumero(c.tenantId()));

        ResultadoPagamentoFaturado r = emitir(c, req);
        Map<String, Object> d = documento(r.resposta().documentoFiscal().id());
        assertEquals(0, p.base().compareTo((BigDecimal) d.get("total_base")));
        assertEquals(0, p.iva().compareTo((BigDecimal) d.get("total_iva")));
        assertEquals(0, p.retencao().compareTo((BigDecimal) d.get("total_retencao")));
        assertEquals(0, p.total().compareTo((BigDecimal) d.get("total_documento")));
        assertEquals(0, p.liquidoRecebido().compareTo((BigDecimal) d.get("valor_liquido")));
        assertEquals(p.adquirenteNif(), d.get("adquirente_nif"));
        assertEquals(p.descricaoLinha(), jdbc.queryForObject(
                "SELECT descricao FROM t_documento_fiscal_linha WHERE documento_fiscal_id = ?", String.class,
                d.get("id")));
    }

    // ------------------------------------------------------------------------------- g

    @Test
    void snapshotNaoMudaDepoisDeEditarClienteEConfiguracao() {
        Cenario c = cenario(RegimeIva.NORMAL);
        ResultadoPagamentoFaturado r = emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID()));
        Map<String, Object> antes = documento(r.resposta().documentoFiscal().id());

        jdbc.update("UPDATE t_cliente SET nome = 'Outro Nome', morada = 'Outra Morada', nif = '345678912' "
                + "WHERE id = ?", c.clienteId());
        jdbc.update("UPDATE t_configuracao_fiscal SET firma = 'Nova Firma', morada = 'Nova Morada' "
                + "WHERE tenant_id = ?", c.tenantId());

        Map<String, Object> depois = tx().execute(status -> {
            DocumentoFiscal d = documentoFiscalRepository
                    .findByIdAndTenantId(r.resposta().documentoFiscal().id(), c.tenantId()).orElseThrow();
            assertEquals("Maria Lopes", d.getAdquirenteNome());
            assertEquals(FixturaEmissaoFiscal.FIRMA, d.getEmitenteFirma());
            return documento(d.getId());
        });
        for (String coluna : new String[]{"adquirente_nome", "adquirente_morada", "adquirente_nif",
                "emitente_firma", "emitente_morada", "emitente_nif"}) {
            assertEquals(antes.get(coluna), depois.get(coluna), coluna);
        }
        assertEquals("Rua da Praia 5", depois.get("adquirente_morada"));
        assertEquals(FixturaEmissaoFiscal.MORADA_EMITENTE, depois.get("emitente_morada"));
    }

    // ------------------------------------------------------------------------------- h

    @Test
    void mesmaChaveSequencial() {
        Cenario c = cenario(RegimeIva.NORMAL);
        UUID chave = UUID.randomUUID();

        ResultadoPagamentoFaturado primeiro = emitir(c, pedido(c.honorarioId(), VALOR, chave));
        ResultadoPagamentoFaturado segundo = emitir(c, pedido(c.honorarioId(), VALOR, chave));

        assertTrue(primeiro.novo());
        assertFalse(segundo.novo());
        assertEquals(primeiro.resposta().id(), segundo.resposta().id());
        assertEquals(primeiro.resposta().documentoFiscal(), segundo.resposta().documentoFiscal());
        Contadores depois = contadores(c);
        assertEquals(1, depois.pagamentos());
        assertEquals(1, depois.documentos());
        assertDecimal("120000.00", depois.saldo());

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> emitir(c, pedido(c.honorarioId(), new BigDecimal("120000.01"), chave)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("CHAVE_REUTILIZADA", e.getCodigo());
        assertEquals(depois, contadores(c));
    }

    // ------------------------------------------------------------------------------- i

    @Test
    void doisEmitentes() {
        Cenario a = cenario(RegimeIva.NORMAL);
        Cenario b = cenario(RegimeIva.ISENTO);

        ResultadoPagamentoFaturado ra = emitir(a, pedido(a.honorarioId(), VALOR, UUID.randomUUID()));
        ResultadoPagamentoFaturado rb = emitir(b, pedido(b.honorarioId(), VALOR, UUID.randomUUID()));

        assertEquals("SIM-FR-2026/1", ra.resposta().documentoFiscal().numeroFormatado());
        assertEquals("SIM-FR-2026/1", rb.resposta().documentoFiscal().numeroFormatado());
        assertEquals(1L, fixtura.ultimoNumero(a.tenantId()));
        assertEquals(1L, fixtura.ultimoNumero(b.tenantId()));
        UUID serieA = (UUID) documento(ra.resposta().documentoFiscal().id()).get("serie_id");
        UUID serieB = (UUID) documento(rb.resposta().documentoFiscal().id()).get("serie_id");
        assertFalse(serieA.equals(serieB), "cada emitente tem a sua série");

        Page<DocumentoFiscal> listaA = tx().execute(s -> documentoFiscalRepository.buscar(a.tenantId(), null, null,
                null, null, null, PageRequest.of(0, 10)));
        Page<DocumentoFiscal> listaB = tx().execute(s -> documentoFiscalRepository.buscar(b.tenantId(), null, null,
                null, null, null, PageRequest.of(0, 10)));
        assertEquals(1, listaA.getTotalElements());
        assertEquals(1, listaB.getTotalElements());
        assertEquals(ra.resposta().documentoFiscal().id(), listaA.getContent().get(0).getId());
        assertEquals(rb.resposta().documentoFiscal().id(), listaB.getContent().get(0).getId());
    }

    // ------------------------------------------------------------------------------- j

    @Test
    void faturacaoDesligada() {
        UUID tenant = fixtura.criarTenant(RegimeIva.NORMAL, false);
        Cenario c = cenarioNoTenant(tenant, "P-9");

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> emitir(c, pedido(c.honorarioId(), VALOR, UUID.randomUUID())));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("FATURACAO_DESLIGADA", e.getCodigo());
        assertFalse(service.faturacaoAtiva(tenant));
        assertTrue(service.faturacaoAtiva(fixtura.criarTenantComFaturacao(RegimeIva.NORMAL)));
        Contadores depois = contadores(c);
        assertEquals(new Contadores(0, 0, 0, 0, 0, null, null), depois);
    }
}
