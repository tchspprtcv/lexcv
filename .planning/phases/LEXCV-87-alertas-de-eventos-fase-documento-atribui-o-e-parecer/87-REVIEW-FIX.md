---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
fixed_at: 2026-07-09T13:11:08Z
review_path: .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
iteration: 3
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 87: Code Review Fix Report

**Fixed at:** 2026-07-09T13:11:08Z
**Source review:** .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
**Iteration:** 3 (final allowed iteration — auto-fix loop stops here regardless of remaining findings)

**Summary:**
- Findings in scope: 7 (2 critical, 5 warning; IN-01 excluded — `fix_scope` is `critical_warning`)
- Fixed: 7
- Skipped: 0

All fixes were made in an isolated git worktree on a dedicated branch
(`gsd-reviewfix/87-33200`), one commit per finding (see commit hashes below)
— no rebase or history rewrite. Each fix was verified with a re-read of the
modified region (Tier 1) plus a real compile check (Tier 2):
`mvn -o -q -DskipTests compile` (offline, exit 0, clean before and after
every backend commit) and `npx tsc --noEmit -p tsconfig.json` for the one
frontend change (baseline: 0 pre-existing errors, same 0 after). For CR-02,
the full `NotificacaoServiceTest` suite was additionally run after the fix
(`mvn -o -q -Dtest=NotificacaoServiceTest test`): 20/20 tests passed,
including two new regression tests added as part of this fix.

**IMPORTANT — fast-forward onto `master` did NOT complete.** The cleanup
step that normally fast-forwards `master` to the fix branch
(`git merge --ff-only gsd-reviewfix/87-33200`) failed because the main
working tree has *unrelated, unstaged, uncommitted local changes* to
`backend/src/main/java/com/lexcv/controllers/ResourceController.java` (a
file two of these fixes — WR-02 and, indirectly, WR-01/WR-04's sibling
edits — also touch) that would have been overwritten. Per this agent's
safety protocol, that local work was left completely untouched and the
merge was aborted rather than forced. **All 7 fix commits below exist only
on the `gsd-reviewfix/87-33200` branch, not yet on `master`.** `master`'s
`HEAD` is still `8361ad6`, unchanged from before this run. To land these
fixes, a human needs to either (a) commit or `git stash` the current
uncommitted changes on `master`, then
`git merge --ff-only gsd-reviewfix/87-33200` (or rebase/cherry-pick if that
still conflicts), or (b) resolve the overlap in
`ResourceController.java` manually. The branch was deliberately **not**
deleted so this can happen safely.

Four of the seven fixes below are flagged **"fixed: requires human
verification"** per this agent's verification-strategy rules: each is a
genuine conditional/algorithm/state-handling change that only Tier 1
(re-read) and Tier 2 (compile) verification could cover here — none of them
had an existing automated test harness in this codebase capable of exercising
the exact runtime scenario (no controller-test suite exists for
`ParecerController`/`ResourceController`, no frontend component test exists
for `page.tsx`, and no H2/Testcontainers setup exists in this project for a
real concurrent-transaction test). This is a call for a human to do a quick
manual/functional pass on those four before considering the phase fully
verified — it does not indicate any known defect in the fix itself.

## Fixed Issues

### CR-01: `updateSolicitacao` silently wipes `prazo` and can crash on `prioridade` for any partial update

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `ce6d1f0`
**Commit status:** fixed: requires human verification
**Applied fix:** `prazo` and `prioridade` in `updateSolicitacao` are now
null-guarded (`if (payload.getX() != null) { solicitacao.setX(...); }`)
exactly like the pre-existing `clienteId`/`processoId` guards two lines
below them — a payload that omits a field now preserves the existing DB
value instead of wiping it (prazo) or risking an uncaught 500 on flush
(prioridade, `@Column(nullable = false)`). `descricao`, previously not
editable through this endpoint at all, was added with the same
null-and-not-blank guard used at creation time. `clienteId`/`processoId`
guards and the "status/advogadoId intentionally excluded" comment were left
untouched.
**Why flagged for human verification:** this is a conditional-logic fix on
a legal-deadline-bearing field (`prazo`) — Tier 1/2 confirm the code
compiles and the guards are textually present and correctly shaped, but
only an actual partial-update HTTP request (e.g. a PUT with only
`clienteId` in the body) exercised against a running instance can fully
confirm the runtime behavior matches intent.

