# Phase 60 Verification: Ficha Imprimível

**Verified:** 2026-06-30
**Status:** passed
**Method:** Independent code read (not SUMMARY.md trust) + `pnpm build`

## Scope

Plans 60-01 (ficha page) and 60-02 (access points) merged to master via 294ec21, de5c35b, ab63428,
71947ba, 4dc4a53. Verified against 60-CONTEXT.md decisions D-01..D-08, 60-01/60-02 PLAN.md
must_haves, and REQUIREMENTS.md FICH-01/FICH-02.

## Files Read

- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`
- `web/src/app/(dashboard)/clientes/[id]/page.tsx`
- `web/src/app/(dashboard)/clientes/page.tsx`
- `web/src/types/clientes.ts`

## Must-Have Scoring

### 60-01-PLAN.md truths

| Truth | Status | Evidence |
|---|---|---|
| User with `clientes:view` sees data in 8 sections | PASS | `Ficha` component renders Identificação, Contactos, Descrição do Caso, Advogados e Administrativos, Documentos, Deslocações, Honorários, Data e Assinaturas (page.tsx:187-241) |
| Unfilled fields show `"___________"` | PASS | `fmt()` helper (line 39-42) returns `BLANK = "___________"` for null/undefined/empty; used throughout via `Field` component, which also applies `font-mono underline` styling when blank (line 132-140) |
| Imprimir button calls `window.print()`; print hides sidebar/topbar/button | PASS | Button `onClick={() => window.print()}` (line 96); `PRINT_CSS` hides `aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn` (lines 22-35) |
| User without `clientes:view` gets AccessDeniedState | PASS | Guard at line 56-63: `if (!permissions.isLoading && !canViewClientes) return <AccessDeniedState .../>` — matches pattern from `/clientes/[id]/page.tsx` |
| A4 with 2cm margins via `@page` | PASS | `@page { size: A4; margin: 2cm; }` literal in `PRINT_CSS` (lines 31-34) |

### 60-02-PLAN.md truths

| Truth | Status | Evidence |
|---|---|---|
| Detail page has "Imprimir Ficha" button opening ficha in new tab | PASS | `clientes/[id]/page.tsx:103-112` — `Link href={...}/ficha`, `target="_blank"`, `rel="noopener noreferrer"`, text "Imprimir Ficha", `Printer` icon imported at line 5 |
| Listing row has Printer icon opening ficha in new tab | PASS | `clientes/page.tsx:570-578` — same href pattern, `target="_blank"`, `rel="noopener noreferrer"`; `Printer` imported in the shared lucide-react import (line 5) |
| Both entry points work independently without reloading current page | PASS | Both use `target="_blank"` Links — standard browser new-tab behavior, no client-side navigation of current tab |

## CONTEXT.md Decision Verification

| Decision | Status | Notes |
|---|---|---|
| D-01 high-fidelity layout, A4, signature footer | PASS | Header with tenant name/"Ficha Cliente" title/date; footer with "A Advogada"/"O Cliente" signature blocks (border-b border-black, min-w-[200px] h-8) |
| D-02 8 sections in original order | PASS | Order matches spec exactly |
| D-03 blank line placeholder | PASS | `fmt()` / `BLANK` constant |
| D-04 `@media print` hides nav chrome | PASS | Selectors match D-04 list (sidebar/topbar/buttons/breadcrumbs handled via `aside, header, [data-print-hide]`) |
| D-05 A4 page size, 2cm margin | PASS | Literal `@page` rule |
| D-06 `window.print()`, no PDF lib | PASS | No new dependency added; confirmed no PDF-related import |
| D-07 detail page button, opens new tab | PASS | Confirmed above |
| D-08 listing "Ver Ficha" action, opens new tab | PASS (adapted) | Implemented as direct Printer icon button rather than kebab/dropdown menu — explicitly called out as a deliberate deviation in 60-02-PLAN.md ("Padrão 4 / Assunção A5") to stay consistent with existing Eye/Pencil/Trash2 direct-button pattern in `ClienteRow`. This is a reasonable, documented adaptation of D-08's intent (single click, new tab), not a gap. |

## Requirements Traceability

| Requirement | Status | Evidence |
|---|---|---|
| FICH-01 (view reproducing physical form) | PASS | 8-section ficha page exists and renders cliente data |
| FICH-02 (print/export via print CSS) | PASS | `window.print()` + `@media print` + `@page` A4 |

## Deviations From Plan Text (Non-Gaps)

The 60-01-PLAN.md interface spec assumed flat string fields (`advogados?: string`, `administrativos?: string`,
etc.) on `Cliente`. The actual `web/src/types/clientes.ts` and `ficha/page.tsx` instead use:
- `dados_tipo: DadosTipoParticular | DadosTipoEmpresa` (polymorphic, with type guard `isDadosTipoParticular`)
- Dedicated hooks `useClienteAdvogados(id)` / `useClienteAdministrativos(id)` returning structured lists, joined to display strings
- `documentos_entregues` / `documentos_a_tratar` / `deslocacoes` as typed array objects (not flat strings), mapped via `.map(d => d.descricao).join(", ")`
- `honorarios_propostos` as a structured `HonorariosPropostos { total, totalPorExtenso, previsao }` object

This reflects the actual shape later phases (57/59) settled on rather than the plan's draft guess, and is functionally equivalent — all 8 sections still render with correct blank-fallback behavior. Not scored as a gap.

## Build Verification

```
cd web && pnpm build
```
Result: **success**. TypeScript compiled with no errors. Route table confirms:
```
ƒ /clientes/[id]/ficha
```
registered as a dynamic server-rendered route, consistent with a per-client print page.

## Overall Verdict

**PASSED** — 8/8 must-have truths across both plans verified directly in code. All 8 CONTEXT.md
decisions (D-01–D-08) implemented as specified or with a documented, reasonable adaptation (D-08).
Both access points (detail page button, listing row icon) confirmed to use `target="_blank"` +
`rel="noopener noreferrer"`. Production build compiles cleanly with the new route registered.

No gaps found. No further action required for Phase 60.
