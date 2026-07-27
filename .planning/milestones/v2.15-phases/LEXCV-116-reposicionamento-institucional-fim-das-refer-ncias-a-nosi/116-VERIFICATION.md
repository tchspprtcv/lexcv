---
phase: 116-reposicionamento-institucional-fim-das-referencias-a-nosi
verified: 2026-07-27T22:15:00Z
status: passed
score: 7/7 must-haves verified
overrides_applied: 0
---

# Phase 116: Reposicionamento Institucional — Fim das Referências a NOSi Verification Report

**Phase Goal:** O LexCV deixa de se descrever, em toda a sua superfície viva (documentação de projeto, landing pública, documentação técnica de referência e dados de demonstração seedados), como ligado à NOSi — passando a referenciar corretamente o SIJ (Sistema Judicial de Cabo Verde) como o seu ecossistema-alvo.
**Verified:** 2026-07-27T22:15:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Visitante anónimo lê, no card "Ecossistema Cabo Verde" da landing pública, uma descrição que referencia o SIJ e nunca a NOSi | ✓ VERIFIED | `webpage/src/components/trust-section.tsx:8` — `desc: "Desenhado para a realidade institucional cabo-verdiana, alinhado ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)."`. Título `"Ecossistema Cabo Verde"` e ícone `Building2` preservados. `grep -iF "nosi"` no ficheiro → 0 matches. |
| 2 | Secção "What This Is" de `.planning/PROJECT.md` descreve o LexCV alinhado ao ecossistema do SIJ, sem mencionar NOSi | ✓ VERIFIED | `awk '/^## What This Is/{f=1} /^## Core Value/{f=0} f' .planning/PROJECT.md` → `grep -iF "nosi"` = 0 matches; `grep -cF "ecossistema do SIJ (Sistema Judicial de Cabo Verde)"` dentro da secção = 1. Linha 5: "...alinhada ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)...". |
| 3 | Parágrafo 1 de `.trae/documents/SPEC.md` descreve o produto com foco no ecossistema do SIJ, sem mencionar NOSi | ✓ VERIFIED | `.trae/documents/SPEC.md:4` — "...com foco no ecossistema do SIJ (Sistema Judicial de Cabo Verde)." Resto da frase ("desenvolvida pela Speed Tech") intacto. `grep -iF "nosi"` no ficheiro → 0 matches. |
| 4 | Instalação nova que corra o seed cria tenant de demonstração com identidade genérica, sem nenhum campo associado a NOSi — incluindo o `nome` servido publicamente por `GET /api/v1/public/branding` | ✓ VERIFIED | `DatabaseSeeder.java:64-70` — `.nome("Gabinete Jurídico Demonstração")`, `.nif("000000000")`, `.tipoEntidade("PRIVADO")`, `.email("contacto@lexcv.cv")`, `.telefone("+238 200 0000")`. `PublicController.java:58-64` confirma `t.getNome()` copiado sem condição para `TenantPublicInfoResponse.nome()`, endpoint não autenticado, apenas `nome`+`logoDataUrl` expostos. `mvn -DskipTests compile` → exit 0. |
| 5 | Nenhuma superfície viva do produto (`backend/src`, `webpage/src`, `web/src` excl. mock legado, `.trae/documents`) contém "nosi" em qualquer capitalização | ✓ VERIFIED | `grep -rniE "nosi" backend/src webpage/src web/src .trae/documents \| grep -v "web/src/server/mock-db.ts"` → 0 matches. Varrimento adicional adversarial (docker-compose*.yml, `backend/**/*.sql`\|`*.yml`\|`*.properties`, outros seeders/fixtures) → 0 matches adicionais. |
| 6 | As três formulações institucionais (PROJECT.md, trust-section.tsx, SPEC.md) usam a mesma expressão canónica "ecossistema do SIJ (Sistema Judicial de Cabo Verde)" | ✓ VERIFIED | `grep -cF "ecossistema do SIJ (Sistema Judicial de Cabo Verde)"` → `SPEC.md:1`, `trust-section.tsx:1`, `PROJECT.md:2` (2 esperado: linha 5 "What This Is" + linha 13 frase Goal da milestone, ambas legítimas). Substring byte-idêntica nos 3 ficheiros. |
| 7 | Ficheiros fora de âmbito (`web/src/server/mock-db.ts`, `.figma/`) permanecem por tocar | ✓ VERIFIED | `git status --porcelain .figma/ .planning/milestones/ .planning/RETROSPECTIVE.md web/src/server/mock-db.ts backend/migrations/` → vazio. `mock-db.ts:190` ainda contém `nome: "NOSi (Demonstração)"` literal, inalterado. |

**Score:** 7/7 truths verified

