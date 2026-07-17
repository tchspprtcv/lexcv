---
phase: LEXCV-108-m-dulo-pareceres
reviewed: 2026-07-17T15:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
  - web/src/hooks/use-pareceres.ts
  - web/src/lib/pareceres.ts
findings:
  critical: 0
  warning: 1
  info: 5
  total: 6
status: issues_found
---

# Phase LEXCV-108: Code Review Report (Iteration 2 — Re-review)

**Reviewed:** 2026-07-17T15:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

This is a re-review of the Pareceres module after commits c871554, 16edcb5, 4598c80, 4c48a41,
70c0a7b, f83c711, 9baf465, 1d319c0 claimed to fix all 8 findings (1 critical, 7 warning) from the
first-pass review (`108-REVIEW.md` iteration 1). This pass re-verifies each of the 8 fixes line by
line against the current code, specifically checks whether any fix introduced a new regression, and
takes a fresh adversarial look at all five files (the three page components plus the two now-included
support modules, `hooks/use-pareceres.ts` and `lib/pareceres.ts`).

**Fix verification result: all 8 fixes are correctly and completely applied, with no regressions
found**, including one fix (WR-06) that received deep scrutiny because it touches a shared helper
(`formatDate`) used for two structurally different kinds of values (date-only `prazo` strings vs.
full `createdAt` timestamps) — tracing through the ECMAScript Date Time String Format rules confirms
the new regex-based parsing produces byte-identical Y/M/D output to the pre-fix `new Date(v)` path for
timestamp strings (which were never affected by the original bug, since date-*time* strings without an
offset are parsed as local time, not UTC, per spec), so no new day-shift bug was introduced for
`createdAt` displays. Detail on all 8 verifications is below.

The fresh look surfaced one new Warning: `EntregarParecerDialog`'s local "which version to deliver"
selection state is never reset when the dialog is cancelled and reopened, so a manually-picked
(possibly non-newest) version silently persists as the pre-selected default on the next open — a
residual risk in the same "irreversible delivery" flow that CR-01 hardened, just via a different
mechanism (stale component state instead of unsorted array order). One new Info-level accessibility
gap was also found (a subset of Pesquisa Avançada's `<label>`s were left unpaired, out of WR-07's
original scope). The four Info items from iteration 1 (IN-01 through IN-04) were intentionally left
unfixed per the requested fix scope and remain present, unchanged — they are carried forward below
for completeness since this document supersedes the iteration-1 review.

## Fix Verification (Iteration 1 → Iteration 2)

