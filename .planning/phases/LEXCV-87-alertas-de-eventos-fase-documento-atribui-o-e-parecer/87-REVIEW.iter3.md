---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
reviewed: 2026-07-09T13:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
  - backend/src/main/java/com/lexcv/dtos/UserSummaryResponse.java
  - web/src/hooks/use-processos.ts
  - web/src/hooks/use-users.ts
  - web/src/app/(dashboard)/processos/[id]/page.tsx
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: issues_found
---

# Phase 87: Code Review Report

**Reviewed:** 2026-07-09T13:00:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

This is a re-review after the prior round's 5 findings (CR-01, CR-02, WR-01, WR-02, WR-03 in `87-REVIEW.md`/`87-REVIEW-FIX.md`) were applied (commits `c529b57`, `8122c7d`, `b3532c5`, `7bdcf7f`, `396ab8d`). All five are indeed present in the current code and were independently re-verified by re-reading each modified region rather than trusting the fix report:

- **CR-01 (prior)** — `createProcessoFase` and both `uploadDocumento` branches now wrap `notificacaoService.notificar*` calls in `try/catch (IllegalArgumentException)`. Confirmed present (`ResourceController.java:1691-1695`, `2565-2571`, `2578-2584`).
- **CR-02 (prior)** — new `GET /api/v1/users` (`ResourceController.java:1044-1052`), `UserSummaryResponse`, and `web/src/hooks/use-users.ts` exist and `processos/[id]/page.tsx` consumes them instead of `useAdminUsers()`. Confirmed present.
- **WR-01 (prior)** — no-op guards confirmed in both `atribuirResponsavel` (`ResourceController.java:1023-1025`) and `atribuirAdvogado` (`ParecerController.java:282-284`).
- **WR-02 (prior)** — `notificarProcessoAtribuido` now early-returns on `responsavelId == null` (`NotificacaoService.java:132-134`). Confirmed present.
- **WR-03 (prior)** — `ProcessoDetailContent` now has a `useEffect` re-syncing `tab` from `searchParams` (`page.tsx:244-250`). Confirmed present and verified against `next/dist/docs` — no evidence this reintroduces a stale-tab regression, since nothing else on this route mutates `?tab=` via the router.

However, tracing the CR-01 fix's actual control flow (rather than trusting that "wrapped in try/catch" fully closed the gap) surfaced a **new, more fundamental defect that the prior fix did not address**: the try/catch was placed at the *controller* call site, wrapping the entire `notificar*` call as one unit, while the *service* methods internally do "primary recipient, then unconditionally notify ADMIN" as two sequential steps with no isolation between them. A single stale primary recipient now avoids the false-500 (the original bug), but it still silently prevents the ADMIN fan-out from running at all — which is the one guarantee this whole notification subsystem is designed never to skip. See CR-01 below (renumbered for this iteration).

Also found: the new tenant-user-listing endpoint doesn't filter deactivated accounts (WR-01), the new `useTenantUsers()` hook is the only query hook in the codebase missing the SSR guard every sibling hook has (WR-02), and the new process-reassignment endpoint has no audit trail unlike its parecer-reassignment sibling this same phase wired up (WR-03).

## Critical Issues

