---
phase: 50-backend-minio-integration
reviewed: 2026-06-19T17:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - backend/src/main/java/com/lexcv/config/MinioProperties.java
  - backend/src/main/java/com/lexcv/config/MinioConfig.java
  - backend/src/main/java/com/lexcv/services/StorageService.java
  - backend/src/main/java/com/lexcv/exceptions/StorageUnavailableException.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/resources/application.yml
  - backend/.env.example
findings:
  critical: 4
  warning: 3
  info: 1
  total: 8
status: issues_found
---

# Phase 50: Code Review Report

**Reviewed:** 2026-06-19T17:00:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the MinIO integration layer (Plan 50-01) and the ResourceController refactor (Plan 50-02). The infrastructure wiring is correct — SDK flags, ConfigurationProperties, and object-key construction all look sound. Four critical defects were found, spanning a guaranteed NullPointerException on every upload, an uncaught exception that can abort startup when MinIO bucket auto-creation fails, a false audit log written on failed delete, and an internal MinIO hostname leaking into presigned URLs returned to the browser.

---

## Critical Issues

### CR-01: NullPointerException on every upload when original filename is absent

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1737`

**Issue:** `file.getOriginalFilename()` returns `null` when the multipart part has no `filename` header (e.g. curl without `; filename=`, or any programmatic client that omits it). The `null` value is passed directly to `StorageService.upload()` as the `filename` parameter, where `filename.replaceAll("[/\\\\]", "_")` immediately throws a `NullPointerException`. This NPE is NOT caught by the `catch (IOException e)` or `catch (StorageUnavailableException e)` handlers; it propagates as a 500 with a full stack trace.

**Fix:**
```java
// ResourceController.java, line 1737 — guard before use
String originalName = file.getOriginalFilename();
if (originalName == null || originalName.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "Nome do ficheiro em falta"));
}
```

Alternatively, provide a safe fallback:
```java
String originalName = file.getOriginalFilename() != null
        ? file.getOriginalFilename()
        : "ficheiro_sem_nome";
```

---

### CR-02: Startup aborted when bucket auto-creation fails (SdkException escapes run())

**File:** `backend/src/main/java/com/lexcv/services/StorageService.java:110-117`

**Issue:** The `createBucket` call at line 112 sits inside the `catch (NoSuchBucketException e)` block. If `createBucket` itself throws an `SdkException` (e.g. credentials lack `s3:CreateBucket` permission, or network drops between the `headBucket` check and the `createBucket` call), that exception is NOT caught by the sibling `catch (SdkException e)` block at line 115 — sibling catch clauses do not cover exceptions thrown inside their predecessor's body. The uncaught `SdkException` propagates out of `ApplicationRunner.run()`, which Spring treats as a fatal startup error and shuts down the application. The documented intent (T-50-03) is that MinIO unavailability must never prevent startup; this case violates that invariant.

**Fix:**
```java
@Override
public void run(ApplicationArguments args) {
    try {
        HeadBucketRequest headRequest = HeadBucketRequest.builder()
                .bucket(props.getBucketName()).build();
        s3Client.headBucket(headRequest);
        log.info("MinIO bucket '{}' verified.", props.getBucketName());
    } catch (NoSuchBucketException e) {
        log.info("MinIO bucket '{}' not found — creating.", props.getBucketName());
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(props.getBucketName()).build());
        } catch (SdkException ce) {
            log.warn("MinIO bucket creation failed: {}", ce.getMessage());
        }
    } catch (SdkException e) {
        log.warn("MinIO unavailable at startup: {}", e.getMessage());
    }
}
```

---

### CR-03: Presigned URL contains internal MinIO hostname — unreachable from browser

**File:** `backend/src/main/java/com/lexcv/services/StorageService.java:63-78`

**Issue:** `S3Presigner` is constructed with `endpointOverride(URI.create(props.getEndpoint()))` where `MINIO_ENDPOINT` is typically an internal address (e.g. `http://minio:9000` or `http://localhost:9000` in Docker deployments). The presigned URL produced by `presignGetObject` embeds this same host in the URL string. The controller returns this URL directly to the frontend (`downloadDocumento` response body). The browser then attempts to `GET` an URL containing an internal Docker/VPC hostname that is unreachable from the public internet, resulting in a silent network error on every download.

AWS S3 real deployments share the same public endpoint for API calls and presigned URLs, so this is a MinIO-specific deployment concern that was not accounted for. A public-facing endpoint for presigned URLs differs from the backend-to-MinIO API endpoint.

**Fix:** Introduce a separate `MINIO_PUBLIC_ENDPOINT` env var used only when building the `S3Presigner`, so the signed URL contains the publicly reachable host:

```yaml
# application.yml
minio:
  endpoint: ${MINIO_ENDPOINT}           # backend → MinIO (internal)
  public-endpoint: ${MINIO_PUBLIC_ENDPOINT:${MINIO_ENDPOINT}}  # presigned URL host
```

```java
// MinioProperties.java
private String publicEndpoint;  // falls back to endpoint if not set
```

```java
// MinioConfig.java — S3Presigner uses publicEndpoint
.endpointOverride(URI.create(
    props.getPublicEndpoint() != null ? props.getPublicEndpoint() : props.getEndpoint()))
```

