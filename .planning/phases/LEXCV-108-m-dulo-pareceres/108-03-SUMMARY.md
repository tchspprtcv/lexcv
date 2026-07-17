---
phase: 108-m-dulo-pareceres
plan: 03
subsystem: ui
tags: [react, nextjs, radix-ui, accordion, tooltip, native-select, rbac, pareceres]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: Accordion/Tooltip/NativeSelect primitives (shadcn `-b radix` init)
  - phase: 102-design-system-reconciliation
    provides: global TooltipProvider (delayDuration=700) mounted in providers.tsx
provides:
  - Pareceres detail page's EntregarParecerDialog versaoFinalId control migrated from native <select> to NativeSelect
  - 4 of 7 bundled permissions.isLoading -> permissions.isFetched RBAC fixes on the Pareceres detail page (view gate, showNovaVersaoForm, showEntregarTrigger, loading-skeleton guard)
  - Project's first Accordion consumer, composed with Tooltip on non-focusable markers, for the "Histórico de Versões" timeline
affects: [108-04 (Wave-2 holistic gate/build/lint), pareceres]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tooltip on a non-focusable trigger element requires manually adding tabIndex={0} + aria-label on the child inside TooltipTrigger asChild (first instance in the project; every other consumer wraps an already-focusable Button)"
    - "Accordion default-open item derived from a single shared computation (defaultOpenVersaoId) also reused by the Tooltip label function (versaoTooltipLabel), avoiding two independent 'is this the current/delivered version' checks drifting apart"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/pareceres/[id]/page.tsx"

key-decisions:
  - "Accordion type=\"single\" collapsible (not type=\"multiple\") chosen for the timeline root — plan explicitly left this to executor discretion, single/collapsible is the simpler form given exactly one item is ever open by default"
  - "isResponsavelOuAdmin (instance-level advogado-responsável/ADMIN RBAC) left untouched per explicit plan instruction — only the 4 permissions.isLoading race-condition sites were fixed"

patterns-established:
  - "First Accordion + Tooltip-on-non-focusable-marker composition in web/src — future timeline-style UIs can copy this shared-derivation approach (sortedVersoes/defaultOpenVersaoId/versaoTooltipLabel)"

requirements-completed: [PARC-18, PARC-19, PARC-20]

# Metrics
duration: ~25min
completed: 2026-07-17
---

# Phase 108 Plan 03: Pareceres Detail Page — NativeSelect Dialog + Tooltip/Accordion Timeline Summary

**Migrated the Pareceres detail page's EntregarParecerDialog to NativeSelect, closed 4 client-side RBAC render-race sites, and rebuilt the "Histórico de Versões" timeline as the project's first Accordion consumer paired with Tooltip on every (previously non-focusable) version marker.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-17
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- `EntregarParecerDialog`'s `versaoFinalId` control is now a controlled `NativeSelect` (`className="w-full"`, no `rounded-none`/height change per UI-SPEC finding #4), replacing the raw `<select>` with its long inline className.
- Closed the pre-resolve render race at 4 `permissions.isLoading` sites (view gate, `showNovaVersaoForm`, `showEntregarTrigger`, loading-skeleton guard) by switching to `permissions.isFetched`, correctly inverting polarity on the skeleton guard (`{!permissions.isFetched ? (`). `isResponsavelOuAdmin` instance-level RBAC logic left untouched as instructed.
- Rebuilt the "Histórico de Versões" timeline as a single `Accordion` (`type="single" collapsible`) + `Tooltip` composition: exactly one item open by default (most-recent version, or the delivered version when `CONCLUIDO`), every marker `<span>` keyboard-focusable (`tabIndex={0}`) with an `aria-label` matching its tooltip text ("Versão atual" / "Versão entregue" / "Versão anterior").
- Introduced one shared derivation (`sortedVersoes` via `React.useMemo`, `defaultOpenVersaoId`, `versaoTooltipLabel()`) consumed by both the Accordion's `defaultValue` and every marker's Tooltip label, so the "is this the current/delivered version" computation lives in exactly one place.

## Task Commits

Each task was committed atomically:

1. **Task 1: EntregarParecerDialog versaoFinalId to NativeSelect + isFetched RBAC (4 sites, isResponsavelOuAdmin untouched)** - `6a93eb4` (feat)
2. **Task 2: Histórico de Versões timeline — Tooltip on every marker + Accordion collapsing old versions (one composition)** - `6001958` (feat)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` - `EntregarParecerDialog`'s `versaoFinalId` swapped to `NativeSelect`; 4 `permissions.isLoading` sites switched to `permissions.isFetched`; "Histórico de Versões" timeline rebuilt as `Accordion`+`Tooltip` composition with a new shared `sortedVersoes`/`defaultOpenVersaoId`/`versaoTooltipLabel` derivation.

## Decisions Made
- Used `Accordion type="single" collapsible` (the plan explicitly allowed either this or `type="multiple"` with an array `defaultValue` — chose the simpler single-item form since exactly one item is ever open by default).
- Kept `NativeSelect`'s own shipped `h-9`/`rounded-md` styling as the sole className (`className="w-full"` only) per UI-SPEC finding #4 — no `rounded-none` addendum, unlike the other NativeSelect call sites in this same phase's other plans.

## Deviations from Plan

None - plan executed exactly as written. Both tasks' automated `<verify>` grep assertions pass against the final file state; `isResponsavelOuAdmin` and the loading/error/empty-state branches of the timeline Card were confirmed unchanged.

## Issues Encountered

- **Executor tooling note (not a code defect):** this worktree has no `node_modules` installed (git worktrees don't share `node_modules`, which is gitignored), so `pnpm exec tsc`/`pnpm lint` could not run directly in this workspace during execution — additionally, the wrapped `pnpm`/`tsc` invocation was intercepted by the user's `rtk` (Rust Token Killer) shell hook and returned a misleading fabricated "success" summary that didn't match the real underlying `npx`-fallback error in its own log file. Per this plan's own `<verification>` section ("Plan-level `pnpm build`/`pnpm lint` is covered by the Wave-2 holistic gate (Plan 04)"), full build/lint verification is intentionally deferred to Plan 04's holistic gate, not this plan's responsibility. Correctness here was instead confirmed via: (1) the plan's own automated grep-based `<verify>` assertions for both tasks (all passed), and (2) a manual JSX open/close tag-balance check across `Accordion`/`AccordionItem`/`AccordionTrigger`/`AccordionContent`/`Tooltip`/`TooltipTrigger`/`TooltipContent` (all 1:1 balanced).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- PARC-18, PARC-19, PARC-20 all satisfied for the Pareceres detail page; 4 of the 7 bundled RBAC sites across the whole Pareceres module are fixed here (the remaining 3 sites live in `pareceres/page.tsx` and `pareceres/nova/page.tsx`, owned by sibling Wave-1 plans 108-01/108-02).
- Plan 04 (Wave 2, holistic gate) should run `pnpm build`/`pnpm lint` against the merged result of all 3 Wave-1 plans (108-01/02/03) — this plan's file was never locally build/lint-verified due to the missing `node_modules` in this worktree (see Issues Encountered above); recommend that gate run in an environment with dependencies installed.

---
*Phase: 108-m-dulo-pareceres*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
- FOUND: `.planning/phases/LEXCV-108-m-dulo-pareceres/108-03-SUMMARY.md`
- FOUND commit: `6a93eb4` (Task 1)
- FOUND commit: `6001958` (Task 2)
