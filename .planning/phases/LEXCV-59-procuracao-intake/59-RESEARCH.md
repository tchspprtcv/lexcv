# Phase 59: Procuração + Intake — Research

**Researched:** 2026-06-29
**Domain:** Spring Boot JPA entity extension, MinIO file upload, ManyToMany junction tables, JSON column converters, Next.js form + modal UX
**Confidence:** HIGH — all findings are from direct codebase inspection

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Upload de procuração não bloqueia o submit — aviso visual no formulário ("Procuração em falta") mas o cliente pode ser guardado sem ela
- D-02: Quando presente: link para visualização via URL pré-assinada MinIO (padrão já usado para documentos em geral)
- D-03: Substituição: botão "Substituir procuração" que abre o file picker; substitui o ficheiro no MinIO e actualiza a referência
- D-04: Campo `procuracao_key` (String) na entidade `Cliente` — guarda a MinIO object key do ficheiro
- D-05: Upload via endpoint existente de documentos ou endpoint dedicado `POST /clientes/{id}/procuracao` — Claude decide qual
- D-06: Advogados atribuídos ao cliente são utilizadores do sistema com papel `ADVOGADO` (entidade `User` existente)
- D-07: Tabela de ligação `t_cliente_advogado (cliente_id, user_id)` — relação ManyToMany entre `Cliente` e `User`
- D-08: Administrativos: mesma abordagem — tabela `t_cliente_administrativo (cliente_id, user_id)` com utilizadores de papel `ASSISTENTE` ou `TECNICO`
- D-09: Endpoints: `GET /clientes/{id}/advogados`, `POST /clientes/{id}/advogados/{userId}`, `DELETE /clientes/{id}/advogados/{userId}` (idem para administrativos)
- D-10: UI: multi-select/combobox de utilizadores do sistema filtrados por papel; UX de modal para adicionar
- D-11: UX: botão "Adicionar" abre modal com os campos do item; remoção com ícone X em cada linha da lista
- D-12: Armazenamento: colunas JSON na tabela `t_cliente`:
  - `documentos_entregues` (TEXT/JSON): `[{ descricao, data }]`
  - `documentos_a_tratar` (TEXT/JSON): `[{ descricao }]`
  - `deslocacoes` (TEXT/JSON): `[{ descricao, local, data }]`
  - `honorarios_propostos` (TEXT/JSON): `{ total, totalPorExtenso, previsao }`
- D-13: Honorários propostos são um objecto único (não lista) — totalidade, por extenso, previsão; visível e editável na ficha

### Claude's Discretion
- Endpoint para upload de procuração: reutilizar `POST /documentos` com tipo especial vs. endpoint dedicado `/clientes/{id}/procuracao`
- Validação de papéis ao adicionar advogado/administrativo (verificar papel no servidor vs. confiar no client)

### Deferred Ideas (OUT OF SCOPE)
- Ficha imprimível → Phase 60
- Assinatura digital da procuração → Future (FUT-01)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROC-01 | Upload de documento de procuração é obrigatório para todos os clientes (Particular e Empresa) | Campo `procuracao_key` em Cliente + endpoint dedicado POST /clientes/{id}/procuracao + warning badge no frontend |
| PROC-02 | Utilizador pode visualizar e substituir o documento de procuração na ficha do cliente | presignedDownloadUrl() em StorageService já existente; substituição: re-upload sobrescreve procuracao_key |
| INT-01 | Utilizador regista a descrição do caso no intake do cliente | Campo `descricao_caso` (String/TEXT) em Cliente ou via PUT /clientes/{id} |
| INT-02 | Utilizador associa advogados ao cliente com nome, número de cédula e contacto | Junction table t_cliente_advogado; User já tem nome + email/telefone; nº cédula pode ser extra field no User ou anotação na junção |
| INT-03 | Utilizador regista os administrativos que intervêm no processo do cliente | Junction table t_cliente_administrativo; mesma abordagem que advogados |
| INT-04 | Utilizador regista documentos entregues pelo cliente (lista) | Coluna JSON `documentos_entregues` em t_cliente via AttributeConverter |
| INT-05 | Utilizador regista documentos a tratar (lista) | Coluna JSON `documentos_a_tratar` em t_cliente via AttributeConverter |
| INT-06 | Utilizador regista deslocações a realizar (lista) | Coluna JSON `deslocacoes` em t_cliente via AttributeConverter |
| INT-07 | Utilizador regista honorários propostos no intake: valor total, valor por extenso, previsão | Coluna JSON `honorarios_propostos` em t_cliente via AttributeConverter (objecto único, não lista) |
</phase_requirements>

