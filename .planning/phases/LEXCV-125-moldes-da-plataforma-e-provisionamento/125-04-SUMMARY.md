---
phase: 125-moldes-da-plataforma-e-provisionamento
plan: 04
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, rbac]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento (Plan 03)
    provides: "GET/PUT/POST /api/v1/platform/moldes, MoldesConsolaResponse com escritoriosInstanciados"
provides:
  - "web/src/types/platform-moldes.ts -- tipos MoldePermissao, MoldeSummary, MoldesConsola, MoldesUpdateRequest, MoldeCreateRequest, espelhando o wire format real"
  - "web/src/hooks/use-platform-moldes.ts -- useMoldes, useUpdateMoldes, useCreateMolde via apiFetch, invalidacao de cache, sem setQueryData"
  - "Botao de entrada 'Gerir Moldes' em /plataforma, antes de 'Ver Relatorio'"
affects: [125-05-tela-consola-de-moldes, 125-06-painel-de-criacao-de-molde]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Camada de dados antes do ecra: tipos + hooks TanStack Query escritos e verificados contra o DTO real do backend, antes de qualquer componente de consumo existir"
    - "Botao de entrada como unico artefacto de UX de uma rota nova ainda sem ecra proprio -- torna a rota alcancavel sem construir a pagina de destino"

key-files:
  created:
    - web/src/types/platform-moldes.ts
    - web/src/hooks/use-platform-moldes.ts
  modified:
    - "web/src/app/(dashboard)/plataforma/page.tsx"

key-decisions:
  - "Tipos verificados directamente contra os DTOs Java (MoldesConsolaResponse, MoldesUpdateRequest, MoldeCreateRequest) em vez de confiar no 125-UI-SPEC.md ou no plano -- escritoriosInstanciados e long primitivo (nunca opcional), id e Integer (mapeado para number), descricao e String nullable (mapeado para string | null)"
  - "Comentario de cabeçalho do ficheiro de hooks reescrito para nao conter a substring literal 'setQueryData', preservando a mesma proibicao semantica -- o proprio ficheiro-analogo use-platform-admin.ts falha o gate de aceitacao do plano tal como especificado (ver Deviations)"

patterns-established:
  - "Ficheiro de hooks de consola de plataforma: chave de query const X_LIST_KEY, useQuery com enabled: typeof window !== \"undefined\" e staleTime: 30_000, cada mutacao invalida a mesma chave, nunca setQueryData"

requirements-completed: [MOLD-02, MOLD-04]

# Metrics
duration: 26min
completed: 2026-09-21
---

# Phase 125 Plan 04: Moldes da Plataforma e Provisionamento - Tipos, Hooks e Botão de Entrada Summary

**Tipos TypeScript e hooks TanStack Query para `/platform/moldes` verificados linha a linha contra os DTOs Java já enviados no Plan 03, mais o botão "Gerir Moldes" que torna `/plataforma/moldes` alcançável a partir de `/plataforma`.**

## Performance

- **Duration:** ~26 min
- **Started:** 2026-09-21T00:00:00Z (aprox.)
- **Completed:** 2026-09-21T00:26:06Z
- **Tasks:** 2/2
- **Files modified:** 3

## Accomplishments
- `web/src/types/platform-moldes.ts` criado com 5 `export type` planos (`MoldePermissao`, `MoldeSummary`, `MoldesConsola`, `MoldesUpdateRequest`, `MoldeCreateRequest`), verificados contra `MoldesConsolaResponse.java`, `MoldesUpdateRequest.java` e `MoldeCreateRequest.java` reais em vez de qualquer documento — `escritoriosInstanciados: number` é campo obrigatório (o Java é `long` primitivo, nunca `null`).
- `web/src/hooks/use-platform-moldes.ts` criado copiando literalmente o padrão de `use-platform-admin.ts`: `useMoldes()` (GET, `staleTime: 30_000`, `enabled` guardado por `typeof window`), `useUpdateMoldes()` e `useCreateMolde()` (mutações via `apiFetch`, ambas invalidam `MOLDES_LIST_KEY` em `onSuccess`, nenhuma escreve na cache directamente).
- Botão "Gerir Moldes" (outline, ícone `LayoutTemplate`, mesmas classes do botão "Ver Relatório" vizinho) acrescentado ao cabeçalho de "Tenants Registados" em `/plataforma`, ligado a `/plataforma/moldes`, posicionado antes de "Ver Relatório" — ordem `[Gerir Moldes] [Ver Relatório] [Criar Tenant]` conforme UI-SPEC secção 1.

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipos e hooks da consola de moldes** - `cef49939` (feat)
2. **Task 2: Botão de entrada "Gerir Moldes" no cabeçalho de /plataforma** - `1f2d0acb` (feat)

**Plan metadata:** (a ser commitado separadamente por este agente, junto do SUMMARY.md)

## Files Created/Modified
- `web/src/types/platform-moldes.ts` (novo) - 5 tipos planos do payload de `/platform/moldes`, com comentário de cabeçalho a fixar a inversão key/nome e a natureza não-opcional de `escritoriosInstanciados`
- `web/src/hooks/use-platform-moldes.ts` (novo) - `useMoldes`, `useUpdateMoldes`, `useCreateMolde`, todos via `apiFetch`, invalidação de `MOLDES_LIST_KEY` em cada mutação
- `web/src/app/(dashboard)/plataforma/page.tsx` - import de `LayoutTemplate` acrescentado ao import existente de `lucide-react`; novo `Button asChild` com `Link href="/plataforma/moldes"` inserido antes do botão "Ver Relatório"; nenhuma outra linha alterada

