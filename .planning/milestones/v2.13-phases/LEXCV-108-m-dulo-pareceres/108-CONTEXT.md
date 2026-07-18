# Phase 108: Módulo Pareceres - Context

**Gathered:** 2026-07-17
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

Os formulários de Pareceres usam `Select`/`NativeSelect` conforme a convenção já estabelecida (filtros de lista → `Select`, campos ligados a `react-hook-form` ou controlo single-purpose → `NativeSelect`). A timeline "Histórico de Versões" na página de detalhe (`pareceres/[id]/page.tsx`) ganha `Tooltip` no marcador de cada versão, e o mesmo histórico usa `Accordion` para colapsar versões antigas, mantendo apenas a mais recente (ou a entregue, quando concluído) expandida por padrão. Cobre PARC-18, PARC-19, PARC-20.

**Nota de âmbito:** este é o primeiro uso de `Accordion` em todo o projeto, e a primeira combinação Timeline+Tooltip — não há precedente direto no código para replicar (ao contrário do `Tooltip` em botões icon-only, já estabelecido desde a Phase 102/DSR-03). Os campos `aprovado`/`aprovadoPorId`/`aprovadoEm` de `ParecerVersao` existem no tipo e no backend (`PUT /{id}/versoes/{versaoId}/aprovar`), mas são explicitamente fora de âmbito (PARC-17, adiado para v2.7 desde o milestone v2.6) — esta fase não constrói UI nova em torno deles.

</domain>

<decisions>
## Implementation Decisions

### Select (Radix) — filtros de lista
- Os 6 `<select>` nativos de filtro em `pareceres/page.tsx` (Estado/Advogado/Cliente nos filtros rápidos, linhas 178-226, + Cliente/Advogado/Estado na pesquisa avançada, linhas 259-307) migram para `Select` (Radix), replicando o padrão já estabelecido em Financeiro/Agenda (Phases 106/107)
- Sentinela `"todos"` obrigatória (Radix `SelectItem` não aceita `value=""`) — exige atualizar a lógica de filtro (`onApply`/`onPesquisar`/`onClear`/`onLimparPesquisa`, linhas 80-125) para tratar `"todos"` como "sem filtro" em vez do atual check `if (status) ...` (string falsy)

### NativeSelect — campos de formulário
- Os 4 campos RHF em `pareceres/nova/page.tsx` (`clienteId` linha 127, `processoId` linha 152, `prioridade` linha 191, `advogadoId` linha 200) migram para `NativeSelect`
- O `<select id="versaoFinalId">` do `EntregarParecerDialog` (`pareceres/[id]/page.tsx:492-504` — controlo local via `useState`, não RHF, não filtro de lista) migra também para `NativeSelect`, por ser a mesma família de "controlo single-purpose de formulário/modal" já estabelecida nas Phases 105/106/107

### RBAC isFetched (bundled)
- Os ~7 sites de RBAC em 3 ficheiros (`pareceres/page.tsx:29`, `pareceres/nova/page.tsx:35,219`, `pareceres/[id]/page.tsx:83,157-158,159-164,217`) usam ainda `!permissions.isLoading` — corrigir para `permissions.isFetched` (ou remover o `!permissions.isLoading` das expressões compostas), já que estes ficheiros serão tocados de qualquer forma
- **Não tocar** na lógica de RBAC de instância `isResponsavelOuAdmin` (`pareceres/[id]/page.tsx:153-155`) — verificação pré-existente do milestone v2.6 (advogado responsável OU ADMIN), não relacionada com esta migração

### Tooltip — timeline "Histórico de Versões"
- Aplicado ao marcador (ponto) de cada versão na timeline (`pareceres/[id]/page.tsx:240-308`), indicando "Versão atual" (a mais recente, quando o parecer não está concluído) ou "Versão entregue" (a que corresponde a `parecer.data.versaoFinalId`, quando `isConcluido`) para a versão relevante, e implicitamente "versão anterior" para as restantes (sem tooltip próprio, ou com um texto neutro — decisão de implementação)
- `TooltipProvider` já montado globalmente (`providers.tsx:30`, `delayDuration={700}`) — zero setup novo necessário
- O botão `AnexoLink` (texto+ícone, não icon-only) não ganha Tooltip — mantém a distinção já estabelecida na Phase 102 (Tooltip reservado a afordances icon-only)

