---
phase: 65-funda-o-listagem-e-detalhe
verified: 2026-07-01T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Load /pareceres as a user with pareceres:view on both desktop and mobile viewport widths"
    expected: "Desktop shows the table view (hidden md:block); mobile (<768px) shows the stacked card view (md:hidden); status badges render in the correct colors (gray/blue/amber/green) for each of the 4 status values; filters (status/advogado/cliente) narrow the list correctly"
    why_human: "Responsive breakpoint rendering and visual badge coloring cannot be confirmed by static analysis alone — Tailwind classes are present in source but actual rendered layout/CSS cascade needs a browser"
  - test: "Load /pareceres/[id] for a solicitação that has versions with and without anexo, and click 'Descarregar anexo'"
    expected: "The presigned URL opens in a new tab and downloads/previews the file; entries without caminhoAnexo show 'Sem anexo' plain text with no button"
    why_human: "Presigned URL generation, MinIO connectivity, and browser new-tab behavior cannot be exercised without a running backend + MinIO + browser"
  - test: "Log in as a user WITHOUT pareceres:view (e.g. a role lacking the scope) and confirm the sidebar does not show 'Pareceres', and direct navigation to /pareceres shows AccessDeniedState"
    expected: "Nav item absent; page shows access-denied message, not data"
    why_human: "Requires a live authenticated session with a specific role to observe actual rendered RBAC behavior end-to-end (server + client)"
  - test: "Navigate to /pareceres/{a valid UUID belonging to another tenant}"
    expected: "Detail page shows the generic fetch-error message, with no metadata card or timeline rendered — no partial/leaked data"
    why_human: "Requires two seeded tenants and a live backend session to trigger the actual 404 path; static analysis confirms the error branch withholds rendering but not the live HTTP behavior"
---

# Phase 65: Fundação — Listagem e Detalhe Verification Report

**Phase Goal:** Utilizador consegue observar o estado de qualquer solicitação de parecer através da aplicação — lista, detalhe e histórico de versões — sobre uma camada de tipos verificada contra o contrato JSON real do backend (evitando o defeito camelCase/snake_case do v2.4).
**Verified:** 2026-07-01
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador com `pareceres:view` vê `/pareceres` com tabela (desktop) e cards (mobile), status badges e filtros por status/advogado/cliente | ✓ VERIFIED | `web/src/app/(dashboard)/pareceres/page.tsx` L200-227 (`md:hidden` card fork) and L229-264 (`hidden md:block` table fork); status/advogado/cliente `<select>` filters at L129-178; `statusVariant()` maps all 4 status values (L25-35) |
| 2 | Utilizador consegue abrir detalhe (`/pareceres/[id]`) e ver todos os campos corretamente, sem blanks por mismatch de casing | ✓ VERIFIED | Types (`web/src/types/pareceres.ts`) match `ParecerSolicitacao.java`/`ParecerVersao.java` field-for-field, pure camelCase, no `@JsonProperty` override on either entity (confirmed by direct read of both Java files) — no normalization bridge exists to introduce a mismatch; detail page (`[id]/page.tsx` L132-156) renders cliente/advogado/estado/prioridade/prazo/criado directly from `parecer.data.*` camelCase fields |
| 3 | Utilizador vê, no detalhe, timeline imutável das versões (mesmo vazia) | ✓ VERIFIED | `[id]/page.tsx` L181-223 renders `versoes.data` as a chronological dot+connector list (numeroVersao, autor via `resolveUserNome`, createdAt, conteudo, anexo link); no edit/delete affordance exists anywhere in the file (grep found none); empty state at L174-180 shows "Nenhuma versão ainda" / "Aguarda elaboração pelo advogado atribuído." exactly per UI-SPEC |
| 4 | Item de navegação "Pareceres" aparece na sidebar apenas para utilizadores com `pareceres:view` | ✓ VERIFIED | `dashboard-shell.tsx` L48 adds `{ href: "/pareceres", ..., requiredPermission: "pareceres:view" }`; both desktop-aside and mobile-Sheet render sites (L88, L168) apply `NAV.filter((item) => hasPermission(me.data?.permissions, item.requiredPermission))` — nav item is excluded from the DOM entirely for unauthorized users, not just hidden. Backend seed (`DatabaseSeeder.java` L302-347) confirms `pareceres:view` is a real granted scope (ADMIN, ASSISTENTE, TECNICO, ADVOGADO all receive it), matching `KNOWN_SCOPES` in `web/src/lib/permissions.ts` |

