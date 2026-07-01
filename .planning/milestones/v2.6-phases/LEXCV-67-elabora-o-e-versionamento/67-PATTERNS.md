# Phase 67: Elaboração e Versionamento - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 3 (all edits to existing files)
**Analogs found:** 3 / 3

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|----------------|
| `web/src/schemas/pareceres.ts` (add `parecerVersaoCreateFormSchema`) | schema/validation | request-response | `web/src/schemas/documentos.ts` (`documentoUploadFormSchema`) | exact |
| `web/src/hooks/use-pareceres.ts` (add `useCreateParecerVersao`) | hook (mutation) | file-I/O (multipart upload w/ progress) | `web/src/hooks/use-documentos.ts#useUploadDocumentoComProgresso` | exact |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (add "Nova Versão" form section) | component (page section) | file-I/O / request-response | `web/src/app/(dashboard)/documentos/novo/page.tsx` | exact |

## Pattern Assignments

### `web/src/schemas/pareceres.ts` (schema, request-response)

**Analog:** `web/src/schemas/documentos.ts`

**Existing file conventions** (`web/src/schemas/pareceres.ts` lines 1-11):
```typescript
import { z } from "zod";

export const parecerStatusSchema = z.enum(["PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO"]);
export const parecerPrioridadeSchema = z.enum(["ALTA", "MEDIA", "BAIXA"]);

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();
```

**Required-file pattern to copy** (`web/src/schemas/documentos.ts` lines 16-21) — note this phase's file is stricter (required, not just "exactly one"), reuse the same `FileList` custom-type technique:
```typescript
const fileListSchema = z.custom<FileList>((value) => value instanceof FileList, {
  message: "O ficheiro é obrigatório",
});

export const documentoUploadFormSchema = z.object({
  file: fileListSchema.refine((files) => files.length === 1, "O ficheiro é obrigatório"),
  ...
```

**Character-count validation pattern to copy** (`web/src/schemas/pareceres.ts` lines 16-33, existing `descricao` field in `parecerCreateFormSchema` — same 10-char threshold applies to `conteudo` per UI-SPEC):
```typescript
descricao: z
  .string()
  .trim()
  .superRefine((val, ctx) => {
    if (val.length === 0) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "Descreva o pedido de parecer." });
      return;
    }
    if (val.length < 10) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "A descrição deve ter pelo menos 10 caracteres." });
    }
  }),
```

**New schema to add** — combine both patterns above (field name `conteudo`, message "O resumo deve ter pelo menos 10 caracteres." per UI-SPEC copy, plus a required `file: FileList`):
```typescript
export const parecerVersaoCreateFormSchema = z.object({
  conteudo: z
    .string()
    .trim()
    .superRefine((val, ctx) => {
      if (val.length === 0 || val.length < 10) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "O resumo deve ter pelo menos 10 caracteres.",
        });
      }
    }),
  file: fileListSchema.refine((files) => files.length === 1, "É necessário anexar um ficheiro para submeter esta versão."),
});

export type ParecerVersaoCreateFormValues = z.infer<typeof parecerVersaoCreateFormSchema>;
```
Note: the `fileListSchema` helper is private to `documentos.ts`; redeclare an equivalent local const in `pareceres.ts` (do not cross-import between schema files — no existing precedent for that in this codebase).

---

### `web/src/hooks/use-pareceres.ts` (hook, file-I/O / XHR upload)

**Analog:** `web/src/hooks/use-documentos.ts#useUploadDocumentoComProgresso` (lines 96-141)

**Imports already present in target file** (`use-pareceres.ts` lines 1-5) — must add `API_BASE`:
```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { ParecerPrioridade, ParecerSolicitacao, ParecerVersao } from "@/types/pareceres";
```
Add `API_BASE` to the `@/lib/api` import (matches `use-documentos.ts` line 3: `import { apiFetch, API_BASE } from "@/lib/api";`).

