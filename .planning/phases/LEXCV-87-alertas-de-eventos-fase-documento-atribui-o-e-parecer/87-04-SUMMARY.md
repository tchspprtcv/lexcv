---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
plan: 04
subsystem: ui
tags: [notifications, next.js, react, tanstack-query, radix-ui, processos, deep-linking]

# Dependency graph
requires:
  - phase: 87-02 (same phase, plan 02)
    provides: "PUT /api/v1/processos/{id}/atribuir endpoint (backend) + FASE_ENTRADA notification's ?tab=fases linkUrl (backend copy, previously inert on the frontend)"
provides:
  - "useReatribuirResponsavel mutation hook (use-processos.ts) — calls PUT /processos/{id}/atribuir, invalidates list+detail+workflow query keys"
  - "ReatribuirResponsavelControl — Dialog (select) -> AlertDialog (confirm) two-step reassignment flow wired into the Responsavel dd of the ficha do processo's Workflow card, gated by processos:manage"
  - "?tab= query-param-driven initial tab state on /processos/[id] — makes the FASE_ENTRADA notification's ?tab=fases deep-link functional"
affects: [87-VERIFICATION, v2.10-MILESTONE-AUDIT, phase-89 (bell/notification list will render this same linkUrl)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Two-step Dialog->AlertDialog confirm flow for a sensitive-but-reversible action, mirroring EntregarParecerDialog's state shape (open/selection/error local state + try/catch mutateAsync + dual error channel) without copying its broken bg-destructive styling"
    - "useSearchParams()-initialized React.useState (read once at mount, no useEffect sync) for tab deep-linking, requiring a <Suspense> boundary around the Client Component that calls it (Next.js 16 requirement, precedent: web/src/app/(auth)/login/page.tsx)"

key-files:
  created: []
  modified:
    - web/src/hooks/use-processos.ts
    - web/src/app/(dashboard)/processos/[id]/page.tsx

key-decisions:
  - "ReatribuirResponsavelControl added as a sibling function component in the same file (after ProcessoDetailContent, before ProcessoDocumentosTab), not a new file in components/shared — matches 87-PATTERNS.md convention and the file's existing helper-component placement"
  - "AlertDialogAction confirm button styled bg-blue-600 hover:bg-blue-700 text-white (accent), never bg-destructive — UI-SPEC.md flagged bg-destructive as a broken, unstyled class in this codebase (no --destructive token defined) and classified reassignment as sensitive-but-reversible, not destructive"
  - "tab's initial value is derived once from searchParams.get('tab') as the useState initializer (mount-only read, validated against a TAB_KEYS allow-list, fallback 'timeline') — no useEffect/bidirectional URL sync added, since only the initial-load deep-link needed to work"

patterns-established: []

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-07-09
---

# Phase 87 Plan 04: Reatribuir Responsável Control + ?tab= Deep-Link Summary

**Two-step Dialog->AlertDialog "Reatribuir" control wired into the ficha do processo's Workflow card, plus `useSearchParams()`-driven tab initialization that turns the FASE_ENTRADA notification's `?tab=fases` link from inert backend copy into a real navigation.**

## Performance

- **Duration:** ~20 min (Tasks 1-3; includes a one-time `pnpm install` for this worktree, which had no `node_modules`)
- **Started:** 2026-07-09 (session start, after worktree base sync)
- **Completed (Tasks 1-3):** 2026-07-09T07:59:46Z
- **Tasks:** 3 of 4 completed (Task 4 is a `checkpoint:human-verify` — returned to orchestrator, not executed by this agent)
- **Files modified:** 2

## Accomplishments
- `useReatribuirResponsavel(processoId)` added to `use-processos.ts`: calls `PUT /processos/{id}/atribuir` with `{ responsavelId }`, and on success invalidates `["processos","list"]`, sets `["processos","detail",processoId]` from the response, and — critically — invalidates `["processos","workflow",processoId]`, since `useWorkflow` is the actual source of the rendered Responsável name (UI-SPEC.md flagged omitting this key as the single most likely integration bug for this control).
- `ReatribuirResponsavelControl` added to `processos/[id]/page.tsx` and wired into the existing Responsável `dd` (Workflow card), gated by `canManageProcessos` (not `canEditProcessos` — CONTEXT.md/UI-SPEC.md lock this to the stricter `processos:manage` scope). Flow: Dialog (native `<select>` sourced from `useAdminUsers()`, submit disabled on empty/no-op selection) -> closes Dialog, opens AlertDialog naming the processo número and new responsável -> only "Confirmar Reatribuição" calls `reatribuir.mutateAsync`. Success: `toast.success("Responsável reatribuído com sucesso.")` + close. Error: inline `text-sm text-red-600` line inside the still-open AlertDialog + `toast.error(...)`, confirm button re-enabled.
- `/processos/[id]` now reads `?tab=` on first render: a module-level `TAB_KEYS` allow-list validates the query param before it's allowed to seed `tab`'s initial `useState` value (anything absent/invalid falls back to `"timeline"`). `ProcessoDetailPage`'s render of `ProcessoDetailContent` is wrapped in `<Suspense>` (required by Next.js 16 whenever a Client Component calls `useSearchParams()`; pattern copied from `web/src/app/(auth)/login/page.tsx`). This makes the `?tab=fases` `linkUrl` that `notificarFaseEntrada` (Plan 87-02) already generates a real, working deep-link.

## Task Commits

Each task was committed atomically:

1. **Task 1: Hook useReatribuirResponsavel em use-processos.ts** - `049c246` (feat)
2. **Task 2: Componente ReatribuirResponsavelControl + wiring no dd Responsável** - `22e15f4` (feat)
3. **Task 3: Inicializar a aba (tab) a partir do query param ?tab=** - `d630167` (fix)

**Task 4 (checkpoint:human-verify):** not executed — returned to orchestrator per parallel-executor instructions (requires a live backend + frontend to test end-to-end; this agent does not start dev servers).

**Plan metadata:** pending — SUMMARY.md commit follows this document (STATE.md/ROADMAP.md updates are the orchestrator's responsibility per this run's instructions, not committed by this agent).

## Files Created/Modified
- `web/src/hooks/use-processos.ts` — added `useReatribuirResponsavel(processoId)` mutation hook (PUT `/processos/{id}/atribuir`, list+detail+workflow invalidation).
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — added `AlertDialog*` imports and `useReatribuirResponsavel` to the existing `use-processos` import; added `ReatribuirResponsavelControl` component and wired it into the Responsável `dd`; added `useSearchParams` (next/navigation) + `Suspense` (react) imports, `TAB_KEYS` allow-list constant, `?tab=`-derived `initialTab`, and a `<Suspense>` boundary around `<ProcessoDetailContent>`.

## Decisions Made
- `ReatribuirResponsavelControl` placed as an inline sibling function (not a new `components/shared` file) — matches 87-PATTERNS.md's explicit convention confirmation for this phase.
- Confirm button uses `bg-blue-600 hover:bg-blue-700 text-white`, never `bg-destructive` (verified absent via grep) — reassignment is sensitive-but-reversible per UI-SPEC.md's Copywriting Contract, and `bg-destructive` resolves to nothing in this codebase (no `--destructive` token defined anywhere in `globals.css`).
- `tab`'s initial value is read from the URL exactly once (as the `useState` initializer) with no `useEffect` synchronization — deliberately out of scope per the plan; only the notification-link's first-load behavior needed fixing, not full bidirectional tab<->URL sync.

## Deviations from Plan

**None — plan executed exactly as written for Tasks 1-3.** No Rule 1/2/3 auto-fixes were needed; the codebase already had every prerequisite the plan's `<interfaces>` section named (`useAdminUsers`, `useWorkflow`, the Prazo-picker `<select>` className, the `EntregarParecerDialog` state-machine shape, the `login/page.tsx` `Suspense`/`useSearchParams` precedent), and the backend `PUT /processos/{id}/atribuir` endpoint from Plan 87-02 was already present and matched the plan's documented contract exactly.

One environment-only setup step, not a code deviation: this worktree had no `web/node_modules` and no `web/.env.local`. Ran `pnpm install` (worktrees don't share `node_modules`, which is gitignored) and created `web/.env.local` from `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`) so `pnpm --dir web build` could run — both are gitignored and were required only to execute the plan's own `<verify>` commands, not part of the shipped diff.

## Issues Encountered

**Pre-existing lint errors/warnings, unrelated to this plan's diff (out of scope, logged not fixed).** `pnpm --dir web lint` reports 22 problems (5 errors, 17 warnings) both before and after all three tasks' edits — identical count, confirmed via `grep` that neither modified file (`use-processos.ts`, `processos/[id]/page.tsx`) contributes a new entry beyond the single pre-existing `'textareaClassName' is assigned a value but never used` warning already present in `processos/[id]/page.tsx` before this plan touched it. The 5 errors live in unrelated files (`dashboard-shell.tsx` setState-in-effect, `documentos/novo/page.tsx` ref-during-render). Per the deviation rules' Scope Boundary, these are out of scope for this plan and were not touched; logged to `deferred-items.md`.

## User Setup Required

None — no external service configuration required. (The `web/.env.local` created for local build verification is gitignored and worktree-local; it mirrors `web/.env.example` and is not part of the deployed configuration.)

## Next Phase Readiness

- Tasks 1-3 are complete, committed, and pass `pnpm --dir web build` (TypeScript + static generation across all 23 routes). `pnpm --dir web lint` shows zero new issues.
- **Task 4 (checkpoint:human-verify) is outstanding** — requires a live backend (`mvn -f backend/pom.xml spring-boot:run` + PostgreSQL) and frontend (`pnpm --dir web dev`) to walk through: the Reatribuir button's visibility gating, the Dialog->AlertDialog flow, the immediate Responsável name update (workflow-key invalidation proof), the dual error channel on a forced backend failure, the `PROCESSO_ATRIBUIDO` notification row for the new responsável + ADMIN, and the `/processos/{id}?tab=fases` vs `/processos/{id}` deep-link behavior. This agent does not start dev servers — the orchestrator's own browser preview tooling owns this verification per this run's instructions.
- Once Task 4 is confirmed ("approved"), Phase 87's only frontend-visible surface (per 87-UI-SPEC.md's explicit scope note) is complete, and NOTF-15's locked decision (notification link must land on the Fases tab, not a general overview) and NOTF-17 (reassignment flow) are both fully closed end-to-end.

## Self-Check: PASSED

- FOUND: `web/src/hooks/use-processos.ts`
- FOUND: `web/src/app/(dashboard)/processos/[id]/page.tsx`
- FOUND commit: `049c246` (feat, Task 1)
- FOUND commit: `22e15f4` (feat, Task 2)
- FOUND commit: `d630167` (fix, Task 3)

---
*Phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer*
*Completed: 2026-07-09 (Tasks 1-3; Task 4 pending human verification)*
