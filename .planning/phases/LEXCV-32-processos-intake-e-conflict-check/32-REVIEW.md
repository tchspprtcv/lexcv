---
phase: LEXCV-32-processos-intake-e-conflict-check
reviewed: 2026-06-13T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/ConflictCheckDecisao.java
  - backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java
  - backend/src/main/java/com/lexcv/dtos/ConflictCheckRequest.java
  - backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java
  - backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - web/src/types/processos.ts
  - web/src/schemas/processos.ts
  - web/src/lib/conflict-check.ts
  - web/src/hooks/use-processos.ts
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
findings:
  critical: 8
  warning: 8
  info: 4
  total: 20
status: issues_found
---

# Phase LEXCV-32: Code Review Report

**Reviewed:** 2026-06-13T00:00:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

This phase introduces the intake wizard, conflict-check engine, decisao recording, and the TRIAGEM→ATIVO formalizar transition. The invariants under review were: tenant isolation at every query, RBAC alignment across layers, server-side TRIAGEM block enforcement (campos minimos + impeditivo decisao), and decisor identity sourced from the security context only.

The core security intent is largely correct — `decisorId` is server-resolved, formalizar blocks on missing decisao and on impeditivo nivel, and most queries carry tenant scoping. However, several blockers exist: the conflict-check search queries cross-tenant `Parte` data, the intake endpoint re-uses the wrong `@PreAuthorize` scope, the `formalizar` does not verify the processo is actually in TRIAGEM state (enabling double-formalization attacks), the `Parte` model has no tenant_id column making tenant isolation structurally impossible, and the frontend wizard blocks formalizar only client-side (the backend must remain the authority, and it does, but the mismatch creates confusion that can be exploited with a direct API call bypassing the wizard entirely). Additional warnings relate to upsert-on-decisao allowing silent overwrites, missing `@Transactional` on multi-step state mutations, and a type mismatch between the frontend `fase_id` (UUID string) and the backend `ProcessoFase.faseId` (Integer) that will cause runtime errors.

---

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Cross-tenant leak in conflict-check — Parte rows have no tenant_id

**File:** `backend/src/main/java/com/lexcv/models/Parte.java:1` (entity) / `backend/src/main/java/com/lexcv/controllers/ResourceController.java:696-711` (usage)

**Issue:** `Parte` has no `tenantId` column. The conflict-check loop at line 696 iterates all `processoRepository.findByTenantId(tenantId)` but then calls `parteRepository.findByProcessoId(p.getId())` with no further tenant gate. Because `processoId` is globally unique (UUID), this is safe _as long as_ the processo list is correctly tenant-scoped — but the deeper structural problem is that `Parte` carries no `tenantId` of its own, so:

1. There is no unique constraint enforcing per-tenant isolation on partes.
2. Any future direct query against `ParteRepository` (e.g., search, export) that forgets to join through `Processo` will produce a cross-tenant leak.
3. The frontend type `ProcessoParte` (types/processos.ts line 46) declares a `tenant_id` field, but the backend model does not supply it — the serialized JSON will always carry `null` for `tenant_id`.

**Fix:** Add `tenantId` to `Parte`:
```java
@Column(name = "tenant_id", nullable = false)
private UUID tenantId;
```
Set it from the controller when creating partes:
```java
parte.setProcessoId(id);
parte.setTenantId(getTenantId()); // add this
return ResponseEntity.status(HttpStatus.CREATED).body(parteRepository.save(parte));
```
Add a repository finder `findByTenantIdAndProcessoId(UUID tenantId, UUID processoId)` and use it in all sub-resource reads and in the conflict-check parte loop.

---

