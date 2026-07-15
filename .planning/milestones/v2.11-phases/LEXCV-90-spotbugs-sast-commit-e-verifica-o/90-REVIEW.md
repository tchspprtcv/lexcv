---
phase: LEXCV-90-spotbugs-sast-commit-e-verifica-o
reviewed: 2026-07-13T19:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/pom.xml
  - backend/spotbugs-exclude.xml
  - backend/src/main/java/com/lexcv/config/UserPrincipal.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
  - backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
  - backend/src/main/java/com/lexcv/models/Cliente.java
  - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
  - web/src/hooks/use-clientes.ts
  - web/src/app/(dashboard)/clientes/merge/page.tsx
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase LEXCV-90: Code Review Report

**Reviewed:** 2026-07-13T19:00:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

This is the iteration-3 re-review of this phase, over the iteration-2 fix pass (commits `69965f8`..`126079e`, see `90-REVIEW-FIX.md`). Scope grew from 7 to 10 files this round (`Cliente.java` and `ClienteRepository.java` were touched by the WR-03 fix; `use-clientes.ts` and `clientes/merge/page.tsx` were touched by the CR-01 fix). I re-read every in-scope file end to end — including all ~3,220 lines of `ResourceController.java` — and independently re-verified each of the four iteration-2 findings at its cited line range rather than trusting the fix report:

