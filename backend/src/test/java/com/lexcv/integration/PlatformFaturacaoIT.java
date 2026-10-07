package com.lexcv.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.MinioProperties;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.CriarNotaCreditoSubscricaoRequest;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.RegistarPagamentoSubscricaoRequest;
import com.lexcv.dtos.SubscricaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.AuditLogRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.PagamentoSubscricaoRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.services.StorageService;
import com.lexcv.services.fiscal.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({
        PlatformFaturacaoConfigService.class,
        ConfiguracaoFiscalService.class,
        SubscricaoFaturadaService.class,
        SubscricaoNotaCreditoService.class,
        PlatformDocumentoFiscalService.class,
        DocumentoFiscalService.class,
        DescargaDocumentoFiscalService.class,
        DescargaDocumentoFiscalTransacoes.class,
        PdfDocumentoFiscalService.class,
        PdfDocumentoFiscalTransacoes.class,
        PdfDocumentoFiscalRenderer.class,
        NumeracaoService.class,
        ParametroFiscalService.class,
        AuditoriaFiscalService.class,
        PlatformFaturacaoIT.Apoio.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PlatformFaturacaoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");

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
            return new EmailProperties(
                    new EmailProperties.Smtp("smtp.example.cv", 587, null, null, "faturacao@lexcv.cv", true,
                            Duration.ofSeconds(10), Duration.ofSeconds(20)),
                    new EmailProperties.Outbox(Duration.ofSeconds(30), Duration.ofSeconds(40), 10, Duration.ofMinutes(2)));
        }

        @Bean
        MinioProperties minioProperties() {
            MinioProperties props = new MinioProperties();
            props.setPresignedUrlExpiry(300);
            return props;
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlatformFaturacaoConfigService platformConfigService;

    @Autowired
    private SubscricaoFaturadaService subscricaoFaturadaService;

    @Autowired
    private SubscricaoNotaCreditoService subscricaoNotaCreditoService;

    @Autowired
    private PlatformDocumentoFiscalService platformDocumentoFiscalService;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @Autowired
    private PagamentoSubscricaoRepository pagamentoSubscricaoRepository;

    @MockitoBean
    private StorageService storageService;

    private FixturaEmissaoFiscal fixtura;
    private UUID lexcvTenantId;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setup() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();

        when(storageService.presignedDownloadUrl(anyString(), anyString()))
                .thenReturn("https://minio.lexcv.cv/downloads/test.pdf");

        // Garantir tenant reservada LexCV
        var opt = tenantRepository.findFirstByNome("LexCV");
        if (opt.isEmpty()) {
            lexcvTenantId = UUID.randomUUID();
            jdbc.update("INSERT INTO t_tenant (id, nome, plano, ativo, created_at) VALUES (?, 'LexCV', 'ENTERPRISE', true, now())",
                    lexcvTenantId);
        } else {
            lexcvTenantId = opt.get().getId();
        }

        adminPrincipal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(lexcvTenantId)
                .nome("Admin Plataforma")
                .email("admin@lexcv.cv")
                .build();
    }

    @Test
    void fluxoCompletoFaturacaoPlataformaSubscricaoENC() {
        // 1. Configurar dados fiscais da plataforma LexCV
        ConfiguracaoFiscalRequest configReq = new ConfiguracaoFiscalRequest(
                "500123456",
                "LexCV Tecnologias Lda",
                "Av. Cidade de Lisboa, 100",
                "Praia",
                "financeiro@lexcv.cv",
                "+238 261 00 00",
                RegimeIva.NORMAL,
                null
        );
        ConfiguracaoFiscalResponse configResp = platformConfigService.guardarConfiguracao(adminPrincipal, configReq);
        assertTrue(configResp.completa());
        assertTrue(configResp.ativa());
        assertTrue(platformConfigService.prontaParaEmitir());

        // 2. Criar um escritório cliente adquirente
        UUID officeTenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_tenant (id, nome, plano, ativo, created_at) VALUES (?, 'Escritório Alfa', 'STARTER', true, now())",
                officeTenantId);

        // 3. Registar pagamento de subscrição -> Emissão FR
        RegistarPagamentoSubscricaoRequest payReq = new RegistarPagamentoSubscricaoRequest(
                officeTenantId,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 6, 15),
                "TRANSFERENCIA",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                "STARTER",
                UUID.randomUUID()
        );

        SubscricaoFaturaResponse fr = subscricaoFaturadaService.registarPagamentoFaturado(adminPrincipal, payReq);
        assertNotNull(fr);
        assertEquals(TipoDocumentoFiscal.FR, fr.tipo());
        assertEquals("SIM-FR-2026", fr.serieCodigo());
        assertEquals(1L, fr.numero());
        assertEquals("SIM-FR-2026/1", fr.numeroFormatado());
        assertEquals(new BigDecimal("5000.00"), fr.totalDocumento());
        assertEquals(EstadoComunicacaoFiscal.PENDENTE, fr.estadoComunicacao());

        // Verificar persistência do pagamento e documento
        assertTrue(pagamentoSubscricaoRepository.findById(fr.pagamentoSubscricaoId()).isPresent());
        var docSalvo = documentoFiscalRepository.findByIdAndTenantId(fr.documentoId(), lexcvTenantId).orElseThrow();
        assertEquals(officeTenantId, docSalvo.getAdquirenteTenantId());
        assertEquals(lexcvTenantId, docSalvo.getTenantId());
        assertNull(docSalvo.getClienteId());
        assertNull(docSalvo.getProcessoId());
        assertNull(docSalvo.getHonorarioId());
        assertNull(docSalvo.getPagamentoId());

        // 4. Emitir Nota de Crédito parcial (2000.00 CVE)
        CriarNotaCreditoSubscricaoRequest ncParcialReq = new CriarNotaCreditoSubscricaoRequest(
                MotivoNotaCredito.CORRECAO_VALOR,
                "Desconto comercial concedido",
                new BigDecimal("2000.00"),
                UUID.randomUUID()
        );
        SubscricaoFaturaResponse nc1 = subscricaoNotaCreditoService.emitirNotaCredito(fr.documentoId(), adminPrincipal, ncParcialReq);
        assertNotNull(nc1);
        assertEquals(TipoDocumentoFiscal.NC, nc1.tipo());
        assertEquals("SIM-NC-2026", nc1.serieCodigo());
        assertEquals(1L, nc1.numero());
        assertEquals("SIM-NC-2026/1", nc1.numeroFormatado());
        assertEquals(new BigDecimal("2000.00"), nc1.totalDocumento());

        // 5. Emitir Nota de Crédito restante (3000.00 CVE)
        CriarNotaCreditoSubscricaoRequest ncTotalReq = new CriarNotaCreditoSubscricaoRequest(
                MotivoNotaCredito.ANULACAO_TOTAL,
                "Cancelamento da subscrição",
                new BigDecimal("3000.00"),
                UUID.randomUUID()
        );
        SubscricaoFaturaResponse nc2 = subscricaoNotaCreditoService.emitirNotaCredito(fr.documentoId(), adminPrincipal, ncTotalReq);
        assertNotNull(nc2);
        assertEquals(2L, nc2.numero());
        assertEquals("SIM-NC-2026/2", nc2.numeroFormatado());
        assertEquals(new BigDecimal("3000.00"), nc2.totalDocumento());

        // 6. Tentar emitir outra NC além do teto -> Recusa 422 DOCUMENTO_TOTALMENTE_CREDITADO
        CriarNotaCreditoSubscricaoRequest ncExcessoReq = new CriarNotaCreditoSubscricaoRequest(
                MotivoNotaCredito.OUTRO,
                "Tentativa indevida",
                new BigDecimal("100.00"),
                UUID.randomUUID()
        );
        RecusaFiscalException ex = assertThrows(RecusaFiscalException.class, () ->
                subscricaoNotaCreditoService.emitirNotaCredito(fr.documentoId(), adminPrincipal, ncExcessoReq));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("DOCUMENTO_TOTALMENTE_CREDITADO", ex.getCodigo());

        // 7. Testar detalhe e descarga de PDF na consola da plataforma
        DocumentoFiscalDetalheResponse detalhe = platformDocumentoFiscalService.obterDocumento(fr.documentoId());
        assertNotNull(detalhe);
        assertEquals("SIM-FR-2026/1", detalhe.numeroFormatado());
        assertEquals(2, detalhe.notasCredito().size());

        // Simula XML gerado pela eFatura para permitir geração de PDF
        jdbc.update("INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente, "
                        + "repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '1.0', '<Dfe/>', ?, ?)",
                UUID.randomUUID(), lexcvTenantId, fr.documentoId(), "CV326061550012345600000000000000000000001", "a".repeat(64),
                java.sql.Timestamp.from(AGORA));

        var descargaPdf = platformDocumentoFiscalService.descarregarPdf(adminPrincipal, fr.documentoId());
        assertNotNull(descargaPdf);
        assertEquals("https://minio.lexcv.cv/downloads/test.pdf", descargaPdf.url());

        var descargaXml = platformDocumentoFiscalService.descarregarXml(adminPrincipal, fr.documentoId());
        assertNotNull(descargaXml);
        assertEquals("SIM-FR-2026-1.xml", descargaXml.nomeFicheiro());

        // Verificar registo de auditoria de download
        int logsAudit = fixtura.contarEventosEmissao(lexcvTenantId);
        assertTrue(logsAudit >= 1);
    }
}