## Decisions Made
- Os tipos foram verificados directamente contra os ficheiros Java reais (`MoldesConsolaResponse.java`, `MoldesUpdateRequest.java`, `MoldeCreateRequest.java`), não contra o UI-SPEC nem o texto do plano — confirmando `escritoriosInstanciados` como `long` primitivo (nunca `null`/opcional), `id` como `Integer` (mapeado para `number` em TS, sem perda), e `descricao` como `String` sem anotação de não-nulidade (mapeado para `string | null`, conforme o plano já previa).
- `MoldeCreateRequest`/`MoldesUpdateRequest` do lado Java usam `@Getter/@Setter` simples (não `record`), mas isso é um detalhe de implementação do lado do servidor sem qualquer impacto na forma JSON — os tipos TS permanecem planos como o plano pedia.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Comentário de cabeçalho de `use-platform-moldes.ts` continha a substring literal `setQueryData`, contradizendo o próprio critério de aceitação do plano**
- **Found during:** Task 1 (verificação dos critérios de aceitação por grep, após copiar o comentário de cabeçalho do ficheiro-analogo)
- **Issue:** O plano pedia, no mesmo parágrafo: (a) copiar literalmente o comentário de cabeçalho de `use-platform-admin.ts`, que menciona `setQueryData` para explicar a proibição; (b) um critério de aceitação `grep -c 'setQueryData' ... devolve 0`. A cópia literal do comentário-fonte faz o próprio `use-platform-admin.ts` falhar esse mesmo grep (confirmado: `grep -c 'setQueryData' web/src/hooks/use-platform-admin.ts` = 1), então copiar "literalmente" e passar o gate são mutuamente exclusivos tal como o plano os descreve.
- **Fix:** Reescrita a frase final do comentário para descrever a mesma proibição sem o nome literal da função ("nenhuma mutação escreve directamente na cache do TanStack Query fora do fluxo de invalidação"), preservando o significado e a regra sem o texto que o gate conta.
- **Files modified:** web/src/hooks/use-platform-moldes.ts
- **Verification:** `grep -c 'setQueryData' web/src/hooks/use-platform-moldes.ts` = `0`; `pnpm exec tsc --noEmit` continuou exit 0 após a alteração.
- **Committed in:** `cef49939` (parte do commit da Task 1)

---

**Total deviations:** 1 auto-fixed (1 contradição textual entre a instrução de "copiar literalmente" e o critério de aceitação do próprio plano, mesma classe de achado documentada no Plan 03)
**Impact on plan:** Nenhum impacto na garantia real (a proibição de escrita directa na cache permanece documentada e verificada por grep de `setQueryData`/`invalidateQueries`); apenas a forma de referência ao nome da função mudou.

## Issues Encountered

- O bash `grep` deste ambiente devolveu falso-negativo em `grep -n 'href="/plataforma/moldes"'` sobre o ficheiro de página (linha 170, confirmada existente por leitura directa e pela ferramenta `Grep` dedicada, que encontrou a linha correctamente). Reconfirmado com a ferramenta `Grep` conforme a nota de tooling do prompt — a linha existe e precede `href="/plataforma/relatorio"` (linha 176), cumprindo o critério de ordem.

## User Setup Required

None — nenhuma variável de ambiente nova, nenhum serviço externo, nenhuma dependência nova (`git diff --stat web/package.json` vazio, confirmado).

## Next Phase Readiness

- `web/src/types/platform-moldes.ts` e `web/src/hooks/use-platform-moldes.ts` estão prontos para consumo directo pelo Plan 05 (ecrã `/plataforma/moldes` — matriz de checkboxes e listagem) sem qualquer inferência de contrato a meio da implementação.
- `useCreateMolde` está pronto para o Plan 06 (painel de criação de molde); o Zod do formulário de criação foi deliberadamente deixado de fora deste plano, por pertencer a `schemas/` junto do painel que o usa.
- O botão "Gerir Moldes" já existe em `/plataforma`, mas aponta para uma rota (`/plataforma/moldes`) que ainda não tem página própria — isto é esperado (Plan 05 constrói o ecrã); até lá, clicar no botão resulta num 404 do Next.js, não numa falha silenciosa.
- Nenhum componente de `components/ui/` foi criado; nenhuma dependência nova foi instalada (`web/package.json` inalterado).

---
*Phase: 125-moldes-da-plataforma-e-provisionamento*
*Completed: 2026-09-21*

## Self-Check: PASSED

- `web/src/types/platform-moldes.ts` — FOUND
- `web/src/hooks/use-platform-moldes.ts` — FOUND
- `web/src/app/(dashboard)/plataforma/page.tsx` — FOUND (modificado, confirmado via `git diff --name-only a79957d5..HEAD`)
- Commit `cef49939` — FOUND em `git log --oneline --all`
- Commit `1f2d0acb` — FOUND em `git log --oneline --all`

Nenhum item em falta.
