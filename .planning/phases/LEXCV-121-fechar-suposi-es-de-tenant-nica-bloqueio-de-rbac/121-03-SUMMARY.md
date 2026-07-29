---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
plan: 03
subsystem: api
tags: [tenant-isolation, security-audit, rbac, audit-only]

# Dependency graph
requires:
  - phase: 121-01
    provides: "Method-level @PreAuthorize(\"hasRole('PLATAFORMA_ADMIN')\") on AdminController.updateRbac, cited as an already-shipped source fact in the ISOL-02 Role/Permission verdict row"
provides:
  - "121-ISOL-AUDIT.md — reproducible tenant-isolation audit record closing ISOL-01 (regression confirmation) and ISOL-02 (verdict-by-surface sweep), zero code changes"
affects: [123-isol-04-auditoria-de-isolamento]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Audit verdict-table format (Query/Guard | Scope confirmed | Verdict) reused verbatim from 97-01-SUMMARY.md (AUD-01) for a second time in this project"

key-files:
  created:
    - .planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-ISOL-AUDIT.md
  modified: []

key-decisions:
  - "Did not stop-and-escalate to the orchestrator when 2 of Task 1's 4 literal grep-based acceptance checks returned non-zero where the plan expected 0 (grep -c 'Repository' on PublicController.java = 1, not 0; grep -cE 'findFirstBy|findTopBy' on TenantRepository.java = 2, not 0) — direct code reading confirmed both are false positives from an overly broad grep pattern coincidentally matching (a) the class's own historical-reference Javadoc comment and (b) a distinct, legitimate, Phase-119-reviewed findFirstByNome(String) exact-name lookup, not a reintroduction of the removed findFirstByOrderByCreatedAtAsc heuristic. Documented transparently in 121-ISOL-AUDIT.md rather than treated as an ISOL-01 regression."
  - "Classified 4 web/src '?? \"LexCV\"' fallback-default call sites (dashboard-shell.tsx, ficha/page.tsx, termo-honorarios/page.tsx) and 1 backend ResourceController.executarTransicao .findFirst() plus 1 movs.get(0) hit as a 4th, explicit 'unrelated to tenant resolution' outcome rather than force-fitting them into the plan's 3 prescribed outcomes (a/b/c) — none of them determine which tenant's data is served, so none are ISOL-02 candidates in the first place"

requirements-completed: [ISOL-01, ISOL-02]

# Metrics
duration: ~20min
completed: 2026-07-29
---

# Phase 121 Plan 03: Auditoria ISOL-01/ISOL-02 (registo, sem código) Summary

**`121-ISOL-AUDIT.md` (116 linhas) confirma ISOL-01 por execução de teste + 4 pesquisas independentes, e fecha ISOL-02 com uma tabela de veredito de 15 linhas (todas COVERED, zero FIXED) sobre 5 famílias de padrão varridas em `backend/src/main/java` e `web/src` — zero alterações de código.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-29T21:47:17Z
- **Tasks:** 2/2 completed
- **Files modified:** 1 (criado; nenhum ficheiro de código alterado)

## Accomplishments

- ISOL-01 confirmado por **execução**, não por leitura: `mvn test -Dtest=PublicControllerTest` → `Tests run: 2, Failures: 0, Errors: 0` (`BUILD SUCCESS`), mais 4 pesquisas independentes registadas literalmente.
- ISOL-02 re-varrido do zero, por comando explícito (não copiado de `121-CONTEXT.md`), cobrindo `findFirstBy`/`findTopBy`, `.findAll()`, `.get(0)`/`stream().findFirst()`, o literal `"LexCV"`, e `findFirstByOrderByCreatedAtAsc` — sobre `backend/src/main/java` **e** `web/src`. Resultado: 15 superfícies traçadas, todas `COVERED`, zero achados reais.
- Produzido `121-ISOL-AUDIT.md`, reutilizando o formato de tabela de veredito de `97-01-SUMMARY.md` (AUD-01), com secção de comandos de reprodução para a Phase 123 (ISOL-04) poder re-executar a varredura sem a redesenhar.
- Registado, como contexto de fundo explicitamente fora de âmbito, o padrão `findByXxxId`-sem-`tenantId` (`PITFALLS.md` Pitfall 1) e a assimetria intencional `GET`/`PUT` de `/admin/rbac` deixada pelo Plan 01 — ambos com dono nomeado (Phase 123) para não serem redescobertos como surpresa.

## Task Commits

1. **Task 1: Confirmar ISOL-01 por execução, não por leitura** — sem commit (zero ficheiros alterados; task de recolha de evidência pura, evidência reutilizada diretamente na Task 2, mesmo padrão de `97-01-SUMMARY.md`: "a task commit is only made when files are modified")
2. **Task 2: Re-correr a varredura ISOL-02 e escrever 121-ISOL-AUDIT.md** — `3618157` (docs)

**Plan metadata:** _pending — committed together with STATE.md/ROADMAP.md/REQUIREMENTS.md per the atomic close-out protocol_

## Files Created/Modified

