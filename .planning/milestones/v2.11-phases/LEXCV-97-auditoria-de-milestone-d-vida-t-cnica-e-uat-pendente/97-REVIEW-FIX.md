---
phase: 97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente
source: [97-REVIEW.md]
fixed: 2026-07-14T21:45:00.000Z
---

# Phase 97 Code Review Fix Report

Applied directly by the orchestrator (not a spawned fixer agent — both findings were small, well-scoped, single-file mechanical edits).

## WR-01: FIXED

**File:** `web/src/lib/cliente-documento-tipo.ts`
**Fix:** `getDocumentoTipoLabel` now guards the lookup with `Object.prototype.hasOwnProperty.call(DOCUMENTO_TIPO_LABELS, value)` before indexing, instead of relying on `DOCUMENTO_TIPO_LABELS[value] ?? value`. This closes the gap where a stored value matching an inherited `Object.prototype` property name (`constructor`, `toString`, `hasOwnProperty`, `__proto__`, etc.) returned the inherited function/object instead of falling back to the raw string, which would have thrown at React render time.

**Verification:**
- Re-ran the review's own repro (`node -e`) for `constructor`/`toString`/`hasOwnProperty`/`__proto__`/`LEGACY_UNKNOWN`/`PASSAPORTE` — all 6 now behave correctly (prototype-shadowing keys pass through verbatim as strings; real enum values still translate).
- `pnpm exec tsc --noEmit` — same 3 pre-existing, unrelated `vitest`-resolution errors as before the fix, nothing new.

## IN-01: FIXED

**File:** `backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java`
**Fix:** `nif_naoNumerico_produzViolacaoPattern` now asserts via `violations.stream().anyMatch(...)` on the exact message string, matching its three sibling tests, instead of `assertEquals(1, violations.size())` + direct iterator indexing. Removed the now-unused `assertEquals` static import.

**Verification:** `mvn -q -DskipITs test -Dtest=ClienteNifValidationTest` — 4/4 passing, exit 0.

## WR-02: NOT FIXED (documented, out of scope)

**File:** `web/src/lib/cliente-documento-tipo.test.ts` (and 3 other pre-existing `*.test.ts` files repo-wide)
**Reason:** No test runner (`vitest`/`jest`) is installed anywhere in `web/` — this is a pre-existing, repo-wide gap predating this phase (documented since Phase 74, reconfirmed in Phase 96), not something 97-02 introduced. Installing a test runner is a meaningfully larger, cross-cutting change (new dependency, CI wiring, `package.json` script) than this phase's narrow AUD-03 label-translation scope, and 97-02's own threat model explicitly excluded new package installs. Already tracked in `.planning/STATE.md` as accepted technical debt; not re-opened here to avoid re-litigating an already-made scope decision mid-review-fix.