---

## Executive Summary

Phase 59 extends the `Cliente` entity and its detail/edit pages with procuração upload and a full intake section. Every pattern needed already exists in the codebase — the main work is additive: new fields, new junction-table entities, new endpoints, and new frontend sections.

**Three independent work streams:**

1. **Procuração** — add `procuracao_key` String field to `Cliente`, dedicated endpoint `POST /clientes/{id}/procuracao` (multipart), `GET /clientes/{id}/procuracao/download` returning a presigned URL, delete-old-and-upload-new on replace. Frontend: warning badge when null, link + replace button when present. Decision on D-05: use a **dedicated endpoint** (not the generic documents one) because procuração is a 1:1 relationship with the cliente, not a `t_documento` record. The generic upload endpoint creates a `Documento` entity and returns its ID — procuração needs only a key string on the Cliente row.

2. **Advogados/Administrativos (junction tables)** — two new JPA entities `ClienteAdvogado` and `ClienteAdministrativo` each mapping `(clienteId, userId)`. New endpoints follow the exact pattern of `/clientes/{id}/contactos` and `/clientes/{id}/notas` already in ResourceController. Backend must validate user role at write time (server-side — not trust client). UserRepository needs a `findByTenantIdAndRoleName` query. Frontend: modal with combobox of filtered users.

3. **JSON intake lists** — four new columns on `t_cliente` following the `dados_tipo` / `DadosTipoConverter` pattern exactly. Requires four new POJO classes and four new `AttributeConverter` implementations (or one generic approach). Also need `descricao_caso` field (plain String) for INT-01.

The largest risk is INT-02's requirement that advogados have "nome, número de cédula e contacto" — `User.java` has `nome` and `telefone`/`email` but no `numeroCedula`. This must be clarified: either add `numeroCedula` to `User`, add it to the junction table, or accept that the existing User fields satisfy the requirement.

**Primary recommendation:** Implement in three waves: (1) procuração backend+frontend, (2) advogados+administrativos backend+frontend, (3) JSON intake columns backend+frontend. Each wave is independently deployable.

---

## Existing Patterns to Reuse

### 1. JSON Column Pattern — DadosTipoConverter

The exact pattern for JSON columns already exists. `DadosTipo.java` is a plain POJO (no `@Entity`), and `DadosTipoConverter.java` implements `AttributeConverter<DadosTipo, String>` using `ObjectMapper`. The column on `Cliente` is:

```java
@Column(name = "dados_tipo", columnDefinition = "TEXT")
@Convert(converter = DadosTipoConverter.class)
private DadosTipo dadosTipo;
```

JPA's `ddl-auto=update` will add the new columns automatically when the entity gains them. No manual migration SQL is needed in dev. In prod (`ddl-auto=validate`) a migration script is required — but prod deployment is out of scope.

**Replicate exactly for each new JSON column.** The `@JsonInclude(JsonInclude.Include.NON_NULL)` on `DadosTipo` is a good pattern to carry over (avoids storing null fields in JSON text).

### 2. MinIO Upload Pattern

`StorageService.upload()` signature:
```java
public String upload(UUID tenantId, UUID documentoId, String filename,
                     InputStream inputStream, String contentType, long size)
```
Returns `objectKey = tenantId + "/" + documentoId + "/" + sanitisedFilename`.

`StorageService.presignedDownloadUrl(String objectKey)` returns a presigned URL (expiry from `MinioProperties.getPresignedUrlExpiry()`).

`StorageService.delete(String objectKey)` deletes an object.

For procuração, a synthetic `documentoId` (a new `UUID.randomUUID()`) is sufficient as the key segment — procuração is not a `t_documento` row.

Upload replaces: upload new object first, then delete old (same safe ordering already used in `/documentos/upload` replace path — "upload new BEFORE deleting old so old remains if upload fails").

### 3. ManyToMany Pattern — t_user_role

`User.java` shows the exact JPA ManyToMany:
```java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(
    name = "t_user_role",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
private Set<Role> roles = new HashSet<>();
```

