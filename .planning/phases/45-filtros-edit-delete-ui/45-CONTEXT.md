# Phase 45: Filtros + Edit/Delete UI - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona filtros client-side na lista de honorários e as ações de edição e eliminação na página de detalhe. Toda a lógica é frontend — nenhum novo endpoint backend é necessário (os endpoints foram adicionados na Phase 43).

**Fora do âmbito desta fase:** Exportação CSV (Phase 46), gráficos de receita, paginação.

</domain>

<decisions>
## Implementation Decisions

### Filtros (FIN-11, FIN-12, FIN-13) — `page.tsx`
- **Filtragem client-side**: os dados já são carregados (`useHonorarios`, `useProcessos`); filtrar via `.filter()` sobre `honorarios.data` com base em `useState` para os três filtros
- **Filtro por processo** (FIN-11): `<select>` nativo ou `Select` de shadcn populado com `processos.data`; valor = processoId da option; filtro: `h.processoId === selectedProcessoId`
- **Filtro por status** (FIN-12): `<select>` com opções Todos/Pendente/Parcialmente Pago/Pago; reutiliza `calcHonorarioStatus` (já existe em `page.tsx` após Phase 44)
- **Filtro por data** (FIN-13): dois `<input type="date">` para `dataAcordoDe` e `dataAcordoAte`; filtro: `h.dataAcordo >= dataAcordoDe && h.dataAcordo <= dataAcordoAte`
- Botão "Limpar filtros" quando qualquer filtro está ativo
- Filtros posicionados entre o header e a tabela, numa barra horizontal com labels

### Edit Honorário (FIN-14) — `[id]/page.tsx`
- Botão "Editar" no header da página de detalhe, gated por `canEditFinanceiro`
- Dialog modal com form usando react-hook-form + Zod — reutiliza `honorarioUpdateSchema` (já existe ou deve ser criado em `schemas/financeiro.ts`) com campos: `valorTotal`, `descricao`, `dataAcordo`
- Pré-preencher form com valores actuais do honorário (`honorario.data`)
- Usa `useUpdateHonorario()` já existente; invalidação automática no `onSuccess`
- Fecha dialog no sucesso

### Delete Honorário (FIN-15) — `[id]/page.tsx`
- Botão "Apagar" no header da página de detalhe, gated por `canManageFinanceiro` (`financeiro:manage`)
- `AlertDialog` de confirmação (shadcn) com mensagem clara
- Usa `useDeleteHonorario()` já existente
- No sucesso: `router.push('/financeiro')` para voltar à lista
- Se backend responde com 409: mostrar mensagem de erro inline (honorário tem pagamentos)

### Delete Pagamento (FIN-16) — `[id]/page.tsx`
- Botão de lixo/trash por linha da tabela de pagamentos, gated por `canManageFinanceiro`
- `AlertDialog` de confirmação por pagamento
- Usa `useDeletePagamento()` já existente (passa `pagamentoId` e `honorarioId`)
- Depois de apagar, a tabela atualiza automaticamente via cache invalidation

### Claude's Discretion
- Usar `Dialog` do shadcn para o form de edição (verificar se disponível em `components/ui/`)
- Usar `AlertDialog` do shadcn para confirmações (verificar se disponível)
- Se não disponíveis, implementar com modal simples usando `div` + state
- Layout exato dos botões de filtro (inline vs stacked)
- Ícone para o botão de apagar pagamento (trash icon ou texto "Apagar")

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `calcHonorarioStatus` — adicionada em Phase 44 em `page.tsx`; reutilizar diretamente
- `useUpdateHonorario()`, `useDeleteHonorario()`, `useDeletePagamento()` — adicionadas em Phase 43, prontas a usar
- `honorarioUpdateSchema` — verificar se existe em `schemas/financeiro.ts`; se não, criar com `valorTotal: z.coerce.number().positive()`, `descricao: z.string().optional()`, `dataAcordo: z.string().optional()`
- `useProcessos()` — já importado em `page.tsx` (lista), usar para popular o filtro de processo

### Established Patterns
- React state para filtros: `const [filtroProcesso, setFiltroProcesso] = React.useState("")`
- Dialog form: `zodResolver(schema)` + `useForm` + `onSuccess` fecha dialog via `open` state
- `router.push` para redirect após delete: `import { useRouter } from "next/navigation"`
- `canManageFinanceiro`: `permissions.can.manage("financeiro")` (verificar padrão em outros módulos)

### Integration Points
- `web/src/app/(dashboard)/financeiro/page.tsx` — filtros
- `web/src/app/(dashboard)/financeiro/[id]/page.tsx` — edit/delete honorário + delete pagamento
- `web/src/schemas/financeiro.ts` — adicionar `honorarioUpdateSchema` se não existe
- `web/src/components/ui/dialog.tsx` e `alert-dialog.tsx` — verificar existência
- `web/src/hooks/use-financeiro.ts` — hooks já prontos

</code_context>

<specifics>
## Specific Ideas

- Verificar `components/ui/dialog.tsx` e `components/ui/alert-dialog.tsx` — se não existirem, usar `npx shadcn@latest add dialog alert-dialog`
- O detail page usa `canEditFinanceiro` para o form de pagamento; adicionar `canManageFinanceiro = permissions.can.manage("financeiro")` para os botões delete

</specifics>

<deferred>
## Deferred Ideas

- Filtro de pesquisa textual por descrição — fora do âmbito v2.0
- Paginação da lista de honorários — fora do âmbito v2.0
- Filtro por cliente (além de processo) — fora do âmbito v2.0

</deferred>
