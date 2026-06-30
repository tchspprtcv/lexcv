---
phase: 61-data-layer-backend-crud
reviewed: 2026-06-30T19:10:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java
  - backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - web/src/lib/permissions.ts
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
status: clean
---

# Phase 61: Code Review Report (Final re-review after fix iteration 2)

**Reviewed:** 2026-06-30T19:10:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** clean

## Summary

Final re-review after fix commit `5cab55a`, which addressed WR-04 (the last open Warning from the prior review iteration). Verified the fix directly against `ParecerController.java` rather than trusting the fix report.

`updateSolicitacao` (lines 168-175) now correctly separates unconditional field assignment from guarded field assignment:

```java
solicitacao.setPrazo(payload.getPrazo());
solicitacao.setPrioridade(payload.getPrioridade());
if (payload.getClienteId() != null) {
    solicitacao.setClienteId(payload.getClienteId());
}
if (payload.getProcessoId() != null) {
    solicitacao.setProcessoId(payload.getProcessoId());
}
```

This matches the prescribed fix exactly. The previously-flagged behavior — a partial PUT omitting `clienteId` throwing an unhandled 500 via `DataIntegrityViolationException`, or omitting `processoId` silently clearing a valid process link — is eliminated. The null-skip semantics now match the validation guards at lines 159/163 (which already skipped tenant checks on `null`), so validation and assignment are consistent: a field is either fully ignored when absent, or validated-then-applied when present.

No regression was introduced by this change:
- The fix is scoped to exactly the two lines previously flagged; no other logic in `updateSolicitacao` (or elsewhere in the file) was touched.
- `prazo` and `prioridade` remain intentionally unconditional (these are not validated/guarded fields and were never part of WR-04's scope — nulling them out via PUT is the existing, accepted "clear this optional field" contract for this endpoint, unlike `clienteId`/`processoId` which carry referential-integrity and tenant-isolation concerns).
- `status` and `advogadoId` remain correctly excluded from the PUT body (state-machine fields, mutated only via `/atribuir`), unaffected by this change.
- All five Critical/Warning findings from the original review (CR-01, CR-02, WR-01, WR-02, WR-03) and WR-04 from the first re-review are now verified resolved in code.

All remaining items are pre-existing Info-level observations, explicitly out of scope for this fix iteration and not blocking. No new issues were found while re-reading the diff or its surrounding context.

**Overall phase status: CLEAN.** No Critical or Warning findings remain open. Phase 61 is approved to ship from a code-review standpoint.

## Verification of WR-04

### WR-04: updateSolicitacao unconditionally overwrites clienteId/processoId — RESOLVED

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:170-175`
**Verification:** The fix wraps both assignments in null guards (`if (payload.getClienteId() != null) { ... }` / `if (payload.getProcessoId() != null) { ... }`), exactly as prescribed. A partial PUT that omits `clienteId` no longer nulls out the required field (avoiding the unhandled `DataIntegrityViolationException` / 500), and a partial PUT that omits `processoId` no longer silently clears an existing process link. Confirmed correct by direct code inspection, not just fix-report claim.

## Info

### IN-01: validateAdvogado does not check User.ativo (account active flag) — still open

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:42-53`
**Issue:** `validateAdvogado` checks tenant + ADVOGADO role but not whether the account is active. Intentionally excluded from all fix iterations to date.
**Fix:** Add `Boolean.TRUE.equals(user.getAtivo())` to the validation predicate.

### IN-02: List endpoint advogadoId filter cannot express "unassigned only" — still open

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:134`
**Issue:** `advogadoId == null` means "no filter," not "filter for null." Intentionally excluded from all fix iterations to date.
**Fix:** Consider a documented sentinel or separate `semAdvogado=true` param in a later phase.

### IN-03: prioridade has no input validation against the documented ALTA|MEDIA|BAIXA value set

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:105-107, 169`
**Issue:** `prioridade` is copied through on both create and update with no validation against the comment-documented set `ALTA | MEDIA | BAIXA` (model line 36). This mirrors an established codebase-wide pattern (free-string `prioridade`/`status` fields, e.g. `Evento.prioridade`, `Prazo.prioridade`) and is informational only.
**Fix:** Optional future hardening — validate against an explicit allowed-value set or migrate to a Java enum across all `prioridade` fields in a dedicated cleanup phase, not specific to this controller.

---

_Reviewed: 2026-06-30T19:10:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
