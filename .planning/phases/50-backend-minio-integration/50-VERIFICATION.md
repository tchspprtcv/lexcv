---
phase: 50-backend-minio-integration
verified: 2026-06-19T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Upload a document via POST /api/v1/documentos/upload and confirm no file appears in the container filesystem (e.g., ls uploads/)"
    expected: "Object lands in MinIO under {tenant_id}/{documento_id}/{filename}; no file created under uploads/ or any local path"
    why_human: "Filesystem absence cannot be proven by grep; requires running the app with MinIO and inspecting both the bucket and the container volume"
  - test: "Click Descarregar on a document in the UI or call GET /api/v1/documentos/{id}/download"
    expected: "Response is JSON {url, expiresIn: 3600}; pasting the URL in a browser downloads the file directly from MinIO without any additional auth cookie"
    why_human: "Presigned URL validity requires live MinIO; browser download behaviour cannot be verified statically"
  - test: "Delete a document via DELETE /api/v1/documentos/{id} and confirm the object is gone from MinIO"
    expected: "MinIO console or mc ls shows the object no longer exists in the bucket"
    why_human: "MinIO state change requires a live environment"
  - test: "Verify tenant isolation: upload a document as tenant A, then attempt to access it via a session belonging to tenant B"
    expected: "Tenant B receives 404; the MinIO object key starts with tenant A's UUID, so tenant B can never construct a valid path"
    why_human: "Multi-tenant runtime behaviour requires two separate authenticated sessions"
---

# Phase 50: Backend MinIO Integration — Verification Report

**Phase Goal:** O backend armazena, serve e elimina ficheiros de documentos no MinIO em vez do filesystem local, com isolamento por tenant e downloads seguros via URLs pré-assinadas
**Verified:** 2026-06-19
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ficheiro carregado armazenado no MinIO sob prefixo `{tenant_id}/{documento_id}/{filename}`; nenhum ficheiro criado no filesystem | VERIFIED (static) | `StorageService.upload()` constructs `tenantId.toString() + "/" + documentoId.toString() + "/" + sanitisedFilename` (line 42); `ResourceController.uploadDocumento` calls `storageService.upload(getTenantId(), documentoId, ...)` and stores the returned key in `caminhoArquivo`; no `Files.write`, `UPLOAD_DIR`, or `file.getBytes()` found anywhere in `ResourceController` |
| 2 | Utilizador recebe URL pré-assinada temporária que permite download direto do MinIO sem autenticação adicional | VERIFIED (static) | `downloadDocumento` calls `storageService.presignedDownloadUrl(doc.getCaminhoArquivo())` (line 1833) and returns `ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600))` (line 1834); `StorageService.presignedDownloadUrl()` uses `signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiry()))` and returns the URL string; `FileSystemResource` absent from controller |
| 3 | Ao apagar um documento, o objeto desaparece do bucket MinIO | VERIFIED (static) | `deleteDocumento` calls `storageService.delete(doc.getCaminhoArquivo())` (line 1860) before `documentoRepository.delete(doc)` (line 1877); `StorageService.delete()` calls `s3Client.deleteObject`; `Files.deleteIfExists` is absent from `ResourceController` |
| 4 | Documentos de um tenant nunca acessíveis através de prefixos de outro tenant — isolamento garantido pelo prefixo de path | VERIFIED (static) | Object key is always prefixed with `tenantId.toString()` (StorageService line 42); `downloadDocumento` and `deleteDocumento` both check `doc.getTenantId().equals(getTenantId())` before any StorageService call (lines 1816, 1845); `uploadDocumento` replaceId branch checks `documento.getTenantId().equals(getTenantId())` (line 1746); single shared bucket confirmed by `MinioProperties.bucketName` |

