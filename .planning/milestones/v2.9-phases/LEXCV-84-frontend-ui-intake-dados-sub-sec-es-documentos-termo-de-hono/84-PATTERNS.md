# Phase 84: Frontend — UI (Intake, Dados, Sub-secções, Documentos, Termo de Honorários) - Pattern Map

**Mapped:** 2026-07-07
**Files analyzed:** 3 (2 modified, 1 new)
**Analogs found:** 3 / 3

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|---------------|
| `web/src/app/(dashboard)/processos/[id]/page.tsx` (modified — add 4 tabs, refactor Partes/Fases, Dados card additions) | component (detail page, tabbed) | CRUD + file-I/O (Decisão upload) | `web/src/app/(dashboard)/clientes/[id]/page.tsx` (Dialog-tab pattern) | exact |
| `web/src/app/(dashboard)/processos/novo/page.tsx` (modified — add Origem field) | component (multi-step form) | CRUD (create) | itself, sibling `tipo_processo` field in same file | exact (self-analog) |
| `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` (new) | component (printable route) | request-response (read-only render) | `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` | exact |

## Pattern Assignments

### `web/src/app/(dashboard)/processos/[id]/page.tsx` — new tabs (Decisões/Factos/Testemunhas/Documentos) + Partes/Fases refactor + Dados card additions

**Analog:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` (Dialog "Adicionar" sections, lines ~892-1027) and `web/src/app/(dashboard)/clientes/[id]/page.tsx` documento-row component (lines ~1223-1468).

**Current file state (baseline to diff against — full read, no analog needed, this IS the file being edited):**

Current imports (`web/src/app/(dashboard)/processos/[id]/page.tsx` lines 1-87) — **missing** `DialogTrigger` (must be added to the existing `@/components/ui/dialog` import), missing `Table`/`TableBody`/`TableCell`/`TableHead`/`TableHeader`/`TableRow` (only used by the Cliente page's `ClienteProcessosTab`/`ClienteParecerTab`; the existing Partes/Fases/new tabs on this page use raw `<table>` elements — **keep raw `<table>`, do not introduce shadcn `Table` here**, so as not to mix conventions within one file), missing `FileDropZone` import, missing hooks (`useDecisoes`, `useAddDecisao`, `useUpdateDecisao`, `useDeleteDecisao`, `useTestemunhas`, `useAddTestemunha`, `useUpdateTestemunha`, `useDeleteTestemunha`, `useFactos`, `useAddFacto`, `useUpdateFacto`, `useDeleteFacto`, `useHonorarios` (for the Dados-card "Gerar Termo" gate), `useDocumentos`/`useDeleteDocumento`/`useDownloadDocumento`/`useUploadDocumentoComProgresso` from `@/hooks/use-documentos`), missing schema imports (`decisaoFormSchema`, `testemunhaFormSchema`, `factoFormSchema` + their `*FormValues` types from `@/schemas/processos`), missing lib imports (`tipoDecisaoToLabel` from `@/lib/tipo-decisao.ts`, `tipoTestemunhaToLabel` from `@/lib/tipo-testemunha.ts`, `origemProcessoToLabel` from `@/lib/origem-processo.ts`), missing `Printer` icon from `lucide-react` (needed for "Gerar Termo de Honorários" — actually per UI-SPEC this button does NOT carry a Printer icon, only the print-route "Imprimir" button does; verify against UI-SPEC copy table — "Gerar Termo de Honorários" has no icon specified).

```typescript
// current top-of-file imports, lines 1-22 (processos/[id]/page.tsx)
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { useForm } from "react-hook-form";
import {
  AlertCircle, Calendar, CheckCircle2, Circle, FileText, GitBranch,
  Paperclip, Pause, Play, Plus, RotateCcw, ShieldCheck, User, XCircle,
} from "lucide-react";
```

**`TabKey` union — current (line 93) vs. required:**
```typescript
// current
type TabKey = "timeline" | "partes" | "fases" | "auditoria";
// required (per UI-SPEC "Component & Layout Notes")
type TabKey =
  | "timeline" | "partes" | "fases" | "decisoes" | "factos"
  | "testemunhas" | "documentos" | "auditoria";
```

**Tab button toggle group — exact pattern to extend (lines 915-946), insert 4 new `Button` entries between the "fases" and "auditoria" buttons, same shape:**
```typescript
<Button
  type="button"
  variant={tab === "fases" ? "secondary" : "outline"}
  onClick={() => setTab("fases")}
