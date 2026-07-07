# Phase 79: Documentos Entregues — Upload Real - Pattern Map

**Mapped:** 2026-07-06
**Files analyzed:** 3 (1 backend, 1 hook, 1 page — page.tsx involves both a deletion and an addition)
**Analogs found:** 3 / 3

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (new method `listClienteDocumentos`) | controller (REST endpoint) | CRUD (read, tenant-scoped list) | `listProcessoDocumentos` (same file, lines 2063–2071) | exact |
| `web/src/hooks/use-documentos.ts` (`useDocumentos` queryFn) | hook (TanStack Query) | request-response (list fetch, filter-branch routing) | `useDocumentos` itself, current impl (same file, lines 20–32) — needs internal branching, no external analog needed | exact (self-fix, precedent = Phase 77 lazy-mount, NOT a hook `enabled` param) |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` — new tab content for `tab === "documentosEntregues"` | component (page sub-section / new sub-component) | CRUD (upload=create, list=read, delete) + file-I/O | Combination of 3 analogs: Phase 78 Dialog shape (`documentosATratar` block, lines 959–1012), `documentos/novo/page.tsx` upload mechanics (lines 129–269), `documentos/page.tsx` `DocumentoMobileCard` download/delete mechanics (lines 195–275) | exact (composite) |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` — deletion of old `tab === "dados"` block | component (deletion target) | n/a (removal) | itself — see exact line ranges below | exact |

---

## Pattern Assignments

### 1. Backend: `GET /clientes/{id}/documentos` (new endpoint)

**File to modify:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`

**Analog:** `listProcessoDocumentos` — **exact current lines 2063–2071**:

```java
@PreAuthorize("hasAuthority('documentos:view')")
@GetMapping("/processos/{id}/documentos")
public ResponseEntity<?> listProcessoDocumentos(@PathVariable UUID id) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    return ResponseEntity.ok(documentoRepository.findByTenantIdAndProcessoId(getTenantId(), id));
}
```

Insert the new method immediately after this block (i.e. after line 2071, before the `downloadDocumento` method at line 2073), swapping `Processo`/`processoRepository` for `Cliente`/`clienteRepository`, and `findByTenantIdAndProcessoId` for `findByTenantIdAndClienteId` (already exists — see below). The "cliente not found" 404 wording should match the sibling cliente sub-resource endpoint style at lines 575–587 (`listClienteContactos`) rather than reusing "Processo não encontrado":

```java
@PreAuthorize("hasAuthority('documentos:view')")
@GetMapping("/clientes/{id}/documentos")
public ResponseEntity<?> listClienteDocumentos(@PathVariable UUID id) {
    Cliente cliente = clienteRepository.findById(id).orElse(null);
    if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
    }
    return ResponseEntity.ok(documentoRepository.findByTenantIdAndClienteId(getTenantId(), id));
}
```

**Repository method already exists — no repository change needed.** `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java` line 11:
```java
List<Documento> findByTenantIdAndClienteId(UUID tenantId, UUID clienteId);
```
(line 10, for reference, is the sibling method already used by the processos analog: `List<Documento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);`)

**Auth pattern:** identical `@PreAuthorize("hasAuthority('documentos:view')")` annotation — copy verbatim, do NOT use `clientes:view` (per CONTEXT.md's explicit RBAC decision to keep `documentos:*` scopes for this endpoint).

**Error handling pattern:** manual `null`/tenant-mismatch check → `404 NOT_FOUND` with `Map.of("message", ...)` body — no try/catch needed (no I/O in this method, unlike the upload/download endpoints which wrap `StorageUnavailableException`/`IOException`).

**Placement note:** `clienteRepository` and `documentoRepository` are both already injected as fields on `ResourceController` (used throughout the file, e.g. lines 577, 592, 2060, 2070) — no new dependency wiring required.

---

### 2. Frontend hook: `web/src/hooks/use-documentos.ts` — `useDocumentos`

**Current exact code (lines 1–32, full relevant block):**
```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, API_BASE } from "@/lib/api";

import type {
  Documento,
  DocumentoUploadPayload,
  DocumentoUploadResponse,
  DocumentosListFilters,
} from "@/types/documentos";

