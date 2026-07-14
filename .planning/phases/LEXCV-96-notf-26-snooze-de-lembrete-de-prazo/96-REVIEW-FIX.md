---
phase: LEXCV-96-notf-26-snooze-de-lembrete-de-prazo
fixed_at: 2026-07-14T18:28:34Z
review_path: .planning/phases/LEXCV-96-notf-26-snooze-de-lembrete-de-prazo/96-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase LEXCV-96: Code Review Fix Report

**Fixed at:** 2026-07-14T18:28:34Z
**Source review:** .planning/phases/LEXCV-96-notf-26-snooze-de-lembrete-de-prazo/96-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 1 (fix_scope: critical_warning — 0 critical, 1 warning; IN-01/IN-03/IN-04 out of scope)
- Fixed: 1
- Skipped: 0
- Bonus (out-of-scope, applied anyway): IN-02 — trivial one-line dead-code cleanup directly adjacent to the WR-01 fix, applied at the reviewer's/orchestrator's explicit invitation even though it is Info-tier and outside `critical_warning` scope.

## Fixed Issues

### WR-01: `PATCH /notificacoes/{id}/snooze` throws an unhandled NullPointerException on a null JSON body instead of returning 400

**Files modified:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java`
**Commit:** 46c801a
**Applied fix:** Re-read the handler and confirmed it matched the review's description exactly:
`Integer dias = body.get("dias");` dereferenced `body` with no null check, so a literal JSON
`null` request body (which Jackson deserializes to a `null` `Map` reference, distinct from an
*absent* body which Spring's own `@RequestBody`-required check already rejects) would throw an
NPE that `GlobalExceptionHandler`'s catch-all turns into a leaky 500. Applied the review's
suggested fix verbatim (the minimal null-guard, not the alternative typed-DTO refactor from
IN-04, which is a larger change out of scope for this warning): changed the line to
`Integer dias = body != null ? body.get("dias") : null;`, so a null body now falls through the
existing `dias == null` check and returns the intended `400 {"message": "dias é obrigatório"}`
instead of a 500. Verified by re-reading the modified method in full (lines 156-171) — the
surrounding try/catch, `IllegalArgumentException` → 400, and `Optional.empty()` → 404 mapping are
unchanged. No Java syntax checker is available for single-file compilation in this project
without a full Maven build (not in the verification_strategy tool table), so verification relied
on Tier 1 (re-read) only, per the "Other: skip to Tier 1" rule.

## Bonus Fix (out of critical_warning scope, applied anyway)

### IN-02: Leftover redundant `.slice(0, 10)` in the bell dropdown render

**Files modified:** `web/src/components/shared/notification-bell.tsx`
**Commit:** ba1ebcc
**Applied fix:** Confirmed `visibleNotificacoes` (defined a few lines above) already ends in
`.slice(0, 10)` after the snooze-visibility filter, making the render-time
`visibleNotificacoes.slice(0, 10).map(...)` a no-op re-slice of an already-bounded array. Removed
the redundant second `.slice(0, 10)` so the render now reads `visibleNotificacoes.map((n) => (`.
Purely cosmetic/dead-code removal — no behavior change. `npx tsc --noEmit` is unavailable in this
worktree (TypeScript is not resolvable via `npx` without a `node_modules` install, which isn't
present in the ephemeral fix worktree), so verification fell back to Tier 1 (full re-read of the
surrounding render block, lines 118-132) confirming the JSX structure and closing tags are
intact.

## Skipped Issues

None — the single in-scope finding (WR-01) was fixed, and the optional bonus item (IN-02) was
also applied successfully.

---

_Fixed: 2026-07-14T18:28:34Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
