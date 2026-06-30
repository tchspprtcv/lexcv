---
phase: LEXCV-59-procuracao-intake
reviewed: 2026-06-30T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/models/Cliente.java
  - backend/src/main/java/com/lexcv/models/ClienteAdministrativo.java
  - backend/src/main/java/com/lexcv/models/ClienteAdvogado.java
  - backend/src/main/java/com/lexcv/models/Deslocacao.java
  - backend/src/main/java/com/lexcv/models/DeslocacoesConverter.java
  - backend/src/main/java/com/lexcv/models/DocumentoATratar.java
  - backend/src/main/java/com/lexcv/models/DocumentoEntregue.java
  - backend/src/main/java/com/lexcv/models/DocumentosATratarConverter.java
  - backend/src/main/java/com/lexcv/models/DocumentosEntreguesConverter.java
  - backend/src/main/java/com/lexcv/models/HonorariosPropostos.java
  - backend/src/main/java/com/lexcv/models/HonorariosPropostosConverter.java
  - backend/src/main/java/com/lexcv/models/User.java
  - backend/src/main/java/com/lexcv/repositories/ClienteAdministrativoRepository.java
  - backend/src/main/java/com/lexcv/repositories/ClienteAdvogadoRepository.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
  - "web/src/app/(dashboard)/clientes/[id]/editar/page.tsx"
  - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
  - web/src/hooks/use-clientes.ts
  - web/src/schemas/clientes.ts
  - web/src/types/clientes.ts
findings:
  critical: 3
  warning: 6
  info: 4
  total: 13
status: issues_found
---

# Phase LEXCV-59: Code Review Report

**Reviewed:** 2026-06-30T00:00:00Z
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

Reviewed the procuração intake/upload/delete flow, the cliente advogado/administrativo junction endpoints, the supporting JSON-converter models, and the frontend cliente detail/edit pages. The tenant-scoping pattern (`findById` + `tenantId.equals` check) is applied consistently across all new endpoints, and the procuração upload-then-delete-old ordering correctly avoids orphaning the old object on upload failure. Server-side role validation for `ClienteAdvogado`/`ClienteAdministrativo` is real (checks the persisted `User.roles`, not a client-supplied claim).

However, several correctness and security-adjacent issues remain: the `clienteAdvogadoRepository.findByClienteIdAndTenantId` / `userRepository.findById` lookup pattern in the advogados/administrativos list endpoints leaks users belonging to other tenants if a `ClienteAdvogado` row's `userId` were ever to point cross-tenant (defense-in-depth gap); the `updateCliente` PUT handler unconditionally overwrites `nif` based on `payload.getDocumentoTipo()` even when the caller does not intend to touch documento fields, which can silently corrupt `nif` on partial updates; the `HonorariosPropostosConverter`/list converters swallow all `Exception` on both read and write paths, silently dropping data with no logging; and `ClienteAdministrativo`/`ClienteAdvogado` lack a tenant_id presence check at the unique-constraint level (constraint is `(cliente_id, user_id)` without tenant_id), which is a latent multi-tenant ambiguity even though it isn't currently exploitable given upstream tenant filtering.

## Critical Issues

