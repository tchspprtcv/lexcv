# Phase 99: webpage/ — Nova App Next.js de Landing - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Criar uma nova aplicação Next.js 16 standalone `webpage/` (sibling de `backend/`/`web/` na raiz do monorepo) que serve a landing page pública completa em `/`, personalizada com nome/logo da tenant (via `GET /api/v1/public/branding` da Phase 98), verificada isoladamente nesta fase via `pnpm dev`/build próprios — a integração real com Caddy/routing/Docker fica para a Phase 100.

Esta fase cobre exclusivamente LP-03 a LP-12 (conteúdo, comportamento de gate de setup, CTA, responsividade/dark-light). Não modifica `web/` nem `backend/` (exceto consumir o endpoint já existente da Phase 98). Paralelizável com Phase 98.

</domain>

<decisions>
## Implementation Decisions

### Conteúdo & Tom
- Hero: título "Gestão jurídica completa para a sua instituição" + subtítulo "Clientes, processos, prazos e documentos — tudo num único painel, com isolamento total por tenant." (reflete o Core Value de PROJECT.md)
- Secção Funcionalidades/Módulos: uma frase curta por módulo (Clientes, Processos, Agenda/Prazos, Documentos, Financeiro, Notificações), refletindo capacidades reais já validadas em REQUIREMENTS.md — nunca descrições inventadas ou não implementadas
- Secção Prova Social/Confiança (LP-09): exatamente 4 bullets/cards — isolamento multi-tenant rigoroso, RBAC granular por perfil, trilha de auditoria, ecossistema institucional Cabo Verde/NOSi — nunca contadores/estatísticas fabricadas, nunca testemunhos/logótipos de clientes
- Contacto/Pedir Demonstração (LP-10): `mailto:contacto@lexcv.cv` com assunto pré-preenchido "Pedido de Demonstração — LexCV"

### Fetch de Branding & Estados de Erro
- `GET /api/v1/public/branding` é chamado **server-side**, diretamente no Server Component async de `page.tsx` — sem hook/TanStack Query. Isto evita CORS por completo (fetch server-to-server, nunca no browser) — **resolve definitivamente a preocupação WR-01 (CORS) deixada em aberto pela code review da Phase 98**: não há necessidade de configurar `CORS_ALLOWED_ORIGINS` para esta app porque o browser nunca faz a chamada diretamente
- Tratamento de falha: 404 (sistema não inicializado) → o gate de `proxy.ts` já redireciona para `/setup` antes deste fetch ser relevante (LP-05); erro de rede/timeout no fetch server-side → fail open, renderiza a landing com fallback "LexCV" genérico (nome) — nunca uma página de erro/crash para um visitante anónimo
- Cache: `cache: 'no-store'` no fetch (sempre fresh) — mirrors o padrão já usado por `fetchSetupStatus()` em `web/src/lib/setup.ts`; sem requisito de performance nesta milestone que justifique ISR/ revalidate
- `proxy.ts` próprio da `webpage/` replica o padrão "fail open" do `web/proxy.ts` (catch → `NextResponse.next()`) — um erro transitório do backend nunca deve bloquear um visitante de ver a landing

