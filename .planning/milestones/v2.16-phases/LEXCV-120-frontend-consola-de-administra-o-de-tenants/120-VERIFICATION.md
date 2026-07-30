---
phase: 120-frontend-consola-de-administra-o-de-tenants
verified: 2026-07-29T22:30:00Z
status: passed
score: 4/4 ROADMAP success criteria verified (33/33 plan-level must-have truths across 6 plans)
overrides_applied: 0
re_verification: false
---

# Phase 120: Frontend — Consola de Administração de Tenants Verification Report

**Phase Goal:** O administrador de plataforma tem um ecrã interno, não público, onde cria novos tenants (com o respetivo ADMIN inicial), lista todos os tenants existentes e a sua utilização, ajusta plano/limite de qualquer um, e suspende quem não pague — bloqueando-lhe o acesso de imediato.
**Verified:** 2026-07-29T22:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

**Adversarial stance applied:** started from the hypothesis that the phase goal was not achieved and that the narrative of "1 Critical + 3 Warnings found, all fixed, 172/172 green, live UAT 10/10 CONFIRMED" was executor self-reporting, not proof. Did not trust `120-01..06-SUMMARY.md`, `120-REVIEW.md`, or `120-HUMAN-UAT.md` claims on their word — independently re-read every production file changed by this phase line-by-line, independently re-ran the full backend test suite, SpotBugs, frontend lint, frontend production build, and the phase's own executable structural gate (`pnpm verify:consola-tenants`) from a fresh shell in this session, independently confirmed every commit hash cited in the phase narrative actually exists via `git show`, and specifically hunted for the class of gap this codebase's own history flags as recurring (fail-open UI guards, orphaned migrations, duplicated validation, wiring gaps) rather than accepting "REVIEW.md says resolved" at face value.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ecrã interno, acessível só a `PLATAFORMA_ADMIN` (nunca ao `ADMIN` de um tenant normal), cria um novo tenant preenchendo nome + dados do utilizador ADMIN inicial, reutilizando o serviço de backend da Phase 119 | ✓ VERIFIED | `web/src/app/(dashboard)/plataforma/page.tsx:76-91` — `useMe()` guard: `if (!me.isFetched) return null;` (fail-closed while loading, WR-01/WR-03-fixed) then `if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) return <AccessDeniedState .../>`. Backend-authoritative gate: `PlatformAdminController.java:53` class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`, confirmed NOT in `SecurityConfig.java`'s 6-path `permitAll()` allowlist (read in full, zero matches for `/api/v1/platform`). `createTenant` (`PlatformAdminController.java:68-94`) delegates to `SetupService.provisionTenant()` (`SetupService.java:102-131`), which reuses the shared private `validateRequest()` — same helper `initializeSystem` uses (Phase 119 contract, unbroken). `CriarTenantPanel` (`criar-tenant-panel.tsx:57-65`) imports `setupSchema`/`SetupFormValues` from `@/schemas/setup` verbatim (confirmed `git log` shows `setup.ts` untouched by any Phase 120 commit). Live-confirmed: `120-HUMAN-UAT.md` point 1 (sidebar shows only "Plataforma", zero tenant modules, desktop+mobile) and point 2 (tenant "Escritorio Teste 120" created via the real UI, appeared in the list without a page reload) and Task 1's curl checks (all 3 `/platform` endpoints return `403` for an `admin@lexcv.cv` session). |
| 2 | O mesmo ecrã lista todos os tenants existentes, mostrando o número de utilizadores ativos de cada um | ✓ VERIFIED | `PlatformAdminController.listTenants()` (`:106-113`) maps every `Tenant` via a shared `toSummary()` helper (`:191-200`) that sources `utilizadoresAtivos` from `userRepository.countByTenantIdAndAtivoTrue(tenant.getId())` — confirmed as the sole definition of this method in `UserRepository.java:38` (Phase 117's single source of truth, explicitly documented as reused by "Phases 120 and 122"). Frontend: `useTenantsAdmin()` (`use-platform-admin.ts:21-30`) feeds both the desktop `<DataTable columns={tenantColumns} data={tenantsFiltrados} .../>` (`page.tsx:313`) and the mobile card block (`page.tsx:192-309`), both rendering `{utilizadoresAtivos}/{limiteUtilizadores}` or `"{n} · sem limite"` (`columns.tsx:163-193`). Live-confirmed: `120-HUMAN-UAT.md` point 2 (3 rows: "LexCV" with "Plataforma" badge, demo tenant, "Escritorio Teste 120", each with plano/estado/occupancy) and point 3 (`1/1` red "limite atingido" after setting limit=1 on a tenant with exactly 1 active user). |
| 3 | Administrador de plataforma edita `plano`/`limiteUtilizadores` de qualquer tenant a partir desse ecrã | ✓ VERIFIED | `PUT /api/v1/platform/tenants/{id}` (`PlatformAdminController.updateTenant`, `:126-147`): validates `plano` non-null (`400` "O plano é obrigatório."), `limiteUtilizadores` either `null` or `>=1` (`400` with exact message), then `tenant.setPlano(...)`/`setLimiteUtilizadores(...)` + `tenantRepository.save(...)`, returns `200` with the updated summary. Frontend: `EditarTenantDialog`/`EditarTenantForm` (`page.tsx:386-510`) + `useUpdateTenant()` mutation (`use-platform-admin.ts:47-60`), which `invalidateQueries(TENANTS_LIST_KEY)` on success. CR-01 defense-in-depth: `EditarTenantForm` seeds `useState<TenantPlano>(tenant.plano ?? PLANO_OPTIONS[0])` (`page.tsx:432`) so the `<NativeSelect>` can never desync from a legacy-null `plano` value. Proven by `PlatformAdminControllerTest` cases (e.g. `updateTenant_com...` ArgumentCaptor asserting saved `plano`/`limiteUtilizadores`, `null`-limit accepted, `0`-limit rejected, `null`-plano rejected, id-not-found 404) — independently re-ran, all pass. Live-confirmed: `120-HUMAN-UAT.md` point 3 (STANDARD + limit 1 saved, toast "Tenant atualizado com sucesso.", row updates to `1/1`; limit cleared, row reads "1 · sem limite"). |
| 4 | Administrador de plataforma alterna o estado suspenso/ativo de um tenant a partir desse ecrã; um tenant suspenso deixa imediatamente de conseguir autenticar-se ou continuar a usar uma sessão já ativa | ✓ VERIFIED | `PATCH /api/v1/platform/tenants/{id}/ativo` (`PlatformAdminController.setTenantAtivo`, `:165-184`) sets `Tenant.ativo`, guarded against suspending the reserved `"LexCV"` tenant (`400`, exact UI-SPEC phrase). Enforcement traced through all 3 access paths, each independently re-read: (a) `AuthController.login` (`:107-119`) — gate runs *after* the account-disabled gate, `403` "O acesso da sua organização está suspenso..."; (b) `AuthController.refresh` (`:171-182`) — its own gate because `/auth/refresh` is `permitAll()` and never passes through the filter, `401` "Sessão inválida..."; (c) `JwtAuthenticationFilter.doFilterInternal` (`:44-59`) — re-resolves `Tenant` on **every** authenticated request and requires `Boolean.TRUE.equals(tenant.getAtivo())` before populating `SecurityContextHolder`, with zero caching (verified: `grep -ci cache` = 0). All three use the identical null-safe `Boolean.TRUE.equals(...)` fail-closed pattern. Proven by 7 (`JwtAuthenticationFilterTenantSuspensoTest`) + 6 (`AuthControllerTenantSuspensoTest`) independently re-run Mockito tests covering happy path, suspended tenant, null-`ativo` tenant, missing tenant, disabled user, and the never-short-circuits-the-chain guarantee. **Live-confirmed as a real-time, cross-session event, not just statically reasoned:** `120-HUMAN-UAT.md` point 5 (PROVA CENTRAL) — a genuinely separate authenticated HTTP session (isolated cookie jar, never re-logged-in) received `200` on `GET /auth/me` before suspension and `403` on the very next request after suspending the tenant in a different window, measured at **~1.06 seconds** elapsed, and point 6 confirms a fresh login attempt with correct credentials is also rejected with the suspension-specific message (not "Credenciais inválidas"). Point 9 confirms reactivation restores access immediately, both for the still-open old session and a fresh login. |

**Score:** 4/4 ROADMAP success criteria verified.

### Plan-Level Must-Have Truths (supporting detail, 33 total across 6 plans)

All plan-level `must_haves.truths` entries were checked against the current (post-review-fix) code and its tests/live evidence. Condensed by plan.

**Plan 01 (PROV-05) — `Tenant.ativo` + 3 enforcement points, 5/5 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `Tenant.ativo` persisted `Boolean`, `NOT NULL DEFAULT TRUE`, same name/polarity as `User.ativo` | ✓ | `Tenant.java:70-72`: `@Column(name="ativo", nullable=false, columnDefinition="boolean not null default true") @Builder.Default private Boolean ativo = true;` |
| Session already open at suspension time is rejected on its very next request, no logout/re-login | ✓ | `JwtAuthenticationFilter.java:44-59`, re-resolves tenant per request, no cache; live-proven at ~1.06s (`120-HUMAN-UAT.md` pt.5) |
| `POST /auth/login` → `403` for a suspended tenant, correct credentials | ✓ | `AuthController.java:107-119`; `AuthControllerTenantSuspensoTest.login_comTenantSuspenso_devolve403ENuncaGeraTokens` |
| `POST /auth/refresh` → `401`, public route not bypassing suspension | ✓ | `AuthController.java:171-182`; `AuthControllerTenantSuspensoTest.refresh_comTenantSuspenso_devolve401ENuncaGeraNovosTokens` |
| Active-tenant user still authenticates/navigates with zero regression | ✓ | Happy-path cases in both new test files pass; full 172/172 suite green (no pre-existing test broken) |

**Plan 02 (PROV-03/04/05) — `PlatformAdminController` 3 new endpoints, 6/6 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `GET /platform/tenants` returns all tenants with active-user counts | ✓ | `listTenants()` + `toSummary()`, `PlatformAdminController.java:106-113,191-200` |
| `PUT /platform/tenants/{id}` adjusts `plano`/`limiteUtilizadores`, `null` = no limit | ✓ | `updateTenant()`, `:126-147`; Casos with `ArgumentCaptor` prove saved values |
| `PATCH /platform/tenants/{id}/ativo` toggles suspended/active | ✓ | `setTenantAtivo()`, `:165-184` |
| Suspending reserved "LexCV" tenant rejected with `400` + explicit message | ✓ | `:176-178`; `setTenantAtivo_comFalseSobreLexCVDevolve400ComMensagemExataENuncaChamaSave` |
| A tenant `ADMIN` gets `AccessDeniedException` (403) on all 3 new endpoints | ✓ | 3 dedicated real-AOP-proxy tests (`listTenants_comRoleAdminDeTenantNormalERecusado...`, `updateTenant_...`, `setTenantAtivo_...`), each also asserting zero repository interaction |
| Active-user count uses `countByTenantIdAndAtivoTrue`, no 2nd implementation | ✓ | `toSummary()` is the only call site producing `utilizadoresAtivos`; `grep -c 'findByTenantId('` in the controller = 0 |

**Plan 03 (PROV-02/03/04/05) — frontend data layer + nav item, 5/5 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `Role` union includes `PLATAFORMA_ADMIN`, type-checks | ✓ | `types/auth.ts:2`; `pnpm build`'s own TS check passes |
| 1 query hook + 3 mutation hooks against the 4 `/platform/tenants` endpoints | ✓ | `use-platform-admin.ts` exports exactly `useTenantsAdmin`/`useCreateTenant`/`useUpdateTenant`/`useSetTenantAtivo` |
| All mutations invalidate the list query, never reload the page | ✓ | 3× `invalidateQueries({queryKey: TENANTS_LIST_KEY})`; `grep -c 'window.location.reload'` in the hook file = 0 |
| `PLATAFORMA_ADMIN` sees "Plataforma" nav item, desktop + mobile | ✓ | `dashboard-shell.tsx:91-95,141-146,161-166`; both `<SidebarNav>` call sites receive `navItems` |
| Non-platform-admin never sees it (fails closed while loading, not just after) | ✓ | `isPlatformAdmin = me.data?.roles?.includes(...) ?? false` — `false` default during load; live-confirmed pt.1 |

**Plan 04 (PROV-02/03) — columns + Criar Tenant panel, 5/5 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| 5 columns: nome+avatar, plano badge, X/Y occupancy, Ativo/Suspenso, 2 row actions | ✓ | `columns.tsx:121-220`, exactly 5 `id`s (`nome`/`plano`/`utilizadores`/`estado`/`acoes`) |
| Reserved "LexCV" row shows "Plataforma" badge + disabled suspend w/ tooltip | ✓ | `columns.tsx:139-143,61-82` |
| Disabled-button tooltip fires by mouse and keyboard (`<span tabIndex={0}>`) | ✓ | `columns.tsx:68` composition; live-confirmed `120-HUMAN-UAT.md` pts.7-8 (separate verdicts) |
| Criar Tenant panel reuses `/setup`'s Zod schema verbatim, logo upload+preview | ✓ | `criar-tenant-panel.tsx:20,57-65`; `git log` confirms `schemas/setup.ts` untouched |
| No column/panel calls `window.location.reload` | ✓ | `grep -c` = 0 in both files |

**Plan 05 (PROV-02/03/04/05) — composed `/plataforma` screen + executable gate, 6/6 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `/plataforma` exists inside `DashboardShell`, lists tenants w/ plano/occupancy/estado | ✓ | `page.tsx` full file; `pnpm build` route list includes `○ /plataforma` |
| Non-platform-admin visiting `/plataforma` gets `AccessDeniedState` | ✓ | `page.tsx:82-89`; live-confirmed indirectly (backend 403 on all fetches even during any guard gap) |
| Criar Tenant opens inline panel, creates, list updates, toast, no reload | ✓ | `page.tsx:129-139,154-159`; live-confirmed pt.2 |
| Editar opens Dialog w/ Plano+Limite, saves both together | ✓ | `page.tsx:386-510`; live-confirmed pt.3 |
| Suspender/Reativar open distinct AlertDialogs w/ UI-SPEC copy | ✓ | `page.tsx:344-376,517-557`; live-confirmed pts.5,9 |
| Client-side name search filters list, desktop + mobile | ✓ | `page.tsx:106-111`; live-confirmed pt.2 ("Escritorio" filter) |

Plus the executable gate `pnpm verify:consola-tenants` — grew from the originally-planned 10 assertions to **12** (2 added post-review for WR-03 and CR-01's frontend fixes) — independently re-run this session: **12/12 PASS**, exit code 0.

**Plan 06 (PROV-02/03/04/05) — live human UAT, 6/6 CONFIRMED**

All 6 plan-level truths for this plan map 1:1 onto `120-HUMAN-UAT.md`'s 10 numbered verification points, all marked `CONFIRMADO`, none `FALHOU`. Read the UAT report in full: verdicts are specific (exact HTTP status codes, exact error-message text, a measured ~1.06s timing, explicit "no logout/re-login" attestations for the central scenario), not vague rubber-stamps — this is substantive evidence, not narrative-only completion.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Tenant.java` | `ativo` field, `NOT NULL DEFAULT TRUE` | ✓ VERIFIED | 81 lines. `ativo` (`:70-72`) and `plano` (`:45-48`, CR-01 fix) both `@Builder.Default` with matching `columnDefinition` |
| `backend/migrations/120-add-tenant-ativo.sql` | manual prod migration, ≥20 lines | ✓ VERIFIED | 33 lines, correct header convention, `ALTER TABLE t_tenant ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;` |
| `backend/migrations/120b-backfill-tenant-plano.sql` (review-fix artifact, not originally planned) | CR-01 backfill + `NOT NULL` tightening | ✓ VERIFIED | 32 lines, idempotent `UPDATE ... WHERE plano IS NULL` + 2 `ALTER COLUMN` statements |
| `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` | per-request tenant re-validation | ✓ VERIFIED | 117 lines, `TenantRepository` injected, `Boolean.TRUE.equals(tenant.getAtivo())` gate, zero caching |
| `backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterTenantSuspensoTest.java` | ≥80 lines, behavioral proof | ✓ VERIFIED | 220 lines, 7 tests, all independently re-run green |
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` | login/refresh suspension gates | ✓ VERIFIED | 303 lines, both gates present with exact messages, WR-01 IP-based lockout fix present |
| `backend/src/test/java/com/lexcv/controllers/AuthControllerTenantSuspensoTest.java` | ≥60 lines | ✓ VERIFIED | 209 lines, 6 tests, all pass |
| `backend/src/test/java/com/lexcv/controllers/AuthControllerLoginLockoutTest.java` (review-fix artifact) | WR-01 fix proof | ✓ VERIFIED | 114 lines, 2 tests (6th attempt 429, cross-IP non-sharing), both pass |
| `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java` | 6-field projection | ✓ VERIFIED | 35 lines, exactly 6 fields, no `logoDataUrl`/`nif`/`email`/`telefone`/`createdAt` |
| `backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java` | typed `plano`+`limiteUtilizadores` | ✓ VERIFIED | 26 lines, `TenantPlano` (not `String`), no `ativo` field |
| `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` | 3 new endpoints, class-level gate | ✓ VERIFIED | 201 lines, 4 handlers total, exactly 1 `@PreAuthorize` (class-level), zero `SecurityContextHolder` reads |
| `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` | ≥26 tests | ✓ VERIFIED | 530 lines, 26 tests (confirmed via independent `mvn test` run), all pass |
| `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` | `HttpMessageNotReadableException` → 400 | ✓ VERIFIED | 106 lines, 5 `@ExceptionHandler`s, new one never echoes internal exception detail |
| `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` (review-fix artifact) | WR-02 skip-suspended-tenant fix | ✓ VERIFIED | 355 lines, `if (!Boolean.TRUE.equals(tenant.getAtivo())) continue;` as the first statement in the per-tenant loop |
| `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` | WR-02 fix proof | ✓ VERIFIED | 474 lines, includes 2 dedicated cases proving a suspended tenant is skipped before any repository call, and that a co-processed active tenant is unaffected |
| `web/src/types/platform-admin.ts` | `TenantAdminSummary`/`TenantUpdateRequest` mirroring backend DTOs | ✓ VERIFIED | 28 lines, 4 exported types, field names byte-identical to backend |
| `web/src/hooks/use-platform-admin.ts` | 1 query + 3 mutations, invalidation | ✓ VERIFIED | 75 lines, exactly 4 hooks, 3 `invalidateQueries` calls, zero `window.location.reload` |
| `web/src/components/shared/dashboard-shell.tsx` | gated nav item, 2 call sites | ✓ VERIFIED | 244 lines, `PLATAFORMA_ADMIN` referenced, both `<SidebarNav>` sites use `navItems` |
| `web/src/app/(dashboard)/plataforma/columns.tsx` | 5-column `ColumnDef` factory | ✓ VERIFIED | 220 lines (≥120 required), zero hooks, tooltip-span-wrapper composition present |
| `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx` | inline creation panel | ✓ VERIFIED | 241 lines (≥120 required), imports `setupSchema` verbatim, zero network/mutation calls |
| `web/src/app/(dashboard)/plataforma/page.tsx` | composed screen | ✓ VERIFIED | 557 lines (≥220 required), guard/list/search/create/edit/suspend all present |
| `web/src/lib/tenant-initials.ts` (review-fix artifact, IN-01) | shared initials helper | ✓ VERIFIED | 19 lines, imported by both `columns.tsx` and `page.tsx`, de-duplicating the prior inline logic |
| `web/scripts/verify-consola-tenants.mjs` | executable structural gate | ✓ VERIFIED | 281 lines (≥80 required), 12 assertions (grew from 10 post-review), zero external dependencies, independently re-run: **12/12 PASS**, exit 0 |
| `.planning/phases/LEXCV-120-.../120-HUMAN-UAT.md` | per-scenario verdict, live | ✓ VERIFIED | 48 lines (≥25 required), 10/10 `CONFIRMADO`, 0 `FALHOU` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `JwtAuthenticationFilter.java` | `TenantRepository.java` | per-request `findById(user.getTenantId())` | ✓ WIRED | `:48`, resolved only when a user was found (no wasted query, Caso 7) |
| `AuthController.login`/`refresh` | `Tenant.getAtivo()` | suspension gate after account gate | ✓ WIRED | `:114-119` (login), `:177-182` (refresh), both `Boolean.TRUE.equals(...)` |
| `PlatformAdminController.java` | `UserRepository.countByTenantIdAndAtivoTrue` | `utilizadoresAtivos` in every summary | ✓ WIRED | Single call site in shared `toSummary()`, `:198` |
| `PlatformAdminController.java` | `Tenant.ativo` | `PATCH .../ativo` w/ reserved-tenant guard | ✓ WIRED | `:176-180`, guard precedes `setAtivo` |
| `web/src/hooks/use-platform-admin.ts` | backend `GET/PUT/PATCH /api/v1/platform/tenants` | `apiFetch`, shared query key | ✓ WIRED | All 4 hooks use `/platform/tenants...` paths, zero native `fetch` |
| `dashboard-shell.tsx` | `sidebar-nav.tsx` | derived `navItems` array, both call sites | ✓ WIRED | `sidebar-nav.tsx` confirmed byte-unmodified (`git diff` empty against its last unrelated commit), never learns about `PLATAFORMA_ADMIN` |
| `plataforma/criar-tenant-panel.tsx` | `web/src/schemas/setup.ts` | direct import, no re-derived regex | ✓ WIRED | `criar-tenant-panel.tsx:20`; `schemas/setup.ts` untouched by any Phase 120 commit (`git log`) |
| `plataforma/columns.tsx` | `components/ui/tooltip.tsx` | `<TooltipTrigger asChild><span tabIndex={0}>` over disabled `Button` | ✓ WIRED | `columns.tsx:66-82`; live-confirmed by mouse AND keyboard (`120-HUMAN-UAT.md` pts.7-8) |
| `plataforma/page.tsx` | `web/scripts/verify-consola-tenants.mjs` | `package.json` script `verify:consola-tenants` | ✓ WIRED | `web/package.json:12`; independently re-run this session, 12/12 PASS |
| `AlertasDiariosJob.java` | `Tenant.ativo` | per-tenant `continue` before any repository call | ✓ WIRED | `:101-103`; proven by dedicated test asserting `processoRepository`/`userRepository` never touched for a suspended tenant |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `plataforma/page.tsx` desktop `<DataTable>`/mobile cards | `tenants.data` (via `tenantsFiltrados`) | `useTenantsAdmin()` → `apiFetch<TenantAdminSummary[]>("/platform/tenants")` → `PlatformAdminController.listTenants()` → real `tenantRepository.findAll()` + live `countByTenantIdAndAtivoTrue` per row | Yes — genuine DB-backed data, not static/mock; live-confirmed non-empty (3 rows) in `120-HUMAN-UAT.md` pt.2 | ✓ FLOWING |
| `EditarTenantForm`'s `NativeSelect`(Plano) | `plano` state | Seeded from `tenant.plano ?? PLANO_OPTIONS[0]` (CR-01 fallback) | Yes, with one **known, documented, non-blocking residual**: any tenant row still holding a legacy `plano = NULL` (confirmed to exist in *this* dev DB per `120-HUMAN-UAT.md` pt.10, prior to `120b-backfill-tenant-plano.sql` being run) renders `PLANO_BADGE_VARIANT[null]` → `undefined` → Badge's own `cva` `defaultVariants.variant="secondary"` fallback (desktop) or an incorrectly amber "Enterprise"-colored badge showing literal `null` text (mobile, `page.tsx:218-229`'s ternary has no `null` branch). New tenants (created via this phase's own panel, or via Phase 119's `provisionTenant`/`initializeSystem`, or the seeded "LexCV" tenant) can never hit this because `Tenant.plano` now has `@Builder.Default = STARTER` — this is a **legacy-row-only** gap, closed once the accompanying migration runs. | ⚠️ FLOWING (documented residual on un-migrated legacy rows — see Anti-Patterns) |
| `AlertasDiariosJob.executar` | `tenant.getAtivo()` | `tenantRepository.findAll()`, same entity/column this phase modifies | Yes — real per-tenant DB value drives an actual `continue`, proven by a test asserting zero downstream repository calls for a suspended tenant | ✓ FLOWING |

### Behavioral Spot-Checks

All checks below were independently re-executed in this verification session (not copied from any prior report):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles clean | `cd backend && mvn -q -DskipTests compile` | exit 0 | ✓ PASS |
| Full backend unit suite (regression) | `cd backend && mvn test` | `Tests run: 172, Failures: 0, Errors: 0` — BUILD SUCCESS (matches `120-REVIEW.md`'s independently-claimed 172/172 exactly) | ✓ PASS |
| Phase-120-specific test classes, individually confirmed within the full run | (parsed from the same `mvn test` run) | `JwtAuthenticationFilterTenantSuspensoTest` 7/7, `AuthControllerTenantSuspensoTest` 6/6, `AuthControllerLoginLockoutTest` 2/2, `PlatformAdminControllerTest` 26/26, `AlertasDiariosJobTest` 11/11 (incl. 2 WR-02 cases) | ✓ PASS |
| SAST (SpotBugs + FindSecBugs, ASVS L1 gate) | `cd backend && mvn spotbugs:check` | `BugInstance size is 0`, `Error size is 0` — BUILD SUCCESS | ✓ PASS |
| Frontend lint | `cd web && pnpm lint` | `0 errors, 18 warnings` — the 2 warnings in `dashboard-shell.tsx` (`@next/next/no-img-element`) traced via `git show f4619de4` to be on lines the Phase-120 commit never touched (pre-existing tenant-logo `<img>` tags) | ✓ PASS |
| Frontend production build | `cd web && pnpm build` | Compiled successfully; `/plataforma` present in the generated route list as `○ /plataforma` (static) | ✓ PASS |
| Phase's own executable structural gate | `cd web && node scripts/verify-consola-tenants.mjs` | 12/12 `PASS` lines printed, exit code 0 (script grew from the originally-planned 10 assertions to 12 after the WR-03/CR-01 review fixes each added a dedicated assertion) | ✓ PASS |
| Every commit hash cited in the phase narrative genuinely exists | `git show --no-patch <hash>` for all 12 cited hashes | All 12 found, messages match the narrative exactly | ✓ PASS |
| `setup.ts`/`sidebar-nav.tsx` genuinely untouched (no duplicated validation, no leaked coupling) | `git log --oneline -- web/src/schemas/setup.ts`; `git diff --quiet -- web/src/components/shared/sidebar-nav.tsx` | `setup.ts` last touched by an unrelated ancient monorepo-inclusion commit; `sidebar-nav.tsx` diff empty | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` files exist anywhere in the repository (confirmed via `find`), and neither the PLAN/SUMMARY files nor the ROADMAP success criteria for this phase reference probe-based verification. Not applicable to this phase.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| PROV-02 | 120-03, 120-04, 120-05, 120-06 | Administrador de plataforma cria um novo tenant + utilizador ADMIN inicial, num ecrã interno não público | ✓ SATISFIED | RBAC-gated `/plataforma` route + `CriarTenantPanel` + `POST /platform/tenants` → `SetupService.provisionTenant`; live-created a real 2nd tenant via the UI (`120-HUMAN-UAT.md` pt.2) |
| PROV-03 | 120-02, 120-03, 120-04, 120-05, 120-06 | Administrador de plataforma lista todos os tenants e vê utilizadores ativos por tenant | ✓ SATISFIED | `GET /platform/tenants` + `countByTenantIdAndAtivoTrue`-sourced `utilizadoresAtivos`, rendered desktop+mobile; live-confirmed 3-row list with correct occupancy |
| PROV-04 | 120-02, 120-03, 120-05, 120-06 | Administrador de plataforma ajusta `plano`/`limite_utilizadores` de qualquer tenant | ✓ SATISFIED | `PUT /platform/tenants/{id}` + `EditarTenantDialog`; live-confirmed persisted change + updated occupancy display |
| PROV-05 | 120-01, 120-02, 120-03, 120-05, 120-06 | Administrador de plataforma suspende um tenant que não pague (bloqueia acesso) | ✓ SATISFIED | `PATCH /platform/tenants/{id}/ativo` + all 3 backend enforcement points + AlertDialog UI; live-confirmed real-time session cutoff at ~1.06s and blocked re-login |