- **CR-01** (`mergeClientes` discarding `ContaCorrente` balance / orphaning `Documento`/`ClienteAdvogado`/`ClienteAdministrativo`) — confirmed fixed: the secondary's `saldo` is now added onto the primary's `ContaCorrente` before the secondary row is deleted (lines 852-866), `Documento` rows are re-pointed at the primary (lines 868-870), and `ClienteAdvogado`/`ClienteAdministrativo` links are migrated with de-duplication against the primary's existing links (lines 872-887). The response map and the frontend hook/page were updated accordingly and confirmed consistent (`moved_documentos`, `merged_saldo` both present end-to-end in `use-clientes.ts` and `merge/page.tsx`).
- **WR-01** (`createProcesso`/`createProcessoIntake`/`createParte` missing presence checks) — confirmed fixed at all three call sites (lines 1042-1044, 1215-1217, 1759-1761); cross-checked against `Processo.java`/`Parte.java` and found no other `nullable = false` column left unvalidated.
- **WR-02** (`updateProcesso` nulling `clienteId` and bypassing the CR-03 tenant check when omitted) — confirmed fixed: the null check (lines 1167-1169) now runs *before* the tenant-ownership lookup, so the tenant check can no longer be skipped by omitting the field, and no regression was found in this pass.
- **WR-03** (`createCliente`'s `DataIntegrityViolationException` catch mislabeling unrelated constraint violations) — confirmed fixed for the `createCliente` path: `Cliente.nome` now carries `@NotBlank` (`Cliente.java:35`), and an explicit `documentoNumero` uniqueness check (lines 241-245) runs before the `synchronized` block, using the new `ClienteRepository.findByTenantIdAndDocumentoNumero` (confirmed present). However, this pass found the fix was applied asymmetrically — see WR-01 below, a new finding.

Beyond re-verifying iteration 2, this pass surfaced **three new warnings**, all instances of the same "fix applied to one path, sibling path left open" pattern that iteration 2 itself found in iteration 1's work: `updateCliente` still lacks the `documentoNumero` uniqueness check that was added only to `createCliente`; `mergeClientes`'s otherwise-thorough data migration still misses one FK class (`ParecerSolicitacao.clienteId`, a `nullable = false` reference resolved by `ParecerController`), leaving it orphaned exactly like the `Documento`/link cases CR-01 just fixed; and `updateHonorario` has no validation around its `valorTotal` parsing, unlike its sibling `dataAcordo` handling two lines below it. None of these are regressions of the iteration-2 fixes themselves — each is a previously-unreviewed adjacent code path. The five carried-forward Info items from the iteration-2 review (out of `fix_scope: critical_warning`) remain accurate and unaddressed; one new Info item is added for a minor transparency gap in the CR-01 fix's response payload.

## Warnings

### WR-01: `updateCliente` has no `documentoNumero` uniqueness check, unlike `createCliente` — an uncaught `DataIntegrityViolationException` surfaces as a generic 500 with a raw exception message

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:291-336`
**Issue:** The iteration-2 WR-03 fix added an explicit `documentoNumero` uniqueness check to `createCliente` (lines 241-245) specifically so that the `(tenant_id, documento_numero)` unique-constraint violation would be caught as a clean `409` instead of misreported. `updateCliente` has the identical constraint exposure — line 318 does `cliente.setDocumentoNumero(payload.getDocumentoNumero());` unconditionally (whenever the value changed) — but has **no** equivalent check and **no** `try/catch` around `clienteRepository.save(cliente)` at line 334 at all. A `PUT /clientes/{id}` request that sets `documentoNumero` to a value already used by another client in the same tenant hits the DB unique constraint at `save()`, throws an uncaught `DataIntegrityViolationException`, and falls through to `GlobalExceptionHandler`'s catch-all `Exception` handler (`GlobalExceptionHandler.java:42-49`), which returns `500` with `body.put("message", ex.getMessage())` — leaking the raw JDBC/Hibernate exception text (constraint name, sometimes table/column names) to the client instead of a clean `409` with a Portuguese-language message consistent with the rest of the controller.
**Fix:**
```java
// After the documentoTipoUnchanged / isDocumentoTipoValidoParaTipo check, before any setters:
boolean documentoNumeroChanged = !java.util.Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero());
if (documentoNumeroChanged && payload.getDocumentoNumero() != null
        && clienteRepository.findByTenantIdAndDocumentoNumero(getTenantId(), payload.getDocumentoNumero()).isPresent()) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Já existe um cliente com este número de documento"));
}
```

### WR-02: `mergeClientes` still doesn't migrate `ParecerSolicitacao.clienteId` — the same orphaned-FK defect class CR-01 fixed for `Documento`/`ClienteAdvogado`/`ClienteAdministrativo`, left open for a different table

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:866-889` (cross-referenced against `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java:24` and `backend/src/main/java/com/lexcv/controllers/ParecerController.java:80-121`, both out of this phase's file scope but load-bearing for this finding)
**Issue:** `ParecerSolicitacao.clienteId` is declared `@Column(name = "cliente_id", nullable = false)` and is validated at create/update time by `ParecerController.clienteBelongsToTenant`, i.e. it is a real, enforced FK-like reference to `Cliente` from the "Parecer Jurídico" module. `mergeClientes` migrates `Processo` (836-840), `ClienteContacto` (842-845), `ClienteNota` (847-850), `ContaCorrente` (852-866), `Documento` (868-870), and `ClienteAdvogado`/`ClienteAdministrativo` (872-887) off of `secondaryId` before deleting the secondary `Cliente` row at line 889 — but no equivalent step exists for `ParecerSolicitacao`. Any parecer request still referencing `secondaryId` becomes an orphaned row pointing at a `cliente_id` that no longer exists in `t_cliente` once the merge completes; the parecer becomes permanently disconnected from any client's history (it won't show up under the surviving primary client, and any code path that resolves `clienteId` back to a `Cliente` — e.g., for display — will silently get nothing). This is exactly the defect class CR-01 fixed in the iteration-2 pass, just for a table that CR-01's fix didn't enumerate.
**Fix:** Add a migration loop analogous to the `Documento` one, requiring `ParecerSolicitacaoRepository` (already exists for `ParecerController`) as a dependency of `ResourceController`, plus a tenant-scoped finder:
```java
List<ParecerSolicitacao> pareceresToMove =
        parecerSolicitacaoRepository.findByTenantIdAndClienteId(tenantId, payload.secondaryId());
pareceresToMove.forEach(ps -> ps.setClienteId(savedPrimary.getId()));
parecerSolicitacaoRepository.saveAll(pareceresToMove);
```
(`findByTenantIdAndClienteId` doesn't exist yet on `ParecerSolicitacaoRepository` — add it alongside whatever finder the repository already exposes.) Include `moved_pareceres` in the response map for the same operator-visibility reason CR-01 added `moved_documentos`.

### WR-03: `updateHonorario` doesn't validate `valorTotal` before parsing — a malformed value throws an uncaught `NumberFormatException`, surfaced as a generic 500

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2965-2967`
**Issue:**
```java
if (body.containsKey("valorTotal")) {
    hon.setValorTotal(new BigDecimal(body.get("valorTotal").toString()));
}
```
Unlike the adjacent `dataAcordo` handling four lines below (2971-2976), which wraps its parse in `try { ... } catch (Exception e) { return ResponseEntity.status(HttpStatus.BAD_REQUEST)...}`, this line has no error handling at all. A request body with a non-numeric `valorTotal` (e.g. `"abc"`, or a locale-formatted number like `"1.234,56"`) throws `NumberFormatException` from `new BigDecimal(String)`, which is not caught anywhere in this method and propagates to `GlobalExceptionHandler`'s catch-all, returning a generic `500` instead of a `400` with an actionable message. There is also no check that the parsed value is non-negative.
**Fix:**
```java
if (body.containsKey("valorTotal")) {
    try {
        hon.setValorTotal(new BigDecimal(body.get("valorTotal").toString()));
    } catch (NumberFormatException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "valorTotal inválido"));
    }
}
```

## Info

### IN-01: `UserPrincipal.getRoles()`/`getPermissions()` expose the live mutable `Set`

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:19-25, 64-67`
**Issue:** Carried forward from the prior review, still unfixed (out of iteration-2 fix scope). `@Getter` generates plain accessors for `roles`/`permissions` (both mutable `Set`s built in `create()`), returning direct references — inconsistent with `getAuthorities()`, which is explicitly overridden to return an unmodifiable view.
**Fix:** Wrap the fields in `Collections.unmodifiableSet(...)` at construction time in `create()`, or override the getters the same way `getAuthorities()` is overridden.

