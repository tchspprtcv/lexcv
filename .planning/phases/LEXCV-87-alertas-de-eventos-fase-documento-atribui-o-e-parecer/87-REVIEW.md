---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
reviewed: 2026-07-09T10:49:04Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
  - web/src/hooks/use-processos.ts
  - web/src/app/(dashboard)/processos/[id]/page.tsx
findings:
  critical: 2
  warning: 3
  info: 1
  total: 6
status: issues_found
---

# Phase 87: Code Review Report

**Reviewed:** 2026-07-09T10:49:04Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Reviewed the four notification triggers (FASE_ENTRADA, DOCUMENTO_NOVO, PROCESSO_ATRIBUIDO, PARECER_ATRIBUIDO) and the new `PUT /processos/{id}/atribuir` reassignment endpoint, front-to-back (`NotificacaoService` → `ResourceController`/`ParecerController` → `use-processos.ts` → `processos/[id]/page.tsx`).

Verified clean against the four specific risk areas called out in scope:
- **Tenant validation on reassignment** — `atribuirResponsavel` (`ResourceController.java:1012-1016`) correctly mirrors `createProcesso`'s `responsavelId` tenant-ownership check verbatim, as required.
- **Cross-tenant leak in the documento-sem-processo branch** — `uploadDocumento`'s cliente/team branch (`ResourceController.java:2536-2544`) scopes both `ClienteAdvogado`/`ClienteAdministrativo` queries by the request's own `tenantId`, and `clienteId` was already validated against that same tenant earlier in the method. No leak found.
- **Actor exclusion** — correctly applied only to `notificarDocumentoNovo` and `notificarParecerAtribuido` (both primary destinatario and ADMIN fan-out), and correctly *not* applied to `notificarFaseEntrada`/`notificarProcessoAtribuido`, matching 87-CONTEXT.md.
- **Null-safety of `Processo.responsavelId`** — all four call sites guard against a `null` responsavelId correctly. However, tracing this further surfaced a distinct, unguarded failure mode (CR-01 below): a *non-null but stale* responsavelId (referencing a deleted user) is not handled, and this is a real gap, not a hypothetical one — the code's own inline comments show the author was aware of and tried to mitigate "breaking the parent controller's transaction" for the null case, but missed the equally-reachable stale-reference case.

Beyond the four targeted areas, two more issues were found by tracing call chains and cross-referencing with sibling code in the same files: an unhandled-exception path that turns a successful write into a false 500 (CR-01), and a new UI feature that silently renders non-functional for non-ADMIN `processos:manage` holders (CR-02) because it depends on an ADMIN-only endpoint that a sibling page in the same codebase already knows to guard against.

## Critical Issues

### CR-01: Stale (deleted-user) `responsavelId`/team reference turns a successful write into a false 500

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:31-39` (root cause), with unguarded call sites at `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1662-1664` (`createProcessoFase`) and `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2523-2544` (`uploadDocumento`, both the processo-responsável branch and the cliente-team branch)

**Issue:**
`NotificacaoService.criar()` throws `IllegalArgumentException` whenever `destinatarioId` doesn't resolve to an existing `User` in the given tenant (`userRepository.findById(destinatarioId).filter(...).orElseThrow(...)`, lines 36-39). `notificarFaseEntrada`'s own inline comment acknowledges this exact risk: *"responsavelId é nullable ... null-guard evita que criar() lance IllegalArgumentException e rebente a transação do controller pai"* — but the guard added (`if (responsavelId != null)`) only covers the case where no responsável was ever assigned. It does **not** cover the case where a responsável *was* assigned but the referenced `User` row no longer exists.

That case is fully reachable: `AdminController.deleteUser` (`/api/v1/admin/users/{id}`) does a raw `userRepository.deleteById(id)` with no cascade cleanup of `Processo.responsavelId` (a plain `@Column(name = "responsavel_id")` UUID with no `@ManyToOne`/FK mapping — confirmed in `Processo.java:27-28`), nor of `ClienteAdvogado`/`ClienteAdministrativo` join rows. So any tenant that deletes a staff member who still owns a process (or is still on a cliente's advogado/administrativo team) will hit this the next time someone:
- adds a fase to that process (`createProcessoFase` reads `processo.getResponsavelId()` straight off the DB row and passes it, unguarded, into `notificarFaseEntrada` → `criar()`), or
- uploads a document to that process or cliente (`uploadDocumento` reads `proc.getResponsavelId()` or the `ClienteAdvogado`/`ClienteAdministrativo` rows and passes them, unguarded, into `notificarDocumentoNovo` → `criar()`).

Neither `createProcessoFase` nor `uploadDocumento` is `@Transactional`, and the primary write (`processoFaseRepository.save(pf)` at line 1662, `documentoRepository.save(documento)` at line 2523) happens **before** the notification call. The uncaught `IllegalArgumentException` is then caught by `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` and turned into a 500. Net effect: the fase/documento is durably persisted, the storage object is uploaded, but the client receives a 500 and has no way to know the operation actually succeeded — and may retry, risking a duplicate fase/documento.

The two currently-safe call sites for `notificarProcessoAtribuido` (`createProcesso` and `atribuirResponsavel`) are not affected, because both freshly validate `responsavelId` against the tenant in the same request immediately before calling notify — this bug is specific to the two call sites that read an already-persisted, unrevalidated FK-shaped field.

**Fix:** Make the notification call best-effort at the two exposed call sites (or make `criar()` tolerant of a stale reference when invoked from a fire-and-forget context) so a side-channel notification failure can never mask a successful primary write:
```java
// ResourceController.createProcessoFase
ProcessoFase saved = processoFaseRepository.save(pf);
try {
    notificacaoService.notificarFaseEntrada(processo.getTenantId(), id, processo.getResponsavelId(),
            processo.getNumeroProcesso(), faseNome, "/processos/" + id + "?tab=fases");
} catch (IllegalArgumentException ex) {
    log.warn("FASE_ENTRADA: falha ao notificar (responsavelId possivelmente órfão) processo={}", id, ex);
}
return ResponseEntity.status(HttpStatus.CREATED).body(saved);
```
Apply the same pattern around both `notificarDocumentoNovo(...)` calls in `uploadDocumento`. Add a regression test to `NotificacaoServiceTest` (or a new controller test) that stubs `userRepository.findById(responsavelId)` to return empty and asserts the fase/documento write still succeeds.

### CR-02: New "Reatribuir Responsável" control is non-functional for ADVOGADO (and any non-ADMIN `processos:manage`) users

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:2359-2360, 2408-2422, 2437` (root cause of dependency at `web/src/hooks/use-admin.ts:7-16`, gated by `backend/src/main/java/com/lexcv/controllers/AdminController.java:26` `@PreAuthorize("hasRole('ADMIN')")` at class level)

