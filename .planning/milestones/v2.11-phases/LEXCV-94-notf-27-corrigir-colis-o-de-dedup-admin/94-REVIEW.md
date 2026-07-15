---
phase: LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin
reviewed: 2026-07-14T15:30:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
findings:
  critical: 1
  warning: 1
  info: 5
  total: 7
status: issues_found
---

# Phase LEXCV-94: Code Review Report

**Reviewed:** 2026-07-14T15:30:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

This is a re-review of the phase after the prior iteration's fixes (CR-01, WR-01, WR-02, commits
`f839243`/`a34b5b1`/`7fd8bb0`) were applied. The `LinkedHashSet<UUID>` merge in
`criarComFanOutAdmin` (NOTF-27's core fix) is sound and correctly eliminates the deterministic
primary-recipient-is-also-ADMIN dedup collision this phase set out to fix. The previous WR-01
(event-level validation hoisted above the per-destinatario loop) and WR-02 (deactivated admins
excluded via `findByTenantIdAndRoleNameAndAtivoTrue`) fixes are both correctly implemented and
verified against the current code.

However, the previous CR-01 fix — replacing `save()`/`saveAndFlush()` with a native
`@Modifying @Query` upsert (`NotificacaoRepository.inserirSeNaoDuplicado`) to avoid the
"Postgres aborts the whole ambient transaction" failure mode — introduces a **new, more severe**
regression: the repository method carries no `@Transactional` of its own, and neither does
`NotificacaoService.criar()`/`criarComFanOutAdmin()` nor any of the four public `notificar*`
wrappers. `@Modifying` queries are required by the JPA specification to run inside an active
transaction (`Query.executeUpdate()` throws `TransactionRequiredException` otherwise); Spring
Data JPA does not fabricate a fallback transaction for a custom `@Query` method that carries no
`@Transactional` anywhere in its declaration (verified against the shipped
`spring-data-commons:3.4.1` bytecode: `RepositoryAnnotationTransactionAttributeSource
.computeTransactionAttribute` returns `null` for exactly this shape of method, and a `null`
transaction attribute means `TransactionInterceptor` does not open one). Concretely, this means
every call to `criar()`/`criarComFanOutAdmin()` that is **not nested inside an already-open,
externally-managed transaction** will throw at runtime — and most current callers are not:
`ResourceController.createProcesso`, `ResourceController.createProcessoFase`,
`ResourceController.uploadDocumento`, and the entirety of `AlertasDiariosJob` (the original
Phase 88 motivation for this whole subsystem) call these methods with no ambient transaction
anywhere in their stack. Only `ResourceController.atribuirResponsavel` and
`ParecerController.createSolicitacao`/`atribuirAdvogado` (all three explicitly `@Transactional`)
happen to work, which is presumably why this was not caught by the prior fix's manual trace (it
only reasoned about the three call sites CR-01's original writeup happened to cite, not the full
call-site set). No automated test exercises this — `NotificacaoServiceTest` mocks the repository,
and the `NotificacaoRepositoryIT` Testcontainers suite (unable to run in the fixer's sandbox, per
their own admission in `94-REVIEW-FIX.md`) does not reference `inserirSeNaoDuplicado` at all.

The previously-reported IN-01 through IN-04 info items remain open and unaddressed (they were
explicitly out of scope for the `critical_warning`-only fix pass); they are restated below for
completeness of this re-review. One additional info item and one additional warning were found
this pass.

## Critical Issues