For `t_cliente_advogado` and `t_cliente_administrativo`, the decision is to use **simple join entities** (not `@ManyToMany` on `Cliente`) because:
- `Cliente` must not eagerly fetch all advogados on every list query
- Endpoints are `POST/DELETE /clientes/{id}/advogados/{userId}` — direct junction-table manipulation is cleaner than managing a collection on the aggregate
- Mirrors the `ClienteContacto` / `ClienteNota` pattern exactly

### 4. Sub-resource Endpoint Pattern — ClienteContacto

ResourceController already implements the exact pattern for junction sub-resources:

```
GET  /clientes/{id}/contactos           → listClienteContactos
POST /clientes/{id}/contactos           → createClienteContacto
PUT  /clientes/{clienteId}/contactos/{contactoId}
DELETE /clientes/{clienteId}/contactos/{contactoId}
```

All follow: validate cliente exists + tenantId → validate sub-entity exists + tenantId → act. New advogado/administrativo endpoints copy this pattern verbatim.

### 5. Frontend Dialog Pattern

`web/src/components/ui/dialog.tsx` — full Radix UI Dialog is available with `Dialog`, `DialogTrigger`, `DialogContent`, `DialogHeader`, `DialogTitle`, `DialogFooter`. No additional library needed.

There is **no Combobox component** in `web/src/components/ui/`. The decision D-10 says "multi-select/combobox". The options are:
- A controlled `<select>` with `<option>` elements filtered by role (simplest, consistent with rest of app)
- Inline search input + dropdown (more complex, no existing component)

**Recommendation:** Use a simple `<select>` element since no Combobox primitive exists. The user list per tenant is small (< 50 users typically). This is consistent with how `select` elements are already styled in `clientes/[id]/editar/page.tsx` (`selectClassName`).

### 6. Frontend Upload Pattern

`FileDropZone` component exists at `web/src/components/shared/file-drop-zone.tsx`. Accepts `onFileChange: (file: File) => void`, `accept`, `disabled`. Already used in documentos upload page.

