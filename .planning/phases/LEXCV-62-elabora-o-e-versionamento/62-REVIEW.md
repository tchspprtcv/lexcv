---
phase: 62-elabora-o-e-versionamento
reviewed: 2026-06-30T21:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/ParecerVersao.java
  - backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
findings:
  critical: 0
  warning: 0
  info: 2
  total: 2
status: clean
---

# Phase 62: Code Review Report (Re-review after fixes)

**Reviewed:** 2026-06-30T21:00:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** clean

## Summary

Re-reviewed `ParecerController.java` and `ParecerVersao.java` after commits fd61a2a and 02b46d3, which addressed CR-01, WR-01, and WR-02 from the prior review (62-REVIEW.md, 2026-06-30T20:00:00Z). All three findings are confirmed resolved with no regressions introduced.

**CR-01 (TOCTOU race on numeroVersao) — RESOLVED.** The `synchronized (ParecerVersaoRepository.class)` block (`ParecerController.java:304-329`) now wraps both the `findMaxNumeroVersaoBySolicitacaoId` read and the `parecerVersaoRepository.save(versao)` write, closing the check-then-act window. Verified the lock scope does not regress: the `storageService.upload()` call (lines 290-301) happens entirely *before* the synchronized block is entered, so the lock is never held across blocking network I/O to MinIO — no new lock-contention or availability risk was introduced while fixing the race. The single-JVM caveat noted in the original finding still applies in theory (horizontal scaling), but is now mitigated by the DB-level constraint added for WR-01.

**WR-01 (no DB unique constraint) — RESOLVED.** `ParecerVersao.java:9-10` now declares `@Table(name = "t_parecer_versao", uniqueConstraints = @UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"}))`, matching the codebase's existing `Cliente` pattern. This provides a correctness backstop independent of process topology, exactly as recommended.

**WR-02 (orphaned upload on save failure) — RESOLVED.** `createVersao` now catches `RuntimeException` around `save()` (lines 317-328) and performs a best-effort `storageService.delete(caminhoAnexo)` cleanup, logging (not swallowing) any cleanup failure via `log.warn`, then rethrows the original exception so the caller still sees a proper error response. This also means a `DataIntegrityViolationException` thrown by the new unique constraint (in the theoretical multi-instance race case) is itself routed through this same cleanup path rather than leaking an orphaned object — a desirable side effect of fixing WR-02 after WR-01.

No new Critical or Warning issues were found during this re-review. IN-01 and IN-02 from the prior review remain open by design (both were explicitly deferred as out-of-scope hardening items, not regressions); they are re-listed below for traceability, not re-flagged as new findings.

## Critical Issues

None.

## Warnings

None.

## Info

### IN-01: validateAdvogado's account-active gap also applies to the createVersao ownership check (carried over, unchanged)

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:276-277`
**Issue:** `isResponsavel` is computed purely from `solicitacao.getAdvogadoId().equals(principal.getUserId())`, with no check that the advogado's account is still active (`User.ativo`). This mirrors IN-01 from the Phase 61 review, explicitly accepted as out of scope at the time. A deactivated advogado who still holds a valid session/JWT could continue creating versions for solicitações assigned to them.
**Fix:** Same as Phase 61 IN-01 — add an active-account check at the principal/session level (e.g., in `JwtAuthenticationFilter` or via a `UserPrincipal.isEnabled()`-style check) in a dedicated security-hardening phase, rather than per-endpoint. No action required for this phase.

### IN-02: caminhoAnexo / versaoId not validated for max filename length before forming the object key (carried over, unchanged)

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:288-293`, `backend/src/main/java/com/lexcv/services/StorageService.java:39-42`
**Issue:** `file.getOriginalFilename()` is passed through unchanged (after path-traversal sanitisation in `StorageService.upload`) with no length/charset validation. An extremely long or malformed filename could exceed S3/MinIO key length limits, causing an unhandled `SdkException` subtype to bubble as an uncaught 500. Pre-existing `StorageService` characteristic, not introduced by this phase.
**Fix:** Consider filename length/charset validation in `StorageService.upload` (shared by all callers), out of scope for this phase's controller-level fix.

---

_Reviewed: 2026-06-30T21:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
