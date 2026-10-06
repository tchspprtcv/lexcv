---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 11
subsystem: web-pages
tags: [fiscal, efatura, web, ui, source-gate]
requires: ["136-08"]
provides:
  - "Documentos fiscais list: 'Comunicação' badge column (id comunicacao) after Tipo, four-state 'Comunicação' filter (URL key estado, whitelisted)"
  - "Document detail: one header ComunicacaoEstadoBadge + description line from comunicacao.estado, ComunicacaoFiscalCard before Ligações, updated notice copy"
  - "ModoSimuladoBanner at the top of list, detail and honorário pages"
  - "verify:documentos-fiscais section (h) Phase 136"
affects: [136-16]
tech-stack:
  added: []
  patterns:
    - "URL filter whitelist through Object.prototype.hasOwnProperty on the ESTADOS_COMUNICACAO table (same table gives the option labels)"
key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/scripts/verify-documentos-fiscais.mjs
decisions:
  - "The detail page computes podeReprocessarComunicacao once and passes it with modoComunicacao into DetalheDocumento; the header badge title and the helper line both use descricaoEstadoComunicacao(estado, podeReprocessar)"
  - "The list badge uses the library's default description (the list has no reprocess action)"
  - "The gate also pins podeReprocessarComunicacao(permissions) in the card (the reprocess component receives no permission prop, the card gates it), the detail page's freedom from useReprocessarComunicacao, the <ComunicacaoFiscalCard composition and the extended list description"
metrics:
  duration: "~20 min"
  completed: 2026-10-06
  tasks: 2
  files: 5
---

# Phase 136 Plan 11: Phase 136 UI wired into the fiscal pages Summary

The Phase 136 communication UI is now visible on the real pages.

- **List:** a "Comunicação" badge column sits right after "Tipo". A "Comunicação" filter offers Todas, Pendente, Aceite (simulação), Rejeitado and Erro, kept in the URL as `estado` and whitelisted to the four states.
- **Detail:** the header shows exactly one communication badge plus its one-line description, both from `comunicacao.estado`. The read-only "Comunicação fiscal" card sits after "Valores" (after "Notas de crédito" on an FR) and before "Ligações". The per-document notice no longer promises a future communication.
- **Banner:** the "Modo simulado" banner is at the top of the list, detail and honorário pages, inside the authorised branch.
- **Source gate:** `verify:documentos-fiscais` gains section (h), which pins all of this. It also forbids authorisation wording in the eight fiscal UI files. The detail page still has no mutation.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | List column + filter, detail header/card/banner, honorário banner | c16721c |
| 2 | verify:documentos-fiscais section (h) Phase 136 | 62d9e41 |

## Verification

- `pnpm exec tsc --noEmit` is clean.
- `pnpm lint` reports 0 errors. The same 20 warnings were already there, and none are in the touched files.
- `pnpm exec vitest run`: 14 files, 318 tests, green.
- `pnpm verify:documentos-fiscais` and `pnpm verify:faturacao` pass.
- **Negative check of the new gate:** appending `// Autorizado` to `comunicacao-estado-badge.tsx` made `verify:documentos-fiscais` exit 1 with `contem linguagem de autorização "Autorizad"`. After restoring the file it exits 0, and `git status` shows the badge file unchanged.
- **Acceptance greps:**
  - `<ComunicacaoEstadoBadge` appears once in columns and `title="Estado"` 0 times.
  - `<ComunicacaoFiscalCard` appears once in the detail page.
  - `<ModoSimuladoBanner` appears once in each of the three pages.
  - `useMutation|useReprocessarComunicacao|method: "POST"` appears 0 times in the detail page.
  - `chega numa versão futura` appears 0 times.
  - `(h) Phase 136` appears once in the script, and `Autorizad` at least once.
  - `git diff 1c7df63 -- web/scripts/verify-documentos-fiscais.mjs | grep -c '^-.*exige'` is 0, so no existing check was removed.

## Deviations from Plan

**1. [Rule 2 - Hardening] Extra gate pins**
- **Change:** besides the planned tokens, section (h) also requires these:
  - `<ComunicacaoFiscalCard` in the detail page.
  - The extended list description sentence.
  - No `useReprocessarComunicacao` in the detail page.
  - `podeReprocessarComunicacao(permissions)` in the card. This is the actual gate site; the reprocess component receives no permission prop.
- **Commit:** 62d9e41.

Otherwise the plan was executed as written. The loading skeleton gained one card for the new "Comunicação fiscal" card.

## Threat Flags

None.
- T-136-40: wording gate, banner on three pages, and updated notice copy.
- T-136-41: the detail page still has no mutation, and the gate proves it.
- T-136-42: the gate was extended only.

## Self-Check: PASSED

- FOUND: all 5 modified files listed in key-files
- FOUND: commits c16721c, 62d9e41
