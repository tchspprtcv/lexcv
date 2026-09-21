---
phase: 126-migracao-de-papeis-existentes
plan: 05
subsystem: auth
tags: [rbac, spring-security, mockito, multi-tenant, provenance]

# Dependency graph
requires:
  - phase: 126-02
    provides: "ResolucaoPapeisService.temPapelDeMolde(User, String) -- predicado de proveniencia sobre TenantRole.moldeId"
  - phase: 126-04
    provides: "Cutover de leitura de JwtAuthenticationFilter/AuthController/AdminController, disjunto dos ficheiros tocados aqui"
provides:
  - "ParecerController.validateAdvogado resolve 'e advogado?' por proveniencia do molde ADVOGADO, nao por nome do papel de escritorio"
  - "ResourceController.addClienteAdvogado/addClienteAdministrativo resolvem os tres sitios de logica de negocio (ADVOGADO, ASSISTENTE OR TECNICO) por proveniencia"
  - "Os tres ultimos pontos de leitura por nome literal do 126-CONTEXT.md (Decisao 2) estao fechados -- fecha MIGR-01 desta fase"
affects: [127-crud-papeis-escritorio]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Constantes NOME_MOLDE_* privadas por controller, com comentario a marcar que designam moldes globais estaveis em t_role, nao o nome de papel de escritorio que a Phase 127 deixa renomear -- para que um refactor futuro nao 'arrume' a constante para o rotulo visivel do escritorio"
    - "Predicado composto (OR de dois moldes) traduzido para !temA && !temB, preservando a semantica de noneMatch(A || B) do original, com as duas metades provadas por testes separados"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/ParecerControllerProveniencaPapelTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerProveniencaPapelTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ParecerController.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java

key-decisions:
  - "ParecerControllerProveniencaPapelTest exercita validateAdvogado atraves do endpoint publico PUT /{id}/atribuir, nao por invocacao reflexiva do metodo privado -- o andaime (mock de parecerSolicitacaoRepository.findById/save) e pequeno e o endpoint publico prova tambem o caminho HTTP completo."
  - "Caso 6 do plano ('as guardas preexistentes continuam a fechar: outro tenant, inativo') implementado como UM teste com duas invocacoes/asserts, para bater a contagem exacta de 6 testes exigida pelo acceptance criteria de ParecerControllerProveniencaPapelTest."
  - "Comentarios explicativos junto das novas constantes NOME_MOLDE_* evitam repetir a string literal exacta entre aspas duplas onde os gates de grep do plano contam ocorrencias exactas (mesma licao do 126-04-SUMMARY.md) -- ex. 'designa o MOLDE global do advogado' em vez de 'designa o MOLDE global \"ADVOGADO\"'."

patterns-established: []

requirements-completed: [MIGR-01]

# Metrics
duration: 68min
completed: 2026-09-21
---

# Phase 126 Plan 05: Os tres sitios de comparacao por nome passam a proveniencia de molde Summary

**`ParecerController.validateAdvogado` e os dois sitios de `ResourceController` (`addClienteAdvogado`, `addClienteAdministrativo`) deixam de comparar `user.getRoles()` por nome literal ("ADVOGADO"/"ASSISTENTE"/"TECNICO") e passam a resolver por `TenantRole.moldeId` via `ResolucaoPapeisService.temPapelDeMolde` -- sobrevivendo a uma renomeacao de papel de escritorio que a Phase 127 (PAPEL-04) vai permitir.**

## Performance

- **Duration:** ~68 min
- **Started:** 2026-09-21T12:22:00Z (aprox.)
- **Completed:** 2026-09-21T13:30:32Z
- **Tasks:** 2/2
- **Files modified:** 3 producao/teste-existente + 2 testes novos = 5

## Accomplishments