### CR-01: `updateCliente` silently overwrites `nif` whenever `documentoTipo` is not NIF, even on partial/legacy-only updates

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:285-289`
**Issue:** In `updateCliente` (PUT `/clientes/{id}`), the handler always executes:
```java
if (payload.getDocumentoTipo() == DocumentoTipo.NIF) {
    cliente.setNif(payload.getDocumentoNumero());
} else {
    cliente.setNif(payload.getNif());
}
```
Because the controller takes a full `Cliente` payload (not a partial DTO) and the frontend always sends the full form, this path is the same flow that already supports a "NIF (Legado)" field (`nif`) as documented by the UI label in `editar/page.tsx:280`. The bug: if a client sends `documentoTipo` as anything other than `NIF` (including `null`/unset), `cliente.setNif(payload.getNif())` runs — and `payload.getNif()` will be `null` for any caller that omits the `nif` field in the JSON body (e.g., a future integration, or a manual `PUT` via API client that does not echo back the legacy `nif`). This silently nulls out the legacy NIF field even though the cliente had a previously-stored NIF, because the rest of `updateCliente` does *not* guard `setNif` with a null-check the way it guards `documentosEntregues`, `documentosATratar`, etc. (lines 279-283 use `if (payload.getX() != null)` guards, but the NIF logic at 285-289 has no such guard).
**Fix:**
```java
if (payload.getDocumentoTipo() == DocumentoTipo.NIF) {
    cliente.setNif(payload.getDocumentoNumero());
} else if (payload.getNif() != null) {
    cliente.setNif(payload.getNif());
}
```

### CR-02: `ClienteAdministrativo`/`ClienteAdvogado` unique constraint omits `tenant_id`, allowing cross-tenant uniqueness collisions

**File:** `backend/src/main/java/com/lexcv/models/ClienteAdministrativo.java:10-11`, `backend/src/main/java/com/lexcv/models/ClienteAdvogado.java:10-11`
**Issue:** Both entities declare:
```java
@Table(name = "t_cliente_administrativo",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cliente_id", "user_id"}))
```
`cliente_id` is a UUID generated with `GenerationType.UUID`, so practical collision across tenants is improbable, but the unique constraint is conceptually wrong for a multi-tenant schema — it does not include `tenant_id` even though every other unique constraint in this codebase pattern is per-tenant (see `Cliente`'s `(tenant_id, documento_numero)` constraint, explicitly called out as the project convention in CLAUDE.md: "Unique constraints are per-tenant"). If `clienteId`+`userId` could ever theoretically collide (e.g., due to a future UUID-generation change, data import, or merge bug), this constraint would incorrectly block legitimate per-tenant rows or allow corrupted joins. This is a deviation from the documented multi-tenancy convention and should be fixed before this becomes load-bearing.
**Fix:**
```java
@Table(name = "t_cliente_administrativo",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "cliente_id", "user_id"}))
```
(same for `ClienteAdvogado`)

### CR-03: All JSON `AttributeConverter`s swallow conversion exceptions silently, causing silent data loss on read and write

**File:** `backend/src/main/java/com/lexcv/models/DeslocacoesConverter.java:20-25,32-36`, `DocumentosATratarConverter.java:20-25,32-36`, `DocumentosEntreguesConverter.java:20-25,32-36`, `HonorariosPropostosConverter.java:17-22,29-34`
**Issue:** Every converter follows this pattern:
```java
try {
    return MAPPER.writeValueAsString(attribute);
} catch (Exception e) {
    return null;
}
```
and
```java
try {
    return MAPPER.readValue(dbData, ...);
} catch (Exception e) {
    return null;
}
```
On the write path, if serialization fails (e.g., a non-serializable value sneaks into a list, or a future field addition breaks Jackson), the column is silently written as `null` instead of failing the save — the caller (`updateCliente`/`createCliente`) gets a 200 OK with the data silently dropped, with zero log trace to diagnose what happened. On the read path, if the stored JSON is corrupted or the deserialization target schema changes (e.g., new required field added to `Deslocacao`), the converter returns `null` for the whole list/object rather than surfacing the corruption — a cliente's `documentosEntregues`/`honorariosPropostos` etc. silently disappears from API responses without any error. This is especially risky for the procuração-intake feature since `documentosEntregues`/`documentosATratar`/`honorariosPropostos` are core intake data entered via the edit form (`editar/page.tsx`).
**Fix:** At minimum, log the exception so failures are diagnosable:
```java
} catch (Exception e) {
    log.error("Failed to serialize {} for persistence", attribute, e);
    return null;
}
```
Better: don't swallow on the write path — wrap in a runtime exception so the transaction rolls back instead of silently persisting null:
```java
} catch (Exception e) {
    throw new IllegalStateException("Failed to serialize " + attribute.getClass().getSimpleName(), e);
}
```

## Warnings

### WR-01: `addClienteAdvogado`/`addClienteAdministrativo` allow duplicate-role users without race protection (TOCTOU)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:418-427`, `480-489`
**Issue:** The conflict check
```java
if (clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(id, userId, getTenantId()).isPresent()) {
    return ResponseEntity.status(HttpStatus.CONFLICT)...
}
ClienteAdvogado link = ClienteAdvogado.builder()...build();
clienteAdvogadoRepository.save(link);
```
is a classic check-then-act race: two concurrent requests adding the same advogado to the same cliente can both pass the `findBy...isPresent()` check before either `save()` commits, resulting in two rows (the unique constraint on `(cliente_id, user_id)` would catch this at the DB level and throw a `DataIntegrityViolationException`, but that exception is unhandled here and will propagate as an unhandled 500 rather than a clean 409). This is a narrow window but worth tightening, especially since there's no `@Transactional` + catch around the save.
**Fix:** Wrap `save()` in a try/catch for `DataIntegrityViolationException` and translate it to 409, or rely on `saveAndFlush` plus the existing constraint with explicit exception handling.

