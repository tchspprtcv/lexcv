---
phase: LEXCV-105-m-dulos-clientes-processos-combinados
reviewed: 2026-07-16T14:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/merge/page.tsx
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx
  - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
findings:
  critical: 0
  warning: 0
  info: 4
  total: 4
status: issues_found
---

# Phase 105: Code Review Report (Re-review, iteration 2)

**Reviewed:** 2026-07-16T14:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Re-reviewed the Clientes+Processos shadcn migration after gsd-code-fixer applied 4 fixes for the iteration-1 findings (CR-01, WR-01, WR-02, WR-03). All four fixes were traced line-by-line against the source and cross-checked for regressions. **All four are correctly and completely applied; no regression was introduced by any of them.** No new Critical or Warning issues were found. Four Info-level (code-quality) items remain: three are pre-existing duplication/memoization items carried forward unchanged from iteration 1 (not part of the fix scope), and one is a small new duplication introduced by the CR-01 fix itself.

### Fix-by-fix verification

**CR-01 (cancel-after-tipo-change data loss) — CONFIRMED FIXED, no regression.**
`clientes/[id]/page.tsx:282-289` now extracts `computeLegacyDocumentoTipo(data: Cliente)` as a `useCallback`, and it is called from both the load effect (`clientes/[id]/page.tsx:293`) and `onCancel` (`clientes/[id]/page.tsx:357`), in each case immediately alongside the corresponding `form.reset(buildDefaultValues(cliente.data))` call. Traced the full repro scenario from the original finding (load with invalid legacy combo → change tipo → confirm → cancel → re-edit → save without touching the field): `legacyDocumentoTipo` is now correctly restored to the original loaded value on cancel, so the `<option value={legacyDocumentoTipo}>` branch (`clientes/[id]/page.tsx:585-589`) and the schema's per-value exemption (`buildClienteFormSchema`'s `allowedLegacyDocumentoTipo`, `schemas/clientes.ts:78-88`) stay in sync with the form's restored `documento_tipo` value. No infinite-loop or stale-closure risk: both `computeLegacyDocumentoTipo` and `buildDefaultValues` are `useCallback(..., [])`, and the load effect's dependency array (`clientes/[id]/page.tsx:297`) includes both stably.

**WR-01 (`NativeSelect` missing `w-full`) — CONFIRMED FIXED across all 17 flagged call sites, no wrong/missed sites.**
Verified every one of the originally-flagged sites individually (not just via inference from the diff):
- `clientes/page.tsx:358, 373` (Tipo, Estado filters) — both have `className="w-full"`.
- `clientes/novo/page.tsx:258, 294` (Tipo de Documento, Ramo de Atividade) — both fixed.
- `clientes/[id]/page.tsx:576, 709, 1712, 1836, 1876` (documento_tipo, ramo_atividade, "Adicionar a {title}" utilizador select, contacto tipo x2) — all 5 fixed; confirmed via full-file grep that the file contains exactly 5 `NativeSelect` usages and all 5 now carry `w-full`.
- `clientes/merge/page.tsx:118, 135` (Cliente principal, Cliente duplicado) — both fixed.
- `processos/[id]/page.tsx:1216, 1230, 1729, 1808, 2132, 2421` (Prioridade, Responsável, fase status, decisão tipo, testemunha tipo, reatribuir responsável) — all 6 fixed; confirmed via full-file grep that the file contains exactly 6 `NativeSelect` usages and all 6 now carry `w-full`.

