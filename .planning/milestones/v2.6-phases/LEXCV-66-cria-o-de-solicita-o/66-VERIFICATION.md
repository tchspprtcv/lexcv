---
phase: 66-cria-o-de-solicita-o
verified: 2026-07-01T00:00:00Z
status: human_needed
score: 6/6 must-haves verified (static analysis)
overrides_applied: 0
human_verification:
  - test: "Login as a user with pareceres:create, navigate to /pareceres, click 'Nova Solicitação', fill the form (select cliente, optionally processo/prazo/advogado, write descricao >= 10 chars), submit."
    expected: "Toast 'Solicitação de parecer criada com sucesso.' appears; browser redirects to /pareceres/{newId}; navigating back to /pareceres shows the new solicitação in the list without a manual refresh."
    why_human: "Requires a running frontend+backend+DB stack and browser interaction; cannot be verified by static grep/tsc analysis alone."
  - test: "Login as a user WITHOUT pareceres:create, navigate directly to /pareceres/nova via URL."
    expected: "AccessDeniedState renders with message 'Não tem permissão para criar solicitações de parecer.' — no form is shown; 'Nova Solicitação' button is absent from /pareceres header for this user."
    why_human: "Requires a second test user/role and browser session; RBAC gating logic is statically confirmed but the rendered UI behavior needs a live check."
  - test: "Select Cliente A, select a Processo belonging to A, then change the cliente select to Cliente B without touching processo, then submit."
    expected: "processoId field is silently reset (WR-01 fix); form either submits with no processoId or user must reselect; no cross-cliente processo/cliente pairing is persisted. If bypassed and sent anyway, backend now returns 400 'processoId não pertence ao cliente indicado' (WR-02 fix)."
    why_human: "Confirms the WR-01/WR-02 fix behaves correctly end-to-end with real network requests and DOM state, beyond the static code read of the useEffect and backend check."
---

# Phase 66: Criação de Solicitação Verification Report

**Phase Goal:** Utilizador consegue iniciar o ciclo de vida de um parecer diretamente na aplicação, atribuindo um advogado responsável.
**Verified:** 2026-07-01
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador com `pareceres:create` vê o botão "Nova Solicitação" no header de `/pareceres` e chega ao formulário | ✓ VERIFIED | `web/src/app/(dashboard)/pareceres/page.tsx:100-104` — `canCreatePareceres` gates a `Button asChild` `Link` to `/pareceres/nova`; `pareceres` is a registered scope in `web/src/lib/permissions.ts:12` |
| 2 | Utilizador sem `pareceres:create` não vê o botão e, ao aceder a `/pareceres/nova` diretamente, vê `AccessDeniedState` | ✓ VERIFIED | `nova/page.tsx:31-45` — `ParecerCreatePage` returns `<AccessDeniedState description="Não tem permissão para criar solicitações de parecer." backHref="/pareceres" />` when `!permissions.isLoading && !canCreatePareceres`, matching backend `@PreAuthorize("hasAuthority('pareceres:create')")` at `ParecerController.java:99` |
| 3 | Utilizador seleciona cliente (obrigatório), opcionalmente processo, escreve descrição, define prioridade/prazo e opcionalmente atribui advogado | ✓ VERIFIED | `nova/page.tsx:126-208` renders all six fields with native selects/inputs sourced from `useClientes`, `useProcessos`, `useAdminUsers`; schema in `web/src/schemas/pareceres.ts:13-37` enforces `clienteId` required, `descricao` required (superRefine dual-message), others optional, `prioridade` defaults `MEDIA` |
| 4 | Submissão com sucesso cria a solicitação (`POST /pareceres/solicitacoes`), mostra toast e redireciona para `/pareceres/{id}` | ✓ VERIFIED | `nova/page.tsx:83-100` — `onSubmit` calls `createParecer.mutateAsync(values)`, then `toast.success(...)` + `router.push('/pareceres/${created.id}')`; `use-pareceres.ts:74-83` mutationFn POSTs to `/pareceres/solicitacoes`; backend `ParecerController.createSolicitacao` (`ParecerController.java:99-152`) validates cliente/processo/advogado tenant+cross-ownership and persists via allowlist, returning the created entity |
| 5 | A nova solicitação aparece imediatamente na lista `/pareceres` (cache `['pareceres','list']` invalidada) | ✓ VERIFIED | `use-pareceres.ts:80-82` — `onSuccess` awaits `queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] })`; list query key in `usePareceres` (`use-pareceres.ts:29`) is `["pareceres","list",...]`, a superset match for TanStack Query invalidation |
| 6 | O select de advogado só lista utilizadores do tenant atual com papel ADVOGADO | ✓ VERIFIED | `nova/page.tsx:78-81` — `advogados = (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO"))`; `useAdminUsers()` is tenant-scoped server-side (same hook/pattern already verified in Phase 65); backend independently re-validates via `validateAdvogado` at `ParecerController.java:142-146`, rejecting non-ADVOGADO/cross-tenant ids with 400 |

