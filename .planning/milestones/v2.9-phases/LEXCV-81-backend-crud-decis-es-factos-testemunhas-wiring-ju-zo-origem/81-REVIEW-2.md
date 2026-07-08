---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
reviewed: 2026-07-07T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/models/Facto.java
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-81: Code Review Report (Re-review of WR-01..WR-04 fixes)

**Reviewed:** 2026-07-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

This is a re-review of the fixes applied for the 4 warnings raised in `81-REVIEW.md` (already committed: WR-01 `@Transactional` on `createDecisao`, WR-02 orphaned-`Documento` cleanup in `deleteDecisao`, WR-03 clean-400 validation via `Map<String, Object>` on `updateDecisao`/`createTestemunha`/`updateTestemunha`, WR-04 unique constraint + 409 on `Facto`). All four original findings are confirmed fixed as described:

- **WR-01 confirmed fixed:** `createDecisao` (`ResourceController.java:1674`) now carries `@Transactional` above `@PreAuthorize`, so `documentoRepository.save(...)` and `decisaoRepository.save(...)` roll back together on failure.
- **WR-02 confirmed fixed:** `deleteDecisao` (`ResourceController.java:1809-1821`) now looks up the linked `Documento` (tenant-scoped), deletes its storage object (swallowing `StorageUnavailableException`), and deletes the `Documento` row before deleting the `Decisao`.
- **WR-03 confirmed fixed, adaptation verified:** `updateDecisao`, `createTestemunha`, `updateTestemunha` were switched from entity-typed `@RequestBody` to `@RequestBody Map<String, Object>`, with manual `TipoDecisao.valueOf()`/`TipoTestemunha.valueOf()` calls wrapped in try/catch returning clean 400s. Confirmed against `GlobalExceptionHandler.java` that no `HttpMessageNotReadableException` handler exists (only `MethodArgumentNotValidException`, `ConstraintViolationException`, and a catch-all `Exception` → 500), so this adaptation is necessary and correctly targeted. Field extraction is safe: every value is read with `.toString()` (never a blind cast), so a wrong-typed JSON value (e.g. a number sent for `data`) can't throw an uncaught `ClassCastException` — it instead fails `LocalDate.parse`/`TipoDecisao.valueOf`, which are both already wrapped in try/catch. All fields the respective entities actually need for these three endpoints (`data`/`tipo`/`resumo` for Decisão; `nome`/`contacto`/`tipo`/`notas` for Testemunha) are extracted, using the same camelCase JSON keys Jackson would have bound automatically — no field is silently dropped, and `documentoId` is deliberately still excluded from `updateDecisao` exactly as the original plan (`81-02-PLAN.md`) specified. `updateDecisao`/`createTestemunha`/`updateTestemunha` still perform the double-check parent-tenant + child-`processoId` ownership pattern before any mutation, unaffected by the parameter-type change.
- **WR-04 confirmed fixed (mechanism), but see WR-02/WR-03 below for gaps left by this fix):** `Facto.java:9` now declares `@Table(..., uniqueConstraints = @UniqueConstraint(columnNames = {"processo_id", "ordem"}))`, and `createFacto` (`ResourceController.java:1952-1961`) wraps the `synchronized` block in a `try/catch (DataIntegrityViolationException)` returning a clean 409.

However, the fixes for WR-02 and WR-04 each introduced a **new** gap not present before those fixes were applied, and the WR-03 adaptation surfaced a maintainability concern worth flagging. Three Warnings and two Info items are recorded below.

## Warnings

### WR-01: `deleteDecisao`'s new two-entity delete (added by the WR-02 fix) is not transactional — a late failure can leave a `Decisao` pointing at an already-deleted `Documento`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1797-1825`
**Issue:** Before the WR-02 fix, `deleteDecisao` deleted exactly one entity (`decisaoRepository.delete(decisao)`), so there was no atomicity concern. The WR-02 fix correctly added cleanup of the linked `Documento`, but did so as **two separate repository calls** — `documentoRepository.delete(d)` (line 1819, inside the `ifPresent` lambda) followed by `decisaoRepository.delete(decisao)` (line 1823) — with no `@Transactional` on the method. Spring Data's `SimpleJpaRepository` write methods are transactional per-call by default, so each `delete(...)` commits in its own transaction when the enclosing controller method isn't itself `@Transactional`. If `documentoRepository.delete(d)` succeeds but `decisaoRepository.delete(decisao)` subsequently throws (e.g. a transient DB error, or a future FK constraint added on `Decisao`), the `Documento` row is already gone while the `Decisao` row survives with a `documentoId` that now references nothing — the exact same class of partial-write bug that WR-01 fixed for `createDecisao`, reintroduced here for the delete path by the WR-02 fix itself.
**Fix:** Add `@Transactional` to `deleteDecisao`, matching the pattern already used on `createDecisao`:
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@DeleteMapping("/processos/{id}/decisoes/{decisaoId}")
public ResponseEntity<?> deleteDecisao(@PathVariable UUID id, @PathVariable Integer decisaoId) {
    ...
}
```

### WR-02: `updateFacto` is exposed to the new `(processo_id, ordem)` unique constraint added by WR-04 without the matching `DataIntegrityViolationException` handling — regresses to an unhandled 500

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1965-1992` (`updateFacto`), compare `:1941-1963` (`createFacto`, which does have the try/catch)
**Issue:** WR-04 added `@UniqueConstraint(columnNames = {"processo_id", "ordem"})` to `Facto` and wrapped `createFacto`'s write in `try/catch (DataIntegrityViolationException)` → 409. `updateFacto` also writes `ordem` directly from client input (`facto.setOrdem(payload.getOrdem())`, line 1989) via `factoRepository.save(facto)` (line 1991), with **no equivalent try/catch**. Before WR-04, no unique constraint existed, so `updateFacto` could never fail this way. After WR-04, a client `PUT`-ing an `ordem` value that collides with another `Facto` on the same `processoId` now throws `DataIntegrityViolationException`, which is uncaught here and falls through to `GlobalExceptionHandler`'s catch-all `Exception` handler — returning **500** with `ex.getClass().getSimpleName()`/`ex.getMessage()` leaked in the body, for what is a completely predictable client input error. This is exactly the failure mode WR-03 was written to eliminate for the *other* new endpoints, but it was left open here as a direct side effect of WR-04's own fix.
**Fix:** Wrap `updateFacto`'s save in the same try/catch as `createFacto`:
```java
try {
    facto.setDescricao(payload.getDescricao());
    facto.setData(payload.getData());
    facto.setOrdem(payload.getOrdem());
    return ResponseEntity.ok(factoRepository.save(facto));
} catch (DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Conflito ao atribuir ordem ao facto, tente novamente"));
}
```

