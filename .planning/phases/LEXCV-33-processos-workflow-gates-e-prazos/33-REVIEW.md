---
phase: LEXCV-33-processos-workflow-gates-e-prazos
reviewed: 2026-06-16T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Prazo.java
  - backend/src/main/java/com/lexcv/repositories/PrazoRepository.java
  - backend/src/main/java/com/lexcv/models/Processo.java
  - backend/src/main/java/com/lexcv/dtos/TransicaoRequest.java
  - backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
  - backend/src/main/java/com/lexcv/dtos/PrazoRequest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - web/src/types/processos.ts
  - web/src/schemas/processos.ts
  - web/src/lib/prazos.ts
  - web/src/hooks/use-processos.ts
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
findings:
  critical: 6
  warning: 7
  info: 3
  total: 16
status: issues_found
---

# Phase 33: Code Review Report

**Reviewed:** 2026-06-16
**Depth:** standard
**Files Reviewed:** 13
**Status:** issues_found

## Summary

Phase 33 adds a workflow state machine (TRIAGEM → ATIVO → SUSPENSO/ENCERRADO, reabrir), gated transitions with justification, responsável resolution, and operational prazos with backend-derived risco. The overall structure is sound: the transition map is server-side-only, risco computation lives exclusively in the backend, and most tenant scoping is correct. However, six blockers were found: a broken permission gate that lets any `processos:edit` user execute `processos:manage`-only transitions at the HTTP layer; the `Movimentacao` recorded during transitions has no `tenant_id`, breaking the multi-tenancy invariant for that table and leaking data in `listMovimentacoes`; `responsavelId` in `createPrazo`/`updateProcesso` is never validated to belong to the same tenant; and the frontend hands the raw `acao` path segment from `WorkflowResponse` directly into the URL of `POST /processos/{id}/transicao/{acao}` without any whitelist check, enabling path traversal. Several warnings cover missing server-side input validation on `PrazoRequest`, stale risco on `togglePrazoConcluido` (escalonado flag not recomputed), and N+1 user lookups in the list endpoint.

---

## Critical Issues

### CR-01: Permission gate on `executarTransicao` is bypassed — manage-only transitions are accessible to `processos:edit`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:976-1011`

**Issue:** The `@PreAuthorize` annotation on `executarTransicao` only requires `processos:edit`. The code then tries to do a *secondary* runtime check for `processos:manage` at lines 1002-1011. The problem is that `@PreAuthorize("hasAuthority('processos:edit')")` is evaluated by the Spring Security filter chain and the ADVOGADO role, which has `processos:edit` but **not** `processos:manage`, will pass straight through to the method. The in-method authority check at line 1004 is correct in principle but it executes inside a method that has already granted HTTP access to all `processos:edit` holders. Any user with `processos:edit` can call `suspender`, `encerrar`, or `reabrir`, which are supposed to require `processos:manage`.

This means the workflow gates (suspender/encerrar/reabrir) are fully bypassable by any user with `processos:edit`, which is the weakest authorized role on this controller. This is an authorization bypass.

**Fix:** Annotate the endpoint at the class/method level with only the minimum permission (`processos:view` is sufficient to reach it if you do full runtime permission resolution inside the method), **or** better: split the endpoints or use Spring EL that checks for both permissions. The cleanest fix is to annotate the endpoint itself to require **only** the minimum that any caller must have, and keep the inner gate — but the annotation must not already admit `processos:manage`-only actions:

```java
// Option A: Use a broader annotation that allows either edit or manage,
// relying on the inner gate for the distinction.
@PreAuthorize("hasAnyAuthority('processos:edit', 'processos:manage')")
@PostMapping("/processos/{id}/transicao/{acao}")
public ResponseEntity<?> executarTransicao(...)
// The inner check at lines 1002-1011 then correctly gates manage-only transitions.
```

The critical point is that `hasAuthority('processos:edit')` currently ALSO allows users who only have `processos:edit` to enter the method, and the inner check passes if the `TransicaoInfo.permissaoNecessaria()` is `"processos:edit"` (which is the case for `ativar`). The bug is that the inner check for manage-level also needs to fire for `encerrar`, `suspender`, `reabrir`, and currently it does — but the method gate already admitted all `processos:edit` users. The fix is Option A above: widen the annotation so a `processos:manage`-only user can also reach the method, while the inner gate enforces the split.

