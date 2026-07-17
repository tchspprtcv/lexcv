# Phase 107: Módulos Documentos + Financeiro - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

O upload de documentos usa o `Progress` oficial (shadcn/Radix) em vez das 3 barras de progresso customizadas hoje duplicadas em 3 ficheiros. Os campos com enum real (`Documento.confidencialidade`, `Honorário.processoId`) usam `NativeSelect`; os filtros de lista do Financeiro (`Processo`, `Estado`) usam `Select`. O campo `Documento.tipo` (que hoje não tem nenhum enum, em nenhuma camada) é migrado para um novo `Combobox` (Popover+Command, primeira composição deste tipo no projeto) nos 2 sítios onde já existe como `datalist` (aba Documentos de Processo, aba Documentos Entregues de Cliente) — não em `documentos/novo/page.tsx`, onde continua a ser texto livre sem sugestões. Os filtros de Processo/Cliente da lista de Documentos (hoje `Input` livre para UUID) também passam a `Combobox` pesquisável. Cobre DOF-01, DOF-02.

**Esclarecimento de âmbito (decisão travada):** nem `Documento.tipo` nem `Pagamento.metodo` têm um enum em qualquer camada (frontend ou backend) — são texto livre. DOF-02 ("formulários de tipo/honorário/pagamento usam Select") não pode ser satisfeito por uma troca mecânica de `<select>` nativo para esses dois campos porque não existe nenhum `<select>` nativo para trocar. `Pagamento.metodo` fica como está (texto livre) — inventar um enum sem confirmação de produto está fora de âmbito. `Documento.tipo` recebe tratamento especial: os 2 usos como `datalist` (que já simulam uma sugestão fechada, mas com entrada livre) sobem de nível para `Combobox`, que é a componente shadcn correta para "lista fechada + é possível escrever um valor novo" — isto é uma expansão de âmbito deliberada face ao texto literal de DOF-02, decidida explicitamente pelo utilizador em vez de adiar (como o item já parcialmente registado `DOF-V2-01` sugeria).

</domain>

<decisions>
## Implementation Decisions

### Progress (upload) — 3 duplicados, não só 1
- As 3 implementações duplicadas da barra de progresso (`documentos/novo/page.tsx`, `processos/[id]/page.tsx` `ProcessoDocumentosTab`, `clientes/[id]/page.tsx` `ClienteDocumentosEntreguesTab`) migram para o componente oficial `Progress` (`web/src/components/ui/progress.tsx`, já instalado na Phase 101, zero consumidores até agora) — não só a de `documentos/novo`, apesar de 2 dos 3 sítios viverem em ficheiros já fechados pela Phase 105
- `Progress` aceita um `value` numérico 0-100 que mapeia diretamente do `progresso: number | null` já existente em cada sítio (`value={progresso ?? 0}`) — troca mecânica, sem mudança de lógica de upload (`useUploadDocumentoComProgresso`, `XMLHttpRequest.upload.onprogress`, inalterado)

### RBAC isFetched (bundled)
- Os 6 gates de acesso em Documentos+Financeiro (`documentos/page.tsx:35`, `documentos/novo/page.tsx:120`, `documentos/[id]/page.tsx:25`, `financeiro/page.tsx:103`, `financeiro/novo/page.tsx:28`, `financeiro/[id]/page.tsx:80`) usam ainda `!permissions.isLoading && !canX` — corrigir para `permissions.isFetched && !canX` nos 6, já que serão tocados de qualquer forma
- Não tocar nos usos legítimos de `permissions.isLoading` como guarda de "disable submit enquanto carrega" (`documentos/novo/page.tsx:261`, `financeiro/novo/page.tsx:175`, `financeiro/[id]/page.tsx:478`) — mesma distinção já estabelecida nas Phases 103/105/106

### NativeSelect — campos com enum real
- `Documento.confidencialidade` (`documentos/novo/page.tsx:232-246`, enum `PUBLICO`/`INTERNO`/`CONFIDENCIAL`/`RESTRITO`) migra para `NativeSelect`
- `Honorário.processoId` (`financeiro/novo/page.tsx:104-122`, único campo select-worthy do formulário de criar honorário) migra para `NativeSelect`
- O dialog de editar honorário (`financeiro/[id]/page.tsx:270-320`) não tem campo `processoId` nem qualquer outro campo select-worthy (só `valorTotal`/`dataAcordo`/`descricao`, todos `Input`) — confirmado, nada a migrar aí

### Select (Radix) — filtros de lista do Financeiro
- Os 2 filtros de lista do Financeiro (`financeiro/page.tsx:212-223` Processo, `:228-237` Estado — 3 opções fixas: Pendente/Parcialmente Pago/Pago) migram para `Select`, replicando o padrão já estabelecido pela Agenda (Phase 106)

