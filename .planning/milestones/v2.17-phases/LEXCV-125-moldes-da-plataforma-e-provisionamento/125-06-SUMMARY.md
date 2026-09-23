---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 06
subsystem: ui
tags: [nextjs, react, react-hook-form, zod, accessibility, rbac, structural-gate]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 04)
    provides: "web/src/hooks/use-platform-moldes.ts (useCreateMolde), web/src/types/platform-moldes.ts (MoldeCreateRequest, MoldePermissao)"
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 05)
    provides: "web/src/app/(dashboard)/plataforma/moldes/page.tsx -- matriz, banner, AlertDialog de gravacao (extendido, nao substituido, por este plano)"
provides:
  - "web/src/schemas/moldes.ts -- criarMoldeSchema com transform-on-submit para maiusculas e recusa case-insensitive do nome reservado PLATAFORMA_ADMIN"
  - "web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx -- painel inline de criacao de molde, orientado por props, sem mutacao propria"
  - "web/scripts/verify-consola-moldes.mjs -- gate estrutural Node-only (16 assercoes) do aviso em tres camadas e da acessibilidade da matriz, ligado a `pnpm run verify:consola-moldes`"
affects: [125-milestone-close]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Painel de criacao inline orientado por props (onCancel/onSubmit/isSubmitting), mutacao/toast/fecho detidos pelo chamador -- mesmo padrao de CriarTenantPanel, agora replicado para moldes"
    - "Checklist de permissoes agrupada por modulo, coluna unica, estado marcado em azul de accento em vez de ambar (ambar reservado ao AlertDialog de gravacao, nunca ao painel de criacao)"
    - "Gate estrutural Node-only com prova de capacidade de reprovar documentada no SUMMARY (nao so alegada) -- duas remocoes temporarias e independentes, cada uma capturada com FAIL isolado, seguidas de restauro confirmado a exit 0"

key-files:
  created:
    - "web/src/schemas/moldes.ts"
    - "web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx"
    - "web/scripts/verify-consola-moldes.mjs"
    - ".planning/phases/LEXCV-125-moldes-da-plataforma-e-provisionamento/deferred-items.md"
  modified:
    - "web/src/app/(dashboard)/plataforma/moldes/page.tsx"
    - "web/package.json"

key-decisions:
  - "A pluralizacao gramatical da linha do AlertDialog (\"1 escritório já tem...\" vs \"{N} escritórios já têm...\") ja vinha corrigida do commit 634efd3d, anterior a este plano -- o texto literal \"escritório(s)\" mencionado no checkpoint humano (Task 3, ponto 14) esta desatualizado face ao codigo real; documentado como drift resolvido, nao reintroduzido."
  - "125-HUMAN-UAT.md nao foi criado com veredictos fabricados -- a Task 3 e um checkpoint:human-verify executado numa run autonoma sem humano presente. Os 16 pontos ficam registados como pendentes na seccao dedicada deste SUMMARY, seguindo o protocolo explicito de checkpoint_handling do executor, em vez de inventar CONFIRMADO/FALHOU sem observar o ecra ao vivo."
  - "O gate estrutural (Task 2) recebeu 16 assercoes, nao as ~13 literalmente enumeradas na acao do plano -- duas assercoes extra (wiring-hook-criacao-e-toast, matriz-sem-branch-readonly) foram acrescentadas para cumprir o criterio de aceitacao explicito de 'pelo menos 15 linhas PASS', que a primeira versao do script (14 assercoes) nao alcancava."
  - "A assercao matriz-scope-col-row foi reforcada de 'existencia' para 'contagem >= 2' de scope=\"col\" depois de a primeira demonstracao de reprovacao falhar silenciosamente -- a matriz tem 2 ocorrencias literais de scope=\"col\" no codigo-fonte (cabecalho de rotulo + cabecalho por molde), e remover so uma delas nao invalidava um predicado que apenas testava presenca. Ver Deviations."

patterns-established:
  - "Prova de capacidade de reprovar de um gate estrutural documentada com outputs reais (nao so a alegacao de que existe) -- referencia para qualquer gate .mjs futuro nesta consola."