**Issue:**
The backend endpoint this phase adds, `PUT /processos/{id}/atribuir`, is deliberately gated by the `processos:manage` **permission** rather than a hard `ADMIN` role check (per 87-CONTEXT.md: *"Novo endpoint backend gated por processos:manage (não processos:edit)"*). Per `DatabaseSeeder.seedRbac()` (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:336-352`), the `ADVOGADO` role is explicitly granted `processos:manage` — so `canManageProcessos` is `true`, and `ReatribuirResponsavelControl` renders, for any ADVOGADO user, not just ADMIN.

However, `ReatribuirResponsavelControl` populates its "Novo Responsável" `<select>` via `useAdminUsers()` (`page.tsx:2360`), which calls `GET /admin/users` — an endpoint gated by `@PreAuthorize("hasRole('ADMIN')")` at the `AdminController` class level, with no other tenant-scoped user-listing endpoint anywhere in the backend. For an ADVOGADO (or any other non-ADMIN `processos:manage` holder), this query returns 403. `apiFetch` (`web/src/lib/api.ts:43-45`) deliberately suppresses the error toast for 401/403, so `adminUsers.data` silently stays `undefined`, the dropdown renders with only the disabled `"Selecione um utilizador"` placeholder, and the "Reatribuir"/"Confirmar Reatribuição" buttons stay permanently disabled (`disabled={!selectedUserId || ...}` at line 2437) — with zero error message surfaced anywhere in the component (it never checks `adminUsers.isError`).

Net effect: the reassignment feature this phase adds is only actually usable by ADMIN-role users, even though the backend was explicitly designed to allow ADVOGADO to use it too, and the UI itself renders the control (and lets the user open the dialog) for ADVOGADO users before silently failing to let them do anything with it. Notably, this exact hazard is already known elsewhere in this same codebase: `clientes/[id]/page.tsx:1147` calls `useAdminUsers({ enabled: isAdmin })`, i.e. an existing page in this repo already gates this same hook on an admin check — this new control doesn't apply that same defensive pattern, and even if it did, gating wouldn't fix the underlying gap (an ADVOGADO would still have no candidate list at all).

**Fix:** Add a tenant-scoped user-listing endpoint that any `processos:manage` (or broader) holder can call — e.g. `GET /api/v1/users` scoped to the caller's tenant, gated by a permission ADVOGADO already holds — and have `ReatribuirResponsavelControl` (and ideally the pre-existing "Novo Prazo" responsável selector, which has the same latent issue) consume that instead of `useAdminUsers()`/`/admin/users`.

## Warnings

### WR-01: No server-side no-op guard on reassignment — reassigning to the same user still fires "you were assigned" notifications

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1018-1022`
**Issue:** `atribuirResponsavel` sets `processo.setResponsavelId(responsavelId)`, saves, and unconditionally calls `notificarProcessoAtribuido` — there is no check for whether `responsavelId` actually differs from the process's current `responsavelId`. Calling this endpoint twice with the same `responsavelId` sends the "Foi-lhe atribuído o processo ..." notification (and an ADMIN broadcast) again, even though nothing changed. The frontend disables its "Reatribuir" button when `selectedUserId === currentResponsavelId` (`page.tsx:2437`), but that's a UI-only guard; any direct API call (or a future client) can trigger repeated, misleading notifications. `ParecerController.atribuirAdvogado` has the same gap, and this phase is what newly wires a notification side-effect onto that pre-existing endpoint, so the same spam risk now applies there too.
**Fix:** Short-circuit when the value is unchanged, before mutating/saving/notifying:
```java
if (responsavelId.equals(processo.getResponsavelId())) {
    return ResponseEntity.ok(processo);
}
```

### WR-02: `notificarProcessoAtribuido`'s ADMIN broadcast is not internally null-safe — relies entirely on callers to avoid a misleading message

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:123-135`
**Issue:** Unlike `notificarFaseEntrada` (which correctly fires the ADMIN fan-out unconditionally because "a fase entrada happened" is true regardless of whether a responsável exists), `notificarProcessoAtribuido`'s ADMIN message ("O processo ... foi atribuído a um novo responsável.") asserts that an assignment occurred, but the method calls `notificarAdmins(...)` unconditionally even when `responsavelId == null` — i.e. even when nothing was actually assigned. Today this never manifests because both call sites externally prevent invoking the method with a `null` responsavelId (`createProcesso` wraps the whole call in `if (saved.getResponsavelId() != null)`, and `atribuirResponsavel` requires `responsavelId` as a non-blank input). But the method's own contract doesn't enforce or document this precondition, making it a latent foot-gun for any future caller (e.g. a Phase 89 consumer) that invokes it directly without replicating the same external guard.
**Fix:** Make the method self-defending, consistent with how `notificarFaseEntrada` is written:
```java
public void notificarProcessoAtribuido(UUID tenantId, UUID processoId, UUID responsavelId,
                                        String numeroProcesso, String linkUrl) {
    if (responsavelId == null) {
        return; // nothing was actually assigned; avoid a misleading ADMIN broadcast
    }
    ...
}
```

### WR-03: Tab state only reads `?tab=` once at mount — breaks the FASE_ENTRADA deep-link on same-route navigation

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:233-237`
**Issue:**
```tsx
const searchParams = useSearchParams();
const tabParam = searchParams.get("tab");
const initialTab: TabKey =
  tabParam && (TAB_KEYS as string[]).includes(tabParam) ? (tabParam as TabKey) : "timeline";
const [tab, setTab] = React.useState<TabKey>(initialTab);
```
`useState`'s initializer only runs on the component's first mount. Next.js App Router reuses the existing component instance (no remount) for client-side navigations that change only the search string on the same dynamic route — so navigating from `/processos/{id}?tab=timeline` to `/processos/{id}?tab=fases` while `ProcessoDetailContent` is already mounted for that same `id` will update `searchParams`/the URL but **will not** update `tab`, since the `useState` initializer doesn't re-run. This directly undermines 87-CONTEXT.md's stated goal for the FASE_ENTRADA notification link ("Link da notificação aponta para a aba 'Fases' ... usar o mesmo padrão de deep-link por aba"): a user who is already on that processo's page (e.g. two tabs open, or navigating there again via any in-app link built the same way) and then follows a `?tab=fases` link will not actually land on the Fases tab. It works correctly today only because Phase 89's notification-consumption UI (the realistic source of these links) doesn't exist yet, so the common path is a fresh mount from elsewhere in the app.
**Fix:** Re-sync `tab` whenever `searchParams` changes, in addition to the initial value:
```tsx
React.useEffect(() => {
  const p = searchParams.get("tab");
  if (p && (TAB_KEYS as string[]).includes(p) && p !== tab) {
    setTab(p as TabKey);
  }
}, [searchParams]);
```

## Info

### IN-01: No direct test coverage for the new mutating endpoint or the controller-level notification wiring

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:987-1025` (and the trigger call sites throughout the same file), `backend/src/main/java/com/lexcv/controllers/ParecerController.java:164-169, 292-296`
**Issue:** `NotificacaoServiceTest.java` thoroughly covers the new `NotificacaoService` methods in isolation (actor exclusion, null-responsável handling, admin fan-out), but there is no test exercising `atribuirResponsavel`'s own logic (UUID parsing, 404 vs 400 ordering, tenant validation) or the fact that the controllers actually call the right `notificar*` method with the right arguments at each trigger point. This matches the project's existing convention (there are only two test files in the entire backend, both service-level — `RiscoPrazoServiceTest`, `NotificacaoServiceTest` — no controller test exists anywhere in the codebase), so it isn't a regression, but the new endpoint is both mutating and security-sensitive (tenant isolation), which makes it a good candidate for at least a lightweight `@WebMvcTest`/`MockMvc` test going forward.
**Fix:** Consider adding a minimal MockMvc/WebMvcTest for `atribuirResponsavel` covering: cross-tenant `responsavelId` rejection, cross-tenant `processo` 404, and malformed UUID 400 — as a template other controller endpoints could later follow.

---

_Reviewed: 2026-07-09T10:49:04Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
