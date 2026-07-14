---
phase: 94-notf-27-corrigir-colis-o-de-dedup-admin
verified: 2026-07-14T12:30:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 94: NOTF-27 — Corrigir Colisão de Dedup ADMIN Verification Report

**Phase Goal:** Notificar um destinatário que é simultaneamente membro de equipa/responsável e ADMIN nunca falha por colisão da constraint `uk_notificacao_dedup` — um bug pré-existente desde a Phase 88 (v2.10), agravado pelo alargamento de destinatários que a Phase 95 (NOTF-25) está para introduzir.
**Verified:** 2026-07-14T12:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Um utilizador simultaneamente destinatário primário/responsável/advogado E ADMIN recebe exatamente UMA `Notificacao` (nunca duas) em cada um dos 4 gatilhos | ✓ VERIFIED | 4 new Mockito tests (`notificarFaseEntrada_responsavelTambemAdmin_...`, `notificarProcessoAtribuido_responsavelTambemAdmin_...`, `notificarDocumentoNovo_destinatarioTambemAdmin_...`, `notificarParecerAtribuido_advogadoTambemAdmin_...`) each assert `verify(notificacaoRepository, times(1)).save(...)`. Ran `mvn test -Dtest=NotificacaoServiceTest` myself: 31/31 pass (log: "Tests run: 31, Failures: 0, Errors: 0"). |
| 2 | Nenhuma `DataIntegrityViolationException`/500 propaga para fora de qualquer um dos 4 métodos `notificar*`; a ação de negócio nunca é revertida | ✓ VERIFIED | `criarComFanOutAdmin` (NotificacaoService.java L117-156) wraps `criar(...)` in a per-recipient try with `catch (IllegalArgumentException ...)` and `catch (DataIntegrityViolationException ex)` (L145-154), logging and continuing instead of propagating. Backstop test `notificarDocumentoNovo_saveLancaDataIntegrityViolation_naoPropagaEContinuaFanOut` stubs `save()` to throw DIV on the 1st call and asserts `assertDoesNotThrow(...)` + `verify(..., times(2)).save(any())` — confirmed passing in the live test run. |
| 3 | Os 4 métodos `notificar*` fundem primário(s) + ADMINs num único `LinkedHashSet<UUID>` deduplicado ANTES do loop de criação (em vez de duas chamadas não coordenadas) | ✓ VERIFIED | Read NotificacaoService.java directly: `criarComFanOutAdmin` builds `LinkedHashSet<UUID> primarios` then `LinkedHashSet<UUID> todos = new LinkedHashSet<>(primarios)`, adds ADMIN ids to `todos` (Set naturally dedups), then iterates `todos` once calling `criar()` at most once per person. All 4 public methods (`notificarFaseEntrada` L161, `notificarProcessoAtribuido` L174, `notificarDocumentoNovo` L200, `notificarParecerAtribuido` L212) delegate to this single helper — `notificarAdmins` no longer exists (`grep -cE "void notificarAdmins\("` = 0). |
| 4 | Comportamentos preexistentes preservados sem regressão (2ª/3ª pessoa, exclusão de ator, isolamento de órfão por-destinatário, uma linha por ADMIN atual, ordem primário-antes-de-admins) | ✓ VERIFIED | All pre-existing trigger-level tests still pass unmodified in behavior: `notificarFaseEntrada_responsavelNaoNulo_geraLinhaResponsavelELinhaAdmin` asserts `List.of(responsavelId, admin.getId())` insertion order; `notificarProcessoAtribuido_responsavelNaoNulo_...` asserts 2nd-person message for primary + distinct admin message; `notificarDocumentoNovo_adminIgualAoAtor_adminExcluidoDoFanOut` and `..._advogadoIgualAoAtor_...` cover actor-exclusion-of-admin; orphan isolation tests (`..._responsavelInvalido_naoLancaExcecaoEAdminAindaRecebeLinha`, etc.) still present. Full run: 31/31 green, 0 regressions. |
| 5 | As 4 assinaturas públicas permanecem idênticas — os 7 call sites em `ResourceController`/`ParecerController` continuam a compilar sem alteração | ✓ VERIFIED | `grep` confirms all 7 call sites unchanged (ResourceController.java L1085, L1150, L1847, L2768, L2781; ParecerController.java L181, L337). Ran `mvn -q compile` myself: exit 0, no errors — production code (including both controllers) compiles clean against the new signatures. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | Private helper `criarComFanOutAdmin` (merge + single loop + DIV backstop) + 4 `notificar*` delegating; `notificarAdmins` overloads removed; obsolete comments deleted | ✓ VERIFIED | Read in full. Helper at L117-156 present and substantive (not a stub — real merge/loop/dual-catch logic). `criarComFanOutAdmin(` count = 5 (1 decl + 4 call sites), matches plan acceptance criterion exactly. `void notificarAdmins(` count = 0. `"recebe 2 linhas"` and `"sem dedup entre"` obsolete comments both count = 0. |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | 4 primary==ADMIN collision tests + 1 DIV backstop test; 2 obsolete `notificarAdmins`-direct-call tests removed | ✓ VERIFIED | Read the 5 new tests (L544-644) — all substantive, assert `times(1)`/`times(2)` + `assertDoesNotThrow` as specified. `grep -c "TambemAdmin"` = 4. `grep -c "DataIntegrityViolationException"` = 3 (import + comment + test). Confirmed `notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant` / `notificarAdminsComExclusao_umAdminExcluido_apenasOOutroRecebeLinha` no longer present (no `notificarAdmins` match anywhere in file). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `notificarFaseEntrada`/`notificarProcessoAtribuido`/`notificarDocumentoNovo`/`notificarParecerAtribuido` | `criarComFanOutAdmin` | delegation call | ✓ WIRED | Each of the 4 methods' body ends in a single call to `criarComFanOutAdmin(...)` with method-specific primarios/messages/excluirUserId — confirmed by direct code read, not just grep count. |
| `criarComFanOutAdmin` | `DataIntegrityViolationException` | per-recipient `catch` backstop | ✓ WIRED | `catch (DataIntegrityViolationException ex)` at L145, logs and continues loop — mirrors `AlertasDiariosJob.notificar()` pattern per plan intent (that file confirmed untouched). |
| `criarComFanOutAdmin` | `NotificacaoService.criar` | single loop over deduplicated `LinkedHashSet<UUID>` | ✓ WIRED | Loop at L135-155 calls `criar(tenantId, dest, ...)` exactly once per `dest` in `todos` (the merged/deduplicated set) — confirmed by reading the loop body directly. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `criarComFanOutAdmin` | `todos` (LinkedHashSet<UUID>) | `destinatariosPrimarios` param (caller-supplied) + live `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` | Yes — real repository call, not hardcoded/static; tests mock the repository call itself (standard Mockito unit-test boundary) but production code path invokes the real Spring Data query | ✓ FLOWING |

