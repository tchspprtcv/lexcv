package com.lexcv.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexcv.config.MinioProperties;
import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.services.fiscal.NomesFicheiroFiscal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Phase 137 (ENTR-01, ENTR-02): métodos aditivos do {@link StorageService} para os ficheiros
 * fiscais (upload de bytes com chave guardada, leitura, URL pré-assinado com nome de anexo) e os
 * nomes de ficheiro derivados da série/número. O comportamento genérico não muda.
 */
class StorageServiceFiscalTest {

    private static final String BUCKET = "lexcv-test";
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOC = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LINHA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CHAVE = TENANT + "/documentos-fiscais/" + DOC + "/pdf-" + LINHA + ".pdf";

    private S3Client s3;
    private S3Presigner presigner;
    private StorageService storage;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        MinioProperties props = new MinioProperties();
        props.setBucketName(BUCKET);
        props.setPresignedUrlExpiry(900L);
        storage = new StorageService(s3, presigner, props);
    }

    // ---- uploadBytes ----

    @Test
    void uploadBytesFazUmPutComChaveTipoETamanho() throws IOException {
        byte[] conteudo = "%PDF-1.7 teste".getBytes(StandardCharsets.US_ASCII);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.uploadBytes(CHAVE, conteudo, "application/pdf");

        ArgumentCaptor<PutObjectRequest> pedido = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> corpo = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(pedido.capture(), corpo.capture());
        assertThat(pedido.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(pedido.getValue().key()).isEqualTo(CHAVE);
        assertThat(pedido.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(pedido.getValue().contentLength()).isEqualTo(conteudo.length);
        assertThat(corpo.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(conteudo);
    }

    @Test
    void uploadBytesFalhaDoSdkViraStorageUnavailable() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("down"));
        assertThatThrownBy(() -> storage.uploadBytes(CHAVE, new byte[]{1}, "application/pdf"))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "pdf-x.pdf",
            "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222/x.pdf",
            "11111111-1111-1111-1111-111111111111/documentos-fiscais/22222222-2222-2222-2222-222222222222/../x.pdf",
            "11111111-1111-1111-1111-111111111111/documentos-fiscais/22222222-2222-2222-2222-222222222222/a/b.pdf",
            "11111111-1111-1111-1111-111111111111/documentos-fiscais/nao-uuid/x.pdf",
            "nao-uuid/documentos-fiscais/22222222-2222-2222-2222-222222222222/x.pdf",
            "11111111-1111-1111-1111-111111111111/documentos-fiscais/22222222-2222-2222-2222-222222222222/x y.pdf",
            "/11111111-1111-1111-1111-111111111111/documentos-fiscais/22222222-2222-2222-2222-222222222222/x.pdf",
    })
    void uploadBytesRecusaChavesForaDoFormatoFiscal(String chave) {
        assertThatThrownBy(() -> storage.uploadBytes(chave, new byte[]{1}, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadBytesRecusaChaveNula() {
        assertThatThrownBy(() -> storage.uploadBytes(null, new byte[]{1}, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- presignedDownloadUrl(key, nome) ----

    @Test
    void urlPreAssinadoLevaNomeDeAnexoETipoEExpiracao() throws Exception {
        PresignedGetObjectRequest assinado = mock(PresignedGetObjectRequest.class);
        when(assinado.url()).thenReturn(new URL("https://minio.example.cv/lexcv-test/x?sig=1"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(assinado);

        String url = storage.presignedDownloadUrl(CHAVE, "FR-2026A-000123.pdf");

        assertThat(url).isEqualTo("https://minio.example.cv/lexcv-test/x?sig=1");
        ArgumentCaptor<GetObjectPresignRequest> pedido = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(pedido.capture());
        GetObjectRequest get = pedido.getValue().getObjectRequest();
        assertThat(get.bucket()).isEqualTo(BUCKET);
        assertThat(get.key()).isEqualTo(CHAVE);
        assertThat(get.responseContentDisposition()).isEqualTo("attachment; filename=\"FR-2026A-000123.pdf\"");
        assertThat(get.responseContentType()).isEqualTo("application/pdf");
        assertThat(pedido.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void urlPreAssinadoDeXmlNaoForcaTipoPdf() throws Exception {
        PresignedGetObjectRequest assinado = mock(PresignedGetObjectRequest.class);
        when(assinado.url()).thenReturn(new URL("https://minio.example.cv/x"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(assinado);

        storage.presignedDownloadUrl(CHAVE, "FR-2026A-000123.xml");

        ArgumentCaptor<GetObjectPresignRequest> pedido = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(pedido.capture());
        assertThat(pedido.getValue().getObjectRequest().responseContentType()).isNotEqualTo("application/pdf");
        assertThat(pedido.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo("attachment; filename=\"FR-2026A-000123.xml\"");
    }

    @Test
    void urlPreAssinadoComNomeFalhaDoSdkViraStorageUnavailable() {
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("down"));
        assertThatThrownBy(() -> storage.presignedDownloadUrl(CHAVE, "FR-1.pdf"))
                .isInstanceOf(StorageUnavailableException.class);
    }

    // ---- lerBytes ----

    @Test
    void lerBytesDevolveOConteudo() {
        byte[] conteudo = {1, 2, 3, 4};
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), conteudo));

        assertThat(storage.lerBytes(CHAVE)).isEqualTo(conteudo);

        ArgumentCaptor<GetObjectRequest> pedido = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObjectAsBytes(pedido.capture());
        assertThat(pedido.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(pedido.getValue().key()).isEqualTo(CHAVE);
    }

    @Test
    void lerBytesFalhaDoSdkViraStorageUnavailable() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("down"));
        assertThatThrownBy(() -> storage.lerBytes(CHAVE)).isInstanceOf(StorageUnavailableException.class);
    }

    // ---- comportamento genérico inalterado ----

    @Test
    void uploadGenericoContinuaIgual() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        String chave = storage.upload(TENANT, DOC, "a/b\\c.pdf", new ByteArrayInputStream(new byte[]{9}),
                "application/pdf", 1);
        assertThat(chave).isEqualTo(TENANT + "/" + DOC + "/a_b_c.pdf");
    }

    @Test
    void presignedGenericoNaoForcaAnexo() throws Exception {
        PresignedGetObjectRequest assinado = mock(PresignedGetObjectRequest.class);
        when(assinado.url()).thenReturn(new URL("https://minio.example.cv/y"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(assinado);

        storage.presignedDownloadUrl("k");

        ArgumentCaptor<GetObjectPresignRequest> pedido = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(pedido.capture());
        assertThat(pedido.getValue().getObjectRequest().responseContentDisposition()).isNull();
    }

    @Test
    void deleteGenericoContinuaIgual() {
        storage.delete("k");
        ArgumentCaptor<DeleteObjectRequest> pedido = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(pedido.capture());
        assertThat(pedido.getValue().key()).isEqualTo("k");
        assertThat(pedido.getValue().bucket()).isEqualTo(BUCKET);
    }

    // ---- NomesFicheiroFiscal ----

    @Test
    void nomesDeFicheiroAPartirDaSerieNumero() {
        assertThat(NomesFicheiroFiscal.pdf("FR 2026A/000123")).isEqualTo("FR-2026A-000123.pdf");
        assertThat(NomesFicheiroFiscal.xml("FR 2026A/000123")).isEqualTo("FR-2026A-000123.xml");
        assertThat(NomesFicheiroFiscal.base("NC SIM-NC-2026/000007")).isEqualTo("NC-SIM-NC-2026-000007");
        assertThat(NomesFicheiroFiscal.base("FR ç\"x;\r\n/1")).isEqualTo("FR--x---1");
        assertThat(NomesFicheiroFiscal.csv(YearMonth.of(2026, 9))).isEqualTo("documentos-fiscais-simulacao-2026-09.csv");
    }
}
