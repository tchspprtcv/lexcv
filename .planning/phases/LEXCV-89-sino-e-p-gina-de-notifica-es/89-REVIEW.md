---
phase: 89-sino-e-pagina-de-notificacoes
reviewed: 2026-07-10T10:27:12Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - web/src/types/notificacoes.ts
  - web/src/lib/notificacao-categoria.ts
  - web/src/hooks/use-notificacoes.ts
  - web/src/components/shared/notification-bell.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
findings:
  critical: 0
  warning: 6
  info: 1
  total: 7
status: issues_found
---

# Phase LEXCV-89: Code Review Report

**Reviewed:** 2026-07-10T10:27:12Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the sino (bell) rewrite and the new `/notificacoes` history page: the two new source-of-truth modules (`types/notificacoes.ts`, `lib/notificacao-categoria.ts`), the new hooks file (`use-notificacoes.ts`), and both consuming components. I read every file in full, cross-checked the frontend/backend contract against `NotificacaoController`/`NotificacaoRepository`/`NotificacaoService`/`Notificacao` (backend), verified permission/badge/button/toast/access-denied helper contracts, ran `tsc --noEmit` and `eslint` against the five files (both clean), and — since this phase shipped with an approved `89-CONTEXT.md`/`89-UI-SPEC.md` — cross-checked every behavioral finding candidate against that written spec before including it, to avoid flagging deliberate, documented design decisions as bugs (one candidate finding, "bell marks an already-read notification as read again on click," was dropped for exactly this reason: `89-CONTEXT.md` explicitly locks that as "ação única, sem passo de confirmação").

No SQL/command injection, hardcoded secrets, XSS, or crash-class bugs found (no `dangerouslySetInnerHTML`, no `eval`, all category/label lookups have safe fallbacks, all user-visible strings are rendered as JSX text and auto-escaped). No Critical/BLOCKER findings. The issues below are all real behavioral divergences between the bell and the `/notificacoes` page for what the phase's own UI-SPEC calls "identical" behavior (mark-all disabled state, mark-all error handling, list-load error state), plus one input-validation gap in a link-safety check and one type-exhaustiveness gap in a hand-maintained array. None of these are currently exploitable/reachable through any traced backend call site (verified directly against `ResourceController`, `ParecerController`, and `AlertasDiariosJob` — every `linkUrl` passed to `NotificacaoService` is built from a hardcoded prefix + a UUID, never free text), but each is a concrete, provable gap worth closing.

## Warnings

### WR-01: Bell dropdown has no error-state handling for the notification queries — contradicts the phase's own UI-SPEC

**File:** `web/src/components/shared/notification-bell.tsx:42-45, 90-98`
**Issue:** `89-UI-SPEC.md`'s Copywriting Contract requires the copy *"Não foi possível carregar as notificações. Verifique a ligação e tente novamente."* for "Error state (list load failure — **page or dropdown**)" — explicitly both surfaces. `web/src/app/(dashboard)/notificacoes/page.tsx` implements this (`list.isError` branch, line 167-170), but `notification-bell.tsx`'s render chain only checks `list.isPending` and then `!list.data?.content.length`:
```tsx
{list.isPending ? (
  <p>...A carregar...</p>
) : !list.data?.content.length ? (
  <p>...Sem notificações por agora.</p>
) : ( ... )}
```
When the list query errors (permission revoked for a custom role, transient network failure, 5xx), `list.isPending` becomes `false` and `list.data` stays `undefined`; `!list.data?.content.length` then evaluates to `!undefined` → `true`, so the popover silently renders "Sem notificações por agora." — telling the user there are zero notifications when the real problem is a failed request. The same gap exists for the unread badge: `showBadge = !unread.isLoading && count > 0` treats an errored unread-count query identically to a genuinely-zero one (badge silently disappears instead of surfacing anything).
**Fix:**
```tsx
{list.isPending ? (
  <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">A carregar...</p>
) : list.isError ? (
  <p className="px-4 py-6 text-sm text-red-600 text-center">
    Não foi possível carregar as notificações. Verifique a ligação e tente novamente.
  </p>
) : !list.data?.content.length ? (
  ...
```

### WR-02: "Marcar todas como lidas" on the `/notificacoes` page has no error handling, unlike the bell's identical action