>
  Fases
</Button>
{/* insert here: Decisões / Factos / Testemunhas / Documentos buttons, same shape, before the canManageProcessos-gated Auditoria button */}
{canManageProcessos ? (
  <Button type="button" variant={tab === "auditoria" ? "secondary" : "outline"} onClick={() => setTab("auditoria")}>
    Auditoria
  </Button>
) : null}
```

**Dialog "Adicionar" pattern to replicate exactly** — source `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 892-923 ("Documentos a Tratar" tab body):
```typescript
<Card>
  <CardContent className="space-y-2 pt-6">
    <div className="flex items-center justify-between">
      <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos a Tratar</h4>
      {canEditClientes && isEditing ? (
        <Dialog open={addDocATratarModal} onOpenChange={setAddDocATratarModal}>
          <DialogTrigger asChild>
            <Button type="button" variant="outline" size="sm">Adicionar</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Adicionar Documento a Tratar</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="new-doc-tratar-descricao">Descrição</Label>
                <Input
                  id="new-doc-tratar-descricao"
                  className="rounded-none"
                  value={newDocATratar.descricao}
                  onChange={(e) => setNewDocATratar({ descricao: e.target.value })}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setAddDocATratarModal(false)}>Cancelar</Button>
              <Button type="button" onClick={confirmAddDocATratar}>Confirmar</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      ) : null}
    </div>
    {/* list rendering: empty-state paragraph vs. <ul>/<table> map, with per-row remove button, see below */}
  </CardContent>
</Card>
```

