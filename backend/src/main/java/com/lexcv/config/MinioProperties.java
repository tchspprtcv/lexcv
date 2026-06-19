package com.lexcv.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "minio")
@Validated
@Data
public class MinioProperties {

    @NotBlank
    private String endpoint;

    /**
     * Public-facing endpoint used when building presigned URLs.
     * Defaults to {@code endpoint} if not set, which is correct for single-host deployments.
     * Override with {@code MINIO_PUBLIC_ENDPOINT} when the backend reaches MinIO via an
     * internal hostname (e.g. Docker service name) but presigned URLs must be reachable
     * from a browser using a public hostname.
     */
    private String publicEndpoint;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String bucketName;

    @Min(1)
    private long presignedUrlExpiry = 3600L;

    /**
     * Returns the endpoint to use when constructing presigned URLs.
     * Falls back to {@link #endpoint} when {@link #publicEndpoint} is not configured.
     */
    public String getEffectivePublicEndpoint() {
        return (publicEndpoint != null && !publicEndpoint.isBlank()) ? publicEndpoint : endpoint;
    }
}
