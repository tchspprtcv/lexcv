---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 02
subsystem: api
tags: [spring-boot, jpa, mockito, rbac, multi-tenant, provisioning]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 01)
    provides: "Role.instanciavel, RoleRepository.findAllByInstanciavelTrue(), TenantRole/TenantRoleRepository, seeder convergindo instanciabilidade"
provides:
  - "SetupService.provisionTenant instancia, dentro da sua transaccao existente, uma copia propria (TenantRole) de cada molde instanciavel para o tenant recem-criado"
  - "Prova Mockito de que a copia de permissoes e um snapshot (Set distinto), nunca uma referencia partilhada com o molde (MOLD-03)"
  - "Guarda de defesa em profundidade (NOME_PAPEL_PLATAFORMA) contra PLATAFORMA_ADMIN aparecer numa eventual regressao do filtro SQL de findAllByInstanciavelTrue()"
affects: [126-migracao-de-tenants-existentes, 127-crud-e-leitura-de-moldes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Metodo privado nao-transacional invocado de dentro de um metodo @Transactional do chamador, para partilhar a mesma transaccao (instanciarMoldes)"
    - "Guarda por nome literal de defesa em profundidade sobre um filtro SQL (mesmo idioma de TENANT_RESERVADO em PlatformAdminController)"

key-files:
  created:
    - backend/src/test/java/com/lexcv/services/SetupServiceInstanciacaoMoldesTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/SetupService.java
    - backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java

key-decisions:
  - "instanciarMoldes(UUID tenantId) e um metodo privado sem @Transactional proprio -- corre dentro da transaccao existente de provisionTenant; uma falha propaga e rebobina tenant+utilizador+papeis juntos"
  - "new HashSet<>(molde.getPermissions()) e uma copia, nunca a mesma instancia -- provado por assertNotSame + mutacao pos-provisionamento do Set de origem, com demonstracao de RED genuino (ver secao dedicada abaixo)"
  - "adminUser.roles continua Set.of(adminRole), o papel GLOBAL ADMIN -- nao trocado para o papel instanciado; assertSame contra a instancia devolvida por findByNome('ADMIN') e a rede de seguranca desta fronteira"
  - "Guarda de defesa em profundidade adicionada (Rule 2, nao estava no ficheiro original): SetupService.instanciarMoldes ignora explicitamente qualquer molde chamado PLATAFORMA_ADMIN, mesmo que findAllByInstanciavelTrue() o devolvesse por regressao futura do filtro SQL"

patterns-established:
  - "Bloco de comentario nas quatro invariantes do laço de instanciacao (transaccao partilhada, copia vs referencia, moldeId como proveniencia historica, papel global inalterado) documentando o porque de cada uma para refactors futuros"

requirements-completed: [MOLD-01, MOLD-03]

# Metrics
duration: 21min
completed: 2026-09-20
---

# Phase 125 Plan 02: Moldes da Plataforma e Provisionamento - Provisionamento Summary

**`SetupService.provisionTenant` instancia, dentro da sua transaccao existente, uma copia (`TenantRole`) de cada molde instanciavel com permissoes copiadas por `new HashSet<>()`, mantendo o administrador inicial no papel GLOBAL `ADMIN`.**

## Performance

- **Duration:** ~21 min (commits entre 22:43:45 e 22:51:03 UTC-1, 2026-09-20)
- **Started:** 2026-09-20T22:37:48-01:00 (aprox., inicio de leitura do plano)
- **Completed:** 2026-09-20T22:51:03-01:00
- **Tasks:** 2/2
- **Files modified:** 3 (1 criado, 2 modificados)

## Accomplishments
- `SetupService` ganha `TenantRoleRepository` como 6º e último colaborador (`@RequiredArgsConstructor`); `provisionTenant` invoca `instanciarMoldes(tenant.getId())` depois de gravar o `adminUser`, dentro da mesma `@Transactional`.
- `instanciarMoldes` lê exclusivamente `roleRepository.findAllByInstanciavelTrue()` (nunca `findAll()`), constrói um `TenantRole` por molde com `tenantId`, `moldeId` (proveniência histórica), `sistema = true` e `permissions = new HashSet<>(molde.getPermissions())` — cópia, nunca referência partilhada.
- Guarda de defesa em profundidade acrescentada (`NOME_PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN"`): mesmo que uma regressão futura do filtro SQL devolvesse `PLATAFORMA_ADMIN` na lista de moldes, `instanciarMoldes` ignora-o explicitamente por nome literal — o mesmo idioma de `TENANT_RESERVADO` em `PlatformAdminController`.
- `SetupServiceInstanciacaoMoldesTest` (novo, 6 casos Mockito) prova: 1 `TenantRole` por molde com campos correctos; a independência do snapshot (o caso mais importante, com demonstração de RED genuíno); ausência de moldes não rebenta o provisionamento; `PLATAFORMA_ADMIN` nunca instanciado (duas camadas); administrador inicial recebe a MESMA instância do papel GLOBAL `ADMIN` (`assertSame`); falha na gravação de um `TenantRole` propaga sem ser silenciada.
- `SetupServiceProvisionTenantTest` (existente, 11 casos, 0 removidos) actualizado para o 6º argumento do construtor, com stub `lenient()` de `findAllByInstanciavelTrue() -> List.of()` — o Caso 2 (zero interacção com `SystemSettingRepository`) continua a passar exactamente como antes.

## Task Commits

Each task was committed atomically:

1. **Task 1: provisionTenant instancia uma copia propria de cada molde na mesma transaccao** - `3cb60f26` (feat)
2. **Task 2: Prova comportamental de MOLD-01 e da invariante de snapshot MOLD-03** - `5ead30b9` (test)

**Plan metadata:** (pendente — orquestrador não pediu commit de metadata separado nesta execução, apenas SUMMARY.md, conforme instrução recebida)

_Note: nenhuma task usou TDD com gates RED/GREEN/REFACTOR formais no plano; a Task 2 incluiu, no entanto, uma demonstração de RED genuíno específica para a invariante de snapshot (ver secção dedicada abaixo), realizada e revertida antes do commit._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/SetupService.java` - `TenantRoleRepository` como 6º colaborador; `provisionTenant` chama `instanciarMoldes(tenant.getId())`; método privado novo `instanciarMoldes` com bloco de comentário das 4 invariantes; guarda `NOME_PAPEL_PLATAFORMA`
- `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java` - 6º mock (`TenantRoleRepository`), construtor actualizado, stub `lenient()` de `findAllByInstanciavelTrue()`
- `backend/src/test/java/com/lexcv/services/SetupServiceInstanciacaoMoldesTest.java` (novo) - 6 casos Mockito provando MOLD-01/MOLD-03

## Decisions Made
- `instanciarMoldes` é invocado depois de `userRepository.save(adminUser)` (a ordem exacta dentro da transacção não é significativa por si — nenhum código a seguir depende dos papéis instanciados já existirem — mas manter o `adminUser` antes preserva a ordem de leitura do método tal como estava, minimizando o diff).
- A guarda `NOME_PAPEL_PLATAFORMA` foi acrescentada por iniciativa própria (Rule 2, o plano pedia-a explicitamente como asserção a construir "se esta asserção exigir uma guarda explícita no serviço, acrescentá-la") — sem ela, o Caso 4 do novo teste teria falhado ao simular a regressão do filtro SQL.
- Mantive `adminUser.roles` intocado (`Set.of(adminRole)`, papel GLOBAL) — invariante bloqueada da fase, provada por `assertSame` no Caso 5 do novo teste.

## Demonstração de RED genuíno (invariante de snapshot, MOLD-03)

Por instrução do plano, alterei temporariamente `SetupService.java:185` de:
```java
.permissions(new HashSet<>(molde.getPermissions()))
```
para:
```java
.permissions(molde.getPermissions())
```
(referência partilhada em vez de cópia) e corri `mvn test -Dtest=SetupServiceInstanciacaoMoldesTest`.

**Saída FALHA (RED), com a referência partilhada:**
```
[ERROR] Tests run: 6, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 4.020 s <<< FAILURE! -- in com.lexcv.services.SetupServiceInstanciacaoMoldesTest
[ERROR] com.lexcv.services.SetupServiceInstanciacaoMoldesTest.instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva -- Time elapsed: 0.029 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: not same but was: <[com.lexcv.models.Permission@4f2958cc]>
	at com.lexcv.services.SetupServiceInstanciacaoMoldesTest.instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva(SetupServiceInstanciacaoMoldesTest.java:170)
[INFO] Tests run: 6, Failures: 1, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```
Falhou exactamente onde esperado: `assertNotSame(advogadoMolde.getPermissions(), tenantRoleInstanciado.getPermissions())`, porque com a referência partilhada os dois `Set` SÃO a mesma instância. Os outros 5 casos continuaram verdes (a regressão só afecta a asserção de identidade de instância).

Revertida a alteração para `new HashSet<>(molde.getPermissions())`. **Saída PASSA (GREEN), com a cópia:**
```
[INFO] Running com.lexcv.services.SetupServiceInstanciacaoMoldesTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.925 s -- in com.lexcv.services.SetupServiceInstanciacaoMoldesTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`git diff --stat backend/src/main/java/com/lexcv/services/SetupService.java` confirmado sem resíduo da alteração temporária antes do commit da Task 2 (o único diff remanescente era a guarda `NOME_PAPEL_PLATAFORMA`, intencional).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Guarda de defesa em profundidade contra PLATAFORMA_ADMIN**
- **Found during:** Task 2 (redacção do Caso 4 de `SetupServiceInstanciacaoMoldesTest`)
- **Issue:** O código de `instanciarMoldes` da Task 1 confiava inteiramente no filtro SQL de `findAllByInstanciavelTrue()` para nunca devolver `PLATAFORMA_ADMIN`. O próprio plano pedia uma segunda camada de defesa ("se esta asserção exigir uma guarda explícita no serviço, acrescentá-la") para o cenário de regressão simulado no teste (mock devolvendo `PLATAFORMA_ADMIN` na lista).
- **Fix:** Acrescentada constante `NOME_PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN"` e um `continue` no laço de `instanciarMoldes` que ignora qualquer molde com esse nome literal, mesmo que a lista devolvida o contivesse.
- **Files modified:** backend/src/main/java/com/lexcv/services/SetupService.java
- **Verification:** Caso 4 (`instanciarMoldes_plataformaAdminNuncaEInstanciado`) verde; suite completa 209/209; SpotBugs 0 achados.
- **Committed in:** `5ead30b9` (parte do commit da Task 2)

---

**Total deviations:** 1 auto-fixed (1 funcionalidade crítica em falta, defesa em profundidade explicitamente antecipada pelo próprio plano)
**Impact on plan:** Nenhum impacto no escopo ou na arquitectura da fase — reforça exactamente a garantia T-125-10 do threat model (`mitigate`), sem alterar nenhum comportamento de resolução de autoridade.

## Issues Encountered

- O critério de aceitação da Task 1 `grep -n 'roles(Set.of(adminRole))' ... devolve exactamente 1 linha` não se verifica literalmente: o padrão aparece 2 vezes no ficheiro (`initializeSystem` linha 85 e `provisionTenant` linha 132), porque `initializeSystem` já continha essa mesma linha antes desta fase — o plano parece ter assumido, por engano, que o padrão só existiria em `provisionTenant`. Confirmei manualmente que ambas as ocorrências atribuem correctamente o papel GLOBAL `ADMIN` (nenhuma foi trocada por um `TenantRole`), que é a intenção real do critério, e documentei aqui a discrepância em vez de forçar o grep a devolver `1` de forma artificial (o que exigiria reescrever `initializeSystem`, fora do escopo desta plan). Todos os outros critérios de grep do plano foram verificados literalmente e passaram.

## User Setup Required

None — nenhuma variável de ambiente nova, nenhum serviço externo, nenhuma dependência nova (`git diff --stat backend/pom.xml` vazio, confirmado).

## Next Phase Readiness

- O mecanismo central do marco está provado: provisionar um escritório novo já instancia, de facto, uma cópia própria de cada molde, com snapshot comprovado por teste capaz de reprovar (demonstração de RED genuíno documentada acima).
- Zero alterações em resolução de autoridade — `git diff --name-only` confirmado sem `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `User.java` nem `AdminController.java` em nenhum dos 2 commits desta plan.
- Suite completa de testes verde (209/209, acima dos 203 herdados do Plan 01) e SpotBugs sem novos achados.
- A Phase 126 (migração de tenants existentes) pode agora reutilizar directamente o mesmo idioma de `new HashSet<>(molde.getPermissions())` + `TenantRole.builder()` para retro-instanciar moldes em tenants que já existiam antes desta fase — nenhum bloqueador conhecido.
- A tenant reservada `ALCv` continua, por construção (não por guarda), sem papéis instanciados — não passa por `provisionTenant`.

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-20*

## Self-Check: PASSED

Todos os 3 ficheiros declarados em `files_modified` (`SetupService.java`, `SetupServiceProvisionTenantTest.java`, `SetupServiceInstanciacaoMoldesTest.java`) e o próprio SUMMARY.md foram confirmados no disco (`[ -f ... ]`), e os 2 hashes de commit (`3cb60f26`, `5ead30b9`) foram confirmados em `git log --oneline --all`. Nenhum item em falta.
