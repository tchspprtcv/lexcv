---
phase: 89-sino-e-pagina-de-notificacoes
reviewed: 2026-07-10T11:53:22Z
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
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase LEXCV-89: Code Review Report

**Reviewed:** 2026-07-10T11:53:22Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Iteration 3 (final iteration) of this auto-fix loop: a full re-read of all five files, not a diff-only pass, independently re-verifying each of iteration 2's four fixes rather than trusting the fix report's own claims, plus a fresh adversarial pass for anything new. I re-ran `tsc --noEmit` project-wide (same 3 pre-existing, unrelated `vitest` module-resolution errors noted in prior iterations — none in the 5 reviewed files, zero new errors) and `eslint` scoped to the five files, re-verified the frontend/backend contract (the 9-value `NotificacaoCategoria` union matches the 9 categoria literals actually used across `NotificacaoService.java`/`AlertasDiariosJob.java` exactly), re-checked `NotificacaoRepository`'s native queries (parameterized, correctly AND-scoped by both `tenant_id` and `destinatario_id` — no injection or cross-recipient leak), and additionally traced the exact runtime mechanics of `isInternalLinkUrl` against Next.js's own `Link`/`isLocalURL`/`resolveHref` internals and TanStack Query's `Query` state reducer, reading the installed `next` and `@tanstack/query-core@5.100.14` source directly and using Node's WHATWG `URL` implementation to reproduce resolution behavior empirically rather than reasoning about it abstractly.

**Verdict on iteration 2's four fixes:**

| ID (iter. 2) | Verdict | Detail |
|---|---|---|
| WR-01 (`isInternalLinkUrl` backslash check) | **Not fully resolved — same blind spot, different bypass string** | Blocks the exact backslash string it was written for, but checking only `url.charAt(1)` is still vulnerable to a different WHATWG URL-parser normalization quirk (embedded TAB/LF/CR are stripped from anywhere in the string, not just the edges). Re-opened below as WR-01, with evidence that this variant is *more* directly exploitable than the one it replaced — reachable via a plain, unmodified click, not just a modifier-click. |
| WR-02 (unread-count badge error state) | **Fully resolved** | `showBadge = !unread.isLoading && (unread.isError || count > 0)` is correct. I additionally checked an edge case the original fix report didn't test — a *background* refetch failure after a prior successful fetch — against `@tanstack/query-core`'s actual `Query` reducer: it unconditionally sets `status: "error"` regardless of previously-cached data, so that case is covered too, not just "first load fails." No gap found. |
| WR-03 (`/notificacoes` page-clamp) | **Functionally correct, but the fix itself introduces a new lint-rule violation** | The clamp logic is correct in every scenario traced. But it derives state inside a bare `React.useEffect`, which `eslint`'s `react-hooks/set-state-in-effect` flags as an **error** (confirmed by actually running the project's lint), and which causes a real extra render/commit (a visible flash of the stale page before it self-corrects). Re-opened below as WR-02. |
| WR-04 (bell popover closes on link click) | **Fully resolved** | `Popover` is confirmed to be `PopoverPrimitive.Root` re-exported with no prop-stripping wrapper, so `open`/`onOpenChange` are genuine controlled-component props; both the per-notification `<Link>` and the footer "Ver todas as notificações" `<Link>` call `setOpen(false)`. No residual gap. |

No Critical/BLOCKER findings. As with the prior two iterations, the reopened WR-01 remains a broken defense-in-depth control rather than a live exploit today: I independently re-verified all current backend call sites that populate `linkUrl` (`ResourceController.java:991,1056,1725,2606,2619`; `ParecerController.java:182,338`; `AlertasDiariosJob.java:179,224,278` — 10 sites total) still only ever build it from a hardcoded literal prefix concatenated with a UUID, never attacker-influenced input. Given this is the third review iteration in a row to find a fresh bypass of the exact same 4-line function, it is graded Warning (consistent with the established precedent for this non-reachable-today control) rather than Critical, but flagged clearly below as something that should be fixed at the root rather than patched again.

