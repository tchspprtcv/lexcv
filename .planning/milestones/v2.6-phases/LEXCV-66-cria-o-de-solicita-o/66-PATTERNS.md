# Phase 66: Criação de Solicitação - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 4 (1 new, 3 modified)
**Analogs found:** 4 / 4

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/pareceres/nova/page.tsx` | component (page, create form) | request-response (single POST, no wizard) | `web/src/app/(dashboard)/processos/novo/page.tsx` (Step 1 "Intake" form only — ignore steps 2/3) | role-match (analog is 3-step wizard; this phase is single-step, use step-1 sub-pattern) |
| `web/src/hooks/use-pareceres.ts` (add `useCreateParecer`) | hook (mutation) | CRUD (create) | `web/src/hooks/use-clientes.ts#useCreateCliente` (simpler, no normalize wrapper — matches pareceres' pure-camelCase convention) | exact |
| `web/src/schemas/pareceres.ts` (add create-form schema) | utility (Zod schema) | transform (validation) | `web/src/schemas/processos.ts#processoFormSchema` / `#prazoFormSchema` | exact |
| `web/src/app/(dashboard)/pareceres/page.tsx` (add CTA button) | component (page, list header edit) | request-response (nav only) | Same file's own header block (self-analog) + `processos/novo/page.tsx`'s `Link`-wrapped `Button asChild` pattern | exact |

## Pattern Assignments

### `web/src/app/(dashboard)/pareceres/nova/page.tsx` (component, request-response)

**Analog:** `web/src/app/(dashboard)/processos/novo/page.tsx` (use only the page-shell + Step 1 "Intake" card — do not replicate the 3-step wizard, conflict-check, or formalizar logic; this form is a single `POST` and redirects immediately)

**Imports pattern** (lines 1-32 of analog, adapt names):
```typescript
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { useCreateParecer } from "@/hooks/use-pareceres";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import {
  parecerCreateFormSchema,
  type ParecerCreateFormValues,
} from "@/schemas/pareceres";
```
Note: no `Badge`/`Check`/`ChevronRight` needed (no step indicator, no wizard). `useRouter` only needed for the success redirect (`router.push`); no other client-side navigation state.

**Style constants** (copy verbatim, per UI-SPEC "duplication pattern", analog lines 34-38):
```typescript
const selectClassName =
  "flex h-9 w-full rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 py-1 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50";

const textareaClassName =
  "flex min-h-24 w-full rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 py-2 text-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50 dark:placeholder:text-neutral-400";
```

**Access-guard pattern** (analog lines 40-54, copy shape exactly, change permission scope/backHref/copy):
```typescript
export default function ParecerCreatePage() {
  const permissions = usePermissions();
  const canCreatePareceres = permissions.can.create("pareceres");

  if (!permissions.isLoading && !canCreatePareceres) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar solicitações de parecer."
        backHref="/pareceres"
      />
    );
  }

  return <ParecerCreateFormContent />;
}
```

**Cliente `<select>` pattern** (analog lines 279-302 — copy structure, disabled-while-loading, inline Zod + fetch-error rendering):
```typescript
const clientes = useClientes({});
// ...
<div className="space-y-2">
  <Label htmlFor="clienteId">Cliente</Label>
  <select
    id="clienteId"
    className={selectClassName}
    disabled={clientes.isPending || clientes.isError}
    {...form.register("clienteId")}
  >
    <option value="">{clientes.isPending ? "A carregar..." : "Selecionar cliente"}</option>
    {(clientes.data ?? []).map((c) => (
      <option key={c.id} value={c.id}>
        {c.nome}
      </option>
    ))}
  </select>
  {clientes.isError ? (
    <p className="text-sm text-red-600">
      {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar clientes"}
    </p>
  ) : null}
  {form.formState.errors.clienteId ? (
    <p className="text-sm text-red-600">{form.formState.errors.clienteId.message}</p>
  ) : null}
</div>
```

