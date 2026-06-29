# Phase 60: Ficha Imprimível — Research

**Researched:** 2026-06-29
**Domain:** Next.js App Router · CSS @media print · React Client Components
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Layout que imita o formulário físico do escritório: cabeçalho com nome do escritório, secções com linhas de dados, rodapé com campos de assinatura ("A Advogada" / "O Cliente"), formato A4.
- **D-02:** Secções: 1) Identificação (nº, nome, tipo, BI/NIF, idade, sexo, nacionalidade); 2) Contactos (morada, tel, email); 3) Descrição do caso; 4) Advogados e administrativos; 5) Documentos entregues / a tratar; 6) Deslocações; 7) Honorários propostos (totalidade, por extenso, previsão); 8) Data e assinaturas.
- **D-03:** Campos não preenchidos mostram linha sublinada `___________` para preenchimento manuscrito.
- **D-04:** `@media print` oculta sidebar, top bar, botões de acção, breadcrumbs; mantém apenas o conteúdo da ficha.
- **D-05:** `@page { size: A4; margin: 2cm; }` — paginação correcta em A4.
- **D-06:** Botão "Imprimir" chama `window.print()` — sem biblioteca PDF externa.
- **D-07:** Botão "Imprimir Ficha" na página de detalhe (`/clientes/[id]`) → abre `/clientes/[id]/ficha` em nova aba.
- **D-08:** Acção "Ver Ficha" no menu de contexto (kebab) da listagem (`/clientes`) → abre `/clientes/[id]/ficha` em nova aba.

### Claude's Discretion

- Logo/nome do escritório no cabeçalho: usar texto estático "LexCV" ou ler do perfil do tenant.
- Estilos CSS específicos (fontes, bordas das linhas de assinatura).
- Se Particular: omitir campos de Empresa; se Empresa: omitir campos demográficos — gerir espaço em branco.

### Deferred Ideas (OUT OF SCOPE)

- Export PDF via servidor (sem `window.print()`) → FUT-02.
- Logo/branding do tenant no cabeçalho → Future (requer configuração de tenant).

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FICH-01 | Utilizador acede a uma vista de ficha do cliente que reproduz o formato real do escritório | Nova rota `/clientes/[id]/ficha/page.tsx` dentro do grupo `(dashboard)`; dados carregados via `useCliente(id)` já existente |
| FICH-02 | Utilizador imprime ou exporta a ficha do cliente (print CSS / botão de impressão) | CSS `@media print` + `@page` inline no componente ou em globals; `window.print()` chamado pelo botão |

</phase_requirements>

---

## Summary

Phase 60 é puramente frontend: uma nova página Next.js App Router dentro do grupo `(dashboard)` que renderiza os dados do cliente num layout de alta fidelidade ao formulário físico do escritório, com CSS de impressão A4 nativo via `window.print()`.

O hook `useCliente(id)` já existe em `web/src/hooks/use-clientes.ts` e carrega todos os campos disponíveis do cliente via `GET /api/v1/clientes/{id}`. A nova página reutiliza esse hook directamente.

O principal desafio técnico é o CSS de impressão: o shell `DashboardShell` renderiza sidebar, top bar e bottom nav à volta de todos os filhos — o `@media print` precisa de ocultar esses elementos sem modificar o componente shell. Isso é resolvido com selectores CSS direcionados às classes do shell ou com uma classe `print:hidden` em Tailwind (disponível desde Tailwind v3) aplicada via globals.

**Recomendação primária:** Criar `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` com `"use client"`, usar `useCliente(id)` para dados, escrever CSS de impressão com `<style>` inline via `dangerouslySetInnerHTML` ou via `globals.css` — depois adicionar botão na página de detalhe e acção no menu de contexto da listagem.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Renderização da ficha | Frontend (Next.js Client Component) | — | Layout HTML puro; `window.print()` requer browser |
| Dados do cliente | API (Spring Boot) | Frontend (TanStack Query cache) | Endpoint `/clientes/{id}` já existente |
| CSS de impressão A4 | Browser (CSS @media print) | — | Padrão web nativo; sem lógica de servidor necessária |
| Navegação (botão detalhe) | Frontend Client Component | — | `Link target="_blank"` na página de detalhe existente |
| Navegação (menu de contexto listagem) | Frontend Client Component | — | Adicionar item ao `ClienteRow` na listagem existente |

