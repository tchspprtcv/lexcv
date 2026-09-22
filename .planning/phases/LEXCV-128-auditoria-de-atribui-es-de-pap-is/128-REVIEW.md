---
phase: LEXCV-128-auditoria-de-atribui-es-de-pap-is
reviewed: 2026-09-22T00:00:00Z
depth: deep
files_reviewed: 21
files_reviewed_list:
  - backend/migrations/128-add-audit-log-detalhe.sql
  - backend/migrations/README.md
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java
  - backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java
  - backend/src/main/java/com/lexcv/controllers/RecusaTransacional.java
  - backend/src/main/java/com/lexcv/dtos/AuditoriaRbacEntradaDto.java
  - backend/src/main/java/com/lexcv/models/AuditLog.java
  - backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java
  - backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
  - backend/src/main/java/com/lexcv/services/AuditoriaRbacService.java
  - backend/src/main/java/com/lexcv/services/SetupService.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerTransacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerAuditoriaTest.java
  - backend/src/test/java/com/lexcv/controllers/RecusaTransacionalTest.java
  - backend/src/test/java/com/lexcv/repositories/AuditLogImutabilidadeTest.java
  - web/package.json
  - web/scripts/verify-auditoria-rbac.mjs
  - web/src/app/(dashboard)/settings/auditoria-tab.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/hooks/use-admin.ts
  - web/src/lib/auditoria-rbac.ts
  - web/src/types/auditoria-rbac.ts
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase LEXCV-128: Code Review Report

**Reviewed:** 2026-09-22
**Depth:** deep
**Files Reviewed:** 21 (backend main + repos + tests; frontend lib/hooks/components; migration + README)
**Status:** issues_found

## Summary

This phase adds an audit trail for office-role changes by reusing `t_audit_log`, narrowing
`AuditLogRepository` to `Repository<AuditLog, Long>`, marking `AuditLog` `@Immutable`, and
routing every non-2xx return of the seven RBAC write handlers through
`RecusaTransacional.recusar` so a refused request never commits a partial mutation. This is an
unusually well-defended piece of code: the refusal-rollback mechanism, the `MANDATORY`
propagation on the audit writer, and the last-administrator pessimistic lock are all backed by
tests that exercise a **real** `TransactionInterceptor`/`AnnotationTransactionAttributeSource`
proxy (not a mock that only checks "was called"), which is exactly the kind of test needed to
catch a handler silently losing its `@Transactional` annotation. I traced every one of the seven
write handlers (`OfficeRolesController.createRole/renameRole/deleteRole`,
`AdminController.createUser/updateUser/deleteUser/updateRbac`) for a path that returns non-2xx or
throws without going through `recusar`, and did not find one that also lacks test coverage. The
last-administrator lock (`bloquearParaAlteracaoDeDetentores` + `countByTenantRolesIdAndAtivoTrueAndIdNot`)
correctly locks a single fixed row, cannot deadlock (never two locks in one request), and is
keyed by `moldeId` (not name), so a rename of the protected role does not defeat it. The privacy
rule (never write anything but a display name to `detalhe`) holds on every write path I traced,
including `SetupService.provisionTenant`'s cross-tenant-safe `autor = null`. The native
`buscarEventosRbac` query is parameterized (no injection), and is always AND-ed with the
principal's own `tenant_id`, so the `papelId`/`utilizadorAlvoId` filters cannot be used to probe
another tenant's ids. The frontend renders event text as React text children (no
`dangerouslySetInnerHTML`), never mutates via the auditoria hook, and has an explicit fallback for
any unrecognized `acao`.

Two real defects were found, both in the `DataIntegrityViolationException` handling that Plan 03
added around the new audit writes in `OfficeRolesController`, plus two minor items. Full findings
below. `mvn -o compile` (main sources) and `pnpm tsc --noEmit` both pass cleanly for the reviewed
file set. `node scripts/verify-auditoria-rbac.mjs` passes 11/11.