### CR-02: `formalizar` does not check that the processo is currently in TRIAGEM — enables double-formalization and state bypass

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:776-824`

**Issue:** `formalizarProcesso` checks campos-minimos and decisao presence, then unconditionally sets `estado = "ATIVO"`. It never asserts `processo.getEstado().equals("TRIAGEM")`. Consequences:

- A processo already in `ATIVO`, `SUSPENSO`, or `ENCERRADO` can be re-formalized (no-op on state but triggers all side-effects again in future logic).
- An ATIVO processo can be re-driven through formalizar by any user with `processos:manage` — effectively resetting state if the field is later used for workflow gating.
- A processo in `ENCERRADO` can be incorrectly reopened as `ATIVO`.

**Fix:**
```java
if (!"TRIAGEM".equalsIgnoreCase(processo.getEstado())) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "message", "Não é possível formalizar: o processo não está em estado de TRIAGEM"
    ));
}
```
Insert this check before the campos-minimos block (line 785).

---

### CR-03: `@PreAuthorize` on intake endpoint uses wrong scope — should be `processos:create`, is `processos:edit` on generic `POST /processos`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:585-591` (generic create) and `640-647` (intake)

**Issue:** Two endpoints create processos:

- `POST /processos` (line 585) — gated `processos:edit`. Per the RBAC design, this allows ADVOGADO (has `processos:edit`) to create processos bypassing the intake wizard entirely. Any processo created here is set to whatever estado the caller provides (no forced TRIAGEM). An ADVOGADO with `processos:edit` can create a processo in `ATIVO` or `ENCERRADO` state directly, completely skipping the conflict-check gate.
- `POST /processos/intake` (line 640) — correctly gated `processos:create` and forces TRIAGEM.

The RBAC in `DatabaseSeeder` gives ADVOGADO `processos:edit` AND `processos:create`. ASSISTENTE does NOT have `processos:edit`. This means:
1. ADVOGADOs can use the direct `POST /processos` endpoint to bypass the intake/conflict-check flow.
2. The generic `POST /processos` endpoint should either be removed or have its `@PreAuthorize` raised to `processos:manage` (admin-only backdoor), and the intake path should be the canonical route for all new processos.

**Fix:** Either restrict `POST /processos` to `processos:manage` and add documentation marking it as admin-only, or remove it if intake is the sole creation path.

---

### CR-04: Conflict-check name-match search leaks `nomeToSearch` in case-sensitivity trap — name substring match against client used as plain `contains`, allowing trivial bypass

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:680-693`

**Issue:** The approximate-name check is `contains(c.getNome(), nomeLower)` where `contains` is defined as:
```java
if (value == null) return false;
return value.toLowerCase().contains(queryLower);
```
This checks whether the _existing client name_ contains the _search name as substring_, not the reverse. So if the new processo's client is named "Jo" and an existing client is named "João Andrade", this check will NOT match (because "joão andrade".contains("jo") is true). However, if the existing client is named "Jo" and the search name is "João Andrade", the match also won't fire because "jo".contains("joão andrade") is false. The match logic is asymmetric and will silently miss genuine conflicts where the existing client's name is longer than the new client's name. This is a correctness defect in the conflict-check engine, not just quality.

Additionally, at line 701 the parte name check is `contains(parte.getNome(), nomeToSearch.toLowerCase())` — this passes the **raw non-lowercased** `parte.getNome()` through the `contains()` helper, which calls `value.toLowerCase()` inside. So that branch is fine. But the inconsistency means the name-match logic differs between the cliente and parte branches, and one of them will miss conflicts in real-world data.

**Fix:** To detect that the new client and an existing entity are related, the correct check is:
```java
// Match if either name contains the other
boolean nameMatches = contains(c.getNome(), nomeLower) || contains(nomeToSearch, c.getNome().toLowerCase());
```

---

### CR-05: `upsert` on ConflictCheckDecisao silently overwrites previous decision without audit trail

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:763-773`

