---
phase: 124-cat-logo-de-permiss-es-em-base-de-dados
plan: 01
subsystem: database
tags: [rbac, permissions, jpa, hibernate, postgresql, seed, mockito]

# Dependency graph
requires:
  - phase: 119-provisionamento-multi-tenant
    provides: papel PLATAFORMA_ADMIN com colecao de permissoes deliberadamente vazia (reserva por papel)
provides:
  - "Permission entity com 5 colunas descritivas novas: rotulo, descricao, modulo, ordem, reservadaPlataforma"
  - "seedRbac() reescrito como catalogo declarativo de 20 entradas (CATALOGO_PERMISSOES) com upsert idempotente que actualiza campos descritivos sem nunca apagar linhas"
  - "As 3 chaves antes invisiveis (processos:create, processos:manage, financeiro:manage) passam a ter rotulo/descricao proprios"
  - "Script manual backend/migrations/124-add-permission-catalogo-columns.sql, idempotente, catalogado no README"
affects: [125-moldes-de-permissoes, 126-consumo-do-catalogo-pelo-controller, 127-papeis-por-escritorio]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Coluna nova NOT NULL numa entidade com linhas em producao: columnDefinition com default embutido + @Builder.Default (mesmo padrao de Tenant.plano/ativo)"
    - "Catalogo declarativo como List.of(record) static final, upsert por findByNome -> map(actualizar)/orElseGet(criar) -> save, nunca delete"

key-files:
  created:
    - backend/migrations/124-add-permission-catalogo-columns.sql
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/Permission.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
    - backend/src/main/java/com/lexcv/config/UserPrincipal.java
    - backend/migrations/README.md

key-decisions:
  - "reservadaPlataforma nasce default=true (falha fechada); seedRbac() poe false explicitamente nas 20 entradas do catalogo em cada arranque"
  - "As 3 chaves antes invisiveis (processos:create, processos:manage, financeiro:manage) tornam-se ofereciveis, com rotulo/descricao derivados dos @PreAuthorize reais de ResourceController, para fechar a perda silenciosa de permissoes do ADVOGADO que o Plan 02 introduziria ao ler t_permission em vez da lista hardcoded de 17"
  - "ordem atribuida em multiplos de 10 (10..200) por ordem de declaracao, para deixar espaco a insercoes futuras sem renumerar"
  - "backend/migrations/124-add-permission-catalogo-columns.sql nao faz backfill de rotulo/descricao/modulo/ordem -- DatabaseSeeder.seedRbac() povoa-as no arranque seguinte, antes de a app aceitar pedidos"

patterns-established:
  - "Entidade com linhas de producao ganhando coluna NOT NULL: columnDefinition '<tipo> not null default <valor>' + @Builder.Default, migracao manual sem UPDATE de backfill quando o proprio seeder cobre isso no arranque seguinte"

requirements-completed: [CATL-01, CATL-02, CATL-03]

duration: 10min
completed: 2026-09-20
---

# Phase 124 Plan 01: Catálogo de Permissões em Base de Dados Summary

**`Permission` ganha rótulo/descrição/módulo/ordem/reservadaPlataforma; `seedRbac()` passa a semear um catálogo declarativo de 20 permissões com upsert não-destrutivo, e as 3 chaves antes invisíveis na matriz RBAC (`processos:create`, `processos:manage`, `financeiro:manage`) ganham rótulo próprio.**

## Performance

- **Duration:** ~10 min de execução activa (commits às 20:00, 20:04, 20:07 UTC-1)
- **Started:** 2026-09-20T20:00Z (aprox.)
- **Completed:** 2026-09-20T21:10Z
- **Tasks:** 2/2
- **Files modified:** 6 (2 criados, 4 modificados)

