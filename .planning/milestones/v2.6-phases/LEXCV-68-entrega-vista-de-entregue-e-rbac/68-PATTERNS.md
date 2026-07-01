# Phase 68: Entrega, Vista de Entregue e RBAC - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 2 (1 edit to page.tsx across 4 concerns, 1 edit to hook file)
**Analogs found:** 4 / 4

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — entrega `AlertDialog` trigger + confirm | component (page section) | request-response (irreversible mutation) | `web/src/app/(dashboard)/agenda/[id]/page.tsx` lines 166-215 (delete-confirmation `AlertDialog`) | exact |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — "Parecer Entregue" summary block | component (page section) | transform (derive-from-existing-cache lookup, no new fetch) | Same file's existing timeline-rendering block (lines 213-255) that maps over `versoes.data` | role-match (read-only summary card, same file/module) |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — CardTitle typography fix (module-wide) | component (style fix) | n/a | Same file's own `NovaVersaoForm` CardTitle (line 325, already correct: `text-lg font-bold`) | exact (self-analog — the fix is to match the one correct instance already in the file) |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — timeline-dot accent fix (line 221) | component (style fix) | n/a | UI-SPEC mandate; no in-repo analog needed (straight class-swap `bg-blue-600` → `bg-slate-400 dark:bg-slate-500`) | n/a (mechanical fix) |
| `web/src/hooks/use-pareceres.ts` — `useEntregarParecer` | hook (mutation) | request-response (PUT, no body, query-param, cascading cache invalidation) | `useCreateParecerVersao` in same file (lines 91-144) — closest shape (mutation + `onSuccess` cache invalidation); simpler variant needed since no XHR/upload/progress is involved (no body at all, `PUT` with query param) | role-match (same file, same hook family; simplify to plain `apiFetch`) |

## Pattern Assignments

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — Entrega `AlertDialog` (controller/component, request-response)

**Analog:** `web/src/app/(dashboard)/agenda/[id]/page.tsx`

**Imports pattern** (agenda `[id]/page.tsx` lines 7-17, already present in pareceres `[id]/page.tsx` — reuse identically, just add the entrega hook import):
```tsx
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
```
Pareceres detail page does not yet import `alert-dialog` — add this import block. It already imports `Button`, `Card*`, `toast`, `usePermissions`.

**Trigger + dialog structure** (agenda `[id]/page.tsx` lines 165-216 — direct copy target):
```tsx
{canEditAgenda && evento.data ? (
  <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
    <AlertDialogTrigger asChild>
      <Button type="button" variant="secondary" className="border border-red-300 text-red-600 hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950" disabled={isDeleting}>
        Apagar
      </Button>
    </AlertDialogTrigger>
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>Apagar evento</AlertDialogTitle>
        <AlertDialogDescription>
          Tem a certeza que deseja apagar este evento? Esta ação não pode ser revertida.
        </AlertDialogDescription>
      </AlertDialogHeader>
      {deleteError ? (
        <p className="text-sm text-red-600 px-1">{deleteError}</p>
      ) : null}
      <AlertDialogFooter>
        <AlertDialogCancel disabled={isDeleting}>Cancelar</AlertDialogCancel>
        <AlertDialogAction
          disabled={isDeleting}
          onClick={(e) => { e.preventDefault(); void handleDeleteSeries(); }}
          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
        >
          {del.isPending ? "A apagar..." : "Apagar evento"}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
) : null}
```