**Issue:** The decisao handler unconditionally upserts — if a decisao already exists it is overwritten in-place:
```java
ConflictCheckDecisao decisao = conflictCheckDecisaoRepository
    .findByTenantIdAndProcessoId(tenantId, id)
    .orElse(ConflictCheckDecisao.builder().tenantId(tenantId).processoId(id).build());
decisao.setNivel(nivel);
decisao.setDecisorId(decisorId);
decisao.setDataDecisao(LocalDate.now());
```
A decision that previously said "impeditivo" can be silently changed to "sem_conflito" by calling the same endpoint again. There is no history, no `updated_at` field, and no authorization check beyond `processos:manage`. The original `decisorId` and `dataDecisao` are lost. If the process is later audited, the decision trail is gone.

This is a **data integrity** issue: in a legal compliance context, decisions on conflict-of-interest must be immutable once recorded, or at minimum versioned.

**Fix:** Either (a) block re-submission when a decisao already exists and require an explicit `override` flag with additional justification, or (b) append a new row to an audit table rather than mutating the existing record. At minimum, add an `updatedAt` / `updatedBy` column pair to `ConflictCheckDecisao` and populate them on change.

---

### CR-06: No `@Transactional` on `formalizarProcesso` — partial failure leaves the processo in TRIAGEM with decisao state committed

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:776-824`

**Issue:** `formalizarProcesso` reads the decisao and then saves the processo state. These are two separate JPA repository calls with no surrounding transaction. If the second `processoRepository.save(processo)` fails (e.g., optimistic locking, network blip to DB), the user sees an error but the decisao was already read and nothing was changed — so the issue here is survivable. However, the inverse danger is greater: if future code adds more operations inside `formalizar` (e.g., creating an initial event, logging), those operations could partially succeed. Mark the method `@Transactional` now, before such additions inevitably occur.

The same applies to `registarDecisaoConflito` (line 730): the method does a lookup + conditional build + save — also not transactional.

**Fix:**
```java
import org.springframework.transaction.annotation.Transactional;

@Transactional
@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos/{id}/formalizar")
public ResponseEntity<?> formalizarProcesso(@PathVariable UUID id) { ... }

@Transactional
@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos/{id}/conflict-check/decisao")
public ResponseEntity<?> registarDecisaoConflito(...) { ... }
```

---

### CR-07: `ProcessoFase.faseId` is `Integer` on the backend but frontend sends a UUID string — runtime failure for all fase operations

**File:** `backend/src/main/java/com/lexcv/models/ProcessoFase.java:24` / `backend/src/main/java/com/lexcv/controllers/ResourceController.java:908` / `web/src/app/(dashboard)/processos/[id]/page.tsx:472`

**Issue:** `ProcessoFase.faseId` is typed `Integer` in the JPA model (and `ProcessoFaseRepository` key is `Integer` at line 908: `@PathVariable Integer faseId`). The frontend type `ProcessoFase` declares `fase_id: string` (types/processos.ts line 73) and the form at `[id]/page.tsx:472` collects a free-text UUID string via `Input`. The `useUpdateProcessoFaseStatus` hook sends this UUID string as the path segment for `PATCH /processos/:id/fases/:faseId`. Spring will fail to parse a UUID string as `Integer` and return a 400 error for every fase status update.

Additionally, the `createProcessoFase` endpoint (line 882) expects a `nome` field in the request body but the frontend `ProcessoFaseCreateRequest` sends `fase_id` and `status` (types/processos.ts line 80-83). This means the create-fase form in the detail page will always receive a 400 because `nome` is never provided.

**Fix:** Standardize fase identifiers. If `ProcessoFase.id` is an auto-increment Integer (legacy), then either:
- Change the frontend to send the numeric `ProcessoFase.id` (not the UUID `fase_id`), or
- Change `ProcessoFase.faseId` and `ProcessoFase.id` to UUID with `@GeneratedValue(strategy = GenerationType.UUID)` to match the frontend contract.

Also align the create-fase API contract: either accept `fase_id` (UUID of the catalog entry) as the frontend sends, or document that `nome` is required and update the frontend form.

---

### CR-08: Path traversal in document download — `doc.getCaminhoArquivo()` is stored as a filesystem path and used directly in `new File(...)`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1103`

