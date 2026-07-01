---
phase: 64-auditoria-e-pesquisa-avan-ada
plan: 02
status: complete
---

# 64-02 Summary: Pesquisa textual avançada de pareceres

## What was built

Added advanced full-text search over pareceres, satisfying PARS-01 (free-text
search) and PARS-02 (text combined with structured filters):

- `ParecerSolicitacaoRepository.pesquisar(...)` — new native SQL `@Query`
  (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`)
  returning `List<ParecerSolicitacao>`. Joins `t_parecer_solicitacao` to
  `t_parecer_versao`, restricting the join to each solicitação's most recent
  version via a correlated `MAX(numero_versao)` subquery (mirroring
  `ParecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId`'s aggregate
  pattern but correlated per-row), so text matches never produce duplicate
  rows. `texto` filters via Postgres `ILIKE '%' || :texto || '%'` on
  `v.conteudo`. Six filters (`texto`, `clienteId`, `advogadoId`, `status`,
  `dataInicio`, `dataFim`) are all optional via the
  `(:param IS NULL OR campo = :param)` idiom (date filters use `>=`/`<=`
  against `s.created_at`). `WHERE` always starts with
  `s.tenant_id = :tenantId`. `nativeQuery = true` was required because
  `ILIKE` is Postgres-specific, not portable JPQL — this is a new pattern
  for the repository layer (no prior native `@Query` existed).

- `GET /api/v1/pareceres/pesquisa` handler `pesquisarSolicitacoes`
  (`backend/src/main/java/com/lexcv/controllers/ParecerController.java`),
  placed adjacent to `listSolicitacoes`/`getSolicitacao`. Uses an absolute
  path in `@GetMapping` to bypass the class's
  `@RequestMapping("/api/v1/pareceres/solicitacoes")` base path (Spring
  permits this). Gated by `@PreAuthorize("hasAuthority('pareceres:view')")`,
  same scope as the existing list endpoint. Resolves `tenantId` via the
  existing `getTenantId()` helper and delegates directly to
  `parecerSolicitacaoRepository.pesquisar(tenantId, texto, clienteId,
  advogadoId, status, dataInicio, dataFim)` — no stream-filtering. Always
  returns `ResponseEntity.ok(result)`, including for an empty list (no 404
  branch), consistent with `listSolicitacoes`'s convention.

## Verification

- `mvn -DskipTests compile` → BUILD SUCCESS (run after each task).
- `grep -c "pesquisar" ParecerSolicitacaoRepository.java` → 1 (method name).
- `grep -c "nativeQuery = true" ParecerSolicitacaoRepository.java` → 1
  (occurrence in the annotation; `grep -c` also matched the explanatory
  comment mentioning "nativeQuery = true", confirmed by reading the file
  directly that only one real annotation attribute exists).
- Query verified by direct read to contain `ILIKE`, the correlated
  `MAX(v2.numero_versao)` subquery, `s.tenant_id = :tenantId` in the WHERE
  clause, and exactly 6 `(:param IS NULL OR ...)` optional-filter clauses.
- `grep -c "/api/v1/pareceres/pesquisa" ParecerController.java` → 1.
- `grep -c "hasAuthority('pareceres:view')" ParecerController.java` → 6
  (5 pre-existing view endpoints + 1 new `/pesquisa` endpoint — confirms the
  new endpoint carries the required scope).
- Handler body directly calls
  `parecerSolicitacaoRepository.pesquisar(tenantId, ...)` as its only data
  access; no 404 branch present; returns `ResponseEntity.ok(result)`
  unconditionally.
- Threat model: all six filters plus `texto` are bound via `@Param`
  (T-64-04, SQL injection mitigated — no string concatenation into the SQL
  text, the `ILIKE '%' || :texto || '%'` interpolation happens in SQL via a
  bind parameter, not Java string building). `WHERE` unconditionally opens
  with the tenant filter (T-64-05). Endpoint requires `pareceres:view`
  (T-64-06).

## Deviations from plan

None. Implementation follows 64-02-PLAN.md and 64-PATTERNS.md exactly:
native query with the prescribed optional-param idiom, correlated
max-version subquery, absolute-path `@GetMapping`, same `@PreAuthorize`
scope and empty-result convention as `listSolicitacoes`.

## Files changed

- `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java`

## Commits

- `cbc6196` — feat(64): add pesquisar() native query to ParecerSolicitacaoRepository (PARS-01/PARS-02)
- `2182856` — feat(64): add GET /api/v1/pareceres/pesquisa endpoint (PARS-01/PARS-02)
