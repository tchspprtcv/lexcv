---
phase: LEXCV-72-form-refactoring-create-edit
verified: 2026-07-02T00:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 72: Form Refactoring (Create/Edit) Verification Report

**Phase Goal:** Os formulários de criação e edição de cliente usam campos planos (sem seletor de card JSON) com labels dinâmicas ("Morada" para Particular, "Sede" para Empresa) e validação de NIF/REG_COMERCIAL
**Verified:** 2026-07-02
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ao selecionar tipo=EMPRESA no formulário de criação, o rótulo do campo nome muda para "Nome Comercial" e o do campo morada para "Sede" ao vivo | VERIFIED | `web/src/app/(dashboard)/clientes/novo/page.tsx:127-129` — `const tipoValue = form.watch("tipo"); const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome"; const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";` rendered at line 179 (`<Label htmlFor="nome">{nomeLabel}</Label>`) and line 222 (`<Label htmlFor="morada">{moradaLabel}</Label>`). `form.watch` guarantees live re-render on radio change. |
| 2 | Ao selecionar tipo=PARTICULAR (ou por defeito), o rótulo do campo nome é "Nome" e o do campo morada é "Morada" | VERIFIED | Ternary defaults to "Nome"/"Morada" for any non-"EMPRESA" value, including `undefined` (initial state before selection). Same file, same lines as above. |
| 3 | O mesmo comportamento dinâmico de rótulo está presente no formulário de edição | VERIFIED | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:214-216` — identical `tipoValue`/`nomeLabel`/`moradaLabel` derivation inside `ClienteEditContent`, applied at line 255 (`{nomeLabel}`) and line 326 (`{moradaLabel}`). Placed after the async `form.reset()` effect (lines 130-155) that populates `tipo` from loaded cliente data, so `form.watch("tipo")` reflects the loaded value correctly on first paint. |
| 4 | O campo NIF é rotulado apenas "NIF" (sem sufixo "(Legado)") em ambos os formulários | VERIFIED | `novo/page.tsx:188` and `editar/page.tsx:264` both render `<Label htmlFor="nif">NIF</Label>`. `grep -rn "NIF (Legado)" web/src/app/(dashboard)/clientes` returns no matches (exit code 1). |
| 5 | O select documento_tipo oferece a opção "Registo Comercial" (REG_COMERCIAL) em ambos os formulários | VERIFIED | `novo/page.tsx:250` and `editar/page.tsx:354` both contain `<option value="REG_COMERCIAL">Registo Comercial</option>`. `DOCUMENTO_TIPOS` const array in both files also includes `"REG_COMERCIAL"` (line 30 in each). |

**Score:** 5/5 truths verified

### Additional Goal-Level Checks (phase goal wording)

| Check | Status | Evidence |
|-------|--------|----------|
| "Campos planos (sem seletor de card JSON)" | VERIFIED | `grep -rn "dados_tipo\|dadosTipo"` against `novo/page.tsx`, `editar/page.tsx`, and `schemas/clientes.ts` returns no matches. Both forms bind directly to flat fields (`nome`, `nif`, `morada`, `documento_tipo`, `documento_numero`, etc.) via `form.register`/`Controller`, no JSON-blob/card-selector UI present. |
| Validação de NIF (CLI-05, form layer) | VERIFIED | `web/src/schemas/clientes.ts:3,25` — `export const nifPattern = /^\d{9}$/;` applied to the `nif` field via `.regex(nifPattern, "NIF deve conter exatamente 9 dígitos numéricos")`, required (not optional). `superRefine` (lines 49-66) additionally enforces the same 9-digit numeric rule when `documento_tipo === "NIF"` on `documento_numero`. |
| Validação de REG_COMERCIAL selecionável | VERIFIED | See truth #5 above; option is present and selectable (not disabled) in both selects. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | Formulário de criação com rótulos dinâmicos nome/morada e NIF renomeado | VERIFIED | Contains `form.watch("tipo")`, `nomeLabel`, `moradaLabel`, `{nomeLabel}`, `{moradaLabel}`; no "NIF (Legado)" string; REG_COMERCIAL option present. All wired into rendered JSX (not orphaned constants). |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | Formulário de edição com rótulos dinâmicos nome/morada e NIF renomeado | VERIFIED | Same pattern confirmed; additionally verified correct placement relative to async `form.reset()` (derived constants read after the effect that seeds `tipo` from server data — no stale-closure risk since `form.watch` re-subscribes on every render). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `novo/page.tsx` | react-hook-form `tipo` field | `form.watch("tipo")` drives `nomeLabel`/`moradaLabel` | WIRED | Line 127 reads `form.watch("tipo")`; lines 128-129 derive labels; lines 179 and 222 render them in JSX. |
| `editar/page.tsx` | react-hook-form `tipo` field | `form.watch("tipo")` drives `nomeLabel`/`moradaLabel` | WIRED | Line 214 reads `form.watch("tipo")`; lines 215-216 derive labels; lines 255 and 326 render them in JSX. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `novo/page.tsx` label rendering | `tipoValue` | `form.watch("tipo")`, itself driven by `Controller`/`RadioGroup` `onValueChange` → `form.setValue("tipo", ...)` | Yes — user radio selection flows through `onTipoChange`/`confirmTipoChange` into form state, which `form.watch` observes live | FLOWING |
| `editar/page.tsx` label rendering | `tipoValue` | `form.watch("tipo")`, initially seeded by `form.reset({ tipo: cliente.data.tipo ... })` in the `useEffect` at line 130-155 (real API data via `useCliente(id)`), then updatable via same radio/dialog flow as create form | Yes — no hardcoded/static fallback; `cliente.data.tipo` comes from the live `GET` cliente hook | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compiles cleanly across the whole `web` project (includes both modified files) | `cd web && pnpm exec tsc --noEmit` | No output, exit clean | PASS |
| No "NIF (Legado)" string remains under clientes directory | `grep -rn "NIF (Legado)" web/src/app/(dashboard)/clientes` | No matches (exit 1) | PASS |
| No residual `dados_tipo`/`dadosTipo` JSON-card field in create/edit forms or shared schema | `grep -rn "dados_tipo\|dadosTipo" novo/page.tsx editar/page.tsx schemas/clientes.ts` | No matches (exit 1) | PASS |
| No debt markers (TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) introduced in modified files | `grep -n -E "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER..."` on both files | No matches | PASS |

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` conventions or declarations found for this phase; PLAN.md verification section specifies grep/build/lint checks only, which are covered above.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| CLI-05 | 72-01 | NIF válido (9 dígitos, obrigatório) para criar/editar qualquer cliente | SATISFIED | Form-layer piece closed this phase: `nifPattern = /^\d{9}$/` required on `nif` field (`schemas/clientes.ts`), label renamed to plain "NIF" in both forms. Combined with Phase 71 schema-layer work, requirement is now fully complete end-to-end. |
| CLI-07 | 72-01 | Campo `nome` reaproveitado para nome (Particular) / nome comercial (Empresa) | SATISFIED | Dynamic `nomeLabel` in both forms confirmed above. |
| CLI-08 | 72-01 | Campo `morada` reaproveitado para morada (Particular) / sede (Empresa) | SATISFIED | Dynamic `moradaLabel` in both forms confirmed above. |
| CLI-09 | 72-01 | REG_COMERCIAL em `documento_tipo` para Empresa | SATISFIED | Confirmed present and selectable in both selects (already delivered in Phase 70/71 review fix; re-verified intact after this phase's edits). |
| CLI-10 | 72-01 | Formulários adaptados para campos planos com labels dinâmicas | SATISFIED | Confirmed: flat-field forms (no JSON card), dynamic Morada/Sede labels live in both create and edit forms. |

**Note on REQUIREMENTS.md staleness:** The REQUIREMENTS.md traceability table (line 38) still reads "CLI-05 | Phase 71 (schema layer) + Phase 72 (form UI layer) | Phase 71 complete, Phase 72 pending" even though the `[x]` checkbox above it (line 12) already marks CLI-05 as done and this phase's SUMMARY documents the form-layer piece as complete. This is a documentation-freshness gap in REQUIREMENTS.md, not a code gap — the underlying NIF validation and label work is verified present and correct in the codebase. Recommend updating REQUIREMENTS.md traceability row for CLI-05 to "Complete" in a follow-up doc-sync commit; not a blocker for phase 72 goal achievement.

### Anti-Patterns Found

None. Both modified files were scanned for TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers, empty implementations, and hardcoded-empty stub patterns — no matches.

### Human Verification Required

None. All truths are statically verifiable via source inspection (label ternary bound directly to `form.watch("tipo")`, which is React Hook Form's documented live-subscription API) and the TypeScript compiler confirms no type errors across the affected files. Live browser click-through of the radio toggle would be a nice-to-have but is not required to establish correctness here, since the derivation is a pure synchronous ternary with no async/timing dependency.

### Gaps Summary

No gaps. All 5 must-have truths, both required artifacts (exists/substantive/wired/data-flowing), both key links, and all 5 requirement IDs (CLI-05, CLI-07, CLI-08, CLI-09, CLI-10) are verified against live source in `novo/page.tsx` and `editar/page.tsx`, independently of SUMMARY.md's claims. `pnpm exec tsc --noEmit` run directly by the verifier confirms no regressions. The only note is a stale traceability row in REQUIREMENTS.md (informational, non-blocking).

---

*Verified: 2026-07-02*
*Verifier: Claude (gsd-verifier)*
