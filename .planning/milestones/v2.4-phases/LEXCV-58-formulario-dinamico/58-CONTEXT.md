# Phase 58: Formulário Dinâmico - Context

**Gathered:** 2026-06-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Actualizar o formulário frontend de criação/edição de clientes (`/clientes/novo`, `/clientes/[id]/editar`) para:
- Selector de tipo (Particular/Empresa) no topo do formulário
- Campos dinâmicos conforme o tipo seleccionado (seccionados na mesma página)
- Confirmação ao mudar de tipo (evita perda de dados não intencional)
- `numero_cliente` exibido como badge/chip ao lado do nome na ficha e na listagem
- Flag "Avençado" como toggle/checkbox visível na ficha e nas listagens

Não inclui: procuração, campos de intake, ficha imprimível.

</domain>

<decisions>
## Implementation Decisions

### Selector de tipo e layout do formulário
- **D-01:** Selector Particular/Empresa no **topo** do formulário (RadioGroup ou Select), seguido das secções de campos — tudo numa única página, sem wizard/steps
- **D-02:** Ao mudar de tipo, exibir dialog de confirmação: "Mudar o tipo irá limpar os dados de [Tipo Anterior]. Continuar?" — só limpa após confirmação do utilizador
- **D-03:** Campos de Particular (idade, sexo, nacionalidade) e Empresa (nome comercial, sede, representante legal, cargo) trocam dinamicamente com base no tipo seleccionado; campos comuns (nome, NIF, email, telefone, morada) permanecem visíveis para ambos os tipos

### numero_cliente
- **D-04:** Exibido como **badge/chip** ao lado do nome do cliente:
  - Na ficha de detalhe: badge `CLI-0001` no cabeçalho junto ao nome
  - Na listagem: coluna ou badge na linha do cliente
  - Campo gerado pelo backend após save — não editável pelo utilizador
  - Enquanto não existe (cliente novo antes de guardar): oculto ou placeholder "—"

### Flag Avençado
- **D-05:** Toggle/Switch ou Checkbox com label "Avençado" no formulário de criação/edição
- **D-06:** Na listagem: badge distintivo (ex: "Avençado" badge verde) visível na linha do cliente
- **D-07:** Na ficha de detalhe: badge junto ao `numero_cliente`

### Claude's Discretion
- Componente UI específico para o selector de tipo (RadioGroup vs Select vs ToggleGroup): Claude decide com base nos componentes shadcn disponíveis
- Validação Zod dos campos específicos por tipo: Claude decide (campos de Empresa vs Particular obrigatórios)
- Animação de transição ao trocar campos: Claude decide

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Frontend — Formulários e componentes existentes
- `web/src/app/(dashboard)/clientes/` — páginas de clientes a actualizar (novo, editar, listagem, detalhe)
- `web/src/schemas/` — schemas Zod existentes para clientes (a estender)
- `web/src/hooks/use-clientes.ts` (ou similar) — hooks TanStack Query a actualizar com novos campos

### Backend (resultado da Phase 57)
- `backend/src/main/java/com/lexcv/models/Cliente.java` — campos adicionados na Phase 57 (numero_cliente, tipo como TipoCliente, avencado, dados_tipo JSON)

### Requirements
- `.planning/REQUIREMENTS.md` — PERF-02, PERF-03, PERF-04, EMP-02 (in scope desta fase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- shadcn/ui já instalado: `Dialog` (para confirmação de troca de tipo), `Badge`, `Switch`/`Checkbox`, `RadioGroup`/`Select`
- React Hook Form + Zod já em uso nos formulários existentes — padrão a seguir
- TanStack Query hooks já em `web/src/hooks/` para clientes

### Established Patterns
- Formulários existentes usam React Hook Form + Zod schemas em `web/src/schemas/`
- Dual-view pattern CSS puro (`hidden md:block` / `md:hidden`) para mobile/desktop
- Bottom-sheet dialogs em mobile (Phase 55) — dialog de confirmação deve seguir o mesmo padrão

### Integration Points
- Página `/clientes/novo` e `/clientes/[id]/editar` — formulário a actualizar
- Listagem `/clientes` — adicionar badge `numero_cliente` e badge "Avençado"
- Ficha `/clientes/[id]` — cabeçalho com badge `numero_cliente` + "Avençado"

</code_context>

<specifics>
## Specific Ideas

- Transição de tipo com dialog de confirmação (não limpa silenciosamente)
- Badge CLI-0001 no cabeçalho da ficha ao lado do nome
- Badge "Avençado" verde nas listagens e ficha

</specifics>

<deferred>
## Deferred Ideas

- Procuração (upload obrigatório) → Phase 59
- Campos de intake (advogados, documentos, deslocações, honorários propostos) → Phase 59
- Ficha imprimível → Phase 60

</deferred>

---

*Phase: 58-Formulário Dinâmico*
*Context gathered: 2026-06-29*
