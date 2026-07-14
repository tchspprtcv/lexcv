---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
reviewed: 2026-07-14T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java
  - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - backend/migrations/93-create-notificacao-preferencia-table.sql
  - web/src/types/notificacoes.ts
  - web/src/hooks/use-notificacao-preferencias.ts
  - web/src/app/(dashboard)/settings/page.tsx
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-93: Code Review Report

**Reviewed:** 2026-07-14T00:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Reviewed the NOTF-24 "per-user notification category preferences" feature end-to-end: the new
`NotificacaoPreferencia` join-table entity, the `CategoriaNotificacao` enum (single source of
truth for "silenciável" categories), the repository, the `criar()` mute guard plus the three new
service/controller methods (`listarCategoriasSilenciadas`, `silenciarCategoria`,
`reativarCategoria`), the manual production migration script, and the frontend
type/hook/settings-tab consuming the new `/notificacoes/preferencias` endpoints.

Tenant/user dual-scoping is correct throughout (every read/write is scoped by `tenantId` +
`userId` sourced from the JWT principal, never from the request), the mute guard is correctly
positioned as the single choke point in `criar()` so it cannot be bypassed by the daily job or any
`notificar*` helper, and the migration's schema matches the entity mapping. No SQL injection,
hardcoded secrets, or authorization bypass was found in the reviewed files.

The issues found are one concurrency/idempotency correctness bug in `silenciarCategoria()`, one
cross-layer business-rule duplication risk between the backend enum and a hardcoded frontend
filter, and a missing error state in the settings UI that can silently misrepresent the user's
actual preferences. Two lower-severity API-consistency nits round out the findings.

## Warnings

### WR-01: `silenciarCategoria()` is not actually idempotent under concurrent requests, and the resulting exception is unhandled

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:302-316`
**Issue:** The method documents itself as idempotent ("Idempotente: uma segunda chamada... não cria
uma segunda linha") but implements this via a classic check-then-act race: it calls
`existsByTenantIdAndUserIdAndCategoria` and only inserts if absent, with no locking or
`INSERT ... ON CONFLICT`. Two concurrent requests for the same `(tenant, user, categoria)` (e.g. a
double-click, or two browser tabs) can both pass the `exists` check before either commits — one
insert succeeds, the other violates `uk_notificacao_preferencia` and throws
`DataIntegrityViolationException`. Neither `silenciarCategoria()` nor
`NotificacaoController.silenciar()` catches anything but `IllegalArgumentException`, so the
exception falls through to `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)`
(`backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java:42-49`), which returns
**HTTP 500** with the raw exception class name and message in the JSON body — surfaced verbatim to
the user by `apiFetch`'s automatic error toast (`web/src/lib/api.ts:44`). This both breaks the
documented idempotency contract and leaks internal exception detail (constraint/table name) to the
client on a race.
**Fix:**
```java
@Transactional
public void silenciarCategoria(UUID tenantId, UUID userId, String categoria) {
    CategoriaNotificacao resolvida = CategoriaNotificacao.fromString(categoria)
            .orElseThrow(() -> new IllegalArgumentException("categoria desconhecida: " + categoria));
    if (!resolvida.isSilenciavel()) {
        throw new IllegalArgumentException("categoria não silenciável: " + categoria);
    }
    try {
        if (!notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)) {
            notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .categoria(categoria)
                    .build());
        }
    } catch (DataIntegrityViolationException ex) {
        // Lost the race to another concurrent request for the same (tenant, user, categoria) --
        // the row now exists either way, so this is still a successful "silenced" outcome.
        log.debug("silenciarCategoria: concurrent insert for {}/{}/{}, treating as success",
                tenantId, userId, categoria);
    }
}
```
(Alternatively, use a native `INSERT ... ON CONFLICT (tenant_id, user_id, categoria) DO NOTHING`
query to make this atomic at the database level.)

### WR-02: "Which categories can be silenced" is duplicated between backend and frontend with no shared source of truth

**File:** `web/src/app/(dashboard)/settings/page.tsx:875-877`
**Issue:** The backend's `CategoriaNotificacao` enum is explicitly documented as "the single source
of truth for silenciabilidade" (`backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java:5-10`),
with `PRAZO_VENCIDO` being the only non-silenciável category today. The frontend re-derives the same
fact independently via a hardcoded literal filter:
```ts
const categoriasSilenciaveis = NOTIFICACAO_CATEGORIA_OPTIONS.filter(
  (o) => o.value !== "PRAZO_VENCIDO",
);
```
There is no shared config or API contract enforcing these stay in sync. If a future category is
added to the backend enum as non-silenciável (i.e. a second `false` entry), the frontend will
continue rendering a toggle for it; toggling "off" will call `PUT /notificacoes/preferencias/{cat}`,
which the backend will correctly reject with 400 ("categoria não silenciável"), producing a
confusing failed-toggle UX with no compile-time or contract-level signal that the two lists have
drifted.
**Fix:** Have the `GET /notificacoes/preferencias` response (or a small dedicated metadata
endpoint) include which categories are silenciáveis, e.g.
`{"silenciadas": [...], "silenciaveis": ["FASE_ENTRADA", ...]}`, and derive
`categoriasSilenciaveis` in the frontend from that response instead of a hardcoded exclusion. At
minimum, centralize the exclusion list next to `CATEGORIA_LABEL_MAP` in
`web/src/lib/notificacao-categoria.ts` with an explicit comment that it must be kept in sync with
`CategoriaNotificacao.java`, so a future author has one place to update instead of an inline filter
buried in a settings sub-component.

### WR-03: `NotificationPreferencesTab` has no error state for the preferences fetch — silently defaults every category to "delivered" on failure

**File:** `web/src/app/(dashboard)/settings/page.tsx:861-874`
**Issue:**
```tsx
const { data, isLoading } = useNotificacaoPreferencias();
...
if (isLoading) { return <Loader2 ... />; }
const silenciadas = data?.silenciadas ?? [];
```
Only `isLoading` is checked; `isError`/`error` from the query are never read. If
`GET /notificacoes/preferencias` fails (network error, transient 5xx, etc.), `isLoading` still
settles to `false` once retries are exhausted, `data` remains `undefined`, and `silenciadas`
silently falls back to `[]` — every category then renders as `checked = true` ("A ENTREGAR"). A
transient `apiFetch` toast fires once, but it disappears; the rendered table permanently (until a
manual refresh) shows a state that does not reflect the user's actual stored preferences, with no
visual indication that the data failed to load or that a retry is available.
**Fix:**
```tsx
const { data, isLoading, isError, refetch } = useNotificacaoPreferencias();

