package com.lexcv.services.fiscal;

import com.lexcv.config.MinioProperties;
import com.lexcv.config.UserPrincipal;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.fiscal.pdf.FalhaGeracaoPdf;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.services.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 137-15 (ENTR-02): descarga auditada do PDF e do XML de um documento fiscal.
 *
 * <p>Assimetria intencional da auditoria: no PDF o evento faz commit ANTES da geração/presign
 * (que correm fora da transação), por isso um pedido de PDF que acaba em 503 deixa o registo da
 * tentativa; no XML a falta da linha XML é recusada dentro da transação, antes do evento, e não
 * deixa registo.
 */
class DescargaDocumentoFiscalServiceTest {

    private static final String NUMERO = "FR 2026A/000123";
    private static final String NOME_PDF = "FR-2026A-000123.pdf";
    private static final String CHAVE = UUID.randomUUID() + "/documentos-fiscais/" + UUID.randomUUID() + "/pdf-1.pdf";
    private static final String XML = "<Dfe nome=\"Conceição\"/>";

    private final DocumentoFiscalRepository documentoRepository = mock(DocumentoFiscalRepository.class);
    private final DocumentoFiscalXmlRepository xmlRepository = mock(DocumentoFiscalXmlRepository.class);
    private final AuditoriaFiscalService auditoria = mock(AuditoriaFiscalService.class);
    private final PdfDocumentoFiscalService pdfService = mock(PdfDocumentoFiscalService.class);
    private final StorageService storage = mock(StorageService.class);
    private final MinioProperties minio = new MinioProperties();

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final UserPrincipal autor = UserPrincipal.create(UUID.randomUUID(), tenantId, "Ana", "ana@escritorio.cv",
            Set.of(), Set.of(), Set.of());
    private final List<String> semTransacao = new ArrayList<>();

    private DescargaDocumentoFiscalTransacoes transacoes;
    private DescargaDocumentoFiscalService service;