### CR-01: `inserirSeNaoDuplicado` has no active transaction on most real call paths — throws `TransactionRequiredException` at runtime

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:80-92`
**Issue:**
`inserirSeNaoDuplicado` is a `@Modifying @Query(nativeQuery = true)` method with no `@Transactional`
annotation of its own. Per the JPA specification, `Query.executeUpdate()` (what a `@Modifying`
query compiles to) **requires an active transaction**, or it throws
`jakarta.persistence.TransactionRequiredException: Executing an update/delete query`. Spring Data
JPA does not synthesize a fallback transaction for this: I extracted and disassembled the shipped
`spring-data-commons-3.4.1.jar`'s
`TransactionalRepositoryProxyPostProcessor$RepositoryAnnotationTransactionAttributeSource
.computeTransactionAttribute(Method, Class)` — for a custom query method with no `@Transactional`
anywhere on the interface method/class and no distinct backing "target class" implementation (true
for every hand-written `@Query` method, as opposed to inherited `SimpleJpaRepository` CRUD methods
like `save()`, which *are* annotated `@Transactional` directly on `SimpleJpaRepository`), the method
returns `null`. A `null` transaction attribute means Spring's `TransactionInterceptor` opens **no**
transaction for that call — the method executes with whatever transaction (if any) is already
active on the calling thread.

None of the call chain in `NotificacaoService.java` provides that ambient transaction:
`criar()` (`NotificacaoService.java:43`), `criarComFanOutAdmin()`
(`NotificacaoService.java:140`), and all four public wrappers
(`notificarFaseEntrada`/`notificarProcessoAtribuido`/`notificarDocumentoNovo`/
`notificarParecerAtribuido`, `NotificacaoService.java:200,213,239,251`) carry no `@Transactional`
of their own — contrast this with `marcarLida`/`marcarTodasLidas`/`silenciarCategoria`/
`reativarCategoria`, all of which are explicitly `@Transactional`.

Tracing real call sites confirms this is not hypothetical:
- `ResourceController.createProcesso` (`ResourceController.java:1063`, **no** `@Transactional`)
  calls `notificarProcessoAtribuido` at line 1085 whenever a new `Processo` is created with a
  `responsavelId` — an everyday operation, with **no** try/catch around the call at all.
- `ResourceController.createProcessoFase` (`ResourceController.java:1825`, **no**
  `@Transactional`) calls `notificarFaseEntrada` at line 1847 — wrapped only in
  `catch (IllegalArgumentException ex)`, which does **not** catch
  `TransactionRequiredException` (a `PersistenceException`, not an `IllegalArgumentException`).
- `ResourceController.uploadDocumento` (`ResourceController.java:2668`, **no** `@Transactional`)
  calls `notificarDocumentoNovo` at lines 2768/2781 — same `catch (IllegalArgumentException ex)`
  gap.
- `AlertasDiariosJob.executar`/`processarTenant`/`processarPrazos`/`processarEventos`/
  `processarHonorarios` (none `@Transactional`) all funnel into `notificar()`
  (`AlertasDiariosJob.java:307`), which calls `notificacaoService.criar(...)` directly at line 317
  — this is the **entire daily alertas job** that Phase 88 exists to run; every notification it
  tries to create on every scheduled run will throw.

By contrast, `ResourceController.atribuirResponsavel` (`@Transactional`, line 1093) and
`ParecerController.createSolicitacao`/`atribuirAdvogado` (both `@Transactional`, lines 107 and
278) happen to work today, because they supply the ambient transaction `inserirSeNaoDuplicado`
needs — these are also the exact three call sites the original CR-01 writeup traced through, which
is presumably why the gap in the other (majority) call sites went unnoticed.

**Net effect:** creating a processo with a responsible party, adding a fase to a processo,
uploading a document with a linked processo/cliente, and the entire daily alertas job all become
broken by this change — each throws an unhandled 500 (or, worse, in `createProcesso`'s case, the
`Processo` row is already committed via its own auto-committing `processoRepository.save()` call
before the notification call throws, so the client sees a request failure while the processo was
in fact created, risking a confusing retry/duplicate).

**Fix:** Give `inserirSeNaoDuplicado` its own transaction directly on the repository method, so it
works uniformly regardless of whether a caller happens to have an ambient transaction (self-
invocation from `criarComFanOutAdmin` is irrelevant here since the annotation lives on the
repository interface, not on `NotificacaoService`, so Spring's proxy-based interception applies
normally):

```java
@Modifying
@Transactional
@Query(value = """
        INSERT INTO t_notificacao (id, tenant_id, destinatario_id, categoria, entidade_tipo, entidade_id,
                                    titulo, mensagem, link_url, lida, created_at)
        VALUES (:id, :tenantId, :destinatarioId, :categoria, :entidadeTipo, :entidadeId,
                :titulo, :mensagem, :linkUrl, false, :createdAt)
        ON CONFLICT (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria) DO NOTHING
        """, nativeQuery = true)
int inserirSeNaoDuplicado(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                           @Param("destinatarioId") UUID destinatarioId, @Param("categoria") String categoria,
                           @Param("entidadeTipo") String entidadeTipo, @Param("entidadeId") String entidadeId,
                           @Param("titulo") String titulo, @Param("mensagem") String mensagem,
                           @Param("linkUrl") String linkUrl, @Param("createdAt") LocalDateTime createdAt);
```

With default propagation (`REQUIRED`), this joins the caller's transaction where one already exists
(`atribuirResponsavel`, `ParecerController`) and opens its own short-lived transaction otherwise
(`createProcesso`, `createProcessoFase`, `uploadDocumento`, `AlertasDiariosJob`), so both classes of
caller work correctly. Add (or extend) a `NotificacaoRepositoryIT` test that calls
`inserirSeNaoDuplicado` **without** any surrounding `@Transactional` test method (mirroring a
non-transactional controller call) to catch a regression of this specific gap — none of the
existing Mockito-based `NotificacaoServiceTest` tests can, since they stub the repository entirely
and never exercise Spring's real transactional proxy.

## Warnings

### WR-01: `AlertasDiariosJob.notificar()`'s `catch (DataIntegrityViolationException ex)` is now dead code, masking that the exception path it defends against can no longer occur

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:88-96` (contract change) — dead consumer at `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:322-330`
**Issue:** Since the previous CR-01 fix, `NotificacaoService.criar()` can no longer throw
`DataIntegrityViolationException` on the dedup path — a duplicate insert now returns
`Optional.empty()` via `inserirSeNaoDuplicado`'s `ON CONFLICT DO NOTHING` instead. This makes
`AlertasDiariosJob.notificar()`'s `catch (DataIntegrityViolationException ex)` block
(`AlertasDiariosJob.java:322-330`) permanently unreachable. This isn't just cosmetic: the comment
on those lines still asserts this is an active "backstop... if the DB-level unique index still
rejects a duplicate insert", which is no longer true and will mislead the next person investigating
a duplicate-notification incident into thinking this path is exercised and tested when it is
provably dead.
**Fix:** Remove the now-dead `catch (DataIntegrityViolationException ex)` block (and its now-unused
`org.springframework.dao.DataIntegrityViolationException` import) from `AlertasDiariosJob.java`, the
same cleanup already applied to `NotificacaoService.criarComFanOutAdmin` for the identical reason
per `94-REVIEW-FIX.md`'s CR-01 section — this file was evidently missed because it wasn't part of
this phase's declared file scope, even though it consumes the exact contract that scope changed.

