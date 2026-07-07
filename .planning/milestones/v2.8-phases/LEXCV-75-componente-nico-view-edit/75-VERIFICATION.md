---
phase: LEXCV-75-componente-nico-view-edit
verified: 2026-07-04T00:00:00Z
status: human_needed
score: 8/8 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Open /clientes/[id] for an existing client, confirm read mode (dl/dd grid) renders identically to the pre-phase detail page (visual/pixel parity)"
    expected: "Read mode looks the same as before the merge; no layout regression"
    why_human: "Visual/pixel-level comparison cannot be verified by static grep/tsc analysis"
  - test: "Click Editar, confirm the same page becomes an editable form with no URL/navigation change, then click Guardar and confirm a success toast appears and the page returns to read mode with the saved values reflected"
    expected: "Page toggles to edit mode in place; save round-trips through the real PUT /clientes/{id} endpoint and returns to read mode with updated data, no navigation"
    why_human: "Requires a running backend + browser session to exercise the live save round-trip; static analysis confirms the wiring (mutateAsync, cache update) but not runtime behavior"
  - test: "Click Editar, make a change, click Cancelar, confirm the edit is discarded and no window.confirm prompt appears"
    expected: "Form resets to original values and view returns to read mode without navigation or a confirmation dialog"
    why_human: "Runtime interaction behavior, not statically verifiable"
  - test: "As a user with clientes:edit, toggle into edit mode and confirm the Adicionar/Editar/Remover affordances appear on Contactos, Notas, Advogados, Administrativos, and Procuração cards; toggle back to view mode and confirm they disappear; confirm Ver/Download on Procuração stays visible in both modes"
    expected: "All CRUD affordances hidden in view mode, visible in edit mode; Ver/Download always visible"
    why_human: "Requires a logged-in session with clientes:edit permission and live toggling; deferred explicitly by 75-02-PLAN.md's own verification section"
  - test: "From the clientes list page, click the pencil icon (mobile card and desktop row) and confirm it lands on /clientes/[id] in read mode, with the in-page Editar toggle available"
    expected: "Both pencil affordances navigate to the unified detail page, not a 404 or the deleted /editar route"
    why_human: "End-to-end navigation click-through, deferred explicitly by 75-03-PLAN.md's own verification section"
---

# Phase 75: Componente Único View/Edit Verification Report

