---
phase: 123-auditoria-de-isolamento-dedicada
verified: 2026-07-30T12:30:00Z
status: passed
score: 3/3 ROADMAP success criteria verified (11/11 plan-level must-have truths across 2 plans — every grep count, file excerpt, test result, and cross-phase citation in 123-ISOL-AUDIT.md independently re-derived from scratch this session, zero discrepancies found)
overrides_applied: 0
re_verification: false
---

# Phase 123: Auditoria de Isolamento Dedicada Verification Report

**Phase Goal:** Uma auditoria de isolamento dedicada — no espírito da AUD-01 da v2.11 — confirma que as novas superfícies multi-tenant (consola de administração de tenants, relatório de utilização, bloqueio de RBAC) não deixam nenhum tenant ver ou influenciar dados de outro, antes de o utilizador provisionar um 2º tenant pagante real através da consola da Phase 120. `StorageService` confirmado fora de âmbito.
**Verified:** 2026-07-30T12:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

**Adversarial stance applied:** started from the hypothesis that `123-ISOL-AUDIT.md` was executor self-reporting (a documentation file asserting its own correctness) rather than proof, and set out to falsify every load-bearing claim in it independently — not by re-reading the audit's prose, but by re-running its cited commands myself and reading the underlying source fresh. This included: re-deriving every grep in both Critério tables byte-for-byte (annotation counts, field counts, write-path enumeration, dead-handler unreachability); independently running all 3 cited Maven test targets (41+21 tests) and `pnpm verify:bloqueio-rbac` (12 assertions) fresh, not trusting the SUMMARY's reported counts; independently confirming `git status --porcelain -- backend web` empty AND (going further than the audit itself) `git diff --stat` across the **entire** phase's commit range for `backend web` and for `web/package.json`/`backend/pom.xml`, to prove zero production drift across all of Phase 123, not just at single checkpoints; verifying all 3 explicitly-cited commit hashes exist via `git show` with matching messages and diff-scope; cross-checking the claimed "121-ISOL-AUDIT.md is an outdated precedent" reasoning by reading 121-ISOL-AUDIT.md's own text (confirms it names Phase 123 as decision-owner for Pitfall 1, and documents the pre-CR-01 `getRbac` state) and then independently confirming via `git log` that commit `31619be9` ("CR-01: widen getRbac") postdates the Phase 121 Plan 01 lock commit — triangulating the claim through three independent sources rather than accepting the narrative; and, because this project's own `AGENTS.md` explicitly warns this Next.js version has breaking changes vs. training data, independently checking the bundled `node_modules/next/dist/docs/` for the "private folder" (`_folder`) routing-exclusion convention that the dead-handler dismissal depends on, rather than assuming it from prior Next.js knowledge. Found zero discrepancies anywhere between the audit's claims and the current codebase state.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A consola de administração de tenants (Phase 120) e o relatório de utilização (Phase 122) são auditados e confirmados a expor dados exclusivamente através de endpoints gated a `PLATAFORMA_ADMIN`, nunca através de um endpoint tenant-scoped comum | ✓ VERIFIED | Independently re-read `PlatformAdminController.java` in full (201 lines): exactly 1 class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (`:53`), confirmed by `grep -n '^@PreAuthorize'` (1 hit) and `grep -nE '^[[:space:]]+@PreAuthorize'` (0 hits — no method relaxes it); the 4 handlers (`POST`/`GET /tenants`, `PUT /tenants/{id}`, `PATCH /tenants/{id}/ativo`, at lines 67/106/126/165) all inherit only this gate. `TenantAdminSummaryResponse.java` read in full: exactly 6 `private` fields (`grep -cE` confirms `6`), 0 matching `logoDataUrl\|nif\|email\|telefone\|createdAt` (confirms `0`); its only consumer across `backend/src/main/java` is `PlatformAdminController` (`grep -rnE` returns 11 lines, all in the controller or the DTO files themselves). `web/src/hooks/use-platform-admin.ts` read in full: exactly 5 `apiFetch` occurrences (1 import + 4 calls), 0 pointing anywhere but `/platform/tenants*`; `MSYS_NO_PATHCONV=1 grep -rn '/platform' web/src` returns exactly 9 lines across 4 files, none a live HTTP call outside this hook. Both `plataforma/page.tsx` and `plataforma/relatorio/page.tsx` read in full: both implement `if (!me.isFetched) { return null; }` **before** `!me.data?.roles?.includes("PLATAFORMA_ADMIN")` (WR-03 fail-closed pattern), confirmed byte-for-byte. The two non-role-gated paths the audit names instead of omitting were independently confirmed: `AuthController.getMe()` (`:203-233`) resolves `Tenant` exclusively via `tenantRepository.findById(principal.getTenantId())`, where `getTenantId()` comes from `UserPrincipal` built from the caller's own JWT (`SecurityContextHolder.getContext().getAuthentication()`) — never a client-supplied id; `SecurityConfig.java` confirms `/api/v1/setup/initialize` is in the `permitAll()` list (`:57`), and `SetupService`/`SetupController` read in full confirm the singleton gate (`SetupController.initialize` checks `isInitialized()` before calling the service; `initializeSystem` re-checks `settings.getInitialized()` under a `findByIdForUpdate` row lock, throwing `IllegalStateException`) is structurally distinct from `provisionTenant` (never touches `SystemSettingRepository`, invoked only by the gated `PlatformAdminController`). |
| 2 | O bloqueio de `PUT /api/v1/admin/rbac` (Phase 121) é confirmado sem via de contorno — nenhum outro endpoint tenant-facing continua a permitir escrever `Role`/`Permission` | ✓ VERIFIED | Independently re-ran the exhaustive (not directed) enumeration: `grep -rnE 'roleRepository[.](save|delete|saveAll|deleteAll)|permissionRepository[.](save|delete|saveAll|deleteAll)'` returns exactly 3 lines (`AdminController.java:438` inside `updateRbac`; `DatabaseSeeder.java:377,381`, startup code) — zero `permissionRepository` writes anywhere; `grep -rnE '[.]setPermissions[(]'` returns exactly 2 lines (`AdminController.java:320` `user.setPermissions` inside `updateUser`; `:437` `role.setPermissions` inside `updateRbac`). Read `AdminController.java`'s RBAC region directly: `updateRbac` (`PUT /rbac`) carries method-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (`:409`) overriding the class-level `hasRole('ADMIN')` (`:27`); `getRbac`'s **current** annotation, read directly, is `@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")` (`:356`) — confirmed to genuinely differ from what `121-ISOL-AUDIT.md` documented (no method-level gate on `getRbac` at that time) by reading that file directly, and confirmed the transition is real (not a narrative invention) via `git log --oneline` showing commit `31619be9` "fix(121): CR-01 widen getRbac..." landing after the Plan-01 lock commit `cd19ce54`. `updateUser` read directly: `!user.getTenantId().equals(principal.getTenantId())` → `404` (`:234-236`) evaluated before any mutation, confirming the per-user permission write is tenant-scoped, not a matrix bypass. `DatabaseSeeder.java`: `grep -nE '@RestController|@Controller|Mapping'` returns 0 lines — unreachable via HTTP. `Role.java`/`Permission.java` read in full: neither has a tenant column. The dead handler `web/src/app/_api-backup/v1/admin/rbac/route.ts` was read in full and matches the audit's description exactly (checks only `ctx.roles.includes("ADMIN")`, writes `mockDb.rolePermissions`). Its 3 independent unreachability proofs were each independently reproduced: (1) `node -e "...app-path-routes-manifest.json..."` → `rotas: 36 | rotas de api: 0`; `grep -c '_api-backup'`/`grep -c 'admin/rbac'` on the manifest both return `0`; provenance re-confirmed (manifest `2026-07-30 08:45:31`, newer than both `route.ts`'s mtime and the last commit touching `_api-backup`, `40008dc8`/`2026-06-17`) — AND, because this Next.js version may differ from training data (project's own `AGENTS.md` warning), the underscore "private folder" routing-exclusion rule was independently confirmed against this exact installed version's bundled docs (`node_modules/next/dist/docs/01-app/01-getting-started/02-project-structure.md:257-261`: "Private folders can be created by prefixing a folder with an underscore... opting the folder and all its subfolders out of routing"), not assumed; (2) `web/src/lib/api.ts` read in full confirms `apiFetch` always builds `${API_BASE}${path}` where `API_BASE` is `NEXT_PUBLIC_API_BASE_PATH`, and `next.config.ts` rewrites `/api/v1/:path*` to the external Spring backend — `grep -rn '_api-backup' web/src` returns 0 lines anywhere; (3) `mockDb`/`request-auth` confirmed disconnected from PostgreSQL by reading the route file's imports. Negative sweep `grep -rniE 'rolePermissions|systemPermissions' web/src` confined to exactly the 4 expected files (`settings/page.tsx`, the dead handler, `use-admin.ts`, `mock-db.ts`). |
| 3 | A auditoria produz um veredito explícito por superfície (COVERED, ou lista de fixes aplicados), documentado antes de se considerar segura a criação de um 2º tenant pagante real | ✓ VERIFIED | `123-ISOL-AUDIT.md` (310 lines, independently counted via `wc -l`) contains a named `Veredito final por superfície` table with one row each for the tenant-admin console (Phase 120), usage report (Phase 122), RBAC lock (Phase 121), and `StorageService` — every row `COVERED`, none subjective. `StorageService`'s claim was pushed further than the audit's own shallow grep: not only does `grep -n 'tenantId' StorageService.java` return the 2 cited lines (`:39` signature, `:42` MinIO object-key prefix), but I additionally traced every call site (`ParecerController.java:527`, `ResourceController.java:405,1974,2722,2740`) and confirmed the `tenantId` argument is always the caller-local variable assigned from the same `getTenantId()` security-context helper used everywhere else in this codebase — never client-supplied input — closing the one place the audit's own evidence was thinner than elsewhere. The document closes with an explicit `## Pré-condição para provisionar um 2º tenant pagante real` section naming a **GO condicional** verdict tied to 4 concrete, named pre-conditions (3 pending manual SQL migrations + the Phase 120→121 production deploy-order constraint), plus an explicit `### O que esta auditoria NÃO cobriu` scope-boundary section (Pitfall 1 pattern, infra/network security, `_api-backup` cleanup) — satisfying "documented before considering it safe," not merely asserting safety. |

**Score:** 3/3 ROADMAP success criteria verified.

### Plan-Level Must-Have Truths (supporting detail, 11 total across 2 plans)

**Plan 01 (Critério de Sucesso 1) — 4/4**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Veredito explícito e nomeado para consola + relatório | ✓ VERIFIED | 14-row table in the Critério 1 section, all `COVERED` |
| Cada linha carrega o comando literal + valor observado | ✓ VERIFIED | Dedicated "Comandos de reproducao — Critério 1" section; all commands independently re-run this session with byte-identical output |
| Todos os caminhos backend de Tenant têm disposição, incl. os 2 não-gated-por-papel | ✓ VERIFIED | `/auth/me` (own-tenant) and `/setup/initialize` (singleton) both named with mechanism, not omitted |
| `TenantAdminSummaryResponse` declarada como única projeção + omissões enumeradas | ✓ VERIFIED | Confirmed by direct DTO read: 6 fields, 5 sensitive fields named as deliberately excluded in its own Javadoc |

**Plan 02 (Critério de Sucesso 2 + Pitfall 1 + veredito final) — 7/7**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `updateRbac` provado, com prova executável, como único call site de escrita Role/Permission | ✓ VERIFIED | Exhaustive grep enumeration (3 write occurrences, 2 non-`updateRbac` are startup-only) + `AdminControllerRbacAutorizacaoTest`/`AdminControllerPlataformaAdminContencaoTest` (21/21 green, re-run fresh) |
| Handler morto `_api-backup` traçado-e-descartado com provas | ✓ VERIFIED | 3 independent proofs re-executed fresh (manifest, apiFetch/rewrite, disconnected mock deps); framework convention independently confirmed against this Next.js version's own bundled docs |
| `updateUser`'s per-user write dispositionado como own-tenant-only | ✓ VERIFIED | Direct read: 404-before-mutation tenant check (`:234-236`) confirmed |
| Decisão do Pitfall 1 registada como risco residual aceite, 4 razões | ✓ VERIFIED | `123-CONTEXT.md`'s 4 numbered reasons reproduced faithfully, verbatim in substance, in `123-ISOL-AUDIT.md`'s dedicated section; baseline pattern description cross-checked against `.planning/research/PITFALLS.md` Pitfall 1 |
| Veredito nomeado por superfície + pré-condição de 2º tenant | ✓ VERIFIED | See Truth 3 above |
| ISOL-04 marcado concluído (checklist + tabela de rastreabilidade) | ✓ VERIFIED | `REQUIREMENTS.md:33` `- [x] **ISOL-04**`, `:82` `Complete` with correct file reference |
| Nenhum ficheiro sob `backend/`/`web/` alterado em toda a Fase 123 | ✓ VERIFIED | `git status --porcelain -- backend web` empty AND `git diff --stat` across the phase's entire commit range (context-commit through HEAD) for both `backend web` and `web/package.json backend/pom.xml` empty — stronger proof than a single-point-in-time check |

### Deferred Items

None. No gap identified in this phase was deferred to a later milestone phase — Phase 123 is the milestone's last phase, and its own scope-boundary section (`O que esta auditoria NÃO cobriu`) already names the one genuinely-deferred item (Pitfall 1's full repository-signature refactor) as belonging to a **future milestone**, not a later phase of v2.16. This is not a gap of this phase — it is an explicit, reasoned, and correctly-scoped non-goal (see Gaps Summary).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md` | ≥150 lines (Plan 02 floor), verdict tables for both criteria, reproduction commands, divergences section, Pitfall 1 decision, final verdict, go/no-go pre-condition | ✓ VERIFIED | 310 lines (`wc -l`, independently counted); both `Critério de Sucesso 1`/`2` tables present (14 + 8 rows, all `COVERED`); `Comandos de reproducao` sections for both criteria; divergences section present (documents 2 genuinely new Git-Bash/MSYS2 environment quirks, correctly classified as non-code findings); dead-handler section; Pitfall 1 decision section with 4 numbered reasons and the literal phrase "risco residual"; final verdict table naming Phase 120/121/122 + `StorageService`; go/no-go pre-condition + scope-boundary sections |
| `.planning/REQUIREMENTS.md` | ISOL-04 closed in checklist + traceability table, 15/15 milestone coverage | ✓ VERIFIED | Line 33: `- [x] **ISOL-04**: ...`; line 82: `| ISOL-04 | Phase 123 | Complete (...) |` citing `123-ISOL-AUDIT.md` and the Pitfall-1 residual-risk decision; lines 86-88: "v1 requirements: 15 total / Mapped to phases: 15 / Unmapped: 0" |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `123-ISOL-AUDIT.md` | `PlatformAdminController.java` | Verdict line citing the class gate + zero method-level exceptions | ✓ WIRED | Independently re-read source: line 53 exact string match `hasRole('PLATAFORMA_ADMIN')`, 0 method-level `@PreAuthorize` |
| `123-ISOL-AUDIT.md` | `TenantAdminSummaryResponse.java` | Verdict line on the projection shared by both surfaces | ✓ WIRED | 6 fields confirmed by direct read; 0 sensitive fields |
| `123-ISOL-AUDIT.md` | `web/src/hooks/use-platform-admin.ts` | Verdict line proving the 4 calls only reach `/platform/tenants*` | ✓ WIRED | 5 `apiFetch` occurrences confirmed, 0 outside `/platform/tenants*` |
| `123-ISOL-AUDIT.md` | `backend/.../controllers/AdminController.java` (`updateRbac`) | Verdict line citing `updateRbac` as the sole write path | ✓ WIRED | Confirmed via independent exhaustive-write-enumeration greps, byte-identical counts |
| `123-ISOL-AUDIT.md` | `web/.next/app-path-routes-manifest.json` | Proof that zero API routes exist in the built manifest | ✓ WIRED | Independently executed: 36 routes total, 0 API routes, 0 `_api-backup`/`admin/rbac` occurrences |
| `.planning/REQUIREMENTS.md` | `123-ISOL-AUDIT.md` | Traceability line citing the audit file for ISOL-04 | ✓ WIRED | Line 82 confirmed |

### Data-Flow Trace (Level 4)

Not applicable — this phase's only artifacts are two Markdown/documentation files (`123-ISOL-AUDIT.md`, `REQUIREMENTS.md`); it introduces zero new components, endpoints, or rendered state. The runtime data-flow of the 3 surfaces being audited (tenant console, usage report, RBAC tab) was already traced with `FLOWING` verdicts in `120-VERIFICATION.md`/`121-VERIFICATION.md`/`122-VERIFICATION.md`, and Phase 123 introduces no changes to that flow (confirmed by the zero-production-diff proof above).

### Behavioral Spot-Checks

All commands below were re-executed fresh in this verification session (not copied from `123-01-SUMMARY.md`/`123-02-SUMMARY.md`):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Criterion 1 backend regression | `mvn test -Dtest=PlatformAdminControllerTest,SetupControllerSingletonRegressaoTest,SetupServiceProvisionTenantTest` | `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` | ✓ PASS |
| Criterion 2 backend regression | `mvn test -Dtest=AdminControllerRbacAutorizacaoTest,AdminControllerPlataformaAdminContencaoTest` | `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` | ✓ PASS |
| RBAC UI structural gate | `cd web && pnpm verify:bloqueio-rbac` | `PASS A01`..`A12` (12/12), exit 0 | ✓ PASS |
| Routes manifest excludes all API/`_api-backup` routes | `node -e "...app-path-routes-manifest.json..."` | `rotas: 36 | rotas de api: 0` | ✓ PASS |
| AOP-proxy denial test is genuine (not tautological) | Read `PlatformAdminControllerTest.java` `createTenant_comRoleAdminDeTenantNormalERecusadoAntesDeOMetodoCorrer` | Uses real `ProxyFactory` (CGLIB) + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` (the same interceptor `@EnableMethodSecurity` uses in production), asserts `AccessDeniedException` AND `verify(setupService, never()).provisionTenant(...)` | ✓ PASS (disconfirmation check) |
| Zero production diff (point-in-time) | `git status --porcelain -- backend web` | (empty) | ✓ PASS |
| Zero production diff (whole-phase range) | `git diff --stat <context-commit>~1..HEAD -- backend web` | (empty) | ✓ PASS |
| Zero dependency-manifest changes | `git diff --stat -- web/package.json backend/pom.xml` | (empty) | ✓ PASS |
| Cited commit hashes genuinely exist, correct scope | `git show --stat 32a4151f/644fcc1a/2222cf37` | All 3 exist, messages match; diffs touch only `123-ISOL-AUDIT.md` (130+180 lines = 310 total) and `REQUIREMENTS.md` (2+2 lines) respectively | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists anywhere in this repository (confirmed: no top-level `scripts/`, no `backend/scripts/`; `web/scripts/` contains only 5 `verify-*.mjs` structural gates, none named `probe-*`). Not referenced by this phase's PLAN/SUMMARY files or ROADMAP success criteria. Not applicable.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| ISOL-04 | 123-01, 123-02 | Auditoria de isolamento dedicada cobre as novas superfícies (ecrã de tenants, relatório de utilização, bloqueio RBAC) antes de existir um 2º tenant pagante real | ✓ SATISFIED | `123-ISOL-AUDIT.md` (310 lines) with named `COVERED` verdicts for all 3 surfaces + `StorageService`, a go/no-go pre-condition declaration, and an explicit scope-boundary section; `REQUIREMENTS.md` checklist `[x]` + traceability `Complete` with correct file citation |

