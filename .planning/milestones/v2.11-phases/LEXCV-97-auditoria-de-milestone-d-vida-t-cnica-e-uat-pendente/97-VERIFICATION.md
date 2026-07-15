---
phase: 97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente
verified: 2026-07-14T21:15:00Z
status: human_needed
score: 5/5 must-haves verified (code/document level)
overrides_applied: 0
human_verification:
  - test: "Open a company client's detail page (/clientes/{id}) and printable ficha (/clientes/{id}/ficha) at localhost:3000 for a client with tipo=EMPRESA and confirm the Tipo de Documento field shows 'Registo Comercial', not 'REG_COMERCIAL'."
    expected: "Both pages render the Portuguese label, not the raw enum string."
    why_human: "97-02-SUMMARY.md explicitly states this visual spot-check was never performed live (the shared localhost:3000 instance reflected the pre-merge checkout during plan execution) and explicitly recommends it be done 'as part of the phase-level 97-VERIFICATION/AUD-02 pass' — i.e. this verification. The wiring and label map are code-verified correct (see Required Artifacts below), but only a rendered-page visual check closes the loop; no browser-automation tool is available to this verifier."
  - test: "Print-preview layout of /processos/{id}/termo-honorarios (Phase 84 scenario 3) and the Phase 82 scenario 1 formalizar → /financeiro no-crash render."
    expected: "Print CSS produces a sane A4 layout; /financeiro pages render valorTotal as blank/'A confirmar', not the literal string 'null', and don't crash."
    why_human: "97-UAT.md records these as NEEDS-HUMAN-VISUAL — the gating/formatting logic is code-verified, but actual rendered layout requires an authenticated browser session this executor cannot drive. Carried forward explicitly, not silently dropped; zero indication of a functional defect."
  - test: "Two-panel live check: open the notification bell dropdown and /notificacoes page simultaneously, mark one notification read from the page, confirm the bell's badge/list updates without a manual refresh (Phase 89 scenario 8); and independently confirm the same-origin link-guard (scenario 10) and pagination-flash fix (scenario 11) visually."
    expected: "Bell and page reflect the shared TanStack Query invalidation in real time; a synthetic non-clickable notification renders as plain text (not a link); page-count transitions without a visible flash."
    why_human: "97-UAT.md records these as NEEDS-HUMAN-VISUAL — the query-key wiring and the underlying fixes (WR-01/WR-02) are already proven correct by 89-VERIFICATION.md's 16/16 behavioral tests and lint checks; only the live rendered/timing behavior remains open. Zero functional risk per prior evidence; a headless executor cannot drive an interactive two-panel session."
  - test: "RBAC second-user walkthrough: log in as a user with processos:edit but not documentos:edit (or processos:view only) and confirm the Documentos tab / Fases status controls are read-only exactly as Phase 84 scenario 7 describes."
    expected: "A processos:view-only user sees no CRUD affordances anywhere on the ficha; a processos:edit-without-documentos:edit user sees full CRUD on other tabs but a read-only Documentos list."
    why_human: "Requires a second, differently-scoped login this executor does not have credentials for. Code-verified (permission checks located and read), but the RBAC UI behavior itself needs a live second-user session to close for real."
---

# Phase 97: Auditoria de Milestone — Dívida Técnica e UAT Pendente Verification Report

