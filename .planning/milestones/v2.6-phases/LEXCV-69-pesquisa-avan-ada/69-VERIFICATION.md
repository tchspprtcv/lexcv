---
phase: 69-pesquisa-avan-ada
verified: 2026-07-01T00:00:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Abrir /pareceres, clicar 'Pesquisa Avançada', preencher texto livre + filtros de cliente/advogado/estado/data e clicar 'Pesquisar'"
    expected: "Painel de pesquisa avançada aparece distinto do painel 'Filtros'; os resultados no dual-view (tabela/cards) refletem exatamente o que o endpoint GET /pareceres/pesquisa devolve para os critérios indicados"
    why_human: "Requer servidor dev + browser para observar o resultado renderizado de facto; análise estática confirma a chamada de rede e a lógica de merge dos dados mas não o render visual final"
  - test: "Clicar 'Limpar Filtros' no painel de pesquisa avançada depois de uma pesquisa submetida"
    expected: "Página volta a mostrar a lista simples da Phase 65 (usePareceres), não um ecrã vazio"
    why_human: "Comportamento de estado React em runtime; grep confirma a chamada setPesquisaSubmitted(false) e setPesquisaFilters({}) mas não o efeito visual"
  - test: "Submeter uma pesquisa sem correspondências (ex: texto livre não existente)"
    expected: "Mostra o empty-state distinto 'Nenhum resultado encontrado' com a copy exata do UI-SPEC, não a copy do empty-state da lista simples"
    why_human: "Depende de dados reais devolvidos pelo backend em runtime"
  - test: "Aplicar filtros simples ('Filtros' > Aplicar) enquanto uma pesquisa avançada anterior está submetida"
    expected: "A lista simples volta a ficar visível imediatamente (WR-01 fix: onApply chama setPesquisaSubmitted(false))"
    why_human: "Comportamento de estado cruzado entre dois toggles independentes, melhor confirmado interativamente"
---

# Phase 69: Pesquisa Avançada Verification Report

**Phase Goal:** Utilizador consegue localizar pareceres relevantes combinando texto livre e filtros estruturados, aproveitando a capacidade de pesquisa já construída (e não usada) no backend.
**Verified:** 2026-07-01
**Status:** human_needed
**Re-verification:** No — initial verification

This is the final phase of milestone v2.6 (Módulo de Parecer Jurídico — UI).

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador vê um segundo botão "Pesquisa Avançada" distinto do "Filtros" existente | ✓ VERIFIED | `pareceres/page.tsx:169-177` renders a second `<Button>` with `Search` icon, toggling independent `pesquisaOpen` state (line 67), separate from `advancedOpen` (line 61) |
| 2 | Ao expandir o painel, utilizador pode preencher texto livre, cliente, advogado, estado, data início e data fim | ✓ VERIFIED | `page.tsx:251-372` renders all six draft fields (`pesquisaTexto` text input, `pesquisaClienteId`/`pesquisaAdvogadoId`/`pesquisaStatus` selects, `pesquisaDataInicio`/`pesquisaDataFim` native date inputs) inside the `pesquisaOpen` panel |
| 3 | Resultados vêm do endpoint `GET /pareceres/pesquisa` e substituem a lista simples no mesmo dual-view | ✓ VERIFIED | `use-pareceres.ts:60-78` `usePesquisarPareceres` calls `apiFetch<ParecerSolicitacao[]>(\`/pareceres/pesquisa${...}\`)`; `page.tsx:91-94` derives `rows`/`resultsLoading`/`resultsError` via a single `searchActive` conditional feeding the same dual-view JSX (mobile cards `408-434`, desktop table `436-471`) |
| 4 | "Limpar Filtros" volta à lista simples | ✓ VERIFIED | `page.tsx:132-141` `onLimparPesquisa` resets all draft fields, sets `pesquisaFilters({})` and `pesquisaSubmitted(false)`, which flips `searchActive` back to false and `rows` back to `pareceres.data` |
| 5 | Pesquisa sem correspondências mostra empty-state "Nenhum resultado encontrado" distinto da lista simples | ✓ VERIFIED | `page.tsx:384-404` — `searchActive` branch renders "Nenhum resultado encontrado" / matching UI-SPEC body copy; non-search branch renders the Phase 65 "Nenhuma solicitação de parecer encontrada" copy unchanged |

**Score:** 5/5 truths verified

### Backend Contract Match (Success Criterion 2)

Direct read of `backend/src/main/java/com/lexcv/controllers/ParecerController.java:184-198`:

