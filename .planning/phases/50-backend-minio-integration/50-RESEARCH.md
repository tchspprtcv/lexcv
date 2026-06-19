# Phase 50: Backend MinIO Integration - Research

**Researched:** 2026-06-19
**Domain:** Java AWS SDK v2, S3-compatible object storage (MinIO), Spring Boot 3 service layer
**Confidence:** HIGH

## Summary

This phase replaces three filesystem operations inside `ResourceController` (upload, download, delete) with calls to a new `StorageService` Spring bean backed by MinIO via the AWS SDK v2 S3 client. The `Documento.caminhoArquivo` column transitions from storing a filesystem path to storing a MinIO object key (`{tenantId}/{documentoId}/{filename}`). No schema migration is required.

The primary complexity is around the AWS SDK v2 S3 presigner: there are documented compatibility issues between the presigner and MinIO when using HTTP endpoints and path-style access. The critical workarounds are: (1) `pathStyleAccessEnabled(true)` in `S3Configuration`, (2) `chunkedEncodingEnabled(false)` on the S3Client, and (3) `checksumValidationEnabled(false)` on the presigner's service configuration. With these three flags, AWS SDK v2 and MinIO work correctly together.

The download endpoint contract changes from returning a binary file stream to returning a JSON body `{"url": "...", "expiresIn": 3600}`. The frontend opens this URL directly to download from MinIO — the Spring app never proxies binary data for downloads.

**Primary recommendation:** Introduce a single `StorageService` Spring bean with three methods (upload, presignedDownloadUrl, delete). Wire it into the three document endpoint handlers. Bucket creation on startup via `ApplicationRunner` prevents silent first-upload failures.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Use `software.amazon.awssdk:s3` v2 (S3-compatible with MinIO, official presigned URL support)
- Create a dedicated `StorageService` Spring bean — extracted from `ResourceController` for single responsibility
- Config via `@ConfigurationProperties` class `MinioProperties` grouping all MinIO env vars
- MinIO config injected via new `MINIO_*` env vars in `.env` and `application.yml` — consistent with existing pattern
- Object key format: `{tenantId}/{documentoId}/{filename}` (matches MIN-04, simple)
- `caminhoArquivo` column reused to store the MinIO object key string — no schema/DB migration needed
- Presigned URL expiry: 1 hour (3600s)
- Bucket name via `MINIO_BUCKET_NAME` env var — no hardcoded bucket names
- `GET /documentos/{id}/download` returns JSON `{"url": "...", "expiresIn": 3600}` — frontend opens URL directly
- Keep existing download audit log (`documento_download` in `t_audit_log`)
- Error when MinIO is unreachable: `503 Service Unavailable` with message "Storage service unavailable"
- File size limits: keep existing Spring Boot defaults (no new explicit cap)
- On startup: verify bucket exists and create if absent

### Claude's Discretion
- `StorageService` method signatures (agreed pattern in CONTEXT.md specifics section)
- `MinioProperties` field naming and defaults
- How to implement the startup bucket check (ApplicationRunner recommended)
- Exception handling strategy inside StorageService

### Deferred Ideas (OUT OF SCOPE)
- Migration script for existing files in `uploads/` directory to MinIO bucket
- Bucket lifecycle policies / expiry rules
- Versioning support in MinIO
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MIN-01 | Uploaded file stored under `{tenantId}/{documentoId}/{filename}` in MinIO bucket; no filesystem file created | AWS SDK v2 `PutObjectRequest` with object key constructed from those three components; remove `Files.write()` call |
| MIN-02 | Download endpoint returns presigned URL (1 hour expiry); user downloads directly from MinIO | `S3Presigner.presignGetObject()` with `Duration.ofSeconds(3600)`; response contract becomes `Map<String, Object>` |
| MIN-03 | Delete document removes MinIO object | `S3Client.deleteObject()` called with stored object key; replaces `Files.deleteIfExists()` |
| MIN-04 | Tenant isolation via object key prefix; one tenant cannot access another's objects | Object key always prefixed with `tenantId.toString()`; controller already validates tenant ownership before calling service |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| File upload (receive multipart) | API / Backend | — | Spring controller receives `MultipartFile` from browser via Next.js proxy |
| Object storage (persist bytes) | External Service (MinIO) | — | MinIO holds the actual file bytes; Spring delegates entirely |
| Presigned URL generation | API / Backend | External Service (MinIO) | Spring generates the signed URL; MinIO serves the download directly to browser |
| Download delivery | External Service (MinIO) | — | Browser hits the presigned URL directly; Spring never streams bytes |
| Tenant isolation enforcement | API / Backend | — | Controller validates `tenantId` ownership before any StorageService call |
| Storage config/credentials | API / Backend (config) | — | `@ConfigurationProperties` class; env vars loaded by `application.yml` |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `software.amazon.awssdk:bom` | 2.46.14 | BOM to align all AWS SDK v2 module versions | Official AWS approach; avoids version mismatches across modules |
| `software.amazon.awssdk:s3` | (managed by BOM) | S3Client + S3Presigner for upload/delete/presign | Official SDK; S3-compatible with MinIO |

