---
phase: 68-entrega-vista-de-entregue-e-rbac
plan: 01
subsystem: ui
tags: [react-query, alert-dialog, rbac, pareceres]

requires:
  - phase: 67-elabora-o-e-versionamento
    provides: "Nova Versão form, useCreateParecerVersao, RBAC instance-check pattern on pareceres/[id]/page.tsx"
provides:
  - "useEntregarParecer mutation hook in web/src/hooks/use-pareceres.ts (PUT query-param, no body, cascading invalidation)"
  - "Entrega AlertDialog with version selector on /pareceres/[id]"
  - "Parecer Entregue read-only summary block for CONCLUIDO solicitações"
  - "Module-wide CardTitle text-lg font-bold typography fix (closes 3-phase recurring defect)"
affects: []

tech-stack:
  added: []
  patterns:
    - "Plain apiFetch PUT mutation with query-param payload (no JSON body), cascading 2-key cache invalidation"
    - "AlertDialog irreversible-action confirmation with a data-derived (not free-text) selection control"
    - "Read-only card derived entirely from already-fetched data (no new fetch) instead of an effect-driven default"

key-files:
  created: []
  modified:
    - web/src/hooks/use-pareceres.ts
    - "web/src/app/(dashboard)/pareceres/[id]/page.tsx"
    - "web/src/app/(dashboard)/pareceres/nova/page.tsx"

key-decisions:
  - "Default selected version for entrega computed via a plain derived expression (state ?? default) instead of a useEffect + setState, avoiding the react-hooks/set-state-in-effect lint error the naive approach introduced"
  - "Entrega trigger and Parecer Entregue block render independently (trigger only pre-CONCLUIDO, block only post-CONCLUIDO), both driven by the same isConcluido/showEntregarTrigger derivations already established in Phase 67"

requirements-completed: [PARC-14, PARC-15, PARC-16]

duration: ~25min
completed: 2026-07-01
---

# Phase 68 Plan 01: Entrega, Vista de Entregue e RBAC Summary

**Wires the irreversible "Entregar Parecer" action (AlertDialog + version selector + useEntregarParecer PUT), adds a "Parecer Entregue" read-only summary card for CONCLUIDO solicitações, and closes the 3-phase-recurring CardTitle typography gap across the whole /pareceres module.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3 completed
- **Files modified:** 3

## Accomplishments

- `useEntregarParecer(solicitacaoId)` added to `use-pareceres.ts`: plain `apiFetch` `PUT` to `/pareceres/solicitacoes/{id}/entregar?versaoFinalId={uuid}` with no JSON body (query param, matching `@RequestParam UUID` backend contract), `onSuccess` invalidates only `["pareceres","detail",id]` + `["pareceres","list"]` (PARC-14)
- `EntregarParecerDialog` component: `AlertDialog` trigger visible only when `canEditPareceres && isResponsavelOuAdmin && !isConcluido` (mirrors backend `isAdmin || isResponsavel` + `pareceres:edit`), mandatory irreversibility copy, version `<select>` sourced exclusively from the already-fetched `versoes.data` (no free-text ID field — tampering mitigation), destructive-styled confirm/trigger, dual-channel error handling (inline + toast) (PARC-14, PARC-16)
- `ParecerEntregueBlock` component replaces the old placeholder text card: looks up the final version from cached `versoes.data` by `versaoFinalId` (no new fetch), shows "Versão N", "Elaborado por {autor} em {data}" derived only from the version's own `criadoPorId`/`createdAt` (no fabricated entrega-timestamp field), green badge, and the reused `AnexoLink` (PARC-15)
- Read-only enforcement verified: CONCLUIDO solicitações render neither the Nova Versão form nor the entrega trigger; cache invalidation on entrega success flips `status` and the UI updates without a manual reload (PARC-16)
- Timeline dot recolored from `bg-blue-600` to neutral `bg-slate-400 dark:bg-slate-500` (accent-leak fix carried from 67-UI-REVIEW)
- Every `CardTitle` under `/pareceres/**` now carries `text-lg font-bold` — "Dados", "Versões", "Parecer Entregue" (detail page) and "Dados da Solicitação" (nova page); zero bare/`font-bold`-only instances remain

## Task Commits

1. **Task 1: useEntregarParecer hook** - `fd29066` (feat)
2. **Task 2: Entrega AlertDialog + version selector + Parecer Entregue block + RBAC/read-only** - `2b58a48` (feat)
3. **Task 3: CardTitle typography fix on nova page** - `d180b35` (style)

## Files Created/Modified

- `web/src/hooks/use-pareceres.ts` - added `useEntregarParecer`
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` - added `AlertDialog` import block, `useEntregarParecer` import, `showEntregarTrigger` derivation, `EntregarParecerDialog` + `ParecerEntregueBlock` components, timeline-dot and CardTitle fixes
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` - CardTitle typography fix

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed useEffect-based default-selection to avoid new lint error**
- **Found during:** Task 2 (post-edit `pnpm lint` run)
- **Issue:** The plan's suggested `React.useState` initializer + a defensive `useEffect` that called `setSelectedVersaoId` inside the effect body triggered a NEW `react-hooks/set-state-in-effect` lint error on `pareceres/[id]/page.tsx:427`, which would have broken the plan's "no NEW errors referencing this file" acceptance criterion.
- **Fix:** Replaced the effect with a derived expression: `const selectedVersaoId = selectedVersaoIdState ?? defaultVersaoId;` — the default is computed inline from `versoes.data` on every render, no effect/setState needed. Behavior is identical (defaults to the most recent version, explicit user selection still overrides).
- **Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
- **Commit:** `2b58a48`

## Issues Encountered

`pnpm lint` reports 23 problems (5 errors, 18 warnings) across unrelated files (`use-toast.ts`, `dashboard-shell.tsx`, `processos/*`, `settings/page.tsx`, `user-profile-form.tsx`) — identical count to Phase 67's confirmed baseline. Zero lint output references any of this plan's three touched files after the Rule-1 fix above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The parecer lifecycle is now closed end-to-end: criar solicitação → elaborar versões → entregar → vista "Parecer Entregue" read-only. RBAC/instance-check visibility on every `/pareceres` action now mirrors the backend's `@PreAuthorize` + instance checks exactly. Ready for Phase 69 (pesquisa avançada) — no blockers.

---
*Phase: 68-entrega-vista-de-entregue-e-rbac*
*Completed: 2026-07-01*

## Self-Check: PASSED

- FOUND: web/src/hooks/use-pareceres.ts
- FOUND: web/src/app/(dashboard)/pareceres/[id]/page.tsx
- FOUND: web/src/app/(dashboard)/pareceres/nova/page.tsx
- FOUND: fd29066 in git log
- FOUND: 2b58a48 in git log
- FOUND: d180b35 in git log
