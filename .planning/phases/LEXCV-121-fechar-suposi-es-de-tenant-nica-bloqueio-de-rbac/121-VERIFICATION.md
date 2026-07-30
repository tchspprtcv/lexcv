---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
verified: 2026-07-29T23:55:00Z
status: passed
score: 4/4 ROADMAP success criteria verified (20/20 plan-level must-have truths across 4 plans — 2 reinterpreted after same-day reviewed fixes, see notes)
overrides_applied: 0
re_verification: false
---

# Phase 121: Fechar Suposições de Tenant Única + Bloqueio de RBAC Verification Report

**Phase Goal:** Nenhuma superfície pública ou administrativa do produto continua a assumir que existe apenas um tenant — a landing pública mostra sempre a marca genérica LexCV, e a gestão de permissões por papel deixa de ser editável por cada escritório na interface, passando a ser uma operação fixa de plataforma.
**Verified:** 2026-07-29T23:55:00Z
**Status:** passed
**Re-verification:** No — initial verification

**Adversarial stance applied:** started from the hypothesis that the phase goal was not achieved and that the "4/4 success criteria closed, 179/179 green, 11/11 gate, live UAT 8/8 CONFIRMADO" narrative was executor/reviewer self-reporting, not proof. Did not take `121-01..04-SUMMARY.md`, `121-REVIEW.md`, `121-ISOL-AUDIT.md`, or `121-HUMAN-UAT.md` on their word — independently re-read every production file this phase (and its review-fix pass) touched line-by-line; independently re-ran the full backend suite (4 times, to specifically probe a transient first-run anomaly — see Behavioral Spot-Checks), SpotBugs, the dedicated `AdminControllerRbacAutorizacaoTest`/`PublicControllerTest` classes in isolation, the frontend structural gate, and frontend lint, all fresh in this session; independently re-derived all 5 ISOL-02 pattern-family grep sweeps from scratch (not copied from the audit) and confirmed byte-identical hit counts; independently confirmed all 16 commit hashes cited in the phase narrative exist via `git show` with matching messages, and confirmed the exact backend/web diff-scope across the whole phase (`git diff --stat`) matches the union of all 4 plans' declared `files_modified` with zero scope creep. Also specifically hunted for the exact failure class this project's own history flags as recurring (a plan-level must-have silently going stale after a later code-review fix changes the underlying behavior it described) — found two, both non-blocking, documented below with full evidence rather than swept into a blanket pass.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `GET /api/v1/public/branding` deixa de depender de `TenantRepository.findFirstByOrderByCreatedAtAsc()` — devolve sempre a marca genérica LexCV, independentemente de quantos tenants reais existirem | ✓ VERIFIED | `PublicController.java` (42 lines, read in full): the class has **zero fields, zero constructor, zero `@RequiredArgsConstructor`, zero imports of any `Repository`** — `getBranding()` (`:33-41`) returns a hardcoded `TenantPublicInfoResponse.builder().nome("LexCV").logoDataUrl(null).build()`. There is structurally no `Tenant`/`TenantRepository` reference left to depend on. `PublicControllerTest.java` independently proves this by construction: `new PublicController()` (zero-arg) at line 27 — if the controller still required a `TenantRepository`, this line would not compile. Re-ran fresh this session: `mvn test -Dtest=PublicControllerTest` → `Tests run: 2, Failures: 0, Errors: 0` (surefire report confirmed). `TenantRepository.java` (25 lines, read in full) no longer declares `findFirstByOrderByCreatedAtAsc()` at all — its only method is the unrelated, parametrized `findFirstByNome(String nome)` (`:24`, exact-name lookup for the reserved "LexCV" tenant, Phase 119's WR-01 pattern, not a "pick the oldest" heuristic). |
| 2 | Uma pesquisa dedicada ao código de produção confirma que nenhum outro caminho resolve "a" tenant por heurística de "mais antiga" quando existir mais de uma tenant real | ✓ VERIFIED | Independently re-ran (not copied from `121-ISOL-AUDIT.md`) all 5 pattern-family sweeps this session, over both `backend/src/main/java` and `web/src`: `findFirstBy\|findTopBy` (`.java`) → the same 2 comment-only hits (`PublicController.java:15`, `PublicControllerTest.java:14`) plus the 2 legitimate `findFirstByNome` occurrences in `TenantRepository.java`, zero elsewhere; `.findAll()` (`.java`) → exactly 3 hits, byte-identical to the audit's claim: `AlertasDiariosJob.java:90` (documented cross-tenant background job, `tenantId` always an explicit loop parameter, never inferred), `AdminController.java:359` (`roleRepository.findAll()` inside `getRbac` — the `Role` table, which has no `tenant_id` column at all, confirmed by direct entity reading), `PlatformAdminController.java:108` (`tenantRepository.findAll()`, deliberately cross-tenant, gated `hasRole('PLATAFORMA_ADMIN')` at the class level); literal `"LexCV"` in `web/src` → exactly 6 hits, byte-identical to the audit's claim (4 `?? "LexCV"` UI fallbacks on already-tenant-scoped `me.data` from `/auth/me`, 1 static `<title>` metadata in `layout.tsx`, 1 named `TENANT_RESERVADO` constant in `plataforma/columns.tsx`) — none of the 6 decide which tenant's data is fetched. Every hit traces to one of: (a) exact-name lookup of the one reserved tenant, (b) a genuinely global platform-wide table with no `tenant_id` column, or (c) already-reviewed, explicitly-documented, gated cross-tenant iteration — never a "pick the first/oldest result" heuristic. |
| 3 | `PUT /api/v1/admin/rbac` deixa de aceitar chamadas de um `ADMIN` de tenant (`403`) — só `PLATAFORMA_ADMIN` pode alterar o mapeamento de permissões por papel | ✓ VERIFIED | `AdminController.java:409-411` (read directly): `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` immediately above `@PutMapping("/rbac")`, overriding — per Spring Security's most-specific-annotation-wins semantics — the class-level `@PreAuthorize("hasRole('ADMIN')")` (`:28`) for this one handler only. Proven, not just read: `AdminControllerRbacAutorizacaoTest.java` (216 lines, read in full) builds a **real CGLIB AOP proxy** (`ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`, the only mechanism in this test suite that evaluates `@PreAuthorize` for real) and proves it bidirectionally: a `ROLE_ADMIN`-only caller is denied with `AccessDeniedException` even though they satisfy the old class gate; a caller with **only** `ROLE_PLATAFORMA_ADMIN` (lacking `ROLE_ADMIN` entirely) passes through and reaches `roleRepository.save`. Independently re-ran fresh this session: `mvn test -Dtest=AdminControllerRbacAutorizacaoTest` → `Tests run: 7, Failures: 0, Errors: 0`. **Live-confirmed beyond the unit-test proxy:** `121-HUMAN-UAT.md`'s HTTP battery (curl against the actually-running backend, real `@EnableMethodSecurity` + JWT filter + DB-derived `UserPrincipal`) recorded `PUT /admin/rbac` → `403` for `admin@lexcv.cv` and `200` for `plataforma@lexcv.cv` (the required counter-test ruling out an accidental universal lock), with the persisted RBAC matrix byte-identical before/after (zero drift). |
| 4 | A aba "Controlo de Acesso (RBAC)" das Definições (`settings/page.tsx`, `RbacTab`) deixa de expor a ação de gravar a um `ADMIN` de tenant na interface, evitando um `403` confuso na UI | ✓ VERIFIED | `settings/page.tsx` `RbacTab` (read in full, lines 760-1000): `isPlatformAdmin` (`:768`) is derived fail-closed (`me.isFetched && (me.data?.roles?.includes("PLATAFORMA_ADMIN") ?? false)` — `false` during the loading window, never a flash of the button). The `CardHeader` (`:868-892`) renders the "Guardar Regras" `<Button onClick={handleSave}>` only when `isPlatformAdmin` is true; every other viewer instead sees a neutral `Badge variant="outline"` (`<Lock/>` icon + exact text "Gerido pela Plataforma") wrapped in a `Tooltip` whose trigger is `<span tabIndex={0}>` (keyboard-reachable, not just mouse-hoverable). Independently re-ran fresh this session: `node scripts/verify-bloqueio-rbac.mjs` → all 11 named source assertions `PASS`, exit 0 (script re-read in full: 11 assertions, matches the claimed count exactly). **Live-confirmed in browser** (`121-HUMAN-UAT.md`, points 1-4, 6-8): the Save button is structurally absent (`document.querySelectorAll('button')` filtered by text — zero matches) for `admin@lexcv.cv`; the Tooltip opens with the exact UI-SPEC copy both on mouse hover (~1.2s, `data-state="delayed-open"`) and on 2 real `Tab` keypresses alone (no mouse); the layout is clean at 375×812; `plataforma@lexcv.cv` cannot see the tab at all (unchanged, intentional). |

**Score:** 4/4 ROADMAP success criteria verified.

### Plan-Level Must-Have Truths (supporting detail, 20 total across 4 plans)

All plan-level `must_haves.truths` were checked against the **current (post-code-review-fix) HEAD**, not the state at the moment each plan closed. Two of the 20 no longer hold literally as originally worded — both because a same-day code review (`121-REVIEW.md`) found the original plan's decision created a real problem and fixed it. Both are flagged below with full evidence rather than silently marked pass or fail.

**Plan 01 (ISOL-03 backend) — 5/5, 1 reinterpreted**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| A caller genuinely satisfying the old class gate (`ROLE_ADMIN`) is denied via `AccessDeniedException` through a real method-security proxy | ✓ VERIFIED | `AdminControllerRbacAutorizacaoTest.updateRbac_comRoleAdminDeTenantNormalERecusadoMesmoSatisfazendoOGateDeClasse` — re-run fresh, PASS |
| A caller lacking `ROLE_ADMIN` entirely but holding `ROLE_PLATAFORMA_ADMIN` passes the gate and the handler runs to completion | ✓ VERIFIED | `...updateRbac_comRolePlataformaAdminPassaOGateMesmoSemHasRoleAdmin` — re-run fresh, PASS |
| `getRbac` continua sem qualquer anotação de método — leitura por um `ADMIN` de tenant não é afetada | ⚠ SUPERSEDED (intent verified) | **No longer literally true**: `getRbac` now carries `@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")` (`AdminController.java:356`), added by the same-day CR-01 review fix. Reason: the review found that `PLATAFORMA_ADMIN` — the only role now able to *write* the matrix — had **no way to read it first**, and `updateRbac` replaces (never merges) a role's permission set per submission, making the writer's first real use of the endpoint a blind, potentially destructive submission. The **intent** behind this must-have — "a leitura da matriz por um ADMIN de tenant não é afetada" — is verified true: a dedicated non-regression test (`getRbac_comRoleAdminContinuaAObterSucesso`) proves a `ROLE_ADMIN`-only caller still reaches `200`, re-run fresh this session, PASS. This is a data-loss-risk closure, not a regression. |
| Class-level annotation of `AdminController` stays exactly `hasRole('ADMIN')`, unchanged | ✓ VERIFIED | `AdminController.java:28`, byte-identical; confirmed by direct reading and by `anotacaoDeClasseContinuaHasRoleAdmin`'s reflection assertion, re-run fresh, PASS |
| Casos 6/7/8 of `AdminControllerPlataformaAdminContencaoTest` stay green with the file literally untouched | ✓ VERIFIED | Full file re-run fresh this session: `Tests run: 14, Failures: 0, Errors: 0`. `git log --oneline` on this file shows its last commit (`6485fd93`) predates Phase 121 entirely (Phase 119) — genuinely untouched. |

**Plan 02 (ISOL-03 frontend) — 5/5, 1 reinterpreted**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Tenant `ADMIN` opens Definições > Controlo de Acesso (RBAC) and sees no "Guardar Regras" button | ✓ VERIFIED | Source read (`page.tsx:868-876`) + gate assertion A05 (re-run, PASS) + live UAT point 2 (structural DOM query, zero matches) |
| Neutral Badge with lock + exact text "Gerido pela Plataforma" appears in the button's place, readable without hovering | ✓ VERIFIED | Source read (`:877-885`) + gate A06 (PASS) + live UAT point 2 (exact Tailwind classes recorded, confirmed neutral, never blue/red) |
| Hovering or keyboard-focusing the Badge reveals the full explanation in a Tooltip | ✓ VERIFIED | Source read (`:878-891`, `<span tabIndex={0}>` wrapper) + gate A07/A08 (PASS) + live UAT points 3 (mouse) and 4 (keyboard, 2 real `Tab` presses), recorded as **separate** verdicts as required |
| During the `useMe()` loading window, the state shown is the Badge — never a flash of the button | ✓ VERIFIED | Source read: `me.isFetched && (...)` (`:768`) — `false` by short-circuit before `isFetched` resolves + gate A03/A04 (PASS) |
| O separador RBAC continua visível e a matriz de permissões continua a renderizar e a **alternar exatamente como antes** | ⚠ SUPERSEDED (intent partially preserved — see Anti-Patterns/Gaps) | Tab visibility and matrix **rendering** are still true (✓ verified). The "**alternar exatamente como antes**" (toggles exactly as before) clause is **no longer literally true**: the same-day WR-01 review finding judged this exact behavior (a matrix that visually accepts clicks for a viewer who can never save them) as undercutting the phase's own goal wording, and it was fixed — `isDisabled = isAdminRow || !isPlatformAdmin` now governs every checkbox (`page.tsx:969,977`), and `handleCheckboxChange` early-returns when `!isPlatformAdmin` (`:815`). I independently confirmed both lines by direct reading. **This is the one loose thread in this verification** — see Anti-Patterns below: neither the executable gate nor the human-UAT record was refreshed to reflect this specific fix, even though the fix itself is correctly implemented. |

**Plan 03 (ISOL-01 confirmation + ISOL-02 audit) — 5/5 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `GET /api/v1/public/branding` sempre devolve a marca genérica, sem ler nenhum `Tenant`, confirmado por teste verde | ✓ | Re-ran `mvn test -Dtest=PublicControllerTest` fresh: 2/2 PASS |
| `findFirstByOrderByCreatedAtAsc` não existe em nenhum código vivo — só vestígios de comentário | ✓ | Re-derived independently: exactly 2 hits, both Javadoc/comment (`PublicController.java:15`, `PublicControllerTest.java:14`); confirmed absent from `TenantRepository.java` |
| Uma varredura reprodutível por comando cobre todos os padrões de resolução-de-tenant-por-heurística com veredito explícito por superfície | ✓ | `121-ISOL-AUDIT.md` (116 lines, read in full): 15-row verdict table, all `COVERED`, 0 `FIXED`; all 5 reproduction commands independently re-run this session with byte-identical hit counts |
| O padrão `findByXxxId`-sem-`tenantId` fica registado como contexto de fundo, explicitamente NÃO como achado de ISOL-02, com dono nomeado (Phase 123) | ✓ | `121-ISOL-AUDIT.md`'s dedicated "Registado mas explicitamente fora de âmbito" section, citing `PITFALLS.md` Pitfall 1, named owner Phase 123 |
| A Phase 123 (ISOL-04) consegue citar este ficheiro em vez de repetir a varredura de raiz | ✓ | Reproduction-command section present and independently confirmed to actually reproduce the claimed counts |

**Plan 04 (live UAT) — 5/5 CONFIRMADO at execution time; 1 of the 4 HTTP codes it recorded later changed by CR-01, correctly addended**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| PROVA CENTRAL: real `ADMIN` de escritório session gets `403` on `PUT /admin/rbac` against the running backend | ✓ | `121-HUMAN-UAT.md` HTTP table, row 3: `403` |
| Real `PLATAFORMA_ADMIN` session gets `200` on the same `PUT` (counter-test) | ✓ | Row 6: `200` |
| Persisted RBAC matrix shows zero drift — `GET` response byte-identical before/after | ✓ | Row 7: identical, re-confirmed again after the CR-01 re-verification battery |
| Tenant `ADMIN` sees no Save button but sees the Badge + intact matrix | ✓ | Points 1, 2, 5 CONFIRMADO |
| Tooltip opens by mouse AND by keyboard, exact UI-SPEC text, separate verdicts | ✓ | Points 3, 4 CONFIRMADO, separately worded |

Note: `121-HUMAN-UAT.md`'s row 5 (`GET /admin/rbac` for `plataforma@lexcv.cv`) originally recorded `403` at the time this plan executed (correct, pre-CR-01). The file carries an explicit "Adenda pós-revisão (CR-01)" at its top, and the table itself was corrected in place to `200` — **live re-verified against the backend restarted with the fixed code**, not just assumed from the commit diff (`121-REVIEW.md` Round 2, cross-checked against `git log` showing `ff72c389` as the dedicated correction commit). This is the correct, established pattern this project uses (matches how `120-REVIEW.md`/`120-HUMAN-UAT.md` handled their own post-hoc corrections).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/controllers/PublicController.java` | Zero tenant dependency, hardcoded generic branding | ✓ VERIFIED | 42 lines, read in full. No fields, no constructor, no repository import. |
| `backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java` | Regression proof, zero-arg construction | ✓ VERIFIED | 52 lines, 2 tests, re-run fresh: 2/2 PASS |
| `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` | No heuristic "oldest tenant" method | ✓ VERIFIED | 25 lines, only `findFirstByNome(String)` remains |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | Method-level `@PreAuthorize` on `updateRbac`; widened `@PreAuthorize` on `getRbac`; class-level gate unchanged | ✓ VERIFIED | 444 lines, read in full. `:28` class gate intact; `:356` `getRbac` widened (post-CR-01); `:409` `updateRbac` restricted to `PLATAFORMA_ADMIN` |
| `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` | Real AOP-proxy behavioral proof, ≥90 lines | ✓ VERIFIED | 216 lines (grew from the original 5-case/Round-1 file to 7 cases post-CR-01), re-run fresh: 7/7 PASS |
| `.planning/phases/LEXCV-121-.../121-ISOL-AUDIT.md` | ISOL-01/02 audit record, ≥60 lines, contains "COVERED" | ✓ VERIFIED | 116 lines, 15 `COVERED` rows, 0 `FIXED` |
| `web/src/app/(dashboard)/settings/page.tsx` (`RbacTab`) | Conditional Save button, Badge+Tooltip, disabled matrix for non-platform-admins | ✓ VERIFIED | RbacTab block (`:760-1000`) read in full; all elements present including the WR-01 `isDisabled` fix |
| `web/scripts/verify-bloqueio-rbac.mjs` | Executable source gate, ≥120 lines, 11 assertions | ✓ VERIFIED | 238 lines, 11 named assertions (A01-A11), re-run fresh: 11/11 PASS, exit 0 |
| `web/package.json` | `verify:bloqueio-rbac` script entry | ✓ VERIFIED | `:13`, `"node scripts/verify-bloqueio-rbac.mjs"` |
| `.planning/phases/LEXCV-121-.../121-HUMAN-UAT.md` | Per-scenario verdict, live, ≥25 lines | ✓ VERIFIED | 54 lines, 8/8 checkpoint points + 7-row HTTP table, all `CONFIRMADO`, 0 `FALHOU`; carries the CR-01 addendum |
| `.planning/phases/LEXCV-121-.../121-REVIEW.md` | Deep code review with fix verification | ✓ VERIFIED | 245 lines, 1 Critical + 3 Warnings + 2 Info, Round-2 fix verification section, Final Verdict APPROVED |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AdminController.java` `updateRbac` | Spring method security | `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` immediately above `@PutMapping("/rbac")` | ✓ WIRED | `:409-411`; proven via real CGLIB AOP proxy (bidirectional test) and live HTTP (`403`/`200`) |
| `AdminController.java` `getRbac` | Spring method security | `@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")` (post-CR-01) | ✓ WIRED | `:356-358`; proven via reflection + real proxy (`200` for both roles) + live HTTP re-verification |
| `AdminControllerRbacAutorizacaoTest.java` | `AdminController` | `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` | ✓ WIRED | Genuine CGLIB proxy, not a direct Java call — the only mechanism in this suite that evaluates `@PreAuthorize` |
| `settings/page.tsx` `RbacTab` | `useMe()` (shared query cache `["auth","me"]`) | `const me = useMe(); ... me.isFetched && me.data?.roles?.includes(...)` | ✓ WIRED | `:762,768`; same idiom as `dashboard-shell.tsx`/`plataforma/page.tsx` (Phase 120) |
| `web/package.json` | `web/scripts/verify-bloqueio-rbac.mjs` | `"verify:bloqueio-rbac": "node scripts/verify-bloqueio-rbac.mjs"` | ✓ WIRED | `package.json:13`; independently re-run this session, 11/11 PASS |
| `TenantRepository.findFirstByNome` | `DatabaseSeeder.seedTenantPlataforma()` | Sole call site, literal `"LexCV"` | ✓ WIRED | Confirmed by `121-ISOL-AUDIT.md` and independently re-derived grep; not a heuristic, a named lookup |
| `PublicController.getBranding()` | *(nothing)* | Deliberately zero dependencies | ✓ CONFIRMED-DISCONNECTED (by design) | No repository, no `SecurityContextHolder`, no field — the absence of a link is the correctness condition here |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `RbacTab`'s `isPlatformAdmin` (gates Save button + matrix `disabled` state) | `me.data?.roles` | `useMe()` → `GET /auth/me` → `UserPrincipal` built from the authenticated JWT + the DB-loaded `User.roles` collection (unmodified by this phase) | Yes — genuine DB-backed role membership, not hardcoded; live-confirmed non-empty and correct for both `admin@lexcv.cv` and `plataforma@lexcv.cv` in `121-HUMAN-UAT.md` | ✓ FLOWING |
| `RbacTab`'s permission matrix (`effectiveRolePermissions`) | `rbac?.rolePermissions` | `useAdminRbac()` → `GET /admin/rbac` → `AdminController.getRbac()` → `roleRepository.findAll()` (real JPA query against `t_role`/`t_role_permission`) | Yes — genuine DB-backed data; live-confirmed non-empty and byte-stable across the whole HTTP battery | ✓ FLOWING |
| Backend authorization decision (`hasRole('PLATAFORMA_ADMIN')` / `hasRole('ADMIN')`) | `Authentication.getAuthorities()` | `UserPrincipal`, populated at login from the DB-loaded `User.roles` (Phase 119 pattern, untouched by this phase) | Yes — proven live against the real Spring context (not just the hand-built test proxy): `403`/`200` pairs measured via curl against the running backend, not simulated | ✓ FLOWING |

### Behavioral Spot-Checks

All checks below were independently re-executed in this verification session (not copied from any prior report):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Targeted RBAC authorization proof | `mvn test -Dtest=AdminControllerRbacAutorizacaoTest` | `Tests run: 7, Failures: 0, Errors: 0` | ✓ PASS |
| Targeted ISOL-01 regression proof | `mvn test -Dtest=PublicControllerTest` | `Tests run: 2, Failures: 0, Errors: 0` | ✓ PASS |
| Full backend regression suite | `mvn test` (run 4 times this session — see note) | Runs 2-4: `Tests run: 179, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`, matching the claimed 179/179 exactly | ✓ PASS |
| SAST (SpotBugs + FindSecBugs, ASVS L1 gate) | `mvn spotbugs:check` | Exit 0, no findings printed | ✓ PASS |
| Frontend structural gate (this phase's own) | `node scripts/verify-bloqueio-rbac.mjs` | 11/11 `PASS` lines, exit 0 | ✓ PASS |
| Frontend lint | `pnpm lint` | `0 errors, 18 warnings` — none of the flagged files is one this phase touched (`settings/page.tsx` does not appear in the warnings list) | ✓ PASS |
| ISOL-02 pattern-family re-derivation (5 families, backend + frontend) | 5 dedicated `grep` sweeps, run fresh, not copied from `121-ISOL-AUDIT.md` | Hit counts byte-identical to the audit's claims in every family (`findFirstBy`/`findTopBy`, `.findAll()`, `.get(0)`/`findFirst()`, literal `"LexCV"`, `findFirstByOrderByCreatedAtAsc`) | ✓ PASS |
| All 16 commit hashes cited in the phase narrative genuinely exist | `git show --no-patch --format="%H | %s" <hash>` for all 16 | All 16 found; every message matches the narrative exactly | ✓ PASS |
| Diff-scope matches declared `files_modified` exactly, whole phase | `git diff --stat ec164fa3..744a6f3c -- backend web` | Exactly 5 files: `AdminController.java`, `AdminControllerRbacAutorizacaoTest.java`, `web/package.json`, `web/scripts/verify-bloqueio-rbac.mjs`, `settings/page.tsx` — matches the union of all 4 plans' declared scope, zero scope creep | ✓ PASS |
| `AdminControllerPlataformaAdminContencaoTest.java` / `use-admin.ts` genuinely untouched by this phase | `git log --oneline` on each | Both files' last commits (`6485fd93`, `13ea8505`) predate Phase 121 (Phases 119 and 77 respectively) | ✓ PASS |

**Note on the full-suite anomaly:** the first of my 4 `mvn test` invocations this session produced a JUnit5 stack trace (exception unwinding through generic framework internals; the specific failing test class was not visible in the truncated output I captured). The subsequent 3 runs — including 2 full, freshly-invoked runs captured to log files — were all clean `179/179 BUILD SUCCESS`. This matches a pattern this exact phase's own `121-01-SUMMARY.md` already documented independently (a one-off, non-reproducing failure in an unrelated background-job test class, immediately green on a clean re-run). Given 3-of-4 clean reproductions at the exact claimed count and no ability to attribute the one anomaly to any Phase-121 file, I treat it as a pre-existing environmental flake, not a regression — but it is disclosed here rather than omitted.

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists anywhere in this repository (confirmed: no top-level `scripts/`, no `backend/scripts/`; `web/scripts/` contains only the 4 `verify-*.mjs` structural gates, none named `probe-*`). Neither the PLAN/SUMMARY files nor the ROADMAP success criteria for this phase reference probe-based verification. Not applicable to this phase.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| ISOL-01 | 121-03 | Landing pública mostra sempre marca genérica LexCV (deixa de tentar mostrar branding "da" tenant) | ✓ SATISFIED | `PublicController.getBranding()` hardcoded, zero tenant dependency; `PublicControllerTest` 2/2 green (re-run fresh) |
| ISOL-02 | 121-03 | Nenhum caminho de código assume "a" tenant (`findFirstByOrderByCreatedAtAsc` ou equivalente) quando existir mais de uma tenant real | ✓ SATISFIED | `121-ISOL-AUDIT.md` 15-surface verdict table, all `COVERED`; independently re-derived all 5 pattern sweeps this session with matching counts |
| ISOL-03 | 121-01, 121-02, 121-04 | `PUT /api/v1/admin/rbac` deixa de ser editável por tenant — gestão de permissões por papel passa a ser fixa para toda a plataforma | ✓ SATISFIED | Method-level `@PreAuthorize` on `updateRbac` (backend), Save-button/matrix gating in `RbacTab` (frontend), both proven by unit test, structural gate, and live HTTP/browser UAT |

**Orphan check:** `.planning/REQUIREMENTS.md`'s traceability table (lines 79-81) maps exactly `ISOL-01`, `ISOL-02`, `ISOL-03` to "Phase 121" — identical to the union of requirement IDs declared across this phase's 4 plans' `requirements:` frontmatter (`[ISOL-03]`, `[ISOL-03]`, `[ISOL-01, ISOL-02]`, `[ISOL-03]`). ISOL-04 correctly maps to Phase 123, not claimed here. **No orphaned requirements.**

### Anti-Patterns Found

No blocking anti-patterns. Scanned every production file this phase (and its review-fix pass) touched — `PublicController.java`, `TenantRepository.java`, `AdminController.java`, `AdminControllerRbacAutorizacaoTest.java`, `settings/page.tsx`, `verify-bloqueio-rbac.mjs`, `web/package.json` — for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, empty-implementation patterns, and hardcoded-empty-data patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AdminController.java`, `AdminControllerRbacAutorizacaoTest.java`, `settings/page.tsx` | various | Case-insensitive substring hits on Portuguese words (`metodo`, `todos`, `todo`) and legitimate JSX `placeholder="..."` input hints | N/A — false positive | None; confirmed by direct reading, no real debt marker exists in any file this phase touched |
| `.planning/phases/LEXCV-121-.../121-HUMAN-UAT.md:39` (point 5) and `121-04-SUMMARY.md`'s Self-Check | — | Both still describe the **pre-WR-01-fix** RBAC matrix behavior for a tenant `ADMIN` ("checkbox de TECNICO... disabled=false... alternou para true") and the **pre-CR-01-fix** HTTP code (`GET plataforma=403`) respectively. The main HTTP-code table in `121-HUMAN-UAT.md` *was* correctly corrected in place with a dated addendum for CR-01 (commit `ff72c389`) — but point 5's specific wording about the matrix being freely interactive, and the SUMMARY's own Self-Check line, were not refreshed the same way after the WR-01 fix (`4a27c109`) landed. Confirmed via `git log` that `4a27c109` never touched `121-HUMAN-UAT.md`. | ⚠ WARNING | Non-blocking to this phase's 4 ROADMAP success criteria — none of them require checkbox-disabling, only that the Save action itself disappears (which is independently verified true). The underlying WR-01 fix **is** correctly implemented in the current source (`page.tsx:815,969,977`, confirmed by my own direct reading), and native HTML `disabled` semantics carry low regression risk. But this is a genuine, independently-found gap in the verification trail: neither `verify-bloqueio-rbac.mjs`'s 11 assertions (unlike Phase 120's equivalent gate, which grew 10→12 after its own review fixes) nor a live browser re-check confirm the new disabled/read-only matrix behavior for a non-platform-admin viewer. **Recommend:** (a) live re-verify that a TECNICO/ASSISTENTE checkbox now renders `disabled` and does not toggle for `admin@lexcv.cv`, adding an addendum to `121-HUMAN-UAT.md` point 5 in the same style as the CR-01 correction; (b) add a 12th assertion to `verify-bloqueio-rbac.mjs` covering the `isDisabled`/`!isPlatformAdmin` guard. |
| `web/src/hooks/use-admin.ts:69` (`useAdminSaveRbac`) | — | Mutation hook defined but never imported/called anywhere (`RbacTab.handleSave` calls `apiFetch` directly instead) | ℹ️ INFO | Pre-existing since at least Phase 77 (`git log` last commit `13ea8505`, 2026-07-05) — not introduced or worsened by Phase 121, out of this phase's scope |

**Confirmation Bias Counter (deliberate disconfirmation pass):** the item genuinely not surfaced with equivalent prominence elsewhere is the stale human-UAT/SUMMARY wording described above — `121-REVIEW.md`'s WR-01 entry says "Verified by re-reading the diff line-by-line" and cites only the pre-existing A11 gate assertion (which tests ADMIN-row immutability, a different guarantee) as confirmation, not a fresh live check or an extended gate assertion for the new non-platform-admin-disabled behavior specifically. This is flagged here as an independently-found completeness gap, not copied from the phase's own narrative.

### Human Verification Required

None outstanding against this phase's 4 ROADMAP success criteria. `121-04-PLAN.md`'s blocking human-verify checkpoint was already executed and produced `121-HUMAN-UAT.md` with all 8 required points at `CONFIRMADO` plus a 7-row live HTTP battery, and — critically, because CR-01 changed a live HTTP contract this file had already recorded a value for — the file carries a dated, explicit addendum showing the corrected value was **live re-verified against the backend restarted with the fixed code**, not assumed from the commit. Every genuinely browser-only or real-time claim tied to the phase's actual success criteria (Save button absence, Tooltip mouse/keyboard behavior, mobile layout, platform-admin tab invisibility, real `403`/`200` against the live Spring context) has already been exercised live, not just reasoned about statically.

The one candidate item — live re-confirming that the WR-01 checkbox-disable fix renders correctly in a real browser for a tenant `ADMIN` — is deliberately **not** listed here as a blocking requirement, because: (1) it is not a ROADMAP success criterion nor any plan's `must_haves.truths` (the original plan-level truth actually asserted the opposite, later superseded by review, see above); (2) the underlying fix was independently confirmed correct by my own direct reading of the current source (`disabled={isDisabled}` + the handler's early-return guard), not just inferred from the review's narrative; (3) native HTML/React `disabled` semantics are low-risk, well-understood behavior, unlike the Tooltip-timing and keyboard-focus-order claims this phase correctly did send to a human checkpoint. It is instead recorded as a WARNING with a concrete recommended remediation in Anti-Patterns above.

### Gaps Summary

No gaps that block the phase goal. All 4 ROADMAP Success Criteria are objectively true in the current codebase — independently re-derived from source, independently re-tested (179/179 backend across 3 of 4 fresh runs, 0 SpotBugs findings, 0 frontend lint errors in this phase's files, 11/11 on the phase's own structural gate), and independently corroborated by a specific, substantive live-UAT record (HTTP status codes, exact error text, Tailwind class names, separate mouse/keyboard Tooltip verdicts) rather than accepted on narrative alone.

This phase's own post-execution process was unusually rigorous: a deep code review found 1 Critical (the sole role now able to write the RBAC matrix couldn't read it first — a real data-loss risk on first legitimate use) + 3 Warnings + 2 Info; a 4-commit fix pass resolved CR-01/WR-01/WR-03/IN-01 with new, specific tests for each; WR-02 (no frontend CI job — pre-existing, project-wide, correctly deferred) and IN-02 (legacy mock-db types — pre-existing, correctly deferred) were left unfixed by explicit, reasoned decision; and because CR-01 changed a live HTTP contract, the phase's own live-UAT record was corrected in place and **re-verified live a second time** against the restarted backend, not just assumed from the diff. I independently re-derived every one of these conclusions from the current source rather than trusting the review's or SUMMARYs' narrative, and they check out.

Two genuinely new observations from this verification pass, not surfaced with equivalent prominence elsewhere: (1) two plan-level must-haves (Plan 01's "`getRbac` stays unannotated", Plan 02's "matrix keeps toggling for everyone") are now literally false as originally worded — both intentionally, for good, tested, reviewed reasons, and both with their underlying intent still true, but worth naming explicitly rather than silently reinterpreting; (2) the human-UAT record's point 5 and the Plan 04 SUMMARY's own Self-Check line still describe pre-fix behavior for the WR-01 checkbox-disable change, and the executable frontend gate was not extended to cover that specific fix the way an equivalent gate was in Phase 120. Neither affects the phase's actual goal achievement — both are flagged as WARNING-level completeness gaps with concrete, cheap remediation suggested above.

---

## Addendum: both recommended remediations closed (same day)

This verification's two WARNING-level completeness gaps were both closed immediately after this
report was written, before Phase 121 was marked complete:

1. **Live re-confirmation of the WR-01 fix** — logged in fresh as `admin@lexcv.cv` (after
   restarting the frontend to clear an unrelated, transient Turbopack persistent-cache
   corruption), navigated to Settings → RBAC, and confirmed structurally: all 4 checkboxes in a
   sampled row (`Gerir Clientes`) now report `disabled: true` (previously only the ADMIN column's
   did); the non-ADMIN checkbox correctly shows `checked: false` (not force-checked by the new
   disabled state); a programmatic click leaves `checked` unchanged. `121-HUMAN-UAT.md` point 5
   and `121-04-SUMMARY.md`'s Self-Check were both corrected in place with dated addenda (commit
   `af8beae`), matching the exact style already used for the CR-01 correction — not silently
   rewritten.
2. **12th gate assertion added** — `verify-bloqueio-rbac.mjs` now asserts both
   `if (!isPlatformAdmin) return;` (in `handleCheckboxChange`) and
   `const isDisabled = isAdminRow || !isPlatformAdmin;` are present, so a future regression of the
   WR-01 fix would fail this gate, not just this verification's one-time manual check. Re-ran:
   12/12 PASS (commit `a836e83`).

Both remediations were the exact ones this report recommended above — no new scope introduced.

---

_Verified: 2026-07-29T23:55:00Z_
_Verifier: Claude (gsd-verifier)_
_Addendum: 2026-07-29, same session_
