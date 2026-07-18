# Phase 110: Refinamento da Landing (webpage/) - Context

**Gathered:** 2026-07-17
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode, com "Discutir mais" em todas as 3 áreas)

<domain>
## Phase Boundary

O `SiteHeader` da `webpage/` (landing pública, app Next.js separada de `web/`) ganha navegação mobile funcional via `Sheet` — hoje zero navegação mobile existe, um gap funcional real. As secções Hero e Contacto são recompostas com `Card`/`Badge`, replicando a composição `Card` já idiomática do `TrustSection` (o `Badge` em si não tem precedente no `TrustSection` — é introduzido de novo nesta fase, copiado de `web/`). Cobre LDG-17, LDG-18.

**Nota de âmbito:** `Sheet` e `Badge` não existem ainda em `webpage/src/components/ui/` (apenas `button.tsx` e `card.tsx` existem hoje) — ambos são copiados de `web/`'s primitivos já vetados no Phase 101's package-legitimacy gate, sem nova dependência (`webpage/package.json` já usa o pacote unificado `radix-ui`).

</domain>

<decisions>
## Implementation Decisions

### Navegação Móvel (Sheet no SiteHeader)
- Itens no Sheet: os mesmos 3 links (Funcionalidades/#funcionalidades, Confiança/#confianca, Contacto/#contacto) + botão "Entrar" CTA, extraídos para um array `NAV_LINKS` partilhado entre o `<nav>` desktop e o novo Sheet (evita duplicar a lista)
- Trigger: botão hamburger (ícone `Menu` de lucide-react), visível só `md:hidden`, espelhando `aria-label="Abrir menu"` do padrão em `web/src/components/shared/dashboard-shell.tsx`
- Fecho ao clicar num link âncora: `onClick` direto em cada link a chamar `setOpen(false)` — **NÃO** replicar o padrão `useEffect(pathname)` do `web/`, que não funcionaria aqui (links âncora na mesma página nunca mudam `pathname`, exatamente a classe do bug WR-01 encontrado e corrigido na Phase 109 — ver `109-REVIEW.md`)
- O botão "Entrar" (CTA) também fecha o Sheet ao clicar, mesmo `onClick`, por consistência com os links âncora

### Secção Hero (Card/Badge)
- O eyebrow span manual (`uppercase tracking-[0.2em]...`) vira `Badge` (copiado de `web/src/components/ui/badge.tsx` para `webpage/src/components/ui/`, já que `webpage/` não tem `Badge` ainda)
- O Hero inteiro (Badge+H1+parágrafo+2 botões CTA) fica dentro de um único `Card`, com `CardHeader`/`CardContent`, seguindo a composição do `TrustSection`
- Variant do Badge: `secondary` (default do componente, uso mais neutro)
- Os 2 botões CTA ficam dentro do `Card` (`CardContent`/`CardFooter`, à discrição do executor)

### Secção Contacto (Card/Badge)
- O eyebrow span manual vira `Badge` variant `secondary`, mesmo componente/variant do Hero (consistência entre as 2 secções)
- A Contacto inteira (Badge+H1+parágrafo+CTA mailto) fica dentro de um `Card`, mesma estrutura do Hero (`Card`+`CardHeader`+`CardContent`)
- O botão CTA único (mailto) fica dentro do `Card`
- Sem diferença visual entre o `Card` do Hero e o `Card` da Contacto — mesma composição/estilo em ambos, só o conteúdo interno muda

### Claude's Discretion
- Nome exato do array `NAV_LINKS` e a sua localização exata (dentro de `site-header.tsx` ou ficheiro próprio)
- `CardHeader`/`CardContent` vs `CardFooter` exato para onde os botões CTA aterram dentro do Card (Hero e Contacto)
- Detalhes de markup do ícone hamburger / botão de fecho do Sheet

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `webpage/src/components/ui/card.tsx` — `Card`/`CardHeader`/`CardTitle`/`CardDescription` já usado em `trust-section.tsx`
- `webpage/src/components/ui/button.tsx` — já usado nos CTAs do Hero/Contacto
- `web/src/components/ui/sheet.tsx` e `web/src/components/ui/badge.tsx` — fonte para copiar `Sheet`/`Badge` para `webpage/` (mesmo pacote `radix-ui` já é dependência de `webpage/`, sem novo `pnpm add`)
- `web/src/components/shared/dashboard-shell.tsx` (linhas ~21, 89-108) e `web/src/components/shared/sidebar-nav.tsx` (Phase 109, `onNavigate` callback) — padrão de referência estrutural para o Sheet + fecho-ao-navegar, com a ressalva explícita de NÃO copiar o `useEffect(pathname)` (não aplicável a links âncora)

### Established Patterns
- `trust-section.tsx` estabelece o padrão `Card`+`CardHeader`+`CardTitle`+`CardDescription` num grid (`grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4`) — este é o precedente de composição `Card` a replicar (não de `Badge`, que é introduzido de novo)
- Fase 109 estabeleceu a lição "onClick direto, não useEffect(pathname)" para fechar Sheets/drawers ao navegar — diretamente aplicável aqui, e mais ainda dado que os links são âncoras na mesma página (o useEffect nem chegaria a disparar)

### Integration Points
- `webpage/src/components/site-header.tsx` (28 linhas — nav desktop + Entrar CTA)
- `webpage/src/components/hero-section.tsx` (32 linhas)
- `webpage/src/components/contact-section.tsx` (26 linhas)
- `webpage/src/components/trust-section.tsx` (padrão Card a replicar)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — a discussão manteve-se dentro do âmbito da fase.

</deferred>