**Phase Goal:** A ficha de cliente é uma única página que alterna entre modo leitura e edição, sem rota dedicada de edição
**Verified:** 2026-07-04T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User opens `/clientes/[id]` and sees client data in read mode (dl/dd grid) by default | VERIFIED | `web/src/app/(dashboard)/clientes/[id]/page.tsx:111` — `isEditing` initialized `false`; lines 384-521, 551-658 render `<dl>/<dd>` grids when `!isEditing`. |
| 2 | User clicks Editar and the same fields become editable inputs on the same page without navigation | VERIFIED | Lines 358-362: `Editar` button calls `setIsEditing(true)` (no `router.push`/`Link` navigation). No `useRouter` import anywhere in the file (grep confirms zero matches). Edit-mode fields (Input/Controller/RadioGroup/Switch) render at lines 384-478, 551-637 when `isEditing`. |
| 3 | User clicks Guardar; data saves via `useUpdateCliente`, a success toast shows, and the page returns to read mode without navigating | VERIFIED | Line 109: `const update = useUpdateCliente(id)`. `onSubmit` (lines 254-296): `await update.mutateAsync(payload)` → `toast.success(...)` → `setIsEditing(false)`. `useUpdateCliente` (`web/src/hooks/use-clientes.ts:88-104`) performs a real `PUT /clientes/{id}` via `apiFetch` and updates the TanStack Query cache — not a stub. |
| 4 | User clicks Cancelar; unsaved edits are discarded and the page returns to read mode without navigating | VERIFIED | `onCancel` (lines 298-314): `form.reset(buildDefaultValues(cliente.data))`, resets the 3 staged intake lists, clears `pendingTipo`/`serverError`, `setIsEditing(false)`. No `window.confirm`, matching CONTEXT.md's explicit decision. |
| 5 | The route `/clientes/[id]/editar` no longer resolves (file deleted) | VERIFIED | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` does not exist (directory listing confirms only `page.tsx` and `ficha/` remain under `clientes/[id]/`). Commit `fcf5dc8` deletes the 740-line file with no redirect shim. |
| 6 | In view mode, the Adicionar/Editar/Remover affordances on Contactos, Notas, Advogados, Administrativos, and Procuração are hidden | VERIFIED | All four inline components (`ClienteContactosCard` L1231, `ClienteNotasCard` L1433, `ResponsaveisCard` L1088, `ProcuracaoCard` L980) gate every CRUD affordance on `canEditClientes && editable` (grep: `canEditClientes && editable` appears 8 times across the file). No bare `canEditClientes ?` guard remains around an edit affordance. |
| 7 | In edit mode (and only with `clientes:edit` permission), those same affordances are visible and functional | VERIFIED | 5 call sites in `ClienteDetailContent` (lines 911-953) pass `editable={isEditing}` alongside `canEditClientes={canEditClientes}` — Contactos, Notas, Advogados, Administrativos, Procuração (grep count: 5 occurrences of `editable={isEditing}`). |
| 8 | The Ver/Download button on Procuração remains visible in both modes | VERIFIED | `ProcuracaoCard` line 1046-1048: the "Ver / Download" `Button` is rendered unconditionally inside the `hasProcuracao` branch, outside any `editable` guard. |
| 9 | The Editar pencil icon in the clientes list (mobile card and desktop row) links to `/clientes/[id]`, not the removed `/editar` route | VERIFIED | `web/src/app/(dashboard)/clientes/page.tsx` lines 463-465 (mobile) and 610-611 (desktop): both `Pencil` icon `Link` hrefs are `` `/clientes/${encodeURIComponent(...)}` `` with no `/editar` suffix. Grep across `web/src` confirms zero remaining references to `[id]/editar` as a route/import path (the only other `/editar` hits are unrelated `processos`/`agenda` routes, out of this phase's scope). |

**Score:** 9/9 truths verified (roadmap's 4 Success Criteria plus 5 additional plan-level must-haves — all pass)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | Unified view/edit client detail page driven by local `isEditing` boolean | VERIFIED | Contains `setIsEditing`, `useUpdateCliente`, `buildClienteFormSchema`, `update.mutateAsync` (all present, grep-confirmed). 1634 lines, substantive implementation, not a stub. |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | Deleted — must not exist | VERIFIED | File and its parent directory confirmed absent from the filesystem. |
| `web/src/app/(dashboard)/clientes/page.tsx` | List page whose Editar pencil links point to the unified detail page | VERIFIED | Both pencil link hrefs repointed; `Pencil` icon and `canEditClientes` guards preserved unchanged. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `[id]/page.tsx` | `useUpdateCliente` | `form.handleSubmit(onSubmit) -> update.mutateAsync(payload)` | WIRED | Line 354: `onClick={form.handleSubmit(onSubmit)}`; `onSubmit` calls `await update.mutateAsync(payload)` (line 282). |
| `[id]/page.tsx` | `buildClienteFormSchema` | `useMemo + zodResolver` | WIRED | Lines 117-123: `React.useMemo(() => buildClienteFormSchema(...), [legacyDocumentoTipo])` passed to `zodResolver` in `useForm`. |
| `ClienteDetailContent` | `ClienteContactosCard`/`ClienteNotasCard`/`ResponsaveisCard`/`ProcuracaoCard` | `editable={isEditing}` prop at each call site | WIRED | 5 call sites (lines 911-953) all pass `editable={isEditing}`. |
| `web/src/app/(dashboard)/clientes/page.tsx` | `/clientes/[id]` | `Link href` on the two Editar pencil affordances | WIRED | Both pencil links use `` `/clientes/${encodeURIComponent(...)}` `` pattern, matching the three pre-existing `Eye` view links in the same file. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `[id]/page.tsx` save flow | `payload` (ClienteUpdateRequest) built from form values | `useUpdateCliente(id).mutateAsync(payload)` → `apiFetch(PUT /clientes/{id})` | Yes — real HTTP mutation, `onSuccess` invalidates list query and sets detail-query cache with the server's response | FLOWING |
| `[id]/page.tsx` read mode | `cliente.data` | `useCliente(id)` (TanStack Query, backed by real GET) | Yes — same query key updated by the mutation's `onSuccess`, so read mode reflects the just-saved data without a manual refetch | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| No stray `router.push`/`useRouter` reintroduced | `grep -n "router\.push\|useRouter" "[id]/page.tsx"` | No matches | PASS |
| `tsc --noEmit` compiles the phase's modified files cleanly | `pnpm exec tsc --noEmit` | Only 2 pre-existing, unrelated `vitest` module errors in `*.test.ts` files (not touched by this phase); zero errors referencing `clientes/[id]/page.tsx` or `clientes/page.tsx` | PASS |
| `/editar` route file physically absent | `find "web/src/app/(dashboard)/clientes" -iname editar` | No results | PASS |
| Claimed commits exist and match described diffs | `git show --stat 438a2a5`, `fcf5dc8` | Both commits present in history with matching file changes (+732/-52 merge; -740 deletion) | PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` conventions or PLAN/SUMMARY-declared probes found for this phase; verification relies on tsc, grep, and commit-history checks instead.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CLI-12 | 75-01 | Utilizador visualiza e edita os dados do cliente numa única página, alternando entre modo leitura e edição via botão "Editar" | SATISFIED | `isEditing` toggle drives read/edit rendering on a single page; no navigation on toggle (verified above). |
| CLI-13 | 75-01, 75-02 | Em modo visualização, controlos de edição ficam inativos/ocultos; em modo edição tornam-se ativos | SATISFIED | Main form fields (75-01) and all 4 sub-component CRUD affordances (75-02) AND-gated on `canEditClientes && editable`; Ver/Download exception confirmed. |
| CLI-14 | 75-01, 75-03 | Rota `/clientes/[id]/editar` é removida em favor do componente único em `/clientes/[id]` | SATISFIED | Route file deleted (75-01); both list-page pencil links repointed (75-03); zero remaining internal references to the deleted route. |

