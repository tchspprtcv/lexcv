---
phase: LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin
fixed_at: 2026-07-14T13:35:00Z
review_path: .planning/phases/LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin/94-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-94: Code Review Fix Report

**Fixed at:** 2026-07-14T13:35:00Z
**Source review:** .planning/phases/LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin/94-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (CR-01, WR-01, WR-02 — `fix_scope: critical_warning`, so IN-01..IN-04 excluded)
- Fixed: 3
- Skipped: 0

## Important deviation from the spawning task's suggested CR-01 fix

The spawning task's instructions suggested a minimal fix for CR-01: change
`notificacaoRepository.save(n)` to `notificacaoRepository.saveAndFlush(n)` inside `criar()`,
citing Phase 93's `NotificacaoPreferencia` race condition as "the exact same bug class already
found and fixed." Investigation before implementing showed this citation actually points at the
**superseded, intermediate** fix from that history, not the final one:

- Phase 93 iteration 2 (commit `ae35bbb`) applied exactly this pattern
  (`saveAndFlush()` + `catch(DataIntegrityViolationException)`) to `silenciarCategoria()`.
- Phase 93 iteration 3's own re-review then escalated that fix to **Critical** (`CR-01`) and
  replaced it entirely (commit `c3cab8a`) with a native `INSERT ... ON CONFLICT DO NOTHING`
  upsert, because `saveAndFlush()` + local catch does **not** protect the caller's transaction
  against real PostgreSQL: Postgres aborts the *entire* transaction the instant any statement
  violates a constraint, and catching the translated exception locally cannot "un-abort" it — the
  interceptor's subsequent implicit `COMMIT` either fails outright or is silently treated as a
  rollback, hiding the failure. This is documented at length in
  `.planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW-FIX.md`
  (iteration 3 section).

Applying only `saveAndFlush()` to this phase's `criar()` would have reproduced the exact defect
Phase 93 already found and escalated — for a bug the current review explicitly rates Critical.

A second, independent problem makes even the `@Transactional(propagation = REQUIRES_NEW)`
variant the 94-REVIEW.md itself proposes for `criar()` non-functional on the call paths CR-01
actually cites (`ResourceController.atribuirResponsavel`,
`ParecerController.createSolicitacao`/`atribuirAdvogado`): `criarComFanOutAdmin()` invokes
`criar(...)` via **self-invocation** (`this.criar(...)` from another method of the same class).
Spring's default proxy-based AOP does not intercept self-invocation, so a `@Transactional`
annotation on `criar()` would be inert on exactly the call path this fix needs to protect —
only external calls (e.g. `AlertasDiariosJob` calling the injected `NotificacaoService` bean)
would actually get a new physical transaction from that annotation.