### IN-02: Hardcoded ADMIN permission list duplicates `DatabaseSeeder.seedRbac()`

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:34-47`
**Issue:** Carried forward from the prior review, still unfixed. The inline comment itself flags the risk: "Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list" — a manually-maintained duplicate that can silently drift. Verified this pass: the two lists are still in sync (`DatabaseSeeder.java:294-303`), but the drift risk itself is what's being flagged, not a current mismatch.
**Fix:** Derive this list from a single shared constant referenced by both `DatabaseSeeder` and `UserPrincipal`.

### IN-03: Misleading no-op `break` in `listEventos`'s recurrence-expansion loop

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2439-2448`
**Issue:** Carried forward from the prior review, still unfixed.
```java
switch (master.getRecurrenceRule()) {
    case "DAILY" -> cursor = cursor.plusDays(1);
    case "WEEKLY" -> cursor = cursor.plusWeeks(1);
    case "MONTHLY" -> cursor = cursor.plusMonths(1);
    default -> { break; }
}
if (!master.getRecurrenceRule().equals("DAILY") && ...) break;
```
The `break;` inside the arrow-switch `default` has no effect on the enclosing `while` loop; the loop is actually terminated by the following `if`. Not incorrect, but misleading to future readers.
**Fix:** Remove the no-op `default -> { break; }`, or restructure with an explicit boolean so the termination condition is a single, unambiguous check.

### IN-04: Magic number `3600` (presigned URL TTL) duplicated

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:420` (downloadProcuracao), `:2795` (downloadDocumento)
**Issue:** Carried forward from the prior review, still unfixed. `Map.of("url", url, "expiresIn", 3600)` repeats the literal in two places, with no visible link to whatever TTL `storageService.presignedDownloadUrl(...)` actually configured.
**Fix:** Extract a shared `private static final long PRESIGNED_URL_EXPIRES_IN_SECONDS = 3600;` (or source it from `storageService`) and reuse it at both call sites.

### IN-05: SpotBugs/OWASP dependency-check are configured but not enforced by CI

**File:** `backend/pom.xml:146-170`
**Issue:** Carried forward from the prior review, still unfixed. `spotbugs-maven-plugin` and `dependency-check-maven` have no `<executions>` binding them to a lifecycle phase. Verified this pass: `.github/workflows/deploy.yml` still only runs `docker/build-push-action` for both images — no `mvn test`, `mvn spotbugs:check`, or `mvn dependency-check:check` step exists anywhere in the pipeline gating `build-and-push`.
**Fix:** Add a `mvn -B verify` (or explicit `spotbugs:check`/`dependency-check:check`/`test` goals) step to `deploy.yml` gating the `build-and-push` job.

### IN-06: `mergeClientes`'s response doesn't report how many `ClienteAdvogado`/`ClienteAdministrativo` links were migrated or dropped as duplicates

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:872-898`; `web/src/hooks/use-clientes.ts:129-139`; `web/src/app/(dashboard)/clientes/merge/page.tsx:52-58`
**Issue:** The CR-01 fix's stated goal was "so the operator can see what actually happened to the balance and documents instead of only `moved_processos`/`moved_contactos`/`moved_notas`" — and it delivers that for `ContaCorrente` (`merged_saldo`) and `Documento` (`moved_documentos`). The staff-assignment migration added in the same fix (lines 872-887) has no corresponding counter in the response map (891-898), so an operator merging two clients who each had different lawyers/administrative staff assigned has no visibility into whether those assignments were carried over, silently dropped as duplicates, or how many of each happened — the same visibility gap CR-01 explicitly closed for the other two data classes.
**Fix:** Track counts while building the two loops (e.g. `movedAdvogados`/`droppedDuplicateAdvogados`) and add them to the response map; surface in the frontend toast alongside `moved_documentos`.

---

_Reviewed: 2026-07-13T19:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
