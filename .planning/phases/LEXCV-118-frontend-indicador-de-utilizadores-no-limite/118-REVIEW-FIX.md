---
phase: LEXCV-118-frontend-indicador-de-utilizadores-no-limite
fixed_at: 2026-07-29T05:49:33Z
review_path: .planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-118-frontend-indicador-de-utilizadores-no-limite: Code Review Fix Report

**Fixed at:** 2026-07-29T05:49:33Z
**Source review:** .planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (WR-01, WR-02 — the only two Warnings marked actionable; WR-03/WR-04 are accepted trade-offs excluded from this run, Info items out of scope)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: `tenant_plano`'s frontend type omits `| null`, contradicting the backend's own tested contract

**Files modified:** `web/src/types/auth.ts`, `web/scripts/verify-limite-utilizadores-indicator.mjs`
**Commit:** `6ff042b`
**Applied fix:** Changed `MeResponse.tenant_plano` from `tenant_plano?: string;` to `tenant_plano?: string | null;` in `web/src/types/auth.ts`, matching the sibling field `tenant_limite_utilizadores?: number | null;` exactly, as specified in the review.

Additionally updated `web/scripts/verify-limite-utilizadores-indicator.mjs`'s `types-auth-tenant-plano` assertion, which was not mentioned in the review's Fix section but is a direct, necessary consequence of it: that assertion did `authTypes.includes("tenant_plano?: string;")` — a substring check for the exact pre-fix (buggy) type declaration. After the WR-01 fix, that substring no longer exists in the file (it's now `tenant_plano?: string | null;`), so the gate would fail on a correct change if left as-is. Updated the assertion's string and description to check for `tenant_plano?: string | null;`, mirroring the already-correct `types-auth-tenant-limite` assertion's wording pattern. Verified this was the only one of the script's 8 assertions affected by either fix (re-ran the script locally: 8/8 PASS after the change).

### WR-02: New "X/Y utilizadores" indicator silently reports "0 utilizadores" (and leaves "Novo Utilizador" enabled) when the user list fails to load

**Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** `a18217c`
**Applied fix:** Destructured `isError` and `refetch` from `useAdminUsers()` in `UserManagementTab`, and added an `if (isError) return (...)` block immediately after the existing `if (isLoading)` block, before `filteredUsers` is computed. Rather than applying the review's minimal Fix snippet verbatim (message-only, no retry action), followed the task's explicit instruction to match "whatever error-display pattern already exists elsewhere in this same file for consistency": the new block mirrors `NotificationPreferencesTab`'s existing `isError` block in the same file exactly — same container classes (`flex flex-col items-center justify-center gap-3 h-48 text-center px-4`), same `AlertCircle` icon, plus a "Tentar novamente" retry button wired to `refetch()` (both `AlertCircle`, `RotateCcw`, and `Button` were already imported in this file; no new imports needed). Message text ("Não foi possível carregar a lista de utilizadores.") taken verbatim from the review's suggested fix.

## Verification

All three commands requested were run from `web/` after both fixes, in an isolated git worktree with a fresh `pnpm install`, and all passed:

- `pnpm verify:limite-utilizadores` — 8/8 PASS (`types-auth-tenant-plano`, `types-auth-tenant-limite`, `toast-prefix-generico`, `use-me-auto-fetch`, `contagem-estrita`, `copy-contract`, `span-wrapper-tooltip`, `layout-stack`)
- `pnpm lint` — 0 errors, 18 warnings (identical count/content to the review's stated pre-existing baseline; the one warning attributed to `settings/page.tsx` (`@next/next/no-img-element` at the pre-existing `<img src={user.avatar_url}>` element) is unchanged code that simply shifted line number due to the WR-02 insertion above it — confirmed by isolating `eslint` to just the 3 modified files)
- `pnpm build` — compiled and type-checked successfully (`✓ Compiled successfully`, `Finished TypeScript`), all 24 routes generated including `/settings`

Notable environment issue encountered and resolved (documented for transparency, no source changes resulted from it): the first worktree attempt was created nested under this session's scratchpad directory (`AppData\Local\Temp\claude\<repo>\<uuid>\scratchpad\...`), whose path depth pushed pnpm's `.pnpm` virtual-store paths for `next` past Windows' 260-character `MAX_PATH`, which made Turbopack's workspace-root inference fail (`next build` errored with "couldn't find the Next.js package" / "Symlink node_modules is invalid"). Recreated the worktree at a shorter path (`/tmp/sv-118-reviewfix-<random>`, i.e. directly under `AppData\Local\Temp`) and re-ran `pnpm install` there, which resolved it without any product/config changes. `next.config.ts` was never modified in the commits — a temporary `turbopack.root` override tried as a diagnostic step at the old (long) path was reverted along with that entire discarded worktree attempt before any commits were made.

## Skipped Issues

None — both in-scope findings were fixed.

---

_Fixed: 2026-07-29T05:49:33Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
