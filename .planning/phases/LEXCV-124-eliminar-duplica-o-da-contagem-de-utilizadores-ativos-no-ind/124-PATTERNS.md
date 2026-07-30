# Phase 124: Eliminar Duplicação da Contagem de Utilizadores Ativos no Indicador de Limite - Pattern Map

**Mapped:** 2026-07-30
**Files analyzed:** 9 (4 backend, 5 frontend)
**Analogs found:** 9 / 9 — every file already carries the exact Phase 118 precedent to extend (this phase is a same-file continuation, not a new pattern)

**Note on scope:** this is a technical-debt closure phase (see `124-CONTEXT.md`), not a research-backed feature phase — there is no `RESEARCH.md`. The single canonical precedent is `118-01-PLAN.md`/`118-02-PLAN.md` (how `tenant_plano`/`tenant_limite_utilizadores` were added). All file states below were read directly from the current repository (not from the old plan docs, which have drifted slightly — see note under `web/src/types/auth.ts`), so line numbers are ground truth as of 2026-07-30.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/controllers/AuthController.java` | controller | request-response | itself — `getMe()` lines 202-233, `ifPresent` block lines 225-230 (Phase 118's own addition) | exact (self, extend existing block) |
| `backend/src/main/java/com/lexcv/dtos/UserResponse.java` | model (DTO) | request-response | itself — full file, 28 lines (Phase 118's own addition) | exact (self, append field) |
| `backend/src/main/java/com/lexcv/repositories/UserRepository.java` | repository | CRUD (aggregate count) | itself — `countByTenantIdAndAtivoTrue` line 38 (Phase 117), already reused by 2 consumers | exact — **confirm-only, zero modification** |
| `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java` → new sibling (e.g. `AuthControllerGetMeUtilizadoresAtivosTest.java`) | test | request-response | `AuthControllerGetMeTenantPlanoTest.java` (full file, 139 lines) — literal Phase 118 precedent for the same method | exact |
| `web/src/types/auth.ts` | model (types) | request-response | itself — `MeResponse` lines 18-32 (Phase 118's own addition) | exact (self, append field) |
| `web/src/hooks/use-me.ts` | hook | request-response | itself — full file, 16 lines | exact — **confirm-only, zero modification** |
| `web/src/app/(dashboard)/settings/page.tsx` (`UserManagementTab`) | component | CRUD (tab) / request-response (counter) | itself — derivation lines 196-211, render lines 391-400 | exact |
| `web/scripts/verify-limite-utilizadores-indicator.mjs` | test (gate script) | transform (static source-text assertions) | itself — `contagem-estrita` assertion lines 114-121 | exact — **must edit in place, not bypass (D-04)** |
| `web/package.json` | config | n/a | itself — `scripts` block, entry already registered at line 12 | exact — **confirm-only, zero modification** |

---

## Pattern Assignments

### 1. `backend/src/main/java/com/lexcv/controllers/AuthController.java` (controller, request-response)

**Analog:** itself — this is literally the block Phase 118 created for this exact purpose; Phase 124 extends it a third time.

**Imports** (lines 1-28, unchanged, no new import needed — `UserRepository` is already a constructor field and already imported):
```java
package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ChangePasswordRequest;
import com.lexcv.dtos.LoginRequest;
import com.lexcv.dtos.LoginResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
...
```

**Constructor field order** (lines 35-38 — `@RequiredArgsConstructor`, do not reorder):
```java
private final UserRepository userRepository;
private final TenantRepository tenantRepository;
private final JwtTokenProvider tokenProvider;
private final PasswordEncoder passwordEncoder;
```

**Core pattern — current `getMe()` in full** (lines 202-233, this IS the post-Phase-118 state, already containing the two fields the previous phase added):
```java
@GetMapping("/me")
public ResponseEntity<?> getMe() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autorizado"));
    }

    UserResponse response = UserResponse.builder()
            .id(principal.getUserId())
            .tenant_id(principal.getTenantId())
            .nome(principal.getNome() != null ? principal.getNome() : principal.getUsername())
            .email(principal.getEmail())
            .roles(principal.getRoles())
            .permissions(principal.getPermissions())
            .ativo(true)
            .build();

    userRepository.findById(principal.getUserId()).ifPresent(u -> {
        response.setNome(u.getNome());
        response.setTelefone(u.getTelefone());
        response.setAvatar_url(u.getAvatarUrl());
    });

    tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {
        response.setTenant_nome(t.getNome());
        response.setTenant_logo_data_url(t.getLogoDataUrl());
        response.setTenant_plano(t.getPlano() != null ? t.getPlano().name() : null);
        response.setTenant_limite_utilizadores(t.getLimiteUtilizadores());
    });

    return ResponseEntity.ok(response);
}
```

**Exact line to add** (D-01, inside the same `ifPresent` lambda, after `setTenant_limite_utilizadores`, i.e. between current lines 229 and 230):
```java
response.setTenant_utilizadores_ativos(userRepository.countByTenantIdAndAtivoTrue(t.getId()));
```
Use `t.getId()` (not a second read of `principal.getTenantId()`) — this mirrors the exact call-site style already used by the other 2 consumers of this method: `AdminController.java:122` (`userRepository.countByTenantIdAndAtivoTrue(tenantId)`) and `PlatformAdminController.java:198` (`userRepository.countByTenantIdAndAtivoTrue(tenant.getId())`). `t.getId()` and `principal.getTenantId()` are guaranteed equal here (`t` came from `tenantRepository.findById(principal.getTenantId())`), so either is safe against T-118-03 (no tenant id ever read from the request) — `t.getId()` is simply the more locally-idiomatic choice matching the sibling controller.

**Important nuance vs. Phase 118's "zero new queries" framing:** this line IS a new query call (`userRepository.countByTenantIdAndAtivoTrue`) — CONTEXT.md explicitly authorizes this ("aqui há 1 query nova — a própria contagem — mas reutiliza um método já existente, não escreve SQL novo"). Do not try to satisfy a literal "zero new queries" gate here; the gate to preserve is "exactly one call to `tenantRepository.findById`" (unchanged) and "reuse `countByTenantIdAndAtivoTrue`, never write new SQL" (satisfied by calling the existing repository method).

**Prohibitions carried over unchanged from Phase 118** (still binding, `AuthController.java` has zero `@PreAuthorize` today and this phase adds none):
- No new endpoint, no new controller, no new DTO.
- No second `tenantRepository.findById(...)` / no second `.ifPresent(...)` block.
- No `@PreAuthorize` added to `getMe` or any `AuthController` method.
- Do not touch `AdminController.java` or `limiteUtilizadoresExcedido` — that 409 gate is out of scope and its test (`AdminControllerLimiteUtilizadoresTest.java`) must stay green untouched (D-04).

---

### 2. `backend/src/main/java/com/lexcv/dtos/UserResponse.java` (model/DTO, request-response)

**Analog:** itself, full file (Phase 118's own addition already present):
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
    private String tenant_plano;
    private Integer tenant_limite_utilizadores;
}
```

