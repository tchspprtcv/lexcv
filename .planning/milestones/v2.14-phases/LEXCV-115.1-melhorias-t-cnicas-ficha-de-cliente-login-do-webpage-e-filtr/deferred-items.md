# Deferred Items — Phase 115.1

Out-of-scope discoveries found during execution, not fixed per the executor's scope boundary rule (only auto-fix issues directly caused by the current task's changes).

## From Plan 115.1-02 (Área 2 — webpage login redirect)

- **`webpage/src/components/brand-mark.tsx`** — `pnpm lint` reports 1 warning: `@next/next/no-img-element`. Confirmed pre-existing (file untouched by this plan; last modified in Phase 99, commit `6f3ae26`). Not caused by the `get-login-url.ts` helper or the 4 "Entrar" call-site rewires. Left unfixed — candidate for a future cleanup pass (swap `<img>` for `next/image` in `BrandMark`).
