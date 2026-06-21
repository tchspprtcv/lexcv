# Phase 53: Shell Responsivo - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning

<domain>
## Phase Boundary

O layout principal da aplicação (DashboardShell) adapta-se a ecrãs mobile — a sidebar fixa de 270px dá lugar a um drawer overlay controlado por botão hamburger, a top bar simplifica-se em mobile, e uma bottom navigation bar oferece acesso rápido aos módulos. Desktop fica sem alteração. Tudo em `web/src/components/shared/dashboard-shell.tsx` e possivelmente um novo componente `BottomNav`.

</domain>

<decisions>
## Implementation Decisions

### Drawer Sidebar
- Usar componente `Sheet` do shadcn/ui (`npx shadcn add sheet`) — padrão já usado no resto da app
- Drawer na posição left (mesmo lado que sidebar desktop) — consistência visual
- Clicar overlay fecha drawer automaticamente
- Animação slide-from-left (padrão Sheet)

### Bottom Navigation Bar
- 5 módulos: Dashboard, Clientes, Processos, Agenda, Documentos
- Estilo ícone + label pequena abaixo (estilo iOS) — legível sem hover
- Item ativo com destaque azul (`text-blue-400`) — mesmo tom da sidebar desktop
- Visível apenas em mobile (< 768px / `md:hidden`)

### Top Bar Mobile
- Layout: hamburger (esq.) + nome instituição (centro) + notificações+perfil (dir.)
- Campo de pesquisa oculto em mobile — ícone lupa que abre overlay (ou apenas oculto na v1)
- Manter altura `h-16` — toque confortável e consistente
- Nome da instituição visível em mobile (truncate se necessário)

### Breakpoints e Estado
- Breakpoint de corte: `md` (768px) — Tailwind padrão, já parcialmente usado
- Estado do drawer: `useState` local no DashboardShell — simples, sem contexto global
- Não persistir estado do drawer — fecha ao refresh (comportamento natural)
- Sidebar desktop: mantém-se fixa 270px em `md:` e acima, sem qualquer alteração

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/shared/dashboard-shell.tsx` — ficheiro alvo principal; já tem `usePathname` para active state
- `web/src/components/ui/` — primitivos shadcn; `Sheet` a adicionar via CLI
- `Building2`, `LogOut`, `Settings`, ícones de navegação já importados do lucide-react
- `Menu` do lucide-react para o hamburger (a adicionar ao import)
- `NAV` array já definido com os 5 módulos principais + ícones

### Established Patterns
- Breakpoint `md:` já usado: `hidden md:flex` na top bar para o nome da instituição
- Active state com `pathname === item.href` já implementado
- Classes de cores: `bg-slate-950`, `text-blue-400`, `hover:bg-slate-900` — padrão da sidebar
- `cn()` utility para classes condicionais já importado

### Integration Points
- `DashboardShell` envolve todo o conteúdo do dashboard — alteração afeta todas as páginas
- `pathname` já disponível via `usePathname()` — usar para bottom nav active state
- `useMe()` já presente para nome/logo da instituição na top bar
- `NotificationBell` e `ThemeToggle` a mover/adaptar para mobile top bar

</code_context>

<specifics>
## Specific Ideas

- NAV array atual: Dashboard, Clientes, Processos, Agenda, Documentos, Financeiro — bottom nav usa os 5 primeiros (Dashboard até Documentos)
- Sidebar no drawer mobile deve ser idêntica à sidebar desktop em conteúdo (mesmo NAV, mesmo user card no fundo)
- Bottom nav fica fixo na parte inferior com `fixed bottom-0 left-0 right-0` e `z-50` para não sobrepor conteúdo

</specifics>

<deferred>
## Deferred Ideas

- Campo de pesquisa em overlay mobile — deixar para uma fase futura (complexidade extra, não é core da responsividade)
- Sidebar colapsável em desktop — fora de scope deste milestone
- Gestos swipe para abrir/fechar drawer — fora de scope (Out of Scope do milestone)

</deferred>