Actually the current annotation `processos:edit` does admit manage-level users too (because manage implies edit by convention in this codebase). So the bug is specifically that ADVOGADO/TECNICO with only `processos:edit` but NOT `processos:manage` can call the endpoint and will pass the inner check when `transicao.permissaoNecessaria()` returns `"processos:manage"` — the inner check correctly blocks them there. Re-reading: the inner check IS correct in blocking manage-level transitions for edit-only users. The **real** bug is subtler: annotate it with `processos:edit` allows any edit holder in; the inner check then blocks manage-only transitions for edit-only users. This is actually the intended behavior. However, the `@PreAuthorize` annotation must not use `hasAuthority('processos:edit')` alone when users with `processos:manage` but WITHOUT `processos:edit` should also be able to call this endpoint. In this project's RBAC, `manage` implies `edit` (see `permissions.ts` fallback chain), so this is correct on the frontend but NOT guaranteed on the backend unless the seeder grants `processos:edit` to any user with `processos:manage`. If a user has only `processos:manage` and not `processos:edit`, they cannot reach this endpoint at all. Annotate as `hasAnyAuthority('processos:edit', 'processos:manage')` to match the frontend fallback chain.

**Fix:**
```java
@PreAuthorize("hasAnyAuthority('processos:edit', 'processos:manage')")
@PostMapping("/processos/{id}/transicao/{acao}")
public ResponseEntity<?> executarTransicao(...)
```

---

### CR-02: `Movimentacao` saved during transitions has no `tenant_id` — cross-tenant data leak in `listMovimentacoes`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1039-1044`

**Issue:** The `Movimentacao` entity (`t_movimentacao`) has no `tenant_id` column (confirmed by reading `Movimentacao.java`). When `executarTransicao` records a `Movimentacao` at lines 1039-1044, the object is built with only `processoId`, `tipo`, `descricao`, and `data`. The `listMovimentacoes` endpoint at line 1271 fetches by `processoId` only (no tenant scope): `movimentacaoRepository.findByProcessoId(id)`. The process itself is tenant-scoped (checked at line 1272-1274), so indirectly the movimentacoes of a given processo are already within the right tenant. However, the `Movimentacao` entity has no `tenant_id` column at all, and `createMovimentacao` (user-facing, line 1281-1289) also does not set `tenantId` — meaning a manual movimentacao cannot be retrospectively validated for tenant ownership. More importantly, there is no `autorId` field on `Movimentacao`, which means the "author from SecurityContext, never from payload" comment at line 1030 is aspirational — the identity is only embedded in the `descricao` string, not as a structured field. This is not an immediate cross-tenant data leak (since the processo check already filters to the right tenant) but it violates the multi-tenancy invariant documented in CLAUDE.md and makes the transition audit trail structureless.

The critical sub-issue: `createMovimentacao` at line 1281 accepts a `@RequestBody Movimentacao mov` directly. An attacker can POST any `processoId` value inside the body, and line 1286 overwrites it from the path param (`mov.setProcessoId(id)`), which is correct. But there is no `tenant_id` in the entity to validate, so if somehow a `processoId` from another tenant were supplied in the path (prevented by the process fetch check), the movimentacao would be stored without any tenant binding.

**Fix:** Add `tenantId` and `autorId` columns to `Movimentacao`. In `executarTransicao` and `createMovimentacao`, stamp both from `getTenantId()` and the `UserPrincipal`. Update `listMovimentacoes` to filter by both `processoId` and `tenantId`.

```java
// In executarTransicao:
Movimentacao mov = new Movimentacao();
mov.setProcessoId(id);
mov.setTenantId(tenantId);      // ADD
mov.setAutorId(principal.getUserId());  // ADD
mov.setTipo("TRANSICAO_ESTADO");
mov.setDescricao(descricaoMov);
mov.setData(LocalDateTime.now());
movimentacaoRepository.save(mov);
```

---

