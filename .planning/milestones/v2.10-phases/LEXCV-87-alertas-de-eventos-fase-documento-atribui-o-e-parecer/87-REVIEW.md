---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
reviewed: 2026-07-09T16:00:00Z
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
  critical: 2
  warning: 5
  info: 1
  total: 8
status: issues_found
---

# Phase 87: Code Review Report

**Reviewed:** 2026-07-09T16:00:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

This is a third pass over Phase 87. The prior iteration's five findings (CR-01, WR-01, WR-02, WR-03, IN-01 in the previous `87-REVIEW.md`) were re-verified by directly re-reading each modified region rather than trusting the fix report, and all are correctly applied:

- **CR-01 (prior)** — `notificarFaseEntrada` (`NotificacaoService.java:118-137`), `notificarDocumentoNovo` (`:164-191`), and both `notificarAdmins` overloads (`:86-113`) now isolate each individual recipient's `criar()` call in its own try/catch, so one stale/orphaned recipient can no longer suppress the ADMIN fan-out or later recipients. Confirmed present and consistent with the new tests (`notificarAdminsComExclusao_...`, etc.).
- **WR-01 (prior)** — `UserSummaryResponse.ativo` (`UserSummaryResponse.java:24`) and the `listTenantUsers` mapping (`ResourceController.java:1063`) now carry `ativo`; both "assign to" pickers in `page.tsx` filter with `.filter((u) => u.ativo !== false)`. Confirmed present.
- **WR-02 (prior)** — `useTenantUsers()` now guards with `const enabled = typeof window !== "undefined";` (`use-users.ts:27`), matching every other query hook. Confirmed present.
- **WR-03 (prior)** — `atribuirResponsavel` now writes an `AuditLog` row (`acao = "processo_atribuir"`) mirroring `ParecerController.atribuirAdvogado` (`ResourceController.java:1030-1042`). Confirmed present.

Two things came out of this pass that move the phase forward rather than just re-confirming prior work:

1. The prior CR-01 fix's own writeup explicitly flagged, as a non-blocking suggestion, that `notificarProcessoAtribuido`/`notificarParecerAtribuido` share the exact "primary recipient, then unconditional ADMIN fan-out" shape and would "silently reproduce this exact bug the moment any future caller invokes them with an unvalidated id" — but noted they "aren't reachable with a stale ID *today*". That suggestion was never applied. Tracing the actual call sites this pass shows they *are* reachable today (Postgres `READ_COMMITTED` lets a concurrent delete/deactivation become visible to the notification service's own re-validation query, inside the same still-open `@Transactional` request), and the consequence is worse than a suppressed ADMIN notification: it rolls back the entire transaction, undoing an already-applied processo/parecer reassignment. This is promoted to CR-02 below.
2. Reading `ParecerController.updateSolicitacao` end-to-end (not just the notification-adjacent methods) surfaced an unrelated but more severe defect: two fields are updated unconditionally where their neighbors in the same method are correctly null-guarded, silently discarding a legal deadline on ordinary partial updates. This is CR-01 below.

Also found: parecer-side audit records never link back to their processo (WR-01), none of the three backend endpoints that accept a raw responsável/advogado ID enforce the `ativo` invariant the prior WR-01 fix's `ativo` field exists to support (WR-02), the "Reatribuir Responsável" picker introduced by that same prior fix mishandles the case where the *current* responsável has since gone inactive (WR-03), a concurrency gap in parecer-version numbering (WR-04), and a missing input-validation allowlist for `prioridade` (WR-05).

## Critical Issues

