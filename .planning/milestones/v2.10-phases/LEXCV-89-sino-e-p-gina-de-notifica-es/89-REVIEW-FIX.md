---
phase: 89-sino-e-pagina-de-notificacoes
fixed_at: 2026-07-10T12:13:51Z
review_path: .planning/phases/LEXCV-89-sino-e-p-gina-de-notifica-es/89-REVIEW.md
iteration: 3
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-89: Code Review Fix Report

**Fixed at:** 2026-07-10T12:13:51Z
**Source review:** .planning/phases/LEXCV-89-sino-e-p-gina-de-notifica-es/89-REVIEW.md
**Iteration:** 3 (final iteration of this auto-fix loop)

**Summary:**
- Findings in scope: 2 (fix_scope: critical_warning — WR-01, WR-02; IN-01/IN-02/IN-03 correctly excluded as Info-level)
- Fixed: 2
- Skipped: 0

Both findings in this iteration were re-openings of issues "fixed" in iteration 2 that turned out to be incomplete (a new bypass string for WR-01's URL-safety check; a new lint violation introduced by WR-03's page-clamp fix, renumbered WR-02 in this iteration's REVIEW.md). Per this iteration's explicit instructions, both fixes target the *underlying* defect class the reviewer identified — not just the newly-cited symptom string/line — since this is the last automated pass for each.

All fixes were applied in an isolated git worktree (branch `gsd-reviewfix/89-174556`, created from `master` at commit `4e29d10`). This worktree does not carry over the gitignored `web/node_modules`, so a project-wide `npx --package typescript tsc --noEmit -p tsconfig.json` reports ~5000 `Cannot find module` / cascading-`any` errors across essentially every file in `web/` (an environment artifact of the missing `node_modules`, not a code-quality signal — confirmed by the fact it affects unrelated, untouched files identically). To get a meaningful signal despite this, each touched file's error list was extracted and compared structurally: `notificacao-categoria.ts` produces **zero** tsc errors of any kind both before and after the edit (it has no `node_modules` imports), and `page.tsx`'s 77 tsc errors are byte-for-byte identical before and after the edit once the post-edit line-number shift (+10, from the added lines) is accounted for — i.e. the edit introduced no new errors and resolved none of the pre-existing environmental noise, confirming it is syntactically clean. `eslint` could not be run for the same reason as iteration 2 (`web/eslint.config.mjs` imports the local `eslint` package itself, which isn't resolvable without `node_modules` in this worktree) — consistent with the prior iteration's documented limitation, not a new gap.

Commits were made on `gsd-reviewfix/89-174556` and fast-forwarded onto `master` during this agent's cleanup step.

## Fixed Issues

### WR-01: `isInternalLinkUrl` still allows off-origin navigation — TAB/CR/LF characters bypass the position-based check

**Status:** fixed: requires human verification
**Files modified:** `web/src/lib/notificacao-categoria.ts`
**Commit:** 54db769
**Applied fix:** Replaced the character-position approach entirely (this function's third such patch in three review iterations) with the reviewer's exact recommended parser-based check: construct `new URL(url, INTERNAL_URL_SENTINEL)` (sentinel = `"http://internal.invalid"`) and accept only when the resulting `.origin` still equals the sentinel. This asks the same WHATWG URL parser the browser and Next.js use whether the value introduces its own authority component, instead of re-implementing a hand-rolled subset of that logic — closing the entire bypass *class* (protocol-relative `//`, backslash-as-slash, and this iteration's embedded TAB/LF/CR-stripping quirk) rather than one more specific string. Also rewrote the function's doc comment to describe the parser-based invariant instead of the now-obsolete character-position rationale, and to record the two prior patch attempts so a future maintainer doesn't reintroduce a hand-rolled check.
**Extra verification performed:** Before applying, empirically re-ran the reviewer's exact proposed snippet (standalone, scratchpad-only, not committed) against 15 cases: ordinary internal paths (`/processos/123`, `/`, `/notificacoes?tab=x` — must return `true`); empty string, `null`, `undefined`, non-rooted relative path (`relative/path` — must return `false`); every bypass string from all three review iterations — `//evil.com`, `//evil.example.com`, backslash variants (`/\evil.example.com`, `/\/evil.example.com`, `/\\evil.example.com`), and this iteration's embedded-control-character variants (`/\t/evil.com`, `/\n/evil.com`, `/\r/evil.com`, plus an additional untested combination `/\t\evil.com`) — all must return `false`. All 15 passed (an initial inline-shell invocation of this same test produced one apparent mismatch on a backslash case; re-running the identical script from a file rather than inline confirmed that was a shell quoting artifact of the test harness, not the function — the function itself was correct in both runs). Post-edit, confirmed via the project-wide `tsc` check described above that `notificacao-categoria.ts` has zero errors.
**Recommended human check:** Same as iteration 2 — craft a `Notificacao` row with a control-character-bearing `linkUrl` (e.g. a direct dev-DB update, since no current backend code path sets `linkUrl` to anything but a hardcoded-prefix + UUID) and confirm the bell/page render it as plain non-clickable text rather than as a `<Link>`. Given this exact function has now been re-opened in all three review iterations, also worth a final skim of the new implementation itself (4 lines, `web/src/lib/notificacao-categoria.ts:74-83`) since it is the last automated attempt.

### WR-02: The `/notificacoes` page-clamp fix derives state in an effect — `eslint` `react-hooks/set-state-in-effect` error

**Status:** fixed: requires human verification
**Files modified:** `web/src/app/(dashboard)/notificacoes/page.tsx`
**Commit:** 4ea450a
**Applied fix:** Removed the `React.useEffect` that clamped `page` back into range, replacing it with React's documented "adjusting state when a prop changes" pattern applied directly in the render body: a `lastTotalPages` state variable tracks the last-seen `list.data.totalPages`; whenever it differs from the current value, the render body updates `lastTotalPages` and — only if the new `totalPages > 0` and the current `page` is now out of range — calls `setPage` in the same pass. Calling `setState` conditionally during render (guarded so it only fires when `totalPages` actually changed) lets React discard the in-progress render and immediately re-render with corrected state before anything commits/paints, instead of committing the stale/out-of-range page first and correcting it a frame later (the visible flash the review reported). No setter is called from inside a `useEffect` body, which is what the lint rule flags.
**Extra verification performed:** Traced all scenarios the review and iteration-2's fix report exercised against the new code: (1) initial mount — `list.data` undefined, guard no-ops; (2) first successful fetch — `lastTotalPages` (initialized `undefined`) differs from the real value, updates once, converges on the next internal render pass since `lastTotalPages` then matches; (3) the review's repro (filter to "Não lidas", last page, mark last item read, `totalPages` shrinks) — clamp fires in the same pass that detects the `totalPages` change, so `page` is already corrected by the first commit the user sees, instead of a second one; (4) legitimately-empty-after-filter (`totalPages === 0`) — `lastTotalPages` still updates (so a later non-zero value is detected correctly) but `setPage` is not called, leaving the empty-state UI intact, matching the pre-existing (correct) behavior; (5) user-triggered pagination (`Anterior`/`Seguinte`) and filter changes — unaffected, since those already call `setPage`/reset directly from their own click handlers, not through this derived block. Confirmed via the structural `tsc` diff described above that the edit introduces no new errors in `page.tsx` (77 errors before and after, identical modulo the expected +10 line-number shift from the added lines — all pre-existing `Cannot find module` / cascading-`any` noise from the worktree's missing `node_modules`).
**Recommended human check:** Reproduce the review's exact repro (filter to "Não lidas", navigate to the last page, mark the last remaining item as read) and confirm the view lands directly on the corrected last-valid page with no visible flash of "Nenhuma notificação encontrada" beforehand. Since `eslint` could not be executed in this sandboxed worktree (see Summary), also worth confirming locally that `pnpm lint` (or equivalent) no longer reports `react-hooks/set-state-in-effect` for this file.

## Skipped Issues

None — both in-scope findings were fixed.

---

_Fixed: 2026-07-10T12:13:51Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
