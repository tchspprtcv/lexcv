# Phase 118: Frontend — Indicador de Utilizadores no Limite - Pattern Map

**Mapped:** 2026-07-29
**Files analyzed:** 4 (0 new, 4 modified — this phase is a pure extension of existing files, no new files anywhere)
**Analogs found:** 4 / 4 (3 exact self-extensions, 1 composite/role-match)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` (`getMe`, lines 146-175) | controller | request-response | itself — existing `tenant_nome`/`tenant_logo_data_url` block, lines 169-172 | exact |
| `backend/src/main/java/com/lexcv/dtos/UserResponse.java` (implied by the `getMe` change — Lombok DTO backing the response, not separately named in CONTEXT.md but required plumbing) | model (DTO) | transform | itself — existing `tenant_nome`/`tenant_logo_data_url` fields, lines 24-25 | exact |
| `web/src/types/auth.ts` (`MeResponse`, lines 17-29) | model (type) | transform | itself — existing `tenant_nome?`/`tenant_logo_data_url?` fields, lines 27-28 | exact |
| `web/src/app/(dashboard)/settings/page.tsx` (`UserManagementTab`, `CardHeader` ~lines 351-365; `handleFormSubmit` catch, lines 292-299) | component | CRUD (whole tab); request-response (indicator readout); event-driven (disabled+tooltip interaction) | itself (Tooltip JSX shape, lines 462-474) **+** `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (span-tabIndex-wrapper technique, lines 302-311) | role-match (composite — no single exact analog exists for "disabled Button + working Tooltip") |

No new files are created by this phase. `web/src/hooks/use-me.ts` and `web/src/hooks/use-permissions.ts` are **consumed, not modified** — see Shared Patterns.

## Pattern Assignments

### `backend/src/main/java/com/lexcv/controllers/AuthController.java` (controller, request-response)

**Analog:** itself — this method already establishes the exact pattern needed; extend, don't invent a new one.

**Imports** (lines 1-26): no new imports required. `TenantRepository` is already imported (line 12) and already injected as a field:
```java
import com.lexcv.repositories.TenantRepository;
...
private final TenantRepository tenantRepository;
```

**Core pattern to extend** (`getMe`, lines 163-172):
```java
userRepository.findById(principal.getUserId()).ifPresent(u -> {
    response.setNome(u.getNome());
    response.setTelefone(u.getTelefone());
    response.setAvatar_url(u.getAvatarUrl());
});

tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {
    response.setTenant_nome(t.getNome());
    response.setTenant_logo_data_url(t.getLogoDataUrl());
});
```
Add two more `response.setXxx(...)` calls inside the **existing** `tenantRepository.findById(...).ifPresent(t -> {...})` lambda (per CONTEXT.md — do not add a second `.ifPresent` block, do not add a second query):
```java
tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {
    response.setTenant_nome(t.getNome());
    response.setTenant_logo_data_url(t.getLogoDataUrl());
    response.setTenant_plano(t.getPlano() != null ? t.getPlano().name() : null);
    response.setTenant_limite_utilizadores(t.getLimiteUtilizadores());
});
```
Field source types, confirmed from `backend/src/main/java/com/lexcv/models/Tenant.java`:
- `getPlano()` → `TenantPlano` enum (`STARTER`, `STANDARD`, `ENTERPRISE`; `models/TenantPlano.java`) — no existing precedent anywhere in the codebase for serializing this enum to JSON, so `.name()` (→ plain String) is the safe choice, matching `MeResponse.tenant_plano?: string` locked by CONTEXT.md and matching the all-String convention of the sibling `tenant_*` fields already in `UserResponse`.
- `getLimiteUtilizadores()` → `Integer`, nullable by design (`Tenant.java:39-43` doc comment: "null = sem limite"). Assign directly, no ternary needed — `response.setTenant_limite_utilizadores(t.getLimiteUtilizadores())` naturally passes `null` through, matching `MeResponse.tenant_limite_utilizadores?: number | null`.