**File:** `web/src/app/(dashboard)/notificacoes/page.tsx:81-84`
**Issue:**
```tsx
const onMarcarTodas = async () => {
  await marcarTodas.mutateAsync();
  toast.success("Todas as notificações foram marcadas como lidas.");
};
```
called directly from `onClick={onMarcarTodas}`. If the mutation rejects (any non-2xx response), this throws inside an async function invoked from a DOM event handler — React does not await/catch that promise, so it becomes an unhandled promise rejection. Worse, on a 401/403 response `apiFetch` (`web/src/lib/api.ts:43-45`) deliberately suppresses its own automatic error toast (to avoid spam on session expiry), so in that case the user gets **no feedback at all**: no success toast (correctly), but no error toast either — the button click appears to silently do nothing. Compare `notification-bell.tsx`'s `handleMarcarTodas`, which wraps the identical call in try/catch. `89-UI-SPEC.md:240` states this control "behaves identically to the dropdown's version" — this is a direct divergence.
**Fix:**
```tsx
const onMarcarTodas = async () => {
  try {
    await marcarTodas.mutateAsync();
    toast.success("Todas as notificações foram marcadas como lidas.");
  } catch {
    // Erro já reportado pelo toast automático do apiFetch (exceto 401/403).
  }
};
```

### WR-03: "Marcar todas como lidas" disabled-state is computed differently on the bell vs. the page during the initial loading window

**File:** `web/src/app/(dashboard)/notificacoes/page.tsx:86`; compare `web/src/components/shared/notification-bell.tsx:44-45,83`
**Issue:** Bell: `const count = unread.data?.count ?? 0;` then `disabled={count === 0 || marcarTodas.isPending}` — while `unread` is still loading, `count` defaults to `0`, so the button starts **disabled**. Page: `const marcarTodasDisabled = marcarTodas.isPending || unreadCount.data?.count === 0;` — while `unreadCount` is still loading, `unreadCount.data` is `undefined`, and `undefined === 0` is `false`, so the button starts **enabled**. `89-UI-SPEC.md:240` calls this control's page behavior identical to the dropdown's, and `:108` defines the disable rule as "Disabled when `unreadCount === 0` or while the mutation is pending" with no loading-state carve-out — the page's version lets a user fire `POST /notificacoes/ler-todas` before the real unread count is even known. Harmless against this particular backend (idempotent — returns `{marcadas: 0}` if there's nothing to mark) but a real, provable divergence between two controls the spec calls identical.
**Fix:** reuse the same `?? 0` fallback the bell already uses:
```tsx
const marcarTodasDisabled = marcarTodas.isPending || (unreadCount.data?.count ?? 0) === 0;
```

### WR-04: Internal-link safety check accepts protocol-relative URLs (`//host/...`), duplicated in both files

**File:** `web/src/components/shared/notification-bell.tsx:105`; `web/src/app/(dashboard)/notificacoes/page.tsx:243`
**Issue:** Both files gate "is this safe to render as an in-app `<Link>`" with a `startsWith("/")` check (bell: `n.linkUrl && n.linkUrl.startsWith("/")`; page: `typeof linkUrl === "string" && linkUrl.startsWith("/")`). A protocol-relative URL such as `"//evil.example.com"` also starts with `"/"`, so it passes this check and would be rendered as `<Link href="//evil.example.com">` — browsers resolve that to an absolute, off-origin URL on click (`https://evil.example.com`), defeating the intended "internal navigation only" guarantee. `89-UI-SPEC.md:185,252` documents `linkUrl` as "always a same-origin relative path... never external" but nullable in the schema, and explicitly frames this check as a defensive fallback for that nullable column — its current form has a well-known bypass class. I verified every current backend call site that populates `linkUrl` (`ResourceController.java:991,1056,1725,2606,2619`, `ParecerController.java:182,338`, `AlertasDiariosJob.java:179,224,278`): all of them build the value as a hardcoded literal prefix concatenated with a UUID (e.g. `"/processos/" + saved.getId()`), never free text, so this is **not reachable today** — but it's the last line of defense against a future caller (or a manually-edited DB row) that isn't as careful.
**Fix:** reject the `//` prefix explicitly, ideally via one shared helper instead of two independent copies of the check:
```ts
// e.g. in web/src/lib/notificacao-categoria.ts or a new lib/link-safety.ts
export function isInternalLink(url: string | null | undefined): url is string {
  return typeof url === "string" && url.startsWith("/") && !url.startsWith("//");
}
```

