---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
fixed_at: 2026-07-14T11:33:12Z
review_path: .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-93: Code Review Fix Report

**Fixed at:** 2026-07-14T11:33:12Z
**Source review:** .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (fix_scope: critical_warning — WR-01, WR-02, WR-03; IN-01 and IN-02 excluded as Info-tier)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `silenciarCategoria()` is not actually idempotent under concurrent requests, and the resulting exception is unhandled

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** 726d9a9
**Applied fix:** Wrapped the check-then-act insert in a try/catch for `DataIntegrityViolationException`
(imported from `org.springframework.dao`). On the unique-constraint race between two concurrent
requests for the same `(tenant, user, categoria)`, the second request now logs a debug message and
returns normally instead of letting the exception fall through to `GlobalExceptionHandler`'s
catch-all (which previously returned a raw HTTP 500 leaking exception/constraint detail). The row
existing either way means the category is muted, so treating the race as a successful no-op
correctly restores the documented idempotency contract. Verified via `mvn -o compile` in the
backend module (0 errors).

### WR-02: "Which categories can be silenced" is duplicated between backend and frontend with no shared source of truth

**Files modified:** `web/src/lib/notificacao-categoria.ts`, `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** 0f5df1d
**Applied fix:** Chose the smallest-correct-fix option noted in the review (centralize, don't change
the API contract). Added `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` (a named, commented constant
listing `PRAZO_VENCIDO`) and `NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS` (derived from
`NOTIFICACAO_CATEGORIA_OPTIONS` by excluding that constant) to `notificacao-categoria.ts`, with an
explicit comment that the list must be kept in sync with `CategoriaNotificacao.java` and that the
real long-term fix is having `GET /notificacoes/preferencias` return the silenciável set. Updated
`NotificationPreferencesTab` in `settings/page.tsx` to consume the new derived constant instead of
an inline `.filter((o) => o.value !== "PRAZO_VENCIDO")` literal buried in the component. Confirmed
`NOTIFICACAO_CATEGORIA_OPTIONS` (unfiltered) is still exported/used unchanged by
`web/src/app/(dashboard)/notificacoes/page.tsx`. Verified via scoped `tsc --noEmit` (no errors in
either modified file; only 3 pre-existing, unrelated `vitest` type-resolution errors in `*.test.ts`
files, present before this fix too).

### WR-03: `NotificationPreferencesTab` has no error state for the preferences fetch — silently defaults every category to "delivered" on failure

**Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** bae3de3
**Applied fix:** Destructured `isError` and `refetch` from `useNotificacaoPreferencias()` (in
addition to the existing `data`/`isLoading`) and added an `isError` branch, rendered after the
`isLoading` branch, that shows an `AlertCircle` icon, a clear Portuguese error message ("Não foi
possível carregar as preferências de notificação."), and an outline "Tentar novamente" button
wired to `refetch()`. This replaces the previous silent fallback where a failed fetch left `data`
undefined, `silenciadas` defaulted to `[]`, and every category rendered as "A ENTREGAR" with no
indication anything had gone wrong. Added `AlertCircle` to the existing `lucide-react` import;
reused the already-imported `Button` component. Verified via scoped `tsc --noEmit` (no errors in
the modified file; same 3 pre-existing unrelated `vitest` errors as the WR-02 baseline).

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-14T11:33:12Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
