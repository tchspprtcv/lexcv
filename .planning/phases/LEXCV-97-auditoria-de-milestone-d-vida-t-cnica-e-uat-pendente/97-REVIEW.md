---
phase: 97-auditoria-de-milestone
reviewed: 2026-07-14T21:15:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java
  - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/lib/cliente-documento-tipo.test.ts
  - web/src/lib/cliente-documento-tipo.ts
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase 97 (Plan 02): Code Review Report

**Reviewed:** 2026-07-14T21:15:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the 5 files touched by 97-02 (AUD-03 gap closure): the new `getDocumentoTipoLabel` helper
and its label map in `cliente-documento-tipo.ts`, its two new call sites in the client detail page
and printable ficha, the accompanying vitest cases, and the new standalone-`Validator`
`ClienteNifValidationTest` for `Cliente.nif`.

Targeted checks per the review brief:

- **DocumentoTipo enum coverage:** `DocumentoTipo` (backend) has exactly 4 values — `BI`, `CNI`,
  `PASSAPORTE`, `REG_COMERCIAL` — and `DOCUMENTO_TIPO_LABELS` is typed `Record<DocumentoTipo, string>`,
  so TypeScript itself guarantees the map cannot omit a real enum member. No gap here.
- **Null/undefined/empty handling:** `getDocumentoTipoLabel(null|undefined|"")` correctly returns
  `undefined`, preserving both call sites' existing `"—"`/`fmt()`-blank fallback. Confirmed correct.
- **Unknown/legacy value handling:** mostly correct (verbatim passthrough, matches the documented
  contract and the "LEGACY_UNKNOWN" test case) — **except** for a real gap found in the lookup
  implementation itself, see WR-01 below: certain string values silently break the "return verbatim"
  contract because the label map is a plain JS object rather than a null-prototype map or `Map`.
- **Backend Validator faithfulness:** confirmed the standalone
  `Validation.buildDefaultValidatorFactory().getValidator()` used in the new test is a faithful
  reproduction of the real runtime path. Both `createCliente` (`ResourceController.java:236`) and
  `updateCliente` (`ResourceController.java:296`) bind `@Valid @RequestBody Cliente` directly (the
  same class under test, not a DTO), there is no custom `Validator`/`LocalValidatorFactoryBean`
  bean, `MessageSource`, or `validation.xml` anywhere in `backend/src/main` that could change
  constraint resolution, and both constraint messages under test (`"NIF é obrigatório"`, `"NIF deve
  conter exatamente 9 dígitos numéricos"`) are hardcoded literals rather than resource-bundle keys,
  so there is no message-interpolation path that could diverge between the standalone validator and
  Spring's default one. `spring-boot-starter-validation` is a normal (non-test-scoped) dependency,
  so the Hibernate Validator version is identical in both contexts. No false-positive-pass risk
  found for this specific test.
