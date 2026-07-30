---
phase: 123-auditoria-de-isolamento-dedicada
plan: 02
subsystem: api
tags: [tenant-isolation, security-audit, rbac, multi-tenancy, audit-closure]

# Dependency graph
requires:
  - phase: 123-01
    provides: "123-ISOL-AUDIT.md com o veredito do Critério de Sucesso 1 (consola de tenants + relatório de utilização, 14 linhas COVERED), formato de tabela reutilizado da AUD-01/121-ISOL-AUDIT.md"
provides:
  - "Veredito do Critério de Sucesso 2 (bloqueio de PUT /api/v1/admin/rbac sem via de contorno), anexado a 123-ISOL-AUDIT.md"
  - "Enumeração exaustiva (não busca dirigida) de toda a escrita HTTP-alcançável de Role/Permission em backend/src/main/java — AdminController.updateRbac confirmado como único call site"
  - "Handler morto web/src/app/_api-backup/v1/admin/rbac/route.ts traçado e descartado com 3 provas independentes de inalcançabilidade (manifesto de rotas, apiFetch/rewrite, dependências mock desconectadas)"
  - "AdminController.updateUser (user.setPermissions) dispositionado como override per-user own-tenant-only, distinto da escrita da matriz global"
  - "Decisão sobre o Pitfall 1 (findByXxxId sem tenantId) registada como risco residual conhecido e aceite, com as 4 razões e recomendação de fase futura dedicada"
  - "Veredito final nomeado para as 3 superfícies novas da v2.16 (consola Phase 120, relatório Phase 122, bloqueio RBAC Phase 121) + StorageService confirmado fora de âmbito por grep"
  - "Declaração explícita de pré-condição de go/no-go para provisionar um 2º tenant pagante real, mais delimitação de âmbito (O que esta auditoria NÃO cobriu)"
  - "ISOL-04 fechado em REQUIREMENTS.md (checklist + tabela de rastreabilidade) — marco v2.16 com 15/15 requisitos completos"
affects: [futura milestone de refactor de repositórios (Pitfall 1), provisionamento real de um 2º tenant pagante]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - .planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md
    - .planning/REQUIREMENTS.md

key-decisions:
  - "Pitfall 1 (findByXxxId sem tenantId, ~11 métodos de repositório) aceite como risco residual conhecido — não corrigido, não silenciosamente ignorado — com as 4 razões trancadas em 123-CONTEXT.md reproduzidas fielmente na auditoria"
  - "121-ISOL-AUDIT.md marcado como precedente desatualizado (não corrigido retroativamente) no ponto específico da assimetria GET/PUT de /admin/rbac, já que o CR-01 de 121-REVIEW.md alargou getRbac a PLATAFORMA_ADMIN depois desse ficheiro ter sido escrito"
  - "StorageService confirmado fora de âmbito por grep direto (tenantId no path do objeto MinIO), não copiado por fé do ROADMAP"
  - "UAT ao vivo não repetido nesta fase — evidência HTTP real das Fases 120/121/122 citada, com justificação explícita (cobertura de regressão automatizada permanente + precedente da AUD-01)"

requirements-completed: [ISOL-04]

# Metrics
duration: 24min
completed: 2026-07-30
---

# Phase 123 Plan 02: Auditoria de Isolamento Dedicada — Critério 2, Pitfall 1, Veredito Final Summary

**Prova executável de que `AdminController.updateRbac` é o único call site HTTP-alcançável que escreve as tabelas globais `Role`/`Permission` em toda a base de código, o handler morto `_api-backup` traçado e descartado com 3 provas independentes, o Pitfall 1 fechado como risco residual aceite, e ISOL-04 encerrado com o marco v2.16 em 15/15 requisitos.**

## Performance

- **Duration:** 24 min
- **Started:** 2026-07-30T11:27:24Z
- **Completed:** 2026-07-30T11:51:18Z
- **Tasks:** 3 completed
- **Files modified:** 2

