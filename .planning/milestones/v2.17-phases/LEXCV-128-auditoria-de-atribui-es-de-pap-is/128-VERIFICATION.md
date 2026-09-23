---
phase: 128-auditoria-de-atribui-es-de-pap-is
verified: 2026-09-22T16:35:00Z
status: human_needed
score: 4/4 roadmap success criteria verified in code; 2 discretionary test-coverage items flagged below (not roadmap must-haves, but explicitly requested checks)
overrides_applied: 0
human_verification:
  - test: "Plan 09's 8-step live checklist (open /settings, Auditoria tab, exercise filters/pager, confirm Phase 127 draft-cache survives a tab switch to Auditoria and back, confirm beforeunload still fires)"
    expected: "Tab renders real PT-CV sentences from a running backend; filters and pager work by click; RbacTab's unsaved-draft warning survives navigating away to Auditoria and back"
    why_human: "Requires a running backend + browser; explicitly deferred by the executor (128-09-SUMMARY.md records Task 3 as pending) and not re-attempted in this autonomous verification pass, consistent with the other MVP phases in this milestone"
---

# Phase 128: Auditoria de Atribuições de Papéis Verification Report

**Phase Goal:** Toda a alteração de papel e de atribuição de papel fica registada de forma permanente, com autoria e alvo, e é consultável — mas nunca editável — por escritório.
**Verified:** 2026-09-22
**Status:** human_needed (all roadmap success criteria verified in code and by passing automated suites; one deferred live-UI checklist item, carried forward unresolved from the plan, keeps status off `passed`)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria, AUDT-01..04)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Criar, alterar ou apagar um papel produz um registo de auditoria com autor, momento e o que mudou (AUDT-01) | VERIFIED | `OfficeRolesController.createRole/renameRole/deleteRole` call `auditoriaRbacService.registarPapelCriado/Renomeado/Apagado` after a successful `saveAndFlush`/`deleteById`+`flush()`, inside `@Transactional` methods. `AdminController.updateRbac` calls `registarPermissoesAlteradas` per role with a before/after permission-key diff. `AuditoriaRbacService` writes `autorNome`, `papelId`, `papelNome`, plus `nomeAntigo`/`nomeNovo` or `permissoesAdicionadas`/`permissoesRemovidas` into `detalhe`. |
| 2 | Atribuir ou remover um papel a um utilizador produz um registo de auditoria com autor, momento e utilizador alvo (AUDT-02) | VERIFIED | `AdminController.createUser/updateUser/deleteUser` all call `auditoriaRbacService.registarAtribuicoes(tenantId, principal, user, papeisAntes, papeisDepois, motivo)` after the user row is persisted/deleted. The diff is computed by `TenantRole.id`, never object identity; `deleteUser` snapshots `papeisAntes` before the hard delete and passes `MOTIVO_UTILIZADOR_ELIMINADO`. `SetupService.provisionTenant` also records the founding admin assignment with `motivo=MOTIVO_PROVISIONAMENTO` and `autor=null`. |
| 3 | Administrador de escritório consulta o registo de auditoria do seu próprio escritório, e apenas desse (AUDT-03) | VERIFIED | `GET /api/v1/admin/rbac/auditoria` (`AuditoriaRbacController`) is class-gated `@PreAuthorize("hasAuthority('rbac:manage')")`, takes no tenant parameter, and resolves tenant exclusively from `UserPrincipal`. `AuditLogRepository.buscarEventosRbac` filters `a.tenant_id = :tenantId` in both the select and the paired `countQuery`. Page size is clamped to 1..100 in the controller. Real-proxy authorization is proven in `AuditoriaRbacControllerTest` (`ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`); tenant isolation at the SQL level is proven in `AuditLogRepositoryIT` (`buscarEventosRbac_isolaPorTenantEExcluiEventosDeProcesso_newestFirst`, `..._tenantBSemFiltros_devolveApenasOSeuUnicoEvento`) — a genuine Testcontainers IT, compiles, but does not run in this environment (Docker npipe unavailable locally — known, accepted). |
| 4 | Nenhum ecrã ou endpoint da aplicação permite editar ou apagar um registo de auditoria já criado (AUDT-04) | VERIFIED | `AuditLogRepository extends Repository<AuditLog, Long>` (not `JpaRepository`/`CrudRepository`), declaring exactly `save`, `findByTenantIdAndProcessoIdOrderByTimestampDesc`, `buscarEventosRbac` — no `delete*`/`saveAll`/`saveAndFlush` exist in the compiled API. `AuditLog` carries `@Immutable`. `AuditLogImutabilidadeTest` (6 tests, all green) asserts the narrowed base type, the exact pinned method set with no `@Modifying`, no delete/update-prefixed method names, `@Immutable` presence, no mutating (`POST/PUT/PATCH/DELETE`) handler anywhere under `com.lexcv.controllers` whose combined path contains "audit", and no literal `DELETE FROM`/`UPDATE` SQL against `t_audit_log`/`auditlog` in any `.java` source file (with vacuous-pass guards: `candidatos.size() >= 5`, `contador[0] > 50`). The frontend `AuditoriaTab` has no menu, edit or delete affordance, and `pnpm verify:auditoria-rbac` (11/11 PASS) structurally proves the absence of `useMutation`, `DropdownMenu`, `Trash`/`Pencil` icons and raw mutating `fetch` methods in `auditoria-tab.tsx`. |