if (isLoading) { return <Loader2 ... />; }
if (isError) {
  return (
    <div className="p-4 text-sm text-red-600">
      Não foi possível carregar as preferências de notificação.
      <Button variant="outline" size="sm" onClick={() => refetch()}>Tentar novamente</Button>
    </div>
  );
}
```

## Info

### IN-01: `reativar` endpoint does not validate `categoria`, unlike `silenciar`

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:138-143`
**Issue:** `silenciar()` delegates to `silenciarCategoria()`, which validates the path variable via
`CategoriaNotificacao.fromString(...)` and returns 400 for unknown categories. `reativar()` calls
`reativarCategoria()`, which goes straight to a derived delete with no validation at all:
```java
public ResponseEntity<?> reativar(@PathVariable String categoria) {
    notificacaoService.reativarCategoria(getTenantId(), getUserId(), categoria);
    return ResponseEntity.ok(Map.of("categoria", categoria, "silenciada", false));
}
```
`DELETE /notificacoes/preferencias/TYPO_CATEGORY` (or any nonexistent string) returns 200 with
`"silenciada": false` even though the value was never a real category. Harmless today (no row ever
existed to delete), but it is an asymmetric validation contract between two sibling endpoints and
will silently swallow client-side typos rather than surfacing them.
**Fix:** Validate via `CategoriaNotificacao.fromString(categoria)` in `reativarCategoria()` (or in
the controller) and return 400 for unknown values, mirroring `silenciar`'s contract.

### IN-02: New mutating preference endpoints are gated by `notificacoes:view`, not an edit/manage scope

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:115-143`
**Issue:** `listarPreferencias` (GET), `silenciar` (PUT) and `reativar` (DELETE) are all annotated
`@PreAuthorize("hasAuthority('notificacoes:view')")`. Per `CLAUDE.md`'s documented convention
("Permissions follow a `scope:action` convention where `action ∈ {view, create, edit, manage}`"),
mutating actions would normally require `edit`/`manage`. This matches the pre-existing pattern
already used by this same controller for `marcarLida`/`marcarTodasLidas` (also gated on `:view`),
so it is not a regression introduced by this phase, and in practice every seeded role (ADMIN,
ADVOGADO, TECNICO, ASSISTENTE) is granted `notificacoes:view`, so there is no privilege-escalation
path today. Flagging for awareness only: if a future role is introduced with `notificacoes:view`
but is intended to be read-only (e.g. a reporting/audit role), it would unexpectedly be able to
mute/unmute its own notification categories too.
**Fix:** Consider introducing `notificacoes:edit` for the mutating endpoints in a follow-up, or
explicitly document that "notificacoes" self-service actions (mark-read, mute/unmute) are
intentionally covered by `:view` because they only ever operate on the caller's own data.

---

_Reviewed: 2026-07-14T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