| ID | Description | Status |
|----|--------------|--------|
| CR-01 | "Entregar Parecer" default final version relied on unsorted array order | **Fixed correctly.** `[id]/page.tsx:261` now passes `sortedVersoes` (descending by `numeroVersao`) into `EntregarParecerDialog`; the dialog's `defaultVersaoId` now reads `versoes[0].id` (line 483), which is the newest version given the descending sort. The `<option>` list (lines 543-547) iterates the same sorted prop. Verified no other call site still passes the raw `versoes.data` in a way that matters (the one remaining raw-array consumer, `ParecerEntregueBlock`, only does an `.find()` by id, so order is irrelevant there). |
| WR-01 | Advanced-search query fired unconditionally before the panel was used | **Fixed correctly.** `usePesquisarPareceres` (`use-pareceres.ts:60-64`) now takes an `options.enabled` param folded into its own `enabled` computation; `page.tsx:74` passes `{ enabled: pesquisaSubmitted }`. Confirmed `usePesquisarPareceres` has exactly one call site so the signature change is safe. `pesquisaSubmitted` is correctly reset to `false` by both `onApply` (line 83) and `onLimparPesquisa` (line 125), so switching back to the plain filter list also disables the search query. |
| WR-02 | Advogado pickers didn't exclude inactive users | **Fixed correctly** in both files (`page.tsx:64`, `nova/page.tsx:77`): `.filter((u) => u.roles?.includes("ADVOGADO") && u.ativo !== false)`. Confirmed `MockUser.ativo` is `boolean | undefined` (`server/mock-db.ts:38`), so `!== false` correctly treats "unset" as active, matching the project convention. |
| WR-03 | `setValue("processoId", undefined)` on a native select | **Fixed correctly.** `nova/page.tsx:72` now sets `""`. Confirmed the schema's `optionalTrimmedString.transform((v) => (v.length ? v : undefined))` (`schemas/pareceres.ts:7-11`) converts `""` back to `undefined` in the parsed *output* passed to `onSubmit`, so the payload sent to `useCreateParecer` is unaffected — no create-request regression (`processoId` is correctly omitted server-side when left as the placeholder). |
| WR-04 | Version-history accordion's default-open item didn't update after "Entregar" | **Fixed correctly.** `[id]/page.tsx:290` adds `key={defaultOpenVersaoId}` to force a remount whenever the intended default-open item changes. Confirmed the Accordion is only ever rendered once `versoes.data` is loaded and non-empty (guarded by the surrounding `versoes.isLoading` / `!versoes.data?.length` branches), so there's no intermediate render with an `undefined` key that would cause an extra remount flash. |
| WR-05 | Upload-failure path discarded the backend's validation message | **Fixed correctly.** `use-pareceres.ts:165-182`'s `xhr.onload` failure branch now parses `xhr.responseText` as JSON and rejects with `json?.message || json?.error`, falling back to the generic `API {status}` string only if the body isn't valid JSON — matching `apiFetch`'s error-surfacing convention. |
| WR-06 | `formatDate` mis-displayed date-only `prazo` by one day in CV's timezone | **Fixed correctly in both copies** (`lib/pareceres.ts:3-9` and the duplicate in `[id]/page.tsx:67-73`), and **verified not to regress `createdAt`/full-timestamp displays** that also flow through this same helper (`page.tsx:427`, `columns.tsx:74`): per the ECMA-262 Date Time String Format, a date-*time* string without a UTC offset (which is what the backend's `LocalDateTime` serializes to, e.g. `"2026-07-17T14:23:45.123"`) is parsed as **local** time, not UTC — so the Y/M/D digits `new Date(v)` would have produced are identical to the Y/M/D digits the new regex now extracts directly. Only true date-*only* strings (`YYYY-MM-DD`, i.e. `prazo`) were ever parsed as UTC-midnight, and those are exactly what the fix targets. No new bug introduced. |
| WR-07 | Bare `<label>`s without `htmlFor`/`id` in Pesquisa Avançada | **Fixed correctly for the 3 elements originally cited**: `pesquisa-texto` (`page.tsx:246,253`), `pesquisa-data-inicio` and `pesquisa-data-fim` (`page.tsx:327-339`) all now have matching `id`/`htmlFor`. Note: the *other* bare `<label>`s in the same panel (Cliente/Advogado/Estado, wrapping `Select` components) were not part of the original finding's scope and remain unpaired — see IN-05 below. |

## Warnings

### WR-08: `EntregarParecerDialog`'s selected-version state is never reset on cancel/reopen, allowing a stale non-default pick to silently persist

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:480,484`

**Issue:** `EntregarParecerDialog` holds its "which version to deliver" choice in local state that
outlives the dialog's own open/close cycle, because the state is declared in the outer component
(mounted for as long as `showEntregarTrigger` is true), not inside the conditionally-rendered dialog
content:

```tsx
const [selectedVersaoIdState, setSelectedVersaoId] = React.useState<string | null>(null);
...
const defaultVersaoId = versoes && versoes.length > 0 ? versoes[0].id : null;
const selectedVersaoId = selectedVersaoIdState ?? defaultVersaoId;
```

As long as the user never manually changes the `<select>`, `selectedVersaoIdState` stays `null` and
`selectedVersaoId` correctly tracks the live "newest version" default on every render (this is what
CR-01's fix relies on). But the moment a user manually picks a *different* version from the dropdown
(e.g. to intentionally review delivering an older version, or by mis-click) and then clicks
"Cancelar" instead of confirming, `selectedVersaoIdState` keeps that non-default value forever — it is
never cleared on cancel, on dialog close, or on reopen. If the user reopens "Entregar Parecer" later
(e.g. after being interrupted, or after re-reading the description to double-check), the dropdown
will silently pre-select that stale, previously-chosen (and possibly not-the-newest) version again,
rather than resetting to the current newest version. This is the same class of risk CR-01 fixed
(silently defaulting to the wrong version for an action explicitly described as irreversible), just
reached via leftover component state instead of unsorted array order.

Note the backend does defend against cross-solicitação misuse (`ParecerController.entregarSolicitacao`
validates `versao.getSolicitacaoId().equals(id)`), but it has no way to know the frontend's
"newest version" intent — it will happily deliver whatever `versaoFinalId` the client sends, including
a stale one the user no longer means to pick.

**Fix:** Reset the selection when the dialog closes (or when it reopens), e.g.:
```tsx
<AlertDialog
  open={confirmOpen}
  onOpenChange={(next) => {
    if (entregar.isPending) return;
    setConfirmOpen(next);
    if (!next) setSelectedVersaoId(null); // reset to default on close/cancel
  }}
