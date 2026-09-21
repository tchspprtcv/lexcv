---
phase: 126-migracao-de-papeis-existentes
verified: 2026-09-21T13:40:16Z
status: passed
score: 12/12 must-haves verified
overrides_applied: 0
---

# Phase 126: Migração de Papéis Existentes Verification Report

**Phase Goal:** Todo escritório que já existe antes deste marco é convertido, por script manual, para papéis próprios — sem que um único utilizador ganhe ou perca uma única permissão efectiva.
**Verified:** 2026-09-21T13:40:16Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria + MIGR-01/02/03 + adversarial checks)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Correr a migração instancia os moldes em cada tenant existente e reaponta cada `t_user_role`-equivalente para o papel de escritório, preservando as atribuições anteriores (MIGR-01, SC1) | VERIFIED | `MigracaoPapeisEscritorioService.migrar()` (backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java:68-129) reuses `SetupService.instanciarMoldes` (promoted package-private, body/signature untouched — `SetupService.java:180`), converges via two independent guards (`tenantRoleRepository.findByTenantId(tenantId).isEmpty()` for moldes, `alvo.equals(user.getTenantRoles())` per user), skips tenant `"ALCv"` by name before reading its users, and never mutates `t_user_role`/`User.roles`. Proven by 9 Mockito tests including two genuine RED demonstrations (reserved-tenant guard, drift-check wiring) in `MigracaoPapeisEscritorioServiceTest.java`. |
| 2 | Uma verificação pós-migração compara, utilizador a utilizador, as permissões efectivas antes/depois e falha visivelmente perante qualquer divergência (MIGR-02, SC2) | VERIFIED | `VerificacaoDerivaPapeisService` (backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java). `capturarAntes` reads exclusively `user.getRoles()`/global `Role.permissions`/`user.getPermissions()` — never `ResolucaoPapeisService`, never `user.getTenantRoles()`. `verificarSemDeriva` recomputes exclusively via `resolucaoPapeisService.resolverNomesPapeis`/`resolverPermissoesEfectivas`. Confirmed by reading both methods line-by-line: no shared code path exists between the two sides. Both parcels 1+2 come from the resolver; parcel 3 (ADMIN block) is obtained on **both** sides by calling `UserPrincipal.create` (2 call sites, verified) rather than duplicating the 20-permission literal list. Throws `IllegalStateException` naming email + symmetric-difference of lost/gained roles/permissions (never a boolean) — confirmed in source (lines 119-174) and by 16 Mockito tests with two genuine RED demonstrations (disabling the throw, forcing the office branch unconditionally) whose literal failure output is documented in `126-02-SUMMARY.md`. Live log evidence from the real (non-mocked) `mvn test` run performed for this verification shows `Deriva de autoridade detectada` messages correctly naming users and permission diffs. |
| 3 | `backend/migrations/README.md` + `DEPLOYMENT.md` document the exact place of the script in the deploy sequence (MIGR-03, SC3) | VERIFIED | `backend/migrations/README.md` updated at all 4 required locations (Path A/B, Re-run safety, Known execution status); arithmetic verified exactly as required: `**6 of 16**` (tolerant group), `**10 must not be re-run**` and `Nine of them fail loudly` (unchanged), `**8 scripts are outstanding**`. `DEPLOYMENT.md` gained `### Phase 126 — conversão de papéis de escritório e verificação de deriva zero` inside `## Database Schema — Two-Stage Boot`, answering what-to-run/when, where conversion happens, what a boot abort means, and how to roll back. |
| 4 | The read cutover reaches all nine call sites, none missed, none silently left on the old path | VERIFIED | `grep -rn 'getRoles()' backend/src/main/java/com/lexcv/` run directly (not trusted from SUMMARY) returns exactly: internal `user.getRoles()` reads inside `ResolucaoPapeisService`/`VerificacaoDerivaPapeisService`/`MigracaoPapeisEscritorioService` (the resolver/verifier/migration services themselves, expected), `AuthController.java:222` (`getMe`, deliberately untouched — reads the already-resolved `UserPrincipal`, with an in-code comment explaining why), and `ParecerController.java:423`/`494` (`principal.getRoles().contains("ADMIN")`, the two sites explicitly and correctly deferred to Phase 127 per `126-CONTEXT.md`). No unaccounted site found. `JwtAuthenticationFilter`, `AuthController.login/refresh/updateMe`, `AdminController.listUsers`/`createUser` response all confirmed reading via `resolucaoPapeisService.resolver*` by direct source inspection. |
| 5 | The write path is fixed: `AdminController.createUser`/`updateUser` populate `tenantRoles`, and the four `PLATAFORMA_ADMIN` guards survive | VERIFIED | `AdminController.java:180` (`createUser`) and `:317-318` (`updateUser`) call `resolucaoPapeisService.resolverPapeisDeEscritorio(principal.getTenantId(), roles)` and set `user.setTenantRoles(...)`, alongside the untouched global `roles` write (reversibility). All four `PLATAFORMA_ADMIN` guards present and unchanged: reject-before-lookup in `createUser` roles (line 157), reject in `createUser` permissions (line 202), reject in `updateUser` roles (line 299), reject in `updateUser` permissions (line 330); class-level `@PreAuthorize` guards on `updateRbac`/other admin-only endpoints (lines 376, 458) also intact. |
| 6 | The platform administrator (`plataforma@lexcv.cv`) is not locked out — zero tenant-role rows, must still resolve via global `PLATAFORMA_ADMIN` | VERIFIED | `ResolucaoPapeisService.usaPapeisDeEscritorio` falls back to global roles whenever `tenantRoles` is empty/null (line 50-52) — this is the single decision point used by every public method. Genuine RED demonstration exists and is documented with literal failure output (`126-02-SUMMARY.md`, `126-04-SUMMARY.md`): forcing the office branch unconditionally breaks exactly `plataforma@lexcv.cv`'s authentication (`expected: <true> but was: <false>` for `ROLE_PLATAFORMA_ADMIN`). `MigracaoPapeisEscritorioService` skips tenant `"ALCv"` by name before reading its users or instantiating moldes (RED-demonstrated separately). Real test classes (`JwtAuthenticationFilterPapeisEscritorioTest`, `ResolucaoPapeisServiceTest`, `MigracaoPapeisEscritorioServiceTest`) contain explicit `plataforma@lexcv.cv`/`PLATAFORMA_ADMIN` cases, part of the 291 green tests re-run for this verification. |
| 7 | Reversibility — `t_user_role.role_id` still populated everywhere, no `DROP COLUMN`/`DELETE`/`TRUNCATE` in this phase's diff | VERIFIED | `git diff 78c223a9..cc5aa93d -- backend/` scanned for `drop`/`delete from`/`truncate` as added lines: zero matches (the single hit found is prose inside a migration-script comment describing the absence of such statements, not an actual statement). `User.roles`/`t_user_role` association untouched in `User.java` diff; `MigracaoPapeisEscritorioService` never calls `setRoles`/`getRoles().clear()`/`deleteAll` (grep-gated in the plan, re-confirmed by direct read of the file). |
| 8 | Boot ordering — explicit `@Order` on both runners | VERIFIED | `DatabaseSeeder.java:24` — `@Order(Ordered.LOWEST_PRECEDENCE - 100)`; `MigracaoPapeisRunner.java:29` — `@Order(Ordered.LOWEST_PRECEDENCE)`. Confirmed by direct grep of both files — seeder runs strictly before the migration runner, resolving the previously-undefined relative order of two `CommandLineRunner`s on an authorization-sensitive path. |
| 9 | No permission marked `reservadaPlataforma = true` by this phase | VERIFIED | `backend/src/main/java/com/lexcv/models/Permission.java` (the file that carries the field) does **not** appear in this phase's `git diff --stat` (`78c223a9..cc5aa93d`) — the field's pre-existing default (`= true`, an unrelated Phase-124-era entity default, not phase-126 code) is untouched. `DatabaseSeeder`'s only phase-126 change is the `@Order` annotation (4 lines, `git diff --stat`-verified in `126-03-SUMMARY.md` and independently re-confirmed here) — its `seedRbac()`/`reservadaPlataforma(false)` logic is untouched. |
| 10 | Health — full backend suite, SpotBugs, and web `verify:*` gates all green | VERIFIED | Re-ran independently (not trusted from SUMMARY): `mvn test` → **291 tests, 0 failures, 0 errors, 0 skipped** (`awk` aggregation over `target/surefire-reports/*.txt`), matching the SUMMARY's claimed count exactly. `mvn spotbugs:check` → clean, exit 0, no findings. All six `web/package.json` `verify:*` gates (`verify:juizo-origem`, `verify:limite-utilizadores`, `verify:consola-tenants`, `verify:bloqueio-rbac`, `verify:relatorio-utilizacao`, `verify:consola-moldes`) run directly — all PASS, every individual assertion printed PASS, exit 0 each. These are static source-text assertion scripts (Node, no server/browser required), consistent with `git diff --stat -- web/` showing **zero** frontend files touched by this phase (no regression risk expected, and none found). |
| 11 | Reserved decision (Decision 2 residual) is recorded and prominent for Phase 127 | VERIFIED | `126-CONTEXT.md`'s `<deferred>` section explicitly names `ParecerController.java:411`/`482` (now `:423`/`:494` after Task 1's field addition) as "bloqueador de pré-requisito para a Phase 127, não um adiamento livre," explains the technical reason (principal carries names not entities), states they are correct today because `TenantRole.nome` is a literal molde-name copy, and states Phase 127 must close them before shipping PAPEL-04. `126-05-SUMMARY.md`'s "Next Phase Readiness" section repeats the same warning with updated line numbers and a phase-sweep grep proving no other site was missed. This is prominent in both the phase's context doc and the last plan's summary — the standard place a Phase 127 planner would look. |
| 12 | Requirements coverage — MIGR-01/02/03 satisfied by code, independent of REQUIREMENTS.md bookkeeping | VERIFIED (with a documentation gap noted below) | See truths 1-3 above for code evidence. `.planning/REQUIREMENTS.md`'s traceability table still lists MIGR-01/02/03 as **"Pending"** and their checkboxes as unchecked, unlike MOLD-*/CATL-* (Phase 124/125) which are marked "Complete" — this is a stale-documentation gap in REQUIREMENTS.md, not a code gap (see Anti-Patterns / Gaps Summary below). |