## Accomplishments
- `Permission` mapeia as 5 colunas novas (`rotulo`, `descricao`, `modulo`, `ordem`, `reservada_plataforma`), com `reservada_plataforma` NOT NULL DEFAULT TRUE (falha fechada) e `@EqualsAndHashCode(of = "nome")` preservado
- `seedRbac()` reescrito à volta de `CATALOGO_PERMISSOES` (record `CatalogoEntry`, `List.of(...)` de 20 entradas), upsert por `findByNome` que actualiza os campos descritivos de uma linha existente (mesma instância, mesmo `id`, `nome` nunca escrito) ou cria uma nova — nunca uma operação de remoção
- As 17 permissões pré-existentes mantêm rótulo/descrição/módulo byte-a-byte iguais aos da lista hardcoded de `AdminController.getRbac()`; as 3 antes invisíveis (`processos:create`, `processos:manage`, `financeiro:manage`) ganham rótulo/descrição próprios
- `backend/migrations/124-add-permission-catalogo-columns.sql` criado (idempotente, `ADD COLUMN IF NOT EXISTS` em todas as instruções) e catalogado nas 3 tabelas + 2 contagens de `backend/migrations/README.md`
- `DatabaseSeederCatalogoPermissoesTest` prova os 5 comportamentos do `<behavior>` do plano via ciclo TDD RED→GREEN real (ver abaixo)

## Task Commits

Cada task foi committada atomicamente; a Task 2 (TDD) gerou dois commits:

1. **Task 1: Colunas do catálogo em Permission + script manual de migração + inventário** — `d9a2b448` (feat)
2. **Task 2 (RED): teste falhando para o catálogo declarativo** — `7ae33070` (test)
3. **Task 2 (GREEN): catálogo declarativo de 20 permissões com upsert não-destrutivo** — `2c2f3d2e` (feat)

**Plan metadata:** commit deste SUMMARY.md a seguir.

## TDD Gate Compliance

Ciclo RED/GREEN executado de forma genuína, não apenas ordenada: a implementação de `seedRbac()`/`UserPrincipal.java` foi temporariamente revertida (`git checkout --` sobre esses dois ficheiros, para o estado do commit `d9a2b448`) antes de escrever `DatabaseSeederCatalogoPermissoesTest`, o teste foi corrido contra o `seedRbac()` antigo (4 de 5 testes falharam, confirmando RED — o teste de "nunca apaga" já passava por coincidência, porque o código antigo também nunca chamava `delete*`), o commit `test(124-01)` foi feito nesse estado, e só depois a implementação foi reaplicada (diff guardado antes do revert) e o commit `feat(124-01)` feito com a suite verde (5/5 na classe nova, 192/192 na suite completa).

- `test(...)` commit: `7ae33070` — RED confirmado por execução real (`mvn test -Dtest=DatabaseSeederCatalogoPermissoesTest` → 4 failures)
- `feat(...)` commit: `2c2f3d2e` — GREEN confirmado por execução real (`mvn test -Dtest=DatabaseSeederCatalogoPermissoesTest,DatabaseSeederPlataformaAdminTest` → 11/11; `mvn test` completo → 192/192)
- Sem `refactor(...)` — não foi necessário.

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Permission.java` — +5 campos (`rotulo`, `descricao`, `modulo`, `ordem`, `reservadaPlataforma`), `reservada_plataforma` fecha por omissão
- `backend/migrations/124-add-permission-catalogo-columns.sql` — script manual idempotente para `ddl-auto: validate`
- `backend/migrations/README.md` — nova linha de inventário (Path B, ordem 13, `125` passa a 14), tabela Safe-to-re-run (4 de 14), Known execution status (6 outstanding)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` — `CatalogoEntry` (record privado) + `CATALOGO_PERMISSOES` (20 entradas) + `seedRbac()` reescrito como upsert de campos descritivos
- `backend/src/main/java/com/lexcv/config/UserPrincipal.java` — só o comentário de sincronização foi alterado (aponta agora para `CATALOGO_PERMISSOES`); o `Arrays.asList` de 20 chaves fica inalterado
- `backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java` — 5 testes Mockito cobrindo criação-com-campos, as 3 chaves antes invisíveis, refresh-sem-apagar (mesma instância/id/nome), never-delete, e ordem distinta/estável