[VERIFIED: central.sonatype.com — confirmed latest version 2.46.14 as of 2026-06-18]

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `software.amazon.awssdk:url-connection-client` | (managed by BOM) | Lightweight HTTP client; avoids pulling in Netty or Apache HTTP | Reduces JAR size vs Netty; fine for synchronous Spring MVC service |
| Spring Boot `spring-boot-configuration-processor` | (managed by SB parent) | Generates IDE metadata for `@ConfigurationProperties` | Add as optional dep for MinioProperties autocomplete in IDEs |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `software.amazon.awssdk:s3` (AWS v2) | `io.minio:minio` (MinIO native client) | MinIO native client is simpler for pure MinIO but AWS SDK v2 was decided; it covers presigned URLs and is S3-compatible long-term |
| `url-connection-client` | Default (netty-nio-client) | Default pulls in Netty (large); url-connection-client is sufficient for synchronous server-side use |

**Installation (additions to pom.xml):**

```xml
<!-- In <dependencyManagement> -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bom</artifactId>
    <version>2.46.14</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- In <dependencies> -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>url-connection-client</artifactId>
</dependency>
```

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `software.amazon.awssdk:s3` | Maven Central | 8+ yrs | 10M+/month | github.com/aws/aws-sdk-java-v2 | N/A (Maven) | Approved |
| `software.amazon.awssdk:url-connection-client` | Maven Central | 8+ yrs | N/A | github.com/aws/aws-sdk-java-v2 | N/A (Maven) | Approved |

slopcheck not run (Maven/Java ecosystem, not npm). Both packages are official AWS SDK modules from the `software.amazon.awssdk` group — the same group as every other AWS SDK v2 module. Source repo is the official `aws/aws-sdk-java-v2` repository on GitHub.

[VERIFIED: central.sonatype.com] — `software.amazon.awssdk:s3:2.46.14` exists and is current.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
Browser
  |
  | multipart POST /api/v1/documentos/upload
  v
Next.js proxy (next.config.ts rewrite)
  |
  v
ResourceController.uploadDocumento()
  |── validates file, tenant, replaceId
  |── constructs object key: {tenantId}/{documentoId}/{filename}
  |── calls StorageService.upload(tenantId, documentoId, filename, inputStream, contentType, size)
  |       |
  |       v
  |   S3Client.putObject() --> MinIO bucket
  |
  |── saves Documento entity (caminhoArquivo = object key)
  v
201 Created (Documento JSON)

Browser
  |
  | GET /api/v1/documentos/{id}/download
  v
ResourceController.downloadDocumento()
  |── validates tenant ownership
  |── writes audit log
  |── calls StorageService.presignedDownloadUrl(objectKey)
  |       |
  |       v
  |   S3Presigner.presignGetObject() --> signed URL string
  |
  v
200 OK {"url": "https://minio:9000/bucket/{key}?X-Amz-...", "expiresIn": 3600}

Browser opens presigned URL directly
  |
  v
MinIO serves file bytes directly to browser (no Spring involvement)

Browser
  |
  | DELETE /api/v1/documentos/{id}
  v
ResourceController.deleteDocumento()
  |── validates tenant, legalHold
  |── writes audit log
  |── calls StorageService.delete(objectKey)
  |       |
  |       v
  |   S3Client.deleteObject() --> MinIO removes object
  |
  |── documentoRepository.delete(doc)
  v
200 OK {"message": "Documento removido com sucesso!"}