requirements-completed: [MOLD-01, MOLD-02, MOLD-03, MOLD-04]

# Metrics
duration: ~55min
completed: 2026-09-21
---

# Phase 125 Plan 06: Criacao de Moldes, Gate Estrutural e Fecho da Fase Summary

**Painel inline "Criar Molde" (schema Zod com recusa case-insensitive de PLATAFORMA_ADMIN + checklist de permissoes por modulo), gate estrutural Node-only de 16 assercoes que reprova genuinamente se o aviso de nao-propagacao em tres camadas ou a acessibilidade da matriz forem removidos, e os 16 pontos do checkpoint humano de fecho da fase registados como pendentes (nao inventados).**

## Performance

- **Duration:** ~55 min
- **Completed:** 2026-09-21
- **Tasks:** 2/3 automatizadas e commitadas (Task 3 e checkpoint:human-verify, ver secao dedicada)
- **Files modified:** 4 novos, 2 alterados

## Accomplishments

- `web/src/schemas/moldes.ts` criado: `criarMoldeSchema` com `nome` obrigatorio, `trim`, recusa **case-insensitive** do literal `PLATAFORMA_ADMIN` via `.refine` com a mensagem exacta do UI-SPEC, e `.transform` para maiusculas no submit (mesma convencao `adminEmail.toLowerCase()` de `schemas/setup.ts`, invertida para uppercase). `permissoes: z.array(z.string())`.
- `web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx` criado: `CriarMoldePanel` orientado por props (`onCancel`/`onSubmit`/`isSubmitting`/`permissoes`), estrutura identica a `CriarTenantPanel` (botao `X` ghost com `aria-label="Fechar"`, `CardFooter` com Cancelar/Criar Molde). Checklist de permissoes agrupada por modulo, coluna unica, `max-h-72 overflow-y-auto`, estado marcado em **azul de accento** (nao ambar — ambar fica reservado ao `AlertDialog` de gravacao da matriz). Zero mutacao, zero toast, zero `Dialog` neste ficheiro — confirmado por grep negativo no gate.
- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` estendido (nao reescrito): estado `isFormOpen`, botao "Criar Molde" (outline, `Plus`) no `CardHeader` antes de "Guardar Alterações", troca inline do `Card` pelo painel (mesmo padrao `isFormOpen` de `PlataformaPageContent`), botao "Criar Molde" tambem no `EmptyContent` do estado vazio defensivo. `handleCreateSubmit` liga `useCreateMolde()`, mostra o toast de sucesso com o nome normalizado, fecha o painel; em falha mantem o painel aberto com o input intacto (mesma convencao de `CriarTenantPanel`).
- `web/scripts/verify-consola-moldes.mjs` criado: gate Node-only, zero dependencias, mesma tecnica `stripComments`/`sliceBetweenMarkers` dos gates analogos (`verify-relatorio-utilizacao.mjs`, `verify-consola-tenants.mjs`). **16 assercoes** cobrindo: ponto de entrada (`Gerir Moldes` antes de `Ver Relatório`), guarda de pagina falha-fechado, as tres camadas do aviso de nao-propagacao (banner ambar + `TriangleAlert`, badge derivado de `escritoriosInstanciados` dentro do `<thead>`, `AlertDialog`/`bg-amber-600` unico e restrito ao dialogo, mutacao invocada so via `AlertDialogAction`→`handleConfirmarGravacao`), acessibilidade da matriz (`scope="col"` x2, `scope="row"`, `text-left`, `aria-label` template por checkbox, `colSpan` dinamico em vez de fixo), nao-regressoes (`isAdminRow`/"Gerido pela Plataforma" ausentes, sem `mock-db`/`NavigationMenu`/`Sidebar`), e o painel de criacao (zod, `aria-label="Fechar"`, `max-h-72`, sem mutacao propria).
- `web/package.json`: uma linha nova, `"verify:consola-moldes": "node scripts/verify-consola-moldes.mjs"`, logo a seguir a `verify:relatorio-utilizacao`. `git diff web/package.json` confirmado restrito ao bloco `scripts` — zero dependencias novas.

## Task Commits

Each automated task was committed atomically:

1. **Task 1: Painel inline de criacao de molde e o seu wiring na consola** - `9db39baf` (feat)
2. **Task 2: Gate estrutural do aviso em tres camadas e da acessibilidade da matriz** - `30057b58` (feat)

**Task 3 (checkpoint:human-verify)** nao foi executada nem aprovada nesta run — ver secao "Verificacao Humana Pendente" abaixo. Nenhum commit corresponde a ela.

**Plan metadata:** (a ser commitado separadamente pelo orquestrador, junto do SUMMARY.md)

## Files Created/Modified

- `web/src/schemas/moldes.ts` (novo) — schema Zod de criacao de molde
- `web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx` (novo) — painel inline de criacao
- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (alterado) — wiring do painel, botao "Criar Molde" no CardHeader e no EmptyContent
- `web/scripts/verify-consola-moldes.mjs` (novo) — gate estrutural, 16 assercoes
- `web/package.json` (alterado) — entrada `verify:consola-moldes`
- `.planning/phases/LEXCV-125-moldes-da-plataforma-e-provisionamento/deferred-items.md` (novo) — drift pre-existente de renomeacao LexCV→ALCv em dois gates de fases anteriores (nao corrigido, fora do ambito deste plano)

## Prova de Capacidade de Reprovar do Gate Estrutural

Exigencia do criterio de aceitacao da Task 2: "um gate que nunca se viu reprovar nao e um gate." Duas demonstracoes independentes, cada uma com remocao → FAIL → restauro → PASS:

**Demonstracao 1 — remocao de `scope="col"` do cabecalho de rotulo da matriz:**
```
FAIL matriz-scope-col-row — scope="col" aparece nos 2 cabecalhos de coluna (rotulo + por molde)
     e scope="row" presente; a celula promovida a <th scope="row"> contem text-left
