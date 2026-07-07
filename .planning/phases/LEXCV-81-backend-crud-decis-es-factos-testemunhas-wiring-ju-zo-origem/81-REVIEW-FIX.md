---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
fixed_at: 2026-07-07T20:22:52Z
review_path: .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase LEXCV-81: Code Review Fix Report

**Fixed at:** 2026-07-07T20:22:52Z
**Source review:** .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (0 critical, 4 warnings, 0 info)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### WR-01: `createDecisao` is not transactional — a late failure orphans the just-created Documento (and its uploaded file)

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 91031c9
**Applied fix:** Added `@Transactional` above `@PreAuthorize` on `createDecisao` (matching the placement convention used elsewhere in the file, e.g. `registarDecisaoConflito`), so the `Documento` save and `Decisao` save roll back together on failure. As noted in REVIEW.md, this does not roll back the already-uploaded storage object (an external side effect), but it removes the orphaned-DB-row failure mode.

### WR-02: `deleteDecisao` does not clean up the linked `Documento` row or its storage object

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 40955ec
**Applied fix:** Before `decisaoRepository.delete(decisao)`, added a tenant-scoped lookup of the linked `Documento` (when `decisao.getDocumentoId() != null`) and deleted both its storage object (`storageService.delete`) and its DB row. Storage deletion is wrapped in a try/catch for `StorageUnavailableException` so a storage outage does not block decisão deletion — matches the exact fix snippet in REVIEW.md.

### WR-03: Missing request-body validation on new Decisão/Testemunha/Facto write endpoints allows required-field violations to surface as unhandled 500s

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 9be1ebc
**Applied fix:** Confirmed via `GlobalExceptionHandler` (only `MethodArgumentNotValidException`, `ConstraintViolationException`, and a catch-all `Exception` handler exist — no `HttpMessageNotReadableException` handler) that invalid enum JSON values currently fall through to the 500 catch-all, not a clean 400. Applied fixes:
- `updateDecisao`: changed `@RequestBody Decisao payload` to `@RequestBody Map<String, Object> payload` so `data`/`tipo` can be validated (null/blank → 400) and `tipo` parsed via `TipoDecisao.valueOf()` in a try/catch (mirrors `createDecisao`'s existing pattern), instead of failing enum deserialization before the method body runs.
- `createTestemunha` / `updateTestemunha`: same approach — `Map<String, Object>` body, `nome` required (400 if null/blank), `tipo` optional but validated via `TipoTestemunha.valueOf()` try/catch when present (matches `Testemunha.tipo` having no `nullable = false` constraint).
- `createFacto`: added a null/blank check on `facto.getDescricao()` before assignment (maps to a `nullable = false` column); `ordem` doesn't need validation here since it's always computed server-side, overwriting any client-supplied value.
- `updateFacto`: added null/blank check on `descricao` and null check on `ordem`, mirroring the `ClienteContacto`/`ClienteNota` validation pattern cited in REVIEW.md (lines ~634-639) and the exact snippet given in the Fix section.

### WR-04: `synchronized (FactoRepository.class)` in `createFacto` only guarantees atomicity within a single JVM instance, with no DB-level backstop

**Files modified:** `backend/src/main/java/com/lexcv/models/Facto.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 819f658
**Applied fix:** Added `@Table(name = "t_facto", uniqueConstraints = @UniqueConstraint(columnNames = {"processo_id", "ordem"}))` to `Facto.java` as a DB-level backstop to the existing single-JVM `synchronized` block. Wrapped the `synchronized` block in `createFacto` with a try/catch for `org.springframework.dao.DataIntegrityViolationException`, returning `409 Conflict` with a Portuguese message instead of letting it fall through to the 500 catch-all. Did not implement the more invasive `SELECT ... FOR UPDATE` alternative, per explicit scope instruction.

## Skipped Issues

None — all 4 in-scope findings were fixed.

---

_Fixed: 2026-07-07T20:22:52Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