---

## Standard Stack

### Core

| Biblioteca | Versão | Propósito | Porquê standard |
|-----------|--------|-----------|-----------------|
| Next.js App Router | 16 (projecto) | Rota `/clientes/[id]/ficha` | Padrão do projecto |
| React 19 | projecto | Client Component com hooks | Padrão do projecto |
| TanStack Query | projecto | `useCliente(id)` — dados do cliente | Já usado em toda a app |
| Tailwind CSS | projecto | Classes utilitárias; `print:hidden` para ocultar nav | Padrão do projecto |
| shadcn/ui Button | projecto | Botão "Imprimir" e botão na página de detalhe | Padrão do projecto |

### Não Instalar Nada

Esta fase não requer nenhuma dependência nova. `window.print()` e `@media print` CSS são APIs web nativas sem custo de pacote.

### Package Legitimacy Audit

> Nenhum pacote novo a instalar nesta fase. Secção N/A.

**Packages removed due to slopcheck:** none
**Packages flagged as suspicious:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Utilizador
    │
    ├─── /clientes             (listagem)
    │         │
    │         └── kebab menu → "Ver Ficha" ──┐
    │                                        │
    ├─── /clientes/[id]        (detalhe)     │  target="_blank"
    │         │                              │
    │         └── botão "Imprimir Ficha" ────┤
    │                                        ▼
    │                               /clientes/[id]/ficha   (nova aba)
    │                                        │
    │                               useCliente(id) ──── GET /api/v1/clientes/{id}
    │                                        │
    │                               Layout A4 (HTML + CSS)
    │                                        │
    │                               window.print()  ──── Browser print dialog
    │                                        │
    │                               @media print CSS oculta:
    │                                  - aside (sidebar)
    │                                  - header (top bar)
    │                                  - .bottom-nav
    │                                  - botão Imprimir
```

### Estrutura de Ficheiros (apenas novos / modificados)

```
web/src/app/(dashboard)/clientes/
├── [id]/
│   ├── page.tsx                    ← MODIFICAR: adicionar botão "Imprimir Ficha"
│   └── ficha/
│       └── page.tsx                ← CRIAR: a nova página de ficha imprimível
└── page.tsx                        ← MODIFICAR: adicionar "Ver Ficha" no menu de contexto
```

Não é necessário criar `layout.tsx` dentro de `ficha/` — a página herda o layout do grupo `(dashboard)` e o CSS de impressão cuida de ocultar o shell.

### Padrão 1: Rota de Ficha — Client Component

```tsx
// web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
// [ASSUMED] — padrão baseado no conhecimento do projecto, não verificado via Context7
"use client";

import * as React from "react";
import { useCliente } from "@/hooks/use-clientes";

type PageProps = { params: Promise<{ id: string }> };

export default function FichaPage({ params }: PageProps) {
  const { id } = React.use(params);
  const cliente = useCliente(id);

  if (cliente.isLoading) return <div>A carregar...</div>;
  if (!cliente.data) return <div>Cliente não encontrado.</div>;

  return (
    <>
      <style dangerouslySetInnerHTML={{ __html: printStyles }} />
      <div className="ficha-container">
        {/* conteúdo da ficha */}
      </div>
      <div className="print:hidden mt-6 flex justify-end">
        <button onClick={() => window.print()}>Imprimir</button>
      </div>
    </>
  );
}