    @BeforeEach
    void setUp() {
        minio.setPresignedUrlExpiry(900L);
        transacoes = new DescargaDocumentoFiscalTransacoes(documentoRepository, xmlRepository, auditoria);
        service = new DescargaDocumentoFiscalService(transacoes, pdfService, storage, minio);
        when(documentoRepository.findByIdAndTenantId(documentoId, tenantId)).thenReturn(Optional.of(documento()));
        DocumentoFiscalXml linha = mock(DocumentoFiscalXml.class);
        when(linha.getXml()).thenReturn(XML);
        when(xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)).thenReturn(Optional.of(linha));
        pdfDevolve(() -> Optional.of(new PdfDocumentoFiscalService.PdfArmazenado(CHAVE, "abc", 10L, NOME_PDF,
                Optional.empty())));
        when(storage.presignedDownloadUrl(CHAVE, NOME_PDF)).thenAnswer(inv -> {
            registarSemTransacao("presign");
            return "https://minio/assinado";
        });
    }

    private DocumentoFiscal documento() {
        return DocumentoFiscal.builder()
                .id(documentoId).tenantId(tenantId).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo("2026A").ano(2026).numero(123L).numeroFormatado(NUMERO)
                .dataEmissao(LocalDate.of(2026, 6, 15)).emitidoEm(Instant.parse("2026-06-15T10:00:00Z"))
                .emitenteNif("512345679").emitenteFirma("Silva").emitenteMorada("Avenida, 1")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Cliente").adquirenteMorada("Rua 2")
                .meioPagamentoCodigo("10").moeda("CVE")
                .totalBase(BigDecimal.TEN).totalIva(BigDecimal.ZERO).totalRetencao(BigDecimal.ZERO)
                .totalDocumento(BigDecimal.TEN).valorLiquido(BigDecimal.TEN)
                .build();
    }

    private void pdfDevolve(Supplier<Optional<PdfDocumentoFiscalService.PdfArmazenado>> resposta) {
        when(pdfService.garantirPdf(tenantId, documentoId)).thenAnswer(inv -> {
            registarSemTransacao("pdf");
            return resposta.get();
        });
    }

    private void registarSemTransacao(String passo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            semTransacao.add(passo);
        }
    }

    private static void assertRecusa(Throwable t, HttpStatus status, String codigo) {
        assertThat(t).isInstanceOf(RecusaFiscalException.class);
        RecusaFiscalException r = (RecusaFiscalException) t;
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getCodigo()).isEqualTo(codigo);
    }

    // ---- PDF ----

    @Test
    void pdfAuditaDepoisGaranteEAssinaComONomeDoAnexo() {
        DescargaDocumentoFiscalService.DescargaPdf r = service.descarregarPdf(tenantId, autor, documentoId);

        assertThat(r.url()).isEqualTo("https://minio/assinado");
        assertThat(r.nomeFicheiro()).isEqualTo(NOME_PDF);
        assertThat(r.expiresIn()).isEqualTo(900L);
        var ordem = inOrder(auditoria, pdfService, storage);
        ordem.verify(auditoria).registarDescarga(tenantId, autor, documentoId, NUMERO, "PDF");
        ordem.verify(pdfService).garantirPdf(tenantId, documentoId);
        ordem.verify(storage).presignedDownloadUrl(CHAVE, NOME_PDF);
    }

    @Test
    void pdfDeOutroTenantDa404SemAuditoriaNemPdf() {
        UUID outro = UUID.randomUUID();
        when(documentoRepository.findByIdAndTenantId(documentoId, outro)).thenReturn(Optional.empty());

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarPdf(outro, autor, documentoId));

        assertRecusa(t, HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO");
        verifyNoInteractions(auditoria, pdfService, storage);
    }

    @Test
    void pdfIndisponivelDa503FicheiroIndisponivelEMantemOEventoDaTentativa() {
        pdfDevolve(() -> {
            throw new FicheiroFiscalIndisponivelException();
        });

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarPdf(tenantId, autor, documentoId));

        assertRecusa(t, HttpStatus.SERVICE_UNAVAILABLE, "FICHEIRO_INDISPONIVEL");
        verify(auditoria, times(1)).registarDescarga(tenantId, autor, documentoId, NUMERO, "PDF");
    }

    @Test
    void storageEmBaixoNoPdfDa503StorageIndisponivel() {
        pdfDevolve(() -> {
            throw new StorageUnavailableException("minio interno", new RuntimeException());
        });
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarPdf(tenantId, autor, documentoId));
        assertRecusa(t, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_INDISPONIVEL");
        assertThat(t.getMessage()).doesNotContain("minio interno");
        verify(auditoria, times(1)).registarDescarga(tenantId, autor, documentoId, NUMERO, "PDF");
    }

    @Test
    void presignComStorageEmBaixoDa503StorageIndisponivel() {
        doThrow(new StorageUnavailableException("presign", new RuntimeException()))
                .when(storage).presignedDownloadUrl(CHAVE, NOME_PDF);

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarPdf(tenantId, autor, documentoId));

        assertRecusa(t, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_INDISPONIVEL");
        verify(auditoria, times(1)).registarDescarga(tenantId, autor, documentoId, NUMERO, "PDF");
    }

    @Test
    void falhaDeGeracaoDa503FalhaPdf() {
        pdfDevolve(() -> {
            throw new FalhaGeracaoPdf(new RuntimeException("renderer"));
        });

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarPdf(tenantId, autor, documentoId));

        assertRecusa(t, HttpStatus.SERVICE_UNAVAILABLE, "FALHA_PDF");
        assertThat(t.getMessage()).isEqualTo(DescargaDocumentoFiscalService.MSG_PREPARAR);
        verify(auditoria, times(1)).registarDescarga(tenantId, autor, documentoId, NUMERO, "PDF");
        verify(storage, never()).presignedDownloadUrl(any(), any());
    }

    @Test
    void pdfEPresignCorremSemTransacaoAtiva() {
        service.descarregarPdf(tenantId, autor, documentoId);

        assertThat(semTransacao).containsExactly("pdf", "presign");
    }

    // ---- XML ----

    @Test
    void xmlAuditaEDevolveOsBytesGuardadosComONome() {
        DescargaDocumentoFiscalTransacoes.XmlDescarregavel r = service.descarregarXml(tenantId, autor, documentoId);

        assertThat(r.conteudo()).isEqualTo(XML.getBytes(StandardCharsets.UTF_8));
        assertThat(r.nomeFicheiro()).isEqualTo(NomesFicheiroFiscal.xml(NUMERO)).isEqualTo("FR-2026A-000123.xml");
        verify(auditoria).registarDescarga(tenantId, autor, documentoId, NUMERO, "XML");
        verifyNoInteractions(pdfService, storage);
    }

    @Test
    void xmlSemLinhaDa503SemEventoDeAuditoria() {
        when(xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)).thenReturn(Optional.empty());

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarXml(tenantId, autor, documentoId));

        assertRecusa(t, HttpStatus.SERVICE_UNAVAILABLE, "FICHEIRO_INDISPONIVEL");
        verifyNoInteractions(auditoria);
    }

    @Test
    void xmlDeOutroTenantDa404SemAuditoria() {
        UUID outro = UUID.randomUUID();
        when(documentoRepository.findByIdAndTenantId(documentoId, outro)).thenReturn(Optional.empty());

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> service.descarregarXml(outro, autor, documentoId));

        assertRecusa(t, HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO");
        verifyNoInteractions(auditoria);
        verify(xmlRepository, never()).findByTenantIdAndDocumentoFiscalId(outro, documentoId);
    }

    // ---- transações ----

    @Test
    void transacoesSaoTransacionaisEOOrquestradorNao() throws Exception {
        for (String nome : List.of("autorizarERegistarPdf", "autorizarERegistarXml")) {
            Method m = DescargaDocumentoFiscalTransacoes.class.getMethod(nome, UUID.class, UserPrincipal.class,
                    UUID.class);
            Transactional tx = m.getAnnotation(Transactional.class);
            assertThat(tx).as(nome).isNotNull();
            assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRED);
        }
        assertThat(DescargaDocumentoFiscalService.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method m : DescargaDocumentoFiscalService.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(Transactional.class)).as(m.getName()).isFalse();
        }
    }

    @Test
    void xmlDescarregavelCopiaOsBytes() {
        byte[] original = {1, 2, 3};
        var x = new DescargaDocumentoFiscalTransacoes.XmlDescarregavel(original, "a.xml");
        original[0] = 9;
        x.conteudo()[1] = 9;

        assertThat(x.conteudo()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> new DescargaDocumentoFiscalTransacoes.XmlDescarregavel(null, "a.xml"))
                .isInstanceOf(NullPointerException.class);
    }
}
