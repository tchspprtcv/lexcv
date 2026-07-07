---
phase: LEXCV-83-frontend-tipos-schemas-e-hooks
verified: 2026-07-07T23:43:33Z
status: passed
score: 8/8 must-haves verified
overrides_applied: 0
---

# Phase 83: Frontend — Tipos, Schemas e Hooks Verification Report

**Phase Goal:** A camada de dados do frontend conhece os campos e entidades novos com tipagem e validação corretas, e o mapeamento camelCase/snake_case está coberto para todos eles antes de qualquer UI ser construída.
**Verified:** 2026-07-07T23:43:33Z
**Status:** passed
**Re-verification:** No — initial verification

## Context

This phase went through 2 rounds of code review + fix (`83-REVIEW.md` → `83-REVIEW-FIX.md` → `83-REVIEW-2.md`). Round 1 found 2 critical bugs (CR-01: `toProcessoApiPayload` silently dropped `legal_hold`/`data_retencao`; CR-02: list endpoint snake_case `numero_processo`/`tipo_processo` not read by `normalizeProcesso`) and 5 warnings; 6/7 were fixed (WR-04 explicitly deferred as out-of-scope by the review itself, no form consumes `DecisaoCreateRequest.file` yet). Round 2 re-verified all fixes with no regressions and found one new non-blocking warning (WR-06 — fragility of the `"in"` operator guard, same accepted pattern as WR-03). This verification independently re-confirms the **final on-disk state**, not the review reports.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `types/processos.ts` exports `Decisao`, `Facto`, `Testemunha`, `TipoDecisao`, `TipoTestemunha`, `OrigemProcesso` with the exact shape returned by Phase 81's 12 endpoints | ✓ VERIFIED | `web/src/types/processos.ts` lines 135-200: `Decisao`/`DecisaoCreateRequest`/`DecisaoUpdateRequest` (DecisaoUpdateRequest correctly excludes `documentoId`/`file` per WR-03 Phase-81-review), `Testemunha`/`TestemunhaCreateRequest`/`TestemunhaUpdateRequest`, `Facto`/`FactoCreateRequest` (no `ordem`)/`FactoUpdateRequest` (required `ordem`, with WR-02 doc comment) |
| 2 | `Processo`/`ProcessoCreateRequest` gain `juizo?`/`origem?`; `ProcessoUpdateRequest` gains `juizo` but explicitly NOT `origem` | ✓ VERIFIED | `types/processos.ts` lines 27-81: `Processo` has `juizo?: string; origem?: OrigemProcesso;` (44-47), `ProcessoCreateRequest` has both (63-64), `ProcessoUpdateRequest` has only `juizo?: string;` (80), no `origem` key present |
| 3 | `schemas/processos.ts` exports `decisaoFormSchema`, `factoFormSchema`, `testemunhaFormSchema`, and a dedicated intake schema where `origem` is a required `z.enum`, not `optionalTrimmedString` | ✓ VERIFIED | `web/src/schemas/processos.ts`: `origemProcessoSchema = z.enum([...])` (27-30), `processoIntakeFormSchema = processoFormSchema.extend({ origem: origemProcessoSchema })` (32-34, required, no `.optional()`), `decisaoFormSchema`/`testemunhaFormSchema`/`factoFormSchema` (125-148); general `processoFormSchema` has `juizo: optionalTrimmedString` (22) but no `origem` key, matching backend's silent-ignore-on-update behavior |
| 4 | PT labels for `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` exist in a presentation layer separate from wire values | ✓ VERIFIED | `web/src/lib/tipo-decisao.ts`, `tipo-testemunha.ts`, `origem-processo.ts` each export a single `Record<Enum,string>`-plus-function (matches `conflict-check.ts` pattern); wire values remain ASCII in `schemas/processos.ts` |
| 5 | `use-processos.ts` exposes the list/create/update/delete quartet for Decisão, Testemunha, Facto, following existing queryKey convention | ✓ VERIFIED | `web/src/hooks/use-processos.ts` lines 364-551: `useDecisoes/useAddDecisao/useUpdateDecisao/useDeleteDecisao` (`["processos","decisoes",id]`), `useTestemunhas/useAddTestemunha/useUpdateTestemunha/useDeleteTestemunha` (`["processos","testemunhas",id]`), `useFactos/useAddFacto/useUpdateFacto/useDeleteFacto` (`["processos","factos",id]`) — 12/12 hooks present, structurally identical to the `useProcessoFases` quartet pattern |
| 6 | `normalizeProcesso()`/`toProcessoApiPayload()` map `juizo`/`origem` correctly, delegating to a shared module (`processo-juizo-origem-mapping.ts`), both exported for direct verification | ✓ VERIFIED | `use-processos.ts` line 99 `export function normalizeProcesso` delegates via `...mapJuizoOrigemFromApi(api)` (117); line 123 `export function toProcessoApiPayload` delegates via `...mapJuizoOrigemToPayload(payload)` (141). `web/src/lib/processo-juizo-origem-mapping.ts` contains zero `@/` imports (confirmed via read — only `import type { OrigemProcesso } from "../types/processos"`) |
| 7 | An automated, executable round-trip test (not just tsc/build) confirms `juizo`/`origem` survive create/update → normalize, calling the SAME shared module used at runtime | ✓ VERIFIED | Ran `node web/scripts/verify-juizo-origem-roundtrip.mjs` myself — printed `PASS`, exit code 0. Script imports `mapJuizoOrigemFromApi`/`mapJuizoOrigemToPayload` directly from `../src/lib/processo-juizo-origem-mapping.ts` (grep for `function mapJuizoOrigem` in the script returns 0 matches — no reimplementation). Durable vitest spec (`use-processos.round-trip.test.ts`) imports the real `normalizeProcesso`/`toProcessoApiPayload` from `./use-processos` |
| 8 | `useDeleteProcesso` also clears caches for the 3 new subresources on processo delete | ✓ VERIFIED | `use-processos.ts` lines 248-259: `removeQueries` calls for `["processos","decisoes",id]`, `["processos","testemunhas",id]`, `["processos","factos",id]` added alongside existing partes/fases/movimentacoes |