**Orphan check:** `.planning/REQUIREMENTS.md`'s traceability table (lines 74-77) maps exactly `PROV-02`, `PROV-03`, `PROV-04`, `PROV-05` to "Phase 120" — identical to the union of requirement IDs declared across this phase's 6 plans' `requirements:` frontmatter (`PROV-01`/`PROV-06`, which also appear in some historical text, correctly belong to and are already closed by Phase 119, not re-claimed here). **No orphaned requirements.**

### Anti-Patterns Found

No blocking anti-patterns. Scanned every production file this phase touched or that this phase's review-fix pass touched (24 files total, backend + frontend) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, empty-implementation patterns, and hardcoded-empty-data patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `JwtAuthenticationFilter.java` / `PlatformAdminController.java` | various | Case-insensitive substring hits on Portuguese words (`TODO pedido` = "every request", `TODOS os` = "all the") | N/A — false positive | None; confirmed by direct reading, no real debt marker exists anywhere in this phase's files |
| `.planning/STATE.md` (Pending Todos section) | 160-178 | `backend/migrations/120-add-tenant-ativo.sql` and `backend/migrations/120b-backfill-tenant-plano.sql` are **not** registered in STATE.md's "Pending Todos" list, unlike every other manual-execution migration in this repo's history (Phase 74, 86/88, and — critically — this same milestone's own Phase 117 migration, which *is* tracked there per its own VERIFICATION.md) | ⚠️ WARNING | Non-blocking to this phase's 4 ROADMAP success criteria (dev/CI `ddl-auto=update` creates both columns automatically via their `columnDefinition` defaults, and both scripts are individually correct and self-documenting). But it is a real, independently-found process gap: without this tracking, a future deploy could ship this phase's code to a `ddl-auto=validate` environment (staging/prod) without anyone being reminded to run the two SQL scripts first. `120-01-SUMMARY.md:146` explicitly acknowledges the requirement ("same standing requirement as every prior manual migration") but STATE.md's own todo list was not updated to reflect it — recommend adding both scripts to STATE.md's Pending Todos before the next deploy |
| `web/src/app/(dashboard)/plataforma/columns.tsx:157` / `page.tsx:218-229` | — | `PLANO_BADGE_VARIANT[plano]` (desktop) and an `if/else if/else` (mobile) both assume `plano` is never `null` at runtime — true for the TS *type* (`TenantAdminSummary.plano: TenantPlano`, non-nullable) but not yet true for every *row* in a database that hasn't run `120b-backfill-tenant-plano.sql` (this dev DB's own 2 pre-existing rows are still `NULL` per `120-HUMAN-UAT.md` pt.10) | ℹ️ INFO (already disclosed, non-blocking, tied to the WARNING above) | Cosmetic only (wrong badge color / blank or "null" text) for legacy rows until the migration runs; zero functional/security impact; `120-REVIEW.md` already classifies this identical observation as an accepted residual risk, not a new finding — repeated here because it directly follows from, and reinforces the urgency of, the STATE.md tracking gap above |
| `AuthController.java:81` (WR-01) | — | `request.getRemoteAddr()` may resolve to this repo's own Caddy reverse-proxy's constant address in production, per `120-REVIEW.md`'s round-2 finding, collapsing the login-lockout key toward per-email rather than per-attacker granularity | ℹ️ INFO (documented, tracked as a separate follow-up, not a regression — still strictly better than the pre-fix state which never locked out anyone) | No functional impact on this phase's own 4 success criteria; explicitly out of this phase's scope (production network topology confirmation), already tracked as a named follow-up task in `STATE.md` |

