---
phase: LEXCV-104-padr-o-datatable-partilhado
fixed_at: 2026-07-16T14:05:00Z
review_path: .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
iteration: 3
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-104: Code Review Fix Report

**Fixed at:** 2026-07-16T14:05:00Z
**Source review:** .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
**Iteration:** 3 (final — capped auto fix/re-review loop)

**Summary:**
- Findings in scope: 2 (1 critical — CR-01, 1 warning — the `nome`-import-corruption warning; Info findings IN-01/IN-02/IN-03 excluded per `fix_scope: critical_warning`)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### CR-01: CSV/formula-injection still reachable via unguarded `telefone`/`email` fields in Clientes export

**Files modified:** `web/src/app/(dashboard)/clientes/page.tsx`
**Commit:** `cf06297`
**Applied fix:** Extended `guardCsvFormula()` in `onExportCsv`'s `rows` array to `c.telefone` and `c.email`, in addition to the already-guarded `c.nome`. `c.nif` remains unguarded, since it is confirmed genuinely format-locked to `^\d{9}$` both client-side (`schemas/clientes.ts`) and server-side (`Cliente.java` `@Pattern`), so it can never start with a formula-trigger character. `telefone` has no format validation anywhere in the stack and `email`'s client-side Zod `.email()` check still permits a leading `+`/`-`, so both are exactly as attacker-controlled as `nome` and needed the same guard.

Also checked `web/src/app/(dashboard)/financeiro/page.tsx`'s CSV export per the finding's instruction: every field beyond the already-guarded `processoLabel`/`clienteLabel` (`id`, `valorTotal`, `totalPago`, `estado`, `dataAcordo`) is either a `number` coerced via `String()` (cannot contain formula syntax beyond a leading numeric sign, which Excel does not treat as a formula trigger) or a closed enum/computed string. No change was needed on the Financeiro side — this matches the review's own verification.

### Warning: anti-formula prefix asymmetric — `guardCsvFormula()` prefix never stripped back out on Clientes CSV re-import

**Files modified:** `web/src/lib/csv.ts`, `web/src/app/(dashboard)/clientes/page.tsx`
**Commit:** `3412f6d`
**Applied fix:** Added `stripCsvFormulaGuard()` to `lib/csv.ts` — the exact inverse of `guardCsvFormula()`, stripping a single leading `'` only when it immediately precedes one of the six formula-trigger characters (`=`, `+`, `-`, `@`, tab, CR), so an apostrophe that is genuinely part of the original value (e.g. `O'Brien`) is left untouched. Applied it in `onImportFile` to the `nome` field, exactly as the review's suggested fix specified.

**Scope adaptation (documented per fix_strategy):** the review's suggested fix only mentioned stripping `nome` on import, because at review time `telefone`/`email` were not guarded on export. Since the CR-01 fix committed immediately before this one (`cf06297`) now also guards `telefone` and `email`, stripping only `nome` would have reintroduced exactly the round-trip corruption bug this fix exists to close — for the common case of a phone number starting with `+`, re-importing this app's own CSV export would silently and permanently prepend a stray apostrophe. Extended `stripCsvFormulaGuard()` to `telefone` and `email` as well as `nome` in `onImportFile`, so the export-guard/import-strip pair stays symmetric across every field that CR-01 now guards.

## Skipped Issues

None — both in-scope findings (CR-01, the Warning) were fixed. Info findings IN-01 (duplicated `FORMULA_TRIGGER_CHARS` between `lib/csv.ts` and `financeiro/page.tsx`), IN-02 (dead fallback branch in `DataTableViewOptions`), and IN-03 (missing `encodeURIComponent` on Documentos mobile card download link) were intentionally left untouched — out of scope per `fix_scope: critical_warning`. These remain open for manual follow-up if desired; none are security- or correctness-blocking.

## Verification Notes

- Tier 1 (re-read): performed for both commits; fix text present, surrounding code intact in both `web/src/lib/csv.ts` and `web/src/app/(dashboard)/clientes/page.tsx`.
- Tier 2 (syntax check): ran `npx tsc --noEmit -p tsconfig.json` from `web/` after each edit (via a temporary `node_modules` link into the isolated worktree, removed before each commit). No type errors were reported against either modified file, before or after each edit. The only pre-existing errors in the project (`Cannot find module 'vitest'` in three `*.test.ts` files) are unrelated to these changes and were present before this fix pass.
- Both fixes are self-contained, deterministic transformations (string-prefix guard / inverse strip) rather than conditional business logic, so neither was flagged as requiring additional human logic verification beyond the syntax check performed.

---

_Fixed: 2026-07-16T14:05:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
