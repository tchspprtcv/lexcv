# Phase 75: Componente Único View/Edit - Context

**Gathered:** 2026-07-04
**Status:** Ready for planning

<domain>
## Phase Boundary

A ficha de cliente (`/clientes/[id]`) passa a ser uma única página que alterna entre modo leitura (por defeito) e modo edição via um botão "Editar", sem navegar para uma rota separada. A rota `/clientes/[id]/editar` é removida por completo. Esta fase cobre APENAS a unificação view/edit da estrutura atual (ainda flat, sem tabs) — a reorganização em separadores (Dados/Contactos e Notas/Processos/Pareceres/Documentos) é âmbito da Phase 76 em diante. Não cobre `/clientes/novo` (criação, que continua uma página própria) nem `/clientes/[id]/ficha` (impressão, inalterada).

</domain>

<decisions>
## Implementation Decisions

### Mecanismo de Toggle & Estado
- Estado "modo edição" vive em `useState<boolean>` local ao componente da página (`isEditing`), sem refletir na URL.
- Ao clicar "Editar", `useForm` (react-hook-form) é inicializado/populado com `defaultValues` a partir de `cliente.data` — mesmo padrão já usado em `editar/page.tsx` hoje.
- "Cancelar" reverte para modo leitura E descarta alterações não guardadas via `form.reset(defaultValues)` + `setIsEditing(false)`, sem `window.confirm` (dados não persistidos, risco baixo, decisão explícita de não adicionar confirmação).
- O modo edição é sempre efémero — reseta para leitura ao sair da página e voltar (sem persistência em sessionStorage).

### Âmbito do "Modo Edição"
- Contactos e Notas (que já têm CRUD inline próprio hoje via `ClienteContactosCard`/`ClienteNotasCard`, funcionando mesmo em modo leitura) passam a ficar **gated** pelo toggle "Editar" — os botões "Adicionar" ficam ocultos/inativos em modo leitura, para consistência com o critério de sucesso "todos os controlos de edição inativos em modo leitura".
- Procuração (upload/substituir/remover) — mesmo tratamento: gated pelo toggle.
- Advogados/Administrativos (adicionar/remover responsável) — mesmo tratamento: gated pelo toggle.
- As 3 secções de listas (Documentos Entregues, Documentos a Tratar, Deslocações) mantêm o padrão atual de staging local em `useState` — só persistem no "Guardar" do formulário principal, replicando exatamente o comportamento de `editar/page.tsx` hoje, agora dentro do modo edição do componente único. (Nota: Documentos Entregues será substituído por upload real na Phase 79 — nesta fase mantém-se o comportamento atual de lista de texto.)

### Guardar/Cancelar e Navegação
- Após "Guardar" com sucesso: fica na mesma página (`/clientes/[id]`), volta a modo leitura, mostra toast de sucesso, refetch via invalidação TanStack Query — sem navegação/redirect.
- Botões Editar/Guardar/Cancelar ficam no cabeçalho da página, junto a "Voltar"/"Imprimir Ficha" — "Editar" desaparece em modo edição, dando lugar a "Guardar"/"Cancelar".
- A rota `/clientes/[id]/editar` é removida por completo (ficheiro apagado, sem redirect de compatibilidade).
- O hook `useUpdateCliente` existente é reaproveitado sem alterações — só muda onde/como é chamado (dentro do componente unificado).

### Claude's Discretion
- Nome exato de variáveis/estado internas (`isEditing` é sugestão, não obrigação).
- Estrutura exata de como os campos alternam entre `<dl>/<dd>` (leitura) e `<Input>`/`<select>` (edição) — pode ser condicional inline por campo, ou blocos condicionais maiores, desde que o resultado visual em modo leitura seja idêntico ao padrão atual (`dl`/`dd` grid 3 colunas) e o de edição idêntico ao formulário atual.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` (902 linhas) — página de leitura atual, layout `dl`/`dd` 3-col para "Dados", cards para Conta-corrente/Informações Adicionais/Contactos/Notas/Procuração/Advogados/Administrativos.
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (700 linhas, pós Phase 74: usa `buildClienteFormSchema(legacyDocumentoTipo)`, `getDocumentoTipoOptions`, `toDocumentoTipo` do módulo partilhado `web/src/lib/cliente-documento-tipo.ts`) — formulário de edição atual, useForm + zodResolver, 3 listas staged localmente (documentosEntregues/documentosATratar/deslocacoes).
- `ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard` (genérico para Advogados/Administrativos) — componentes existentes com hooks `useList`/`useAdd`/`useRemove` já passados como props; precisam de um prop adicional (ex. `readOnly`/`editable`) para gate pelo toggle.
- `FileDropZone` — usado para procuração; precisa do mesmo tratamento de gate.
- `useUpdateCliente(id)` (`web/src/hooks/use-clientes.ts`) — mutation existente, reaproveitada sem alterações.

### Established Patterns
- `dl`/`dd` grid 3 colunas (`grid grid-cols-3 gap-x-4 gap-y-3 text-sm`) é o padrão de exibição read-only em toda a app (também usado em processos).
- react-hook-form + Zod é o padrão de formulário em toda a app; `zodResolver` populado via `useMemo` (precedente da Phase 74's gap-closure 74-04, que já faz isto para lidar com `legacyDocumentoTipo`).
- Toast de sucesso + invalidação de query via TanStack Query é o padrão pós-mutation em toda a app.

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` torna-se o único ficheiro (view + edit).
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` é removido.
- `ClienteContactosCard`/`ClienteNotasCard`/`ResponsaveisCard`/`FileDropZone` (procuração) precisam de um novo prop de controlo de edição.

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do já documentado — o resultado final deve ser visualmente idêntico ao que existe hoje em ambos os modos (leitura = página atual `[id]/page.tsx`; edição = formulário atual `[id]/editar/page.tsx`), apenas unificados numa única página com toggle.

</specifics>

<deferred>
## Deferred Ideas

- Reorganização em separadores (tabs) — Phase 76.
- Upload real para Documentos Entregues — Phase 79.

</deferred>
