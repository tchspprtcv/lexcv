---
phase: LEXCV-105-m-dulos-clientes-processos-combinados
fixed_at: 2026-07-16T19:28:49Z
review_path: .planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase LEXCV-105: Code Review Fix Report

**Fixed at:** 2026-07-16T19:28:49Z
**Source review:** .planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (fix_scope: critical_warning — CR-01, WR-01, WR-02, WR-03; IN-01/02/03 out of scope)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### CR-01: Cancelling an edit after a tipo change silently clears a legacy `documento_tipo` on the next save

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** `d8ac9b7`
**Applied fix:** Extracted the legacy-detection logic (previously inlined in the load `useEffect`) into a new `computeLegacyDocumentoTipo` `useCallback` helper. The load effect now calls `setLegacyDocumentoTipo(computeLegacyDocumentoTipo(cliente.data))` instead of duplicating the inline detection logic, and `onCancel` now calls the same helper right after `form.reset(buildDefaultValues(cliente.data))`, so cancelling a tipo-change confirmation restores the `legacyDocumentoTipo` flag in lockstep with the form values it resets alongside — closing the gap where `onCancel` reset the form but left `legacyDocumentoTipo` stuck at `null`.

### WR-01: `NativeSelect` migration dropped `w-full` on 16 of 23 converted selects

**Files modified:** `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/clientes/novo/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/clientes/merge/page.tsx`, `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** `2cf56f9`
**Applied fix:** Added `className="w-full"` to all 16 cited `NativeSelect` call sites (verified each against the actual file content — line numbers had shifted by +7 in `clientes/[id]/page.tsx` due to the CR-01 fix committed immediately prior, so each site was located by content match rather than blindly trusting the original REVIEW.md line numbers):
- `clientes/page.tsx`: Tipo filter, Estado filter (2 sites)
- `clientes/novo/page.tsx`: Tipo de Documento, Ramo de Atividade (2 sites)
- `clientes/[id]/page.tsx`: Tipo de Documento, Ramo de Atividade, "Adicionar utilizador" select, contacto tipo (add), contacto tipo (edit) (5 sites)
- `clientes/merge/page.tsx`: Cliente principal, Cliente duplicado (2 sites)
- `processos/[id]/page.tsx`: Prioridade, Responsável, fase status, decisão tipo, testemunha tipo, reatribuir responsável (6 sites)

### WR-02: Nested-tab RBAC gates in Cliente ficha are not guarded by `permissions.isFetched`

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** `c8d9e61`
**Applied fix:** Gated the three nested `TabsContent` RBAC checks (Processos, Pareceres, Documentos Entregues) with `!permissions.isFetched` first, showing `<div className="p-6 text-sm text-neutral-500">A carregar...</div>` while permissions are resolving, before falling through to the existing `canViewX ? <RealTab /> : <AccessDeniedState />` branch — matching the pattern already used at the page-level guard and the exact fix suggested in the review.

### WR-03: CSV cliente import blindly casts the `tipo` column to the enum type

**Files modified:** `web/src/app/(dashboard)/clientes/page.tsx`
**Commit:** `3af3d5b`
**Applied fix:** Replaced the force-cast (`(r[idxTipo] ?? "").trim() || undefined) as "PARTICULAR" | "EMPRESA" | undefined`) with explicit validation: the raw CSV value is trimmed/uppercased, checked against the exact `"PARTICULAR" | "EMPRESA"` enum (mirroring `z.enum(["PARTICULAR", "EMPRESA"])` from `schemas/clientes.ts`), and any non-empty value that doesn't match either option now increments `failed`, pushes a specific `linha N: tipo inválido ("VALUE")` message into `failureReasons`, and `continue`s — skipping the `createCliente.mutateAsync` call entirely instead of relying on a generic backend-rejection error.

---

_Fixed: 2026-07-16T19:28:49Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