### WR-05: Bell's fallback "Marcar como lida" button has no pending-guard, unlike the page's equivalent

**File:** `web/src/components/shared/notification-bell.tsx:114-125`
**Issue:** `notificacoes/page.tsx`'s `NotificacaoRow` disables its "Marcar como lida" button while `isMarking` (`marcarLida.isPending && marcarLida.variables === n.id`, line 198/279), preventing duplicate PATCH calls from rapid repeat clicks on the same row. The bell's equivalent button (rendered for a notification whose `linkUrl` is null/external, so the row has no navigable click target) has no `disabled` prop at all:
```tsx
<Button
  type="button"
  variant="ghost"
  size="sm"
  className="flex-shrink-0"
  aria-label="Marcar como lida"
  onClick={() => marcarLida.mutate(n.id)}
>
  <Check />
</Button>
```
Repeated clicks before the first PATCH resolves fire multiple mutations for the same `id`. Not destructive (`NotificacaoService.marcarLida` unconditionally sets `lida=true`, idempotent) but an avoidable, inconsistent gap versus the guard already established in the sibling file for the exact same action.
**Fix:**
```tsx
<Button
  ...
  disabled={marcarLida.isPending && marcarLida.variables === n.id}
  onClick={() => marcarLida.mutate(n.id)}
>
```

### WR-06: `NOTIFICACAO_CATEGORIA_OPTIONS` is a hand-maintained array, not structurally guaranteed to stay exhaustive with `NotificacaoCategoria`

**File:** `web/src/lib/notificacao-categoria.ts:44-56`
**Issue:** `categoriaToLabel`/`categoriaToBadgeVariant` (lines 7, 27) are typed `Record<NotificacaoCategoria, ...>`, so the compiler forces every union member to have an entry — adding a 10th category without updating either map is a compile error, impossible to ship by accident. `NOTIFICACAO_CATEGORIA_OPTIONS` is instead a hand-typed array literal of the same 9 string values, checked only via `satisfies readonly NotificacaoCategoria[]` (line 55) — which validates that every *listed* value belongs to the union, but does not require every union value to be listed. A future category addition that correctly updates both `Record` maps (compiler-enforced) can still silently omit the new value from this array, with no compiler error and no test failure, leaving users unable to ever filter by the new category on the `/notificacoes` page.
**Fix:** derive the options list from one of the already-exhaustive maps instead of hand-duplicating the literal, e.g.:
```ts
const CATEGORIAS: readonly NotificacaoCategoria[] = Object.keys(
  { FASE_ENTRADA: 0, DOCUMENTO_NOVO: 0, PROCESSO_ATRIBUIDO: 0, PARECER_ATRIBUIDO: 0,
    PRAZO_PROXIMO: 0, PRAZO_VENCIDO: 0, EVENTO_PROXIMO: 0, EVENTO_VENCIDO: 0,
    HONORARIO_ATRASADO: 0 } satisfies Record<NotificacaoCategoria, 0>,
) as NotificacaoCategoria[];
export const NOTIFICACAO_CATEGORIA_OPTIONS = CATEGORIAS.map((value) => ({ value, label: categoriaToLabel(value) }));
```
(or simplest: reuse whichever `Record` already exists and `Object.keys()` it directly, so a missing key becomes a type error at the map's own definition rather than a silent array omission).

## Info

### IN-01: Paginated list has no `placeholderData`/`keepPreviousData`, so every page/filter change flashes the results card to "A carregar..."

**File:** `web/src/hooks/use-notificacoes.ts` (`useNotificacoes`)
**Issue:** Changing `page` (or a filter) changes the query key with no `placeholderData` configured, so `/notificacoes` briefly loses `list.data` entirely on every pagination click, and the whole results card (including the pagination controls themselves) is replaced by the generic "A carregar..." text rather than keeping the previous page's rows visible during the refetch. This is consistent with every other paginated hook in this codebase (none use `placeholderData`/`keepPreviousData`), so it's a pre-existing, systemic pattern rather than a regression unique to this phase — noting it here for visibility since it's directly exercised by this phase's new pagination UI, not proposing it be fixed only here.
**Fix:** `import { keepPreviousData } from "@tanstack/react-query";` and add `placeholderData: keepPreviousData` to the `useQuery` options in `useNotificacoes`.

---

_Reviewed: 2026-07-10T10:27:12Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
