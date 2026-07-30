---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
plan: 01
subsystem: api
tags: [spring-security, preauthorize, rbac, method-security, aop-proxy, multi-tenant-isolation]

# Dependency graph
requires:
  - phase: 119-fechos-de-plataforma-admin-e-conten-o-de-papel
    provides: "AuthorizationManagerBeforeMethodInterceptor + ProxyFactory AOP-proxy test harness pattern (PlatformAdminControllerTest); PLATAFORMA_ADMIN role seeded unconditionally"
provides:
  - "Method-level @PreAuthorize(\"hasRole('PLATAFORMA_ADMIN')\") on AdminController.updateRbac, overriding the class-level hasRole('ADMIN') for this handler only"
  - "First proven class+method combined @PreAuthorize precedent in this codebase (bidirectional behavioral proof, not analogy)"
  - "AdminControllerRbacAutorizacaoTest — 5-case proxy-based authorization proof, reusable reference for any future controller needing the same override shape"
affects: [121-02, 121-03, 121-04, any future work touching AdminController's authorization]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Method-level @PreAuthorize overriding a class-level one (Spring Security most-specific-wins), proven via AuthorizationManagerBeforeMethodInterceptor + ProxyFactory CGLIB proxy rather than assumed by analogy"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java

key-decisions:
  - "Worded the new authorization comment and the rewritten PAPEL_PLATAFORMA docblock forward-reference entirely in prose, never reproducing the literal hasRole('PLATAFORMA_ADMIN') annotation text — same precedent as Phases 119-04/120-01/120-02 (STATE.md) for self-referential comments tripping grep-based verify gates; keeps the plan's own literal-count assertions (exactly 2 @PreAuthorize lines, 0 adjacent to getRbac) unambiguous"

requirements-completed: [ISOL-03]

# Metrics
duration: 25min
completed: 2026-07-29
---

# Phase 121 Plan 01: Fechar PUT /api/v1/admin/rbac a PLATAFORMA_ADMIN Summary

**Method-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` on `AdminController.updateRbac` overrides the class-level `hasRole('ADMIN')` gate, proven bidirectionally via a real `AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory` CGLIB proxy — not by reading the annotation.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-29
- **Tasks:** 2/2 completed
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- Closed ISOL-03, the single highest-risk item flagged by the v2.16 proposal (ROADMAP Phase 121 Risk note): a tenant `ADMIN` calling `PUT /api/v1/admin/rbac` today rewrote the same global `t_role`/`t_role_permission` rows every other tenant's `TECNICO`/`ADVOGADO`/`ASSISTENTE` users depend on (`Role`/`Permission` have no `tenant_id` column). That write path now requires `PLATAFORMA_ADMIN`.
- First-of-its-kind proof in this codebase that a method-level `@PreAuthorize` genuinely *replaces* (not ANDs with) a class-level one — demonstrated in both directions: a caller who truly satisfies the old class gate (`ROLE_ADMIN`) is now denied; a caller who does **not** satisfy the class gate at all, but holds only `ROLE_PLATAFORMA_ADMIN`, passes through.
- Zero regression: the Phase 119 containment suite (`AdminControllerPlataformaAdminContencaoTest`, 14/14) is green with the file itself byte-for-byte untouched (confirmed via `git status`); full backend suite 177/177; `mvn spotbugs:check` clean.

## Task Commits

Each task was committed atomically:

1. **Task 1: Escrever a prova comportamental primeiro (RED)** - `af396a0` (test)
2. **Task 2: Adicionar o gate de metodo a updateRbac (GREEN)** - `cd19ce5` (feat)

**Plan metadata:** _pending — committed together with this SUMMARY per the atomic close-out protocol_

## Files Created/Modified

- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` - New 5-case proxy-based authorization proof: (1) a `ROLE_ADMIN`-only caller is denied via `AccessDeniedException` through the proxy; (2) a `ROLE_PLATAFORMA_ADMIN`-only caller (deliberately lacking `ROLE_ADMIN`) passes and reaches `roleRepository.save`; (3) reflection confirms `updateRbac`'s own `@PreAuthorize` value is exactly `hasRole('PLATAFORMA_ADMIN')`; (4) reflection confirms `getRbac` still carries no method-level annotation; (5) reflection confirms the class-level annotation is still exactly `hasRole('ADMIN')`.
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - Added `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` immediately above `updateRbac`'s `@PutMapping("/rbac")` (matching the `NotificacaoController`/`ResourceController` ordering convention), with a short explanatory comment above it; reworded the `PAPEL_PLATAFORMA` docblock's forward-reference (previously "Phase 121 will later close...") to describe the closure as done. `updateRbac`'s body, `getRbac`, and the class-level annotation are unchanged.

## Decisions Made

- Worded the new comment and the rewritten docblock forward-reference entirely in prose, deliberately never reproducing the literal `hasRole('PLATAFORMA_ADMIN')` annotation string — same precedent already established in this project (STATE.md Phases 119-04/120-01/120-02: self-referential comments that spell out a literal being verify-gated can trip that same grep-based assertion). Keeps the plan's own acceptance checks (exact count of 2 `@PreAuthorize` lines anchored at line-start, zero `PreAuthorize` on the line before `@GetMapping("/rbac")`) unambiguous.

## Deviations from Plan

None - plan executed exactly as written. Every file, method name, annotation placement, and test case matched the plan's `<interfaces>` and `121-PATTERNS.md` contracts exactly as pre-confirmed during planning; no bugs, missing functionality, or blockers were encountered that required Rule 1-4 intervention.

## Issues Encountered

- One `mvn test` invocation (run through a `tee`/`grep` pipeline while gathering summary lines) showed a failure in `com.lexcv.jobs.AlertasDiariosJobTest#executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa` — a test in an entirely unrelated package (`com.lexcv.jobs`, not touched by this plan) that this plan's changes cannot affect (this plan only touches `AdminController.java` and its own new test file). A clean, direct re-run of the full suite immediately after passed 177/177 with `BUILD SUCCESS`, including that same test class at 11/11. Not reproduced a second time; not investigated further per the scope-boundary rule (pre-existing/unrelated file), and not filed as a deferred item since it did not reproduce.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- ISOL-03 (this plan's sole requirement) is closed at the code level and proven via a real AOP proxy, not by reading the annotation. ROADMAP Phase 121 Success Criterion 3 ("`PUT /api/v1/admin/rbac` deixa de aceitar chamadas de um `ADMIN` de tenant") is satisfied.
- **Intentional, documented asymmetry:** a `PLATAFORMA_ADMIN` caller can now successfully call `PUT /api/v1/admin/rbac`, but will still receive `403` on `GET /api/v1/admin/rbac` — `getRbac` was deliberately left with only the class-level `hasRole('ADMIN')` gate per `121-CONTEXT.md`'s explicit decision (the ROADMAP success criteria name only `PUT`; `GET` read access for tenant `ADMIN` is unchanged by this phase). Any future plan that wants a working `PLATAFORMA_ADMIN`-facing RBAC read/edit screen will need to separately address `GET`'s gate — out of scope here.
- Frontend Save-button gating (`settings/page.tsx`'s `RbacTab`) and the ISOL-01/ISOL-02 audit/regression work are scoped to other plans in this phase (121-02/121-03/121-04 exist in the phase directory) — not part of this plan.
- Ready for the next plan in Phase 121.

---
*Phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java`
- FOUND: `backend/src/main/java/com/lexcv/controllers/AdminController.java`
- FOUND: `af396a0` (test commit, in `git log --oneline --all`)
- FOUND: `cd19ce5` (feat commit, in `git log --oneline --all`)
- TDD gate sequence confirmed: `test(121-01)` (af396a0) precedes `feat(121-01)` (cd19ce5); no REFACTOR commit needed (change was already minimal).
