---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 03
subsystem: backend-notifications
tags: [fiscal, efatura, notificacao, rbac, tdd]
requires: []
provides:
  - "CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU(false) (10 categories, 2 non-silenceable)"
  - "NotificacaoComunicacaoFiscal.notificarFalhaPersistente(tenantId, documentoId, numeroFormatado, episodio) -> int created"
  - "Constants CATEGORIA = COMUNICACAO_FISCAL_FALHOU, ENTIDADE_TIPO = documento_fiscal, PERMISSAO = financeiro:manage"
affects: [136-04, 136-13, 136-15]
tech-stack:
  added: []
  patterns:
    - "Background recipient filtering by the exact permission set the auth filter would compose (UserPrincipal.create over the resolved roles + permissions)"
    - "Per-episode dedup key entidadeId = documentoId:episodio on the existing ON CONFLICT notification insert"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscalTest.java
    - backend/src/test/java/com/lexcv/models/CategoriaNotificacaoTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
decisions:
  - "Effective permissions = UserPrincipal.create(..., resolverNomesPapeis(user), resolverPermissoesEfectivas(user), Set.of()).getPermissions(), the same composition JwtAuthenticationFilter uses. resolverPermissoesEfectivas alone omits the ADMIN block that UserPrincipal.create adds in code"
  - "entidadeTipo is lower-case documento_fiscal (codebase convention), not the illustrative DOCUMENTO_FISCAL of the UI-SPEC; the UI navigates by linkUrl"
  - "episodio is the comunicacao's reprocessamentos counter at failure time; the caller (136-13) passes it"
  - "The method never throws: user lookup, per-user permission resolution and criar are all inside try/catch(RuntimeException); a failed lookup returns 0 with a WARN that carries ids only"
metrics:
  duration: "~30 min"
  completed: 2026-10-05
  tasks: 2
  files: 4
---

# Phase 136 Plan 03: Non-silenceable COMUNICACAO_FISCAL_FALHOU notification Summary

There is a new notification category, `COMUNICACAO_FISCAL_FALHOU`, and it cannot be muted. `NotificacaoComunicacaoFiscal` uses it when a document's eFatura communication becomes a definitive `ERRO`. It notifies each active user of the same office whose effective permissions include `financeiro:manage`, once per failure episode.

- A repeat call for the same episode creates nothing new.
- A re-failure after a reprocess (the next episode) notifies again.
- A failure for one recipient never stops the others and never reaches the caller.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | COMUNICACAO_FISCAL_FALHOU non-silenceable category | 34b22b7 (RED), c2b79c6 (GREEN) |
| 2 | NotificacaoComunicacaoFiscal: effective financeiro:manage, episode dedup, isolation | 10be6ea (RED), 8567442 (GREEN) |

## Verification

**CategoriaNotificacaoTest:** 4 tests, green.
- Exactly 10 categories, in order.
- `fromString` finds the new category, and both `isSilenciavel()` and `isSilenciavelCategoria` return false for it.
- Only `PRAZO_VENCIDO` and `COMUNICACAO_FISCAL_FALHOU` are non-silenceable; every other category still is.
- `fromString` never throws.

**NotificacaoComunicacaoFiscalTest:** 9 tests, green.
- **Recipients:** only the active user with `financeiro:manage` is notified. Users with view+edit, inactive users, and users with `ativo = null` are not.
- **ADMIN role:** notified through the auth-filter composition even without `financeiro:manage` in the DB permissions.
- **Arguments:** exact UI-SPEC title and message, `documento_fiscal`, `documentoId:2`, and the `/financeiro/documentos-fiscais/{id}` link.
- **Episodes:** episodes 0 and 1 produce distinct `entidadeId` values.
- **Duplicates:** a duplicate (empty Optional) does not count.
- **Isolation:** when `criar` throws IllegalArgumentException or IllegalStateException for some recipients, the others are still notified and nothing is thrown. A permission-resolution failure for one user does not stop the next one.
- **User lookup:** if the lookup fails, the method returns 0 without throwing. Otherwise only `findByTenantId(tenant)` is called (`verifyNoMoreInteractions`).

**Other checks:**
- `*Notificacao*Test` is green.
- The full backend unit suite (`mvn -Dmaven.compiler.release=21 test`) ran 1037 tests: 0 failures, 0 errors, 0 skipped.
- `mvn -DskipTests compile spotbugs:check` is clean.

**Acceptance greps:**
- `COMUNICACAO_FISCAL_FALHOU(false)` appears once.
- `resolverPermissoesEfectivas` appears 2 times (Javadoc + call).
- `findByTenantIdAndRoleName` and `@Transactional` appear 0 times.
- "Falha na comunicação do documento " appears once.

## Deviations from Plan

**1. [Rule 2 - Correctness] Effective permissions mirror the auth filter, not `resolverPermissoesEfectivas` alone**
- **Found during:** Task 2.
- **Issue:** By design, `ResolucaoPapeisService.resolverPermissoesEfectivas` leaves out "parcela 3", the ADMIN permission block that `UserPrincipal.create` adds in code. Filtering on it alone could skip an ADMIN who does hold `financeiro:manage` at request time. The goal is "effective financeiro:manage holders".
- **Fix:** Recipients are filtered on `UserPrincipal.create(..., resolverNomesPapeis, resolverPermissoesEfectivas, Set.of()).getPermissions()`, exactly as `JwtAuthenticationFilter` composes them. Added the test `adminRecebeComoNoFiltroDeAutenticacao`. The method is still not chosen by role name.
- **Commit:** 8567442.

**2. [Rule 2 - Robustness] "Never throws" covers the user lookup and the permission resolution too**
- **Found during:** Task 2.
- **Fix:**
  - Per-user permission resolution moved inside the per-recipient try.
  - A failing `findByTenantId` returns 0 with an ids-only WARN.
  - Two extra tests cover these paths.
- **Commit:** 8567442.

**3. [Rule 1 - SAST] Removed redundant null checks flagged by SpotBugs (RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE)**
- **Found during:** Task 2.
- **Fix:** Both resolver methods are declared non-null, so their results are passed directly.
- **Commit:** 8567442.

## TDD Gate Compliance

- **Task 1:** RED `test(136-03)` 34b22b7, which failed to compile on the missing constant. GREEN `feat(136-03)` c2b79c6.
- **Task 2:** RED `test(136-03)` 10be6ea, which failed to compile on the missing service. GREEN `feat(136-03)` 8567442. The two robustness tests were added in the GREEN commit.

## Notes for downstream plans

- **136-13:** call `notificarFalhaPersistente(tenantId, documentoId, numeroFormatado, comunicacao.getReprocessamentos())` only after the transaction that writes `ERRO` has committed.
- **136-04:** the web side registers the same category as non-silenceable ("Falha de comunicação fiscal", red badge).

## Threat Flags

None. T-136-08 (tenant-only lookup, effective permission filter), T-136-09 (document number only, ids-only logs), T-136-10 (never throws, called after commit) and T-136-11 (non-silenceable) are mitigated as planned.

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscal.java
- FOUND: backend/src/test/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscalTest.java
- FOUND: backend/src/test/java/com/lexcv/models/CategoriaNotificacaoTest.java
- FOUND: commits 34b22b7, c2b79c6, 10be6ea, 8567442