### CR-01: `updateSolicitacao` silently wipes `prazo` and can crash on `prioridade` for any partial update

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:210-239`

**Issue:** In `updateSolicitacao`, `clienteId` and `processoId` are only applied when present in the request body:

```java
if (payload.getClienteId() != null) {
    solicitacao.setClienteId(payload.getClienteId());
}
if (payload.getProcessoId() != null) {
    solicitacao.setProcessoId(payload.getProcessoId());
}
```

but `prazo` and `prioridade`, two lines above, are applied **unconditionally**:

```java
solicitacao.setPrazo(payload.getPrazo());
solicitacao.setPrioridade(payload.getPrioridade());
```

`ParecerSolicitacao.prazo` (`backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java:49`) is a plain nullable `LocalDate` with no default. Any client that PUTs a payload omitting `prazo` — e.g. a form that only edits `clienteId`/`processoId`, or any future caller that copies the guarding convention it sees two lines below — has `payload.getPrazo()` deserialize to `null`, and this line **silently erases the existing deadline** on save. No error, no warning: a real data-loss risk on a legal-deadline field.

`prioridade` (`ParecerSolicitacao.java:40-42`) is `@Column(nullable = false)`. Setting it to `null` and calling `parecerSolicitacaoRepository.save(solicitacao)` fails Hibernate's not-null check on flush, producing an uncaught 500 for what looks like an innocuous partial update. `@Builder.Default private String prioridade = "MEDIA";` does not help here — that default only applies through the Lombok-generated builder, not through the `@NoArgsConstructor` + setters path Jackson uses to deserialize `@RequestBody ParecerSolicitacao payload` — so a JSON body that simply omits `"prioridade"` deserializes to `null`, not `"MEDIA"`.

Separately, `descricao` (required at creation: "descricao é obrigatória") is never touched anywhere in this method, so there is no way to edit it through this endpoint at all, silently.

**Fix:**
```java
if (payload.getPrazo() != null) {
    solicitacao.setPrazo(payload.getPrazo());
}
if (payload.getPrioridade() != null) {
    solicitacao.setPrioridade(payload.getPrioridade());
}
if (payload.getDescricao() != null && !payload.getDescricao().isBlank()) {
    solicitacao.setDescricao(payload.getDescricao());
}
if (payload.getClienteId() != null) {
    solicitacao.setClienteId(payload.getClienteId());
}
if (payload.getProcessoId() != null) {
    solicitacao.setProcessoId(payload.getProcessoId());
}
```
If "clear the prazo" must remain possible, use an explicit signal for it (a dedicated field/endpoint) rather than "key absent from JSON" implicitly meaning "clear".

---

### CR-02: `notificarProcessoAtribuido` / `notificarParecerAtribuido` can roll back an already-persisted assignment

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:142-162` (`notificarProcessoAtribuido`) and `:198-209` (`notificarParecerAtribuido`)

**Issue:** The prior iteration's CR-01 fix isolated the primary recipient's `criar()` call in `notificarFaseEntrada` and `notificarDocumentoNovo` with a try/catch, and its writeup explicitly suggested extending the same isolation to these two methods "for full symmetry/defense-in-depth", noting they weren't reachable with a stale ID "today". That suggestion was not applied — both methods still call their primary recipient's `criar()` unguarded:

```java
// notificarProcessoAtribuido, lines 158-159 — no try/catch
criar(tenantId, responsavelId, "PROCESSO_ATRIBUIDO", titulo, mensagemDest, "processo",
        processoId.toString(), linkUrl);
```
```java
// notificarParecerAtribuido, lines 204-205 — no try/catch
criar(tenantId, advogadoId, "PARECER_ATRIBUIDO", titulo, mensagemDest, "parecer_solicitacao",
        solicitacaoId, linkUrl);
```

This pass traced the actual call sites and found the "not reachable today" assumption no longer holds (if it ever fully did): both are invoked from `@Transactional` controller methods, immediately after those same methods validate the recipient belongs to the tenant:

- `ResourceController.atribuirResponsavel` (`ResourceController.java:992`, `@Transactional`): validates `responsavelId` at lines 1015-1019, saves the reassignment and an audit row, then calls `notificarProcessoAtribuido` at 1044-1045.
- `ParecerController.createSolicitacao` (`:103`, `@Transactional`) and `ParecerController.atribuirAdvogado` (`:244`, `@Transactional`): both validate via `validateAdvogado(...)` immediately before saving and calling `notificarParecerAtribuido` (lines 167-170 and 303-304).

`criar()` re-validates tenant membership with its own `userRepository.findById(...)` (`NotificacaoService.java:38-41`). Under Postgres's default `READ_COMMITTED` isolation, a concurrent transaction's commit (e.g. an admin deleting or deactivating that exact user in a second, overlapping request) can become visible to this later statement within the *same still-open* transaction — the recipient was valid at the earlier validation read and invalid by the time `criar()` re-reads it. When that happens, the `IllegalArgumentException` is not caught anywhere in this call chain, and it rolls back the entire enclosing `@Transactional` method: the processo reassignment or parecer creation/reassignment that had already been saved, plus the audit row, all disappear, while the HTTP client sees an opaque 500 for a request that had, up to that point, fully succeeded. This directly contradicts the resilience guarantee the sibling methods (and the prior CR-01 fix) exist to provide, and no test in `NotificacaoServiceTest.java` exercises an invalid-but-non-null recipient for either method (every existing test for `notificarProcessoAtribuido`/`notificarParecerAtribuido` mocks a valid recipient), so this gap has no regression coverage.

