---
phase: LEXCV-90-spotbugs-sast-commit-e-verifica-o
reviewed: 2026-07-13T14:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend/pom.xml
  - backend/spotbugs-exclude.xml
  - backend/src/main/java/com/lexcv/config/UserPrincipal.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
  - backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
findings:
  critical: 4
  warning: 5
  info: 5
  total: 14
status: issues_found
---

# Phase LEXCV-90: Code Review Report

**Reviewed:** 2026-07-13T14:00:00Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Reviewed the SpotBugs/FindSecBugs SAST wiring (`pom.xml`, `spotbugs-exclude.xml`) and the entity-mass-assignment remediation it documents across `ResourceController.java`, plus the small supporting files (`UserPrincipal`, `ConflictCheckResponse`, `WorkflowResponse`).

The `setId(null)` mass-assignment mitigations described in `spotbugs-exclude.xml` are present and functionally correct everywhere they're claimed. However, the review surfaced several defects that are independent of the SpotBugs triage: a missing `@Transactional` boundary on the multi-entity cliente merge, a genuine TOCTOU race in client-number generation (contrasted with the correct pattern used a few hundred lines later for `Facto.ordem`), a multi-tenant boundary gap on `Processo.clienteId` that the project's own architecture rules (CLAUDE.md) call out as the primary data-isolation mechanism, and a silent-failure path in payment/balance reconciliation that can leave a persisted payment with no corresponding ledger update. `spotbugs-exclude.xml` itself also contains one factual inaccuracy in its own audit trail (two methods placed in the wrong justification group), which undermines the file's stated goal of being individually verified.

## Critical Issues

### CR-01: `mergeClientes` performs a multi-entity merge without a transactional boundary

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:770-838`
**Issue:** `mergeClientes` performs seven sequential writes (`clienteRepository.save(primary)`, `processoRepository.saveAll(...)`, `clienteContactoRepository.saveAll(...)`, `clienteNotaRepository.saveAll(...)`, a conditional `contaCorrenteRepository.delete(...)`, and finally `clienteRepository.delete(secondary)`) with no `@Transactional` annotation on the method. Every other multi-write mutation in this controller that must be atomic is explicitly annotated (`atribuirResponsavel`, `registarDecisaoConflito`, `formalizarProcesso`, `executarTransicao`, `createPrazo`, `togglePrazoConcluido`, `createDecisao`, `deleteDecisao`) — this is the one multi-step write path that isn't, despite being the highest-blast-radius operation in the file (it reassigns ownership of processes, contacts, and notes, then deletes a client row). If any `saveAll`/`delete` call in the middle fails (constraint violation, connection blip), the merge is left half-done: e.g. processes already repointed to `primary` while `secondary` is still not deleted, or contacts moved but notes not moved, with no rollback.
**Fix:**
```java
@PreAuthorize("hasAuthority('clientes:edit')")
@Transactional
@PostMapping("/clientes/merge")
public ResponseEntity<?> mergeClientes(@RequestBody ClienteMergeRequest payload) {
    ...
}
```

### CR-02: TOCTOU race in `createCliente`'s `numeroSequencial`/`numeroCliente` assignment can mint duplicate client numbers

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:245-252`
**Issue:**
```java
synchronized (ClienteRepository.class) {
    java.util.Optional<Integer> result = clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId());
    int maxSeq = result.orElse(0);
    int nextSeq = maxSeq + 1;
    cliente.setNumeroSequencial(nextSeq);
    cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq));
}
Cliente saved = clienteRepository.save(cliente);
```
The `save()` call is **outside** the synchronized block. Two concurrent requests (same tenant) can both enter the block sequentially, both read the same `maxSeq` (since neither has persisted yet), both compute the same `nextSeq`, both release the lock, and then both call `save()` with the identical `numeroSequencial`/`numeroCliente`. There is no unique constraint on `t_cliente` for either column — `Cliente.java`'s only `@UniqueConstraint` is `(tenant_id, documento_numero)` — so both inserts succeed silently, producing two clients with the same `CLI-00NN` number. Contrast with `createFacto` a few hundred lines later (`ResourceController.java:2079-2088`), which holds the lock through the `save()` call itself *and* additionally catches `DataIntegrityViolationException` as defense-in-depth against cross-instance races — the correct pattern is right there in the same file.
**Fix:**
```java
Cliente saved;
synchronized (ClienteRepository.class) {
    int nextSeq = clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId()).orElse(0) + 1;
    cliente.setNumeroSequencial(nextSeq);
    cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq));
    saved = clienteRepository.save(cliente);
}
```
Also add a DB-level `@UniqueConstraint(columnNames = {"tenant_id", "numero_sequencial"})` on `Cliente` so multi-instance deployments (where the in-JVM `synchronized` provides no protection at all) fail loudly instead of silently duplicating.