For the **4 new tabs (Decisões/Factos/Testemunhas/Documentos)** this maps onto a Card+table (reusing the existing Partes/Fases raw `<table>` markup style from this same file, NOT the Cliente page's `<ul><li>` style — UI-SPEC's Claude's Discretion note says "seguir a densidade já usada nas tabelas de Partes/Fases", i.e. `<table className="w-full ... text-sm">` with `<thead>`/`<tbody>` exactly like the current Partes tab (lines 1258-1280) and Fases tab (lines 1320-1367) in this file), with the `size="sm" variant="outline"` Dialog trigger placed in the `CardHeader`'s `flex items-center justify-between` row next to `CardTitle` — this differs slightly from the Cliente analog (which puts the trigger in `CardContent` next to an `<h4>` instead of a `CardHeader`/`CardTitle`) because this file's Partes/Fases tabs already use `CardHeader`+`CardTitle` for the list card; keep that shell, just add the Dialog trigger into the header's flex row:

```typescript
// target shape, combining this file's existing Card/CardHeader/CardTitle shell (line 1250-1253)
// with the Cliente page's Dialog trigger pattern:
<Card>
  <CardHeader>
    <div className="flex items-center justify-between">
      <CardTitle>Decisões</CardTitle>
      {canEditProcessos ? (
        <Dialog open={addDecisaoModal} onOpenChange={setAddDecisaoModal}>
          <DialogTrigger asChild>
            <Button type="button" variant="outline" size="sm">Adicionar Decisão</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader><DialogTitle>Adicionar Decisão</DialogTitle></DialogHeader>
            <form onSubmit={decisaoForm.handleSubmit(onSubmitDecisao)}>
              {/* data / tipo (select from tipoDecisaoToLabel) / resumo / native <input type="file" {...decisaoForm.register("file")} /> */}
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setAddDecisaoModal(false)}>Cancelar</Button>
                <Button type="submit" disabled={addDecisao.isPending}>Confirmar</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      ) : null}
    </div>
  </CardHeader>
  <CardContent>
    {decisoes.isLoading ? (
      <div className="text-sm text-neutral-500">A carregar...</div>
    ) : decisoes.isError ? (
      <div className="text-sm text-red-600">Não foi possível carregar as decisões deste processo.</div>
    ) : !decisoes.data?.length ? (
      <div className="text-sm text-neutral-500 dark:text-neutral-400">Nenhuma decisão registada.</div>
    ) : (
      <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
        <table className="w-full min-w-[480px] text-sm">
          <thead className="text-left text-neutral-500 dark:text-neutral-400">
            <tr className="border-b border-neutral-200 dark:border-neutral-800">
              <th className="py-2 pr-4 font-medium">Data</th>
              <th className="py-2 pr-4 font-medium">Tipo</th>
              <th className="py-2 pr-4 font-medium">Resumo</th>
              <th className="py-2 pr-4 font-medium">Ações</th>
            </tr>
          </thead>
          <tbody>
            {decisoes.data.map((d) => (
              <tr key={d.id} className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
                <td className="py-2 pr-4">{formatDate(d.data)}</td>
                <td className="py-2 pr-4">{tipoDecisaoToLabel(d.tipo)}</td>
                <td className="py-2 pr-4">{d.resumo ?? "—"}</td>
                <td className="py-2 pr-4 flex items-center gap-2">
                  {/* edit trigger opens Dialog pre-populated via decisaoForm.reset(d) */}
                  <button type="button" className="text-neutral-500 hover:text-red-600" onClick={() => onDeleteDecisao(d.id)} aria-label="Apagar decisão">✕</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </CardContent>
</Card>
```

**Delete confirmation + toast pattern** (mirrors `ClienteDocumentoEntregueRow.onDelete`, `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 1413-1423):
```typescript
const onDelete = async () => {
  const ok = window.confirm("Apagar este documento?");
  if (!ok) return;
  try {
    await del.mutateAsync();
    toast.success("Documento apagado com sucesso.");
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
    toast.error(msg);
  }
};
```
Apply verbatim per entity, substituting the confirm string / success toast / aria-label per the Copywriting Contract table in UI-SPEC (`"Apagar esta decisão?"` / `"Decisão apagada com sucesso."` / `aria-label="Apagar decisão"`, etc.).

**Partes tab refactor** — current grid-2-column implementation to REPLACE (lines 1206-1284): the left-side `Card` with `CardTitle>Adicionar parte` and inline `<form>` (lines 1209-1248) must be removed and its form body moved into a `Dialog` triggered from a `size="sm" variant="outline"` Button next to the existing `Partes` `CardTitle` (same shell shown above). The right-side `Partes` list `Card` (lines 1250-1283, unchanged table markup) stays as-is, just no longer in a `grid lg:grid-cols-2` — becomes a single full-width `Card`. No new hooks needed (`useProcessoPartes`/`useAddProcessoParte` already imported and used) — this is a pure JSX reshuffle, not a data-layer change. `parteForm`/`onSubmitParte`/`parteServerError` state (lines 205-209, 326-338) stays, only the trigger moves into a `Dialog`.

**Fases tab refactor** — current grid-2-column implementation to REPLACE (lines 1285-1371): same transformation — move the "Adicionar fase" `Card`+form (lines 1287-1309) into a `Dialog` triggered next to the `Fases` `CardTitle`. The fases table (lines 1312-1370), including the per-row inline `<select>` status control (`faseDraftStatus`/`onUpdateFaseStatus`, lines 1336-1362) and its "Guardar" button, is UNCHANGED per UI-SPEC ("Ações" column keeps the existing inline status update — no delete hook exists for `ProcessoFase`).

**Dados card — Juízo/Origem fields + Termo de Honorários button** — current `dl` grid (lines 450-496) to extend. Insert new `dt`/`dd` pairs following the exact existing pattern (e.g. after "Tribunal", before or after "Cliente" — planner's discretion on ordering, UI-SPEC doesn't pin exact position):
```typescript
// existing pattern to copy (line 460-461, "Tribunal" row)
<dt className="text-neutral-500 dark:text-neutral-400">Tribunal</dt>
<dd className="col-span-2">{processo.data.tribunal ?? "—"}</dd>

// new Juízo row, same shape
<dt className="text-neutral-500 dark:text-neutral-400">Juízo</dt>
<dd className="col-span-2">{processo.data.juizo ?? "—"}</dd>

// new Origem row — read-only, immutable, uses origemProcessoToLabel()
<dt className="text-neutral-500 dark:text-neutral-400">Origem</dt>
<dd className="col-span-2">{processo.data.origem ? origemProcessoToLabel(processo.data.origem) : "—"}</dd>
```

"Gerar Termo de Honorários" button — mirrors the "Imprimir Ficha" button pattern from `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 367-376 (target `_blank`, `Link` wrapped in `Button asChild`), placed conditionally on `processo.data?.estado === "ATIVO"`:
```typescript
// analog: clientes/[id]/page.tsx lines 367-376
<Button asChild variant="outline">
  <Link href={`/clientes/${encodeURIComponent(id)}/ficha`} target="_blank" rel="noopener noreferrer">
    <Printer className="h-4 w-4 mr-2" />
    Imprimir Ficha
  </Link>
</Button>

// target, in the Dados CardContent (no Printer icon per UI-SPEC copy table — plain text label)
{processo.data?.estado === "ATIVO" ? (
  <Button asChild className="rounded-none font-bold bg-blue-600 hover:bg-blue-700 text-white">
    <Link href={`/processos/${encodeURIComponent(id)}/termo-honorarios`} target="_blank" rel="noopener noreferrer">
      Gerar Termo de Honorários
    </Link>
  </Button>
) : null}
```

**Decisão file-input registration (native input, NOT `FileDropZone`)** — per CONTEXT.md/UI-SPEC, this is intentionally different from the Documentos tab's drop-zone:
```typescript
// decisaoForm uses decisaoFormSchema from @/schemas/processos (already built, Phase 83)
const decisaoForm = useForm<DecisaoFormValues>({
  resolver: zodResolver(decisaoFormSchema),
  defaultValues: { data: "", tipo: undefined, resumo: undefined },
});
// ...
<input id="decisao_file" type="file" {...decisaoForm.register("file")} />
// on submit, useAddDecisao(id) expects DecisaoCreateRequest { file?: File; data; tipo; resumo? }
// — extract values.file?.[0] (FileList -> File) before calling addDecisao.mutateAsync
const onSubmitDecisao = async (values: DecisaoFormValues) => {
  try {
    await addDecisao.mutateAsync({
      data: values.data,
      tipo: values.tipo,
      resumo: values.resumo,
      file: values.file?.[0],
    });
    toast.success("Decisão adicionada com sucesso.");
    setAddDecisaoModal(false);
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Erro ao adicionar decisão";
    toast.error(msg);
  }
};
```

**Documentos tab (processo)** — mirror `ClienteDocumentosEntreguesTab` (`web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 1223-1390 + `ClienteDocumentoEntregueRow` lines 1392-1468) verbatim, scoped by `processo_id` instead of `cliente_id`:
```typescript
// list hook — same hook, different filter key
const documentos = useDocumentos({ processo_id: id });
// upload hook — identical, with FileDropZone + tipo datalist + progress bar (lines 1242, 1305-1345 in clientes page)
const upload = useUploadDocumentoComProgresso({ onProgress: setProgresso });
// on confirm — pass processo_id instead of cliente_id
await upload.mutateAsync({ file: novoFicheiro, tipo: novoTipo.trim(), processo_id: id });
```
Row component (`ClienteDocumentoEntregueRow`) is reusable almost as-is — `useDeleteDocumento(documento.id)` / `useDownloadDocumento(documento.id)` are generic (not cliente-scoped), so the row markup (lines 1434-1467) can be copied directly; only the wire-shape workaround comment (WR-01, lines 1404-1411, reading `tamanho`/`createdAt` off the raw entity) needs to be re-verified against the processo-documentos endpoint's actual response shape (do not assume it's identical — check network response or backend DTO before reusing blindly).

---

### `web/src/app/(dashboard)/processos/novo/page.tsx` — add Origem field to intake step 1

**Analog:** the file's own sibling field `tipo_processo` (lines 304-323), since Origem must match its exact select/placeholder/error convention.

**Pattern to copy (lines 304-323), adapted for Origem:**
```typescript
// current tipo_processo field — exact select+placeholder+error shape to replicate
<div className="space-y-2">
  <Label htmlFor="tipo_processo">Tipo de Processo</Label>
  <select id="tipo_processo" className={selectClassName} {...intakeForm.register("tipo_processo")}>
    <option value="">Selecionar tipo</option>
    <option value="civel">Cível</option>
    {/* ... */}
  </select>
  {intakeForm.formState.errors.tipo_processo ? (
    <p className="text-sm text-red-600">{intakeForm.formState.errors.tipo_processo.message}</p>
  ) : null}
</div>

// target: new Origem field, options sourced from origemProcessoToLabel(), bound to
// processoIntakeFormSchema's `origem: origemProcessoSchema` (already required, Phase 83)
<div className="space-y-2">
  <Label htmlFor="origem">Origem</Label>
  <select id="origem" className={selectClassName} {...intakeForm.register("origem")}>
    <option value="">Selecionar origem</option>
    <option value="PETICAO_INICIAL">{origemProcessoToLabel("PETICAO_INICIAL")}</option>
    <option value="NOTIFICACOES_AVULSAS">{origemProcessoToLabel("NOTIFICACOES_AVULSAS")}</option>
  </select>
  {intakeForm.formState.errors.origem ? (
    <p className="text-sm text-red-600">{intakeForm.formState.errors.origem.message}</p>
  ) : null}
</div>
```

**IMPORTANT — schema mismatch to resolve in the plan:** this page currently uses `useForm<ProcessoFormValues>({ resolver: zodResolver(processoFormSchema), ... })` (line 79-93), i.e. `processoFormSchema`, NOT `processoIntakeFormSchema`. `processoFormSchema` does not have an `origem` field — only `processoIntakeFormSchema` (its `.extend()` in `web/src/schemas/processos.ts` lines 32-34) does. The plan must either (a) switch this form's resolver/type to `processoIntakeFormSchema`/`ProcessoIntakeFormValues`, or (b) manually register an `origem` field not covered by the current schema (loses Zod validation — not recommended). Also update `defaultValues` (line 81-92) to include `origem: undefined` and confirm `ProcessoCreateRequest.origem?: OrigemProcesso` (`web/src/types/processos.ts` line 64) is passed through unchanged in `onStep1Submit`'s `intakeValues` spread (line 113) — no extra mapping needed since `values` already carries `origem` once the schema switch is made.

---

### `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx` (new)

**Analog:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (full file, 239 lines — read entirely, no analog gaps).

**PRINT_CSS + window.print() + BLANK pattern to clone verbatim (lines 22-42):**
```typescript
const PRINT_CSS = `
  @media print {
    aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn {
      display: none !important;
    }
    body {
      background: white !important;
    }
  }
  @page {
    size: A4;
    margin: 2cm;
  }
`;

const BLANK = "___________";

function fmt(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return BLANK;
  return String(value);
}
```

**Page shell (lines 44-115) to replicate, substituting the Cliente-specific hooks for Processo/Cliente/Honorário ones (see "Termo de Honorários data sourcing" below):**
```typescript
export default function TermoHonorariosPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");

  if (!permissions.isLoading && !canViewProcessos) {
    return <AccessDeniedState description="Não tem permissão para consultar este processo." backHref="/processos" />;
  }

  return <TermoHonorariosContent id={id} />;
}

function TermoHonorariosContent({ id }: { id: string }) {
  const processo = useProcesso(id);
  const cliente = useCliente(processo.data?.cliente_id ?? "");
  const honorarios = useHonorarios({ processoId: id });
  const honorario = honorarios.data?.[0]; // one Honorario per processo (Phase 82 invariant)

  const isLoading = processo.isLoading || cliente.isLoading || honorarios.isLoading;
  const isError = processo.isError || cliente.isError || honorarios.isError;

  const isBlocked = !honorario || honorario.valorTotal === null;

  return (
    <div>
      {/* eslint-disable-next-line react/no-danger */}
      <style dangerouslySetInnerHTML={{ __html: PRINT_CSS }} />
      <div className="flex items-center justify-between gap-4 mb-6" data-print-hide>
        <Link href={`/processos/${encodeURIComponent(id)}`} className="text-sm hover:underline">
          &larr; Voltar ao processo
        </Link>
        <div>
          <Button type="button" className="ficha-print-btn" data-print-hide disabled={isBlocked} onClick={() => window.print()}>
            <Printer className="h-4 w-4 mr-2" />
            Imprimir
          </Button>
          {isBlocked && honorario ? (
            <p className="text-sm text-red-600 mt-2">
              O valor dos honorários ainda não foi preenchido. Preencha o valor em{" "}
              <Link href={`/financeiro/${honorario.id}`} className="text-blue-600 hover:underline">Financeiro</Link>{" "}
              antes de gerar o termo.
            </p>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">Erro ao carregar o termo.</div>
      ) : !honorario ? (
        <div className="text-sm text-red-600">
          Não foi possível gerar o termo: nenhum honorário associado a este processo.
        </div>
      ) : processo.data && cliente.data ? (
        <TermoHonorarios processo={processo.data} cliente={cliente.data} honorario={honorario} />
      ) : null}
    </div>
  );
}
```

**`SectionTitle`/`Field` components to reuse verbatim (lines 117-133):**
```typescript
function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="uppercase font-semibold text-sm border-b border-gray-300 pb-1 mb-3 mt-6">
      {children}
    </h2>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  const isBlank = value === BLANK;
  return (
    <div className="grid grid-cols-2 gap-2 py-1 text-sm">
      <span className="text-gray-600">{label}</span>
      <span className={isBlank ? "font-mono underline" : ""}>{value}</span>
    </div>
  );
}
```

**Document body sections** (per UI-SPEC's section-heading list: `Identificação do Cliente`, `Identificação do Processo`, `Honorários`, `Data e Assinaturas`) — pattern per Ficha Cliente's `Ficha()` render function (lines 171-237): a `max-w-[210mm] mx-auto p-8 bg-white text-black` wrapper, a centered header block (tenant name / doc title / date), then `SectionTitle`+`Field` pairs per section, ending in the same "Data e Assinaturas" two-column signature block (lines 224-235) — clone that block's markup verbatim (labels would become e.g. "O Advogado" / "O Cliente" — exact wording is Claude's Discretion per UI-SPEC).

**Termo de Honorários data sourcing (UI-SPEC "Component & Layout Notes"):**
```typescript
// combine 3 hooks — useProcesso already imported in processos/[id]/page.tsx from @/hooks/use-processos
// useCliente from @/hooks/use-clientes; useHonorarios from @/hooks/use-financeiro
const processo = useProcesso(id);
const cliente = useCliente(processo.data?.cliente_id ?? "");
const honorarios = useHonorarios({ processoId: id }); // take first/only result
// useHonorario(honorarioId) is available for a single-record re-fetch if needed, but
// useHonorarios({ processoId }) already returns the full Honorario[] — index [0] suffices
// per the Phase 82 invariant (one Honorario per processo).
```

---

## Shared Patterns

### Dialog "Adicionar X" shell (applies to all 6 tabs: Partes, Fases, Decisões, Factos, Testemunhas, Documentos)
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 898-922 (`Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`)
**Apply to:** every sub-section tab body in `processos/[id]/page.tsx`
```typescript
<Dialog open={addXModal} onOpenChange={setAddXModal}>
  <DialogTrigger asChild>
    <Button type="button" variant="outline" size="sm">Adicionar X</Button>
  </DialogTrigger>
  <DialogContent>
    <DialogHeader><DialogTitle>Adicionar X</DialogTitle></DialogHeader>
    {/* form body */}
    <DialogFooter>
      <Button type="button" variant="outline" onClick={() => setAddXModal(false)}>Cancelar</Button>
      <Button type="button" onClick={confirmAddX}>Confirmar</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```
Note: `DialogTrigger` must be added to this file's existing `@/components/ui/dialog` import (currently only imports `Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle` — no `DialogTrigger`, since existing Dialogs in this file are all imperatively opened via `setXDialogOpen(true)` inside a click handler, not `DialogTrigger asChild`. Both patterns coexist fine — the new tabs use `DialogTrigger asChild` per the Cliente-page convention; the existing Justificativa/Prazo dialogs keep their imperative-open style unchanged).

### Destructive delete confirmation + toast
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 1413-1423 (`ClienteDocumentoEntregueRow.onDelete`)
**Apply to:** every row-level delete action (Decisão/Facto/Testemunha/Documento `✕` buttons)
```typescript
const onDelete = async () => {
  const ok = window.confirm("Apagar {artigo} {entidade}?");
  if (!ok) return;
  try {
    await del.mutateAsync(entityId);
    toast.success("{Entidade} apagad{a/o} com sucesso.");
  } catch (e) {
    toast.error(e instanceof Error ? e.message : "Erro ao apagar {entidade}");
  }
};
```

### Error/loading list-state ternary
**Source:** `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 1255-1257 (Partes tab) and `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 1367-1386 (Documentos Entregues tab)
**Apply to:** every new tab's list rendering
```typescript
{query.isLoading ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
) : query.isError ? (
  <p className="text-sm text-red-600">{"{List load error copy from UI-SPEC}"}</p>
) : !query.data?.length ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">{"{Empty-state copy from UI-SPEC}"}</p>
) : (
  /* table/list */
)}
```

### `rounded-none` everywhere
**Source:** established codebase-wide convention (see UI-SPEC Design System section), consistently applied on `Card`, `Button`, `Input`, `Dialog`, `select`, `Badge` across both analog files.
**Apply to:** every new element in this phase — no exceptions.

### Badge variants (reuse only, do not invent new ones)
**Source:** `web/src/components/ui/badge.tsx` (`cva` definition) — variants confirmed in use: `green`/`amber`/`purple`/`gray`/`secondary`/`blue`/`red`, always paired with `className="rounded-none font-bold tracking-wide"` (see `processos/[id]/page.tsx` line 480-482, 507-513, 587-592 for usage examples).

### `apiFetch` + TanStack Query hook shape (list/add/update/delete quadruplet)
**Source:** `web/src/hooks/use-processos.ts` lines 364-551 (Decisão/Testemunha/Facto hooks, already built Phase 83 — DO NOT reimplement, only consume)
```typescript
export function useDecisoes(id: string) { /* useQuery, queryKey ["processos","decisoes",id] */ }
export function useAddDecisao(id: string) { /* useMutation, FormData multipart, invalidates ["processos","decisoes",id] + ["documentos","list"] if documentoId returned */ }
export function useUpdateDecisao(id: string) { /* useMutation, PUT JSON, setQueryData map-replace */ }
export function useDeleteDecisao(id: string) { /* useMutation, DELETE, invalidates both decisoes + documentos lists */ }
// identical quadruplet shape for useTestemunhas/useAddTestemunha/useUpdateTestemunha/useDeleteTestemunha
// and useFactos/useAddFacto/useUpdateFacto/useDeleteFacto
```

## No Analog Found

None — all 3 files (and every sub-surface within the modified `processos/[id]/page.tsx`) have a strong, specific analog identified above.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`, `web/src/app/(dashboard)/processos/[id]/page.tsx` (full, self), `web/src/app/(dashboard)/processos/novo/page.tsx` (full, self), `web/src/hooks/use-processos.ts`, `web/src/hooks/use-documentos.ts`, `web/src/hooks/use-financeiro.ts`, `web/src/hooks/use-clientes.ts`, `web/src/schemas/processos.ts`, `web/src/types/processos.ts`, `web/src/types/financeiro.ts`, `web/src/lib/origem-processo.ts`, `web/src/lib/tipo-decisao.ts`, `web/src/lib/tipo-testemunha.ts`, `web/src/components/ui/badge.tsx`, `web/src/app/(dashboard)/financeiro/[id]/page.tsx` (route existence check only).
**Files scanned:** 16
**Pattern extraction date:** 2026-07-07