**Note on scope:** the working tree also contains an uncommitted, in-progress modification to
`backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java` and two new test
files (`MigracaoPapeisEscritorioServiceAuditoriaTest.java`,
`SetupServiceProvisionTenantTransacaoTest.java`), which is the concurrent agent's work referenced
in the task brief. That change is not in the `git diff 7da0e0c9..HEAD` file list for this phase
and is excluded from this review as instructed. Note for the record: it currently leaves
`mvn test-compile` broken (a stale existing test, `MigracaoPapeisEscritorioServiceTest.java`, still
calls the old 6-arg constructor) — this is expected to resolve when that concurrent work finishes
and is not attributed to Phase 128's reviewed file set here.

## Warnings

### WR-01: `createRole`/`renameRole` conflate audit-write failures with "duplicate name" (409)

**File:** `backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java:177-206` (createRole), `:251-267` (renameRole)

**Issue:** Plan 03 put the new `auditoriaRbacService.registarPapelCriado(...)` /
`registarPapelRenomeado(...)` calls *inside* the same `try` block whose `catch
(DataIntegrityViolationException ex)` was written to catch the `(tenant_id, nome)` unique-constraint
race on `tenantRoleRepository.saveAndFlush(...)`:

```java
try {
    TenantRole papelGravado = tenantRoleRepository.saveAndFlush(novoPapel);
    auditoriaRbacService.registarPapelCriado(tenantId, principal, papelGravado); // <-- inside the try
    return ResponseEntity.status(HttpStatus.CREATED)...
} catch (DataIntegrityViolationException ex) {
    // always reports "Já existe um papel com este nome neste escritório." (409)
    return RecusaTransacional.recusar(...);
}
```

If `AuditoriaRbacService.gravar` (the `auditLogRepository.save(...)` inside `registarPapelCriado`/
`registarPapelRenomeado`) ever throws a `DataIntegrityViolationException` for a reason unrelated to
role-name uniqueness (e.g. a future NOT-NULL/length constraint on `t_audit_log`, or any other FK/
constraint issue on that insert), this catch block swallows it and reports "Já existe um papel com
este nome neste escritório." to the client — even on a legitimately unique name, and even though
the real cause was the audit write. The transaction is still correctly rolled back (via
`RecusaTransacional.recusar`, so no orphaned role is left without its audit event), but the
error message returned is factually wrong and would mislead an operator debugging the failure.

This is inconsistent with two patterns already established elsewhere in the very same phase/
codebase that show the fix was known and deliberately applied in other spots:
1. `OfficeRolesController.deleteRole` (same file, same phase) places its
   `auditoriaRbacService.registarPapelApagado(...)` call *after* the `try/catch`, specifically so
   the catch "Apanha SO esta excecao NESTE ponto (nunca um catch generico a volta do metodo
   inteiro...)" (see the doc-comment at `OfficeRolesController.java:342-364`).
2. `PlatformAdminController.createTenant`'s catch for the exact same kind of
   `DataIntegrityViolationException` race inspects `ex.getMostSpecificCause().getMessage()` before
   deciding it's the email-duplicate case, and re-throws otherwise (see
   `PlatformAdminController.java:99-132`, `isViolacaoDeEmailDuplicado`) — precisely to avoid
   mis-attributing an unrelated constraint violation (their own comment cites the historical
   precedent of `t_tenant_role` constraint failures being wrongly reported as email conflicts).

Confirmed untested: `OfficeRolesControllerAuditoriaTest` only exercises
`tenantRoleRepository.saveAndFlush(...)` throwing `DataIntegrityViolationException`
(`createRole_saveAndFlushLancaDataIntegrityViolation_...`, line ~193); no test makes
`registarPapelCriado`/`registarPapelRenomeado` throw `DataIntegrityViolationException` to exercise
this exact conflation.

**Fix:** Narrow the `try` to just the entity write (mirroring `deleteRole`), or apply the same
`getMostSpecificCause()` message inspection `PlatformAdminController` already uses:

```java
TenantRole papelGravado;
try {
    papelGravado = tenantRoleRepository.saveAndFlush(novoPapel);
} catch (DataIntegrityViolationException ex) {
    return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
            "Já existe um papel com este nome neste escritório.")));
}
auditoriaRbacService.registarPapelCriado(tenantId, principal, papelGravado);
return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("id", papelGravado.getId(), "nome", papelGravado.getNome()));
```

(Same restructuring for `renameRole`.)

