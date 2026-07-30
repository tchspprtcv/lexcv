# Phase 121: Fechar Suposições de Tenant Única + Bloqueio de RBAC - Pattern Map

**Mapped:** 2026-07-29
**Files analyzed:** 5 (4 explicit Integration Points from 121-CONTEXT.md + 1 new artifact implied by its ISOL-02 decision — no RESEARCH.md exists for this phase, `research_enabled` is false, so this map is grounded directly in 121-CONTEXT.md/121-UI-SPEC.md plus fresh reads of every file they name)
**Analogs found:** 5 / 5 (4 exact; 1 partial — the `AdminController` change reuses an exact annotation *value* but has no precedent for its class+method *combination shape*; see "No Analog Found")

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` — add method-level `@PreAuthorize` to `updateRbac` (:391) | controller (authorization annotation only) | request-response (unchanged — the gate wraps the existing handler, no signature/body change) | `PlatformAdminController.java:53` for the annotation value (`@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`); **no exact precedent** for combining it with a *different* class-level annotation on the same class (confirmed via full grep of every `@PreAuthorize` in `controllers/` — see below) | partial (value: exact / combination: novel, first-of-its-kind) |
| `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` (extend) **or** a new dedicated test file — exact choice is Claude's Discretion per 121-CONTEXT.md | test | n/a | `PlatformAdminControllerTest.java`'s "Grupo B" (`novoProxyComMethodSecurity()` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`) | exact (proven, reusable AOP-proxy harness; needs one non-obvious adaptation — see nuance in Pattern Assignment 2) |
| `web/src/app/(dashboard)/settings/page.tsx` — `RbacTab` (function starts :760, Save button :860-871) | component (conditional render swap, no new route/hook) | UI state (unchanged data flow — `handleSave`/`useAdminRbac` untouched; only the header's rendered element becomes conditional) | `dashboard-shell.tsx:91` (role-check idiom) + `plataforma/page.tsx:78-89` (`isFetched` gate) + `plataforma/columns.tsx:61-82` (Tooltip + non-interactive-element `<span tabIndex={0}>` composition) + `columns.tsx:141` (`<Badge variant="outline">` precedent) + `settings/page.tsx:201` (sibling tab's self-fetch `useMe()`) | exact (121-UI-SPEC.md's Interaction Notes already pin the literal target JSX; every piece traces to a confirmed existing line) |
| `backend/src/main/java/com/lexcv/controllers/PublicController.java` + `backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java` (ISOL-01) | controller + test — **regression confirmation only, zero code change** | n/a | itself — already fixed by Phase 119's CR-02 | exact (already-closed; this phase re-reads + re-runs, never rewrites — independently reverified below) |
| ISOL-02 audit write-up (new artifact — exact filename/location Claude's Discretion, e.g. `121-ISOL02-AUDIT.md`, or folded into a plan's own `SUMMARY.md`) | docs (audit record) | n/a | `.planning/milestones/v2.11-phases/LEXCV-97-.../97-01-SUMMARY.md` (AUD-01, "Tenant-Isolation Audit of Notification Surfaces") | exact (identical verdict-table format directly reusable, same project, same kind of check) |

---

## Pattern Assignments

### 1. `backend/src/main/java/com/lexcv/controllers/AdminController.java` — add method-level `@PreAuthorize` to `updateRbac`

**Analog:** `PlatformAdminController.java:53` for the exact annotation string; the file's own class-level annotation (`:28`) for the "do not touch this" boundary; `NotificacaoController.java:69-70` and `ResourceController.java:169-170` for **annotation ordering convention**.

**Current state (confirmed by direct read):**
```java
// AdminController.java:26-30 — class-level gate, KEEP exactly as-is
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
```
```java
// AdminController.java:346 — getRbac, immediately precedes updateRbac, explicitly NOT touched
@GetMapping("/rbac")
public ResponseEntity<?> getRbac() { ... }   // still bare — inherits only the class-level hasRole('ADMIN')

// AdminController.java:391-424 — updateRbac, current state
@PutMapping("/rbac")
public ResponseEntity<?> updateRbac(@RequestBody Map<String, Object> body) {
    if (!body.containsKey("rolePermissions")) {
        return ResponseEntity.badRequest().body(Map.of("message", "Mapeamento rolePermissions é obrigatório"));
    }
    Map<?, ?> newRolePermissions = (Map<?, ?>) body.get("rolePermissions");
    for (Map.Entry<?, ?> entry : newRolePermissions.entrySet()) {
        String roleName = (String) entry.getKey();
        if ("ADMIN".equals(roleName) || PAPEL_PLATAFORMA.equals(roleName)) {
            continue;
        }
        Role role = roleRepository.findByNome(roleName).orElse(null);
        if (role != null) {
            List<?> permsList = (List<?>) entry.getValue();
            Set<Permission> permissions = new HashSet<>();
            for (Object pObj : permsList) {
                String pName = (String) pObj;
                permissionRepository.findByNome(pName).ifPresent(permissions::add);
            }
            role.setPermissions(permissions);
            roleRepository.save(role);
        }
    }
    return ResponseEntity.ok(Map.of("message", "Permissões de perfis (RBAC) atualizadas com sucesso!"));
}
```

**Target change — single added line, method body untouched:**
```java
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@PutMapping("/rbac")
public ResponseEntity<?> updateRbac(@RequestBody Map<String, Object> body) {
    // ... body identical, zero changes ...
}
```

**Ordering convention (confirmed, not a guess):** every other method-level `@PreAuthorize` in this codebase is placed **above** the mapping annotation, never below or interleaved:
```java
// NotificacaoController.java:69-70
@PreAuthorize("hasAuthority('notificacoes:view')")
@GetMapping
```
```java
// ResourceController.java:169-170
@PreAuthorize("hasAuthority('clientes:view')")
@GetMapping("/clientes")
```
Follow the identical order for `updateRbac`.

**Import:** zero new imports. `org.springframework.security.access.prepost.PreAuthorize` is already imported at `AdminController.java:17` (used today only at class level, line 28) — the method-level annotation reuses the same import.

**Optional (not required by 121-CONTEXT.md, but worth flagging for the planner):** `AdminController.java:32-51` carries a pre-existing docblock comment that already narrates this exact change as a forward reference: *"A Phase 121 (ISOL-03) ira depois fechar o endpoint PUT /rbac por inteiro a papeis de plataforma -- esta fase apenas protege o papel novo, sem antecipar esse bloqueio."* (line 50-51). This codebase has a demonstrated habit of updating a past phase's comment in place once a forward reference lands — e.g. `PublicController.java`'s current docblock literally reads *"ISOL-01 (Phase 121, já satisfeito por esta correção)"*. The executor may want to similarly annotate this comment as closed, though 121-CONTEXT.md's decisions don't mandate it.

---

### 2. Test file — proxy-based `@PreAuthorize` proof for `updateRbac`

**Analog:** `PlatformAdminControllerTest.java`'s "Grupo B" section (:209-255, plus helpers :103-134) — the only place in this codebase that proves a `@PreAuthorize` fires through a real AOP proxy rather than a bare Java call.

**Reusable helper shape, adapted for `AdminController`'s 5-arg constructor** (constructor already exists verbatim in `AdminControllerPlataformaAdminContencaoTest.java:98-100`):
```java
// AdminControllerPlataformaAdminContencaoTest.java:98-100 — already exists, reuse as-is
private AdminController novoController() {
    return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder, tenantRepository);
}

// NEW — mirrors PlatformAdminControllerTest.java:111-116 exactly, only the return type changes
private AdminController novoProxyComMethodSecurity() {
    ProxyFactory factory = new ProxyFactory(novoController());
    factory.setProxyTargetClass(true);
    factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
    return (AdminController) factory.getProxy();
}
```

**New imports required if extending `AdminControllerPlataformaAdminContencaoTest.java`** (none of these are in that file today; all five already exist, proven, in `PlatformAdminControllerTest.java`):
```java
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Arrays;                                    // for Arrays.stream in the auth helper below
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
```
(That file currently imports `java.util.List/Map/Optional/UUID` individually, not `java.util.*` — `Arrays` is genuinely new there, unlike in `AdminController.java` itself which already has the wildcard import.)

**⚠ Sharpest, most important nuance in this whole pattern map — do not blindly copy `PlatformAdminControllerTest.autenticarComoRoles`:**
```java
// PlatformAdminControllerTest.java:118-124 — the seemingly obvious helper to copy
private void autenticarComoRoles(String... roles) {
    List<SimpleGrantedAuthority> autoridades = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)          // NOTE: no "ROLE_" prefix added here
            .toList();
    SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
}
```
This helper is called as `autenticarComoRoles("ADMIN")`, which creates a raw `SimpleGrantedAuthority("ADMIN")` — **not** `"ROLE_ADMIN"`. For `PlatformAdminController`'s own tests this is harmless: every Grupo-B assertion there is a pure negative proof ("this caller is not `PLATAFORMA_ADMIN`"), and an authority that never satisfies *any* `hasRole(...)` check still correctly fails that check for the right reason in practice.

For `updateRbac`'s new tests, this matters more, because 121-CONTEXT.md explicitly asks for proof of the specific "most-specific-annotation-wins" mechanism — i.e., that a caller who **genuinely, correctly** satisfies the class-level `hasRole('ADMIN')` (holding the properly-prefixed authority `"ROLE_ADMIN"`) is *still* denied by the method-level override. If the test authenticates with unprefixed `"ADMIN"` instead, the denial is trivially true for the wrong reason (the authority never matched any `hasRole` check at all, so the test wouldn't actually distinguish "the override works" from "the security context was simply empty/wrong"). Use the fully-prefixed form instead — matching `PlatformAdminControllerTest.autenticarComoPlataformaAdmin`'s own correctly-prefixed literal:
```java
// PlatformAdminControllerTest.java:126-134 — the CORRECT prefixing precedent to follow instead
private void autenticarComoPlataformaAdmin(UUID tenantIdReservado) {
    UserPrincipal principal = UserPrincipal.builder().userId(UUID.randomUUID()).tenantId(tenantIdReservado).build();
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_PLATAFORMA_ADMIN"))));   // <- prefixed correctly
}
```