>
```

## Info

### IN-05: Residual un-paired `<label>` elements for Select-based fields in "Pesquisa Avançada" (new, out of WR-07's original scope)

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:262-264,282-284,302-305`

**Issue:** The "Cliente", "Advogado", and "Estado" fields in the same Pesquisa Avançada panel that
WR-07 fixed still use bare `<label>` tags with no `htmlFor`, wrapping the custom `Select` component
rather than a native input:
```tsx
<label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
  Cliente
</label>
<Select value={pesquisaClienteId} onValueChange={setPesquisaClienteId}>...</Select>
```
This wasn't part of the original WR-07 finding (which only cited the free-text input and the two date
inputs), so it wasn't touched by the fix pass, but it's the same underlying accessibility gap.

**Fix:** Radix `Select` doesn't take a plain `htmlFor` target the same way a native input does; use
`aria-label` on the `SelectTrigger`, or wrap the trigger with `aria-labelledby` pointing at an `id` on
the label, e.g. `<label id="pesquisa-cliente-label">Cliente</label>` +
`<SelectTrigger aria-labelledby="pesquisa-cliente-label">`.

---

_The following four Info items are carried forward unchanged from iteration 1 — they were explicitly
out of scope for the fix pass (critical + warning only) and remain present in the code as of this
review. Line numbers re-verified against current source._

### IN-01: Duplicated `formatDate`/`statusVariant` instead of reusing `@/lib/pareceres`

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:67-85`

**Issue:** `[id]/page.tsx` still re-implements byte-for-byte identical `formatDate` and
`statusVariant` locally instead of importing them from `@/lib/pareceres` (only `formatDateTime` is
genuinely new to this file). Both copies did receive the WR-06 date-parsing fix, so they're
currently in sync, but the duplication remains a latent risk if either copy is edited again in
isolation.

**Fix:** Import `formatDate`/`statusVariant` from `@/lib/pareceres` here too, keeping only the new
`formatDateTime` local.

### IN-02: Resolver type-checking bypassed with `as any`

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:53-55`

**Issue:**
```tsx
const form = useForm<ParecerCreateFormValues>({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  resolver: zodResolver(parecerCreateFormSchema) as any,
```
Still present, working around the `z.input`/`z.output` mismatch from `optionalTrimmedString`'s
`.transform()`.

**Fix:** Type the form with the schema's *input* type instead, e.g.
`useForm<z.input<typeof parecerCreateFormSchema>>(...)`.

### IN-03: Repeated `undefined as unknown as FileList` casts

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:380,424,427`

**Issue:** The same double-cast is still repeated three times to represent "no file selected."

**Fix:** Factor out a small typed helper (e.g. `const EMPTY_FILE_LIST = undefined as unknown as
FileList;`) to avoid repeating the unsafe cast at each call site.

### IN-04: `usePermissions()` invoked twice per page (parent guard + content component)

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:27,43`, `web/src/app/(dashboard)/pareceres/nova/page.tsx:30,47`, `web/src/app/(dashboard)/pareceres/[id]/page.tsx:89`

**Issue:** Each page still calls `usePermissions()` once in the outer guard component and again in
the inner content component. Harmless (TanStack Query dedupes `["auth","me"]`), but an easy-to-avoid
redundant hook call.

**Fix:** Not required; consider computing permissions once and passing down, as already done for
`ParecerDetailContent`'s `permissions` prop.

---

_Reviewed: 2026-07-17T15:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Iteration: 2 (re-review; supersedes iteration 1)_
