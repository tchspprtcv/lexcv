---
phase: 123-auditoria-de-isolamento-dedicada
plan: 01
subsystem: api
tags: [tenant-isolation, security-audit, rbac, multi-tenancy]

# Dependency graph
requires:
  - phase: 120-consola-de-administra-o-de-tenants
    provides: "PlatformAdminController (gate de classe hasRole('PLATAFORMA_ADMIN'), os 4 handlers /platform/tenants*, TenantAdminSummaryResponse/TenantProvisionResponse) — o objeto sob auditoria"
  - phase: 122-relat-rio-de-utiliza-o-por-tenant
    provides: "/plataforma/relatorio, reutilizando o mesmo GET /platform/tenants e TenantAdminSummaryResponse da consola — a 2ª superfície sob auditoria"
  - phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
    provides: "121-ISOL-AUDIT.md — o formato de tabela de veredito (Query/Guard | Scope confirmed | Verdict) e a secção de comandos de reprodução, reutilizados literalmente"
provides:
  - "123-ISOL-AUDIT.md — veredito por superfície do Critério de Sucesso 1 do ROADMAP (consola + relatório expõem dados só via endpoints gated a PLATAFORMA_ADMIN), com 14 linhas de tabela, todas COVERED, re-derivadas por execução de comando e leitura fresca do código"
  - "Disposição nomeada para os 2 caminhos backend não-gated-por-papel que tocam Tenant: GET /auth/me (own-tenant-only, UserPrincipal.getTenantId()) e POST /setup/initialize (gate singleton, PROV-06)"
  - "2 divergências de ambiente genuinamente novas documentadas (MSYS2 path-mangling em padrões grep com barra inicial; '|' sem -E tratado como literal neste GNU grep 3.0) — nenhuma é achado de código"