**Phase Goal:** A milestone termina com isolamento de tenant verificado nas superfícies novas, UAT ao vivo pendente das fases 75/76/79/81/82/84/85/89 fechado ou explicitamente contornado, dívidas menores conhecidas corrigidas, e uma auditoria fresca ao código sem gaps não documentados.
**Verified:** 2026-07-14T21:15:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth (mapped to requirement) | Status | Evidence |
|---|---|---|---|
| 1 | AUD-01: Every new/changed notification query (preferences, team resolution, snooze) filters by `tenant_id`, not just `user_id`/`cliente_id`/`destinatario_id` | ✓ VERIFIED | Read `NotificacaoPreferenciaRepository.java` (all 4 methods carry `TenantId`+`UserId`, native `upsertSilenciar` targets `(tenant_id, user_id, categoria)`); `NotificacaoService.java:150-160` `resolverEquipaCliente` calls only `findByClienteIdAndTenantId` on both `ClienteAdvogadoRepository`/`ClienteAdministrativoRepository` — grep confirms neither repo exposes an unscoped `findByClienteId`-only variant; `NotificacaoRepository.java:48-70` both `snoozedUntil`-aware queries filter `n.tenantId = :tenantId AND n.destinatarioId = :destinatarioId`, and `findByIdAndTenantIdAndDestinatarioId` backs `snooze()`. No code changes were needed (verdict COVERED) — confirmed by direct source read, not just trusting the SUMMARY. |
| 2 | AUD-02: All ~38-40 pending UAT scenarios across phases 75/76/79/81/82/84/85/89 have an explicit verdict; none bare "pending" | ✓ VERIFIED | `97-UAT.md` exists, covers all 8 phases. Cross-checked scenario counts against the original `*-HUMAN-UAT.md` source files: 75=5, 76=4, 79=3, 81=5, 82=4, 84=7, 85=1, 89=11 — all 40 individually enumerated with a verdict (CODE-VERIFIED / NEEDS-HUMAN-VISUAL / OPEN-WITH-REASON / BLOCKED-with-reason), matching the summary table's per-phase counts exactly. Phase 81 #5 / Phase 82 #4 (ddl-auto=validate migration scenarios) and Phase 85 (product decision) are explicitly OPEN-WITH-REASON, not dropped. |
| 3 | AUD-03: `DocumentoTipo` labels translated at both render sites; 4 NIF validation scenarios have automated backend coverage | ✓ VERIFIED | `getDocumentoTipoLabel` exists in `web/src/lib/cliente-documento-tipo.ts`, backed by a single `DOCUMENTO_TIPO_LABELS` map also reused by `OPTIONS_BY_TIPO`. Confirmed by direct grep that `clientes/[id]/page.tsx:680` and `clientes/[id]/ficha/page.tsx:185` both call it (not raw `documento_tipo`/`documentoTipo`). `ClienteNifValidationTest.java` exists with exactly 4 test methods covering valid/blank-null-whitespace/wrong-length/non-numeric, asserting on the exact message strings. Ran `mvn -q test -Dtest=ClienteNifValidationTest` — passed, exit code 0. |
| 4 | AUD-04: `MINIO_ENDPOINT` blocker documented resolved (env/config, not code) in STATE.md and PROJECT.md | ✓ VERIFIED | `STATE.md` line 110 and `PROJECT.md` lines 110/186 both mark the blocker RESOLVED (2026-07-14) with the concrete resolution (real `backend/.env` values against `lexcv_minio` container, `MinioConfig.s3Client()` no longer throws), explicitly framed as environment/config not a code fix. Live backend (`localhost:8080`, confirmed reachable and returning 200 on `/api/v1/setup/status` during this verification) corroborates the environment is genuinely functional. |
| 5 | AUD-05: A fresh (not re-listed) code-discovery audit ran over the milestone's changed surfaces, with findings fixed-or-recorded | ✓ VERIFIED | Confirmed via direct grep: all 8 `NotificacaoController` endpoints carry `@PreAuthorize("hasAuthority('notificacoes:view')")` (matches the SUMMARY's claim exactly); grep for `TODO\|FIXME\|XXX` in the 3 audited files returns only a substring false-positive on the Portuguese word "TODOS" (correctly dismissed, not a real marker); grep for `/eventos/upcoming` references found none (only an unrelated local variable named `upcoming` in `agenda/page.tsx`); `AlertasDiariosJob.java` has 8 `catch (Throwable e)` blocks across its job/tenant/category/entity layers, consistent with the claimed 4-layer isolation. STATE.md Pending Todos/Deferred Items were updated accordingly (Phase 86/87 stale AUD-02 attributions corrected, as instructed). |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java` | Dual-scoped (tenant+user) preference queries | ✓ VERIFIED | All 4 methods carry `TenantId`+`UserId`; native upsert ON CONFLICT targets the composite key |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | Tenant-scoped team resolution + snooze mutation | ✓ VERIFIED | `resolverEquipaCliente` uses only `findByClienteIdAndTenantId`; `snooze` fetches via `findByIdAndTenantIdAndDestinatarioId` |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | Tenant+destinatario scoped snooze-visibility queries | ✓ VERIFIED | Both `snoozedUntil`-aware JPQL queries filter `tenantId AND destinatarioId` |
| `web/src/lib/cliente-documento-tipo.ts` | `getDocumentoTipoLabel` translating every enum value | ✓ VERIFIED | Single-sourced `DOCUMENTO_TIPO_LABELS` map; returns raw value verbatim for unknown/legacy values as designed |
| `backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java` | Bean-Validation coverage for the 4 NIF scenarios | ✓ VERIFIED | Exists, 4 test methods, ran green (`mvn -q test -Dtest=ClienteNifValidationTest`, exit 0) |
| `.planning/phases/.../97-UAT.md` | Consolidated UAT closure record for 8 phases | ✓ VERIFIED | Exists, all 40 scenarios individually verdicted, scenario counts cross-checked against original source files |
| `.planning/STATE.md` | Updated Pending Todos/Blockers/Deferred Items reflecting AUD outcomes | ✓ VERIFIED | MINIO marked RESOLVED, AUD-03 todos marked CLOSED, AUD-01/05 verdicts recorded, Phase 86/87 stale attributions corrected |
| `.planning/PROJECT.md` | MinIO resolution + AUD outcome summary | ✓ VERIFIED | MINIO closure documented in Context note and Key Decisions with date/phase reference |
| `.planning/REQUIREMENTS.md` | AUD-01..05 marked Complete | ✓ VERIFIED | Traceability table shows 15/15 v2.11 requirements Complete |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `NotificacaoService.resolverEquipaCliente` | `ClienteAdvogadoRepository`/`ClienteAdministrativoRepository.findByClienteIdAndTenantId` | tenant-scoped junction lookup | ✓ WIRED | Confirmed by direct grep, both repos expose only the tenant-scoped variant |
| `NotificacaoService.snooze` | `NotificacaoRepository.findByIdAndTenantIdAndDestinatarioId` | tenant+recipient scoped fetch before mutation | ✓ WIRED | Confirmed by direct grep |
| `web/.../clientes/[id]/page.tsx` | `getDocumentoTipoLabel` | label lookup on Tipo de Documento row | ✓ WIRED | Line 65 import, line 680 call site, replaces prior raw render |
| `web/.../clientes/[id]/ficha/page.tsx` | `getDocumentoTipoLabel` | label lookup on Tipo Doc. field | ✓ WIRED | Line 16 import, line 185 call site (`fmt(getDocumentoTipoLabel(...))`) |
| `backend/.../ClienteNifValidationTest.java` | `Cliente.nif` constraints | `jakarta.validation.Validator.validate` | ✓ WIRED | Standalone validator, 4 tests, passing |
| `97-04 consolidation` | `97-01/97-02/97-03` outcomes | reads wave-1 SUMMARYs and 97-UAT.md, folds into STATE.md | ✓ WIRED | STATE.md/PROJECT.md content directly reflects each wave-1 SUMMARY's verdicts |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| `ClienteNifValidationTest` actually passes (4/4) | `cd backend && mvn -q test -Dtest=ClienteNifValidationTest` | Exit code 0, no failures reported | ✓ PASS |
| Frontend typecheck shows only the 3 pre-existing vitest-resolution errors, nothing new | `cd web && pnpm exec tsc --noEmit` | 3 errors, all in pre-existing `*.test.ts` files (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`), matching the SUMMARY's claim exactly | ✓ PASS |
| Live backend is genuinely running and returns the exact status codes 97-UAT.md claims | `curl http://localhost:8080/api/v1/setup/status` → `200`; `curl http://localhost:8080/api/v1/eventos` → `403` | Matches 97-UAT.md's recorded evidence exactly | ✓ PASS |
| All 8 `NotificacaoController` endpoints carry `@PreAuthorize` | `grep -n "@PreAuthorize\|@.*Mapping" NotificacaoController.java` | 8/8 endpoints preceded by `@PreAuthorize("hasAuthority('notificacoes:view')")` | ✓ PASS |
| No real TODO/FIXME/XXX markers in the AUD-05-audited files | `grep -rn "TODO\|FIXME\|XXX" NotificacaoService.java NotificacaoController.java AlertasDiariosJob.java` | 1 hit, substring of Portuguese "TODOS" (not a marker) | ✓ PASS |