**Score:** 4/4 truths verified (static analysis)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/config/MinioProperties.java` | `@ConfigurationProperties(prefix="minio")` with endpoint, accessKey, secretKey, bucketName, presignedUrlExpiry | VERIFIED | All 5 fields present; `@NotBlank` on required fields; `@Min(1)` on expiry; bonus `publicEndpoint` / `getEffectivePublicEndpoint()` for Docker split-host deployments |
| `backend/src/main/java/com/lexcv/config/MinioConfig.java` | `@Bean S3Client` with `pathStyleAccessEnabled(true)` + `chunkedEncodingEnabled(false)`; `@Bean S3Presigner` with `pathStyleAccessEnabled(true)` + `checksumValidationEnabled(false)` | VERIFIED | Both beans present; all four MinIO compatibility flags confirmed; `@EnableConfigurationProperties(MinioProperties.class)` present |
| `backend/src/main/java/com/lexcv/services/StorageService.java` | `upload()`, `presignedDownloadUrl()`, `delete()` + `ApplicationRunner` bucket check | VERIFIED | All three methods implemented; `implements ApplicationRunner`; `run()` uses `headBucket` → `createBucket` on `NoSuchBucketException`; `log.warn` on broader `SdkException` (does not throw); filename sanitisation `replaceAll("[/\\\\]", "_")` present |
| `backend/src/main/java/com/lexcv/exceptions/StorageUnavailableException.java` | `extends RuntimeException` with `(String message, Throwable cause)` constructor | VERIFIED | Exact match; single constructor calling `super(message, cause)` |
| `backend/src/main/resources/application.yml` | `minio:` block with `${MINIO_ENDPOINT}`, `${MINIO_ACCESS_KEY}`, `${MINIO_SECRET_KEY}`, `${MINIO_BUCKET_NAME}`, `${MINIO_PRESIGNED_EXPIRY:3600}` | VERIFIED | All 5 vars present; bonus `public-endpoint` with fallback `${MINIO_PUBLIC_ENDPOINT:${MINIO_ENDPOINT}}` |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | `uploadDocumento`, `downloadDocumento`, `deleteDocumento` refactored to use `StorageService` | VERIFIED | `private final StorageService storageService` injected (line 65); all three methods delegate to storage service exclusively |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MinioConfig` | `MinioProperties` | `@EnableConfigurationProperties(MinioProperties.class)` | WIRED | Line 17 of MinioConfig.java |
| `StorageService` | `S3Client` / `S3Presigner` | `@RequiredArgsConstructor` + final fields | WIRED | Fields declared lines 31-33; `@RequiredArgsConstructor` picks them up |
| `StorageService.upload()` | objectKey | `tenantId.toString() + "/" + documentoId.toString() + "/" + sanitisedFilename` | WIRED | Line 42 of StorageService.java |
| `ResourceController` | `StorageService` | `private final StorageService storageService` + `@RequiredArgsConstructor` | WIRED | Line 65; import line 6 of ResourceController.java |
| `uploadDocumento` | `storageService.upload()` | `file.getInputStream()`, `file.getSize()` | WIRED | Lines 1753, 1767 of ResourceController.java |
| `downloadDocumento` | `storageService.presignedDownloadUrl()` | `doc.getCaminhoArquivo()` | WIRED | Line 1833 of ResourceController.java |
| `deleteDocumento` | `storageService.delete()` | `doc.getCaminhoArquivo()` | WIRED | Line 1860 of ResourceController.java |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `ResourceController.uploadDocumento` | `objectKey` / `caminhoArquivo` | `storageService.upload()` → `s3Client.putObject()` → MinIO | Yes (live S3 call; not hardcoded) | FLOWING |
| `ResourceController.downloadDocumento` | `url` | `storageService.presignedDownloadUrl()` → `s3Presigner.presignGetObject()` | Yes (live presign; not hardcoded) | FLOWING |
| `ResourceController.deleteDocumento` | (side-effect: object removed) | `storageService.delete()` → `s3Client.deleteObject()` | Yes (live S3 call) | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — verification requires a live MinIO instance; app cannot be started without database and MinIO configuration. See Human Verification section.

