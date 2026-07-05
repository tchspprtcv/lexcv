---
phase: LEXCV-76-separadores-—-dados-contactos-e-notas
verified: 2026-07-05T12:15:00Z
status: human_needed
score: 7/7 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Abrir a ficha de um cliente existente e clicar sequencialmente nos 7 botões de separador"
    expected: "7 botões aparecem na ordem Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações; 'Dados' fica ativo (variant secondary) ao abrir; os 5 não implementados mostram o card 'Em breve' instantaneamente, sem spinner nem pedido de rede visível no Network tab"
    why_human: "Requer execução no browser com dados reais/mock; grep confirma a estrutura JSX mas não a renderização visual nem a ausência de flicker/loading"
  - test: "Em modo edição, navegar para o separador 'Contactos e Notas' e depois clicar 'Guardar' com um campo obrigatório de 'Dados' inválido"
    expected: "A UI troca automaticamente para 'Dados', mostra o erro inline no campo e um toast 'Existem campos por corrigir no separador Dados.'"
    why_human: "Comportamento de validação cross-tab depende de execução real do react-hook-form no browser; grep confirma o handler `onError` mas não o resultado visual"
  - test: "Abrir o modal 'Adicionar' (Documento Entregue/A Tratar/Deslocação) em modo edição, preencher texto, mudar de separador e voltar a 'Dados'"
    expected: "O modal não reabre com o rascunho anterior (fecha e limpa ao sair de 'Dados', conforme o fix CR-01)"
    why_human: "Timing de efeito React (useEffect keyed on tab) e interação com Radix Dialog só é observável em execução real, não por análise estática"
  - test: "Redimensionar a janela para largura mobile e verificar a fila de 7 botões de separador"
    expected: "A fila de botões faz scroll horizontal (overflow-x-auto) em vez de quebrar linha, mantendo todos os 7 botões numa linha rolável"
    why_human: "Comportamento de overflow/scroll é visual, não verificável por grep"
---

# Phase 76: Separadores — Dados, Contactos e Notas Verification Report

