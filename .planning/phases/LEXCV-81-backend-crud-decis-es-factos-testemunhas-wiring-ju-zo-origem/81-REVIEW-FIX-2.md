---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
fixed_at: 2026-07-07T20:45:00Z
review_path: .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-REVIEW-2.md
iteration: 2
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-81: Code Review Fix Report (Iteration 2)

**Fixed at:** 2026-07-07T20:45:00Z
**Source review:** .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-REVIEW-2.md
**Iteration:** 2

**Summary:**
- Findings in scope: 3 (0 critical, 3 warnings, 0 info — IN-01/IN-02 left untouched per default critical+warning scope)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `deleteDecisao`'s new two-entity delete (added by the WR-02 fix) is not transactional

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 62d158b
**Applied fix:** Added `@Transactional` above `@PreAuthorize` on `deleteDecisao`, matching the exact placement convention already used on `createDecisao`. The `Documento` delete (and its storage-object cleanup) and the `Decisao` delete now roll back together on failure, closing the partial-write window the WR-02 fix (round 1) had reintroduced.

### WR-02: `updateFacto` was exposed to the `(processo_id, ordem)` unique constraint without matching `DataIntegrityViolationException` handling

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 47be5c3
**Applied fix:** Read `createFacto`'s existing try/catch pattern (lines ~1952-1961) and mirrored it exactly around `updateFacto`'s `factoRepository.save(facto)` call: wrapped the save in `try { ... } catch (DataIntegrityViolationException ex) { return 409 Conflict with the same Portuguese message used in createFacto }`. A client `PUT`-ing an `ordem` that collides with another `Facto` on the same `processoId` now gets a clean 409 instead of falling through to the 500 catch-all.

### WR-03: WR-04 (round 1)'s DB-level unique constraint on `Facto(processo_id, ordem)` has no accompanying migration and will not exist in production

**Files modified:** `backend/migrations/81-add-facto-ordem-unique-constraint.sql` (new file)
**Commit:** 60ff17a
**Applied fix:** Read `backend/migrations/74-cleanup-nif-documento-tipo.sql` first to confirm the project's manual-migration-script conventions (header comment explaining why the script is needed, explicit statement that it must be run manually since `ddl-auto=validate` in prod never creates schema and there is no Flyway/Liquibase, and which environments/deploy ordering it applies to). Verified against `Facto.java` that the actual table/column names are `t_facto` / `processo_id` / `ordem` (per `@Table(name = "t_facto")` and the existing `@Column(name = "processo_id")` annotation) before writing the SQL, rather than assuming Hibernate's default naming. Created `backend/migrations/81-add-facto-ordem-unique-constraint.sql` with:
```sql
ALTER TABLE t_facto ADD CONSTRAINT uk_facto_processo_ordem UNIQUE (processo_id, ordem);
```
mirroring the 74-script's header wording style, flagging this as a required manual production migration step to run before/during the deploy that added `@UniqueConstraint` to `Facto.java`.

## Skipped Issues

None — all 3 in-scope findings (WR-01, WR-02, WR-03) were fixed. IN-01 and IN-02 were intentionally left untouched — out of scope per the default critical+warning fix scope for this run.

## Verification

`cd backend && mvn -DskipTests package -q` completed successfully after all three fixes were committed — `target/backend-0.0.1-SNAPSHOT.jar` was produced with no compilation errors.

Note: WR-01 and WR-02 both touch `ResourceController.java`; to keep each finding's commit atomic, the WR-02 hunk was temporarily reverted before committing WR-01 alone, then reapplied and committed separately for WR-02.

---

_Fixed: 2026-07-07T20:45:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
