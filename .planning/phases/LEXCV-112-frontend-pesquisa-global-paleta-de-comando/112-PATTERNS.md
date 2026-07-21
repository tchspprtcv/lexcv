# Phase 112: Frontend — Pesquisa Global (Paleta de Comando) - Pattern Map

**Mapped:** 2026-07-21
**Files analyzed:** 9 (4 new, 5 modified) — zero backend files (Phase 111's contract is stable/unmodified)
**Analogs found:** 9 / 9 (2 with a direct precedent gap that the planner must treat as new capability, called out explicitly below)

## Contract Correction (read this before anything else)

`ARCHITECTURE.md` (written before Phase 111 landed) speculated a `SearchController` / `GET /api/v1/search` / `SearchResultDto`. **Phase 111 actually shipped something with different names** — verified by direct read:

- Endpoint: `GET /api/v1/pesquisa?q=` (not `/search`) — `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:50` (`@RequestMapping("/api/v1/pesquisa")`), single `@GetMapping` at line 109.
- DTO: `ResultadoPesquisaDto` (not `SearchResultDto`) — `backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java`, a `record(String tipo, String id, String titulo, String subtitulo, String rota)`.
- `tipo` is one of exactly `"cliente" | "processo" | "documento" | "parecer"`.
- **`rota` is already a complete, ready-to-navigate path** (e.g. `"/clientes/" + cliente.getId()`, `PesquisaController.java:177,210,223,236`). The frontend does **not** need to build its own `tipo → route segment` map (ARCHITECTURE.md's Integration Points table suggested one) — just `router.push(resultado.rota)`.
- Server-side minimum query length is **2 characters** (`TERMO_MIN_LENGTH = 2`, `PesquisaController.java:58,113`) — below that it returns `200 OK` + `[]` instantly. The frontend's debounce/enabled gate should mirror this `>= 2` threshold (matches the UI-SPEC's own "Loading" state row: "Debounced query ≥2 chars, request in flight").
- Auth is `@PreAuthorize("isAuthenticated()")` only at the method level (`PesquisaController.java:108`) — **not** `hasAnyAuthority(...)` as ARCHITECTURE.md speculated. Real RBAC is done per-branch inside the method body and silently omits a category the caller can't see; a caller with none of the 4 scopes gets `200 OK` + `[]`, never `403`. Practically: this endpoint will not throw for permission reasons — the frontend's error-state copy only needs to cover genuine network/5xx failures, not RBAC denial.
- Each of the 4 branches is independently try/caught server-side (`pesquisarComIsolamentoDeFalhas`, `PesquisaController.java:157-167`) — one failing category degrades to an empty list for that category only, never a whole-request 500.

Use these real names/paths in every file below — do not follow `ARCHITECTURE.md`'s hypothetical naming.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/components/shared/global-search-dialog.tsx` | component | request-response | `web/src/components/shared/notification-bell.tsx` (shell shape) + `web/src/components/shared/combobox.tsx` (Command usage) | role-match |
| `web/src/hooks/use-global-search.ts` | hook | request-response | `web/src/hooks/use-pareceres.ts` (`usePesquisarPareceres`) | exact |
| `web/src/lib/use-debounced-value.ts` | utility | transform | inline effect in `web/src/app/(dashboard)/clientes/page.tsx:80-85` | role-match |
| `web/src/types/search.ts` | model | transform | `web/src/types/pareceres.ts` | exact |
| `web/src/components/shared/dashboard-shell.tsx` | component | event-driven | itself (in-place) — mount precedent `notification-bell.tsx`; icon-button precedent is its own hamburger button | exact |
| `web/src/app/(dashboard)/clientes/page.tsx` | component | CRUD | `dashboard-shell.tsx` (only existing `useSearchParams` user) + its own debounce effect | partial-match |
| `web/src/app/(dashboard)/processos/page.tsx` | component | CRUD | `web/src/app/(dashboard)/clientes/page.tsx` (near-identical filter/debounce shape) | exact |
| `web/src/app/(dashboard)/documentos/page.tsx` | component | CRUD | `web/src/components/shared/notification-bell.tsx:66-68` (client-side display-only filter) | partial-match — **no free-text filter of any kind exists on this page today, see gap note** |
| `web/src/app/(dashboard)/pareceres/page.tsx` | component | CRUD | itself, `onPesquisar` handler (lines 98-115) | role-match — **different state shape than clientes/processos, see gap note** |

---

## Pattern Assignments

### `web/src/components/shared/global-search-dialog.tsx` (component, request-response)

**Analogs:** `web/src/components/shared/notification-bell.tsx` (self-contained shell) + `web/src/components/shared/combobox.tsx` (Command primitive usage)

**Self-contained shell pattern** — `notification-bell.tsx:49-53`:
```tsx
export function NotificationBell() {
  const [open, setOpen] = React.useState(false);
  const unread = useNotificacoesUnreadCount();
  const count = unread.data?.count ?? 0;
  const showBadge = !unread.isLoading && (unread.isError || count > 0);
```
Copy this shape: `GlobalSearchDialog` owns its own `open` state, is mounted once with zero props from `dashboard-shell.tsx`, and manages its own side effects (here: the global keydown listener) — exactly how `NotificationBell` manages its own polling (`{ poll: true }` at line 59) without the parent knowing about it.

**Icon-only trigger button (36px, topbar convention)** — `notification-bell.tsx:82-99`:
```tsx
<PopoverTrigger asChild>
  <Button
    type="button"
    variant="ghost"
    aria-label="Notificações"
    className="h-9 w-9 p-0 rounded-full text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors relative"
  >
    <Bell className="h-[1.1rem] w-[1.1rem]" />
    {showBadge && ( ... )}
  </Button>
</PopoverTrigger>
```
Use this exact `h-9 w-9` / `aria-label` shape for the mobile icon-only trigger (per UI-SPEC: `Search`, `aria-label="Pesquisar"`). Note `NotificationBell` uses `Popover`, not `Dialog` — for `GlobalSearchDialog` the shell wraps `CommandDialog` (which is a `Dialog` under the hood, see below) instead of `Popover`; only the "owns its own trigger + open state" shape is being copied, not the Popover primitive itself.

**`Command` inside a popover/dialog, ranking preserved** — `combobox.tsx:107-119` (`PopoverContent` wrapper omitted, only the `Command` part matters here):
```tsx
<Command shouldFilter={false}>
  <CommandInput
    placeholder={searchPlaceholder}
    value={query}
    onValueChange={setQuery}
  />
  <CommandList>
    <CommandGroup>
      {filtered.map((option) => (
        <CommandItem key={option.value} value={itemKey} onSelect={() => commit(option.value)}>
          {option.label}
        </CommandItem>
      ))}
    </CommandGroup>
    <CommandEmpty>{loading ? "A carregar..." : emptyMessage}</CommandEmpty>
  </CommandList>
</Command>
```
`shouldFilter={false}` **must** be copied verbatim onto the `Command` root inside `CommandDialog` — cmdk's own fuzzy `command-score` scorer otherwise re-sorts (or, at score `0`, silently hides) the backend's already-correctly-ranked results. This is the single most important line to replicate exactly.

**`CommandDialog` override for title/description** — `web/src/components/ui/command.tsx:36-63` (primitive, reused unmodified):
```tsx
function CommandDialog({
  title = "Command Palette",
  description = "Search for a command to run...",
  children,
  className,
  ...props
}: React.ComponentProps<typeof Dialog> & { title?: string; description?: string; className?: string }) {
  return (
    <Dialog {...props}>
      <DialogHeader className="sr-only">
        <DialogTitle>{title}</DialogTitle>
        <DialogDescription>{description}</DialogDescription>
      </DialogHeader>
      <DialogContent className={cn("top-1/3 translate-y-0 overflow-hidden rounded-xl! p-0", className)}>
        {children}
      </DialogContent>
    </Dialog>
  )
}
```
`open`/`onOpenChange` pass straight through `...props` to Radix `Dialog` — pass `title="Pesquisa global"` / `description="Pesquisar clientes, processos, documentos e pareceres da sua instituição"` per the UI-SPEC copy table; this overrides the English defaults, no new component logic needed for Esc/click-outside/focus-return (Radix default, already relied on elsewhere).

**Entity icon auto-sizing** — `command.tsx:155` — `CommandItem`'s own class includes `[&_svg:not([class*='size-'])]:size-4`, so the four `lucide-react` icons (`Users`, `Scale`, `FileText`, `ScrollText`) need **no explicit sizing class** when placed as the first child of a `CommandItem`.

**Entity → icon map (copy verbatim)** — `dashboard-shell.tsx:29-37`:
```tsx
const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
  { href: "/financeiro", label: "Financeiro", icon: Wallet, requiredPermission: "financeiro:view" },
  { href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" },
];
```
Map `tipo` → icon as `{ cliente: Users, processo: Scale, documento: FileText, parecer: ScrollText }`, same order as this array (Clientes → Processos → Documentos → Pareceres — Agenda/Financeiro are not part of this feature, skip them).

**"Ver todos" link, accent color + no icon** — `notification-bell.tsx:170-178`:
```tsx
<Link
  href="/notificacoes"
  className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium"
  onClick={() => setOpen(false)}
>
  Ver todas as notificações
</Link>
```
Copy this class string verbatim for each group's "Ver todos os {Entidade}" `CommandItem` (per UI-SPEC's reserved-accent-color rule) — plain text, no icon, closes the dialog on click, same as this precedent.

