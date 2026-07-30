# Phase 121: Fechar Suposições de Tenant Única + Bloqueio de RBAC - Context

**Gathered:** 2026-07-29
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

Three requirements (ISOL-01/02/03), but dedicated codebase research (this session, prior to writing this file) found their actual remaining work is very asymmetric:

- **ISOL-01 is already closed** — Phase 119's CR-02 fix already made `PublicController.getBranding()` return a hardcoded generic `{"LexCV", null}` response with zero tenant-data dependency (confirmed: zero constructor dependencies, no repository references). `findFirstByOrderByCreatedAtAsc` no longer exists anywhere in the codebase (confirmed via full-repo grep — the only 3 hits are historical comments/docblocks, zero live code). `PublicControllerTest.java` already has 2 green tests proving this. **This phase's only ISOL-01 work is a regression confirmation (re-run the existing test), not new code.**
- **ISOL-02 is COVERED** — a dedicated sweep (this session) found zero genuine "resolve-the-tenant-by-heuristic" gaps anywhere in `backend/src/main/java` or `web/src`. Every `findFirst`/`.get(0)`/`findAll()`/hardcoded-`"LexCV"` hit traced to either a legitimate reserved-tenant-by-name lookup, a legitimately-global platform table (`Role`/`Permission`, see ISOL-03), or `PlatformAdminController`'s/`AlertasDiariosJob`'s already-reviewed, already-documented cross-tenant iteration (both explicitly confirmed safe by this milestone's own ROADMAP summary and their own class docblocks). **This phase's ISOL-02 work is producing the audit record itself (a COVERED verdict with evidence), not fixing anything.** One adjacent, pre-existing, already-documented-elsewhere pattern was found (see Deferred) — explicitly NOT this phase's job.
- **ISOL-03 is the one real, currently-exploitable gap**, and the phase's actual center of gravity. `AdminController`'s RBAC endpoints (`GET`/`PUT /api/v1/admin/rbac`) are gated only by a class-level `@PreAuthorize("hasRole('ADMIN')")`. `Role`/`Permission` are flat, platform-wide JPA entities with **no `tenant_id` column at all** (confirmed by reading both entities) — this is intentional (REQUIREMENTS.md's own Out-of-Scope table: RBAC is deliberately becoming platform-fixed, not per-tenant, in v2.16). The mechanical consequence: **any tenant's own ADMIN calling `PUT /api/v1/admin/rbac` today rewrites the exact same `Role` rows every other tenant's `TECNICO`/`ADVOGADO`/`ASSISTENTE` users also depend on.** This is exploitable right now — Phase 120 already made provisioning a real 2nd tenant possible. ROADMAP's own "Risco" note names this the single highest-risk item in the whole v2.16 proposal.

</domain>

<decisions>
## ISOL-01: Confirmation only

- No code change. Plan should include a task that re-reads `PublicController.java` + re-runs `PublicControllerTest` and records the result as this phase's ISOL-01 evidence (mirrors how Phase 120 confirmed pre-existing suspension paths rather than re-building them).

## ISOL-02: Produce the audit record

- No code change for the literal "resolve-the-tenant" concern — zero gaps found.
- Plan should include a task that writes up the sweep as a dedicated audit artifact (e.g. `121-ISOL02-AUDIT.md` or folded into a plan's own SUMMARY — Claude's discretion on exact filename), using the verdict-table format this project already established for this exact kind of check: `.planning/milestones/v2.11-phases/LEXCV-97-.../97-01-SUMMARY.md` (AUD-01, "Tenant-Isolation Audit of Notification Surfaces" — one `| Query/Guard | Scope confirmed | Verdict |` table per surface checked). This gives Phase 123's dedicated audit (ISOL-04) a citable precedent instead of re-deriving the same sweep from scratch.
- **Explicitly out of scope, do not fix here:** a pre-existing, already-documented pattern where several repository methods (`ProcessoRepository.findByClienteId` and similar `findByXxxId`-without-`tenantId` signatures across ~11 methods) rely on every call site separately re-checking the parent entity's tenant before use, rather than the query itself being tenant-scoped. This is a different, older, IDOR-adjacent concern (`.planning/research/PITFALLS.md` Pitfall 1, predates v2.16 by multiple milestones, not newly risky specifically because of a 2nd paying tenant). Note it in the audit write-up as background/context, explicitly flagged as NOT an ISOL-02 finding — Phase 123 owns deciding whether it needs its own fix phase.

## ISOL-03: Lock `PUT /api/v1/admin/rbac`, minimal and literal to the stated success criteria

**Backend — add a method-level override, don't touch the class-level gate:**
- `AdminController` (`backend/src/main/java/com/lexcv/controllers/AdminController.java:28`) keeps its class-level `@PreAuthorize("hasRole('ADMIN')")` — every other method on this large controller (users, clientes/processos/etc. admin actions) is unaffected.
- `updateRbac` (`:391-424`) gets its own method-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`, which Spring Security's most-specific-annotation-wins semantics will correctly evaluate in place of the class-level one for this method only. **This is a first-of-its-kind pattern in this codebase** (research confirmed: every other controller is either class-level-only or method-level-only, never combined) — treat the "does this actually take effect via the real AOP proxy" question as something that needs its own explicit proof, not an assumption by analogy.
- `getRbac` (`:346-389`) is **deliberately left untouched** (still class-level `hasRole('ADMIN')`, tenant ADMIN keeps read access). Reasoning: success criterion 3 names `PUT` specifically, not `GET`; criterion 4 implies the Settings tab still shows tenant ADMIN *something* ("evitando um 403 confuso" reads as: don't let them hit a surprise 403, not: hide the whole tab) — leaving GET as read-only-for-everyone-who-already-could and blocking only the write is the smallest change that satisfies both criteria exactly as worded.
- **Verification approach:** Phase 119 already established a reusable pattern for proving a method-level `@PreAuthorize` actually fires through a real AOP proxy without a full `@SpringBootTest`/MockMvc harness (which this codebase deliberately doesn't have) — `PlatformAdminControllerTest`'s `AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory` approach. Reuse that exact pattern for `updateRbac`, don't invent a new one. Given ROADMAP's own framing of this as the milestone's single highest-risk item, also plan a live-UAT confirmation step (narrower in scope than Phase 120's — one endpoint, not a whole screen) that a real tenant ADMIN session gets a real `403` calling `PUT /admin/rbac`, matching the rigor Phase 120 applied to its own highest-stakes claim.
- Existing regression tests to preserve exactly: `AdminControllerPlataformaAdminContencaoTest`'s Caso 6/7/8 (`getRbac_naoExpoePapelDePlataforma`, `updateRbac_ignoraEntradaPlataformaAdmin`, `updateRbac_continuaAEditarPapeisDeTenant`) call the Java methods directly (bypassing the proxy), so they keep passing regardless of the new annotation — but don't let a refactor of `updateRbac`'s body change what they assert.

**Frontend — hide the Save action from tenant ADMIN, don't build a new PLATAFORMA_ADMIN screen:**
- Success criterion 4 is written entirely as a *removal* ("deixa de expor a ação de gravar a um ADMIN de tenant") — it does not ask for a working RBAC-edit screen for `PLATAFORMA_ADMIN`. Combined with this project's consistently-enforced anti-scope-creep discipline (REQUIREMENTS.md's own Out-of-Scope table, repeated STATE.md decision-log entries rejecting speculative additions), **this phase does not add PLATAFORMA_ADMIN access to the RBAC tab.** If a platform-level RBAC-editing UI is ever wanted, that is a future decision for a future phase, not implied by this phase's literal criteria.
- Concretely: `settings/page.tsx`'s `hasRbacManage` tab-visibility gate (`:58`, `can.manage("rbac") || isAdmin`) is **left unchanged** — a tenant ADMIN still sees the tab and the read-only permission matrix (satisfying "evitando um 403 confuso" by not hiding information, just the broken action).
- The "Guardar Regras" button (currently unconditional inside the tab, `:860-871`) gets a new, separate condition requiring `me?.roles?.includes("PLATAFORMA_ADMIN")` — reusing the exact established pattern from `dashboard-shell.tsx:91` / `plataforma/page.tsx:78-89` (role-array check, not the scoped-permission system, since `PLATAFORMA_ADMIN` intentionally carries zero scoped permissions per Phase 119). Since no `PLATAFORMA_ADMIN` user can currently even see this tab (`hasRbacManage` is false for them today — confirmed by research), the practical effect of this change is: the button disappears for every real user of this tab today (all tenant ADMINs), and reappears for nobody yet, which is exactly what "no longer editable by any tenant" means. This is intentional, not a bug to "fix" by also widening tab visibility.
- Do not touch `GET /admin/rbac`'s frontend call path — it already works for tenant ADMIN and criterion 4 doesn't ask to change read access.

## Claude's Discretion
- Exact filename/location for the ISOL-02 audit write-up.
- Exact shape of the live-UAT step for ISOL-03 (folded into the last plan vs. its own plan) — follow whichever is proportionate once the plan count is known, similar to how Phase 120 dedicated its own final plan/wave to live verification.
- Whether the new `@PreAuthorize` proof test lives in the existing `AdminControllerPlataformaAdminContencaoTest.java` file or a new dedicated file.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PlatformAdminControllerTest`'s `AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory` pattern (Phase 119) — the only proven way in this codebase to test a real `@PreAuthorize` evaluation without a MockMvc/`@SpringBootTest` harness (confirmed: zero `@SpringBootTest`/`@WebMvcTest`/`MockMvc` usage anywhere in the test suite)
- `me?.roles?.includes("PLATAFORMA_ADMIN")` — established role-check pattern (`dashboard-shell.tsx:91`, `plataforma/page.tsx:78-89`), including the `!me.isFetched` fail-closed guard `plataforma/page.tsx` uses (relevant if any new frontend conditional needs the same loading-window discipline WR-03 fixed in Phase 120)
- `.planning/milestones/v2.11-phases/LEXCV-97-.../97-01-SUMMARY.md` — the verdict-table format for a tenant-isolation audit write-up (ISOL-02's deliverable shape)

### Established Patterns
- Method-level `@PreAuthorize` overriding a class-level one is new to this codebase as of this phase — Spring Security supports it natively (most-specific-wins), but there's no existing analog to copy verbatim, unlike most other decisions in this project
- Every existing controller test instantiates the controller directly with Mockito mocks (no AOP proxy, no real security evaluation) except Phase 119's one exception noted above

### Integration Points
- `backend/src/main/java/com/lexcv/controllers/AdminController.java:391` — add `@PreAuthorize` to `updateRbac`
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` — existing tests to preserve, likely home for the new proxy-based proof test
- `web/src/app/(dashboard)/settings/page.tsx:58` (`hasRbacManage`), `:860-871` (Save button) — frontend gate
- `backend/src/main/java/com/lexcv/controllers/PublicController.java`, `backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java` — ISOL-01 regression confirmation only
- `.planning/research/PITFALLS.md` — cite, don't fix, the adjacent IDOR-pattern footnote for ISOL-02

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured above and in ROADMAP.md's own Success Criteria for Phase 121.

</specifics>

<deferred>
## Deferred Ideas

- The `findByXxxId`-without-`tenantId` repository pattern (`PITFALLS.md` Pitfall 1) — real, pre-existing, but a different concern than ISOL-02's literal scope; candidate for Phase 123's dedicated audit (ISOL-04) to explicitly re-confirm or hand off as its own fix item, not this phase's job.
- A working `PLATAFORMA_ADMIN`-facing RBAC-editing UI — not implied by this phase's success criteria (which are framed entirely as removing tenant-ADMIN write access, not adding a platform-admin replacement). Future milestone territory if ever wanted.
- Any change to `GET /api/v1/admin/rbac`'s authorization — success criteria only name `PUT`; read access for tenant ADMIN is unchanged by this phase.

</deferred>