Exit code: 1
```
Restauro (`git checkout -- .../moldes/page.tsx`) confirmado com as 16 assercoes de volta a `PASS` e `exit 0`.

**Demonstracao 2 — remocao do texto do banner de nao-propagacao:**
```
FAIL aviso-camada1-banner — existe um bloco com bg-amber-50 e TriangleAlert contendo
     "Alterar um molde não atualiza escritórios já criados.", e ShieldAlert nao existe no ficheiro
Exit code: 1
```
Restauro confirmado com as 16 assercoes de volta a `PASS` e `exit 0`.

Saida real da execucao apos o restauro final (16/16 `PASS`, `exit 0`):
```
PASS entrada-gerir-moldes
PASS guarda-de-pagina-falha-fechado
PASS aviso-camada1-banner
PASS aviso-camada2-badge-contagem
PASS aviso-camada3-alertdialog-gravacao
PASS aviso-camada3-mutacao-so-via-alertdialogaction
PASS matriz-scope-col-row
PASS matriz-aria-label-checkbox
PASS matriz-colspan-dinamico
PASS sem-regressoes-rbactab
PASS sem-mock-db-nem-navegacao-nova
PASS painel-criacao-zod-e-fechar
PASS painel-criacao-sem-mutacao-propria
PASS wiring-pagina-usa-criarmoldepanel
PASS wiring-hook-criacao-e-toast
PASS matriz-sem-branch-readonly
```

## Verificacoes Automatizadas Executadas

- `cd web && pnpm install && pnpm exec tsc --noEmit && pnpm lint` — exit 0, sem erros novos (so os avisos pre-existentes de `react-hooks/incompatible-library` ja presentes noutros formularios `react-hook-form` do projecto).
- `cd web && BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1 pnpm build` — exit 0, `/plataforma/moldes` gerado como rota estatica.
- `cd web && pnpm run verify:consola-moldes` — exit 0, 16/16 `PASS`.
- `cd web && pnpm run verify:relatorio-utilizacao` — exit 0, 15/15 `PASS` (nao-regressao confirmada).
- `cd web && pnpm run verify:consola-tenants` — **exit 1**, 2 `FAIL` (`tooltip-span-wrapper`, `guarda-tenant-reservado`). **Pre-existente, nao causado por este plano** — ver "Deviations" e `deferred-items.md`.
- `cd web && pnpm run verify:bloqueio-rbac` — **exit 1**, 1 `FAIL` (`A08-texto-exato-do-tooltip`). **Pre-existente, nao causado por este plano** — mesma causa raiz.
- `git diff web/package.json` — restrito ao bloco `scripts`. `git diff --name-only web/src/components/ui/` — vazio.
- Backend (`mvn test`) **nao foi executado nesta run** — esta execucao cobriu apenas as Tasks 1/2, que sao alteracoes de frontend puro; nenhum ficheiro backend foi tocado por este plano.

## Decisions Made

- Ver `key-decisions` no frontmatter para as quatro decisoes desta plano (drift do texto de pluralizacao ja resolvido antes deste plano, nao-fabricacao do UAT humano, expansao do gate para 16 assercoes, reforco da assercao de `scope="col"` de existencia para contagem).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Primeira versao do gate estrutural tinha 14 assercoes, abaixo do minimo de 15 `PASS` exigido pelo criterio de aceitacao da Task 2**
- **Found during:** Task 2, ao correr `pnpm run verify:consola-moldes` pela primeira vez e contar as linhas `PASS` (14, nao >= 15).
- **Issue:** As assercoes obrigatorias enumeradas na accao do plano, agrupadas literalmente como escrito, resultam em 14 predicados distintos — nao 15+.
- **Fix:** Acrescentadas duas assercoes adicionais, ambas genuinamente verificaveis e nao redundantes: `wiring-hook-criacao-e-toast` (confirma que `page.tsx` usa `useCreateMolde`/`criarMolde.mutateAsync` e mostra o toast de sucesso) e `matriz-sem-branch-readonly` (confirma a ausencia de `isDisabled`, reforcando por outro angulo o requisito do UI-SPEC "No read-only branch").
- **Files modified:** `web/scripts/verify-consola-moldes.mjs`
- **Verification:** `pnpm run verify:consola-moldes` passou a devolver 16 linhas `PASS`, `exit 0`.
- **Committed in:** `30057b58` (parte do commit da Task 2)

**2. [Rule 1 - Bug] A primeira versao da assercao `matriz-scope-col-row` testava so presenca (`includes`) de `scope="col"`, nao contagem — a primeira demonstracao de capacidade de reprovar (remocao de UM dos dois `scope="col"` da matriz) nao produzia FAIL, porque o segundo `scope="col"` (cabecalho por molde) ainda estava presente**
- **Found during:** Task 2, na primeira tentativa de demonstrar capacidade de reprovar exigida pelo criterio de aceitacao ("remover temporariamente o atributo scope=\"col\"... confirmar exit != 0").
- **Issue:** `moldesPage.includes('scope="col"')` continua `true` mesmo depois de remover uma das duas ocorrencias literais de `scope="col"` no ficheiro (uma no cabecalho "Módulo / Permissão", outra no cabecalho de cada molde) — um gate que so testa presenca binaria de um token que aparece 2+ vezes no ficheiro nao consegue detectar a remocao de uma so ocorrencia.
- **Fix:** Assercao reescrita para contar ocorrencias (`(moldesPage.match(/scope="col"/g) ?? []).length >= 2`) em vez de testar so presenca.
- **Files modified:** `web/scripts/verify-consola-moldes.mjs`
- **Verification:** Repetida a demonstracao de reprovacao (remocao do `scope="col"` do cabecalho de rotulo) — agora produz `FAIL matriz-scope-col-row` e `exit 1`, conforme exigido. Restauro confirmado de volta a `exit 0`.
- **Committed in:** `30057b58` (parte do commit da Task 2)

### Out-of-Scope Discoveries (logged, not fixed)

**3. Drift pre-existente entre dois gates estruturais de fases anteriores e o codigo real, causado pela renomeacao do produto de LexCV para ALCv**
- **Found during:** Task 2, ao correr as verificacoes de nao-regressao exigidas pela seccao `<verification>` do plano (`verify:consola-tenants`, `verify:bloqueio-rbac`).
- **Issue:** `verify-consola-tenants.mjs` (Phase 120) e `verify-bloqueio-rbac.mjs` tem literais hardcoded `"(LexCV)"`, mas `web/src/app/(dashboard)/plataforma/columns.tsx:81` ja diz `"(ALCv)"` — o produto foi renomeado depois desses gates terem sido escritos, e os gates nunca foram actualizados.
- **Why not fixed:** Nenhum dos ficheiros envolvidos (os dois scripts `.mjs`, `columns.tsx`, `settings/page.tsx`) esta na lista `files_modified` deste plano, nem e tocado por nenhuma das suas tasks — fora do ambito por definicao (Scope Boundary do executor).
- **Logged in:** `.planning/phases/LEXCV-125-moldes-da-plataforma-e-provisionamento/deferred-items.md`

---

**Total deviations:** 2 auto-fixed (ambas no proprio gate estrutural que esta Task 2 construiu, antes de ele ser commitado — nao alteram nenhuma garantia do ecra, so tornam o proprio gate mais rigoroso, exactamente o que o criterio de aceitacao pedia), 1 descoberta fora do ambito registada e nao corrigida.
**Impact on plan:** Nenhum impacto na garantia funcional do painel de criacao nem do gate — os dois ajustes ao `.mjs` aconteceram durante a construcao da Task 2, antes do commit, e o proprio processo de os encontrar (demonstracao de capacidade de reprovar) e uma exigencia explicita do plano, nao um efeito colateral indesejado.

## Verificacao Humana Pendente (Task 3, checkpoint:human-verify)

Esta run e autonoma (sem humano presente) — a Task 3 nao pode ser aprovada aqui. Nenhum dos 16 pontos abaixo foi observado ao vivo nesta sessao; nenhum veredicto foi inventado. `125-HUMAN-UAT.md` **nao foi criado** por esta mesma razao — criar esse ficheiro com veredictos fabricados violaria directamente o protocolo de checkpoint deste executor. Um humano (ou uma run subsequente com acesso a um browser e a base de dados) tem de executar os 16 pontos abaixo e preencher `125-HUMAN-UAT.md` antes de a Fase 125 poder ser fechada.

**Pre-requisitos:** `backend/.env` com `DB_*`/`MINIO_*` validos e PostgreSQL acessivel; `backend/migrations/126-add-tenant-role-tables.sql` continua **pendente de execucao manual em producao** (nao necessario em dev, onde `ddl-auto: update` cria o esquema).

1. Arrancar o backend (`JAVA_HOME="/c/Program Files/Java/jdk-23" mvn spring-boot:run` a partir de `backend/`) e confirmar arranque sem erro de validacao de esquema.
2. Arrancar o frontend (`pnpm dev` a partir de `web/`) e entrar em `http://localhost:3000` como `PLATAFORMA_ADMIN`.
3. Em `/plataforma`, confirmar a ordem `Gerir Moldes` → `Ver Relatório` → `Criar Tenant` no `CardHeader`. Clicar "Gerir Moldes".
4. **(MOLD-02)** Confirmar que `/plataforma/moldes` mostra os 4 moldes (ADMIN, ADVOGADO, TECNICO, ASSISTENTE) como colunas, `PLATAFORMA_ADMIN` **nao** aparece, e as 20 permissoes aparecem agrupadas por modulo com rotulo legivel.
5. Confirmar que o banner ambar esta visivel sem clicar em nada.
6. Confirmar que cada coluna mostra um badge de contagem de escritorios (cinzento a "0 escritórios" antes de qualquer provisionamento novo).
7. Confirmar que "Guardar Alterações" comeca desactivado; alterar uma checkbox e confirmar que passa a activo.
8. Clicar "Guardar Alterações": confirmar que abre o `AlertDialog` "Confirmar alterações aos moldes" listando so o molde alterado; cancelar e confirmar que nada foi gravado.
9. Repetir e gravar: confirmar o toast "Moldes atualizados com sucesso." e a persistencia apos recarregar.
10. **(MOLD-04)** Clicar "Criar Molde", preencher nome em minusculas (ex. `supervisor`), marcar 2-3 permissoes, submeter. Confirmar toast com o nome em MAIUSCULAS, fecho do painel, e a quinta coluna nova com badge "0 escritórios".
11. Tentar criar `plataforma_admin` (minusculas). Confirmar a recusa com "Este nome está reservado à plataforma e não pode ser usado como molde." e que nada e criado.
12. **(MOLD-01)** Criar um tenant novo em `/plataforma`. Confirmar via SQL: `SELECT nome, molde_id, sistema FROM t_tenant_role WHERE tenant_id = '<id>' ORDER BY nome;` devolve 5 linhas, todas `sistema = true`, `molde_id` preenchido; e que a tenant "ALCv" **nao** tem linhas em `t_tenant_role`.
13. Confirmar que o badge de contagem passa a "1 escritório" (singular) apos recarregar.
14. **(MOLD-03 — o mais importante)** Remover uma permissao de um molde e gravar (o `AlertDialog` deve mostrar a pluralizacao gramatical correcta — `"1 escritório já tem uma cópia própria..."` ou `"{N} escritórios já têm..."`, **nao** o texto literal `"escritório(s)"`, que ja foi corrigido no commit `634efd3d`, anterior a este plano). Confirmar via SQL que as permissoes do papel instanciado do tenant do ponto 12 **nao mudaram**.
15. Testar o gate de autoridade: `admin@alcv.cv` (ADMIN de escritorio) a navegar directamente para `/plataforma/moldes`. Confirmar acesso negado, e confirmar 403 (nao 200) no separador de rede para qualquer pedido a `/api/v1/platform/moldes`.
16. Verificar acessibilidade com o inspector: `<th>` de coluna com `scope="col"`, celula de rotulo como `<th scope="row">` alinhada a esquerda, e um checkbox com `aria-label` da forma "Ver Clientes — ADVOGADO".

