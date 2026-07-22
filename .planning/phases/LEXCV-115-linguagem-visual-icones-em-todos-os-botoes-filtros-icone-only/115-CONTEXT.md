# Phase 115: Linguagem Visual — Ícones em Todos os Botões + Filtros Ícone-Only - Context

**Gathered:** 2026-07-21
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous)

<domain>
## Phase Boundary

Todos os botões da aplicação comunicam a sua ação através de um ícone consistente, e os botões de ação de filtro em todos os módulos tornam-se ícone-only com tooltip.

**Descoberta de âmbito:** 208 usos de `<Button>` em 48 ficheiros de `web/src`. 32 desses ficheiros já importam `lucide-react` — uma parte relevante dos botões já tem ícone (padrão incremental já em curso ao longo de milestones anteriores). O gap real (ICON-01) é um subconjunto — botões só-com-texto que ainda não comunicam a ação visualmente — não os 208 usos todos.

</domain>

<decisions>
## Implementation Decisions

### ICON-01 — Ícones em Botões
- Abordagem: auditar e preencher só os gaps (botões sem ícone) — não reaplicar/trocar ícones já corretos nos 32 ficheiros que já os têm
- Botões só-com-ícone já existentes (`MoreVertical`, paginação, etc.) ficam fora de âmbito — já cumprem o objetivo
- Vocabulário de ícones: reutilizar o Lucide já em uso no projeto (Plus, Search, Trash2, Pencil, Download, etc.) — sem introduzir biblioteca ou convenção nova

### FICO-01 — Filtros Ícone-Only
- Ícones: Aplicar→`Check`, Limpar→`X`, Exportar→`Download`
- Tooltip: reutiliza o primitivo `Tooltip` já instalado (v2.13 Phase 108/109), mesmo padrão dos botões ícone-only já existentes
- Âmbito de módulos: exatamente os 5 nomeados em REQUIREMENTS.md (Clientes, Processos, Agenda, Documentos, Financeiro) — não expandir a Pareceres/Notificações mesmo que tenham padrões semelhantes, mantendo o âmbito literal do requisito
- Botões "Novo Cliente"/"Novo Processo" etc. (já ícone+texto) mantêm-se como estão — só os botões de ação de filtro (aplicar/limpar/exportar) passam a ícone-only

### Claude's Discretion
- Mapeamento exato ícone↔ação para os botões que ainda não têm ícone — a decidir durante o planeamento/execução, olhando ao contexto de cada botão e ao vocabulário Lucide já estabelecido no projeto

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Tooltip` primitivo já instalado (v2.13 Phase 108/109), já usado em botões ícone-only existentes (ex. `MoreVertical` em `processos/page.tsx`/`pareceres/page.tsx`)
- Vocabulário Lucide já em uso: `Plus`, `Search`, `Filter`, `Download`, `Trash2`, `Pencil`, `MoreVertical`, entre outros

### Established Patterns
- 32 de 48 ficheiros com `<Button>` já importam `lucide-react` — o padrão de ícone+texto já existe e deve ser replicado, não reinventado
- Botões de filtro (aplicar/limpar) já identificados em 8 ficheiros: `clientes/page.tsx`, `processos/page.tsx`, `processos/[id]/page.tsx`, `agenda/page.tsx`, `documentos/page.tsx`, `financeiro/page.tsx`, `pareceres/page.tsx`, `notificacoes/page.tsx` — FICO-01 aplica-se só aos 5 módulos nomeados no requisito (Clientes/Processos/Agenda/Documentos/Financeiro)

### Integration Points
- Ficheiros a auditar para ICON-01: todos os 48 com `<Button>`, foco nos que ainda não importam `lucide-react` ou que têm botões de texto puro
- Ficheiros a modificar para FICO-01: os 5 módulos nomeados, botões "Aplicar"/"Limpar"/"Exportar" especificamente

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do vocabulário Lucide já estabelecido.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
