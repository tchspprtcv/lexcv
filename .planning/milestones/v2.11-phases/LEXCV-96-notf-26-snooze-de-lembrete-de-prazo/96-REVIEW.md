---
phase: LEXCV-96-notf-26-snooze-de-lembrete-de-prazo
reviewed: 2026-07-14T18:30:00Z
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
  warning: 1
  info: 4
  total: 5
status: issues_found
---

# Phase LEXCV-96: Code Review Report

**Reviewed:** 2026-07-14T18:30:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

This is a re-review of the NOTF-26 snooze feature after the fixer applied both
warnings from the prior review iteration (commits `626279e` WR-01 and `5bdecf1`
WR-02, per `96-REVIEW-FIX.md`). Both fixes were independently verified against the
current file state and are correct:

- `notification-bell.tsx` now fetches `size: 20` and slices the filtered list to 10
  (`useNotificacoes({ size: 20 }, ...)` + `.slice(0, 10)` at the `visibleNotificacoes`
  definition), reducing the previous badge/dropdown contradiction from "guaranteed at
  10 snoozed items" to "only if 11+ of the 20 most recent are currently snoozed."
- `Notificacao.snoozedUntil` now carries `@JsonFormat(pattern =
  "yyyy-MM-dd'T'HH:mm:ss'Z'")`, so `new Date(n.snoozedUntil)` on the client resolves
  to the same UTC instant the server computed, fixing the timezone-naive
  cross-boundary comparison used to gate visibility in both `notification-bell.tsx`
  and `notificacoes/page.tsx`.

No new critical issues were found: tenant/destinatario scoping is still consistently
derived from the JWT, the preset validation (1/3/7) still happens before any
repository write, and the `PRAZO_VENCIDO` non-adiável guard is unchanged and
correctly reused. No SQL/command injection, no auth bypass, no new IDOR surface.

One new warning surfaced on closer tracing of the new `PATCH /notificacoes/{id}/snooze`
endpoint's request-body handling: a literal-`null` JSON body causes an unhandled
`NullPointerException` rather than the intended 400. Four lower-severity items round
out the report: a residual (much less likely, but not eliminated) instance of the
same class of bug the WR-01 fix addressed, a small piece of now-dead code left over
from that fix, and the two previously-reported, still-unaddressed `IN-01`/`IN-02`
items (out of scope for the prior auto-fix, which was limited to
critical+warning-tier findings) confirmed still present in the current code.

## Warnings

### WR-01: `PATCH /notificacoes/{id}/snooze` throws an unhandled NullPointerException on a null JSON body instead of returning 400

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:157-161`
**Issue:** The handler reads the preset directly off the deserialized body:
```java
public ResponseEntity<?> snooze(@PathVariable UUID id, @RequestBody Map<String, Integer> body) {
    Integer dias = body.get("dias");
    if (dias == null) { ... }
```
`body` itself is never null-checked. A request whose JSON payload is the literal
`null` (a valid JSON document, `Content-Type: application/json`) deserializes via
Jackson to a `null` `Map` reference; Spring passes that `null` straight into the
method parameter (this differs from an *absent* body, which Spring's own
`@RequestBody`-required check rejects earlier with a clean 400). `body.get("dias")`
then throws a `NullPointerException`. This is not caught anywhere in the call chain:
`backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` has a catch-all
`@ExceptionHandler(Exception.class)` that runs ahead of Spring's own default
`HttpMessageNotReadableException` → 400 mapping (Spring resolves `@ControllerAdvice`
`@ExceptionHandler` methods before `DefaultHandlerExceptionResolver`), so the NPE is
turned into a `500` response whose body echoes the raw exception class/message
(`{"error":"NullPointerException","message":"Cannot invoke \"java.util.Map.get(Object)\" because \"body\" is null"}`)
back to the client via `apiFetch`'s `json.message || json.error` fallback — an
authenticated but otherwise well-behaved-looking request produces an internal-error
response with an implementation-detail message instead of the intended validation
error. This specific manifestation is new code introduced by this phase (the
sibling `silenciar`/`marcarLida` endpoints don't take a body at all); the underlying
"generic `Exception.class` handler shadows Spring's built-in `400` mapping" behavior
in `GlobalExceptionHandler` is pre-existing and out of this review's file scope, but
this endpoint is the concrete place a defensive null-check would close the gap.
**Fix:**
```java
Integer dias = body != null ? body.get("dias") : null;
if (dias == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "dias é obrigatório"));
}
```
or switch the parameter to a small typed DTO (see IN-02) which sidesteps the raw
`Map` null-dereference entirely.

## Info

### IN-01: WR-01 fix reduces, but does not eliminate, the bell dropdown/badge contradiction

**File:** `web/src/components/shared/notification-bell.tsx:58,65-67`
**Issue:** The applied fix (`size: 20` + `.slice(0, 10)` after filtering) makes the
scenario from the previous review's WR-01 much less likely but not impossible: if
11 or more of the 20 most-recently-fetched notifications are currently snoozed, the
post-filter list can still shrink below what's needed to fill the visible 10-item
window (in the extreme, all 20 snoozed → `visibleNotificacoes` is still empty),
while `useNotificacoesUnreadCount()` (server-side filtered) can simultaneously
report a non-zero count. This was the explicitly-accepted tradeoff documented in
`96-REVIEW-FIX.md` ("minimal fix... consistent with this codebase's existing
patterns" vs. the larger server-side-filter alternative), so this is not a new
regression, just a residual risk worth tracking rather than considering fully
closed.
**Fix:** If this edge case is hit in practice, the full fix from the original
review still applies: add a server-side `snoozed`/`ocultarAdiadas`-aware query
option to `GET /notificacoes` (mirroring
`countByTenantIdAndDestinatarioIdAndLidaFalse`'s predicate) so the bell's fetch
and the badge's count are computed with the exact same filter, eliminating the
possibility of disagreement regardless of how many items in a fixed-size window
happen to be snoozed.

### IN-02: Leftover redundant `.slice(0, 10)` in the bell dropdown render

**File:** `web/src/components/shared/notification-bell.tsx:65-67,126`
**Issue:** The WR-01 fix moved the `.slice(0, 10)` into the `visibleNotificacoes`
definition:
```tsx
const visibleNotificacoes = (list.data?.content ?? [])
  .filter((n) => !(n.snoozedUntil && new Date(n.snoozedUntil) > new Date()))
  .slice(0, 10);
