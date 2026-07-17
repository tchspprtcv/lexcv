---
phase: LEXCV-108-m-dulo-pareceres
fixed_at: 2026-07-17T11:43:59Z
review_path: .planning/phases/LEXCV-108-m-dulo-pareceres/108-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase LEXCV-108: Code Review Fix Report

**Fixed at:** 2026-07-17T11:43:59Z
**Source review:** .planning/phases/LEXCV-108-m-dulo-pareceres/108-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (1 critical + 7 warning; Info findings IN-01 through IN-04 explicitly excluded per fix scope)
- Fixed: 8
- Skipped: 0

## Fixed Issues

### CR-01: "Entregar Parecer" default final version relies on unsorted/unguaranteed array order

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** c871554
**Applied fix:** Changed the `EntregarParecerDialog` call site to pass the already-sorted `sortedVersoes` (descending by `numeroVersao`) instead of the raw `versoes.data`. Updated the dialog's internal `defaultVersaoId` derivation to index `[0]` (now the newest, since the array is sorted descending) instead of `[versoes.length - 1]`. Verified the `<option>` list (which iterates the same `versoes` prop) now renders in the same descending, newest-first order as `sortedVersoes` — no separate change needed there since it simply maps over the prop.

### WR-01: Advanced-search query fires unconditionally, even before the panel is opened

**Files modified:** `web/src/hooks/use-pareceres.ts`, `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** 4c48a41
**Applied fix:** Extended `usePesquisarPareceres` to accept a second `options: { enabled?: boolean }` argument (defaulting to enabled, preserving all other call sites/behavior), and updated the one call site in `pareceres/page.tsx` to pass `{ enabled: pesquisaSubmitted }` so the search query is gated on the user actually having submitted a search.

### WR-02: "Advogado" pickers don't exclude inactive users

**Files modified:** `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/app/(dashboard)/pareceres/nova/page.tsx`
**Commit:** 70c0a7b
**Applied fix:** Added `&& u.ativo !== false` to the `advogados` filter predicate in both files, matching the existing project convention established in `processos/[id]/page.tsx`.

### WR-03: Clearing `processoId` via `setValue(..., undefined)` on a native select

**Files modified:** `web/src/app/(dashboard)/pareceres/nova/page.tsx`
**Commit:** f83c711
**Applied fix:** Changed `form.setValue("processoId", undefined)` to `form.setValue("processoId", "")`, matching the placeholder option's `value=""` and the schema's string input type (`optionalTrimmedString` transforms `""` to `undefined` on output, so no schema/type mismatch was introduced).

### WR-04: Version-history accordion's default open item does not update after "Entregar"

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** 16edcb5
**Applied fix:** Added `key={defaultOpenVersaoId}` to the `Accordion` element so it force-remounts (and re-seeds its uncontrolled `defaultValue`) whenever the intended default-open version changes, e.g. after "Entregar Parecer" flips `isConcluido` and `defaultOpenVersaoId` recomputes to `versaoFinalId`.

### WR-05: Version-upload failures lose the backend's validation message

**Files modified:** `web/src/hooks/use-pareceres.ts`
**Commit:** 9baf465
**Applied fix:** Rewrote the `xhr.onload` failure branch in `useCreateParecerVersao` to parse `xhr.responseText` as JSON and reject with `json?.message || json?.error`, falling back to the generic `API {status}` message only if the body isn't valid JSON — matching `apiFetch`'s error-surfacing behavior used everywhere else in the module.

### WR-06: `formatDate` mis-displays date-only `prazo` values by one day in Cape Verde's timezone

**Files modified:** `web/src/lib/pareceres.ts`, `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** 4598c80
**Applied fix:** Changed `formatDate` to explicitly parse the `YYYY-MM-DD` components via regex and construct a local `Date(year, monthIndex, day)` instead of handing the raw date-only string to `new Date()` (which parses as UTC midnight per the ECMAScript Date Time String Format, causing a one-day-back display in Cape Verde's UTC-01:00 timezone). Applied identically to both the shared helper in `lib/pareceres.ts` and its verbatim local duplicate in `pareceres/[id]/page.tsx` (the duplication itself is IN-01, out of scope — only the shared bug was fixed in both copies).

### WR-07: Raw `<label>` elements without `htmlFor`/`id` pairing in "Pesquisa Avançada"

**Files modified:** `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** 1d319c0
**Applied fix:** Added `id="pesquisa-texto"` / `htmlFor="pesquisa-texto"` to the free-text search label/input pair, and `id="pesquisa-data-inicio"` / `id="pesquisa-data-fim"` with matching `htmlFor` to the two date inputs in the "Período" block, matching the `Label`/`id` pairing convention already used in `nova/page.tsx`.

## Skipped Issues

None — all 8 in-scope findings (CR-01, WR-01 through WR-07) were fixed. Info findings IN-01 through IN-04 were intentionally left undone per the requested fix scope (critical + warning only).

## Verification

- **Tier 1 (mandatory):** Every modified file was re-read after each edit to confirm the fix text was present and surrounding code intact.
- **Tier 2 (preferred):** Ran `npx tsc --noEmit -p tsconfig.json` (project-wide, to correctly resolve `@/` path aliases and JSX) after each fix. No new TypeScript errors were introduced by any fix; the only errors present throughout (`Cannot find module 'vitest'` in 3 unrelated `*.test.ts` files) are pre-existing and unrelated to the reviewed files.
- **Post-fix full build/lint (requested explicitly for this task):**
  - `pnpm build` — succeeded cleanly (Turbopack production build, all 24 routes compiled, TypeScript pass finished with no errors).
  - `pnpm lint` — project-wide run reported 6 errors / 18 warnings, all in files unrelated to this phase's changes (`dashboard-shell.tsx`, `clientes/[id]/page.tsx`, `documentos/novo/page.tsx`, `processos/[id]/*`, `profile/user-profile-form.tsx`, `agenda/novo/page.tsx`, `clientes/novo/page.tsx`, `clientes/[id]/ficha/page.tsx`). A scoped lint run against only the files touched by this fix pass (`pareceres/**/*.tsx`, `hooks/use-pareceres.ts`, `lib/pareceres.ts`) reported exactly one warning — `react-hooks/incompatible-library` at `pareceres/nova/page.tsx:67:26` on `form.watch("clienteId")` — which is pre-existing (React Compiler flagging react-hook-form's `watch()` API generally, unrelated to and at a different line than any WR-03 edit) and not introduced by this fix pass.

No new build or lint errors were introduced by any of the 8 applied fixes.

---

_Fixed: 2026-07-17T11:43:59Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