- `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-ISOL-AUDIT.md` - Registo de auditoria ISOL-01/ISOL-02: secção de confirmação ISOL-01 (4 evidências), tabela de veredito ISOL-02 (15 linhas, todas `COVERED`), comandos de reprodução para as 5 famílias de padrão, secção "fora de âmbito" citando `PITFALLS.md` Pitfall 1, e secção da assimetria `GET`/`PUT` de `/admin/rbac`.

## Decisions Made

- **Não escalado ao orquestrador** apesar de 2 dos 4 checks automatizados literais da Task 1 devolverem valores diferentes de `0` como o plano esperava (`grep -c 'Repository'` = `1`; `grep -cE 'findFirstBy|findTopBy'` = `2`). Confirmado por leitura direta do código, em ambos os casos, que a causa é uma correspondência de grep genérica demasiado ampla — não uma regressão de ISOL-01:
  - `Repository` (1 hit): a mesma linha 15 do docblock de `PublicController.java`, que documenta em prosa histórica o método removido `TenantRepository.findFirstByOrderByCreatedAtAsc()` — texto de comentário, não código vivo.
  - `findFirstBy|findTopBy` (2 hits): ambos referem-se a `TenantRepository.findFirstByNome(String nome)` — um método distinto, parametrizado, de procura por nome exato da tenant reservada "LexCV" (Phase 119, WR-01/`119-REVIEW.md`), não a assinatura removida.
  Este é o mesmo padrão já documentado várias vezes em `STATE.md` (Phases 119-04, 120-01, 120-02, 121-01): comentário/código auto-referencial a colidir com um gate de verificação baseado em grep literal. Documentado com total transparência em `121-ISOL-AUDIT.md` (secção ISOL-01, "Nota de transparência"), incluindo os valores literais exatos, em vez de reportado como divergência da premissa da fase.
- Duas superfícies do `.get(0)`/`stream().findFirst()` (backend, `ResourceController.java`) e as 6 ocorrências frontend do literal `"LexCV"` que não são o `plataforma/columns.tsx` (4 fallbacks de UI + 1 metadata de `<title>`) foram classificadas como um 4º desfecho explícito — "não relacionado com resolução de tenant" — em vez de forçadas nas 3 categorias prescritas pelo plano (a/b/c), porque nenhuma delas decide qual tenant é servido.

## Deviations from Plan

None in the Rule 1-4 sense — zero code was changed, zero bugs fixed, zero missing functionality added, zero architectural question raised. See "Issues Encountered" below for a verification-command nuance found and resolved via direct code inspection, and "Decisions Made" above for the reasoning.

## Issues Encountered

- **Task 1's automated verify block (and the plan-level `<verification>` steps 3-4) contain 2 literal grep-count assertions that do not hold exactly as written**, both for the same underlying, already-understood, non-regressive reason (see "Decisions Made"):
  - `grep -c 'Repository' backend/src/main/java/com/lexcv/controllers/PublicController.java` → literal `1`, not `0` (docblock historical reference).
  - `grep -cE 'findFirstBy|findTopBy' backend/src/main/java/com/lexcv/repositories/TenantRepository.java` → literal `2`, not `0` (legitimate `findFirstByNome`).
  Both were investigated by direct file reading (not just re-running the grep), confirmed as false positives of an overly broad literal pattern rather than a code regression, and documented in full inside `121-ISOL-AUDIT.md` itself so the discrepancy is auditable rather than silently swept aside. All other literal checks in both tasks (including the combined `<verify><automated>` block for Task 2, re-run after a one-word header fix — see below) pass exactly as specified.
  - **Task 2's own automated verify block initially failed** on the `grep -q 'Comandos de reproducao'` check: the first draft of `121-ISOL-AUDIT.md` used correctly-accented Portuguese ("Comandos de reprodução"), which does not literally contain the plan's own unaccented spelling. Fixed by renaming that one section header to the unaccented literal form the plan specifies (matching this project's established convention of PLAN.md files being written without diacritics); re-ran the full combined verify command afterward — passes clean (`TASK 2 COMBINED VERIFY: PASS`).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- ISOL-01 and ISOL-02 (this plan's two requirements) are both closed. Combined with 121-01 (ISOL-03, already merged), all three ISOL requirements this phase owns are now closed at the source-of-truth level.
- `121-ISOL-AUDIT.md` is ready for Phase 123 (ISOL-04) to cite directly — verdict table, reproduction commands, and the two explicitly-out-of-scope background items (`PITFALLS.md` Pitfall 1; the `/admin/rbac` GET/PUT asymmetry) are all in place so that phase does not need to re-derive this sweep from scratch.
- Remaining work in Phase 121: plan 121-04 (if any; per `.planning/STATE.md`, Phase 121 has 4 plans total, this is plan 3 of 4).

---
*Phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-ISOL-AUDIT.md`
- FOUND: `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-03-SUMMARY.md`
- FOUND: `3618157` (docs commit, in `git log --oneline --all`)
- Re-ran all acceptance criteria for both tasks: mvn test 2/2 green; `findFirstByOrderByCreatedAtAsc` = 2 hits (both comments); `121-ISOL-AUDIT.md` has 15 `| COVERED` rows (>= 8 required), 0 `| FIXED`; contains `PITFALLS.md` reference and the literal `Comandos de reproducao` section header; `git status --porcelain -- backend web` empty throughout.