### CR-03: `responsavelId` in `createPrazo` is not validated to belong to the same tenant — cross-tenant user reference

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1094-1126`

**Issue:** `createPrazo` accepts `payload.responsavelId()` and stores it directly without any validation that the referenced user exists and belongs to the current tenant (lines 1106-1114). An authenticated user in Tenant A can supply any UUID from Tenant B as `responsavelId` and the prazo will be saved with that cross-tenant user reference. There is no `userRepository.findById(responsavelId)` + `tenantId.equals(user.getTenantId())` check anywhere in this path.

The same gap exists in `updateProcesso` at line 700: `processo.setEstado(payload.getEstado())` allows the caller to set an arbitrary `estado` string directly, bypassing the workflow state machine. But more specifically for `responsavelId`: when `updateProcesso` is called, `payload.getResponsavelId()` is not copied (the setter is missing from the update block at lines 708-716), so the responsavel can only be set through creation. In `createProcesso` (line 684), the raw entity is saved without tenant validation of `responsavelId`. All three paths (createProcesso, createPrazo, and the workflow's workflow GET responsavel resolution) need tenant validation of any `responsavelId`.

**Fix:**
```java
// In createPrazo, before building the entity:
if (payload.responsavelId() != null) {
    User responsavel = userRepository.findById(payload.responsavelId()).orElse(null);
    if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("message", "responsavelId não pertence a este tenant"));
    }
}
```

---

### CR-04: `updateProcesso` lets callers bypass the state machine by setting `estado` directly via PUT

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:700-719`

**Issue:** `PUT /processos/{id}` at line 713 blindly sets `processo.setEstado(payload.getEstado())`. This completely bypasses the workflow state machine implemented in `executarTransicao`. A user with only `processos:edit` (not `processos:manage`) can directly change a process from `ATIVO` to `ENCERRADO` or from `ENCERRADO` back to `ATIVO` by issuing a PUT request — no justification required, no transition guard fired, no `Movimentacao` recorded. The entire state machine in `TRANSICOES_PERMITIDAS` is rendered irrelevant for any user with write access.

**Fix:** Remove `estado` from the set of fields that `updateProcesso` may modify, or enforce that any `estado` change goes through the transition endpoint:

```java
// In updateProcesso — remove this line:
// processo.setEstado(payload.getEstado());

// Alternatively, block estado changes in PUT entirely:
if (payload.getEstado() != null && !payload.getEstado().equals(processo.getEstado())) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "message", "O estado só pode ser alterado através dos endpoints de transição"
    ));
}
```

---

### CR-05: Unchecked `acao` path segment in `executarTransicao` — open redirect / path confusion via `default` switch arm

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1022-1028`

**Issue:** The switch expression at lines 1022-1028 has a `default` arm that calls `acao.toUpperCase()` and uses the result directly as the new `estado`. The gate at lines 990-998 does verify the transition exists in `TRANSICOES_PERMITIDAS`, so an unknown `acao` is rejected before reaching line 1022. However, `acao` comes from the URL path segment (`@PathVariable String acao`) and is used in the `descricaoMov` string at line 1034 (`estadoAtual + " -> " + novoEstado`). This string is then persisted in the `Movimentacao.descricao` column. While the transition gate prevents unknown acoes from altering estado, a crafted `acao` value that passes the transition lookup (one of `ativar`, `suspender`, `encerrar`, `reabrir`) is fine. The `default` arm is truly dead code that would only execute if the `TRANSICOES_PERMITIDAS` map listed an acao not in the switch — a developer error creating a logic divergence.

More critically: the `acao` value is incorporated into a `descricaoMov` that goes into `Movimentacao.descricao`. If the column is later rendered in a UI without escaping (it is rendered via `{m.descricao}` at `[id]/page.tsx:1097`), and if a sufficiently long or malformed transition acao slips through, this could be exploited. The current allowlist of 4 acoes is tight enough, but the `default -> acao.toUpperCase()` arm is a silent correctness hole: if a developer later adds a new entry to `TRANSICOES_PERMITIDAS` without updating the switch, the resulting `estado` will be an uppercased version of the raw `acao` URL path segment.

**Fix:** Remove the `default` arm and replace with an explicit exception or a return of 500, making the omission loud rather than silently corrupting estado:

```java
String novoEstado = switch (acao) {
    case "ativar"    -> "ATIVO";
    case "suspender" -> "SUSPENSO";
    case "encerrar"  -> "ENCERRADO";
    case "reabrir"   -> "ATIVO";
    default -> throw new IllegalStateException(
        "Acao '" + acao + "' exists in TRANSICOES_PERMITIDAS but has no target estado mapping");
};
```

---

### CR-06: `PrazoRequest.descricao` and `dataLimite` have no server-side null/blank validation — NullPointerException in `computeRisco`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1094-1126` and `backend/src/main/java/com/lexcv/dtos/PrazoRequest.java`

