---
phase: "50"
plan: "02"
subsystem: backend/controllers
tags: [minio, s3, storage, refactor, documents]
dependency_graph:
  requires: [StorageService, StorageUnavailableException]
  provides: [uploadDocumento-minio, downloadDocumento-presigned, deleteDocumento-minio]
  affects: [backend/src/main/java/com/lexcv/controllers/ResourceController.java]
tech_stack:
  added: []
  patterns: [presigned-url, 503-error-propagation, tenant-scoped-object-keys]
key_files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - "StorageUnavailableException caught at each document endpoint individually; returns 503 with body {message: 'Storage service unavailable'}"
  - "downloadDocumento audit log written BEFORE presignedDownloadUrl call (T-34-03 preserved)"
  - "deleteDocumento audit log written BEFORE storageService.delete() call (T-34-03 preserved)"
  - "replaceId branch: storageService.delete(oldKey) called before storageService.upload() to avoid orphaned MinIO objects"
  - "IOException from file.getInputStream() caught separately; returns 500 with 'Erro ao ler ficheiro'"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-19T16:00:00Z"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 50 Plan 02: ResourceController MinIO Integration Summary

**One-liner:** Refactored uploadDocumento, downloadDocumento, and deleteDocumento in ResourceController to delegate all storage I/O to StorageService; no filesystem references remain; presigned URL response replaces binary streaming.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Inject StorageService and refactor uploadDocumento | 27a7883 | ResourceController.java |
| 2 | Refactor downloadDocumento and deleteDocumento; remove dead imports | 985a83f | ResourceController.java |

## What Was Built

Complete MinIO integration for the three document endpoints in `ResourceController`:

- **uploadDocumento** — removed `UPLOAD_DIR` constant, `new File()`, `Files.write()`, `file.getBytes()`. Now calls `storageService.upload(tenantId, documentoId, originalName, inputStream, contentType, size)` and stores the returned objectKey in `documento.caminhoArquivo`. The `replaceId` branch calls `storageService.delete(oldKey)` before uploading the new object. `StorageUnavailableException` returns 503; `IOException` from `getInputStream()` returns 500.
- **downloadDocumento** — removed `new File(path)` and `file.exists()` check. Audit log preserved before the storage call. Returns `ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600))` from `storageService.presignedDownloadUrl()`. No binary streaming through Spring. 503 on MinIO unreachable.
- **deleteDocumento** — replaced `Files.deleteIfExists(Paths.get(...))` with `storageService.delete(doc.getCaminhoArquivo())`. Audit log and `documentoRepository.delete(doc)` call order preserved. 503 on MinIO unreachable.
- **Import cleanup** — removed: `FileSystemResource`, `Resource`, `HttpHeaders`, `java.io.File`, `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.Paths`.

## Verification

- `mvn compile` exits 0
- `storageService.upload(` appears 2 times (replaceId branch + new-doc branch)
- `storageService.presignedDownloadUrl(` appears 1 time
- `storageService.delete(` appears 2 times (replaceId cleanup + deleteDocumento)
- `FileSystemResource` appears 0 times
- `Files.deleteIfExists` appears 0 times
- `expiresIn` appears 1 time (JSON key in download response)
- `documento_download` appears 1 time (audit log preserved)
- `documento_eliminacao` appears 1 time (audit log preserved)
- `SERVICE_UNAVAILABLE` appears 3 times (one per endpoint)

## Deviations from Plan

None — plan executed exactly as written.

## Threat Mitigations Applied

| Threat ID | Mitigation |
|-----------|-----------|
| T-50-04 | Presigned URL returned directly in response body; never logged; 3600s expiry via StorageService |
| T-50-05 | `doc.getTenantId().equals(getTenantId())` check precedes every StorageService call in all three endpoints |
| T-50-06 | `@PreAuthorize("hasAuthority('documentos:edit')")` on upload and delete endpoints preserved unchanged |
| T-50-07 | `StorageUnavailableException` caught at each endpoint; returns 503 with `{message: "Storage service unavailable"}` |

## Self-Check: PASSED

- Commit 27a7883 — FOUND
- Commit 985a83f — FOUND
- `ResourceController.java` contains `storageService.upload(` — CONFIRMED
- `ResourceController.java` contains `storageService.presignedDownloadUrl(` — CONFIRMED
- `ResourceController.java` contains `storageService.delete(` — CONFIRMED
- `ResourceController.java` does NOT contain `FileSystemResource` — CONFIRMED
- `ResourceController.java` does NOT contain `Files.deleteIfExists` — CONFIRMED
- `ResourceController.java` contains `expiresIn` — CONFIRMED
- `mvn compile` exits 0 — CONFIRMED
