---
phase: 66-cria-o-de-solicita-o
fixed_at: 2026-07-01T12:30:00Z
review_path: .planning/phases/LEXCV-66-cria-o-de-solicita-o/66-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 66: Code Review Fix Report

**Fixed at:** 2026-07-01T12:30:00Z
**Source review:** .planning/phases/LEXCV-66-cria-o-de-solicita-o/66-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (Critical + Warning; Info out of scope for this run)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: Stale `processoId` survives a cliente change

**Files modified:** `web/src/app/(dashboard)/pareceres/nova/page.tsx`
**Commit:** effa243
**Applied fix:** Added a `React.useEffect` that watches `clienteIdValue` and resets the `processoId` field (`form.setValue("processoId", undefined)`) whenever the selected cliente changes, placed immediately after the `clienteIdValue`/`processos`/`adminUsers` declarations. This prevents a processo belonging to a previously-selected cliente from being silently submitted alongside a newly-selected, unrelated cliente.

### WR-02: Backend has no server-side check that `processoId` belongs to the same `clienteId`

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 1823944
**Applied fix:** Added a new private helper `processoBelongsToCliente(UUID processoId, UUID clienteId)` that loads the `Processo` and compares its `clienteId` field against the submitted `clienteId` (confirmed exact field name via `Processo.java:25`). Wired this check into:
- `createSolicitacao`: after the existing tenant-ownership check, added `if (body.getProcessoId() != null && !processoBelongsToCliente(body.getProcessoId(), body.getClienteId()))` returning 400 with message `"processoId não pertence ao cliente indicado"`.
- `updateSolicitacao`: same check, but using an `effectiveClienteId` that falls back to the existing `solicitacao.getClienteId()` when the payload does not also change `clienteId` in the same request (payload's `clienteId` may be null on updates that only touch other fields), so the cross-check remains correct in both "changing clienteId and processoId together" and "changing only processoId" cases.

This closes the gap flagged in the review — a request with `{clienteId: A, processoId: <processo of cliente C>}` (whether via UI bypass or the WR-01 stale-state case) is now rejected server-side with a 400, independent of the frontend fix.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-01T12:30:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