**Score:** 4/4 truths verified (via static analysis; live-browser behavior deferred to human verification below)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/types/pareceres.ts` | Pure camelCase types, 1:1 with Java entities | ✓ VERIFIED | Exports `ParecerStatus`, `ParecerPrioridade`, `ParecerSolicitacao`, `ParecerVersao`; every field name/nullability matches `ParecerSolicitacao.java`/`ParecerVersao.java` exactly; no `_id` snake_case keys, no `normalize*`/`*Api` bridge type |
| `web/src/schemas/pareceres.ts` | Zod enum schemas | ✓ VERIFIED | `parecerStatusSchema` (4 values), `parecerPrioridadeSchema` (3 values) |
| `web/src/hooks/use-pareceres.ts` | TanStack Query hooks for list/detail/versoes/anexo | ✓ VERIFIED | `usePareceres`, `useParecer`, `useParecerVersoes`, `useDownloadParecerAnexo` all present; all queryFn paths use `/pareceres/solicitacoes...` without `/api/v1` prefix (matches `apiFetch` convention); query-key namespace `["pareceres", "list"\|"detail"\|"versoes", ...]`; no `normalize` identifier in file |
| `web/src/components/shared/dashboard-shell.tsx` | Pareceres nav item gated by `pareceres:view` | ✓ VERIFIED | 1 occurrence of `pareceres:view` in NAV array; existing filter logic (unmodified) applies gating at both render sites |
| `web/src/app/(dashboard)/pareceres/page.tsx` | List page, dual-view, badges, filters | ✓ VERIFIED (min_lines 120 satisfied, 271 lines) | Contains `usePareceres`, both dual-view forks, permission gate + `AccessDeniedState` |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | Detail page, metadata + timeline | ✓ VERIFIED (min_lines 120 satisfied, 230 lines) | Contains `useParecerVersoes`, metadata `dl`/`dd` grid, timeline, `AnexoLink` wired to `useDownloadParecerAnexo` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `use-pareceres.ts` | `/pareceres/solicitacoes` | `apiFetch` | ✓ WIRED | Confirmed backend `@RequestMapping("/api/v1/pareceres/solicitacoes")` in `ParecerController.java` matches (apiFetch prepends `/api/v1`) |
| `dashboard-shell.tsx` | `pareceres:view` | `requiredPermission` on NAV item + `hasPermission` filter | ✓ WIRED | Both desktop and mobile render sites gate correctly; permission scope confirmed present in backend seed and `permissions.ts` KNOWN_SCOPES |
| `pareceres/page.tsx` | `usePareceres` | hook import | ✓ WIRED | `usePareceres(filters)` called at L71, results rendered in both view forks |
| `pareceres/[id]/page.tsx` | `useParecer` + `useParecerVersoes` | hook imports | ✓ WIRED | Both called at L96-97, data rendered directly, error branch (L121-124) withholds metadata/timeline rendering — satisfies the IDOR-safety requirement (no partial/leaked data on 404) |
| `pareceres/page.tsx` | `pareceres:view` | `permissions.can.view("pareceres")` + `AccessDeniedState` | ✓ WIRED | L38-48; same pattern replicated identically in detail page L49-59 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `pareceres/page.tsx` | `pareceres.data` | `usePareceres(filters)` → `GET /pareceres/solicitacoes` (real backend query, confirmed live in `ParecerController.java` `@PreAuthorize("hasAuthority('pareceres:view')")` handler, tenant-scoped) | Yes | ✓ FLOWING |
| `pareceres/[id]/page.tsx` | `parecer.data`, `versoes.data` | `useParecer(id)`, `useParecerVersoes(id)` → real backend endpoints, same controller | Yes | ✓ FLOWING |
| `pareceres/page.tsx` | `clienteNomeById` (cliente name resolution) | `useClientes({})` — pre-existing hook, real backend query | Yes | ✓ FLOWING |
| `pareceres/[id]/page.tsx` | `userNomeById` (advogado/autor name resolution) | `useAdminUsers()` — pre-existing hook | Yes | ✓ FLOWING |

No hardcoded/static empty-array fallbacks found that would mask real data; all `?? []` fallbacks are standard TanStack Query `undefined`-while-loading guards, not stub data.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PARC-11 | 65-01, 65-02 | Lista de solicitações com dual-view, badges, filtros | ✓ SATISFIED | List page verified above; filter params map 1:1 to backend `clienteId`/`advogadoId`/`status` query params (confirmed against `ParecerController` — filter by `processoId` intentionally excluded per requirement's own noted scope carve-out) |
| PARC-12 | 65-01, 65-02 | Detalhe com timeline imutável de versões | ✓ SATISFIED | Detail page + timeline verified above; no mutation/edit affordance present |

Both requirements traced to this phase in REQUIREMENTS.md are satisfied. No orphaned requirements found for Phase 65.

### Anti-Patterns Found

None. Scanned all 6 changed files for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/empty-implementation patterns — zero matches (the only `"Todos"` hits are the Portuguese word "All" in filter `<option>` labels, not a debt marker). No unresolved review findings: `65-REVIEW-FIX.md` confirms all 3 warning-level findings from `65-REVIEW.md` (WR-01, WR-02, WR-04) were fixed and re-verified with zero regressions; remaining Info-level items (IN-01 redundant trim, IN-02 duplicated `statusVariant`, IN-03 no loading-disable on filter buttons) are cosmetic/non-blocking and explicitly scoped as such by the reviewer.

### Behavioral Spot-Checks

Skipped — this phase has no runnable backend/frontend server available in this verification environment. `pnpm exec tsc --noEmit` was run instead as the closest available automated check:

```
cd web && pnpm exec tsc --noEmit
```

Result: zero errors referencing any pareceres file (types, schemas, hooks, dashboard-shell, list page, detail page). Full TypeScript compilation is clean across the entire changed surface.

### Probe Execution

Not applicable — no `scripts/*/tests/probe-*.sh` files exist for this phase, and neither PLAN nor SUMMARY declare probe-based verification. Step 7c skipped.

### Human Verification Required

### 1. Responsive dual-view rendering

**Test:** Load `/pareceres` as a user with `pareceres:view` at both desktop (>=768px) and mobile (<768px) viewport widths.
**Expected:** Desktop renders the table; mobile renders stacked cards; all 4 status badge colors (gray/blue/amber/green) render correctly; filters narrow results.
**Why human:** Static analysis confirms the Tailwind `hidden md:block` / `md:hidden` classes exist in source, but actual breakpoint behavior and visual badge coloring require a browser render.

### 2. Anexo download flow

**Test:** Open a solicitação detail with versions that do/don't have an anexo; click "Descarregar anexo".
**Expected:** Presigned URL opens in a new tab; entries without `caminhoAnexo` show "Sem anexo" text only.
**Why human:** Requires a live backend + MinIO + browser to exercise the presigned-URL round trip.

### 3. Nav visibility and access-denied for unauthorized role

**Test:** Log in as a role without `pareceres:view`; confirm sidebar hides "Pareceres" and direct navigation to `/pareceres` shows `AccessDeniedState`.
**Expected:** Nav item absent from DOM; page shows the Portuguese access-denied message, not solicitação data.
**Why human:** Requires an authenticated live session with a specific role to observe end-to-end RBAC behavior (server-issued permissions + client render).

### 4. Cross-tenant IDOR check

**Test:** Navigate to `/pareceres/{uuid}` for a solicitação belonging to another tenant.
**Expected:** Generic error message shown; no metadata card or timeline rendered (no partial/leaked data).
**Why human:** Requires two seeded tenants and a live backend to trigger the actual 404 path; static analysis confirms the error branch code path withholds rendering, but the live HTTP round trip is unverified here.

### Gaps Summary

No blocking gaps found. All 4 roadmap success criteria and both PLAN-level must-haves (truths, artifacts, key links) are supported by direct evidence in the codebase: types are verified field-for-field against the Java entities with zero normalization layer (directly addressing the stated v2.4 regression risk), TanStack Query hooks point at the correct backend paths, the list/detail pages are fully wired (no stubs, no hardcoded empty data), RBAC gating is implemented identically to the backend's `@PreAuthorize` scope and confirmed against the actual seeded role-permission matrix, and `tsc --noEmit` is clean. The only class of finding requiring further validation is inherently visual/live-session behavior (responsive rendering, presigned-URL download, RBAC enforcement across roles, cross-tenant 404 handling) — these are routed to human verification, not treated as gaps, per the environment's stated inability to run a live dev server/browser.

---

_Verified: 2026-07-01_
_Verifier: Claude (gsd-verifier)_