**Auth/Guard pattern:** none needed — no method in `AuthController` carries `@PreAuthorize` (confirmed by reading the full file); `/auth/me` is gated only by the `Authentication`/`UserPrincipal` `instanceof` check already at lines 148-151, unchanged by this phase. This matches CONTEXT.md's point that `tenant_limite_utilizadores` goes to every authenticated role, not just ADMIN — the role gate lives in the frontend (`hasUsersManage` tab visibility), not here.

**Error handling pattern:** none needed — `ifPresent` silently no-ops if the tenant row is missing, identical to the existing `tenant_nome`/`tenant_logo_data_url` behavior. No new exception path.

---

### `backend/src/main/java/com/lexcv/dtos/UserResponse.java` (model/DTO, transform)

**Analog:** itself, full file (26 lines):
```java
package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private UUID tenant_id;
    private String nome;
    private String email;
    private Set<String> roles;
    private Set<String> permissions;
    private String avatar_url;
    private String telefone;
    private Boolean ativo;
    private String tenant_nome;
    private String tenant_logo_data_url;
}
```
**Pattern:** field names are snake_case, matching the JSON wire format directly (no `@JsonProperty` anywhere in this class — Jackson serializes by field name by default). Add, after line 25:
```java
private String tenant_plano;
private Integer tenant_limite_utilizadores;
```
`@Data`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor` (lines 10-13) auto-generate getters/setters/builder methods for the new fields — no manual boilerplate, and `AuthController`'s `response.setTenant_plano(...)` call (above) resolves against the Lombok-generated setter automatically.

---

### `web/src/types/auth.ts` (model/type, transform)

**Analog:** itself, `MeResponse` interface (lines 17-29):
```typescript
export interface MeResponse {
  id: string;
  tenant_id: string;
  nome: string;
  email: string;
  roles: Role[];
  avatar_url?: string;
  telefone?: string;
  ativo?: boolean;
  permissions: string[];
  tenant_nome?: string;
  tenant_logo_data_url?: string;
}
```
**Pattern:** add, after line 28, following the exact optionality/naming convention of the line directly above (locked verbatim by `118-CONTEXT.md` line 21):
```typescript
  tenant_plano?: string;
  tenant_limite_utilizadores?: number | null;
}
```
No other frontend file needs a type change — `web/src/hooks/use-me.ts` types its query as `apiFetch<MeResponse>("/auth/me")`, so the two new optional fields flow through automatically to every consumer (structural typing, zero code change in the hook itself).

---

### `web/src/app/(dashboard)/settings/page.tsx` — `UserManagementTab` (component)

This single file has three independent sub-patterns. Current relevant line ranges (re-read fresh, may have drifted slightly from CONTEXT.md/UI-SPEC's line numbers): component starts at line 179; `CardHeader` for the user-list Card at lines 351-365; `handleFormSubmit` catch block at lines 292-299; existing Tooltip usage at lines 462-474 (Edit) and 476-488 (Delete).

#### 1. Self-fetching hook composition (sub-pattern: request-response)

**Analog:** itself — the component already self-fetches two independent queries rather than receiving them as props (lines 179-193):
```tsx
function UserManagementTab({ currentUserId }: { currentUserId?: string }) {
  const { data: users, isLoading } = useAdminUsers();

  const [editingUser, setEditingUser] = React.useState<Partial<MockUser> | null>(null);
  ...
  // Load all system permissions to display in custom permissions overrides
  const { data: rbacData } = useAdminRbac();
  const systemPermissions = rbacData?.systemPermissions || [];
```
**Pattern to follow:** add a third self-fetched hook the same way, e.g. `const { data: me } = useMe();` — import `useMe` from `@/hooks/use-me` (not currently imported inside this file; only `usePermissions` is imported at line 25, used by the parent `SettingsPage`, not by `UserManagementTab`). Per `web/src/hooks/use-me.ts`, `queryKey: ["auth", "me"]` with `staleTime: 60_000` — TanStack Query dedupes against the parent's already-mounted `usePermissions()` call, so this is not a second network round-trip. Do **not** prop-drill `me`/`tenant_limite_utilizadores` from `SettingsPage` into `UserManagementTab` — CONTEXT.md and the existing convention above both rule this out.

`X` (active count) needs no new fetch at all — derive inline from the already-destructured `users`:
```tsx
const activeCount = users?.filter((u) => u.ativo === true).length ?? 0;
```
Note the deliberate strictness: this differs from the table's own display convention at line 448 (`user.ativo !== false`, which treats `undefined` as active for the "Ativo" badge). `MockUser.ativo` is typed `ativo?: boolean` (`web/src/server/mock-db.ts:38`) — the count must use `=== true` / `.filter((u) => u.ativo)`, not the table's `!== false`, per CONTEXT.md Interaction Note 4 (mirrors backend's `countByTenantIdAndAtivoTrue` semantics exactly).

#### 2. Disabled Button + Tooltip composition (sub-pattern: event-driven)

No single existing analog combines a real `disabled` shadcn `Button` with a working `Tooltip` — this combination is new to the codebase (see "No Analog Found" below). Two partial analogs must be combined:

**Analog A — Tooltip JSX shape, already in this exact file** (lines 462-474, Edit button):
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <Button
      variant="ghost"
      aria-label="Editar"
      onClick={() => handleEditClick(user)}
      className="h-8 w-8 p-0 text-slate-500 hover:text-blue-500 dark:text-slate-400 dark:hover:text-blue-400"
    >
      <Edit className="h-4 w-4" />
    </Button>
  </TooltipTrigger>
  <TooltipContent>Editar</TooltipContent>
</Tooltip>
```
This shape only works because the wrapped `Button` is never `disabled`. `buttonVariants` bakes in `disabled:pointer-events-none` (`web/src/components/ui/button.tsx:8`), so copying this shape verbatim with `disabled` added to the `Button` would silently produce a dead tooltip — this is a **documented, previously-deferred bug class** in this codebase (`.planning/PROJECT.md` v2.13 decision log: *"`Tooltip` num botão `disabled` (não dispara) deixados como dívida documentada, não corrigidos"*, Phase 102). This phase is the first to actually fix it rather than defer it.

**Analog B — the actual working technique, `web/src/app/(dashboard)/pareceres/[id]/page.tsx` lines 302-311:**
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <span
      tabIndex={0}
      aria-label={versaoTooltipLabel(versao, index)}
      className="h-2.5 w-2.5 rounded-full shrink-0 bg-slate-400 dark:bg-slate-500"
    />
  </TooltipTrigger>
  <TooltipContent>{versaoTooltipLabel(versao, index)}</TooltipContent>
</Tooltip>
```
This is the only place in the codebase where `TooltipTrigger asChild` targets a plain focusable `<span tabIndex={0}>` instead of an interactive element directly. It wraps a decorative dot (not a disabled button), but it is the concrete precedent for "put a focusable non-disabled element between `TooltipTrigger` and the thing that can't itself receive focus/hover."

**Combined pattern (illustrative — exact JSX is Claude's discretion per CONTEXT.md, but the two structural pieces above are what to copy):**
```tsx
{atLimite ? (
  <Tooltip>
    <TooltipTrigger asChild>
      <span tabIndex={0}>
        <Button
          disabled
          className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
        >
          <Plus className="h-4 w-4" />
          Novo Utilizador
        </Button>
      </span>
    </TooltipTrigger>
    <TooltipContent>
      Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga.
    </TooltipContent>
  </Tooltip>
) : (
  <Button
    onClick={handleCreateClick}
    className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
  >
    <Plus className="h-4 w-4" />
    Novo Utilizador
  </Button>
)}
```
The `<Button>` itself stays natively `disabled` (submission blocked at the DOM level, independent of the tooltip) — only the `<span tabIndex={0}>` wrapper is the actual `TooltipTrigger` target, so hover/focus still reaches it. `TooltipProvider` is already mounted once at the app root with `delayDuration={700}` (`web/src/app/providers.tsx:30`) — no new provider needed. `Tooltip`/`TooltipTrigger`/`TooltipContent` are already imported in this file (line 44).

**Insertion point** — current `CardHeader` (lines 351-365):
```tsx
<CardHeader className="flex flex-row items-center justify-between space-y-0">
  <div>
    <CardTitle className="text-xl font-semibold">Utilizadores Registados</CardTitle>
    <CardDescription>
      Lista de profissionais com credenciais de acesso ao sistema LexCV.
    </CardDescription>
  </div>
  <Button
    onClick={handleCreateClick}
    className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
  >
    <Plus className="h-4 w-4" />
    Novo Utilizador
  </Button>
</CardHeader>
```
The bare `<Button onClick={handleCreateClick}>...</Button>` block is what gets replaced by a `flex flex-col items-end gap-2` wrapper containing the counter `<span>` above the (conditionally tooltip-wrapped) `Button`, per UI-SPEC Interaction Note 6.

**Copy strings** (from `118-UI-SPEC.md`, verbatim, do not rephrase):
- Under limit: `"{X}/{Y} utilizadores"`
- No limit: `"{X} utilizadores"`
- At limit: `"{X}/{Y} utilizadores · limite atingido"` — the `·` separator matches the existing precedent at `web/src/app/(dashboard)/clientes/[id]/page.tsx:1536`: `{formatDocumentoSize(tamanho)} · {formatDocumentoDate(criadoEm)}`
- Tooltip: `"Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga."`

#### 3. Toast prefix-strip generalization (sub-pattern: transform / error handling)

**Analog:** itself — the exact code to fix, `handleFormSubmit` catch block (lines 292-299):
```tsx
} catch (err: unknown) {
  const msg =
    err instanceof Error
      ? err.message.replace("API 400: ", "")
      : "Erro ao gravar dados.";
  setMessage({ text: msg || "Erro ao gravar dados.", type: "error" });
  toast.error(msg || "Erro ao gravar dados.");
}
```
**Fix:** replace the literal `.replace("API 400: ", "")` with a regex that strips any 3-digit status prefix:
```tsx
err.message.replace(/^API \d{3}: /, "")
```
**Source of the wire format being stripped** — `web/src/lib/api.ts:47`:
```typescript
throw new Error(`API ${res.status}: ${errorMessage}`);
```
(and the auto-toast at `api.ts:44`, `toast.error(\`Erro ${res.status}: ${errorMessage}\`)`, which is already correctly generic and needs no change — only this component's own **local**, duplicate toast has the hardcoded-400 bug).

Without this fix, a 409 (limit reached) would render this component's local toast as the raw `"API 409: Limite de utilizadores atingido..."` instead of the clean backend sentence, per `118-UI-SPEC.md`'s Copywriting Contract for the error state.

**Related but out of scope:** the identical bug (`err.message.replace("API 400: ", "")`) also exists independently at `web/src/components/profile/user-password-form.tsx:62`. Not listed in `118-CONTEXT.md`'s file list, so not touched by this phase — noted here only as evidence this is a copy-pasted pattern, not a one-off, in case the planner wants to flag it for a future cleanup phase.

---

## Shared Patterns

### Tenant-level data on `/auth/me` (backend DTO + frontend type pairing)
**Source:** `AuthController.java:169-172` + `UserResponse.java:24-25` + `web/src/types/auth.ts:27-28`
**Apply to:** all three backend/frontend files touched by this phase — this is the single established convention for "add a tenant-scoped field visible to every authenticated session," and all three files must be extended together, in lockstep, using identical field names (`tenant_plano`, `tenant_limite_utilizadores`) across Java and TypeScript.

### `TooltipProvider` already globally mounted
**Source:** `web/src/app/providers.tsx:30`
```tsx
<TooltipProvider delayDuration={700}>{children}</TooltipProvider>
```
**Apply to:** `settings/page.tsx` — no new provider needed; `Tooltip`/`TooltipTrigger`/`TooltipContent` already imported (line 44) and already used twice in this same file.

### `disabled:pointer-events-none` baked into `Button` — blocks naive Tooltip+disabled composition
**Source:** `web/src/components/ui/button.tsx:8`
```
"inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-[color,box-shadow] disabled:pointer-events-none disabled:opacity-50 ..."
```
**Apply to:** the `CardHeader` Button+Tooltip composition — must use the `<span tabIndex={0}>` wrapper technique (see Pattern Assignments §2), not a bare `TooltipTrigger asChild` around the `Button` itself.

### Two-layer enforcement — frontend indicator mirrors, backend 409 stays authoritative
**Source:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:89-99` (`limiteUtilizadoresExcedido` — unchanged by this phase, still the sole authoritative check) + `web/src/lib/api.ts:26-47` (generic auto-toast, already correct) + `settings/page.tsx:292-299` (local toast needing the regex fix above)
```java
private Optional<ResponseEntity<?>> limiteUtilizadoresExcedido(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant != null && tenant.getLimiteUtilizadores() != null) {
        long utilizadoresAtivos = userRepository.countByTenantIdAndAtivoTrue(tenantId);
        if (utilizadoresAtivos >= tenant.getLimiteUtilizadores()) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Limite de utilizadores atingido para o vosso plano.")));
        }
    }
    return Optional.empty();
}
```
**Apply to:** the frontend indicator/disable logic must never be treated as the enforcement mechanism — it is UX-only. This mirrors the exact same architectural pattern CLAUDE.md documents for RBAC (`hasScopedPermission` mirrors `@PreAuthorize`, "both layers must agree"), just applied to a 409-on-write instead of a 403-on-request.

### Sonner toast wrapper
**Source:** `web/src/hooks/use-toast.ts:19-29`
```typescript
function toast(message: React.ReactNode, options?: ToastOptions) {
  return sonnerToast(message, options)
}
toast.success = (message, options) => sonnerToast.success("Sucesso", { description: message, ...options })
toast.error = (message, options) => sonnerToast.error("Erro", { description: message, ...options })
```
**Apply to:** no change needed — already imported and used throughout `settings/page.tsx` (`import { toast } from "@/hooks/use-toast";`, line 35). Nothing new to wire up.

## No Analog Found

| File / Element | Role | Data Flow | Reason |
|-----------------|------|-----------|--------|
| The `"{X}/{Y} utilizadores"` ratio-counter widget itself (3-way conditional copy + weight/color state) | component (inline) | request-response | No existing UI in this codebase renders a derived "count vs. limit" text indicator. Nearest conceptual (not visual) precedent: `UserManagementTab` already combines two independently-fetched queries in one component (`useAdminUsers()` + `useAdminRbac()`), which is the precedent for adding `useMe()` as a third — but the ratio/limit-copy logic itself has no prior art. |
| Disabled shadcn `Button` wrapped in a functioning `Tooltip` | component (inline) | event-driven | Zero precedent combining an actually-`disabled` `Button` with a firing `Tooltip` anywhere in the app. Previously hit and explicitly deferred as known debt in Phase 102 (`.planning/PROJECT.md` v2.13 decision log). Must be assembled from two partial analogs — see Pattern Assignments §2. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/` (AuthController, AdminController), `backend/src/main/java/com/lexcv/dtos/` (UserResponse), `backend/src/main/java/com/lexcv/models/` (Tenant, TenantPlano), `web/src/types/`, `web/src/hooks/` (use-me, use-permissions, use-admin, use-toast, use-dashboard-kpis), `web/src/app/(dashboard)/settings/`, `web/src/app/(dashboard)/pareceres/[id]/`, `web/src/app/(dashboard)/clientes/[id]/`, `web/src/components/ui/` (button, tooltip), `web/src/app/providers.tsx`, `web/src/lib/api.ts`, `web/src/server/mock-db.ts`, `web/src/components/profile/user-password-form.tsx`, `.planning/PROJECT.md`
**Files scanned:** ~20 (full reads: AuthController.java, UserResponse.java, Tenant.java, TenantPlano.java, auth.ts, settings/page.tsx, use-me.ts, use-permissions.ts, use-admin.ts, use-toast.ts, use-dashboard-kpis.ts, button.tsx, tooltip.tsx, providers.tsx, api.ts; targeted reads/greps: AdminController.java, pareceres/[id]/page.tsx, clientes/[id]/page.tsx, mock-db.ts, user-password-form.tsx, PROJECT.md)
**Pattern extraction date:** 2026-07-29
