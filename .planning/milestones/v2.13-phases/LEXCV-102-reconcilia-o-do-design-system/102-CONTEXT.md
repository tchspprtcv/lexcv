# Phase 102: Reconciliação do Design System - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

Os 14 componentes hand-rolled existentes (`button`, `dialog`, `alert-dialog`, `card`, `table`, `sheet`, `badge`, `input`, `label`, `popover`, `radio-group`, `switch`, `textarea`) estão reconciliados com o registo oficial do shadcn (via `add <component> --diff`, nunca overwrite cego) sem perder nenhuma variante/prop customizada, e nenhum dos 38 ficheiros consumidores existentes quebra. `Tooltip` adicionado a botões icon-only (sidebar colapsada + ações de linha), com `TooltipProvider` montado uma vez na raiz. Cobre DSR-01, DSR-02, DSR-03.

</domain>

<decisions>
## Implementation Decisions

### Estratégia de Reconciliação
- Variantes customizadas (ex.: `gray` do badge) reintroduzidas manualmente no ficheiro gerado pela CLI, verificadas contra os call sites conhecidos via grep antes de considerar a task concluída
- `sheet.tsx` deixado como está estruturalmente (já compatível com o shape da CLI, per pesquisa da milestone); só normalização de cores hardcoded para tokens, se aplicável
- `rounded-none` hardcoded (dialog.tsx/card.tsx) substituído pelo utilitário tokenizado (`rounded-lg`, que resolve a `0` via `--radius: 0rem`) em vez do literal — propaga automaticamente se `--radius` mudar

### Rollout do Tooltip (DSR-03)
- Escopo inicial: ícones da sidebar colapsada + ações de linha icon-only (exatamente o texto de DSR-03, não alargar a mais superfícies)
- `delayDuration` do `TooltipProvider`: default do shadcn (700ms)
- Comportamento touch: confiar no fallback nativo do Radix Tooltip (tap-and-hold/foco), sem desativar em touch

### Rigor de Verificação e Limpeza Herdada da Fase 101
- Checkpoint visual humano (claro/escuro) obrigatório após a reconciliação, mesmo protocolo da Fase 101 (browser + getComputedStyle, não só grep/contagem de blocos) — esta fase toca a superfície renderizada de 14 componentes muito usados
- Resolver também as duplicações encontradas na revisão de código da Fase 101: `buttonVariants` duplicado entre `button.tsx`/`calendar.tsx` (esta fase é dona do `button.tsx`, exportar a variante e reutilizar em `calendar.tsx`), e aliasing inconsistente do `Slot` entre `breadcrumb.tsx`/`button.tsx` (uniformizar)
- Corrigir também `"shadcn": "^4.13.0"` de `dependencies` para `devDependencies` em `web/package.json` (achado da auditoria UI da Fase 101, correção trivial de uma linha)

### Claude's Discretion
- Ordem exata de reconciliação dos 14 componentes dentro do plano (recomendação: `button`/`card` primeiro, maior raio de impacto)
- Texto exato dos tooltips (em português, seguindo as convenções de copy já existentes na app)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `.planning/research/PITFALLS.md` já documenta o protocolo "diff-first, never blind overwrite" e a lista completa dos 14 ficheiros
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-REVIEW.md` e `101-UI-REVIEW.md` já documentam achados concretos herdados: `buttonVariants` duplicado, aliasing `Slot` inconsistente, `dark:bg-[#020617]` hardcoded em 3 ficheiros, `shadcn` mal colocado em `dependencies`
- Fase 101 já entregou os 16 primitivos novos (incluindo `Tooltip`) e os tokens semânticos completos em `globals.css` de ambas as apps — esta fase consome essa fundação, não a recria

### Established Patterns
- Protocolo "diff-first": `shadcn add <component> --diff`, nunca `--overwrite` cego, reconciliar um de cada vez
- Checkpoint humano `type="checkpoint:human-verify"` com verificação via browser real (getComputedStyle), não apenas grep — padrão estabelecido na Fase 101 após o bug de cascata CSS

### Integration Points
- `web/src/components/ui/*` (14 ficheiros existentes a reconciliar + `tooltip.tsx` já existe da Fase 101, só falta o `TooltipProvider` na raiz)
- `web/src/app/layout.tsx` (montagem do `TooltipProvider`)
- Componentes da sidebar/topbar com ícones colapsados e ações de linha icon-only (a identificar durante o planeamento)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima — pesquisa e revisões de código das fases anteriores já fornecem o detalhe necessário.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