## Info

### IN-01: Unchecked `processoId.toString()` — latent NPE if ever called with a null processoId

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:206,229`
**Issue:** `notificarFaseEntrada` and `notificarProcessoAtribuido` both call `processoId.toString()`
without a null check, unlike `responsavelId`, which is explicitly documented and guarded as
nullable. All current callers pass a non-null `processoId`, so this is not currently reachable, but
there is no defensive guard or doc comment recording the "processoId is never null" contract the
way there is for `responsavelId`. (Carried forward, unaddressed, from the prior review pass —
`fix_scope: critical_warning` excluded it.)
**Fix:** Add an explicit `Objects.requireNonNull(processoId, "processoId")` guard (or a short doc
comment recording the invariant) for parity with how `responsavelId`'s nullability is handled.

### IN-02: Misplaced comment over `MAX_VARCHAR_LENGTH`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:31-35`
**Issue:** The comment block on lines 31-34 documents the class-wide invariant ("único ponto de
escrita de criação... +ADMIN, nunca broadcast em massa") but sits directly above the unrelated
`MAX_VARCHAR_LENGTH` constant declaration on line 35, making it look like documentation for that
constant. (Carried forward, unaddressed.)
**Fix:** Move the class-wide invariant comment to the top of the class (near the class declaration)
and give `MAX_VARCHAR_LENGTH` its own one-line comment, or none.

### IN-03: `criarComFanOutAdmin`'s 10-parameter, same-typed-argument signature is error-prone

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:140-142`
**Issue:** The private helper takes `(UUID, String, String, String, String, String,
Collection<UUID>, String, String, UUID)` — six same-typed `String` parameters and two same-typed
`UUID` parameters in positional order. All current call sites are correct, but this shape makes a
future silent parameter-transposition bug (e.g. swapping `mensagemPrimario`/`mensagemAdmin`, or
`entidadeTipo`/`entidadeId`) easy to introduce and hard to catch in review, since the compiler
cannot help. (Carried forward, unaddressed.)
**Fix:** Consider a small builder/value object (e.g. `FanOutRequest`) grouping
`categoria/titulo/entidadeTipo/entidadeId/linkUrl/mensagemPrimario/mensagemAdmin/excluirUserId`, or
at minimum add parameter-name Javadoc to the method signature.

### IN-04: `reativarCategoria` does not validate `categoria`, unlike `silenciarCategoria`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:330-333`
**Issue:** `silenciarCategoria` validates `categoria` via `CategoriaNotificacao.fromString(...)` and
rejects unknown values with `IllegalArgumentException` (lines 304-309). `reativarCategoria` performs
no such validation — an unknown/mistyped categoria string simply matches zero rows and returns
silently, giving the caller no feedback that the value was invalid. (Carried forward, unaddressed.)
**Fix:** Mirror the validation in `silenciarCategoria`:
```java
@Transactional
public void reativarCategoria(UUID tenantId, UUID userId, String categoria) {
    CategoriaNotificacao.fromString(categoria)
            .orElseThrow(() -> new IllegalArgumentException("categoria desconhecida: " + categoria));
    notificacaoPreferenciaRepository.deleteByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria);
}
```

### IN-05: `criarComFanOutAdmin` has no null-guard on the `destinatariosPrimarios` collection itself

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:163-168`
**Issue:** `for (UUID dest : destinatariosPrimarios)` (line 164) will throw `NullPointerException` if
the `Collection<UUID>` argument itself (not one of its elements) is `null`. Every current caller
already guards against this individually (`notificarDocumentoNovo` uses
`destinatarios == null ? List.of() : destinatarios`; the other three wrappers always construct a
`List.of(...)`/`List.of()` locally), so this is not reachable today, but it means the null-safety of
this private helper depends entirely on every caller remembering to guard externally rather than on
the helper defending its own contract.
**Fix:** Normalize once inside the helper, e.g.
`Collection<UUID> primariosSeguro = destinatariosPrimarios != null ? destinatariosPrimarios : List.of();`
before the loop, removing the need for each individual caller to guard.

---

_Reviewed: 2026-07-14T15:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