**Phase Goal:** A ficha de cliente organiza a informação em separadores, com identificação incluída no card "Dados" principal
**Verified:** 2026-07-05T12:15:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador vê 7 botões de separador na ficha do cliente, na ordem: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações | VERIFIED | `page.tsx:416-467` — 7 `<Button>` elements in exact label order (Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações), none with `disabled` |
| 2 | Separador ativo por defeito ao abrir a ficha é "Dados" | VERIFIED | `page.tsx:121` — `const [tab, setTab] = React.useState<TabKey>("dados")` |
| 3 | Utilizador clica em qualquer um dos 5 separadores ainda não implementados e vê um card "Em breve" (sem spinner, sem chamada de API) | VERIFIED | `page.tsx:1055-1065` — `processos`/`pareceres`/`documentosEntregues`/`documentosATratar`/`deslocacoes` arms all render `<PlaceholderEmBreve />`; component body (`page.tsx:1091-1100`) has no hooks, no `isLoading`/`isError`, no fetch/hook calls — pure static JSX |
| 4 | Separador "Dados" apresenta NIF, tipo de documento e número de documento agrupados sob a sub-secção "Identificação" dentro do card "Dados" | VERIFIED | Edit mode: `page.tsx:564-622` (`<h4>Identificação</h4>` + `form.register("nif"\|"documento_tipo"\|"documento_numero")`, still inside the Dados `<CardContent>`). View mode: `page.tsx:665-677` (second `dl` with same heading, inside same `CardContent`, closes at line 679). Each field label appears exactly once per mode (grep-verified) |
| 5 | Separador "Contactos e Notas" apresenta os cards ClienteContactosCard e ClienteNotasCard, já não visíveis na página principal fora deste separador | VERIFIED | `page.tsx:1036-1054` — both cards render only inside `tab === "contactosNotas"` arm; `grep -c 'ClienteContactosCard'` = 2 (function def + 1 usage), `grep -c 'ClienteNotasCard'` = 2 (function def + 1 usage) — no duplication, no second render site |
| 6 | Ao clicar "Editar" estando noutro separador, a UI muda automaticamente para "Dados" | VERIFIED | `page.tsx:397` — `onClick={() => { setIsEditing(true); setTab("dados"); }}`; `Cancelar`/`Guardar` handlers (lines 379, 382-394) do not call `setTab` directly (Guardar only switches tab via the `onError` validation-failure branch, which is a distinct, intentional behavior, not a a forced switch on every click) |
| 7 | Toggle view/edit (isEditing/editable) de todos os campos e cards movidos permanece inalterado | VERIFIED | `isEditing` ternaries preserved verbatim around Identificação block (lines 478/624/664), `editable={isEditing}` prop unchanged on `ClienteContactosCard`/`ClienteNotasCard` (lines 1041, 1049); `legacyDocumentoTipo` occurrence count (10) matches pre-existing Phase-74 logic, all moved unchanged |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | Tab shell (7 botões), conteúdo real para "Dados" e "Contactos e Notas", placeholder para os outros 5; contains `type TabKey` | VERIFIED | `type TabKey` union (7 keys) declared at line 73-80; full tab shell, Identificação sub-section, contactosNotas arm, and 5 `PlaceholderEmBreve` arms all present and wired; `pnpm build` compiles cleanly (independently re-run, not just trusted from SUMMARY) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| Tab button row | `setTab` state setter | `onClick={() => setTab(<key>)}` | WIRED | All 7 buttons wired (lines 421, 428, 435, 442, 449, 456, 463); `grep -c 'setTab('` = 9 (7 buttons + Editar handler + validation-error handler) |
| Editar button | tab state | `setTab("dados")` inside Editar onClick | WIRED | Line 397: `setIsEditing(true); setTab("dados");` — exact pattern match |
| Identificação sub-section | `documento_tipo`/`documento_numero`/`nif` form bindings | relocated `form.register` calls | WIRED | Lines 570, 583, 615 (edit mode) all inside the Identificação block (564-622), inside the Dados card's CardContent — not orphaned, not duplicated (count = 1 each) |
| Contactos e Notas tab arm | `ClienteContactosCard`/`ClienteNotasCard` | JSX render inside `tab === "contactosNotas"` | WIRED | Lines 1038-1053; same props (`clienteId`, `canEditClientes`, `editable={isEditing}`, `data`, `isLoading`, `isError`) as pre-Phase-76 location — data flow from `useClienteContactos`/`useClienteNotas` hooks (lines 113-114) unchanged |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Identificação sub-section (view mode) | `cliente.data.nif`, `cliente.data.documento_tipo`, `cliente.data.documento_numero` | `useCliente(id)` hook (real API call, line 111) | Yes — same hook used pre-Phase-76, unmodified | FLOWING |
| Contactos e Notas tab | `contactos.data`, `notas.data` | `useClienteContactos(id)` / `useClienteNotas(id)` (lines 113-114, real API calls) | Yes — unmodified hooks | FLOWING |
| PlaceholderEmBreve (5 tabs) | none (static text) | N/A — intentionally no data source per plan spec ("sem chamada de API") | N/A by design | STATIC (intentional, matches must-have #3) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Production build compiles with the restructured file | `cd web && pnpm build` | `✓ Compiled successfully in 11.2s`, `Finished TypeScript in 10.3s`, all 23 routes generated including `/clientes/[id]` | PASS |
| No `disabled` attribute on any of the 7 tab buttons | `sed -n '416,467p' page.tsx \| grep disabled` | no output (no matches) | PASS |
| Acceptance-criteria grep counts (Task 1 & 2) | `grep -c` for `setTab(`, `ClienteContactosCard`, `ClienteNotasCard`, `form.register("nif"\|"documento_tipo"\|"documento_numero")`, `Em breve` | 9, 2, 2, 1/1/1, 1 — all match plan's specified thresholds exactly | PASS |
| Git commits referenced in SUMMARY/REVIEW-FIX actually exist | `git log --oneline \| grep -E "ff70d85\|b77aedb\|51edeb2\|b3ef070"` | All 4 commits found: `feat(76)` x2, `fix(76)` x2 | PASS |
| Analog file (`processos/[id]/page.tsx`) untouched by this phase | `git diff --stat HEAD~4 -- processos/[id]/page.tsx` | No diff output (unchanged) | PASS |

### Probe Execution

Not applicable — this is a frontend UI-restructuring phase with no `scripts/*/tests/probe-*.sh` convention in this repository and none declared in PLAN/SUMMARY. Skipped.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CLI-15 | 76-01-PLAN.md | Ficha de cliente apresenta 7 separadores: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações | SATISFIED | 7-tab button row confirmed in exact order, `TabKey` union type matches, default tab "dados" |
| CLI-18 | 76-01-PLAN.md | Separador "Contactos e Notas" apresenta os cards de Contactos e Notas atualmente na página principal | SATISFIED | `ClienteContactosCard`/`ClienteNotasCard` moved into `tab === "contactosNotas"` arm exclusively, no duplicate render site |
| CLI-19 | 76-01-PLAN.md | Card "Dados" principal inclui identificação (NIF, tipo de documento, número de documento) como elemento do card | SATISFIED | Identificação sub-section confirmed inside Dados card's `CardContent`, both view and edit mode, single occurrence each |

No orphaned requirements — REQUIREMENTS.md maps only CLI-15, CLI-18, CLI-19 to Phase 76, and all three appear in the plan's `requirements` frontmatter and are satisfied above.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None found (no TBD/FIXME/XXX/TODO/HACK/placeholder-text/stub-return patterns in the modified file) | — | — |

Pre-existing `react-hooks/set-state-in-effect` lint errors at lines 1240/1385/1587 (in `ResponsaveisCard`/`ClienteContactosCard`/`ClienteNotasCard`, carried over from Phase 75, documented in 76-REVIEW.md IN-01) are informational only — confirmed pre-existing by the review's stash-comparison, not introduced by this phase, and not part of this phase's `files_modified` scope beyond relocation.

### Human Verification Required

### 1. 7-tab click-through and default-tab visual check

**Test:** Open an existing cliente's ficha; observe which tab is active by default; click through all 7 tab buttons in sequence.
**Expected:** "Dados" is visually active (secondary variant) on load; all 7 labels render in the specified order; the 5 unimplemented tabs show "Em breve" instantly with no loading spinner or network request.
**Why human:** Static analysis confirms the JSX/state wiring but not the actual rendered visual state, variant styling, or absence of a network call at runtime.

### 2. Cross-tab validation error surfacing

**Test:** Enter edit mode, navigate to "Contactos e Notas", leave a required "Dados" field invalid, click "Guardar".
**Expected:** UI switches back to "Dados", inline field error displays, and a toast "Existem campos por corrigir no separador Dados." appears.
**Why human:** This is react-hook-form runtime validation behavior (`handleSubmit(onSubmit, onError)`) that can only be observed by actually triggering a validation failure in a running app.

### 3. Intake dialog reset on tab round-trip (CR-01 fix)

**Test:** In edit mode, open the "Adicionar Documento Entregue/A Tratar/Deslocação" modal, type a draft, switch away from "Dados" and back.
**Expected:** The modal does not reopen with the stale draft — it should be closed and the draft cleared.
**Why human:** Depends on React effect timing and Radix Dialog `open` prop behavior at runtime; not verifiable by grep alone (though code review already traced the logic and confirmed it structurally).

### 4. Mobile tab-row horizontal scroll

**Test:** Resize viewport to mobile width and observe the 7-button tab row.
**Expected:** Buttons stay in a single row and scroll horizontally (`overflow-x-auto` + `w-max`), rather than wrapping to multiple lines.
**Why human:** Visual/layout behavior not verifiable through static code inspection.

### Gaps Summary

No gaps found. All 7 must-have truths, the single required artifact, and all 4 key links are VERIFIED against actual code (not SUMMARY claims) — independently re-checked via direct file reads, grep counts matching the plan's own acceptance criteria, and a clean re-run of `pnpm build`. The code review (76-REVIEW.md) is clean (0 critical, 0 warning) with both prior fixes (CR-01, WR-01) confirmed correctly applied and not regressed. Git commit hashes referenced in SUMMARY/REVIEW-FIX all exist in history. Requirements CLI-15, CLI-18, CLI-19 are fully accounted for with no orphans against REQUIREMENTS.md.

Status is `human_needed` (not `passed`) solely because this is a UI-restructuring phase whose core behaviors (tab switching visuals, cross-tab validation UX, dialog-reset timing, mobile scroll) require live browser interaction to fully confirm — consistent with this milestone's established pattern of deferring live UAT to a human verification pass, as the SUMMARY itself notes. This does not indicate a code defect; it reflects the limits of static verification for visual/runtime UI behavior.

---

_Verified: 2026-07-05T12:15:00Z_
_Verifier: Claude (gsd-verifier)_
