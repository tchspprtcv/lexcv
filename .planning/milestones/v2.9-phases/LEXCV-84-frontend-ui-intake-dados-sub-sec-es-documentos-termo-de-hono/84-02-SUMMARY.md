---
phase: 84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
plan: 02
subsystem: ui
tags: [nextjs, react, tanstack-query, print-css, termo-honorarios]

requires:
  - phase: 83
    provides: "useProcesso/useCliente/useHonorarios hooks, Processo.juizo/origem fields, origemProcessoToLabel() mapping"

provides:
  - "New printable route /processos/[id]/termo-honorarios combining Cliente + Processo + Honorario data"
  - "Hard block on printing when Honorario.valorTotal is null (PROC-16)"

affects: [84-03, 84-04, 84-05]

tech-stack:
  added: []
  patterns:
    - "3-hook combined loading/error gating (isLoading/isError = OR of all three source hooks) before rendering any cross-entity document body"
    - "fmtMoney() local currency formatter distinct from the generic fmt() text helper, to avoid printing a raw unformatted number on a legal document"

key-files:
  created:
    - "web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx"
  modified: []

key-decisions:
  - "Signature captions relabelled 'O Advogado' / 'O Cliente' (neutral, non-gendered) instead of Ficha Cliente's 'A Advogada' / 'O Cliente', per UI-SPEC Claude's Discretion note — signer identity is unknown at render time"
  - "Imprimir Button carries explicit rounded-none per UI-SPEC Anti-Safe Harbor sharp-edges requirement, even though the Ficha Cliente analog it was cloned from omits it"

patterns-established:
  - "Termo de Honorários print route pattern: clone of Ficha Cliente's PRINT_CSS/BLANK/fmt(), extended with a currency-safe fmtMoney() for money fields on legal documents"

requirements-completed: [PROC-15, PROC-16]

duration: 15min
completed: 2026-07-08
---

# Phase 84 Plan 02: Termo de Honorários print route Summary

**New printable `/processos/{id}/termo-honorarios` route combining Cliente + Processo + Honorário data, with a hard print-block (not just a warning) when `valorTotal` is null.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-07-08T00:36:00Z
- **Completed:** 2026-07-08T00:51:43Z
- **Tasks:** 2 completed
- **Files modified:** 1 (new file)

## Accomplishments
- Created `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` as a direct architectural clone of `clientes/[id]/ficha/page.tsx`'s print pattern (`PRINT_CSS`, `window.print()`, `BLANK`/`fmt()`)
- Wired three independently-checked hooks (`useProcesso`, `useCliente`, `useHonorarios`) with combined `isLoading`/`isError` gating so the document body never renders with partial cross-entity data (PITFALLS.md Pitfall 6)
- Implemented PROC-16's hard print block: Imprimir button is `disabled` when `honorario.valorTotal === null`, with an inline red message linking to `/financeiro/{honorarioId}`
- Distinct error state for "no Honorário at all" (defensive, pre-Phase-82 processos) vs. "Honorário exists but valorTotal is null" (blocked state)
- Four UI-SPEC document sections (Identificação do Cliente, Identificação do Processo, Honorários, Data e Assinaturas) with a currency-safe `fmtMoney()` helper for `valorTotal`/`totalPago`

## Task Commits

1. **Task 1: Page shell — permission gate, 3-hook data sourcing, loading/error/blocked gating, print toolbar** - `e13ef53` (feat)
2. **Task 2: Document body — SectionTitle/Field components, 4 sections, currency-safe valorTotal rendering** - `2e2b8ea` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` - New printable Termo de Honorários route: permission-gated shell, 3-hook data sourcing with combined loading/error gating, hard print-block on null `valorTotal`, and the four-section document body

## Decisions Made
- Signature block relabelled "O Advogado" / "O Cliente" (Claude's Discretion, UI-SPEC) since the signer's gender/role is not known at render time
- `fmtMoney()` defined locally (not extracted to a shared lib) since it's the only currency-rendering surface in this file, mirroring the `formatMoneyCVE` pattern from `financeiro/[id]/page.tsx` without introducing a new shared export
- Explicit `rounded-none` added to the Imprimir Button per UI-SPEC's hard sharp-edges requirement, even though the Ficha Cliente analog being cloned omits it

## Deviations from Plan

None - plan executed exactly as written. Both tasks implemented in the same pass (context budget allowed it), following the plan's explicit allowance to implement Task 2 immediately rather than leaving a stub.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Route is fully functional and gated; `pnpm run build` passes with `/processos/[id]/termo-honorarios` listed as a dynamic (ƒ) route
- Ready for the "Gerar Termo de Honorários" trigger button (Dados card) to be wired in a later plan of this phase, linking to this route
- No blockers for downstream plans (84-03, 84-04, 84-05)

---
*Phase: 84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx
- FOUND: e13ef53
- FOUND: 2e2b8ea