**Score:** 4/4 roadmap success criteria verified.

### Two Items Specifically Requested by the Coordinator — Checked Directly, Not Taken on Faith

**(a) Is there a real test proving `initializeSystem` and `MigracaoPapeisEscritorioService` write no audit event, or only doc comments and a grep?**

- `initializeSystem`: **Real runtime test exists.** `SetupServiceAtribuicaoAdminFundadorTest.initializeSystem_comAdminInstanciavel_atribuiTenantRoleAdminAoFundadorDeImediato` calls `setupService.initializeSystem(request)` with a fully-mocked `AuditoriaRbacService` and asserts `verifyNoInteractions(auditoriaRbacService)` at the end (line 172). This is a genuine Mockito interaction-verification test, not a doc comment.
- `MigracaoPapeisEscritorioService`: **No test exists — only a structural, grep-verifiable fact plus doc comments.** Reading the class (`backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java`), its `@RequiredArgsConstructor` fields are `TenantRepository`, `UserRepository`, `TenantRoleRepository`, `ResolucaoPapeisService`, `VerificacaoDerivaPapeisService`, `SetupService` — **no `AuditoriaRbacService` field at all**, so the class cannot call it; this is enforced by the compiler, not by a test. `MigracaoPapeisEscritorioServiceTest.java` contains zero references to "audit"/"Audit" anywhere — there is no `verifyNoInteractions` or equivalent runtime assertion for this class, unlike the sibling `initializeSystem` case. The class-level Javadoc (SetupService.java:113-120) states the omission is deliberate ("mesma categoria" as the Phase 126 conversion, no principal to attribute to), which is a reasonable design rationale, but it is documented and structurally-true, not test-proven the way `initializeSystem` is.

**(b) Is there a real test proving that if the audit write fails inside `provisionTenant`, the whole provisioning rolls back?**