### CR-02: `notificarProcessoAtribuido` / `notificarParecerAtribuido` can roll back an already-persisted assignment

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`, `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Commit:** `3be4d52`
**Commit status:** fixed
**Applied fix:** Wrapped the primary recipient's `criar(...)` call in both
`notificarProcessoAtribuido` and `notificarParecerAtribuido` in a
try/catch(IllegalArgumentException), logging a warning and continuing to
the unconditional `notificarAdmins(...)` fan-out — the exact isolation
pattern already used in `notificarFaseEntrada`/`notificarDocumentoNovo`/
`notificarAdmins` from the prior iteration's CR-01 fix. Added two new
regression tests (`notificarProcessoAtribuido_responsavelInvalido_...`,
`notificarParecerAtribuido_advogadoInvalido_...`) mocking an orphaned
(non-null) recipient id and asserting `assertDoesNotThrow(...)` plus that
the ADMIN fan-out still runs — mirroring the existing
`notificarFaseEntrada_responsavelNulo_...` test but for an invalid rather
than null recipient, as the review requested. Full suite run:
`mvn -o -q -Dtest=NotificacaoServiceTest test` → 20/20 passed (18 existing +
2 new), 0 failures.

### WR-01: Parecer audit records always hardcode `processoId(null)`, hiding them from the processo's Auditoria tab

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `cc77529`
**Commit status:** fixed
**Applied fix:** All five `AuditLog.builder()...processoId(null)` call sites
now pass the solicitação's real `processoId` — `saved.getProcessoId()` in
`createSolicitacao`, `atribuirAdvogado`, and `entregarSolicitacao` (each has
a `saved` variable holding the persisted `ParecerSolicitacao` in scope), and
`solicitacao.getProcessoId()` in `aprovarVersao` and `createVersao` (in
those two methods, `saved`/no equivalent variable refers to the
`ParecerVersao`/is absent, so `solicitacao` — the fetched
`ParecerSolicitacao` — is the correct variable in scope). `null` still
flows through naturally for pareceres genuinely unlinked from a processo,
since `getProcessoId()` returns `null` in that case.

### WR-02: The `ativo` invariant the prior fix exposed is still not enforced server-side at any assignment endpoint

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `d17f3f6`
**Commit status:** fixed: requires human verification
**Applied fix:** Added `|| Boolean.FALSE.equals(x.getAtivo())` to the
existing tenant-membership guard at all three call sites named in the
review: `ResourceController.atribuirResponsavel`,
`ResourceController.createPrazo`, and `ParecerController.validateAdvogado`
(the shared private helper used by both `createSolicitacao` and
`atribuirAdvogado`). Error messages updated to mention "está inativo" at
all four affected response bodies (`atribuirResponsavel`, `createPrazo`,
and both `validateAdvogado` call sites in `ParecerController`). Uses the
`Boolean.FALSE.equals(...)` idiom (not `!getAtivo()`) so a `null` `ativo`
(the pre-existing-data default) is treated as active, matching the
frontend's `u.ativo !== false` convention from the prior iteration's WR-01
fix.
**Why flagged for human verification:** authorization-adjacent conditional
change across three call sites with no existing controller-test harness in
this codebase to exercise it automatically.