Startup:
ApplicationRunner / @PostConstruct in StorageService
  |── S3Client.headBucket(bucketName) 
  |   if NoSuchBucketException --> S3Client.createBucket(bucketName)
  v
Bucket ready (or SdkClientException logged as startup warning)
```

### Recommended Project Structure

```
backend/src/main/java/com/lexcv/
├── config/
│   ├── MinioProperties.java       # @ConfigurationProperties("minio")
│   └── MinioConfig.java           # @Bean S3Client + S3Presigner
├── services/
│   └── StorageService.java        # upload / presignedDownloadUrl / delete
└── controllers/
    └── ResourceController.java    # inject StorageService; remove filesystem code
```

### Pattern 1: MinioProperties (@ConfigurationProperties)

**What:** Strongly-typed config class bound to `minio.*` prefix in application.yml.
**When to use:** Any time multiple related env vars need to be grouped — consistent with Spring Boot best practices.

```java
// Source: Spring Boot @ConfigurationProperties pattern
@ConfigurationProperties(prefix = "minio")
@Validated
public class MinioProperties {
    @NotBlank private String endpoint;
    @NotBlank private String accessKey;
    @NotBlank private String secretKey;
    @NotBlank private String bucketName;
    private long presignedUrlExpiry = 3600L; // seconds, with default
}
```

`application.yml` additions:
```yaml
minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ${MINIO_BUCKET_NAME}
  presigned-url-expiry: ${MINIO_PRESIGNED_EXPIRY:3600}
```

`.env.example` additions:
```
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=lexcv-documentos
```

### Pattern 2: MinioConfig Bean Factory

**What:** `@Configuration` class that produces `S3Client` and `S3Presigner` beans using `MinioProperties`.
**When to use:** Two separate beans because `S3Client` (for upload/delete) and `S3Presigner` (for presigned URLs) are different objects in AWS SDK v2 — they cannot be derived from each other.

```java
// Source: AWS SDK v2 official docs + community MinIO workarounds [CITED: medium.com/@AlexanderObregon + github.com/aws/aws-sdk-java-v2/issues/4697]
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public S3Client s3Client(MinioProperties props) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
            props.getAccessKey(), props.getSecretKey());

        return S3Client.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .region(Region.of("us-east-1"))           // MinIO ignores region, but SDK requires one
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)          // REQUIRED for MinIO
                .chunkedEncodingEnabled(false)         // REQUIRED for MinIO compatibility
                .build())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(MinioProperties props) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
            props.getAccessKey(), props.getSecretKey());

        return S3Presigner.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .region(Region.of("us-east-1"))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)          // REQUIRED for MinIO path-style URLs
                .checksumValidationEnabled(false)      // REQUIRED: prevents signed headers that block browser
                .build())
            .build();
    }
}
```

**Why `Region.of("us-east-1")`:** MinIO ignores the region value entirely, but AWS SDK v2 throws if no region is set. Any string works; `us-east-1` is the conventional placeholder. [ASSUMED — based on community practice; MinIO docs confirm it is region-agnostic]

### Pattern 3: StorageService Implementation

```java
// Source: AWS SDK v2 official API [CITED: docs.aws.amazon.com/java/api/latest/...S3Presigner.html]
@Service
@RequiredArgsConstructor
public class StorageService implements ApplicationRunner {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;

