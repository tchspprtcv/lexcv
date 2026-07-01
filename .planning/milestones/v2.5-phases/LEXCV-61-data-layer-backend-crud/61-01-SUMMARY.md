---
phase: 61-data-layer-backend-crud
plan: 01
status: complete
---

# Summary: Data Layer Foundation for Parecer Jurídico

## What was built

1. **`ParecerSolicitacao` JPA entity** (`backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java`) — maps to `t_parecer_solicitacao`, following the `Prazo`/`Processo`/`Evento` conventions: UUID id, `tenantId` (not null), `clienteId` (not null), `descricao` (TEXT, not null, per PARC-01), optional `processoId`/`advogadoId`, `prioridade` (default `MEDIA`), `status` (default `PENDENTE`, free-form String per codebase convention — no Java enum), `prazo` (LocalDate, nullable), and `createdAt` set via `@PrePersist`.
2. **`ParecerSolicitacaoRepository`** (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`) — `JpaRepository<ParecerSolicitacao, UUID>` with `findByTenantId(UUID tenantId)`.
3. **RBAC seeding** (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`) — added `pareceres:view/create/edit/manage` to `permKeys`. ADMIN receives all four via the existing blanket `permissionMap.values()` assignment. ADVOGADO gets `view`+`create`+`edit`. TECNICO and ASSISTENTE get `view` only. `pareceres:manage` is withheld from all non-ADMIN roles (reserved for Phase 63 approval/delivery).
4. **Frontend scope mirror** (`web/src/lib/permissions.ts`) — added `KNOWN_SCOPES` (includes `"pareceres"` alongside the five existing scopes) and a derived `PermissionScope` type, as an additive canonical registry. `resolveScopedPermissions`/`hasScopedPermission` signatures were left unchanged (`scope: string`) to avoid breaking existing ad-hoc call sites. No nav/route entries were added — no `/pareceres` page exists until Phase 62.

## Tasks completed

- Task 1: ParecerSolicitacao entity + repository — commit `10f6ce6`
- Task 2: pareceres RBAC seeding — commit `107d0a3`
- Task 3: frontend pareceres scope mirror (PARC-10) — commit `8c4ac79`

## Verification

- `mvn -DskipTests compile` — passed after Task 1 and again after Task 2 (both green, no errors).
- `grep -v '^\s*//' DatabaseSeeder.java | grep -c 'pareceres:view'` → `4` (1 in `permKeys` + ASSISTENTE + TECNICO + ADVOGADO references), matching the per-role assignment table in `61-CONTEXT.md`.
- `grep -c "pareceres" web/src/lib/permissions.ts` → confirms the scope is present.
- **Deviation from plan's verify step**: `pnpm exec tsc --noEmit -p tsconfig.json` could not run — `web/node_modules` is not installed in this worktree (fresh worktree, no `pnpm install` has been run). The edit itself is a minimal, self-contained addition (one new `const`/`as const` array and one derived `type`, no new imports, no signature changes to existing exported functions), manually reviewed for TypeScript correctness. Recommend running `pnpm install && pnpm exec tsc --noEmit` once dependencies are available (e.g. after worktree merge) to confirm typecheck passes, per the plan's stated verification command.

## Requirements delivered

- PARC-04 (persistence contract for parecer requests) — entity + repository in place.
- PARC-10 (RBAC scope `pareceres:view/create/edit/manage`, backend seed + frontend mirror) — fully delivered in this phase per the plan's stated intent (no Phase 62 frontend dependency needed for the scope declaration itself).

## Notes for downstream plans

- Plan 02 (ParecerController) builds directly on `ParecerSolicitacaoRepository.findByTenantId` for tenant-scoped listing, and on the seeded `pareceres:*` authorities for `@PreAuthorize`.
- `t_parecer_solicitacao` table will be created automatically by `ddl-auto=update` on next backend boot — no manual migration needed in dev.
