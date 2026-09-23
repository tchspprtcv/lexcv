---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 05
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, accessibility, rbac]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 04)
    provides: "web/src/types/platform-moldes.ts (MoldePermissao, MoldeSummary, MoldesConsola, MoldesUpdateRequest) e web/src/hooks/use-platform-moldes.ts (useMoldes, useUpdateMoldes)"
provides:
  - "web/src/app/(dashboard)/plataforma/moldes/page.tsx -- rota /plataforma/moldes completa: guarda de página, banner âmbar permanente de não-propagação, matriz permissão × molde acessível e dinâmica, fluxo de gravação com AlertDialog obrigatório"
affects: [125-06-painel-de-criacao-de-molde]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reinicialização de estado local por comparação de referência do payload aplicado em vez de useEffect+setState (regra react-hooks/set-state-in-effect do projecto), generalização do padrão localRolePermissions do RbacTab para um Record<number, Set<string>>"
    - "Aviso de não-propagação em três camadas independentes (banner permanente + badge de contagem por coluna + AlertDialog obrigatório listando só os moldes alterados) em vez de um único aviso pontual"
    - "Correcção de acessibilidade de tabela sobre um precedente existente (RbacTab): scope=\"col\"/scope=\"row\" e aria-label por checkbox, sem alterar o precedente em si"

key-files:
  created:
    - "web/src/app/(dashboard)/plataforma/moldes/page.tsx"
  modified: []

key-decisions:
  - "O texto do diálogo de confirmação para escritoriosInstanciados > 0 usa literalmente \"escritório(s)\" (sem pluralização gramatical), reproduzindo a citação exacta do texto do plano/UI-SPEC -- ao contrário do badge da coluna da matriz, que pluraliza correctamente (\"1 escritório\"/\"5 escritórios\") por instrução explícita do plano. Ver Deviations."
  - "O botão 'Guardar Alterações' e o AlertDialog de confirmação foram construídos como Task 2, sobre o ficheiro já criado pela Task 1 (guarda, banner, matriz, estado local de edição) -- dois commits atómicos sobre o mesmo ficheiro novo, em vez de um único commit, para respeitar a divisão de tarefas do plano."

patterns-established:
  - "Matriz permissão × molde com scope=\"col\"/scope=\"row\" e aria-label por checkbox -- referência para qualquer matriz futura no mesmo estilo (RbacTab não foi alterado; só a nova matriz corrige a lacuna)"

requirements-completed: [MOLD-02, MOLD-03]

# Metrics
duration: ~20min
completed: 2026-09-21
---

# Phase 125 Plan 05: Consola de Moldes — Matriz e Fluxo de Gravação Summary

**Ecrã `/plataforma/moldes` completo: matriz permissão × molde acessível e dinâmica, com o aviso de não-propagação em três camadas (banner permanente, badge de contagem por coluna, `AlertDialog` obrigatório) que garante que ninguém confunde snapshot com propagação ao editar um molde.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-20T23:27:07-01:00 (aprox., após o commit de metadados do Plan 04)
- **Completed:** 2026-09-20T23:45:35-01:00
- **Tasks:** 2/2
- **Files modified:** 1 (novo)

## Accomplishments
- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (403 linhas) criado do zero: guarda de página na ordem correcta (`!me.isFetched` antes do teste de `PLATAFORMA_ADMIN`, WR-03), cabeçalho com back-arrow para `/plataforma`, `Card` estrutural idêntico a `relatorio/page.tsx`.
- Banner de não-propagação (`TriangleAlert`, âmbar) permanentemente visível no topo do `CardContent`, nunca condicionado a nenhum estado de gravação -- camada 1 de 3 do aviso.
- Matriz permissão × molde dinâmica (colunas de `data.moldes`, não um array hardcoded), com filtro defensivo de `PLATAFORMA_ADMIN`, sem `isAdminRow` nem badge "Gerido pela Plataforma" -- nenhuma linha é imutável, esta página só é alcançada por `PLATAFORMA_ADMIN`.
- Badge de contagem de escritórios instanciados por coluna (`amber` se > 0, `gray`/"0 escritórios" caso contrário) -- camada 2 de 3, sempre visível antes de qualquer decisão de edição.
- Três correcções de acessibilidade sobre a lacuna real do `RbacTab`: `scope="col"` nos cabeçalhos de coluna, `<th scope="row">` com `text-left` explícito na célula de rótulo de permissão, e `aria-label={`${permissao.nome} — ${molde.nome}`}` em cada checkbox.
- Estado local de edição (`Record<number, Set<string>>`) reinicializado por comparação de referência do payload aplicado (não `useEffect`+`setState`), evitando apagar edições não gravadas quando a query refaz fetch.
- Botão "Guardar Alterações" (azul, `CardHeader`) desactivado sem diff local; nunca chama a mutação directamente -- abre sempre o `AlertDialog`.
- `AlertDialog` de confirmação (camada 3 de 3): lista apenas os moldes efectivamente alterados (diff por conjunto de chaves, não ordem de array), com uma linha por molde -- texto âmbar para `escritoriosInstanciados > 0`, texto neutro/slate para `= 0` -- e acção "Confirmar e Gravar" em âmbar, existente só dentro do diálogo.
- `useUpdateMoldes()` ligado exclusivamente ao `AlertDialogAction`; falha na gravação mantém o diálogo aberto (convenção já estabelecida no `AlertDialog` de estado do tenant em `plataforma/page.tsx`), sucesso mostra `toast.success("Moldes atualizados com sucesso.")` e fecha o diálogo.
- Nenhum placeholder de "Criar Molde" -- confirmado por grep (`0` ocorrências) -- esse botão e o painel de criação são deliberadamente do Plan 06.