## Accomplishments
- Enumeração exaustiva (não busca dirigida a `updateRbac`) de toda a escrita de `Role`/`Permission`: exatamente 3 ocorrências de `roleRepository.save` (2 em `DatabaseSeeder`, código de arranque inalcançável por HTTP; 1 em `AdminController.updateRbac`) e 2 ocorrências de `setPermissions` (`user.setPermissions` em `updateUser`, own-tenant-only; `role.setPermissions` em `updateRbac`, gated a `PLATAFORMA_ADMIN`) — zero ocorrências de qualquer escrita de `permissionRepository`
- Handler morto `web/src/app/_api-backup/v1/admin/rbac/route.ts` traçado e descartado com 3 provas independentes: manifesto de rotas construído (36 rotas, 0 de API), `apiFetch`/`next.config.ts` nunca alcançam rotas internas Next.js, e as dependências `mockDb`/`request-auth` desconectadas de qualquer base de dados real ou do mecanismo de auth real (cookies httpOnly vs. `Authorization: Bearer` simulado)
- Decisão sobre o Pitfall 1 (`findByXxxId` sem `tenantId`) fechada como risco residual conhecido e aceite, reproduzindo fielmente as 4 razões trancadas em `123-CONTEXT.md`
- Veredito final nomeado para as 3 superfícies novas da v2.16 (Phase 120, 121, 122) mais `StorageService` confirmado fora de âmbito por grep direto (não por fé)
- ISOL-04 fechado em `REQUIREMENTS.md` — marco v2.16 conclui com 15/15 requisitos completos

## Task Commits

Cada tarefa foi submetida atomicamente. A Task 1 foi uma auditoria por execução de comando e leitura direta que não escreveu nenhum ficheiro (as suas conclusões alimentam a Task 2) — por isso não gerou commit próprio, tal como o próprio plano especifica (`<files>(nenhum...)</files>`), e como já aconteceu na estrutura equivalente do Plano 01 desta fase.

1. **Task 1: Provar por execução que a escrita da matriz RBAC não tem via de contorno HTTP-alcançável** — sem commit próprio (zero ficheiros escritos; evidência incorporada no commit da Task 2)
2. **Task 2: Anexar o veredito do Critério de Sucesso 2, a decisão do Pitfall 1 e o veredito final por superfície** - `644fcc1a` (docs)
3. **Task 3: Fechar ISOL-04 e provar zero alterações de produção em toda a fase** - `2222cf37` (docs)

**Plan metadata:** (este commit, a seguir)

## Files Created/Modified
- `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md` - Apêndice de 180 linhas: veredito do Critério de Sucesso 2, achado traçado-e-descartado do handler morto, decisão do Pitfall 1, evidência ao vivo citada, veredito final por superfície, pré-condição de go/no-go (ficheiro final: 310 linhas; secções do Critério 1 do Plano 01 preservadas intactas)
- `.planning/REQUIREMENTS.md` - ISOL-04 marcado `[x]` na checklist e `Complete` na tabela de rastreabilidade, com referência ao ficheiro de auditoria (exatamente 2 linhas alteradas)

## Decisions Made

