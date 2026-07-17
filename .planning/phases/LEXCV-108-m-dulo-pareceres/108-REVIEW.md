---
phase: LEXCV-108-m-dulo-pareceres
reviewed: 2026-07-17T12:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
findings:
  critical: 1
  warning: 7
  info: 4
  total: 12
status: issues_found
---

# Phase LEXCV-108: Code Review Report

**Reviewed:** 2026-07-17T12:00:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Reviewed the three Pareceres (legal-opinion request) pages: the list/search page, the creation
form, and the detail page (with its nested "Nova Versão" and "Entregar Parecer" flows). The
permission gating (`usePermissions().can.*`) consistently mirrors the backend's
`@PreAuthorize` scopes (`pareceres:view/create/edit`) and the "responsável ou ADMIN" business
rule for delivering/versioning a parecer, which is a real strength — both layers agree, per
project convention.

The most significant defect is in the "Entregar Parecer" (deliver final opinion) dialog: it
picks the *last array element* of an unsorted `versoes` list as the default final version, while
the same file explicitly distrusts that same array's order everywhere else (it re-sorts by
`numeroVersao` for the visible history). The backend repository method backing this list has no
`ORDER BY`, so nothing guarantees the last element is actually the newest version — for an action
the UI itself describes as irreversible. Several further issues affect date-display correctness,
a stale-state UI trap for the version-selection accordion, an inconsistent error-message path for
file uploads, and inactive users remaining selectable as "advogado responsável."

## Critical Issues

### CR-01: "Entregar Parecer" default final version relies on unsorted/unguaranteed array order

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:260,465-541`

**Issue:**
`EntregarParecerDialog` is passed the raw `versoes.data` (not the parent's already-sorted
`sortedVersoes`):

```tsx
// line 260
{showEntregarTrigger ? (
  <EntregarParecerDialog solicitacaoId={id} versoes={versoes.data} />
) : null}
```

Inside the dialog, the default version to deliver as final is derived by indexing the *last*
element of that raw array:

```tsx
// lines 476-477
const defaultVersaoId = versoes && versoes.length > 0 ? versoes[versoes.length - 1].id : null;
const selectedVersaoId = selectedVersaoIdState ?? defaultVersaoId;
```

and the `<option>` list at lines 536-540 is rendered in that same raw, unsorted order.

This assumes the API always returns versions in ascending `numeroVersao`/creation order, but:
- The backend repository call backing this data, `ParecerVersaoRepository.findBySolicitacaoId`,
  is a plain Spring Data derived query with **no `@Query`/`ORDER BY`** — JPA/Hibernate makes no
  ordering guarantee for a query without an explicit `ORDER BY` clause.
- The same file already treats this exact data as unordered elsewhere: `sortedVersoes` (lines
  166-169) explicitly re-sorts `versoes.data` by `numeroVersao` descending before rendering the
  "Histórico de Versões" panel, specifically because the raw order can't be trusted.

If the raw order ever deviates from creation order (e.g. due to a query plan change, a future
`ORDER BY id` addition to the repository, or any other backend change), the dialog will silently
pre-select an old version as the "final" one. The delivery action is explicitly described in the
UI as irreversible ("Esta ação é irreversível. Depois de entregue, o parecer não pode receber
novas versões nem ser reaberto."), so a user who trusts the default and clicks "Confirmar Entrega"
without manually re-checking the dropdown could deliver the wrong version as the client's final
legal opinion, with no way to undo it.

**Fix:** Derive the default (and the option order) from an explicit sort by `numeroVersao`, reusing
the same logic already used for `sortedVersoes`, and pass that into the dialog instead of the raw
array:

```tsx
// call site
<EntregarParecerDialog solicitacaoId={id} versoes={sortedVersoes} />

// inside EntregarParecerDialog — versoes is now sorted descending (highest first)
const defaultVersaoId = versoes && versoes.length > 0 ? versoes[0].id : null;
```

(Optionally also add an explicit `ORDER BY numero_versao` — e.g.
`@Query("... ORDER BY v.numeroVersao DESC")` — to `ParecerVersaoRepository.findBySolicitacaoId` on
the backend, so the guarantee doesn't rely solely on the frontend's sort.)

## Warnings

### WR-01: Advanced-search query fires unconditionally, even before the panel is opened

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:73-74`

**Issue:**
```tsx
const pareceres = usePareceres(filters);
const pesquisa = usePesquisarPareceres(pesquisaFilters);
```
Both hooks are `enabled: typeof window !== "undefined"` only (see `hooks/use-pareceres.ts`), so
`usePesquisarPareceres` fires a request to `/pareceres/pesquisa` on every mount and on every
`pesquisaFilters` change — regardless of whether the user has ever opened "Pesquisa Avançada" or
clicked "Pesquisar" (`pesquisaOpen`/`pesquisaSubmitted` are not part of the `enabled` condition).
This means the page always issues two list requests instead of one, and continues issuing the
second one in the background even while the search UI is fully hidden.

