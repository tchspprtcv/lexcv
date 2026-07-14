---
phase: LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin
reviewed: 2026-07-14T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
findings:
  critical: 1
  warning: 2
  info: 4
  total: 7
status: issues_found
---

# Phase LEXCV-94: Code Review Report

**Reviewed:** 2026-07-14T00:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed `NotificacaoService.java` after the NOTF-27 fix (`criarComFanOutAdmin`), which merges the
primary recipient(s) and the ADMIN fan-out into a single `LinkedHashSet<UUID>` before calling
`criar()`. That in-memory merge does correctly eliminate the *deterministic* collision this phase
set out to fix (a primary recipient who is also an ADMIN of the same tenant no longer produces two
`criar()` calls for the same `(tenant, destinatario, entidade_tipo, entidade_id, categoria)` tuple
within a single invocation) — traced through all four call sites
(`notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`,
`notificarParecerAtribuido`) and the Set-membership logic; this part is sound.

However, the `catch (DataIntegrityViolationException ex)` "backstop" added around the per-destinatario
`criar()` call (lines 145-154) is documented as guaranteeing that a duplicate-notification collision
"nunca deve propagar para fora deste método nem reverter a transação de negócio chamadora"
(never propagates out of this method nor reverts the caller's business transaction). Tracing the
actual call sites in `ResourceController` and `ParecerController` (several of which run
`notificacaoService.notificar*` inside their own `@Transactional` method) shows this guarantee does
not hold: `Notificacao` uses client-generated `GenerationType.UUID`, so `notificacaoRepository.save()`
does not force an immediate flush when it runs inside an ambient transaction, and the constraint
check is deferred to the enclosing transaction's commit — by which point this try/catch has already
returned. See CR-01 below for the full trace and a concrete fix. A second, related but independent
issue (WR-01) is that the same catch blocks conflate destinatario-specific failures with
event-level validation failures, producing misleading logs and total notification loss for an event.

## Critical Issues

### CR-01: DataIntegrityViolationException backstop does not protect the caller's transaction when `criarComFanOutAdmin` runs inside an ambient `@Transactional` context

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:145-154`
**Issue:**
The comment on lines 145-153 states this catch is a defense-in-depth backstop against a residual
concurrent race on `uk_notificacao_dedup`, and asserts it "nunca deve propagar para fora deste
método nem reverter a transação de negócio chamadora." This is not true for at least three real
call sites:

- `ResourceController.atribuirResponsavel` (`@Transactional`, line 1093) calls
  `notificarProcessoAtribuido` at line 1150, after already persisting the `Processo` reassignment
  and an `AuditLog` row in the *same* transaction.
- `ParecerController.createSolicitacao` (`@Transactional`, line 107) calls
  `notificarParecerAtribuido` at line 181/182, after persisting the `ParecerSolicitacao` and
  `AuditLog`.
- `ParecerController.atribuirAdvogado` (`@Transactional`, line 278) calls
  `notificarParecerAtribuido` at line 337/338, same pattern.

`Notificacao.id` uses `@GeneratedValue(strategy = GenerationType.UUID)` (client-side id generation,
`Notificacao.java:24`), so `notificacaoRepository.save(n)` does **not** need to hit the database to
obtain a generated key. When `criar()`/`criarComFanOutAdmin()` run inside one of the ambient
`@Transactional` methods above, Hibernate's default `FlushMode.AUTO` defers the actual `INSERT` (and
therefore the `uk_notificacao_dedup` constraint check) until the enclosing transaction flushes —
which happens at commit time, i.e. **after** `criarComFanOutAdmin` has already returned and the
controller method body has already completed. At that point the `DataIntegrityViolationException`
(wrapped as `TransactionSystemException` by Spring's transaction interceptor) is thrown from the
commit machinery, completely bypassing the try/catch on lines 137-154, and **does** roll back the
whole ambient transaction — reverting the already-"successful" `Processo`/`ParecerSolicitacao`
reassignment and its `AuditLog` row.

This is exactly the failure class NOTF-27/Phase 88 exist to prevent, and it is realistically
triggerable: a double-click / network retry on "assign responsible"/"assign advogado" for the same
target within the race window produces two concurrent transactions attempting to insert the same
`(tenant, destinatario, entidade_tipo, entidade_id, categoria)` tuple — the second one's business
transaction (processo/parecer update + audit log) fails with an unhandled 500, even though
"assign the same person twice" should be a harmless no-op. Note the existing "no-op" guards
(`atribuirResponsavel:1129`, `atribuirAdvogado:316`) only protect against this if the first request's
transaction has already committed and become visible to the second request's read — they do not
close the window between two genuinely concurrent requests.

This is the same "Postgres aborts the whole transaction on any statement failure" pitfall the code
already documents and correctly defends against in `silenciarCategoria` (lines 271-284) with a
native `ON CONFLICT DO NOTHING` upsert — but that fix was not applied here.

**Fix:** Make each notification insert commit (or fail) independently of the caller's ambient
transaction, and force the flush synchronously so the constraint violation is guaranteed to surface
inside the existing try/catch:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Optional<Notificacao> criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                                    String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
    // ... unchanged validation ...
    Notificacao n = Notificacao.builder()
            .tenantId(tenantId)
            .destinatarioId(destinatarioId)
            .categoria(categoria)
            .titulo(titulo)
            .mensagem(mensagem)
            .entidadeTipo(entidadeTipo)
            .entidadeId(entidadeId)
            .linkUrl(linkUrl)
            .build();
    // saveAndFlush forces the INSERT (and constraint check) to run here, synchronously,
    // inside this method's own REQUIRES_NEW transaction -- a violation rolls back only
    // this sub-transaction and is caught by criarComFanOutAdmin's existing try/catch,
    // never the caller's ambient business transaction.
    return Optional.of(notificacaoRepository.saveAndFlush(n));
}
```

(Alternatively, mirror the `silenciarCategoria` precedent and replace the `save()`/catch pattern
with a native `INSERT ... ON CONFLICT (tenant_id, destinatario_id, entidade_tipo, entidade_id,
categoria) DO NOTHING` upsert, eliminating the exception path entirely.)

## Warnings

### WR-01: Event-level validation failures are misreported as "destinatario inválido/órfão" and silently drop the notification for every recipient

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:135-144`
**Issue:** Inside the loop, `categoria`, `titulo`, `entidadeTipo`, `entidadeId` and `linkUrl` are
identical on every iteration (only `dest` and `mensagem` vary). `criar()` validates all of these
(`requireNonBlank`/`requireMaxLength`, lines 53-63) and throws `IllegalArgumentException` for any
violation. If, say, a caller ever builds a `linkUrl` longer than 255 characters (plausible — it is
assembled from path segments plus a UUID, e.g. `"/processos/" + saved.getId()"`, with no length
guard at any call site) or passes a blank `entidadeTipo`, **every single destinatario in `todos`
(primary and all ADMINs) fails identically** on every iteration, and each failure is logged as:
```
"{}: destinatario {} inválido/órfão, notificação ignorada para este destinatário"
```
which is misleading — the destinatario is fine; the bug is in a caller-supplied field shared by the
whole event. The net effect is a **complete, silent loss of notification for the entire event**
(nobody — not the primary recipient, not any ADMIN — gets notified), with logs that point
on-call engineers toward "check this user's data" instead of "check the caller's titulo/linkUrl
construction."