### CR-03: `Processo.clienteId` is never validated against the caller's tenant on create/update

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:976-994` (createProcesso), `:1132-1145` (createProcessoIntake), `:1091-1112` (updateProcesso, specifically line 1097)
**Issue:** CLAUDE.md states plainly: "Every domain entity carries a `tenant_id`. Controllers... **must** scope all reads/writes by it... this is the primary data-isolation boundary." `createProcesso` validates `responsavelId` against the tenant (lines 981-987) but never validates `processo.getClienteId()`. `createProcessoIntake` validates neither `clienteId` nor `responsavelId` at all. `updateProcesso` copies `payload.getClienteId()` straight onto the tenant-verified, fetched `processo` entity with zero validation:
```java
processo.setClienteId(payload.getClienteId());
```
Any user holding `processos:manage`/`processos:create`/`processos:edit` can therefore link a `Processo` in their own tenant to a `Cliente` UUID belonging to a **different** tenant (a small closed set of law-firm tenants, but UUIDs do leak through URLs, logs, and support tickets). This is exactly the class of cross-tenant FK the merge/mass-assignment work in this phase was supposedly hardening against, just on a different field. It is also directly exploitable downstream: `runConflictCheck` (line 1165) does
```java
Cliente clienteDoProcesso = processo.getClienteId() != null
        ? clienteRepository.findById(processo.getClienteId()).orElse(null)
        : null;
```
with no tenant check on the fetched `Cliente`, trusting that `processo.getClienteId()` already belongs to this tenant — a trust that CR-03 shows is unfounded.
**Fix:** Mirror the `responsavelId` check already present for `createProcesso`:
```java
if (processo.getClienteId() != null) {
    Cliente cliente = clienteRepository.findById(processo.getClienteId()).orElse(null);
    if (cliente == null || !tenantId.equals(cliente.getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "clienteId não pertence a este tenant"));
    }
}
```
Apply the same guard in `createProcessoIntake` (also add the `responsavelId` check there) and in `updateProcesso` before line 1097.

### CR-04: `createPagamento` can silently persist a payment that never updates the conta corrente balance

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2784-2814`
**Issue:** `Pagamento.valorPago` (`backend/.../models/Pagamento.java:23-24`) has no `nullable = false` and no bean-validation annotation, and `createPagamento` doesn't use `@Valid` or check it. A request omitting `valorPago` (or with `valorPago: null`) is persisted successfully by `pagamentoRepository.save(pag)` (line 2796). The very next block then does:
```java
try {
    ...
    cc.setSaldo(cc.getSaldo().add(pag.getValorPago()));
    contaCorrenteRepository.save(cc);
} catch (Exception ex) {
    log.warn("PAGAMENTO_CREATE: falha ao atualizar saldo da conta corrente, pagamento={}", saved.getId(), ex);
}
return ResponseEntity.status(HttpStatus.CREATED).body(saved);
```
`BigDecimal.add(null)` throws `NullPointerException`, which is swallowed by the broad `catch (Exception ex)` and merely logged as a warning. The endpoint still returns `201 Created` with the saved (nonsensical, amount-less) payment, and the client's account balance is never adjusted — a financial record now exists that is invisible to the reconciliation the whole `ContaCorrente` mechanism exists for, and the caller has no way to know it failed. The same null also blows up `calculateMensalReceived` (line 2969, `total.add(pag.getValorPago())`) uncaught, which will 500 the `/dashboard` KPI endpoint for the rest of that tenant's month.
**Fix:** Validate before persisting, not just before using the value:
```java
if (pag.getValorPago() == null || pag.getValorPago().compareTo(BigDecimal.ZERO) <= 0) {
    return ResponseEntity.badRequest().body(Map.of("message", "valorPago é obrigatório e deve ser positivo"));
}
```
placed before `pag.setId(null)` at line 2795. Consider also tightening the column to `@Column(name = "valor_pago", nullable = false)` on the entity.

## Warnings