**The two tests that together constitute the "explicit proof, not an assumption by analogy" 121-CONTEXT.md calls for** (`updateRbac` never reads `SecurityContextHolder` itself, so a bare authority list — no `UserPrincipal` needed — is sufficient, simpler than `autenticarComoPlataformaAdmin`'s full principal):
```java
private void autenticarComoAuthorities(String... authorities) {
    List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
    SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
}

// Direction 1: a caller who genuinely satisfies the OLD class-level gate (hasRole('ADMIN'),
// i.e. holds "ROLE_ADMIN") is now still denied -- proves the method-level annotation REPLACES,
// not merely supplements, the class-level one for this specific method.
@Test
void updateRbac_comRoleAdminDeTenantNormalERecusadoMesmoSatisfazendoOGateDeClasse() {
    autenticarComoAuthorities("ROLE_ADMIN");
    Map<String, Object> body = Map.of("rolePermissions", Map.of("ASSISTENTE", List.of("clientes:view")));
    AdminController proxy = novoProxyComMethodSecurity();

    assertThrows(AccessDeniedException.class, () -> proxy.updateRbac(body));
    verify(roleRepository, never()).save(any());
}

// Direction 2 (the converse): a caller who does NOT satisfy the class-level hasRole('ADMIN') at
// all, but DOES hold ROLE_PLATAFORMA_ADMIN, still gets through -- proves the two annotations are
// not ANDed together (if they were, this caller would be wrongly denied too).
@Test
void updateRbac_comRolePlataformaAdminPassaOGateMesmoSemHasRoleAdmin() {
    autenticarComoAuthorities("ROLE_PLATAFORMA_ADMIN");
    when(roleRepository.findByNome("ASSISTENTE"))
            .thenReturn(Optional.of(Role.builder().id(4).nome("ASSISTENTE").build()));
    when(permissionRepository.findByNome("clientes:view"))
            .thenReturn(Optional.of(Permission.builder().id(1).nome("clientes:view").build()));
    Map<String, Object> body = Map.of("rolePermissions", Map.of("ASSISTENTE", List.of("clientes:view")));
    AdminController proxy = novoProxyComMethodSecurity();

    ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.updateRbac(body));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(roleRepository, times(1)).save(any());
}
```

**Why the existing Casos 6/7/8 are unaffected (confirmed, not assumed):** `AdminControllerPlataformaAdminContencaoTest`'s existing tests (`getRbac_naoExpoePapelDePlataforma`, `updateRbac_ignoraEntradaPlataformaAdmin`, `updateRbac_continuaAEditarPapeisDeTenant`, :209-256) call `novoController().updateRbac(...)` directly — a plain Java method call on a non-proxied instance. `@PreAuthorize` is inert reflection metadata unless a proxy/AOP interceptor evaluates it; these tests never construct a proxy, so adding the annotation cannot change their outcome. This mirrors 121-CONTEXT.md's own framing exactly.

**Naming, if a new dedicated file is chosen instead of extending the existing one:** this codebase's convention for single-concern controller test files is `<Controller><ConcernPhrase>Test.java` (`AdminControllerLimiteUtilizadoresTest`, `AdminControllerPlataformaAdminContencaoTest`, `AuthControllerLoginLockoutTest`, `AuthControllerTenantSuspensoTest`, `SetupControllerSingletonRegressaoTest`) — e.g. `AdminControllerRbacAutorizacaoTest.java` would fit that pattern.

---

### 3. `web/src/app/(dashboard)/settings/page.tsx` — `RbacTab` Save-button → Badge+Tooltip swap

**Analog (assembled from 4 confirmed precedents, all in this exact codebase):**

**(a) Role-check idiom** — `dashboard-shell.tsx:91`:
```typescript
const isPlatformAdmin = me.data?.roles?.includes("PLATAFORMA_ADMIN") ?? false;
```

**(b) `isFetched` fail-closed gate** — `plataforma/page.tsx:76-89`:
```typescript
const me = useMe();
if (!me.isFetched) {
  return null;
}
if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
  return <AccessDeniedState description="..." backHref="/dashboard" />;
}
```

**(c) Self-fetch `useMe()` within a settings-page sub-component, not prop-drilled** — `settings/page.tsx:196-201` (the sibling `UserManagementTab`, confirmed same file):
```typescript
// Indicador "X/Y utilizadores" (Phase 118 PLAN-03) — useMe() dedupe pela cache
// partilhada ["auth","me"], nao e um segundo pedido de rede.
const { data: me } = useMe();
```
Confirmed call site: `<RbacTab />` is invoked with **zero props** today (`settings/page.tsx:163`, gated only by `activeTab === "rbac" && hasRbacManage` at the parent level), so adding `useMe()` inside `RbacTab` itself requires no change to the call site or to `hasRbacManage` (`:58`) — purely additive within the function body, exactly like `UserManagementTab` already does.

**(d) Tooltip + non-interactive-element `<span tabIndex={0}>` composition, and the `variant="outline"` Badge** — `plataforma/columns.tsx:61-82` and `:141`:
```tsx
// columns.tsx:61-82 — disabled-Button case (same wrapper technique, different root cause)
<Tooltip>
  <TooltipTrigger asChild>
    <span tabIndex={0}>
      <Button type="button" size="sm" variant="ghost" aria-label="Suspender tenant" disabled className="h-9 w-9 p-0 text-slate-500">
        <Lock className="h-4 w-4" />
      </Button>
    </span>
  </TooltipTrigger>
  <TooltipContent>Não é possível suspender o tenant da plataforma (LexCV).</TooltipContent>
</Tooltip>
```
```tsx
// columns.tsx:139-143 — the exact Badge variant precedent for "this belongs to the platform"
{tenant.nome === TENANT_RESERVADO ? (
  <div className="mt-1">
    <Badge variant="outline">Plataforma</Badge>
  </div>
) : null}
```
`badge.tsx:15-16` confirms `variant="outline"` renders `"border-neutral-200 text-neutral-900 dark:border-neutral-800 dark:text-neutral-50"` — neutral, never accent/destructive-colored, matching 121-UI-SPEC.md's Color contract exactly.

**Current state at the actual edit site (confirmed, `settings/page.tsx:849-872`):**
```tsx
return (
  <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
    <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between space-y-2 sm:space-y-0">
      <div>
        <CardTitle className="text-xl font-semibold flex items-center gap-2">
          Matriz de Regras de Acesso (RBAC)
        </CardTitle>
        <CardDescription>
          Defina quais as permissões atribuídas globalmente a cada perfil profissional.
        </CardDescription>
      </div>
      <Button
        onClick={handleSave}
        disabled={saving}
        className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm flex items-center gap-2 self-start sm:self-auto"
      >
        {saving ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          <Save className="h-4 w-4" />
        )}
        Guardar Regras
      </Button>
    </CardHeader>
```

**Target (per 121-UI-SPEC.md Interaction Notes 1-4, cross-checked against the precedents above — all imports already present at the top of `settings/page.tsx`: `Badge` :44, `Tooltip/TooltipContent/TooltipTrigger` :45, `Lock` :6, `useMe` :26):**
```tsx
function RbacTab() {
  const { data: rbac, isLoading, isError, refetch } = useAdminRbac();
  const me = useMe();
  const isPlatformAdmin = me.isFetched && (me.data?.roles?.includes("PLATAFORMA_ADMIN") ?? false);
  // ...unchanged state/handlers (localRolePermissions, saving, success, error, handleSave)...

  return (
    <Card ...>
      <CardHeader ...>
        <div>{/* CardTitle/CardDescription unchanged */}</div>
        {isPlatformAdmin ? (
          <Button onClick={handleSave} disabled={saving} className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm flex items-center gap-2 self-start sm:self-auto">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Guardar Regras
          </Button>
        ) : (
          <Tooltip>
            <TooltipTrigger asChild>
              <span tabIndex={0} className="self-start sm:self-auto">
                <Badge variant="outline" className="gap-1">
                  <Lock className="h-3 w-3" />
                  Gerido pela Plataforma
                </Badge>
              </span>
            </TooltipTrigger>
            <TooltipContent>
              As regras de acesso por perfil (RBAC) passaram a ser uma configuração fixa e comum a
              toda a plataforma LexCV — já não podem ser alteradas a partir de um escritório individual.
            </TooltipContent>
          </Tooltip>
        )}
      </CardHeader>
      {/* CardContent unchanged: success/error banners, Nota Importante banner, permission matrix table */}
    </Card>
  );
}
```

**Explicit non-goals (per 121-UI-SPEC.md, cross-checked against the read source — confirmed accurate):** `hasRbacManage` (`:58`) unchanged; `handleCheckboxChange`'s `isDisabled = role === "ADMIN"` unchanged (no new disabled styling added to the matrix for non-ADMIN roles); `GET /admin/rbac` / `useAdminRbac()` call path unchanged; `handleSave`/`saving`/`success`/`error` state and banners unchanged, just unreachable via UI for a tenant ADMIN once the Button no longer renders for them.

---

### 4. ISOL-01 — `PublicController.java` / `PublicControllerTest.java` (confirmation only, zero code change)

**Analog:** itself. Both files, read in full, confirm 121-CONTEXT.md's claim exactly:

```java
// PublicController.java:29-42 — full file body, zero constructor dependencies
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {
    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        return ResponseEntity.ok(
                TenantPublicInfoResponse.builder()
                        .nome("LexCV")
                        .logoDataUrl(null)
                        .build()
        );
    }
}
```
The class docblock (`:9-28`) already states in its own words: *"CR-02 (119-REVIEW.md) / ISOL-01 (Phase 121, já satisfeito por esta correção)"* — this phase's own future closure is pre-documented at the fix site.

`PublicControllerTest.java` (full file, 53 lines) has exactly 2 green tests, `getBranding_devolveSempreAMarcaGenericaLexCV` and `getBranding_devolveSempreAMesmaRespostaIndependentementeDoEstado`, neither of which mocks or injects any repository (the controller takes none).

**Independently reverified in this pattern-mapping pass** (not merely trusted from CONTEXT.md): a repo-wide grep for `findFirstByOrderByCreatedAtAsc` returns exactly 2 hits, both inside Javadoc/comment text in `PublicController.java:15` and `PublicControllerTest.java:14` — zero live code anywhere. This confirms the method genuinely no longer exists in production code.

**Task shape for this phase:** re-read `PublicController.java` (done above), re-run `PublicControllerTest` (`mvn test -Dtest=PublicControllerTest`), record the pass as this phase's ISOL-01 evidence in whatever plan/audit artifact the executor writes. No diff to either file is expected or wanted.

---

### 5. ISOL-02 — tenant-heuristic sweep audit write-up

**Analog:** `.planning/milestones/v2.11-phases/LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente/97-01-SUMMARY.md` (AUD-01) — same project, same *kind* of deliverable (a source-level tenant-isolation sweep concluding COVERED, zero code changes), directly reusable structure.

**Reusable frontmatter shape** (97-01-SUMMARY.md:1-39 — adapt `tags`/`requirements-completed`/`key-decisions` to ISOL-02, keep the shape):
```yaml
---
phase: <this phase's id>
plan: <NN>
subsystem: api
tags: [tenant-isolation, security-audit, rbac]
key-decisions:
  - "No code changes made for ISOL-02 — every heuristic-resolution call site traced already
     legitimately scopes to a reserved/global entity or an already-reviewed cross-tenant job;
     verdict is COVERED, not FIXED."
requirements-completed: [ISOL-02]
---
```

**Reusable verdict-table format — the exact reusable element** (97-01-SUMMARY.md:71-77, one table per surface family):
```markdown
## Per-Surface Tenant-Scoping Verdict (ISOL-02)

| Query / Guard | Scope confirmed | Verdict |
|---|---|---|
| `TenantRepository.findFirstByOrderByCreatedAtAsc()` | No longer exists anywhere in production code (2 remaining hits are historical Javadoc text in `PublicController.java`/`PublicControllerTest.java`) | COVERED (closed by Phase 119 CR-02, ISOL-01) |
| `PlatformAdminController` cross-tenant iteration (`listTenants`/`updateTenant`/`setTenantAtivo`) | Deliberately operates over ALL tenants by design, gated at class level by `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` — never resolves "a single" tenant by heuristic | COVERED (by design, already reviewed Phase 119/120) |
| `AlertasDiariosJob` (`TenantRepository.findAll()` loop) | Own docblock (`AlertasDiariosJob.java:31-37`) already states: runs off the scheduler thread with no `SecurityContextHolder`/JWT, so `tenantId` is always an explicit loop parameter, never a "guess the tenant" resolution | COVERED (by design, already documented) |
| hardcoded `"LexCV"` literals (`plataforma/columns.tsx:18`, `DatabaseSeeder`) | Refer to the reserved platform tenant by exact name match, not a "first/oldest tenant" heuristic | COVERED (legitimate reserved-name lookup) |
```

**Explicit "background, not a finding" callout** (per 121-CONTEXT.md's decision — cite, don't fix):
```markdown
**Noted but explicitly out of scope for ISOL-02:** `.planning/research/PITFALLS.md` Pitfall 1
("Tenant isolation leak via an entity that has no `tenant_id` column of its own") documents that
`ProcessoRepository.findByClienteId(UUID)` and similar `findByXxxId`-without-`tenantId` repository
methods (~11 across the codebase) are safe today only because every current call site separately
re-checks the parent entity's tenant before use — not because the query itself is tenant-scoped.
This is a real, pre-existing, IDOR-adjacent concern, but it predates v2.16 by multiple milestones
and is not newly risky specifically because of a 2nd paying tenant (unlike ISOL-03's RBAC gap,
which Phase 120 makes newly exploitable). Phase 123 (ISOL-04) owns deciding whether it needs its
own fix phase.
```

**Filename/location:** Claude's Discretion per 121-CONTEXT.md — either a dedicated `121-ISOL02-AUDIT.md` in this phase directory, or folded as a section into whichever plan's own `SUMMARY.md` covers ISOL-02. Either way, the verdict-table format above is the reusable unit — Phase 123's own audit (ISOL-04) is meant to cite this file directly, exactly how `97-04-milestone-closeout` cites `97-01-SUMMARY.md` today.

---

## Shared Patterns

### Method-level `@PreAuthorize` overriding a class-level one (Spring Security most-specific-wins)
**Source:** Spring Security's `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` (already registered as the sole method-security advisor this codebase's tests build manually — see `PlatformAdminControllerTest.java:114`) resolves exactly one `@PreAuthorize` value per intercepted method call: if the method itself carries the annotation, that value is used exclusively; the class-level annotation is consulted only when the method has none of its own. The two are never combined/ANDed.
**Apply to:** `AdminController.updateRbac` (method-level `hasRole('PLATAFORMA_ADMIN')`) vs. every other method on the same class (still governed solely by the class-level `hasRole('ADMIN')`, :28).
**Why this needs its own test, not an assumption by analogy (121-CONTEXT.md's own framing):** confirmed via grep that no other controller in this codebase combines class-level and method-level `@PreAuthorize` — `ParecerController`/`NotificacaoController`/`ResourceController`/`PesquisaController` are method-level-only (no class annotation at all), while `AdminController`/`PlatformAdminController` are class-level-only (zero method-level annotations before this phase). This phase is the first to combine both on one class, so the mechanism must be proven behaviorally (Pattern Assignment 2's two-direction test), not just asserted by reading the annotation.