**Exact field to append** (after `tenant_limite_utilizadores`, same snake_case sibling convention, no `@JsonProperty` — Jackson serializes by field name, as already established):
```java
private Long tenant_utilizadores_ativos;
```

**Typing gotcha (concrete, not abstract):** `UserRepository.countByTenantIdAndAtivoTrue` returns primitive `long` (line 38 of `UserRepository.java`). The DTO field must be the boxed wrapper `Long` (not `Integer`, not primitive `long`) so that `response.setTenant_utilizadores_ativos(userRepository.countByTenantIdAndAtivoTrue(t.getId()))` autoboxes directly with zero cast. `Integer` would not compile without an explicit `(int)` cast (and would risk silent truncation); primitive `long` would break the DTO's established "boxed, defaults to `null` when the `ifPresent` block never runs" contract shared by `tenant_plano`/`tenant_limite_utilizadores`. `Long` is the only type consistent with both the repository signature and the sibling-field null-safety convention.

---

### 3. `backend/src/main/java/com/lexcv/repositories/UserRepository.java` (repository, CRUD/aggregate) — confirm-only

**Exact signature to reuse, verbatim** (line 38, plus its doc comment, lines 32-37 — do not modify this file):
```java
// Phase 117 (limite de utilizadores por tenant): UNICA fonte de verdade para "utilizadores
// ativos de um tenant" no codebase. Consumida por AdminController.limiteUtilizadoresExcedido
// (chamado a partir de createUser e de updateUser, na reativação false -> true — ver CR-01 em
// 117-REVIEW.md) para aplicar Tenant.limiteUtilizadores; as Phases 120 (consola de tenants) e
// 122 (relatório de utilização) reutilizam este mesmo método (Success Criteria 4 da fase) —
// não duplicar esta contagem noutro sítio.
long countByTenantIdAndAtivoTrue(UUID tenantId);
```
Phase 124 makes this the **3rd** consumer (after `AdminController.java:122` and `PlatformAdminController.java:198`). The existing doc comment's "não duplicar esta contagem noutro sítio" instruction is exactly what this phase satisfies — worth echoing in the new call site's comment in `AuthController.java`.

