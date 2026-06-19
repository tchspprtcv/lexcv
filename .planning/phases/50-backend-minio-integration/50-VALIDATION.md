---
phase: 50
slug: backend-minio-integration
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-19
---

# Phase 50 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Spring Boot Test (JUnit 5 + MockMvc) — `spring-boot-starter-test` already in pom.xml |
| **Config file** | `backend/src/test/resources/application-test.yml` (Wave 0 gap — create) |
| **Quick run command** | `mvn test -Dtest=StorageServiceTest -pl backend` |
| **Full suite command** | `mvn test -pl backend` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=StorageServiceTest -pl backend`
- **After every plan wave:** Run `mvn test -pl backend`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------------|-----------|-------------------|-------------|--------|
| 50-01-01 | 50-01 | 1 | MIN-01, MIN-04 | Object key prefixed with tenantId; no filesystem write | unit | `mvn test -Dtest=StorageServiceTest -pl backend` | ❌ W0 | ⬜ pending |
| 50-01-02 | 50-01 | 1 | MIN-02, MIN-03 | Presigned URL generated with correct expiry; delete removes object | unit | `mvn test -Dtest=StorageServiceTest -pl backend` | ❌ W0 | ⬜ pending |
| 50-02-01 | 50-02 | 2 | MIN-01, MIN-02 | Upload stores key in caminhoArquivo; download returns JSON {url, expiresIn} | unit | `mvn test -Dtest=ResourceControllerTest -pl backend` | ❌ W0 | ⬜ pending |
| 50-02-02 | 50-02 | 2 | MIN-03 | Delete calls storageService.delete(); 503 on StorageUnavailableException | unit | `mvn test -Dtest=ResourceControllerTest -pl backend` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/lexcv/services/StorageServiceTest.java` — covers MIN-01, MIN-03, MIN-04, startup bucket check (mock S3Client + S3Presigner)
- [ ] `backend/src/test/java/com/lexcv/controllers/ResourceControllerTest.java` — covers MIN-02 presigned URL response, audit log preserved, 503 on StorageUnavailableException
- [ ] `backend/src/test/resources/application-test.yml` — test profile disabling real MinIO connection

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Object visible in MinIO bucket after upload | MIN-01 | Requires running MinIO instance | Upload a document via API, open MinIO console at configured endpoint, verify object exists under {tenantId}/{documentoId}/{filename} |
| Presigned URL opens file in browser | MIN-02 | Browser navigation required | GET /documentos/{id}/download, copy url field, open in browser — file should download directly |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
