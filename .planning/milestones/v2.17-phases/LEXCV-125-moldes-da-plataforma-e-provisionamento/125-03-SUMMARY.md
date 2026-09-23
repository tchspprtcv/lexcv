---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 03
subsystem: api
tags: [spring-boot, rbac, mockito, spring-security, multi-tenant, rest-api]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 01)
    provides: "Role.instanciavel, RoleRepository.findAllByInstanciavelTrue(), TenantRole/TenantRoleRepository.countByMoldeId"
  - phase: 124-catalogo-de-permissoes-em-base-de-dados
    provides: "Permission.rotulo/descricao/modulo/ordem/reservadaPlataforma, PermissionRepository.findAllByReservadaPlataformaFalse()"
provides:
  - "GET/PUT/POST /api/v1/platform/moldes -- os 3 handlers que a consola /plataforma/moldes consome"
  - "4 DTOs novos (MoldesConsolaResponse, MoldesUpdateRequest, MoldeCreateRequest, MoldeProvisionResponse), independentes do contrato de GET /admin/rbac"
  - "Prova comportamental completa dos 3 handlers e prova por proxy AOP real de que um ADMIN de escritorio e recusado nos 3, com demonstracao de RED genuino do gate de classe"
affects: [127-crud-e-leitura-de-moldes, consola-plataforma-moldes-frontend]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Payload unico de consola (catalogo + lista) sob um so gate de classe, DTO independente do contrato de outra superficie com gate mais largo (GET /admin/rbac)"
    - "Validate-then-write num handler PUT em lote: todas as entradas resolvidas e validadas antes de qualquer save"
    - "Resolucao de chaves de permissao exclusivamente por leitura filtrada (findAllByReservadaPlataformaFalse), nunca por findById sobre id cru"
    - "Projeccao partilhada entre GET e PUT via metodo privado (montarConsolaResponse), reaproveitando os mesmos helpers de mapeamento"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/MoldesConsolaResponse.java
    - backend/src/main/java/com/lexcv/dtos/MoldesUpdateRequest.java
    - backend/src/main/java/com/lexcv/dtos/MoldeCreateRequest.java
    - backend/src/main/java/com/lexcv/dtos/MoldeProvisionResponse.java
    - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerMoldesTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
    - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java

key-decisions:
  - "MoldesConsolaResponse.PermissaoDto e uma classe propria, nao reutiliza a definicao de par chave/rotulo do DTO de GET /admin/rbac -- gates diferentes (PLATAFORMA_ADMIN exclusivo vs. ADMIN-ou-PLATAFORMA_ADMIN), acoplamento evitado de propósito antes da Phase 127"
  - "Campo escritoriosInstanciados (nao tenantsInstanciados) -- UI-SPEC prevalece sobre a sugestao de 125-PATTERNS.md"
  - "PUT /platform/moldes e em lote, sem {id} na rota -- decisao de UI (um so AlertDialog, um so botao Confirmar e Gravar)"
  - "updateMoldes nunca le nem escreve tenantRoleRepository excepto a contagem de leitura em toMoldeDto -- MOLD-03 e uma decisao de snapshot, nao afirmada, provada por verify(never())"
  - "Nenhuma permissao do catalogo foi marcada como reservada nesta fase -- a divergencia rolePermissions/JWT herdada da Phase 124 (WR-03) continua inerte"

patterns-established:
  - "PAPEL_RESERVADO = PLATAFORMA_ADMIN como terceira camada de defesa em profundidade (filtro SQL de findAllByInstanciavelTrue + exclusao defensiva na listagem + guarda literal-name no PUT e no POST)"

requirements-completed: [MOLD-02, MOLD-03, MOLD-04]

# Metrics
duration: 21min
completed: 2026-09-20
---

# Phase 125 Plan 03: Moldes da Plataforma e Provisionamento - CRUD de Moldes Summary

**GET/PUT/POST /api/v1/platform/moldes expostos em PlatformAdminController sob o gate de classe herdado, com 4 DTOs novos independentes do contrato de GET /admin/rbac, e prova por proxy AOP real (com demonstracao de RED genuino) de que um ADMIN de escritorio nunca alcanca nenhum dos 3 handlers.**

## Performance

- **Duration:** ~21 min (commits entre 23:00:32 e 23:13:41 UTC-1, 2026-09-20)
- **Started:** 2026-09-20T22:52:50-01:00 (aprox., inicio de leitura do plano, logo apos o commit de metadata do Plan 02)
- **Completed:** 2026-09-20T23:13:41-01:00
- **Tasks:** 3/3
- **Files modified:** 7

