---
phase: LEXCV-60-ficha-imprimivel
reviewed: 2026-06-30T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - web/src/types/clientes.ts
  - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase LEXCV-60: Code Review Report

**Reviewed:** 2026-06-30
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Reviewed the printable ficha feature: the new `/clientes/[id]/ficha` page, its two entry points (detail page button, listing row icon), and the `Cliente` type extension. The implementation deviated from the literal plan text (using existing structured sub-resources/arrays instead of inventing flat string duplicate fields) — this deviation is correct and well-documented in the SUMMARY, and avoids introducing parallel/inconsistent data shapes.

No critical security issues found. The permission guard matches the established `clientes:view` pattern exactly, `target="_blank"` links correctly include `rel="noopener noreferrer"`, and the `dangerouslySetInnerHTML` usage is a static CSS string literal with no user-controlled interpolation — no XSS risk. Findings below are mostly correctness/robustness gaps around money formatting, the `dados_tipo` type guard, and minor consistency issues.

## Warnings

### WR-01: Honorários total rendered as raw number instead of currency

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:175-176, 226`
**Issue:** `honorariosTotal` is `cliente.honorarios_propostos?.total` (a `number`). It's passed through `fmt()`, which just calls `String(value)`. Elsewhere in the codebase (`web/src/app/(dashboard)/clientes/[id]/page.tsx:56-58`, `formatMoneyCVE`) monetary values are formatted via `toLocaleString("pt-CV", { style: "currency", currency: "CVE" })`. On the printed ficha, "Totalidade" will show a bare number (e.g. `150000`) instead of a formatted currency amount (e.g. `150 000$00`), which is inconsistent with the rest of the app and less useful on a printed/legal document where the value should be unambiguous.
**Fix:**
```tsx
function fmtCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined) return BLANK;
  return value.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}
// ...
<Field label="Totalidade" value={fmtCurrency(honorariosTotal)} />
```

### WR-02: `isDadosTipoParticular` type guard misclassifies empty/partial objects and a "0 idade" edge case is silently dropped by `fmt`

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:44-49, 158`
**Issue:** `isDadosTipoParticular` returns `true` only if `"idade" in dados || "sexo" in dados || "nacionalidade" in dados`. If the backend ever sends `dados_tipo: {}` (e.g. a partially-saved `DadosTipoEmpresa` with no keys set) the guard returns `false` even for an objectively "particular" record, silently falling back to `undefined` for all three fields — acceptable as a heuristic, but undocumented and fragile since it relies on key presence rather than the `cliente.tipo` discriminator that's already available in scope. Prefer keying off `cliente.tipo !== "EMPRESA"` (already computed as `isEmpresa` two lines below) for the read of `dados_tipo`, which is consistent with how `isEmpresa` already branches the rest of the section.
**Fix:**
```tsx
const dadosParticular = !isEmpresa && cliente.dados_tipo
  ? (cliente.dados_tipo as DadosTipoParticular)
  : undefined;
```
Move `isEmpresa` above this computation, or compute both together. This removes the duplicate, separately-maintained heuristic in `isDadosTipoParticular`.

### WR-03: `fmt()` silently coerces `idade: 0` to a printed `"0"`, but a `string | number` union allows accidental empty-string pass-through inconsistently across call sites

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:39-42`
**Issue:** Minor robustness gap: `fmt` treats `""` as blank for strings (correct), but for joined-array fields like `documentosEntregues`/`documentosATratar`/`deslocacoes` (lines 162-173) the code already manually guards `length > 0 ? ... : undefined` before calling `fmt`, while `advogadosNomes`/`administrativosNomes` instead pass `advogadosNomes || undefined` (line 215-216) — two different idioms for the same "empty string → blank line" intent within the same file. Not a bug per se, but an inconsistency that increases the chance of a future edit missing one of the two patterns and printing an empty cell instead of the underline placeholder.
**Fix:** Standardize on one idiom, e.g. always pass the raw possibly-empty string/array-join directly into `fmt`, since `fmt` already treats `""` as blank:
```tsx
<Field label="Advogados" value={fmt(advogadosNomes)} />
<Field label="Administrativos" value={fmt(administrativosNomes)} />
```
(drop the `|| undefined`, since `fmt("")` already returns `BLANK`).

## Info

### IN-01: `cliente.data` falsy-but-not-undefined state silently renders "Cliente não encontrado"

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:103-119`
**Issue:** The render branches on `isLoading`, `isError`, then `cliente.data ? <Ficha/> : <NotFound/>`. If the API ever returns a falsy-but-defined value (unlikely with the current `apiFetch<Cliente>` contract, but not type-impossible at runtime e.g. backend bug returning `null` with HTTP 200), this is handled gracefully. No actual bug, just worth noting there's no `cliente.error` surfaced in the "not found" branch even though `react-query` may have additional context. Not actionable; included for completeness.
**Fix:** None required.

### IN-02: `dataFormatada` uses `"pt-CV"` locale which has inconsistent ICU/Intl support across browsers

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:77-81`
**Issue:** `"pt-CV"` (Portuguese, Cape Verde) is not guaranteed to be a recognized `Intl.DateTimeFormat` locale tag in all browser ICU builds; unsupported locales typically fall back to the default locale or `en-US` formatting rather than throwing, so this degrades gracefully, but the date format (`day/month/year` order) could silently shift on some browsers/printers. This mirrors an existing app-wide convention (also used in `formatMoneyCVE`), so it's consistent with the rest of the codebase rather than a new defect — flagged only for awareness, not a required fix for this phase.
**Fix:** None required for this phase (pre-existing convention).

### IN-03: `Field` value prop typed as `string`, forcing all numeric/array values through `fmt()` — fine, but `fmt`'s signature accepts `number` while `Field` only accepts `string`

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:39, 132`
**Issue:** `fmt(value: string | number | null | undefined): string` returns a `string`, and `Field`'s `value: string` prop is always fed via `fmt(...)`. This is fine as implemented, but if a future contributor calls `<Field value={cliente.idade} />` directly (skipping `fmt`) TypeScript will correctly reject it — no actionable defect, just confirming the type boundary is sound. No fix needed.

### IN-04: `numero_cliente` referenced in ficha but documented as a Phase 57/59 dependency without a runtime fallback note

**File:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:188`, `web/src/types/clientes.ts:47`
**Issue:** `cliente.numero_cliente` is optional and already present in the type (added in a prior phase, not this one) — confirmed it exists at `web/src/types/clientes.ts:47`. No actual issue; included to confirm the SUMMARY's claim that this field "already existed" is accurate and the ficha page doesn't reference a non-existent field. No fix needed.

---

_Reviewed: 2026-06-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
