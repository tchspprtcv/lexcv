# Phase 28: Clientes — Enriquecimento cadastral - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Este plano descreve a adição de campos de enriquecimento cadastral ao modelo `Cliente` tanto no backend (JPA/Base de dados) quanto no frontend (esquemas Zod, tipos TypeScript, formulários de criação/edição e página de detalhe).

Os campos novos são:
- `documentoTipo` (Enum no JPA, String na BD: NIF, CNI, Passaporte)
- `documentoNumero` (String na BD, com constraint unique composta por tenant)
- `ramoAtividade` (String na BD, ramo de negócio do cliente)
- `detalhesAdicionais` (VARCHAR(255) na BD, observações livres)

</domain>

<decisions>
## Implementation Decisions

### BD e Modelo Backend
- **Tipo de Documento**: Modelado como um `Enum` JPA no backend (`DocumentoTipo` com valores `NIF`, `CNI`, `PASSAPORTE`) mapeado para `String` no banco de dados.
- **Unicidade de Documento**: Imposição de constraint `unique` composta por `(tenant_id, documento_numero)` diretamente na base de dados para evitar cadastros duplicados sob o mesmo tenant.
- **Tamanho do Campo de Detalhes**: Usar tipo `VARCHAR(255)` no banco de dados para observações adicionais.
- **Ramos de Atividade**: Utilizar a lista padrão (Banca, Telecom, Construção, Serviços, Comércio, Outros) como base para seleção.

### Layout e Validação no Formulário (UI/UX)
- **Localização dos Campos**: Agrupados numa seção de duas colunas "Informações Adicionais" no próprio formulário principal de criação/edição.
- **Inputs**: Utilizar dropdowns/selects (componentes Select do shadcn/ui) para seleção de `documentoTipo` e `ramoAtividade`.
- **Categorias**: Utilizar a distinção nativa `Singular` vs `Coletivo` (já existente no campo `tipo`) como a categorização principal, sem adicionar um novo campo redundante de categoria.
- **Validação de Documento**: Aplicar validação dinâmica baseada no tipo de documento selecionado (ex: se selecionado `NIF`, validar formato de 9 dígitos de Cabo Verde no Zod/frontend e no backend).

### Visualização no Detalhe
- **Organização**: Exibição dos novos dados cadastrais num card "Informações Adicionais" na aba principal (Geral) com design sharp.
- **Tabela Geral**: Apenas o `NIF` e o `tipo` (Singular/Coletivo) aparecem na tabela geral de clientes para evitar poluição visual.
- **Campos Vazios**: Omitir ou exibir texto em itálico ("Sem observações") para campos opcionais não preenchidos.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Hooks de clientes em `web/src/hooks/use-clientes.ts` (`useCliente`, `useUpdateCliente`, `useCreateCliente`).
- Componentes UI básicos como `Input`, `Select`, `Button` e `Form` estruturados com Tailwind.

### Established Patterns
- Fetching de dados reativo com React Query.
- Formulários tipados usando React Hook Form e validações controladas via Zod.
- Persistência JPA no backend com mapeamento Hibernate para base de dados PostgreSQL.

### Integration Points
- `/api/v1/clientes` CRUD no backend (`ResourceController.java`).
- Página de detalhe do cliente `web/src/app/(dashboard)/clientes/[id]/page.tsx`.
- Formulário de criação `web/src/app/(dashboard)/clientes/novo/page.tsx` e edição `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx`.

</code_context>

<specifics>
## Specific Ideas

- NIF de Cabo Verde deve ser validado especificamente com 9 caracteres numéricos.
- O mapeamento do Enum no Hibernate deve ser feito com `@Enumerated(EnumType.STRING)`.

</specifics>

<deferred>
## Deferred Ideas

- Nenhuma. O planeamento cobriu todos os campos cadastrais previstos para a Phase 28.

</deferred>
