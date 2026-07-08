---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
reviewed: 2026-07-07T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/repositories/FactoRepository.java
findings:
  critical: 0
  warning: 4
  info: 0
  total: 4
status: issues_found
---

# Phase LEXCV-81: Code Review Report

**Reviewed:** 2026-07-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed the 12 new Decisão/Testemunha/Facto CRUD endpoints and the `juizo`/`origem` wiring added to `ResourceController.java`, plus `FactoRepository.java`.

**The highest-severity risk flagged for this review — a missing double ownership check (parent Processo tenant + child row's `processoId`) enabling IDOR on the six PUT/DELETE endpoints — is NOT present.** All six endpoints (`updateDecisao`, `deleteDecisao`, `updateTestemunha`, `deleteTestemunha`, `updateFacto`, `deleteFacto`) correctly:
1. Load the parent `Processo` and verify `processo.getTenantId().equals(getTenantId())` first, returning 404 on mismatch, before any child lookup.
2. Load the child row by its own ID and verify `child.getProcessoId().equals(id)` (the path variable), returning 404 on mismatch, before any mutation.
3. Never write to the DB before both checks pass.
4. Return a uniform 404 status (not a differentiated status/body) on either check's failure, so tenant/ownership existence is not leaked via status code.

The `createDecisao` multipart upload path also does **not** reintroduce the Phase 79 `/documentos/upload` IDOR gap: the `Documento.processoId`/`tenantId` are derived server-side from the already-tenant-validated path variable `id` and `getTenantId()`, never from client-supplied identifiers, and `clienteId` is hardcoded to `null`.

The `synchronized (FactoRepository.class)` block in `createFacto` does correctly serialize the read-max-then-write-`ordem` critical section — it is not synchronizing on a per-instance/wrong object, and the lock is released even on exception because it's a `synchronized` block. However, it only provides that guarantee within a single JVM (see WR-04).

Four Warnings were found relating to transactional integrity around the new file-upload-in-decisão flow, orphaned-document cleanup on delete, missing request-body validation causing unhandled 500s instead of clean 400s, and the scope limits of the `synchronized` lock in `createFacto`.

## Warnings

### WR-01: `createDecisao` is not transactional — a late failure orphans the just-created Documento (and its uploaded file)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1673-1746`
**Issue:** `createDecisao` performs, in sequence and outside any `@Transactional` boundary: (1) `storageService.upload(...)`, (2) `documentoRepository.save(documento)` (line 1734), (3) `decisaoRepository.save(decisao)` (line 1745). If step 3 throws for any reason (DB connectivity blip, constraint violation, etc.) after steps 1–2 have already committed, the newly-created `Documento` row and its uploaded file are left permanently orphaned — referenced by no `Decisao`, never cleaned up, and consuming storage indefinitely. Compare with `deleteProcuracao`/`uploadProcuracao` elsewhere in the file, which are careful about upload-before-delete ordering but this method has no equivalent safety net for the reverse failure mode (final entity save after a dependent row is already committed). Other single-entity upload endpoints in this file (`uploadDocumento`, `uploadProcuracao`) don't have this exposure because they only persist one entity.
**Fix:** Wrap the whole handler in `@Transactional` so the `Documento` save (a plain DB write) rolls back together with the `Decisao` save on failure:
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping(value = "/processos/{id}/decisoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> createDecisao(...) { ... }
```
Note `@Transactional` won't roll back the already-uploaded storage object (that's an external side effect), but it at least prevents the orphaned DB row, and the storage object becomes unreferenced-but-harmless rather than pointed to by a dangling `documento_id`. If full consistency is required, upload the file only after `decisaoRepository.save()` succeeds (build the `Decisao` first without `documentoId`, save it, then upload+create the `Documento`, then update `decisao.documentoId` in a follow-up save within the same transaction).

### WR-02: `deleteDecisao` does not clean up the linked `Documento` row or its storage object

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1770-1784`
**Issue:** `deleteDecisao` calls `decisaoRepository.delete(decisao)` only. If the `Decisao` was created via the multipart path (i.e. `decisao.getDocumentoId() != null`), the associated `Documento` row and its uploaded file in storage are never removed. This is a permanent leak: the `Documento` becomes unreachable from any UI flow tied to the deleted decisão (it isn't linked to a cliente, and it's not obviously discoverable via `/processos/{id}/documentos` unless that endpoint independently lists all documents by `processoId` regardless of decisão linkage), yet the file continues to occupy storage indefinitely.
**Fix:** When deleting a `Decisao` with a non-null `documentoId`, also delete the underlying `Documento` (and its storage object), scoped by tenant:
```java
if (decisao.getDocumentoId() != null) {
    documentoRepository.findById(decisao.getDocumentoId())
        .filter(d -> d.getTenantId().equals(getTenantId()))
        .ifPresent(d -> {
            try {
                storageService.delete(d.getCaminhoArquivo());
            } catch (StorageUnavailableException ignored) {
                // log and continue; avoid blocking decisão deletion on storage outage
            }
            documentoRepository.delete(d);
        });
}
decisaoRepository.delete(decisao);
```
(If the product intent is actually to preserve the document as an independent record after decisão deletion, that's a valid alternative design — but it should be a deliberate choice, not an unhandled gap, and should be documented as such.)

### WR-03: Missing request-body validation on new Decisão/Testemunha/Facto write endpoints allows required-field violations to surface as unhandled 500s

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1750-1768` (`updateDecisao`), `:1797-1804` (`createTestemunha`), `:1808-1828` (`updateTestemunha`), `:1857-1870` (`createFacto`), `:1873-1892` (`updateFacto`)
**Issue:** None of these five endpoints use `@Valid`/bean-validation, and none of them null-check the incoming payload fields before assigning them to entity properties that are `@Column(nullable = false)`:
- `updateFacto` (line 1887-1889) sets `facto.setDescricao(payload.getDescricao())` and `facto.setOrdem(payload.getOrdem())` unconditionally — both map to `nullable = false` columns on `Facto`. A payload omitting `descricao` or `ordem` (or explicitly sending `null`) passes straight through to `factoRepository.save(facto)`, which throws a `DataIntegrityViolationException`.
- `updateTestemunha` (line 1822) sets `testemunha.setNome(payload.getNome())` unconditionally against a `nullable = false` column; `createTestemunha` has the same exposure for a payload with no `nome`.
- `updateDecisao` (line 1763-1765) sets `data`/`tipo` unconditionally against `nullable = false` columns.
- Additionally, `updateDecisao`, `createTestemunha`, and `updateTestemunha` deserialize `tipo` directly from the JSON body into the `TipoDecisao`/`TipoTestemunha` enum via Jackson. An invalid enum string throws `HttpMessageNotReadableException` during argument resolution — this is inconsistent with `createDecisao`'s own multipart handler, which explicitly does `TipoDecisao.valueOf(tipo)` in a try/catch and returns a clean `400` (lines 1694-1700).

In every one of these cases, `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` (`handleAllExceptions`) is the only backstop: it returns HTTP `500` with `ex.getClass().getSimpleName()` and `ex.getMessage()` in the body — leaking internal exception/constraint details to the client for what is really a client input error, instead of a clean `400 Bad Request`.
**Fix:** Add explicit null/blank checks before assignment (mirroring the pattern already used for `ClienteContacto`/`ClienteNota` in this same file, e.g. lines 634-639), or annotate the request DTOs with `@NotNull`/`@NotBlank` and add `@Valid` to the controller parameter:
```java
if (payload.getDescricao() == null || payload.getDescricao().isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "descricao é obrigatória"));
}
if (payload.getOrdem() == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "ordem é obrigatória"));
}
facto.setDescricao(payload.getDescricao());
facto.setOrdem(payload.getOrdem());
```
For the enum fields, either switch to `@Valid` + `@NotNull` on the DTO, or accept `tipo` as a raw `String` and parse with the same `try { TipoDecisao.valueOf(...) } catch (IllegalArgumentException) { return 400 }` pattern already established in `createDecisao`.

### WR-04: `synchronized (FactoRepository.class)` in `createFacto` only guarantees atomicity within a single JVM instance, with no DB-level backstop

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1864-1868`, `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` (no unique constraint), `backend/src/main/java/com/lexcv/models/Facto.java` (no `@Table(uniqueConstraints=...)` on `(processo_id, ordem)`)
**Issue:** The `synchronized (FactoRepository.class)` block does correctly serialize the `findMaxOrdemByProcessoId` read + `save` write within a single JVM — it's synchronizing on the interface's `Class` object, which is a single shared instance per classloader, so it behaves like a global mutex for every thread handling `createFacto` in that process, and the lock is properly released on exception since it's a `synchronized` statement. That part of the mechanism is sound.

However, this provides **no protection at all** across multiple application instances (a common Spring Boot production topology behind a load balancer). Two `createFacto` requests for the *same* `processoId`, handled concurrently by two different JVM instances, can both read the same `MAX(ordem)` before either writes, and both compute/persist the same `nextOrdem` value — producing two `Facto` rows with a duplicate `ordem` for the same processo. There is no `UNIQUE(processo_id, ordem)` constraint (checked in `Facto.java` and confirmed no migration defines one) to catch this at the database level as a backstop.
**Fix:** Either (a) add a DB-level `UNIQUE (processo_id, ordem)` constraint and handle the resulting `DataIntegrityViolationException` with a retry-with-recompute or a `409 Conflict`, or (b) compute `ordem` via a DB-native mechanism (`SELECT ... FOR UPDATE` on the parent `Processo` row, or a sequence/window function) instead of an application-level `synchronized` block, so correctness doesn't depend on single-instance deployment. At minimum, document that this endpoint requires single-instance deployment (or sticky routing per tenant) until fixed, since the current safeguard silently stops working the moment the app is scaled horizontally.

---

_Reviewed: 2026-07-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
