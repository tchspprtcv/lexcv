---
phase: LEXCV-96-notf-26-snooze-de-lembrete-de-prazo
fixed_at: 2026-07-14T18:16:34Z
review_path: .planning/phases/LEXCV-96-notf-26-snooze-de-lembrete-de-prazo/96-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-96: Code Review Fix Report

**Fixed at:** 2026-07-14T18:16:34Z
**Source review:** .planning/phases/LEXCV-96-notf-26-snooze-de-lembrete-de-prazo/96-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (fix_scope: critical_warning — CR-*/BL-* + WR-*; IN-01/IN-02 out of scope)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: Bell dropdown can misreport "no notifications" when snoozed items dominate the fetched page

**Files modified:** `web/src/components/shared/notification-bell.tsx`
**Commit:** 626279e
**Applied fix:** Bumped the bell dropdown's `useNotificacoes` fetch size from `10` to `20`, and
changed the client-side snooze-visibility filter to `.slice(0, 10)` after filtering rather than
before. This matches the review's suggested minimal fix (over-fetch and slice) rather than the
larger alternative (a new server-side `snoozed`-aware query param), since it resolves the
observable badge/dropdown contradiction with a one-line change consistent with this codebase's
existing patterns. Verified via `npx tsc --noEmit` on the file (no errors) and a full re-read of
the surrounding component.

### WR-02: Timezone-naive `LocalDateTime` used for cross-boundary snooze-active comparisons

**Files modified:** `backend/src/main/java/com/lexcv/models/Notificacao.java`
**Commit:** 5bdecf1
**Applied fix:** Checked the project's existing convention first (per the review's own
instruction) — grepped for `@JsonFormat`/Jackson timezone config project-wide and found none;
every `LocalDateTime` field (including `createdAt`) is serialized naive with no zone/offset. Since
no established convention exists, applied the review's suggested smallest fix: annotated
`snoozedUntil` with `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")` so the wire format carries
an explicit (literal) UTC `Z` suffix. This is truthful because the codebase already documents
elsewhere (`AlertasDiariosJob`'s `FUSO_CABO_VERDE` comment) that the container always runs in
UTC, so `LocalDateTime.now()` on the server is already a UTC instant in practice — the fix simply
makes that fact explicit in the JSON the frontend parses, so `new Date(snoozedUntil)` on the
client now resolves to the same instant the server computed, regardless of the browser's local
timezone. Scoped to `snoozedUntil` only (not `createdAt`), matching the review's own distinction:
`createdAt` is only ever used to format a display label (`toLocaleDateString`), never to gate
visibility, so its naive-local-time serialization is unchanged and out of scope. No frontend
changes were needed — `new Date("...Z")` already parses correctly as UTC in both
`notification-bell.tsx` and `notificacoes/page.tsx`. Documented the new convention inline as a
comment on the field for future time-sensitive fields that gate behavior. Verified via a full
re-read of the file and a full `mvn -o compile` of the backend module (no errors).

## Skipped Issues

None — both in-scope findings were fixed.

---

_Fixed: 2026-07-14T18:16:34Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