### Behavioral Spot-Checks / Probe Execution

This phase is a pure backend bug fix with real Mockito unit tests. I ran the following myself (not trusted from SUMMARY.md):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| NotificacaoServiceTest suite green (incl. 5 new regression tests) | `cd backend && mvn -Dtest=NotificacaoServiceTest test` | `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` | ✓ PASS |
| Full backend Surefire suite green (no collateral regression) | `cd backend && mvn test` | `AlertasDiariosJobTest` 9/9, `NotificacaoServiceTest` 31/31, `RiscoPrazoServiceTest` 15/15 → `Tests run: 55, Failures: 0, Errors: 0` / `BUILD SUCCESS` | ✓ PASS |
| Production compiles with unchanged public signatures (7 call sites) | `cd backend && mvn -q compile` | Exit 0, no errors | ✓ PASS |
| Plan's own sanity greps | `grep -c "criarComFanOutAdmin("` = 5; `grep -cE "void notificarAdmins\("` = 0; `grep -c "catch (DataIntegrityViolationException"` = 1; obsolete-comment greps = 0 each | All match plan acceptance criteria exactly | ✓ PASS |
| Diff scope confined to the 2 declared files (AlertasDiariosJob.java untouched) | `git diff --stat 544a43d~1 f30d941 -- <5 candidate files>` | Only `NotificacaoService.java` (170 lines changed) and `NotificacaoServiceTest.java` (153 lines changed) appear; `AlertasDiariosJob.java`/controllers absent from the diff | ✓ PASS |

No MINIO_ENDPOINT or Testcontainers dependency was involved — confirmed this phase's tests are pure Mockito (`NotificacaoRepositoryIT`/`NotificacaoPreferenciaRepositoryIT` are separate Failsafe-scoped integration tests, untouched by this phase, and correctly excluded from `mvn test`).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|------------|-------------|--------|----------|
| NOTF-27 | 94-01-PLAN.md | Corrigir bug pré-existente — notificar um destinatário que é simultaneamente membro de equipa e ADMIN não deve falhar (500) por colisão do constraint `uk_notificacao_dedup` | ✓ SATISFIED | Fully implemented and test-proven per Observable Truths 1-5 above. |

No orphaned requirements: REQUIREMENTS.md traceability table maps only NOTF-27 to Phase 94 (line 65), and the PLAN's `requirements:` frontmatter declares exactly `[NOTF-27]` — 1:1 match, no gap.

**Note (non-blocking, informational):** REQUIREMENTS.md still shows `NOTF-27` as an unchecked `[ ]` item (line 30) and `Status: Pending` in the traceability table (line 65), unlike Phase 93's `NOTF-24` which is marked `[x]`/`Complete`. This is a documentation-bookkeeping lag, not a code gap — the actual implementation and tests fully satisfy the requirement as verified above. This should be updated as part of closing out this phase (typically done by the orchestrator/milestone bookkeeping step following a passed verification), but does not affect the goal-achievement determination.

### Anti-Patterns Found

None. Scanned both modified files for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, empty-return stubs (`return null|return {}|return []|=> {}`), and console-log-only implementations — zero matches in either `NotificacaoService.java` or `NotificacaoServiceTest.java`.

### Human Verification Required

None. This phase is entirely backend service logic with full Mockito unit-test coverage; no UI, no visual behavior, no external service integration, and no `<human-check>` blocks were present in 94-01-PLAN.md's tasks (both tasks used `<verify><automated>` only). All verification was completed programmatically, including live re-execution of the test suite by the verifier (not just trusting SUMMARY.md's reported counts, which matched exactly).

### Gaps Summary

No gaps. All 5 must-have truths from PLAN frontmatter (which subsume all 3 ROADMAP success criteria) are verified against the actual codebase: the merge-before-write `LinkedHashSet<UUID>` dedup helper exists, is substantive, is wired from all 4 public `notificar*` methods, carries a `DataIntegrityViolationException` backstop, and is proven by both new collision-regression tests (times(1)) and a dedicated backstop test — all passing in a live `mvn test` run I executed myself (31/31 for the class, 55/55 for the full backend Surefire suite). Public signatures are unchanged and production compiles clean. The only observation is a documentation-lag item in REQUIREMENTS.md (checkbox/status not yet flipped to Complete), which is informational only and does not block phase goal achievement.

---

_Verified: 2026-07-14T12:30:00Z_
_Verifier: Claude (gsd-verifier)_
