# Phase 60: Ficha Imprimível - Context

**Gathered:** 2026-06-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Criar a rota `/clientes/[id]/ficha` com layout de alta fidelidade ao formulário físico do escritório, acessível por:
1. Botão "Imprimir Ficha" na página de detalhe do cliente (`/clientes/[id]`)
2. Acção "Ficha" no menu de contexto da listagem de clientes

A página inclui CSS de impressão que oculta a navegação e pagina correctamente em A4.

</domain>

<decisions>
## Implementation Decisions

### Layout — Alta Fidelidade ao Formulário Real
- **D-01:** Layout que imita o formulário físico do escritório: cabeçalho com nome do escritório, secções com linhas de dados, rodapé com campos de assinatura ("A Advogada" / "O Cliente"), formato A4
- **D-02:** Organização das secções seguindo o formulário original:
  1. Identificação do cliente (nº, nome, tipo, BI/NIF, idade, sexo, nacionalidade)
  2. Contactos (morada, telefone, email)
  3. Descrição do caso
  4. Advogados e administrativos intervenientes
  5. Documentos entregues / Documentos a tratar
  6. Deslocações a realizar
  7. Honorários propostos (totalidade, por extenso, previsão)
  8. Data e assinaturas
- **D-03:** Para campos não preenchidos: linha vazia ("___________") que permite preenchimento manuscrito na versão impressa

### CSS de Impressão
- **D-04:** `@media print` CSS: oculta sidebar, top bar, botões de acção, breadcrumbs; mantém apenas o conteúdo da ficha
- **D-05:** Page size A4 (`@page { size: A4; margin: 2cm; }`), conteúdo pagina correctamente
- **D-06:** Botão "Imprimir" chama `window.print()` — sem dependência de biblioteca PDF externa

### Navegação e Acesso
- **D-07:** **Página de detalhe** (`/clientes/[id]`): botão "Imprimir Ficha" no cabeçalho (junto aos outros botões de acção: Editar, Eliminar) — abre `/clientes/[id]/ficha` em nova aba
- **D-08:** **Listagem** (`/clientes`): acção "Ver Ficha" no menu de contexto (3 pontos/kebab) de cada linha — abre `/clientes/[id]/ficha` em nova aba

### Claude's Discretion
- Logo/nome do escritório no cabeçalho: usar texto estático "LexCV" ou ler do perfil do tenant — Claude decide
- Estilos CSS específicos (fontes, bordas das linhas de assinatura): Claude decide com base nas convenções do projecto
- Se Particular: omitir campos de Empresa; se Empresa: omitir campos demográficos — Claude decide como gerir o espaço em branco

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Frontend — Ficha e navegação
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — página de detalhe do cliente (adicionar botão "Imprimir Ficha")
- `web/src/app/(dashboard)/clientes/page.tsx` — listagem de clientes (adicionar acção "Ver Ficha" no menu de contexto)
- `web/src/hooks/use-clientes.ts` (ou similar) — hook TanStack Query para carregar dados do cliente (reutilizar na ficha)

### Formulário físico de referência
- O formulário real do escritório tem: "Ficha Cliente", "Cliente nº", Nome, Idade, Sexo, Nacionalidade, BI/Pass. nº, Contactos (Morada/Tel/Email), Descrição do Caso, Advogados (Nome/Cédula/Contacto), Administrativos, Documentos Entregues, Documentos a Tratar, Deslocações, Honorários Propostos (Totalidade/Por Extenso/Previsão), Data, Assinaturas

### Requirements
- `.planning/REQUIREMENTS.md` — FICH-01, FICH-02 (in scope desta fase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- TanStack Query hooks de clientes — carregar dados do cliente para a ficha
- shadcn/ui `Button` com `target="_blank"` para abrir em nova aba
- Tailwind classes existentes para layout responsivo

### Established Patterns
- Next.js App Router: rota `/clientes/[id]/ficha/page.tsx` — nova rota dentro do grupo `(dashboard)`
- CSS `@media print` — padrão web nativo, sem biblioteca externa
- `window.print()` — chamado pelo botão Imprimir

### Integration Points
- `/clientes/[id]/ficha` — nova página sob `web/src/app/(dashboard)/clientes/[id]/ficha/`
- Botão na página de detalhe (`/clientes/[id]/page.tsx`)
- Menu de contexto na listagem (`/clientes/page.tsx`)
- Dados carregados via `GET /api/v1/clientes/{id}` (endpoint já existente, estendido na Phase 57)

</code_context>

<specifics>
## Specific Ideas

- Layout de alta fidelidade: imitar o formulário físico com cabeçalho, secções numeradas, linhas para dados, rodapé de assinaturas
- Campos em branco → linhas "___" para preenchimento manual possível
- Acesso por dois pontos: botão na ficha de detalhe + menu de contexto na listagem

</specifics>

<deferred>
## Deferred Ideas

- Export PDF via servidor (sem `window.print()`) → FUT-02
- Logo/branding do tenant no cabeçalho → Future (requer configuração de tenant)

</deferred>

---

*Phase: 60-Ficha Imprimível*
*Context gathered: 2026-06-29*