## Accomplishments
- `MoldesConsolaResponse` (payload unico de `GET /moldes`, com `PermissaoDto` independente do DTO de `GET /admin/rbac` e `MoldeDto.escritoriosInstanciados`), `MoldesUpdateRequest` (lote), `MoldeCreateRequest` e `MoldeProvisionResponse` criados em `backend/src/main/java/com/lexcv/dtos/`.
- `GET /api/v1/platform/moldes` devolve, num so pedido, o catalogo filtrado (`findAllByReservadaPlataformaFalse` + exclusao de rotulo em branco, ordenado por `ordem` com nulos no fim) e a lista de moldes (`findAllByInstanciavelTrue`, exclusao defensiva de `PLATAFORMA_ADMIN`) com `escritoriosInstanciados` calculado ao vivo via `countByMoldeId`.
- `PUT /api/v1/platform/moldes` grava em lote com validate-then-write completo (id/existencia/instanciavel/`PLATAFORMA_ADMIN`/permissoes desconhecidas) antes de qualquer `save`; nunca le nem escreve `tenantRoleRepository` excepto a contagem de leitura reutilizada na reprojeccao da resposta.
- `POST /api/v1/platform/moldes` cria um molde `instanciavel = true` com nome normalizado para maiusculas, recusando `PLATAFORMA_ADMIN` (em qualquer caixa) com a mensagem exacta do UI-SPEC e nome duplicado (pre-check + `catch (DataIntegrityViolationException)` para a corrida concorrente).
- `PlatformAdminControllerMoldesTest` (novo, 23 casos) prova o comportamento dos 3 handlers (Grupo A) e, por proxy AOP real (`AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory`), que um ADMIN de escritorio e um contexto sem autenticacao sao recusados nos 3 handlers novos, com demonstracao de RED genuino documentada abaixo. `PlatformAdminControllerTest` existente (27 casos) actualizado apenas no `novoController()` para os 3 colaboradores novos -- nenhum caso apagado ou reescrito.

## Task Commits

Each task was committed atomically:

1. **Task 1: DTOs da consola de moldes e GET /platform/moldes** - `224fe991` (feat)
2. **Task 2: PUT e POST /platform/moldes com as guardas do papel reservado** - `86521423` (feat)
3. **Task 3: Testes dos 3 handlers, incluindo recusa de ADMIN de escritorio por proxy AOP real** - `2150d4af` (test)

**Plan metadata:** (pendente -- orquestrador nao pediu commit de metadata separado nesta execucao, apenas SUMMARY.md, conforme instrucao recebida)

