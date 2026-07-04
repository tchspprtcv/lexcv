---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
verified: 2026-07-04T00:00:00Z
status: passed
score: 10/10 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 9/10
  gaps_closed:
    - "Backend ResourceController.updateCliente (backend/src/main/java/com/lexcv/controllers/ResourceController.java, lines 267-277) now computes `documentoTipoUnchanged = Objects.equals(cliente.getDocumentoTipo(), payload.getDocumentoTipo()) && Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero())` against the entity already fetched at line 262, and guards the isDocumentoTipoValidoParaTipo call with `!documentoTipoUnchanged &&`. Independently re-read the committed source (not just the SUMMARY): the exact code matches the plan's specified behavior byte-for-byte. `createCliente` (lines 218-247) and the private validator's body (lines 315-326) are confirmed untouched — grep for `documentoTipoUnchanged` returns exactly 2 hits, both inside updateCliente (lines 271, 274), none in createCliente. `mvn -q -DskipTests compile` re-executed in this verification pass, exit 0. git log confirms the isolated fix commit `be4f788` (fix(74): CR-02 tolerate unchanged legacy documento_tipo on cliente update), a single-file 9-line diff. Combined with the already-verified frontend fix from 74-04 (buildClienteFormSchema(allowedLegacyDocumentoTipo) + editar/page.tsx's useMemo-wired resolver, re-confirmed unchanged in this pass), the full end-to-end path now works: the frontend resolver lets a legacy value through client-side, onSubmit sends it verbatim in the PUT payload (documentoTipo/documentoNumero, camelCase — confirmed matching Cliente.java's Jackson field names, no @JsonProperty override), and the backend now exempts it from the per-tipo membership check because it is unchanged from the stored entity. Only a genuinely changed documentoTipo or documentoNumero re-triggers full validation on the backend, matching 74-CONTEXT.md line 26 exactly."
  gaps_remaining: []
  regressions: []
---

# Phase 74: Enum documento_tipo (BI/NIF) + Restrição por Tipo Verification Report