### Estrutura Técnica da App
- `assetPrefix: '/landing-static'` em `next.config.ts` (Multi-Zones, LP-13)
- Dependências do `web/` explicitamente EXCLUÍDAS de `webpage/`: `@tanstack/react-query`, `react-hook-form`, `zod` — nenhuma necessária (contacto é `mailto:` simples sem formulário; captura de leads é Out of Scope per REQUIREMENTS.md)
- `webpage/` tem `tsconfig.json`/eslint config próprios, copiados/adaptados de `web/` (mesmo alias `@/*` → `./src/*`) — não existe pnpm workspace neste monorepo (confirmado: sem `pnpm-workspace.yaml` raiz), cada app é standalone
- Porta de dev server: **3001** (evita colisão com `web/`'s 3000; `backend/` usa 8080) — permite correr `web/` e `webpage/` simultaneamente em dev local

### Setup Gate & Navegação
- O redirect para `/setup` (quando sistema não inicializado, LP-05) vive em `webpage/proxy.ts` (Edge middleware próprio) — **NUNCA copiado literalmente do `web/proxy.ts`**: contém apenas o ramo "não inicializado → `/setup`"; o ramo de verificação `access_token`/redirecionamento condicional para `/dashboard` ou `/login` do `web/proxy.ts` é removido por completo (LP-04 exige que a landing apareça sempre para visitantes autenticados ou não)
- `page.tsx` mantém-se "burra" — toda a lógica de redirect vive no `proxy.ts`, nunca misturada com apresentação
- CTA "Entrar" (LP-11): `<a href="/login">` simples nos dois locais (topo e fundo) — mesmo texto e comportamento, apenas estilos diferentes (topo: botão pequeno/secundário; fundo: botão grande/primário como CTA final); detalhe visual fino de estilo fica para o UI-SPEC
- Navegação: header com âncoras simples para as secções da própria página (`#funcionalidades`, `#confianca`, `#contacto`) além do CTA "Entrar" — padrão single-page landing, LP-03 não exige múltiplas rotas
- Fallback quando `logoDataUrl` é `null` (tenant existe mas sem logo definido): reutilizar exatamente o padrão já usado em `web/src/components/shared/dashboard-shell.tsx` — ícone `Building2` (lucide-react) + nome da tenant em texto, para consistência visual entre `web/` e `webpage/`

### Claude's Discretion
- Estilo visual detalhado (cores, espaçamento, tipografia, variantes de componente) fica a cargo do UI-SPEC gerado a seguir a esta discussão (`gsd-ui-phase`), não desta fase de discussão de conteúdo/comportamento
- Nomes exatos de arquivo/componente dentro de `webpage/src/components/` (ex.: `hero.tsx` vs `hero-section.tsx`)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/globals.css` — Tailwind v4 CSS-based config (`@import "tailwindcss"`, `@plugin "tailwindcss-animate"`, `@custom-variant dark`, tokens `--background`/`--foreground`) — copiar como ponto de partida
- `web/src/app/providers.tsx` — `NextThemesProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange`; `webpage/` só precisa da parte de tema, não do `QueryClientProvider` (TanStack Query excluído)
- `web/src/components/theme-toggle.tsx` — toggle dark/light via `useTheme()` + ícones `lucide-react` Sun/Moon — copiável tal como está
- `web/src/components/ui/button.tsx`, `card.tsx`, `badge.tsx` — primitivos CVA + `cn()`, sem acoplamento a autenticação — diretamente copiáveis
- `web/src/lib/utils.ts` — `cn()` (clsx + tailwind-merge), 6 linhas, dependência de todos os primitivos `ui/`
- `web/src/lib/setup.ts` — `fetchSetupStatus()`: fetch mínimo (`cache: 'no-store'`, sem `apiFetch`/credentials) — padrão exato a replicar para o fetch de branding, não o wrapper `apiFetch` (que traz `credentials: 'include'` e sistema de toast desnecessários para tráfego anónimo)
- `web/src/components/shared/dashboard-shell.tsx:262-276` — padrão de fallback de logo (`<img>` com fallback para ícone `Building2` + "LexCV")
- `web/Dockerfile` — 3-stage build (deps→builder→runner), `output: standalone`, pnpm via corepack, `appuser` não-root — referência para Phase 100, não necessário nesta fase

### Established Patterns
- Tailwind v4 é CSS-based (sem `tailwind.config.*`); config vive em `globals.css` + `postcss.config.mjs`
- Não existe `components.json` (shadcn) em lado nenhum do repo — componentes `ui/` são copiados/adaptados manualmente, nunca via `npx shadcn`
- Não há pnpm workspace raiz — cada app (`backend/`, `web/`, e agora `webpage/`) é totalmente standalone com o seu próprio `node_modules`/lockfile
- `web/next.config.ts` lança erro no module load se env vars obrigatórias estiverem em falta (`BACKEND_API_ORIGIN`) — `webpage/` deve seguir o mesmo padrão fail-fast para as suas próprias env vars

### Integration Points
- `webpage/src/app/page.tsx` — Server Component async, fetch de branding + render das secções
- `webpage/proxy.ts` — gate de setup-status próprio (não copiado do `web/`)
- `webpage/next.config.ts` — `assetPrefix: '/landing-static'`, `output: 'standalone'`
- `webpage/src/app/globals.css`, `webpage/src/app/providers.tsx` — copiados/adaptados de `web/`

</code_context>

<specifics>
## Specific Ideas

- Fetch server-side de `GET /api/v1/public/branding` (não client-side) foi a decisão mais importante desta discussão — simplifica a arquitetura E fecha definitivamente o item WR-01 (CORS) deixado como deferred na review da Phase 98, sem precisar de tocar em `SecurityConfig`/`CORS_ALLOWED_ORIGINS` nesta milestone
- Reaproveitar literalmente o texto/capacidades já validadas em PROJECT.md "Requirements > Validated" para a secção de Funcionalidades — a landing deve ser honesta sobre o que a plataforma já faz, não uma lista de aspirações

</specifics>

<deferred>
## Deferred Ideas

- Imagem OG dinâmica personalizada e screenshots reais da UI — já registados como ideias futuras (não requisitos) em REQUIREMENTS.md v2, fora do âmbito pequeno e focado desta milestone
- Favicon/OG-image/robots.txt próprios da `webpage/` — decisão explícita do utilizador (REQUIREMENTS.md Out of Scope): usa os ficheiros estáticos já servidos por `web/`, evita branches extra de routing no Caddy
- Backend de captura de leads persistido para "Pedir Demonstração" — Out of Scope, o `mailto:` fixo cobre a necessidade atual

</deferred>
