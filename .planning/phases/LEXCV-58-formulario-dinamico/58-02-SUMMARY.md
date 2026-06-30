---
phase: 58-formulario-dinamico
plan: 02
subsystem: web-clientes-listing-detail
tags: [badges, enum-fix, clientes, listing, detail]
requires:
  - "58-01: Cliente type extended with numero_cliente/avencado, Badge already in use"
provides:
  - "Clientes listing page (page.tsx) with PARTICULAR/EMPRESA enum (replacing legacy SINGULAR/COLETIVA)"
  - "numero_cliente (blue) and avencado (green) badges in listing rows (mobile + desktop) and detail header"
affects:
  - "web/src/app/(dashboard)/clientes/page.tsx"
  - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
tech-stack:
  added: []
  patterns:
    - "Badge variant=blue + font-mono for numero_cliente; Badge variant=green for avencado flag — conditional render, hidden when falsy/null"
key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/page.tsx
decisions: []
metrics:
  duration: "~20 min"
  completed: "2026-06-29"
---

# Phase 58 Plan 02: Listing Badges + Enum Fix Summary

Fixed the legacy SINGULAR/COLETIVA enum mismatch (introduced by Phase 57's backend rename to PARTICULAR/EMPRESA) across the clientes listing page's stat cards, filter dropdown, and row badge variant, and added `numero_cliente` (blue, monospace) and `avencado` (green, "Avençado") badges to listing rows (mobile cards + desktop table) and the client detail page header.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Fix enum values and add badges to listing page | 28e4775 | web/src/app/(dashboard)/clientes/page.tsx |
| 2 | Add numero_cliente and avencado badges to detail page header | 8c370b4 | web/src/app/(dashboard)/clientes/[id]/page.tsx |

## What Was Built

- **`web/src/app/(dashboard)/clientes/page.tsx`**:
  - Stat card counters renamed `totalSingulares`/`totalColetivas` → `totalParticulares`/`totalEmpresas`, filtering on `"PARTICULAR"`/`"EMPRESA"` instead of `"SINGULAR"`/`"COLETIVA"`; labels updated to "Particulares" and "Empresas".
  - Advanced filter dropdown options changed from `SINGULAR`/`Singular` and `COLETIVA`/`Coletiva` to `PARTICULAR`/`Particular` and `EMPRESA`/`Empresa`.
  - `ClienteRow` badge variant ternary now checks `tipo === "PARTICULAR"` (blue) / `tipo === "EMPRESA"` (purple) instead of the legacy values.
  - Mobile card view: after the existing `ativo` badge, conditionally renders a blue `numero_cliente` badge (font-mono) and a green "Avençado" badge when `cliente.avencado` is true, both `flex-shrink-0`.
  - Desktop table row name cell: after the `ID: #...` line, conditionally renders the same two badges in a `flex items-center gap-1 mt-1` wrapper.

- **`web/src/app/(dashboard)/clientes/[id]/page.tsx`**:
  - Added `import { Badge } from "@/components/ui/badge"` (not previously imported on this page).
  - Nome `<dd>` in the "Dados" card now uses `flex items-center gap-2 flex-wrap` and renders the client name followed by a conditional blue `numero_cliente` badge and a conditional green "Avençado" badge — both hidden when the underlying field is falsy/null, matching D-04 ("hidden when null").

## Verification

`pnpm build` (run from `web/`, after `pnpm install` to populate `node_modules` and copying `.env.example` → `.env.local` for the build-time env validation) exits 0 with all 26 routes compiled successfully, including `/clientes` and `/clientes/[id]`.

Acceptance criteria confirmed via grep:
- `page.tsx`: 0 occurrences of `SINGULAR`/`COLETIVA`, 4 occurrences each of `PARTICULAR`/`EMPRESA`, 5 occurrences of `numero_cliente`, 2 occurrences of `variant="green"`.
- `[id]/page.tsx`: `import { Badge } from "@/components/ui/badge"` present, 2 occurrences of `numero_cliente`, 1 occurrence each of `variant="green"` and `variant="blue"`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking environment issue] Missing `node_modules` and `.env.local` blocked build verification**
- **Found during:** Task 1 verification (`pnpm build`)
- **Issue:** The worktree had no `node_modules` (fresh checkout) and no `web/.env.local`, so `pnpm build` failed first with "next not recognized" then with "BACKEND_API_ORIGIN is required" (env validated at `next.config.ts` load time, per CLAUDE.md).
- **Fix:** Ran `pnpm install` in `web/`, then copied `web/.env.example` to `web/.env.local` (placeholder values only — `BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`). `.env.local` is gitignored and was not committed.
- **Files modified:** none tracked (node_modules and .env.local both gitignored)
- **Commit:** N/A (no tracked file changes)

No other deviations — both tasks executed exactly as specified in the plan and 58-PATTERNS-equivalent inline guidance (the phase has no separate 58-PATTERNS.md file; the plan's `<action>` blocks contained sufficiently precise instructions, verified against direct reads of both target files).

## Known Stubs

None — both badges are wired to real `Cliente.numero_cliente`/`Cliente.avencado` fields (extended in 58-01) and render conditionally based on actual data; no placeholder or mock values introduced.

## Threat Flags

None — no new network endpoints, auth paths, or trust-boundary changes. Matches the plan's threat model: `numero_cliente` is a non-sensitive sequential ID displayed read-only to users who already pass `clientes:view` permission checks on both pages.

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/page.tsx (contains PARTICULAR, EMPRESA, numero_cliente, variant="green")
- FOUND: web/src/app/(dashboard)/clientes/[id]/page.tsx (contains Badge import, numero_cliente, variant="blue", variant="green")
- FOUND: commit 28e4775
- FOUND: commit 8c370b4
- `pnpm build` exits 0
