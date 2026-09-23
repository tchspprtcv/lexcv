---
phase: 124-cat-logo-de-permiss-es-em-base-de-dados
verified: 2026-09-20T21:45:00Z
status: human_needed
score: 3/3 roadmap success criteria verified (automated evidence); 1 human checkpoint outstanding
overrides_applied: 0
human_verification:
  - test: "124-02-PLAN.md Task 2 (checkpoint:human-verify, gate=blocking) — live RBAC screen check in Definições → Permissões/RBAC"
    expected: "Matrix shows 20 permissions across 8 modules in the order Clientes, Processos, Agenda, Documentos, Financeiro, Pareceres, Notificações, Administração; the 3 previously-invisible permissions (Iniciar Processos, Administrar Processos, Eliminar Lançamentos Financeiros) appear labelled inside their correct module; no unlabelled boxes or raw technical keys; PLATAFORMA_ADMIN absent from every row; ordering stable across 3 reloads; after a backend restart the count stays 20 and no ADVOGADO/TECNICO/ASSISTENTE checkbox is lost."
    why_human: "Requires a running backend (PostgreSQL + MinIO reachable) and a running frontend, and visual confirmation of DOM ordering/labels that cannot be derived from static analysis alone. This phase executed inside an autonomous milestone run with no human present, so 124-02-SUMMARY.md explicitly records the checkpoint as PENDENTE — not approved, not simulated."
---

# Phase 124: Catálogo de Permissões em Base de Dados Verification Report

**Phase Goal:** O catálogo de permissões (nome técnico, rótulo, descrição, categoria) é servido a partir de `t_permission`, não de uma lista Java hardcoded, e sobrevive a qualquer reinício sem apagar atribuições existentes.
**Verified:** 2026-09-20T21:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | O catálogo de permissões devolvido pela aplicação reflecte linhas de `t_permission` (rótulo, descrição, categoria incluídos), não a lista Java hardcoded | ✓ VERIFIED | `AdminController.getRbac()` (backend/src/main/java/com/lexcv/controllers/AdminController.java:381-393) calls `permissionRepository.findAllByReservadaPlataformaFalse()` and maps via `toPermissionDef`. `grep -v comments \| grep -c 'new RbacResponse.PermissionDefDto('` on AdminController.java returns `0` — confirmed by direct grep, not the plan's claim. The old 17-entry `Arrays.asList` literal is gone, not dead/bypassed code. |
| 2 | Reiniciar o backend semeia permissões novas e actualiza rótulo/descrição/categoria das existentes, sem apagar nenhuma atribuição já persistida | ✓ VERIFIED | `DatabaseSeeder.seedRbac()` (lines 386-408) does `findByNome(...).map(existente -> {setRotulo/setDescricao/setModulo/setOrdem/setReservadaPlataforma(false); return existente;}).orElseGet(builder...)` then `save()` — never touches `nome`/`id` on the update branch. Repo-wide grep `grep -rnE "permissionRepository\.(delete\|deleteAll\|deleteById\|deleteAllInBatch)" backend/src/main/` returns zero matches anywhere in `backend/src/main/`, not just in the seeder. `DatabaseSeederCatalogoPermissoesTest.java` asserts `verify(permissionRepository, never()).delete(any())` etc. (lines 181-184) and a refresh-same-instance/same-id/same-nome scenario (lines 172-174). |
| 3 | As permissões reservadas à plataforma nunca aparecem em nenhum catálogo servido a um escritório | ✓ VERIFIED | `Permission.reservadaPlataforma` defaults closed: `@Column(..., columnDefinition = "boolean not null default true")` + `@Builder.Default private Boolean reservadaPlataforma = true;` (Permission.java:61-63) — a row entering `t_permission` outside the declared catalogue defaults to reserved (fail-closed). Enforced at query level: `PermissionRepository.findAllByReservadaPlataformaFalse()` is the only read path used by `getRbac()` (`grep -c 'permissionRepository.findAll()'` in AdminController.java after comment-stripping returns `0`). All 20 catalogue entries are explicitly lowered to `false` in `seedRbac()`. |

**Score:** 3/3 ROADMAP success criteria verified by direct codebase inspection.

### Additional Verification Items (from task instructions)