const printStyles = `
  @media print {
    aside, header, .bottom-nav, [data-print-hide] { display: none !important; }
    body { background: white; }
    .ficha-container { width: 100%; }
  }
  @page { size: A4; margin: 2cm; }
`;
```

### Padrão 2: Selectores CSS para Ocultar o Shell

O `DashboardShell` renderiza três elementos que precisam de desaparecer em impressão:

| Elemento | Selector CSS seguro |
|----------|---------------------|
| Sidebar (`aside`) | `aside` (tag semântica directa) |
| Top bar (`header`) | `header` (tag semântica directa) |
| Bottom nav (`BottomNav`) | Adicionar classe `bottom-nav` ao componente existente **ou** usar `[class*="BottomNav"]` — preferir adicionar classe explícita |

A abordagem mais limpa: injectar as regras `@media print` no `globals.css` ou via `<style>` inline na página de ficha. Como a ficha abre numa nova aba, o CSS de impressão afecta apenas essa janela.

Alternativa Tailwind: `print:hidden` pode ser aplicado directamente nos elementos do shell, mas implicaria modificar `dashboard-shell.tsx`. Preferir CSS selectores na página de ficha para não tocar no shell.

### Padrão 3: Botão na Página de Detalhe

```tsx
// Adicionar ao bloco de botões em /clientes/[id]/page.tsx
<Button asChild variant="outline">
  <Link href={`/clientes/${encodeURIComponent(id)}/ficha`} target="_blank" rel="noopener noreferrer">
    Imprimir Ficha
  </Link>
