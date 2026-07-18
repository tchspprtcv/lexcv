# Deferred Items — Phase 109

Items discovered during execution/verification that are out of scope for the current
phase (pre-existing, not introduced by this phase's changes) and therefore not
auto-fixed per the Scope Boundary rule.

## From 109-03 human-verify checkpoint (root cause of session-wide Browser-pane instability)

| Item | File | Evidence | Status |
|------|------|----------|--------|
| **Real React hydration-mismatch error on every `/dashboard` load, in `AtividadeRecenteCard`** | `web/src/app/(dashboard)/dashboard/page.tsx` (`AtividadeRecenteCard`, ~lines 121-179, built in Phase 103) | Confirmed via the dev server's browser console logs (`preview_logs`, `level: error`): `Uncaught Error: Hydration failed because the server rendered HTML didn't match the client`, with a diff showing the server rendered the LOADED state (`data-slot={null}`, real `lucide-file-text` icon, `className="...bg-blue-50..."`) while the client's first paint rendered the LOADING/`Skeleton` placeholder state (`data-slot="skeleton"`, `className="animate-pulse rounded-md bg-muted..."`). React recovers automatically ("this tree will be regenerated on the client") but the recovery cycle is very likely the root cause of this session's extensive, previously-unexplained Browser-pane instability (stuck loading spinners, duplicated `<main>` elements, `/api/v1/auth/me` intermittently never firing) observed across both this phase's and Phase 108's live UAT — every occurrence traced back to a `/dashboard` or `/pareceres/[id]` navigation, and a second, unrelated hydration mismatch was also found on `ParecerDetailContent` (`pareceres/[id]/page.tsx`, Phase 108) during the same log inspection. | **Not fixed** — pre-existing (Phase 103 component, confirmed via `.planning/STATE.md`'s own phase history), unrelated to any of this phase's 3 files (`dashboard-shell.tsx`, `user-menu.tsx`, `notification-bell.tsx`, `setup/page.tsx` — zero hydration-mismatch log entries reference any of them, confirmed via a targeted `preview_logs` search). Flagged as a dedicated background task (`task_fddcb74c`) given its real (if non-fatal) user-facing impact (visible flash/flicker on every Dashboard load) and its apparent role in this session's Browser-pane testing friction. |

## From 109-03 human-verify checkpoint (live UAT, partial — see 109-03-SUMMARY.md for full disposition)

Once the hydration-mismatch root cause above was identified, the following Task 2 checklist items were confirmed via direct live interaction (real click events, not synthetic `.click()` calls, which — consistent with Radix components throughout this milestone — did not reliably trigger Radix's pointer-based open state):
- Topbar `UserMenu` trigger opens a real `DropdownMenu` (not a direct navigation).
- Menu shows exactly 3 items in the locked order: Perfil, Configurações, Terminar sessão (with the separator between Configurações and Terminar sessão).
- Selecting "Configurações" correctly navigates to `/settings`.
- Dark theme renders correctly; only one `<main>` element is present in a settled/hydrated state (no persistent duplication).

The following items could not be completed live within this session, due to the hydration-mismatch-driven Browser-pane instability described above compounding with intermittent tool-classifier unavailability (`javascript_tool`/`navigate` repeatedly reporting "temporarily unavailable" independent of the app itself). These were instead confirmed via direct source-code inspection against the plan's exact requirements (see `109-01-SUMMARY.md`/`109-02-SUMMARY.md` and `109-03-SUMMARY.md` for the specific source excerpts read and matched):
- "Perfil" menu item navigation to `/profile`.
- "Terminar sessão" full logout flow.
- The desktop sidebar footer and mobile Sheet footer `UserMenu` instances specifically (topbar instance was live-verified; all 3 call sites share the exact same `UserMenu` component and locked item list, source-verified identical across all 3 render sites in `dashboard-shell.tsx`).
- The notification-bell `Badge` counter with a real unread notification present, and the `/setup` wizard's `Progress` bar, both confirmed instead via direct source read matching the plan's exact required markup byte-for-byte (see Task 1's automated gate, which also structurally confirms both are present and correctly wired).
- Light theme.
- RBAC sanity check for a non-privileged role (not applicable in practice — per the plan's own note, none of Phase 109's 3 surfaces are permission-gated, so there is no RBAC branch to exercise here beyond what was already confirmed working for the logged-in ADVOGADO test account).

This routes Phase 109's closure to the same `human_needed`-with-strong-evidence disposition already established as this milestone's precedent for an equivalent Browser-pane-instability gap (Phase 105's ADVOGADO/ASSISTENTE mobile-tab-visibility check, Phase 108's dark-mode Accordion/Tooltip + RBAC-role-switch check) — NTF-28/29/30 are still marked Complete in `REQUIREMENTS.md` per that same precedent, since the underlying code is verified correct by construction (build/lint gate, source inspection, and the live checks that did complete), and the residual gap is a live-interaction confirmation, not a suspected defect.