Also re-verified the 7 call sites the iteration-1 review said "already got it right" (`processos/page.tsx:236, 282`, `processos/novo/page.tsx:302, 328, 352, 560`, `processos/[id]/editar/page.tsx:160`) — all still intact with `className="w-full"`, confirming the fixer did not regress them while touching the other files. Total `NativeSelect` instances across the 11 files: 24 (17 fixed + 7 already-correct), all now consistently `w-full`. (Note: the iteration-1 review's summary line said "16 of 23" — off by one from the actual 17-of-24 count — but this is an arithmetic note about the prior report, not a code defect; every site is confirmed fixed either way.)

Confirmed the mechanism is sound: `NativeSelect`'s wrapper `<div>` uses `cn("...relative w-fit...", className)` (`web/src/components/ui/native-select.tsx:16-20`), and `cn` is `twMerge(clsx(...))` (`web/src/lib/utils.ts`), so a later `w-full` in `className` correctly wins over the earlier `w-fit` via tailwind-merge's width-utility conflict resolution.

**WR-02 (nested-tab RBAC race) — CONFIRMED FIXED, no regression.**
`clientes/[id]/page.tsx:864-896`: all three nested `TabsContent` blocks (Processos, Pareceres, Documentos Entregues) now follow the pattern `!permissions.isFetched ? <loading /> : canView... ? <Tab /> : <AccessDeniedState />`, mirroring the already-fixed page-level guard. Confirmed `permissions.isFetched` now appears 4 times in this file (1 page-level + 3 nested), matching the fix scope exactly.

**WR-03 (CSV `tipo` force-cast) — CONFIRMED FIXED, no regression.**
`clientes/page.tsx:176-187`: `tipo` is now derived via `rawTipo === "PARTICULAR" || rawTipo === "EMPRESA" ? rawTipo : undefined` (uppercased/trimmed first), and a row with a non-empty-but-invalid `tipo` value is rejected with a specific `linha N: tipo inválido ("X")` message and `continue`s (skips the `createCliente.mutateAsync` call for that row) rather than silently forwarding a bad enum value to the backend. Verified the edge cases: empty `tipo` column value → `undefined`, no error (correct, matches "tipo optional" schema); missing `tipo` column entirely (`idxTipo === -1`) → also `undefined`, no error (correct); non-enum value → rejected with `failed++`/`continue` before any network call (correct, matches iteration-1's suggested fix).

## Critical Issues

None found.

## Warnings

None found.

## Info

### IN-01 (carried forward, unaddressed): Documento wire-shape workaround (`tamanho`/`createdAt`) duplicated across two files

**Files:** `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx:45-48` (`wireSizeAndDate`) and `web/src/app/(dashboard)/clientes/[id]/page.tsx:1418-1420` (inline in `ClienteDocumentoEntregueRow`)

**Issue:** Both independently re-derive `{ tamanho, createdAt }` from the same `Documento as unknown as {...}` cast, with near-identical comments explaining the same backend/DTO field-naming mismatch. Unchanged from iteration 1 — not part of the 4 fixes and not required by this iteration, but still present.

**Fix:** Extract a single `getDocumentoWireFields(documento: Documento)` helper into e.g. `web/src/lib/documento-wire.ts` and import it from both call sites.

### IN-02 (carried forward, unaddressed): `deriveInitials` duplicated three times

**Files:** `clientes/[id]/page.tsx:111-119`, `processos/[id]/page.tsx:194-202` (identical function bodies), plus an inline third copy of the same logic in `clientes/page.tsx:437` (mobile card initials).

**Fix:** Move to a shared `web/src/lib/utils.ts` (or a new `lib/initials.ts`) export and import from all three call sites.

### IN-03 (carried forward, unaddressed): `ProcessoDocumentosTab`'s `columns(canEditDocumentos)` is not memoized, unlike its sibling list pages

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:2658`

**Issue:** `<DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />` still calls the `columns()` factory inline on every render (e.g. on every upload-progress tick via `setProgresso`), unlike `clientes/page.tsx:65` and `processos/page.tsx:60`, which both memoize equivalent factories. Not a functional bug (sorting/visibility state lives in `DataTable`'s own `useState`), but remains an inconsistency with the established pattern.

**Fix:**
```tsx
const documentoColumns = React.useMemo(() => columns(canEditDocumentos), [canEditDocumentos]);
...
<DataTable columns={documentoColumns} data={documentos} getRowId={(d) => d.id} />
```

### IN-04 (new, introduced by the CR-01 fix): `computeLegacyDocumentoTipo` and `buildDefaultValues` duplicate the same `loadedTipo`/`loadedDocumentoTipo` extraction

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:248-250` (`buildDefaultValues`) and `web/src/app/(dashboard)/clientes/[id]/page.tsx:282-284` (`computeLegacyDocumentoTipo`)

**Issue:** Both callbacks independently compute:
```tsx
const loadedTipo = (data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined;
const loadedDocumentoTipo = data.documento_tipo ?? data.documentoTipo ?? "";
```
This duplication pre-dates the CR-01 fix in part (the extraction previously lived inline in the load effect), but the fix perpetuated it by copying the same two lines into the newly-extracted `computeLegacyDocumentoTipo` rather than factoring out a shared helper both callbacks could call. Low risk (the two call sites are adjacent and covered by the same tests/manual repro as CR-01), but a future change to one field-fallback rule (e.g. adding a third field-naming variant) would need to be applied in two places to stay consistent.

**Fix:**
```tsx
const getLoadedTipoAndDocumentoTipo = React.useCallback((data: Cliente) => ({
  loadedTipo: (data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined,
  loadedDocumentoTipo: data.documento_tipo ?? data.documentoTipo ?? "",
}), []);
```
and call it from both `buildDefaultValues` and `computeLegacyDocumentoTipo`.

---

_Reviewed: 2026-07-16T14:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