### AOP-proxy test harness for a real `@PreAuthorize` evaluation
**Source:** `PlatformAdminControllerTest.java:64-77` (class Javadoc explaining *why*) and `:111-116` (the harness itself).
**Apply to:** the new `updateRbac` proof tests (Pattern Assignment 2).
```java
private PlatformAdminController novoProxyComMethodSecurity() {
    ProxyFactory factory = new ProxyFactory(novoController());
    factory.setProxyTargetClass(true);   // CGLIB proxy required — the target class implements no interface
    factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
    return (PlatformAdminController) factory.getProxy();
}
```
This is still the *only* place in the codebase that constructs any kind of Spring context/security harness — no `MockMvc`, no `@SpringBootTest`, no `@WebMvcTest` anywhere in the test suite (confirmed unchanged since Phase 119). A direct `novoController().updateRbac(...)` call (as every existing test in `AdminControllerPlataformaAdminContencaoTest.java` does) never evaluates `@PreAuthorize` at all — only a call through this proxy does.

### Role-array gate on the frontend mirrors, never replaces, the backend `@PreAuthorize`
**Source:** `dashboard-shell.tsx:85-90`'s own comment: *"Este gate e apenas espelho de UX: a fronteira real de autorizacao e o @PreAuthorize... do PlatformAdminController."* Same framing repeated in `plataforma/page.tsx:60-63`.
**Apply to:** `RbacTab`'s new `isPlatformAdmin` check — it is a UX nicety (hide a button nobody can currently use), never the authorization boundary itself. The boundary is `AdminController.updateRbac`'s new `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (Pattern Assignment 1); if the frontend check were ever wrong or bypassed (e.g. via direct `PUT /api/v1/admin/rbac` from curl), the backend still returns 403.

### Tooltip + non-focusable-element `<span tabIndex={0}>` composition
**Source:** `plataforma/columns.tsx:66-82` (first-established instance of this exact composition for a *disabled Button*; also present in `settings/page.tsx` per 120-PATTERNS.md's own citation of that file's earlier at-limit-button fix).
**Apply to:** the new `Badge` replacement state in `RbacTab` — same wrapper technique, but a **different underlying reason** than the disabled-Button precedent: there, the wrapper works around `buttonVariants`' baked-in `disabled:pointer-events-none` (a disabled native `<button>` still nominally focusable but with pointer events suppressed); here, a plain `Badge` (`<span>` with no `tabIndex`) has no native tab stop *at all* to work around — the wrapper is what supplies keyboard reachability in the first place, not a workaround for a suppressed one. Do not skip it just because the reason differs.

### Verdict-table format for a tenant-isolation audit write-up
**Source:** `97-01-SUMMARY.md:71-90` (two tables, one per surface family, `| Query/Guard | Scope confirmed | Verdict |`).
**Apply to:** the ISOL-02 audit artifact (Pattern Assignment 5) — same three-column shape, one row per query/guard/design-decision traced, `Verdict` always `COVERED` (never `FIXED`, since no code changes are being made).

### Test file naming convention for a single-concern controller test
**Source:** every existing test filename in `backend/src/test/java/com/lexcv/controllers/`: `AdminControllerLimiteUtilizadoresTest`, `AdminControllerPlataformaAdminContencaoTest`, `AuthControllerGetMeTenantPlanoTest`, `AuthControllerLoginLockoutTest`, `AuthControllerTenantSuspensoTest`, `SetupControllerSingletonRegressaoTest` — always `<Controller><SpecificConcernPhrase>Test.java`, never a generic `<Controller>Test.java`.
**Apply to:** if a new dedicated file is chosen for the `updateRbac` proxy tests instead of extending the existing contenção test (Claude's Discretion, Pattern Assignment 2).

---

## No Analog Found

| File / Change | Role | Data Flow | Reason |
|---|---|---|---|
| `AdminController.java`'s combined class-level (`hasRole('ADMIN')`) + method-level (`hasRole('PLATAFORMA_ADMIN')`) `@PreAuthorize` on `updateRbac` | controller (authorization) | request-response | Confirmed via a full grep of every `@PreAuthorize` occurrence under `backend/src/main/java/com/lexcv/controllers/`: `ParecerController`, `NotificacaoController`, `ResourceController`, and `PesquisaController` are method-level-only (zero class-level annotations); `AdminController` and `PlatformAdminController` are class-level-only (zero method-level annotations anywhere in either class, before this phase). No existing controller in this codebase combines both on the same class. The annotation **value** (`hasRole('PLATAFORMA_ADMIN')`) has an exact precedent (`PlatformAdminController.java:53`); the **combination shape** (a stricter method-level override sitting alongside a looser class-level default, on the same class, for the first time) does not. This is precisely why 121-CONTEXT.md calls for an explicit behavioral proof (Pattern Assignment 2) rather than treating it as a routine copy-paste — Spring Security's most-specific-wins semantics are well-documented framework behavior, but this codebase has never yet exercised them, so there's no in-repo test to confirm the wiring (`@EnableMethodSecurity` config, proxy target class, etc.) actually produces that behavior here until this phase's own test does so. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/`, `backend/src/test/java/com/lexcv/controllers/`, `backend/src/main/java/com/lexcv/models/` (Role/Permission), `backend/src/main/java/com/lexcv/jobs/`, `web/src/app/(dashboard)/settings/`, `web/src/app/(dashboard)/plataforma/`, `web/src/components/shared/`, `web/src/components/ui/` (badge, tooltip), `web/src/hooks/use-me.ts`, `web/src/types/auth.ts`, `.planning/milestones/v2.11-phases/LEXCV-97-.../`, `.planning/research/PITFALLS.md`, `.planning/ROADMAP.md`