---

### 4. New test file: `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMe*Test.java` (test, request-response)

**Analog:** `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java`, full file (139 lines) — this is the literal Phase 118 precedent for the exact same method (`getMe()`), and the strongest possible analog in this codebase. Recommend a **new sibling file** (e.g. `AuthControllerGetMeUtilizadoresAtivosTest.java`) rather than extending the existing one in place — matches this codebase's convention of one feature-scoped test class per `AuthController` concern (`AuthControllerLoginLockoutTest`, `AuthControllerTenantSuspensoTest`, `AuthControllerGetMeTenantPlanoTest` each own one slice; the existing class's Javadoc and name are scoped to "TenantPlano" and would become misleading if silently grown to cover a third, unrelated field).

**Header / mocks / helpers to copy verbatim** (lines 1-69):
```java
package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantPlano;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerGetMeTenantPlanoTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant() {
        UserPrincipal principal = UserPrincipal.builder().userId(USER_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AuthController novoController() {
        return new AuthController(userRepository, tenantRepository, tokenProvider, passwordEncoder);
    }
```

**One representative test case to copy the shape of** (lines 71-84 — the numeric/happy-path case; note the existing suite already stubs `userRepository.findById(USER_ID)` as `Optional.empty()` for the strict-stubs `MockitoExtension` requirement — the new test needs an additional stub, `when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(...)`, in every case, following the exact idiom already used in `AdminControllerLimiteUtilizadoresTest.java:99` (`when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L);`)):
```java
@Test
void getMe_comPlanoELimiteNumericos_devolveAmbosNoContrato() {
    autenticarComoPrincipalDoTenant();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
            Tenant.builder().id(TENANT_ID).plano(TenantPlano.STANDARD).limiteUtilizadores(5).build()));

    ResponseEntity<?> response = novoController().getMe();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    UserResponse body = (UserResponse) response.getBody();
    assertEquals("STANDARD", body.getTenant_plano());
    assertEquals(5, body.getTenant_limite_utilizadores().intValue());
}
```

**Non-regression case to mirror** (lines 116-138 — the pattern for "sibling fields unchanged + query-count assertion"; the new test should add a `verify(userRepository, times(1)).countByTenantIdAndAtivoTrue(TENANT_ID)` alongside the existing `verify(tenantRepository, times(1)).findById(TENANT_ID)`):
```java
@Test
void getMe_naoQuebraCamposIrmaosEConsultaTenantApenasUmaVez() {
    autenticarComoPrincipalDoTenant();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
            Tenant.builder()
                    .id(TENANT_ID)
                    .nome("Escritorio X")
                    .logoDataUrl("data:image/png;base64,AAA")
                    .plano(TenantPlano.STARTER)
                    .limiteUtilizadores(3)
                    .build()));

    ResponseEntity<?> response = novoController().getMe();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    UserResponse body = (UserResponse) response.getBody();
    assertEquals("Escritorio X", body.getTenant_nome());
    assertEquals("data:image/png;base64,AAA", body.getTenant_logo_data_url());
    assertEquals("STARTER", body.getTenant_plano());
    assertEquals(3, body.getTenant_limite_utilizadores().intValue());
    verify(tenantRepository, times(1)).findById(TENANT_ID);
}
```