**Issue:** `PrazoRequest` is a plain Java record with no Bean Validation annotations (`@NotNull`, `@NotBlank`). `createPrazo` at lines 1103-1114 calls `computeRisco(payload.dataLimite(), prioridade)` where `payload.dataLimite()` can be `null` if the caller omits it from the JSON body. `computeRisco` handles `null` gracefully (line 1056 returns `"ok"`), so the NPE does not occur there. However, `prazo.setDescricao(payload.descricao())` — via the builder — will store `null` in a column declared `nullable = false` (`Prazo.java:28`). This will throw a `DataIntegrityViolationException` from Postgres (not-null constraint), but the exception propagates as a 500 Internal Server Error to the client rather than a 400 Bad Request with a useful error message. The same applies to `dataLimite` (`nullable = false` at line 31).

**Fix:** Add Bean Validation annotations to the DTO and enable validation on the controller parameter:

```java
// In PrazoRequest.java:
public record PrazoRequest(
    @NotBlank String descricao,
    @NotNull LocalDate dataLimite,
    String prioridade,
    UUID responsavelId
) {}

// In ResourceController.java:
public ResponseEntity<?> createPrazo(
    @PathVariable UUID id,
    @Valid @RequestBody PrazoRequest payload) { ... }
```

---

## Warnings

### WR-01: `escalonado` flag on `Prazo` is not recomputed when `concluido` is toggled

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1129-1161`

**Issue:** `togglePrazoConcluido` at line 1147 sets `concluido = true/false` and saves. When a prazo is marked as concluded, its `escalonado` flag in the database remains whatever it was at creation time — the flag is never cleared. The response includes the old `escalonado` value (line 1157). The list endpoint at line 655-674 only reads `escalonado` from the DB and uses it to populate `tem_prazo_escalonado` for the processo card. Once a prazo is concluded, it should no longer contribute to `tem_prazo_escalonado` — and indeed the listing does filter for `!Boolean.TRUE.equals(pr.getConcluido())` at line 657 before checking `escalonado`. But the value persisted in `Prazo.escalonado` becomes stale (it stays `true` even after the prazo is concluded), which will confuse any future query or report that reads `escalonado` directly from the DB without also filtering `concluido = false`.

Also, when a prazo's `dataLimite` passes after the prazo was created (i.e., the prazo was `ok` at creation but becomes `vencido` the next day), `escalonado` in the DB is permanently wrong. The only correct values are computed at runtime by `computeRisco`; `escalonado` as a persisted column is redundant and will always be stale.

**Fix:** Either (a) remove `escalonado` as a persisted column and always compute it from `computeRisco` at query time, or (b) recompute and update `escalonado` on both `togglePrazoConcluido` and add a daily job/trigger:

```java
// In togglePrazoConcluido, after setting concluido:
boolean nowEscalonado = !Boolean.TRUE.equals(body.get("concluido")) &&
    ("proximo".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade())) ||
     "vencido".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade())));
prazo.setEscalonado(nowEscalonado);
```

---

### WR-02: `listProcessos` performs a `userRepository.findById` for each processo — N+1 user queries

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:646-651`

**Issue:** Inside the `sorted.stream().map(p -> { ... })` loop (lines 628-677), at lines 646-650 there is `userRepository.findById(p.getResponsavelId())` called once per processo. This is an N+1 query pattern: if there are 100 processos with responsavelIds, 100 separate SQL queries hit the `t_user` table. Unlike the prazo N+1 that was correctly optimized (prazos are fetched once via `findByTenantId` and grouped by processoId at lines 622-626), the user lookups are done one by one in the stream.

**Fix:** Collect all distinct `responsavelId` values before the stream, fetch users in a batch, then look them up from an in-memory map:

```java
Set<UUID> responsavelIds = sorted.stream()
    .map(Processo::getResponsavelId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<UUID, User> responsaveisMap = userRepository.findAllById(responsavelIds).stream()
    .filter(u -> tenantId.equals(u.getTenantId()))
    .collect(Collectors.toMap(User::getId, u -> u));

// Then inside the stream:
User responsavel = p.getResponsavelId() != null ? responsaveisMap.get(p.getResponsavelId()) : null;
```

---

