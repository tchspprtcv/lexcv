---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 01
subsystem: database
tags: [jpa, spring-boot, postgresql, rbac, seed, migration]

# Dependency graph
requires:
  - phase: 124-catalogo-de-permissoes-em-base-de-dados
    provides: "idioma de coluna-bandeira com default seguro (Permission.reservadaPlataforma) e precedente de exclusao ao nivel de SQL (findAllByReservadaPlataformaFalse)"
provides:
  - "Role.instanciavel (Boolean, default fechado false) marcando quais papeis globais sao moldes instanciaveis"
  - "RoleRepository.findAllByInstanciavelTrue() como unica porta de leitura de moldes"
  - "Entidade TenantRole (t_tenant_role) + TenantRoleRepository, unica por (tenant_id, nome), molde_id como coluna historica nao navegavel"
  - "DatabaseSeeder convergindo instanciavel nos 5 papeis globais em cada arranque (4 moldes + PLATAFORMA_ADMIN nao-molde)"
  - "backend/migrations/126-add-tenant-role-tables.sql catalogado no README"
affects: [126-migracao-de-tenants-existentes, 127-crud-e-leitura-de-moldes, consola-plataforma-moldes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Coluna-bandeira com default fechado + @Builder.Default (mesma forma de Permission.reservadaPlataforma, Phase 124)"
    - "Query derivada de exclusao ao nivel de SQL (findAllByInstanciavelTrue, espelhando findAllByReservadaPlataformaFalse)"
    - "Proveniencia historica como coluna Integer crua, nunca associacao JPA navegavel (moldeId)"
    - "Upsert convergente no seeder (terceiro argumento booleano tolerando null) -- mesmo padrao de convergencia da Phase 124"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/TenantRole.java
    - backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederInstanciabilidadeMoldesTest.java
    - backend/migrations/126-add-tenant-role-tables.sql
  modified:
    - backend/src/main/java/com/lexcv/models/Role.java
    - backend/src/main/java/com/lexcv/repositories/RoleRepository.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
    - backend/migrations/README.md

key-decisions:
  - "moldeId em TenantRole e um Integer cru, nunca @ManyToOne/@OneToOne/cascade -- snapshot, nao referencia viva (D: 125-CONTEXT.md)"
  - "Default de Role.instanciavel e false (falha fechada); DatabaseSeeder.seedRbac() e o unico ponto que declara true/false, convergindo em cada arranque"
  - "Migracao numerada 126, nao 125 (125 ja ocupado por script anterior nao relacionado); sequencia numerica estritamente ascendente preservada no README"
  - "Nenhum ficheiro de resolucao de autoridade tocado (JwtAuthenticationFilter, UserPrincipal, User, AdminController) -- as linhas nascem dormentes"

patterns-established:
  - "Terceiro argumento booleano em upsertRolePermissions convergindo uma marca por papel em cada arranque, tolerando coluna recem-criada (getInstanciavel() == null)"

requirements-completed: [MOLD-01, MOLD-03]

# Metrics
duration: 12min
completed: 2026-09-20
---

# Phase 125 Plan 01: Moldes da Plataforma e Provisionamento - Esquema de Dados Summary

**Role ganha coluna instanciavel com default fechado, TenantRole/TenantRoleRepository nascem com proveniencia historica nao-navegavel, e DatabaseSeeder converge a marca de molde nos 5 papeis globais em cada arranque.**

## Performance

- **Duration:** ~12 min (commits entre 22:25:53 e 22:34:06 UTC-1, 2026-09-20)
- **Started:** 2026-09-20T22:22:00-01:00 (aprox., inicio de leitura do plano)
- **Completed:** 2026-09-20T22:34:06-01:00
- **Tasks:** 3/3
- **Files modified:** 8

## Accomplishments
- `Role.instanciavel` (Boolean, `columnDefinition = "boolean not null default false"` + `@Builder.Default`) declara explicitamente quais papeis globais sao moldes; `RoleRepository.findAllByInstanciavelTrue()` fecha a leitura ao nivel de SQL.
- `TenantRole` (t_tenant_role) e `TenantRoleRepository` criados com unicidade `(tenant_id, nome)`, `moldeId` como `Integer` cru sem qualquer associacao JPA navegavel, e `countByMoldeId` para o aviso de nao-propagacao da futura consola de moldes.
- `DatabaseSeeder.upsertRolePermissions` ganha um terceiro argumento booleano que converge `instanciavel` em cada arranque (4 papeis de escritorio `true`, `PLATAFORMA_ADMIN` `false`), provado por 5 casos Mockito em `DatabaseSeederInstanciabilidadeMoldesTest`.
- `backend/migrations/126-add-tenant-role-tables.sql` criado (numerado 126, nao 125, para nao colidir com o script ja existente), idempotente, sem FK/constraint de `molde_id` para `t_role` e sem backfill; `README.md` actualizado nas 4 localizacoes exigidas.

## Task Commits

Each task was committed atomically:

1. **Task 1: Marca de instanciabilidade em Role e entidade/repositorio do papel de escritorio** - `469d92a9` (feat)
2. **Task 2: DatabaseSeeder declara quais papeis sao moldes, convergindo em cada arranque** - `4ed83aca` (feat)
3. **Task 3: Script de migracao 126 e inventario do README** - `9d6dad06` (chore)

**Plan metadata:** (pendente -- ver nota abaixo; orquestrador nao pediu commit de metadata separado nesta execucao, apenas SUMMARY.md)

_Note: nenhuma task usou TDD; todos os commits sao atomicos por task, sem RED/GREEN separados._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Role.java` - coluna `instanciavel` com default fechado e doc-comment explicando a escolha
- `backend/src/main/java/com/lexcv/models/TenantRole.java` (novo) - entidade do papel de escritorio, unica por `(tenant_id, nome)`, `moldeId` historico nao navegavel
- `backend/src/main/java/com/lexcv/repositories/RoleRepository.java` - `findAllByInstanciavelTrue()`
- `backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java` (novo) - `findByTenantId`, `findByTenantIdAndNome`, `countByMoldeId`
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - `upsertRolePermissions` com terceiro argumento `instanciavel`, convergindo em cada arranque; 5 chamadores actualizados
- `backend/src/test/java/com/lexcv/seed/DatabaseSeederInstanciabilidadeMoldesTest.java` (novo) - 5 casos Mockito
- `backend/migrations/126-add-tenant-role-tables.sql` (novo) - script manual idempotente
- `backend/migrations/README.md` - Path A, Path B (linha 15), re-run safety (5 de 15), known execution status (7 pendentes)

## Decisions Made
- `moldeId` e um `Integer` cru sem `@ManyToOne`/cascade/FK -- decisao bloqueada em `125-CONTEXT.md` ("Snapshot, not live reference"), verificada por grep a `0` ocorrencias de `@ManyToOne|@OneToOne|cascade` no ficheiro.
- Default de `instanciavel` e `false` tanto na coluna SQL (`columnDefinition`) como no campo Java (`@Builder.Default`); `PLATAFORMA_ADMIN` e o caso concreto que este default protege, reforçado por chamada explicita `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList(), false)`.
- Migracao numerada `126` (nao `125`, ja ocupado); confirmado com `ls backend/migrations` antes de criar o ficheiro -- nenhum numero duplicado.
- Header do script de migracao evita as strings literais "FOREIGN KEY" e "IF NOT EXISTS" em prosa fora das instrucoes SQL reais, para que os gates de grep da Task 3 (que contam ocorrencias exactas nessas frases) meçam apenas as instrucoes, nao a documentacao.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Ajuste do teste estrutural "seeder nao interage com TenantRole"**
- **Found during:** Task 2 (escrita de `DatabaseSeederInstanciabilidadeMoldesTest`)
- **Issue:** A primeira versao do 5º caso usava `mockingDetails(seeder)`, mas `seeder` e uma instancia real criada por `@InjectMocks` (nao um mock) -- `mockingDetails` teria lancado `NotAMockException` em runtime.
- **Fix:** Substituido por uma verificacao estrutural directa via reflexao (`DatabaseSeeder.class.getDeclaredFields()`), confirmando que nenhum campo declarado menciona `TenantRole` no seu tipo -- prova o mesmo facto (nenhum colaborador de papeis de escritorio) sem depender de Mockito sobre um objecto que nao e mock.
- **Files modified:** backend/src/test/java/com/lexcv/seed/DatabaseSeederInstanciabilidadeMoldesTest.java
- **Verification:** `mvn test -Dtest=DatabaseSeederInstanciabilidadeMoldesTest` -- 5/5 verde.
- **Committed in:** `4ed83aca` (parte do commit da Task 2)

**2. [Rule 3 - Blocking] Reformulacao do header do script 126 para nao colidir com os gates de grep**
- **Found during:** Task 3 (escrita de `126-add-tenant-role-tables.sql`)
- **Issue:** A primeira versao do header usava a frase literal "FOREIGN KEY" (para explicar a ausencia de FK) e "IF NOT EXISTS" em prosa (para explicar a idempotencia) -- ambas contadas pelos gates `grep -cE 'FOREIGN KEY|REFERENCES t_role'` (esperado `0`) e `grep -c 'IF NOT EXISTS'` (esperado exactamente `4`), inflacionando as contagens para `1` e `5` respectivamente.
- **Fix:** Reescrita a prosa para "no constraint tying ... back to t_role" e "every statement below guards its own creation", preservando o significado sem usar as strings literais que os gates contam.
- **Files modified:** backend/migrations/126-add-tenant-role-tables.sql
- **Verification:** `grep -cE 'FOREIGN KEY|REFERENCES t_role' ...` = `0`; `grep -c 'IF NOT EXISTS' ...` = `4`.
- **Committed in:** `9d6dad06` (parte do commit da Task 3)

---

**Total deviations:** 2 auto-fixed (1 bug de teste, 1 ajuste de redacao para satisfazer gate de verificacao)
**Impact on plan:** Nenhum impacto no escopo ou nas garantias da fase -- ambos os ajustes sao correcoes mecanicas para que o codigo/teste faca exactamente o que o plano exige, sem alterar comportamento nem decisao de arquitectura.

## Issues Encountered

- O grep de ordem sugerido no plano para confirmar que a linha `126` vem depois da linha `125` na tabela do README (`grep -n '125-convert-tenant-logo-data-url-to-text.sql | Converts'`) nao produz correspondencia literal, porque a celula da tabela fecha com um crase (`` ` ``) imediatamente antes do separador `|` (ex.: `` `125-....sql` | Converts ``), e o padrao do plano nao inclui essa crase. Confirmado manualmente por numero de linha em vez disso: a linha `14` (125) esta em `backend/migrations/README.md:157` e a linha `15` (126) esta em `backend/migrations/README.md:158` -- ordem ascendente correcta. Nao alterei o texto da celula existente da linha 125 para nao tocar em conteudo fora do escopo desta fase.

