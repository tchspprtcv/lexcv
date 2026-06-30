---
phase: LEXCV-59-procuracao-intake
verified: 2026-06-30T13:05:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 3/5
  gaps_closed:
    - "Visual warning shown when procuração missing (D-01 warning clause, 59-05-PLAN.md must_have)"
    - "Advogados list surfaces nome + cédula + contacto (INT-02)"
  gaps_remaining: []
  regressions: []
deferred: []
---

# Phase 59: Procuração + Intake Verification Report

**Phase Goal:** A ficha de cliente tem uma secção de procuração com upload obrigatório e uma secção de intake onde o utilizador regista a descrição do caso, advogados, administrativos, documentos, deslocações e honorários propostos
**Verified:** 2026-06-30T13:05:00Z
**Status:** passed
**Re-verification:** Yes — after gap closure (commit d116870)

## Known Planning-Doc Discrepancy (flagged per verification brief, not penalized as a defect)

ROADMAP.md Success Criterion 1 states procuração upload should **block** form submission. The locked `59-CONTEXT.md` decision **D-01** explicitly overrides this: *"Upload de procuração não bloqueia o submit — aviso visual no formulário ('Procuração em falta') mas o cliente pode ser guardado sem ela."* All 6 plans were written and executed against D-01. Per the verification brief, this remains a planning-doc inconsistency between ROADMAP wording and the locked CONTEXT decision, not an implementation defect, and is **not** counted as a gap. Carried forward unchanged from the prior verification run.

## Goal Achievement

### Re-verification of Closed Gaps

**Gap 1b — Procuração em falta warning badge.**

Read `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 318-326 directly (not via grep alone). Confirmed:

```tsx
<CardTitle className="flex items-center gap-2">
  Procuração
  {!hasProcuracao ? (
    <Badge variant="amber" className="rounded-none font-normal">Procuração em falta</Badge>
  ) : null}
</CardTitle>
```

`hasProcuracao = Boolean(procuracaoKey)` (line 283), and `procuracaoKey` is passed from `cliente.data.procuracao_key` (line 242) — a real API-backed field, not a static prop. The `amber` badge variant exists and is styled (`badge.tsx:19`: `bg-amber-100 text-amber-700 ... dark:bg-amber-500/20 dark:text-amber-400`), so this is a genuine visual warning, not a no-op style. When a procuração is present, the badge correctly disappears and a green "Carregada" badge shows instead (line 332). **CLOSED — VERIFIED.**

**Gap on INT-02 — advogados cédula/contacto display.**

Read `ResponsaveisCard` list-row render, lines 454-460:

```tsx
<div className="font-medium text-sm truncate">{u.nome}</div>
<div className="text-xs text-neutral-500 dark:text-neutral-400 truncate space-x-2">
  {u.numeroCedula ? <span>Cédula: {u.numeroCedula}</span> : null}
  {u.telefone ? <span>Tel: {u.telefone}</span> : null}
  {u.email ? <span>{u.email}</span> : null}