### CR-01: ADMIN fan-out is silently skipped whenever the primary recipient is a stale/orphaned user reference

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:108-118` (`notificarFaseEntrada`) and `:154-165` (`notificarDocumentoNovo`), reachable via `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1691-1695` (`createProcessoFase`) and `:2565-2571`/`:2578-2584` (`uploadDocumento`)

**Issue:**
Both methods are structured as "try the primary recipient first, then unconditionally notify ADMIN":

```java
public void notificarFaseEntrada(UUID tenantId, UUID processoId, UUID responsavelId,
                                  String numeroProcesso, String nomeFase, String linkUrl) {
    ...
    if (responsavelId != null) {
        criar(tenantId, responsavelId, "FASE_ENTRADA", titulo, mensagem, "processo",
                processoId.toString(), linkUrl);              // <-- throws IllegalArgumentException if orphaned
    }
    notificarAdmins(tenantId, "FASE_ENTRADA", titulo, mensagem, "processo", processoId.toString(), linkUrl); // <-- never reached
}
```

and

```java
public void notificarDocumentoNovo(UUID tenantId, String documentoId, Collection<UUID> destinatarios,
                                    String nomeDocumento, String linkUrl, UUID atorId) {
    ...
    for (UUID dest : destinatariosUnicos) {
        if (dest != null && !dest.equals(atorId)) {
            criar(tenantId, dest, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl); // <-- throws on first orphan
        }
    }
    notificarAdmins(tenantId, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl, atorId); // <-- never reached, and any
                                                                                                                //     dest *after* the orphan
                                                                                                                //     in iteration order is
                                                                                                                //     also skipped
}
```

`criar()` throws `IllegalArgumentException` whenever a destinatario doesn't resolve to an existing tenant `User` (already established as fully reachable in the prior review: `AdminController.deleteUser` does a raw `deleteById` with no cleanup of `Processo.responsavelId` or the `ClienteAdvogado`/`ClienteAdministrativo` join rows). The prior CR-01 fix wrapped the *entire* `notificar*(...)` call in a try/catch at the two `ResourceController` call sites — this stops the exception from becoming a false HTTP 500, which was the finding as originally written. But because the try/catch sits *outside* the service method rather than around each individual `criar()` call *inside* it, the exception still aborts the method partway through, which means:

- In `notificarFaseEntrada`: a stale `responsavelId` means the ADMIN broadcast on line 117 **never executes at all** — not just "the responsável doesn't get notified" (expected/acceptable) but "nobody, including ADMIN, learns a new fase was entered."
- In `notificarDocumentoNovo`: a stale destinatario anywhere in the (deduplicated, insertion-ordered) set means every destinatario *after* it in iteration order, **and always the entire ADMIN fan-out**, silently never fires. In the cliente-team branch (`uploadDocumento`'s `ClienteAdvogado`/`ClienteAdministrativo` path), this list can have several entries — one departed team member silently blocks notifications to every other still-active team member ordered after them, plus ADMIN.

This is worse than "the responsável notification fails" (which is inherent to a nullable/stale FK and arguably acceptable) — it silently defeats the one part of the design that's supposed to be unconditional: 87-CONTEXT.md and the code's own comments repeatedly frame ADMIN notification as an always-on guarantee ("+ADMIN", "notifica ... + ADMIN"), and the whole point of routing every write through `NotificacaoService` is to make that guarantee structurally reliable. Today it silently isn't, in a scenario the team already knows is fully reachable (deleting a user who still owns work). There is no error surfaced anywhere — the controller's `catch` only logs a terse warning with no indication that an unrelated, valid recipient (or ADMIN) was also dropped as a side effect.

**Fix:** Isolate each recipient's `criar()` call with its own try/catch *inside the service method*, so one bad recipient can never prevent any other recipient (least of all ADMIN) from being notified. This also makes the outer controller-level try/catch effectively redundant defense-in-depth rather than the only line of defense:

```java
// NotificacaoService.java — add a logger (e.g. @Slf4j) and do this in both methods:
public void notificarFaseEntrada(UUID tenantId, UUID processoId, UUID responsavelId,
                                  String numeroProcesso, String nomeFase, String linkUrl) {
    String numeroTexto = numeroProcesso != null ? numeroProcesso : "(sem número)";
    String titulo = "Nova fase";
    String mensagem = "O processo " + numeroTexto + " entrou na fase " + nomeFase;
    if (responsavelId != null) {
        try {
            criar(tenantId, responsavelId, "FASE_ENTRADA", titulo, mensagem, "processo",
                    processoId.toString(), linkUrl);
        } catch (IllegalArgumentException ex) {
            log.warn("FASE_ENTRADA: responsavelId {} inválido/órfão, notificação primária ignorada", responsavelId, ex);
        }
    }
    notificarAdmins(tenantId, "FASE_ENTRADA", titulo, mensagem, "processo", processoId.toString(), linkUrl);
}