## User Setup Required

`backend/migrations/126-add-tenant-role-tables.sql` e um script de execucao manual pendente para producao (nao ha migration runner neste repositorio) -- a juntar aos ja pendentes `74`, `117`, `120`, `124` (ver `backend/migrations/README.md`, seccao "Known execution status": agora **7 scripts pendentes** no total, incluindo `120b` parcialmente aplicado e `125` de estado indeterminado por base de dados).

Nenhuma variavel de ambiente nova, nenhum servico externo, nenhuma dependencia nova (`git diff --stat backend/pom.xml web/package.json` vazio).

## Next Phase Readiness

- O esquema de dados que a Phase 126 (migracao de tenants existentes) e a Phase 127 (CRUD/leitura de moldes) precisam ja existe: `Role.instanciavel`, `t_tenant_role`, `t_tenant_role_permission`, `TenantRoleRepository.countByMoldeId`.
- Nada nesta fase altera resolucao de autoridade -- `git diff --name-only` confirmado sem `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `User.java` nem `AdminController.java` em nenhum dos 3 commits.
- As linhas de `t_tenant_role` continuam inexistentes ate `SetupService.provisionTenant` (proximo plano da fase) comecar a instanciar moldes -- nenhum bloqueador conhecido para esse trabalho.
- Suite completa de testes verde (203/203) e SpotBugs sem novos achados, confirmando ausencia de regressao.

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-20*

## Self-Check: PASSED

Todos os 8 ficheiros declarados em `files_modified` foram confirmados no disco (`[ -f ... ]`), e os 3 hashes de commit (`469d92a9`, `4ed83aca`, `9d6dad06`) foram confirmados em `git log --oneline --all`. Nenhum item em falta.