**ROADMAP Success Criteria cross-check** (SIJ-01..04, all 4 subsumed by truths above): all 4 independently confirmed — SIJ-01↔truth 2, SIJ-02↔truth 1, SIJ-03↔truth 3, SIJ-04↔truth 4.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `webpage/src/components/trust-section.tsx` | Contains `"alinhado ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)."` | ✓ VERIFIED | Line 8, exact match (`grep -cF` = 1). `git diff --numstat` = 1 insertion/1 deletion (commit `204f138`). No other line touched. |
| `.trae/documents/SPEC.md` | Contains `"com foco no ecossistema do SIJ (Sistema Judicial de Cabo Verde)."` | ✓ VERIFIED | Line 4, exact match (`grep -cF` = 1). `git diff --numstat` = 1 insertion/1 deletion (commit `204f138`). |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` | Contains `.tipoEntidade("PRIVADO")` | ✓ VERIFIED | Line 67, plus 4 sibling literal changes (nome/nif/email/telefone) confirmed at lines 65-69. `git diff --numstat` = 5 insertions/5 deletions (commit `226e89e`). `mvn compile` exit 0. |
| `.planning/PROJECT.md` | Contains `"ecossistema do SIJ (Sistema Judicial de Cabo Verde)"` in "What This Is" | ✓ VERIFIED | Line 5, pre-existing from milestone opening (before this phase), reverified formally by Task 3's section-scoped assertion. File not modified by this phase's commits (correct — SIJ-01 was already satisfied). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `DatabaseSeeder.java` `Tenant.builder().nome(...)` | `GET /api/v1/public/branding` (`PublicController`) | `t.getNome()` → `TenantPublicInfoResponse.nome()` | ✓ WIRED | `PublicController.java:58-64` — unconditional getter-to-builder copy, no auth gate (`@RequestMapping("/api/v1/public")`), only `nome`+`logoDataUrl` exposed (`nif`/`email`/`telefone` structurally excluded from the DTO). Note: PLAN documents the path as `GET /api/v1/branding`; actual mapping is `GET /api/v1/public/branding` — cosmetic label discrepancy in plan text only, does not affect verified wiring. |
| `trust-section.tsx` array `CONFIANCA` | `<CardDescription>{desc}</CardDescription>` | `CONFIANCA.map` inside `TrustSection()` | ✓ WIRED | Lines 22-27 — direct JSX child render, React auto-escaping, no `dangerouslySetInnerHTML`. |
| `.planning/PROJECT.md` "What This Is" | `.trae/documents/SPEC.md` §1 + `trust-section.tsx` | Expressão canónica idêntica | ✓ WIRED | Byte-identical substring `"ecossistema do SIJ (Sistema Judicial de Cabo Verde)"` confirmed present verbatim in all 3 documents via `grep -cF`. |

### Data-Flow Trace (Level 4)

This phase changes only literal/static content — no new DB queries, API calls, or state management were introduced. Trace scope: confirm the one genuinely dynamic path (seeded literal → persisted row → HTTP response) is a direct, unconditional copy with no transformation/caching layer that could reintroduce stale data, and that the static marketing copy is exactly what will render (no build-time templating).

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `PublicController.getBranding()` | `t.getNome()` | `DatabaseSeeder` literal → `tenantRepository.save()` → `findFirstByOrderByCreatedAtAsc()` | Yes — deterministic literal, no fallback/mock branch | ✓ FLOWING |
| `TrustSection()` (`trust-section.tsx`) | `desc` (from `CONFIANCA` array) | Hardcoded literal in the same file (static marketing copy, by design — not DB/API-driven) | Yes — static content is the intended source | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with new seed literals | `cd backend && mvn -q -DskipTests compile` | exit 0, no output | ✓ PASS |
| Frontend public app (`webpage/`) lints clean | `cd webpage && pnpm lint` | exit 0; 0 errors, 1 pre-existing unrelated warning (`@next/next/no-img-element` in `brand-mark.tsx`, outside this phase's files) | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` declared or found; phase is a pure content/data-literal correction (no migration/CLI/tooling probes applicable). Confirmed via repo search (`find . -path "*/scripts/*/tests/probe-*.sh"` → 0 results) and PLAN/SUMMARY text scan (no probe references).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| SIJ-01 | 116-01-PLAN.md | PROJECT.md descreve o LexCV sem "NOSi", referenciando o SIJ | ✓ SATISFIED | Truth 2 above; section-scoped `awk` assertion clean, canonical phrase present |
| SIJ-02 | 116-01-PLAN.md | Landing pública (`trust-section.tsx`) sem NOSi no card "Ecossistema Cabo Verde" | ✓ SATISFIED | Truth 1 above; line 8 verified |
| SIJ-03 | 116-01-PLAN.md | `.trae/documents/SPEC.md` sem "foco no ecossistema do NOSi" | ✓ SATISFIED | Truth 3 above; line 4 verified |
| SIJ-04 | 116-01-PLAN.md | Tenant seed deixa de representar a NOSi (nome/NIF/email genéricos) | ✓ SATISFIED | Truth 4 above; 5 fields verified generalized (nome/nif/tipoEntidade/email/telefone — 2 fields beyond the literal REQUIREMENTS.md wording, an explicitly user-approved extension per `116-CONTEXT.md`), compiles clean |

