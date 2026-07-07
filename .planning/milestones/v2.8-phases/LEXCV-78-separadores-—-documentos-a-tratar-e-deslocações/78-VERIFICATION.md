---
phase: 78-separadores-documentos-a-tratar-e-deslocacoes
verified: 2026-07-06T00:00:00Z
status: passed
score: 7/7
overrides_applied: 0
---

# Phase 78: Separadores — Documentos a Tratar e Deslocações Verification Report

**Phase Goal:** As listas de documentos a tratar e deslocações do cliente ficam isoladas nos seus próprios separadores, mantendo o comportamento atual
**Verified:** 2026-07-06
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ao selecionar "Documentos a Tratar" em edição, vê a lista real (Adicionar + lista/estado-vazio), não o placeholder | VERIFIED | `page.tsx:959-1012` — `tab === "documentosATratar"` branch renders `<Card><CardContent>` with heading, Dialog("Adicionar Documento a Tratar"), empty-state ("Nenhum documento a tratar registado.") and `documentosATratar.map(...)` list. `PlaceholderEmBreve` call-count dropped from 4→2 (verified via grep), remaining 2 = function def + `documentosEntregues` branch only |
| 2 | Ao selecionar "Deslocações" em edição, vê a lista real (descrição/local/data), não o placeholder | VERIFIED | `page.tsx:1013-1089` — `tab === "deslocacoes"` branch renders Card with heading, Dialog("Adicionar Deslocação") with 3 fields (descrição/local/data inputs, `type="date"`), empty-state and `deslocacoes.map(...)` rendering `{d.descricao}{local}{data}` |
| 3 | O separador "Dados" já não mostra as secções "Documentos a Tratar" nem "Deslocações" | VERIFIED | Searched lines 485-957 (the entire `tab === "dados"` branch) — only "Documentos Entregues" (Phase 79 scope, correctly retained) appears; no "Documentos a Tratar" / "Deslocações" text or JSX remains in the Dados branch |
| 4 | Mudar de separador após abrir "Adicionar Documento a Tratar" fecha e limpa o diálogo | VERIFIED | `page.tsx:216-219` — dialog-reset `useEffect` (dep `[tab]`) includes `if (tab !== "documentosATratar") { setAddDocATratarModal(false); setNewDocATratar({ descricao: "" }); }` |
| 5 | Mudar de separador após abrir "Adicionar Deslocação" fecha e limpa o diálogo | VERIFIED | `page.tsx:220-223` — same effect includes `if (tab !== "deslocacoes") { setAddDeslocacaoModal(false); setNewDeslocacao({ descricao: "", local: "", data: "" }); }` |
| 6 | Ambas as listas continuam a persistir no "Guardar" do cabeçalho, independente do separador ativo | VERIFIED | `onSubmit` (`page.tsx:317-318`) includes `documentosATratar`/`deslocacoes` in the update payload unconditionally; `onCancel` (`page.tsx:341-342`) and the load-effect (`page.tsx:268-269`) read/reset both from `cliente.data` — all three sites live outside the tab-conditional JSX, untouched by the relocation |
| 7 | Em modo leitura, ambas as secções continuam totalmente ocultas (mesmo isEditing-gate), sem nova vista read-only | VERIFIED | Both branches wrap their `<Card>` in `isEditing ? ( ... ) : null` (`page.tsx:960` and `page.tsx:1014`) — in read mode both render `null`, identical to pre-phase-78 behavior confirmed in 78-CONTEXT.md's corrected discrepancy analysis |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | Relocated JSX blocks into own tab branches; extended dialog-reset useEffect | VERIFIED | Confirmed via `git show 7a40ff3` diff (85 insertions / 79 deletions, single file) — pure relocation, no markup/handler/state changes beyond the useEffect split |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `tab === "documentosATratar"` | documentosATratar list JSX + confirmAddDocATratar + addDocATratarModal | conditional tab branch | WIRED | `page.tsx:959` branch renders `addDocATratarModal` Dialog, `newDocATratar` inputs, `confirmAddDocATratar` button handler, `documentosATratar.map` list — grep confirms all present in the same branch |
| `tab === "deslocacoes"` | deslocacoes list JSX + confirmAddDeslocacao + addDeslocacaoModal | conditional tab branch | WIRED | `page.tsx:1013` branch renders `addDeslocacaoModal` Dialog, `newDeslocacao` 3-field inputs, `confirmAddDeslocacao` button handler, `deslocacoes.map` list |
| dialog-reset useEffect | addDocATratarModal / addDeslocacaoModal | per-dialog tab conditions | WIRED | `page.tsx:216-223` — `tab !== "documentosATratar"` and `tab !== "deslocacoes"` conditions present and independent, `[tab]` dependency array unchanged |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `documentosATratar` tab branch | `documentosATratar` (useState) | Loaded from `cliente.data.documentos_a_tratar` on fetch (`page.tsx:268`); backend `Cliente.documentosATratar` (`DocumentoATratar[]`, JSON-converted TEXT column) | Yes — real per-tenant DB-backed data, not static | FLOWING |
| `deslocacoes` tab branch | `deslocacoes` (useState) | Loaded from `cliente.data.deslocacoes` (`page.tsx:269`); backend `Cliente.deslocacoes` field | Yes | FLOWING |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CLI-30 | 78-01-PLAN.md | Separador "Documentos a Tratar" mantém lista de texto atual, isolado no seu próprio separador | SATISFIED | Tab branch renders real list, isolated from "Dados"; field set unchanged (`descricao` only — see note below) |
| CLI-31 | 78-01-PLAN.md | Separador "Deslocações" mantém lista de texto atual (descrição/local/data), isolado no seu próprio separador | SATISFIED | Tab branch renders real list with descrição/local/data, isolated from "Dados" |

