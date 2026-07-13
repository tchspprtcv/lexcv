---
phase: LEXCV-92-agenda-riscoprazoservice-consolida-o
fixed_at: 2026-07-13T23:45:43Z
review_path: .planning/phases/LEXCV-92-agenda-riscoprazoservice-consolida-o/92-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 8
skipped: 1
status: partial
---

# Phase LEXCV-92: Code Review Fix Report

**Fixed at:** 2026-07-13T23:45:43Z
**Source review:** .planning/phases/LEXCV-92-agenda-riscoprazoservice-consolida-o/92-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope (Critical + Warning; `fix_scope: critical_warning`): 9
- Fixed: 8
- Skipped: 1

All backend edits were verified with an offline Maven compile
(`mvn -q -o compile`, zero errors) after each change. Both frontend
edits were verified with a full-project `tsc --noEmit -p tsconfig.json`
run (zero new errors — the only pre-existing errors are unrelated
`vitest` module-resolution failures in `*.test.ts` files, present before
and after these changes). Every fix is committed individually on top of
the phase branch; see commit hashes below.

## Fixed Issues

### CR-02: `createEvento` does not verify `processoId` belongs to the caller's tenant

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `2823642`
**Applied fix:** Added a tenant-ownership check on `evento.getProcessoId()` in `createEvento`,
mirroring the existing pattern in `uploadDocumento`/`listHonorarios` — returns 400 if the
processo doesn't exist or belongs to another tenant. Applied first, ahead of the other findings,
given this is the tenant-isolation-boundary issue CLAUDE.md calls out as primary.

### CR-01: `updateEvento` never persists `processoId` changes

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `65da518`
**Applied fix:** Added `processoId` to the partial-update whitelist in `updateEvento`, with the
same tenant-ownership check as CR-02. Restores "set/change processo" via `PUT /eventos/{id}`.
Per the review's own note, this does not solve "unlink to null" under the current
`if (x != null)` partial-update convention — that limitation is now made explicit in the TS
contract by WR-05 below, rather than silently implied as supported.

### CR-04: Recurring events dropped from ranged `/eventos` queries before recurrence expansion

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `ab7a347`
**Status:** `fixed: requires human verification`
**Applied fix:** Moved the `start`/`end` date-window `removeIf` filters from the raw
(pre-expansion) master list to the `expanded` (post-expansion) instance list, exactly as
suggested in the review. The `processoId`/`concluido` pre-expansion filters were left in place
(they filter on immutable per-event attributes, not on a per-occurrence date, so pre-expansion
filtering is correct for them). Flagged for human verification because this reorders a loop's
filtering logic (an algorithmic change, not a pure add/guard) — recommend exercising a recurring
event whose `dataInicio` predates a queried window to confirm in-window instances now appear.

### WR-01: `createEvento` does not validate required domain fields

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `093f168`
**Applied fix:** Added explicit 400 responses when `titulo` is null/blank or `dataInicio` is
null, matching the review's suggested code exactly. `dataFim` was intentionally left
unenforced, matching the review's own example fix (only `titulo`/`dataInicio` were shown) —
the existing order-check (`dataFim` before `dataInicio`) already guards the one place a null
`dataFim` would matter.

### WR-02: Unguarded `.equals()` call on `getConcluido()` can NPE the whole tenant's eventos listing

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `7441dad`
**Applied fix:** Flipped the operand order to `concluido.equals(e.getConcluido())` (the
already-null-checked request param as receiver), so a legacy row with a null `concluido` is
excluded from the filtered result instead of throwing an NPE that would 500 the whole endpoint.

### WR-03: `recurrenceRule` loosely typed `string` in request DTOs vs. strict union in response type

**Files modified:** `web/src/types/eventos.ts`
**Commit:** `8217ac7`
**Applied fix:** Changed `EventoCreateRequest.recurrenceRule` and
`EventoUpdateRequest.recurrenceRule` from `string` to `'DAILY' | 'WEEKLY' | 'MONTHLY'`, matching
the response type and the backend's recognized literals.

### WR-05: `EventoUpdateRequest` advertises null-clearable fields the backend can never actually clear

**Files modified:** `web/src/types/eventos.ts`
**Commit:** `f29123f`
**Applied fix:** Narrowed `processoId`, `tipo`, and `descricao` from `X | null` to `X` in
`EventoUpdateRequest` (dropping the misleading `| null`), since `updateEvento`'s
`if (payload.getX() != null)` partial-update pattern (binding the raw entity as the payload)
cannot distinguish "field omitted" from "field explicitly nulled" — clearing was never actually
supported despite the wider type. Verified no call site in `web/src` constructs an update
payload with an explicit `null` for these three fields (the only file with a `processo_id: null`-
style check, `web/src/app/_api-backup/v1/eventos/[id]/route.ts`, is legacy mock code explicitly
excluded from the TS project via `tsconfig.json`'s `exclude`, and is not built against per
CLAUDE.md). Chose type-narrowing over a backend PATCH-DTO rewrite as the smaller, more
conservative fix; a proper `JsonNullable`-style backend DTO remains open for a future phase if
"clear to null" support is actually needed.

### WR-04: Optimistic drag-and-drop reschedule doesn't recompute `risco` for the moved event

**Files modified:** `web/src/app/(dashboard)/agenda/page.tsx`
**Commit:** `82ff850`
**Applied fix:** Set `risco: undefined` in the optimistic-override branch of the
`allUnifiedEvents` mapping, matching the review's suggested fix, rather than carrying over the
stale server-computed value via the object spread.

## Skipped Issues

### CR-03: Risco computed from `dataInicio` in the agenda list but from `dataFim` in the dashboard KPIs

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2479` (vs. `:3091`)
**Reason:** Skipped deliberately — this is a cross-cutting product/semantics decision, not a
mechanical bug, and the orchestrator's own phase context flagged it as a candidate for the
broader Phase 97 audit rather than an in-phase fix. Reasoning for deferring rather than
applying the review's suggested one-line change:

1. **Existing deliberate design rationale is tied to the current (`dataFim`) semantics.** The
   comment immediately above `isEventoCritico` (lines 3077-3089) explicitly documents an accepted
   corner case — "ALTA + dataFim nula não conta como crítico" — carried over from a prior phase's
   review (WR-01, 85-REVIEW.md). That comment was written with full knowledge of the `dataFim`
   basis and reasons about it explicitly. Swapping to `dataInicio` would silently invalidate that
   documented rationale (the corner case shifts to "ALTA + dataInicio nula", a materially
   different — and rarer — condition) without a fresh product/dev sign-off that the shifted
   corner case is still acceptable.
2. **High blast radius on two production KPIs.** `isEventoCritico` is the sole basis for both
   `prazos_vencer` (`/dashboard`) and `prazos_criticos_count` (`/processos/dashboard`). Changing
   its date basis changes real dashboard numbers tenant-wide the moment this deploys — this
   warrants the broader review Phase 97 is scoped to give it, not a fix applied incidentally while
   reviewing an unrelated agenda-consolidation phase.
3. **`AlertasDiariosJob` is not two-out-of-three by accident but is a separate call site with its
   own daily-notification semantics** — confirming `dataInicio` is more common today doesn't by
   itself establish it as the *intended* canonical field for the KPI corner case reasoned about in
   point 1; that's exactly the kind of judgment call Phase 97's audit is positioned to make with
   full context.

No code changes were made for this finding. Recommend carrying it forward verbatim into Phase
97's audit scope (it is already flagged there per the orchestrator's phase context).

---

_Fixed: 2026-07-13T23:45:43Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