**Nota sobre o ponto 14:** o texto exacto a observar ja diverge do que a `<how-to-verify>` original da Task 3 descreve (ver `phase_context` recebido por este executor) — o `AlertDialog` gramatica correctamente a pluralizacao desde o commit `634efd3d`. Confirmar a leitura correcta ("1 escritório já tem" / "N escritórios já têm"), nao a string antiga "escritório(s)".

## User Setup Required

Nenhuma variavel de ambiente nova, nenhum servico externo, nenhuma dependencia nova (`git diff --stat web/package.json` restrito ao bloco `scripts`, confirmado). Para a Task 3 (verificacao humana pendente), o operador precisa de `backend/.env` valido, PostgreSQL acessivel, e acesso de browser/SQL — nenhum requisito novo introduzido por este plano alem dos ja documentados em `CLAUDE.md`.

## Next Phase Readiness

- MOLD-01..04 tem cobertura de codigo e de gate estrutural completa. O unico item em falta para fechar a Fase 125 e a Task 3 (verificacao humana ao vivo), que fica pendente para uma sessao com acesso a browser e base de dados -- ver seccao dedicada acima.
- `deferred-items.md` regista um debito tecnico pre-existente (drift LexCV→ALCv em dois gates de fases anteriores) que nao bloqueia o fecho desta fase mas devia ser corrigido numa fase futura ou como ajuste standalone.
- `backend/migrations/126-add-tenant-role-tables.sql` continua pendente de execucao manual em producao (repetido de `125-05-SUMMARY.md`, nenhuma alteracao de estado nesta plano).

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-21*

## Self-Check: PASSED

- `web/src/schemas/moldes.ts` — FOUND
- `web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx` — FOUND
- `web/scripts/verify-consola-moldes.mjs` — FOUND
- `.planning/phases/LEXCV-125-moldes-da-plataforma-e-provisionamento/deferred-items.md` — FOUND
- Commit `9db39baf` — FOUND em `git log --oneline --all`
- Commit `30057b58` — FOUND em `git log --oneline --all`

Nenhum item em falta. (A Task 3, checkpoint:human-verify, esta deliberadamente pendente -- ver secao "Verificacao Humana Pendente" acima -- e nao entra neste self-check porque nao produz nenhum artefacto verificavel nesta run.)