### WR-02: Procuração download endpoint does not validate `getContentType()`/extension before generating presigned URL, but more importantly the upload endpoint accepts any `MultipartFile` content-type without server-side validation

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:298-337`
**Issue:** `uploadProcuracao` validates only that the file is non-empty and has a non-blank original filename — it never validates `file.getContentType()` against an allowlist (the frontend's `FileDropZone accept="application/pdf,image/*,.doc,.docx"` is a client-side hint only, trivially bypassed). Combined with `originalName` being passed through to `storageService.upload(...)` (not reviewed here, but the filename is attacker-controlled), this endpoint will accept arbitrary file types (executables, scripts) into procuração storage. Given documents are later served back via presigned URL and potentially opened in-browser, this is a stored-content risk (not classic XSS since served via S3/MinIO presigned URL, but still worth an allowlist for a legal-document upload flow).
**Fix:** Add a server-side content-type/extension allowlist check before calling `storageService.upload`:
```java
private static final Set<String> ALLOWED_PROCURACAO_TYPES = Set.of(
    "application/pdf", "image/jpeg", "image/png",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
...
if (file.getContentType() == null || !ALLOWED_PROCURACAO_TYPES.contains(file.getContentType())) {
    return ResponseEntity.badRequest().body(Map.of("message", "Tipo de ficheiro não permitido"));
}
```

### WR-03: `removeClienteAdvogado`/`removeClienteAdministrativo` return 204 even when no row was deleted

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:434-442`, `496-504`
**Issue:**
```java
clienteAdvogadoRepository.deleteByClienteIdAndUserIdAndTenantId(id, userId, getTenantId());
return ResponseEntity.noContent().build();
```
`deleteByClienteIdAndUserIdAndTenantId` is a Spring Data derived delete query that returns `void` (per the repository signature) and does not throw if zero rows matched. The endpoint always returns 204 regardless of whether the link existed, which masks client bugs (e.g., calling remove on a userId that was never linked returns the same success response as a real deletion). Not a security issue, but inconsistent with the rest of the codebase's pattern of returning 404 for "not found" sub-resources (e.g., `deleteClienteContacto`, `deleteClienteNota`).
**Fix:** Change the repository method to return `long` (Spring Data supports derived delete-count) and check it:
```java
long deleteByClienteIdAndUserIdAndTenantId(UUID clienteId, UUID userId, UUID tenantId);
```
```java
long deleted = clienteAdvogadoRepository.deleteByClienteIdAndUserIdAndTenantId(id, userId, getTenantId());
if (deleted == 0) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Associação não encontrada"));
}
return ResponseEntity.noContent().build();
```

### WR-04: `listClienteAdvogados`/`listClienteAdministrativos` perform N+1 `userRepository.findById` lookups instead of a batched `findAllById`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:393-398`, `455-460`
**Issue:** Both endpoints do:
```java
List<ClienteAdvogado> links = clienteAdvogadoRepository.findByClienteIdAndTenantId(id, getTenantId());
List<User> users = links.stream()
        .map(link -> userRepository.findById(link.getUserId()).orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
```
This is an N+1 query pattern — for each link, a separate `findById` round-trip. The codebase elsewhere (e.g., `listProcessos`, lines 868-874) explicitly batches with `userRepository.findAllById(...)` "to avoid N+1 queries" per its own comment. This is an inconsistency with the established pattern in the same file. (Flagged as a quality/consistency issue rather than a pure performance issue, since performance is out of v1 scope — but the established codebase convention right above this code makes the omission notable.)
**Fix:**
```java
Set<UUID> userIds = links.stream().map(ClienteAdvogado::getUserId).collect(Collectors.toSet());
List<User> users = userRepository.findAllById(userIds).stream()
        .filter(u -> getTenantId().equals(u.getTenantId()))
        .toList();
```
Note: also add the tenant filter on `User` here — currently the lookup trusts that every `ClienteAdvogado.userId` already points to a same-tenant user (enforced at link-creation time in `addClienteAdvogado`), but a defense-in-depth check costs nothing and matches the project's stated multi-tenancy posture ("always filter by tenant id").

### WR-05: `Deslocacao`/`DocumentoEntregue`/`DocumentoATratar`/`HonorariosPropostos` `data`/date fields are untyped `String`, allowing unvalidated free-text dates

**File:** `backend/src/main/java/com/lexcv/models/Deslocacao.java:17`, `DocumentoEntregue.java:16`
**Issue:** `data` is typed as plain `String` rather than `LocalDate`/`LocalDateTime`. The frontend sends raw `<input type="date">` values (`editar/page.tsx:571`, `696`) which are reasonably well-formed, but nothing on the backend validates the format before persisting via the JSON converter. Any caller bypassing the frontend (direct API call) can store arbitrary strings in a "date" field, which will then fail silently or render garbage in the UI (`{doc.data ? ` — ${doc.data}` : ""}` in `editar/page.tsx:591`, with no validation that it's a real date).
**Fix:** Either type these fields as `LocalDate` (and rely on Jackson's `JavaTimeModule`) or add explicit validation in the converter/DTO before accepting them.

### WR-06: Frontend `Cliente` type carries both snake_case and camelCase duplicate fields for the same data (merge artifact), increasing risk of stale-field bugs

**File:** `web/src/types/clientes.ts:43-72`
**Issue:** The `Cliente` interface declares both:
```ts
documento_tipo?: string;
documento_numero?: string;
ramo_atividade?: string;
detalhes_adicionais?: string;
documentoTipo?: string;
documentoNumero?: string;
ramoAtividade?: string;
detalhesAdicionais?: string;
```
i.e., every documento-related field exists twice under both naming conventions. This is consumed defensively throughout (`cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? ""` appears in both `editar/page.tsx:146-149` and `page.tsx:199,202,205,209-210`), which works but is a clear sign of an unresolved merge conflict between worktrees that used different casing conventions for the same backend response. The actual Spring `Cliente` entity (`Cliente.java`) only ever serializes camelCase via Jackson's default `documentoTipo` etc. (no `@JsonProperty` snake_case override visible) — so the `documento_tipo` (snake_case) variants in the TS type are effectively dead/unreachable for this entity and only exist to support some other endpoint/shape inconsistency, or are simply vestigial. This duplication should be resolved to a single canonical casing to prevent future bugs where a field is read from the wrong (always-undefined) key.
**Fix:** Confirm which casing the backend actually serializes (camelCase, per Jackson defaults with no explicit `@JsonProperty`), remove the snake_case duplicates from the `Cliente`/`ClienteCreateRequest`/`ClienteUpdateRequest` interfaces, and update all `??` fallback chains to use only the real key.

## Info

### IN-01: `ClienteUpdateRequest` also duplicates camelCase/snake_case fields for the new intake data (`documentosEntregues`/`documentos_entregues` etc.)

**File:** `web/src/types/clientes.ts:114-122`
**Issue:** Same pattern as WR-06 extends to the new Phase 59 intake fields: `descricao_caso`/`descricaoCaso`, `documentos_entregues`/`documentosEntregues`, `documentos_a_tratar`/`documentosATratar`, `honorarios_propostos`/`honorariosPropostos`. The `editar/page.tsx` submit handler (lines 203-217) sends both casings simultaneously in the same payload object (spread `...values` includes snake_case keys from the form schema, then explicit camelCase overrides are added) — meaning the backend's `Cliente` entity (camelCase Jackson deserialization) will only read the camelCase keys, and the snake_case duplicates sent in the JSON body are simply ignored bytes. Harmless today but wasteful and confusing for future maintainers.
**Fix:** Drop the snake_case duplicates from the `ClienteUpdateRequest` payload construction in `onSubmit`.

### IN-02: `resolveAutorNome` is unused in the procuração/advogados flow but defined near it — confirm it's still referenced elsewhere

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:121-125`
**Issue:** Not a bug — `resolveAutorNome` is used by `getTimeline`/`getAuditLog` later in the file. No action needed; noting only because its placement near the procuração section (added in this phase) could mislead future reviewers into thinking it's procuração-specific. Cosmetic only.
**Fix:** None required; consider moving private helpers to the bottom of the class or a dedicated section comment for clarity.

### IN-03: `DadosTipo` model has fields (`biPassaporte`, `nif`, `cargoRepresentante`) that don't match the frontend's `dados_tipo` shape (`representante_legal`, `cargo`, no `biPassaporte`/`nif`)

**File:** `backend/src/main/java/com/lexcv/models/DadosTipo.java:14-26` vs `web/src/types/clientes.ts:1-12`
**Issue:** Backend `DadosTipo` has `biPassaporte`, `nif`, `nomeComercial`, `representanteLegal`, `cargoRepresentante`. Frontend `DadosTipoEmpresa` has `nome_comercial`, `sede`, `representante_legal`, `cargo` (no `cargoRepresentante`, no `biPassaporte`, no `nif`). Given `DadosTipoConverter` was not included in the reviewed file set, this mismatch could not be fully traced, but on the surface the field-name mismatch (`cargo` vs `cargoRepresentante`, `representante_legal` vs `representanteLegal`) suggests data submitted by the frontend for "Cargo do Representante" may not map onto the backend's `cargoRepresentante` field correctly if the converter does naive snake_case-to-camelCase JSON mapping rather than explicit `@JsonProperty` aliases. This is flagged as Info since the converter itself was out of scope for this review and the actual serialization behavior could not be confirmed, but it is worth a follow-up check.
**Fix:** Read `DadosTipoConverter.java` (not in this file set) to confirm whether it uses `ObjectMapper` defaults (which would NOT match `cargo` → `cargoRepresentante` automatically) or explicit field mapping.

### IN-04: `ResourceController` is documented in CLAUDE.md as "deliberately large (~1000-line)" but has grown to 2446 lines

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Issue:** Project documentation (CLAUDE.md) describes `ResourceController` as "a deliberately large (~1000-line) controller." The file reviewed here is 2446 lines — more than double the documented size. This isn't a defect introduced by this phase specifically, but the procuração/advogados/administrativos endpoints added in Phase 59 (lines 295-504, ~210 lines) further compound an already-acknowledged maintainability concern. Worth flagging for a future extraction into a dedicated `ClienteProcuracaoController`/`ClienteResponsaveisController` per the single-responsibility convention used elsewhere (e.g., `AdminController`, `SetupController` are already split out).
**Fix:** Consider splitting cliente-related sub-resource endpoints (procuração, advogados, administrativos, contactos, notas) into a dedicated `ClienteSubresourceController` in a future refactor.

---

_Reviewed: 2026-06-30T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