**Issue:** The `downloadDocumento` handler does:
```java
File file = new File(doc.getCaminhoArquivo());
```
`caminhoArquivo` is whatever path was stored at upload time. During upload (line 1055-1065), `caminhoArquivo` is set from `Paths.get(UPLOAD_DIR + savedName)` where `savedName = fileId + extension`. The `extension` is derived from `file.getOriginalFilename()`. While the file `id` portion is a random UUID, the `extension` comes from the user-supplied original filename. If an attacker uploads a file named `../../../../etc/passwd`, the extension is blank (no dot), so the current code is partially mitigated. However:

1. The original filename itself is stored as `doc.getNome()` and directly injected into the `Content-Disposition` header (line 1112): `"attachment; filename=\"" + doc.getNome() + "\""`. A filename with `"` or newline characters will corrupt the HTTP header (header injection).
2. The stored `caminhoArquivo` is a value in the database. If an admin or a future migration incorrectly inserts a path with traversal characters, the download endpoint will serve arbitrary filesystem files from the backend host.

**Fix for header injection (immediate):**
```java
String safeName = doc.getNome() != null
    ? doc.getNome().replaceAll("[\\r\\n\"\\\\]", "_")
    : "download";
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
```

**Fix for path traversal (defense in depth):** Validate that the resolved path is inside the upload directory:
```java
Path uploadRoot = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
Path filePath = Paths.get(doc.getCaminhoArquivo()).toAbsolutePath().normalize();
if (!filePath.startsWith(uploadRoot)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Acesso negado"));
}
```

---

## Warnings