_Note: nenhuma task usou TDD com gates RED/GREEN/REFACTOR formais no plano; a Task 3 incluiu, no entanto, a demonstracao de RED genuino especifica para o gate de classe exigida pelo criterio de aceitacao, realizada e revertida antes do commit (ver seccao dedicada abaixo)._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/dtos/MoldesConsolaResponse.java` (novo) - payload unico de `GET /moldes`, `PermissaoDto`/`MoldeDto` aninhados, independente de `RbacResponse`
- `backend/src/main/java/com/lexcv/dtos/MoldesUpdateRequest.java` (novo) - corpo em lote de `PUT /moldes`
- `backend/src/main/java/com/lexcv/dtos/MoldeCreateRequest.java` (novo) - corpo de `POST /moldes`
- `backend/src/main/java/com/lexcv/dtos/MoldeProvisionResponse.java` (novo) - projeccao minima de 201
- `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` - 3 colaboradores novos (`RoleRepository`, `PermissionRepository`, `TenantRoleRepository`), constante `PAPEL_RESERVADO`, handlers `listMoldes`/`updateMoldes`/`createMolde`, helper partilhado `montarConsolaResponse`, helpers `toPermissaoDto`/`toMoldeDto`
- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` - `novoController()` actualizado com os 3 colaboradores novos, `@Mock` acrescentados, nenhum caso existente alterado
- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerMoldesTest.java` (novo) - 23 casos Mockito, Grupo A (comportamento) + Grupo B (proxy AOP)

## Decisions Made
- `MoldesConsolaResponse.PermissaoDto` e uma classe propria, deliberadamente nao acoplada ao DTO de resposta de `AdminController.getRbac()` -- gates diferentes (`PLATAFORMA_ADMIN` exclusivo vs. `ADMIN` ou `PLATAFORMA_ADMIN`), documentado no doc-comment de classe sem usar a string literal `RbacResponse` (ver Deviations).
- `escritoriosInstanciados`, nao `tenantsInstanciados` -- o UI-SPEC (secção 5, contrato de dados) prevalece sobre a sugestão de `125-PATTERNS.md` onde os dois documentos divergem.
- Nenhuma permissão do catálogo foi marcada como `reservadaPlataforma = true` nesta fase -- a divergência `rolePermissions`/JWT herdada da Phase 124 (achado WR-03) continua inerte, exactamente como o CONTEXT.md exigia.
- `updateMoldes` resolve as chaves de permissão exclusivamente pela mesma leitura filtrada de `listMoldes` (`findAllByReservadaPlataformaFalse`), nunca por `findById` sobre um id cru -- uma permissão reservada à plataforma não pode entrar num molde por via de um corpo de pedido (T-125-20).

## Demonstração de RED genuíno (gate de classe cross-tenant, T-125-17)

Por instrução do plano (Task 3, critério de aceitação), comentei temporariamente a anotação de classe em `PlatformAdminController.java`:
```java
@RestController
@RequestMapping("/api/v1/platform")
// @PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
```
e corri `mvn test -Dtest=PlatformAdminControllerMoldesTest`.

**Saída FALHA (RED), com o gate de classe ausente — 6 de 23 casos falham, exactamente os 6 casos de recusa do Grupo B (3 handlers × 2 cenários: ADMIN de escritório e contexto sem autenticação):**
```
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.listMoldes_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios -- FAILURE!
org.opentest4j.AssertionFailedError: Expected org.springframework.security.access.AccessDeniedException to be thrown, but nothing was thrown.
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.updateMoldes_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios -- FAILURE!
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.createMolde_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios -- FAILURE!
org.opentest4j.AssertionFailedError: Unexpected exception type thrown, expected: <AccessDeniedException> but was: <java.lang.NullPointerException>
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.listMoldes_semAutenticacaoNenhumaERecusado -- FAILURE!
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.updateMoldes_semAutenticacaoNenhumaERecusado -- FAILURE!
[ERROR] com.lexcv.controllers.PlatformAdminControllerMoldesTest.createMolde_semAutenticacaoNenhumaERecusado -- FAILURE!
[INFO] Tests run: 23, Failures: 6, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```
`createMolde` sem gate lançou `NullPointerException` em vez das exceções de segurança esperadas — o método executou até ao fim (mock de `roleRepository.save` não estava stubado para este cenário, devolvendo `null`), prova adicional (não planeada, mas reveladora) de que sem o gate o handler alcança mesmo a lógica de negócio.

Restaurada a anotação. **Saída PASSA (GREEN), com o gate de classe presente:**
```
[INFO] Running com.lexcv.controllers.PlatformAdminControllerMoldesTest
[INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.883 s
[INFO] BUILD SUCCESS
```
`git diff --stat backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` confirmado sem resíduo da alteração temporária antes do commit da Task 3 (o único diff remanescente era a limpeza de import `HashMap` → `LinkedHashMap`, intencional — ver Deviations).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Doc-comment de `MoldesConsolaResponse` mencionava `RbacResponse` literalmente, contradizendo o próprio gate de aceitação da Task 1**
- **Found during:** Task 1 (verificação dos critérios de aceitação por grep)
- **Issue:** O plano pedia, no mesmo parágrafo, duas coisas em tensão: (a) documentar num doc-comment de classe a decisão de não reutilizar `RbacResponse.PermissionDefDto`, citando-a pelo nome; (b) um critério de aceitação `grep -c 'RbacResponse' ... devolve 0` ("DTO independente"). A primeira versão do doc-comment usava `{@link RbacResponse.PermissionDefDto}`, o que fazia o grep devolver `1`, não `0`.
- **Fix:** Reescrita a prosa para descrever a mesma decisão sem o nome literal da classe ("o DTO de resposta de `AdminController#getRbac()`"), preservando o significado e a rastreabilidade (via `AdminController#getRbac()`, que é único) sem o texto que o gate conta.
- **Files modified:** backend/src/main/java/com/lexcv/dtos/MoldesConsolaResponse.java
- **Verification:** `grep -c 'RbacResponse' MoldesConsolaResponse.java` = `0`; suite de testes do controller continuou verde (27/27).
- **Committed in:** `224fe991` (parte do commit da Task 1)

**2. [Rule 1 - Bug] `autenticarComoRoles("PLATAFORMA_ADMIN")` sem o prefixo `ROLE_` nos testes "atravessa o gate"**
- **Found during:** Task 3 (primeira corrida de `PlatformAdminControllerMoldesTest`)
- **Issue:** `hasRole('PLATAFORMA_ADMIN')` exige a autoridade prefixada `ROLE_PLATAFORMA_ADMIN`; o helper `autenticarComoRoles` (copiado do ficheiro de teste existente) não acrescenta esse prefixo — ao contrário de `UserPrincipal.create` para roles reais. As 3 primeiras versões dos testes "PlataformaAdmin atravessa o gate" passavam `"PLATAFORMA_ADMIN"` sem prefixo, o que fazia `hasRole(...)` avaliar `false` e lançar `AuthorizationDeniedException` em vez de deixar o pedido passar.
- **Fix:** Alterado para `autenticarComoRoles("ROLE_PLATAFORMA_ADMIN")` nos 3 testes, com comentário a explicar a diferença de prefixagem.
- **Files modified:** backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerMoldesTest.java
- **Verification:** `mvn test -Dtest=PlatformAdminControllerMoldesTest` — os 3 casos passaram a verde.
- **Committed in:** `2150d4af` (parte do commit da Task 3)

**3. [Rule 1 - Bug] Tipo de excepção errado nos testes "sem autenticação nenhuma"**
- **Found during:** Task 3 (mesma corrida)
- **Issue:** Os 3 testes que autenticam um contexto vazio esperavam `AccessDeniedException`, mas um `SecurityContextHolder` sem nenhum `Authentication` faz `AuthorizationManagerBeforeMethodInterceptor` lançar `AuthenticationCredentialsNotFoundException` — tipo irmão, não subtipo, de `AccessDeniedException` (ambos estendem `SpringSecurityException` directamente).
- **Fix:** Alterada a asserção para `AuthenticationCredentialsNotFoundException`, com comentário a documentar a distinção entre os dois tipos de recusa (nenhuma credencial presente vs. credencial presente mas insuficiente).
- **Files modified:** backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerMoldesTest.java
- **Verification:** `mvn test -Dtest=PlatformAdminControllerMoldesTest` — 23/23 verde.
- **Committed in:** `2150d4af` (parte do commit da Task 3)

**4. [Rule 1 - Bug] Import `java.util.HashMap` não usado em `PlatformAdminController`**
- **Found during:** Task 2 (revisão antes de escrever os testes da Task 3)
- **Issue:** `HashMap` foi importado ao escrever `updateMoldes`, mas o mapa de resolução acabou implementado com `LinkedHashMap` (referenciado com o nome totalmente qualificado `java.util.LinkedHashMap` para evitar duplicar o import), deixando `HashMap` sem uso.
- **Fix:** Substituído o import por `java.util.LinkedHashMap` e a referência totalmente qualificada pelo nome simples.
- **Files modified:** backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
- **Verification:** `mvn -DskipTests compile` sem avisos; SpotBugs 0 achados.
- **Committed in:** `2150d4af` (parte do commit da Task 3, por ter sido descoberto nessa fase da execução)

---

**Total deviations:** 4 auto-fixed (2 bugs de teste descobertos ao correr a suite, 1 contradição textual entre a acção e o critério de aceitação do próprio plano, 1 limpeza de import)
**Impact on plan:** Nenhum impacto no escopo ou nas garantias da fase — todos os ajustes são correcções mecânicas para que o código/teste faça exactamente o que o plano exige, sem alterar nenhuma decisão de arquitectura nem nenhuma guarda de segurança.

## Issues Encountered

- O plano pedia, no mesmo parágrafo da Task 1, documentar a não-reutilização de `RbacResponse` citando-a pelo nome E um gate de aceitação que conta ocorrências literais dessa string esperando `0`. Resolvido descrevendo a decisão sem o nome literal da classe (ver Deviation 1) — o significado documentado é idêntico, só a forma de referência mudou.

## User Setup Required

None — nenhuma variável de ambiente nova, nenhum serviço externo, nenhuma dependência nova (`git diff --stat backend/pom.xml` vazio, confirmado).

## Next Phase Readiness

- Os 3 handlers que a consola `/plataforma/moldes` (frontend, fora do âmbito desta plan) precisa já existem e estão provados: `GET`/`PUT`/`POST /api/v1/platform/moldes`.
- `escritoriosInstanciados` é o nome de campo real no payload — os planos de frontend que ainda não foram escritos devem usar este nome, não `tenantsInstanciados`.
- `PUT /admin/rbac` permanece inalterado (`git diff --name-only backend/src/main/java/com/lexcv/controllers/AdminController.java` vazio confirmado nos 3 commits) — CATL-04 e `rbac:manage` continuam reservados à Phase 127.
- Nenhuma permissão do catálogo foi marcada como `reservadaPlataforma = true` — a divergência `rolePermissions`/JWT (WR-03, Phase 124) continua inerte; uma fase futura que decida marcar alguma permissão como reservada terá de revisitar esse comentário em `AdminController.getRbac()`.
- Suite completa de testes verde (232/232, acima dos 209 herdados do Plan 02) e SpotBugs sem novos achados, confirmando ausência de regressão.

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-20*

## Self-Check: PASSED

Todos os 7 ficheiros declarados em `files_modified` foram confirmados no disco (`[ -f ... ]`), e os 3 hashes de commit (`224fe991`, `86521423`, `2150d4af`) foram confirmados em `git log --oneline --all`. Nenhum item em falta.