**Score:** 12/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/User.java` | `tenantRoles` EAGER `@ManyToMany` coexisting with `roles` | VERIFIED | Confirmed both associations present, `FetchType.EAGER` on `tenantRoles` |
| `backend/migrations/127-add-user-tenant-role-table.sql` | Idempotent `CREATE TABLE IF NOT EXISTS`, no backfill, no destructive statement | VERIFIED | Read in full; matches exactly |
| `backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java` | Single office-vs-global resolver | VERIFIED | One `isEmpty()` check gates all 4 public methods |
| `backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java` | Independent-by-construction drift check, throws not returns boolean | VERIFIED | `capturarAntes` and `verificarSemDeriva` confirmed genuinely disjoint data paths |
| `backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java` | Converging conversion reusing `instanciarMoldes`, skips ALCv, verifies drift in-transaction | VERIFIED | `@Transactional migrar()` reviewed line-by-line |
| `backend/src/main/java/com/lexcv/seed/MigracaoPapeisRunner.java` | Boot-time runner, ordered after seeder, uncaught exception aborts boot | VERIFIED | `@Order(LOWEST_PRECEDENCE)`, no try/catch around `migrar()` |
| `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` | Reads via resolver, one query per request preserved | VERIFIED | `tenantRoles` EAGER means no extra query |
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` | login/refresh/updateMe cut over, `getMe` deliberately untouched | VERIFIED | Confirmed with in-code comment explaining `getMe` |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | Read cutover + write-path `tenantRoles` population, 4 `PLATAFORMA_ADMIN` guards intact | VERIFIED | All 4 guards found at expected lines |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | `validateAdvogado` by provenance | VERIFIED | `temPapelDeMolde(user, NOME_MOLDE_ADVOGADO)` |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | Two sites by provenance, composite OR preserved | VERIFIED | `!temA && !temB` de Morgan translation confirmed correct |
| `backend/migrations/README.md` | Updated in all 4 locations, arithmetic unchanged where required | VERIFIED | `6 of 16`, `10 must not be re-run`, `Nine of them fail loudly`, `8 scripts are outstanding` all confirmed |
| `DEPLOYMENT.md` | Phase 126 sub-section in Two-Stage Boot | VERIFIED | Confirmed present with all 4 operator questions answered |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `MigracaoPapeisEscritorioService` | `SetupService.instanciarMoldes` | Direct method call (package-private) | WIRED | Zero `TenantRole.builder()` calls in the migration service — single reuse point |
| `MigracaoPapeisEscritorioService` | `VerificacaoDerivaPapeisService.verificarSemDeriva` | Called at end of `@Transactional migrar()` | WIRED | Confirmed inside the same transaction; RED-demonstrated as genuinely load-bearing |
| `JwtAuthenticationFilter` | `ResolucaoPapeisService` | `resolverNomesPapeis`/`resolverPermissoesEfectivas` | WIRED | Confirmed at the critical per-request authority path |
| `AdminController.createUser`/`updateUser` | `ResolucaoPapeisService.resolverPapeisDeEscritorio` | Write path populating `tenantRoles` | WIRED | RED-demonstrated (removing the call broke a real test) |
| `ParecerController`/`ResourceController` | `ResolucaoPapeisService.temPapelDeMolde` | Provenance predicate replacing name comparison | WIRED | RED-demonstrated for both the single-molde and composite-OR cases |
| `DatabaseSeeder` | `MigracaoPapeisRunner` | `@Order` boot sequencing | WIRED | Explicit precedence confirmed by direct grep of both files |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend suite green, count matches claim | `mvn test` (JDK 23) + `awk` aggregation of `target/surefire-reports/*.txt` | `TestsRun=291 Failures=0 Errors=0 Skipped=0` | PASS |
| SpotBugs/FindSecBugs clean | `mvn spotbugs:check` | Exit 0, no findings | PASS |
| Frontend static verify gates | `pnpm run verify:juizo-origem` / `verify:limite-utilizadores` / `verify:consola-tenants` / `verify:bloqueio-rbac` / `verify:relatorio-utilizacao` / `verify:consola-moldes` | All PASS, exit 0 each, every individual assertion printed `PASS` | PASS |
| Read-cutover sweep, all nine sites accounted for | `grep -rn 'getRoles()' backend/src/main/java/com/lexcv/` (run directly, not from SUMMARY) | Exactly resolver/verifier/migration internals + `AuthController.java:222` (getMe) + `ParecerController.java:423,494` (deferred) | PASS |
| No DROP/DELETE/TRUNCATE introduced by this phase | `git diff 78c223a9..cc5aa93d -- backend/` scanned for added `drop`/`delete from`/`truncate` lines | Zero matches (only prose describing their absence) | PASS |
| `Permission.java` (reservadaPlataforma field) untouched by this phase | `git diff 78c223a9..cc5aa93d --stat -- backend/` | File absent from the diff | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|------------|--------------|--------|----------|
| MIGR-01 | 126-01, 126-03, 126-04, 126-05 | Migração instancia moldes e repõe atribuições sem ganho/perda de acesso | SATISFIED | Truths 1, 4, 6, 7 above |
| MIGR-02 | 126-02, 126-03 | Verificação pós-migração compara efectivas antes/depois, falha visivelmente | SATISFIED | Truth 2 above |
| MIGR-03 | 126-01 | Documentado em README.md + DEPLOYMENT.md | SATISFIED | Truth 3 above |