```java
@PreAuthorize("hasAuthority('pareceres:view')")
@GetMapping("/api/v1/pareceres/pesquisa")
public ResponseEntity<?> pesquisarSolicitacoes(
        @RequestParam(required = false) String texto,
        @RequestParam(required = false) UUID clienteId,
        @RequestParam(required = false) UUID advogadoId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDateTime dataInicio,
        @RequestParam(required = false) LocalDateTime dataFim
) {
    UUID tenantId = getTenantId();
    List<ParecerSolicitacao> result = parecerSolicitacaoRepository.pesquisar(
            tenantId, texto, clienteId, advogadoId, status, dataInicio, dataFim);
    return ResponseEntity.ok(result);
}
```

Frontend `buildParecerPesquisaSearch` (`use-pareceres.ts:48-58`) sends exactly these six params, with `dataInicio`/`dataFim` correctly suffixed `T00:00:00`/`T23:59:59` to satisfy the `LocalDateTime` binding (confirmed this is required — a bare `YYYY-MM-DD` would fail Spring's default `LocalDateTime` param conversion). No business logic (filtering/matching) is duplicated client-side — the frontend passes raw filter values and renders whatever array the backend returns verbatim. Tenant scoping happens entirely server-side (`getTenantId()` from security context); the frontend never sends or overrides a tenant parameter.

`@PreAuthorize("hasAuthority('pareceres:view')")` on the endpoint matches the frontend's route-level gate `permissions.can.view("pareceres")` (`page.tsx:44-53`), which wraps the entire `ParecerPageContent` including the new panel and hook call — both layers agree, per CLAUDE.md's RBAC requirement.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/hooks/use-pareceres.ts` | `usePesquisarPareceres` hook + `ParecerPesquisaFilters` type + `buildParecerPesquisaSearch` builder | ✓ VERIFIED | Lines 39-78 — all three present, substantive (not stubs), read-only (no mutation), correctly builds date-time-suffixed query string |
| `web/src/app/(dashboard)/pareceres/page.tsx` | Pesquisa Avançada toggle panel + submit-driven results rendering | ✓ VERIFIED | Lines 67-141 (state/handlers), 169-177 (toggle button), 251-372 (panel), 91-94 (data-source swap), 384-404 (distinct empty-state) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `pareceres/page.tsx` | `usePesquisarPareceres` | hook call in `ParecerPageContent` | ✓ WIRED | Line 89: `const pesquisa = usePesquisarPareceres(pesquisaFilters);` — result consumed at lines 92-94 for `rows`/`resultsLoading`/`resultsError` |
| `use-pareceres.ts` | `/pareceres/pesquisa` | `apiFetch` in `queryFn` | ✓ WIRED | Line 71-74 — path matches backend `@GetMapping("/api/v1/pareceres/pesquisa")` exactly (Next.js rewrite strips `/api/v1` prefix per `apiFetch`/`next.config.ts` convention used identically by `usePareceres`) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `pareceres/page.tsx` results block | `rows` | `pesquisa.data` (from `usePesquisarPareceres`) when `searchActive`, else `pareceres.data` (from `usePareceres`) | Yes — both hooks call live `apiFetch` against real backend endpoints, no hardcoded arrays, no mock fallback | ✓ FLOWING |

No hardcoded empty/static arrays found in either changed file. `useState<ParecerPesquisaFilters>({})` initial states are legitimate draft-state, overwritten by `onPesquisar`/`onLimparPesquisa` before any query fires with real filter values (query itself is always live via `apiFetch`, even with empty filters — an empty-filter search still hits the real endpoint and returns real tenant data).

### Cache Namespace Isolation (Success Criterion 3)

- `usePareceres` (Phase 65): `queryKey: ["pareceres", "list", clienteId, advogadoId, status]` (line 29)
- `usePesquisarPareceres` (Phase 69): `queryKey: ["pareceres", "pesquisa", texto, clienteId, advogadoId, status, dataInicio, dataFim]` (line 70)

Distinct top-level second segment (`"list"` vs `"pesquisa"`) — no key collision, confirmed by direct read, not just SUMMARY claim. Additionally, mutation `onSuccess` handlers (`useCreateParecer` line 121-126, `useCreateParecerVersao` line 180-187, `useEntregarParecer` line 200-206) all invalidate both `["pareceres","list"]` and `["pareceres","pesquisa"]` — this addresses code-review finding WR-03 (stale search cache), confirmed fixed in commit `47d6c07` (`fix(69): WR-03 invalidate pesquisa query-key namespace on parecer mutations`), verified present in the current file, not just claimed in a commit message.

### Code Review Findings — Resolution Status

69-REVIEW.md flagged 3 warnings (WR-01, WR-02, WR-03) and 3 info items. Checked resolution against current file state (not SUMMARY claims):

| Finding | Status | Evidence |
|---------|--------|----------|
| WR-01 (both toggles/results can be ambiguous; Aplicar doesn't reset search mode) | ✓ FIXED | `page.tsx:96-98` `onApply` now calls `setPesquisaSubmitted(false)` before setting `filters` — commit `8444827` |
| WR-02 (inconsistent shape of `{}` vs fully-populated pesquisaFilters) | ✓ FIXED | `page.tsx:113-130` `onPesquisar` now builds `next: ParecerPesquisaFilters = {}` and only sets keys with truthy trimmed values — partial-shape consistent with `onLimparPesquisa`'s `{}` — commit `8444827` |
| WR-03 (pesquisa cache never invalidated by mutations) | ✓ FIXED | All three mutation hooks in `use-pareceres.ts` now invalidate `["pareceres","pesquisa"]` alongside `["pareceres","list"]` — commit `47d6c07` |
| IN-01 (isFetching flips on window refocus, cosmetic) | Not fixed, acknowledged info-only | No functional impact, correctly left as info-level per review |
| IN-02 (STATUS_OPTIONS duplicated across both panels) | Not fixed, acknowledged info-only | Cosmetic duplication remains (lines 199-203, 319-323) — correctly deprioritized as non-blocking |
| IN-03 (formatDate pre-existing, out of phase scope) | Not applicable | Pre-existing helper, not touched by this phase |

All 3 warning-level (actionable) findings from code review were fixed in follow-up commits before this verification ran. Both info-level cosmetic items remain open but do not affect goal achievement.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PARS-03 | 69-01-PLAN.md | Utilizador pode pesquisar pareceres na UI combinando texto livre com filtros, espelhando `pesquisar()` do backend | ✓ SATISFIED | All 5 truths verified above; backend contract match confirmed by direct source read |

No orphaned requirements found for Phase 69 in REQUIREMENTS.md.

### Anti-Patterns Found

None in the two changed files. No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers, no `return null`/empty-stub handlers, no hardcoded empty arrays feeding rendered output, no disconnected props. `pnpm exec tsc --noEmit` runs clean project-wide (independently re-run, zero output/errors). `pnpm lint` shows 5 pre-existing errors, all in files untouched by this phase (`web/src/app/(dashboard)/profile/page.tsx`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/hooks/use-toast.ts`) — confirmed by direct lint run, not just SUMMARY claim.

