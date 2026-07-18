---
phase: LEXCV-104-padr-o-datatable-partilhado
reviewed: 2026-07-16T16:30:00Z
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
  info: 3
  total: 5
status: issues_found
---

# Phase LEXCV-104: Code Review Report (Re-review, iteration 3 — final)

**Reviewed:** 2026-07-16T16:30:00Z
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Final re-review of the two targeted fixes applied since the last review pass (commits `7914611` and `d91646e`), plus a fresh look at the 4 files those commits touched (`web/src/lib/csv.ts`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/financeiro/page.tsx`, `web/src/app/(dashboard)/documentos/columns.tsx`). The other 16 files in scope have no commits since the prior full review pass (confirmed via `git log -- <file>` on each), so their prior "clean apart from the two carried-forward Info items" status stands unchanged.

Both requested verifications hold up on their own narrow terms:

1. **CR-01 scoping (verified, with a caveat — see new CR-01 below):** `guardCsvFormula()` in `lib/csv.ts` is no longer applied by `escapeCsvValue`/`toCsv` to every field. `clientes/page.tsx`'s `onExportCsv` now calls it only on `nome` (`clientes/page.tsx:112`); `tipo`, `nif`, `telefone`, `email` are exported unprefixed. `financeiro/page.tsx` mirrors this with a local `guardFormula()` applied only to `processoLabel`/`clienteLabel` (`financeiro/page.tsx:76-77`); `id`, `valorTotal`, `totalPago`, `estado`, `dataAcordo` are unprefixed. The Financeiro side is fully sound: every unguarded field there is either attacker-uncontrollable (a `number` converted via `String()`, which can never contain formula syntax beyond a leading numeric sign) or a closed enum/computed string (`estado`).
2. **WR-01 (verified fixed):** `DocumentoAcoesCell` in `documentos/columns.tsx:78-86` now renders `<Button asChild variant="ghost" size="sm" aria-label="Download"><a href=... target="_blank" rel="noreferrer"><Download .../></a></Button>` — a single `<a>` element, matching `ClienteAcoesCell`'s established `asChild` pattern. No nested interactive elements, no duplicate tab-stop.

However, digging into *why* `telefone`/`nif` were assumed "structured" for the Clientes side surfaces a new, genuine issue: that assumption doesn't hold for `telefone` (and, more narrowly, `email`). Checked against both the frontend Zod schema (`web/src/schemas/clientes.ts`) and the backend JPA entity (`backend/src/main/java/com/lexcv/models/Cliente.java`):
- `nif` **is** genuinely structured/safe: `@Pattern(regexp = "^\\d{9}$")` enforced server-side, `nifPattern = /^\d{9}$/` enforced client-side. It can never start with a formula-trigger character.
- `telefone` has **zero** format validation anywhere in the stack — `optionalTrimmedString` client-side (any string at all), plain `private String telefone;` with no `@Pattern`/`@Email` server-side. It is exactly as attacker-controlled as `nome`.
- `email` is validated client-side with Zod's `.email()`, but that regex still accepts a leading `+` or `-` in the local part (verified interactively: `z.string().email().safeParse("+1+1@x.com").success === true`, same for a leading `-`), and the backend has no `@Email`/`@Pattern` constraint on `Cliente.email` either.

So the Clientes CSV export (`onExportCsv`) still leaves a real, reachable CSV/formula-injection vector via `telefone` (and, to a lesser degree, `email`) — see CR-01 below. This is not a regression *introduced by* the two targeted fixes (those two fixes are internally correct and were applied exactly as their own commit messages describe), but it is a residual, unremediated instance of the original vulnerability class (CWE-1236) that the "structured fields don't need guarding" rationale did not actually hold for once checked against the schema/model.

A second, smaller residual issue: the anti-formula prefix is still asymmetric — `guardCsvFormula()` prefixes `nome` on export but nothing strips it back out in `parseCsv`/`onImportFile`, so a Clientes CSV round-tripped through this app's own Export→Import feature will still silently corrupt any `nome` that happens to start with `=`, `+`, `-`, `@`, tab, or CR (narrower than the original telefone-wide corruption bug the iteration-2 fix closed, but not eliminated). See WR-01 below.

## Critical Issues

### CR-01: CSV/formula-injection still reachable via unguarded `telefone`/`email` fields in Clientes export

**File:** `web/src/app/(dashboard)/clientes/page.tsx:110-118`, `web/src/lib/csv.ts:36-53`
**Issue:** The current fix applies `guardCsvFormula()` only to `c.nome` in `onExportCsv`'s `rows` array:
```ts
const rows = (clientes.data ?? []).map((c) => [
  guardCsvFormula(c.nome ?? ""),
  c.tipo ?? "",
  c.nif ?? "",
  c.telefone ?? "",   // unguarded, and NOT format-locked anywhere
  c.email ?? "",       // unguarded, and the format check has a gap
]);
```
The rationale for leaving `telefone`/`nif` unguarded is that they're "structured" data. That holds for `nif` (`^\d{9}$`, enforced both client- and server-side — confirmed in `schemas/clientes.ts:5` and `backend/src/main/java/com/lexcv/models/Cliente.java:39-41`), but not for the other two:
- `telefone` has no format constraint at all, client or server (`schemas/clientes.ts:41` — `optionalTrimmedString`; `Cliente.java:43` — plain `String telefone` with no `@Pattern`). A user with `clientes:create`/`clientes:edit` can set `telefone` to `=1+1`, `+HYPERLINK("http://evil/","x")`, or a DDE-style payload (`-2+3+cmd|'/c calc'!A0`), and it is stored and exported verbatim.
- `email` is checked client-side against Zod's HTML5-style email regex, but that regex still accepts a leading `+` or `-` in the local part — confirmed: `z.string().email().safeParse("+1+1@x.com").success === true` (and identically for a leading `-`) — and there is no server-side `@Email`/`@Pattern` constraint on `Cliente.email` at all (`Cliente.java:42`). A minimal value like `+1+1@x.com` passes validation and is enough to trigger the same class of Excel/Sheets formula execution once exported.

Any staff member who exports "Clientes" to CSV and opens the file in Excel/LibreOffice/Google Sheets is exposed to formula execution (including DDE-based command execution in legacy Excel configurations, or phishing via `HYPERLINK`) driven by another tenant user's `telefone`/`email` input — the exact vulnerability class CR-01 was originally raised to close, still reachable through two fields the "scoped" fix assumed were safe.

**Fix:** Extend the guard to every field whose value is not provably format-locked, rather than trusting a "looks structured" assumption:
```ts
// clientes/page.tsx
const rows = (clientes.data ?? []).map((c) => [
  guardCsvFormula(c.nome ?? ""),
  c.tipo ?? "",
  c.nif ?? "",                       // safe: backend + frontend enforce ^\d{9}$
  guardCsvFormula(c.telefone ?? ""), // NOT format-locked anywhere — must be guarded
  guardCsvFormula(c.email ?? ""),    // client regex still allows a leading +/- — must be guarded
]);
```
Longer-term, either add a real `@Pattern` phone-number constraint server-side (which would make excluding `telefone` from the guard defensible) or keep guarding every field whose value isn't backed by a strict, server-enforced format — don't rely on "this field is usually formatted like X" as a security boundary.

## Warnings

### WR-01: `nome` values starting with a formula-trigger character are still silently corrupted on Clientes CSV re-import

**File:** `web/src/lib/csv.ts:1-34,48-53`, `web/src/app/(dashboard)/clientes/page.tsx:110-118,136-208`
**Issue:** `onExportCsv` prefixes `nome` with `guardCsvFormula()` when it starts with `=`, `+`, `-`, `@`, tab, or CR, but `parseCsvLine` (used by `parseCsv`, in turn used by `onImportFile`) has no counterpart that strips a leading guard apostrophe back out. Re-importing a Clientes CSV that this app itself just exported — a directly supported workflow via the same page's "Exportar CSV" / "Importar CSV" buttons — will create a cliente whose `nome` is `'Sociedade Exemplo` instead of `-Sociedade Exemplo` (or whatever the original value was) for any `nome` that happens to start with one of those six characters. This is a narrower version of the exact round-trip corruption bug the CR-01 rescoping fix (iteration 2) was written to eliminate for `telefone` — it wasn't eliminated for `nome`, only made much rarer (company/cliente names starting with `-`, `+`, `@`, or `=` are less common than international phone numbers starting with `+`, but not impossible).
**Fix:** Mirror the fix already suggested for the telefone case, scoped to import: strip a single leading `'` in `onImportFile` (or in `parseCsv`) when it's immediately followed by one of the trigger characters:
```ts
// lib/csv.ts — export FORMULA_TRIGGER_CHARS alongside guardCsvFormula so importers can share it
export function stripCsvFormulaGuard(value: string): string {
  if (value.length > 1 && value[0] === "'" && FORMULA_TRIGGER_CHARS.some((c) => value[1] === c)) {
    return value.slice(1);
  }
  return value;
}
```
```ts
// clientes/page.tsx, onImportFile
const nome = stripCsvFormulaGuard((r[idxNome] ?? "").trim());
```

## Info

### IN-01: Anti-formula-injection trigger-char array still duplicated between `lib/csv.ts` and `financeiro/page.tsx`

**File:** `web/src/lib/csv.ts:36`, `web/src/app/(dashboard)/financeiro/page.tsx:18`
**Issue:** `FORMULA_TRIGGER_CHARS = ["=", "+", "-", "@", "\t", "\r"]` is defined verbatim in both files (`lib/csv.ts`'s `guardCsvFormula` and `financeiro/page.tsx`'s local `guardFormula`). Flagged already in the prior review iteration as an accepted, explicitly out-of-scope Info item (`fix_scope: critical_warning`); still true today, unchanged.
**Fix:** Export `FORMULA_TRIGGER_CHARS` (and ideally `guardCsvFormula` itself) from `lib/csv.ts` and import it in `financeiro/page.tsx` instead of redefining it locally.

### IN-02: Dead fallback branch in `DataTableViewOptions` after the WR-02 fix

**File:** `web/src/components/shared/data-table/data-table-view-options.tsx:56-58`
**Issue:** `const label = metaLabel ?? (typeof header === "string" ? header : column.id);` — every column definition across all 5 migrated tables now sets `meta.label`, so `metaLabel` is always defined for a hideable column and the `typeof header === "string"` branch is unreachable. Unchanged since the prior review; still Info-level (no user-facing effect).
**Fix:** Simplify to `const label = metaLabel ?? column.id;` unless there's a deliberate intent to support a future un-migrated column without `meta.label`.

### IN-03: Documentos mobile card Download link still missing `encodeURIComponent`

**File:** `web/src/app/(dashboard)/documentos/page.tsx:223`
**Issue:** `href={`/api/v1/documentos/${id}/download`}` interpolates `id` directly, unlike the desktop `DocumentoAcoesCell` (`documentos/columns.tsx:80`, fixed alongside WR-01) and every other link in the same file (e.g. `documentos/page.tsx:201`), which use `encodeURIComponent(id)`. This file was not touched by either of the two targeted fix commits, so it remains exactly as flagged in the original (iteration 1) review — harmless today since ids are UUIDs, but an inconsistency with the codebase's own convention.
**Fix:** `href={`/api/v1/documentos/${encodeURIComponent(id)}/download`}`.

---

_Reviewed: 2026-07-16T16:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