Two previously-unreported, low-severity Info items are included below, plus IN-01 from iteration 2's review, which remains unchanged (still open, correctly excluded from auto-fix scope as Info-level).

## Warnings

### WR-01: `isInternalLinkUrl` still allows off-origin navigation — TAB/CR/LF characters bypass the position-based check, and this variant is reachable via a plain, unmodified click

**File:** `web/src/lib/notificacao-categoria.ts:68-72`; consumed at `web/src/components/shared/notification-bell.tsx:119`, `web/src/app/(dashboard)/notificacoes/page.tsx:258,272-279`
**Issue:** The current implementation:
```ts
export function isInternalLinkUrl(url: string | null | undefined): url is string {
  if (typeof url !== "string" || !url.startsWith("/")) return false;
  const second = url.charAt(1);
  return second !== "/" && second !== "\\";
}
```
only inspects the literal character at index 1. The WHATWG URL parsing algorithm (used by both `new URL()` and the browser's own `<a href>` resolution) has a preprocessing step that removes **every** ASCII TAB (U+0009), LF (U+000A) and CR (U+000D) character from the input — not just leading/trailing ones, from anywhere in the string — before it looks for the authority-introducing `//`. A value with one of those characters sitting between the two slashes still collapses to a protocol-relative URL once parsed, while `charAt(1)` sees an innocuous character and approves it. Verified directly with Node's `URL` (same parsing algorithm as the browser):
```
isInternalLinkUrl("/\t/evil.com") -> true                                    (passes the check)
new URL("/\t/evil.com", "http://localhost:3000").origin -> "http://evil.com"  (resolves OFF-ORIGIN)
// identical result for "/\n/evil.com" and "/\r/evil.com"
```
This is the same root cause as the two prior findings against this exact function (iteration 1's `//`-only check, iteration 2's backslash-position check): each fix closed the specific reported string, not the underlying class of bug, because the check reasons about fixed character positions instead of how the string is actually parsed.

I additionally traced what happens once such a value is rendered as `<Link href="/\t/evil.com">` (since our own function wrongly approves it as "internal"), because iteration 2's review asserted Next's `isLocalURL` "steps aside" for these cases and only a native anchor click resolves off-origin. That claim does not hold up against the actual installed source — the real mechanism is more directly exploitable, not less:
- `isLocalURL` (`next/dist/shared/lib/router/utils/is-local-url.js:13-15`) returns `true` ("treat as local") for **any** scheme-less string, including `"//evil.com"` itself — `isAbsoluteUrl`'s regex (`shared/lib/utils.js:104`) requires the string to *start with a letter*, and a leading `/` never matches it.
- `Link` does not use the raw `href` prop for either the click check or the rendered DOM attribute — it uses `resolveHref(router, hrefProp, true)` (`link.js:255,257`). Tracing `resolveHref` (`client/resolve-href.js:21-98`) for this exact input: its own repeated-slash/backslash guard (line 31, `/(\/\/|\\)/`) does **not** match `"/\t/evil.com"` (no literal `\` or `//` substring — the two slashes are separated by a real TAB, not caught by that regex), so that guard is skipped. Execution reaches `new URL(urlAsString, base)` (line 73), which — exactly like the Node test above — strips the TAB and resolves to origin `"http://evil.com"`. Since that differs from the internal dummy base's origin, `resolveHref` returns `finalUrl.href` verbatim (line 88, the else branch): the fully-qualified absolute string `"http://evil.com/"`.
- That absolute string is what's set as the actual DOM `href` attribute *and* what's passed into the click handler `linkClicked(e, router, href, ...)` (`link.js:376`). At click time, `isLocalURL("http://evil.com/")` now correctly returns `false` (this string starts with a scheme and genuinely resolves to a different origin) — so `linkClicked` returns early without calling `preventDefault()` (`link.js:84-93`), and the browser's native anchor-click navigation runs against the DOM's actual, already-off-origin `href`.

Net effect: for this bypass variant, a plain, unmodified left-click is enough — Next's own href-resolution machinery performs the off-origin conversion before the click handler's own safety check ever runs. (A modifier-click / middle-click would also reach the same outcome via `isModifiedEvent`/`linkClicked`'s early-return at `link.js:70-83`, but isn't even necessary here.)

This carries the same non-reachability caveat as the two prior findings against this function: every current backend call site that populates `linkUrl` still only builds it from a hardcoded literal prefix + UUID (independently re-verified this pass — see Summary), so it is not exploitable through any live feature today. But this is the third review iteration in a row where a targeted, string-specific patch to this exact function has left the underlying vulnerability class open.
**Fix:** stop enumerating individual bypass characters/positions and resolve the value through the real URL parser, rejecting anything that changes the origin:
```ts
const INTERNAL_URL_SENTINEL = "http://internal.invalid";

export function isInternalLinkUrl(url: string | null | undefined): url is string {
  if (typeof url !== "string" || !url.startsWith("/")) return false;
  try {
    return new URL(url, INTERNAL_URL_SENTINEL).origin === INTERNAL_URL_SENTINEL;
  } catch {
    return false;
  }
}
```
This is immune by construction to any parser-normalization trick (repeated slash, backslash, embedded TAB/LF/CR, and any future WHATWG quirk), because it asks the same parser the browser uses whether the value introduces its own authority component, instead of re-implementing a subset of that logic by hand. Verified this exact replacement against 13 cases — every string from this and the prior two reviews' findings, ordinary internal paths (`/processos/123`, `/`, `/notificacoes?tab=x`), empty string, `null`/`undefined`, and a non-rooted relative path (`relative/path`, correctly still rejected since it doesn't start with `/`) — all 13 passed.

### WR-02: The `/notificacoes` page-clamp fix derives state in an effect — a real `eslint` error (`react-hooks/set-state-in-effect`), not just a style nit

**File:** `web/src/app/(dashboard)/notificacoes/page.tsx:64-68`
**Issue:** The clamp logic added for iteration 2's WR-03:
```tsx
React.useEffect(() => {
  if (list.data && list.data.totalPages > 0 && page >= list.data.totalPages) {
    setPage(list.data.totalPages - 1);
  }
}, [list.data, page]);
```
functionally clamps the page correctly in every scenario traced (stale page after mark-as-read shrinks the filtered set; the legitimately-empty-after-filter case where `totalPages === 0` is correctly left alone; rapid filter/page changes). But this is precisely the "adjust state in response to a computed value changing" pattern React's own guidance (linked directly in the lint failure) recommends doing during render, not in an effect. Running the project's actual lint config confirms this is not a preference:
```
src/app/(dashboard)/notificacoes/page.tsx
  66:7  error  Calling setState synchronously within an effect can trigger cascading renders  react-hooks/set-state-in-effect

ESLint: 1 errors, 0 warnings in 1 files
```
All other 4 files in scope, and this file's only other check, are fully clean — this is the single new lint error introduced by iteration 2's changes. The real consequence: the page first commits with the stale (out-of-range) `page`, React then runs the effect, `setPage` fires, and a second render/commit corrects it — a visible flash of the soon-to-be-corrected content (in the empty-after-clamp case, a flash of "Nenhuma notificação encontrada" right before it self-corrects) — where computing the same clamp during render resolves it in a single commit, before anything paints.
**Fix:** move the adjustment into the render body using React's documented "adjusting state when a prop changes" pattern — compare against the last-seen `totalPages` and call both setters conditionally during render instead of inside a `useEffect`:
```tsx
const [lastTotalPages, setLastTotalPages] = React.useState(list.data?.totalPages);
if (list.data && list.data.totalPages !== lastTotalPages) {
  setLastTotalPages(list.data.totalPages);
  if (list.data.totalPages > 0 && page >= list.data.totalPages) {
    setPage(list.data.totalPages - 1);
  }
}
```
(Remove the `React.useEffect` entirely.) This resolves the same clamp within a single render pass — React discards the in-progress render and re-renders immediately with corrected state, before committing/painting — and satisfies `react-hooks/set-state-in-effect` since no setter is called from inside an effect body.

## Info

### IN-01 (carried over from iteration 2, unchanged): `/notificacoes` row loses compiler-verified link-safety narrowing via an unnecessary `as string` cast

**File:** `web/src/app/(dashboard)/notificacoes/page.tsx:258,272-279`
**Issue:** Still present, unchanged from iteration 2 (correctly out of scope for auto-fix as an Info-level item). `NotificacaoRow` stores the type guard's result in a plain `boolean` first (`const isInternalLink = isInternalLinkUrl(linkUrl);`), which discards TypeScript's narrowing and forces `href={linkUrl as string}` at the usage site. The bell (`notification-bell.tsx:119`) calls the guard inline in the JSX condition instead, so it needs no cast. Not a regression from this iteration, but still worth closing given `isInternalLinkUrl` is exactly the kind of function you want the compiler actively re-checking (see WR-01 above).
**Fix:** call the guard inline, mirroring the bell:
```tsx
{isInternalLinkUrl(linkUrl) ? (
  <Link href={linkUrl} onClick={handleTitleClick} className={`${titleClassName} hover:underline`}>
    {titulo}
  </Link>
) : (
  <span className={titleClassName}>{titulo}</span>
)}
```

### IN-02: Bell dropdown marks an already-read notification as read again on every click, unlike the `/notificacoes` page's equivalent row

**File:** `web/src/components/shared/notification-bell.tsx:119-129` (contrast: `web/src/app/(dashboard)/notificacoes/page.tsx:263-265`)
**Issue:** For an internal-link notification, the whole row is wrapped in one `<Link>` whose `onClick` unconditionally calls `marcarLida.mutate(n.id)`:
```tsx
<Link href={n.linkUrl} className="block" onClick={() => { marcarLida.mutate(n.id); setOpen(false); }}>
```
with no `if (!n.lida)` guard, so clicking an already-`lida: true` notification still fires a `PATCH /notificacoes/{id}/lida` and the resulting `invalidateQueries({queryKey: ["notificacoes"]})`, refetching both the list and the unread-count on every click regardless of read state. `NotificacoesContent`'s `NotificacaoRow` guards the equivalent interaction: `const handleTitleClick = () => { if (!lida) onMarcarLida(id); };`. Backend-side this is harmless (`NotificacaoService.marcarLida` just re-sets `lida = true` on an already-true row; there's no separate read-timestamp column to corrupt), so this is not a correctness bug — just wasted requests and an inconsistency between the two implementations of the same interaction. Predates this iteration's changes (WR-04 only added `setOpen(false)` to this line), so not a new regression, but not previously reported either.
**Fix:** guard the same way the page does:
```tsx
onClick={() => {
  if (!n.lida) marcarLida.mutate(n.id);
  setOpen(false);
}}
```

### IN-03: Bell trigger button has no accessible name

**File:** `web/src/components/shared/notification-bell.tsx:69-84`
**Issue:** The icon-only `<Button>` wrapping the `Bell` icon has no `aria-label` (or visually-hidden text), so its accessible name is empty for assistive tech — a screen reader announces only "button." This is an inconsistency within the same file: the fallback "Marcar como lida" icon button a few lines down (`notification-bell.tsx:141`) correctly sets `aria-label="Marcar como lida"`, showing the convention is known and applied elsewhere, just not here.
**Fix:**
```tsx
<Button
  type="button"
  variant="ghost"
  aria-label="Notificações"
  className="h-9 w-9 p-0 rounded-full ..."
>
```

---

_Reviewed: 2026-07-10T11:53:22Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
