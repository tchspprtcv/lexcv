# Deferred Items — Phase 89

Out-of-scope discoveries logged during plan execution. Not fixed (per executor scope-boundary rule: only auto-fix issues directly caused by the current task's changes).

## Plan 89-01 (data layer: types, categoria map, hooks)

Discovered while running `pnpm --dir web lint` as the automated verification step for Tasks 1-2. All 6 errors and 17 warnings are pre-existing, in files this plan does not touch (confirmed: none reference `notificacoes.ts` / `notificacao-categoria.ts` / `use-notificacoes.ts`). Logged here rather than fixed:

| File | Issue |
|------|-------|
| `web/src/app/(dashboard)/pareceres/nova/page.tsx:69` | `react-hooks/incompatible-library` warning — `form.watch()` cannot be memoized safely |
| `web/src/app/(dashboard)/processos/[id]/page.tsx:165` | `@typescript-eslint/no-unused-vars` warning — `textareaClassName` assigned but never used |
| `web/src/app/(dashboard)/processos/[id]/page.tsx:247` | `react-hooks/set-state-in-effect` error — `setTab` called synchronously inside a `useEffect` |
| `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx:81` | Unused eslint-disable directive warning |
| `web/src/app/(dashboard)/processos/novo/page.tsx:115` | `@typescript-eslint/no-unused-vars` warning — `_estado` assigned but never used |
| `web/src/app/(dashboard)/settings/page.tsx:375` | `@next/next/no-img-element` warning |
| `web/src/components/profile/user-profile-form.tsx:92,108` | `react-hooks/incompatible-library` warning + `@next/next/no-img-element` warning |
| `web/src/components/shared/dashboard-shell.tsx:67` | `react-hooks/set-state-in-effect` error — `setDrawerOpen` called synchronously inside a `useEffect` |
| `web/src/components/shared/dashboard-shell.tsx:138,218,263,272,290` | `@next/next/no-img-element` warnings (x5) |

**Total:** 6 errors, 17 warnings — all pre-existing, unrelated to this plan's `files_modified` (`web/src/types/notificacoes.ts`, `web/src/lib/notificacao-categoria.ts`, `web/src/hooks/use-notificacoes.ts`). Candidate for a future cleanup phase.
