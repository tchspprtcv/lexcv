---
phase: 124-cat-logo-de-permiss-es-em-base-de-dados
plan: 02
subsystem: api
tags: [rbac, permissions, spring-boot, jpa, mockito]

# Dependency graph
requires:
  - phase: 124-cat-logo-de-permiss-es-em-base-de-dados (Plan 01)
    provides: colunas descritivas rotulo/descricao/modulo/ordem/reservadaPlataforma em Permission e seedRbac() como catalogo declarativo de 20 entradas
provides:
  - "GET /api/v1/admin/rbac constrói systemPermissions a partir de t_permission (via findAllByReservadaPlataformaFalse), não de literais Java"
  - "Exclusão ao nível de SQL de permissões reservadas à plataforma (CATL-03) + exclusão em Java de permissões sem rótulo, com aviso em log por chave excluída"
  - "As 3 permissões antes invisíveis (processos:create, processos:manage, financeiro:manage) tornam-se composáveis num papel de escritório"
  - "Mecanismo reservadaPlataforma fica disponível (hoje false em todas as 20 entradas) para qualquer autoridade que a plataforma venha a reservar"
affects: [125-moldes-de-permissoes, 127-papeis-por-escritorio]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "findAll → stream → map para DTO via mapper privado → sorted(nullsLast) → collect, seguindo o analog de PlatformAdminController.listTenants/toSummary"
    - "Exclusão de superfície de leitura ao nível de query derivada (findAllByReservadaPlataformaFalse), não em filtro Java pós-leitura -- fecha por SQL, não por disciplina de código"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/PermissionRepository.java
    - backend/src/main/java/com/lexcv/controllers/AdminController.java

key-decisions:
  - "getRbac() usa findAllByReservadaPlataformaFalse() (query derivada) em vez de findAll() + filtro Java -- a exclusão de reservadas fica ao nível de SQL, coerente com CATL-03"
  - "Mapeamento key<-Permission.nome / nome<-Permission.rotulo feito por builder() com campos nomeados (não construtor posicional), para evitar a troca silenciosa identificada em 124-PATTERNS.md"
  - "Ordenação por Permission.ordem com Comparator.nullsLast + desempate por nome, para tolerar uma base de dados onde a migração correu mas o seeder ainda não populou ordem"
  - "Permissão sem rótulo utilizável (nulo ou em branco após trim) é excluída em Java (não em SQL) com log.warn nomeando a chave técnica, usando o idioma @Slf4j já estabelecido em ResourceController"

patterns-established:
  - "Repositório com query derivada de exclusão booleana (findAllByReservadaPlataformaFalse) como única porta de leitura de uma tabela de catálogo com marca de reserva -- qualquer chamador futuro herda a exclusão sem precisar de repeti-la"

requirements-completed: [CATL-01, CATL-03]

# Metrics
duration: 25min
completed: 2026-09-20
---

# Phase 124 Plan 02: Consumo do Catálogo pelo Controller Summary

**`AdminController.getRbac()` deixa de embutir 17 `PermissionDefDto` hardcoded e passa a servir `systemPermissions` a partir de `t_permission` via uma query derivada que exclui permissões reservadas à plataforma ao nível de SQL, mantendo o contrato de resposta e o gate de autorização byte-a-byte iguais.**

## Performance

- **Duration:** ~25 min de execução ativa
- **Started:** 2026-09-20T21:00Z (aprox.)
- **Completed:** 2026-09-20T21:26Z
- **Tasks:** 1/2 automatizada e concluída; 1/2 (checkpoint humano) documentada como pendente — ver secção dedicada abaixo
- **Files modified:** 3 (1 criado, 2 modificados)

