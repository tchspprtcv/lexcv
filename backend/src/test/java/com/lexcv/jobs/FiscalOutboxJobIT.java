package com.lexcv.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.EfaturaConfig;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.User;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.fiscal.AuditoriaFiscalService;
import com.lexcv.services.fiscal.ComunicacaoFiscalTransacoes;
import com.lexcv.services.fiscal.FixturaEmissaoFiscal;
import com.lexcv.services.fiscal.NotaCreditoService;
import com.lexcv.services.fiscal.NotificacaoComunicacaoFiscal;
import com.lexcv.services.fiscal.NumeracaoService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import com.lexcv.services.fiscal.ParametroFiscalService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import com.lexcv.services.fiscal.ProcessadorComunicacaoFiscal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 136 (DFE-01, DFE-02, DFE-04, DFE-06, DFE-07): o fluxo de comunicação em segundo plano em
 * PostgreSQL real -- emissão real (FR por {@link PagamentoFaturadoService}, NC por
 * {@link NotaCreditoService}) -> o job -> XML + IUD -> {@code ACEITE_SIMULADO}; ordem FR antes de
 * NC na mesma execução; escritórios suspensos processados; {@code ERRO} com notificação só aos
 * titulares de {@code financeiro:manage} do escritório, uma vez por episódio.
 *
 * <p>O job é chamado diretamente ({@code executarUmaVez()}), sem esperar pelo scheduler (o slice
 * JPA não ativa {@code @EnableScheduling}). {@link ResolucaoPapeisService} é simulado e devolve as
 * permissões efetivas por id de utilizador; os utilizadores são linhas reais, por isso a
 * verificação de tenant de {@link NotificacaoService#criar} e o {@code ON CONFLICT} correm de
 * verdade.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({FiscalOutboxJob.class, ProcessadorComunicacaoFiscal.class, ComunicacaoFiscalTransacoes.class,
        FilaComunicacaoFiscal.class, DfeXmlBuilder.class, DfeMarshaller.class, DfeValidador.class, IudGerador.class,
        EfaturaConfig.class, NotificacaoComunicacaoFiscal.class, NotificacaoService.class,
        PagamentoFaturadoService.class, NotaCreditoService.class, PreVisualizacaoFaturaService.class,
        NumeracaoService.class, ParametroFiscalService.class, AuditoriaFiscalService.class,
        FiscalOutboxJobIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FiscalOutboxJobIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AGORA = Instant.parse("2026-06-15T13:00:00Z");
    static final RelogioMovel RELOGIO = new RelogioMovel(AGORA);
    static final String CATEGORIA = NotificacaoComunicacaoFiscal.CATEGORIA;
    static final BigDecimal VALOR = new BigDecimal("120000.00");

    /** Relógio de teste que avança quando o teste manda (ordem de criação FR -> NC). */
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

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private FiscalOutboxJob job;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private NotaCreditoService notaCredito;

    @Autowired
    private FilaComunicacaoFiscal fila;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ResolucaoPapeisService resolucaoPapeis;

    private final Map<UUID, Set<String>> permissoes = new ConcurrentHashMap<>();
    private final DfeValidador validador = new DfeValidador();
    private FixturaEmissaoFiscal fixtura;

    @BeforeEach
    void preparar() {
        RELOGIO.definir(AGORA);
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
        // A reclamação é multi-tenant: cada teste começa com a fila vazia.
        jdbc.update("DELETE FROM t_comunicacao_fiscal");
        when(resolucaoPapeis.resolverNomesPapeis(any())).thenReturn(Set.of());
        when(resolucaoPapeis.resolverPermissoesEfectivas(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return permissoes.getOrDefault(u.getId(), Set.of());
        });
    }

    // ------------------------------------------------------------------ apoio

    private record Fr(UUID tenantId, UUID id, String numero) {
    }

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    private Fr emitirFr(UUID tenant) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-2026/" + UUID.randomUUID().toString().substring(0, 6));
        Integer honorario = fixtura.criarHonorario(processo, VALOR, "Defesa no caso X");
        var r = pagamentoFaturado.registar(tenant, autor(tenant),
                new PagamentoRequest(honorario, VALOR, null, "TRANSFERENCIA", new BigDecimal("20"), UUID.randomUUID()));
        return new Fr(tenant, r.resposta().documentoFiscal().id(), r.resposta().documentoFiscal().numeroFormatado());
    }

    private UUID emitirNc(Fr fr) {
        RELOGIO.definir(RELOGIO.instant().plusSeconds(1));
        var r = notaCredito.emitir(fr.tenantId(), autor(fr.tenantId()), fr.id(),
                new NotaCreditoRequest("PARCIAL", new BigDecimal("20000.00"), "CORRECAO_VALOR",
                        "Desconto acordado com o cliente", UUID.randomUUID()));
        return r.resposta().id();
    }

    private UUID utilizador(UUID tenant, boolean ativo, String... perms) {
        UUID id = fixtura.criarUtilizador(tenant, UUID.randomUUID() + "@example.cv", ativo);
        permissoes.put(id, Set.of(perms));
        return id;
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : ((Timestamp) valor).toInstant();
    }

    private static String sha256(String xml) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private void reprocessar(UUID tenant, UUID documentoId) {
        Integer n = new TransactionTemplate(transactionManager)
                .execute(s -> fila.reporPendente(tenant, documentoId, RELOGIO.instant()));
        assertThat(n).isEqualTo(1);
    }

    // ------------------------------------------------------------------ testes

    @Test
    void frEmitidaFicaPendenteSemXmlEUmaExecucaoAceitaComXmlValidoEIudCv3() throws Exception {
        Fr fr = emitirFr(fixtura.criarTenantComFaturacao(RegimeIva.NORMAL));

        // A emissão não comunicou: só a linha PENDENTE do outbox.
        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("PENDENTE");
        assertThat(fixtura.linhaXml(fr.id())).isEmpty();

        assertThat(job.executarUmaVez()).isEqualTo(1);

        Map<String, Object> c = fixtura.linhaComunicacao(fr.id());
        assertThat(c.get("estado")).isEqualTo("ACEITE_SIMULADO");
        assertThat(((Number) c.get("tentativas")).intValue()).isEqualTo(1);
        assertThat(instante(c.get("concluido_em"))).isEqualTo(AGORA);
        assertThat(c.get("ultimo_erro")).isNull();
        assertThat(c.get("lease_ate")).isNull();

        Map<String, Object> x = fixtura.linhaXml(fr.id()).orElseThrow();
        String iud = (String) x.get("iud");
        String xml = (String) x.get("xml");
        assertThat(iud).startsWith("CV3").hasSize(45);
        assertThat(((Number) x.get("repositorio_codigo")).intValue()).isEqualTo(3);
        assertThat(((Number) x.get("led_codigo")).intValue()).isEqualTo(99999);
        assertThat(x.get("versao_formato")).isEqualTo("2024-05-27");
        assertThat(x.get("ambiente")).isEqualTo("SIMULADO");
        assertThat(x.get("tenant_id")).isEqualTo(fr.tenantId());
        assertThat(x.get("xml_sha256")).isEqualTo(sha256(xml));
        assertThat(validador.validar(xml.getBytes(StandardCharsets.UTF_8)).valido()).isTrue();
        assertThat(xml).contains("Id=\"" + iud + "\"");

        // Uma segunda execução não tem nada para fazer.
        assertThat(job.executarUmaVez()).isZero();
    }

    @Test
    void frENcNumaSoExecucao_ambasAceitesEANcReferenciaOIudDaFr() {
        Fr fr = emitirFr(fixtura.criarTenantComFaturacao(RegimeIva.NORMAL));
        UUID nc = emitirNc(fr);
        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("PENDENTE");

        assertThat(job.executarUmaVez()).isEqualTo(2);

        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("ACEITE_SIMULADO");
        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ACEITE_SIMULADO");
        String iudFr = (String) fixtura.linhaXml(fr.id()).orElseThrow().get("iud");
        String xmlNc = (String) fixtura.linhaXml(nc).orElseThrow().get("xml");
        assertThat(validador.validar(xmlNc.getBytes(StandardCharsets.UTF_8)).valido()).isTrue();
        assertThat(xmlNc).containsPattern("<([a-zA-Z0-9]+:)?References>.*<([a-zA-Z0-9]+:)?FiscalDocument>"
                + iudFr + "</");
        assertThat((String) fixtura.linhaXml(nc).orElseThrow().get("iud")).startsWith("CV3").hasSize(45)
                .isNotEqualTo(iudFr);
    }

    @Test
    void ncCujaFrFoiRejeitadaERejeitadaNaPrimeiraTentativa() {
        // WR-05: a FR REJEITADO nunca terá XML, por isso a NC não pode esperar pelo IUD de origem.
        Fr fr = emitirFr(fixtura.criarTenantComFaturacao(RegimeIva.NORMAL));
        UUID nc = emitirNc(fr);
        jdbc.update("UPDATE t_comunicacao_fiscal SET estado = 'REJEITADO', ultimo_erro_codigo = 'TEXTO_INVALIDO', "
                + "concluido_em = now() WHERE documento_fiscal_id = ?", fr.id());

        assertThat(job.executarUmaVez()).isEqualTo(1);

        Map<String, Object> c = fixtura.linhaComunicacao(nc);
        assertThat(c.get("estado")).isEqualTo("REJEITADO");
        assertThat(c.get("ultimo_erro_codigo")).isEqualTo("ORIGEM_REJEITADA");
        assertThat(((Number) c.get("tentativas")).intValue()).isEqualTo(1);
        assertThat(fixtura.linhaXml(nc)).isEmpty();
    }

    @Test
    void escritorioSuspensoDepoisDaEmissaoEProcessado() {
        Fr fr = emitirFr(fixtura.criarTenantComFaturacao(RegimeIva.NORMAL));
        fixtura.suspenderTenant(fr.tenantId());

        job.executarUmaVez();

        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("ACEITE_SIMULADO");
    }

    @Test
    void falhaPersistenteChegaAErroENotificaSoOsGestoresDoEscritorioUmaVez() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID gestor1 = utilizador(tenant, true, "financeiro:view", "financeiro:edit", "financeiro:manage");
        UUID gestor2 = utilizador(tenant, true, "financeiro:manage");
        UUID inativo = utilizador(tenant, false, "financeiro:manage");
        UUID leitor = utilizador(tenant, true, "financeiro:view");
        UUID outroTenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID gestorOutro = utilizador(outroTenant, true, "financeiro:manage");

        Fr fr = emitirFr(tenant);
        UUID nc = emitirNc(fr);
        // A FR fica fora desta execução: a NC não encontra o IUD da origem.
        fixtura.adiarComunicacao(fr.id(), AGORA.plus(Duration.ofDays(1)));
        fixtura.forcarTentativas(nc, 7);
        // Outro escritório com uma FR válida no mesmo lote: a falha da NC não para o lote.
        Fr frOutro = emitirFr(outroTenant);

        assertThat(job.executarUmaVez()).isEqualTo(2);

        Map<String, Object> c = fixtura.linhaComunicacao(nc);
        assertThat(c.get("estado")).isEqualTo("ERRO");
        assertThat(c.get("ultimo_erro_codigo")).isEqualTo("ORIGEM_SEM_IUD");
        assertThat(c.get("ultimo_erro")).isEqualTo("A fatura-recibo de origem ainda não foi comunicada.");
        assertThat(((Number) c.get("tentativas")).intValue()).isEqualTo(8);
        assertThat(fixtura.linhaXml(nc)).isEmpty();
        assertThat(fixtura.estadoComunicacao(frOutro.id())).isEqualTo("ACEITE_SIMULADO");

        assertThat(fixtura.contarNotificacoesDe(gestor1, CATEGORIA)).isEqualTo(1);
        assertThat(fixtura.contarNotificacoesDe(gestor2, CATEGORIA)).isEqualTo(1);
        assertThat(fixtura.contarNotificacoesDe(inativo, CATEGORIA)).isZero();
        assertThat(fixtura.contarNotificacoesDe(leitor, CATEGORIA)).isZero();
        assertThat(fixtura.contarNotificacoesDe(gestorOutro, CATEGORIA)).isZero();
        assertThat(fixtura.contarNotificacoes(tenant, CATEGORIA)).isEqualTo(2);
        assertThat(fixtura.contarNotificacoes(outroTenant, CATEGORIA)).isZero();
        assertThat(jdbc.queryForObject("SELECT entidade_id FROM t_notificacao WHERE destinatario_id = ?",
                String.class, gestor1)).isEqualTo(nc + ":0");

        // Nova execução: a NC está em ERRO (não é reclamada) e nada novo é criado.
        assertThat(job.executarUmaVez()).isZero();
        assertThat(fixtura.contarNotificacoes(tenant, CATEGORIA)).isEqualTo(2);
    }

    @Test
    void novaFalhaDepoisDeUmReprocessamentoNotificaDeNovo() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID gestor = utilizador(tenant, true, "financeiro:manage");
        UUID leitor = utilizador(tenant, true, "financeiro:view");
        Fr fr = emitirFr(tenant);
        UUID nc = emitirNc(fr);
        fixtura.adiarComunicacao(fr.id(), AGORA.plus(Duration.ofDays(1)));
        fixtura.forcarTentativas(nc, 7);
        job.executarUmaVez();
        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ERRO");
        assertThat(fixtura.contarNotificacoesDe(gestor, CATEGORIA)).isEqualTo(1);

        // Reprocessamento manual (episódio 1) e nova falha persistente.
        reprocessar(tenant, nc);
        Map<String, Object> reposta = fixtura.linhaComunicacao(nc);
        assertThat(reposta.get("estado")).isEqualTo("PENDENTE");
        assertThat(((Number) reposta.get("reprocessamentos")).intValue()).isEqualTo(1);
        fixtura.forcarTentativas(nc, 7);
        job.executarUmaVez();

        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ERRO");
        assertThat(fixtura.contarNotificacoesDe(gestor, CATEGORIA)).isEqualTo(2);
        assertThat(fixtura.contarNotificacoesDe(leitor, CATEGORIA)).isZero();
        assertThat(jdbc.queryForList("SELECT entidade_id FROM t_notificacao WHERE destinatario_id = ? "
                + "ORDER BY entidade_id", String.class, gestor)).containsExactly(nc + ":0", nc + ":1");
    }

    @Test
    void depoisDeAFrSerAceiteONcReprocessadaEAceiteComOMesmoIudDaOrigem() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Fr fr = emitirFr(tenant);
        UUID nc = emitirNc(fr);
        fixtura.adiarComunicacao(fr.id(), AGORA.plus(Duration.ofDays(1)));
        fixtura.forcarTentativas(nc, 7);
        job.executarUmaVez();
        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ERRO");

        // A FR fica devida, é aceite; depois o reprocessamento da NC encontra o IUD.
        fixtura.adiarComunicacao(fr.id(), AGORA);
        job.executarUmaVez();
        assertThat(fixtura.estadoComunicacao(fr.id())).isEqualTo("ACEITE_SIMULADO");
        reprocessar(tenant, nc);
        job.executarUmaVez();

        assertThat(fixtura.estadoComunicacao(nc)).isEqualTo("ACEITE_SIMULADO");
        String iudFr = (String) fixtura.linhaXml(fr.id()).orElseThrow().get("iud");
        assertThat((String) fixtura.linhaXml(nc).orElseThrow().get("xml")).contains(iudFr);
    }
}