**Fix:** Validate the event-level fields once, before entering the per-destinatario loop, and let
that failure propagate (or be logged distinctly) instead of being caught per-destinatario:

```java
private void criarComFanOutAdmin(UUID tenantId, String categoria, String titulo, String entidadeTipo,
                                  String entidadeId, String linkUrl, Collection<UUID> destinatariosPrimarios,
                                  String mensagemPrimario, String mensagemAdmin, UUID excluirUserId) {
    requireNonBlank("categoria", categoria);
    requireNonBlank("titulo", titulo);
    requireNonBlank("entidadeTipo", entidadeTipo);
    requireNonBlank("entidadeId", entidadeId);
    requireMaxLength("titulo", titulo, MAX_VARCHAR_LENGTH);
    requireMaxLength("entidadeTipo", entidadeTipo, MAX_VARCHAR_LENGTH);
    requireMaxLength("entidadeId", entidadeId, MAX_VARCHAR_LENGTH);
    requireMaxLength("linkUrl", linkUrl, MAX_VARCHAR_LENGTH);
    // ... existing per-destinatario loop, now only catching destinatario-specific
    // failures (orphaned/foreign-tenant destinatarioId, per-user mensagem issues) ...
}
```

### WR-02: ADMIN fan-out notifies deactivated admin accounts indefinitely

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:127`
**Issue:** `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` returns every user with the
ADMIN role regardless of `ativo` status. Unlike `ResourceController.atribuirResponsavel`, which
explicitly rejects `Boolean.FALSE.equals(responsavel.getAtivo())` before assigning a responsible
party, this fan-out has no such filter, so a deactivated ADMIN account keeps accumulating
notification rows for every `FASE_ENTRADA`/`PROCESSO_ATRIBUIDO`/`DOCUMENTO_NOVO`/`PARECER_ATRIBUIDO`
event indefinitely, with no user able to ever read/dismiss them.
**Fix:** Filter the fan-out query by active status, e.g. add
`findByTenantIdAndRoleNameAndAtivoTrue(tenantId, "ADMIN")` to `UserRepository` and use it here, or
filter the returned list with `.filter(admin -> Boolean.TRUE.equals(admin.getAtivo()))` before the
`todos.add(admin.getId())` call on line 132.

## Info

### IN-01: Unchecked `processoId.toString()` — latent NPE if ever called with a null processoId

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:167,190`
**Issue:** `notificarFaseEntrada` and `notificarProcessoAtribuido` both call `processoId.toString()`
without a null check, unlike `responsavelId`, which is explicitly documented and guarded as
nullable. All current callers happen to pass a non-null `processoId` (path variables/persisted
entity ids), so this is not currently reachable, but there is no defensive guard or doc comment
recording the "processoId is never null" contract the way there is for `responsavelId`.
**Fix:** Add an explicit `Objects.requireNonNull(processoId, "processoId")` guard (or a short doc
comment recording the invariant) for parity with how `responsavelId`'s nullability is handled.