### WR-03: "Reatribuir Responsável" picker excludes the current responsável when inactive, leaving the `<select>` in a phantom-value state

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** `4e43534`
**Commit status:** fixed: requires human verification
**Applied fix:** Introduced `filteredUsers` (active-only, replacing the
inline `.filter(...)` previously chained straight into `.map(...)`) and
`currentStillActive` (whether the current responsável is present in
`filteredUsers`). When the current responsável is inactive, a synthetic
`<option value={currentResponsavelId}>{currentResponsavelNome} (inativo)</option>`
is rendered ahead of the active-user options, so the `<select>` always has
a matching `<option>` for `selectedUserId`'s initial value instead of
silently falling back to the disabled placeholder. `novoNome`'s existing
derivation (querying the unfiltered `tenantUsers.data`) and the confirm
button's existing `disabled` condition were left untouched — both already
behave correctly given the fix (the confirm button stays disabled while
`selectedUserId === currentResponsavelId`, whether active or not, and
`novoNome` only needs to resolve a *different*, necessarily-active user by
the time the confirm dialog is reachable).
**Why flagged for human verification:** a conditional-rendering/state fix
in a component with no frontend test file — Tier 2 (`tsc --noEmit`,
0 errors before and after) confirms type-correctness but not that the
dropdown visually renders as intended; worth a quick manual check with an
inactive current responsável.

### WR-04: `synchronized (ParecerVersaoRepository.class)` does not prevent concurrent `numeroVersao` collisions

**Files modified:** `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`, `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `601b44f`
**Commit status:** fixed: requires human verification
**Applied fix:** Replaced the JVM monitor with a genuine DB-level lock,
following the exact existing convention already established elsewhere in
this codebase (`SystemSettingRepository.findByIdForUpdate` /
`SetupService.initializeSystem`): added
`ParecerSolicitacaoRepository.findByIdForUpdate(UUID)` annotated
`@Lock(LockModeType.PESSIMISTIC_WRITE)`, and `createVersao` now fetches its
`solicitacao` via this method instead of plain `findById` — the row lock is
held for the rest of the already-`@Transactional` method (same as the
`SetupService` precedent), so a second concurrent request for the same
`solicitacaoId` blocks at `findByIdForUpdate` until the first transaction
commits, at which point its own `findMaxNumeroVersaoBySolicitacaoId(...)`
correctly observes the just-inserted row. The `synchronized` block was
removed entirely (no longer needed — the DB row lock is the actual
correctness mechanism now, not a JVM monitor that only serialized threads
within one instance). No schema migration was required (`SELECT ... FOR
UPDATE` needs no DDL), so this works identically under both
`ddl-auto=update` (dev) and `ddl-auto=validate` (prod).
**Why flagged for human verification:** a concurrency-control algorithm
change — compile-check confirms it's syntactically valid Spring Data JPA,
but genuine verification of the fix's core claim (two concurrent requests
for the same `solicitacaoId` now serialize instead of racing) would need a
real concurrent-transaction test against Postgres; this project has no
H2/Testcontainers setup to exercise that here.

### WR-05: `prioridade` is never validated against its documented domain in `ParecerController`

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `dd5c1a1`
**Commit status:** fixed
**Applied fix:** Added an allowlist check (`Set.of("ALTA", "MEDIA",
"BAIXA")`, case-insensitive via `.toUpperCase()`) in both
`createSolicitacao` and `updateSolicitacao`, returning `400 Bad Request`
with `"prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"` on an
out-of-domain value — mirroring `ResourceController.createPrazo`'s
identical, already-established check for `Prazo.prioridade` field-for-field
(same message text, same allowlist, same local-`Set` idiom rather than a
new shared constant, to stay consistent with that existing convention).
Added the missing `import java.util.Set;` to `ParecerController.java`. In
`updateSolicitacao`, the check runs immediately after the existing
`processoId`-belongs-to-`clienteId` validation and before any field
mutation, matching the fail-fast-before-mutating-state pattern the method
already uses for its other request validations.

---

_Fixed: 2026-07-09T13:11:08Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
