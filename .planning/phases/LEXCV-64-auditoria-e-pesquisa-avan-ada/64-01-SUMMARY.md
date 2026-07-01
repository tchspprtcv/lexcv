---
phase: 64-auditoria-e-pesquisa-avan-ada
plan: 01
status: complete
---

# 64-01 Summary: Auditoria automática nos endpoints de transição do ParecerController

## What was built

Added automatic `AuditLog` writes to the 5 transition endpoints of
`ParecerController` (`backend/src/main/java/com/lexcv/controllers/ParecerController.java`):

- `createSolicitacao` → `acao=parecer_criar`, `entidadeTipo=parecer_solicitacao`
- `atribuirAdvogado` → `acao=parecer_atribuir`, `entidadeTipo=parecer_solicitacao`
- `createVersao` → `acao=parecer_versao_criar`, `entidadeTipo=parecer_versao`
- `aprovarVersao` → `acao=parecer_aprovar`, `entidadeTipo=parecer_versao`
- `entregarSolicitacao` → `acao=parecer_entregar`, `entidadeTipo=parecer_solicitacao`

`AuditLogRepository` was constructor-injected as a new final field, following the
existing repository injection convention in the class. `createSolicitacao` and
`atribuirAdvogado` did not previously extract `Authentication`/`UserPrincipal` in
their bodies — the standard two-line extraction idiom
(`SecurityContextHolder.getContext().getAuthentication()` →
`(UserPrincipal) auth.getPrincipal()`) was added to both, matching the pattern
already used in `aprovarVersao`/`entregarSolicitacao`/`createVersao`. The other
three endpoints reused their existing `principal` variable.

Every audit call sets `autorId` from `principal.getUserId()` (never from the
request payload) and `tenantId` from the endpoint's already-resolved `tenantId`
(itself from `principal.getTenantId()`). `processoId` is explicitly set to
`null` in all 5 calls, per plan/pattern-map decision (pareceres are not linked
to Processo for audit purposes in this phase). Each audit `save()` call is
placed immediately after the primary entity save, before the `return`, with the
comment `// Audit record — PARA-01: autor_id from SecurityContext`.

## Verification

- `mvn -DskipTests compile` → BUILD SUCCESS, no errors.
- `grep -c "auditLogRepository.save" ParecerController.java` → 5
- Distinct `acao` values present (`parecer_criar`, `parecer_atribuir`,
  `parecer_versao_criar`, `parecer_aprovar`, `parecer_entregar`) → 5
- `private final AuditLogRepository auditLogRepository` field present → 1
- No audit call derives `autorId` from the request body; all use
  `principal.getUserId()` sourced from `SecurityContextHolder`.

## Deviations from plan

None. Implementation follows 64-PATTERNS.md exactly (field/import placement,
acao/entidadeTipo/entidadeId mapping per endpoint, comment convention,
`processoId(null)` on all 5).

## Files changed

- `backend/src/main/java/com/lexcv/controllers/ParecerController.java`

## Commits

- `4eadf78` — feat(64): audit ParecerController transition endpoints (PARA-01)