### WR-01: Conflict-check endpoint `POST /processos/{id}/conflict-check` accepts any processo state, not just TRIAGEM

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:649-715`

**Issue:** The conflict-check endpoint checks tenant ownership but not whether the processo is in TRIAGEM state. A user with `processos:create` can run conflict-check on a processo that is already ATIVO, ENCERRADO, etc. This is a minor workflow violation but the real risk is: by running conflict-check on an already-active processo, a user triggers a NIF/name search against all tenant clients, which could be used as a covert search mechanism without leaving a decisao record.

**Fix:** Add a state guard:
```java
if (!"TRIAGEM".equalsIgnoreCase(processo.getEstado())) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "message", "Conflict check só pode ser executado em processos em estado TRIAGEM"
    ));
}
```

---

### WR-02: `normalizeProcesso` in frontend maps `titulo` and `tipo_processo` from the same source field, causing field pollution

**File:** `web/src/hooks/use-processos.ts:74-75`

**Issue:**
```typescript
titulo: api.titulo ?? api.tipoProcesso,
tipo_processo: api.tipoProcesso ?? api.titulo,
```
Both `titulo` and `tipo_processo` fall back to each other. If only `tipoProcesso` is present (the canonical backend field), then `titulo` is populated with the tipo value. This causes the detail page (`[id]/page.tsx:247`) to display `tipo_processo ?? titulo` — which works — but `titulo` and `tipo_processo` will always both be set to `tipoProcesso`, making it impossible to distinguish whether the processo actually has a separate title. When the backend adds a real `titulo` field distinct from `tipoProcesso`, this normalization will silently shadow it.

**Fix:** Keep the fields separate:
```typescript
titulo: api.titulo,
tipo_processo: api.tipoProcesso ?? api.tipo_processo,
```

---

### WR-03: `useConflictCheckDecisao` GET endpoint can return HTTP 200 with `null` body — frontend must handle null but the type annotation says `ConflictCheckDecisao | null`, creating a subtle runtime issue

**File:** `web/src/hooks/use-processos.ts:400-412` / `backend/src/main/java/com/lexcv/controllers/ResourceController.java:719-728`

**Issue:** The backend `getDecisaoConflito` returns `ResponseEntity.ok(null)` when no decisao exists (line 726). The frontend hook types this as `apiFetch<ConflictCheckDecisao | null>`. Whether `apiFetch` correctly handles a `null` JSON body (i.e., when the backend serializes `null` as the response body, Jackson will return literally `null` in JSON) depends on `apiFetch`'s implementation. If `apiFetch` throws on non-2xx or on empty body, this will surface as an unexpected error. More concretely, if the API returns `null` and `apiFetch` tries to parse it, the result stored in `decisao.data` will be `null`, but TanStack Query caches `null` as a valid query result — so after `decisao.data` is `null` and the user records a decisao, the `invalidateQueries` call will refetch and the cache may briefly render the blocked formalizar state before the new data arrives.

The more serious issue: `decisao.data` in `[id]/page.tsx` is used for `isFormalizarBlocked` at line 128-129:
```typescript
const isFormalizarBlocked = !decisao.data || decisao.data.nivel === "impeditivo";
```
While the backend _also_ blocks this server-side (correct), the frontend treats a query that is still loading (`decisao.isLoading`) the same as having no decisao, so formalizar is blocked while loading. This is correct behavior, but if `decisao.error` is set (the GET endpoint returned an error), the block would also fire correctly. However, the GET endpoint returns 200+null (not 404) when no decisao exists, so this is survivable.

**Fix:** Return 404 when no decisao exists, or return a structured response with an explicit `exists: false` field. This also makes the TanStack Query cache behavior predictable.

---

### WR-04: Intake wizard allows advancing to Step 3 without ever running conflict-check — only requires `decisaoData !== null`

**File:** `web/src/app/(dashboard)/processos/novo/page.tsx:177`

**Issue:**
```typescript
const canAdvanceToStep3 = decisaoData !== null && decisaoData.nivel !== "impeditivo";
```
The wizard lets a user record a decisao and advance to Step 3 _without ever clicking "Executar Conflict Check"_. A user with `processos:manage` can jump from the blank Step 2 directly to recording a decision (the decisao form appears after `conflictResult` is set, so in theory it's hidden — but `conflictResult` is only client-side state and is initialized to `null`). The decisao form is rendered inside `{conflictResult ? (...) : null}` at line 445, so it is correctly hidden until a result exists. However, if the user performs a conflict check that returns zero matches, records a "sem_conflito" decisao, advances, then navigates back and records another decisao without re-running the check, `decisaoData` is updated in component state but the backend's upsert at CR-05 means the old result is silently overwritten.

The real missing check: the backend `formalizar` does not verify that a conflict-check was _ever run_ — it only checks that a `ConflictCheckDecisao` record exists. A user can POST directly to `/conflict-check/decisao` without POSTing to `/conflict-check` first, and formalizar will succeed. This is a process integrity gap.

**Fix:** Track whether the conflict-check scan was run as a separate field (e.g., a `conflito_verificado_at` timestamp on the processo or as a separate record). The `formalizar` endpoint should verify `conflito_verificado_at IS NOT NULL` in addition to the decisao record.

---

### WR-05: `CAMPOS_MINIMOS_POR_TIPO` uses uppercase keys ("CIVIL", "PENAL") but the frontend sends lowercase values ("civel", "penal", "laboral")

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:62-70` / `web/src/app/(dashboard)/processos/novo/page.tsx:300-307`

