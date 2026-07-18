---
phase: LEXCV-104-padr-o-datatable-partilhado
reviewed: 2026-07-16T15:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - web/package.json
  - web/src/app/(dashboard)/clientes/columns.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/documentos/columns.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/financeiro/columns.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
  - web/src/app/(dashboard)/pareceres/columns.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/processos/columns.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/components/shared/data-table/data-table-column-header.tsx
  - web/src/components/shared/data-table/data-table-pagination.tsx
  - web/src/components/shared/data-table/data-table-view-options.tsx
  - web/src/components/shared/data-table/data-table.tsx
  - web/src/components/ui/pagination.tsx
  - web/src/lib/csv.ts
  - web/src/lib/financeiro.ts
  - web/src/lib/pareceres.ts
findings:
  critical: 1
  warning: 1
  info: 2
  total: 4
status: issues_found
---

# Phase LEXCV-104: Code Review Report (Re-review, iteration 2)

**Reviewed:** 2026-07-16T15:00:00Z
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Re-reviewed all 20 files after `gsd-code-fixer` applied the 7 fixes recorded in `104-REVIEW-FIX.md` (iteration 1: CR-01 CSV/formula-injection, WR-01 Documentos Download parity, WR-02 view-options friendly labels, WR-03 Clientes NIF accessor/display mismatch, WR-04 column-def memoization, WR-05 shared `lib/financeiro.ts`/`lib/pareceres.ts`, WR-06 `getRowId`).

Six of the seven fixes were verified correct with no regression: WR-02 (`meta.label` threaded through every column in all 5 tables, confirmed exhaustively — no hideable column is left showing a raw id), WR-03 (the `nif` column's `accessorFn` now mirrors the cell's own display branch exactly), WR-04 (`processoById`/`clienteNomeById`/columns all correctly `useMemo`'d with correct dependency arrays in Processos, Pareceres, and Financeiro — verified with a full `tsc --noEmit` and targeted `eslint` pass, both clean), WR-05 (`lib/financeiro.ts` and `lib/pareceres.ts` created and imported correctly by both `columns.tsx`/`page.tsx` in each pair, no leftover duplicate local definitions), and WR-06 (`getRowId` threaded into the shared `DataTable` and passed with a correct, unique, entity-based id from all 5 call sites — confirmed `DataTable` has no other unlisted consumers that could be broken by the new optional prop).

The remaining two findings are new regressions/gaps introduced by the fixes themselves:

1. **CR-01 (new, Critical):** the CR-01 fix (leading-apostrophe prefix on fields starting with `= + - @`) was applied uniformly to every exported field in the shared `lib/csv.ts`, including `telefone` — a field this very app's own placeholders (`+238 000 0000`) encourage users to enter in a format that triggers the prefix. The prefix is never stripped back out by this app's own `parseCsv`/`onImportFile`, so round-tripping a Clientes export through the app's own "Importar CSV" feature permanently corrupts phone numbers (and any other field starting with those characters) by leaving a literal `'` character in the stored value.
2. **WR-01 fix (new, Warning):** the new Download link added to `DocumentoAcoesCell` nests a `<Button>` (a real `<button>` element) inside an `<a>` tag instead of using this codebase's own established `Button asChild` + `<Link>`/`<a>` pattern (used everywhere else, e.g. `ClienteAcoesCell`). This is invalid HTML (interactive content inside interactive content), triggers a React `validateDOMNesting` console warning, and creates two overlapping keyboard tab-stops for one visual control.

## Critical Issues

### CR-01: CSV anti-formula-injection prefix corrupts legitimate data on import round-trip (telefone and other `+`/`-`/`=`/`@`-prefixed fields)

**File:** `web/src/lib/csv.ts:36-47`, `web/src/app/(dashboard)/clientes/page.tsx:110-129,136-208`
**Issue:** The iteration-1 fix for CR-01 added `FORMULA_TRIGGER_CHARS = ["=", "+", "-", "@", "\t", "\r"]` and prepends `'` to any exported field starting with one of those characters (`escapeCsvValue` in `lib/csv.ts`, mirrored by `escapeField` in `financeiro/page.tsx`). This is applied blindly to **every** field passed to `toCsv`, including `telefone` (`clientes/page.tsx:110-118`, `onExportCsv`'s `rows` array: `[c.nome ?? "", c.tipo ?? "", c.nif ?? "", c.telefone ?? "", c.email ?? ""]`). Phone numbers in this app's own recommended international format start with `+` — confirmed by the app's own placeholders in `web/src/app/(dashboard)/settings/page.tsx:556` and `web/src/components/profile/user-profile-form.tsx:165`, both `placeholder="+238 000 0000"`. So exporting any cliente with an international-format phone number (a normal, encouraged case for this Cape Verde app) produces a CSV cell containing the literal text `'+238 000 0000` instead of `+238 000 0000`.

That alone is only cosmetically wrong in Excel/Sheets (which recognize a leading `'` as a "force text" marker and hide it visually). The real defect is that this app's own CSV importer does **not** implement the same convention: `parseCsvLine` (`lib/csv.ts:1-34`) has no special handling for a leading `'` — it is read as literal character data. `onImportFile` (`clientes/page.tsx:136-208`) then takes that raw parsed value straight into `createCliente.mutateAsync({ ..., telefone: idxTelefone >= 0 ? (r[idxTelefone] ?? "").trim() || undefined : undefined, ... })` (`clientes/page.tsx:182`) with no stripping. Re-importing a CSV this app itself exported (a directly supported workflow via the same page's "Exportar CSV"/"Importar CSV" buttons) therefore silently and permanently persists a corrupted phone number (`'+238 000 0000`) with no error surfaced to the user. This is a genuine regression introduced by the CR-01 fix — before the fix, `telefone` values round-tripped untouched, since the pre-fix `escapeCsvValue` only quoted values containing `,`/`"`/newlines.