**Files scanned:** `AdminController.java` (full), `AdminControllerPlataformaAdminContencaoTest.java` (full), `PlatformAdminControllerTest.java` (full), `PlatformAdminController.java` (header), `PublicController.java` (full), `PublicControllerTest.java` (full), `Role.java` (full), `Permission.java` (full), `RbacResponse.java` (full), `NotificacaoController.java` (partial, annotation-order check), `ResourceController.java` (partial, annotation-order check + full `@PreAuthorize` grep), `AlertasDiariosJob.java` (header/docblock), `settings/page.tsx` (imports + `SettingsPage` header + `UserManagementTab` useMe call + `RbacTab` full function body + call site), `dashboard-shell.tsx` (full), `plataforma/page.tsx` (guard + header), `plataforma/columns.tsx` (full), `badge.tsx` (full), `tooltip.tsx` (full), `use-me.ts` (full), `types/auth.ts` (full), `97-01-SUMMARY.md` (full), `PITFALLS.md` Pitfall 1 (full section), `ROADMAP.md` Phase 121 section (full)

**Independent verifications performed (beyond trusting 121-CONTEXT.md's own claims):** repo-wide grep confirms `findFirstByOrderByCreatedAtAsc` has exactly 2 remaining hits, both in Javadoc/comment text, zero live code; full-controller grep confirms no existing class combines class-level + method-level `@PreAuthorize`; `Role.java`/`Permission.java` read in full, confirmed zero `tenant_id` column on either entity.

**Pattern extraction date:** 2026-07-29