**Issue:** The intake form at line 300-307 sends:
```html
<option value="civel">Cível</option>
<option value="penal">Penal</option>
<option value="laboral">Laboral</option>
<option value="administrativo">Administrativo</option>
...
```
The `formalizarProcesso` handler does:
```java
String tipoProcesso = processo.getTipoProcesso() != null
    ? processo.getTipoProcesso().toUpperCase()
    : null;
List<String> camposObrigatorios = CAMPOS_MINIMOS_POR_TIPO.getOrDefault(tipoProcesso,
        CAMPOS_MINIMOS_POR_TIPO.get("default"));
```
`.toUpperCase()` maps "civel" → "CIVEL" but the map key is "CIVIL" (not "CIVEL"). So all processos created with tipo_processo = "civel" will fall through to the `default` key, not the `CIVIL` key. The minimum field set for the default is `["clienteId", "tipoProcesso", "areaJuridica", "dataInicio"]` which is less strict than `CIVIL`'s `["clienteId", "numeroProcesso", "areaJuridica", "tribunal", "dataInicio"]`. This means "CIVIL" processos can be formalized without `numeroProcesso` and `tribunal` — the field enforcement is silently bypassed.

**Fix:** Align the enum values. Either:
- Change the form values to `"CIVIL"`, `"PENAL"`, `"LABORAL"`, etc. (matching the backend map keys exactly), or
- Change the map keys to the Portuguese form: `"CÍVEL"`, `"PENAL"`, `"LABORAL"`, `"ADMINISTRATIVO"`, `"FAMÍLIA"`, `"COMERCIAL"`.

---

### WR-06: `DatabaseSeeder.seedRbac()` runs on every startup regardless of `seedEnabled` — permission changes cannot be rolled back

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:40-44`

**Issue:** `seedRbac()` is called unconditionally at line 41, before the `seedEnabled` guard at line 43. This means every time the application starts — even in production with `SEED_ENABLED=false` — the RBAC seed runs. The `upsertRolePermissions` logic only adds permissions (never removes). If a permission is removed from the code list in a future change (e.g., `processos:create` is eliminated during a role redesign), the removal will never take effect: the old permission remains in the database.

This is not a data-loss risk per se but is a security maintenance risk: permission grants are permanent and accumulative.

**Fix:** Either (a) move `seedRbac()` inside the `seedEnabled` guard and provide a separate admin migration for RBAC changes, or (b) implement a full sync that also removes permissions not in the current list:
```java
// Remove permissions no longer in the list
Set<Permission> toRemove = new HashSet<>(role.getPermissions());
toRemove.removeAll(new HashSet<>(permissions));
role.getPermissions().removeAll(toRemove);
```

---

### WR-07: Pagination UI in processos list page is non-functional — hardcoded page numbers with no logic

**File:** `web/src/app/(dashboard)/processos/page.tsx:363-370`

**Issue:** The pagination UI renders hardcoded buttons 1, 2, 3, …, › with no click handlers and no state. All records are already returned in a single backend call (`listProcessos` returns all filtered records with no server-side pagination). Clicking buttons 2, 3, or › does nothing. Users may assume records beyond page 1 exist and click the buttons expecting new data, receiving no feedback. This is a latent UX defect that will become a data-correctness problem as the dataset grows.

**Fix:** Either remove the pagination UI entirely and label the result count, or implement server-side pagination with `page` and `pageSize` parameters and wire the buttons to update state.

---

### WR-08: `onUpdateFaseStatus` in `[id]/page.tsx` can submit `undefined` status when no draft change has been made for a given fase

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:168-175`

**Issue:**
```typescript
const onUpdateFaseStatus = async (faseId: string) => {
    const status = faseDraftStatus[faseId];
    const payload: ProcessoFaseUpdateRequest = { status };
    ...
```
If the user clicks "Guardar" for a fase without first changing the dropdown (i.e., `faseDraftStatus[faseId]` is undefined because the state map was never populated for that key), `status` will be `undefined` and `payload` will be `{ status: undefined }`. The backend receives a JSON body with `"status": null` (or the key may be omitted). The backend handler at ResourceController line 920 checks `body.containsKey("status")` — if the key is absent (omitted in serialization), the status update is silently skipped and a 200 is returned, making the user think the save succeeded.

**Fix:** Guard against undefined status before submitting:
```typescript
const onUpdateFaseStatus = async (faseId: string) => {
    const status = faseDraftStatus[faseId];
    if (!status) return; // nothing changed
    ...
```

