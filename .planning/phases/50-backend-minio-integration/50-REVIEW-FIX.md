---
phase: 50-backend-minio-integration
fixed_at: 2026-06-19T17:30:00Z
review_path: .planning/phases/50-backend-minio-integration/50-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 5
skipped: 1
status: partial
---

# Phase 50: Code Review Fix Report

**Fixed at:** 2026-06-19T17:30:00Z
**Source review:** .planning/phases/50-backend-minio-integration/50-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 6 (CR-01, CR-02, CR-03, CR-04, WR-01, WR-02)
- Fixed: 5 (CR-01, CR-02, CR-03 + WR-02 combined, CR-04, WR-01)
- Skipped: 1 (WR-03 — explicitly excluded from scope)

Note: WR-02 (`@Min(1)` constraint) was applied atomically together with CR-03 in the same `MinioProperties.java` edit; both are captured in commit `442fa26`.

## Fixed Issues

### CR-01: NullPointerException on every upload when original filename is absent

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `eaa6828`
**Applied fix:** Moved `file.getOriginalFilename()` call outside the `try` block, immediately after the `file.isEmpty()` guard. Added an early-return 400 response if `originalName` is null or blank, preventing the NPE in `StorageService.upload()`.

---

### CR-02: Startup aborted when bucket auto-creation fails

**Files modified:** `backend/src/main/java/com/lexcv/services/StorageService.java`
**Commit:** `b8b217a`
**Applied fix:** Wrapped the `s3Client.createBucket()` call inside its own nested `try { ... } catch (SdkException ce)` block. A failure to create the bucket now logs a warning and does not propagate, preserving the startup-resilience invariant (T-50-03).

---

### CR-03: Presigned URL contains internal MinIO hostname

**Files modified:**
- `backend/src/main/java/com/lexcv/config/MinioProperties.java`
- `backend/src/main/java/com/lexcv/config/MinioConfig.java`
- `backend/src/main/resources/application.yml`
- `backend/.env.example`

**Commit:** `442fa26`
**Applied fix:** Added optional `publicEndpoint` field to `MinioProperties` with a `getEffectivePublicEndpoint()` helper that falls back to `endpoint` when `publicEndpoint` is not set. Updated `MinioConfig.s3Presigner()` to use `getEffectivePublicEndpoint()` instead of `getEndpoint()`. Added `minio.public-endpoint: ${MINIO_PUBLIC_ENDPOINT:${MINIO_ENDPOINT}}` to `application.yml` and documented `MINIO_PUBLIC_ENDPOINT=` (optional, commented) in `.env.example`.

---

### WR-02: Zero/negative presignedUrlExpiry crashes at runtime

**Files modified:** `backend/src/main/java/com/lexcv/config/MinioProperties.java`
**Commit:** `442fa26` (same commit as CR-03 — same file)
**Applied fix:** Added `@Min(1)` validation constraint to `presignedUrlExpiry`. Bean validation now rejects startup if the env var is set to 0 or a negative value, producing a clear startup error rather than a runtime 500 on every download request.

---

### CR-04: False audit entry written on 503 delete failure

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `01c3963`
**Applied fix:** Moved `auditLogRepository.save(...)` to after `storageService.delete()` succeeds and before `documentoRepository.delete()`. The audit entry is now written only when the storage delete completes successfully, eliminating the phantom audit record on 503 responses. The FK invariant from T-34-03 is preserved because the `Documento` entity still exists in the DB at audit-save time.

---

### WR-01: Old object deleted before new upload in replaceId branch

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `85cabe6`
**Applied fix:** Captured the old key in `oldKey` before uploading. New object is uploaded first; `storageService.delete(oldKey)` is called only after the upload succeeds. If the upload throws `StorageUnavailableException`, the old MinIO object remains intact and the DB record is unmodified.

---

## Skipped Issues

### WR-03: MinIO credentials exposed via Spring Boot Actuator

**File:** `backend/src/main/java/com/lexcv/config/MinioProperties.java`
**Reason:** Explicitly excluded from scope per task instructions — Actuator security is a separate cross-cutting concern outside this phase.
**Original issue:** `accessKey` field may not be masked by Spring's default Actuator sanitization patterns, potentially exposing MinIO credentials via `/actuator/env` or `/actuator/configprops`.

---

_Fixed: 2026-06-19T17:30:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