**XHR + FormData + progress core pattern to replicate** (`use-documentos.ts` lines 96-141, do not import — port the shape for the new endpoint/fields):
```typescript
export function useUploadDocumentoComProgresso(options?: { onProgress?: (pct: number) => void }) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DocumentoUploadPayload) =>
      new Promise<DocumentoUploadResponse>((resolve, reject) => {
        const form = new FormData();
        form.set("file", payload.file);
        if (payload.nome?.trim()) form.set("nome", payload.nome.trim());
        // ... other optional fields set conditionally

        const xhr = new XMLHttpRequest();
        xhr.open("POST", `${API_BASE}/documentos/upload`);
        xhr.withCredentials = true;

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            options?.onProgress?.(Math.round((e.loaded / e.total) * 100));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              resolve(JSON.parse(xhr.responseText) as DocumentoUploadResponse);
            } catch {
              reject(new Error("Resposta inválida do servidor"));
            }
          } else {
            reject(new Error(`API ${xhr.status}`));
          }
        };

        xhr.onerror = () => reject(new Error("Erro de rede ao enviar ficheiro"));

        xhr.send(form);
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["documentos", "list"] });
    },
  });
}
```

**New hook to add** — same shape, different endpoint (`/pareceres/solicitacoes/{solicitacaoId}/versoes`), fields (`conteudo`, `file`), response type (`ParecerVersao`), and cascading invalidation per CONTEXT.md decision (3 query-key namespaces, not 1):
```typescript
export type ParecerVersaoCreatePayload = {
  conteudo: string;
  file: File;
};

export function useCreateParecerVersao(
  solicitacaoId: string,
  options?: { onProgress?: (pct: number) => void },
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ParecerVersaoCreatePayload) =>
      new Promise<ParecerVersao>((resolve, reject) => {
        const form = new FormData();
        form.set("conteudo", payload.conteudo);
        form.set("file", payload.file);

        const xhr = new XMLHttpRequest();
        xhr.open(
          "POST",
          `${API_BASE}/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes`,
        );
        xhr.withCredentials = true;

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            options?.onProgress?.(Math.round((e.loaded / e.total) * 100));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              resolve(JSON.parse(xhr.responseText) as ParecerVersao);
            } catch {
              reject(new Error("Resposta inválida do servidor"));
            }
          } else {
            reject(new Error(`API ${xhr.status}`));
          }
        };

        xhr.onerror = () => reject(new Error("Erro de rede ao enviar ficheiro"));

        xhr.send(form);
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pareceres", "versoes", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "detail", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
      ]);
    },
  });
}
```

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (component, file-I/O form section)

**Analog:** `web/src/app/(dashboard)/documentos/novo/page.tsx` (full file, 264 lines)

**Imports to add** (documentos/novo/page.tsx lines 1-18, adapted):
```typescript
import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea"; // not Input — conteudo is a textarea per UI-SPEC
import { Label } from "@/components/ui/label";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import { useCreateParecerVersao } from "@/hooks/use-pareceres";
import { toast } from "@/hooks/use-toast";
import { parecerVersaoCreateFormSchema, type ParecerVersaoCreateFormValues } from "@/schemas/pareceres";
```
Note: `useMe()`/`usePermissions()` are already imported/used in the target file for `canView`; reuse the same `permissions` object for the `pareceres:edit` check rather than a second `usePermissions()` call.

**`createFileList` helper to copy verbatim** (documentos/novo/page.tsx lines 20-24):
```typescript
function createFileList(file: File): FileList {
  const dt = new DataTransfer();
  dt.items.add(file);
  return dt.files;
}
```

**Progress state + hook wiring pattern** (documentos/novo/page.tsx lines 36-41):
```typescript
const [serverError, setServerError] = React.useState<string | null>(null);
const [progresso, setProgresso] = React.useState<number | null>(null);
const upload = useUploadDocumentoComProgresso({ onProgress: (pct) => setProgresso(pct) });
```
Port as: `const versaoUpload = useCreateParecerVersao(id, { onProgress: (pct) => setProgresso(pct) });`