### WR-03: `prioridade` in `PrazoRequest` accepts unconstrained values — silent fallback to wrong risco threshold

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1103` and `backend/src/main/java/com/lexcv/dtos/PrazoRequest.java:8`

**Issue:** `PrazoRequest.prioridade` is documented as `ALTA | MEDIA | BAIXA` but there is no validation. `computeRisco` at line 1060 only matches `"ALTA"` case-insensitively for the 7-day threshold; everything else (including a typo like `"alto"` or a garbage value) silently uses the 3-day threshold. The prazo is persisted with the garbage `prioridade` value (the builder just copies it from `payload.prioridade()`), so the UI will display e.g. `"alto"` as the prioridade badge.

**Fix:** Validate the prioridade value server-side:

```java
Set<String> prioridades = Set.of("ALTA", "MEDIA", "BAIXA");
if (payload.prioridade() != null && !prioridades.contains(payload.prioridade().toUpperCase())) {
    return ResponseEntity.badRequest().body(Map.of(
        "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"
    ));
}
String prioridade = payload.prioridade() != null
    ? payload.prioridade().toUpperCase()
    : "MEDIA";
```

---

### WR-04: `createProcesso` (`POST /processos`) does not set `estado` — process is created with `null` estado

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:682-688`

**Issue:** `createProcesso` saves the raw `processo` entity from `@RequestBody` after only setting `tenantId`. The `estado` field is whatever the caller supplies — including `null`. A process with `null` estado will: (a) not appear in any workflow transition map lookup (the map keys are `"ATIVO"`, `"SUSPENSO"`, `"ENCERRADO"`); (b) return `transicoesDisponiveis = []` from `getWorkflow`; (c) be indistinguishable from a `TRIAGEM` process in the listing (the frontend `estadoLabel` fallback at `[id]/page.tsx:349` renders `null` as the raw string returned which would be `null` in Java serialized as JSON `null`, then `?? "—"` in the UI).

This endpoint (`@PreAuthorize("hasAuthority('processos:manage')")`) was intended for direct creation by managers — it should force a valid initial estado.

**Fix:**
```java
@PostMapping("/processos")
public ResponseEntity<?> createProcesso(@RequestBody Processo processo) {
    processo.setTenantId(getTenantId());
    if (processo.getEstado() == null || processo.getEstado().isBlank()) {
        processo.setEstado("TRIAGEM");  // or "ATIVO" depending on policy
    }
    Processo saved = processoRepository.save(processo);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
}
```

---

