---
phase: LEXCV-96-notf-26-snooze-de-lembrete-de-prazo
reviewed: 2026-07-14T18:09:41Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Notificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - web/src/types/notificacoes.ts
  - web/src/hooks/use-notificacoes.ts
  - web/src/components/shared/notificacao-snooze-control.tsx
  - web/src/components/shared/notification-bell.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase LEXCV-96: Code Review Report

**Reviewed:** 2026-07-14T18:09:41Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Reviewed the NOTF-26 snooze feature: the new `Notificacao.snoozedUntil` column, the
`NotificacaoRepository` visibility-predicate queries, `NotificacaoService.snooze()`,
the `PATCH /notificacoes/{id}/snooze` endpoint, and the frontend snooze control wired
into the bell dropdown and the `/notificacoes` history page.

The backend implementation is solid: tenant/destinatario scoping is consistently
derived from the JWT (never the request), the 1/3/7-day preset validation happens
before any repository access, the `PRAZO_VENCIDO` non-adiável guard is reused
correctly and is well-documented (including its own known future-risk caveat), and
the two visibility-predicate query rewrites (`countByTenantIdAndDestinatarioIdAndLidaFalse`,
`findByTenantIdAndDestinatarioIdAndLidaFalse`) are exercised by dedicated integration
tests that prove both the "hidden while snoozed" and "reappears once the snooze
lapses" behaviors against real Postgres. No SQL/command injection, no
authentication/authorization gaps, and no new IDOR surface were found — the snooze
endpoint mirrors the existing `marcarLida` 404-via-`Optional.empty()` contract
exactly.

Two frontend-side issues stood out under closer tracing of the new client-side
snooze-visibility filtering logic (`notification-bell.tsx`, `notificacoes/page.tsx`):
one around the interaction between the bell's fixed page size and its new
client-side snooze filter, and one around timezone-naive datetime comparisons for
the newly time-sensitive `snoozedUntil` field. Neither is a data-loss or security
risk, but both can produce user-visible incorrect state and are worth fixing.
Two lower-severity quality/test-coverage gaps are also noted.

## Warnings

### WR-01: Bell dropdown can misreport "no notifications" when snoozed items dominate the fetched page

