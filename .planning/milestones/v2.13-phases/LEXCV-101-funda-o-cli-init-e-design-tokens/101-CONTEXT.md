# Phase 101: Fundação — CLI Init e Design Tokens - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning
**Mode:** Smart discuss (infrastructure phase — grey areas skipped)

<domain>
## Phase Boundary

`web/` e `webpage/` têm uma fundação de design system corretamente inicializada (scaffolded pela CLI oficial, base Radix, tokenizada, com todos os primitivos que as fases seguintes vão precisar) sem que nenhuma página visível mude ainda. Cobre FND-01 a FND-08: `shadcn init -b radix` em ambas as apps, tokens semânticos em `globals.css`, ~15 primitivos em falta adicionados, migração `radix-ui` unificada, pin de `react-day-picker@9.14.0`, troca `tailwindcss-animate` → `tw-animate-css`, adoção do Sonner.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Fase de infraestrutura pura — critérios de sucesso são todos técnicos (ficheiro existe, compila, versão fixada) e nenhum comportamento visível ao utilizador é descrito ("sem que nenhuma página visível mude ainda"). Todas as escolhas de implementação ficam ao critério do executor, seguindo o goal do ROADMAP, os critérios de sucesso e as decisões já tomadas nesta sessão (ver REQUIREMENTS.md/SUMMARY.md de pesquisa):
- `shadcn init -b radix` explícito em ambas as apps (nunca o novo default Base UI)
- `web/` primeiro; `webpage/` recebe as mesmas respostas copiadas manualmente (não re-executar o wizard lá)
- `shadcn migrate radix` corrido (decisão já tomada: migrar agora, não manter ponte)
- `--dry-run` antes de `init` real, para confirmar que só existe um bloco `:root`/`.dark` (evitar corrupção silenciosa de tema)
- `--background`/`--foreground` restaurados aos hex já validados; `--radius`/`--primary` definidos deliberadamente para a identidade institucional

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `.planning/research/STACK.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `SUMMARY.md` já documentam exatamente os comandos/flags/versões a usar
- 14 componentes hand-rolled existentes em `web/src/components/ui/` (não tocados nesta fase — reconciliação é Phase 102)
- `web/src/lib/utils.ts` (`cn()` helper) e `tsconfig.json` (`@/*` → `./src/*`) já compatíveis com os aliases default do shadcn

### Established Patterns
- Tailwind v4 CSS-first (`@theme` em `globals.css`, sem `tailwind.config.ts`) em ambas as apps
- `web/` e `webpage/` são apps pnpm independentes (sem `pnpm-workspace.yaml` raiz) — dois `components.json` distintos, não um package partilhado

### Integration Points
- `globals.css` de ambas as apps
- `package.json` de ambas as apps (novas deps: `@tanstack/react-table` fica para Phase 104, não aqui; `tw-animate-css`, `sonner` entram aqui)
- Raiz de layout de ambas as apps (montagem do `<Toaster />`)

</code_context>

<specifics>
## Specific Ideas

Nenhuma — fase de infraestrutura, decisões técnicas já fechadas pela pesquisa de milestone e por REQUIREMENTS.md.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