**Form + FileDropZone + progress bar markup to copy near-verbatim** (documentos/novo/page.tsx lines 140-187 — the `<FileDropZone>` usage, error text, and progress bar are exact reuse per UI-SPEC's "reuse verbatim" directive):
```typescript
<div className="space-y-2">
  <Label>Ficheiro</Label>
  <FileDropZone
    onFileChange={handleFicheiroSelecionado}
    accept="image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.txt"
    disabled={form.formState.isSubmitting || upload.isPending}
  >
    Arraste um ficheiro para aqui ou clique para selecionar
  </FileDropZone>

  {progresso !== null ? (
    <div className="space-y-1">
      <div className="flex justify-between text-xs text-neutral-500">
        <span>A enviar...</span>
        <span>{progresso}%</span>
      </div>
      <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
        <div
          className="h-2 rounded-full bg-blue-600 transition-all"
          style={{ width: `${progresso}%` }}
        />
      </div>
    </div>
  ) : null}

  {form.formState.errors.file ? (
    <p className="text-sm text-red-600">{form.formState.errors.file.message}</p>
  ) : null}
</div>
```
UI-SPEC deltas from this analog to apply when porting:
- No image/PDF preview block needed (UI-SPEC doesn't call for a preview in this phase — omit `preVisualizacao` state/JSX).
- `accept` string for the parecer file: same formats as UI-SPEC copy "Formatos aceites: PDF, Word, imagens" — reuse the same `accept` value as documentos/novo verbatim.
- Add the helper text `<p>` below the drop zone: "Formatos aceites: PDF, Word, imagens." (new copy, no analog equivalent — documentos/novo has no such helper).

**Submit handler pattern** (documentos/novo/page.tsx lines 77-109) — adapt for the parecer version endpoint, single toast+reset (no navigation, stays on page per CONTEXT.md):
```typescript
const onSubmit = async (values: ParecerVersaoCreateFormValues) => {
  setServerError(null);
  const file = values.file.item(0);
  if (!file) return;

  try {
    await versaoUpload.mutateAsync({ conteudo: values.conteudo, file });
    setProgresso(null);
    form.reset({ conteudo: "", file: undefined as unknown as FileList });
    toast.success("Nova versão submetida com sucesso.");
  } catch (e) {
    setProgresso(null);
    const msg = e instanceof Error ? e.message : "Não foi possível submeter a versão. Verifique a ligação e tente novamente.";
    setServerError(msg);
    toast.error(msg);
  }
};
```

**Submit button pattern** (documentos/novo/page.tsx lines 248-258, label text swapped per UI-SPEC copy contract):
```typescript
<Button
  type="submit"
  disabled={form.formState.isSubmitting || versaoUpload.isPending}
>
  {form.formState.isSubmitting || versaoUpload.isPending ? "A submeter..." : "Submeter Versão"}
</Button>
```

**CardTitle typography fix (mandatory per UI-SPEC, lines 52, 58)** — the existing detail page's `CardTitle` usages already use `className="font-bold"` only (page.tsx lines 129, 162); UI-SPEC requires `text-lg font-bold` explicitly on the NEW "Nova Versão" CardTitle (and ideally corrected on existing ones, though that's out of this phase's strict scope):
```typescript
<CardTitle className="text-lg font-bold">Nova Versão</CardTitle>
```

**RBAC gate pattern to copy** — matches `usePermissions().can.edit(scope)` convention already established (`use-permissions.ts` lines 11-21) combined with the instance-level `advogadoId === me.id || roles.includes("ADMIN")` check (no existing analog for the instance-check part in the codebase; derive from `useMe()` + `parecer.data`):
```typescript
const permissions = usePermissions(); // already present in ParecerDetailPage for canView
const me = permissions.data; // MeResponse | undefined, from useMe() re-exported by usePermissions()
const canEditPareceres = permissions.can.edit("pareceres");
const isResponsavelOuAdmin =
  me?.roles.includes("ADMIN") || (parecer.data?.advogadoId && parecer.data.advogadoId === me?.id);
const showNovaVersaoForm =
  canEditPareceres && isResponsavelOuAdmin && parecer.data?.status !== "CONCLUIDO";
```
Note: `usePermissions()` spreads `...me` (see `use-permissions.ts` line 25), so `permissions.data` is the same as `useMe().data` — no second network call needed if `ParecerDetailContent` lifts a single `usePermissions()` call and passes down, matching existing pattern of calling `usePermissions()` once at the top of `ParecerDetailPage`.

---

## Shared Patterns

### Multipart XHR upload with progress
**Source:** `web/src/hooks/use-documentos.ts#useUploadDocumentoComProgresso` (lines 96-141)
**Apply to:** `useCreateParecerVersao` in `use-pareceres.ts`
Key elements: `new Promise<T>()` wrapper around `XMLHttpRequest`, `FormData` built from payload fields, `xhr.withCredentials = true` (cookie-based auth, no bearer token per CLAUDE.md), `xhr.upload.onprogress` for percent callback, `xhr.onload`/`xhr.onerror` for resolve/reject, `API_BASE` from `@/lib/api` for the URL (not `apiFetch`, since progress events require raw XHR).

### FileDropZone component
**Source:** `web/src/components/shared/file-drop-zone.tsx` (90 lines, full file)
**Apply to:** Nova Versão form's file field
Reuse verbatim — no modification needed, it's a generic, domain-agnostic drag/drop + click-to-browse input already used by Documentos.

### Query invalidation cascade
**Source:** `web/src/hooks/use-pareceres.ts#useCreateParecer` (lines 71-84) shows the single-namespace pattern; CONTEXT.md mandates a 3-namespace cascade for `useCreateParecerVersao` (versoes, detail, list) — use `Promise.all` of three `invalidateQueries` calls as shown in the Pattern Assignments section above.

### RBAC gating (`hasScopedPermission`)
**Source:** `web/src/hooks/use-permissions.ts` (lines 7-29), consumed as `permissions.can.edit("pareceres")`
**Apply to:** conditionally rendering/disabling the Nova Versão form section
Existing precedent for the `.can.view()` gate is already in `pareceres/[id]/page.tsx` lines 50-59 (`AccessDeniedState` on view-denial) — the new edit-gate should NOT render `AccessDeniedState` (per CONTEXT.md, the section is silently omitted, not blocked with an error page) — just conditionally omit the Card, matching Phase 66's "no dead buttons" principle referenced in UI-SPEC.

### Toast + inline error dual-channel
**Source:** `web/src/app/(dashboard)/documentos/novo/page.tsx` lines 103-108 (`setServerError` + `toast.error`)
**Apply to:** Nova Versão submit error handling — same dual-channel (inline `<p className="text-sm text-red-600">` banner AND `toast.error`), per UI-SPEC's explicit "dual-channel per Phase 66 pattern" note.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Instance-level RBAC check (`advogadoId === me.id \|\| isAdmin`) in a detail-page form-gate | component logic | request-response | No existing frontend file combines `usePermissions()` scope check with a per-record ownership check; backend does this in `ParecerController.createVersao` but there is no prior frontend port of that specific compound condition — derive directly from `useMe()` + `parecer.data.advogadoId` as shown above (low risk, straightforward boolean composition, no need for a dedicated hook). |

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/documentos/`, `web/src/hooks/`, `web/src/schemas/`, `web/src/components/shared/`, `web/src/app/(dashboard)/pareceres/`
**Files scanned:** 9 (`documentos/novo/page.tsx`, `file-drop-zone.tsx`, `use-documentos.ts`, `use-pareceres.ts`, `schemas/pareceres.ts`, `schemas/documentos.ts`, `pareceres/[id]/page.tsx`, `types/pareceres.ts`, `use-permissions.ts`, `use-me.ts`, `types/auth.ts`)
**Pattern extraction date:** 2026-07-01