    // MIN-01: Upload
    public String upload(UUID tenantId, UUID documentoId, String filename,
                         InputStream inputStream, String contentType, long size) {
        String objectKey = tenantId + "/" + documentoId + "/" + filename;
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(size)
                    .build(),
                RequestBody.fromInputStream(inputStream, size)
            );
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
        return objectKey;
    }

    // MIN-02: Presigned URL
    public String presignedDownloadUrl(String objectKey) {
        try {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiry()))
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(props.getBucketName())
                    .key(objectKey)
                    .build())
                .build();
            return s3Presigner.presignGetObject(req).url().toString();
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    // MIN-03: Delete
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build());
        } catch (SdkException e) {
            throw new StorageUnavailableException("Storage service unavailable", e);
        }
    }

    // Startup bucket check
    @Override
    public void run(ApplicationArguments args) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(props.getBucketName()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(props.getBucketName()).build());
        } catch (SdkException e) {
            // Log warning but don't crash startup — MinIO may start after the app
            log.warn("MinIO unavailable at startup: {}", e.getMessage());
        }
    }
}
```

### Pattern 4: Updated Download Endpoint

**What changes:** Old endpoint streamed `FileSystemResource`; new endpoint returns JSON with presigned URL.

```java
@PreAuthorize("hasAuthority('documentos:view')")
@GetMapping("/documentos/{id}/download")
public ResponseEntity<?> downloadDocumento(@PathVariable UUID id) {
    Documento doc = documentoRepository.findById(id).orElse(null);
    if (doc == null || !doc.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Documento não encontrado"));
    }

    // Keep audit log — unchanged from current impl
    // ...auditLogRepository.save(...)

    try {
        String url = storageService.presignedDownloadUrl(doc.getCaminhoArquivo());
        return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
    } catch (StorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Storage service unavailable"));
    }
}
```

### Anti-Patterns to Avoid

- **Streaming file bytes through Spring for downloads:** The whole point of presigned URLs is to have the browser download directly from MinIO. Never proxy binary through `ResponseEntity<Resource>` in the new implementation.
- **Hardcoding bucket name or region:** Bucket name must come from `MINIO_BUCKET_NAME` env var. Region placeholder `"us-east-1"` is fine; do not expose it as a configurable env var (MinIO doesn't use it).
- **Omitting `pathStyleAccessEnabled(true)`:** Without this, the SDK generates virtual-host style URLs (`bucket.host/key`) which do not resolve with a standalone MinIO instance.
- **Omitting `chunkedEncodingEnabled(false)` on S3Client:** MinIO does not support AWS chunked transfer encoding. Uploads will fail with a signature mismatch without this flag.
- **Catching `IOException` only inside StorageService:** AWS SDK v2 throws `SdkException` (a runtime exception), not `IOException`. Catch `SdkException` or its subclasses (`S3Exception`, `SdkClientException`).
- **Using `file.getBytes()` for the InputStream:** For large files, `getBytes()` loads the entire file into heap. Use `file.getInputStream()` with `file.getSize()` as the content-length for `RequestBody.fromInputStream()`.
- **Blocking startup if MinIO is unavailable:** MinIO may boot after the Spring app in a Docker Compose environment. Catch `SdkException` in the startup check and log a warning rather than throwing.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| S3 request signing / presigned URLs | Custom HMAC-SHA256 signing | `S3Presigner.presignGetObject()` | Signing is complex; clock skew, path encoding, header canonicalization, expiry embedded in URL — all handled by SDK |
| Chunked upload | Custom multipart implementation | `RequestBody.fromInputStream(stream, size)` | SDK handles the wire format |
| Bucket existence check | Custom HTTP HEAD request | `S3Client.headBucket()` + catch `NoSuchBucketException` | SDK maps HTTP 404 to the correct typed exception |
| Config binding / validation | Manual `@Value` + null-checks | `@ConfigurationProperties` + `@Validated` + `@NotBlank` | Spring Boot validates at startup; missing var throws with a clear message |

**Key insight:** The AWS SDK v2 encapsulates all signing logic, retry, and error mapping. The application code should be thin wrappers calling SDK methods; any custom HTTP or signing code is a maintenance liability.

## Common Pitfalls

### Pitfall 1: Missing `pathStyleAccessEnabled` on Presigner

**What goes wrong:** Presigned URLs are generated in virtual-host style (`http://lexcv-documentos.localhost:9000/key`) which fails DNS resolution for MinIO running on localhost or a Docker hostname.
**Why it happens:** AWS SDK v2 defaults to virtual-host style; this works for real AWS S3 where DNS resolves `bucket.s3.amazonaws.com` but not for MinIO.
**How to avoid:** Set `pathStyleAccessEnabled(true)` on the `S3Configuration` passed to BOTH `S3Client.builder()` AND `S3Presigner.builder()`.
**Warning signs:** Presigned URLs in response body contain the bucket name as a subdomain rather than as a path segment.

[CITED: github.com/aws/aws-sdk-java-v2/issues/4958 + github.com/aws/aws-sdk-java-v2/issues/4183]

### Pitfall 2: Chunked Encoding Rejection on Upload

**What goes wrong:** `PUT` to MinIO returns HTTP 400 or signature mismatch error.
**Why it happens:** AWS SDK v2 v2.18+ enables chunked encoding by default for large payloads. MinIO does not implement the AWS chunked encoding extension.
**How to avoid:** Set `chunkedEncodingEnabled(false)` in the `S3Configuration` passed to `S3Client.builder()`.
**Warning signs:** Upload throws `S3Exception` with status 400 and error code `InvalidChunkSizeError` or similar.