- **Test-suite executability:** the 6 new `getDocumentoTipoLabel` cases were added to
  `cliente-documento-tipo.test.ts`, but `web/package.json` has no `test` script and no
  `vitest`/`jest` anywhere in `dependencies`/`devDependencies`/`pnpm-lock.yaml` — confirmed by
  direct inspection. These tests (and the file's pre-existing tests) cannot execute via any command
  in this repo today. See WR-02.

No critical/security findings. Two warnings and one info-level nit below.

## Warnings

### WR-01: `getDocumentoTipoLabel` mis-handles inherited `Object.prototype` keys, silently violating its own "never hide/corrupt a stored value" contract

**File:** `web/src/lib/cliente-documento-tipo.ts:52-57`
**Issue:**
```ts
export function getDocumentoTipoLabel(
  value: string | null | undefined,
): string | undefined {
  if (!value) return undefined;
  return DOCUMENTO_TIPO_LABELS[value as DocumentoTipo] ?? value;
}
```
`DOCUMENTO_TIPO_LABELS` is a plain object literal, so it inherits `Object.prototype`. Indexing it
with a value that happens to match an inherited property name (`"constructor"`, `"toString"`,
`"hasOwnProperty"`, `"valueOf"`, `"isPrototypeOf"`, `"__proto__"`, etc.) does **not** return
`undefined` — it returns the inherited function/object, which is truthy, so `?? value` never falls
back to the raw string. Verified directly:

```
$ node -e '...'
constructor -> [Function: Object]
toString -> [Function: toString]
hasOwnProperty -> [Function: hasOwnProperty]
__proto__ -> [Object: null prototype] {}
LEGACY_UNKNOWN -> LEGACY_UNKNOWN   (control case, correct)
```

The function's whole purpose (per its own JSDoc: "nunca esconder um valor real guardado na base de
dados") and its return-type contract (`string | undefined`) are both broken for this narrow input
set: instead of the raw string being rendered, a `Function`/object leaks out of a helper typed as
returning `string | undefined`. If such a value ever reached either render site, React would throw
at render time ("Functions/Objects are not valid as a React child") rather than degrade gracefully
to the raw value as designed.

Exploitability today is low — `documento_tipo` is written through `isDocumentoTipoValidoParaTipo`-
gated create/update endpoints that only accept the 4 real enum values, so this requires
already-corrupted/directly-inserted DB data — but the entire point of the verbatim-passthrough
branch is to defensively handle exactly that class of "unexpected stored value," and it does not.

**Fix:** Guard with `hasOwnProperty` (or build the map without a prototype chain):
```ts
export function getDocumentoTipoLabel(
  value: string | null | undefined,
): string | undefined {
  if (!value) return undefined;
  return Object.prototype.hasOwnProperty.call(DOCUMENTO_TIPO_LABELS, value)
    ? DOCUMENTO_TIPO_LABELS[value as DocumentoTipo]
    : value;
}
```
or declare `DOCUMENTO_TIPO_LABELS` via a `Map<string, string>` instead of a `Record`.

### WR-02: New (and all pre-existing) `cliente-documento-tipo.test.ts` cases are not executable by any command in this repo — AUD-03's frontend test-coverage claim is unverifiable

**File:** `web/src/lib/cliente-documento-tipo.test.ts` (whole file); `web/package.json`
**Issue:** The plan's own acceptance criteria required `pnpm test -- cliente-documento-tipo` to
pass with the 6 new cases green. `web/package.json` has no `"test"` script, and neither `vitest`
nor `jest` appears anywhere in `dependencies`, `devDependencies`, or `pnpm-lock.yaml` (confirmed by
direct inspection — this is not specific to the new cases, it's true of the whole file and every
other `*.test.ts` in `web/`). The 6 new cases read correctly against the implementation on manual
trace (all 6 would pass if run), but "would pass if a runner existed" is not the same as regression
coverage: nothing in CI or any `pnpm` script actually executes this file today, so a future
regression to `getDocumentoTipoLabel` (e.g. reintroducing WR-01, or breaking the label map) would
not be caught by any automated gate. The SUMMARY documents this as a known, accepted pre-existing
gap and works around it with an uncommitted scratch verification script, but the phase's `done`
condition ("tests + typecheck green") and AUD-03 closure claim rest on a test suite that cannot
actually run.
**Fix:** Either install a minimal test runner (e.g. `vitest` + a `"test": "vitest run"` script) so
this and the other 3 already-orphaned `*.test.ts` files in `web/` become real regression gates, or
— if that's genuinely out of scope for this phase — make the gap explicit rather than silent: add a
top-of-file comment noting these are non-executable specification-only cases, and track "add a
frontend test runner" as its own tracked debt item rather than repeatedly deferring it phase after
phase under a no-new-installs threat model that was never actually about test tooling.

## Info

### IN-01: Inconsistent assertion style in `ClienteNifValidationTest` makes one test case more brittle than its siblings

**File:** `backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java:91-93`
**Issue:** Three of the four test methods assert `violations.stream().anyMatch(...message...)`,
tolerant of any additional future constraints on `nif`. `nif_naoNumerico_produzViolacaoPattern`
instead asserts `assertEquals(1, violations.size())` before indexing
`violations.iterator().next()`. This is functionally correct today, but is the only method in the
file that would break (for an unrelated reason — extra violation count, not a message change) if a
future contributor added a second constraint to `nif` (e.g. a defensive `@Size`), even though the
`@Pattern` behavior under test would still be correct.
**Fix:** For consistency with the other three methods, prefer:
```java
assertTrue(
    violations.stream().anyMatch(v -> "NIF deve conter exatamente 9 dígitos numéricos".equals(v.getMessage())),
    "nif='12345678A' deve produzir a violação @Pattern...");
```

---

_Reviewed: 2026-07-14T21:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
