package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.services.StorageService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 137 (ENTR-01): provas em PostgreSQL real do PDF fiscal gerado uma vez -- dois pedidos
 * concorrentes convergem numa só linha {@code t_documento_fiscal_pdf} (o perdedor apaga o seu
 * objeto), o SHA-256 guardado é o dos bytes carregados, uma segunda chamada não volta a carregar
 * nada, e outro tenant não vê nem cria nada. Renderer real; MinIO simulado (não há contentor MinIO
 * neste repositório).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PdfDocumentoFiscalRenderer.class, PdfDocumentoFiscalTransacoes.class, PdfDocumentoFiscalService.class,
        PdfDocumentoFiscalServiceIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PdfDocumentoFiscalServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant T0 = Instant.parse("2026-06-15T13:00:00Z");

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(T0, ZoneOffset.UTC);
        }
    }

    private static final AtomicLong SEQUENCIA = new AtomicLong(1);

    @Autowired
    private PdfDocumentoFiscalService servico;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private StorageService storage;

    @BeforeEach
    void preparar() {
        reset(storage);
    }

    // ------------------------------------------------------------------ apoio JDBC

    private UUID frComXml(UUID tenantId) {
        UUID id = UUID.randomUUID();
        long n = SEQUENCIA.getAndIncrement();
        String serie = "SIM-FR-2026";
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia, documento_origem_id) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, ?, 2026, ?, ?, ?, ?, '512345679', 'Firma', 'Morada', "
                        + "'NORMAL', '234567891', 'Cliente', 'Morada cliente', ?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', "
                        + "15, 100, 15, 0, 115, 115, ?, NULL)",
                id, tenantId, UUID.randomUUID(), serie, n, "FR " + serie + "/" + n,
                java.sql.Date.valueOf("2026-06-15"), Timestamp.from(T0), UUID.randomUUID(), UUID.randomUUID(),
                (int) -n, UUID.randomUUID());
        jdbc.update("INSERT INTO t_documento_fiscal_linha (id, tenant_id, documento_fiscal_id, numero_linha, "
                        + "descricao, quantidade, preco_unitario, valor_base, taxa_iva, valor_iva, valor_retencao, "
                        + "total_linha) VALUES (?, ?, ?, 1, 'Honorários', 1, 100, 100, 15, 15, 0, 115)",
                UUID.randomUUID(), tenantId, id);
        jdbc.update("INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente, "
                        + "repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '1.0', '<Dfe/>', ?, ?)",
                UUID.randomUUID(), tenantId, id, "CV3260615512345679" + String.format("%027d", n), "a".repeat(64),
                Timestamp.from(T0));
        return id;
    }

    private List<Map<String, Object>> linhasPdf(UUID documentoId) {
        return jdbc.queryForList("SELECT * FROM t_documento_fiscal_pdf WHERE documento_fiscal_id = ?", documentoId);
    }

    // ------------------------------------------------------------------ testes

    @Test
    void doisPedidosConcorrentesConvergemNumaSoLinha() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID doc = frComXml(tenant);
        // Os dois pedidos só passam o upload quando ambos lá chegaram: a corrida é garantida.
        CountDownLatch ambosNoUpload = new CountDownLatch(2);
        AtomicBoolean transacaoNoUpload = new AtomicBoolean(false);
        doAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                transacaoNoUpload.set(true);
            }
            ambosNoUpload.countDown();
            assertThat(ambosNoUpload.await(30, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).uploadBytes(anyString(), any(), eq("application/pdf"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<PdfDocumentoFiscalService.PdfArmazenado>> a = pool.submit(() -> servico.garantirPdf(tenant, doc));
            Future<Optional<PdfDocumentoFiscalService.PdfArmazenado>> b = pool.submit(() -> servico.garantirPdf(tenant, doc));
            PdfDocumentoFiscalService.PdfArmazenado ra = a.get(60, TimeUnit.SECONDS).orElseThrow();
            PdfDocumentoFiscalService.PdfArmazenado rb = b.get(60, TimeUnit.SECONDS).orElseThrow();

            List<Map<String, Object>> linhas = linhasPdf(doc);
            assertThat(linhas).hasSize(1);
            String chave = (String) linhas.get(0).get("object_key");
            assertThat(ra.objectKey()).isEqualTo(chave);
            assertThat(rb.objectKey()).isEqualTo(chave);
            assertThat(chave).startsWith(tenant + "/documentos-fiscais/" + doc + "/pdf-" + linhas.get(0).get("id"));
            assertThat(linhas.get(0).get("versao_modelo")).isEqualTo("137.1");

            ArgumentCaptor<String> carregadas = ArgumentCaptor.forClass(String.class);
            verify(storage, times(2)).uploadBytes(carregadas.capture(), any(), eq("application/pdf"));
            ArgumentCaptor<String> apagadas = ArgumentCaptor.forClass(String.class);
            verify(storage, atMost(1)).delete(apagadas.capture());
            assertThat(apagadas.getAllValues()).allSatisfy(k -> {
                assertThat(k).isNotEqualTo(chave);
                assertThat(carregadas.getAllValues()).contains(k);
            });
            assertThat(transacaoNoUpload.get()).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sha256GuardadoEODosBytesCarregadosESegundaChamadaNaoCarrega() {
        UUID tenant = UUID.randomUUID();
        UUID doc = frComXml(tenant);
        List<byte[]> carregados = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            carregados.add(inv.getArgument(1));
            return null;
        }).when(storage).uploadBytes(anyString(), any(), anyString());

        PdfDocumentoFiscalService.PdfArmazenado primeiro = servico.garantirPdf(tenant, doc).orElseThrow();

        assertThat(carregados).hasSize(1);
        byte[] bytes = carregados.get(0);
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Map<String, Object> linha = linhasPdf(doc).get(0);
        assertThat(linha.get("sha256")).isEqualTo(ProcessadorComunicacaoFiscal.sha256Hex(bytes));
        assertThat(((Number) linha.get("tamanho_bytes")).longValue()).isEqualTo(bytes.length);
        assertThat(primeiro.bytesFrescos()).isPresent();
        String numero = jdbc.queryForObject("SELECT numero_formatado FROM t_documento_fiscal WHERE id = ?",
                String.class, doc);
        assertThat(primeiro.nomeFicheiro()).isEqualTo(NomesFicheiroFiscal.pdf(numero)).startsWith("FR-SIM-FR-2026-");

        PdfDocumentoFiscalService.PdfArmazenado segundo = servico.garantirPdf(tenant, doc).orElseThrow();

        assertThat(segundo.objectKey()).isEqualTo(primeiro.objectKey());
        assertThat(segundo.sha256()).isEqualTo(primeiro.sha256());
        assertThat(segundo.bytesFrescos()).isEmpty();
        verify(storage, times(1)).uploadBytes(anyString(), any(), anyString());
        verify(storage, never()).delete(anyString());
        assertThat(linhasPdf(doc)).hasSize(1);
    }

    @Test
    void outroTenantDevolveVazioENaoCriaNada() {
        UUID tenant = UUID.randomUUID();
        UUID doc = frComXml(tenant);

        assertThat(servico.garantirPdf(UUID.randomUUID(), doc)).isEmpty();

        assertThat(linhasPdf(doc)).isEmpty();
        verify(storage, never()).uploadBytes(anyString(), any(), anyString());
    }
}