## Flagged Risks for the Planner (found during pattern mapping, not asked for but load-bearing)

1. **Schema mismatch on `processos/novo/page.tsx`:** the intake form currently binds to `processoFormSchema` (no `origem` field), not `processoIntakeFormSchema` (has it). The plan must explicitly include switching the resolver/type, or the Origem field will have no Zod validation wired.
2. **`FactoUpdateRequest.ordem` docblock contradiction:** `web/src/types/processos.ts` lines 196-198 has a comment stating "no form collects this field yet ... reusing a stale value would silently corrupt ordering" — Phase 84 explicitly reverses that assumption by adding an editable `ordem` field to the "Editar Facto" Dialog per CONTEXT.md/UI-SPEC. The plan should update or remove that stale comment when wiring the edit form, and ensure the field is sourced from user input (not silently re-read from the current record) once the form is added.
3. **Processo-Documentos wire-shape (WR-01 workaround) may not transfer as-is:** `ClienteDocumentoEntregueRow` (`clientes/[id]/page.tsx` lines 1404-1411) reads `tamanho`/`createdAt` off the raw entity as a documented workaround for a serialization quirk specific to the cliente-documentos endpoint. Verify the processo-documentos response shape independently rather than assuming byte-for-byte parity.
4. **No shadcn `Table` component in this file** — `processos/[id]/page.tsx` uses raw `<table>` throughout (Partes/Fases); the Cliente page uses shadcn `Table`/`TableRow`/etc. in `ClienteProcessosTab`/`ClienteParecerTab`. For the new Decisões/Factos/Testemunhas tables, stay consistent with the file being edited (raw `<table>`), not the analog file's `Table` component.