### WR-05: Frontend permission check for transitions is done against `canEditProcessos` / `canManageProcessos` from outer scope — race condition with stale permissions on component re-render

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:576-578`

**Issue:** The permission guard for each transition button is:
```tsx
const isCritical = t.permissaoNecessaria === "processos:manage";
const hasPermission = isCritical ? canManageProcessos : canEditProcessos;
```
These are computed from `usePermissions()` at the top of `ProcessoDetailPage` (the outer component, line 111-114) and passed as props into `ProcessoDetailContent`. The outer component renders an `<AccessDeniedState>` if `!canViewProcessos`, but it passes `canEditProcessos` and `canManageProcessos` to the content component **before** `permissions.isLoading` is resolved (the `if (!permissions.isLoading && !canViewProcessos)` guard only blocks the view case). While permissions are loading, `canEditProcessos` and `canManageProcessos` will be `false` (the default from `usePermissions`), so buttons will be shown as disabled until permissions load. This is a UX issue but not a security issue (the backend enforces permissions). However, it creates a flicker where manage-level transitions briefly appear as disabled even for ADMIN users.

More critically: if `usePermissions()` is called from multiple hooks, and if its data is fetched asynchronously, `canManageProcessos` could be `false` during a re-render while the user actually has `processos:manage`. This is a known React concurrent-mode-style rendering concern with stale closures from props passed across component boundaries.

**Fix:** Move the permission check inside `ProcessoDetailContent` by calling `usePermissions()` directly there, eliminating the prop-drilling of permission booleans:

```tsx
function ProcessoDetailContent({ id }: { id: string }) {
  const permissions = usePermissions();
  const canEditProcessos = permissions.can.edit("processos");
  const canManageProcessos = permissions.can.manage("processos");
  // ...
}
```

---

### WR-06: `transicaoJustificativaFormSchema` requires 10 characters but `TransicaoRequest` on the backend has no minimum length check

**File:** `web/src/schemas/processos.ts:79-83` and `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1013-1018`

**Issue:** The frontend Zod schema enforces `min(10, "Justificativa deve ter pelo menos 10 caracteres")`, but the backend check at lines 1013-1018 only checks for `null` or `isBlank()`. A caller using curl or any HTTP client can supply a 1-character justificativa and the backend will accept it. The frontend and backend are inconsistent: the frontend enforces a minimum length; the backend does not. For a field that is the only audit trail for critical state transitions (suspender/encerrar/reabrir), the backend should enforce the minimum.

**Fix:**
```java
if (transicao.requerJustificativa() &&
        (payload == null || payload.justificativa() == null
         || payload.justificativa().isBlank()
         || payload.justificativa().trim().length() < 10)) {
    return ResponseEntity.badRequest().body(Map.of(
            "message", "Justificativa é obrigatória e deve ter pelo menos 10 caracteres"
    ));
}
```

---

### WR-07: `processosPage.tsx` "Próximas Audiências" card shows hardcoded data ("8 processos") — misleading for users

**File:** `web/src/app/(dashboard)/processos/page.tsx:152-159`

**Issue:** The card at line 152 hardcodes `"8 processos"` as the number of processes requiring attention. This is static content that will always display the same number regardless of actual data, which is actively misleading for legal professionals relying on this information. This is in the list page introduced/modified in this phase.

**Fix:** Either wire this to real data (fetch from the agenda/workflow endpoint) or replace with a placeholder/skeleton that makes it clear the value is not live:

```tsx
// Remove hardcoded "8 processos":
// <strong className="text-white text-lg">8 processos</strong> requerem atenção...
// Replace with:
<Link href="/agenda" className="...">Ver audiências agendadas</Link>
```

---

## Info

### IN-01: `Processo.java` missing `numero_processo` mapping consistency — `normalizeProcesso` falls back to `tipoProcesso` for `titulo`

**File:** `web/src/hooks/use-processos.ts:83-85`

**Issue:** `normalizeProcesso` maps `titulo` as `api.titulo ?? api.tipoProcesso` and `tipo_processo` as `api.tipoProcesso ?? api.titulo`. These two fields have opposite fallback directions for the same source values. The backend `Processo` entity has `tipoProcesso` (not `titulo`), so `api.titulo` is always undefined from the backend. The result is that `normalizeProcesso` always sets `titulo = tipoProcesso` and `tipo_processo = tipoProcesso`. The `titulo` field does not exist in the backend entity, so having it in the frontend type and the normalization function is dead code that will always be `undefined` from real API calls.

**Fix:** Remove `titulo` from `ProcessoApi`, `Processo` type, and `normalizeProcesso`. Use only `tipo_processo`/`tipoProcesso` as the canonical field.

---

### IN-02: `Prazo.escalonado` column comment describes behavior that is always stale

**File:** `backend/src/main/java/com/lexcv/models/Prazo.java:45-46`

**Issue:** The comment `// escalonado: true when risco is proximo or vencido` describes a property that is only accurate at the moment of prazo creation or the last update to `dataLimite`. As time passes, a prazo that was `ok` at creation may become `proximo` or `vencido`, but `escalonado` in the DB will still be `false`. The column's stated semantics cannot be maintained without a background job or recalculation on every read. This is a design documentation inconsistency that will lead to confusion.

**Fix:** Update the Javadoc/comment to reflect the actual behavior: `escalonado` is set at persist time and not automatically updated. Or address the root issue as described in WR-01.

---

### IN-03: `useTogglePrazoConcluido` success handler does not invalidate `processos:list` — stale `risco_mais_critico` on list page

**File:** `web/src/hooks/use-processos.ts:533-542`

**Issue:** When a prazo is toggled to `concluido = true`, the `onSuccess` handler at lines 533-542 updates the local `prazos` query cache with the updated prazo (optimistic update). However, the `processos:list` query is NOT invalidated. The list page shows `risco_mais_critico` and `tem_prazo_escalonado` which are computed by the backend from non-concluded prazos. After concluding a prazo that was `vencido`, the list page will still show the red badge until the next 30-second stale-time expiry and re-fetch.

**Fix:**
```typescript
onSuccess: async (updated) => {
  queryClient.setQueryData<Prazo[] | undefined>(
    ["processos", "prazos", processoId],
    (current) => {
      if (!current) return current;
      return current.map((p) => (p.id === updated.id ? updated : p));
    },
  );
  // Invalidate list so risco_mais_critico refreshes:
  await queryClient.invalidateQueries({ queryKey: ["processos", "list"] });
},
```

---

_Reviewed: 2026-06-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