public void notificarDocumentoNovo(UUID tenantId, String documentoId, Collection<UUID> destinatarios,
                                    String nomeDocumento, String linkUrl, UUID atorId) {
    String titulo = "Novo documento";
    String mensagem = "Foi adicionado o documento \"" + nomeDocumento + "\".";
    Set<UUID> destinatariosUnicos = new LinkedHashSet<>(destinatarios == null ? List.of() : destinatarios);
    for (UUID dest : destinatariosUnicos) {
        if (dest != null && !dest.equals(atorId)) {
            try {
                criar(tenantId, dest, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl);
            } catch (IllegalArgumentException ex) {
                log.warn("DOCUMENTO_NOVO: destinatario {} inválido/órfão, ignorado", dest, ex);
            }
        }
    }
    notificarAdmins(tenantId, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl, atorId);
}
```

For full symmetry/defense-in-depth, apply the same per-recipient isolation inside `notificarAdmins` itself (the `for (User admin : ...)` loop), and consider it for `notificarProcessoAtribuido`/`notificarParecerAtribuido` too — those two aren't reachable with a stale ID *today* (both call sites freshly re-validate the id in the same request), but they share the identical "primary then admin" shape and would silently reproduce this exact bug the moment any future caller (e.g. a Phase 89 consumer) invokes them with an unvalidated id, echoing the same "latent foot-gun" reasoning that motivated the already-applied WR-02 fix on `notificarProcessoAtribuido`.

## Warnings

### WR-01: New tenant-user listing endpoint doesn't filter out deactivated accounts, letting work be reassigned to users who cannot log in

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1044-1052` (`listTenantUsers`), `backend/src/main/java/com/lexcv/dtos/UserSummaryResponse.java:18-21`, consumed by `ReatribuirResponsavelControl` and the "Novo Prazo" responsável picker in `web/src/app/(dashboard)/processos/[id]/page.tsx`

**Issue:** `listTenantUsers` maps every row from `userRepository.findByTenantId(tenantId)` straight into `UserSummaryResponse {id, nome}` with no filter and no `ativo` field:

```java
List<UserSummaryResponse> response = userRepository.findByTenantId(tenantId).stream()
        .map(u -> UserSummaryResponse.builder().id(u.getId()).nome(u.getNome()).build())
        .collect(Collectors.toList());
```

`User.ativo` genuinely gates the ability to use the system — confirmed in `AuthController.java:77` and `:129` (login is rejected when `!user.getAtivo()`) and `JwtAuthenticationFilter.java:43` (every authenticated request re-checks `ativo`, so even an already-logged-in session is cut off once deactivated). Since the DTO exposes no `ativo` flag, the frontend has no way to distinguish active from deactivated users in the "Reatribuir Responsável" and "Novo Prazo" `<select>` pickers (`page.tsx:2431-2435`, `:1217-1221`) — any `processos:manage`/`processos:edit` holder can knowingly or unknowingly reassign a process or a prazo to a former/disabled staff account. That assignment then silently orphans the work: `NotificacaoService.criar()` also doesn't check `ativo`, so a notification row is still created for that user, but they can never log in to see or act on it, and nobody else is alerted that the assignment is effectively a no-op.

Note this endpoint is deliberately also used for historical name lookups on the same page (`userNomeById`, e.g. resolving the `decisorId` of a past conflict-check decision) — simply filtering `ativo=true` server-side would break those legitimate historical lookups for anyone who has since been deactivated.

**Fix:** Add `ativo` to the DTO and filter only at the two *assignment* call sites, keeping the unfiltered list available for name lookups:

```java
// UserSummaryResponse.java
private UUID id;
private String nome;
private Boolean ativo;

// ResourceController.listTenantUsers — include ativo in the mapping, unchanged otherwise
.map(u -> UserSummaryResponse.builder().id(u.getId()).nome(u.getNome()).ativo(u.getAtivo()).build())
```
```tsx
// page.tsx — filter to active users only where they're offered as a *new* assignment target
{(tenantUsers.data ?? []).filter((u) => u.ativo !== false).map((u) => (
  <option key={u.id} value={u.id}>{u.nome}</option>
))}
```

### WR-02: `useTenantUsers()` is the only query hook in the codebase missing the SSR `typeof window` guard

**File:** `web/src/hooks/use-users.ts:18-24`

**Issue:**
```ts
export function useTenantUsers() {
  return useQuery({
    queryKey: ["users", "tenant-list"],
    queryFn: () => apiFetch<TenantUserOption[]>("/users"),
    staleTime: 30_000,
  });
}
```
Every other `useQuery` call across all 13 hook files under `web/src/hooks/` (38 occurrences, confirmed by grep) guards with `enabled: typeof window !== "undefined" && ...` — including `useAdminUsers` in `use-admin.ts:7-8`, the exact hook `useTenantUsers` was introduced to replace on this page. `use-users.ts` is the sole hook file with zero occurrences of this pattern. This is a "use client" page (`page.tsx:1`), and Client Components are still executed once during Next.js's server render pass for the initial HTML — the surrounding codebase clearly treats that pass as something every other data hook must defensively no-op during. Whatever concrete issue that convention exists to prevent (this repo's own `web/AGENTS.md` warns Next 16 has routing/data-fetching behavior changes worth double-checking), `useTenantUsers()` is not protected against it, and it's now called twice on this page (`page.tsx:265` and `:2374`).