## Accomplishments
- `PermissionRepository` ganha `findAllByReservadaPlataformaFalse()`, uma query derivada de uma linha que exclui, ao nível de SQL, qualquer permissão marcada `reservada_plataforma = TRUE` — nenhum chamador futuro deste método pode esquecer-se do filtro
- `AdminController.getRbac()` constrói `systemPermissions` lendo esse método (nunca `findAll()`), filtra em Java as permissões sem rótulo utilizável (registando um `log.warn` por chave excluída), ordena de forma estável por `Permission.ordem` (`Comparator.nullsLast`, desempate por `nome`) e mapeia cada entidade para `RbacResponse.PermissionDefDto` através de um método privado `toPermissionDef` (builder, campos nomeados: `key`←`nome`, `nome`←`rotulo`)
- O literal `Arrays.asList(new RbacResponse.PermissionDefDto(...) × 17)` foi removido por completo; zero literais `PermissionDefDto` restam em `AdminController`
- Nada mais no método mudou: o bloco de comentário CR-01 (Phase 121), a anotação `@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")`, o laço de papéis com o `continue` de `PAPEL_PLATAFORMA`, e `updateRbac` (incluindo `role.setPermissions(permissions)`) ficam byte-a-byte iguais
- `AdminControllerRbacCatalogoTest` (163 linhas, 5 métodos de teste cobrindo os 6 comportamentos do plano — o teste de origem-dos-dados combina os comportamentos 1 e 3) prova via TDD real: origem exclusiva no repositório, mapeamento correto de campos, exclusão de sem-rótulo, ordenação estável com `null` tolerado, e não-regressão do laço de papéis
- Zero alterações em `web/`, `RbacResponse.java`, `backend/pom.xml` ou `web/package.json` — confirmado por `git status --porcelain`

## Task Commits

Task 1 (TDD) gerou dois commits:

1. **Task 1 (RED): teste falhando para systemPermissions vindo de t_permission** — `d95671b5` (test)
2. **Task 1 (GREEN): getRbac() serve systemPermissions a partir do catálogo em base de dados** — `e3457d8b` (feat)

**Plan metadata:** commit deste SUMMARY.md a seguir.

## TDD Gate Compliance

Ciclo RED/GREEN executado por reversão genuína: a implementação de `AdminController.java` foi temporariamente revertida (`git checkout --` para o estado do commit anterior a este plano) antes de escrever `AdminControllerRbacCatalogoTest`, com `PermissionRepository.findAllByReservadaPlataformaFalse()` já adicionado (scaffolding de compilação, ainda sem uso pelo controller). O teste foi corrido contra o `getRbac()` antigo (5/5 testes falharam — 3 `AssertionFailedError` de contagem/ordem e 2 `UnnecessaryStubbingException`, porque o método antigo nunca invoca o repositório), confirmando RED genuíno. O commit `test(124-02)` foi feito nesse estado. Só depois a implementação foi reaplicada (diff guardado antes do revert) e o commit `feat(124-02)` feito com a suite nova 5/5 verde, `AdminControllerRbacAutorizacaoTest`/`AdminControllerPlataformaAdminContencaoTest` inalterados e verdes, `mvn test` completo verde, e `mvn spotbugs:check` sem novos achados.

- `test(...)` commit: `d95671b5` — RED confirmado por execução real (`mvn test -Dtest=AdminControllerRbacCatalogoTest` → 5 failures/errors)
- `feat(...)` commit: `e3457d8b` — GREEN confirmado por execução real (`mvn test -Dtest=AdminControllerRbacCatalogoTest,AdminControllerRbacAutorizacaoTest,AdminControllerPlataformaAdminContencaoTest` → 0 falhas; `mvn test` completo → exit 0, sem `BUILD FAILURE`)
- Sem `refactor(...)` — não foi necessário. Um único ajuste de nomenclatura no comentário explicativo (para não repetir o texto literal `findAllByReservadaPlataformaFalse` duas vezes no ficheiro, cumprindo o critério de aceitação `grep -c ... devolve 1`) foi feito dentro do próprio commit GREEN, antes de o commitar — não gerou um commit `refactor` separado porque a suite já estava verde nesse ponto e a mudança era puramente textual (comentário), sem alterar comportamento.

## Files Created/Modified
- `backend/src/main/java/com/lexcv/repositories/PermissionRepository.java` — +`findAllByReservadaPlataformaFalse()`, query derivada de uma linha
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` — `@Slf4j` acrescentado à classe; bloco `systemPermissions` de `getRbac()` reescrito (leitura do repositório, filtro de rótulo com log, ordenação estável, mapeamento via `toPermissionDef`); novo método privado `toPermissionDef(Permission)`
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java` — 5 testes Mockito (sem proxy AOP) cobrindo os 6 comportamentos do plano