### WR-02: `nomeAntigoSegmento`/`nomeNovoSegmento` fall back to the wrong copy for `papel_renomear`

**File:** `web/src/lib/auditoria-rbac.ts:40-46`

**Issue:** When `entry.nomeAntigo` or `entry.nomeNovo` is null (legacy event pre-dating the
`detalhe` column, or a malformed `detalhe`), both segments fall back to the constant
`PAPEL_REMOVIDO = "um papel removido"`:

```js
function nomeAntigoSegmento(entry) {
  return { texto: entry.nomeAntigo ?? PAPEL_REMOVIDO, destaque: true };
}
function nomeNovoSegmento(entry) {
  return { texto: entry.nomeNovo ?? PAPEL_REMOVIDO, destaque: true };
}
```

For a `papel_renomear` event this produces "Maria Silva renomeou o papel um papel removido para
um papel removido." — which reads as if the role was deleted, when in fact the role still exists
and only its *former or new display name* failed to resolve. `PAPEL_REMOVIDO` is the correct
fallback for `papelSegmento` (used by `papel_criar`/`papel_apagar`/`papel_permissoes_alterar`/
`papel_atribuir`/`papel_retirar`, where a null `papelNome` really can mean the role itself was
later deleted), but reusing it here for a missing *name snapshot* on a rename event is a copy/paste
of the wrong fallback string. Confirmed untested: `auditoria-rbac.test.ts` only tests
`papel_renomear` with both names present (line 45).

**Fix:** Introduce a distinct fallback string (e.g. `"nome desconhecido"`) for
`nomeAntigoSegmento`/`nomeNovoSegmento`, or drop the two functions in favor of a small,
purpose-named constant so the rendered sentence doesn't imply deletion.

## Info

### IN-01: `AdminController` password-required-field check can NPE on explicit `null`

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:417` (`createUser`)

**Issue:** `createUser` checks `body.containsKey("password")` (true even for a JSON body with
`"password": null`) and then does `String password = (String) body.get("password"); if
(!password.matches(...))` unconditionally — `password.matches(...)` NPEs if the client sent an
explicit JSON `null` for `password`. This is pre-existing behavior (not modified by this phase —
Plan 04 only wrapped the existing `return ResponseEntity.badRequest()...` calls in
`RecusaTransacional.recusar(...)`), but it is now inside a newly-`@Transactional` method: the NPE
still correctly triggers a Spring rollback (unchecked exception, default rollback rules), but it
then surfaces to the client as an unstructured `500` via `GlobalExceptionHandler`'s catch-all
`Exception` handler, which also currently echoes `ex.getMessage()` in the response body (Java's
helpful-NPE message can name the local variable). Flagged as info because it predates this phase
and the transactional change does not make the underlying behavior worse (rollback is still
correct) — but it's now reachable through one of the seven newly-audited write paths and is worth
fixing opportunistically while this code is being touched.

**Fix:** `if (body.get("password") == null || !((String) body.get("password")).matches(...))`.

### IN-02: `GlobalExceptionHandler`'s catch-all echoes `ex.getMessage()` to the client

**File:** `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java:98-105`

**Issue:** Pre-existing (not introduced by this phase), but now more reachable: any unexpected
exception thrown from inside one of the seven newly-`@Transactional` RBAC handlers (e.g. a
`MANDATORY`-propagation failure if a future refactor drops `@Transactional` from one of them, or
the IN-01 NPE above) rolls back correctly but then returns `ex.getMessage()` verbatim in the JSON
body as `500`. For most exceptions in this phase this is an internal, non-sensitive string (e.g.
`AuditoriaRbacService`'s own `IllegalStateException("Falha ao serializar detalhe...")`), so no
new information disclosure was introduced by this phase specifically — but the pattern is a latent
risk for any future exception type whose message could carry sensitive detail (e.g. a raw SQL
exception via a different code path).

**Fix:** Out of scope to fix generically as part of this phase, but worth a follow-up: return a
generic message from the catch-all and log `ex.getMessage()` server-side only, mirroring the
existing `AccessDeniedException`/`HttpMessageNotReadableException` handlers immediately above it.

---

_Reviewed: 2026-09-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
