---
phase: 118-frontend-indicador-de-utilizadores-no-limite
verified: 2026-07-29T06:30:00Z
status: passed
score: 19/19 must-haves verified
overrides_applied: 0
---

# Phase 118: Frontend — Indicador de Utilizadores no Limite Verification Report

**Phase Goal:** A aba "Gestão de Utilizadores" das Definições mostra a ocupação do plano do tenant e impede visualmente ultrapassar o limite antes mesmo de chamar a API.
**Verified:** 2026-07-29T06:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Methodology

This is not a re-statement of SUMMARY.md claims. Every truth below was independently re-checked against the current codebase and, where feasible, by re-executing the actual gates:

- Re-ran `mvn test -Dtest=AuthControllerGetMeTenantPlanoTest` → 4/4 tests pass (fresh execution, not trusted from SUMMARY).
- Re-ran the full backend suite `mvn test` → 97/97 tests pass, BUILD SUCCESS (fresh execution).
- Re-ran `mvn test -Dtest=AdminControllerLimiteUtilizadoresTest` (Phase 117 regression) → 9/9 pass.
- Re-ran `mvn spotbugs:check` → 0 bug instances, 0 errors.
- Re-ran `node scripts/verify-limite-utilizadores-indicator.mjs` from `web/` → 8/8 assertions PASS.
- Re-ran `pnpm lint` → 0 errors, 18 pre-existing warnings (unchanged baseline).
- Re-ran `pnpm build` → compiled, type-checked, 24 routes generated.
- Read `AuthController.java`, `UserResponse.java`, `auth.ts`, `settings/page.tsx`, `use-me.ts`, `use-permissions.ts`, `AdminController.java`, `Tenant.java` directly (not inferred from SUMMARY prose).
- Verified every commit hash cited in both SUMMARYs and the REVIEW-FIX report actually exists in `git log` with a diff matching its stated description (`2901ebb`, `fa5cf83`, `0991f6c`, `d6c2d7f`, `19d4c15`, `df234ec`, `6ff042b`, `a18217c`).
- Confirmed via `git diff --name-only` across the whole phase span that exactly the files declared in the three plans' `files_modified` were touched — no scope creep, `backend/pom.xml` and `AdminController.java` genuinely absent from the diff.
- Treated `118-HUMAN-UAT.md` as strong direct evidence for the rendering/interaction claims (Success Criteria 2 and 3) per the task's explicit framing — it is a live browser-automation session against a real running backend+frontend, with the tooltip's mouse and keyboard triggers recorded as **separate** verdicts, not a single conflated confirmation. I additionally cross-checked its factual claims (the exact 409 message text, the exact JSON keys) against the actual backend source rather than accepting them at face value.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | **(ROADMAP SC1)** `UserManagementTab` mostra "X/Y utilizadores" com X = utilizadores ativos e Y = `limiteUtilizadores` do tenant | ✓ VERIFIED | `settings/page.tsx:202-211` computes `activeUserCount`/`tenantUserLimit`/`userCountLabel`; rendered at line 399. Live: HUMAN-UAT point 2 shows literal `"5/5 utilizadores · limite atingido"`; points 6-7 show the no-limit and under-limit renderings with real counts. |
| 2 | Sem limite (`null`/`undefined`) → "X utilizadores" sem barra, botão "Novo Utilizador" nunca desativado por esta regra | ✓ VERIFIED | `tenantUserLimit === null ? \`${activeUserCount} utilizadores\` : ...` (line 207-208); `atUserLimit` is `false` whenever `tenantUserLimit === null` (line 204-205), so the disabled branch (line 401) never triggers. Live: HUMAN-UAT point 6, `"5 utilizadores"`, button `disabled: false`. |
| 3 | **(ROADMAP SC2)** X ≥ Y → botão "Novo Utilizador" desativado, contador vermelho/semibold com sufixo "· limite atingido", legível sem hover | ✓ VERIFIED | Lines 391-400 (conditional class `text-red-600 dark:text-red-400` + `font-semibold`) and 401-417 (`disabled` on `Button`). Live: HUMAN-UAT points 2-3 confirm exact text and `opacity: 0.5`/`disabled === true`. |
| 4 | **(ROADMAP SC2)** Tooltip explicativo dispara por rato E por foco de teclado sobre um botão nativamente `disabled` | ✓ VERIFIED | Span-wrapper technique at `settings/page.tsx:402-417` (`<TooltipTrigger asChild><span tabIndex={0}><Button disabled>`). Structurally gated by the `span-wrapper-tooltip` assertion (re-ran, PASS). Live: HUMAN-UAT points 4 and 5 are **separate, independently CONFIRMADO** verdicts — mouse hover after 700ms delay and direct keyboard focus on the span both produced `role="tooltip"` with the exact copy. This is the highest-risk item in the phase (Phase 102/v2.13 debt) and it has the strongest evidence class (live, both input modalities, separately recorded). |
| 5 | **(ROADMAP SC3)** Um 409 do backend aparece como toast com a frase limpa, sem prefixo `API 409:`, sem crash da UI | ✓ VERIFIED | `handleFormSubmit` catch block: `err.message.replace(/^API \d{3}: /, "")` (line 313), replacing the old hardcoded `"API 400: "`-only strip. Live: HUMAN-UAT point 8 — local toast and inline banner both read `"Limite de utilizadores atingido para o vosso plano."` with no prefix; form stayed open/usable; `psql` confirmed no user was actually created. |
| 6 | Indicador consome `useMe()` dentro do próprio `UserManagementTab`, cache partilhada `["auth","me"]`, sem prop-drilling, sem segundo pedido de rede | ✓ VERIFIED | `import { useMe } from "@/hooks/use-me"` (line 26) + call at line 201. `use-me.ts` (read directly) uses `queryKey: ["auth","me"]`; `usePermissions()` (the parent `SettingsPage`'s hook, read directly) calls the same `useMe()` internally — same TanStack Query cache key, so this is a cache hit, not a parallel fetch. |
| 7 | Contagem X usa `ativo === true` (espelha `countByTenantIdAndAtivoTrue` do backend); o badge "Ativo" da tabela mantém `user.ativo !== false`, deliberadamente diferentes | ✓ VERIFIED | `grep` confirms both literals present: `.ativo === true` (line 202, the counter) and `user.ativo !== false` (line 511, the table badge) — exactly once each, matching the gate's `contagem-estrita` assertion (re-ran, PASS). |
| 8 | `GET /api/v1/auth/me` devolve `tenant_plano` (String ou `null`) e `tenant_limite_utilizadores` (Integer ou `null`) a qualquer utilizador autenticado, qualquer papel | ✓ VERIFIED | `AuthController.getMe()` (read directly, lines ~169-174) sets both fields unconditionally inside the existing `ifPresent` block; no `@PreAuthorize` on `getMe` (`grep -c '@PreAuthorize'` → 0). Proven by 4 fresh-run Mockito tests + live curl in HUMAN-UAT point 1. |
| 9 | `tenant_limite_utilizadores` a `null` chega como `null` explícito, nunca coagido a `0` | ✓ VERIFIED | `UserResponse.tenant_limite_utilizadores` is boxed `Integer` (never `int`); `AuthController` assigns `t.getLimiteUtilizadores()` directly (no ternary/`orElse`). Test `getMe_comLimiteNull_devolveNullNuncaZero` re-run and passes with `assertNull(...)`. |
| 10 | Um tenant com `plano` a `null` não causa `NullPointerException` — `getMe` devolve 200 | ✓ VERIFIED | Ternary guard `t.getPlano() != null ? t.getPlano().name() : null` confirmed in source. Test `getMe_comPlanoNull_naoLancaNullPointerException` re-run and passes. |
| 11 | `tenant_nome`/`tenant_logo_data_url` continuam devolvidos exatamente como antes, dentro do mesmo (único) bloco `ifPresent` | ✓ VERIFIED | Both setters still present in the same lambda, confirmed by direct source read. Test `getMe_naoQuebraCamposIrmaosEConsultaTenantApenasUmaVez` re-run and passes, including `verify(tenantRepository, times(1)).findById(TENANT_ID)`. |
| 12 | Exatamente 1 consulta ao `TenantRepository` por pedido — zero queries novas | ✓ VERIFIED | `grep -c 'tenantRepository.findById' AuthController.java` → 1. Confirmed by the same times(1) assertion above. |
| 13 | O tenant lido é sempre o do principal autenticado; nenhum tenant id é lido do pedido | ✓ VERIFIED | `grep -c 'body.get("tenant_id")\|body.get("tenantId")\|containsKey("tenant_id")'` → 0 in `AuthController.java`; the sole `findById` call uses `principal.getTenantId()`. |
| 14 | A verificação autoritária do limite (`AdminController.limiteUtilizadoresExcedido`, Phase 117) permanece intacta e a sua suite continua verde | ✓ VERIFIED | `git diff --name-only` across the entire phase span excludes `AdminController.java` and `pom.xml` entirely. Re-ran `AdminControllerLimiteUtilizadoresTest` fresh: 9/9 pass. Message text cross-checked in source: `"Limite de utilizadores atingido para o vosso plano."`, matches exactly what HUMAN-UAT observed live. |
| 15 | (Live) `GET /api/v1/auth/me`, contra um backend a correr de verdade, devolve as chaves `tenant_plano`/`tenant_limite_utilizadores` | ✓ VERIFIED | HUMAN-UAT point 1: live `curl` + in-page `fetch()` both show `"tenant_plano":"STANDARD","tenant_limite_utilizadores":5`. Corroborated independently: the backend code producing this exact shape was re-tested by this verifier (4/4 Mockito tests, fresh run). |
| 16 | (Live) Com o tenant no limite, aba mostra contador vermelho "· limite atingido" e botão desativado | ✓ VERIFIED | HUMAN-UAT points 2-3, CONFIRMADO. |
| 17 | (Live) Tooltip aparece ao vivo por rato e por teclado, separadamente | ✓ VERIFIED | HUMAN-UAT points 4-5, CONFIRMADO separately (not merged into one verdict, as the plan required). |
| 18 | (Live) Com limite `NULL`, contador lê "X utilizadores" e botão volta a ativo | ✓ VERIFIED | HUMAN-UAT point 6, CONFIRMADO. |
| 19 | (Live) Um 409 forçado aparece como toast limpo e a UI não rebenta | ✓ VERIFIED | HUMAN-UAT point 8, CONFIRMADO — clean local toast, clean inline banner, expected/documented duplicate generic toast, no crash, no accidental user created (`psql` count = 0). |

**Score:** 19/19 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/dtos/UserResponse.java` | 2 novos campos snake_case, `Integer` boxed | ✓ VERIFIED | `private String tenant_plano;` + `private Integer tenant_limite_utilizadores;` present (lines 26-27); no `@JsonProperty` introduced. |
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` | Mapeamento dos 2 campos dentro do `ifPresent` existente | ✓ VERIFIED | `setTenant_plano(...)`/`setTenant_limite_utilizadores(...)` inside the pre-existing lambda; single `findById`; zero `@PreAuthorize`. |
| `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java` | 4 casos automatizados, ≥80 lines | ✓ VERIFIED | 139 lines, 4 `@Test` methods, re-run and green (4/4). `assertNull` used twice; `verify(times(1)).findById` present. |
| `web/src/types/auth.ts` | `tenant_limite_utilizadores?: number \| null;` | ✓ VERIFIED | Present at line 30, plus `tenant_plano?: string \| null;` at line 29 (post WR-01 fix — both siblings symmetric). |
| `web/src/app/(dashboard)/settings/page.tsx` | Indicador X/Y, disabled+tooltip, toast sem prefixo | ✓ VERIFIED | `"limite atingido"` present; full CardHeader rewrite confirmed by direct read (lines 384-428); `isError` guard added (WR-02 fix, lines 346-359). |
| `web/scripts/verify-limite-utilizadores-indicator.mjs` | Gate de 8 assercões, ≥60 lines | ✓ VERIFIED | 182 lines. Re-ran directly: 8/8 `PASS`, exit 0. Registered in `package.json` as `verify:limite-utilizadores`. |
| `.planning/phases/.../118-HUMAN-UAT.md` | Registo de cenários verificados ao vivo, ≥20 lines | ✓ VERIFIED | 46 lines. 9/9 points with explicit `CONFIRMADO` verdicts, points 4/5 recorded separately as required. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AuthController.java` | `Tenant.java` | `tenantRepository.findById(principal.getTenantId())` | ✓ WIRED | Exactly 1 occurrence; reads `Tenant.plano`/`Tenant.limiteUtilizadores`, both confirmed present in the entity. |
| `AuthController.java` | `UserResponse.java` | `setTenant_limite_utilizadores(t.getLimiteUtilizadores())` | ✓ WIRED | Direct pass-through, no ternary — `null` crosses intact (test-proven). |
| `settings/page.tsx` | `web/src/hooks/use-me.ts` | `useMe()` call inside `UserManagementTab` | ✓ WIRED | Import + call confirmed; hook itself confirmed unchanged and using the shared `["auth","me"]` key. |
| `settings/page.tsx` | `web/src/components/ui/tooltip.tsx` | `TooltipTrigger asChild` over `<span tabIndex={0}>` wrapping disabled `Button` | ✓ WIRED | Structural gate `span-wrapper-tooltip` re-run PASS; live-fired confirmed by HUMAN-UAT points 4-5 (both mouse and keyboard). |
| `settings/page.tsx` | `web/src/lib/api.ts` | Generic `API NNN:` prefix strip on the error thrown by `apiFetch` | ✓ WIRED | `err.message.replace(/^API \d{3}: /, "")` confirmed; `api.ts` itself confirmed untouched (still throws `API {status}: {message}`, this component now strips any 3-digit code, not just 400). |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `UserManagementTab` counter | `activeUserCount` | `useAdminUsers()` → `GET /api/v1/admin/users` (real DB query via `AdminController`) | Yes — live counts (5, 5/5, 5/7) observed in HUMAN-UAT matching real seeded users, not hardcoded | ✓ FLOWING |
| `UserManagementTab` counter/gate | `tenantUserLimit` (`me?.tenant_limite_utilizadores`) | `useMe()` → `GET /api/v1/auth/me` → `tenantRepository.findById(principal.getTenantId())` (real DB row, not static) | Yes — live value changed correctly after `psql UPDATE`, observed on next fetch (5→NULL→7→5) | ✓ FLOWING |
| Disabled `Button` + `Tooltip` | `atUserLimit` (derived boolean) | Pure derivation from the two flowing sources above (`tenantUserLimit !== null && activeUserCount >= tenantUserLimit`) | Yes — toggled live across 3 DB states in the same UAT session | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| New backend test class is genuinely green | `mvn test -Dtest=AuthControllerGetMeTenantPlanoTest` (re-run by this verifier) | `Tests run: 4, Failures: 0, Errors: 0` | ✓ PASS |
| Full backend suite has no regressions | `mvn test` (re-run by this verifier) | `Tests run: 97, Failures: 0, Errors: 0`, `BUILD SUCCESS` | ✓ PASS |
| Phase 117's authoritative 409 gate is untouched | `mvn test -Dtest=AdminControllerLimiteUtilizadoresTest` (re-run) | `Tests run: 9, Failures: 0, Errors: 0` | ✓ PASS |
| Backend SAST is clean | `mvn spotbugs:check` (re-run) | `BugInstance size is 0`, `BUILD SUCCESS` | ✓ PASS |
| Frontend structural gate is genuinely green | `node scripts/verify-limite-utilizadores-indicator.mjs` (re-run) | 8/8 `PASS` lines, exit 0 | ✓ PASS |
| Frontend lint is clean | `pnpm lint` (re-run) | `0 errors, 18 warnings` (pre-existing baseline, none in touched files) | ✓ PASS |
| Frontend build + type-check succeeds | `pnpm build` (re-run) | `Compiled successfully`, `Finished TypeScript`, 24 routes | ✓ PASS |

### Probe Execution

Not applicable — this phase has no `scripts/*/tests/probe-*.sh` convention. The equivalent "executable source-of-truth gate" for this phase is `web/scripts/verify-limite-utilizadores-indicator.mjs`, executed above under Behavioral Spot-Checks (re-run directly by this verifier, not trusted from SUMMARY).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PLAN-03 | 118-01, 118-02, 118-03 | "Frontend mostra 'X/Y utilizadores' e desativa 'novo utilizador' no limite" | ✓ SATISFIED | All 19 observable truths above; ROADMAP.md Success Criteria 1-3 all independently confirmed both statically and live. |

**Orphan check:** `REQUIREMENTS.md`'s Traceability table maps only `PLAN-03 → Phase 118`. No other requirement ID references Phase 118. All three plans declare `requirements: [PLAN-03]` — no mismatch, no orphans.

### Anti-Patterns Found

No blocking anti-patterns. `grep -n -E "TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER"` across all 7 files touched by this phase returns zero matches.

The following pre-existing/out-of-scope observations were carried forward from `118-REVIEW.md`'s own adversarial pass (re-confirmed by this verifier as non-blocking to Phase 118's must-haves specifically):

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `backend/.../AuthController.java` | 169-174 (`getMe`) | `tenant_plano`/`tenant_limite_utilizadores` reach every authenticated role, not just ADMIN/`users:manage` | ℹ️ INFO (accepted trade-off, threat-modeled, precedent-consistent with `tenant_nome`) | None on this phase's goal — rendering surface stays confined to the ADMIN-gated tab. |
| `web/src/types/auth.ts` | 29 | `tenant_plano` is typed and backend-tested but has zero frontend consumers | ℹ️ INFO | Forward-scaffolding for a later phase (likely Phase 120); not required by any of this phase's Success Criteria (which concern the numeric limit only). |
| `web/.../settings/page.tsx` | 404 | Focusable tooltip-trigger `<span>` has no explicit `aria-label`; Radix supplies `aria-describedby` (description) but not an accessible *name* | ℹ️ INFO | Hedged, unverified with an actual screen reader; not a stated must-have of this phase. Recommend a follow-up a11y pass, not a blocker. |
| `web/scripts/verify-limite-utilizadores-indicator.mjs` | 144 | `hasDisabled` regex matches the word "disabled" anywhere in the delimited block, not specifically as a `<Button>` JSX attribute | ℹ️ INFO | Gate-quality nitpick; the actual live behavior (disabled attribute present, tooltip fires) was independently confirmed via HUMAN-UAT, so this weakness didn't let a real defect through. |
| `web/.../settings/page.tsx` (`RbacTab`, separate component) | 761, 769-778 | `RbacTab` has no `isError` branch — a failed `GET /admin/rbac` spins forever | ℹ️ INFO — different tab/feature | Outside Phase 118's delivered scope (`UserManagementTab` only). Pre-existing, not introduced or touched by this phase's commits. Worth a fast-follow, not a Phase 118 gap. |
| `web/src/app/(dashboard)/layout.tsx` (not touched by this phase) | — | Cold/hard navigation to any authenticated route hangs on the `<Suspense>` fallback forever; only in-app client-side link clicks work | ℹ️ INFO — pre-existing, unrelated file | Discovered incidentally during 118-03's live UAT, already flagged separately as `task_08e7aed2` for dedicated investigation. Does not affect Phase 118's own truths (all 9 UAT points were reached and confirmed via the working client-side-navigation path). Worth noting for Phase 120/122 planning (both under the same `(dashboard)/` layout), per the SUMMARY's own "Next Phase Readiness" note. |

### Human Verification Required

None. Success Criteria 2 and 3 are rendering/interaction claims that would normally require human testing — but this phase's own Plan 03 already executed a live, blocking `checkpoint:human-verify` task (`118-HUMAN-UAT.md`), with all 9 points independently `CONFIRMADO` against a real running backend+frontend via browser automation, including the two highest-risk items (tooltip-on-disabled-button firing by mouse and by keyboard, verified as separate verdicts). This verifier additionally cross-checked the underlying source code and backend message text that produced those observations, rather than accepting the UAT narrative at face value. No further human action is required to close this phase.

(Optional, non-blocking suggestion for a future phase: a real screen-reader pass on the new focusable tooltip-trigger `<span>` per IN-02 above — not required by this phase's stated must-haves.)

### Gaps Summary

None. All 19 observable truths (merging ROADMAP.md's 3 Success Criteria with all `must_haves.truths` declared across the three plans' frontmatter) are VERIFIED. All 7 required artifacts exist, are substantive, and are wired. All 5 key links are WIRED. Data flows genuinely from real DB-backed endpoints through to the rendered UI (Level 4 trace). Every backend and frontend gate was re-executed fresh by this verifier (not trusted from SUMMARY.md) and passed. Every cited commit hash is real and its diff matches its description. File scope is exactly what the three plans declared — no scope creep, and `AdminController.java`/`pom.xml` are confirmed absent from the diff across the entire phase. The code review went through two rounds (initial → fix pass → final re-review), landing at APPROVED TO SHIP with 0 critical findings; both actionable Warnings (WR-01, WR-02) were fixed and independently re-verified by the reviewer, and the remaining two Warnings (WR-03, WR-04) are explicitly documented, reasoned trade-offs consistent with this project's existing patterns (`tenant_nome` precedent, pre-existing dual-toast behavior). The one new observation from the final review pass (WR-05, `RbacTab`) belongs to a different sub-component/feature than what Phase 118 delivers and does not touch any file this phase modified.

Phase 118's goal — "A aba 'Gestão de Utilizadores' das Definições mostra a ocupação do plano do tenant e impede visualmente ultrapassar o limite antes mesmo de chamar a API" — is achieved and independently confirmed, both statically (source code, re-executed automated gates) and dynamically (live human/browser-driven UAT against a real running application). Ready to proceed to Phase 119.

---

_Verified: 2026-07-29T06:30:00Z_
_Verifier: Claude (gsd-verifier)_