**Processo `<select>` (optional, client-filtered)** — no direct analog does client-filtering by another field's watched value; combine the cliente `<select>` shape above with `useProcessos` (`web/src/hooks/use-processos.ts` lines 70-78, 130+) and `form.watch("clienteId")`:
```typescript
const clienteIdValue = form.watch("clienteId");
const processos = useProcessos(clienteIdValue ? { cliente_id: clienteIdValue } : {});
// <select> options render processos.data ?? [] the same way as cliente options above;
// placeholder: "Nenhum processo associado" (per UI-SPEC); not disabled when clienteId is empty —
// useProcessos({}) with no cliente_id returns the flat tenant list (confirm at planning time
// whether an unfiltered call is acceptable or should be gated behind `enabled: Boolean(clienteId)`
// inside a wrapper — CONTEXT.md leaves this at Claude's discretion).
```

**Advogado `<select>` client-filtered by role** (list page `pareceres/page.tsx` lines 61-65 — copy this filtering idiom exactly, it's the phase's own established convention from Phase 65):
```typescript
const adminUsers = useAdminUsers();
const advogados = React.useMemo(
  () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
  [adminUsers.data],
);
// <select> placeholder: "Atribuir mais tarde"; options: advogados.map(u => <option value={u.id}>{u.nome}</option>)
```
Note: this requires `import * as React from "react"` for `useMemo` (already used elsewhere in the module set).

**Prioridade `<select>` with default** — no analog has a pre-selected enum default; combine `processoFaseStatusSchema`-style enum options (analog `processos/novo/page.tsx` lines 519-529, decisao "nivel" select) with RHF `defaultValues.prioridade = "MEDIA"`:
```typescript
<select id="prioridade" className={selectClassName} {...form.register("prioridade")}>
  <option value="ALTA">Alta</option>
  <option value="MEDIA">Média</option>
  <option value="BAIXA">Baixa</option>
</select>
```

**Date input (`prazo`)** — copy the native date input pattern verbatim (analog lines 372-382, `data_inicio`):
```typescript
<Input
  id="prazo"
  type="date"
  className="rounded-none focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
  {...form.register("prazo")}
/>
```
Confirms CONTEXT.md's "Claude's discretion" note: no date-picker component exists anywhere in the codebase (only native `<input type="date">`), so use that — no further research needed.

**Descrição `<textarea>`** (analog lines 399-410, adapt placeholder/copy per UI-SPEC):
```typescript
<div className="space-y-2">
  <Label htmlFor="descricao">Descrição</Label>
  <textarea
    id="descricao"
    className={textareaClassName}
    placeholder="Descreva o pedido de parecer (contexto, questão jurídica, urgência)"
    {...form.register("descricao")}
  />
  {form.formState.errors.descricao ? (
    <p className="text-sm text-red-600">{form.formState.errors.descricao.message}</p>
  ) : null}
</div>
```

**Submit handler + success redirect pattern** (analog lines 105-124 `onStep1Submit`, adapt: no intermediate step, redirect straight to detail page per UI-SPEC):
```typescript
const [formError, setFormError] = React.useState<string | null>(null);
const createParecer = useCreateParecer();

const onSubmit = async (values: ParecerCreateFormValues) => {
  setFormError(null);
  if (!canCreatePareceres) {
    setFormError("Não tem permissão para criar solicitações de parecer.");
    return;
  }
  try {
    const created = await createParecer.mutateAsync(values);
    toast.success("Solicitação de parecer criada com sucesso.");
    router.push(`/pareceres/${created.id}`);
  } catch (e) {
    const msg =
      e instanceof Error
        ? e.message
        : "Não foi possível criar a solicitação. Verifique a ligação e tente novamente.";
    setFormError(msg);
    toast.error(msg);
  }
};
```