### Combobox (Popover+Command) — primeira composição deste tipo no projeto
- Novo componente partilhado `Combobox` (nome/localização exatos: discrição do Claude, mas recomenda-se `web/src/components/shared/combobox.tsx`, seguindo o precedente de `date-picker-field.tsx` da Phase 106) construído a partir de `web/src/components/ui/popover.tsx` + `web/src/components/ui/command.tsx` (ambos já instalados na Phase 101, zero consumidores de `Command` até agora — confirmado via grep)
- Aplicado a **4 sítios**:
  1. `Documento.tipo` no `datalist` de `processos/[id]/page.tsx` `ProcessoDocumentosTab` (hoje `tipoOptions` computado a partir dos tipos já usados nesse processo) — sobe para `Combobox`, mantendo a capacidade de escrever um valor novo (não é uma lista fechada)
  2. `Documento.tipo` no `datalist` de `clientes/[id]/page.tsx` `ClienteDocumentosEntreguesTab` (mesmo padrão, `tipoOptions` scoped ao cliente) — mesma migração; isto substitui/cumpre o item já registado `DOF-V2-01` do REQUIREMENTS.md, que só mencionava explicitamente o sítio de Cliente
  3. Filtro "Processo ID" da lista de Documentos (`documentos/page.tsx:112-118`, hoje `Input` livre de UUID) — sobe para `Combobox` pesquisável por número/título do processo (via `useProcessos()`, já usado noutros sítios do módulo)
  4. Filtro "Cliente ID" da lista de Documentos (`documentos/page.tsx:120-126`, mesmo padrão) — sobe para `Combobox` pesquisável por nome do cliente (via o hook de clientes já existente)
- `Documento.tipo` em `documentos/novo/page.tsx` (o formulário de upload dedicado) **não** ganha um Combobox — fica como `Input` de texto livre sem sugestões, já que não há histórico de valores para sugerir nesse contexto (criação de raiz, sem processo/cliente ainda associado no momento do upload nalguns fluxos)

### Claude's Discretion
- Nome exato do ficheiro/componente do `Combobox` partilhado
- Detalhes exatos de wiring do `Combobox` com `react-hook-form` (via `Controller`, seguindo o mesmo padrão já estabelecido pela Phase 106 para o `DatePickerField`)
- Se os 2 filtros de Documentos (Processo/Cliente) devem partilhar o mesmo componente `Combobox` ou instâncias distintas com opções diferentes — não muda o resultado visual/funcional

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/progress.tsx` — `Progress`, wrapper fino sobre `radix-ui` Progress, prop `value` 0-100 numérico, zero consumidores até agora
- `web/src/components/ui/select.tsx` — `Select`/`SelectTrigger`/`SelectContent`/`SelectItem`/etc., já usado 12+ vezes (Agenda/Clientes/Processos)
- `web/src/components/ui/native-select.tsx` — `NativeSelect`, já estabelecido como padrão para campos RHF (Phases 105/106)
- `web/src/components/ui/command.tsx` + `web/src/components/ui/popover.tsx` — ambos existem (instalados Phase 101), zero consumidores de `Command` até agora — esta fase constrói a primeira composição Combobox do projeto
- `web/src/hooks/use-documentos.ts:103-159` — `useUploadDocumentoComProgresso`, hook de upload com progresso real via `XMLHttpRequest.upload.onprogress`, inalterado por esta fase
- `web/src/hooks/use-processos.ts` (`useProcessos`) — já usado para popular selects de processo noutros módulos, reutilizável para o novo Combobox de filtro

### Established Patterns
- Fase 105/106 estabeleceram: `NativeSelect` para campos RHF com enum real, `Select` (Radix) reservado para filtros de lista, `className="w-full"` sempre no `NativeSelect`
- Fase 106 estabeleceu o padrão de construir uma composição nova a partir de primitivos existentes num componente partilhado (`DatePickerField` em `web/src/components/shared/`) quando não há analog em código — o `Combobox` desta fase segue o mesmo precedente
- Fase 103/105/106 estabeleceram a correção `permissions.isFetched && !canX` para o bug de race condition de RBAC

### Integration Points
- `web/src/app/(dashboard)/documentos/page.tsx` (filtros da lista)
- `web/src/app/(dashboard)/documentos/novo/page.tsx` (upload dedicado)
- `web/src/app/(dashboard)/documentos/[id]/page.tsx` (detalhe, só RBAC)
- `web/src/app/(dashboard)/processos/[id]/page.tsx` `ProcessoDocumentosTab` (aba Documentos, dialog de upload + datalist de tipo)
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` `ClienteDocumentosEntreguesTab` (aba Documentos Entregues, dialog de upload + datalist de tipo)
- `web/src/app/(dashboard)/financeiro/page.tsx` (filtros da lista)
- `web/src/app/(dashboard)/financeiro/novo/page.tsx` (form criar honorário)
- `web/src/app/(dashboard)/financeiro/[id]/page.tsx` (detalhe honorário + form pagamento + dialog editar honorário)
- `.planning/REQUIREMENTS.md` linha 84 (`DOF-V2-01`, a atualizar/fechar por esta fase em vez de ficar adiado)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- `Pagamento.metodo` continua texto livre — inventar um enum fixo (ex.: TRANSFERENCIA/DINHEIRO/CHEQUE/CARTAO) exigiria confirmação de produto sobre a lista real de métodos aceites; candidato para uma fase futura, não decidido aqui sem essa confirmação
- Upgrade geral de UUID-Input para Combobox pesquisável noutros módulos além de Documentos (ex.: outros filtros de Processo/Cliente na app) — fora de âmbito, esta fase só cobre os 2 filtros de Documentos explicitamente decididos acima

</deferred>
