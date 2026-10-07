package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.fiscal.pdf.DadosPdfDocumentoFiscal;
import com.lexcv.fiscal.pdf.FalhaGeracaoPdf;
import com.lexcv.fiscal.pdf.PdfDocumentoFiscalRenderer;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalPdf;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.StorageService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 137 (ENTR-01, ENTR-02): o PDF é gerado uma vez, reutilizado, gerado a pedido de forma
 * idempotente, nunca antes do IUD, e nunca dentro de uma transação.
 */
class PdfDocumentoFiscalServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOC = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String IUD = "CV3260307512345679000000000000000000000000123";
    private static final Instant AGORA = Instant.parse("2026-03-07T10:00:00Z");
    private static final byte[] BYTES = "%PDF-1.7 conteudo".getBytes(StandardCharsets.US_ASCII);

    private PdfDocumentoFiscalTransacoes transacoes;
    private PdfDocumentoFiscalRenderer renderer;
    private StorageService storage;
    private PdfDocumentoFiscalService servico;

    @BeforeEach
    void setUp() {
        transacoes = mock(PdfDocumentoFiscalTransacoes.class);
        renderer = mock(PdfDocumentoFiscalRenderer.class);
        storage = mock(StorageService.class);
        servico = new PdfDocumentoFiscalService(transacoes, renderer, storage, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static DocumentoFiscal documento() {
        return DocumentoFiscal.builder()
                .id(DOC)
                .tenantId(TENANT)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .numeroFormatado("FR 2026A/000123")
                .serieCodigo("2026A")
                .dataEmissao(LocalDate.of(2026, 3, 7))
                .emitenteFirma("Firma")
                .emitenteNif("512345679")
                .moeda("CVE")
                .totalBase(new BigDecimal("100.00"))
                .totalIva(new BigDecimal("15.00"))
                .totalRetencao(BigDecimal.ZERO)
                .totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00"))
                .build();
    }

    private static DocumentoFiscalPdf pdf(UUID id, String chave) {
        DocumentoFiscalPdf p = mock(DocumentoFiscalPdf.class);
        when(p.getId()).thenReturn(id);
        when(p.getTenantId()).thenReturn(TENANT);
        when(p.getDocumentoFiscalId()).thenReturn(DOC);
        when(p.getObjectKey()).thenReturn(chave);
        when(p.getSha256()).thenReturn("a".repeat(64));
        when(p.getTamanhoBytes()).thenReturn(1234L);
        when(p.getVersaoModelo()).thenReturn("137.1");
        return p;
    }

    private void dados(Optional<String> iud, Optional<DocumentoFiscalPdf> existente) {
        when(transacoes.carregarDados(TENANT, DOC)).thenReturn(Optional.of(
                new PdfDocumentoFiscalTransacoes.DadosParaPdf(documento(), List.of(), iud, null, existente)));
    }

    /** O registo devolve a linha escrita com o id e a chave que o serviço lhe passou. */
    private void registoGanha() {
        when(transacoes.registar(any(), eq(TENANT), eq(DOC), anyString(), anyString(), anyLong(), anyString(),
                any())).thenAnswer(inv -> Optional.of(pdf(inv.getArgument(0), inv.getArgument(3))));
    }

    @Test
    void pdfExistenteEReutilizadoSemRenderNemUpload() {
        String chave = TENANT + "/documentos-fiscais/" + DOC + "/pdf-" + UUID.randomUUID() + ".pdf";
        dados(Optional.of(IUD), Optional.of(pdf(UUID.randomUUID(), chave)));

        Optional<PdfDocumentoFiscalService.PdfArmazenado> r = servico.garantirPdf(TENANT, DOC);

        assertThat(r).isPresent();
        assertThat(r.get().objectKey()).isEqualTo(chave);
        assertThat(r.get().nomeFicheiro()).isEqualTo("FR-2026A-000123.pdf");
        assertThat(r.get().bytesFrescos()).isEmpty();
        verifyNoInteractions(renderer, storage);
        verify(transacoes, never()).registar(any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void semPdfGeraUmaVezCarregaERegista() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        registoGanha();

        Optional<PdfDocumentoFiscalService.PdfArmazenado> r = servico.garantirPdf(TENANT, DOC);

        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sha = ArgumentCaptor.forClass(String.class);
        verify(transacoes).registar(id.capture(), eq(TENANT), eq(DOC), chave.capture(), sha.capture(),
                eq((long) BYTES.length), eq("137.1"), eq(AGORA));
        assertThat(chave.getValue()).isEqualTo(TENANT + "/documentos-fiscais/" + DOC + "/pdf-" + id.getValue() + ".pdf");
        assertThat(sha.getValue()).isEqualTo(ProcessadorComunicacaoFiscal.sha256Hex(BYTES));
        verify(storage).uploadBytes(chave.getValue(), BYTES, "application/pdf");
        verify(renderer).renderizar(any(DadosPdfDocumentoFiscal.class));

        assertThat(r).isPresent();
        assertThat(r.get().objectKey()).isEqualTo(chave.getValue());
        assertThat(r.get().bytesFrescos()).contains(BYTES);
        assertThat(r.get().nomeFicheiro()).isEqualTo("FR-2026A-000123.pdf");
    }

    @Test
    void oRendererRecebeOIudEAFotografia() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        registoGanha();

        servico.garantirPdf(TENANT, DOC);

        ArgumentCaptor<DadosPdfDocumentoFiscal> d = ArgumentCaptor.forClass(DadosPdfDocumentoFiscal.class);
        verify(renderer).renderizar(d.capture());
        assertThat(d.getValue().iud()).isEqualTo(IUD);
        assertThat(d.getValue().numeroFormatado()).isEqualTo("FR 2026A/000123");
    }

    @Test
    void concorrenteVencedorDevolveALinhaDeleEApagaOProprioObjeto() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        String chaveVencedor = TENANT + "/documentos-fiscais/" + DOC + "/pdf-" + UUID.randomUUID() + ".pdf";
        DocumentoFiscalPdf vencedor = pdf(UUID.randomUUID(), chaveVencedor);
        when(transacoes.registar(any(), eq(TENANT), eq(DOC), anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(Optional.of(vencedor));

        Optional<PdfDocumentoFiscalService.PdfArmazenado> r = servico.garantirPdf(TENANT, DOC);

        ArgumentCaptor<String> propria = ArgumentCaptor.forClass(String.class);
        verify(storage).uploadBytes(propria.capture(), eq(BYTES), eq("application/pdf"));
        verify(storage).delete(propria.getValue());
        assertThat(propria.getValue()).isNotEqualTo(chaveVencedor);
        assertThat(r).isPresent();
        assertThat(r.get().objectKey()).isEqualTo(chaveVencedor);
        assertThat(r.get().bytesFrescos()).isEmpty();
    }

    @Test
    void falhaAoApagarOObjetoDoPerdedorEEngolida() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        DocumentoFiscalPdf vencedor = pdf(UUID.randomUUID(), "outra");
        when(transacoes.registar(any(), eq(TENANT), eq(DOC), anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(Optional.of(vencedor));
        doThrow(new StorageUnavailableException("Storage service unavailable", null)).when(storage).delete(anyString());

        assertThat(servico.garantirPdf(TENANT, DOC)).map(PdfDocumentoFiscalService.PdfArmazenado::objectKey)
                .contains("outra");
    }

    @Test
    void semXmlFicheiroIndisponivelSemRender() {
        dados(Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> servico.garantirPdf(TENANT, DOC))
                .isInstanceOf(FicheiroFiscalIndisponivelException.class)
                .hasMessage("O ficheiro ainda não está disponível. Aguarde um momento e tente novamente.")
                .satisfies(e -> assertThat(((FicheiroFiscalIndisponivelException) e).codigo())
                        .isEqualTo("FICHEIRO_INDISPONIVEL"));
        verifyNoInteractions(renderer, storage);
    }

    @Test
    void documentoDeOutroTenantDevolveVazio() {
        when(transacoes.carregarDados(TENANT, DOC)).thenReturn(Optional.empty());

        assertThat(servico.garantirPdf(TENANT, DOC)).isEmpty();
        verifyNoInteractions(renderer, storage);
    }

    @Test
    void falhaNoUploadPropagaENaoRegista() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        doThrow(new StorageUnavailableException("Storage service unavailable", null))
                .when(storage).uploadBytes(anyString(), any(), anyString());

        assertThatThrownBy(() -> servico.garantirPdf(TENANT, DOC)).isInstanceOf(StorageUnavailableException.class);
        verify(transacoes, never()).registar(any(), any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void silenciosoNuncaLanca() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenThrow(new FalhaGeracaoPdf(new RuntimeException()));
        assertThatCode(() -> servico.garantirPdfSilencioso(TENANT, DOC)).doesNotThrowAnyException();

        dados(Optional.empty(), Optional.empty());
        assertThatCode(() -> servico.garantirPdfSilencioso(TENANT, DOC)).doesNotThrowAnyException();

        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        doThrow(new StorageUnavailableException("Storage service unavailable", null))
                .when(storage).uploadBytes(anyString(), any(), anyString());
        assertThatCode(() -> servico.garantirPdfSilencioso(TENANT, DOC)).doesNotThrowAnyException();

        when(transacoes.carregarDados(TENANT, DOC)).thenThrow(new IllegalStateException("db"));
        assertThatCode(() -> servico.garantirPdfSilencioso(TENANT, DOC)).doesNotThrowAnyException();
    }

    @Test
    void lerPdfDevolveOsBytesFrescosQuandoAcabouDeGerar() {
        dados(Optional.of(IUD), Optional.empty());
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenReturn(BYTES);
        registoGanha();

        assertThat(servico.lerPdf(TENANT, DOC)).isEqualTo(BYTES);
        verify(storage, never()).lerBytes(anyString());
    }

    @Test
    void lerPdfLeDoArmazenamentoQuandoJaExistia() {
        String chave = TENANT + "/documentos-fiscais/" + DOC + "/pdf-x.pdf";
        dados(Optional.of(IUD), Optional.of(pdf(UUID.randomUUID(), chave)));
        when(storage.lerBytes(chave)).thenReturn(BYTES);

        assertThat(servico.lerPdf(TENANT, DOC)).isEqualTo(BYTES);
        verifyNoInteractions(renderer);
    }

    @Test
    void lerPdfDeDocumentoInexistenteLanca() {
        when(transacoes.carregarDados(TENANT, DOC)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servico.lerPdf(TENANT, DOC)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendererEArmazenamentoNuncaCorremDentroDeUmaTransacao() {
        dados(Optional.of(IUD), Optional.empty());
        AtomicReference<Boolean> txNoRender = new AtomicReference<>();
        AtomicReference<Boolean> txNoUpload = new AtomicReference<>();
        when(renderer.renderizar(any(DadosPdfDocumentoFiscal.class))).thenAnswer(inv -> {
            txNoRender.set(TransactionSynchronizationManager.isActualTransactionActive());
            return BYTES;
        });
        doAnswer(inv -> {
            txNoUpload.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(storage).uploadBytes(anyString(), any(), anyString());
        registoGanha();

        servico.garantirPdf(TENANT, DOC);

        assertThat(txNoRender.get()).isFalse();
        assertThat(txNoUpload.get()).isFalse();
    }

    @Test
    void servicoNaoTemTransactional() {
        assertThat(PdfDocumentoFiscalService.class
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
        for (var m : PdfDocumentoFiscalService.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                    .as(m.getName()).isFalse();
        }
    }
}
