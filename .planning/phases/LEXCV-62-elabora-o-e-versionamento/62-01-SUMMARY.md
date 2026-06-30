# 62-01 Summary: Parecer Versioning Data Layer

## What shipped

- `backend/src/main/java/com/lexcv/models/ParecerVersao.java` — immutable JPA entity mapping to `t_parecer_versao`. Carries its own `tenant_id` (defense-in-depth, mirrors `ParecerSolicitacao`/`Documento`), `solicitacaoId`, `numeroVersao` (plain Integer, server-set), `conteudo` (nullable TEXT), `caminhoAnexo` (nullable), `criadoPorId`, and an immutable `createdAt` set via `@PrePersist`. No `updatedAt` field and no update hook — versions cannot be mutated after creation.
- `backend/src/main/java/com/lexcv/repositories/ParecerVersaoRepository.java` — `JpaRepository<ParecerVersao, UUID>` with `findBySolicitacaoId` (version history/comparison) and `findMaxNumeroVersaoBySolicitacaoId` (`@Query` selecting `MAX(v.numeroVersao)` scoped by `solicitacaoId`, not tenant — tenant isolation is enforced by the controller via the parent solicitação check in 62-02).

## Verification

- `mvn -DskipTests compile` succeeded after each task with no errors.
- Source inspection confirms required column names (`tenant_id`, `solicitacao_id`, `numero_versao`, `caminho_anexo`, `criado_por_id`, `created_at`), `columnDefinition = "TEXT"` on `conteudo`, the `@PrePersist` hook, and absence of any `updatedAt` field.
- Repository confirmed to extend `JpaRepository<ParecerVersao, UUID>` and expose both required method signatures.

## Commits

1. `feat(62): add ParecerVersao entity for parecer versioning`
2. `feat(62): add ParecerVersaoRepository with per-solicitacao max-version query`

## Notes for 62-02

- `ParecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId` is per-solicitação only; the controller must independently verify the parent `ParecerSolicitacao.tenantId` matches the caller's tenant before trusting any version row.
- `numeroVersao` has no DB sequence — the controller must compute `MAX+1` under a `synchronized` block (same pattern as `Cliente.numeroSequencial` in `ResourceController`) to avoid race conditions on concurrent version creation.
- No migration needed: `ddl-auto=update` creates `t_parecer_versao` automatically on next dev boot.