**Orphan check:** both `123-01-PLAN.md` and `123-02-PLAN.md` declare `requirements: [ISOL-04]` in frontmatter; `.planning/REQUIREMENTS.md`'s traceability table maps exactly `ISOL-04` to `Phase 123` (line 82) — identical, no orphans. Milestone-wide: `REQUIREMENTS.md` lines 86-88 confirm all 15 v1 requirements mapped to phases, 0 unmapped.

### Anti-Patterns Found

No blocking anti-patterns. Scanned both files this phase created/modified (`123-ISOL-AUDIT.md`, `REQUIREMENTS.md`) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` and empty-implementation patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `123-ISOL-AUDIT.md:194` | 194 | Substring hit on Portuguese word "TODO" (meaning "all/every": "0 linhas em TODO o web/src") | N/A — false positive | None; confirmed by direct reading, not a debt marker |

No `FIXME`/`TBD`/`XXX`/`HACK`/`PLACEHOLDER` markers found in either file. The plan text itself explicitly engineered around the historically-recurring false-positive class this codebase has hit 6 times before (a verification gate asserting `grep -c 'FIXED' == 0` against a document that legitimately discusses fix counts) — confirmed no such self-invalidating gate was created; fix counts are recorded positively ("Fixes aplicados: 0") throughout.

### Human Verification Required

None. This phase is documentation/confirmation-only (explicitly has no "UI hint" in ROADMAP.md, unlike Phases 120-122), introduces zero new UI, zero new runtime behavior, and zero production code changes (independently proven above). Every claim in `123-ISOL-AUDIT.md` that would normally require human judgment (visual appearance, live HTTP behavior, real-time behavior) is instead satisfied by **citing** HTTP/browser evidence already gathered and human-confirmed in Phases 120/121/122's own live-UAT checkpoints — which this verification independently confirmed those citations are accurate reproductions of, not invented. No `<human-check>` blocks exist in either plan (confirmed by grep), and no `123-HUMAN-UAT.md` was produced (correctly, since none was planned).

### Gaps Summary

No gaps. This is an unusually clean verification: every grep count, file excerpt, test result, and cross-phase citation in `123-ISOL-AUDIT.md` was independently re-derived from the current codebase state in this session, and every single one matched the audit's claims exactly — including several that I deliberately pushed harder on than the audit itself did (tracing `StorageService.upload`'s `tenantId` argument back to its `getTenantId()` origin at every call site, rather than accepting the audit's own shallow "grep for the string `tenantId`" evidence as sufficient; independently confirming the Next.js "private folder" convention against this exact installed version's bundled documentation rather than assuming prior framework knowledge given this project's own explicit Next-16 warning; and reading the actual body of the cited AOP-proxy test to confirm it is a genuine behavioral proof, not a tautological reflection check).

## Milestone-Level Assessment (v2.16, per explicit request — this is the milestone's last phase)

Because Phase 123 is the last phase of milestone v2.16, the verification task explicitly asked whether there is any reason not to consider v2.16 complete at this point, not just whether Phase 123 in isolation passes.

**Against v2.16's own contract (`.planning/REQUIREMENTS.md`), it is complete:** all 15 v1 requirements are checked off, the traceability table maps all 15 to a phase with a `Complete` status, and `.planning/ROADMAP.md`'s Progress table independently confirms all 7 phases (117-123) as `Complete`, with per-phase plan counts (2+3+4+6+4+4+2 = 25) matching `STATE.md`'s own reported "25/25 planos" exactly.

**Two items already resolved, not blocking, but worth naming for completeness (both pre-date this verification):**
- Phase 122 closed with 2 user-accepted `overrides:` entries in `122-VERIFICATION.md` for 6 live-browser visual scenarios (H1-H6) blocked by a disclosed Browser-MCP tooling failure, not a product defect — already a deliberate, dated, user-approved decision (`STATE.md`'s own "Operator Next Steps" explicitly states this "não bloqueia o marco").
- 4 background follow-up tasks (`task_a78e2d45`, `task_0ccb6ccf`, `task_3b2eae90`, `task_f7e73ed0`) are explicitly and correctly deferred per `STATE.md`, none blocking v2.16.

**Pre-existing, out-of-milestone-scope items surfaced for transparency, not counted as gaps against v2.16:** `STATE.md`'s own "Blockers/Concerns" and "Pending Todos" sections still list a small number of functional bugs predating v2.16 by multiple milestones and unrelated to multi-tenancy/isolation — most notably a v2.13/Phase-107 finding that new document uploads crash via a Hibernate `persist()`-vs-`merge()` mismatch, plus a Pareceres "Advogado" picker 500/permissions issue and an Agenda timezone-offset bug (both also v2.13). None of these appear in `REQUIREMENTS.md`'s v1 scope for v2.16, none were assigned to any of Phases 117-123, and Phase 123's own audit boundary is explicit that it covers only the 3 new v2.16 surfaces' tenant-isolation properties, not a full-application defect sweep. **They do not block considering v2.16 "complete" against its own stated requirements**, but a user reading "milestone complete" should not infer from it that these pre-existing, unrelated bugs are also resolved — they remain open, separately tracked, exactly where `STATE.md` already places them.

**Conclusion:** no reason found to withhold considering v2.16 complete. Phase 123 genuinely achieves its audit goal, ISOL-04 is genuinely closed, and the 15/15 requirement/7/7 phase/25/25 plan completion claim independently checks out against both `REQUIREMENTS.md` and `ROADMAP.md`.

---

_Verified: 2026-07-30T12:30:00Z_
_Verifier: Claude (gsd-verifier)_