- **Pitfall 1 aceite como risco residual conhecido, não corrigido:** eliminar por completo o padrão `findByXxxId` sem `tenantId` exigiria alterar a assinatura de ~11 métodos de repositório e todos os seus call sites — refactor de grande risco, fora do âmbito desta milestone. A dupla verificação já existente em cada call site (confirmada em investigações anteriores desta sessão, zero indícios de omissão) é aceite como mitigação suficiente por agora, com recomendação explícita de uma fase dedicada futura.
- **`121-ISOL-AUDIT.md` marcado como precedente desatualizado, não corrigido retroativamente:** esse ficheiro documentou corretamente, no seu próprio momento, a assimetria `GET`/`PUT` de `/admin/rbac`. O CR-01 de `121-REVIEW.md` alargou `getRbac` a `PLATAFORMA_ADMIN` depois disso. Esta auditoria lê o estado atual do código e regista-o como fonte de verdade corrente, deixando o ficheiro antigo como registo histórico datado — não como erro a corrigir.
- **`StorageService` confirmado fora de âmbito por grep direto**, em vez de aceitar por fé a afirmação do `ROADMAP.md` — `tenantId` confirmado no path do objeto MinIO (`StorageService.java:39,42`).
- **UAT ao vivo não repetido:** evidência HTTP real já recolhida nas Fases 120/121/122 (403/200 em endpoints de plataforma e RBAC, tenant suspenso visível no relatório) citada em vez de reexecutada, com justificação explícita (cobertura de regressão automatizada permanente por 5 classes de teste + precedente direto da AUD-01, `97-01-SUMMARY.md`).

## Deviations from Plan

None - plan executed exactly as written. Todos os comandos de verificação (grep exaustivo, testes Mockito, `pnpm verify:bloqueio-rbac`, manifesto de rotas) devolveram exatamente os valores esperados no momento do planeamento, sem necessidade de nenhum fix automático (Regras 1-3) nem decisão arquitetural (Regra 4). Nenhuma divergência nova de ambiente foi encontrada — os 2 quirks de `grep`/MSYS2 já catalogados na secção do Critério 1 (Plano 01) foram evitados por desenho nesta fase (nenhum padrão com barra inicial, `-E` sempre presente onde havia alternância).

## Issues Encountered

None.

## Known Stubs

None — este plano produziu apenas documentação de auditoria (`.planning/`); nenhum código de aplicação ou componente de UI foi criado ou modificado.

## Threat Flags

None — toda a superfície de segurança tocada por este plano (escrita de `Role`/`Permission`, o handler morto de `_api-backup`, o override per-user de `updateUser`, o padrão do Pitfall 1, o precedente desatualizado, e o gate de legitimidade de pacotes) já está coberta pelo `<threat_model>` do próprio `123-02-PLAN.md` (T-123-07 a T-123-12, T-123-SC). Nenhuma superfície nova foi introduzida — este plano só modificou ficheiros de planeamento (`.planning/`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Marco v2.16 concluído: 15/15 requisitos completos.** ISOL-04 foi o último requisito pendente; a Fase 123 era a última fase do marco.
- `123-ISOL-AUDIT.md` está completo (310 linhas) com veredito nomeado para os 3 Critérios de Sucesso da Fase 123 e para as 3 superfícies novas da milestone — é a porta documentada que o utilizador atravessa para provisionar um 2º tenant pagante real.
- **Pré-condições de deployment que continuam a aplicar-se antes de um 2º tenant pagante real em produção** (detalhadas na secção "Pré-condição" de `123-ISOL-AUDIT.md`): as 3 migrações manuais pendentes (`117-add-tenant-plano-limite-utilizadores.sql`, `120-add-tenant-ativo.sql`, `120b-backfill-tenant-plano.sql` — esta última já confirmada como bloqueador funcional, não cosmético, se saltada) têm de correr em produção, e o deploy tem de partir do estado atual do repositório (que já inclui as Fases 120 e 121 em conjunto).
- Candidato de âmbito futuro, explicitamente não desta milestone: uma fase dedicada de refactor de repositórios para eliminar o padrão do Pitfall 1 (`findByXxxId` sem `tenantId` na própria query).
- Nenhum bloqueador para o fecho do marco v2.16.

---
*Phase: 123-auditoria-de-isolamento-dedicada*
*Completed: 2026-07-30*

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-02-SUMMARY.md`
- FOUND: `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md`
- FOUND: `.planning/REQUIREMENTS.md`
- FOUND commit: `644fcc1a` (Task 2)
- FOUND commit: `2222cf37` (Task 3)