### Behavioral Spot-Checks

No dev server available in this environment (per task instructions). Static verification only:

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compiles clean | `pnpm exec tsc --noEmit` | Zero errors/output | ✓ PASS |
| Lint has no new errors in changed files | `pnpm lint` | 5 pre-existing errors, all in unrelated files | ✓ PASS |
| Backend endpoint contract matches frontend call | source read of `ParecerController.java:184-198` vs `use-pareceres.ts:60-78` | Path, params, and types match exactly | ✓ PASS |

### Probe Execution

No probe scripts found for this phase (`scripts/*/tests/probe-*.sh` — none referenced in PLAN/SUMMARY, not a migration/tooling phase). Skipped — not applicable.

### Human Verification Required

See frontmatter `human_verification` list. These are runtime/visual behaviors (panel toggle interplay, live search results, empty-state rendering with real backend data) that static analysis confirms are wired correctly at the code level but cannot be observed rendering without a running dev server + browser, which is unavailable in this environment.

### Gaps Summary

No blocking gaps found. All 5 must-have truths, both required artifacts, both key links, and the cache-namespace isolation criterion are verified directly against the codebase (not SUMMARY claims). The three actionable code-review warnings (WR-01/02/03) were confirmed fixed in the current file state via follow-up commits (`8444827`, `47d6c07`), not merely claimed. `tsc --noEmit` was independently re-run and is clean.

The only reason this is not `passed` is that a set of runtime/visual behaviors require a live browser to observe (per Step 9 status rules, `human_needed` takes priority over `passed` whenever human-verification items exist, even with a perfect truths score). This is the final phase of milestone v2.6 — once the human verification items above are confirmed, PARS-03 is satisfied and the milestone is complete.

---

_Verified: 2026-07-01_
_Verifier: Claude (gsd-verifier)_
