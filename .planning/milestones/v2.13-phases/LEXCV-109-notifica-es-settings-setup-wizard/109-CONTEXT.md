# Phase 109: Notificações / Settings / Setup Wizard - Context

**Gathered:** 2026-07-17
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

O menu de utilizador na topbar (avatar+nome, hoje um `<Link>` simples para `/settings`) ganha um `DropdownMenu` real com Perfil/Configurações/Terminar sessão. O contador de não-lidas do sino de notificações (`notification-bell.tsx`, hoje um `<span>` manual) migra para o componente oficial `Badge`. O wizard `/setup` ganha um indicador `Progress` linear derivado de um pequeno estado de fase local. O `Popover` do sino de notificações não é tocado — mantém-se exatamente como está, por ser já a composição correta (evita o anti-padrão de acessibilidade "DropdownMenu para lista com múltiplos controlos interativos"). Cobre NTF-28, NTF-29, NTF-30.

</domain>

<decisions>
## Implementation Decisions

### Menu de Utilizador (DropdownMenu)
- Itens: Perfil, Configurações, separador, Terminar sessão — consolidando num único `DropdownMenu` o link `/settings` e o botão de logout que hoje existem como elementos estruturalmente independentes
- As 2 instâncias duplicadas (sidebar footer desktop `dashboard-shell.tsx:294-311`/`:155-162`, Sheet mobile `:240-247`) são unificadas num único subcomponente partilhado `UserMenu`, consumido por ambas
- O bloco inteiro (avatar+nome) torna-se `DropdownMenuTrigger asChild`, deixando de navegar diretamente para `/settings` ao clicar — a navegação passa a acontecer via o item "Configurações" dentro do menu
- Reutilizar os ícones já importados (`Settings`, `LogOut` de lucide-react) e a copy exata já existente ("Configurações", "Terminar sessão")

### Badge no contador do sino
- Nova classe/variant custom no `Badge` mantendo `bg-red-500 text-white` via `className` override (a variant `red` existente no `Badge` é mais suave, não substitui o vermelho vibrante atual)
- Mesmo `Badge`, apenas o `children` muda ("!" erro vs número/"9+" normal) e a cor muda condicionalmente (`bg-slate-400` erro vs `bg-red-500` normal), exatamente como a lógica atual
- Mantém exatamente o posicionamento/tamanho atual (`absolute -top-0.5 -right-0.5 h-4 w-4`) via `className` passado ao `Badge`, sobrepondo o padding/tamanho default do primitivo
- Copy/cap mantidos exatamente ("9+", "!")

### Progress no wizard /setup
- Percentagem derivada de um pequeno enum de fase local (`idle` → 33%, `submitting` → 66%, `success` → 100%), mapeado a partir de `form.formState.isSubmitting`/`successMessage`/`serverError` já existentes — sem novo estado de negócio
- Sempre visível desde o início (mesmo em 33% antes de submeter), dando contexto do wizard de 3 fases
- Substitui o bloco de texto "Checklist" estático (`setup/page.tsx:262-269`) pelo `Progress`, mantendo as 3 fases nomeadas como legenda/labels associados ao indicador (não se perde informação)

### Claude's Discretion
- Nome exato do enum/estado de fase local introduzido no wizard `/setup` (ex.: `wizardPhase`, `progressPhase`)
- Nome exato do novo subcomponente `UserMenu` e a sua localização exata dentro de `web/src/components/shared/` (ou ficheiro próprio)
- Detalhes exatos de markup do `DropdownMenuSeparator`/`DropdownMenuLabel` (se usados) entre os itens

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/dropdown-menu.tsx` — `DropdownMenu`/`DropdownMenuTrigger`/`DropdownMenuContent`/`DropdownMenuItem`/etc. já instalado (Phase 101), zero consumidores em `web/src` até agora — esta fase é a primeira utilização real
- `web/src/components/ui/badge.tsx` — `Badge` com variants `default|secondary|outline|blue|green|amber|red|purple|gray`, já usado no próprio `notification-bell.tsx:36` para o chip de categoria (import já presente no ficheiro)
- `web/src/components/ui/progress.tsx` — `Progress` (Radix, prop `value` 0-100), já usado em Documentos (Phase 107) para barras de upload

### Established Patterns
- Fase 102 estabeleceu o `Popover` do sino como composição já correta (`notification-bell.tsx`) — não tocar
- Fases 105-108 estabeleceram a correção `permissions.isFetched` para RBAC — **não aplicável aqui**: nenhum dos 3 ficheiros desta fase (`dashboard-shell.tsx`, `notification-bell.tsx`, `setup/page.tsx`) usa esse hook; `dashboard-shell.tsx` usa `useMe()`+`hasPermission()` diretamente para filtrar a navegação (linha 89), um padrão diferente — fora do âmbito desta migração de componentes

### Integration Points
- `web/src/components/shared/dashboard-shell.tsx` (topbar user menu — sidebar footer desktop + Sheet mobile, 2 instâncias hoje duplicadas)
- `web/src/components/shared/notification-bell.tsx` (contador de não-lidas, linhas 88-95)
- `web/src/app/setup/page.tsx` (wizard, bloco "Checklist" linhas 262-269, sinais de fase existentes: `form.formState.isSubmitting`, `successMessage`, `serverError` linhas 31-32/296-298)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Adicionar um guard de loading (`isFetched`-style) ao padrão `useMe()`+`hasPermission()` de `dashboard-shell.tsx` (hoje sem guard, navegação pode aparecer vazia brevemente antes de `me` resolver) — fora do âmbito desta fase (padrão diferente do `permissions.isFetched` já estabelecido, não um drop-in fix); candidato a fase/tarefa futura se o gap se mostrar visível na prática

</deferred>