**Class-level Javadoc style** (lines 31-44) — follow the same structure (what is proved, why, and the "no MockMvc/`@SpringBootTest` in this codebase" note) for the new file's own Javadoc.

---

### 5. `web/src/types/auth.ts` (model/types, request-response)

**Analog:** itself, full file (32 lines — already contains Phase 118's two fields):
```typescript
// PLATAFORMA_ADMIN e o papel de plataforma introduzido na Phase 119, deliberadamente sem permissoes com scope.
export type Role = "ADMIN" | "TECNICO" | "ADVOGADO" | "ASSISTENTE" | "PLATAFORMA_ADMIN";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: {
    id: string;
    nome: string;
  };
  access_token?: string;
  refresh_token?: string;
}

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
  tenant_plano?: string | null;
  tenant_limite_utilizadores?: number | null;
}
```

**Drift note:** `118-02-PLAN.md`'s `<interfaces>` block shows this file's *pre*-Phase-118 shape, and its `<action>` text even specifies `tenant_plano?: string;` (without `| null`) — but the actual shipped code (confirmed by direct read above) uses `tenant_plano?: string | null;` for both new fields. Use the **actual current file** as the precedent, not the old plan text.

**Exact line to append** (after `tenant_limite_utilizadores`, following the more recent/more structurally-similar numeric sibling's `| null` convention rather than the older `tenant_nome`/`tenant_logo_data_url` fields, which have no `| null`):
```typescript
tenant_utilizadores_ativos?: number | null;
```

---

### 6. `web/src/hooks/use-me.ts` (hook, request-response) — confirm-only

**Analog:** itself, full file (16 lines, unchanged from Phase 118 — no modification expected this phase either):
```typescript
import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { MeResponse } from "@/types/auth";

export function useMe() {
  const enabled = typeof window !== "undefined" ;

  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: () => apiFetch<MeResponse>("/auth/me"),
    enabled,
    staleTime: 60_000,
  });
}
```
Confirmed: this hook types the query as `apiFetch<MeResponse>`, so it inherits the new field purely by structural typing once `MeResponse` gains it — exactly the "hook not modified, only the type changes" precedent CONTEXT.md's Integration Points section anticipates.

---

### 7. `web/src/app/(dashboard)/settings/page.tsx` — `UserManagementTab` (component, CRUD tab / request-response counter)

**Imports already present, unchanged** (lines 26, 28-29):
```typescript
import { useMe } from "@/hooks/use-me";
import {
  useAdminUsers,
  useAdminRbac
} from "@/hooks/use-admin";
```

**Core pattern — current derivation block, in full** (lines 196-211, this is exactly what D-02 targets):
```typescript
  // Indicador "X/Y utilizadores" (Phase 118 PLAN-03) — useMe() dedupe pela cache
  // partilhada ["auth","me"], nao e um segundo pedido de rede. X usa igualdade
  // estrita (=== true) para espelhar countByTenantIdAndAtivoTrue do backend;
  // deliberadamente diferente da convencao de exibicao do badge "Ativo" da
  // tabela mais abaixo, que trata utilizadores sem o campo definido como ativos.
  const { data: meData } = useMe();
  const activeUserCount = users?.filter((u) => u.ativo === true).length ?? 0;
  const tenantUserLimit = meData?.tenant_limite_utilizadores ?? null;
  const atUserLimit =
    tenantUserLimit !== null && activeUserCount >= tenantUserLimit;
  const userCountLabel =
    tenantUserLimit === null
      ? `${activeUserCount} utilizadores`
      : atUserLimit
        ? `${activeUserCount}/${tenantUserLimit} utilizadores · limite atingido`
        : `${activeUserCount}/${tenantUserLimit} utilizadores`;
```

**Exact one-line change** (D-02 — only the `activeUserCount` assignment changes; `tenantUserLimit`/`atUserLimit`/`userCountLabel` are untouched, preserving D-03's byte-identical visual states):
```typescript
const activeUserCount = meData?.tenant_utilizadores_ativos ?? 0;
```
This is a direct parallel to the very next line's existing idiom (`meData?.tenant_limite_utilizadores ?? null`) — same optional-chaining/fallback shape, just `?? 0` instead of `?? null` because a missing/undefined count should read as "0 utilizadores" (fail-safe: never show a blank or NaN counter), not "no limit".

**Comment block must be updated, not just left stale:** the existing 5-line comment (reproduced above) explains *why* the client recomputes the count ("X usa igualdade estrita... para espelhar countByTenantIdAndAtivoTrue do backend"). After this phase, the client no longer recomputes anything — it consumes the backend's own count directly. Leaving the old comment in place would document a rationale that no longer describes the code. Update it to state that `activeUserCount` now comes straight from `tenant_utilizadores_ativos` on `GET /auth/me` (Phase 124), and that `useAdminUsers()`'s `users` list is kept only for the management table below.

**Grep-confirmed fact relevant to D-02's "Claude's Discretion" question:** `.ativo === true` appears **exactly once** in this file (line 202, this exact derivation) — no other code path depends on it. `user.ativo !== false` appears 4 times elsewhere (form defaults, table badge, edit-form toggle) and is a **separate, intentionally-different** convention that must NOT be touched. Safe to delete the `.filter((u) => u.ativo === true)` computation entirely rather than leave it as unused dead code — **but see the gate-script conflict below before doing so.**

**Render block — unaffected, shown for non-regression proof only** (lines 391-400, `CardHeader`; D-03 requires this stays byte-identical, no change needed here since only the `activeUserCount` variable feeding it changes, not the JSX):
```tsx
<div className="flex flex-col items-end gap-2">
  <span
    className={
      atUserLimit
        ? "text-xs font-semibold text-red-600 dark:text-red-400"
        : "text-xs text-slate-500 dark:text-slate-400"
    }
  >
    {userCountLabel}
  </span>
  {atUserLimit ? (
    <Tooltip>
      <TooltipTrigger asChild>
        <span tabIndex={0}>
```

**Cross-cutting integration confirmed (no new work needed):** `web/src/hooks/use-admin.ts`'s `useAdminSaveUser` mutation already invalidates `["auth","me"]` on success (lines 30-35):
```typescript
onSuccess: async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
    queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
  ]);
},
```
This means creating/editing/reactivating a user already refreshes the `me` query the new counter will read from — no new invalidation logic is required for D-02. (`useAdminDeleteUser` does not invalidate `["auth","me"]`, but `handleDeleteClick` in this same file does a full `window.location.reload()` after its toast, so staleness is not a concern there either.)

**Do-not-confuse note:** `web/scripts/verify-bloqueio-rbac.mjs` also reads this same `settings/page.tsx` file (for an unrelated RBAC-gating assertion, Phase 121) — confirmed by grep it does not reference `ativo`/`activeUserCount`/`tenant_limite`/`tenant_utilizadores` in any form, so it is not affected by this phase's changes.

---

### 8. `web/scripts/verify-limite-utilizadores-indicator.mjs` (test/gate script, transform) — must edit in place

**Analog:** itself — this is the Phase 118 gate D-04 explicitly requires to stay green, updated (not bypassed) if it assumes the old payload/derivation shape.

**The one assertion that WILL break and must be rewritten** (lines 114-121, verbatim):
```javascript
{
  id: "contagem-estrita",
  descricao:
    "settings/page.tsx contem '.ativo === true' (contagem estrita, espelha countByTenantIdAndAtivoTrue) E continua a conter 'user.ativo !== false' (convencao de exibicao do badge da tabela, inalterada)",
  predicate: () =>
    settingsPage.includes(".ativo === true") &&
    settingsPage.includes("user.ativo !== false"),
},
```
If Task 7's recommended change removes `.filter((u) => u.ativo === true)` entirely (as the grep above shows is safe to do), this predicate's first half (`settingsPage.includes(".ativo === true")`) will start failing — this is **expected and correct**, not a regression to work around. Per CONTEXT.md D-04 ("Se o gate assumir a forma antiga do payload de GET /auth/me, atualizar o próprio gate para refletir o novo campo — não contorná-lo"), this assertion's `id`, `descricao`, and `predicate` must be rewritten to check for the new source instead, e.g. asserting `settingsPage.includes("meData?.tenant_utilizadores_ativos")` (or equivalent) while still requiring `user.ativo !== false` to remain present (that half of the assertion is genuinely unaffected and must keep passing).

**Assertion array shape to follow when rewriting/adding** (lines 79-158, the full array — every entry is `{ id, descricao, predicate }`, `predicate` a zero-arg function returning boolean, evaluated against `settingsPage`/`authTypes` after `stripComments()`):
```javascript
{
  id: "types-auth-tenant-limite",
  descricao:
    "web/src/types/auth.ts contem 'tenant_limite_utilizadores?: number | null;' (com '| null' explicito)",
  predicate: () =>
    authTypes.includes("tenant_limite_utilizadores?: number | null;"),
},
```
A new sibling assertion for the type file (e.g. `types-auth-tenant-utilizadores-ativos`, checking `authTypes.includes("tenant_utilizadores_ativos?: number | null;")`) should be added following this exact shape.

**PASS/FAIL harness, unchanged** (lines 160-179 — do not modify, only the `assertions` array content changes):
```javascript
let failures = 0;
for (const assertion of assertions) {
  let pass = false;
  let error = null;
  try {
    pass = assertion.predicate();
  } catch (err) {
    error = err;
  }
  if (pass) {
    console.log(`PASS ${assertion.id}`);
  } else {
    failures += 1;
    const motivo = error ? error.message : assertion.descricao;
    console.log(`FAIL ${assertion.id} — ${motivo}`);
  }
}
process.exit(failures === 0 ? 0 : 1);
```

**`stripComments()` helper, already handles both files' comment styles — reuse, do not duplicate** (lines 34-49): strips `{/* ... */}` JSX comment blocks, `/* ... */` blocks, and `//`/`*`-leading lines, before any predicate runs. Any new assertion must rely on this pre-stripped `settingsPage`/`authTypes` string, exactly like the existing 8 assertions do.

---

### 9. `web/package.json` (config) — confirm-only

**Analog:** itself, `scripts` block (lines 5-15) — the `verify:limite-utilizadores` entry is already registered from Phase 118; this phase reuses the same script name/entry, only its file's contents change:
```json
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "start": "next start",
  "lint": "eslint",
  "test": "vitest run",
  "verify:juizo-origem": "node scripts/verify-juizo-origem-roundtrip.mjs",
  "verify:limite-utilizadores": "node scripts/verify-limite-utilizadores-indicator.mjs",
  "verify:consola-tenants": "node scripts/verify-consola-tenants.mjs",
  "verify:bloqueio-rbac": "node scripts/verify-bloqueio-rbac.mjs",
  "verify:relatorio-utilizacao": "node scripts/verify-relatorio-utilizacao.mjs"
```
No new entry needed. No `dependencies`/`devDependencies` change expected (this phase does not install anything, consistent with every prior phase's zero-install discipline).

---

## Shared Patterns

### `tenant_*` snake_case DTO/type naming, zero `@JsonProperty`
**Source:** `backend/src/main/java/com/lexcv/dtos/UserResponse.java` (all 4 `tenant_*` fields) and `web/src/types/auth.ts` (`MeResponse`, same 4 fields).
**Apply to:** the new field in both files. Java field name IS the JSON key (Jackson default, no `@JsonProperty` anywhere in `UserResponse.java` — do not introduce one). `tenant_utilizadores_ativos` is consistent both locally (snake_case `tenant_*` siblings) and cross-controller (`PlatformAdminController.toSummary`, line 198, uses the same semantic word order `utilizadoresAtivos`, not `ativosUtilizadores`) — no reason to deviate from CONTEXT.md's suggested name.

### Single source of truth for "active users of a tenant"
**Source:** `backend/src/main/java/com/lexcv/repositories/UserRepository.java:38` (`countByTenantIdAndAtivoTrue`), doc comment lines 32-37.
**Apply to:** `AuthController.getMe()`. This becomes consumer #3 (after `AdminController.java:122` and `PlatformAdminController.java:198`) — the exact goal of this phase. Never re-derive this count via a `.filter()`/stream anywhere else.

### Tenant id always from the authenticated principal, never from the request
**Source:** `AuthController.java` — the only `tenantRepository.findById` call uses `principal.getTenantId()` (T-118-03 precedent, `118-01-PLAN.md` threat model). The new `countByTenantIdAndAtivoTrue` call must use `t.getId()` (derived from that same principal-scoped lookup) — never a tenant id read from a request body/param.

### Zero new authorization surface on `AuthController`
**Source:** `AuthController.java` — no method in this class carries `@PreAuthorize` today; role-based visibility of the "Utilizadores" tab is enforced entirely in the frontend (`hasUsersManage`, gating which components can even call `useMe()`/render the counter). This phase must not add one either — matches T-118-01's accepted disposition (these are non-sensitive, own-tenant commercial metadata fields, same class as the already-universal `tenant_nome`).

### React Query cache reuse — no prop-drilling, no new invalidation
**Source:** `web/src/hooks/use-me.ts` (`queryKey: ["auth", "me"]`) and `web/src/hooks/use-admin.ts:30-35` (`useAdminSaveUser`'s `onSuccess` already invalidates `["auth","me"]`). `UserManagementTab` already calls `useMe()` directly (line 201) — a cache hit, not a second network request, since `SettingsPage`'s parent already mounts `usePermissions()`/shares the same query client. No new hook, no new invalidation call needed for this phase.

### Gate script must be updated in place, never bypassed, when the contract it encodes changes
**Source:** `web/scripts/verify-limite-utilizadores-indicator.mjs`, assertion `contagem-estrita` (lines 114-121) — CONTEXT.md D-04 states this explicitly. This is the one pattern in this phase that is a **modification of an existing assertion's meaning**, not a pure addition — flag clearly in the plan so the executor does not mistake "keep the gate green" for "leave the assertion's old predicate untouched."

### Mockito controller test idiom (no MockMvc/`@SpringBootTest` in this codebase)
**Source:** `AuthControllerGetMeTenantPlanoTest.java` (full file) and `AdminControllerLimiteUtilizadoresTest.java` (stubbing idiom for `countByTenantIdAndAtivoTrue`, e.g. line 99: `when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L);`). Controller instantiated directly with `@Mock` collaborators, `SecurityContextHolder` populated manually via `UsernamePasswordAuthenticationToken(UserPrincipal..., null, List.of())` and cleared in `@AfterEach`. Apply this idiom verbatim to the new test file for Task 4.

---

## No Analog Found

None — every file in scope already has an exact, direct, same-file analog because this phase is a continuation of Phase 118's own precedent rather than new surface area.

**Explicitly off-limits (not "no analog" — actively must NOT be touched, per D-04 and the Phase 118 precedent it repeats):**

| File | Reason |
|------|--------|
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | `limiteUtilizadoresExcedido` is the sole authoritative 409 gate (Phase 117) — this phase is UI/mirror-only, must not touch it; `AdminControllerLimiteUtilizadoresTest.java` must stay green unmodified. |
| `web/src/lib/api.ts` | Generic error/toast plumbing, already correct and unrelated to this phase's data-source change. |
| `web/src/components/ui/button.tsx`, `web/src/components/ui/tooltip.tsx`, `web/src/app/providers.tsx` | Phase 118's span-wrapper tooltip technique lives here; this phase does not touch the tooltip mechanism, only the counter's data source. |
| `backend/pom.xml`, `web/package.json` `dependencies`/`devDependencies` | Zero new installs expected — same discipline as every prior phase in this milestone. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,dtos,repositories}/`, `backend/src/test/java/com/lexcv/controllers/`, `web/src/{types,hooks,app/(dashboard)/settings}/`, `web/scripts/`, `web/package.json`.
**Files scanned:** 9 target files read directly (full or targeted ranges) + 3 supporting cross-reference reads (`AdminController.java`, `PlatformAdminController.java`, `use-admin.ts`) + 2 gate-script grep checks (`verify-bloqueio-rbac.mjs` ruled out as unaffected).
**Pattern extraction date:** 2026-07-30