## Decisions Made
- `reservadaPlataforma` nasce `true` (falha fechada); `seedRbac()` baixa-a a `false` explicitamente nas 20 entradas do catálogo em cada arranque — nenhuma permissão do catálogo é hoje reservada, o mecanismo existe para as fases 125/127
- As 3 chaves antes invisíveis tornam-se ofereciveis com rótulo/descrição próprios (decisão explícita de 124-CONTEXT.md `<specifics>`), evitando que o Plan 02 (leitura de `t_permission` em vez da lista hardcoded de 17) desnude silenciosamente o `ADVOGADO` de `processos:create`/`processos:manage` na próxima gravação da matriz RBAC
- `ordem` em múltiplos de 10 (10..200) por ordem de declaração, deixando espaço a inserções futuras sem renumerar
- O script de migração manual não faz backfill de `rotulo`/`descricao`/`modulo`/`ordem` — `seedRbac()` povoa-as no arranque seguinte, antes de a aplicação aceitar pedidos (janela sem exposição, documentada no próprio script)

## Deviations from Plan

None — plan executado exactamente como escrito. O único ajuste operacional (não uma deviação de conteúdo) foi a necessidade de usar `JDK 23` explicitamente (`JAVA_HOME=/c/Program Files/Java/jdk-23`) para os comandos `mvn`, porque o `java`/`mvn` por omissão neste ambiente resolvem para JDK 26, que falha a compilar (`ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`) com a versão actual do Lombok — problema pré-existente do ambiente, não introduzido por este plano. JDK 23 está instalado em `C:\Program Files\Java\jdk-23`, coerente com `<maven.compiler.release>` do `pom.xml` (Java 23).

## Issues Encountered
- `mvn -DskipTests compile` falhava inicialmente com `java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — causado pelo JDK 26 (`java -version` por omissão neste ambiente) ser incompatível com a versão de Lombok fixada no `pom.xml`. Resolvido apontando `JAVA_HOME`/`PATH` para o JDK 23 já instalado na máquina antes de qualquer comando `mvn`. Nenhum ficheiro do projecto foi alterado por esta causa — é puramente uma variável de ambiente do shell.

## User Setup Required

None — não há configuração de serviço externo. Registo operacional para a Phase 126 e para o próximo deploy: `backend/migrations/124-add-permission-catalogo-columns.sql` é um script de execução manual **pendente** em qualquer base de dados existente a correr com `ddl-auto: validate` (o mesmo padrão de `74`/`117`/`120` documentado em `.planning/STATE.md`). Deve ser executado antes ou durante o deploy do código deste plano, seguindo `backend/migrations/README.md` (Path B, posição 13).

## Next Phase Readiness
- `t_permission` é agora a fonte de verdade completa do catálogo (rótulo/descrição/módulo/ordem/reservadaPlataforma) para o Plan 02 (fase 124) ler via `permissionRepository.findAll()` em `AdminController.getRbac()`, substituindo a lista hardcoded de 17 entradas
- Nenhuma autoridade nem gate foi alterado nesta fase — `GET /admin/rbac` continua a devolver exactamente o mesmo shape de `RbacResponse`/`PermissionDefDto`, agora alimentável pela base de dados
- Script de migração manual pendente de execução em produção antes do deploy que introduz este plano — ver "User Setup Required" acima

---
*Phase: 124-cat-logo-de-permiss-es-em-base-de-dados*
*Completed: 2026-09-20*

## Self-Check: PASSED

All 7 claimed files found on disk (Permission.java, migration script, README.md, DatabaseSeeder.java, UserPrincipal.java, DatabaseSeederCatalogoPermissoesTest.java, this SUMMARY.md). All 4 claimed commit hashes (`d9a2b448`, `7ae33070`, `2c2f3d2e`, `b3927f70`) found in `git log --oneline --all`.