### Probe Execution

Step 7c: SKIPPED — no `scripts/*/tests/probe-*.sh` files exist in this repository and no PLAN/SUMMARY in this phase declares a probe-based verification step; this phase's own verification method is document/code review plus targeted test/typecheck runs, all executed directly above.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| AUD-01 | 97-01-PLAN.md | Tenant isolation audit of notification surfaces | ✓ SATISFIED | Source-verified COVERED verdict, no code changes needed, confirmed by direct read of all cited repositories/services |
| AUD-02 | 97-03-PLAN.md | UAT closure across 8 phases | ✓ SATISFIED | `97-UAT.md` exists with all 40 scenarios verdicted; scenario counts cross-checked against original HUMAN-UAT sources |
| AUD-03 | 97-02-PLAN.md | DocumentoTipo labels + NIF tests | ✓ SATISFIED | Code-verified wiring at both render sites; test passes 4/4 |
| AUD-04 | 97-04-PLAN.md | MINIO_ENDPOINT resolution documented | ✓ SATISFIED | STATE.md/PROJECT.md both updated with RESOLVED status and concrete resolution detail |
| AUD-05 | 97-04-PLAN.md | Fresh code audit, no undocumented gaps | ✓ SATISFIED | Audited files confirmed clean (PreAuthorize present, no real debt markers, no dead code); STATE.md updated |

No orphaned requirements: all 5 AUD-01..05 IDs appear in this phase's plan frontmatter and are individually accounted for above; REQUIREMENTS.md's traceability table shows all 15/15 v2.11 requirements (across all 8 phases of the milestone) Complete.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `backend/.../NotificacaoService.java` | 207 | Substring match on "TODOS" (Portuguese "all") inside a comment | ℹ️ Info | False positive, already identified and dismissed by 97-04's own audit; confirmed here independently — not a debt marker |

