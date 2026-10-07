package com.lexcv.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.EfaturaConfig;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import com.lexcv.repositories.FilaEntregaEmail;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.fiscal.AuditoriaFiscalService;
import com.lexcv.services.fiscal.ComunicacaoFiscalTransacoes;
import com.lexcv.services.fiscal.EnfileiramentoEntregaEmail;
import com.lexcv.services.fiscal.PdfDocumentoFiscalService;
import com.lexcv.services.fiscal.FixturaEmissaoFiscal;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 136 (DFE-04): com {@code app.efatura.simulado.falhas-forcadas=true} (contexto próprio),
 * uma FR válida falha de forma transitória e fica {@code PENDENTE} com a próxima tentativa daqui a
 * 30 s, o XML já gravado (reutilizado na próxima tentativa) e nenhuma notificação.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "app.efatura.simulado.falhas-forcadas=true")
@Import({FiscalOutboxJob.class, ProcessadorComunicacaoFiscal.class, ComunicacaoFiscalTransacoes.class,
        EnfileiramentoEntregaEmail.class, FilaEntregaEmail.class,
        FilaComunicacaoFiscal.class, DfeXmlBuilder.class, DfeMarshaller.class, DfeValidador.class, IudGerador.class,
        EfaturaConfig.class, NotificacaoComunicacaoFiscal.class, NotificacaoService.class,
        PagamentoFaturadoService.class, PreVisualizacaoFaturaService.class, NumeracaoService.class,
        ParametroFiscalService.class, AuditoriaFiscalService.class, FiscalOutboxJobFalhasForcadasIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FiscalOutboxJobFalhasForcadasIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AGORA = Instant.parse("2026-06-15T13:00:00Z");
    static final BigDecimal VALOR = new BigDecimal("1150.00");

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

    @Autowired
    private FiscalOutboxJob job;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ResolucaoPapeisService resolucaoPapeis;

    /** Phase 137: o PDF/MinIO é coberto pelos testes de 137-10; aqui só a chamada depois do aceite. */
    @MockitoBean
    private PdfDocumentoFiscalService pdfDocumentoFiscalService;

    private FixturaEmissaoFiscal fixtura;

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
        jdbc.update("DELETE FROM t_comunicacao_fiscal");
    }

    @Test
    void frValidaComFalhasForcadasFicaPendenteComRecuoDe30Segundos() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Maria Lopes", "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-2026/9");
        Integer honorario = fixtura.criarHonorario(processo, VALOR, "Consulta");
        UserPrincipal autor = UserPrincipal.create(UUID.randomUUID(), tenant, "Ana", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
        UUID fr = pagamentoFaturado.registar(tenant, autor, new PagamentoRequest(honorario, VALOR, null,
                "DINHEIRO", null, UUID.randomUUID())).resposta().documentoFiscal().id();

        assertThat(job.executarUmaVez()).isEqualTo(1);

        Map<String, Object> c = fixtura.linhaComunicacao(fr);
        assertThat(c.get("estado")).isEqualTo("PENDENTE");
        assertThat(((Number) c.get("tentativas")).intValue()).isEqualTo(1);
        assertThat(c.get("ultimo_erro_codigo")).isEqualTo("FALHA_SIMULADA");
        assertThat(((Timestamp) c.get("proxima_tentativa_em")).toInstant()).isEqualTo(AGORA.plus(Duration.ofSeconds(30)));
        assertThat(c.get("concluido_em")).isNull();
        assertThat(c.get("lease_ate")).isNull();
        assertThat(fixtura.linhaXml(fr)).isPresent();
        assertThat(fixtura.contarNotificacoes(tenant, NotificacaoComunicacaoFiscal.CATEGORIA)).isZero();

        // Ainda não é devida: o mesmo instante não a volta a reclamar.
        assertThat(job.executarUmaVez()).isZero();
    }
}