**Fix actually applied:** the atomic `ON CONFLICT DO NOTHING` native upsert (the "alternatively"
option in 94-REVIEW.md's own CR-01 Fix section), mirroring the current, reviewer-approved,
already-shipped `silenciarCategoria()`/`upsertSilenciar` implementation. This sidesteps both
problems at once: no Spring transaction-boundary trickery is needed (works identically regardless
of self-invocation or ambient-transaction state), and no exception is ever raised on the
"duplicate" path, so there is nothing for the caller's transaction to abort on.

## Fixed Issues

### CR-01: `DataIntegrityViolationException` backstop does not protect the caller's transaction when `criarComFanOutAdmin` runs inside an ambient `@Transactional` context

**Files modified:**
- `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java`
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`

**Commit:** f839243

**Applied fix:** Added `NotificacaoRepository.inserirSeNaoDuplicado(...)`, a `@Modifying
@Query(nativeQuery = true)` method executing `INSERT INTO t_notificacao (...) VALUES (...) ON
CONFLICT (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria) DO NOTHING`,
returning the number of rows actually inserted (0 = duplicate skipped, 1 = inserted) — mirroring
`NotificacaoPreferenciaRepository.upsertSilenciar` exactly. `id` and `created_at` are generated in
Java (`UUID.randomUUID()` / `LocalDateTime.now()`) rather than via `gen_random_uuid()`/`now()` in
SQL, so the in-memory `Notificacao` returned to the caller on the success path is byte-for-byte
consistent with what was persisted, with no SELECT-after-INSERT round trip.

Rewrote `NotificacaoService.criar()`'s final persistence step to call `inserirSeNaoDuplicado(...)`
instead of `notificacaoRepository.save(n)`; on 0 rows affected it logs a warning and returns
`Optional.empty()` (the same "nothing persisted" contract already used for the silenced-category
path), never throwing. Removed the now-dead `catch (DataIntegrityViolationException ex)` block
from `criarComFanOutAdmin()` (unreachable since `criar()` can no longer throw that exception on
this path) and the now-unused `org.springframework.dao.DataIntegrityViolationException` import.

Updated `NotificacaoServiceTest`: every test exercising `criar()` (directly or via a `notificar*`
wrapper) now stubs/verifies `inserirSeNaoDuplicado(...)` instead of `save(...)`, using
`ArgumentCaptor<UUID>`/`ArgumentCaptor<String>` on the specific positional parameters
(`destinatarioId`, `categoria`, `entidadeTipo`, `mensagem`) each test needs, since the method no
longer takes a single `Notificacao` object. `marcarLida()`/`marcarTodasLidas()` tests are
untouched (those still use `save()`/`saveAll()`, an unrelated code path). Replaced
`notificarDocumentoNovo_saveLancaDataIntegrityViolation_naoPropagaEContinuaFanOut` (which
simulated the old, now-impossible exception path) with
`notificarDocumentoNovo_inserirSeNaoDuplicadoRetorna0_naoPropagaEContinuaFanOut`, which stubs the
first `inserirSeNaoDuplicado` call to return `0` (duplicate) and the second to return `1`
(inserted), proving the fan-out continues past a duplicate without throwing.

**Verification performed:** `mvn -o compile` and `mvn -o test-compile` both succeeded (exit 0).
`mvn -o test -Dtest=NotificacaoServiceTest,AlertasDiariosJobTest`: 40/0/0/0 (Tests run/Failures/
Errors/Skipped). Full `mvn -o test` for the whole backend module (all three `*Test.java` classes —
`NotificacaoServiceTest`, `AlertasDiariosJobTest`, `RiscoPrazoServiceTest`; no Docker/Testcontainers
available in this sandbox, so `*IT.java` integration tests were not exercised, consistent with the
same limitation documented by both the Phase 93 iteration-2 fixer and iteration-3 reviewer):
55/0/0/0, BUILD SUCCESS.

**Human verification recommended:** this is a logic/runtime-semantics fix (an atomic SQL-level
constraint-handling change), and none of the Testcontainers-backed `NotificacaoRepositoryIT`/
`NotificacaoPreferenciaRepositoryIT` suites could run in this sandbox (no Docker daemon reachable).
Recommend adding (or extending an existing) integration test exercising
`NotificacaoRepository.inserirSeNaoDuplicado` directly against real PostgreSQL with two concurrent
inserts for the same dedup tuple — mirroring
`NotificacaoPreferenciaRepositoryIT#upsertSilenciar_duasTransacoesConcorrentes_...` — before this
lands in an environment where a genuine concurrent double-insert can occur, to get an actual
green/red signal for the "0 rows on conflict, no exception" claim under real concurrent load.

### WR-01: Event-level validation failures are misreported as "destinatario inválido/órfão" and silently drop the notification for every recipient

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`

**Commit:** a34b5b1

**Applied fix:** Added the five event-level validation calls
(`requireNonBlank`/`requireMaxLength` for `categoria`, `titulo`, `entidadeTipo`, `entidadeId`,
`linkUrl`) to the very top of `criarComFanOutAdmin`, before the per-destinatario loop, exactly as
94-REVIEW.md's Fix section proposed. A violation now throws `IllegalArgumentException` once,
immediately, before any destinatario is touched — instead of being caught identically on every
loop iteration and logged as a misleading "destinatario inválido/órfão" for every recipient. The
per-destinatario validation inside `criar()` (lines 53-63, unchanged) remains as defense-in-depth;
this is intentionally redundant for the shared fields, since `criar()` is still the single choke
point for all callers (including `AlertasDiariosJob`, which never goes through
`criarComFanOutAdmin`). `dest`/`mensagem` (the fields that legitimately vary per iteration) are
deliberately NOT part of this upfront check.

**Verification performed:** `mvn -o test-compile` succeeded. `mvn -o test
-Dtest=NotificacaoServiceTest,AlertasDiariosJobTest`: 40/0/0/0 — all existing tests (including the
four `criar_*_lancaIllegalArgumentException` tests that exercise these exact fields, just through
`criar()` directly rather than `criarComFanOutAdmin`) continued to pass unchanged, confirming the
earlier validation point produces identical externally-observable behavior for all current
callers. No new test was added for the "shared field invalid, caught once instead of N times"
distinction itself, since asserting *how many times* a log line fires is brittle; the existing
`assertThrows(IllegalArgumentException.class, ...)` coverage already proves the validation fires.

### WR-02: ADMIN fan-out notifies deactivated admin accounts indefinitely

**Files modified:**
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java`
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`

**Commit:** 7fd8bb0

**Applied fix:** Added `UserRepository.findByTenantIdAndRoleNameAndAtivoTrue(tenantId, roleName)`,
a JPQL query identical to the existing `findByTenantIdAndRoleName` plus `AND u.ativo = true` —
added as a **new**, separate method rather than editing `findByTenantIdAndRoleName` in place, so
`AlertasDiariosJob.safeAdmins()` (the daily job's own ADMIN fan-out, which shares the same
underlying query and has the same latent gap) is left untouched, since it was not part of this
review's scope. `NotificacaoService.criarComFanOutAdmin` now calls the new
`findByTenantIdAndRoleNameAndAtivoTrue` instead, so a deactivated ADMIN account no longer
accumulates `FASE_ENTRADA`/`PROCESSO_ATRIBUIDO`/`DOCUMENTO_NOVO`/`PARECER_ATRIBUIDO` notification
rows it can never read or dismiss — mirroring the `ativo` check
`ResourceController.atribuirResponsavel` already applies before assigning a responsible party.

Updated `NotificacaoServiceTest`: all 15 stubs of the ADMIN fan-out query were renamed from
`findByTenantIdAndRoleName` to `findByTenantIdAndRoleNameAndAtivoTrue` (production code no longer
calls the old method at all from this class, so leaving any old-name stub in place would trip
Mockito's `STRICT_STUBS` `UnnecessaryStubbingException`). `AlertasDiariosJobTest`'s stubs of the
old method were left unchanged, matching the untouched production behavior there.

**Verification performed:** `mvn -o test-compile` succeeded. `mvn -o test
-Dtest=NotificacaoServiceTest,AlertasDiariosJobTest`: 40/0/0/0. Full `mvn -o test` (whole backend
module, all three `*Test.java` classes): 55/0/0/0, BUILD SUCCESS — confirming `AlertasDiariosJob`'s
own ADMIN fan-out (still using the un-renamed `findByTenantIdAndRoleName`) is unaffected. No new
test was added specifically asserting that a deactivated admin is excluded (would require a
`User.builder().ativo(false)` fixture plus a query-level assertion that a mocked repository can't
meaningfully provide, since the `ativo = true` filter lives in the JPQL `WHERE` clause itself, not
in Java branching); this guarantee is provable only against a real database and is a reasonable
candidate for a future `UserRepositoryIT`/`NotificacaoRepositoryIT` addition.

## Skipped Issues

None — all three in-scope findings (CR-01, WR-01, WR-02) were fixed. IN-01 through IN-04 were out
of scope for this pass (`fix_scope: critical_warning`) and are unchanged; see 94-REVIEW.md for
their descriptions if a future `--all`-scope pass wants to pick them up.

---

_Fixed: 2026-07-14T13:35:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