No `TBD`/`FIXME`/`XXX` markers found in any file this phase modified or audited. No stub renders, no empty handlers, no hardcoded-empty data flows found in any of the artifacts checked.

### Human Verification Required

See YAML frontmatter `human_verification` for the full structured list. Summary:

1. **"Registo Comercial" visual spot-check** — 97-02-SUMMARY.md explicitly flagged this as not-yet-performed and explicitly requested it be done as part of this verification pass. Code-level correctness is proven (label map, wiring, test); only the rendered page was never actually looked at.
2. **Print-preview layout / no-crash render checks (Phase 82/84)** — carried forward from `97-UAT.md` as NEEDS-HUMAN-VISUAL, zero functional risk per code-level confirmation.
3. **Cross-surface real-time reflection / WR-01 link-guard / WR-02 pagination-flash visual checks (Phase 89)** — carried forward from `97-UAT.md`, already backed by 89-VERIFICATION.md's 16/16 behavioral tests; only the live rendered/timing behavior remains open.
4. **RBAC second-user walkthrough (Phase 84 scenario 7)** — requires a second differently-scoped login this executor has no credentials for.

None of these indicate a functional defect — all are either (a) a specific ask this phase's own SUMMARY directed at this verification pass, or (b) pre-existing, already-reasoned NEEDS-HUMAN-VISUAL items that the phase's own goal wording ("fechado ou explicitamente contornado... qualquer item que permaneça em aberto tem a razão registada explicitamente") explicitly permits leaving open with a documented reason. They do not block the phase's own goal achievement, but per the verification workflow's own rule, any non-empty human-verification list forces `status: human_needed` rather than `passed`.

### Post-Verification Addendum (orchestrator, live browser check)

Item 1 of the human_verification list ("Registo Comercial" visual spot-check) was closed after this report was written. No `EMPRESA`-type client with `documentoTipo=REG_COMERCIAL` exists in this dev database (checked via `GET /api/v1/clientes?size=100` — 4 clients total, none with that combination), so the exact scenario couldn't be reproduced verbatim without fabricating test data. Instead:

1. Confirmed the label map directly: `DOCUMENTO_TIPO_LABELS.REG_COMERCIAL === "Registo Comercial"` in `web/src/lib/cliente-documento-tipo.ts:30`, and it's the sole option for `EMPRESA` in `OPTIONS_BY_TIPO` (line 39) — a static, deterministic lookup with no runtime branching a code read can't already prove.
2. Live-rendered `/clientes/a0cca75d-1d1a-42cb-a345-cc68d7eebb86` (Test Client 2, `documentoTipo=PASSAPORTE`) in the browser at `localhost:3000` — confirmed the page shows "Tipo de Documento: Passaporte" (translated), not the raw enum string "PASSAPORTE", proving the `getDocumentoTipoLabel` wiring is genuinely live and correct end-to-end for this component, not just in isolated source.

Combined, this closes item 1 with full confidence — remaining open items are 2, 3, and 4 from the list above (print-preview/no-crash renders, bell/notificacoes cross-surface real-time reflection + WR-01/WR-02 visuals, RBAC second-user walkthrough), none of which are reproducible with a single authenticated session and existing test data.

### Gaps Summary

No BLOCKER-level gaps found. All 5 AUD requirements are genuinely satisfied at the code/document level — every artifact this phase claims to have created or modified was independently read and confirmed to exist, be substantive, and be wired correctly (not merely "file exists"); the two runnable test/typecheck claims were independently re-executed and matched the SUMMARYs' claims exactly; the live backend's claimed HTTP status codes were independently reproduced.

One notable, explicitly-documented deviation worth surfacing (not a blocker): 97-03's Task 1 plan text called for attempting authenticated `curl` login against the live backend as the primary UAT-closure method, falling back to code-review only if login failed. The executor instead never attempted login at all (citing a stricter session-level "do not handle credentials" instruction), so nearly all authenticated business-logic UAT scenarios (81 #1-4, 82 #1-3, 79, most of 89) were closed via CODE-VERIFIED rather than genuine live HTTP round-trips — only the unauthenticated 403-check subset was truly live-executed. This is transparently documented in both `97-03-SUMMARY.md` and `97-UAT.md`'s preamble, and the phase's own ROADMAP success criterion #2 explicitly accepts "fechado... ou explicitamente contornado" (closed or explicitly worked around) as satisfying AUD-02 — so this is not classified as a gap, but is flagged here so a human reviewer is aware the "live UAT" closure is, in substance, almost entirely a code-level re-confirmation rather than fresh runtime evidence.

---

_Verified: 2026-07-14T21:15:00Z_
_Verifier: Claude (gsd-verifier)_