**Error/loading/empty tri-state render** — `notification-bell.tsx:115-127`:
```tsx
{list.isPending ? (
  <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">A carregar...</p>
) : list.isError ? (
  <p className="px-4 py-6 text-sm text-red-600 text-center">
    Não foi possível carregar as notificações. Verifique a ligação e tente novamente.
  </p>
) : !visibleNotificacoes.length ? (
  <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">Sem notificações por agora.</p>
) : ( ... )}
```
Structural precedent for the dialog's own `isPending` (no indicator, per locked decision) → debounced-and-fetching (`Skeleton` rows) → `isError` (inline text, mirrored copy per UI-SPEC: *"Não foi possível pesquisar. Verifique a ligação e tente novamente."*) → empty (`Empty`/`EmptyTitle`/`EmptyDescription`, not this plain `<p>`) → results branching chain.

**Permission gate for skeleton-only display optimization** — `web/src/hooks/use-permissions.ts` (full file) + usage pattern below (Shared Patterns) — gate each group's skeleton rows on `permissions.isFetched && permissions.can.view("<scope>")`, **never** `!permissions.isLoading` (see Shared Patterns section — recurring bug class in this codebase).

**Novel pieces this file must author fresh (no codebase analog — see "No Analog Found" below):** the global `keydown` listener, the `sessionStorage` recent-items read/write/dedupe, the bold-substring-highlight renderer, and the `<kbd>` shortcut hint.