### Probe Execution

Step 7c: No probe scripts found under `scripts/*/tests/probe-*.sh`. Phase does not declare probes in PLAN or SUMMARY frontmatter. SKIPPED.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| MIN-01 | 50-01, 50-02 | Armazena ficheiros no MinIO via AWS S3 SDK em vez do filesystem local | SATISFIED | StorageService uses `software.amazon.awssdk:s3`; ResourceController has zero filesystem write calls |
| MIN-02 | 50-01, 50-02 | Download via URL pré-assinada temporária gerada pelo backend | SATISFIED | `presignedDownloadUrl()` in StorageService; `downloadDocumento` returns `{url, expiresIn}` JSON |
| MIN-03 | 50-01, 50-02 | Ao apagar documento, objeto removido do bucket MinIO | SATISFIED | `deleteDocumento` calls `storageService.delete()` before `documentoRepository.delete()` |
| MIN-04 | 50-01, 50-02 | Objetos guardados com prefixo `{tenant_id}/{documento_id}/{filename}` | SATISFIED | `StorageService.upload()` line 42 constructs key with tenant and documento UUIDs as path segments |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No TBD/FIXME/XXX markers found in phase files | — | None |
| — | — | No `return null` / empty stubs found in implementation files | — | None |
| — | — | `file.getBytes()` absent from ResourceController | — | Correct streaming via `getInputStream()` |

One deliberate ordering deviation noted (not a defect): the PLAN (50-02, Task 1 action step 5b) described the replaceId branch as "delete OLD MinIO object then upload new"; the actual implementation does upload-first then delete (with a clarifying comment "Upload the new object BEFORE deleting the old one — if the upload fails, the old object remains intact (WR-01)"). This is a safer implementation and correctly satisfies MIN-03.

### Human Verification Required

#### 1. File stored in MinIO — no filesystem artifact

**Test:** Upload a document via `POST /api/v1/documentos/upload`. After the request succeeds, check inside the running container: `ls uploads/` (or whatever path was previously used). Then check MinIO via the console or `mc ls lexcv-documentos/{tenant_id}/{documento_id}/`.
**Expected:** No file appears on the container filesystem; the object appears in MinIO under the correct prefix.
**Why human:** Static analysis confirms the code calls `storageService.upload()` and not `Files.write()`, but only a live run can confirm no other code path writes to disk and that the MinIO connection actually succeeds.

#### 2. Presigned URL enables direct browser download

**Test:** Call `GET /api/v1/documentos/{id}/download` for a document. Copy the returned `url` value and open it in a browser (no cookies / no auth headers).
**Expected:** The file downloads directly from MinIO. The URL expires after the configured period (default 3600 s).
**Why human:** Whether the presigned URL is browser-usable (correct scheme, reachable hostname, valid signature) depends on `MINIO_PUBLIC_ENDPOINT` configuration and MinIO network exposure — not verifiable by grep.

#### 3. Object deleted from MinIO on document deletion

**Test:** Delete a document via `DELETE /api/v1/documentos/{id}`. Then open the MinIO console or run `mc ls` on the bucket at the former object path.
**Expected:** The object is absent from the bucket. The DB row is also gone.
**Why human:** MinIO bucket state change requires a live environment.

#### 4. Tenant isolation at runtime

**Test:** Log in as a user belonging to tenant A and upload a document. Note the MinIO object key from the DB (`caminhoArquivo`). Then log in as a user belonging to tenant B and attempt `GET /api/v1/documentos/{id}/download` using the document ID from tenant A.
**Expected:** 404 response. The presigned URL is never generated for tenant B because the controller ownership check (`doc.getTenantId().equals(getTenantId())`) rejects the request before StorageService is called.
**Why human:** Multi-tenant runtime behaviour requires two separate authenticated sessions.

---

_Verified: 2026-06-19_
_Verifier: Claude (gsd-verifier)_
