# Phase 57: Backend Schema + API - Context

**Gathered:** 2026-06-29
**Status:** Ready for planning

<domain>
## Phase Boundary

Estender a entidade `Cliente` e os endpoints REST (`/api/v1/clientes`) para suportar:
- `numero_cliente` sequencial por tenant (formato CLI-0001)
- `tipo_cliente` Particular vs. Empresa (reutilizando o campo `tipo` existente)
- Campos demográficos para Particular: `idade`, `sexo`, `nacionalidade`
- Dados de entidade coletiva para Empresa: `nome_comercial`, `nif`, `sede`, `representante_legal`, `cargo_representante`
- Flag `avencado` (boolean)

Não inclui: upload de procuração (Phase 59), campos de intake (Phase 59), ficha imprimível (Phase 60), formulário frontend (Phase 58).

</domain>

<decisions>
## Implementation Decisions

### numero_cliente
- **D-01:** Formato `CLI-0001` (prefixo CLI, 4 dígitos com zero-padding)
- **D-02:** Geração via `MAX+1` por tenant — query `SELECT MAX(numero_sequencial) FROM t_cliente WHERE tenant_id = ?` + incremento em memória antes de persistir. Campo `numero_sequencial` (Integer) armazenado separadamente para facilitar o MAX+1; `numero_cliente` (String) calculado e guardado como `CLI-XXXX`
- **D-03:** Geração no backend (no `@PrePersist` ou no controller antes do save), nunca no cliente

### tipo_cliente
- **D-04:** Reutilizar o campo `tipo` (String) existente na entidade `Cliente` — valores: `PARTICULAR` | `EMPRESA`. Sem migração de dados necessária (campo era livre, agora tem valores controlados)
- **D-05:** Adicionar enum `TipoCliente { PARTICULAR, EMPRESA }` no backend para type-safety; guardar como STRING no DB

### Campos específicos por tipo (Particular e Empresa)
- **D-06:** Coluna JSON `dados_tipo` (TEXT/JSONB) na tabela `t_cliente` — um único campo JSON guarda os campos específicos de cada tipo
  - Para PARTICULAR: `{ "idade": 35, "sexo": "M", "nacionalidade": "Cabo-Verdiana" }`
  - Para EMPRESA: `{ "nomeComercial": "...", "sede": "...", "representanteLegal": "...", "cargoRepresentante": "..." }`
- **D-07:** Serialização/deserialização com Jackson (`@Column(columnDefinition = "TEXT")` + `@Convert` ou `@Type`); não usar `@Embeddable` — JSON é mais simples e evita colunas nulas

### Flag avencado
- **D-08:** Campo `avencado` (Boolean) directamente na entidade `Cliente`

### Claude's Discretion
- Estratégia de concorrência para MAX+1: Claude decide (pode usar `synchronized` no controller, ou `SELECT FOR UPDATE`, ou aceitar colisão rara com retry)
- Validação de campos obrigatórios por tipo (ex: NIF obrigatório para EMPRESA): Claude decide o nível de validação no backend

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Entidade e Repositório
- `backend/src/main/java/com/lexcv/models/Cliente.java` — entidade JPA actual a estender
- `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` — repositório a estender com query MAX+1
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — endpoints CRUD de clientes (linhas ~151–350)

### Requirements
- `.planning/REQUIREMENTS.md` — PERF-01, PERF-02, PERF-03, PERF-04, PART-01, PART-02, EMP-01 (in scope desta fase)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Cliente.java` com `@PrePersist` — lógica de `numero_cliente` pode ir aqui
- `DocumentoTipo` enum existente — padrão a seguir para `TipoCliente` enum
- `clienteRepository.findByTenantId(tenantId)` — padrão de query por tenant já estabelecido

### Established Patterns
- Multi-tenancy: todos os campos filtrados por `tenant_id`; `getTenantId()` no controller
- JPA `ddl-auto=update` em dev — novas colunas adicionadas automaticamente
- `@PrePersist` já usado em `Cliente.java` para `createdAt` e `ativo`
- Enums com `@Enumerated(EnumType.STRING)` — padrão para `TipoCliente`

### Integration Points
- `POST /clientes` — criar cliente, gerar `numero_cliente` aqui
- `PUT /clientes/{id}` — actualizar cliente, incluir novos campos
- `GET /clientes` — listar clientes, incluir `numero_cliente` e `avencado` na resposta
- `GET /clientes/{id}` — detalhe, incluir todos os novos campos

</code_context>

<specifics>
## Specific Ideas

- `numero_cliente` formato: `CLI-0001` (4 dígitos, zero-padded)
- `tipo` reutilizado para `tipo_cliente` — sem coluna nova, apenas semântica controlada
- JSON em `dados_tipo` para campos de Particular e Empresa — uma coluna TEXT/JSONB

</specifics>

<deferred>
## Deferred Ideas

- Upload de procuração → Phase 59
- Campos de intake (advogados, documentos, deslocações, honorários propostos) → Phase 59
- Formulário dinâmico frontend → Phase 58
- Ficha imprimível → Phase 60

</deferred>

---

*Phase: 57-Backend Schema + API*
*Context gathered: 2026-06-29*
