---
phase: "50"
plan: "01"
subsystem: backend/storage
tags: [minio, s3, storage, config, infrastructure]
dependency_graph:
  requires: []
  provides: [StorageService, MinioConfig, MinioProperties, StorageUnavailableException]
  affects: [backend/pom.xml, backend/application.yml]
tech_stack:
  added: [software.amazon.awssdk:s3:2.46.14, software.amazon.awssdk:url-connection-client:2.46.14]
  patterns: [ConfigurationProperties, ApplicationRunner bucket-check, S3Client/S3Presigner beans]
key_files:
  created:
    - backend/src/main/java/com/lexcv/config/MinioProperties.java
    - backend/src/main/java/com/lexcv/config/MinioConfig.java
    - backend/src/main/java/com/lexcv/services/StorageService.java
    - backend/src/main/java/com/lexcv/exceptions/StorageUnavailableException.java
  modified:
    - backend/pom.xml
    - backend/src/main/resources/application.yml
    - backend/.env.example
decisions:
  - "Hardcoded Region.of(us-east-1) in MinioConfig — MinIO ignores region; satisfies SDK requirement without extra env var"
  - "Filename sanitised with replaceAll([/\\\\], _) in upload() to prevent path traversal (T-50-01)"
  - "Startup SdkException logged as warn and swallowed — allows app to start without MinIO (T-50-03 accept)"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-19T15:33:15Z"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 50 Plan 01: MinIO Infrastructure Layer Summary

**One-liner:** AWS SDK v2 S3Client/S3Presigner beans wired to MinIO via ConfigurationProperties with tenant-scoped object keys and startup bucket auto-create.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Add AWS SDK v2 deps and MinIO config classes | c8b5945 | pom.xml, application.yml, .env.example, MinioProperties.java, MinioConfig.java |
| 2 | Create StorageUnavailableException and StorageService | a8b84c5 | StorageUnavailableException.java, StorageService.java |

## What Was Built

A complete MinIO storage infrastructure layer:

- **MinioProperties** — `@ConfigurationProperties(prefix="minio")` with `@Validated @NotBlank` on all credential fields; fails fast at startup if any `MINIO_*` var is missing
- **MinioConfig** — produces `S3Client` bean (pathStyleAccessEnabled=true, chunkedEncodingEnabled=false) and `S3Presigner` bean (pathStyleAccessEnabled=true, checksumValidationEnabled=false); all three MinIO compatibility flags present
- **StorageService** — `@Service` implementing `ApplicationRunner`; exposes `upload()`, `presignedDownloadUrl()`, `delete()`; object key is `{tenantId}/{documentoId}/{sanitisedFilename}`; startup runner auto-creates bucket if absent, warns and continues if MinIO is unreachable
- **StorageUnavailableException** — `RuntimeException(String, Throwable)` for 503 propagation in Plan 50-02

## Verification

- `mvn compile` exits 0 with all new classes
- `pathStyleAccessEnabled(true)` appears twice in MinioConfig (once per bean)
- `chunkedEncodingEnabled(false)` present on S3Client bean
- `checksumValidationEnabled(false)` present on S3Presigner bean
- `StorageService` contains `implements ApplicationRunner`
- `StorageService` does NOT contain `getBytes()`
- `backend/.env.example` contains all four `MINIO_*` stubs

## Deviations from Plan

None — plan executed exactly as written.

## Threat Mitigations Applied

| Threat ID | Mitigation |
|-----------|-----------|
| T-50-01 | `filename.replaceAll("[/\\\\]", "_")` in `upload()` strips path separators before building object key |
| T-50-02 | All credentials come from env vars via `MinioProperties`; `@NotBlank` ensures fail-fast on missing vars; credentials never logged |
| T-50-03 | `SdkException` in `run()` caught and logged as `warn` — app starts regardless of MinIO availability |

## Self-Check: PASSED

- `backend/src/main/java/com/lexcv/config/MinioProperties.java` — FOUND
- `backend/src/main/java/com/lexcv/config/MinioConfig.java` — FOUND
- `backend/src/main/java/com/lexcv/services/StorageService.java` — FOUND
- `backend/src/main/java/com/lexcv/exceptions/StorageUnavailableException.java` — FOUND
- Commit c8b5945 — FOUND
- Commit a8b84c5 — FOUND