No orphaned requirements: REQUIREMENTS.md traceability table maps only CLI-30 and CLI-31 to Phase 78, and both appear in the PLAN's `requirements` frontmatter. Full match.

**Note on ROADMAP.md Success Criterion #1 wording:** ROADMAP.md phase 78 SC1 states the Documentos a Tratar list is "descrição+data" — this is a pre-existing inaccuracy in the roadmap text. Direct inspection of the backend model (`backend/src/main/java/com/lexcv/models/DocumentoATratar.java`) confirms the entity has only a `descricao` field; no `data` field exists in the DB converter, backend model, or frontend type (`web/src/types/clientes.ts`). The phase's CONTEXT.md and PLAN.md both explicitly flag and correct this discrepancy, deliberately declining to add a new `data` field (which would be unrequested scope creep, contradicting the phase's explicit "same fields, zero new data" boundary). The true intent of SC1 — "mantém a lista de texto atual" (preserves the current text list) — is satisfied exactly; only the literal roadmap wording is stale. Treated as satisfied, not a gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None found | — | No TBD/FIXME/XXX/TODO/HACK debt markers in the modified file. `PlaceholderEmBreve` remains only at its function definition and the (intentionally out-of-scope) `documentosEntregues` branch — correct per Phase 79 boundary, not a stub regression |

Independently re-ran verification commands (not just trusting SUMMARY):
- `pnpm exec tsc --noEmit` — zero errors in `clientes/[id]/page.tsx`; only 2 pre-existing unrelated `vitest` module-resolution errors in test files
- `pnpm lint` — zero errors in `clientes/[id]/page.tsx`; the file's own lint output shows only 4 pre-existing React-Compiler/effect warnings at lines 355, 1422, 1567, 1769 — all outside the diff introduced by commit `7a40ff3` (confirmed via `git diff 7a40ff3~1 7a40ff3`) and unrelated to the relocated blocks. All 5 errors / remaining warnings from the full `pnpm lint` run are in other files (`documentos/novo`, `pareceres/nova`, `processos/[id]`, `processos/novo`, `settings/page.tsx`, `dashboard-shell.tsx`) — pre-existing, untouched by this phase
- Code review (78-REVIEW.md): 0 critical, 0 warning, 2 info (stale comment wording IN-01, pre-existing index-based `key` pattern IN-02 — both non-blocking, IN-02 predates this phase)

### Human Verification Required

None. All must-haves are statically verifiable via source inspection, and both `tsc`/`lint` were independently re-run against the actual working tree (not merely quoted from SUMMARY.md).

### Gaps Summary

No gaps. All 7 derived observable truths verified directly against the codebase. Both tab branches render real, wired, data-flowing content in place of the Phase 76 placeholders; the Dados tab no longer contains either section; the dialog-reset useEffect correctly isolates each dialog to its own tab; persistence and read-mode visibility are provably unchanged. CLI-30 and CLI-31 are the only requirement IDs mapped to this phase in REQUIREMENTS.md, and both are satisfied with no orphans.

---

_Verified: 2026-07-06_
_Verifier: Claude (gsd-verifier)_
