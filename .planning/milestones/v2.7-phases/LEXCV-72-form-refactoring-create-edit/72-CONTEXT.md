# Phase 72: Form Refactoring (Create & Edit) - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Os formulários de criação (`clientes/novo/page.tsx`) e edição (`clientes/[id]/editar/page.tsx`) de cliente são simplificados para o modelo aplanado (Phase 71 já removeu `dados_tipo` dos tipos/schema): rótulos dinâmicos ("Nome"/"Nome Comercial" e "Morada"/"Sede") conforme o tipo de cliente, o campo NIF passa a ser a identificação primária obrigatória (deixa de estar rotulado como "Legado"), e o novo tipo de documento `REG_COMERCIAL` fica selecionável (CLI-05, CLI-07, CLI-08, CLI-09, CLI-10).

</domain>

<decisions>
## Implementation Decisions

### Dynamic Labels (Particular vs Empresa)
- Label do campo `nome`: "Nome" quando tipo=PARTICULAR, "Nome Comercial" quando tipo=EMPRESA (CLI-07)
- Label do campo `morada`: "Morada" quando tipo=PARTICULAR, "Sede" quando tipo=EMPRESA (CLI-08)
- A troca de label é ao vivo, reagindo à mudança do radio `tipo` (reaproveitar o handler `onTipoChange` já existente)

### NIF / Documento Consolidation
- O campo atualmente rotulado "NIF (Legado)" passa a chamar-se apenas "NIF" — deixa de ser legado, é agora o campo de identificação obrigatório de CLI-05
- Mantém-se como método de identificação separado de `documento_tipo`/`documento_numero` (CNI/Passaporte/REG_COMERCIAL) — o NIF é sempre obrigatório independentemente do `documento_tipo` escolhido
- O campo NIF passa para logo abaixo do campo Nome (identificação primária, topo do formulário)

### Empresa-Specific Handling
- `REG_COMERCIAL` não tem seleção automática por tipo=EMPRESA — o utilizador escolhe manualmente entre NIF/CNI/PASSAPORTE/REG_COMERCIAL no select `documento_tipo` (já disponível desde a correção de review da Phase 71)
- Os sub-campos de Empresa removidos (`nome_comercial`, `representante_legal`, `cargo`) ficam totalmente ausentes do formulário — sem placeholders desativados, sem vestígios
- A página de edição (`editar/page.tsx`) recebe o mesmo conjunto de alterações que a página de criação (labels dinâmicas, renomeação do NIF, disponibilidade de REG_COMERCIAL)

### Claude's Discretion
Detalhes de implementação não cobertos acima (posicionamento exato de grid/spacing, mensagens de validação adicionais, ordem de outros campos não mencionados) ficam ao critério do Claude, seguindo os padrões visuais já estabelecidos em `novo/page.tsx` (Card/CardHeader/CardTitle, radio "Tipo de Cliente", grid responsivo `md:grid-cols-2`, `rounded-none` inputs).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `novo/page.tsx` já define `DOCUMENTO_TIPOS`/`toDocumentoTipo` (com REG_COMERCIAL, adicionado na correção de review da Phase 71) e o `selectClassName`/`textareaClassName` reutilizáveis
- `onTipoChange`/`pendingTipo`/dialog de confirmação de troca de tipo já existem em `novo/page.tsx` — mecanismo pronto para acionar a troca de label dinâmica
- `clienteFormSchema` (Phase 71) já valida `nif` como obrigatório com regex de 9 dígitos, incondicional ao tipo de cliente

### Established Patterns
- react-hook-form + zodResolver + `Controller` para campos não nativos (RadioGroup, Switch)
- Erros de validação renderizados como `<p className="text-sm text-red-600">{...message}</p>` logo abaixo de cada campo
- Layout em `Card`/`CardContent`/`CardHeader` com `space-y-6`/`space-y-4`/`grid md:grid-cols-2`

### Integration Points
- `editar/page.tsx` espelha a estrutura de `novo/page.tsx` mas usa `useUpdateCliente`/dados pré-carregados via `form.reset` — as mesmas alterações de label/NIF devem ser replicadas lá

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual externa — seguir os padrões já estabelecidos no próprio formulário `novo/page.tsx`.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