**Score:** 8/8 truths verified (roadmap's 4 success criteria fully subsumed by truths 1-2, 3, 5, 6-7 above)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/types/processos.ts` | Decisao/Facto/Testemunha + Processo.juizo/origem types | ✓ VERIFIED | All types present, correct asymmetries (ProcessoUpdateRequest no origem, DecisaoUpdateRequest no documentoId/file, FactoCreateRequest no ordem / FactoUpdateRequest required ordem). `TipoDecisao`/`TipoTestemunha`/`OrigemProcesso` now derived via `z.infer<typeof ...Schema>` from `@/schemas/processos` (WR-05 fix) — no hand-duplicated literal unions |
| `web/src/schemas/processos.ts` | decisaoFormSchema/factoFormSchema/testemunhaFormSchema/origemProcessoSchema/processoIntakeFormSchema | ✓ VERIFIED | All 5 present; `processoFormSchema` gains `juizo` but not `origem` |
| `web/src/lib/tipo-decisao.ts`, `tipo-testemunha.ts`, `origem-processo.ts` | PT label maps | ✓ VERIFIED | All 3 exist, exact label text confirmed |
| `web/src/lib/processo-juizo-origem-mapping.ts` | Shared mapping module, zero `@/` imports | ✓ VERIFIED | Confirmed via file read — only relative type-only import; both `mapJuizoOrigemFromApi`/`mapJuizoOrigemToPayload` exported |
| `web/src/hooks/use-processos.ts` | 12 new hooks + exported normalizeProcesso/toProcessoApiPayload delegating to shared module | ✓ VERIFIED | Confirmed by direct read of the full file |
| `web/src/hooks/use-processos.round-trip.test.ts` | Durable vitest spec | ✓ VERIFIED (not executable — no test runner installed, documented/accepted precedent from Phase 74/82) | Imports real functions, 4 test cases matching plan spec |
| `web/scripts/verify-juizo-origem-roundtrip.mjs` | Executable Node proof | ✓ VERIFIED | Ran directly: `PASS`, exit 0 |
| `web/package.json` | `verify:juizo-origem` script | ✓ VERIFIED | `"verify:juizo-origem": "node scripts/verify-juizo-origem-roundtrip.mjs"` present (line 10) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `processoIntakeFormSchema` | `origemProcessoSchema` | `.extend({ origem: origemProcessoSchema })` | ✓ WIRED | Confirmed at `schemas/processos.ts:32-34` |
| `lib/tipo-decisao.ts` | `types/processos.ts` (TipoDecisao) | `import type` | ✓ WIRED | Confirmed |
| `use-processos.ts` (toProcessoApiPayload) | `processo-juizo-origem-mapping.ts` (mapJuizoOrigemToPayload) | spread delegation | ✓ WIRED | Confirmed, `"origem" in payload` narrowing lives only in the shared module |
| `verify-juizo-origem-roundtrip.mjs` | `processo-juizo-origem-mapping.ts` | direct relative `.ts` import, Node native type-stripping | ✓ WIRED | Executed script directly — confirmed working, no reimplementation |
| `useAddDecisao` | `POST /processos/{id}/decisoes` | FormData with literal `file`/`data`/`tipo`/`resumo` fields | ✓ WIRED | Confirmed at lines 375-397, no JSON.stringify, matches backend `@RequestParam` shape |

### Independent Fix Re-confirmation (per task instructions)

| Item | Claim | Independently Confirmed |
|------|-------|--------------------------|
| (a) `toProcessoApiPayload` forwards legalHold/dataRetencao | CR-01 fixed | ✓ Confirmed on disk: lines 139-140, `"legal_hold" in payload ? payload.legal_hold : undefined` / same for `data_retencao`, with explanatory comment. `ProcessoApiPayload` type gained `legalHold?`/`dataRetencao?` (lines 83-84) |
| (b) `normalizeProcesso` resolves numero/tipo_processo from either camelCase or snake_case | CR-02 fixed | ✓ Confirmed on disk: `ProcessoApi` type has both `numeroProcesso`/`numero_processo` and `tipoProcesso`/`tipo_processo` (lines 45-49); `normalizeProcesso` fallback chains read both casings (lines 104-106) |
| (c) TipoDecisao/TipoTestemunha/OrigemProcesso derived via z.infer | WR-05 fixed | ✓ Confirmed on disk: `types/processos.ts` lines 3-23, `z.infer<typeof tipoDecisaoSchema>` etc., importing schemas via `import type` from `@/schemas/processos` — no import cycle (schemas file only imports `zod`) |
| (d) `web/package.json` has `verify:juizo-origem` script | WR-01 fixed | ✓ Confirmed: `grep -n "verify:juizo-origem" web/package.json` → line 10 |
| (e) `npx tsc --noEmit` and `node scripts/verify-juizo-origem-roundtrip.mjs` clean/PASS | Claimed clean | ✓ Ran both myself: tsc produces only the 3 pre-existing baseline `vitest`-module-not-found errors (2 pre-existing from Phase 74 + 1 expected new one from this phase's own `.test.ts`, documented and accepted precedent); the Node script prints `PASS` with exit code 0 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | Debt-marker scan (TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER) across all 8 phase-modified files | none found | — |
| `web/src/hooks/use-processos.ts` | 105-106 | `titulo: api.titulo ?? api.tipoProcesso ?? api.tipo_processo` — pre-existing quirk (titulo falls back to tipo_processo values) predates this phase (confirmed via `git show 3af6325`, this line existed as `api.titulo ?? api.tipoProcesso` before the CR-02 commit only added the `?? api.tipo_processo` snake_case leg) | ℹ️ Info | Out of this phase's diff scope — not introduced or worsened by Phase 83; not re-flagged as a phase-83 gap |

### Requirements Coverage

Phase 83 owns no dedicated requirement ID (confirmed by `.planning/REQUIREMENTS.md` line 83: "Coverage: 17/17 requirements mapped ✓ (Phase 83 ... is a pure integration phase supporting all of the above; it owns no requirement directly but is a hard dependency for Phase 84.)"). Both plan frontmatter files declare `requirements: []` with matching justification comments. No orphaned requirements found for this phase.

### Human Verification Required

None. This phase is a pure data-layer/type-contract phase with no UI — there is nothing render-dependent to visually confirm (no processos-list rendering or legal-hold checkbox round-trip to test in a browser, since no UI consumes these hooks/types yet; that is explicitly Phase 84's scope). All must-haves are statically verifiable via type-checking, direct code reads, and the executable round-trip script, all of which were run directly rather than trusted from SUMMARY/REVIEW claims.

### Gaps Summary

No gaps. All 8 derived truths (which fully subsume the roadmap's 4 stated success criteria) verified directly against the current on-disk code, not SUMMARY.md claims. Both review-fix rounds' claims were independently re-confirmed by reading the actual diffs/files and executing `tsc --noEmit` and the round-trip script myself. The one residual WR-06 warning from `83-REVIEW-2.md` (fragility of the `"in"`-operator guard for `legalHold`/`dataRetencao`, not covered by a dedicated round-trip test) is a legitimate code-quality observation but does not block the phase goal — the guard is correct for the only live call site today, and the review itself classified it as a non-blocking warning, not a bug.

---

*Verified: 2026-07-07T23:43:33Z*
*Verifier: Claude (gsd-verifier)*