**Fix:** Apply the same per-recipient isolation already used in `notificarFaseEntrada`:
```java
public void notificarProcessoAtribuido(UUID tenantId, UUID processoId, UUID responsavelId,
                                        String numeroProcesso, String linkUrl) {
    if (responsavelId == null) {
        return;
    }
    String numeroTexto = numeroProcesso != null ? numeroProcesso : "(sem número)";
    String titulo = "Processo atribuído";
    String mensagemDest = "Foi-lhe atribuído o processo " + numeroTexto + ".";
    String mensagemAdmin = "O processo " + numeroTexto + " foi atribuído a um novo responsável.";
    try {
        criar(tenantId, responsavelId, "PROCESSO_ATRIBUIDO", titulo, mensagemDest, "processo",
                processoId.toString(), linkUrl);
    } catch (IllegalArgumentException ex) {
        log.warn("PROCESSO_ATRIBUIDO: responsavelId {} inválido/órfão, notificação primária ignorada",
                responsavelId, ex);
    }
    notificarAdmins(tenantId, "PROCESSO_ATRIBUIDO", titulo, mensagemAdmin, "processo",
            processoId.toString(), linkUrl);
}
```
Apply the equivalent wrapping in `notificarParecerAtribuido`, and add a test per method mocking an invalid (non-null) primary recipient, asserting `assertDoesNotThrow(...)` and that the ADMIN fan-out still runs — mirroring `notificarFaseEntrada_responsavelNulo_geraApenasLinhaAdminSemExcecao` but for an *invalid* rather than *null* recipient.

## Warnings