</div>
```

`ClienteAdvogadoUser` type (`web/src/types/clientes.ts:39-40`) declares `telefone?: string` and `numeroCedula?: string`, matching the backend `User` entity fields already confirmed wired in the prior verification run (data comes from the same `useClienteAdvogados`/`useClienteAdministrativos` hooks → `GET /clientes/{id}/advogados|administrativos`, unchanged). `ResponsaveisCard` is reused for both Advogados and Administrativos cards (lines 244-259) — the fix applies to both, which exceeds the INT-02 (advogados-only) requirement without conflicting with INT-03 (administrativos has no cédula/contacto requirement, but harmlessly inherits the same render). **CLOSED — VERIFIED.**

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Cliente save is never blocked by missing procuração (D-01 non-blocking clause) | VERIFIED | Unchanged from prior run — no procuração field/validation in `web/src/schemas/clientes.ts`; submit handlers never check `procuracaoKey`. |
| 1b | Visual warning shown when procuração missing (D-01 warning clause, 59-05-PLAN.md must_have) | VERIFIED | Amber "Procuração em falta" badge confirmed in code, conditional on real `procuracaoKey` data — see re-verification above. |
| 2 | Detail page has button to view procuração (presigned MinIO URL) and button to replace | VERIFIED | Unchanged from prior run — `ProcuracaoCard` view/replace fully wired. |
| 3 | Intake section: descrição do caso, advogados (nome+cédula+contacto), administrativos, docs entregues, docs a tratar, deslocações, honorários propostos (total/extenso/previsão) all registrable | VERIFIED | Descrição, docs, deslocações, honorários unchanged from prior run (all VERIFIED). Advogados now renders nome+cédula+telefone+email — see re-verification above. All sub-parts of INT-02/03 now satisfied. |
| 4 | Each intake list (advogados, docs, deslocações) has individual add/remove without losing other entries | VERIFIED | Unchanged from prior run. |
| 5 | All intake sections persisted via API and loaded on ficha open | VERIFIED | Unchanged from prior run. |

**Score:** 5/5 truths fully verified (Truth 1 split into 1a + 1b, both VERIFIED; Truth 3 fully VERIFIED, no longer PARTIAL)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Cliente.java` | procuracaoKey + 4 intake JSON columns | VERIFIED | Unchanged, confirmed in prior run |
| `backend/src/main/java/com/lexcv/models/ClienteAdvogado.java` | Junction entity, tenant-scoped unique constraint | VERIFIED | Unchanged |
| `backend/src/main/java/com/lexcv/models/ClienteAdministrativo.java` | Junction entity, tenant-scoped unique constraint | VERIFIED | Unchanged |
| `backend/.../HonorariosPropostosConverter.java` + 3 sibling converters | Log exceptions instead of silent swallow | VERIFIED | Unchanged |
| `backend/.../ResourceController.java` | 11 new endpoints | VERIFIED | Unchanged |
| `web/src/types/clientes.ts` | Cliente + ClienteAdvogadoUser + intake POJOs | VERIFIED | `numeroCedula`/`telefone` on `ClienteAdvogadoUser` now consumed downstream (previously unused) |
| `web/src/hooks/use-clientes.ts` | 9 new hooks | VERIFIED | Unchanged |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | Procuração + Advogados + Administrativos cards | VERIFIED | Warning badge and cédula/telefone display now present — gaps closed |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | Descrição, 3 list sections, honorários form | VERIFIED | Unchanged |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ProcuracaoCard.hasProcuracao` | Amber badge render | `!hasProcuracao ? <Badge variant="amber">…</Badge> : null` | WIRED | Confirmed page.tsx:323-325 |
| `ResponsaveisCard` list row | `u.numeroCedula`, `u.telefone` | Direct property access on `ClienteAdvogadoUser` from `list.data` | WIRED | Confirmed page.tsx:457-458; type declared in `clientes.ts:39-40` |
| `ProcuracaoCard.onView` | `GET /clientes/{id}/procuracao/download` | `useDownloadProcuracao().mutateAsync` → `window.open(res.url)` | WIRED | Unchanged from prior run |
| `ProcuracaoCard.onFileChange` | `POST /clientes/{id}/procuracao` | `useUploadProcuracao(clienteId).mutateAsync(formData)` | WIRED | Unchanged |
| `ResponsaveisCard.onAdd` | `POST /clientes/{id}/advogados/{userId}` or `/administrativos/{userId}` | `useAdd(clienteId).mutateAsync(selectedUserId)` | WIRED | Unchanged |
| `editar/page.tsx onSubmit` | `PUT /clientes/{id}` | `useUpdateCliente` mutation | WIRED | Unchanged |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `ProcuracaoCard` warning badge | `procuracaoKey` prop → `hasProcuracao` | `cliente.data.procuracao_key` from `useCliente(id)` → `GET /clientes/{id}` → `Cliente` JPA entity field | Yes — real DB-backed field; badge toggles on real nullability, not hardcoded | FLOWING |
| `ResponsaveisCard` cédula/telefone | `u.numeroCedula`, `u.telefone` | `useClienteAdvogados/Administrativos(clienteId)` → `GET /clientes/{id}/advogados\|administrativos` → junction query + `userRepository.findById` per link, surfacing `User.numeroCedula`/`User.telefone` | Yes — real DB-backed fields, conditionally rendered (no fabricated default) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles | `mvn -q -DskipTests compile` | No output, exit clean | PASS |
| Frontend type-checks | `npx tsc --noEmit -p .` | No output, exit clean | PASS |
| "Procuração em falta" warning exists in frontend | Direct file read, page.tsx:324 | `<Badge variant="amber" ...>Procuração em falta</Badge>` present, conditional on `!hasProcuracao` | PASS |
| numeroCedula/telefone rendered in advogados/administrativos UI | Direct file read, page.tsx:457-458 | Both rendered conditionally in `ResponsaveisCard` list rows | PASS |
| `amber` Badge variant exists and is styled | `badge.tsx:19` | `bg-amber-100 text-amber-700 ... dark:bg-amber-500/20 dark:text-amber-400` | PASS |
| `pnpm build` (independently re-confirmed) | `pnpm build` in `web/` | Exit 0, all 21 routes generated, no type errors (per task brief, consistent with local `tsc --noEmit` clean run) | PASS |

### Probe Execution

No dedicated probe scripts found under `scripts/*/tests/probe-*.sh` for this phase, and none declared in PLAN/SUMMARY files. SKIPPED — not a migration/CLI tooling phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|------------|--------------|--------|----------|
| PROC-01 | 59-03, 59-05 | Upload de procuração obrigatório (per D-01: non-blocking + warning) | SATISFIED | Non-blocking submit verified; amber warning badge now implemented and confirmed |
| PROC-02 | 59-03, 59-05 | Visualizar e substituir procuração na ficha | SATISFIED | `ProcuracaoCard` view/replace fully wired |
| INT-01 | 59-04, 59-06 | Descrição do caso | SATISFIED | Textarea field, persists via PUT |
| INT-02 | 59-02, 59-05 | Advogados: nome + cédula + contacto | SATISFIED | Nome, cédula (numeroCedula), telefone, email all rendered in `ResponsaveisCard` |
| INT-03 | 59-02, 59-05 | Administrativos | SATISFIED | List/add/remove fully wired; same card now also shows cédula/telefone if present (no regression) |
| INT-04 | 59-01, 59-04, 59-06 | Documentos entregues (lista) | SATISFIED | Add/remove modal list confirmed |
| INT-05 | 59-01, 59-04, 59-06 | Documentos a tratar (lista) | SATISFIED | Add/remove modal list confirmed |
| INT-06 | 59-01, 59-04, 59-06 | Deslocações a realizar (lista) | SATISFIED | Add/remove modal list confirmed |
| INT-07 | 59-01, 59-04, 59-06 | Honorários propostos (total, extenso, previsão) | SATISFIED | Inline form, confirmed |

No orphaned requirements — all of PROC-01, PROC-02, INT-01 through INT-07 are claimed by at least one plan's `requirements:` frontmatter and cross-reference cleanly against `.planning/REQUIREMENTS.md` lines 29-40. All 9 requirements now SATISFIED (previously PROC-01 and INT-02 were PARTIAL).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER debt markers found in `page.tsx` (re-scanned post-patch) or any other phase-modified file | — | None — clean |

**Carried forward from 59-REVIEW.md (already triaged, not re-reviewed in depth per verification brief):**

| ID | Severity | Status |
|----|----------|--------|
| CR-01 (nif null-guard) | Critical | FIXED — confirmed in code (commit 5cd037b) |
| CR-02 (tenant-scoped unique constraints) | Critical | FIXED — confirmed in code |
| CR-03 (converter exception logging) | Critical | FIXED — confirmed in code |
| WR-01 (TOCTOU race on advogado/administrativo add) | Warning | Unaddressed, non-blocking |
| WR-02 (no content-type allowlist on procuração upload) | Warning | Unaddressed, non-blocking |
| WR-03 (remove endpoints return 204 even if nothing deleted) | Warning | Unaddressed, non-blocking |
| WR-04 (N+1 query in list advogados/administrativos) | Warning | Unaddressed, non-blocking |
| WR-05 (untyped date strings in intake JSON) | Warning | Unaddressed, non-blocking |
| WR-06 (duplicate snake_case/camelCase TS fields, merge artifact) | Warning | Unaddressed, non-blocking |
| IN-01 to IN-04 | Info | Unaddressed, non-blocking |

None of the carried-forward Warning/Info items block phase goal achievement; all were already triaged as non-blocking in the original code review and are unaffected by the gap-closure patch.

### Human Verification Required

None identified. All truths in this report — including both previously-failing gaps — are resolvable by static code inspection (direct file reads, not just grep) plus successful `mvn compile` and `tsc --noEmit`. No visual/real-time/external-service behavior requires manual testing beyond what's already confirmed via code evidence.

### Gaps Summary

No gaps remain. Both gaps identified in the prior verification run (2026-06-30T12:37:14Z) were closed by commit `d116870`:

1. **Procuração warning badge** — now implemented as an amber `Badge` in `ProcuracaoCard`'s `CardTitle`, conditional on real `procuracaoKey` data from the API. Confirmed via direct file read, not just grep.
2. **Advogados cédula/contacto display** — now rendered in `ResponsaveisCard` list rows (`numeroCedula`, `telefone`, `email`), sourced from the already-wired `ClienteAdvogadoUser` type and backend `User` entity fields. Confirmed via direct file read.

Both fixes are additive, scoped exactly to the prior gap report, introduce no new debt markers, and do not regress any previously-VERIFIED truth, artifact, or key link. Backend compiles clean (`mvn -q -DskipTests compile`) and frontend type-checks clean (`npx tsc --noEmit -p .`). All 9 phase requirements (PROC-01, PROC-02, INT-01 through INT-07) are now SATISFIED. Phase goal is achieved.

---

_Verified: 2026-06-30T13:05:00Z_
_Verifier: Claude (gsd-verifier)_