### Accordion — histórico de versões
- O mesmo bloco "Histórico de Versões" (`pareceres/[id]/page.tsx:240-308`) migra de uma lista sempre-expandida para `Accordion` (Radix, `type="single"` ou `"multiple"` com `collapsible`)
- Apenas a versão mais recente (ou a versão entregue, via `versaoFinalId`, quando `isConcluido`) fica expandida por padrão (`defaultValue`); todas as restantes começam colapsadas
- Não construir nenhuma UI nova em torno de `aprovado`/`aprovadoPorId`/`aprovadoEm` (fora de âmbito, PARC-17/v2.7)

### Claude's Discretion
- Detalhes exatos do texto do Tooltip para as versões "anteriores" (sem marcação especial) — pode ser omitido, ou um texto neutro tipo "Versão anterior"
- Exact `type="single"`/`collapsible` vs `type="multiple"` do Accordion — decisão técnica sem impacto de produto, desde que o comportamento "só a mais recente/entregue expandida por padrão, resto colapsado" seja respeitado
- Nome exato de qualquer variável/helper novo introduzido para determinar "é a versão atual/entregue"

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/select.tsx` — `Select`/`SelectTrigger`/`SelectContent`/`SelectItem`/etc., já usado em Financeiro/Agenda (filtros de lista)
- `web/src/components/ui/native-select.tsx` — `NativeSelect`, já estabelecido como padrão para campos RHF (Phases 105/106/107)
- `web/src/components/ui/tooltip.tsx` — `Tooltip`/`TooltipTrigger`/`TooltipContent`, `TooltipProvider` global já montado (`providers.tsx:30`, Phase 102/DSR-03); já usado em `pareceres/columns.tsx:9,88-103` (botão icon-only "Ver detalhes") e outros módulos — mas nunca ainda numa timeline
- `web/src/components/ui/accordion.tsx` — `Accordion`/`AccordionItem`/`AccordionTrigger`/`AccordionContent`, zero consumidores em todo o `web/src` até agora — esta fase é a primeira utilização real

### Established Patterns
- Fase 106/107 estabeleceram: sentinela não-vazia (`"todos"`) obrigatória para filtros `Select`, com atualização da lógica de filtro a acompanhar a troca de template
- Fase 105/106/107 estabeleceram: `NativeSelect` para campos RHF ou controlos single-purpose de formulário/modal, `className="w-full"` sempre
- Fase 102 estabeleceu: `Tooltip` reservado para afordances icon-only (botões sem texto visível), não para elementos texto+ícone
- Fase 103/105/106/107 estabeleceram a correção `permissions.isFetched` para o bug de race condition de RBAC

### Integration Points
- `web/src/app/(dashboard)/pareceres/page.tsx` (lista + filtros rápidos + pesquisa avançada)
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` (formulário de criar solicitação)
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (detalhe: NovaVersaoForm, EntregarParecerDialog, timeline "Histórico de Versões")
- `web/src/hooks/use-pareceres.ts` (`useParecerVersoes`, dados da timeline)
- `web/src/types/pareceres.ts` (`ParecerVersao`, campos `aprovado*` não usados nesta fase)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- UI de aprovação de versão (`aprovado`/`aprovadoPorId`/`aprovadoEm`, endpoint `PUT /{id}/versoes/{versaoId}/aprovar` já existe no backend) — explicitamente fora de âmbito, tracked como PARC-17 desde o milestone v2.6, adiado para v2.7
- Paginação/limite no histórico de versões (`useParecerVersoes` devolve a lista completa sem paginação) — fora de âmbito desta fase; o `Accordion` resolve a UX de "muitas versões" sem exigir mudança de backend

</deferred>