**Submit + cancel button pair** (analog lines 414-425, adapt copy per UI-SPEC — "Criar Solicitação"/"A criar..."/"Cancelar", note UI-SPEC uses "Cancelar" not "Voltar" here):
```typescript
<div className="flex gap-2">
  <Button
    type="submit"
    className="rounded-none font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white max-sm:min-h-[48px]"
    disabled={form.formState.isSubmitting || createParecer.isPending || permissions.isLoading || !canCreatePareceres}
  >
    {form.formState.isSubmitting || createParecer.isPending ? "A criar..." : "Criar Solicitação"}
  </Button>
  <Button asChild type="button" variant="outline" className="rounded-none">
    <Link href="/pareceres">Cancelar</Link>
  </Button>
</div>
```

**Page header** (analog lines 196-208, single Card, no step indicator — per UI-SPEC "no wizard/step indicator", `text-2xl` not `text-3xl`):
```typescript
<div className="space-y-6">
  <div className="flex items-start justify-between gap-4">
    <div>
      <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Nova Solicitação de Parecer</h1>
      <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
        Registe os dados da solicitação para encaminhar ao advogado responsável.
      </p>
    </div>
    <Button asChild variant="outline" className="rounded-none">
      <Link href="/pareceres">Cancelar</Link>
    </Button>
  </div>
  <Card>
    <CardHeader>
      <CardTitle>Dados da Solicitação</CardTitle>
    </CardHeader>
    <CardContent>
      <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
        {/* fields */}
      </form>
    </CardContent>
  </Card>
</div>
```

---

### `web/src/hooks/use-pareceres.ts` — add `useCreateParecer` (hook, CRUD)

