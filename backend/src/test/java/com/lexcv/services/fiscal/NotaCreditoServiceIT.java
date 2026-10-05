package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoNotaCreditoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.Honorario;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.services.RecebidoNoMes;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Phase 135 (NCRD-01, NCRD-02, NCRD-03): provas em PostgreSQL real do que o Mockito não prova
 * sobre a Nota de Crédito -- emissão tudo-ou-nada (incluindo o incremento da série da NC), coerência
 * dos quatro leitores de "pago" (saldo da conta corrente, {@code Honorario.totalPago}, contribuição
 * para o KPI mensal via {@link RecebidoNoMes#somar} e condição do alerta HONORARIO_ATRASADO) depois
 * de uma NC parcial e de uma NC total, série própria da NC, fotografia do adquirente, paridade da
 * pré-visualização, repetição pela chave e isolamento entre tenants.
 *
 * <p>Andaime de {@link PagamentoFaturadoServiceIT}: {@code @DataJpaTest} + {@code Replace.NONE} +
 * {@code @ServiceConnection}, testes {@code NOT_SUPPORTED} (o serviço é dono da sua transação; os
 * commits são reais), tenants aleatórios e relógio fixo (2026-06-15, meio-dia em Cabo Verde).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({NotaCreditoService.class, PagamentoFaturadoService.class, PreVisualizacaoFaturaService.class,
        NumeracaoService.class, ParametroFiscalService.class, AuditoriaFiscalService.class,
        NotaCreditoServiceIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotaCreditoServiceIT {

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

    private static final BigDecimal VALOR_FR = new BigDecimal("120000.00");
    private static final String TEXTO = "Desconto acordado com o cliente";

    @Autowired
    private NotaCreditoService service;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private HonorarioRepository honorarioRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @MockitoSpyBean
    private AuditoriaFiscalService auditoria;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private FixturaEmissaoFiscal fixtura;

    /** Um tenant com uma FR de 120 000 emitida (retenção 20 %) sobre um honorário de 120 000. */
    private record Cenario(UUID tenantId, UUID clienteId, UUID processoId, Integer honorarioId, UUID frId,
                           String frNumero) {
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

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private Cenario cenarioComFr(RegimeIva regime) {
        return cenarioComFrNoTenant(fixtura.criarTenantComFaturacao(regime));
    }

    private Cenario cenarioComFrNoTenant(UUID tenant) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-2026/17");
        Integer honorario = fixtura.criarHonorario(processo, VALOR_FR, "Defesa no caso X");
        ResultadoPagamentoFaturado fr = pagamentoFaturado.registar(tenant, autor(tenant),
                new PagamentoRequest(honorario, VALOR_FR, null, "TRANSFERENCIA", new BigDecimal("20"),
                        UUID.randomUUID()));
        return new Cenario(tenant, cliente, processo, honorario, fr.resposta().documentoFiscal().id(),
                fr.resposta().documentoFiscal().numeroFormatado());
    }

    private static NotaCreditoRequest parcial(String valor, UUID chave) {
        return new NotaCreditoRequest("PARCIAL", new BigDecimal(valor), "CORRECAO_VALOR", TEXTO, chave);
    }

    private static NotaCreditoRequest total(UUID chave) {
        return new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", TEXTO, chave);
    }

    private ResultadoNotaCredito emitir(Cenario c, NotaCreditoRequest req) {
        return service.emitir(c.tenantId(), autor(c.tenantId()), c.frId(), req);
    }

    private Map<String, Object> documento(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_documento_fiscal WHERE id = ?", id);
    }

    private static void assertDecimal(String esperado, Object real) {
        assertEquals(0, new BigDecimal(esperado).compareTo((BigDecimal) real), esperado + " != " + real);
    }

    /** Estado observável de um cenário (para provar rollbacks e ausência de efeitos). */
    private record Contadores(int notas, int estornos, int pagamentos, int documentos, int linhas,
                              int comunicacoes, int eventos, BigDecimal saldo, Long ultimoNumeroNc) {
    }

    private Contadores contadores(Cenario c) {
        return new Contadores(fixtura.contarNotasCredito(c.tenantId()), fixtura.contarEstornos(c.honorarioId()),
                fixtura.contarPagamentos(c.honorarioId()), fixtura.contarDocumentos(c.tenantId()),
                fixtura.contarLinhas(c.tenantId()), fixtura.contarComunicacoes(c.tenantId()),
                fixtura.contarEventosEmissao(c.tenantId()), fixtura.saldo(c.clienteId()),
                fixtura.ultimoNumeroNotaCredito(c.tenantId()));
    }

    /** Os quatro leitores de "pago", cada um pelo seu caminho real. */
    private record Leitores(BigDecimal saldo, BigDecimal totalPago, BigDecimal kpiMes, boolean alerta) {
    }

    private Leitores leitores(Cenario c) {
        // totalPago (@Formula) relido numa transação NOVA: o valor é avaliado no carregamento.
        Honorario h = tx().execute(s -> honorarioRepository.findById(c.honorarioId()).orElseThrow());
        assertNotNull(h);
        BigDecimal kpi = tx().execute(s -> RecebidoNoMes.somar(
                pagamentoRepository.findByHonorarioId(c.honorarioId()), YearMonth.from(HOJE)));
        // Condição do AlertasDiariosJob: o honorário só deixa de ser elegível quando totalPago >= valorTotal.
        boolean alerta = h.getTotalPago() == null || h.getTotalPago().compareTo(h.getValorTotal()) < 0;
        return new Leitores(fixtura.saldo(c.clienteId()), h.getTotalPago(), kpi, alerta);
    }

    private void assertLeitoresConcordam(Leitores l, String esperado) {
        assertDecimal(esperado, l.saldo());
        assertDecimal(esperado, l.totalPago());
        assertDecimal(esperado, l.kpiMes());
        assertEquals(0, l.saldo().compareTo(l.totalPago()), "saldo == totalPago");
        assertEquals(0, l.totalPago().compareTo(l.kpiMes()), "totalPago == contribuição ao KPI");
    }

    // ------------------------------------------------------------------------------- coerência

    @Test
    void coerenciaSaldoTotalPagoKpiEAlertaAposNcParcialETotal() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        Leitores inicial = leitores(c);
        assertLeitoresConcordam(inicial, "120000.00");
        assertFalse(inicial.alerta(), "honorário pago na totalidade não é elegível para o alerta");

        ResultadoNotaCredito p = emitir(c, parcial("20000.00", UUID.randomUUID()));

        assertTrue(p.novo());
        assertDecimal("20000.00", p.resposta().totalDocumento());
        assertDecimal("100000.00", p.resposta().valorCreditavelRestante());
        assertDecimal("-20000.00", p.resposta().estorno().valorPago());
        Leitores aposParcial = leitores(c);
        assertLeitoresConcordam(aposParcial, "100000.00");
        assertDecimal("100000.00", fixtura.totalPagoHonorario(c.honorarioId()));
        assertTrue(aposParcial.alerta(), "depois da NC parcial o honorário volta a ser elegível para o alerta");

        ResultadoNotaCredito t = emitir(c, total(UUID.randomUUID()));

        assertTrue(t.novo());
        assertDecimal("100000.00", t.resposta().totalDocumento());
        assertDecimal("0", t.resposta().valorCreditavelRestante());
        Leitores aposTotal = leitores(c);
        assertLeitoresConcordam(aposTotal, "0");
        assertDecimal("0", fixtura.totalPagoHonorario(c.honorarioId()));
        assertTrue(aposTotal.alerta());
        assertEquals(2, fixtura.contarEstornos(c.honorarioId()));
        assertEquals(3, fixtura.contarPagamentos(c.honorarioId()));

        // Esgotada: mais uma NC é recusada sem efeitos.
        Contadores antes = contadores(c);
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> emitir(c, parcial("0.01", UUID.randomUUID())));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("NC_EXCEDE_ORIGINAL", e.getCodigo());
        assertEquals(antes, contadores(c));
    }

    // ------------------------------------------------------------------------------- CR-01 (revisão)

    /**
     * Processo reatribuído a outro cliente depois da FR (como o fazia o {@code PUT /processos/{id}}
     * antes da guarda PROCESSO_COM_DOCUMENTOS_FISCAIS): a NC devolve o saldo ao cliente que a FR
     * creditou e regista esse cliente; o cliente atual do processo fica intacto.
     */
    @Test
    void processoReatribuidoDepoisDaFrDebitaOClienteDaFr() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID clienteA = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID clienteB = fixtura.criarCliente(tenant, "345678912", "João Tavares", "Rua do Sol 9");
        fixtura.criarContaCorrente(clienteA, new BigDecimal("500.00"));
        fixtura.criarContaCorrente(clienteB, new BigDecimal("700.00"));
        UUID processo = fixtura.criarProcesso(tenant, clienteA, "P-2026/18");
        Integer honorario = fixtura.criarHonorario(processo, VALOR_FR, "Defesa no caso Y");
        ResultadoPagamentoFaturado fr = pagamentoFaturado.registar(tenant, autor(tenant),
                new PagamentoRequest(honorario, VALOR_FR, null, "TRANSFERENCIA", new BigDecimal("20"),
                        UUID.randomUUID()));
        assertDecimal("120500.00", fixtura.saldo(clienteA));
        jdbc.update("UPDATE t_processo SET cliente_id = ? WHERE id = ?", clienteB, processo);

        ResultadoNotaCredito r = service.emitir(tenant, autor(tenant), fr.resposta().documentoFiscal().id(),
                total(UUID.randomUUID()));

        assertTrue(r.novo());
        assertDecimal("120000.00", r.resposta().totalDocumento());
        assertDecimal("500.00", fixtura.saldo(clienteA));
        assertDecimal("700.00", fixtura.saldo(clienteB));
        Map<String, Object> nc = documento(r.resposta().id());
        assertEquals(clienteA, nc.get("cliente_id"));
        assertEquals(processo, nc.get("processo_id"));
        assertEquals("234567891", nc.get("adquirente_nif"));
    }

    // ------------------------------------------------------------------------------- atomicidade

    @Test
    void rollbackTotalQuandoAuditoriaDaNcFalha() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        Contadores antes = contadores(c);
        assertNull(antes.ultimoNumeroNc());
        // O spy fica atrás do proxy transacional (MANDATORY): o stub é feito no alvo, fora do proxy.
        AuditoriaFiscalService alvo = AopTestUtils.getUltimateTargetObject(auditoria);
        doThrow(new IllegalStateException("falha injetada na auditoria da NC"))
                .when(alvo).registarEmissaoNotaCredito(any(), any(), any(), any(), any());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> emitir(c, parcial("20000.00", UUID.randomUUID())));

        assertEquals("falha injetada na auditoria da NC", e.getMessage());
        Contadores depois = contadores(c);
        assertEquals(antes, depois, "estorno, débito, documento, linha, comunicação e série voltaram atrás");
        assertEquals(0, depois.notas());
        assertEquals(0, depois.estornos());
        assertDecimal("120000.00", depois.saldo());
        assertTrue(depois.ultimoNumeroNc() == null || depois.ultimoNumeroNc() == 0L,
                "a série da NC não foi incrementada: " + depois.ultimoNumeroNc());

        Mockito.reset(alvo);
        ResultadoNotaCredito ok = emitir(c, parcial("20000.00", UUID.randomUUID()));
        assertEquals("SIM-NC-2026/1", ok.resposta().numeroFormatado(), "a próxima NC com sucesso é a n.º 1");
    }

    // ------------------------------------------------------------------------------- numeração

    @Test
    void serieDaNcEhIndependenteDaSerieDaFr() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        // Mais duas FR no mesmo tenant: a série FR vai em 3.
        Cenario c2 = cenarioComFrNoTenant(c.tenantId());
        Cenario c3 = cenarioComFrNoTenant(c.tenantId());
        assertEquals("SIM-FR-2026/1", c.frNumero());
        assertEquals("SIM-FR-2026/3", c3.frNumero());

        ResultadoNotaCredito n1 = emitir(c, parcial("1000.00", UUID.randomUUID()));
        ResultadoNotaCredito n2 = emitir(c2, total(UUID.randomUUID()));

        assertEquals("SIM-NC-2026/1", n1.resposta().numeroFormatado());
        assertEquals("SIM-NC-2026/2", n2.resposta().numeroFormatado());
        assertEquals(List.of(1L, 2L), fixtura.numerosNotasCredito(c.tenantId()));
        assertEquals(List.of(1L, 2L, 3L), fixtura.numerosFaturasRecibo(c.tenantId()));
        assertEquals(3L, fixtura.ultimoNumero(c.tenantId()), "a série FR não avançou com as NC");
        assertEquals(2L, fixtura.ultimoNumeroNotaCredito(c.tenantId()));
        Map<String, Object> d1 = documento(n1.resposta().id());
        assertEquals("NC", d1.get("tipo"));
        assertEquals("SIM-NC-2026", d1.get("serie_codigo"));
        assertEquals(c.frId(), d1.get("documento_origem_id"));
        assertFalse(d1.get("serie_id").equals(documento(c.frId()).get("serie_id")), "séries diferentes");
    }

    // ------------------------------------------------------------------------------- fotografia

    @Test
    void adquirenteDaNcEhAFotografiaDaFr() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        Map<String, Object> fr = documento(c.frId());
        jdbc.update("UPDATE t_cliente SET nome = 'Outro Nome', morada = 'Outra Morada' WHERE id = ?", c.clienteId());
        jdbc.update("UPDATE t_configuracao_fiscal SET firma = 'Nova Firma' WHERE tenant_id = ?", c.tenantId());

        ResultadoNotaCredito r = emitir(c, parcial("5000.00", UUID.randomUUID()));

        Map<String, Object> nc = documento(r.resposta().id());
        for (String coluna : new String[]{"adquirente_nome", "adquirente_morada", "adquirente_nif",
                "emitente_firma", "emitente_morada", "emitente_nif", "emitente_regime_iva"}) {
            assertEquals(fr.get(coluna), nc.get(coluna), coluna);
        }
        assertEquals("Maria Lopes", nc.get("adquirente_nome"));
        assertEquals("Rua da Praia 5", nc.get("adquirente_morada"));
        assertEquals(FixturaEmissaoFiscal.FIRMA, nc.get("emitente_firma"));
    }

    // ------------------------------------------------------------------------------- pré-visualização

    @Test
    void preVisualizacaoIgualAEmissaoESemEfeitos() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        NotaCreditoRequest req = parcial("20000.00", UUID.randomUUID());
        Contadores antes = contadores(c);

        PreVisualizacaoNotaCreditoResponse p = service.preVisualizar(c.tenantId(), c.frId(), req);

        assertEquals(antes, contadores(c), "a pré-visualização não escreve nada");
        assertNull(fixtura.ultimoNumeroNotaCredito(c.tenantId()));

        ResultadoNotaCredito r = emitir(c, req);
        Map<String, Object> d = documento(r.resposta().id());
        assertEquals(0, p.base().compareTo((BigDecimal) d.get("total_base")));
        assertEquals(0, p.iva().compareTo((BigDecimal) d.get("total_iva")));
        assertEquals(0, p.retencao().compareTo((BigDecimal) d.get("total_retencao")));
        assertEquals(0, p.total().compareTo((BigDecimal) d.get("total_documento")));
        assertEquals(0, p.liquido().compareTo((BigDecimal) d.get("valor_liquido")));
        assertEquals(0, p.valorCreditavelDepois().compareTo(r.resposta().valorCreditavelRestante()));
        assertEquals(p.adquirenteNif(), d.get("adquirente_nif"));
        assertEquals(p.adquirenteNome(), d.get("adquirente_nome"));
        assertEquals(p.dataEmissao(), ((java.sql.Date) d.get("data_emissao")).toLocalDate());
        assertEquals(p.descricaoLinha(), jdbc.queryForObject(
                "SELECT descricao FROM t_documento_fiscal_linha WHERE documento_fiscal_id = ?", String.class,
                d.get("id")));
        assertEquals(c.frNumero(), p.documentoOrigemNumero());
    }

    // ------------------------------------------------------------------------------- repetição

    @Test
    void mesmaChaveMesmoPedidoDevolveAMesmaNc() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        UUID chave = UUID.randomUUID();

        ResultadoNotaCredito primeiro = emitir(c, parcial("20000.00", chave));
        ResultadoNotaCredito segundo = emitir(c, parcial("20000.00", chave));

        assertTrue(primeiro.novo());
        assertFalse(segundo.novo());
        assertEquals(primeiro.resposta().id(), segundo.resposta().id());
        assertEquals(primeiro.resposta().estorno().id(), segundo.resposta().estorno().id());
        assertEquals(1, fixtura.contarNotasCredito(c.tenantId()));
        assertEquals(1, fixtura.contarEstornos(c.honorarioId()));
        assertDecimal("100000.00", fixtura.saldo(c.clienteId()));
    }

    @Test
    void mesmaChaveOutroValorRecusaChaveReutilizada() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        UUID chave = UUID.randomUUID();
        emitir(c, parcial("20000.00", chave));
        Contadores antes = contadores(c);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> emitir(c, parcial("20000.01", chave)));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("CHAVE_REUTILIZADA", e.getCodigo());
        assertEquals(antes, contadores(c));
    }

    // ------------------------------------------------------------------------------- isolamento

    @Test
    void outroTenantNaoVeNemCreditaAFr() {
        Cenario a = cenarioComFr(RegimeIva.NORMAL);
        UUID tenantB = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Contadores antes = contadores(a);

        RecusaFiscalException emissao = assertThrows(RecusaFiscalException.class,
                () -> service.emitir(tenantB, autor(tenantB), a.frId(), parcial("1000.00", UUID.randomUUID())));
        RecusaFiscalException previa = assertThrows(RecusaFiscalException.class,
                () -> service.preVisualizar(tenantB, a.frId(), parcial("1000.00", null)));

        for (RecusaFiscalException e : List.of(emissao, previa)) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
            assertEquals("DOCUMENTO_FISCAL_NAO_ENCONTRADO", e.getCodigo());
        }
        assertEquals(antes, contadores(a));
        assertEquals(0, fixtura.contarDocumentos(tenantB));
        assertNull(fixtura.ultimoNumeroNotaCredito(tenantB));
    }

    @Test
    void ncSobreNcRecusada() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        ResultadoNotaCredito nc = emitir(c, parcial("1000.00", UUID.randomUUID()));
        Contadores antes = contadores(c);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.emitir(c.tenantId(), autor(c.tenantId()), nc.resposta().id(),
                        parcial("100.00", UUID.randomUUID())));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals("NC_SOBRE_NC", e.getCodigo());
        assertEquals(antes, contadores(c));
    }

    // ------------------------------------------------------------------------------- faturação desligada

    @Test
    void faturacaoDesligadaDepoisDeUmaNcRepeteAGuardadaERecusaNova() {
        Cenario c = cenarioComFr(RegimeIva.NORMAL);
        UUID chave = UUID.randomUUID();
        ResultadoNotaCredito primeiro = emitir(c, parcial("20000.00", chave));
        jdbc.update("UPDATE t_configuracao_fiscal SET ativa = false WHERE tenant_id = ?", c.tenantId());
        Contadores antes = contadores(c);

        ResultadoNotaCredito repetido = emitir(c, parcial("20000.00", chave));

        assertFalse(repetido.novo());
        assertEquals(primeiro.resposta().id(), repetido.resposta().id());
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> emitir(c, parcial("1000.00", UUID.randomUUID())));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("FATURACAO_DESLIGADA", e.getCodigo());
        assertEquals(antes, contadores(c));
    }
}
