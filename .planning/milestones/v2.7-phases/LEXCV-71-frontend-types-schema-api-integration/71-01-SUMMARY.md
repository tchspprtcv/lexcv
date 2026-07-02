---
phase: LEXCV-71-frontend-types-schema-api-integration
plan: 01
subsystem: types
tags: [typescript, zod, react-hook-form, clientes, nif-validation]

# Dependency graph
requires:
  - phase: LEXCV-70
    provides: Flattened backend Cliente model (dados_tipo removed) and DocumentoTipo enum (NIF | CNI | PASSAPORTE | REG_COMERCIAL)
provides:
  - "Flattened Cliente/ClienteCreateRequest/ClienteUpdateRequest TS types with no dados_tipo JSON blob"
  - "Shared DocumentoTipo union type (NIF | CNI | PASSAPORTE | REG_COMERCIAL) reused across all three interfaces"
  - "Zod clienteFormSchema with mandatory nif field validated as exactly 9 numeric digits via exported nifPattern regex, unconditional for PARTICULAR and EMPRESA"
affects: [LEXCV-71-02, LEXCV-72, LEXCV-73]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Named regex constant + z.string().regex(pattern, message) for mandatory validated fields (mirrors web/src/schemas/setup.ts strongPasswordPattern)"

key-files:
  created: []
  modified:
    - web/src/types/clientes.ts
    - web/src/schemas/clientes.ts

key-decisions:
  - "Dropped DadosTipoEmpresa sub-fields (nome_comercial, sede, representante_legal, cargo) entirely rather than re-adding them as flat fields — CONTEXT.md gave explicit discretion, and CLI-06 only mandates removing dados_tipo, not preserving those Empresa sub-fields."
  - "nif made required (non-optional) on Cliente and ClienteCreateRequest to mirror how nome is already required there; kept optional on ClienteUpdateRequest for PATCH-style partial-update semantics, mirroring the existing nome? convention on that interface."
  - "Replaced the dados_tipo-based EMPRESA superRefine block (nome_comercial/representante_legal required) with nothing — those rules are dropped in this phase per CONTEXT.md; form-level Empresa validation is redesigned in Phase 72."
  - "Kept the orthogonal documento_tipo/documento_numero pairing check and the existing NIF-digit check on documento_numero untouched, since they are independent of the new dedicated nif field."

patterns-established:
  - "Regex-constant + mandatory z.string().regex() pattern for schema-level format validation (established precedent: web/src/schemas/setup.ts strongPasswordPattern; now also nifPattern in web/src/schemas/clientes.ts)."

requirements-completed: [CLI-05, CLI-06]

# Metrics
duration: 25min
completed: 2026-07-01
---

# Phase LEXCV-71 Plan 01: Flatten Client Types & Mandatory NIF Validation Summary

**Flattened `Cliente`/`ClienteCreateRequest`/`ClienteUpdateRequest` TS types (removed the `dados_tipo` JSON blob), added a shared `DocumentoTipo` union including `REG_COMERCIAL`, and made the Zod `clienteFormSchema` require a 9-digit numeric `nif` unconditionally for both PARTICULAR and EMPRESA.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-07-01T00:00:00Z (approx, executor-local)
- **Completed:** 2026-07-01
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `web/src/types/clientes.ts` no longer contains `dados_tipo`, `DadosTipoParticular`, or `DadosTipoEmpresa` — replaced by a shared `DocumentoTipo` union type used consistently across all three DTO interfaces.
- `nif` is now a required field on `Cliente` and `ClienteCreateRequest` (still optional on `ClienteUpdateRequest` for partial-update semantics).
- `web/src/schemas/clientes.ts` exports a new `nifPattern` regex (`/^\d{9}$/`) and wires `nif` to it as a mandatory field, applying uniformly to PARTICULAR and EMPRESA clients.
- The `dados_tipo`-dependent EMPRESA `superRefine` validation block was removed; the orthogonal `documento_tipo`/`documento_numero` pairing and digit-format checks were preserved unchanged.

## Task Commits

Each task was committed atomically:

1. **Task 1: Flatten client DTO types and add DocumentoTipo union** - `ebbe153` (refactor)
2. **Task 2: Remove dados_tipo API coupling from Zod schema and add mandatory 9-digit NIF** - `ddbcf21` (refactor)

**Plan metadata:** (this SUMMARY commit, following this file)

## Files Created/Modified
- `web/src/types/clientes.ts` - Removed `DadosTipoParticular`/`DadosTipoEmpresa` interfaces and all `dados_tipo` field references; added `export type DocumentoTipo = "NIF" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL"`; retyped `documento_tipo`/`documentoTipo` fields to `DocumentoTipo`; made `nif` required on `Cliente`/`ClienteCreateRequest`, kept optional on `ClienteUpdateRequest`.
- `web/src/schemas/clientes.ts` - Added exported `nifPattern` regex constant; removed the `dados_tipo` Zod object field and its EMPRESA-only `superRefine` validation block; changed `nif` from `optionalTrimmedString` to a mandatory `z.string().trim().regex(nifPattern, ...)` field.

## Decisions Made
- Empresa-specific sub-fields (`nome_comercial`, `sede`, `representante_legal`, `cargo`) were dropped entirely rather than flattened onto the interfaces, per CONTEXT.md's explicit discretion — CLI-06 only requires removing `dados_tipo`, not preserving those fields. Phase 72/73 will redesign Empresa-specific data capture if needed.
- `nif` required vs optional follows the same required/optional split already used for `nome` across `Cliente`/`ClienteCreateRequest` (required) vs `ClienteUpdateRequest` (optional, PATCH semantics).

## Deviations from Plan

None - plan executed exactly as written. (One non-deviation note: initial ad-hoc `node -e` shell verification commands run via Bash's `-e` inline flag produced false-negative failures due to backslash-escaping being consumed by the shell before reaching Node; this was a verification-tooling artifact, not a code defect. Re-running the same checks from a script file, and inspecting the raw file bytes, confirmed the actual file content exactly matches the plan's required regex/pattern strings. No source code was changed as a result — this is documented here for transparency, not as a Rule 1-3 auto-fix.)

## Issues Encountered
None - both files compile in isolation exactly as specified. `pnpm exec tsc --noEmit` on the full `web/` project reports errors, but all of them are confined to the 3 expected consumer pages (`web/src/app/(dashboard)/clientes/novo/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`) that reference the now-removed `dados_tipo`/`DadosTipoParticular`/`DadosTipoEmpresa` — this is the explicitly expected, temporary non-compiling state documented in the plan, to be resolved by the dependent Plan 71-02.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `web/src/types/clientes.ts` and `web/src/schemas/clientes.ts` are internally consistent and ready for Plan 71-02 to adapt the 3 broken consumer pages to the flattened contract.
- `DocumentoTipo` union and `nifPattern` are now available for reuse by consumer forms and the `<select>` in `novo/page.tsx`.
- Blocker: the frontend does not fully type-check (`pnpm exec tsc --noEmit`) until Plan 71-02 lands — this is expected and by design, not a regression to fix here.

---
*Phase: LEXCV-71-frontend-types-schema-api-integration*
*Completed: 2026-07-01*

## Self-Check: PASSED

- FOUND: web/src/types/clientes.ts
- FOUND: web/src/schemas/clientes.ts
- FOUND: commit ebbe153 (Task 1)
- FOUND: commit ddbcf21 (Task 2)
