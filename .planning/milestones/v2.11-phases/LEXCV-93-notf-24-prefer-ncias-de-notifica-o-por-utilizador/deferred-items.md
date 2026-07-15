# Deferred Items — Phase 93

Out-of-scope discoveries logged during plan execution. Not fixed (per executor scope-boundary rule: only auto-fix issues directly caused by the current task's changes).

## Plan 93-04 (frontend: types, hook, /settings preferences tab)

**Missing `node_modules` (fixed, not deferred):** This worktree had no `node_modules` at all when execution started — `rtk exec tsc`/`rtk lint` were silently reporting false-positive success ("No errors found") through a stale cache/filter rather than actually invoking the tools. Ran `pnpm install` (Rule 3, blocking-issue auto-fix) before any real verification; re-ran all checks via `rtk proxy` (unfiltered) afterward.

**Pre-existing `vitest` type errors (3, unrelated files):** `pnpm exec tsc --noEmit` reports `Cannot find module 'vitest'` in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, `src/schemas/clientes.legacy-documento-tipo.test.ts`. `vitest` is not declared in `package.json` at all. None of these files are touched by this plan (`web/src/types/notificacoes.ts`, `web/src/hooks/use-notificacao-preferencias.ts`, `web/src/app/(dashboard)/settings/page.tsx`). Confirmed via `tsc --noEmit` output filtered for this plan's files: zero matches.

**Pre-existing lint errors/warnings (6 errors, 17 warnings, unrelated files):** `pnpm lint` reports the exact same 6 errors / 17 warnings already documented in Phase 89's `deferred-items.md` (`.planning/milestones/v2.10-phases/LEXCV-89-sino-e-p-gina-de-notifica-es/deferred-items.md`) — `react-hooks/set-state-in-effect`, `react-hooks/incompatible-library`, `react-hooks/refs`, `@next/next/no-img-element`, `@typescript-eslint/no-unused-vars`, and one unused eslint-disable directive, across `clientes/[id]/page.tsx`, `clientes/novo/page.tsx`, `documentos/novo/page.tsx`, `pareceres/nova/page.tsx`, `processos/[id]/page.tsx`, `processos/[id]/termo-honorarios/page.tsx`, `processos/novo/page.tsx`, `settings/page.tsx:403` (pre-existing `UserManagementTab` avatar `<img>`, not the new `NotificationPreferencesTab`), `components/profile/user-profile-form.tsx`, `components/shared/dashboard-shell.tsx`. Confirmed via lint output filtered for `use-notificacao-preferencias` / `notificacoes.ts`: zero matches — this plan introduces no new lint errors or warnings.

**Total:** 3 tsc errors + 6 lint errors + 17 lint warnings, all pre-existing and unrelated to this plan's `files_modified`. Candidate for a future cleanup phase (same candidate noted in Phase 89).