**Fix:** Match the established convention:
```ts
export function useTenantUsers() {
  const enabled = typeof window !== "undefined";
  return useQuery({
    queryKey: ["users", "tenant-list"],
    queryFn: () => apiFetch<TenantUserOption[]>("/users"),
    enabled,
    staleTime: 30_000,
  });
}
```

### WR-03: `atribuirResponsavel` writes no audit-log entry, unlike the equivalent `ParecerController.atribuirAdvogado` this same phase wired up

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:990-1034` (`atribuirResponsavel`), contrast with `backend/src/main/java/com/lexcv/controllers/ParecerController.java:291-301` (`atribuirAdvogado`)

**Issue:** Both endpoints do the same shape of thing — a `processos:manage`/`pareceres:edit`-gated reassignment of a responsible party, followed by a notification — and both were touched by this phase to add that notification call. `ParecerController.atribuirAdvogado` writes an `AuditLog` row (`acao = "parecer_atribuir"`, `autorId` from `SecurityContext`) immediately after saving. `ResourceController.atribuirResponsavel` performs the analogous mutation (`processo.setResponsavelId(...)`, save, notify) but never touches `auditLogRepository`. For a security-sensitive, management-only action ("reatribuir responsável" is explicitly called out in `87-CONTEXT.md` as "uma ação de gestão distinta"), this is an inconsistent audit trail between two structurally identical actions delivered in the same phase — an investigation into "who reassigned process X and when" has an answer for pareceres but not for processos.

**Fix:** Mirror the existing pattern already used four times in `ParecerController.java`:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(saved.getId())
        .acao("processo_atribuir")
        .entidadeTipo("processo")
        .entidadeId(saved.getId().toString())
        .autorId(principal.getUserId())
        .build());
```

## Info

### IN-01: No test covers the WR-02(prior-iteration) null-guard in `notificarProcessoAtribuido`

**File:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Issue:** `NotificacaoServiceTest` is otherwise unusually thorough — every branch of every method has a dedicated test, including the null-responsável branch of the sibling method (`notificarFaseEntrada_responsavelNulo_geraApenasLinhaAdminSemExcecao`, line 271). But there is exactly one test for `notificarProcessoAtribuido` (line 288, `..._responsavelNaoNulo_...`), and it only exercises the non-null path. The null-guard added by the prior WR-02 fix (`NotificacaoService.java:132-134`) — the entire point of that fix — has zero regression coverage; the fix's own report explicitly flagged this as unverified ("no test exercises the new null-path early-return itself"). Manual tracing shows the guard is correct as written, but a future edit to this method has no safety net for this specific branch the way every other branch in this file does.
**Fix:** Add the missing symmetric test:
```java
@Test
void notificarProcessoAtribuido_responsavelNulo_naoGeraNotificacoesNemExcecao() {
    NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
    assertDoesNotThrow(() ->
            service.notificarProcessoAtribuido(TENANT_ID, UUID.randomUUID(), null, "PROC-0003", "/link"));
    verify(notificacaoRepository, never()).save(any());
    verify(userRepository, never()).findByTenantIdAndRoleName(any(), any());
}
```

### IN-02: Pre-existing empty catch blocks silently swallow ContaCorrente balance-update failures (unrelated to Phase 87, noted for visibility only)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2766`, `:2853`
**Issue:** `catch (Exception ignored) {}` in `createPagamento` and `deletePagamento` silently discards any failure updating a cliente's `ContaCorrente.saldo`, while the payment itself is still created/deleted and `201`/`204` is returned — the client-facing ledger can silently drift from the recorded payments. This is in the `financeiro`/`honorarios` domain, entirely untouched by this phase's notification/atribuição work (no Phase 87 code references it), so it is **not** a regression from this phase and is out of this review's remit to fix. Flagging only because it was noticed while reading the full required file; not counted toward this phase's fix scope.
**Fix:** Out of scope for this phase — consider a follow-up ticket to at least log the swallowed exception (`log.error(...)`) so a balance-update failure is observable.

---

_Reviewed: 2026-07-09T13:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