---

### `web/src/hooks/use-global-search.ts` (hook, request-response)

**Analog:** `web/src/hooks/use-pareceres.ts` — `usePesquisarPareceres` (lines 60-81), an enabled-gated `useQuery` keyed by trimmed search text hitting a `GET` with a query param, returning an array of DTOs. This is the closest possible match — same shape, different endpoint.

```typescript
export function usePesquisarPareceres(
  filters: ParecerPesquisaFilters = {},
  options: { enabled?: boolean } = {},
) {
  const enabled = typeof window !== "undefined" && (options.enabled ?? true);
  const texto = filters.texto?.trim() ?? "";
  // ...other filters trimmed the same way...

  return useQuery({
    queryKey: ["pareceres", "pesquisa", texto, /* ...other filters... */],
    queryFn: () =>
      apiFetch<ParecerSolicitacao[]>(
        `/pareceres/pesquisa${buildParecerPesquisaSearch({ texto, /* ... */ })}`,
      ),
    enabled,
    staleTime: 30_000,
  });
}
```

Copy this shape for `useGlobalSearch(debouncedQ: string)`:
- `queryKey: ["pesquisa", debouncedQ]` (per UI-SPEC's own recommendation)
- `queryFn: () => apiFetch<ResultadoPesquisa[]>(`/pesquisa?q=${encodeURIComponent(debouncedQ)}`)`
- `enabled: typeof window !== "undefined" && debouncedQ.trim().length >= 2` — mirror the backend's own `TERMO_MIN_LENGTH = 2` gate (see Contract Correction above) so the hook never fires a request the server would just no-op.
- A short `staleTime` (e.g. `30_000`, matching every other list/search hook in this codebase) is reasonable — search results for an identical term rarely change mid-session.
- **`apiFetch` import** — `import { apiFetch } from "@/lib/api";` (see Shared Patterns — full file below).

**Do not** add a `SearchResultDto`/`/search` path — use `ResultadoPesquisa`/`/pesquisa` per the Contract Correction.

---

### `web/src/lib/use-debounced-value.ts` (utility, transform)

**Analog:** inline effect, duplicated identically in two files — `web/src/app/(dashboard)/clientes/page.tsx:80-85`:
```tsx
React.useEffect(() => {
  const t = window.setTimeout(() => {
    setFilters((current) => ({ ...current, q: draftQuery.trim() || undefined }));
  }, 300);
  return () => window.clearTimeout(t);
}, [draftQuery]);
```
and `web/src/app/(dashboard)/processos/page.tsx` (confirmed via search, same shape at lines 79-84):
```
79:  React.useEffect(() => {
81:      setFilters((c) => ({ ...c, q: draftQuery.trim() }));
84:  }, [draftQuery]);
```
Both are the exact 300ms `setTimeout` + `clearTimeout`-on-cleanup shape the UI-SPEC calls "the existing inline precedent." Extract it into a generic hook:
```typescript
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = React.useState(value);
  React.useEffect(() => {
    const t = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}
```
No existing standalone debounce hook file exists anywhere in `web/src/hooks/` or `web/src/lib/` today (confirmed by search) — this is a genuine extraction, not a copy of an existing file, but the logic itself is a verbatim lift of the two inline instances above.

---

### `web/src/types/search.ts` (model, transform)

**Analog:** `web/src/types/pareceres.ts` (full file) — the flattest, cleanest existing type file that mirrors a single backend DTO 1:1 with no snake_case/camelCase drift (unlike `types/clientes.ts`/`types/documentos.ts`, which carry historical dual-cased fields — see `CLAUDE.md`/`PROJECT.md`'s documented camelCase-drift precedent, not relevant here since the backend record's fields are already single lowercase words):
```typescript
export type ParecerStatus = "PENDENTE" | "EM_ELABORACAO" | "EM_REVISAO" | "CONCLUIDO";
export type ParecerPrioridade = "ALTA" | "MEDIA" | "BAIXA";

export interface ParecerSolicitacao {
  id: string;
  tenantId: string;
  clienteId: string;
  descricao: string;
  // ...
}
```
Mirror `ResultadoPesquisaDto` (`backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java`) field-for-field:
```typescript
export type PesquisaResultadoTipo = "cliente" | "processo" | "documento" | "parecer";

export interface ResultadoPesquisa {
  tipo: PesquisaResultadoTipo;
  id: string;
  titulo: string;
  subtitulo?: string;
  rota: string;
}
```
(`subtitulo` is nullable server-side for a parecer/documento with no secondary field — `PesquisaController.java`'s `montarSubtituloCliente` can return `null`; mark it optional.)

---

### `web/src/components/shared/dashboard-shell.tsx` (component, event-driven — MODIFIED)

**Analog:** itself — this file already contains every precedent needed for its own edit.

**Decorative `<Input>` to replace** — lines 121-127:
```tsx
<div className="hidden md:flex flex-1 max-w-md relative group">
  <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 transition-colors group-focus-within:text-blue-500" />
  <Input
    placeholder="Pesquisar processos, entidades..."
    className="pl-9 bg-slate-100/50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-800 focus-visible:ring-1 focus-visible:ring-blue-500/50 rounded-full text-sm h-9 shadow-sm transition-all"
  />
</div>
```
Per UI-SPEC: replace `<Input>` with a `<button>` carrying the **exact same classes** (`pl-9 bg-slate-100/50 ... rounded-full text-sm h-9 shadow-sm`), same left-aligned `Search` icon + `group-focus-within:text-blue-500`, placeholder-style "Pesquisar..." text, plus a trailing `⌘K`/`Ctrl K` `<kbd>` hint (novel, no analog — see below). Clicking/focusing opens `GlobalSearchDialog` instead of typing inline.

**Mobile icon-only trigger precedent (36px, `md:hidden`)** — hamburger button, lines 112-119:
```tsx
<button
  type="button"
  onClick={() => setDrawerOpen(true)}
  className="md:hidden flex items-center justify-center h-9 w-9 rounded-md text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
  aria-label="Abrir menu"
>
  <Menu className="h-5 w-5" />
</button>
```
Copy this `md:hidden` + `h-9 w-9` + `aria-label` shape for the new mobile `Search` icon trigger (per UI-SPEC: placed immediately before `ThemeToggle` in the action group below).

**Global mount point (`NotificationBell` precedent)** — lines 147-152:
```tsx
<div className="ml-auto flex items-center gap-3">
  <ThemeToggle />
  <NotificationBell />
  <div className="h-5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>
  <UserMenu variant="topbar" me={me.data} onLogout={onLogout} />
</div>
```
`<GlobalSearchDialog />` mounts the same way `<NotificationBell />` does — zero props, self-contained, dropped into this action group (mobile trigger goes here too, `md:hidden`, immediately before `<ThemeToggle />`).

**`useSearchParams` already imported here** — line 6 (`import { ... useSearchParams } from "next/navigation";`), used at lines 42/49. This is the **only** file in `web/src` that currently imports `useSearchParams` — it is the working precedent confirming this exact import, used inside an unwrapped `"use client"` component in this route group, needs no additional `Suspense` boundary in this codebase's current setup. The 4 list pages (also already `"use client"`) can add the identical import with the same confidence.

---

### `web/src/app/(dashboard)/clientes/page.tsx` (component, CRUD — MODIFIED)

**Analog:** its own existing filter/debounce machinery, combined with `dashboard-shell.tsx`'s `useSearchParams` precedent (no file in this codebase currently combines both — this is a first-time combination, hence "partial-match").

**Permission gate (copy this exact shape everywhere)** — lines 24-37:
```tsx
export default function ClientesPage() {
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  // ...
  if (permissions.isFetched && !canViewClientes) {
    return <AccessDeniedState ... />;
  }
  return <ClientesPageContent ... />;
}
```

**Existing filter state + debounce to seed** — lines 54-98:
```tsx
const [draftQuery, setDraftQuery] = React.useState("");
// ...other draft* fields...
const [filters, setFilters] = React.useState<ClientesListFilters>({});
// ...
React.useEffect(() => {
  const t = window.setTimeout(() => {
    setFilters((current) => ({ ...current, q: draftQuery.trim() || undefined }));
  }, 300);
  return () => window.clearTimeout(t);
}, [draftQuery]);
```
`ClientesListFilters.q` (`web/src/types/clientes.ts:121`) already round-trips to a real backend filter — `web/src/hooks/use-clientes.ts:16` (`if (filters.q?.trim()) sp.set("q", filters.q.trim());`) confirms `GET /clientes?q=` is a genuine, already-working server-side filter. Seeding is therefore simple: on mount, read `searchParams.get("q")`, and if present, call `setDraftQuery(q)` (so the visible input reflects it) — the existing 300ms debounce effect then naturally flows it into `filters.q` with zero new plumbing.

**New code needed (not present today):**
```tsx
const searchParams = useSearchParams();
React.useEffect(() => {
  const q = searchParams.get("q");
  if (q) setDraftQuery(q);
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, []); // mount-only, matches "seed once on mount" — do not re-run on every searchParams change
```
(Exact effect-dependency choice — mount-only vs. reactive to `searchParams` — is an implementation decision for the planner/executor; both `dashboard-shell.tsx:47-56` (mount-oriented reset effects) and `:54-56` (`pathname`-reactive effect) exist as precedent for either shape.)

---

### `web/src/app/(dashboard)/processos/page.tsx` (component, CRUD — MODIFIED)

**Analog:** `web/src/app/(dashboard)/clientes/page.tsx` (post-Phase-112 version) — confirmed near-identical shape today, pre-Phase-112:
```
40:  const [draftQuery, setDraftQuery] = React.useState("");
45:  const [filters, setFilters] = React.useState({
79:  React.useEffect(() => {
81:      setFilters((c) => ({ ...c, q: draftQuery.trim() }));
84:  }, [draftQuery]);
90:      q: draftQuery.trim(),
193:                    value={draftQuery}
```
`ProcessosListFilters.q` (`web/src/types/processos.ts:90`) also round-trips to a real backend filter — `web/src/hooks/use-processos.ts:147` (`if (filters.q?.trim()) sp.set("q", filters.q.trim());`) confirms `GET /processos?q=` genuinely works server-side. Apply the **exact same** new mount-effect as clientes — copy-paste, since the shape is identical (`draftQuery`/`filters`/300ms debounce all match one-for-one).

---

### `web/src/app/(dashboard)/documentos/page.tsx` (component, CRUD — MODIFIED)

**Gap — read before planning this file.** Unlike clientes/processos, `documentos/page.tsx` has **no free-text filter at all today**, client-side or server-side:
- `DocumentosListFilters` (`web/src/types/documentos.ts:18-21`) only has `processo_id?: string; cliente_id?: string;` — no `q`.
- `web/src/hooks/use-documentos.ts`'s query-building has no `q`/text param (confirmed in `ARCHITECTURE.md`'s own research — `GET /documentos` has no free-text query parameter today).
- The results block (`documentos/page.tsx:192-206`) renders `list.data` directly with **no `.filter()` call anywhere** — nothing to hook a `q` into.

Seeding `?q=` here per the locked decision (all 4 lists must pre-fill) requires **adding a new client-side filter**, not just reading into an existing one. Closest available analog for "filter an already-fetched array client-side without touching the query hook" is `notification-bell.tsx:66-68`:
```tsx
const visibleNotificacoes = (list.data?.content ?? [])
  .filter((n) => !(n.snoozedUntil && new Date(n.snoozedUntil) > new Date()))
  .slice(0, 10);
```
Suggested shape for `documentos/page.tsx`: add a local `q` state (seeded from `searchParams.get("q")` on mount, same as clientes/processos), and derive a filtered view of `list.data` with `.filter((d) => d.nome.toLowerCase().includes(q.toLowerCase()))` before passing to `<DataTable>`/the mobile card map (lines 204-225) — purely a display-side filter, no change to `useDocumentos`/`DocumentosListFilters`/the backend. This is a materially bigger change than clientes/processos and should be scoped/estimated accordingly by the planner.

---

### `web/src/app/(dashboard)/pareceres/page.tsx` (component, CRUD — MODIFIED)

**Gap — different state shape than clientes/processos.** `pareceres/page.tsx` has **two independent filter modes**, not one `filters.q`:
```
46:  const [draftStatus, setDraftStatus] = React.useState("todos");
49:  const [filters, setFilters] = React.useState<ParecerSolicitacoesListFilters>({});   // simple list — NO q field
51:  const [pesquisaOpen, setPesquisaOpen] = React.useState(false);
52:  const [pesquisaTexto, setPesquisaTexto] = React.useState("");
58:  const [pesquisaFilters, setPesquisaFilters] = React.useState<ParecerPesquisaFilters>({}); // advanced search — HAS texto
59:  const [pesquisaSubmitted, setPesquisaSubmitted] = React.useState(false);
```
`ParecerSolicitacoesListFilters` has no free-text field; `ParecerPesquisaFilters.texto` is the actual search-text carrier, and it only takes effect when `pesquisaSubmitted === true` (line 76: `const searchActive = pesquisaSubmitted;`) and the panel exposing its input is only rendered `{pesquisaOpen ? (...) : null}` (line 239). The submit handler to mirror programmatically is `onPesquisar` (lines 98-115):
```tsx
const onPesquisar = (e: React.FormEvent) => {
  e.preventDefault();
  const next: ParecerPesquisaFilters = {};
  const texto = pesquisaTexto.trim();
  // ...
  if (texto) next.texto = texto;
  // ...
  setPesquisaFilters(next);
  setPesquisaSubmitted(true);
};
```
Seeding `?q=` here means, on mount: `setPesquisaTexto(q)`, `setPesquisaFilters({ texto: q })`, `setPesquisaSubmitted(true)`, **and** `setPesquisaOpen(true)` (so the panel showing the populated search box is actually visible, not just active behind a collapsed toggle). Do **not** write into `filters`/`setFilters` — that state has no `q`/`texto` field and drives the unrelated simple-list mode.

---

## Shared Patterns

### Permission gate — `isFetched`, never `!isLoading`
**Source:** `web/src/hooks/use-permissions.ts` (full file) + `web/src/lib/permissions.ts` (full file)
**Apply to:** `global-search-dialog.tsx` (gate each result group's skeleton placeholder) and all 4 list pages (already present, do not regress it)
```tsx
const permissions = usePermissions();
const canView = permissions.can.view("clientes");
if (permissions.isFetched && !canView) { /* AccessDeniedState */ }
```
Confirmed identical at `clientes/page.tsx:30`, `documentos/page.tsx:35`, `pareceres/page.tsx:30`. `CLAUDE.md`/`112-CONTEXT.md` both flag `!permissions.isLoading` as a recurring regression (Phase 103/105/v2.13-audit) — never reintroduce it.

### `apiFetch` — the only HTTP path
**Source:** `web/src/lib/api.ts` (full file, 55 lines)
```typescript
export async function apiFetch<TResponse>(path: string, init: RequestInit = {}): Promise<TResponse> {
  // ...
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: "include" });
  if (!res.ok) {
    // ...
    if (res.status !== 401 && res.status !== 403) {
      toast.error(`Erro ${res.status}: ${errorMessage}`);
    }
    throw new Error(`API ${res.status}: ${errorMessage}`);
  }
  // ...
  return (await res.json()) as TResponse;
}
```
**Apply to:** `use-global-search.ts` — call `apiFetch<ResultadoPesquisa[]>(`/pesquisa?q=${encodeURIComponent(debouncedQ)}`)`. Errors already toast automatically (except 401/403) — the dialog's own inline error copy (per UI-SPEC) is additive to, not a replacement for, this global toast.

### Self-contained global shell, mounted once
**Source:** `web/src/components/shared/notification-bell.tsx` (full file) mounted at `dashboard-shell.tsx:149`
**Apply to:** `global-search-dialog.tsx` — owns its own `open` state + keydown listener; parent (`dashboard-shell.tsx`) passes zero props, same relationship as `<NotificationBell />`.

### `Command` + `shouldFilter={false}`
**Source:** `web/src/components/shared/combobox.tsx:107`
**Apply to:** the `Command` root inside `GlobalSearchDialog`'s `CommandDialog` — omitting this lets cmdk's fuzzy scorer re-rank or hide the backend's pre-ranked results.

### Empty/loading states
**Source:** `web/src/app/(dashboard)/dashboard/page.tsx:38-58` (local `EmptyState` wrapper) + `:174-192` (Skeleton-row loading branch), built on the unmodified primitives `web/src/components/ui/empty.tsx` and `web/src/components/ui/skeleton.tsx`
```tsx
function EmptyState({ icon: Icon, title, description }: {...}) {
  return (
    <Empty>
      <EmptyHeader>
        <EmptyMedia variant="icon"><Icon /></EmptyMedia>
        <EmptyTitle className="text-sm font-semibold">{title}</EmptyTitle>
        <EmptyDescription>{description}</EmptyDescription>
      </EmptyHeader>
    </Empty>
  );
}
// ...
{kpis.isLoading ? (
  <>{Array.from({ length: 3 }).map((_, index) => (
    <div key={index} className="flex gap-4">
      <Skeleton className="h-10 w-10" />
      <div className="min-w-0 pt-1"><Skeleton className="h-4 w-40" /><Skeleton className="h-3 w-24 mt-1" /></div>
    </div>
  ))}</>
) : entries.length === 0 ? (
  <EmptyState icon={Inbox} title="Sem atividade recente" description="..." />
) : ( /* results */ )}
```
**Apply to:** `global-search-dialog.tsx` — pre-query empty state and no-results state both use `Empty`/`EmptyHeader`/`EmptyMedia`/`EmptyTitle`/`EmptyDescription` per this exact composition; loading state uses `Skeleton` rows per group (`h-8 w-full rounded-md`, 3 rows/group per UI-SPEC).

### Entity icon map
**Source:** `web/src/components/shared/dashboard-shell.tsx:29-37` (`NAV` array)
**Apply to:** `global-search-dialog.tsx` result-group headers/icons — `{ cliente: Users, processo: Scale, documento: FileText, parecer: ScrollText }`, same import source (`lucide-react`).

---

## No Analog Found

Genuinely novel pieces — no existing file in `web/src` does anything like these today (confirmed by direct search, not just omission):

| Concern | Role | Data Flow | Where it's needed | Reason no analog |
|---|---|---|---|---|
| Global `window.addEventListener("keydown", ...)` for Ctrl+K/⌘K | event-driven | event-driven | `global-search-dialog.tsx` | Zero `addEventListener` calls exist anywhere in `web/src` today (confirmed by search). Closest *structural* precedent is the generic React `useEffect` mount/cleanup shape already used at `dashboard-shell.tsx:47-56` and the debounce effects above — same "effect + cleanup" skeleton, novel keydown-handling content. Must check `event.metaKey || event.ctrlKey`, call `event.preventDefault()`, and toggle `open`. |
| `sessionStorage`-backed recent items (cap 5, dedupe by `(tipo,id)`, most-recent-first) | utility | file-I/O (browser storage) | `global-search-dialog.tsx` | Zero `sessionStorage`/`localStorage` usage exists anywhere in `web/src` today (confirmed by search) — this is the first feature in the codebase to use session storage at all. No read/write/serialize helper to copy from; write it fresh (a small `JSON.parse`/`JSON.stringify` array of `ResultadoPesquisa`-shaped entries, capped and deduped on write). |
| Bold-substring-highlight renderer (case-insensitive literal match → plain/bold `<span>` split, no-match-found → plain fallback) | utility | transform | `global-search-dialog.tsx` (title/subtitle rendering) | Zero `highlight`/`<mark>` usage anywhere in `web/src` today (confirmed by search). Write a small pure function: find the query's index in the target string case-insensitively; if found, render `<>{before}<strong>{match}</strong>{after}</>`; if not found (e.g. backend matched via `unaccent`), render the plain string — per the UI-SPEC's explicit simple-fallback rule. |
| `<kbd>` shortcut hint (`⌘K` / `Ctrl K`, platform-detected) | component | transform | desktop trigger in `dashboard-shell.tsx` | Zero `<kbd>` elements and zero `navigator.platform`/Mac-detection logic exist anywhere in `web/src` today (confirmed by search). Small, self-contained, standard convention (shadcn's own Command docs cover this exact pattern externally) — style with `text-xs`, muted-foreground, per UI-SPEC's typography table (Regular 400 weight, no new weight budget consumed). |
| `documentos/page.tsx` free-text filter | utility (display-only) | transform | `documentos/page.tsx` | See gap note above — no `q` exists client- or server-side for documentos today. Nearest usable analog is `notification-bell.tsx:66-68`'s display-only `.filter()` pattern, but the field being filtered (`d.nome`) and the state plumbing are entirely new for this page. |

---

## Metadata

**Analog search scope:** `web/src/components/shared/`, `web/src/components/ui/`, `web/src/hooks/`, `web/src/lib/`, `web/src/types/`, `web/src/app/(dashboard)/{clientes,processos,documentos,pareceres,dashboard}/`, plus read-only reference reads of `backend/src/main/java/com/lexcv/{controllers/PesquisaController.java,dtos/ResultadoPesquisaDto.java}` for the Phase 111 contract this phase consumes (no backend files are created/modified by Phase 112).
**Files scanned/read directly this session:** 24 (9 target files' current-state analogs + `notification-bell.tsx`, `dashboard-shell.tsx`, `combobox.tsx`, `use-pareceres.ts`, `lib/api.ts`, `ui/command.tsx`, `ui/empty.tsx`, `ui/skeleton.tsx`, `ui/dialog.tsx`, `dashboard/page.tsx`, `use-permissions.ts`, `lib/permissions.ts`, `types/pareceres.ts`, `types/clientes.ts`, `types/documentos.ts`, plus targeted greps across `processos/page.tsx`, `types/processos.ts`, `use-clientes.ts`, `use-processos.ts`, and codebase-wide greps for `sessionStorage`, `debounce`, `<kbd`, `highlight`, `addEventListener("keydown"`).
**Pattern extraction date:** 2026-07-21