**Note:** `.planning/REQUIREMENTS.md` still shows CLI-12/13/14 as unchecked `[ ]` and "Pending" in the Traceability table (lines 12-14, 65-67), despite ROADMAP.md marking all 3 plans `[x]` complete for Phase 75. This is a documentation-sync gap in tracking metadata, not a code gap — all three requirements are independently verified as implemented above. Recommend updating REQUIREMENTS.md's checkboxes/status during milestone bookkeeping.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `web/src/schemas/clientes.ts` | 27 | Stale docblock reference to deleted `editar/page.tsx` (`ver banner em editar/page.tsx`) | INFO | Carried forward from 75-REVIEW.md as IN-01, non-blocking. Comment should reference `clientes/[id]/page.tsx` instead. |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | 760, 814, 884 | Array-index React `key` on intake list items | INFO | Carried forward as IN-02, non-blocking — benign today since rows have no internal input state. |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | 189-202, 215, 641-652 | Duplicated snake/camel-case fallback logic for documento_tipo/numero/ramo_atividade/detalhes_adicionais | INFO | Carried forward as IN-03, non-blocking. |
| `web/src/app/(dashboard)/clientes/page.tsx` | 421, 526-532 | Duplicated initials-computation logic | INFO | Carried forward as IN-04, non-blocking. |

No BLOCKER or WARNING-level anti-patterns found. No debt markers (`TBD`/`FIXME`/`XXX`) or unaddressed `TODO`/`HACK`/`PLACEHOLDER` strings in the modified files. 75-REVIEW.md independently confirms 0 critical/0 warning findings, with all 5 prior WARNING-level findings from an earlier review round fixed and verified in dedicated commits (`c473d49`, `23c34f8`, `22e619a`, `cc3ae97`).

### Human Verification Required

This phase is marked `UI hint: yes` in ROADMAP.md, and all three plans (75-01, 75-02, 75-03) explicitly deferred their "Manual/visual" verification sections to `human_verify` — no live UAT against a running backend was performed in the execution environment (no `backend/.env`/`web/.env.local` configured in the worktrees used). All static/structural checks pass, but the following require a human with a running app and appropriate permissions:

### 1. Read-mode visual parity

**Test:** Open `/clientes/[id]` for an existing client and compare the read-mode layout against the pre-phase detail page.
**Expected:** No visual/layout regression — the `dl`/`dd` grid renders identically to before the merge.
**Why human:** Pixel-level visual comparison isn't verifiable via static analysis.

### 2. Save round-trip

**Test:** Click Editar, change a field, click Guardar.
**Expected:** Success toast appears, the page returns to read mode showing the updated value, with no page navigation.
**Why human:** Requires a live backend and browser session to exercise the real PUT request and observe UI state transitions.

### 3. Cancel discards edits

**Test:** Click Editar, change a field, click Cancelar.
**Expected:** Edits are discarded, page returns to read mode, no `window.confirm` dialog appears.
**Why human:** Runtime interaction behavior.

### 4. Sub-component affordance toggling

**Test:** As a `clientes:edit` user, toggle into edit mode and observe Contactos/Notas/Advogados/Administrativos/Procuração cards; toggle back to view mode.
**Expected:** Add/Edit/Remover controls appear only in edit mode; Ver/Download on Procuração stays visible in both modes.
**Why human:** Explicitly deferred by 75-02-PLAN.md; requires a live permissioned session.

### 5. List-page pencil navigation

**Test:** From `/clientes`, click the pencil icon (mobile card and desktop row).
**Expected:** Lands on `/clientes/[id]` in read mode, with the in-page Editar toggle available (not a 404).
**Why human:** Explicitly deferred by 75-03-PLAN.md; end-to-end click-through.

### Gaps Summary

No code-level gaps found. All 9 observable truths (the roadmap's 4 Success Criteria plus 5 plan-level must-haves) are verified against the actual codebase: the merge is real and substantive (not a stub — `useUpdateCliente` performs a genuine `PUT` with cache synchronization), the `/editar` route is physically deleted with no redirect shim, all four sub-components are AND-gated correctly, and the list-page links are repointed. `tsc --noEmit` is clean for all phase-modified files, and the independent code review (75-REVIEW.md) found 0 critical/0 warning issues, with all previously-flagged warnings fixed and verified in dedicated commits.

The only reason this phase is not marked `passed` is that live/manual verification of the toggle and save/cancel round-trip against a running backend — appropriately deferred by all three plans to `human_verify` — has not yet been performed. This is a process gate, not a discovered code defect.

---

_Verified: 2026-07-04T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