**File:** `web/src/components/shared/notification-bell.tsx:54,61-63,116,122`
**Issue:** The bell preview fetches a fixed `size: 10` most-recent notifications
(`useNotificacoes({ size: 10 }, { poll: true })`, unfiltered by `lida`), then applies
a **client-side** filter to hide currently-snoozed items:
```tsx
const visibleNotificacoes = (list.data?.content ?? []).filter(
  (n) => !(n.snoozedUntil && new Date(n.snoozedUntil) > new Date()),
);
```
Because the snooze filter is applied *after* a fixed-size backend fetch (unlike the
badge/unread-count queries, which apply the same predicate server-side before
limiting), any of the 10 most-recent notifications that happen to be currently
snoozed are simply dropped from the list — the dropdown never backfills from
older, non-snoozed notifications beyond position 10. In the worst case (e.g. the
10 most recent notifications are all currently snoozed, but older non-snoozed/unread
notifications exist beyond that window), `visibleNotificacoes` is empty and the
dropdown renders "Sem notificações por agora." while `useNotificacoesUnreadCount()`
(which *does* apply the predicate server-side) simultaneously shows a non-zero
badge count — a directly observable contradiction to the user (badge says "3
unread", dropdown says "no notifications").
**Fix:** Apply the same snooze-visibility predicate server-side for this surface
(e.g. an optional `ocultarAdiadas`/`snoozed`-aware query param on `GET
/notificacoes` used only by the bell, mirroring `countByTenantIdAndDestinatarioIdAndLidaFalse`),
or at minimum over-fetch a larger page (e.g. `size: 20`) and slice to 10 after
filtering so a handful of currently-snoozed items in the fetched window don't
starve the visible list:
```tsx
const list = useNotificacoes({ size: 20 }, { poll: true });
// ...
const visibleNotificacoes = (list.data?.content ?? [])
  .filter((n) => !(n.snoozedUntil && new Date(n.snoozedUntil) > new Date()))
  .slice(0, 10);
```

### WR-02: Timezone-naive `LocalDateTime` used for cross-boundary snooze-active comparisons

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:62-64`,
`web/src/components/shared/notification-bell.tsx:61-63`,
`web/src/app/(dashboard)/notificacoes/page.tsx:270`
**Issue:** `snoozedUntil` is a plain `LocalDateTime` (no zone/offset). The backend
has no Jackson time-zone/`OffsetDateTime` configuration (`grep` of
`backend/src/main/resources` found no `jackson`/`time-zone` settings), so it
serializes as an ISO string with no `Z`/offset suffix (e.g.
`"2026-07-21T10:15:30"`). On the frontend, `new Date("2026-07-21T10:15:30")` is
parsed by the JS engine as **local browser time**, not as the server's local
time. If the backend host and the browser are in different time zones (a real
possibility for a legal-practice SaaS with API host and end users potentially in
different regions/VMs), the client-side "is this still snoozed?" checks —
`new Date(n.snoozedUntil) > new Date()` in `notification-bell.tsx` and
`new Date(snoozedUntil as string) > new Date()` in `notificacoes/page.tsx` — can
be off by the client/server offset, causing a still-active snooze to be shown as
expired (reappearing in the bell preview early) or an already-expired snooze to
keep showing the "Adiado até" badge/hidden state longer than intended. This
mirrors a pre-existing convention already used for `createdAt`, but this phase is
the first to make a *naive local-time comparison functionally gate visibility*
rather than just format a display label, so the blast radius of the existing
convention is now larger.
**Fix:** Serialize timestamps with an explicit zone (switch to `Instant`/
`OffsetDateTime` for `snoozedUntil`/`createdAt`, or enable
`spring.jackson.serialization.WRITE_DATES_WITH_ZONE_ID`/adopt UTC-suffixed
strings), so `new Date(...)` on the client resolves to the same instant the
server computed. Short of a broader migration, at minimum document the
assumption that server and client must share a timezone, and prefer comparing via
a value fetched fresh from the server (e.g. rely on the server-computed
`unread-count`/list filtering rather than re-deriving "is snoozed" purely
client-side) wherever the comparison result gates behavior rather than just
formatting a label.

## Info

### IN-01: New snooze endpoint has no controller-level test coverage

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:156-171`
**Issue:** `NotificacaoServiceTest` thoroughly covers `NotificacaoService.snooze()`
(valid preset, invalid preset, wrong-owner, `PRAZO_VENCIDO`), but there is no test
file for `NotificacaoController` at all (`grep` across
`backend/src/test/java/com/lexcv/controllers` found zero matches for
`notificacoes`/`NotificacaoController`). This is a pre-existing gap for the whole
controller, not introduced by this phase, but it means the controller-specific
logic added here — the `dias == null` → 400 short-circuit, and the
`catch (IllegalArgumentException) → 400` / `Optional.empty() → 404` HTTP mapping —
is exercised nowhere at the HTTP layer.
**Fix:** Add a `@WebMvcTest`/`MockMvc` (or slice) test class for
`NotificacaoController` covering at least: missing `dias` body key → 400, invalid
preset (e.g. `2`) → 400 with the service's message, unknown/foreign notification id
→ 404, and the happy path → 200 with the updated `snoozedUntil` in the response
body.

### IN-02: Snooze request body is an untyped `Map<String, Integer>`

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:157-161`
**Issue:** `@RequestBody Map<String, Integer> body` accepts any JSON object and
relies on a runtime `body.get("dias")` lookup. A key typo from a future caller
(e.g. `"Dias"`, `"days"`) silently falls through to the generic "dias é
obrigatório" 400 rather than a `@Valid`-driven, schema-checked error, and there is
no compile-time contract for API consumers to reference. This matches the
codebase's existing convention of loosely-typed `Map<String, Object>`/`Map<String,
String>` request bodies elsewhere (`ResourceController`, `AdminController`), so
it's a continuation of an established (if debatable) pattern rather than a new
regression.
**Fix:** Introduce a small `record SnoozeRequest(Integer dias) {}` DTO for this
endpoint (and consider doing the same incrementally for sibling endpoints) to get
compiler-checked field names and a self-documenting request shape.

---

_Reviewed: 2026-07-14T18:09:41Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
