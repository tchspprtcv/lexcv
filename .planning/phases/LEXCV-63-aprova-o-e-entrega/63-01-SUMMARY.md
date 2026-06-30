---
phase: 63-aprova-o-e-entrega
plan: 01
status: complete
requirements: [PARC-07, PARC-08, PARC-09]
---

# Summary: 63-01 — Aprovação interna e entrega final de pareceres

## What was built

Added the backend approval/delivery state machine for pareceres on top of the existing
Phase 61/62 entities and controller, with no new endpoints beyond the two state-transition
PUTs requested.

### Task 1 — Entity fields
- `ParecerVersao`: `aprovado` (Boolean, `@Builder.Default` false), `aprovadoPorId` (UUID,
  nullable), `aprovadoEm` (LocalDateTime, nullable, set only by `/aprovar`). `onCreate()`
  left untouched.
- `ParecerSolicitacao`: `versaoFinalId` (UUID, nullable), placed after `advogadoId`.

### Task 2 — Controller endpoints
- `PUT /api/v1/pareceres/solicitacoes/{id}/versoes/{versaoId}/aprovar`
  (`@PreAuthorize("hasAuthority('pareceres:manage')")`, ADMIN-only scope):
  validates tenant-scoped lookup of solicitação and versão (versão must belong to the
  solicitação), rejects with 400 if solicitação is already `CONCLUIDO`, sets
  `aprovado=true` / `aprovadoPorId` / `aprovadoEm=now()` on the versão, and transitions
  solicitação status `PENDENTE`/`EM_ELABORACAO` → `EM_REVISAO`.
- `PUT /api/v1/pareceres/solicitacoes/{id}/entregar?versaoFinalId=...`
  (`@PreAuthorize("hasAuthority('pareceres:manage')")`, plus an in-method
  advogado-responsável-or-ADMIN check, 403 otherwise): validates the same tenant-scoped
  lookups, rejects with 400 if already `CONCLUIDO` ("Parecer já foi entregue"), then sets
  `versaoFinalId` and status `CONCLUIDO`.
- Both endpoints take no `@RequestBody` for state fields — all state is set
  programmatically server-side, `versaoFinalId` arrives only as a validated
  `@RequestParam UUID`.
- No new GET endpoint added — delivered pareceres remain readable through the existing
  Phase 61/62 GETs (`/{id}`, `/{id}/versoes`, anexo download), satisfying PARC-09.

## Verification

- `mvn -DskipTests compile` → BUILD SUCCESS (run after each task)
- `grep -n "aprovarVersao\|entregarSolicitacao"` → both methods present
- `grep -n "pareceres:manage"` → 2 occurrences (aprovar + entregar)
- Manual code read confirms: tenant-scoped 404 guards before any mutation, `CONCLUIDO`
  guard returns 400 in both endpoints, versão-belongs-to-solicitação check via
  `getSolicitacaoId().equals(id)` in both endpoints.

## Deviations from plan

None. Implementation matches 63-01-PLAN.md task-by-task (field placement, annotations,
guard ordering, error messages).

## Files changed

- `backend/src/main/java/com/lexcv/models/ParecerVersao.java`
- `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java`
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java`

## Commits

- `814ccff` feat(63): add approval and final-version fields to Parecer entities
- `f6c7350` feat(63): add /aprovar and /entregar endpoints to ParecerController
