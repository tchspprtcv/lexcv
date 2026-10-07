package com.lexcv.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.MinioProperties;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.RegistarPagamentoSubscricaoRequest;
import com.lexcv.dtos.SubscricaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.DocumentoFiscalRepository;
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
import org.springframework.data.domain.Page;
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
        FaturacaoSubscricaoEscritorioService.class,
        PagamentoFaturadoService.class,
        PreVisualizacaoFaturaService.class,
        DocumentoFiscalService.class,
        DescargaDocumentoFiscalService.class,
        DescargaDocumentoFiscalTransacoes.class,
        PdfDocumentoFiscalService.class,
        PdfDocumentoFiscalTransacoes.class,
        PdfDocumentoFiscalRenderer.class,
        NumeracaoService.class,
        ParametroFiscalService.class,
        AuditoriaFiscalService.class,
        SubscricaoIsolamentoTenantIT.Apoio.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SubscricaoIsolamentoTenantIT {

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
    private FaturacaoSubscricaoEscritorioService faturacaoSubscricaoEscritorioService;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturadoService;

    @Autowired
    private DocumentoFiscalRepository documentoFiscalRepository;

    @MockitoBean
    private StorageService storageService;

    private FixturaEmissaoFiscal fixtura;
    private UUID lexcvTenantId;
    private UserPrincipal platformAdmin;

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

        platformAdmin = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(lexcvTenantId)
                .nome("Admin Plataforma")
                .email("admin@lexcv.cv")
                .build();

        // Configurar LexCV se ainda não estiver ativa
        if (!platformConfigService.prontaParaEmitir()) {
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
            platformConfigService.guardarConfiguracao(platformAdmin, configReq);
        }
    }

    @Test
    void isolamentoMultiTenantEstritoEmFaturasDeSubscricao() {
        // 1. Criar Tenant A e Tenant B
        UUID tenantAId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_tenant (id, nome, plano, ativo, created_at) VALUES (?, 'Escritório A', 'STARTER', true, now())",
                tenantAId);
        UserPrincipal userA = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantAId)
                .nome("Advogado A")
                .email("advogado@escritorio-a.cv")
                .build();

        UUID tenantBId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_tenant (id, nome, plano, ativo, created_at) VALUES (?, 'Escritório B', 'STANDARD', true, now())",
                tenantBId);
        UserPrincipal userB = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantBId)
                .nome("Advogado B")
                .email("advogado@escritorio-b.cv")
                .build();

        // 2. Emitir subscrição para Tenant A
        RegistarPagamentoSubscricaoRequest payReqA = new RegistarPagamentoSubscricaoRequest(
                tenantAId,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 6, 15),
                "TRANSFERENCIA",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                "STARTER",
                UUID.randomUUID()
        );
        SubscricaoFaturaResponse docA = subscricaoFaturadaService.registarPagamentoFaturado(platformAdmin, payReqA);

        // 3. Emitir subscrição para Tenant B
        RegistarPagamentoSubscricaoRequest payReqB = new RegistarPagamentoSubscricaoRequest(
                tenantBId,
                new BigDecimal("12000.00"),
                LocalDate.of(2026, 6, 15),
                "VINTI4",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                "STANDARD",
                UUID.randomUUID()
        );
        SubscricaoFaturaResponse docB = subscricaoFaturadaService.registarPagamentoFaturado(platformAdmin, payReqB);

        // 4. Verificação sob a perspetiva do Tenant A
        Page<DocumentoFiscalResumoResponse> listaA = faturacaoSubscricaoEscritorioService.listar(tenantAId, 0, 10);
        assertEquals(1, listaA.getTotalElements());
        assertEquals(docA.documentoId(), listaA.getContent().get(0).id());

        // Tenant A acede ao detalhe e download de docA
        DocumentoFiscalDetalheResponse detalheA = faturacaoSubscricaoEscritorioService.detalhe(tenantAId, docA.documentoId());
        assertNotNull(detalheA);
        // Simula XML gerado pela eFatura para docA
        jdbc.update("INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente, "
                        + "repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '1.0', '<Dfe/>', ?, ?)",
                UUID.randomUUID(), lexcvTenantId, docA.documentoId(), "CV326061550012345600000000000000000000001", "a".repeat(64),
                java.sql.Timestamp.from(AGORA));

        var pdfA = faturacaoSubscricaoEscritorioService.descarregarPdf(tenantAId, userA, docA.documentoId());
        assertNotNull(pdfA);

        var xmlA = faturacaoSubscricaoEscritorioService.descarregarXml(tenantAId, userA, docA.documentoId());
        assertNotNull(xmlA);

        // Tenant A tenta aceder ao documento de Tenant B -> Recusa 404
        RecusaFiscalException exDetalhe = assertThrows(RecusaFiscalException.class, () ->
                faturacaoSubscricaoEscritorioService.detalhe(tenantAId, docB.documentoId()));
        assertEquals(HttpStatus.NOT_FOUND, exDetalhe.getStatus());

        RecusaFiscalException exPdf = assertThrows(RecusaFiscalException.class, () ->
                faturacaoSubscricaoEscritorioService.descarregarPdf(tenantAId, userA, docB.documentoId()));
        assertEquals(HttpStatus.NOT_FOUND, exPdf.getStatus());

        RecusaFiscalException exXml = assertThrows(RecusaFiscalException.class, () ->
                faturacaoSubscricaoEscritorioService.descarregarXml(tenantAId, userA, docB.documentoId()));
        assertEquals(HttpStatus.NOT_FOUND, exXml.getStatus());

        // 5. Verificação sob a perspetiva do Tenant B
        Page<DocumentoFiscalResumoResponse> listaB = faturacaoSubscricaoEscritorioService.listar(tenantBId, 0, 10);
        assertEquals(1, listaB.getTotalElements());
        assertEquals(docB.documentoId(), listaB.getContent().get(0).id());

        RecusaFiscalException exDetalheB = assertThrows(RecusaFiscalException.class, () ->
                faturacaoSubscricaoEscritorioService.detalhe(tenantBId, docA.documentoId()));
        assertEquals(HttpStatus.NOT_FOUND, exDetalheB.getStatus());

        // 6. Tenant A emite fatura a um cliente do seu próprio escritório
        // Configura fiscal para Tenant A
        fixtura.criarTenant(RegimeIva.NORMAL, true);
        // Atualiza a config para apontar para tenantAId
        jdbc.update("UPDATE t_configuracao_fiscal SET tenant_id = ? WHERE nif = ?", tenantAId, FixturaEmissaoFiscal.NIF_EMITENTE);
        UUID clienteId = fixtura.criarCliente(tenantAId, "200111222", "Cliente Particular A", "Plateau, Praia");
        UUID processoId = fixtura.criarProcesso(tenantAId, clienteId, "PROC-001");
        Integer honorarioId = fixtura.criarHonorario(processoId, new BigDecimal("10000.00"), "Honorários Consulta");
        fixtura.criarContaCorrente(clienteId, BigDecimal.ZERO);

        PagamentoRequest clientPayReq = new PagamentoRequest(
                honorarioId,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 6, 15),
                "DINHEIRO",
                null,
                UUID.randomUUID()
        );
        var resOffice = pagamentoFaturadoService.registar(tenantAId, userA, clientPayReq);
        assertNotNull(resOffice);

        // Verifica que a fatura do cliente NÃO aparece na listagem de faturas de subscrição do Tenant A
        Page<DocumentoFiscalResumoResponse> subscricoesAposFaturaCliente = faturacaoSubscricaoEscritorioService.listar(tenantAId, 0, 10);
        assertEquals(1, subscricoesAposFaturaCliente.getTotalElements(), "Faturas emitidas pelo escritório aos clientes não devem aparecer nas faturas de subscrição");

        // Verifica que a numeração da plataforma continua isolada
        RegistarPagamentoSubscricaoRequest payReqA2 = new RegistarPagamentoSubscricaoRequest(
                tenantAId,
                new BigDecimal("5000.00"),
                LocalDate.of(2026, 6, 15),
                "TRANSFERENCIA",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "STARTER",
                UUID.randomUUID()
        );
        SubscricaoFaturaResponse docA2 = subscricaoFaturadaService.registarPagamentoFaturado(platformAdmin, payReqA2);
        assertEquals(3L, docA2.numero(), "Série da plataforma avança de forma independente (1=A, 2=B, 3=A2)");
    }
}