## Decisions Made
- `getRbac()` usa a query derivada `findAllByReservadaPlataformaFalse()` em vez de `findAll()` + `.filter(...)` em Java — a exclusão de reservadas fica garantida ao nível de SQL, não por disciplina de código no controller (CATL-03)
- Mapeamento `key`←`Permission.nome`/`nome`←`Permission.rotulo` construído por `RbacResponse.PermissionDefDto.builder()` com campos nomeados, não pelo construtor posicional de 4 argumentos usado na lista antiga — evita a troca silenciosa identificada como armadilha em `124-PATTERNS.md`
- Permissão sem rótulo utilizável (nulo ou em branco após `trim`) é excluída em Java, com `log.warn` nomeando a chave técnica excluída, usando `@Slf4j` — o mesmo idioma de logging já estabelecido em `ResourceController`, sem introduzir dependência nova
- Ordenação por `Permission.ordem` com `Comparator.nullsLast(Comparator.naturalOrder())` e desempate por `Permission::getNome`, para tolerar uma base de dados onde a migração de colunas correu mas o seeder ainda não populou `ordem` num arranque anterior a este deploy

## Deviations from Plan

None de conteúdo — plano executado exatamente como escrito. Um único ajuste textual (não uma deviação de comportamento): o comentário explicativo acima do bloco `systemPermissions` mencionava inicialmente o nome literal `findAllByReservadaPlataformaFalse` dentro do próprio comentário, o que fazia `grep -c 'findAllByReservadaPlataformaFalse' backend/src/main/java/com/lexcv/controllers/AdminController.java` devolver `2` em vez do `1` exigido pelo critério de aceitação. Reformulado o comentário para descrever "a query derivada do repositório abaixo" sem repetir o nome do método — comportamento e semântica do comentário inalterados, critério de aceitação agora cumprido exatamente.

## Issues Encountered
Nenhum além do já documentado no Plan 01: `mvn` requer `JAVA_HOME="/c/Program Files/Java/jdk-23"` explícito neste ambiente (JDK 26 por omissão falha a compilar Lombok). Nenhum ficheiro deste plano foi alterado por essa causa — é puramente uma variável de ambiente do shell, já confirmada como pré-existente e não introduzida por este plano.

## User Setup Required

None — não há configuração de serviço externo nesta task. O script de migração manual pendente (`backend/migrations/124-add-permission-catalogo-columns.sql`, Plan 01) continua a única ação operacional pendente para produção, sem mudança introduzida por este plano.

## Pending Human Verification (Task 2 — checkpoint:human-verify, gate="blocking")

Esta execução correu num run de milestone autónomo, sem operador humano disponível para clicar no ecrã. Por instrução explícita do orquestrador, Task 2 **não foi marcada como aprovada** — fica registada aqui como pendente, com os passos exatos que um humano deve executar antes de o Plan 02 (e a Phase 124) poderem ser considerados concluídos.

**O que foi construído (recapitulação):** o catálogo de permissões vive em `t_permission` (Plan 01: colunas + semeadura idempotente de 20 entradas) e `GET /api/v1/admin/rbac` passou a servir `systemPermissions` a partir dessas linhas, filtrando reservadas e sem-rótulo e ordenando por `ordem` (Task 1 deste plano). Zero linhas alteradas em `web/`.

**Automatizado e confirmado antes deste checkpoint:** suite Mockito completa verde (`AdminControllerRbacCatalogoTest`, `AdminControllerRbacAutorizacaoTest`, `AdminControllerPlataformaAdminContencaoTest`), `mvn test` completo verde, `mvn spotbugs:check` sem novos achados, todos os 12 grep-gates de aceitação da Task 1 confirmados.

**Passos pendentes de verificação humana ao vivo** (copiados do plano, não executados aqui — requerem `backend/.env` com `DB_*`/`MINIO_*` válidos, PostgreSQL acessível, e um browser):