### WR-01: Parecer audit records always hardcode `processoId(null)`, hiding them from the processo's Auditoria tab

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:160, 296, 345, 393, 504`

**Issue:** All five `AuditLog.builder()` calls in this file hardcode `.processoId(null)`, even though `solicitacao.getProcessoId()` (or `saved.getProcessoId()`) is available and frequently non-null — a `ParecerSolicitacao` can be linked to a `Processo` (see `createSolicitacao`'s own `processoBelongsToCliente` validation). `ResourceController.getAuditLog` (`:2180-2189`) filters strictly by `findByTenantIdAndProcessoIdOrderByTimestampDesc(tenantId, id)`, and the frontend "Auditoria" tab (`page.tsx:2293-2333`, via `useAuditLog`) renders exactly that list. As written, `parecer_criar`, `parecer_atribuir`, `parecer_aprovar`, `parecer_entregar`, and `parecer_versao_criar` can **never** appear in a processo's audit trail, even when the parecer is explicitly linked to that processo — a real gap in what the code itself labels a "compliance trail" (`ResourceController.java:2176`).

**Fix:** Use the solicitação's own `processoId` (`null` is fine when the parecer genuinely isn't linked to a processo):
```java
auditLogRepository.save(AuditLog.builder()
        .tenantId(tenantId)
        .processoId(solicitacao.getProcessoId())
        .acao("parecer_criar")
        ...
```
(use `saved.getProcessoId()` where `saved` rather than `solicitacao` is the variable in scope).

---

### WR-02: The `ativo` invariant the prior fix exposed is still not enforced server-side at any assignment endpoint

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1015-1019` (`atribuirResponsavel`), `:1554-1561` (`createPrazo`); `backend/src/main/java/com/lexcv/controllers/ParecerController.java:60-71` (`validateAdvogado`)

**Issue:** The prior iteration's WR-01 fix added `UserSummaryResponse.ativo`/`TenantUserOption.ativo` specifically "so 'assign to' pickers can filter out deactivated accounts" (`UserSummaryResponse.java:21-24`), and the frontend does filter correctly in both the "Reatribuir Responsável" and "Novo Prazo" pickers. But that fix only addressed *client-side* filtering of the picker's options — none of the three backend endpoints that actually accept a raw ID validate `ativo`:
- `ResourceController.atribuirResponsavel` (processo reassignment)
- `ResourceController.createPrazo` (prazo responsável)
- `ParecerController.validateAdvogado` (parecer create/atribuir)

A client calling these endpoints directly (or a stale cached picker, or any non-UI integration) can still assign a deactivated user as the responsible party for a processo, prazo, or parecer — the business rule the `ativo` field exists to support remains enforced only in the UI.

**Fix:** e.g. for `atribuirResponsavel`:
```java
User responsavel = userRepository.findById(responsavelId).orElse(null);
if (responsavel == null || !tenantId.equals(responsavel.getTenantId())
        || Boolean.FALSE.equals(responsavel.getAtivo())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "responsavelId não pertence a este tenant ou está inativo"));
}
```
Apply the same `Boolean.FALSE.equals(user.getAtivo())` guard to `createPrazo` and `validateAdvogado`.

---

### WR-03: "Reatribuir Responsável" picker (introduced by the prior fix) excludes the current responsável when inactive, leaving the `<select>` in a phantom-value state

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:2372, 2405-2409, 2433-2439, 2455`

**Issue:** `selectedUserId` is initialized/reset to `currentResponsavelId` (lines 2372, 2405-2409), but the `<select>`'s options are `(tenantUsers.data ?? []).filter((u) => u.ativo !== false)` (lines 2433-2439) — exactly the filter the prior WR-01 fix added. If the currently-assigned responsável has since been deactivated, `selectedUserId` holds an ID with **no matching `<option>`**, so the dropdown cannot visually show who the current responsável is. Compounding this, the confirm button is `disabled={!selectedUserId || selectedUserId === currentResponsavelId || ...}` (line 2455) — the dialog opens already disabled with no visible explanation, and `novoNome` (line 2378, used in "passará a ser da responsabilidade de {novoNome}") resolves to an empty string for as long as `selectedUserId` matches no active user.

**Fix:** Always surface the current responsável as an option, even if inactive:
```tsx
const filteredUsers = (tenantUsers.data ?? []).filter((u) => u.ativo !== false);
const currentStillActive = filteredUsers.some((u) => u.id === currentResponsavelId);
...
<select ...>
  <option value="" disabled>Selecione um utilizador</option>
  {!currentStillActive && currentResponsavelId && currentResponsavelNome ? (
    <option value={currentResponsavelId}>{currentResponsavelNome} (inativo)</option>
  ) : null}
  {filteredUsers.map((u) => (
    <option key={u.id} value={u.id}>{u.nome}</option>
  ))}
</select>
```

---

### WR-04: `synchronized (ParecerVersaoRepository.class)` does not prevent concurrent `numeroVersao` collisions

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:467-480`

**Issue:** The monitor is released as soon as the `synchronized` block exits, right after `parecerVersaoRepository.save(versao)` (line 479) — but since `createVersao` is `@Transactional`, the row only actually commits (and becomes visible to `findMaxNumeroVersaoBySolicitacaoId`) when the whole method returns, well after the lock is released and the file-upload code (lines 482-499) runs. Two concurrent requests creating a version for the *same* `solicitacaoId` can each acquire the monitor in turn, each compute `next = max + 1` from a snapshot that doesn't yet include the other's uncommitted insert, and each persist a `ParecerVersao` with the same `numeroVersao` — no unique constraint is evident to catch this at commit time. A JVM monitor also provides no protection across multiple application instances in a horizontally-scaled deployment.

**Fix:** Don't rely on an in-process lock for a cross-transaction invariant. Either add a DB-level `UNIQUE (solicitacao_id, numero_versao)` constraint and catch/retry on `DataIntegrityViolationException`, or take a row-level lock on the parent `ParecerSolicitacao` (`SELECT ... FOR UPDATE`) held for the duration of the same transaction that computes-and-inserts the new `ParecerVersao`, so the increment and the insert are atomic with respect to the database, not just the JVM.

---

### WR-05: `prioridade` is never validated against its documented domain in `ParecerController`

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:138-140, 229`

**Issue:** `ParecerSolicitacao.prioridade` is a plain `String` column documented as `// ALTA | MEDIA | BAIXA` (`ParecerSolicitacao.java:39-42`) with no enum type or DB check constraint. `createSolicitacao` (`ParecerController.java:138-140`) and `updateSolicitacao` (`:229`) both persist `body.getPrioridade()`/`payload.getPrioridade()` verbatim with no allow-list check — unlike the equivalent `Prazo.prioridade`, which `ResourceController.createPrazo` explicitly validates (`ResourceController.java:1548-1553`: `Set.of("ALTA", "MEDIA", "BAIXA")`). Arbitrary strings can be persisted, silently breaking any frontend badge/label mapping that assumes only the three documented values.

**Fix:**
```java
Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
if (body.getPrioridade() != null && !prioridadesValidas.contains(body.getPrioridade().toUpperCase())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
}
```
applied in both `createSolicitacao` and `updateSolicitacao`.

## Info

### IN-01: Inconsistent authorization tier for structurally equivalent "reassign responsible party" actions

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:990`; `backend/src/main/java/com/lexcv/controllers/ParecerController.java:242`

**Issue:** Reassigning a processo's responsável requires `@PreAuthorize("hasAuthority('processos:manage')")`. Reassigning a parecer's advogado — structurally the same action — requires only `@PreAuthorize("hasAuthority('pareceres:edit')")`, one tier lower in the `manage implies edit implies create` fallback chain (`CLAUDE.md`). This may be intentional (advogados routinely handing off pareceres under `:edit`), but both actions trigger an ADMIN notification fan-out and write an audit record for the same underlying reason, so it's worth confirming with whoever owns the permission model that the asymmetry is deliberate rather than drift.

**Fix:** No code change unless confirmed unintentional; if `pareceres:manage` was intended, tighten `atribuirAdvogado`'s `@PreAuthorize`.

---

_Reviewed: 2026-07-09T16:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