**Fix:** Either (a) restrict the anti-formula prefixing to genuinely free-text, attacker-influenced fields (`nome` in Clientes; `processoLabel`/`clienteLabel` in Financeiro) rather than blanket-applying it to every field, or (b) make the mitigation symmetric by stripping a single leading `'` in `parseCsvLine`/`onImportFile` when it precedes one of the trigger characters, e.g.:
```ts
// lib/csv.ts, after existing per-cell trim in parseCsvLine's caller (parseCsv), or inline in onImportFile:
function stripFormulaGuard(value: string): string {
  if (value.length > 1 && value[0] === "'" && FORMULA_TRIGGER_CHARS.some((c) => value[1] === c)) {
    return value.slice(1);
  }
  return value;
}
```
Option (a) is preferable — it avoids corrupting non-formula-risk structured data (phone numbers, ids) in the first place and keeps the mitigation scoped to where the original CR-01 finding actually applied (free-text name fields).

## Warnings

### WR-01: New Documentos Download link nests a `<Button>` inside an `<a>` (invalid HTML / duplicate tab-stop), inconsistent with the codebase's own `asChild` pattern

**File:** `web/src/app/(dashboard)/documentos/columns.tsx:79-87`
**Issue:** The iteration-1 fix for WR-01 added:
```tsx
<a
  href={`/api/v1/documentos/${encodeURIComponent(documento.id)}/download`}
  target="_blank"
  rel="noreferrer"
>
  <Button type="button" variant="ghost" size="sm" aria-label="Download">
    <Download className="h-4 w-4" />
  </Button>
</a>
```
This nests a real `<button>` element (rendered by `Button`) inside an `<a>` element. Per the HTML5 content model, `<a>` only permits phrasing/transparent content and explicitly disallows other interactive content (`<button>`) as a descendant. React's DOM validator (`validateDOMNesting`) will log a console warning for this at runtime, and — more importantly — it creates two overlapping, separately focusable elements for what is visually one control: keyboard users tabbing through the row will hit the `<a>` wrapper and then the inner `<button>` as two distinct stops for a single action, and assistive technology will announce conflicting/nested interactive roles.

Every other actionable link-as-button in this codebase (e.g. `ClienteAcoesCell`'s "Ver detalhes"/"Imprimir"/"Editar" in `clientes/columns.tsx:37-91`, and the "Ver detalhes" buttons in `processos/columns.tsx:131-149` and `pareceres/columns.tsx:84-101`) uses the established `<Button asChild><Link href=...>...</Link></Button>` pattern instead, which renders a single `<a>` element styled as the button (no nesting). The new Download link is the only place in the 5 migrated tables that deviates from this convention.
**Fix:** Use the same `asChild` pattern as the rest of the codebase:
```tsx
<Button asChild type="button" variant="ghost" size="sm" aria-label="Download">
  <a
    href={`/api/v1/documentos/${encodeURIComponent(documento.id)}/download`}
    target="_blank"
    rel="noreferrer"
  >
    <Download className="h-4 w-4" />
  </a>
</Button>
```

## Info

### IN-01: Anti-formula-injection escaping logic still duplicated between `lib/csv.ts` and `financeiro/page.tsx`

**File:** `web/src/lib/csv.ts:36-47`, `web/src/app/(dashboard)/financeiro/page.tsx:18-29`
**Issue:** The original CR-01 finding explicitly suggested the `FORMULA_TRIGGER_CHARS`/prefixing logic "ideally share one implementation" once applied to both `financeiro/page.tsx`'s `escapeField` and `lib/csv.ts`'s `escapeCsvValue`. The applied fix duplicated the identical `FORMULA_TRIGGER_CHARS` array and near-identical prefixing logic verbatim in both files instead of extracting it to one shared helper (the same drift risk the phase's own WR-05 finding was raised to prevent for `formatMoneyCVE`/`formatDate`/`calcHonorarioStatus`). A future change to the trigger-character set (e.g. applying the fix from CR-01 above) now has to be made in two places.
**Fix:** Extract `FORMULA_TRIGGER_CHARS` and the prefixing step into a small shared helper (e.g. in `lib/csv.ts`, exported and imported by `financeiro/page.tsx`), consistent with the WR-05 pattern already used for the other duplicated formatting helpers.

### IN-02: Dead fallback branch in `DataTableViewOptions` after the WR-02 fix

**File:** `web/src/components/shared/data-table/data-table-view-options.tsx:56-58`
**Issue:** `const label = metaLabel ?? (typeof header === "string" ? header : column.id);` — now that every column definition across all 5 migrated tables sets `meta: { label: "..." }` (verified exhaustively across Clientes/Documentos/Financeiro/Pareceres/Processos), `metaLabel` is always defined for every hideable column, so the `typeof header === "string" ? header : column.id` fallback is unreachable dead code (the same dead branch the original WR-02 finding identified, now nested one level deeper instead of removed).
**Fix:** Simplify to `const label = metaLabel ?? column.id;` and drop the now-fully-dead `typeof header === "string"` check (or keep only if there's an intent to support un-migrated future columns without `meta.label`, in which case a comment explaining that intent would help).

---

_Reviewed: 2026-07-16T15:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