`useUploadDocumentoComProgresso` in `use-documentos.ts` uses XHR with progress events. A simpler hook (no progress bar needed for procuração — it's a small PDF) can use `apiFetch` with FormData.

---

## File-by-File Analysis

### `backend/src/main/java/com/lexcv/models/Cliente.java`

**Current state (post Phase 57):** Fields present:
- `id`, `tenantId`, `tipo`, `nome`, `nif`, `email`, `telefone`, `morada`, `localidade`, `ativo`
- `documentoTipo` (DocumentoTipo enum), `documentoNumero`, `ramoAtividade`, `detalhesAdicionais`
- `numeroSequencial` (Integer), `numeroCliente` (String, length 20)
- `avencado` (Boolean)
- `dadosTipo` (@Convert + DadosTipoConverter)
- `createdAt`

**Fields to add for Phase 59:**
```java
// Procuração
@Column(name = "procuracao_key")
private String procuracaoKey;

// Intake — plain text field
@Column(name = "descricao_caso", columnDefinition = "TEXT")
private String descricaoCaso;

// Intake — JSON lists
@Column(name = "documentos_entregues", columnDefinition = "TEXT")
@Convert(converter = DocumentosEntreguesConverter.class)
private List<DocumentoEntregue> documentosEntregues;

@Column(name = "documentos_a_tratar", columnDefinition = "TEXT")
@Convert(converter = DocumentosATratarConverter.class)
private List<DocumentoATratar> documentosATratar;

@Column(name = "deslocacoes", columnDefinition = "TEXT")
@Convert(converter = DeslocacoesConverter.class)
private List<Deslocacao> deslocacoes;

@Column(name = "honorarios_propostos", columnDefinition = "TEXT")
@Convert(converter = HonorariosPropostosConverter.class)
private HonorariosPropostos honorariosPropostos;
```

`ddl-auto=update` will ALTER TABLE to add these columns. No data loss to existing rows — all are nullable.

### `backend/src/main/java/com/lexcv/models/User.java`

**Role names confirmed in seed:** `ADMIN`, `ADVOGADO`, `ASSISTENTE`, `TECNICO`. String stored in `t_role.nome`.

**Existing fields:** `id`, `tenantId`, `nome`, `email`, `passwordHash`, `ativo`, `telefone`, `avatarUrl`, `createdAt`, `updatedAt`, `roles` (Set<Role>), `permissions`.

**Gap for INT-02:** Requirement says "nome, número de cédula e contacto". `nome` and `telefone` / `email` exist. **`numeroCedula` does not exist** on User. See risks section.

### `backend/src/main/java/com/lexcv/repositories/UserRepository.java`

**Current state:**
```java
Optional<User> findByEmail(String email);
List<User> findByTenantId(UUID tenantId);
```

**Missing:** no method to filter by role. Need to add:
```java
@Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName")
List<User> findByTenantIdAndRoleName(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);
```

Or for administrativos (ASSISTENTE or TECNICO):
```java
@Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome IN :roleNames")
List<User> findByTenantIdAndRoleNameIn(@Param("tenantId") UUID tenantId, @Param("roleNames") List<String> roleNames);
```

### `backend/src/main/java/com/lexcv/controllers/ResourceController.java`

**Current cliente endpoints:**
- `GET /clientes` — `@PreAuthorize("hasAuthority('clientes:view')")`
- `POST /clientes` — `@PreAuthorize("hasAuthority('clientes:edit')")`
- `GET /clientes/{id}` — returns `Cliente` entity directly (Jackson serializes all fields)
- `PUT /clientes/{id}` — currently sets: nome, tipo, email, telefone, morada, localidade, ativo, documentoTipo, documentoNumero, ramoAtividade, detalhesAdicionais. **Must be extended** to include descricaoCaso and JSON intake fields.
- `GET /clientes/{id}/conta-corrente`
- `GET/POST/PUT/DELETE /clientes/{id}/contactos`
- `GET/POST/PUT/DELETE /clientes/{id}/notas`
- `POST /clientes/merge`

**New endpoints to add:**
```
POST   /clientes/{id}/procuracao          — multipart upload, sets procuracao_key
GET    /clientes/{id}/procuracao/download — returns presigned URL
DELETE /clientes/{id}/procuracao          — removes file + clears procuracao_key
GET    /clientes/{id}/advogados           — list advogados
POST   /clientes/{id}/advogados/{userId}  — add advogado
DELETE /clientes/{id}/advogados/{userId}  — remove advogado
GET    /clientes/{id}/administrativos
POST   /clientes/{id}/administrativos/{userId}
DELETE /clientes/{id}/administrativos/{userId}
```

**Key detail:** `GET /clientes/{id}` already returns the full entity. Once `procuracaoKey`, `descricaoCaso`, and JSON columns are on `Cliente`, they will appear in the GET response automatically (Jackson serializes all fields by default). The planner should **not** create a new DTO for this — the existing direct entity serialization already works (verified: `GET /clientes/{id}` returns `ResponseEntity.ok(cliente)` directly).

### `web/src/types/clientes.ts`

**Current `Cliente` interface** has no `procuracao_key`, `dados_tipo`, `avencado`, `numero_cliente`, or any Phase 57/59 fields. This file must be updated to add all new fields returned by the API.

### `web/src/hooks/use-clientes.ts`

**Current hooks:** `useCliente`, `useClientes`, `useUpdateCliente`, `useCreateCliente`, `useDeleteCliente`, `useMergeClientes`.

**New hooks needed:**
- `useUploadProcuracao(clienteId)` — mutation, FormData POST to `/clientes/{id}/procuracao`
- `useDownloadProcuracao(clienteId)` — mutation, GET presigned URL
- `useDeleteProcuracao(clienteId)` — mutation, DELETE
- `useClienteAdvogados(clienteId)` — query
- `useAddAdvogado(clienteId)` — mutation
- `useRemoveAdvogado(clienteId)` — mutation
- Same three for administrativos
- All mutations must `invalidateQueries(["clientes", "detail", clienteId])`

### `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx`

**Current structure:** Single Card with react-hook-form, fields: nome, nif, tipo, email, telefone, localidade, morada, documento_tipo, documento_numero, ramo_atividade, detalhes_adicionais. Uses `useUpdateCliente` which calls `PUT /clientes/{id}`.

**What must change:** The edit page needs new sections below the existing form. The JSON intake fields (documentos entregues, documentos a tratar, deslocações, honorários propostos, descricao_caso) should be added to the form and submitted via `PUT /clientes/{id}`.

**Decision on UX structure:** Keep the single edit page approach — do not convert to tabs. Add sections with `<hr />` separators matching the existing pattern. The procuração upload and advogados/administrativos management are separate from the main PUT and must use their own hooks + inline UI within the edit page (or on the detail page). Given the complexity, putting procuração + advogados/administrativos on the **detail page** (`[id]/page.tsx`) is more appropriate — it avoids mixing file-upload form state with the text form. The JSON intake lists go in the edit page.

### `web/src/schemas/clientes.ts`

Must add fields for descricao_caso and honorarios_propostos (object with total, totalPorExtenso, previsao). The JSON list fields (documentos entregues etc.) are managed via modal+mutation, not react-hook-form fields, so no schema changes needed for those.

### `web/src/app/(dashboard)/clientes/[id]/page.tsx`

**Current structure:** Shows cliente data, conta-corrente, contactos, notas. All read-only display with inline add/edit/remove for contactos and notas using the card pattern.

**What to add here:**
- Procuração section (upload button if missing, view+replace if present)
- Advogados card (list + add/remove via modal)
- Administrativos card (list + add/remove via modal)
- Intake read-only display (descricao_caso, documentos entregues/a tratar, deslocações, honorários propostos) — with edit redirecting to the edit page

---

## Implementation Recommendations

### D-05 Resolution: Dedicated Endpoint for Procuração

**Use `POST /clientes/{id}/procuracao`** (dedicated endpoint), not the generic `/documentos/upload`.

Reasons:
1. Procuração is a 1:1 field on the Cliente (`procuracao_key`). The generic upload creates a `t_documento` row which is a different concern.
2. The dedicated endpoint can handle the replace-old-key logic transparently (delete old MinIO object, store new key on Cliente row) in a single transaction.
3. The download endpoint `GET /clientes/{id}/procuracao/download` is more semantically clear than `GET /documentos/{id}/download` (which requires knowing the documento UUID).
4. Permission scope: `clientes:edit` is the natural scope for modifying client data, while `documentos:edit` is for document management — keeping them separate avoids scope pollution.

### D-Claude: Server-side role validation — YES

When `POST /clientes/{id}/advogados/{userId}` is called, the backend should verify:
1. The user exists and belongs to the same tenant
2. The user has the role `ADVOGADO` (or `ASSISTENTE`/`TECNICO` for administrativos)

This is a single-line check: `user.getRoles().stream().anyMatch(r -> "ADVOGADO".equals(r.getNome()))`. Do not trust the client.

### New JPA Entities Required

**ClienteAdvogado** (`t_cliente_advogado`):
```java
@Entity @Table(name = "t_cliente_advogado")
public class ClienteAdvogado {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "cliente_id", nullable = false) private UUID clienteId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

Add a `@UniqueConstraint(columnNames = {"cliente_id", "user_id"})` to prevent duplicates.

Same pattern for `ClienteAdministrativo`.

### New POJO + Converter Pairs Required

Following `DadosTipo` / `DadosTipoConverter` pattern:

| POJO class | Fields | Converter class |
|-----------|--------|----------------|
| `DocumentoEntregue` | `descricao` (String), `data` (String) | `DocumentosEntreguesConverter` |
| `DocumentoATratar` | `descricao` (String) | `DocumentosATratarConverter` |
| `Deslocacao` | `descricao`, `local`, `data` (all String) | `DeslocacoesConverter` |
| `HonorariosPropostos` | `total` (BigDecimal or String), `totalPorExtenso` (String), `previsao` (String) | `HonorariosPropostosConverter` |

For list converters, the `AttributeConverter` generic type is `List<DocumentoEntregue>` instead of a single object. Use `ObjectMapper.readValue(dbData, new TypeReference<List<DocumentoEntregue>>(){})`.

### PUT /clientes/{id} Extension

The existing `updateCliente` in ResourceController must be extended:
```java
if (payload.getDescricaoCaso() != null) cliente.setDescricaoCaso(payload.getDescricaoCaso());
if (payload.getDocumentosEntregues() != null) cliente.setDocumentosEntregues(payload.getDocumentosEntregues());
if (payload.getDocumentosATratar() != null) cliente.setDocumentosATratar(payload.getDocumentosATratar());
if (payload.getDeslocacoes() != null) cliente.setDeslocacoes(payload.getDeslocacoes());
if (payload.getHonorariosPropostos() != null) cliente.setHonorariosPropostos(payload.getHonorariosPropostos());
```

Note: do NOT include `procuracaoKey` in PUT — it has its own endpoint.

### Procuração Upload Endpoint Skeleton

```java
@PreAuthorize("hasAuthority('clientes:edit')")
@PostMapping(value = "/clientes/{id}/procuracao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> uploadProcuracao(@PathVariable UUID id,
                                          @RequestParam("file") MultipartFile file) {
    Cliente cliente = clienteRepository.findById(id).orElse(null);
    if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(...);
    }
    String oldKey = cliente.getProcuracaoKey();
    UUID syntheticId = UUID.randomUUID();
    try {
        String newKey = storageService.upload(getTenantId(), syntheticId,
                file.getOriginalFilename(), file.getInputStream(),
                file.getContentType(), file.getSize());
        if (oldKey != null) storageService.delete(oldKey); // safe: upload succeeded first
        cliente.setProcuracaoKey(newKey);
        clienteRepository.save(cliente);
        return ResponseEntity.ok(Map.of("procuracaoKey", newKey));
    } catch (StorageUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)...;
    }
}
```

---

## Risks and Gotchas

### Risk 1: INT-02 — "número de cédula" not in User entity

**Severity:** HIGH. The requirement says "nome, número de cédula e contacto". `User` has `nome` and `email`/`telefone` but no `numeroCedula` field. Options:
- Add `numeroCedula` String field to `User` entity (requires extending User edit in admin panel, but that scope is outside Phase 59)
- Add `numeroCedula` to the `ClienteAdvogado` junction entity (allows per-assignment annotation)
- Treat the requirement as satisfied by displaying `nome` + `email`/`telefone` (simplest, may not match the written requirement)

**Recommendation for planner:** Include a task to clarify this with the user, with the default fallback being to add `numeroCedula` to the junction entity (`ClienteAdvogado`). This keeps User unchanged and allows recording the cédula number in the context of this assignment.

### Risk 2: No TypeReference import for list converters

`DadosTipoConverter` deserializes a single object: `MAPPER.readValue(dbData, DadosTipo.class)`. List converters need `new TypeReference<List<DocumentoEntregue>>(){}`. This is standard Jackson but easy to forget. The converter must also handle `null` and `"null"` (literal string) gracefully.

### Risk 3: updateCliente accepts raw `Cliente` entity (not a DTO)

The current `PUT /clientes/{id}` uses `@RequestBody Cliente payload`. Adding new fields to `Cliente` will allow them to be passed through. However, `procuracaoKey` should NOT be settable via PUT (it has its own endpoint). The existing pattern does not set it: Jackson will populate it if sent, but the update method simply does not call `setProcuracaoKey()`, which is the correct guard.

### Risk 4: Frontend type drift

`web/src/types/clientes.ts` `Cliente` interface does not include Phase 57 fields (`avencado`, `numeroCliente`, `dadosTipo`) either. This is a systemic gap. All new fields added in Phase 59 must also be added to the TypeScript type, and Phase 57 fields should be back-filled while we are in the file. Otherwise the frontend will silently ignore them.

### Risk 5: ddl-auto=update in dev adds columns but does not handle renaming

New columns are always additive — Hibernate adds them. But if during development a column name changes, the old column remains (orphaned). Use exact column names from the decision list and don't rename after first run.

### Risk 6: Dialog accessibility on mobile

The existing Dialog component is `fixed left-[50%] top-[50%]`. On small screens with a virtual keyboard open, the dialog can be obscured. The add-via-modal forms for intake items should be kept short (2-3 fields max per modal) to minimize this issue. For honorariosPropostos (the single object with 3 fields), an inline form on the edit page is better than a modal.

### Risk 7: Procuração presigned URL expiry

`StorageService.presignedDownloadUrl()` uses `props.getPresignedUrlExpiry()` (from `MinioProperties`). The download endpoint for documentos returns `"expiresIn": 3600`. The procuração download endpoint should match the same pattern and also return `expiresIn` so the frontend knows when to refresh.

---

## Implementation Sequence

**Wave 1 — Backend foundation (no new endpoints yet)**
1. Add POJO classes: `DocumentoEntregue`, `DocumentoATratar`, `Deslocacao`, `HonorariosPropostos`
2. Add Converter classes for each (including list converters for the first three)
3. Extend `Cliente.java` with all new fields (`procuracaoKey`, `descricaoCaso`, JSON columns)
4. Add `findByTenantIdAndRoleName` / `findByTenantIdAndRoleNameIn` to `UserRepository`
5. Create `ClienteAdvogado` and `ClienteAdministrativo` entities + repositories

**Wave 2 — Backend endpoints**
6. Add `POST /clientes/{id}/procuracao` and `GET /clientes/{id}/procuracao/download` and `DELETE /clientes/{id}/procuracao`
7. Extend `PUT /clientes/{id}` to accept descricaoCaso + JSON intake fields
8. Add advogados endpoints (GET/POST/DELETE)
9. Add administrativos endpoints (GET/POST/DELETE)

**Wave 3 — Frontend types and hooks**
10. Update `web/src/types/clientes.ts` with all new fields
11. Add new hooks in `use-clientes.ts` (procuracao upload/download/delete, advogados, administrativos)

**Wave 4 — Frontend UI**
12. Detail page: add Procuração card (upload/view/replace)
13. Detail page: add Advogados card (list + add-via-modal + remove)
14. Detail page: add Administrativos card (same pattern)
15. Edit page: add descricao_caso field
16. Edit page: add JSON list sections (documentos entregues, a tratar, deslocações) with add-modal + remove
17. Edit page: add honorarios propostos inline form
18. Update `clienteFormSchema` (zod) for new fields
19. Update `ClienteUpdateRequest` DTO type

---

## Validation Architecture

### PROC-01/02 — Procuração upload
- Backend: POST /clientes/{id}/procuracao with a test PDF; verify `procuracao_key` is set on Cliente row
- Backend: GET /clientes/{id}/procuracao/download returns `{ url, expiresIn }` with a valid presigned URL
- Backend: Replace (POST again) deletes old object and sets new key
- Backend: Delete endpoint clears key and deletes MinIO object
- Frontend: Warning badge visible when `procuracaoKey` is null on detail page; link present when non-null

### INT-02/03 — Advogados/Administrativos
- Backend: POST /clientes/{id}/advogados/{userId} with a non-ADVOGADO user returns 400
- Backend: POST with a valid ADVOGADO user creates junction row
- Backend: GET /clientes/{id}/advogados returns list of user objects
- Backend: DELETE removes junction row
- Backend: Duplicate add returns conflict or idempotent OK (unique constraint handles it)
- Frontend: Modal opens on click, select shows only ADVOGADO users

### INT-04 to INT-07 — JSON intake fields
- Backend: PUT /clientes/{id} with `documentosEntregues: [{ descricao: "Passaporte", data: "2026-01-01" }]` persists to `documentos_entregues` column
- Backend: GET /clientes/{id} returns the parsed JSON (not raw string)
- Backend: Null/empty list is stored as null or `[]` — must be consistent
- Frontend: Add modal creates item in local state, saved on form submit
- Frontend: Remove button clears item from list, saved on form submit

### Permissions
- All new endpoints under `clientes:edit` (write) or `clientes:view` (read advogados list)
- No new permission scopes required

---

## Sources

All findings are from direct codebase inspection — no external sources consulted.

- `backend/src/main/java/com/lexcv/models/Cliente.java` — entity state after Phase 57
- `backend/src/main/java/com/lexcv/models/User.java` — User entity with roles
- `backend/src/main/java/com/lexcv/models/DadosTipo.java` + `DadosTipoConverter.java` — JSON column pattern
- `backend/src/main/java/com/lexcv/models/Role.java` — role entity
- `backend/src/main/java/com/lexcv/services/StorageService.java` — MinIO API surface
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 248-530, 1719-1870 — existing cliente + documento endpoints
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` — current queries
- `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` — current queries
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` — confirmed role names: ADMIN, ADVOGADO, ASSISTENTE, TECNICO
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — detail page structure
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` — edit form structure
- `web/src/app/(dashboard)/documentos/novo/page.tsx` — FileDropZone upload pattern
- `web/src/components/ui/dialog.tsx` — Dialog component API
- `web/src/components/shared/file-drop-zone.tsx` — FileDropZone component
- `web/src/hooks/use-clientes.ts` — existing hooks
- `web/src/hooks/use-documentos.ts` — XHR upload with progress pattern
- `web/src/types/clientes.ts` — current TypeScript types
- `web/src/schemas/clientes.ts` — existing Zod schema

**Confidence breakdown:**
- Entity/model patterns: HIGH — read directly from source files
- Endpoint patterns: HIGH — read directly from ResourceController
- MinIO API: HIGH — read from StorageService
- Frontend component availability: HIGH — verified by file listing
- Combobox gap: HIGH — no combobox file found in ui/ directory
- INT-02 numeroCedula gap: HIGH — User entity read directly, field not present