### WR-03: WR-04's DB-level unique constraint on `Facto(processo_id, ordem)` has no accompanying migration and will not exist in production — the fix's stated "DB-level backstop" is inert where it matters most

**File:** `backend/src/main/java/com/lexcv/models/Facto.java:9`; `backend/src/main/resources/application-prod.yml:10`
**Issue:** `81-REVIEW.md`'s WR-04 explicitly framed the original risk as "provides no protection at all across multiple application instances (a common Spring Boot production topology)" and the fix's stated purpose was to add "a DB-level backstop" via `@UniqueConstraint`. Per `CLAUDE.md`, `ddl-auto` is `update` in dev but `validate` in prod (confirmed: `application-prod.yml:10` sets `ddl-auto: validate`), and this repository has no Flyway/Liquibase dependency (`backend/pom.xml` has no `flyway`/`liquibase` artifact) and no `db/migration` resource directory. `ddl-auto=validate` never creates or alters schema — it only checks the existing schema is compatible at startup. This means the new unique constraint will be silently created in every developer's local DB (via `update`) but **will never be created in the production database** unless a human manually runs the equivalent `ALTER TABLE t_facto ADD CONSTRAINT ... UNIQUE (processo_id, ordem)` out-of-band. Without that manual step, the exact multi-instance race WR-04 set out to close in production remains fully open there, while appearing closed in every dev/CI environment — a false sense of security introduced by this specific fix.
**Fix:** Either (a) add the constraint via an explicit SQL script that is documented as a required manual production migration step (and referenced from the phase SUMMARY/deployment notes), or (b) if this project later adopts Flyway/Liquibase, add a versioned migration for this constraint now so it isn't missed. At minimum, flag this in the phase's deployment checklist so it isn't silently skipped in production.

## Info

### IN-01: `Map<String, Object>` request bodies on `updateDecisao`/`createTestemunha`/`updateTestemunha` trade away compile-time type safety and API self-documentation

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1752, 1839, 1876`
**Issue:** The WR-03 fix is functionally correct, but using `Map<String, Object>` for these three endpoints means the request shape is no longer expressed anywhere in the type system — a typo in a field name (`"resumu"` instead of `"resumo"`) silently becomes a no-op instead of a compile error or a bind failure, and any future OpenAPI/springdoc generation for this controller (none present today) would see `Map<String, Object>` instead of a real schema.
**Fix:** Consider introducing small dedicated request DTOs (e.g. `DecisaoUpdateRequest(String data, String tipo, String resumo)`, `TestemunhaRequest(String nome, String contacto, String tipo, String notas)`) with the `tipo` field kept as `String` and parsed manually exactly as done today — this preserves the clean-400 behavior for invalid enum values while restoring compile-time safety for the other fields and improving readability over repeated `payload.get("x")` lookups.

### IN-02: Enum-parsing boilerplate (`TipoDecisao.valueOf`/`TipoTestemunha.valueOf` in try/catch → 400) is now duplicated across four handlers

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1696-1702, 1781-1786, 1850-1858, 1894-1902`
**Issue:** `createDecisao`, `updateDecisao`, `createTestemunha`, and `updateTestemunha` each contain a near-identical `try { X.valueOf(raw) } catch (IllegalArgumentException ex) { return 400 Map.of("message", "Parâmetro 'tipo' inválido") }` block. This duplication increases the chance that a future edit fixes the pattern in one place but not the others (as already happened once, per WR-03).
**Fix:** Extract a small generic helper, e.g. `private <E extends Enum<E>> E parseEnumOrNull(Class<E> type, String raw, ...)` or two small private methods `parseTipoDecisao(String)`/`parseTipoTestemunha(String)` returning either the parsed value or throwing a lightweight exception the caller maps to the existing 400 response — reduces four copies to one canonical implementation.

---

_Reviewed: 2026-07-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
