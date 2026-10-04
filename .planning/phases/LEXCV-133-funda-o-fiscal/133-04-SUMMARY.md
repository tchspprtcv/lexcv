---
phase: 133-funda-o-fiscal
plan: 04
subsystem: backend-fiscal
tags: [fiscal, efatura, configuracao, auditoria, rbac-audit, validation]
requires:
  - "133-01: ConfiguracaoFiscal (+completa(), NIF_FISCAL_REGEX), ConfiguracaoFiscalRepository, SerieFiscalRepository.existsByTenantIdAndUltimoNumeroGreaterThan, RegimeIva, MotivoIsencaoIva, TipoDocumentoFiscal, AmbienteFiscal, RecusaFiscalException, Clock bean"
provides:
  - "AuditoriaFiscalService: registarDadosAlterados / registarAtivacao / registarDesativacao / registarEmailLigado / registarEmailDesligado (all Propagation.MANDATORY)"
  - "ConfiguracaoFiscalService: obter, guardar, ativar, desativar, definirEmailAutomatico, listarSeries (tenantId + autor always parameters)"
  - "DTO records: ConfiguracaoFiscalRequest, ConfiguracaoFiscalResponse, EmailAutomaticoRequest, SerieFiscalResponse (+de), MotivoIsencaoResponse (+todos)"
  - "Error codes: MOTIVO_ISENCAO_INVALIDO 400, NIF_BLOQUEADO 409, NIF_JA_REGISTADO 409, CONFIGURACAO_FISCAL_CONCORRENTE 409, CONFIGURACAO_FISCAL_INCOMPLETA 422, FATURACAO_JA_EMITIU 409, FATURACAO_DESLIGADA 409, DECLARACAO_NAO_ACEITE 422"
affects:
  - "133-05 FaturacaoController (thin wrapper over ConfiguracaoFiscalService + @Valid on the request DTOs)"
  - "133-07/08 frontend (response shape and error codes already mirrored in web/src/types/faturacao.ts by 133-06)"
  - "Phase 134 (first issued document increments a series -> NIF lock and deactivation refusal activate automatically)"
  - "Phase 137 (reads envioEmailAutomatico; this phase only stores the switch)"
tech-stack:
  added: []
  patterns:
    - "Separate audit writer per domain (fiscal vs RBAC) so the RBAC read path and AuditLogImutabilidadeTest stay untouched"
    - "Refuse-before-mutate: every RecusaFiscalException is thrown before the managed entity is touched"
    - "Audit detalhe stores changed field NAMES only, never values"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java
    - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java
    - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalResponse.java
    - backend/src/main/java/com/lexcv/dtos/EmailAutomaticoRequest.java
    - backend/src/main/java/com/lexcv/dtos/SerieFiscalResponse.java
    - backend/src/main/java/com/lexcv/dtos/MotivoIsencaoResponse.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/dtos/ConfiguracaoFiscalRequestValidationTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/AuditLog.java
decisions:
  - "Fiscal audit events go through a dedicated AuditoriaFiscalService (entidadeTipo configuracao_fiscal), not AuditoriaRbacService; they do not show in the existing Auditoria tab (out of scope per UI-SPEC)"
  - "NIF_JA_REGISTADO (cross-tenant NIF uniqueness, PITFALLS P-25) implemented as planned; it is NOT in 133-CONTEXT.md -- needs user confirmation"
  - "ativar/desativar/email call repository.save explicitly (in addition to dirty checking) so the write is visible in unit tests and explicit in code"
  - "envioEmailAceitePorNome and envioEmailAceiteEm are returned only while the switch is on; the stored acceptance fields stay in the row as history after switching off"
metrics:
  duration: "~25 min"
  completed: 2026-10-04
  tasks: 2
  files: 11
---

# Phase 133 Plan 04: Fiscal Configuration Service and Audit Summary

The office can now save its fiscal data, turn billing on and off, and set the email switch, all through `ConfiguracaoFiscalService`. Billing only turns on when the data is complete. Once any series has issued a number, the NIF can no longer change and billing can no longer be turned off. Each successful change writes one audit event in the same transaction (`AuditoriaFiscalService`, `MANDATORY`). A refused request writes nothing.

## What was built