```
but the render code still re-slices the already-≤10-item array:
```tsx
{visibleNotificacoes.slice(0, 10).map((n) => (
```
This second `.slice(0, 10)` is now a no-op (dead code) — harmless functionally, but
it's leftover from before the fix and obscures that `visibleNotificacoes` is
already the final, bounded list.
**Fix:**
```tsx
{visibleNotificacoes.map((n) => (
```

### IN-03: Snooze endpoint still has no controller-level test coverage

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:156-171`
**Issue:** Confirmed still true on re-review: `NotificacaoServiceTest` thoroughly
covers `NotificacaoService.snooze()` (valid preset, invalid preset, wrong-owner,
`PRAZO_VENCIDO`), and `NotificacaoRepositoryIT` covers the visibility-predicate
queries against real Postgres, but there is still no test file for
`NotificacaoController` at all (`backend/src/test/java/com/lexcv/controllers`
has no `NotificacaoController`/`notificacoes` match). The controller-specific logic
added in this phase — the `dias == null` → 400 short-circuit, the
`catch (IllegalArgumentException) → 400` / `Optional.empty() → 404` HTTP mapping,
and (per WR-01 above) the null-body edge case — remain untested at the HTTP layer.
This was flagged as out-of-scope info in the prior review iteration and was
correctly not touched by the auto-fix (`fix_scope: critical_warning`); still valid
today.
**Fix:** Add a `@WebMvcTest`/`MockMvc` test class for `NotificacaoController`
covering: missing `dias` key → 400, `null` body → 400 (once WR-01 above is fixed),
invalid preset → 400, unknown/foreign id → 404, and the happy path → 200 with
`snoozedUntil` populated in the response.

### IN-04: Snooze request body is still an untyped `Map<String, Integer>`

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:157-161`
**Issue:** Confirmed still true on re-review: `@RequestBody Map<String, Integer>
body` accepts any JSON object shape and relies on a runtime `body.get("dias")`
lookup, with no compile-time contract for API consumers and (per WR-01 above) no
null-safety on `body` itself. Matches the codebase's existing convention of
loosely-typed request-body maps elsewhere (`ResourceController`), so this is a
continuation of an established pattern, not a new regression — but a typed DTO
would resolve both this and WR-01 in one change.
**Fix:** Introduce `record SnoozeRequest(Integer dias) {}` and change the handler
signature to `@RequestBody SnoozeRequest body`; `body.dias()` is then still
nullable (so the existing `dias == null` → 400 check is preserved) but `body`
itself can only be null in the same "literal JSON null" edge case, which is easy
to guard once in one place, or eliminated by adding `@Valid` + `@NotNull` on the
record component to let Spring's existing `MethodArgumentNotValidException` handler
(already wired in `GlobalExceptionHandler`) produce the 400 automatically.

---

_Reviewed: 2026-07-14T18:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