[CITED: github.com/aws/aws-sdk-java-v2/issues/4697]

### Pitfall 3: SdkException Not Mapped to 503

**What goes wrong:** MinIO unreachable causes an unhandled `SdkClientException` which maps to 500 Internal Server Error.
**Why it happens:** `SdkException` is a `RuntimeException`; Spring's default handler returns 500.
**How to avoid:** Introduce a custom exception class (`StorageUnavailableException`) thrown by `StorageService`; handle it in the controller with `ResponseEntity.status(503)`. Alternatively, add a `@ExceptionHandler` in a `@RestControllerAdvice`.
**Warning signs:** Clients receive 500 when MinIO is down instead of 503.

### Pitfall 4: Object Key Not Isolated by Tenant

**What goes wrong:** Two tenants with the same `documentoId` (UUID collision is astronomically unlikely but the architecture must enforce isolation regardless) or a bug that allows listing across tenants.
**Why it happens:** If the key is constructed as just `documentoId/filename` without the tenant prefix, the `Documento` table's tenant check is the only guard.
**How to avoid:** Always construct the key as `tenantId.toString() + "/" + documentoId.toString() + "/" + filename`. The tenant prefix is the storage-layer enforcement of MIN-04.

### Pitfall 5: File Replaced Without Deleting Old MinIO Object

**What goes wrong:** On `replace_id` upload, the old MinIO object is never deleted, accumulating orphaned objects.
**Why it happens:** Current code calls `Files.deleteIfExists()` for the old filesystem path. When migrating, this line is replaced but the MinIO delete must also be added for replace flows.
**How to avoid:** In the `replaceId != null` branch of `uploadDocumento`, call `storageService.delete(documento.getCaminhoArquivo())` before saving the new object key.
**Warning signs:** MinIO bucket grows unbounded; storage cost increases while DB has no reference to old objects.

### Pitfall 6: `getBytes()` OOM on Large Files

**What goes wrong:** `file.getBytes()` in the upload endpoint loads the entire file into the JVM heap, causing OOM for files near the 50 MB limit.
**Why it happens:** Current filesystem implementation uses `Files.write(path, file.getBytes())` — this was acceptable for disk I/O but is problematic for streaming to S3.
**How to avoid:** Replace with `file.getInputStream()` passed to `RequestBody.fromInputStream(stream, file.getSize())`. AWS SDK streams it without buffering the full payload.

## Code Examples

### Complete pom.xml changes

```xml
<!-- Add to <dependencyManagement><dependencies> -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bom</artifactId>
    <version>2.46.14</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Add to <dependencies> -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>url-connection-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

[CITED: docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-maven.html]

### Presigned URL generation (GET)

```java
// Source: AWS SDK v2 official Javadoc [CITED: docs.aws.amazon.com/java/api/latest/...S3Presigner.html]
GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
    .signatureDuration(Duration.ofSeconds(3600))
    .getObjectRequest(GetObjectRequest.builder()
        .bucket(bucketName)
        .key(objectKey)
        .build())
    .build();

PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
String url = presigned.url().toString();
```

### Upload via InputStream

```java
// Source: AWS SDK v2 RequestBody API [ASSUMED from SDK documentation patterns]
PutObjectRequest putReq = PutObjectRequest.builder()
    .bucket(bucketName)
    .key(objectKey)
    .contentType(contentType)
    .contentLength(fileSize)
    .build();