affects: [123-02 (acrescenta Critério de Sucesso 2, decisão do Pitfall 1, e veredito final da fase ao mesmo ficheiro)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reutilização literal do formato de tabela de veredito de 121-ISOL-AUDIT.md/97-01-SUMMARY.md (AUD-01) — 3ª vez que este formato é reaproveitado neste projeto"

key-files:
  created:
    - .planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md
  modified: []

key-decisions:
  - "requirements mark-complete NOT executado para ISOL-04, apesar de listado no frontmatter deste plano — 123-CONTEXT.md e o próprio plano são explícitos: ISOL-04 só fecha no Plano 02 (Critério 2 + decisão do Pitfall 1 + veredito final), mesmo precedente de Phase 120 Planos 02/03/04 (PROV-02/PROV-05) e Phase 122 Planos 01/02 (UTIL-01)"
  - "Task 1 (auditoria por execução/leitura) não produziu nenhum ficheiro nem commit próprio — é uma task de investigação cujas conclusões alimentam a Task 2; só a Task 2 (criação de 123-ISOL-AUDIT.md) gerou um commit, consistente com o próprio plano ('nenhum ficheiro... as conclusões passam para a Task 2')"
  - "Descoberto e documentado ao vivo: grep com padrão de barra inicial (ex. '/platform') é mangled pelo runtime MSYS2 do Git Bash neste Windows, devolvendo sempre 0 falsos; e '|' sem a flag -E é tratado como carácter literal por este GNU grep 3.0, não como alternância — ambos afetam comandos literalmente escritos no próprio plano (acção da Task 1). Corrigidos com MSYS_NO_PATHCONV=1 e -E respetivamente, revalidados, e registados na secção de Divergências de 123-ISOL-AUDIT.md para fases futuras não repetirem"

requirements-completed: []

# Metrics
duration: ~25min
completed: 2026-07-30
---

# Phase 123 Plan 01: Auditoria de Isolamento Dedicada — Critério de Sucesso 1 Summary

**Re-derivação independente, por execução de comando e leitura fresca do código (não por citação das Fases 120/121/122), do Critério de Sucesso 1 da Fase 123: `123-ISOL-AUDIT.md` criado com 14 linhas de veredito `COVERED`, cobrindo o gate de classe único de `PlatformAdminController`, os 4 endpoints `/platform/tenants*`, `TenantAdminSummaryResponse`/`TenantProvisionResponse`, as 4 chamadas de `use-platform-admin.ts`, os 2 guards de página, e os 2 caminhos backend não-gated-por-papel (`GET /auth/me`, `POST /setup/initialize`) — todos nomeados explicitamente, nenhum omitido por "óbvio". Zero alterações de código de produção.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-30T11:14:01Z
- **Tasks:** 2 (Task 1: auditoria por execução/leitura, sem ficheiros; Task 2: criação de `123-ISOL-AUDIT.md`)
- **Files modified:** 1 (criado)

## Accomplishments

- Confirmado por 3 comandos independentes (2 greps + 1 execução de teste com proxy AOP real) que `PlatformAdminController` tem exatamente 1 gate de classe (`hasRole('PLATAFORMA_ADMIN')`) e 0 gates de método nos seus 4 handlers (`POST`/`GET`/`PUT /tenants/{id}`/`PATCH /tenants/{id}/ativo`)
- Confirmado que `TenantAdminSummaryResponse` (6 campos, 0 sensíveis) é a única projeção de `Tenant` servida tanto pela consola `/plataforma` como pelo relatório `/plataforma/relatorio` — ambos consomem o mesmo `GET /platform/tenants`
- Confirmado que `use-platform-admin.ts` é o único ficheiro do frontend com chamadas HTTP reais a este domínio (5 `apiFetch`, 0 fora de `/platform/tenants*`), e que ambos os guards de página (`/plataforma`, `/plataforma/relatorio`) avaliam `!me.isFetched` antes da verificação de papel (WR-03)
- Nomeados explicitamente, com mecanismo próprio, os 2 únicos caminhos backend que tocam dados de `Tenant` sem gate de papel: `GET /api/v1/auth/me` (own-tenant-only, via `UserPrincipal.getTenantId()` do JWT) e `POST /api/v1/setup/initialize` (gate singleton — `isInitialized()` + `findByIdForUpdate`, contrastado com o `provisionTenant` repetível gated a `PLATAFORMA_ADMIN`)
- Descobertas e documentadas 2 divergências de ambiente novas (MSYS2 mangling de padrões `grep` com barra inicial; `|` sem `-E` tratado como literal neste GNU grep 3.0) que, se ignoradas, teriam produzido leituras falso-negativas de comandos literalmente escritos no próprio plano

## Task Commits

1. **Task 1: Re-derivar por execução todos os caminhos de exposição de dados de Tenant** — sem commit (zero ficheiros alterados por desenho; conclusões documentadas na Task 2). Verificado `git status --porcelain -- backend web` vazio no fim da task.
2. **Task 2: Criar 123-ISOL-AUDIT.md com o veredito por superfície do Critério de Sucesso 1** - `32a4151f` (docs)

**Plan metadata:** (commit seguinte, ver `git log`)

## Files Created/Modified

- `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md` - Auditoria de isolamento, Critério de Sucesso 1: tabela de 14 linhas de veredito, secção de comandos de reprodução, secção de divergências de ambiente

## Decisions Made

- `requirements mark-complete` deliberadamente NÃO executado para `ISOL-04` (ver key-decisions no frontmatter) — mesmo precedente de Phase 120/122 para requisitos que só fecham num plano posterior da mesma fase
- Task 1 não gerou commit próprio — é uma task de auditoria pura (leitura + execução de comando), sem ficheiros a alterar; a Task 2 é quem materializa as suas conclusões em `123-ISOL-AUDIT.md`
- As 2 divergências de ambiente encontradas foram corrigidas e revalidadas inline (não bloquearam nenhum veredito), e documentadas com o mesmo nível de transparência que `121-ISOL-AUDIT.md` já estabeleceu para colisões de comentário — mas classificadas como uma 3ª categoria distinta (quirk de ambiente/ferramenta), não forçadas nas 2 categorias existentes

## Deviations from Plan

None - plan executado exatamente como escrito. As 2 divergências de ambiente descobertas (ver acima) são achados *dentro* do âmbito da própria task de auditoria (re-derivar por execução, não confiar cegamente no texto do comando) — não são desvios do plano, são precisamente o que a Task 1 pediu para se fazer.

## Issues Encountered

- `Glob` (ripgrep) expirou por timeout (20s) ao procurar os 3 ficheiros de teste em todo o repositório e depois mesmo restrito a `backend/src/test/**` — contornado com `ls` direto nas subpastas de `backend/src/test/java/com/lexcv/` para localizar `PlatformAdminControllerTest.java`, `SetupControllerSingletonRegressaoTest.java` e `SetupServiceProvisionTenantTest.java`, todos confirmados presentes. Não bloqueou nenhuma verificação — apenas exigiu um caminho de navegação alternativo.
- 2 quirks de ambiente do Git Bash/GNU grep 3.0 (MSYS2 path-mangling; `|` sem `-E` tratado como literal) fizeram 2 comandos do próprio plano devolverem `0` falsos na primeira execução — investigados até à causa raiz, corrigidos, revalidados, e documentados na secção "Divergências" de `123-ISOL-AUDIT.md` para fases futuras não repetirem o mesmo engano.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `123-ISOL-AUDIT.md` está estruturado precisamente para o Plano 02 acrescentar a seguir: Critério de Sucesso 2 (bloqueio de `PUT /api/v1/admin/rbac`), a decisão sobre o Pitfall 1 (`findByXxxId` sem `tenantId`), e o veredito final da fase — nada da secção do Critério 1 escrita por este plano precisa de ser reescrito.
- `ISOL-04` permanece deliberadamente por fechar (ver Decisions Made) até o Plano 02 completar o veredito final.
- Nenhum bloqueio conhecido para o Plano 02 — todas as evidências de que precisa (formato, precedentes, ficheiros a citar) já estão confirmadas e acessíveis.

---
*Phase: 123-auditoria-de-isolamento-dedicada*
*Completed: 2026-07-30*

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md`
- FOUND: `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-01-SUMMARY.md`
- FOUND: commit `32a4151f` in `git log --oneline --all`