**Score:** 6/6 truths verified (static analysis + code review artifacts)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/schemas/pareceres.ts` | `parecerCreateFormSchema` + `ParecerCreateFormValues` | ✓ VERIFIED | Both exported (lines 13, 39); dual-message `superRefine` for `descricao` confirmed present (lines 16-33) |
| `web/src/hooks/use-pareceres.ts` | `useCreateParecer` mutation hook | ✓ VERIFIED | Exported (line 71-84); POSTs allowlisted 6-field `ParecerCreateRequest`, invalidates `["pareceres","list"]` |
| `web/src/app/(dashboard)/pareceres/nova/page.tsx` | Create form page gated by `pareceres:create` | ✓ VERIFIED | 234 lines (exceeds `min_lines: 120`); full form with all fields, access-guard, submit/error handling |
| `web/src/app/(dashboard)/pareceres/page.tsx` | "Nova Solicitação" CTA gated by `pareceres:create` | ✓ VERIFIED | Line 100-104, contains `/pareceres/nova` route link, gated by `canCreatePareceres` |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | Create endpoint + cliente/processo cross-check (WR-02 fix) | ✓ VERIFIED | `processoBelongsToCliente` helper (lines 93-97) wired into both `createSolicitacao` (line 121) and `updateSolicitacao` (line 231) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `nova/page.tsx` | `useCreateParecer` | `mutateAsync` in `onSubmit` | ✓ WIRED | `createParecer.mutateAsync(values)` at line 90 |
| `use-pareceres.ts` | `/pareceres/solicitacoes` | `apiFetch` POST | ✓ WIRED | Line 76-79, `method: "POST"`, body is `payload satisfies ParecerCreateRequest` |
| `use-pareceres.ts` | `["pareceres","list"]` | `invalidateQueries` onSuccess | ✓ WIRED | Line 80-82 |
| `nova/page.tsx` | cliente → processo select | `React.useEffect` reset on `clienteIdValue` change | ✓ WIRED | Lines 73-76 (WR-01 fix, confirmed in code, not just SUMMARY claim) |
| `ParecerController.createSolicitacao` | processo/cliente cross-check | `processoBelongsToCliente` | ✓ WIRED | Line 121 (WR-02 fix, confirmed in code) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `nova/page.tsx` clientes select | `clientes.data` | `useClientes({})` → real TanStack Query fetch to backend | Yes | ✓ FLOWING |
| `nova/page.tsx` processos select | `processos.data` | `useProcessos({cliente_id})` → real fetch, re-queries on cliente change | Yes | ✓ FLOWING |
| `nova/page.tsx` advogados select | `adminUsers.data` filtered | `useAdminUsers()` → real fetch, tenant-scoped server-side | Yes | ✓ FLOWING |
| `page.tsx` list (post-create) | `pareceres.data` | `usePareceres(filters)` → real fetch, refetches on cache invalidation | Yes | ✓ FLOWING |

No hardcoded/static empty-array stubs found in the touched files; all selects are sourced from live TanStack Query hooks already established in Phase 65.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PARC-13 | 66-01-PLAN.md | Utilizador pode criar solicitação via formulário (cliente, processo opcional, advogado via user-picker) | ✓ SATISFIED | All 6 truths above verified; create form + hook + backend endpoint fully wired |

No orphaned requirements found — REQUIREMENTS.md traceability table maps only PARC-13 to Phase 66, and it is claimed in `66-01-PLAN.md` frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `nova/page.tsx` | 56-57 | `zodResolver(...) as any` + eslint-disable | ℹ️ Info | Documented, pre-existing pattern reused from `processos/[id]/page.tsx` for the same `z.default()` input/output type mismatch; confirmed as consistent, not a new stub |
| — | — | No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER found in the 4 touched frontend files or the backend controller diff | — | — |

Code review (`66-REVIEW.md`) found two real Warning-level issues (WR-01 stale processoId, WR-02 missing backend cross-check) — both confirmed FIXED in the current code (`66-REVIEW-FIX.md`, verified independently above, not merely trusted). Three Info-level items (IN-01 unvalidated prazo format, IN-02 duplicate usePermissions calls, IN-03 silent advogado fetch-error) remain open but are non-blocking cosmetic/robustness notes, consistent with the review's own classification (no critical findings, standard-depth review, `status: issues_found` resolved to WR items only).

### Behavioral Spot-Checks

Step 7b: SKIPPED — no running dev server/DB available in this environment; `tsc --noEmit` was run as a static compile-correctness check instead (see below).

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Frontend type-checks cleanly (all 4 touched files + dependents) | `cd web && pnpm exec tsc --noEmit` | No output (exit clean) | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED — no `scripts/*/tests/probe-*.sh` files exist in this repository and neither the PLAN nor SUMMARY reference probe-based verification for this phase.

### Human Verification Required

### 1. End-to-end create flow with live redirect and list update

**Test:** Login as a user with `pareceres:create`, go to `/pareceres`, click "Nova Solicitação", fill in cliente + descricao (≥10 chars), submit.
**Expected:** Success toast appears, browser redirects to `/pareceres/{newId}`, and the new solicitação shows up in `/pareceres` list immediately (no manual refresh needed).
**Why human:** Requires a live backend/DB and browser session; cache invalidation and toast/redirect UX cannot be fully confirmed via static code reading alone.

### 2. Access-denied path for a user without `pareceres:create`

**Test:** Login as a user without `pareceres:create`, navigate directly to `/pareceres/nova`.
**Expected:** `AccessDeniedState` renders with the correct copy; the "Nova Solicitação" CTA is absent on `/pareceres` for this same user.
**Why human:** RBAC gating logic is confirmed in source but needs a live role-differentiated session to observe the rendered result.

### 3. WR-01/WR-02 stale-processoId fix behavior end-to-end

**Test:** Select cliente A + a processo of A, switch cliente to B, attempt submit (with and without a crafted direct API call bypassing the UI).
**Expected:** UI resets processoId on cliente change (WR-01); if bypassed, backend rejects mismatched cliente/processo pairing with 400 "processoId não pertence ao cliente indicado" (WR-02).
**Why human:** Confirms the fix behaves correctly with real network requests and timing, beyond the static useEffect/backend-check code read already performed above.

### Gaps Summary

No blocking gaps found in static analysis. All 6 must-have truths, all 5 required artifacts, and all 5 key links are verified present, substantive, and wired — including the two review-flagged Warning issues (WR-01, WR-02), which were independently re-confirmed as fixed in the current code rather than trusted from `66-REVIEW-FIX.md`'s narrative. `tsc --noEmit` is clean. Three Info-level review items remain open (unvalidated prazo format, duplicate usePermissions calls, silent advogado-fetch error state) but are non-blocking per the review's own classification and do not affect the phase goal.

Status is `human_needed` rather than `passed` solely because this phase's goal requires live browser/DB verification of end-to-end behavior (form submission → toast → redirect → list refresh, RBAC-denied rendering, and the WR-01/WR-02 fix's live behavior) that cannot be exercised without a running stack in this environment.

---

_Verified: 2026-07-01_
_Verifier: Claude (gsd-verifier)_
