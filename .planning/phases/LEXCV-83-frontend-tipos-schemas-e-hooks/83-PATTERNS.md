# Phase 83: Frontend — Tipos, Schemas e Hooks - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 3 (all modified, none created — plus 3 optional new label-map utils at Claude's discretion)
**Analogs found:** 3 / 3 (self-referential: each file's analog is the pre-existing sibling pattern within the same file)

**Source of truth verified directly from live code:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 1676-2030 (Decisão/Testemunha/Facto, all 12 endpoints), lines 834-1043 (`listProcessos`/`createProcesso`/`updateProcesso`/`createProcessoIntake` for `juizo`/`origem`), plus the five backing model files (`Decisao.java`, `Testemunha.java`, `Facto.java`, `Processo.java`, `TipoDecisao.java`, `TipoTestemunha.java`, `OrigemProcesso.java`).

## CRITICAL FINDING — do not blindly copy the ProcessoParte/ProcessoFase shape

The existing `ProcessoParte`/`ProcessoFase` types in `web/src/types/processos.ts` are **themselves stale examples of the exact bug this phase must prevent** — do not use them as a field-naming template, only as a hook-*structure* template.

- `Parte` entity (`backend/.../models/Parte.java`) has fields `id, processoId, nome, tipo` — **no `tenant_id`, no `created_at`**. `GET /processos/{id}/partes` (`listPartes`, line 1552) returns `parteRepository.findByProcessoId(id)` — the **raw entity list**, serialized camelCase by Jackson (`processoId`, `nome`, `tipo`). `POST /processos/{id}/partes` (`createParte`, line 1562) also returns the raw saved entity, camelCase.
  But `web/src/types/processos.ts` declares `ProcessoParte` as `{ id, tenant_id, processo_id, tipo, nome, nif, created_at }` — **`tenant_id` and `created_at` don't exist on the wire at all, and the real key is `processoId` not `processo_id`.** `nif` also doesn't exist on `Parte`. This type has never matched the real API.
- `ProcessoFase` is worse: **inconsistent per-verb**. `GET /processos/{id}/fases` (`listFases`, line 1574) hand-builds a `Map<String,Object>` with **explicit snake_case keys** (`processo_id`, `fase_id`, `data_inicio`, `data_fim`, `status`, `nome`) — this matches the current `ProcessoFase` TS type. But `POST /processos/{id}/fases` (`createProcessoFase`, line 1602) and `PUT .../fases/{faseId}` (`updateProcessoFase`, line 1627) return `processoFaseRepository.save(pf)` — the **raw `ProcessoFase` entity**, camelCase (`processoId`, `faseId`, `dataInicio`, `dataFim`, `ativa`, no `status`, no `nome`). So `useAddProcessoFase`/`useUpdateProcessoFaseStatus` are currently typed to return `ProcessoFase` (snake_case) but actually receive camelCase JSON — accessing `.processo_id` on their result is `undefined` today.

**Good news for the 3 new entities (Decisão/Facto/Testemunha):** all 12 new endpoints (GET list, POST create, PUT update, DELETE) are internally **100% consistent** — every one returns the raw JPA entity (or a `Map.of("message", ...)` for DELETE), so every field is plain **camelCase matching the Java entity field name exactly**, with no snake_case variant anywhere in the wire format. Define the new TS types with camelCase field names identical to the entity (see field-by-field extraction below), and still add `normalizeX`/round-trip coverage per the CONTEXT.md decision, in case a future refactor changes this. Do **not** invent snake_case aliases for these three new entities — there is no snake_case form on the wire to fall back to.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog (structure) | Match Quality |
|---|---|---|---|---|
| `web/src/types/processos.ts` | model (types) | transform | same file — `ProcessoFase`/`ProcessoParte`/`Prazo` blocks (structure only, not field-naming) | role-match (naming must diverge, see finding above) |
| `web/src/schemas/processos.ts` | utility (zod schemas) | transform | same file — `processoFaseFormSchema`/`prazoFormSchema` blocks | exact (structure) |
| `web/src/hooks/use-processos.ts` | hook | CRUD | same file — `useProcessoFases`/`useAddProcessoFase`/`useUpdateProcessoFaseStatus` trio (list/create/update), plus `useDeleteProcesso`'s cache-removal pattern for delete | exact (structure) |
| `web/src/hooks/use-processos.ts` (Decisão POST specifically) | hook | file-I/O (multipart) | `web/src/hooks/use-documentos.ts` `useUploadDocumento` (FormData construction) | exact |
| `web/src/lib/tipo-decisao.ts` / `tipo-testemunha.ts` / `origem-processo.ts` (new, optional convenience files, Claude's discretion) | utility (enum↔label map) | transform | `web/src/lib/cliente-documento-tipo.ts` | exact |

## Pattern Assignments

### `web/src/types/processos.ts` (model, transform)

**Analog for structure:** existing `ProcessoFase`/`Prazo` blocks in the same file (lines 76-94, 165-184).

#### 1. `Decisao` — backend source: `Decisao.java` + `ResourceController.java` lines 1681-1841

Entity fields (`Decisao.java`):
```java
private Integer id;
private UUID processoId;      // @Column(name = "processo_id")
private LocalDate data;
private TipoDecisao tipo;     // @Enumerated(STRING) — DESPACHO | DECISAO_INTERLOCUTORIA | SENTENCA | ACORDAO
private String resumo;        // nullable
private UUID documentoId;     // @Column(name = "documento_id"), nullable
```

- `GET /processos/{id}/decisoes` → `200` `Decisao[]`, each item:
  ```json
  { "id": 1, "processoId": "uuid", "data": "2026-07-01", "tipo": "SENTENCA", "resumo": "texto ou null", "documentoId": "uuid ou null" }
  ```
- `POST /processos/{id}/decisoes` — **`multipart/form-data`**, NOT JSON (`@PostMapping(..., consumes = MULTIPART_FORM_DATA_VALUE)`, lines 1692-1698). Exact form fields (`@RequestParam`):
  | field | required | type |
  |---|---|---|
  | `file` | no | binary (File/Blob) |
  | `data` | yes | string `YYYY-MM-DD` |
  | `tipo` | yes | string, one of `TipoDecisao` |
  | `resumo` | no | string |

  Response `201` = full `Decisao` JSON as above (`documentoId` populated only if `file` was sent and upload succeeded).
- `PUT /processos/{id}/decisoes/{decisaoId}` — JSON body, but controller reads it as **`Map<String,Object>`** (line 1771), not entity-typed. Exact required/optional keys read via `payload.get(...)`:
  | key | required | notes |
  |---|---|---|
  | `data` | yes → 400 `{"message":"data é obrigatória"}` if blank | `YYYY-MM-DD` |
  | `tipo` | yes → 400 `{"message":"tipo é obrigatório"}` if blank, 400 `{"message":"Parâmetro 'tipo' inválido"}` if not a valid enum | |
  | `resumo` | no | |

  **Note:** `documentoId`/`file` are NOT accepted on PUT — the attached document can only be set at creation time. Response `200` = full `Decisao` JSON.
- `DELETE /processos/{id}/decisoes/{decisaoId}` → `200` `{ "message": "Decisão removida com sucesso!" }`. Also cascades: deletes the linked `Documento` + storage object if `documentoId` was set (lines 1826-1838) — irrelevant to the TS type but relevant to invalidating any `documentos` query keys on success.

Suggested TS type:
```typescript
export type TipoDecisao = "DESPACHO" | "DECISAO_INTERLOCUTORIA" | "SENTENCA" | "ACORDAO";

export interface Decisao {
  id: number;
  processoId: string;
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
  documentoId?: string;
}

export interface DecisaoCreateRequest {
  file?: File;
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
}

export interface DecisaoUpdateRequest {
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
}
```

#### 2. `Testemunha` — backend source: `Testemunha.java` + `ResourceController.java` lines 1844-1946

Entity fields (`Testemunha.java`):
```java
private Integer id;
private UUID processoId;
private String nome;          // nullable = false
private String contacto;      // nullable
private TipoTestemunha tipo;  // @Enumerated(STRING), nullable — AUTOR | REU
private String notas;         // nullable
```

- `GET /processos/{id}/testemunhas` → `200` `Testemunha[]`:
  ```json
  { "id": 1, "processoId": "uuid", "nome": "João", "contacto": "9xxxxxxx ou null", "tipo": "AUTOR ou null", "notas": "texto ou null" }
  ```
- `POST /processos/{id}/testemunhas` — JSON body, `@RequestBody Map<String,Object>` (line 1856, current shape post-WR-03). Keys:
  | key | required | notes |
  |---|---|---|
  | `nome` | yes → 400 `{"message":"nome é obrigatório"}` | |
  | `tipo` | no | one of `TipoTestemunha`; 400 `{"message":"Parâmetro 'tipo' inválido"}` if present but invalid |
  | `contacto` | no | |
  | `notas` | no | |

  Response `201` = full `Testemunha` JSON.
- `PUT /processos/{id}/testemunhas/{testemunhaId}` — same `Map<String,Object>` shape and same validation as POST (lines 1893-1929, identical fields, all required/optional rules mirror POST). Response `200` = full `Testemunha` JSON.
- `DELETE /processos/{id}/testemunhas/{testemunhaId}` → `200` `{ "message": "Testemunha removida com sucesso!" }`.

Suggested TS type:
```typescript
export type TipoTestemunha = "AUTOR" | "REU";

export interface Testemunha {
  id: number;
  processoId: string;
  nome: string;
  contacto?: string;
  tipo?: TipoTestemunha;
  notas?: string;
}

export interface TestemunhaCreateRequest {
  nome: string;
  tipo?: TipoTestemunha;
  contacto?: string;
  notas?: string;
}

export interface TestemunhaUpdateRequest {
  nome: string;
  tipo?: TipoTestemunha;
  contacto?: string;
  notas?: string;
}
```

#### 3. `Facto` — backend source: `Facto.java` + `ResourceController.java` lines 1948-2030

Entity fields (`Facto.java`, unique constraint `(processo_id, ordem)`):
```java
private Integer id;
private UUID processoId;
private String descricao;   // nullable = false, TEXT
private LocalDate data;     // nullable
private Integer ordem;      // nullable = false
```

- `GET /processos/{id}/factos` → `200` `Facto[]`, ordered by `ordem` ascending (repository method `findByProcessoIdOrderByOrdemAsc`):
  ```json
  { "id": 1, "processoId": "uuid", "descricao": "texto", "data": "2026-07-01 ou null", "ordem": 1 }
  ```
- `POST /processos/{id}/factos` — **entity-typed** `@RequestBody Facto facto` (line 1960, JSON body, camelCase keys same as entity). Client should send `{ descricao, data? }`.
  - **`ordem` is server-computed on create — any client-supplied `ordem` value is silently overwritten.** Server logic (lines 1968-1978): `facto.setProcessoId(id)` overwrites any client `processoId`; inside a `synchronized` block, `nextOrdem = factoRepository.findMaxOrdemByProcessoId(id).orElse(0) + 1` then `facto.setOrdem(nextOrdem)`. On a `DataIntegrityViolationException` (unique constraint race), returns `409` `{"message":"Conflito ao atribuir ordem ao facto, tente novamente"}`.
  - `descricao` required → `400` `{"message":"descricao é obrigatória"}` if null/blank.
  - Response `201` = full `Facto` JSON with server-assigned `ordem`.
- `PUT /processos/{id}/factos/{factoId}` — **entity-typed** `@RequestBody Facto payload` (line 1987). **Unlike create, `ordem` IS client-supplied and required** here:
  - `descricao` required → `400` `{"message":"descricao é obrigatória"}`.
  - `ordem` required → `400` `{"message":"ordem é obrigatória"}` if `payload.getOrdem() == null`.
  - `data` optional, passed through as-is (including `null`, which clears it).
  - On unique-constraint conflict (another Facto already has that `ordem` for this processo) → `409` `{"message":"Conflito ao atribuir ordem ao facto, tente novamente"}` — **the frontend must handle 409 distinctly here** (e.g. surfaced as a toast, not treated as a generic error) since it's a legitimate expected outcome of concurrent reordering, not a bug.
  - Response `200` = full `Facto` JSON.
- `DELETE /processos/{id}/factos/{factoId}` → `200` `{ "message": "Facto removido com sucesso!" }`.

Suggested TS type:
```typescript
export interface Facto {
  id: number;
  processoId: string;
  descricao: string;
  data?: string;
  ordem: number;
}

export interface FactoCreateRequest {
  descricao: string;
  data?: string;
  // ordem intentionally omitted — server-computed, any client value is ignored
}

export interface FactoUpdateRequest {
  descricao: string;
  data?: string;
  ordem: number; // required on update, unlike create
}
```

#### 4. `juizo` / `origem` on `Processo`

`Processo.java` (lines 39-45):
```java
private String juizo;                 // plain optional String, no validation anywhere in controller

@Enumerated(EnumType.STRING)
@Column(name = "origem")
private OrigemProcesso origem;        // enum: PETICAO_INICIAL | NOTIFICACOES_AVULSAS
```

- `listProcessos` (`GET /processos`, lines 834-961) hand-builds a `LinkedHashMap` per row with **explicit snake_case-ish keys but `juizo`/`origem` are single-word so no case ambiguity** (lines 923-924):
  ```java
  m.put("juizo", p.getJuizo());
  m.put("origem", p.getOrigem());
  ```
  So the list response item includes `"juizo": "1º Juízo Cível" | null` and `"origem": "PETICAO_INICIAL" | "NOTIFICACOES_AVULSAS" | null` alongside the other already-snake_case keys (`numero_processo`, `cliente_id`, etc. — see full key list at lines 915-929).
- `getProcesso` (`GET /processos/{id}`), `createProcesso` (`POST /processos`, line 965), `updateProcesso` (`PUT /processos/{id}`, line 991), `createProcessoIntake` (`POST /processos/intake`, line 1032) all return/consume the **raw `Processo` entity** — Jackson camelCase, but again `juizo`/`origem` have no camelCase/snake_case ambiguity since they're single words. The keys are literally `"juizo"` and `"origem"` in both directions (request body for create/update, response body for all four).
- `createProcesso` (line 965): no server-side validation of `juizo`/`origem` — both pass through as sent.
- `updateProcesso` (lines 991-1012): **`juizo` IS updatable** (`processo.setJuizo(payload.getJuizo())`, line 1002) but **`origem` is intentionally excluded** — comment at line 1004: `// origem is intentionally excluded: immutable after intake, only writable via POST /processos/intake`. Do not send `origem` in the `PUT` payload; it will be silently ignored either way but the frontend type/schema for `ProcessoUpdateRequest` should not offer it as editable.
- `createProcessoIntake` (lines 1032-1043): **`origem` is REQUIRED** — if `processo.getOrigem() == null`, returns `422` `{"message":"Não é possível criar processo: campo 'origem' é obrigatório no intake", "camposEmFalta": ["origem"]}`. This is the endpoint backing `useCreateIntake` in `use-processos.ts` (already wired, currently missing `origem`/`juizo` in `toProcessoApiPayload`).

Per CONTEXT.md decision: `origem` in `schemas/processos.ts` must become `z.enum(["PETICAO_INICIAL", "NOTIFICACOES_AVULSAS"])` (required for the intake form; the general `processoFormSchema` used by `updateProcesso` should likely keep it read-only/absent since the field is immutable post-intake — Claude's discretion on whether one shared schema or two separate schemas is used, but the wire values are exactly these two strings, ASCII, matching `OrigemProcesso.java`).

---

### `web/src/schemas/processos.ts` (utility, transform)

**Analog:** `processoFaseFormSchema` (lines 36-38) for the simple-entity pattern; `prazoFormSchema` (lines 91-96) for the enum-with-default pattern; `conflictCheckDecisaoFormSchema` (lines 57-74) for the `superRefine` conditional-validation pattern (useful if Decisão's `resumo` should become conditionally required for certain `tipo` values — not specified, Claude's discretion).

Existing `optionalTrimmedString` helper (lines 3-7) — reuse for `resumo`, `contacto`, `notas`, `data` fields:
```typescript
const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();
```

Existing enum pattern to replicate for `TipoDecisao`/`TipoTestemunha`/`origem` (from `processoFaseStatusSchema`, line 34, and `conflictNivelEnum`, lines 50-55):
```typescript
export const processoFaseStatusSchema = z.enum(["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA"]);
```
→ apply identically:
```typescript
export const tipoDecisaoSchema = z.enum(["DESPACHO", "DECISAO_INTERLOCUTORIA", "SENTENCA", "ACORDAO"]);
export const tipoTestemunhaSchema = z.enum(["AUTOR", "REU"]);
export const origemProcessoSchema = z.enum(["PETICAO_INICIAL", "NOTIFICACOES_AVULSAS"]);
```

Simple required-field entity pattern to replicate for `Facto`/`Testemunha` forms (from `processoParteFormSchema`, lines 26-30):
```typescript
export const processoParteFormSchema = z.object({
  tipo: optionalTrimmedString,
  nome: z.string().trim().min(1, "O nome é obrigatório"),
  nif: optionalTrimmedString,
});
```

---

### `web/src/hooks/use-processos.ts` (hook, CRUD)

**Analog for list/create (4-hook family):** `useProcessoFases`/`useAddProcessoFase`/`useUpdateProcessoFaseStatus` (lines 258-303) — exact structure to replicate 3×, per CONTEXT.md decision.

```typescript
export function useProcessoFases(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["processos", "fases", id],
    queryFn: () => apiFetch<ProcessoFase[]>(`/processos/${encodeURIComponent(id)}/fases`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddProcessoFase(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProcessoFaseCreateRequest) =>
      apiFetch<ProcessoFase>(`/processos/${encodeURIComponent(id)}/fases`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ProcessoFaseCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "fases", id] });
    },
  });
}

export function useUpdateProcessoFaseStatus(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { faseId: number; payload: ProcessoFaseUpdateRequest }) =>
      apiFetch<ProcessoFase>(
        `/processos/${encodeURIComponent(id)}/fases/${encodeURIComponent(String(args.faseId))}`,
        {
          method: "PUT",
          body: JSON.stringify(args.payload satisfies ProcessoFaseUpdateRequest),
        },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<ProcessoFase[] | undefined>(["processos", "fases", id], (current) => {
        if (!current) return current;
        return current.map((item) => (item.id === updated.id ? updated : item));
      });
    },
  });
}
```

**Analog for delete + cache-removal:** `useDeleteProcesso` (lines 212-230) for the `invalidateQueries`/`removeQueries` pattern; simplest 1:1 shape is `useDeleteDocumento` (`web/src/hooks/use-documentos.ts` lines 74-89):
```typescript
export function useDeleteDocumento(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/documentos/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["documentos", "list"] }),
        queryClient.removeQueries({ queryKey: ["documentos", "detail", id] }),
      ]);
    },
  });
}
```
Note: DELETE responses here are `{ message: string }`, not `void`/204 — `apiFetch<{ message: string }>` (or `apiFetch<void>` and ignore the body) both work since `apiFetch` only special-cases `204`.

**Analog for multipart POST (Decisão create):** `useUploadDocumento` (`web/src/hooks/use-documentos.ts` lines 49-72):
```typescript
export function useUploadDocumento() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DocumentoUploadPayload) => {
      const form = new FormData();
      form.set("file", payload.file);
      if (payload.nome?.trim()) form.set("nome", payload.nome.trim());
      // ... more optional fields, form.set(key, value) only when present
      return apiFetch<DocumentoUploadResponse>("/documentos/upload", {
        method: "POST",
        body: form,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["documentos", "list"] });
    },
  });
}
```
`apiFetch` (`web/src/lib/api.ts` lines 16-18) auto-skips setting `Content-Type: application/json` when `body instanceof FormData`, so no special handling needed beyond building the `FormData` with the exact field names `file`, `data`, `tipo`, `resumo` (see Decisão POST shape above — note these are **not** `camelCase`-transformed, they are literally `data`/`tipo`/`resumo`/`file` as raw multipart field names, matching the `@RequestParam` names 1:1).

**Query key convention (per CONTEXT.md decision):** `["processos", "<subresource>", id]`, exactly mirroring `["processos", "fases", id]` / `["processos", "partes", id]` / `["processos", "movimentacoes", id]`. Apply as `["processos", "decisoes", id]`, `["processos", "testemunhas", id]`, `["processos", "factos", id]`.

**`normalizeProcesso`/`toProcessoApiPayload` update (mandatory same-revision per CONTEXT.md):** lines 80-115. Add `juizo`/`origem` to both:
```typescript
// ProcessoApi (raw wire type) — add:
juizo?: string;
origem?: "PETICAO_INICIAL" | "NOTIFICACOES_AVULSAS";

// normalizeProcesso — add:
juizo: api.juizo,
origem: api.origem,

// ProcessoApiPayload — add:
juizo?: string;
// origem: include only in the intake payload builder / create, NOT in the general update payload builder (backend ignores it on PUT anyway, per updateProcesso line 1004)

// toProcessoApiPayload — add:
juizo: payload.juizo,
```
Since `juizo`/`origem` have no camelCase/snake_case ambiguity on the wire (single-word keys), no `??` fallback chain is needed for them specifically — unlike every other field in `ProcessoApi` which defensively checks both forms because `listProcessos` (snake_case map) and `getProcesso`/`createProcesso`/`updateProcesso` (camelCase entity) disagree. **This is the acceptance-test-worthy detail:** a round-trip test (create/update via hook → refetch via `useProcessos` list → confirm `juizo`/`origem` survive) exercises exactly the snake_case-list vs. camelCase-entity split described in the Critical Finding above, which is why CONTEXT.md requires it as the acceptance criterion rather than just `tsc`/build passing.

---

## Shared Patterns

### Auth (`@PreAuthorize`)
**Source:** `ResourceController.java` — consistent across all 12 new endpoints:
- `GET` (list) → `@PreAuthorize("hasAuthority('processos:view')")` (lines 1680, 1844, 1948)
- `POST`/`PUT`/`DELETE` → `@PreAuthorize("hasAuthority('processos:edit')")` (lines 1691, 1766, 1814, 1854, 1891, 1932, 1958, 1982, 2016)
**Apply to:** N/A for this phase (frontend has no route-level guards to add — `web/src/lib/permissions.ts` already covers `processos:view`/`processos:edit`, no new scopes introduced by Decisão/Testemunha/Facto).

### Error response shape
**Source:** every error path in `ResourceController.java` returns `ResponseEntity.status(<code>).body(Map.of("message", "..."))`.
**Apply to:** all new hooks — `apiFetch` (`web/src/lib/api.ts` lines 26-48) already extracts `json.message` from any non-2xx body and surfaces it via toast + throws `Error("API {status}: {message}")`. No new error-handling code needed in the hooks themselves; callers that need to special-case `409` (Facto ordem conflict) should catch the thrown `Error` and inspect its message/status.

### Not-found guard
**Source:** repeated verbatim 12+ times: `if (processo == null || !processo.getTenantId().equals(getTenantId())) return 404 {"message":"Processo não encontrado"}`, then a second child-scoped check e.g. `if (decisao == null || !decisao.getProcessoId().equals(id)) return 404 {"message":"Decisão não encontrada"}`.
**Apply to:** no frontend action needed — this just confirms 404 is the correct not-found status to expect and that error `message` text is in Portuguese, consistent with existing toasts.

### Enum-with-label pattern (`documento_tipo` precedent)
**Source:** `web/src/lib/cliente-documento-tipo.ts` (full file, 56 lines) + `web/src/types/clientes.ts` line 1 (`export type DocumentoTipo = "BI" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL";`).
```typescript
export interface DocumentoTipoOption {
  value: DocumentoTipo;
  label: string;
}

const OPTIONS_BY_TIPO: Record<ClienteTipo, DocumentoTipoOption[]> = {
  PARTICULAR: [
    { value: "CNI", label: "CNI" },
    { value: "BI", label: "BI" },
    { value: "PASSAPORTE", label: "Passaporte" },
  ],
  EMPRESA: [{ value: "REG_COMERCIAL", label: "Registo Comercial" }],
};

export function getDocumentoTipoOptions(tipo: ClienteTipo | undefined): DocumentoTipoOption[] {
  return [...OPTIONS_BY_TIPO[tipo ?? "PARTICULAR"]];
}

export function toDocumentoTipo(value: string | undefined, tipo: ClienteTipo | undefined): DocumentoTipo | undefined {
  if (!value) return undefined;
  const options = getDocumentoTipoOptions(tipo);
  return options.some((option) => option.value === value) ? (value as DocumentoTipo) : undefined;
}
```
**Apply to:** `TipoDecisao`, `TipoTestemunha`, `origem` — per CONTEXT.md decision, wire values stay ASCII/exact-enum-name, PT labels live only in a presentation-layer map (this is Phase 84's concern to *use*, but the label constants can be introduced now alongside the types since they're pure data with no UI dependency). Suggested labels (not mandated by CONTEXT.md, Claude's discretion for exact wording):
- `TipoDecisao`: `DESPACHO` → "Despacho", `DECISAO_INTERLOCUTORIA` → "Decisão Interlocutória", `SENTENCA` → "Sentença", `ACORDAO` → "Acórdão"
- `TipoTestemunha`: `AUTOR` → "Autor", `REU` → "Réu"
- `origem` (`OrigemProcesso`): `PETICAO_INICIAL` → "Petição Inicial", `NOTIFICACOES_AVULSAS` → "Notificações Avulsas" (labels explicitly named in CONTEXT.md decisions)

## No Analog Found

None — every file/endpoint has a direct structural analog already in the codebase (see above). The only genuinely new *shape* is Decisão's multipart POST combined with a `Map<String,Object>`-typed PUT on the same resource, but the multipart half is covered by `use-documentos.ts` and the loose-JSON half is covered by `useAddProcessoFase`'s sibling `Map<String,Object>`-backed endpoints (`createProcessoFase`/`updateProcessoFase` also take `Map<String,Object>`, ResourceController.java lines 1603, 1631).

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (full file, 2891 lines, targeted reads only — lines 1-90, 834-1043, 1552-1651, 1675-2030), `backend/src/main/java/com/lexcv/models/{Decisao,Testemunha,Facto,Processo,Parte,ProcessoFase,TipoDecisao,TipoTestemunha,OrigemProcesso}.java`, `web/src/hooks/{use-processos,use-documentos}.ts`, `web/src/types/processos.ts`, `web/src/schemas/processos.ts`, `web/src/lib/cliente-documento-tipo.ts`, `web/src/types/clientes.ts`, `web/src/lib/api.ts`.
**Files scanned:** 16
**Pattern extraction date:** 2026-07-07