**Orphaned requirements check:** `REQUIREMENTS.md` Traceability table maps SIJ-01, SIJ-02, SIJ-03, SIJ-04 exclusively to Phase 116 — identical to the PLAN's `requirements:` frontmatter field. 4/4 mapped, 0 unmapped, 0 orphaned.

### Anti-Patterns Found

None in the 3 modified files. Checked: `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers (0 matches), "coming soon"/"not yet implemented" phrasing (0 matches), mojibake/encoding corruption (UTF-8-as-Latin-1 corruption patterns such as `Ã©`, `Ã§`, `Ã£`, and a raw UTF-8 BOM rendered as text — 0 matches), empty-implementation patterns (n/a — no logic touched).

**Informational observations (non-blocking, no BLOCKER/WARNING found):**

| # | Observation | Impact |
|---|-------------|--------|
| 1 | `.planning/PROJECT.md` "### Active" checklist (lines 110-113) still shows unchecked `- [ ]` for the 4 SIJ items, even though SIJ-01..04 are now complete per `REQUIREMENTS.md`. | None on phase goal — Task 3 explicitly scoped these lines as untouched ("os 4 itens do checklist `### Active`... nenhuma pode ser removida por esta tarefa"). Milestone-closing bookkeeping, handled outside this PLAN. |
| 2 | `.planning/ROADMAP.md` line 20 milestone banner still reads "(construction emoji) v2.15 Reposicionamento SIJ — Phase 116 (in progress)" and line 422 has stale "Run `/gsd:execute-phase 116`" text, despite the Progress table (line 420) already showing "1/1 Complete". | None on phase goal — pre-existing GSD roadmap-bookkeeping lag, resolved by the phase-closing workflow step, not a code defect. |
| 3 | `deploy_key.pub` (repo root) SSH key comment contains hostname `govcv\francisco.horta@RAI-GV-NOSI-194`. | None — correctly identified by SUMMARY as non-actionable; not product copy, not within any in-scope surface (`backend/src`, `webpage/src`, `web/src`, `.trae/documents`). Confirmed present, confirmed out of scope. |
| 4 | Stale local git worktrees under `.claude/worktrees/*` (untracked — excluded via `.git/info/exclude`, confirmed via `git check-ignore -v` and `git ls-files` returning empty) contain pre-phase copies of `trust-section.tsx`/`mock-db.ts` with old "NOSi" text. | None — ephemeral local dev artifacts from unrelated, already-merged background-agent sessions (`zealous-wilson`, `jolly-allen`, etc. per git log); not git-tracked, not part of any build output, outside phase scope. Flagged for repo hygiene awareness only. |
| 5 | PLAN's key_link documents the branding endpoint as `GET /api/v1/branding`; actual Spring mapping is `GET /api/v1/public/branding` (`@RequestMapping("/api/v1/public")` + `@GetMapping("/branding")`). | None — cosmetic label mismatch in plan documentation only; the verified field-copy wiring/behavior is unaffected. |

### Human Verification Required

None. Every truth in this phase resolves to a deterministic, statically-verifiable fact (literal string content, `git diff --numstat` line-count proof, compile/lint exit codes, direct source-level wiring trace). The only public-facing surface touched (`trust-section.tsx`) had zero JSX/structural/CSS changes — confirmed via `git diff --numstat` = exactly 1 insertion/1 deletion on the single targeted line — so there is no visual-rendering risk that code inspection cannot already resolve with full confidence.

### Gaps Summary

No gaps. All 7 must-have truths (superset of the 4 ROADMAP Success Criteria SIJ-01..04) are VERIFIED with direct file-content, `git diff`, `git log`/`git show`, and compile/lint evidence — not SUMMARY.md claims taken at face value. Commits `204f138` and `226e89e` were independently inspected via `git show --stat` and match the SUMMARY's description exactly (2 files/2 lines; 1 file/10 lines respectively). Adversarial repo-wide sweeps beyond the plan's declared scope (docker-compose files, other backend seed/fixture sources, full-repo case-insensitive "nosi" search across `.md`/`.ts`/`.tsx`/`.java`/`.yml`/`.json`/`.properties`) found zero additional residues in any live product surface — the only hits outside the phase's declared out-of-scope list were false positives from the unrelated English word "diagnosis" inside GSD tooling docs (`.claude/`, `.agent/`, `.trae/get-shit-done/` etc., which are development-tooling files, not product surface) and stale untracked local worktree checkouts (item 4 above).

---

_Verified: 2026-07-27T22:15:00Z_
_Verifier: Claude (gsd-verifier)_
