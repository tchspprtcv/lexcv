---
phase: LEXCV-73-detail-page-printable-ficha-update
plan: 01
subsystem: ui
tags: [nextjs, clientes, ficha, display]

# Dependency graph
requires:
  - phase: LEXCV-72
    provides: Dynamic Nome/Morada label convention established in cliente create/edit forms
provides:
  - Dynamic "Morada"/"Sede" label on the client detail page and printable ficha, based on cliente.tipo
  - Removal of the always-blank "Nome Comercial", "Representante Legal", "Cargo", and duplicate "Sede" fields from the printed ficha's Empresa identification block
affects: [clientes-detalhe, clientes-ficha]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Simple ternary label (tipo === \"EMPRESA\" ? \"Sede\" : \"Morada\") on already-fetched data — no form.watch needed since these are read-only display pages, consistent with the form-level dynamic label pattern from Phase 72"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/[id]/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx

key-decisions:
  - "Removed the discontinued Empresa fields (Nome Comercial, Representante Legal, Cargo) entirely from the ficha rather than leaving them as blank placeholders, per CONTEXT.md/REQUIREMENTS.md's explicit out-of-scope decision"
  - "Removed the duplicate always-blank 'Sede' field from the Identificação block since the Contactos section's Morada field already covers it dynamically — no data duplication"

requirements-completed: [CLI-11]

# Metrics
duration: ~15min
completed: 2026-07-02
---

# Phase 73 Plan 01: Dynamic Morada/Sede Labels + Ficha Cleanup Summary

**Client detail page and printable ficha now show a dynamic "Morada"/"Sede" label based on client type, and the ficha no longer displays the discontinued always-blank Empresa fields (Nome Comercial, Representante Legal, Cargo, duplicate Sede).**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-02
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `clientes/[id]/page.tsx`: the `<dt>` label for the address row is now `{cliente.data.tipo === "EMPRESA" ? "Sede" : "Morada"}` instead of the static "Morada" text
- `clientes/[id]/ficha/page.tsx`: removed the `<Field label="Nome Comercial">`, `<Field label="Representante Legal">`, `<Field label="Cargo">`, and duplicate blank `<Field label="Sede">` lines from the `isEmpresa` Identificação block; the Contactos section's Morada `<Field>` now uses `label={isEmpresa ? "Sede" : "Morada"}`

## Task Commits

Each task was committed atomically:

1. **Task 1: Dynamic Morada/Sede label on client detail page** - `2cf34f7` (feat)
2. **Task 2: Remove discontinued Empresa fields and add dynamic Sede/Morada on ficha** - `2152ccd` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Dynamic `<dt>` label for the Morada/Sede row
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` - Removed 4 dead `<Field>` lines from the isEmpresa block; dynamic label on the Contactos Morada field

## Decisions Made
- Followed `73-PATTERNS.md` exactly: reused the existing `isEmpresa` const in `ficha/page.tsx` rather than recomputing it; used a plain ternary (no `form.watch`, since these are read-only display pages, not forms) matching the detail page's existing direct-read style
- Fields explicitly discarded from milestone scope (Representante Legal, Cargo) and now-redundant fields (Nome Comercial — already covered by the unified `nome` field since Phase 71/72; duplicate Sede — already covered by the dynamic Contactos Morada field) are fully removed, not left as blank placeholders

## Deviations from Plan

None — plan executed exactly as written; both files matched `73-PATTERNS.md`'s documented line numbers and content exactly.

## Issues Encountered

The executor subagent for this plan hung after committing both task edits (a known Windows stdio subprocess hang — the process stayed alive with near-zero CPU and never returned its final report). The orchestrating session verified the two commits (`2cf34f7`, `2152ccd`) were already correctly applied and matched the plan's `<action>` instructions exactly, independently re-ran both tasks' automated `<verify>` node scripts (both passed) plus a full `pnpm exec tsc --noEmit` and `pnpm build` (both clean), then stopped the hung subagent and wrote this SUMMARY.md directly rather than re-running the edits (which were already correct and already committed).

## Verification Results

- Task 1 automated gate (`node -e ...` checking dynamic `<dt>` label, absence of static `>Morada</dt>`, unchanged `<dd>` expression) → `OK` — PASS
- Task 2 automated gate (`node -e ...` checking absence of Nome Comercial/Representante Legal/Cargo/duplicate-Sede, presence of dynamic Contactos label, NIF field untouched) → `OK` — PASS
- `grep -n 'Nome Comercial\|Representante Legal\|label="Cargo"' web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` → no matches — PASS
- `pnpm exec tsc --noEmit` → exit 0, no errors — PASS
- `pnpm build` → compiled successfully, all 23 routes generated, no errors — PASS

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- CLI-11 closed. This is the last phase of milestone v2.7 — all requirements (CLI-05 through CLI-11) are now complete.
- No blockers. Ready for phase verification, milestone audit, and completion.

---
*Phase: LEXCV-73-detail-page-printable-ficha-update*
*Completed: 2026-07-02*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/[id]/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
- FOUND: commit 2cf34f7
- FOUND: commit 2152ccd
