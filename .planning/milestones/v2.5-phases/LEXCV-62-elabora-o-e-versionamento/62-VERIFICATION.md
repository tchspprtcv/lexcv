---
phase: 62-elabora-o-e-versionamento
verified: 2026-06-30T23:00:00Z
status: passed
score: 8/8 must-haves verified
overrides_applied: 0
---

# Phase 62: Elaboração e Versionamento Verification Report

**Phase Goal:** O advogado responsável consegue elaborar o parecer em versões sucessivas, cada uma com conteúdo, anexo opcional e histórico rastreável
**Verified:** 2026-06-30T23:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A ParecerVersao entity exists and maps to table t_parecer_versao | ✓ VERIFIED | `backend/src/main/java/com/lexcv/models/ParecerVersao.java:8-10` — `@Entity @Table(name = "t_parecer_versao", uniqueConstraints = @UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"}))` |
| 2 | Each version carries solicitacaoId, numeroVersao, conteudo (nullable), caminhoAnexo (nullable), criadoPorId and createdAt | ✓ VERIFIED | `ParecerVersao.java:21-40` — all seven columns present with correct names/types; `conteudo` has `columnDefinition = "TEXT"`, no `nullable=false` on conteudo/caminhoAnexo; `@PrePersist onCreate()` sets `createdAt`; no `updatedAt` field (immutability confirmed) |
| 3 | The repository can list versions of a solicitação and compute the next sequential version number | ✓ VERIFIED | `ParecerVersaoRepository.java:12,14-15` — `findBySolicitacaoId(UUID)` and `@Query("SELECT MAX(v.numeroVersao) FROM ParecerVersao v WHERE v.solicitacaoId = :solicitacaoId") findMaxNumeroVersaoBySolicitacaoId` |
| 4 | The assigned advogado (or ADMIN) can create a new version with optional text and optional file attachment | ✓ VERIFIED | `ParecerController.java:260-301` — `createVersao` checks `isAdmin || isResponsavel` (403 otherwise, line 278-281), requires conteudo or file (400 otherwise, line 283-286), uploads via `storageService.upload` only if file present |
| 5 | Creating a version auto-assigns the next sequential numeroVersao, records criadoPorId and createdAt | ✓ VERIFIED | `ParecerController.java:304-318` — `synchronized (ParecerVersaoRepository.class)` block computes `next = findMaxNumeroVersaoBySolicitacaoId(...).orElse(0)+1`, sets `criadoPorId(principal.getUserId())`; `createdAt` set server-side via entity `@PrePersist`; no client-bound numeroVersao/criadoPorId param exists in the `@RequestParam` list (only `conteudo`, `file`) |
| 6 | A user with pareceres:view can list all versions of a solicitação and fetch one version's detail | ✓ VERIFIED | `ParecerController.java:234-258` — `listVersoes` and `getVersao`, both `@PreAuthorize("hasAuthority('pareceres:view')")`, tenant-checked against parent solicitação, 404 on cross-tenant/mismatch |
| 7 | A version attachment can be downloaded via a presigned StorageService URL | ✓ VERIFIED | `ParecerController.java:333-354` — `downloadAnexo` calls `storageService.presignedDownloadUrl(versao.getCaminhoAnexo())`, returns `{url, expiresIn:3600}`, 404 if no anexo, 503 on `StorageUnavailableException` |
| 8 | All version endpoints are tenant-isolated through the parent solicitação check | ✓ VERIFIED | All four endpoints (lines 236-238, 248-251, 267-270, 336-339) load `ParecerSolicitacao` by id and reject with 404 "Solicitação não encontrada" when null or `tenantId` mismatch, before touching version rows |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/ParecerVersao.java` | Immutable JPA entity for a parecer version | ✓ VERIFIED | 47 lines, all required columns, `@PrePersist`, no `updatedAt`, plus a unique constraint added post-review (WR-01 fix) |
| `backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java` | Spring Data repository with list + max-version query | ✓ VERIFIED | 17 lines, both required methods present with exact signatures |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | Nested /versoes CRUD-append endpoints with multipart upload + presigned download | ✓ VERIFIED | 356 lines total; 4 new endpoints (list, detail, create, download) added without modifying existing endpoints |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| ParecerController.java | ParecerVersaoRepository.java | injected repository + findMaxNumeroVersaoBySolicitacaoId | ✓ WIRED | `private final ParecerVersaoRepository parecerVersaoRepository` (line 40); called at lines 242, 253, 305, 342 |
| ParecerController.java | StorageService.java | upload / presignedDownloadUrl | ✓ WIRED | `private final StorageService storageService` (line 41); `storageService.upload(...)` line 292, `storageService.presignedDownloadUrl(...)` line 348, `storageService.delete(...)` line 322 (post-review WR-02 fix, method confirmed to exist at `StorageService.java:84`) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles | `cd backend && mvn -DskipTests compile` | BUILD SUCCESS | ✓ PASS |
| No debt markers in phase files | grep TODO/FIXME/XXX/HACK/PLACEHOLDER across 3 phase files | no matches | ✓ PASS |
| StorageService.delete exists (used in WR-02 cleanup path) | grep "delete" in StorageService.java | `public void delete(String objectKey)` at line 84 | ✓ PASS |

No live server/DB/MinIO smoke test performed (consistent with SUMMARY.md — optional per plan, requires running stack). Compile/build verification is sufficient evidence given full static wiring confirmation above.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PARV-01 | 62-02 | Advogado responsável pode criar nova versão do parecer (conteúdo + anexo opcional) | ✓ SATISFIED | `createVersao` endpoint, isResponsavel/isAdmin gate, optional conteudo/file |
| PARV-02 | 62-01, 62-02 | Cada versão regista número sequencial, autor e data de criação | ✓ SATISFIED | `numeroVersao` (MAX+1 synchronized), `criadoPorId`, `createdAt` @PrePersist |
| PARV-03 | 62-02 | Utilizador pode consultar e comparar versões anteriores do mesmo parecer | ✓ SATISFIED | `listVersoes` + `getVersao` endpoints, `pareceres:view` gated |
| PARV-04 | 62-02 | Anexo de versão reutiliza StorageService (mesmo padrão de Documentos) | ✓ SATISFIED | `storageService.upload`/`presignedDownloadUrl`/`delete` reused, no duplicate storage path |

No orphaned requirements — all four IDs mapped to this phase in REQUIREMENTS.md are claimed across the two plans.

### Anti-Patterns Found

None. No TODO/FIXME/XXX/HACK/PLACEHOLDER markers in any phase-modified file. No stub return values, no empty handlers, no hardcoded empty arrays masking real queries.

### Code Review Cross-Check

`62-REVIEW.md` (re-review after fixes): status `clean`, 0 critical, 0 warning, 2 info (both explicitly deferred, not regressions). Verified the three fixes claimed in the review are present in code:
- CR-01 (TOCTOU race): `synchronized` block wraps both the MAX query and the save — confirmed lines 304-318.
- WR-01 (no DB unique constraint): `@UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"})` — confirmed `ParecerVersao.java:10`.
- WR-02 (orphaned upload cleanup): `catch (RuntimeException e)` with `storageService.delete(caminhoAnexo)` and `log.warn` — confirmed lines 317-328.

### Human Verification Required

None. All truths are verifiable via static code inspection and successful build; no UI/visual/real-time behavior is part of this backend-only phase.

### Gaps Summary

No gaps. All 8 derived truths verified against actual code (not SUMMARY.md claims), both required artifacts exist and are substantive, both key links are wired with correct method calls, the build succeeds, and the prior code review's claimed fixes are confirmed present in the source. Requirements PARV-01 through PARV-04 are all satisfied with direct evidence.

---

_Verified: 2026-06-30T23:00:00Z_
_Verifier: Claude (gsd-verifier)_