function buildDocumentosSearch(filters: DocumentosListFilters) {
  const sp = new URLSearchParams();
  if (filters.processo_id?.trim()) sp.set("processo_id", filters.processo_id.trim());
  if (filters.cliente_id?.trim()) sp.set("cliente_id", filters.cliente_id.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useDocumentos(filters: DocumentosListFilters) {
  const enabled = typeof window !== "undefined" ;
  const processoId = filters.processo_id?.trim() ?? "";
  const clienteId = filters.cliente_id?.trim() ?? "";

  return useQuery({
    queryKey: ["documentos", "list", processoId, clienteId],
    queryFn: () =>
      apiFetch<Documento[]>(`/documentos${buildDocumentosSearch({ processo_id: processoId, cliente_id: clienteId })}`),
    enabled,
    staleTime: 30_000,
  });
}
```

**Confirmed bug (per CONTEXT.md):** `queryFn` always calls `GET /documentos${qs}` — the generic `listDocumentos()` backend method (ResourceController lines 2057–2061) ignores all query params:
```java
@PreAuthorize("hasAuthority('documentos:view')")
@GetMapping("/documentos")
public ResponseEntity<?> listDocumentos() {
    return ResponseEntity.ok(documentoRepository.findByTenantId(getTenantId()));
}
```
So `cliente_id`/`processo_id` filters are silently dropped server-side today.

**Required fix — repoint queryFn to the scoped endpoint when a filter is present**, mirroring the existing branch style already used for `buildDocumentosSearch`. Suggested concrete shape (executor's discretion on exact variable names, but the branching logic must produce this behavior):
```typescript
export function useDocumentos(filters: DocumentosListFilters) {
  const enabled = typeof window !== "undefined";
  const processoId = filters.processo_id?.trim() ?? "";
  const clienteId = filters.cliente_id?.trim() ?? "";

  return useQuery({
    queryKey: ["documentos", "list", processoId, clienteId],
    queryFn: () => {
      if (clienteId) {
        return apiFetch<Documento[]>(`/clientes/${encodeURIComponent(clienteId)}/documentos`);
      }
      if (processoId) {
        return apiFetch<Documento[]>(`/processos/${encodeURIComponent(processoId)}/documentos`);
      }
      return apiFetch<Documento[]>(`/documentos${buildDocumentosSearch({ processo_id: processoId, cliente_id: clienteId })}`);
    },
    enabled,
    staleTime: 30_000,
  });
}
```
(The `/processos/{id}/documentos` branch is not strictly required by this phase's scope — only the cliente branch is — but is included above for symmetry/consistency since both filters share the same gap; CONTEXT.md only mandates fixing the cliente path. If keeping scope minimal, only add the `clienteId` branch and leave the `processoId` path falling through to the generic endpoint as today.)

**No external `enabled` param needed — reuse the Phase 77 precedent verbatim.** Confirmed by reading both `use-processos.ts` (lines 1–40) and `use-pareceres.ts` (`usePareceres`, lines 22–37): **neither hook has an external `enabled` override parameter.** The Phase 77 resolution to the "lazy fetch while tab inactive" problem was NOT a hook-level change — it was mounting the query only inside a lazy sub-component (`ClienteProcessosTab`/`ClienteParecerTab`, defined at lines 1129 and 1219 of `clientes/[id]/page.tsx`) that itself only renders when its tab is selected (see the ternary at lines 945–956). **Apply the identical pattern here:** define a `ClienteDocumentosEntreguesTab({ clienteId }: { clienteId: string })` sub-component that calls `useDocumentos({ cliente_id: clienteId })` internally, and only mount/render it from the `tab === "documentosEntregues"` branch — do not add an `enabled` param to `useDocumentos`.

---

### 3. `web/src/app/(dashboard)/clientes/[id]/page.tsx` — deletions

**A. Imports** (lines 1–60) — no import removal strictly required (Dialog/Input/Label/Button all remain used by Phase 78 sections and by the new tab), but the `DocumentoEntregue` type import (if any, check `ClienteFormValues`/`Cliente` types usage) tied only to the deleted section should be checked — not found as a standalone import in this file (the type is likely inlined/from `@/types/clientes`); no action needed unless the type becomes fully unused elsewhere.

**B. State declarations to remove** — exact current lines:
- Line 187: `const [documentosEntregues, setDocumentosEntregues] = React.useState<DocumentoEntregue[]>([]);`
- Line 191: `const [addDocEntreModal, setAddDocEntreModal] = React.useState(false);`
- Line 192: `const [newDocEntre, setNewDocEntre] = React.useState<{ descricao: string; data: string }>({ descricao: "", data: "" });`

**C. Dialog-reset `useEffect` branch to remove** — exact current lines 211–224, remove just the `documentosEntregues`-related lines inside (keep the `documentosATratar`/`deslocacoes` branches intact):
```typescript
React.useEffect(() => {
    if (tab !== "dados") {
      setAddDocEntreModal(false);          // ← remove this line
      setNewDocEntre({ descricao: "", data: "" });   // ← remove this line
    }
    if (tab !== "documentosATratar") {
      setAddDocATratarModal(false);
      setNewDocATratar({ descricao: "" });
    }
    if (tab !== "deslocacoes") {
      setAddDeslocacaoModal(false);
      setNewDeslocacao({ descricao: "", local: "", data: "" });
    }
  }, [tab]);
```
Note: the outer `if (tab !== "dados") { ... }` block becomes empty after removal — remove the whole `if` block, not just its body, once `documentosEntregues` is its only content.

**D. Load-effect reset to remove** — exact current line 267 (inside the `React.useEffect` at lines 251–270):
```typescript
setDocumentosEntregues(cliente.data.documentos_entregues ?? []);
```

**E. Handler to remove** — exact current lines 272–277:
```typescript
function confirmAddDocEntre() {
    if (!newDocEntre.descricao.trim()) return;
    setDocumentosEntregues((prev) => [...prev, { ...newDocEntre }]);
    setNewDocEntre({ descricao: "", data: "" });
    setAddDocEntreModal(false);
  }
```

**F. `onSubmit` payload line to remove** — exact current line 316 (inside the `payload: ClienteUpdateRequest` object literal, lines 305–319):
```typescript
documentosEntregues: documentosEntregues,
```

**G. `onSubmit` post-success reset lines to remove** — exact current lines 323 and 326 (inside the `onSubmit` try block, lines 293–335):
```typescript
setNewDocEntre({ descricao: "", data: "" });   // line 323 — remove
...
setAddDocEntreModal(false);                     // line 326 — remove
```

**H. `onCancel` reset lines to remove** — exact current lines 340, 344, 347 (inside `onCancel`, lines 337–353):
```typescript
setDocumentosEntregues(cliente.data.documentos_entregues ?? []);  // line 340 — remove
...
setNewDocEntre({ descricao: "", data: "" });                       // line 344 — remove
...
setAddDocEntreModal(false);                                        // line 347 — remove
```

**I. JSX block to remove entirely** — exact current lines 830–892 (the full "Documentos Entregues" `<div className="space-y-2">...</div>` inside the `tab === "dados"` Card, including its own nested `Dialog`):
```typescript
{/* Documentos Entregues */}
<div className="space-y-2">
  <div className="flex items-center justify-between">
    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos Entregues</h4>
    <Dialog open={addDocEntreModal} onOpenChange={setAddDocEntreModal}>
      ...
    </Dialog>
  </div>
  {documentosEntregues.length === 0 ? (
    <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento entregue registado.</p>
  ) : (
    <ul className="space-y-1">
      {documentosEntregues.map((doc, index) => ( ... ))}
    </ul>
  )}
</div>
```
(Lines 894–897, the `serverError` paragraph + closing `</CardContent></Card>`, must remain — only the block above is deleted, not its siblings.)

**J. Placeholder branch to replace** — exact current lines 957–958:
```typescript
) : tab === "documentosEntregues" ? (
  <PlaceholderEmBreve />
```
Replace with a permission-gated branch matching the `processos`/`pareceres` precedent at lines 945–956:
```typescript
) : tab === "documentosEntregues" ? (
  canViewDocumentos ? (
    <ClienteDocumentosEntreguesTab clienteId={id} editable={isEditing} canEditDocumentos={canEditDocumentos} />
  ) : (
    <AccessDeniedState description="Não tem permissão para consultar os documentos deste cliente." />
  )
```
This requires adding `canViewDocumentos = permissions.can.view("documentos")` and `canEditDocumentos = permissions.can.edit("documentos")` alongside the existing `canViewProcessos`/`canViewPareceres` declarations (lines 116–117).

**Line-number caveat:** all line numbers above are exact as of the current file state (pre-edit). Since edits B–I precede edit J in the file and each edit shifts subsequent line numbers, the executor should apply edits top-to-bottom in a single pass (or use anchor-text-based edits rather than pure line numbers) to avoid drift.

---

### 4. `web/src/app/(dashboard)/clientes/[id]/page.tsx` — new tab content

**Analog A — Dialog shape (Phase 78, `documentosATratar` branch):** exact current lines 959–1012. Full pattern (Card wrapper, `h4` optional title, Dialog trigger/content/footer, list/empty-state):
```typescript
) : tab === "documentosATratar" ? (
  isEditing ? (
    <Card>
      <CardContent className="space-y-2 pt-6">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos a Tratar</h4>
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
        </div>
        {documentosATratar.length === 0 ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento a tratar registado.</p>
        ) : (
          <ul className="space-y-1">
            {documentosATratar.map((doc, index) => (
              <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
                <span>{doc.descricao}</span>
                <button type="button" className="text-neutral-500 hover:text-red-600" onClick={...} aria-label="Remover">✕</button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  ) : null
```
**Key divergence for Phase 79:** unlike this analog (whole section hidden when `!isEditing`), the new tab's LIST must render in both read and edit mode per UI-SPEC §3 — only the "Adicionar" trigger and delete controls are gated by `editable && permissions.can.edit("documentos")`. Do not wrap the whole `Card` in `isEditing ? ... : null`.

**Analog B — lazy-mount sub-component shape (`ClienteProcessosTab`), exact current lines 1129–1198:**
```typescript
function ClienteProcessosTab({ clienteId }: { clienteId: string }) {
  const processos = useProcessos({ cliente_id: clienteId });

  return (
    <Card>
      <CardContent className="p-0 bg-white dark:bg-[#020617]">
        {processos.isLoading ? (
          <div className="p-6 text-sm text-slate-500">A carregar...</div>
        ) : processos.isError ? (
          <div className="p-6 text-sm text-red-600">
            Não foi possível carregar os processos deste cliente.
          </div>
        ) : !processos.data?.length ? (
          <div className="p-6 text-sm text-slate-500">Nenhum processo associado a este cliente.</div>
        ) : ( /* Table ... */ )}
      </CardContent>
    </Card>
  );
}
```
For Phase 79, per UI-SPEC the `Card`/`CardContent` idiom to copy is the Phase 78 `space-y-2 pt-6` variant (not the `p-0` Table variant) since this tab combines a trigger row + compact list, not a `Table`. Reuse the sub-component-as-lazy-mount structure (function takes `clienteId`, calls the hook internally, only invoked from the `tab === "documentosEntregues"` ternary branch) — this IS the resolution to the "hook has no external `enabled`" gap; do not modify `useDocumentos`'s signature to add an `enabled` param.

**Analog C — upload mechanics (`documentos/novo/page.tsx`), exact current lines 33–46 (state/hook setup) and 179–196 (progress bar JSX) and 86–118 (`onSubmit`):**
```typescript
const [progresso, setProgresso] = React.useState<number | null>(null);
const upload = useUploadDocumentoComProgresso({ onProgress: (pct) => setProgresso(pct) });
```
```typescript
{progresso !== null ? (
  <div className="space-y-1">
    <div className="flex justify-between text-xs text-neutral-500">
      <span>A enviar...</span>
      <span>{progresso}%</span>
    </div>
    <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
      <div className="h-2 rounded-full bg-blue-600 transition-all" style={{ width: `${progresso}%` }} />
    </div>
  </div>
) : null}
```
`FileDropZone` usage (lines 152–159):
```typescript
<FileDropZone
  onFileChange={handleFicheiroSelecionado}
  onClear={handleFicheiroLimpo}
  accept="image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.txt"
  disabled={form.formState.isSubmitting || upload.isPending}
>
  Arraste um ficheiro para aqui ou clique para selecionar
</FileDropZone>
```
`onSubmit` upload call + toast + error handling (lines 95–117, adapted — Phase 79 stays inside a Dialog, doesn't `router.push`):
```typescript
try {
  const res = await upload.mutateAsync({
    file,
    tipo: values.tipo,
    cliente_id: clienteId,   // fixed to current cliente, per CONTEXT.md — no free Input for cliente_id
  });
  setProgresso(null);
  toast.success("Documento enviado com sucesso.");
  // close Dialog, clear file/tipo fields — no router.push (stays on same page/tab)
} catch (e) {
  setProgresso(null);
  const msg = e instanceof Error ? e.message : "Erro ao fazer upload";
  setServerError(msg);   // or local dialog-scoped error state
  toast.error(msg);
}
```
`FileDropZoneProps` signature (`web/src/components/shared/file-drop-zone.tsx` line 6): `{ onFileChange, onClear, accept, disabled, children }` — unchanged, reused as-is.

**Analog D — download/delete list mechanics (`documentos/page.tsx`, `DocumentoMobileCard`), exact current lines 195–275 (full component) — key excerpts:**

Delete handler (lines 213–225):
```typescript
const del = useDeleteDocumento(id);
const [error, setError] = React.useState<string | null>(null);

const onDelete = async () => {
  setError(null);
  const ok = window.confirm("Apagar este documento?");
  if (!ok) return;
  try {
    await del.mutateAsync();
    toast.success("Documento apagado com sucesso.");
  } catch (e) {
    const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
    setError(msg);
    toast.error(msg);
  }
};
```
Download link (lines 251–259):
```typescript
<a
  href={`/api/v1/documentos/${id}/download`}
  target="_blank"
  rel="noreferrer"
  className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline min-h-[44px] px-2"
>
  Download
</a>
```
Per UI-SPEC, adapt to the Phase 78 `li` container idiom (border/px-3/py-2/text-sm) instead of the mobile-card `div`, and to the "✕" icon-button delete idiom (prescribed default) rather than the labeled `Apagar` outline button shown above — both are valid per UI-SPEC discretion, but "✕" is the closer match to this ficha's convention.

**`Documento` type fields to use for list rendering** (`web/src/types/documentos.ts` lines 1–14): `nome`, `tipo?`, `size` (bytes), `created_at` (ISO string), `id`. Note: field is `size`/`created_at` (snake_case, not `tamanho`/`createdAt`) on the frontend type — despite backend Java using `tamanho`/`createdAt`, the DTO serialization already normalizes to these names (confirmed by `documentos/page.tsx`'s existing usage of `createdAt`/`size` props derived from this type).

**`useDocumentos`/`useUploadDocumentoComProgresso`/`useDeleteDocumento` signatures — unchanged, reused as-is** (see hook file, lines 20–32, 96–141, 70–85 respectively). `DocumentoUploadPayload` shape (types file lines 23–31): `{ file, processo_id?, cliente_id?, tipo?, confidencialidade?, replace_id?, nome? }` — Phase 79 only needs to pass `file`, `tipo`, `cliente_id`.

---

## Shared Patterns

### Authentication / RBAC (backend)
**Source:** `ResourceController.java`, every `documentos:*`-scoped endpoint (lines 2057, 2063, 2073, 2102)
**Apply to:** new `listClienteDocumentos` endpoint
```java
@PreAuthorize("hasAuthority('documentos:view')")
```

### Authentication / RBAC (frontend)
**Source:** `usePermissions()` hook, pattern established at `clientes/[id]/page.tsx` lines 98–100, 116–117
**Apply to:** new tab gating
```typescript
const canViewDocumentos = permissions.can.view("documentos");
const canEditDocumentos = permissions.can.edit("documentos");
```
Per CONTEXT.md, this is deliberately `documentos:*`, NOT `clientes:*` — the one exception among this ficha's "Adicionar" buttons.

### Cliente tenant-scoping 404 pattern (backend)
**Source:** `listClienteContactos` (lines 575–587), `listProcessoDocumentos` (lines 2063–2071)
**Apply to:** new endpoint
```java
Cliente cliente = clienteRepository.findById(id).orElse(null);
if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
}
```

### Lazy-mount hook-gap resolution (frontend)
**Source:** Phase 77 precedent, `ClienteProcessosTab`/`ClienteParecerTab` (lines 1129, 1219) — confirmed no `enabled` param exists on `useProcessos`/`usePareceres`
**Apply to:** `ClienteDocumentosEntreguesTab` — define as its own function component invoked only from the active-tab ternary; do not add an `enabled` override to `useDocumentos`.

### Toast + inline error fallback string convention
**Source:** `documentos/novo/page.tsx` line 114, `documentos/page.tsx` line 221
**Apply to:** upload and delete error handling in the new tab
```typescript
const msg = e instanceof Error ? e.message : "Erro ao fazer upload";       // upload
const msg = e instanceof Error ? e.message : "Erro ao apagar documento";   // delete
```

### List-fetch error fallback string convention (Phase 77 precedent)
**Source:** `ClienteProcessosTab` line 1139, `ClienteParecerTab` (equivalent line for pareceres)
**Apply to:** documentos list error state
```typescript
list.error instanceof Error ? list.error.message : "Não foi possível carregar os documentos entregues deste cliente."
```

---

## No Analog Found

None — all three files/changes have strong, exact-or-composite analogs already in the codebase, as expected for a phase explicitly scoped around "reutilizando toda a infraestrutura já existente."

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java`, `web/src/hooks/use-documentos.ts`, `web/src/hooks/use-processos.ts`, `web/src/hooks/use-pareceres.ts`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/documentos/novo/page.tsx`, `web/src/app/(dashboard)/documentos/page.tsx`, `web/src/components/shared/file-drop-zone.tsx`, `web/src/types/documentos.ts`
**Files scanned:** 10
**Pattern extraction date:** 2026-07-06