- **`AuditoriaFiscalService`**: five `registar*` methods, each `@Transactional(propagation = MANDATORY)`. They write `AuditLog` rows with `entidadeTipo = "configuracao_fiscal"` and `entidadeId` = the config id. The `detalhe` holds only `autorNome` plus the keys each action needs: sorted `camposAlterados` (field names, never values), `envioEmailDesligado` (only when true), and `declaracaoAceite`. If serialization fails it throws `IllegalStateException`, so the change rolls back together with its event. The action constants are `public static final`.
- **`AuditLog.java`**: the vocabulary comments now list the five new actions and the `configuracao_fiscal` entity type. Only comments changed.
- **DTOs**: `ConfiguracaoFiscalRequest` uses the exact UI-SPEC validation messages. `ConfiguracaoFiscalResponse` always has `pais = "Cabo Verde"`, `nifBloqueado == documentosEmitidos` and `podeDesativar == ativa && !documentosEmitidos`. `EmailAutomaticoRequest`, `SerieFiscalResponse.de(SerieFiscal)` and `MotivoIsencaoResponse.todos()` (enum order) complete the set.
- **`ConfiguracaoFiscalService`**:
  - `obter` reads and never creates a row (CFG-03).
  - `guardar` trims every field and clears the motive when the regime is `NORMAL`. It refuses `ISENTO` without an official motive. It also refuses when the NIF is locked or already used by another office. It works out which fields changed and does nothing if none did. Otherwise it calls `saveAndFlush`; a `DataIntegrityViolationException` becomes `CONFIGURACAO_FISCAL_CONCORRENTE`.
  - `ativar` / `desativar` apply the completeness check and the "documents already issued" check. Turning billing off also turns the email switch off.
  - `definirEmailAutomatico` needs billing to be on and `aceiteDeclaracao == TRUE`. It records who accepted and when.
  - `listarSeries` maps the series to responses with their labels.
  - None of these methods reads `SecurityContextHolder`.

## Verification

- `AuditoriaFiscalServiceTest` 10/10, `AuditLogImutabilidadeTest` 6/6 (still green, `AuditLogRepository` unchanged).
- `ConfiguracaoFiscalRequestValidationTest` 12/12, `ConfiguracaoFiscalServiceTest` 32/32.
- Full unit suite: `mvn -Dmaven.compiler.release=21 test` gives 521 tests, 0 failures, BUILD SUCCESS.
- `mvn -DskipTests compile spotbugs:check` is clean.
- Acceptance greps: 5 uses of `Propagation.MANDATORY`; no getter for the email address; 8 `RecusaFiscalException(` calls; 7 lines with error codes; the `existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L)` gate is present; no `SecurityContextHolder`.

## TDD Gate Compliance

- Task 1: RED `1740004` (test) then GREEN `11662a3` (feat).
- Task 2: RED `a59db6a` (test) then GREEN `1b5281b` (feat).
- No refactor commits were needed.

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 RED | 1740004 | test(133-04): add failing tests for AuditoriaFiscalService |
| 1 GREEN | 11662a3 | feat(133-04): AuditoriaFiscalService append-only fiscal audit writer |
| 2 RED | a59db6a | test(133-04): add failing tests for ConfiguracaoFiscalService and request validation |
| 2 GREEN | 1b5281b | feat(133-04): fiscal configuration DTOs and ConfiguracaoFiscalService |

## Deviations from Plan

None. The plan was executed as written. Two small interpretation choices are recorded under decisions:
- `ativar`, `desativar` and the email switch call `repository.save` explicitly, alongside dirty checking.
- `envioEmailAceiteEm` follows the same rule as `envioEmailAceitePorNome`: it is returned only while the switch is on.

## Decisions for user confirmation

- **NIF_JA_REGISTADO (cross-tenant NIF uniqueness)** is an addition that is **not in 133-CONTEXT.md**. It came from PITFALLS P-25 through the plan. `guardar` refuses with 409 `NIF_JA_REGISTADO` ("Este NIF já está registado noutro escritório.") when another tenant already has the same NIF in `t_configuracao_fiscal`. The result is only a yes/no. No data from the other tenant is returned, but the answer does reveal that the NIF is in use somewhere else. The rationale is that NIFs are public tax identifiers and two issuers must not share one fiscal identity. There is no DB-level unique constraint on `nif` across tenants, so this is an application-level check. Please confirm you want this rule. If not, remove the check in `ConfiguracaoFiscalService.guardar` and the corresponding test.

## Known Stubs

- The email switch has no sending effect yet. This is intentional per the plan and documented in the javadoc; sending arrives in Phase 137.

## Threat Flags

None. All new surface is covered by T-133-15..20. There are no new endpoints in this plan; the controller comes in 133-05.

## Self-Check: PASSED

- All 11 key files exist on disk.
- Commits 1740004, 11662a3, a59db6a and 1b5281b are present in `git log`.
