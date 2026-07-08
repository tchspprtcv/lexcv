# Phase 84: Frontend — UI (Intake, Dados, Sub-secções, Documentos, Termo de Honorários) - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

O utilizador consegue registar e consultar Juízo/Origem, gerir Decisões/Factos/Testemunhas, aceder a uma aba de Documentos dedicada, e gerar o Termo de Honorários impresso — tudo a partir da ficha do processo.

</domain>

<decisions>
## Interação — Listas e Formulários de Adicionar

- As 4 abas novas (Decisões, Factos, Testemunhas, Documentos) seguem o **padrão já usado na ficha de Cliente (v2.8)**: um card com a lista/tabela dos registos + um botão "Adicionar" pequeno (`size="sm"`) que abre um `Dialog` modal (shadcn `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`) contendo o formulário de criação — **não** o padrão de grid de 2 colunas com um card de formulário fixo ao lado, que é o padrão mais antigo ainda usado em Partes/Fases
- Analog exato a replicar: `web/src/app/(dashboard)/clientes/[id]/page.tsx`, secções "Documentos a Tratar"/"Deslocações" (`Dialog open={addXModal} onOpenChange={setAddXModal}` + `<DialogTrigger asChild><Button variant="outline" size="sm">Adicionar</Button></DialogTrigger>`)
- Editar um registo existente (Decisão/Facto/Testemunha) também deve abrir um Dialog (mesma UX consistente), não edição inline na tabela

### Decisão explícita do utilizador — refatorar Partes e Fases para o mesmo padrão
**O utilizador pediu explicitamente, durante a discussão desta fase, que as abas já existentes "Partes" e "Fases"** (que hoje usam o padrão antigo de grid de 2 colunas com formulário fixo) **sejam refatoradas para o mesmo padrão lista+Dialog "Adicionar"** usado nas 4 abas novas, para consistência visual em toda a ficha do processo. Esta é uma extensão deliberada do âmbito original da fase (Partes/Fases não fazem parte dos critérios de sucesso do ROADMAP.md), mas de baixo custo incremental já que a mesma página está a ser modificada. Incluir isto como tarefas explícitas do plano.

## Upload de Anexo em Decisão

- O formulário "Adicionar Decisão" (dentro do Dialog) tem um campo `<input type="file">` nativo opcional, junto aos campos data/tipo/resumo, submetido como multipart num único POST — consistente com o contrato já construído nas Phases 81/83 (`useAddDecisao`, upload direto sem seletor de documento pré-existente)

## Termo de Honorários

- Um botão "Gerar Termo de Honorários" aparece no card "Dados" da ficha do processo, visível apenas quando o processo está `ATIVO` (i.e., já foi formalizado e tem um Honorário associado)
- Clicar navega para a nova rota `[id]/termo-honorarios`
- **Bloqueia** (não apenas avisa) a impressão quando `Honorario.valorTotal` ainda é `null` — o botão de imprimir fica desativado com uma mensagem clara explicando que o valor ainda não foi preenchido (via `/financeiro/[honorarioId]` → "Editar"), em vez de permitir imprimir um documento com campos vazios

### Claude's Discretion
- Exact copy/wording of dialog titles, button labels, empty-states — seguir o tom já estabelecido no módulo (português, direto)
- Layout exato das colunas de cada tabela (Decisões/Factos/Testemunhas) — seguir a densidade já usada nas tabelas de Partes/Fases (`text-sm`, colunas relevantes por entidade)
- Se a reordenação de Factos (drag-and-drop vs. campo numérico editável) é necessária nesta fase — REQUIREMENTS PROC-10 só exige "reordenar", sem especificar o mecanismo; um campo `ordem` editável via Dialog "Editar" já satisfaz o requisito sem necessitar de drag-and-drop

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — padrão exato de Dialog "Adicionar" a replicar (linhas ~898-960, "Documentos a Tratar"/"Deslocações")
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` — padrão CSS-print (`window.print()`, `PRINT_CSS`, `BLANK` placeholder) a clonar para `[id]/termo-honorarios/page.tsx`
- `web/src/hooks/use-processos.ts` — os 12 hooks CRUD (Decisão/Facto/Testemunha) já existem e estão prontos a consumir (Phase 83), incluindo `useProcessoDecisoes`/`useAddDecisao`/etc.
- `web/src/lib/tipo-decisao.ts`, `tipo-testemunha.ts`, `origem-processo.ts` — label maps PT já existem (Phase 83)
- `web/src/hooks/use-honorarios.ts` / `use-financeiro.ts` — hooks existentes para consultar o Honorário do processo (verificar nome exato do hook antes de assumir)
- `web/src/hooks/use-clientes.ts` (`useCliente`) — para o Termo de Honorários combinar dados de Cliente+Processo+Honorário

### Established Patterns
- `TabKey` union + grupo de botões-toggle em `processos/[id]/page.tsx` (não shadcn `Tabs`) — os 4 novos valores de tab (`decisoes`, `factos`, `testemunhas`, `documentos`) seguem exatamente este padrão
- `processoIntakeFormSchema` (Phase 83) já tem `origem` como `z.enum(...)` obrigatório — só falta o campo `<select>`/`<RadioGroup>` no passo 1 do formulário de intake em `processos/novo/page.tsx`

### Integration Points
- `GET /processos/{id}/documentos` já existe (backend, desde antes desta milestone) — a aba Documentos só precisa da parte frontend, mirroring `ClienteDocumentosEntreguesTab` (v2.8 Phase 79)

</code_context>

<specifics>
## Specific Ideas

- Refatorar Partes e Fases para o padrão lista+Dialog (ver decisão acima) — pedido explícito do utilizador, incluir como tarefas do plano
- Termo de Honorários: bloquear impressão com mensagem clara quando valorTotal é null (não apenas avisar)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (a scope extension was requested and accepted, not deferred).

</deferred>