**Phase Goal:** O tipo de documento de identificação do cliente reflete corretamente as opções válidas por tipo de cliente, em backend e frontend
**Verified:** 2026-07-04T00:00:00Z
**Status:** passed
**Re-verification:** Yes — second re-verification, after 74-05 gap-closure plan (backend half of Truth #10)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | DocumentoTipo enum contains BI and no longer contains NIF | VERIFIED (regression) | `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` = `{BI, CNI, PASSAPORTE, REG_COMERCIAL}`, unchanged since prior verification. |
| 2 | Creating a cliente with an invalid tipo/documento_tipo combination is rejected with HTTP 400 + Portuguese message | VERIFIED (regression) | `ResourceController.java:220-224` (createCliente), byte-for-byte unchanged; confirmed no `documentoTipoUnchanged` reference anywhere in this method. |
| 3 | Updating a cliente with an invalid tipo/documento_tipo combination (a genuinely NEW/changed invalid value, not a legacy unchanged value) is rejected with HTTP 400 | VERIFIED | `ResourceController.java:271-277` — `documentoTipoUnchanged` is `false` whenever either field differs from the stored entity, which re-triggers `isDocumentoTipoValidoParaTipo` unconditionally for any real change. Traced in 74-05-SUMMARY.md (variant 1: documentoTipo changed; variant 2: documentoNumero changed while documentoTipo stays the same) and independently re-verified by reading the committed code. |
| 4 | Standalone SQL script exists to null legacy NIF rows before enum change deploys | VERIFIED (regression) | `backend/migrations/74-cleanup-nif-documento-tipo.sql` unchanged. |
| 5 | DocumentoTipo TypeScript type contains BI, not NIF | VERIFIED (regression) | `web/src/types/clientes.ts:1` unchanged. |
| 6 | getDocumentoTipoOptions('PARTICULAR') / ('EMPRESA') return correct per-tipo sets; toDocumentoTipo rejects out-of-set values | VERIFIED (regression) | `web/src/lib/cliente-documento-tipo.ts` unchanged. |
| 7 | Creating a Particular/Empresa cliente shows the correctly filtered dropdown | VERIFIED (regression) | `novo/page.tsx:242` / `editar/page.tsx:374`, unchanged; `novo/page.tsx` still imports/uses the static `clienteFormSchema` (grep confirmed, lines 28/46), no `buildClienteFormSchema` reference — create path untouched by any legacy-tolerance logic. |
| 8 | Zod rejects a documento_tipo not allowed for the selected tipo (new/changed submissions) | VERIFIED (regression) | `web/src/schemas/clientes.ts:78-88`, unchanged since 74-04; membership check still guarded by `data.documento_tipo !== allowedLegacyDocumentoTipo`. |
| 9 | Switching Particular<->Empresa clears documento_tipo/documento_numero when the selection becomes invalid | VERIFIED (regression) | `confirmTipoChange` in both pages unchanged. |
| 10 | Editing a cliente whose documento_tipo is a legacy/invalid value for its tipo can actually be saved end-to-end (per the banner's stated escape hatch, "guarde sem alterar este campo para manter o valor legado") | VERIFIED | **Both halves now closed and consistent.** Frontend (74-04, re-confirmed unchanged): `buildClienteFormSchema(legacyDocumentoTipo ?? undefined)` wired via `useMemo` into the resolver (`editar/page.tsx:68-74`); `onSubmit` sends the legacy value verbatim (`editar/page.tsx:206-209`) as `documentoTipo`/`documentoNumero` (camelCase — matches `Cliente.java` field names exactly, no `@JsonProperty` remap, confirmed by direct read of both files). Backend (74-05, newly verified): `updateCliente` (`ResourceController.java:271-277`) computes `documentoTipoUnchanged` via `Objects.equals` against the entity fetched at line 262 for BOTH `documentoTipo` and `documentoNumero`, and skips `isDocumentoTipoValidoParaTipo` only when both are unchanged. A PUT resending the stored legacy value unchanged now reaches `clienteRepository.save(cliente)` and returns HTTP 200 — traced step-by-step against the actual committed code, not just the SUMMARY narrative. |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | Enum {BI, CNI, PASSAPORTE, REG_COMERCIAL}, no NIF | VERIFIED | Unchanged, compiles. |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | tipo x documentoTipo validation in createCliente (unconditional) + updateCliente (tolerant of unchanged legacy values per 74-CONTEXT.md) | VERIFIED | Read in full for the relevant range (lines 218-326). `createCliente` unconditional and untouched. `updateCliente` now contains the `documentoTipoUnchanged` guard exactly as specified in 74-05-PLAN.md, confirmed via direct source read (not SUMMARY trust) and a fresh `mvn -q -DskipTests compile` (exit 0) executed in this verification pass. |
| `backend/migrations/74-cleanup-nif-documento-tipo.sql` | Defensive UPDATE nulling legacy NIF rows | VERIFIED (regression) | Unchanged. |
| `web/src/types/clientes.ts` | DocumentoTipo union with BI, without NIF | VERIFIED (regression) | Unchanged. |
| `web/src/lib/cliente-documento-tipo.ts` | getDocumentoTipoOptions + toDocumentoTipo single source of truth | VERIFIED (regression) | Unchanged. |
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | Filtered dropdown, static `clienteFormSchema` (no legacy exemption for new records) | VERIFIED (regression) | Confirmed untouched — still imports/uses `clienteFormSchema` (lines 28/46), no `buildClienteFormSchema` reference. |
| `web/src/schemas/clientes.ts` | `buildClienteFormSchema(allowedLegacyDocumentoTipo?)` factory + `clienteFormSchema = buildClienteFormSchema()` static export | VERIFIED (regression) | Read in full; unchanged since 74-04. |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | Resolver rebuilt via `useMemo(() => buildClienteFormSchema(legacyDocumentoTipo ?? undefined), [legacyDocumentoTipo])` | VERIFIED (regression) | Read in full (lines 1-230); unchanged since 74-04. `onSubmit` legacy carve-out (lines 206-209) confirmed still present, sending the legacy value verbatim. |
| `web/src/schemas/clientes.legacy-documento-tipo.test.ts` | Regression test with cases A/B/C/D | VERIFIED (regression) | File confirmed present at expected path. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| ResourceController.createCliente | DocumentoTipo | `isDocumentoTipoValidoParaTipo` unconditional call | WIRED (regression) | Lines 220-224, unchanged, no legacy exemption — correct for create. |
| ResourceController.updateCliente | cliente.getDocumentoTipo()/getDocumentoNumero() | `Objects.equals` comparison against the fetched entity, gating the validation call | WIRED (new) | Lines 271-274, confirmed by direct source read. Comparison uses the tenant-scoped entity fetched and guarded at lines 262-265 (`cliente.getTenantId().equals(getTenantId())`), so no new cross-tenant surface — consistent with 74-05-PLAN's threat model T-74-05-02. |
| editar/page.tsx | schemas/clientes.ts | `buildClienteFormSchema(legacyDocumentoTipo ?? undefined)` via `useMemo`, passed to `zodResolver` | WIRED (regression) | Lines 22, 68-74. |
| editar/page.tsx onSubmit payload | ResourceController.updateCliente | PUT /api/v1/clientes/{id}, JSON body via `apiFetch` (`documentoTipo`/`documentoNumero`, camelCase, no transformation) | WIRED (gap closed) | The payload the client sends for an unchanged legacy value now reaches a backend path that accepts it: `documentoTipoUnchanged` evaluates `true`, `isDocumentoTipoValidoParaTipo` is skipped, `clienteRepository.save(cliente)` executes, HTTP 200 returned. Field-name alignment independently re-checked: `Cliente.java` declares `private DocumentoTipo documentoTipo;` / `private String documentoNumero;` with no `@JsonProperty` override, so Jackson deserializes the frontend's camelCase keys directly — no silent mismatch. |
| novo/page.tsx | schemas/clientes.ts | `clienteFormSchema` (static, unparameterized) | WIRED (regression) | Unchanged — create path has no legacy exemption, matching intent. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| editar/page.tsx `legacyDocumentoTipo` state | Set from `cliente.data` in a `useEffect` comparing the loaded `documento_tipo` against `getDocumentoTipoOptions(loadedTipo)` | `useCliente(id)` React Query hook → GET `/clientes/{id}` | Yes — driven by the real fetched record | FLOWING (regression, unchanged) |
| onSubmit `documentoTipo` payload value | `values.documento_tipo === legacyDocumentoTipo ? values.documento_tipo : toDocumentoTipo(...)` | Form state populated by `form.reset(...)` from `cliente.data`, submitted verbatim through `useUpdateCliente` → `apiFetch` PUT | Yes | FLOWING, and now the destination (backend) accepts it — the previously-reported disconnect is closed. |
| `ResourceController.updateCliente` `documentoTipoUnchanged` boolean | `Objects.equals(cliente.getDocumentoTipo(), payload.getDocumentoTipo()) && Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero())` | `cliente` = tenant-scoped entity fetched via `clienteRepository.findById(id)` at line 262; `payload` = the deserialized PUT body | Yes — both operands are real, one from the DB, one from the live request body, not stubs/hardcoded values | FLOWING (new, verified) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with the new exemption | `cd backend && mvn -q -DskipTests compile` | Exit 0, no output (re-executed fresh in this verification pass, not reused from SUMMARY) | PASS |
| `documentoTipoUnchanged` guard exists exactly once in updateCliente, nowhere in createCliente | `grep -n "documentoTipoUnchanged" ResourceController.java` | 2 hits, both at lines 271 and 274, both inside `updateCliente` (218-247 is createCliente's range, confirmed clean) | PASS |
| `be4f788` is an isolated, single-file fix | `git show --stat be4f788` | `.../ResourceController.java \| 9 ++++++++-`, 1 file changed, 8 insertions, 1 deletion | PASS |
| `createCliente`'s validation line unchanged | Direct read of lines 220-224 | Byte-for-byte identical to prior verification pass | PASS |
| No debt/anti-pattern markers in the modified file | `grep -n "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER" ResourceController.java` | No matches | PASS |
| novo/page.tsx unaffected (still uses static `clienteFormSchema`) | `grep -n "clienteFormSchema" novo/page.tsx` | Lines 28, 46 present, `buildClienteFormSchema` absent | PASS |
| Frontend/backend field-name alignment for the PUT payload | Direct read of `Cliente.java` (no `@JsonProperty` on `documentoTipo`/`documentoNumero`) vs. `editar/page.tsx onSubmit` payload keys | Match — camelCase keys sent by frontend land on the exact Jackson-mapped fields the backend compares | PASS |

### Probe Execution

No project-defined probe scripts found for this phase (`scripts/*/tests/probe-*.sh` search returned nothing; no probes declared in any 74-*-PLAN.md). SKIPPED — no runnable probes exist for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CLI-20 | 74-01, 74-02 | Enum documento_tipo passa a incluir BI | SATISFIED | Unchanged since prior verification. |
| CLI-21 | 74-01, 74-02 | Valor NIF é removido do enum documento_tipo (corte limpo) | SATISFIED | Unchanged since prior verification. |
| CLI-22 | 74-01, 74-02, 74-03 | Para Particular, tipo de documento oferece apenas CNI/BI/Passaporte | SATISFIED | Unchanged since prior verification. |
| CLI-23 | 74-01, 74-02, 74-03 | Para Empresa, tipo de documento oferece apenas Registo Comercial | SATISFIED | Unchanged since prior verification. |
| CLI-24 | 74-01, 74-03, 74-04, 74-05 | Restrição por tipo de cliente validada em frontend (dropdown filtrado) e backend (rejeita combinações inválidas) | SATISFIED | Now fully closed on both layers. New/changed invalid combinations are rejected on both frontend (Zod) and backend (`isDocumentoTipoValidoParaTipo`) for both create and update. The legacy-tolerance carve-out required by 74-CONTEXT.md line 26 (pre-existing invalid combinations tolerated until actively edited) is now implemented end-to-end: frontend Zod exemption (74-04) + backend `documentoTipoUnchanged` exemption (74-05) agree on when to tolerate an unchanged legacy value, and independently re-verified in this pass by reading the committed source of both layers, not by trusting either SUMMARY.md. |

REQUIREMENTS.md's tracking table still shows all 5 IDs' checkboxes unchecked (`- [ ]`) and status "Pending" — this is a doc-maintenance gap in that file, not evidence of implementation status, and does not affect this verification's findings (all 5 IDs independently checked against source in this and the prior verification passes).

No orphaned requirements — all 5 IDs mapped to Phase 74 in REQUIREMENTS.md are declared across the plans' frontmatter (`requirements:` fields checked directly in 74-01 through 74-05).

### Anti-Patterns Found

None. Scanned `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (the sole file modified by 74-05) for TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers — zero matches. The `be4f788` diff is a minimal, scoped 8-insertion/1-deletion change with an explanatory code comment, no stub patterns, no empty-implementation red flags.

### Human Verification Required

None. The prior gap (backend rejecting an unchanged legacy value) and its closure are both deterministically demonstrable via code inspection: the exemption's boolean logic, the entity-fetch/tenant-scoping guard it depends on, and the field-name alignment between frontend payload and backend model are all directly traceable in source, not UX/visual judgment calls. No live-server/curl execution was performed by 74-05 (environment had no reachable backend/PostgreSQL, documented in 74-05-SUMMARY.md), but the static branch-trace is sound and was independently re-derived (not merely re-read) against the actual committed code in this verification pass — reaching the same conclusion via direct code reading, which is sufficient for this deterministic, non-UX logic change. No human action needed to close this out; recommended (non-blocking) future work: add a `@SpringBootTest`/`@WebMvcTest` regression test once `backend/src/test` exists and CI has a live PostgreSQL (noted in 74-05-SUMMARY.md's "Next Phase Readiness" — this is a nice-to-have, not a phase-goal blocker).

### Gaps Summary

All 10 observable truths are now verified. The phase goal — "O tipo de documento de identificação do cliente reflete corretamente as opções válidas por tipo de cliente, em backend e frontend" — is achieved:

- The enum/type cutover (BI added, NIF removed) is solid on both layers (Truths 1, 5).
- Fresh/changed submissions are rejected server-side and client-side for invalid tipo/documento_tipo combinations on both create and update (Truths 2, 3, 8).
- Dropdowns are correctly filtered per tipo on both create and edit pages, with auto-clear on tipo switch (Truths 6, 7, 9).
- The SQL cleanup script for legacy NIF rows exists (Truth 4).
- **Truth #10, the last remaining gap across two prior verification passes, is now closed end-to-end.** 74-04 closed the frontend half (the Zod resolver's dead legacy carve-out was made reachable). This pass confirms 74-05 closed the backend half: `updateCliente` now distinguishes "value unchanged from what's stored" from "newly submitted value" via a direct `Objects.equals` comparison against the tenant-scoped entity already fetched in the handler — exactly the mechanism 74-CONTEXT.md's locked decision (line 26) implies and that the frontend fix assumed existed. Both halves were independently re-read from the actual committed source in this verification pass (not inferred from either SUMMARY.md), the backend re-compiled clean, and the field-name contract between the two layers (camelCase `documentoTipo`/`documentoNumero`, no Jackson remap) was re-confirmed to align.

No deferred items, no regressions detected in any of the 9 previously-verified truths.

---

*Verified: 2026-07-04T00:00:00Z*
*Verifier: Claude (gsd-verifier)*