---

### CR-04: Audit log records deletion before delete succeeds; returns 503 leaving a phantom audit entry

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1852-1865`

**Issue:** The `documento_eliminacao` audit log is written to the database (line 1852) before `storageService.delete()` is called (line 1862). If MinIO is unreachable, `StorageUnavailableException` is caught and the method returns 503. At this point:
- The audit log asserts that the document was deleted.
- The MinIO object was NOT deleted.
- `documentoRepository.delete(doc)` at line 1868 was NOT reached.
- The `Documento` record still exists in the database.

The system is now in an inconsistent state: the audit trail claims deletion of a document that remains fully intact. For a legal platform, a false audit entry asserting deletion is a compliance defect, not merely a cosmetic one.

The SUMMARY comments say "audit log written BEFORE storageService.delete() call (T-34-03 preserved)". T-34-03 preserves the log before the *DB entity* is removed so the FK is valid — but writing the log before the storage call is unnecessary and causes this false positive.

**Fix:** Move the audit log write to after the `storageService.delete()` succeeds and before `documentoRepository.delete()`:

```java
try {
    storageService.delete(doc.getCaminhoArquivo());
} catch (StorageUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Storage service unavailable"));
}

// Audit log written after storage delete succeeds, before DB entity removal (T-34-03)
auditLogRepository.save(AuditLog.builder()
        .tenantId(delPrincipal.getTenantId())
        ...
        .build());

documentoRepository.delete(doc);
```

---

## Warnings

### WR-01: replaceId upload — old object deleted before new upload; DB left with stale key on upload failure

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1745-1753`

**Issue:** In the `replaceId` branch, `storageService.delete(oldKey)` is called at line 1745 before `storageService.upload()` at line 1747. If `storageService.upload()` throws `StorageUnavailableException`, the old MinIO object is already gone (permanently deleted), but `documento.caminhoArquivo` in the database still points to the old (now-deleted) key. The document record exists in the DB but its object is gone from storage. Any subsequent download will return 404 from MinIO.

The fix is either: (a) upload first, update DB, then delete the old object; or (b) keep the old key until both upload and DB save succeed:

```java
// Safer ordering: upload first, then delete old on success
String objectKey = storageService.upload(...);
storageService.delete(documento.getCaminhoArquivo()); // only after upload succeeds
documento.setCaminhoArquivo(objectKey);
```

---

### WR-02: presignedUrlExpiry has no minimum validation — zero or negative value crashes at runtime

**File:** `backend/src/main/java/com/lexcv/config/MinioProperties.java:25`

**Issue:** `presignedUrlExpiry` is a plain `long` with default `3600L` but no `@Min` constraint. If `MINIO_PRESIGNED_EXPIRY=0` or a negative value is set in the environment (or the env var is present but empty, which resolves to `0` after YAML coercion), `Duration.ofSeconds(0)` is passed to the AWS SDK presigner, which will throw `IllegalArgumentException: Expiration must be positive`. This exception is NOT an `SdkException`, so it is NOT caught by the `StorageUnavailableException` handler in the controller — it propagates as a 500 on every download request.

**Fix:**
```java
// MinioProperties.java
@Min(1)
private long presignedUrlExpiry = 3600L;
```

---

### WR-03: MinIO credentials exposed via Spring Boot Actuator if endpoint is unsecured

**File:** `backend/src/main/java/com/lexcv/config/MinioProperties.java:19-23`

**Issue:** `MinioProperties` is a `@Data`-annotated `@ConfigurationProperties` class. Spring Boot Actuator's `/actuator/env` and `/actuator/configprops` endpoints include all `@ConfigurationProperties` values in their output. The `secretKey` field name does not match Spring's default masking patterns (`password`, `secret`, `key`, `token`, `credentials`) — the field is named `secretKey` which DOES match the `key` pattern in Spring Boot 2.x+ sanitization, but `accessKey` may not be masked. If Actuator is reachable (no explicit security config is visible for Actuator in the reviewed files), the MinIO access credentials may be exposed.

**Fix:** Explicitly annotate sensitive fields or configure Spring sanitization:
```java
// application.yml — ensure Actuator sanitizes minio keys
management:
  endpoint:
    env:
      additional-keys-to-sanitize: minio.access-key,minio.secret-key
```

Or limit Actuator exposure to health-only in non-dev profiles.

---

## Info

### IN-01: Filename sanitization is narrow — only strips path separators, not other shell-special characters

**File:** `backend/src/main/java/com/lexcv/services/StorageService.java:41`

**Issue:** `filename.replaceAll("[/\\\\]", "_")` prevents path separator traversal in the object key, which is the stated goal (T-50-01). However, the object key is not used in a shell context — it goes to S3 API — so the remaining concern is object-key characters that MinIO/S3 treats specially (e.g. `#`, `?`, `%`, `+`, which percent-encoded in presigned URLs can cause signature mismatches or URL parsing bugs when the presigned URL is opened by the browser). This is not a security vulnerability but can cause download failures for files whose names contain these characters.

**Fix:** Consider a stricter allowlist:
```java
String sanitisedFilename = filename
        .replaceAll("[/\\\\]", "_")
        .replaceAll("[#?%+&=]", "_");
```

---

_Reviewed: 2026-06-19T17:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