1. Arrancar o backend a partir de `backend/` com `mvn spring-boot:run`. Confirmar no arranque que não há erro de validação de esquema — `seedRbac()` corre na primeira linha de `DatabaseSeeder.run`.
2. Arrancar o frontend a partir de `web/` com `pnpm dev` e entrar em `http://localhost:3000` como `admin@alcv.cv` / `Pa$$w0rd`.
3. Ir a **Definições** → separador **Permissões / RBAC**.
4. Confirmar que a matriz mostra **20** permissões (antes mostrava 17), agrupadas nos 8 módulos pela ordem: Clientes, Processos, Agenda, Documentos, Financeiro, Pareceres, Notificações, Administração.
5. Confirmar que as 3 permissões novas aparecem com rótulo e descrição legíveis, dentro do módulo certo e não no fim da lista: **Iniciar Processos** e **Administrar Processos** dentro de Processos (depois de "Gerir Processos"), e **Eliminar Lançamentos Financeiros** dentro de Financeiro (depois de "Gerir Financeiro").
6. Confirmar que nenhuma caixa aparece sem etiqueta, com etiqueta em branco, ou a mostrar a chave técnica crua (ex.: literalmente `processos:create`) em vez do rótulo.
7. Confirmar que os checkboxes do `ADVOGADO` já vêm marcados em "Iniciar Processos" e "Administrar Processos".
8. Confirmar que o papel **PLATAFORMA_ADMIN** continua a não aparecer em nenhuma linha da matriz.
9. Recarregar a página duas vezes e confirmar que a ordem dos módulos e das permissões dentro de cada módulo é exatamente a mesma nas três renderizações.
10. Reiniciar o backend uma vez e repetir o passo 4 — a contagem tem de continuar 20 e nenhum checkbox do `ADVOGADO`/`TECNICO`/`ASSISTENTE` pode ter desaparecido (prova ao vivo de CATL-02: a semeadura atualiza sem apagar atribuições).

**Resume-signal esperado:** "aprovado", ou uma descrição do que aparece errado (contagem, ordem, rótulo em falta, checkbox perdido) para correção antes de nova aprovação.

**Status:** PENDENTE — não invocado, não simulado. Não reivindicado como aprovado.

## Known Stubs

None.

## Threat Flags

None — as 6 mitigações do threat register da Phase 124 (T-124-06 a T-124-11) foram implementadas exatamente como especificado (query derivada para exclusão de reservadas, filtro de rótulo em branco, mapper com builder, contrato de resposta intacto, `Comparator.nullsLast`) e nenhuma superfície nova (endpoint, caminho de auth, acesso a ficheiro, mudança de schema em fronteira de confiança) foi introduzida além do já coberto pelo threat model do plano.

## Next Phase Readiness
- `GET /api/v1/admin/rbac` está pronto para a Phase 127 (CATL-04/PAPEL-*): as 3 permissões antes invisíveis (`processos:create`, `processos:manage`, `financeiro:manage`) passam a ser composáveis num papel de escritório através do ecrã de Definições, e a marca `reservada_plataforma` fica disponível — hoje `false` em todas as 20 entradas do catálogo — como o mecanismo por-permissão para qualquer autoridade que a plataforma venha a reservar no futuro
- **Bloqueio para fechar a Phase 124:** Task 2 (checkpoint humano) continua pendente de aprovação ao vivo — ver secção acima. Nenhuma alteração de código é esperada a partir dessa verificação (o automatizado já confirma os 6 comportamentos e o contrato intacto), mas a fase não deve ser dada como formalmente concluída sem essa confirmação visual explícita, conforme o gate do próprio plano

---
*Phase: 124-cat-logo-de-permiss-es-em-base-de-dados*
*Completed: 2026-09-20*

## Self-Check: PASSED

All 4 claimed files found on disk (`PermissionRepository.java`, `AdminController.java`, `AdminControllerRbacCatalogoTest.java`, this SUMMARY.md). Both claimed commit hashes (`d95671b5`, `e3457d8b`) found in `git log --oneline --all`.
