---
phase: 99-webpage-nova-app-next-js-de-landing
fixed_at: 2026-07-15T12:27:12Z
review_path: .planning/milestones/v2.12-phases/LEXCV-99-webpage-nova-app-next-js-de-landing/99-REVIEW.md
iteration: 3
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase 99: Code Review Fix Report

**Fixed at:** 2026-07-15T12:27:12Z
**Source review:** .planning/milestones/v2.12-phases/LEXCV-99-webpage-nova-app-next-js-de-landing/99-REVIEW.md
**Iteration:** 3 (final)

**Summary:**
- Findings in scope: 1 (1 critical, 0 warning — `fix_scope: critical_warning`; this round's Info findings IN-01 through IN-04 were left out of scope, as in iteration 2)
- Fixed: 1
- Skipped: 0

This is the third and final fix pass for this phase. The iteration-3 re-review independently re-verified iteration 2's `8aeb1a8` commit (which fixed the prior WR-01 — unvalidated/unnormalized `BACKEND_API_ORIGIN`) via an executable reproduction, confirmed the literal WR-01 symptom was genuinely resolved, but found that the fix itself introduced a fresh, more severe Critical regression (this round's CR-01, unrelated to the round-1 CR-01 of the same ID, which remains resolved). That regression is what this pass fixes.

## Fixed Issues

### CR-01: `getBackendOrigin()` validation ran outside every fail-open catch — a scheme-less `BACKEND_API_ORIGIN` crashed the entire public site instead of degrading gracefully

**Files modified:** `webpage/src/lib/setup.ts`, `webpage/src/lib/branding.ts`, `webpage/src/lib/backend-origin.ts` (comment only — see below)
**Commit:** `5c07863`
**Applied fix:** Read the current source first to confirm it matched the finding exactly — `setup.ts:4` and `branding.ts:4` both still called `const backendOrigin = getBackendOrigin();` at module scope, outside any function, exactly as described. Applied the fix suggested by the review, adapted slightly for clarity:

- `webpage/src/lib/setup.ts`: removed the module-scope `const backendOrigin = getBackendOrigin();` and `const setupStatusUrl = ...` lines; both now live inside `fetchSetupStatus()`'s body, computed fresh on every call. Because `fetchSetupStatus` is an `async function`, a synchronous throw from `getBackendOrigin()` inside its body is automatically converted into the function's rejected return Promise — so `proxy.ts`'s existing `try { await fetchSetupStatus(); } catch { return NextResponse.next(); }` now catches it, exactly like a network error would be caught. No change was needed in `proxy.ts` itself; its try/catch already existed and simply now actually gets a chance to run.
- `webpage/src/lib/branding.ts`: moved the module-scope `const backendOrigin = getBackendOrigin();` to the first line inside `fetchBranding()`'s existing `try { ... } catch { return FALLBACK; }` block. A thrown validation error is now caught by the same `catch` that already handles network/timeout/JSON errors, and returns the same `FALLBACK` (`{ nome: "LexCV", logoDataUrl: null }`) a visitor would see during a transient backend outage.
- `webpage/src/lib/backend-origin.ts`: the validation logic itself (the `getBackendOrigin()` function body — scheme check, trailing-slash trim) was left **byte-for-byte unchanged**, per the fix instructions. Only the doc comment above the function (which explicitly documented the now-removed "evaluated once at module load, outside any try/catch" design) was corrected, since leaving it as-is would actively mislead a future reader/agent back into reintroducing this exact regression by module-scoping the call again. This file was already listed in the finding's own **File:** range for this reason.

**Verification performed (in order):**
1. **Tier 1** — re-read all three modified files in full; confirmed the fix text is present and no surrounding code was corrupted.
2. **Tier 2** — `npx tsc --noEmit -p tsconfig.json` run against the full `webpage` project (via a temporary `node_modules` symlink into the already-installed main-repo `webpage/node_modules`, since the isolated fix worktree has no install of its own): **exit 0, zero errors.** Also ran `npx eslint` on all four touched/related files (`setup.ts`, `branding.ts`, `backend-origin.ts`, `proxy.ts`): **exit 0, zero warnings/errors.**
3. **Behavioral reproduction** (explicitly requested for this fix, over and above the standard 3-tier check) — built a faithful line-for-line mirror of the fixed import chain (`backend-origin.mjs` → `setup.mjs`/`branding.mjs` → `app.mjs`, with `fetch` stubbed so no live backend/network is needed) and exercised it, in a fresh process per case, for all four `BACKEND_API_ORIGIN` inputs the reviewer used:

   ```
   BACKEND_API_ORIGIN="http://localhost:8080"   -> IMPORT_OK, proxy via "try" (success), page branding from stub
   BACKEND_API_ORIGIN="http://localhost:8080/"  -> IMPORT_OK, proxy via "try" (success), page branding from stub
   BACKEND_API_ORIGIN="localhost:8080"          -> IMPORT_OK (no crash), proxy via "catch (fail-open)", page branding = FALLBACK
   (unset)                                       -> IMPORT_OK (no crash), proxy via "catch (fail-open)", page branding = FALLBACK
   ```

   The third case is the exact regression scenario from this round's CR-01: previously `IMPORT_CRASHED` (module import itself threw, before either `proxy()`'s or `fetchBranding()`'s try/catch could run); now `IMPORT_OK` in every case, with the validation error correctly routed to each function's existing fail-open/fallback path instead of an unhandled synchronous throw. The well-formed and trailing-slash cases (1 and 2) are unaffected, confirming iteration 2's WR-01 fix is preserved.

This finding does not fall under the "logic error requiring human verification" caveat — it is a control-flow/scoping fix (moving an existing call site, not altering a condition or algorithm), and its correctness was confirmed empirically via the reproduction above, not just by syntax checking.

**Note for the orchestrator:** IN-03 (`webpage/proxy.ts`'s catch block has no logging) was explicitly flagged by the reviewer as "a necessary companion, not just a nice-to-have" now that this fix restores the fail-open path for a real misconfiguration — without it, a scheme-less `BACKEND_API_ORIGIN` in production will fail open *silently*, with no signal anything is wrong. It remains out of scope for this pass (`fix_scope: critical_warning` excludes Info-level findings, and the task instructions for this run scoped the fix to CR-01 only), but is worth a deliberate follow-up decision rather than staying open by default.

## Skipped Issues

None — the single in-scope finding was fixed. (Info findings IN-01 through IN-04 were intentionally excluded per `fix_scope: critical_warning` and remain open exactly as described in `99-REVIEW.md`; this is the final iteration of the auto-fix loop, so they will need to be addressed manually if desired.)

---

_Fixed: 2026-07-15T12:27:12Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