**Fix:** Gate the search query by whether it has actually been used, e.g. extend the hook to accept
an `enabled` option and pass `pesquisaSubmitted`:
```tsx
const pesquisa = usePesquisarPareceres(pesquisaFilters, { enabled: pesquisaSubmitted });
```

### WR-02: "Advogado" pickers don't exclude inactive users

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:63-66`, `web/src/app/(dashboard)/pareceres/nova/page.tsx:76-79`

**Issue:** Both files derive the advogado list the same way:
```tsx
const advogados = React.useMemo(
  () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
  [adminUsers.data],
);
```
This does not filter out deactivated users (`u.ativo === false`), even though `MockUser.ativo` is
available and `web/src/app/(dashboard)/processos/[id]/page.tsx` already establishes the project
convention of filtering with `.filter((u) => u.ativo !== false)` for equivalent
responsible-user pickers. The backend's `ParecerController.validateAdvogado` explicitly rejects a
deactivated advogado (`Boolean.FALSE.equals(user.getAtivo())`), so today a user can select an
inactive advogado in "Nova Solicitação de Parecer" and only find out it's invalid after submitting,
via a generic 400 response ("advogadoId não pertence a este tenant, não tem papel ADVOGADO ou está
inativo"). The same unfiltered list is also offered in the list page's advanced-filter dropdown.

**Fix:**
```tsx
const advogados = React.useMemo(
  () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO") && u.ativo !== false),
  [adminUsers.data],
);
```

### WR-03: Clearing `processoId` via `setValue(..., undefined)` on a native select

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:71-74`

**Issue:**
```tsx
React.useEffect(() => {
  form.setValue("processoId", undefined);
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [clienteIdValue]);
```
`processoId` is bound to a native `<select>` via `register()`, whose first option has
`value=""` ("Nenhum processo associado"). Assigning `undefined` to a DOM `<select>`'s `.value`
coerces to the string `"undefined"`, which matches no `<option>`, so the browser can end up with no
option visually selected (`selectedIndex === -1`) instead of falling back to the intended
placeholder option. Using `""` is the well-known-safe way to reset a registered select/text field in
react-hook-form.

**Fix:**
```tsx
form.setValue("processoId", "");
```

### WR-04: Version-history accordion's default open item does not update after "Entregar"

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:288`

**Issue:**
```tsx
<Accordion type="single" collapsible defaultValue={defaultOpenVersaoId} className="relative">
```
`defaultValue` is an *uncontrolled* prop — Radix (like a native `<details>`) only reads it once, at
mount, to seed internal state; later re-renders with a different `defaultOpenVersaoId` do not
re-open a different item. Concretely: while the parecer is still open, the accordion mounts with
the newest version expanded (`sortedVersoes[0]`). After the user calls "Entregar Parecer" choosing
an *older* version as the final one, `isConcluido` flips to `true` and `defaultOpenVersaoId`
recomputes to `parecer.data.versaoFinalId`, but the already-mounted accordion keeps the previously
expanded (newest, non-final) version open rather than auto-expanding the delivered version.

**Fix:** Make the accordion controlled (`value`/`onValueChange`) or force remount on delivery, e.g.
`<Accordion key={defaultOpenVersaoId} ... defaultValue={defaultOpenVersaoId}>`.

### WR-05: Version-upload failures lose the backend's validation message

**File:** `web/src/hooks/use-pareceres.ts:162-171` (surfaces in `web/src/app/(dashboard)/pareceres/[id]/page.tsx:378-386`)

**Issue:** Every other mutation in this module (and the app) goes through `apiFetch`, which parses
the response body and surfaces the backend's `message`/`error` field (e.g. "É necessário fornecer
conteúdo ou anexo"). `useCreateParecerVersao` instead uses a raw `XMLHttpRequest` (needed for
upload-progress events) whose failure handler discards the body entirely:
```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) { ... }
  else {
    reject(new Error(`API ${xhr.status}`));
  }
};
```
As a result, `NovaVersaoForm`'s catch block (`pareceres/[id]/page.tsx:378-386`) shows the user a
bare `"API 400"`-style message instead of the actual validation reason, which is a visible
regression in error-message quality specific to this one upload path.

**Fix:** Parse `xhr.responseText` on failure the same way `apiFetch` does, and reject with that
message:
```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) {
    try { resolve(JSON.parse(xhr.responseText) as ParecerVersao); }
    catch { reject(new Error("Resposta inválida do servidor")); }
    return;
  }
  let message = `API ${xhr.status}`;
  try {
    const json = JSON.parse(xhr.responseText);
    message = json?.message || json?.error || message;
  } catch { /* not JSON */ }
  reject(new Error(message));
};
```

### WR-06: `formatDate` mis-displays date-only `prazo` values by one day in Cape Verde's timezone

**File:** `web/src/lib/pareceres.ts:3-8` (imported by `page.tsx`/`columns.tsx`); duplicated at `web/src/app/(dashboard)/pareceres/[id]/page.tsx:67-72`

**Issue:**
```ts
export function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}
```
`prazo` is a date-only string (`YYYY-MM-DD`, produced by `<input type="date">` in
`nova/page.tsx:170-176`). Per the ECMAScript Date Time String Format, a date-only string with no
explicit timezone is parsed as **UTC midnight**, not local midnight. `toLocaleDateString("pt-CV")`
then renders that instant in the browser's local timezone. Cape Verde is UTC−01:00 year-round (no
DST), so `new Date("2026-07-20").toLocaleDateString("pt-CV")` renders **"19/07/2026"** — one day
before the stored deadline — for any user whose browser timezone is Atlantic/Cape_Verde. In a
legal-deadline ("Prazo") context this is a meaningful correctness issue, not just cosmetic. (This
exact helper is duplicated verbatim in `processos/[id]/page.tsx` and `financeiro/[id]/page.tsx`
too, so it's a pre-existing, systemic pattern this phase reproduces rather than a novel regression
— but it is present, and doubly so, in the reviewed files.)

**Fix:** For date-only values, parse the components explicitly instead of handing the raw string to
`Date`:
```ts
export function formatDate(v: string | undefined) {
  if (!v) return "—";
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v);
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}
```

### WR-07: Raw `<label>` elements without `htmlFor`/`id` pairing in "Pesquisa Avançada"

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:245-256,322-339`