- `ParecerController.validateAdvogado` resolve por proveniencia do molde ADVOGADO, com uma constante `NOME_MOLDE_ADVOGADO` documentada a distinguir molde global (estavel) de nome de papel de escritorio (renomeavel pela Phase 127). As guardas de tenant e de `ativo` (WR-02, Phase 87) ficam intactas, byte a byte.
- `ResourceController.addClienteAdvogado`/`addClienteAdministrativo` resolvem os dois sitios remanescentes, com o predicado composto de `addClienteAdministrativo` (`ASSISTENTE` OR `TECNICO`) traduzido para `!temA && !temB`, preservando a semantica exacta do `noneMatch` original. Mensagens de erro e `@PreAuthorize` ficam literalmente iguais.
- Fallback por nome preservado para utilizadores ainda sem papeis de escritorio (Decisao 2 do 126-CONTEXT.md), em todos os tres sitios.
- O limite deliberado (`moldeId` nulo nunca corresponde) esta fixado por teste em ambos os controllers -- um papel criado de raiz por um escritorio continua, tal como hoje, a nao satisfazer a verificacao.
- Dois sitios (`ParecerController:423,494`, `principal.getRoles().contains("ADMIN")`) permanecem deliberadamente NAO convertidos, exactamente como o `126-CONTEXT.md` e o proprio plano determinam -- ver "Sweep de Fase" abaixo.
- 14 casos de teste novos (6 + 8), ambos com `ResolucaoPapeisService` REAL sobre repositorios mockados (nunca stubado directamente) e RED genuino demonstrado e revertido para cada task.
- Suite completa: **291 testes** (277 antes deste plano + 14 novos), `mvn spotbugs:check` limpo, sem novas dependencias (`git diff --stat backend/pom.xml web/package.json` vazio).

## Task Commits

Each task was committed atomically:

1. **Task 1: ParecerController.validateAdvogado por proveniencia** - `8f81d89c` (feat)
2. **Task 2: Os dois sitios de ResourceController, incluindo o predicado composto** - `b7dc2bc6` (feat)

**Plan metadata:** (this commit, immediately following)

## Files Created/Modified

- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` - `validateAdvogado` chama `resolucaoPapeisService.temPapelDeMolde(user, NOME_MOLDE_ADVOGADO)`; constante e nono campo do construtor acrescentados; doc-comment actualizado
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `addClienteAdvogado`/`addClienteAdministrativo` chamam `temPapelDeMolde`; tres constantes `NOME_MOLDE_*` e vigesimo-sexto campo do construtor acrescentados; comentarios sobre a conversao para permissao diferida
- `backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java` - construtor actualizado a 26 argumentos (`@Mock ResolucaoPapeisService` acrescentado), zero casos removidos
- `backend/src/test/java/com/lexcv/controllers/ParecerControllerProveniencaPapelTest.java` - 6 casos novos: molde ADVOGADO, o mesmo papel renomeado (caso central), `moldeId` nulo, `moldeId` de outro molde, fallback por nome, guardas de tenant/ativo
- `backend/src/test/java/com/lexcv/controllers/ResourceControllerProveniencaPapelTest.java` - 8 casos novos: `addClienteAdvogado` (molde, renomeado, `moldeId` nulo, fallback, outro tenant) e `addClienteAdministrativo` (ASSISTENTE, TECNICO -- separados, ADVOGADO recusado)

## Decisions Made

Ver `key-decisions` no frontmatter. Resumo: exercitar `validateAdvogado` pelo endpoint publico em vez de reflexao; combinar as duas guardas preexistentes do caso 6 num unico metodo de teste para bater a contagem exacta de 6 exigida pelo plano; redigir comentarios evitando repetir literais entre aspas que colidiriam com os gates de grep do proprio plano.

## Deviations from Plan

None (funcional) - plano executado exactamente como escrito. As unicas alteracoes acima do especificado sao de redacao de comentarios e de organizacao de casos de teste, necessarias para que os proprios gates de aceitacao do plano medissem o que pretendiam medir (mesma classe de ajuste documentada no 126-04-SUMMARY.md).

## Issues Encountered

**Colisao de grep gates com comentarios explicativos (repetida do plano 04).** O primeiro rascunho do comentario junto a `NOME_MOLDE_ADVOGADO` em `ParecerController` citava `"ADVOGADO"` entre aspas como prosa explicativa, fazendo `grep -c '"ADVOGADO"'` contar 2 ocorrencias em vez da 1 exigida (apenas a declaracao da constante). Corrigido reformulando o comentario para nao repetir a string literal exacta, verificado com o Grep tool.

**Unnecessary stubbing (Mockito strict stubs) nos testes novos.** Em ambas as classes de teste, os casos de recusa (400/BAD_REQUEST) nunca chegam a chamar `save`/`auditLog.save`, pelo que os stubs desses metodos, definidos num helper comum de setup, ficavam nao utilizados nesses casos e o `MockitoExtension` (strict stubs por omissao) falhava com `UnnecessaryStubbingException`. Resolvido marcando esses stubs como `lenient()` no helper, documentado inline com o motivo.

**Drift de numero de linha no grep de varrimento do plano (ver "Sweep de Fase" abaixo).** A exclusao literal `AuthController.java:214\|AuthController.java:215` do plano deixou de bater com a linha real de `getMe` (`AuthController.java:222`), porque o Plano 04 acrescentou um comentario antes desse metodo, deslocando as linhas. Nao e um defeito funcional -- e um artefacto de exclusao por numero de linha absoluto num ficheiro que ja tinha sido editado por um plano anterior da mesma fase. Documentado e nao "corrigido silenciosamente": corri o comando exactamente como escrito no plano e reporto a saida real abaixo.

## Sweep de Fase (verificacao 3 do plano, exclusao corrigida)

Comando corrido literalmente como escrito no plano:

```
grep -rn 'getRoles()' backend/src/main/java/com/lexcv/ | grep -v 'ResolucaoPapeisService\|VerificacaoDerivaPapeisService\|MigracaoPapeisEscritorioService\|UserPrincipal.java\|AuthController.java:214\|AuthController.java:215'
```

Saida real:

```
src/main/java/com/lexcv/controllers/AuthController.java:222:                .roles(principal.getRoles())
src/main/java/com/lexcv/controllers/ParecerController.java:423:        boolean isAdmin = principal.getRoles().contains("ADMIN");
src/main/java/com/lexcv/controllers/ParecerController.java:494:        boolean isAdmin = principal.getRoles().contains("ADMIN");
```

Interpretacao, linha a linha:

- `AuthController.java:222` — `getMe`, o `@GetMapping("/me")` que o Plano 04 deixou deliberadamente intocado (comentario explicativo nas linhas 206-209 do ficheiro actual: le `UserPrincipal` ja resolvido pelo filtro, correcto por consequencia do cutover). A exclusao do plano visava esta linha por numero absoluto (`:214`/`:215`), que ja nao bate porque o proprio Plano 04 acrescentou 4 linhas de comentario antes do metodo. **Este resultado e esperado e correcto** — nao ha nenhum `getRoles()` de `getMe` a converter, apenas um numero de linha desactualizado na exclusao do plano.
- `ParecerController.java:423` e `:494` — os dois `principal.getRoles().contains("ADMIN")` que o plan-checker desta fase encontrou (originalmente `:411`/`:482`, deslocados +12 linhas pelo campo/constante/comentario que o Task 1 deste plano acrescentou a `ParecerController`). **Estes sao os dois sitios explicitamente diferidos para a Phase 127** (126-CONTEXT.md, `<deferred>`; ver tambem os `critical_invariants` deste plano): `principal.getRoles()` devolve `Set<String>` de nomes, nao entidades, e resolver por proveniencia exigiria dar a `UserPrincipal` um `moldeId` -- alteracao ao contrato do principal fora do ambito de uma migracao. Continuam correctos hoje (o nome do `TenantRole` e copia literal do nome do molde); ficam errados no dia em que a Phase 127 permitir renomear -- a Phase 127 tem de os fechar antes de expor essa capacidade.

**Resultado do varrimento: zero pontos de leitura por nome nao contabilizados.** Os tres resultados sao os tres esperados pelo proprio texto do plano (um `getMe` documentado + dois deferidos documentados); nenhum sitio novo, nao identificado, foi encontrado.

## RED Demonstrations (genuinas, com saida literal)

### Task 1 — `ParecerControllerProveniencaPapelTest`

Revertida temporariamente a chamada a `temPapelDeMolde` para a comparacao por nome original (`user.getRoles().stream().anyMatch(r -> "ADVOGADO".equals(r.getNome()))`). Corrida da classe:

```
[ERROR] Tests run: 6, Failures: 2, Errors: 2, Skipped: 0
[ERROR] ParecerControllerProveniencaPapelTest.tenantRoleComMoldeIdDeAdvogado_aceite:132 expected: <200 OK> but was: <400 BAD_REQUEST>
[ERROR] ParecerControllerProveniencaPapelTest.tenantRoleRenomeado_moldeIdPreservado_continuaAceite:151 expected: <200 OK> but was: <400 BAD_REQUEST>
```

O caso `tenantRoleRenomeado_moldeIdPreservado_continuaAceite` (o caso central desta task) falhou exactamente como o plano exige: com a comparacao por nome, um papel renomeado deixa de corresponder. Revertido (`git diff` sobre `ParecerController.java` confirmado a mostrar apenas a alteracao pretendida) e re-confirmado verde (6/6).

### Task 2 — `ResourceControllerProveniencaPapelTest`

Reduzido temporariamente o predicado composto de `addClienteAdministrativo` a apenas `!temPapelDeMolde(user, NOME_MOLDE_ASSISTENTE)` (removendo a metade `TECNICO`). Corrida da classe:

```
[ERROR] Tests run: 8, Failures: 1, Errors: 1, Skipped: 0
[ERROR] ResourceControllerProveniencaPapelTest.addClienteAdministrativo_comMoldeIdDeTecnico_cria:222 expected: <201 CREATED> but was: <400 BAD_REQUEST>
```

O caso `addClienteAdministrativo_comMoldeIdDeTecnico_cria` (a metade `TECNICO` do OR) falhou exactamente como o plano exige: reduzir o composto a uma so metade deixa a outra sem rede. Revertido e re-confirmado verde (8/8 na classe nova, 10/10 na familia `ResourceController*Test`).

## User Setup Required

None - nenhuma configuracao de servico externo necessaria.

## Next Phase Readiness

- Os tres sitios de logica de negocio identificados pelo `126-CONTEXT.md` (Decisao 2) estao fechados: nenhum ramifica mais por nome literal de papel. `MIGR-01` desta fase esta completo.
- **Bloqueador de pre-requisito registado para a Phase 127** (nao um adiamento livre): `ParecerController.java:423` e `:494` (`principal.getRoles().contains("ADMIN")`) tem de ser fechado pela Phase 127 antes de essa fase expor PAPEL-04 (renomear papel), sob pena de um escritorio que renomeie o seu papel de administrador perder silenciosamente a capacidade de entregar pareceres e criar versoes. Fechar isto exige dar a `UserPrincipal` um `moldeId` -- fora do ambito desta fase de migracao.
- Fase 126 completa: migracao de dados (Plano 01-03), cutover de leitura critico (Plano 04) e os tres sitios de logica de negocio por proveniencia (Plano 05, este) estao todos entregues e provados, 291/291 testes verdes.

---
*Phase: 126-migracao-de-papeis-existentes*
*Completed: 2026-09-21*

## Self-Check: PASSED

All created files verified present on disk (`ParecerControllerProveniencaPapelTest.java`, `ResourceControllerProveniencaPapelTest.java`); both task commits (`8f81d89c`, `b7dc2bc6`) verified present in `git log --oneline --all`.