s3Client.putObject(putReq, RequestBody.fromInputStream(inputStream, fileSize));
```

### Startup bucket check

```java
// Source: AWS SDK v2 HeadBucketRequest pattern [ASSUMED from SDK documentation patterns]
try {
    s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
} catch (NoSuchBucketException e) {
    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
} catch (SdkException e) {
    log.warn("MinIO not reachable at startup: {}", e.getMessage());
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AWS SDK v1 (`com.amazonaws`) | AWS SDK v2 (`software.amazon.awssdk`) | 2018 (v2 GA) | v2 is the active SDK; v1 in maintenance mode |
| MinIO Java client (`io.minio:minio`) | AWS SDK v2 S3 client | Design decision (phase 50) | v2 is S3-standard; avoids MinIO vendor lock-in |
| Stream file bytes through Spring | Return presigned URL (JSON) | This phase | Offloads bandwidth from app tier; better UX |

**Deprecated/outdated:**
- Filesystem storage (`UPLOAD_DIR`, `Files.write`, `FileSystemResource`): all three usages are removed by this phase.
- `com.amazonaws:aws-java-sdk-s3` (AWS v1): do not introduce; use `software.amazon.awssdk:s3` (v2).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Region.of("us-east-1")` is the conventional MinIO placeholder and MinIO ignores region | Architecture Patterns (MinioConfig) | Low — MinIO is documented as region-agnostic; worst case a different region string is needed |
| A2 | `chunkedEncodingEnabled(false)` on S3Client resolves upload rejection by MinIO | Pitfall 2 / MinioConfig | Medium — if wrong, uploads fail with 400; workaround is to test during implementation |
| A3 | `RequestBody.fromInputStream(stream, size)` streams without buffering the full payload | Code Examples (Upload) | Low — this is standard AWS SDK v2 behaviour; if size is wrong at runtime, SDK will error |

## Open Questions

1. **MinIO HTTPS in production**
   - What we know: presigned URLs embed the MinIO endpoint URL; if MinIO is behind HTTPS in prod, the env var `MINIO_ENDPOINT` must be `https://...`
   - What's unclear: whether the production deployment will use HTTPS for MinIO or keep it HTTP behind a private network
   - Recommendation: Plan treats this as a config concern; `MINIO_ENDPOINT` accepts either protocol. Document in `.env.example`.

2. **Frontend change scope**
   - What we know: `GET /documentos/{id}/download` response changes from binary stream to `{"url": "...", "expiresIn": 3600}`
   - What's unclear: Whether the current frontend download handler expects a blob or a JSON redirect
   - Recommendation: Out of scope for this backend phase, but flag for the follow-on frontend phase.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| MinIO (running instance) | All storage operations | Unknown — dev env dependent | — | Docker Compose `minio/minio` image for local dev |
| Maven (`mvn`) | Build + dependency resolution | Assumed available (per CLAUDE.md) | — | — |

**Missing dependencies with no fallback:**
- MinIO instance: required for integration testing. Plans should include a note that a `docker run -p 9000:9000 minio/minio server /data` is sufficient for local testing.

**Missing dependencies with fallback:**
- None identified.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Spring Boot Test (JUnit 5 + MockMvc) — already configured via `spring-boot-starter-test` |
| Config file | `src/test/resources/application-test.yml` (Wave 0 gap — create) |
| Quick run command | `mvn test -Dtest=StorageServiceTest -pl backend` |
| Full suite command | `mvn test -pl backend` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MIN-01 | Upload stores object under `{tenantId}/{documentoId}/{filename}` key | Unit (mock S3Client) | `mvn test -Dtest=StorageServiceTest#upload_buildsCorrectObjectKey -pl backend` | ❌ Wave 0 |
| MIN-01 | Upload does not write to filesystem | Unit (assert no File.exists) | `mvn test -Dtest=StorageServiceTest#upload_doesNotWriteFilesystem -pl backend` | ❌ Wave 0 |
| MIN-02 | Download returns JSON with `url` and `expiresIn` fields | Unit (mock StorageService) | `mvn test -Dtest=ResourceControllerTest#download_returnsPresignedUrl -pl backend` | ❌ Wave 0 |
| MIN-02 | Download preserves audit log write | Unit (mock repos) | `mvn test -Dtest=ResourceControllerTest#download_writesAuditLog -pl backend` | ❌ Wave 0 |
| MIN-03 | Delete calls `storageService.delete()` with correct object key | Unit (mock StorageService) | `mvn test -Dtest=ResourceControllerTest#delete_callsStorageDelete -pl backend` | ❌ Wave 0 |
| MIN-04 | Object key always prefixed with tenantId | Unit | `mvn test -Dtest=StorageServiceTest#upload_keyPrefixedWithTenantId -pl backend` | ❌ Wave 0 |
| All | MinIO unreachable → 503 | Unit (mock throws SdkException) | `mvn test -Dtest=ResourceControllerTest#minio_unreachable_returns503 -pl backend` | ❌ Wave 0 |
| Startup | Bucket creation on missing bucket | Unit (mock headBucket → NoSuchBucketException) | `mvn test -Dtest=StorageServiceTest#startup_createsBucketIfAbsent -pl backend` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn test -Dtest=StorageServiceTest -pl backend`
- **Per wave merge:** `mvn test -pl backend`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/src/test/java/com/lexcv/services/StorageServiceTest.java` — covers MIN-01, MIN-03, MIN-04, startup check
- [ ] `backend/src/test/java/com/lexcv/controllers/ResourceControllerTest.java` — covers MIN-02 and 503 error cases (may extend existing controller test if present)

## Security Domain

### Applicable ASVS Categories (ASVS Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Auth handled by JWT cookie filter — unchanged |
| V3 Session Management | No | Session handling unchanged |
| V4 Access Control | Yes | `@PreAuthorize("hasAuthority('documentos:view')")` on download; tenant ownership validated before StorageService call |
| V5 Input Validation | Yes | Filename sanitization: do not trust `file.getOriginalFilename()` directly in object key — the existing code already uses a generated UUID `fileId` as the storage name, preserving original name only in `nome` column. This pattern must be preserved. |
| V6 Cryptography | No | Presigned URL signing is handled entirely by AWS SDK; no custom crypto |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal in object key | Tampering | Object key is composed of UUID strings (`tenantId`, `documentoId`) plus `filename`; UUIDs cannot contain `/..`. Filename sanitization: strip path separators from `originalFilename` before including in key |
| Cross-tenant object access | Information Disclosure | Controller validates `doc.getTenantId().equals(getTenantId())` before calling StorageService; object key prefix with tenantId is defence-in-depth |
| Presigned URL leakage | Information Disclosure | URLs expire in 3600s; no server-side revocation needed at ASVS L1. Do not log presigned URLs (they contain auth credentials in query string) |
| Credentials in config | Information Disclosure | `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` loaded from env vars, never hardcoded. Consistent with existing `.env` pattern |
| MinIO bucket public access | Information Disclosure | Bucket should not have public-read policy; only presigned URLs grant temporary access. This is the default MinIO behaviour — do not override it |

**Filename sanitisation note:** The existing upload code creates a separate `savedName = fileId + extension` for the filesystem path, keeping `originalFilename` only in the DB `nome` column. For MinIO, the same separation applies: the object key component derived from the filename should either use the UUID-based name or be sanitised to remove path traversal characters. The CONTEXT.md specifies `{tenantId}/{documentoId}/{filename}` — `filename` here should be treated as the original file name stored in `nome`, not a raw user input inserted into a path. Sanitise before use.

## Sources

### Primary (HIGH confidence)

- [central.sonatype.com/artifact/software.amazon.awssdk/s3](https://central.sonatype.com/artifact/software.amazon.awssdk/s3) — confirmed latest version 2.46.14
- [docs.aws.amazon.com — AWS SDK v2 Maven setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-maven.html) — BOM pattern, dependency declarations
- [docs.aws.amazon.com — S3Presigner Javadoc](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html) — presignGetObject API, checksumValidationEnabled

### Secondary (MEDIUM confidence)

- [medium.com/@AlexanderObregon — Spring Boot with MinIO](https://medium.com/@AlexanderObregon/using-spring-boot-with-minio-for-s3-compatible-storage-676911bcec21) — pathStyleAccessEnabled pattern
- [github.com/aws/aws-sdk-java-v2/issues/4697](https://github.com/aws/aws-sdk-java-v2/issues/4697) — presigner signature change with HTTP + endpointOverride; chunked encoding issue
- [github.com/aws/aws-sdk-java-v2/issues/4958](https://github.com/aws/aws-sdk-java-v2/issues/4958) — forcePathStyle not respected by presigner; community workaround

### Tertiary (LOW confidence)

- WebSearch results for MinIO + AWS SDK v2 community patterns — cross-referenced with official issues above

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — confirmed on Maven Central; BOM pattern from official AWS docs
- Architecture: HIGH — based on actual existing code read + official SDK API
- Pitfalls: MEDIUM — presigner/MinIO issues confirmed via GitHub issues; workarounds from community sources cross-referenced with SDK docs

**Research date:** 2026-06-19
**Valid until:** 2026-09-19 (AWS SDK v2 is stable; MinIO S3 compatibility is a slow-moving target)