**Issue:** The free-text search input and the two date inputs use bare `<label>` tags with no
`htmlFor`, and the inputs have no matching `id`:
```tsx
<label className="...">Pesquisar</label>
<input type="text" value={pesquisaTexto} onChange={...} .../>
...
<label className="...">Data Início</label>
<input type="date" value={pesquisaDataInicio} onChange={...} .../>
```
This is inconsistent with `nova/page.tsx`, which consistently pairs `<Label htmlFor="clienteId">`
with `id="clienteId"` etc., and degrades screen-reader/label-click association for these fields.

**Fix:** Add matching `id`/`htmlFor`, e.g. `<label htmlFor="pesquisa-texto">...</label>` /
`<input id="pesquisa-texto" .../>`.

## Info

### IN-01: Duplicated `formatDate`/`statusVariant` instead of reusing `@/lib/pareceres`

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:67-84`

**Issue:** `pareceres/page.tsx` and `columns.tsx` both import `formatDate` and `statusVariant` from
`@/lib/pareceres`, but `[id]/page.tsx` re-implements byte-for-byte identical versions of both
functions locally (only `formatDateTime` is genuinely new).

**Fix:** Import `formatDate`/`statusVariant` from `@/lib/pareceres` here too, keeping only the new
`formatDateTime` local.

### IN-02: Resolver type-checking bypassed with `as any`

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:53-56`

**Issue:**
```tsx
const form = useForm<ParecerCreateFormValues>({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  resolver: zodResolver(parecerCreateFormSchema) as any,
```
This is almost certainly working around the `z.input`/`z.output` mismatch introduced by
`optionalTrimmedString`'s `.transform()` in `schemas/pareceres.ts`. Casting to `any` silences the
compiler entirely at this boundary rather than fixing the underlying generic mismatch.

**Fix:** Type the form with the schema's *input* type instead of its (transformed) output type,
e.g. `useForm<z.input<typeof parecerCreateFormSchema>>(...)`, which avoids the cast.

### IN-03: Repeated `undefined as unknown as FileList` casts

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:373,417,420`

**Issue:** The same double-cast is repeated three times to represent "no file selected":
```ts
form.reset({ conteudo: "", file: undefined as unknown as FileList });
...
form.setValue("file", createFileList(file), { shouldValidate: true })
...
form.setValue("file", undefined as unknown as FileList, { shouldValidate: true })
```
**Fix:** Factor out a small typed helper (e.g. `const EMPTY_FILE_LIST = undefined as unknown as
FileList;`, or loosen the schema's field type to `FileList | undefined`) to avoid repeating the
unsafe cast at each call site.

### IN-04: `usePermissions()` invoked twice per page (parent guard + content component)

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:27,43`, `web/src/app/(dashboard)/pareceres/nova/page.tsx:30,47`, `web/src/app/(dashboard)/pareceres/[id]/page.tsx:88`

**Issue:** Each page calls `usePermissions()` once in the outer `AccessDeniedState`-guard
component and again in the inner `*Content` component it renders. This is harmless in practice
(TanStack Query dedupes the underlying `["auth","me"]` fetch), but it's an easy-to-avoid redundant
hook call — the already-resolved `permissions` could be passed down as a prop instead (as is
already done for `permissions` in `ParecerDetailContent`, just not reused for the guard itself).

**Fix:** Not required, but consider computing permissions once at the top and passing down instead
of calling the hook again in the nested content component, for consistency with
`ParecerDetailContent`'s existing `permissions` prop pattern.

---

_Reviewed: 2026-07-17T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