**Adaptation required for entrega (per CONTEXT.md/UI-SPEC):**
- Trigger button copy: "Entregar Parecer" (not "Apagar"); per UI-SPEC use `variant="destructive"` (or equivalent `bg-destructive` styling) on the trigger itself — same visual weight as the confirm action, not the outline-red style agenda uses for its trigger. This is a deliberate divergence from the agenda trigger's exact class list — copy the *structure* (AlertDialog/Trigger/Content/Footer/error-display/disabled-during-pending), not the trigger's specific Tailwind classes.
- `AlertDialogTitle`: "Entregar Parecer"
- `AlertDialogDescription`: "Esta ação é irreversível. Depois de entregue, o parecer não pode receber novas versões nem ser reaberto." — plus a version-selection control (see below), since agenda's dialog has no equivalent nested form control; this is new structure inside `AlertDialogContent`, not present in the analog.
- Version selection: a `<select>` or radio-list of `versoes.data` (already fetched via `useParecerVersoes(id)` on the page, no new fetch) showing `numeroVersao` + `formatDateTime(createdAt)`, defaulting to the most recent (`versoes.data[versoes.data.length - 1]` given the array is chronological ascending as rendered in the timeline) but requiring explicit user confirmation of the selected value (controlled `useState<string | null>` for `selectedVersaoId`).
- Error display: reuse `{deleteError ? <p className="text-sm text-red-600 px-1">{deleteError}</p> : null}` pattern verbatim, renamed to `entregaError`.
- `AlertDialogCancel`: "Cancelar", `disabled={entregarMutation.isPending}` — identical pattern.
- `AlertDialogAction`: `className="bg-destructive text-destructive-foreground hover:bg-destructive/90"` (verbatim from agenda), `onClick={(e) => { e.preventDefault(); void handleEntregar(); }}`, label "Confirmar Entrega" / pending "A entregar...".
- `open`/`onOpenChange` controlled state (`const [confirmOpen, setConfirmOpen] = React.useState(false)`), matching agenda's `confirmOpen` pattern exactly.

**Handler pattern** (agenda `[id]/page.tsx` lines 106-117 — `handleDeleteSeries`, the shape to mirror for `handleEntregar`):
```tsx
const handleDeleteSeries = async () => {
  setDeleteError(null);
  try {
    await del.mutateAsync();
    toast.success("Evento apagado com sucesso.");
    router.push("/agenda");
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Erro ao apagar evento";
    setDeleteError(msg);
    toast.error(msg);
  }
};
```
Entrega variant does NOT navigate away (stays on the same detail page to show "Parecer Entregue") and must pass `versaoFinalId` from local state to the mutation:
```tsx
const handleEntregar = async () => {
  if (!selectedVersaoId) return;
  setEntregaError(null);
  try {
    await entregar.mutateAsync({ versaoFinalId: selectedVersaoId });
    toast.success("Parecer entregue com sucesso.");
    setConfirmOpen(false);
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Não foi possível entregar o parecer. Verifique a ligação e tente novamente.";
    setEntregaError(msg);
    toast.error(msg);
  }
};
```

**Visibility/gating pattern** (pareceres `[id]/page.tsx` lines 134-141, already-established `isResponsavelOuAdmin` + status check — reuse identically, do not re-derive):
```tsx
const me = permissions.data;
const canEditPareceres = permissions.can.edit("pareceres");
const isResponsavelOuAdmin =
  Boolean(me?.roles.includes("ADMIN")) ||
  Boolean(parecer.data?.advogadoId && parecer.data.advogadoId === me?.id);
const isConcluido = parecer.data?.status === "CONCLUIDO";
```
Entrega trigger condition mirrors `showNovaVersaoForm`'s shape exactly:
```tsx
const showEntregarTrigger =
  !permissions.isLoading && canEditPareceres && isResponsavelOuAdmin && !isConcluido;
```

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — "Parecer Entregue" summary block (component, transform/read-only)

**Analog:** same file's timeline block (lines 213-255) for the "read from already-fetched `versoes.data`, no new query" pattern, and the "Parecer já entregue" placeholder card it replaces (lines 265-275):

```tsx
) : isConcluido ? (
  <Card>
    <CardContent className="py-6">
      <p className="text-sm font-medium text-slate-900 dark:text-white">
        Parecer já entregue
      </p>
      <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
        Não é possível submeter novas versões após a entrega final.
      </p>
    </CardContent>
  </Card>
) : showNovaVersaoForm ? (
```

This existing `isConcluido` branch (currently a placeholder text card in the "Nova Versão" slot) is the block to replace/upgrade into the richer "Parecer Entregue" summary card. Reuse:
- The `AnexoLink` component (lines 81-110) verbatim for the "Descarregar anexo"/"Sem anexo" link — same signature `<AnexoLink solicitacaoId={id} versaoId={...} caminhoAnexo={...} />`.
- `resolveUserNome` (lines 128-129) for the author name lookup.
- `formatDateTime`/`formatDate` helpers (lines 38-50) already in file.
- Lookup: `const versaoFinal = versoes.data?.find((v) => v.id === parecer.data?.versaoFinalId);` — no new hook/fetch, per CONTEXT.md decision.
- Badge: reuse the existing `Badge variant="green"` convention from the "Estado" field's `statusVariant()` (line 175) for the CONCLUIDO visual accent — UI-SPEC calls for green as "the only color accent in this block."