### IN-02: Misplaced comment over `MAX_VARCHAR_LENGTH`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:31-35`
**Issue:** The comment block on lines 31-34 documents the class-wide invariant ("único ponto de
escrita de criação... +ADMIN, nunca broadcast em massa") but sits directly above the unrelated
`MAX_VARCHAR_LENGTH` constant declaration on line 35, making it look like documentation for that
constant.
**Fix:** Move the class-wide invariant comment to the top of the class (near the class declaration)
and give `MAX_VARCHAR_LENGTH` its own one-line comment, or none.

### IN-03: `criarComFanOutAdmin`'s 10-parameter, same-typed-argument signature is error-prone

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:117-119`
**Issue:** The private helper takes `(UUID, String, String, String, String, String,
Collection<UUID>, String, String, UUID)` — six same-typed `String` parameters and two same-typed
`UUID` parameters in positional order. All four current call sites were manually checked and are
correct, but this shape makes a future silent parameter-transposition bug (e.g. swapping
`mensagemPrimario`/`mensagemAdmin`, or `entidadeTipo`/`entidadeId`) easy to introduce and hard to
catch in review, since the compiler cannot help.
**Fix:** Consider a small builder/value object (e.g. `FanOutRequest`) grouping
`categoria/titulo/entidadeTipo/entidadeId/linkUrl/mensagemPrimario/mensagemAdmin/excluirUserId`, or
at minimum add parameter-name Javadoc to the method signature.

### IN-04: `reativarCategoria` does not validate `categoria`, unlike `silenciarCategoria`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:291-294`
**Issue:** `silenciarCategoria` validates `categoria` via `CategoriaNotificacao.fromString(...)` and
rejects unknown values with `IllegalArgumentException` (lines 266-270). `reativarCategoria` performs
no such validation — an unknown/mistyped categoria string simply matches zero rows and returns
silently, giving the caller no feedback that the value was invalid.
**Fix:** Mirror the validation in `silenciarCategoria`:
```java
@Transactional
public void reativarCategoria(UUID tenantId, UUID userId, String categoria) {
    CategoriaNotificacao.fromString(categoria)
            .orElseThrow(() -> new IllegalArgumentException("categoria desconhecida: " + categoria));
    notificacaoPreferenciaRepository.deleteByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria);
}
```

---

_Reviewed: 2026-07-14T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