## Task Commits

Each task was committed atomically:

1. **Task 1: Rota, guarda de página, banner de não-propagação e matriz acessível** - `53c88289` (feat)
2. **Task 2: Fluxo de gravação com confirmação obrigatória e diff apenas dos moldes alterados** - `39d0c77b` (feat)

**Plan metadata:** (a ser commitado separadamente pelo orquestrador, junto do SUMMARY.md)

## Files Created/Modified
- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (novo, 403 linhas) - rota completa: guarda de página, banner âmbar, matriz acessível permissão × molde com badges de contagem, `AlertDialog` de confirmação de gravação com diff apenas dos moldes alterados

## Decisions Made
- O texto do diálogo de confirmação para o caso `escritoriosInstanciados > 0` reproduz literalmente `"{N} escritório(s) já têm uma cópia própria deste molde..."` do plano/UI-SPEC, sem pluralização gramatical -- ao contrário do badge de contagem da coluna da matriz, que pluraliza correctamente (`1 escritório` / `5 escritórios`) porque o plano pede explicitamente "pluralização correcta para 1" só para o badge, não para a linha do diálogo. Ver Deviations para o porquê desta escolha ter sido confirmada por grep em vez de decidida por preferência.
- A Task 2 foi construída como um segundo commit sobre o ficheiro que a Task 1 já tinha criado, e não como parte de um único commit -- respeitando a divisão do plano em duas tarefas atómicas mesmo sendo o mesmo ficheiro `files_modified`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Comentário da Task 1 continha a substring literal "Criar Molde", contradizendo o próprio critério de aceitação da Task 1**
- **Found during:** Task 1 (verificação dos critérios de aceitação por grep, logo após escrever o comentário do slot do botão de gravação)
- **Issue:** O comentário explicativo do slot vazio no `CardHeader` mencionava literalmente `"Criar Molde"` para explicar o que não pertence ali, mas o próprio critério de aceitação da Task 1 exige `grep -c 'Criar Molde' ... devolve 0`.
- **Fix:** Reescrito o comentário para descrever a mesma exclusão sem o texto literal ("O botão de criação de molde é do Plan 06 e não pertence a este slot").
- **Files modified:** web/src/app/(dashboard)/plataforma/moldes/page.tsx
- **Verification:** `grep -c 'Criar Molde' ...` = `0`; `tsc`/`lint` continuaram limpos.
- **Committed in:** `53c88289` (parte do commit da Task 1)

**2. [Rule 1 - Bug] Comentário da Task 2 continha a substring literal "Guardar Alterações", fazendo esse critério de aceitação (exactamente 1 linha) falhar**
- **Found during:** Task 2 (verificação dos critérios de aceitação por grep, após acrescentar o comentário do `AlertDialog`)
- **Issue:** O comentário explicativo do `AlertDialog` citava entre aspas o texto "Guardar Alterações" do botão que o abre, duplicando a única ocorrência esperada (o texto visível do próprio botão).
- **Fix:** Reescrito o comentário para se referir ao botão sem citar o seu texto ("o botão azul que abre este diálogo").
- **Files modified:** web/src/app/(dashboard)/plataforma/moldes/page.tsx
- **Verification:** `grep -n 'Guardar Alterações' ...` devolve exactamente 1 linha (a do próprio botão).
- **Committed in:** `39d0c77b` (parte do commit da Task 2)

