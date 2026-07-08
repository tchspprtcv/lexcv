---
status: partial
phase: 84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
source: [84-VERIFICATION.md]
started: 2026-07-08T02:35:00Z
updated: 2026-07-08T02:35:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Complete the intake wizard end-to-end: attempt to submit step 1 without selecting Origem, then select an Origem, finish intake, formalize the processo, and open its ficha
expected: Step 1 cannot be submitted with Origem left on the placeholder (inline red error under the select, no network call to /processos/intake); after formalização, the ficha's Dados card shows the chosen Origem read-only (no edit affordance anywhere)
result: [pending]

### 2. Open /processos/{id}/editar for a processo that already has a juizo value, confirm the Juízo input pre-fills, edit it, save, then reload /processos/{id} and check the Dados card
expected: Juízo input shows the existing value on load; after save, the ficha's read-only Juízo row reflects the new value
result: [pending]

### 3. Open /processos/{id}/termo-honorarios twice: once for a processo whose Honorário has valorTotal=null, once after setting a valorTotal. Also trigger the browser print preview
expected: First visit: Imprimir button is visually disabled, red message with a working 'Financeiro' link shows. Second visit: Imprimir is enabled, Valor Total renders as a formatted CVE currency string, not a raw number. Print preview hides the toolbar/back-link/aside and paginates cleanly on A4
result: [pending]

### 4. On a processo's ficha, open Partes 'Adicionar Parte', type a name, click Cancelar, reopen the dialog; repeat for Fases 'Adicionar Fase'. Separately, on the Fases tab, change one row's status dropdown, then click a different (untouched) row's 'Guardar'
expected: Reopening either dialog after Cancelar shows a blank form, not the previously-typed text (WR-02 fix). Clicking 'Guardar' on an untouched row saves that row's current status unchanged, not an empty/undefined PUT body (CR-01 fix)
result: [pending]

### 5. Create a Decisão with an attached PDF file via the Decisões tab's 'Adicionar Decisão' dialog, then open the Documentos tab and confirm the same file appears there; separately, open 'Editar' on an existing Testemunha and confirm the dialog pre-fills its current values
expected: The uploaded Decisão attachment shows up as a Documento in the Documentos tab without a separate upload step; the Testemunha edit dialog opens pre-populated with nome/tipo/contacto/notas matching the row clicked
result: [pending]

### 6. Reorder two Factos via the 'Editar Facto' dialog's Ordem field and confirm the table re-sorts; upload a document via the processo's Documentos tab and confirm it also appears on the generic /documentos list page
expected: After saving a new Ordem value, the Factos table row order updates to match; a document uploaded from the processo Documentos tab appears in the generic /documentos page with the correct processo association
result: [pending]

### 7. Log in as a user with processos:view but not processos:edit, and separately as a user with processos:edit but not documentos:edit; visit the ficha and every tab
expected: The processos:view-only user sees all lists but no 'Adicionar'/'Editar'/'✕' controls anywhere, including the Fases status select (now disabled per WR-03); the processos:edit-without-documentos:edit user sees full CRUD on Partes/Fases/Decisões/Factos/Testemunhas but only a read-only Documentos list
result: [pending]

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps
