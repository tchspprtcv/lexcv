---
phase: LEXCV-104-padr-o-datatable-partilhado
fixed_at: 2026-07-16T13:30:00Z
review_path: .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
iteration: 2
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-104: Code Review Fix Report

**Fixed at:** 2026-07-16T13:30:00Z
**Source review:** .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 2 (1 critical, 1 warning — this is a re-review of iteration-1 fixes; Info findings IN-01/IN-02 excluded per `fix_scope: critical_warning`)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### CR-01: CSV anti-formula-injection prefix corrupts legitimate data on import round-trip (telefone and other `+`/`-`/`=`/`@`-prefixed fields)

**Files modified:** `web/src/lib/csv.ts`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/financeiro/page.tsx`
**Commit:** `7914611`
**Applied fix:** Applied fix option (a) from the review — restricted the anti-formula leading-apostrophe prefix to genuinely free-text, attacker-influenced fields instead of blanket-applying it to every exported field. In `lib/csv.ts`, extracted the prefixing step out of `escapeCsvValue` into a new exported `guardCsvFormula()` helper; `escapeCsvValue` (used internally by `toCsv` for every field) now only performs the always-safe CSV-syntax quoting (commas/quotes/newlines). `clientes/page.tsx`'s `onExportCsv` now calls `guardCsvFormula(c.nome ?? "")` only on the `nome` field, leaving `tipo`, `nif`, `telefone`, and `email` unprefixed — so international-format phone numbers (`+238 000 0000`, this app's own placeholder format) round-trip through export/import unmodified. `financeiro/page.tsx` has its own separate (duplicated, see IN-01 — out of scope) `escapeField`/`FORMULA_TRIGGER_CHARS` implementation; mirrored the same restructuring there via a new local `guardFormula()` helper applied only to `processoLabel`/`clienteLabel`, not to `id`, `valorTotal`, `totalPago`, `estado`, or `dataAcordo`.

### WR-01 (fix regression): New Documentos Download link nests a `<Button>` inside an `<a>`

**Files modified:** `web/src/app/(dashboard)/documentos/columns.tsx`
**Commit:** `d91646e`
**Applied fix:** Replaced the invalid `<a><Button>...</Button></a>` nesting in `DocumentoAcoesCell` with this codebase's established `<Button asChild><a>...</a></Button>` pattern (matching `ClienteAcoesCell`'s "Ver detalhes"/"Editar" links in `clientes/columns.tsx`). Only a single `<a>` element now renders for the Download control — no nested interactive content, no duplicate keyboard tab-stop. Per the codebase's own `asChild` convention (verified against every existing `Button asChild` call site), `type="button"` is omitted since it has no meaning on the underlying anchor element that `Slot` renders.

## Skipped Issues

None — both in-scope findings (CR-01, WR-01) were fixed. Info findings IN-01 (duplicated `FORMULA_TRIGGER_CHARS` logic between `lib/csv.ts` and `financeiro/page.tsx`) and IN-02 (dead fallback branch in `DataTableViewOptions`) were intentionally left untouched — they are out of scope per `fix_scope: critical_warning`.

---

_Fixed: 2026-07-16T13:30:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