**3. [Rule 1 - Bug] Texto do diálogo com pluralização gramatical não batia com o critério de aceitação que exige a string literal "escritório(s)"**
- **Found during:** Task 2 (verificação dos critérios de aceitação por grep)
- **Issue:** A primeira implementação pluralizava correctamente a linha do diálogo (`1 escritório` / `5 escritórios`, via uma função `pluralizarEscritorios`), espelhando a lógica do badge da matriz. O critério de aceitação da Task 2, porém, exige `grep -cE 'escritório\(s\) já têm uma cópia própria deste molde|...' ... devolve 2`, que só casa com a string literal `"escritório(s)"` -- não com texto gramaticalmente pluralizado. Reexaminando o texto do plano, a acção da Task 2 cita esta linha **entre aspas, literalmente**, com "(s)" incluído, ao contrário da instrução do badge (Task 1), que pede explicitamente "pluralização correcta para 1". Ou seja, o plano distingue deliberadamente os dois casos: o badge pluraliza, a linha do diálogo reproduz a citação literal.
- **Fix:** Alterada a linha do diálogo para usar literalmente `${molde.escritoriosInstanciados} escritório(s) ...`, e removida a função `pluralizarEscritorios` (ficou sem utilização depois desta correcção, já que o badge da Task 1 já usava um ternário inline próprio e não foi alterado).
- **Files modified:** web/src/app/(dashboard)/plataforma/moldes/page.tsx
- **Verification:** `grep -cE 'escritório\(s\) já têm uma cópia própria deste molde|0 escritórios instanciaram este molde ainda' ...` = `2`; `tsc --noEmit`, `lint` e `build` continuaram limpos (sem função órfã).
- **Committed in:** `39d0c77b` (parte do commit da Task 2)

---

**Total deviations:** 3 auto-fixed (2 contradições textuais entre comentário explicativo e o próprio grep-gate da tarefa que o continha, 1 ambiguidade real entre "pluralização correcta" (badge) e "citação literal com (s)" (diálogo) resolvida a favor da leitura mais literal do texto do plano, mesma classe de achado já documentada nos Plans 03/04 desta fase)
**Impact on plan:** Nenhum impacto na garantia real do ecrã -- as três camadas do aviso de não-propagação, a matriz acessível e o fluxo de gravação obrigatório ficaram exactamente como o UI-SPEC descreve. Apenas texto de comentários e uma escolha de formatação de string foram ajustados para bater com os próprios critérios de aceitação do plano.

## Issues Encountered
- `pnpm build` falhou inicialmente com `Error: BACKEND_API_ORIGIN is required` porque este worktree não tem `web/.env.local` (só `web/.env.example`). Não é um defeito do código desta tarefa -- é um requisito de ambiente documentado em `CLAUDE.md`/`web/AGENTS.md`. Contornado fornecendo as duas variáveis inline (`BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1`) apenas para o comando de verificação do build, sem criar nem commitar nenhum ficheiro `.env.local` novo.

## User Setup Required

None -- nenhuma variável de ambiente nova, nenhum serviço externo, nenhuma dependência nova (`git diff --stat web/package.json` vazio, confirmado). O `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` usados para verificar `pnpm build` já são exigidos pelo projecto para qualquer build local (ver `CLAUDE.md`), não uma necessidade introduzida por este plano.

## Next Phase Readiness

- `/plataforma/moldes` está completo e funcional para MOLD-02/MOLD-03: qualquer `PLATAFORMA_ADMIN` pode ver e editar a matriz permissão × molde, com o aviso de não-propagação impossível de ignorar.
- O Plan 06 pode acrescentar o botão "Criar Molde" no `CardHeader` (ao lado do já existente "Guardar Alterações") e o painel de criação inline, reaproveitando `moldesFiltrados`, `modulos` e o padrão de `isFormOpen`/troca de `Card` já usado em `plataforma/page.tsx` -- nenhum placeholder foi deixado no lugar desse botão neste plano.
- `useCreateMolde()` (já criado no Plan 04) continua por consumir; será o hook do Plan 06.
- Nenhum componente novo em `components/ui/`; nenhuma dependência nova instalada.

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-21*

## Self-Check: PASSED

- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` — FOUND
- Commit `53c88289` — FOUND em `git log --oneline --all`
- Commit `39d0c77b` — FOUND em `git log --oneline --all`

Nenhum item em falta.