### WR-01: `MultipartFile` `InputStream`s are never closed

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:366` (uploadProcuracao), `:1842` (createDecisao), `:2560` and `:2574` (uploadDocumento)
**Issue:** Every upload path does `InputStream inputStream = file.getInputStream();` and hands it to `storageService.upload(...)` without a `try-with-resources` or explicit `close()`. Depending on the multipart resolver's threshold, large uploads are backed by a temp-file-based `FileInputStream`, so the underlying file descriptor can remain open until GC finalizes it, not until the request completes.
**Fix:**
```java
try (InputStream inputStream = file.getInputStream()) {
    String newKey = storageService.upload(getTenantId(), syntheticId, originalName, inputStream, file.getContentType(), file.getSize());
    ...
}
```

### WR-02: Old storage object is deleted before the DB write confirming the new reference is committed

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:371-376` (uploadProcuracao), `:2557-2568` (uploadDocumento replace path)
**Issue:** Both "replace" flows upload the new object, then delete the old object, and only afterward update/save the entity that references the new key:
```java
storageService.delete(oldKey);
cliente.setProcuracaoKey(newKey);
clienteRepository.save(cliente); // if this throws, DB still points at oldKey, which is now gone
```
The in-line comment ("old remains intact if upload fails") only covers the upload-failure case; it doesn't cover the case where the *subsequent DB save* fails after the old object has already been deleted. In that window the persisted entity still references the just-deleted `oldKey`, so the next download attempt (`downloadProcuracao`/`downloadDocumento`) will fail against the storage backend even though the DB row looks fine.
**Fix:** Reorder to save the DB reference first (inside a transaction) and only delete the old storage object after the DB commit succeeds, or wrap the whole sequence in a compensating action / outbox pattern if eventual consistency is acceptable.

### WR-03: `createHonorario`/`createPagamento` surface a generic 500 instead of 400 for a missing FK in the body

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2759-2767` (createHonorario), `:2785-2793` (createPagamento)
**Issue:** `processoRepository.findById(hon.getProcessoId())` / `honorarioRepository.findById(pag.getHonorarioId())` are called directly on a body-supplied field that has no `@NotNull`/`@Valid` enforcement. Spring Data's `findById` does `Assert.notNull(id, ...)`, throwing `IllegalArgumentException` when the field is omitted from the JSON payload. That's caught only by `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)`, producing a `500 INTERNAL_SERVER_ERROR` for what is really a client input error.
**Fix:**
```java
if (hon.getProcessoId() == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "processoId é obrigatório"));
}
```
(and the equivalent for `pag.getHonorarioId()`).

### WR-04: `spotbugs-exclude.xml`'s own justification for `createClienteContacto`/`createClienteNota` doesn't match their actual code

**File:** `backend/spotbugs-exclude.xml:14-31`, cross-referenced against `backend/src/main/java/com/lexcv/controllers/ResourceController.java:606-626` (createClienteContacto) and `:696-716` (createClienteNota)
**Issue:** The file's header states each suppressed entry "was individually read and verified against the actual data flow." `createClienteContacto` and `createClienteNota` are listed under **Group 1** ("already safe, no code change needed"), described as either (a) copying an allowlist onto a *fetched* entity, or (b) building a *brand-new* entity and copying an allowlist onto it, discarding the payload. Neither description matches what these two methods do — they mutate the **bound `payload` object itself** and persist it directly:
```java
payload.setId(null);
payload.setTenantId(getTenantId());
payload.setClienteId(id);
payload.setValor(payload.getValor().trim());
...
ClienteContacto saved = clienteContactoRepository.save(payload);
```
This is actually the **Group 2** pattern (bound entity persisted directly, safety comes from the explicit `setId(null)` call), just missing the "ENTITY_MASS_ASSIGNMENT (SpotBugs triage)" inline comment that all the real Group 2 methods carry. The suppression is not unsafe (the `setId(null)` call does prevent the merge()-based overwrite), but the written rationale is factually wrong about *why* it's safe, which defeats the purpose of an audit trail that explicitly asks future readers to trust it without re-deriving the analysis.
**Fix:** Move `createClienteContacto`/`createClienteNota` into the Group 2 match block (or rewrite the Group 1 description to also cover "mutates and persists the bound payload directly, protected only by `setId(null)`"), and add the same `// ENTITY_MASS_ASSIGNMENT (SpotBugs triage): see createCliente's setId(null) comment.` inline comment at their `setId(null)` call sites for consistency with every other Group 2 method.