- **No such test exists.** `SetupServiceProvisionTenantTest` (13 tests) never makes `auditoriaRbacService.registarAtribuicoes(...)` throw and then asserts that `tenant`/`user` persistence is undone — Mockito unit tests with mocked repositories cannot observe real transactional rollback in the first place (there is no real `PlatformTransactionManager` in play). `SetupServiceInstanciacaoMoldesTest.instanciarMoldes_quandoSaveFalha_excepcaoPropagaSemSerSilenciada` (Caso 6, cited by 128-06-SUMMARY.md as supporting evidence) tests a **different failure point** — `tenantRoleRepository.save` throwing during role instantiation — and only asserts that the exception propagates out of `provisionTenant`, not that an audit-write failure specifically triggers rollback. 128-06-SUMMARY.md itself concedes this is "proven structurally" by code inspection (the call sits before `return tenant`, `Propagation.MANDATORY` forces the audit write to join the caller's transaction) rather than by a dedicated test. The underlying mechanism (Spring `@Transactional` + `Propagation.MANDATORY`, meaning an exception from `registarAtribuicoes` propagates and the surrounding `@Transactional` proxy rolls back the whole method) is real, standard Spring behavior and not merely a narrative claim — but no automated test in this repository exercises it end-to-end, and the one IT class that could (`AuditLogRepositoryIT`, Testcontainers) does not cover this scenario and cannot run locally regardless (Docker npipe unavailable — known, accepted limitation).

**Assessment:** both are genuine test-coverage gaps in the "prove a negative"/"prove a failure-mode rollback" sense the coordinator asked about. Item (a)'s `MigracaoPapeisEscritorioService` half and item (b) are asserted by design/reasoning and are structurally sound (compiler-enforced absence of the dependency for (a); standard Spring transaction propagation for (b)), but neither is backed by a runtime test in this codebase today. Recommend adding: a `verifyNoInteractions`-style test for `MigracaoPapeisEscritorioService.migrar()` (trivial, same pattern as the `initializeSystem` case) and a unit test on `provisionTenant` that makes `auditoriaRbacService.registarAtribuicoes` throw and asserts the exception propagates out of `provisionTenant` (proves propagation; full rollback would still require the IT). These are process-quality gaps, not evidence that the runtime behavior is wrong — I am not blocking the phase on them, but flagging them plainly per the coordinator's explicit request rather than accepting the SUMMARY's "proven structurally" framing at face value.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/AuditLog.java` | `detalhe` column, `@Immutable`, updated vocabulary comments | VERIFIED | Nullable `detalhe text` column; `@Immutable`; `acao`/`entidade_tipo` comments list the full RBAC vocabulary. |
| `backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java` | Narrowed `Repository<AuditLog, Long>`, `buscarEventosRbac` | VERIFIED | Exactly 3 declared methods; native `@Query` + `countQuery` pair, tenant-scoped, role-by-id filter that survives renames. |
| `backend/src/main/java/com/lexcv/services/AuditoriaRbacService.java` | Sole writer/reader of RBAC audit events, `Propagation.MANDATORY` | VERIFIED | All `registar*` methods `@Transactional(propagation = MANDATORY)`; `listar`/`paraDto` never resolve names from `UserRepository`, only from the `detalhe` snapshot. |
| `backend/src/main/java/com/lexcv/controllers/RecusaTransacional.java` | Marks current transaction rollback-only on any non-2xx return | VERIFIED | `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`, catching `NoTransactionException` for direct-call unit tests. |
| `backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java` | `createRole`/`renameRole`/`deleteRole` `@Transactional`, audit on success only | VERIFIED | All three handlers `@Transactional`; every non-2xx path returns through `RecusaTransacional.recusar`; audit calls occur only after successful `saveAndFlush`/`deleteById`+`flush()`. |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | `updateRbac`/`createUser`/`updateUser`/`deleteUser` `@Transactional`, last-administrator lock | VERIFIED | All four handlers `@Transactional`; `guardaUltimoAdministrador` acquires `PESSIMISTIC_WRITE` via `tenantRoleRepository.bloquearParaAlteracaoDeDetentores` **before** `countByTenantRolesIdAndAtivoTrueAndIdNot`. |
| `backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java` | `GET /admin/rbac/auditoria`, `rbac:manage`, tenant from principal, bounded page | VERIFIED | Class-gated `@PreAuthorize`, no tenant param, `page>=0`, `1<=size<=100`. |
| `backend/src/main/java/com/lexcv/services/SetupService.java` | `provisionTenant` records founding-admin assignment; `initializeSystem` does not | VERIFIED | `provisionTenant` calls `registarAtribuicoes(..., autor=null, ..., MOTIVO_PROVISIONAMENTO)` after `instanciarMoldesEAtribuirAdminFundador`, inside its own `@Transactional`; `initializeSystem` never references `auditoriaRbacService`. |
| `backend/migrations/128-add-audit-log-detalhe.sql` | Idempotent `ADD COLUMN IF NOT EXISTS detalhe` | VERIFIED | Single statement, idempotent, header documents `t_audit_log`'s `ddl-auto`-origin provenance. |
| `backend/migrations/README.md` | Row 17, "7 of 17" re-runnable, "9 scripts... outstanding", "10 must not be re-run" | VERIFIED | All four exact strings present (`grep` confirmed); "6 of 16" absent. |
| `DEPLOYMENT.md` | "Phase 128 — auditoria de papéis" subsection | VERIFIED | Present, references migration script and README as source of truth. |
| `web/src/app/(dashboard)/settings/auditoria-tab.tsx` | Read-only Auditoria tab | VERIFIED | No mutation hooks, no per-row actions; renders `auditoriaEventoToSentence` output as `<span>` children. |
| `web/src/lib/auditoria-rbac.ts` | PT-CV sentence composer + null-name fallbacks | VERIFIED | Three fixed fallback phrases present; unknown `acao` never leaks a raw code. |
| `web/src/hooks/use-admin.ts` (`useOfficeRbacAuditoria`) | Pure `useQuery` against `/admin/rbac/auditoria` | VERIFIED (data flowing) | `apiFetch<AuditoriaRbacPageResponse>("/admin/rbac/auditoria" + query)`, no mutation in the same block. |
| `web/src/app/(dashboard)/settings/page.tsx` | Additive wiring; Phase 127 draft-cache/beforeunload/`RbacTab` intact | VERIFIED | `activeTab === "auditoria" && hasRbacManage`; `papeisComAlteracoesPorGravar`, `beforeunload`, and literal `function RbacTab() {` all present and untouched by this phase's diff. |
| `backend/src/test/java/com/lexcv/repositories/AuditLogImutabilidadeTest.java` | AUDT-04 structural gate | VERIFIED | 6/6 tests green; reflection + source-scan, vacuous-pass guards present. |
| `web/scripts/verify-auditoria-rbac.mjs` | Frontend structural read-only gate | VERIFIED | 11/11 assertions PASS when run directly. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `OfficeRolesController.createRole/renameRole/deleteRole` | `AuditoriaRbacService` | direct call after successful persist, same `@Transactional` method | WIRED | Confirmed by source read; refusals never reach the audit call. |
| `AdminController.updateRbac/createUser/updateUser/deleteUser` | `AuditoriaRbacService` | direct call after successful persist, same `@Transactional` method | WIRED | Confirmed by source read for all four handlers. |
| `SetupService.provisionTenant` | `AuditoriaRbacService` | direct call, same `@Transactional` method, `Propagation.MANDATORY` | WIRED | Confirmed; autor is `null` (never the invoking `PLATAFORMA_ADMIN`), preventing cross-tenant identity leakage. |
| `AuditoriaRbacController.listar` | `AuditoriaRbacService.listar` | direct call, tenant from `UserPrincipal` | WIRED | No tenant parameter exists to bypass. |
| `AuditLogRepository.buscarEventosRbac` | `t_audit_log.tenant_id` | native query `WHERE a.tenant_id = :tenantId` in both select and countQuery | WIRED | Two-tenant SQL-level isolation proven by `AuditLogRepositoryIT` (compiles; does not run locally — Testcontainers/Docker unavailable, known). |
| `web/.../settings/page.tsx` | `AuditoriaTab` | `activeTab === "auditoria" && hasRbacManage` conditional render | WIRED | Additive; does not disturb the `RbacTab` branch above it. |
| `AuditoriaTab` | `useOfficeRbacAuditoria` → `GET /admin/rbac/auditoria` | TanStack Query `useQuery` + `apiFetch` | WIRED (data flowing) | Real network call to the tenant-scoped backend endpoint; no static/hardcoded array. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `AuditoriaTab` | `auditoria.data.content` | `useOfficeRbacAuditoria` → `apiFetch("/admin/rbac/auditoria"...)` → `AuditoriaRbacController.listar` → `AuditoriaRbacService.listar` → `AuditLogRepository.buscarEventosRbac` (native SQL against `t_audit_log`) | Yes | FLOWING |
| `AuditoriaRow` (permission detail line) | `entry.permissoesAdicionadas/Removidas` | Same page response; resolved to labels via `officeRbac.data?.permissoes` (`useOfficeRbac` → `GET /admin/rbac`) | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend full test suite | `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` | `Tests run: 424, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` | PASS |
| SpotBugs (SAST) | `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` | `BugInstance size is 0`, `BUILD SUCCESS` | PASS |
| Frontend typecheck | `pnpm exec tsc --noEmit` | exit 0, no output | PASS |
| Frontend lint | `pnpm lint` | `0 errors, 20 warnings` (all pre-existing, unrelated to this phase: `react-hooks/incompatible-library`, `@next/next/no-img-element`) | PASS |
| Frontend unit tests | `pnpm test` | `Test Files 6 passed (6)`, `Tests 54 passed (54)` | PASS |
| Frontend production build | `pnpm build` | exit 0, all routes (including `/settings`) built | PASS |
| `pnpm verify:auditoria-rbac` | `node scripts/verify-auditoria-rbac.mjs` | `11/11 PASS` | PASS |
| `pnpm verify:juizo-origem` | | `PASS` | PASS |
| `pnpm verify:limite-utilizadores` | | `6/6 PASS` | PASS |
| `pnpm verify:consola-tenants` | | `6/6 PASS` | PASS |
| `pnpm verify:papeis-escritorio` | | `6/6 PASS` | PASS |
| `pnpm verify:relatorio-utilizacao` | | `6/6 PASS` | PASS |
| `pnpm verify:consola-moldes` | | `6/6 PASS` | PASS |
| `AuditLogImutabilidadeTest` (structural AUDT-04 gate) | `mvn test -Dtest=AuditLogImutabilidadeTest` (covered by the full-suite run above) | 6/6 green, included in the 424 total | PASS |

All numbers match exactly what the phase claimed: backend 424, frontend vitest 54, and all seven `verify:*` gates green.

### Probe Execution

No `scripts/*/tests/probe-*.sh` convention exists in this repository for this phase; the seven `web/scripts/verify-*.mjs` gates listed above serve the equivalent structural-probe role for the frontend and were executed directly (not merely narrated) as required. N/A — SKIPPED (no conventional `probe-*.sh` files declared or found).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| AUDT-01 | 128-01/02/03/04/05 | Papel criado/alterado/apagado gera registo com autor, momento, o que mudou | SATISFIED | See Truth #1 above. |
| AUDT-02 | 128-02/04/05/06 | Atribuição/remoção de papel gera registo com autor, momento, utilizador alvo | SATISFIED | See Truth #2 above. |
| AUDT-03 | 128-01/02/07 | Consulta restrita ao próprio tenant | SATISFIED | See Truth #3 above. |
| AUDT-04 | 128-01/09 | Registo não editável nem apagável por nenhuma superfície | SATISFIED | See Truth #4 above. |

REQUIREMENTS.md still shows AUDT-01..04 as `[ ]`/"Pending" — per the coordinator, this is deliberate bookkeeping updated after this verdict, not a delivery gap, and is not counted against the phase here.

No orphaned requirements found: all four AUDT-* IDs are declared across the nine plans' `requirements:` frontmatter and all are addressed.

### Anti-Patterns Found

None. Scanned all backend files touched by this phase (`AuditLog.java`, `AuditLogRepository.java`, `AuditoriaRbacService.java`, `AuditoriaRbacController.java`, `OfficeRolesController.java`, `RecusaTransacional.java`, `SetupService.java`) and the frontend files (`auditoria-tab.tsx`, `auditoria-rbac.ts`, `use-admin.ts`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/empty-implementation patterns. The only substring hits were false positives from Portuguese words ("método", "Todos") and a legitimate, well-documented `EVENTO_DESCONHECIDO` fallback constant (not a debt marker) plus a genuine `placeholder=` HTML attribute. No debt markers requiring a `#issue`/`DEF-*` reference were found.

### Human Verification Required

### 1. Plan 09's 8-step live checklist

**Test:** Log in as an office administrator, open Definições → Auditoria, exercise the "Utilizador alvo" and "Papel" filters and the pager against a real backend/DB; separately, open Definições → Papéis, make an unsaved edit, switch to the Auditoria tab and back, and confirm the Phase 127 draft-cache warning/`beforeunload` behavior still survives that tab switch.
**Expected:** Real PT-CV sentences render from live data; filters/pager work by click; the RBAC tab's unsaved-changes state is preserved across the tab switch.
**Why human:** Requires a running backend + PostgreSQL + browser session; this was deliberately deferred by the executor in 128-09-SUMMARY.md ("Task 3, the human-verify checkpoint, is recorded below as pending"), consistent with the same deferral pattern already accepted for Phases 124/125/127 in this milestone, and this autonomous verification pass does not start the backend or a browser per its own constraints.

### Gaps Summary

No roadmap success criterion (AUDT-01..04) failed. All four are backed by real, compiling, passing code: seven `@Transactional` write handlers that call `AuditoriaRbacService` only after a successful persist, route every refusal through `RecusaTransacional.recusar` (rollback-only), and write only display names (never email) into `detalhe` — proven by a dedicated `AuditoriaRbacServiceTest` privacy assertion with a distinctive-email canary. The tenant-scoped read endpoint takes tenant only from the principal, is gated by real-proxy `hasAuthority('rbac:manage')`, and bounds page size. Immutability is enforced structurally (narrowed repository + `@Immutable` + a 6-test reflection/source-scan gate that is designed to fail on regression) rather than by convention. The last-administrator race is closed with `PESSIMISTIC_WRITE` acquired strictly before the exclude-self count. Phase 127's draft-cache/`beforeunload`/`RbacTab` code is untouched, and the new tab is wired in additively. Migration 128 and its README/DEPLOYMENT bookkeeping match the plan's exact numbers (row 17, "7 of 17", "9... outstanding", "10 must not be re-run"). All requested health numbers reproduce exactly: backend 424/424, SpotBugs clean, `tsc` clean, lint 0 errors, vitest 54/54, build succeeds, all seven `verify:*` gates green.

Two specific, coordinator-requested checks came back as **coverage gaps in the tests, not defects in the runtime behavior**: (a) there is no dedicated test proving `MigracaoPapeisEscritorioService` writes no audit event — the guarantee is real (the class has no `AuditoriaRbacService` dependency at all, so it structurally cannot call it) but untested, unlike the equivalent `initializeSystem` case, which does have a `verifyNoInteractions` test; (b) there is no test proving that a failure inside `AuditoriaRbacService.registarAtribuicoes` during `provisionTenant` rolls back the whole provisioning — the mechanism (`@Transactional` + `Propagation.MANDATORY`) is standard, sound Spring behavior, but the repository has no test, unit or IT, that exercises this specific failure path; the one Testcontainers IT that could (`AuditLogRepositoryIT`) does not cover it and cannot run locally regardless. These are flagged as findings per explicit instruction, not treated as blockers, since the goal-level guarantees (structural non-audit of system migrations; MANDATORY-propagation same-transaction semantics) are independently verifiable in the source without a test.

The only reason status is not `passed` is the still-open human-verification item inherited from the plan itself (the live 8-step UI checklist), which per the workflow's own decision tree takes priority over an otherwise-clean automated score.

---

_Verified: 2026-09-22_
_Verifier: Claude (gsd-verifier)_
