# Phase 59: Procuração + Intake - Context

**Gathered:** 2026-06-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Adicionar à ficha do cliente:
1. **Procuração** — upload via MinIO, obrigatório com aviso (não bloqueia guardar), visualizável e substituível
2. **Intake completo** — advogados atribuídos (ligados a utilizadores do sistema), administrativos, documentos entregues, documentos a tratar, deslocações a realizar, honorários propostos

Tudo persistido no backend (novos endpoints ou extensão dos existentes) e visível na ficha do cliente no frontend.

</domain>

<decisions>
## Implementation Decisions

### Procuração
- **D-01:** Upload de procuração **não bloqueia** o submit — aviso visual no formulário ("Procuração em falta") mas o cliente pode ser guardado sem ela
- **D-02:** Quando presente: link para visualização via URL pré-assinada MinIO (padrão já usado para documentos em geral)
- **D-03:** Substituição: botão "Substituir procuração" que abre o file picker; substitui o ficheiro no MinIO e actualiza a referência
- **D-04:** Campo `procuracao_key` (String) na entidade `Cliente` — guarda a MinIO object key do ficheiro
- **D-05:** Upload via endpoint existente de documentos ou endpoint dedicado `POST /clientes/{id}/procuracao` — Claude decide qual

### Advogados e Administrativos
- **D-06:** Advogados atribuídos ao cliente são **utilizadores do sistema** com papel `ADVOGADO` (entidade `User` existente)
- **D-07:** Tabela de ligação `t_cliente_advogado (cliente_id, user_id)` — relação ManyToMany entre `Cliente` e `User`
- **D-08:** Administrativos: mesma abordagem — tabela `t_cliente_administrativo (cliente_id, user_id)` com utilizadores de papel `ASSISTENTE` ou `TECNICO`
- **D-09:** Endpoints: `GET /clientes/{id}/advogados`, `POST /clientes/{id}/advogados/{userId}`, `DELETE /clientes/{id}/advogados/{userId}` (idem para administrativos)
- **D-10:** UI: multi-select/combobox de utilizadores do sistema filtrados por papel; UX de modal para adicionar

### Listas de Intake (docs entregues, docs a tratar, deslocações, honorários propostos)
- **D-11:** UX: botão "Adicionar" abre **modal** com os campos do item; remoção com ícone X em cada linha da lista
- **D-12:** Armazenamento: colunas JSON na tabela `t_cliente` (extensão do padrão `dados_tipo` da Phase 57):
  - `documentos_entregues` (TEXT/JSON): `[{ descricao, data }]`
  - `documentos_a_tratar` (TEXT/JSON): `[{ descricao }]`
  - `deslocacoes` (TEXT/JSON): `[{ descricao, local, data }]`
  - `honorarios_propostos` (TEXT/JSON): `{ total, totalPorExtenso, previsao }`
- **D-13:** Honorários propostos são um **objecto único** (não lista) — totalidade, por extenso, previsão; visível e editável na ficha

### Claude's Discretion
- Endpoint para upload de procuração: reutilizar `POST /documentos` com tipo especial vs. endpoint dedicado `/clientes/{id}/procuracao`
- Validação de papéis ao adicionar advogado/administrativo (verificar papel no servidor vs. confiar no client)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend — Modelos e endpoints existentes
- `backend/src/main/java/com/lexcv/models/Cliente.java` — entidade a estender (resultado da Phase 57: inclui dados_tipo, avencado, numero_cliente)
- `backend/src/main/java/com/lexcv/models/User.java` — entidade User (advogados/administrativos são Users)
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — endpoints de documentos/MinIO (~linha 1750+) como referência para upload de procuração
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` — repositório de utilizadores (para listar por papel)

### Frontend — Padrões de upload existentes
- `web/src/app/(dashboard)/documentos/` — componente de upload com MinIO pré-assinado (padrão a seguir)

### Requirements
- `.planning/REQUIREMENTS.md` — PROC-01, PROC-02, INT-01 a INT-07 (in scope desta fase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Upload MinIO já implementado: endpoint de documentos usa presigned URL — mesmo padrão para procuração
- `User.java` com `roles` (Set<Role>) — filtrar por papel para listar advogados/administrativos
- Componente de upload no frontend (Phase 51) — reutilizável para procuração
- shadcn/ui `Dialog` já disponível — para modais de adição de items de intake
- shadcn/ui `Combobox`/`Select` — para seleção de advogados do sistema

### Established Patterns
- JSON columns já definidos em Phase 57 (`dados_tipo`) — mesmo padrão para `documentos_entregues`, `deslocacoes`, etc.
- ManyToMany em JPA já usado noutras entidades (ex: `t_user_role`) — padrão para `t_cliente_advogado`
- MinIO presigned URLs para download de documentos — mesmo para procuração
- Modal UX (Phase 55 bottom-sheet dialogs) — adaptar para modais de intake

### Integration Points
- `GET /clientes/{id}` — estender resposta com procuração e todos os campos de intake
- `PUT /clientes/{id}` — aceitar `honorariosPropostos`, `documentosEntregues`, etc.
- Novos endpoints para advogados/administrativos (add/remove)
- Ficha do cliente no frontend: adicionar secções de intake após os dados base

</code_context>

<specifics>
## Specific Ideas

- Procuração: aviso visual (banner/badge amarelo "Procuração em falta") sem bloquear submit
- Advogados e administrativos ligados aos Users do sistema (não texto livre)
- Todos os items de intake adicionados via modal (não inline)
- Honorários propostos como objecto único (não lista): total, por extenso, previsão

</specifics>

<deferred>
## Deferred Ideas

- Ficha imprimível → Phase 60
- Assinatura digital da procuração → Future (FUT-01)

</deferred>

---

*Phase: 59-Procuração + Intake*
*Context gathered: 2026-06-29*