**Confirmation Bias Counter (deliberate disconfirmation pass):** the one item above genuinely NOT surfaced with equivalent prominence in `120-REVIEW.md`/SUMMARYs is the **STATE.md Pending-Todos tracking gap** — the review and summaries correctly disclose that the migrations must be run manually, but the actual STATE.md file (the mechanism this exact codebase uses, 4 times over, to make sure such steps aren't forgotten before a deploy) was not updated. This is flagged as a genuine, independently-found process gap, not copied from the phase's own narrative.

### Human Verification Required

None outstanding. `120-06-PLAN.md`'s blocking human-verify checkpoint was already executed and produced `.planning/phases/LEXCV-120-.../120-HUMAN-UAT.md` with all 10 required points at `CONFIRMADO`, each with specific, checkable evidence (exact HTTP status codes, exact error-message text, a measured ~1.06s session-cutoff timing, explicit "no logout/re-login" attestation for the central scenario, and separate verdicts for mouse vs. keyboard tooltip activation and for session-cutoff vs. login-block). This report was read in full and judged substantive, not a rubber-stamped "aprovado" — it is exactly the kind of live, cross-session, real-time evidence that static code reading and grep cannot produce, and it was genuinely gathered (curl against a real running backend + Browser MCP against a real running frontend), not simulated.

### Gaps Summary

No gaps that block the phase goal. All 4 ROADMAP Success Criteria are objectively true in the current codebase — independently re-derived from source, independently re-tested (172/172 backend, 0 SpotBugs findings, 0 lint errors, successful production build with `/plataforma` in the route list, 12/12 on the phase's own executable structural gate), and independently corroborated by a substantive, specific live human-UAT report rather than accepted on narrative alone.

This phase underwent an unusually rigorous post-execution process: a deep code review found 1 Critical (every newly-provisioned tenant, including the reserved "LexCV" platform tenant, silently got `plano = NULL`) + 3 Warnings + 3 Info; a 6-commit fix pass resolved CR-01/WR-02/WR-03/IN-01/IN-02 with new, specific tests for each, IN-03 deliberately left as a documented non-fix (a real design decision, not negligence); a 7th commit corrected an inaccurate code comment found during an independent round-2 re-review; and that round-2 re-review itself confirmed 5 of the 6 fixes as CONFIRMED-RESOLVED and 1 (WR-01) as PARTIALLY-RESOLVED with the residual gap explicitly documented and tracked as a separate follow-up rather than silently accepted or guessed at. I independently re-derived every one of these conclusions from the current source rather than trusting the review's or SUMMARYs' narrative, and they check out.

One genuinely new observation from this verification pass, not surfaced with equivalent prominence elsewhere: this phase's two new manual production-migration scripts (`120-add-tenant-ativo.sql`, `120b-backfill-tenant-plano.sql`) are not yet registered in `.planning/STATE.md`'s "Pending Todos" section, breaking this repo's own established convention (every prior manual migration — Phase 74, 86, 88, and this same milestone's Phase 117 — is tracked there). This is a WARNING, not a blocker: it does not affect dev/CI (`ddl-auto=update` creates both columns automatically) and both scripts are individually correct, but it is a real deployment-safety gap worth closing — by adding both scripts to STATE.md's Pending Todos — before this phase's code reaches a `ddl-auto=validate` (staging/prod) environment. Doing so would also make explicit the already-disclosed, non-blocking cosmetic risk (wrong plan badge color/text for the 2 pre-existing tenant rows in this dev database, and any equivalent legacy rows in production) that persists until `120b-backfill-tenant-plano.sql` actually runs.

---

_Verified: 2026-07-29T22:30:00Z_
_Verifier: Claude (gsd-verifier)_