**Analog:** `web/src/hooks/use-clientes.ts#useCreateCliente` (lines 73-86) — simplest create-mutation shape in the codebase, no API/domain-model bridge (pareceres uses pure camelCase per Phase 65's SUMMARY, same as clientes' direct-JSON approach here, NOT `use-processos.ts#useCreateIntake`'s `toProcessoApiPayload`/`normalizeProcesso` bridge).

**Pattern to copy** (imports already present in `use-pareceres.ts`; only `useQueryClient` needs to be added to the existing `import { useMutation, useQuery } from "@tanstack/react-query";` line):
```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export type ParecerCreateRequest = {
  clienteId: string;
  processoId?: string;
  descricao: string;
  prazo?: string;
  prioridade?: ParecerPrioridade;
  advogadoId?: string;
};

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
Import `ParecerPrioridade` from `@/types/pareceres` alongside the existing `ParecerSolicitacao, ParecerVersao` import.

---

### `web/src/schemas/pareceres.ts` — add create-form schema (utility, transform)

**Analog:** `web/src/schemas/processos.ts#processoFormSchema` (lines 3-22, the `optionalTrimmedString` helper) combined with `#prazoFormSchema` (lines 92-97, enum-with-default pattern).

**Pattern to copy:**
```typescript
import { z } from "zod";

export const parecerStatusSchema = z.enum(["PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO"]);
export const parecerPrioridadeSchema = z.enum(["ALTA", "MEDIA", "BAIXA"]);

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const parecerCreateFormSchema = z.object({
  clienteId: z.string().trim().min(1, "Selecione um cliente."),
  processoId: optionalTrimmedString,
  descricao: z
    .string()
    .trim()
    .min(10, "A descrição deve ter pelo menos 10 caracteres."),
  prazo: optionalTrimmedString,
  prioridade: parecerPrioridadeSchema.default("MEDIA"),
  advogadoId: optionalTrimmedString,
});

export type ParecerCreateFormValues = z.infer<typeof parecerCreateFormSchema>;
```
Note: UI-SPEC lists two distinct error copies for `descricao` ("Descreva o pedido de parecer." for empty, "A descrição deve ter pelo menos 10 caracteres." for too-short) — `z.string().trim().min(10, ...)` alone only produces the second message even on empty input; if both exact copies are required, use `.superRefine` (see `processos.ts` lines 58-75 for the `superRefine` pattern) to emit the empty-specific message when `data.descricao.length === 0`. Otherwise a single `.min(10, ...)` message for both cases is an acceptable simplification — flag as a planning decision.

---

### `web/src/app/(dashboard)/pareceres/page.tsx` — add "Nova Solicitação" CTA (component, request-response)

**Analog:** Same file's own header block (lines 92-98) needs a sibling button; copy the `Button asChild` + `Link` idiom from `processos/novo/page.tsx` lines 205-207, and the permission-check idiom already used at the top of this same file (`permissions.can.view("pareceres")`, line 39).

**Pattern to copy** (add `usePermissions` import already present at line 14; add `Link`, already imported at line 3; add `Button` already imported at line 8):
```typescript
// inside ParecerPageContent, alongside existing usePermissions() call pattern from the outer ParecerPage:
const permissions = usePermissions();
const canCreatePareceres = permissions.can.create("pareceres");

// header block, replace lines 94-98:
<div className="flex items-start justify-between gap-6">
  <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
    Pareceres Jurídicos
  </h1>
  {canCreatePareceres ? (
    <Button asChild className="rounded-none font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white">
      <Link href="/pareceres/nova">Nova Solicitação</Link>
    </Button>
  ) : null}
</div>
```
Note: `ParecerPageContent` currently does not call `usePermissions()` itself (only the outer `ParecerPage` wrapper does, for the view-gate). Need a second `usePermissions()` call inside `ParecerPageContent` (cheap — TanStack Query dedupes by query key) to get `create` scope for the CTA gate, mirroring how `processos/novo/page.tsx`'s `ProcessoWizardContent` re-calls `usePermissions()` independently of its outer guard component (lines 41 vs 58).

---

## Shared Patterns

### Access-guard + permission check
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx` lines 40-54, `web/src/hooks/use-permissions.ts` (`permissions.can.create(scope)` / `.view(scope)`)
**Apply to:** `pareceres/nova/page.tsx` (create-gate), `pareceres/page.tsx` (CTA visibility gate)
```typescript
const permissions = usePermissions();
const canX = permissions.can.create("pareceres"); // or .view(...)
if (!permissions.isLoading && !canX) {
  return <AccessDeniedState description="..." backHref="/pareceres" />;
}
```

### Mutation + query-key invalidation
**Source:** `web/src/hooks/use-clientes.ts#useCreateCliente` (lines 73-86)
**Apply to:** `use-pareceres.ts#useCreateParecer`
```typescript
return useMutation({
  mutationFn: (payload) => apiFetch("/path", { method: "POST", body: JSON.stringify(payload) }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] });
  },
});
```

### Toast + redirect on success, inline error + toast on failure
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx` lines 105-124 (`onStep1Submit`), lines 164-178 (`onFormalizar`)
**Apply to:** `pareceres/nova/page.tsx` submit handler
```typescript
try {
  const created = await mutation.mutateAsync(values);
  toast.success("...");
  router.push(`/target/${created.id}`);
} catch (e) {
  const msg = e instanceof Error ? e.message : "fallback copy";
  setFormError(msg);
  toast.error(msg);
}
```

### Native `<select>` populated from TanStack Query list (no Combobox)
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx` lines 279-302; `web/src/app/(dashboard)/pareceres/page.tsx` lines 61-65 (role-filtered variant)
**Apply to:** all four `<select>` fields in the create form (`clienteId`, `processoId`, `prioridade`, `advogadoId`)

## No Analog Found

None — all four files have a strong or exact analog; no gaps requiring RESEARCH.md fallback patterns.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/processos/novo/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/hooks/use-clientes.ts`, `web/src/hooks/use-processos.ts`, `web/src/hooks/use-admin.ts`, `web/src/hooks/use-pareceres.ts`, `web/src/schemas/processos.ts`, `web/src/schemas/pareceres.ts`, `web/src/types/pareceres.ts`
**Files scanned:** 9
**Pattern extraction date:** 2026-07-01