---

## Info

### IN-01: `ConflictCheckRequest` DTO is imported in ResourceController but never used

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:6` / `backend/src/main/java/com/lexcv/dtos/ConflictCheckRequest.java`

**Issue:** `ConflictCheckRequest` (which wraps a single `processoId`) is imported at line 6 of ResourceController but the `runConflictCheck` endpoint uses `@PathVariable UUID id` rather than a request body. The DTO is a dead artifact.

**Fix:** Delete `ConflictCheckRequest.java` and remove the import.

---

### IN-02: `decisaoData.dataDecisao` in the wizard is set client-side from `new Date().toLocaleDateString("pt-CV")` — could differ from the server's stored `dataDecisao`

**File:** `web/src/app/(dashboard)/processos/novo/page.tsx:147`

**Issue:** After a successful decisao POST, the wizard sets local state:
```typescript
setDecisaoData({
    nivel: values.nivel,
    ...
    dataDecisao: new Date().toLocaleDateString("pt-CV"),
});
```
The backend stores `LocalDate.now()` as `dataDecisao`. The display in Step 3's review panel shows this client-side date. If the client clock is wrong or in a different timezone, the displayed date will differ from the stored date. The detail page (`[id]/page.tsx:312`) displays the server-returned `decisao.data.dataDecisao` directly, so this only affects the wizard summary panel.

**Fix:** After a successful decisao POST, read `dataDecisao` from the mutation response object rather than re-computing it on the client:
```typescript
const saved = await registarDecisao.mutateAsync(payload);
setDecisaoData({
    ...
    dataDecisao: saved.dataDecisao,
});
```
(Requires `useRegistarDecisaoConflito` to return the full `ConflictCheckDecisao` — which it already does at `use-processos.ts:378`.)

---

### IN-03: `conflictNivelToVariant` / `conflictNivelToLabel` have no fallback for unknown nivel values

**File:** `web/src/lib/conflict-check.ts:7-31`

**Issue:** Both functions use a chain of ternaries and implicitly return the last branch (`"red"` / `"IMPEDITIVO"`) for any unrecognized nivel string. If the backend ever introduces a new nivel (e.g., `"nao_aplicavel"`) or returns a typo, it will be displayed as "IMPEDITIVO" (red), falsely blocking formalizar in the UI. TypeScript's type guard (`ConflictNivel` is a union) makes this unlikely in the wizard, but the detail page casts `decisao.data.nivel` as `ConflictNivel` without runtime validation.

**Fix:** Add an explicit default/unknown case:
```typescript
export function conflictNivelToVariant(nivel: ConflictNivel): "green" | "amber" | "blue" | "red" {
    const map: Record<ConflictNivel, "green" | "amber" | "blue" | "red"> = {
        sem_conflito: "green", potencial: "amber", sanavel: "blue", impeditivo: "red",
    };
    return map[nivel] ?? "amber";
}
```

---

### IN-04: Seeded `Processo` records use `estado: "ATIVO"` and `estado: "ENCERRADO"` — both will bypass the intake/conflict-check flow if used in testing

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:149,163`

**Issue:** The two seeded processos are created with `estado = "ATIVO"` and `estado = "ENCERRADO"` respectively. They have no associated `ConflictCheckDecisao`. If a developer runs `formalizar` on either of these seeded processos during manual testing, CR-02 (missing TRIAGEM state check) means the endpoint will proceed through the campos-minimos check and then fail at the decisao-absent guard (409 CONFLICT). This is the correct behavior but the seeded data is misleading — it bypasses the intake flow entirely and creates a false impression that ATIVO processos are "normal" to have without a conflict-check decisao.

**Fix:** Either set seeded processos to `estado: "TRIAGEM"` with a corresponding seeded `ConflictCheckDecisao`, or add a comment in the seeder clarifying that these are synthetic legacy records that predate the conflict-check requirement.

---

_Reviewed: 2026-06-13T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