No orphaned requirements — REQUIREMENTS.md maps only MIGR-01/02/03 to Phase 126, and all three plans declare them across the 5 plans' frontmatter.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `.planning/REQUIREMENTS.md` | 36-38, 93-95 | MIGR-01/02/03 checkboxes and traceability status left as `Pending`/unchecked despite Phase 126 completing them (inconsistent with MOLD-*/CATL-* rows for Phase 124/125, which are marked `Complete`) | Info | Stale project bookkeeping only — does not affect runtime behavior or the phase's actual goal achievement. Should be corrected as a small follow-up so REQUIREMENTS.md stays trustworthy for Phase 127/128 planning, but does not block progression. |

No debt markers (`TBD`/`FIXME`/`XXX`) found in any file touched by this phase. The two `TODO`-looking grep hits (`JwtAuthenticationFilter.java:54`, `MigracaoPapeisRunner.java:12`) are the Portuguese word "todo" (= "every"/"all"), not English debt-marker comments — verified by reading both lines in context.

### Human Verification Required

None. All must-haves resolved programmatically with direct source inspection, a full re-run of the backend test suite (291/291 green), SpotBugs (clean), and all six frontend static verify gates (all PASS) — none of this phase's must-haves depend on visual, real-time, or external-service behavior that only a human could confirm.

### Gaps Summary

No blocking gaps. One informational documentation gap: `.planning/REQUIREMENTS.md`'s traceability table (and the MIGR-01/02/03 checkboxes in the requirements list) were not updated to `Complete` when Phase 126 closed, unlike the pattern established for Phase 124 (CATL-*) and Phase 125 (MOLD-*). This does not affect code correctness — every truth backing MIGR-01/02/03 was independently verified against the actual codebase in this report — but should be fixed as routine bookkeeping so future phase planning (127/128) reads an accurate requirements ledger.

The one deliberately-deferred item from this phase (`ParecerController.java:423`/`494`, `principal.getRoles().contains("ADMIN")`) is correctly recorded in `126-CONTEXT.md` and repeated prominently in `126-05-SUMMARY.md`'s "Next Phase Readiness" section as a **hard prerequisite** Phase 127 must close before shipping PAPEL-04 (role renaming). This is not a Phase 126 gap — it is accepted, documented scope per `126-CONTEXT.md` Decision 2 — but is flagged here again to maximize the chance it survives into Phase 127 planning.

---

_Verified: 2026-09-21T13:40:16Z_
_Verifier: Claude (gsd-verifier)_