Field copy per UI-SPEC's Copywriting Contract table: "Versão {numeroVersao}", "Elaborado por {autor} em {data}" (sourced from `versaoFinal.criadoPorId`/`versaoFinal.createdAt`, NOT a fabricated entrega-timestamp field), anexo link. Card surface uses secondary/30% palette (`bg-slate-50 dark:bg-slate-900` or equivalent already used elsewhere on the page — check `Card` component default before adding an override).

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — CardTitle typography fix (mechanical, module-wide)

**Analog:** the one already-correct instance in the same file:
```tsx
// line 325 — CORRECT, copy this className to all others
<CardTitle className="text-lg font-bold">Nova Versão</CardTitle>
```

**Fix required** — every `<CardTitle>` in the module gets `className="text-lg font-bold"`:

| File | Line | Current | Required |
|------|------|---------|----------|
| `pareceres/[id]/page.tsx` | 161 | `<CardTitle className="font-bold">Dados</CardTitle>` | `<CardTitle className="text-lg font-bold">Dados</CardTitle>` |
| `pareceres/[id]/page.tsx` | 194 | `<CardTitle className="font-bold">Versões</CardTitle>` | `<CardTitle className="text-lg font-bold">Versões</CardTitle>` |
| `pareceres/[id]/page.tsx` | 325 | `<CardTitle className="text-lg font-bold">Nova Versão</CardTitle>` | already correct, no change |
| `pareceres/[id]/page.tsx` | new "Entregar Parecer"/"Parecer Entregue" cards (if wrapped in `Card`/`CardHeader`/`CardTitle`) | n/a | must be authored with `className="text-lg font-bold"` from the start |
| `pareceres/nova/page.tsx` | 121 | `<CardTitle>Dados da Solicitação</CardTitle>` | `<CardTitle className="text-lg font-bold">Dados da Solicitação</CardTitle>` |

Also check `pareceres/page.tsx` (list page) for any `CardTitle` usage not caught by the grep above (grep found none in the list page — likely no `Card`/`CardTitle` there, confirm during execution).