| # | Item | Status | Evidence |
|---|------|--------|----------|
| 4 | Response contract intact — `PermissionDefDto` still `key`/`nome`/`descricao`/`modulo`, zero `web/` changes | ✓ VERIFIED | `RbacResponse.java` unchanged (4-field DTO). `git status --porcelain -- web/` empty; `git log --oneline -- web/` shows no phase-124 commits touching it. `RbacTab` module grouping (`web/src/app/(dashboard)/settings/page.tsx:849` `Array.from(new Set(systemPermissions.map(p => p.modulo)))`) relies on JS `Set` insertion order; backend now sorts by `Permission.ordem` (`Comparator.nullsLast(...).thenComparing(Permission::getNome)`, AdminController.java:390-391) and `CATALOGO_PERMISSOES` declares modules in exactly the order Clientes(10-20), Processos(30-60), Agenda(70-80), Documentos(90-100), Financeiro(110-130), Pareceres(140-170), Notificações(180), Administração(190-200) — reproducing the previous 8-module sequence, not reordering it. |
| 5 | Phase 119 invariant not inverted | ✓ VERIFIED | `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList())` present unchanged (DatabaseSeeder.java:453, `grep -c` = 1). `getRbac()` still `continue`s past `PAPEL_PLATAFORMA` when building `rolePermissions` (AdminController.java:367-369). |
| 6 | Scope fence — write authority unchanged, no Phase 125/127 leakage | ✓ VERIFIED | `updateRbac` still `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` only (AdminController.java:423) — no `rbac:manage` authority introduced (CATL-04 correctly deferred to Phase 127, per REQUIREMENTS.md mapping). No molde/RoleTemplate files exist (`find ... -iname "*molde*" -o -iname "*RoleTemplate*"` empty). Full phase diff (`d9a2b448~1..a83ec495`) touches exactly 9 files: the ones declared in both plans' `files_modified`, nothing more. |
| 7 | Test and build health | ✓ VERIFIED | `mvn test` (JDK 23): **197/197 tests pass**, `BUILD SUCCESS`. Targeted re-run of `DatabaseSeederCatalogoPermissoesTest`, `AdminControllerRbacCatalogoTest`, `AdminControllerRbacAutorizacaoTest`, `DatabaseSeederPlataformaAdminTest`, `AdminControllerPlataformaAdminContencaoTest` also clean. `mvn spotbugs:check` exits 0, no new findings. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Permission.java` | 5 new descriptive/reservation columns, fail-closed default | ✓ VERIFIED | All 5 fields present with exact literals claimed (`private Boolean reservadaPlataforma`, `@Builder.Default`, `columnDefinition = "boolean not null default true"`); `@EqualsAndHashCode(of = "nome")` preserved. |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` | Declarative 20-entry catalogue, non-destructive upsert | ✓ VERIFIED | `CATALOGO_PERMISSOES` has 20 `CatalogoEntry` records including `processos:create`, `processos:manage`, `financeiro:manage` with distinct labels; upsert loop never deletes. |
| `backend/src/main/java/com/lexcv/repositories/PermissionRepository.java` | Query-level exclusion of reserved permissions | ✓ VERIFIED | `findAllByReservadaPlataformaFalse()` present, is the sole read path used by `getRbac()`. |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | `getRbac()` reads DB, zero literal `PermissionDefDto` | ✓ VERIFIED | Confirmed by grep (0 literals) and by reading the method body directly. |
| `backend/migrations/124-add-permission-catalogo-columns.sql` | Idempotent manual migration script | ✓ VERIFIED | 5 `ADD COLUMN IF NOT EXISTS` statements, `RAISE NOTICE` diagnostic block, cataloged 3× in `backend/migrations/README.md` (Path B ordinal 13→before 125 at 14, Re-run-safety "4 of the 14", Known execution status "6 scripts are outstanding" with `124` marked Pending). |
| `backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java` | Mockito proof of create/refresh/never-delete | ✓ VERIFIED | 208 lines, asserts field values, `never()).delete/deleteAll/deleteById/deleteAllInBatch`, distinct/stable `ordem`. |
| `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java` | Proof that systemPermissions comes from repository rows | ✓ VERIFIED | 163 lines, covers data-origin, field mapping, reserved/unlabelled exclusion, stable ordering, `rolePermissions` non-regression. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AdminController.getRbac()` | `PermissionRepository.findAllByReservadaPlataformaFalse()` | direct call, never `findAll()` | ✓ WIRED | Confirmed by reading method body and by grep on `findAll()` count = 0 in that method. |
| `AdminController.getRbac()` | `RbacResponse.PermissionDefDto` | `toPermissionDef` private mapper using named builder fields | ✓ WIRED | `key(p.getNome())`, `nome(p.getRotulo())`, `descricao`, `modulo` — no positional-constructor swap risk. |
| `backend/migrations/README.md` | `backend/migrations/124-add-permission-catalogo-columns.sql` | inventory row, re-run-safety row, execution-status row | ✓ WIRED | All 3 references present and internally consistent (13→14 renumbering, "4 of the 14", "6 scripts are outstanding"). |
| `DatabaseSeeder.seedRbac()` | `PermissionRepository.save()` | upsert by `findByNome`, never delete | ✓ WIRED | Confirmed by code read + repo-wide delete grep (0 hits in all of `backend/src/main/`) + Mockito test. |
| `web/.../settings/page.tsx` (`RbacTab`) | `GET /api/v1/admin/rbac` | `systemPermissions` consumed unchanged | ✓ WIRED | Zero `web/` diff in this phase; module-order derivation logic unchanged and reproduced correctly by backend `ordem` sort. |

### Data-Flow Trace (Level 4)

`systemPermissions` traced end-to-end: `t_permission` rows (seeded/updated by `DatabaseSeeder.seedRbac()` on every boot) → `PermissionRepository.findAllByReservadaPlataformaFalse()` (real JPA derived query, not a static/empty return) → filtered (blank-label exclusion, logged) → sorted by `ordem` → mapped to `PermissionDefDto` → `RbacResponse.systemPermissions`. No hardcoded/static fallback found in `getRbac()`. Status: ✓ FLOWING.

### Behavioral Spot-Checks

Not run as live HTTP checks — `<build_environment>` instructions explicitly prohibit starting the backend/DB/MinIO. Equivalent evidence gathered via unit/integration-level Mockito tests instead (`AdminControllerRbacCatalogoTest`, `DatabaseSeederCatalogoPermissoesTest`), which exercise the actual handler and seeder code paths without mocking away the logic under test.

### Probe Execution

No `scripts/*/tests/probe-*.sh` convention or PLAN-declared probes found for this phase. Step 7c: SKIPPED (no runnable probes declared).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| CATL-01 | 124-01, 124-02 | Catálogo servido da BD, não de lista embutida | ✓ SATISFIED | See Truth #1 above. |
| CATL-02 | 124-01 | Arranque semeia/actualiza sem apagar atribuições | ✓ SATISFIED | See Truth #2 above. |
| CATL-03 | 124-01, 124-02 | Permissões reservadas nunca oferecidas ao escritório | ✓ SATISFIED | See Truth #3 above. |
| CATL-04 | Phase 127 (not this phase) | `rbac:manage` governs who edits permissions in-office | N/A — correctly out of scope | Confirmed not attempted: `updateRbac` gate unchanged, no leakage. |

No orphaned requirements: REQUIREMENTS.md maps only CATL-01/02/03 to Phase 124, and all three are claimed by the two plans' `requirements` frontmatter.

### Anti-Patterns Found

None. Scanned all 9 files touched by this phase (`Permission.java`, `DatabaseSeeder.java`, `UserPrincipal.java`, `AdminController.java`, `PermissionRepository.java`, the migration `.sql`, `migrations/README.md`, and both new test files) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER|placeholder|coming soon|not yet implemented|not available` — zero matches.

### Human Verification Required

### 1. Live RBAC screen confirmation (124-02-PLAN.md Task 2, `checkpoint:human-verify`, `gate="blocking"`)

**Test:** Start backend (`mvn spring-boot:run` with valid `.env`) and frontend (`pnpm dev`), log in as `admin@alcv.cv`, navigate to Definições → Permissões/RBAC, and walk through the 10 steps in 124-02-PLAN.md / 124-02-SUMMARY.md ("Pending Human Verification" section): confirm 20 permissions across 8 modules in the expected order, the 3 newly-catalogued permissions appear labelled in their correct module, no unlabelled/raw-key boxes, `PLATAFORMA_ADMIN` absent, stable ordering across 3 reloads, and — critically for CATL-02 — that a backend restart does not drop any ADVOGADO/TECNICO/ASSISTENTE checkbox.

**Expected:** All 7 acceptance criteria in the plan's checkpoint task pass; user responds "aprovado".

**Why human:** Requires a live database (PostgreSQL) and MinIO connection plus visual/DOM confirmation of rendering order and labels — this cannot be derived from static grep/code-read alone, and the task instructions explicitly forbid starting the backend/DB in this verification pass. 124-02-SUMMARY.md is explicit that this checkpoint was deliberately **not** executed (autonomous milestone run, no human present) and records itself as "PENDENTE — não invocado, não simulado. Não reivindicado como aprovado." This is a known, honestly-disclosed pending item, not a gap discovered by this verification.

### Gaps Summary

No gaps found. All three ROADMAP success criteria (CATL-01, CATL-02, CATL-03) are independently verified against the actual codebase — not taken on SUMMARY.md's word:
- The old 17-entry hardcoded `Arrays.asList` literal is confirmed gone by grep (0 occurrences after comment-stripping) and by direct reading of `getRbac()`.
- The non-destructive upsert claim was checked at the strongest level asked for: a repo-wide grep for any `permissionRepository.delete*` call across all of `backend/src/main/`, not just inside the seeder — zero hits. The update branch of the upsert visibly never writes `nome`/`id`.
- The fail-closed default (`reservadaPlataforma = true`) is enforced both at the entity/DDL level and at the query level (`findAllByReservadaPlataformaFalse`), and the seeder explicitly flips all 20 catalogue entries to `false` on boot.
- `mvn test` (197/197) and `mvn spotbugs:check` (clean) were run for real in this environment, not copied from the SUMMARY.
- The one item not independently confirmable in this pass — live-screen rendering with a real DB — is the plan's own deliberately-deferred human checkpoint, honestly disclosed in 124-02-SUMMARY.md as pending. It does not indicate any deficiency in the delivered code; it is an outstanding confirmation step, not a defect. Because it is a `gate="blocking"` human-verify task that was never approved, `status: human_needed` is the correct classification rather than `passed`.

---

*Verified: 2026-09-20T21:45:00Z*
*Verifier: Claude (gsd-verifier)*
