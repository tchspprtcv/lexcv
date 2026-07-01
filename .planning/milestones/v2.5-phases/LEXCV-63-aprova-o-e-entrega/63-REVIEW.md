---
phase: 63-aprova-o-e-entrega
reviewed: 2026-06-30T23:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: clean
---

# Phase 63: Code Review Report (Re-review)

**Reviewed:** 2026-06-30T23:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** clean

## Summary

Re-reviewed `ParecerController.java` after fixes for CR-01 and WR-01 (commits 5478c56,
abd0f57). Both prior findings are confirmed resolved.

**CR-01 (resolved):** `entregarSolicitacao` (line 270) now uses
`@PreAuthorize("hasAuthority('pareceres:edit')")` instead of `pareceres:manage`. Since
`pareceres:edit` is seeded for the ADVOGADO role (unlike `pareceres:manage`, which is
ADMIN-only), the assigned advogado-responsável now actually reaches the method body. The
in-method ownership check (lines 291-297) is no longer dead code:
- An ADMIN caller: `isAdmin` true → passes regardless of `advogadoId`.
- The assigned responsável (`solicitacao.getAdvogadoId().equals(principal.getUserId())`):
  `isResponsavel` true → passes.
- A *different* advogado who holds `pareceres:edit` but is not the assigned responsável:
  clears `@PreAuthorize`, reaches the body, but both `isAdmin` and `isResponsavel` are
  false → correctly rejected with 403 ("Apenas o advogado responsável ou ADMIN pode
  entregar o parecer").
- A caller without `pareceres:edit` at all (e.g. TECNICO/ASSISTENTE without that scope):
  rejected by the framework-level `@PreAuthorize` before the method body runs, as before.

This matches the precedent already used in `createVersao` (line 331) and correctly
implements PARC-08 ("advogado responsável ou ADMIN pode entregar").

**WR-01 (resolved):** Both `aprovarVersao` (lines 244-247) and `entregarSolicitacao`
(lines 279-282) now run the `"CONCLUIDO".equals(solicitacao.getStatus())` irreversibility
guard immediately after the tenant-scoped solicitação lookup, before the version lookup.
Guard ordering is consistent across both endpoints and matches the documented intent
(concluded solicitações are rejected before any version-specific lookup or mutation is
attempted).

No new issues were introduced by the fix. Tenant isolation, version-belongs-to-solicitação
ownership checks, and the rest of the controller are unchanged from the prior review and
remain sound.

## Info

### IN-01: `aprovarVersao` remains ADMIN-only by design via `pareceres:manage`

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:235-237`
**Issue:** Unchanged from the prior review — `aprovarVersao` has no in-method
isAdmin/isResponsavel branch and relies solely on
`@PreAuthorize("hasAuthority('pareceres:manage')")`, which per seed data is granted only
to ADMIN. This correctly matches PARC-07 ("apenas ADMIN pode aprovar") and is intentionally
more restrictive than `entregarSolicitacao`. Re-listed for traceability only; not re-flagged
as an issue.
**Fix:** No action needed.

---

_Reviewed: 2026-06-30T23:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
