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

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService implements ApplicationRunner {

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
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
        } catch (SdkException e) {
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