</Button>
```

Não requer nova permissão — qualquer utilizador que possa `view("clientes")` pode imprimir.

### Padrão 4: Acção no Menu de Contexto da Listagem

A listagem actual (`ClienteRow`) não tem um menu de contexto kebab — tem botões directos (Eye, Pencil, Trash2). A decisão D-08 exige "menu de contexto (3 pontos/kebab)".

Duas opções:
1. **Adicionar ícone de impressora** directamente na linha, junto aos botões actuais — mais simples, consistente com o padrão actual.
2. **Criar menu kebab** com DropdownMenu shadcn — mais trabalho, diferente do padrão actual.

Recomendação: Option 1 (botão com ícone `Printer` de lucide-react), porque o padrão actual não usa kebab menus. Preservar consistência é mais valioso que seguir a letra da D-08. O planner deve registar esta escolha.

### Anti-Patterns a Evitar

- **Não usar biblioteca PDF (jsPDF, html2pdf.js, etc.):** D-06 é explícito — apenas `window.print()`. Além disso, bibliotecas PDF têm problemas com fontes e layout que o browser resolve melhor.
- **Não colocar CSS de impressão apenas em globals.css:** globals.css afecta todas as páginas. Se o CSS oculta `aside` globalmente em impressão, qualquer outra página impressa do dashboard fica sem sidebar. Preferir injectar apenas na página de ficha (via `<style>` inline ou módulo CSS localizado).
- **Não criar layout.tsx separado para ficha:** Adicionar um layout intermédio que substitua o DashboardShell criaria problemas de autenticação e redirecionamento (o `proxy.ts` faz checks de session). A abordagem correcta é herdar o layout existente e ocultar via CSS.
- **Não omitir `"use client"`:** `window.print()` requer browser — a página tem de ser Client Component.

---

## Don't Hand-Roll

| Problema | Não Construir | Usar | Porquê |
|----------|--------------|------|--------|
| CSS impressão A4 | Motor de PDF custom | `@page { size: A4; }` + `window.print()` | Nativo no browser; já decidido em D-05/D-06 |
| Ocultar navegação em print | Lógica JS para remover DOM | `@media print { display: none }` | CSS declarativo; sem flash/flicker |
| Campos em branco | Componente complexo | String `"___________"` com `text-decoration: underline` | Um span com estilo mínimo resolve |
| Tipo condicional (Particular vs Empresa) | State machine | Condicional simples `cliente.tipo === "EMPRESA"` | A lógica é trivial |

---

## Estado dos Campos da API (Campos Disponíveis vs. Campos das Secções)

A interface `Cliente` actual (`web/src/types/clientes.ts`) tem estes campos relevantes para a ficha:

| Campo da Ficha (D-02) | Campo na API actual | Status |
|----------------------|---------------------|--------|
| Nome | `cliente.nome` | Disponível |
| Tipo (Particular/Empresa) | `cliente.tipo` | Disponível |
| NIF | `cliente.nif` | Disponível |
| BI/Passaporte (nº e tipo) | `cliente.documento_tipo`, `cliente.documento_numero` | Disponível |
| Morada | `cliente.morada` | Disponível |
| Localidade | `cliente.localidade` | Disponível |
| Email | `cliente.email` | Disponível |
| Telefone | `cliente.telefone` | Disponível |
| Nº Cliente | `cliente.numero_cliente` ou `cliente.numeroCliente` | **NÃO em `types/clientes.ts` ainda** — adicionado em Phase 57 (backend), tipo precisa de extensão |
| Idade | `cliente.idade` | **NÃO em `types/clientes.ts`** — Phase 57 |
| Sexo | `cliente.sexo` | **NÃO em `types/clientes.ts`** — Phase 57 |
| Nacionalidade | `cliente.nacionalidade` | **NÃO em `types/clientes.ts`** — Phase 57 |
| Avençado (flag) | `cliente.avencado` | **NÃO em `types/clientes.ts`** — Phase 57 |
| Descrição do caso | `cliente.descricao_caso` ou similar | **NÃO em `types/clientes.ts`** — Phase 59 (intake) |
| Advogados | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |
| Administrativos | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |
| Docs entregues | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |
| Docs a tratar | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |
| Deslocações | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |
| Honorários propostos | campo de intake | **NÃO em `types/clientes.ts`** — Phase 59 |

**Implicação para o plano:** Phase 60 DEPENDE de Phases 57 e 59 terem adicionado esses campos ao backend E ao tipo `Cliente` no frontend. O Wave 0 da Phase 60 deve verificar/estender `web/src/types/clientes.ts` para incluir todos os campos novos antes de construir o layout da ficha.

---

## Common Pitfalls

### Pitfall 1: CSS de impressão afecta todas as páginas

**O que corre mal:** Colocar `@media print { aside { display: none } }` em `globals.css` — todas as páginas do dashboard passam a não ter sidebar quando impressas.

**Porquê acontece:** `globals.css` aplica-se a toda a app, não apenas à página de ficha.

**Como evitar:** Injectar o CSS com `<style dangerouslySetInnerHTML={...}>` dentro do componente da ficha. Como a ficha abre em nova aba, o CSS só existe nessa janela.

**Sinais de aviso:** Testes de impressão noutras páginas mostram sidebar desaparecida.

---

### Pitfall 2: `window.print()` em Server Component

**O que corre mal:** Esquecer `"use client"` — Next.js tenta renderizar no servidor, `window` não existe, erro em runtime.

**Porquê acontece:** App Router assume Server Components por padrão.

**Como evitar:** Primeira linha do ficheiro deve ser `"use client"`.

**Sinais de aviso:** `ReferenceError: window is not defined` em build ou em runtime.

---

### Pitfall 3: Campos de Phase 57/59 não disponíveis

**O que corre mal:** A ficha tenta renderizar `cliente.numero_cliente`, `cliente.idade`, `cliente.descricao_caso` etc., mas esses campos ainda não estão no tipo `Cliente` nem no backend de desenvolvimento.

**Porquê acontece:** Phase 60 depende de 57 e 59 — se não foram executadas, a API não retorna esses campos.

**Como evitar:** O Wave 0 deve estender `types/clientes.ts` com campos opcionais (`numero_cliente?: string; idade?: number;` etc.) e usar o padrão D-03 (linha `___________`) para campos ausentes. Assim a ficha funciona mesmo com dados parciais.

**Sinais de aviso:** TypeScript erros em `cliente.numero_cliente`.

---

### Pitfall 4: `@page` ignorado por alguns browsers

**O que corre mal:** `@page { size: A4; margin: 2cm; }` é ignorado no Firefox por defeito se o utilizador tiver "Use custom margins" no diálogo de impressão.

**Porquê acontece:** Suporte a `@page size` varia por browser — Chrome suporta bem, Firefox respeita apenas margem mas não size em alguns modos.

**Como evitar:** Documentar no interface (texto junto ao botão: "Optimize para A4 no diálogo de impressão"). Não há solução puramente CSS para forçar tamanho em todos os browsers.

**Sinais de aviso:** Print preview mostra papel Letter em vez de A4.

---

### Pitfall 5: Abrir nova aba bloqueada por popup blocker

**O que corre mal:** `<Link href="..." target="_blank">` funciona, mas `window.open(...)` chamado de forma programática (não directamente de um click) é bloqueado.

**Porquê acontece:** Browsers bloqueiam `window.open` não directamente ligado a evento de utilizador.

**Como evitar:** Usar `<Link target="_blank">` (um elemento `<a>`) — nunca `window.open()` para navegar para a ficha. O comportamento de nova aba com `<a target="_blank">` não é bloqueado por popup blockers.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest / React Testing Library (verificar `web/package.json`) |
| Config file | `web/vitest.config.*` (verificar se existe) |
| Quick run command | `pnpm --filter web test` |
| Full suite command | `pnpm --filter web test --run` |

### Phase Requirements → Test Map

| Req ID | Behaviour | Test Type | Automated Command | File Exists? |
|--------|-----------|-----------|-------------------|-------------|
| FICH-01 | Página `/clientes/[id]/ficha` renderiza dados do cliente | unit | `pnpm test --filter FichaPage` | ❌ Wave 0 |
| FICH-01 | Campos em branco mostram `___________` | unit | incluído no teste de FichaPage | ❌ Wave 0 |
| FICH-02 | Botão "Imprimir" chama `window.print()` | unit (mock) | incluído no teste de FichaPage | ❌ Wave 0 |
| FICH-02 | Botão "Imprimir Ficha" na página de detalhe navega para `/ficha` | unit | `pnpm test --filter ClienteDetailPage` | ❌ Wave 0 |

### Wave 0 Gaps

- [ ] `web/src/app/(dashboard)/clientes/[id]/ficha/__tests__/page.test.tsx` — cobre FICH-01 e FICH-02
- [ ] Verificar se Vitest está configurado em `web/` — se não, instalar e configurar

---

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | sim | Herdado do DashboardShell — `useMe()` redirige se sessão inválida |
| V3 Session Management | sim | Herdado — cookies httpOnly geridos pelo Spring Boot |
| V4 Access Control | sim | Verificar `permissions.can.view("clientes")` no início da página de ficha |
| V5 Input Validation | não | Página apenas lê dados — não processa input do utilizador |
| V6 Cryptography | não | Sem operações criptográficas nesta página |

**Nota crítica:** A página de ficha DEVE incluir o mesmo guard de permissões que a página de detalhe:

```tsx
if (!permissions.isLoading && !canViewClientes) {
  return <AccessDeniedState description="..." backHref="/clientes" />;
}
```

Sem isso, qualquer utilizador autenticado que conheça a URL `/clientes/{id}/ficha` poderia aceder a dados de clientes sem ter a permissão `clientes:view`.

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Acesso directo à URL sem permissão | Elevation of Privilege | Guard `can.view("clientes")` no Client Component |
| Exposição de dados sensíveis em nova aba sem auth | Information Disclosure | O cookie de sessão segue na nova aba (mesmo domínio, `credentials: "include"`) — sem risco adicional |

---

## Environment Availability

Esta fase é puramente frontend sem dependências externas novas. Pnpm, Node.js e o backend local são suficientes.

| Dependency | Required By | Available | Fallback |
|------------|------------|-----------|----------|
| pnpm | Build/dev | Sim (projecto existente) | — |
| Tailwind CSS `print:` variant | CSS print utilities | Sim (Tailwind v3+ no projecto) | CSS manual equivalente |
| lucide-react `Printer` icon | Botão de impressão | Sim (já instalado no projecto) | Texto "Imprimir" sem ícone |

---

## Assumptions Log

| # | Claim | Section | Risk se Errado |
|---|-------|---------|----------------|
| A1 | Phases 57 e 59 são executadas antes da Phase 60, adicionando campos ao backend e ao tipo `Cliente` | Estado dos Campos | Se não executadas, metade das secções da ficha ficam em branco (aceitável — D-03 cobre com linhas) |
| A2 | O projecto usa Tailwind v3+ com variante `print:` disponível | Standard Stack | Se Tailwind v2, `print:hidden` não funciona — usar CSS manual |
| A3 | lucide-react `Printer` ícone existe na versão instalada | Standard Stack | Usar ícone diferente ou texto simples |
| A4 | Usando `<style dangerouslySetInnerHTML>` é a abordagem correcta para CSS scoped em Next.js 16 App Router | Architecture Patterns | Next.js 16 pode ter mudanças — verificar `web/node_modules/next/dist/docs/` antes de implementar |
| A5 | Não existe menu kebab actual na listagem — botão directo é preferível à criação de DropdownMenu | Architecture Patterns | Se D-08 for interpretada literalmente, implica criar DropdownMenu shadcn — custo de implementação maior |

---

## Open Questions

1. **Campos de Phase 57/59 disponíveis?**
   - O que sabemos: `types/clientes.ts` não tem `numero_cliente`, `idade`, `sexo`, `nacionalidade`, campos de intake.
   - O que é incerto: Se Phases 57 e 59 já foram executadas ou são executadas antes desta.
   - Recomendação: Wave 0 deve estender o tipo com todos os campos opcionais. A ficha funciona com dados parciais via D-03.

2. **Menu kebab na listagem vs. botão directo?**
   - O que sabemos: A listagem actual usa botões directos (Eye, Pencil, Trash2) sem dropdown.
   - O que é incerto: Se D-08 exige criação de novo componente DropdownMenu ou se um botão adicional basta.
   - Recomendação: Adicionar botão `Printer` directo (consistente com padrão actual). Planner deve registar esta opção como decisão.

3. **Nome do escritório no cabeçalho?**
   - O que sabemos: `me.data?.tenant_nome` está disponível via `useMe()` no shell.
   - O que é incerto: Se `useMe()` está disponível na página de ficha ou se é necessário um hook separado.
   - Recomendação: Usar `useMe()` para ler `tenant_nome` — já usado no `DashboardShell`, portanto disponível no contexto de qualquer Client Component dentro do grupo `(dashboard)`.

---

## Sources

### Primary (HIGH confidence)

- Codebase lida directamente:
  - `web/src/app/(dashboard)/clientes/[id]/page.tsx` — padrão de Client Component, guards de permissão, hook usage
  - `web/src/app/(dashboard)/clientes/page.tsx` — padrão de listagem com `ClienteRow`
  - `web/src/hooks/use-clientes.ts` — `useCliente(id)` já existente e funcional
  - `web/src/types/clientes.ts` — campos disponíveis na API
  - `web/src/components/shared/dashboard-shell.tsx` — selectores para CSS de impressão (`aside`, `header`)
  - `web/src/app/(dashboard)/layout.tsx` — confirma que `DashboardShell` envolve todos os filhos
  - `.planning/config.json` — `nyquist_validation: true`, `security_enforcement: true`

### Secondary (MEDIUM confidence)

- [ASSUMED] CSS `@page` e `@media print` — conhecimento de treino do standard web W3C, não verificado via Context7 nesta sessão.
- [ASSUMED] Tailwind `print:hidden` variant — conhecimento de treino Tailwind v3, não verificado via Context7.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — tudo reutilizado do projecto existente, sem dependências novas
- Architecture: HIGH — padrões lidos directamente do codebase
- Pitfalls: MEDIUM — CSS de impressão é conhecimento de treino não verificado via Context7 nesta sessão
- Campos da API: MEDIUM — dependência de Phases 57/59 ainda pendentes

**Research date:** 2026-06-29
**Valid until:** 2026-07-29 (estável — sem dependências externas rápidas)