### WR-05: Broad `catch (Exception ex)` around conta-corrente balance updates hides real bugs as recoverable warnings

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2798-2811` (createPagamento), `:2891-2900` (deletePagamento)
**Issue:** Both balance-adjustment blocks catch `Exception` broadly and merely `log.warn`, treating storage-outage-style transient failures the same as programming errors (see CR-04's NPE). This means genuine bugs (null arithmetic operands, `ClassCastException`, etc.) are indistinguishable in logs/monitoring from an expected/tolerable failure mode, and there is no compensating mechanism (retry queue, reconciliation job, alert) to catch the resulting balance drift.
**Fix:** Narrow the catch to the specific exception types that are actually expected to be recoverable (e.g. optimistic-locking failures), and validate the arithmetic inputs before entering the block so a `NullPointerException` can't occur in the first place (see CR-04).

## Info

### IN-01: `UserPrincipal.getRoles()`/`getPermissions()` expose the live mutable `Set`

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:19-25, 64-67`
**Issue:** `@Getter` generates plain accessors for `roles` and `permissions` (both backed by mutable `Set` implementations built in `create()`), returning direct references to the internal collections. This is inconsistent with `getAuthorities()`, which is explicitly overridden to return `Collections.unmodifiableCollection(authorities)`. Any caller holding a `UserPrincipal` could mutate its `roles`/`permissions` in place.
**Fix:** Either wrap the fields in `Collections.unmodifiableSet(...)` at construction time in `create()`, or override `getRoles()`/`getPermissions()` the same way `getAuthorities()` is overridden.

### IN-02: Hardcoded ADMIN permission list duplicates `DatabaseSeeder.seedRbac()`

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:34-47`
**Issue:** The comment itself flags this: "Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list." A manually-maintained duplicate list is a standing maintenance risk — if a new `scope:action` permission is added to the seeder and this list isn't updated in the same change, ADMIN's fallback grant here silently drifts out of sync with the source of truth.
**Fix:** Consider deriving this list from a single shared constant (e.g. a `Permissions` enum/constants class referenced by both `DatabaseSeeder` and `UserPrincipal`) instead of maintaining two hand-written lists.

### IN-03: Misleading no-op `break` in `listEventos`'s recurrence-expansion loop

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2340-2349`
**Issue:**
```java
switch (master.getRecurrenceRule()) {
    case "DAILY" -> cursor = cursor.plusDays(1);
    case "WEEKLY" -> cursor = cursor.plusWeeks(1);
    case "MONTHLY" -> cursor = cursor.plusMonths(1);
    default -> { break; }
}
// Safety: if rule unknown, avoid infinite loop
if (!master.getRecurrenceRule().equals("DAILY") && ...) break;
```
The `break;` inside the arrow-switch `default` case has no effect on the enclosing `while` loop (it exits the switch block, which arrow-form switch cases don't need anyway). The loop is actually terminated by the following `if` statement. The code isn't wrong, but the `default -> { break; }` reads as if it's the safety mechanism when it's actually inert, which will confuse the next person who touches this method.
**Fix:** Remove the misleading `default -> { break; }` and rely solely on the explicit `if` check, or restructure with a boolean flag (`boolean advanced = switch (...) {...}`) so the termination condition is a single, unambiguous check.

### IN-04: Magic number `3600` (presigned URL TTL) duplicated

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:400` (downloadProcuracao), `:2685` (downloadDocumento)
**Issue:** `Map.of("url", url, "expiresIn", 3600)` repeats the literal `3600` in two places with no shared constant, and no visible connection to whatever TTL `storageService.presignedDownloadUrl(...)` actually configured on the presigned URL itself — if the storage service's TTL ever changes, this reported value would silently drift out of sync with reality.
**Fix:** Extract a `private static final long PRESIGNED_URL_EXPIRES_IN_SECONDS = 3600;` (or source it from `storageService`) and reuse it at both call sites.

### IN-05: SpotBugs/OWASP dependency-check are configured but not enforced by CI

**File:** `backend/pom.xml:146-170`
**Issue:** `spotbugs-maven-plugin` and `dependency-check-maven` are declared with no `<executions>` binding them to a lifecycle phase, matching the documented manual-invocation workflow (`mvn spotbugs:check` per CLAUDE.md). However, the repository's only CI/CD workflow (`.github/workflows/deploy.yml`) builds and pushes both Docker images to the production registry on every push to `master` without running `mvn test`, `mvn spotbugs:check`, or `mvn dependency-check:check` at any point. As configured, this phase's SAST/SCA tooling cannot actually prevent a regression from reaching production — it only runs when a developer remembers to invoke it locally.
**Fix:** Add a `mvn -B verify` (or explicit `spotbugs:check`/`dependency-check:check`/`test` goals) step to `deploy.yml` gating the `build-and-push` job, so a SpotBugs/FindSecBugs or CVSS≥7 finding fails the build before an image is pushed.

---

_Reviewed: 2026-07-13T14:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