**Note:** `AlertDialogTitle` for "Entregar Parecer" must NOT receive this fix — UI-SPEC explicitly grandfathers it at the shared component's default `font-semibold` (600), matching agenda's unmodified "Apagar evento" dialog title.

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` — timeline-dot accent-color fix (mechanical)

**Current** (line 221):
```tsx
<span className="h-2.5 w-2.5 rounded-full shrink-0 bg-blue-600" />
```

**Required fix:**
```tsx
<span className="h-2.5 w-2.5 rounded-full shrink-0 bg-slate-400 dark:bg-slate-500" />
```
No status-based color-coding — status signal is already carried by the "Estado" `Badge`.

---

### `web/src/hooks/use-pareceres.ts` — `useEntregarParecer` (hook, request-response)

**Analog:** `useCreateParecerVersao` in the same file (lines 91-144) for the mutation + cascading invalidation shape, but the entrega call has NO body and NO upload/XHR — it's a plain `PUT` with a query param, so the simpler `useCreateParecer` shape (lines 71-84, plain `apiFetch` + single invalidation) is the better structural match for the request itself, combined with the multi-key invalidation from `useCreateParecerVersao`.

**Plain-mutation pattern to copy** (`useCreateParecer`, lines 71-84):
```typescript
export function useCreateParecer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ParecerCreateRequest) =>
      apiFetch<ParecerSolicitacao>("/pareceres/solicitacoes", {
        method: "POST",
        body: JSON.stringify(payload satisfies ParecerCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] });
    },
  });
}
```

**Cascading invalidation pattern to copy** (`useCreateParecerVersao`, lines 136-142 — but per CONTEXT.md, entrega does NOT need to invalidate the `versoes` key since no version row is created/modified):
```typescript
onSuccess: async () => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["pareceres", "versoes", solicitacaoId] }),
    queryClient.invalidateQueries({ queryKey: ["pareceres", "detail", solicitacaoId] }),
    queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
  ]);
},
```

**Composed `useEntregarParecer` shape (planner/executor guidance, not existing code):**
```typescript
export function useEntregarParecer(solicitacaoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { versaoFinalId: string }) =>
      apiFetch<ParecerSolicitacao>(
        `/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/entregar?versaoFinalId=${encodeURIComponent(payload.versaoFinalId)}`,
        { method: "PUT" },
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pareceres", "detail", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
      ]);
    },
  });
}
```
Note: `versaoFinalId` is a query-string param on the backend (`@RequestParam UUID versaoFinalId`, confirmed in `ParecerController.entregarSolicitacao`, backend line 356), NOT a JSON body field — no `body:`/`JSON.stringify` in the request, matching CONTEXT.md's explicit call-out.

---

## Shared Patterns

### AlertDialog irreversible-action confirmation
**Source:** `web/src/app/(dashboard)/agenda/[id]/page.tsx` lines 166-215
**Apply to:** the new entrega trigger/dialog in `pareceres/[id]/page.tsx`
- Controlled `open`/`onOpenChange` state, `AlertDialogTrigger asChild` wrapping a `Button`, error paragraph (`text-sm text-red-600`) rendered inside `AlertDialogContent` above the footer, `AlertDialogCancel disabled={isPending}`, `AlertDialogAction` styled `bg-destructive text-destructive-foreground hover:bg-destructive/90` with `e.preventDefault()` + async handler + pending-label swap.

### RBAC instance-check (advogado responsável OR ADMIN)
**Source:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx` lines 134-141 (already established in Phase 67)
**Apply to:** entrega trigger visibility — reuse `isResponsavelOuAdmin` + `canEditPareceres` + `!isConcluido` combination verbatim, matching backend's `isAdmin || isResponsavel` check in `ParecerController.entregarSolicitacao`.

### Cascading cache invalidation after mutating actions
**Source:** `web/src/hooks/use-pareceres.ts` — `useCreateParecerVersao` (lines 136-142), `useCreateParecer` (lines 80-83)
**Apply to:** `useEntregarParecer` — invalidate `["pareceres","detail",id]` and `["pareceres","list"]` only (no `versoes` key per CONTEXT.md, since entrega changes `status`/`versaoFinalId` on the solicitação, not a version row).

### Toast + inline-error dual-channel on mutation failure
**Source:** `pareceres/[id]/page.tsx` `NovaVersaoForm`'s `onSubmit` catch block (lines 311-319) and agenda's `handleDeleteSeries` catch block (lines 112-116)
**Apply to:** `handleEntregar` — `setEntregaError(msg)` + `toast.error(msg)` together, matching the established dual-channel convention from Phase 66/67.

### "No dead buttons" / silent omission over disabled state
**Source:** `pareceres/[id]/page.tsx` `showNovaVersaoForm` conditional render (lines 259-278) — card fully omitted, not disabled, when RBAC/status gates fail
**Apply to:** entrega trigger — omit entirely (not render a disabled button) when `!showEntregarTrigger`, matching Phase 67's established convention and this phase's explicit RBAC-audit decision.

## No Analog Found

None — all files/sections have a strong same-file or same-module analog. The version-selection control inside the entrega `AlertDialogContent` (dropdown/list of versões) has no direct prior analog in this codebase (agenda's dialog has no nested form control); build it from `versoes.data` already in scope on the page using plain HTML `<select>` or the existing `Label`+radio pattern used elsewhere in forms (e.g. `NovaVersaoForm`'s `Label`/`Textarea` structure) — treat as net-new structure per UI-SPEC's explicit call-out, not a gap requiring a different analog.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/agenda/[id]/page.tsx`, `web/src/app/(dashboard)/pareceres/**`, `web/src/hooks/use-pareceres.ts`, `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (entregarSolicitacao endpoint, lines 353-371+), `web/src/types/pareceres.ts`
**Files scanned:** 6
**Pattern extraction date:** 2026-07-01
