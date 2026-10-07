package com.lexcv.services;

import com.lexcv.config.MinioProperties;
import com.lexcv.exceptions.StorageUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService implements ApplicationRunner {

    private static final String UUID_RE = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /**
     * Phase 137 (T-137-37): forma única das chaves fiscais,
     * {@code <tenantId>/documentos-fiscais/<documentoId>/<nome>}. O segundo segmento nunca colide com
     * uma chave genérica {@code <tenantId>/<documentoId>/...} (um UUID nunca é "documentos-fiscais").
     */
    static final Pattern CHAVE_FISCAL = Pattern.compile(
            UUID_RE + "/documentos-fiscais/" + UUID_RE + "/[A-Za-z0-9._-]{1,200}");

    /** Nome de anexo aceite em {@code Content-Disposition} (sem aspas, CR/LF ou separadores). */
    static final Pattern NOME_ANEXO = Pattern.compile("[A-Za-z0-9._-]{1,200}");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;

    /**
     * Uploads a file to MinIO and returns the object key.
     * Filename is sanitised to prevent path traversal (T-50-01).
     */
    public String upload(UUID tenantId, UUID documentoId, String filename,
                         InputStream inputStream, String contentType, long size) {
        String sanitisedFilename = filename.replaceAll("[/\\\\]", "_");
        String objectKey = tenantId.toString() + "/" + documentoId.toString() + "/" + sanitisedFilename;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(size)
                .build();

        try {
            byte[] contentBytes = inputStream.readAllBytes();
            s3Client.putObject(request, RequestBody.fromBytes(contentBytes));
        } catch (SdkException | java.io.IOException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }

        return objectKey;
    }

    /**
     * Returns a presigned download URL for the given object key.
     */
    public String presignedDownloadUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiry()))
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    /**
     * Phase 137: carrega bytes já em memória (PDF fiscal) sob uma chave fiscal. Só para chaves
     * construídas por {@code PdfDocumentoFiscalService}; qualquer outra forma (incluindo "..",
     * subpastas extra ou segmentos que não sejam UUID) é recusada antes de qualquer pedido.
     *
     * @throws IllegalArgumentException chave fora de {@link #CHAVE_FISCAL}
     * @throws StorageUnavailableException falha do MinIO
     */
    public void uploadBytes(String objectKey, byte[] conteudo, String contentType) {
        exigirChaveFiscal(objectKey);
        if (conteudo == null) {
            throw new IllegalArgumentException("Conteúdo em falta");
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) conteudo.length)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(conteudo));
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    /**
     * Phase 137 (ENTR-02): URL pré-assinado (mesma expiração dos documentos) que força o
     * descarregamento com o nome indicado ({@code Content-Disposition: attachment}).
     *
     * @param nomeFicheiroAnexo só {@code [A-Za-z0-9._-]} (ver {@code NomesFicheiroFiscal})
     */
    public String presignedDownloadUrl(String objectKey, String nomeFicheiroAnexo) {
        if (nomeFicheiroAnexo == null || !NOME_ANEXO.matcher(nomeFicheiroAnexo).matches()) {
            throw new IllegalArgumentException("Nome de ficheiro inválido");
        }
        GetObjectRequest.Builder get = GetObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .responseContentDisposition("attachment; filename=\"" + nomeFicheiroAnexo + "\"");
        if (nomeFicheiroAnexo.endsWith(".pdf")) {
            get.responseContentType("application/pdf");
        } else if (nomeFicheiroAnexo.endsWith(".xml")) {
            get.responseContentType("application/xml");
        }
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiry()))
                .getObjectRequest(get.build())
                .build();
        try {
            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    /**
     * Phase 137: lê um objeto inteiro (o PDF fiscal anexado ao email).
     *
     * @throws StorageUnavailableException falha do MinIO (incluindo objeto inexistente)
     */
    public byte[] lerBytes(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build();
        try {
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    private static void exigirChaveFiscal(String objectKey) {
        if (objectKey == null || !CHAVE_FISCAL.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("Chave de objeto fiscal inválida");
        }
    }

    /**
     * Deletes an object from MinIO.
     */
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    /**
     * On startup: verify the bucket exists and create it if absent.
     * If MinIO is unreachable, log a warning and continue — do not prevent app startup (T-50-03).
     */
    @Override
    public void run(ApplicationArguments args) {
        HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(props.getBucketName())
                .build();

        try {
            s3Client.headBucket(headRequest);
            log.info("MinIO bucket '{}' verified.", props.getBucketName());
        } catch (NoSuchBucketException e) {
            log.info("MinIO bucket '{}' not found — creating.", props.getBucketName());
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(props.getBucketName())
                        .build());
            } catch (SdkException ce) {
                log.warn("MinIO bucket creation failed: {}", ce.getMessage());
            }
        } catch (SdkException e) {
            log.warn("MinIO unavailable at startup: {}", e.getMessage());
        }
    }
}
