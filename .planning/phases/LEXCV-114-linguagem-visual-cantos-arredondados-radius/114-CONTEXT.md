# Phase 114: Linguagem Visual — Cantos Arredondados (`--radius`) - Context

**Gathered:** 2026-07-21
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous)

<domain>
## Phase Boundary

A aplicação deixa de ter cantos retos e passa a ter cantos arredondados, de forma consistente em todos os componentes e em ambos os temas — reversão deliberada da identidade "documento institucional" estabelecida na v2.13. Mudança de um único token CSS já isolado (`--radius`), consumido por todos os componentes através dos derivados `--radius-sm/md/lg/xl/2xl/3xl/4xl`.

</domain>

<decisions>
## Implementation Decisions

### Valor e Âmbito
- `--radius: 0.5rem` (8px) — valor moderado, o "meio-termo" mais comum em shadcn/ui
- Muda em **ambas** as apps: `web/src/app/globals.css` (dashboard interno) **e** `webpage/src/app/globals.css` (landing pública) — ambas partilham hoje a mesma estrutura de tokens (`--radius: 0rem` idêntico nas duas), mudar só uma criaria inconsistência visual entre o site público e a app interna
- Verificação visual prioriza uma amostra representativa (Dashboard, Clientes, Processos incl. pesquisa global + filtro Estado promovido, Login, um Dialog/Sheet aberto) em vez de percorrer exaustivamente todas as ~30 rotas

### Claude's Discretion
- `rounded-full` explícito (avatares, badges circulares) não deriva de `--radius` (é um valor Tailwind fixo) — não muda, sem necessidade de decisão
- Elementos estruturais de layout (sidebar, topbar) tipicamente não usam classes de radius hoje (são painéis full-bleed, não "cartões") — confirmar durante a execução se algum caso pontual precisa de ajuste

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/globals.css:42-48,75` — `--radius` + 7 derivados já calculados via `calc(var(--radius) * N)`, um único ponto de mudança
- `webpage/src/app/globals.css:41-47,74` — estrutura idêntica, cópia separada (não partilhada via package)

### Established Patterns
- Padrão já usado na v2.13 Phase 101 (Fundação — CLI Init e Design Tokens) para o `radius: 0` original — esta fase é o mesmo tipo de mudança, valor oposto

### Integration Points
- Apenas 2 ficheiros a modificar: `web/src/app/globals.css` e `webpage/src/app/globals.css` (linha do `--radius:` em cada)
- Nenhum componente individual precisa de edição — todos já consomem os tokens derivados

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do valor `0.5rem` já acordado.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
