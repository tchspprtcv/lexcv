---
phase: LEXCV-125-moldes-da-plataforma-e-provisionamento
verified: 2026-09-21T01:25:04Z
status: human_needed
score: 4/4 must-haves verified (automated/code evidence) — live confirmation owed
overrides_applied: 0
human_verification:
  - test: "MOLD-02 — consultar e editar moldes em /plataforma/moldes (ROADMAP SC1)"
    expected: "A matriz mostra os 4 moldes (ADMIN, ADVOGADO, TECNICO, ASSISTENTE) como colunas, PLATAFORMA_ADMIN nunca aparece, permissões agrupadas por módulo com rótulo legível; editar e gravar funciona ponta-a-ponta contra um backend real."
    why_human: "Requer backend + PostgreSQL a correr e um browser real; código e testes Mockito/proxy AOP provam o contrato, não o comportamento visual/rede ao vivo."
  - test: "MOLD-04 — criar um molde novo em /plataforma/moldes (ROADMAP SC2)"
    expected: "Nome normalizado para maiúsculas, 5ª coluna aparece na matriz com badge '0 escritórios', tentativa de criar 'plataforma_admin' (qualquer caixa) é recusada com a mensagem exata do contrato."
    why_human: "Mesmo motivo — ponta-a-ponta contra API real."
  - test: "MOLD-01 — provisionar um escritório novo e confirmar em SQL que recebeu cópia de cada molde (ROADMAP SC3)"
    expected: "SELECT nome, molde_id, sistema FROM t_tenant_role WHERE tenant_id = '<novo>' devolve uma linha por molde instanciável, todas com sistema=true e molde_id preenchido; a tenant reservada 'ALCv' continua sem nenhuma linha em t_tenant_role."
    why_human: "Exige uma base de dados real e uma consulta SQL contra dados persistidos por um provisionamento ao vivo — os testes Mockito provam a chamada e os campos do objeto, não a persistência real."
  - test: "MOLD-03 — editar um molde já instanciado e confirmar que o papel copiado não muda (ROADMAP SC4, o mais importante)"
    expected: "Depois de editar e gravar um molde com escritórios já instanciados, a contagem de permissões do TenantRole do escritório existente (via SQL) permanece exactamente igual à de antes da edição."
    why_human: "Mesmo motivo — os testes unitários provam a independência de instância do Set em memória (assertNotSame + mutação pós-facto), mas a garantia final tem de ser observada contra linhas reais em t_tenant_role_permission."
  - test: "Gate de autoridade ao vivo — ADMIN de escritório tentando /plataforma/moldes"
    expected: "Ecrã de acesso negado no browser; pedido de rede a GET /api/v1/platform/moldes devolve HTTP 403, não 200 com dados."
    why_human: "Confirma o código de estado HTTP real observado no separador de rede do browser, distinto da prova por proxy AOP em testes unitários."
  - test: "Acessibilidade da matriz com o inspector do browser"
    expected: "th de coluna com scope=\"col\"; célula de rótulo de linha é th scope=\"row\" com texto alinhado à esquerda; um checkbox qualquer tem aria-label no formato \"Ver Clientes — ADVOGADO\"."
    why_human: "Inspecção de DOM renderizado, não apenas do código-fonte JSX."
---

# Phase 125: Moldes da Plataforma e Provisionamento Verification Report

**Phase Goal:** A plataforma passa a gerir os moldes de papel numa consola própria, e todo o mecanismo de "instanciar cópia de um molde" nasce e é provado primeiro no caminho de menor risco — um escritório novo — antes de ser reutilizado pela migração.
**Verified:** 2026-09-21T01:25:04Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `PLATAFORMA_ADMIN` consulta e edita os moldes de papel existentes através da consola `/plataforma` (MOLD-02) | ✓ VERIFIED (code) | `GET/PUT /api/v1/platform/moldes` in `PlatformAdminController.java:229-305`, class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`; frontend `web/src/app/(dashboard)/plataforma/moldes/page.tsx` renders and edits the matrix; `PlatformAdminControllerMoldesTest` (23 tests, all pass) proves the real `@PreAuthorize` gate via AOP proxy for tenant-ADMIN, PLATAFORMA_ADMIN and unauthenticated callers. Live browser/network confirmation owed (see Human Verification). |
| 2 | `PLATAFORMA_ADMIN` cria um molde novo, disponível para instanciação em escritórios provisionados a partir desse momento (MOLD-04) | ✓ VERIFIED (code) | `POST /api/v1/platform/moldes` (`PlatformAdminController.java:313-361`) creates `Role.instanciavel=true`, rejects `PLATAFORMA_ADMIN` (any case) with the exact contract message, rejects duplicate names (pre-check + `DataIntegrityViolationException` race fallback). `CriarMoldePanel` wired via `useCreateMolde`. Tests: `createMolde_normalizaNomeParaMaiusculasEGravaComInstanciavelTrue`, `createMolde_recusaPlataformaAdminEmQualquerCaixaComMensagemExataENuncaGrava`, `createMolde_convertDataIntegrityViolationExceptionEmDuplicacaoConcorrenteEm400` all pass. Live confirmation owed. |
| 3 | Provisionar um escritório novo instancia automaticamente uma cópia própria de cada molde actual, pronta a ser atribuída de imediato (MOLD-01) | ✓ VERIFIED (code) | `SetupService.provisionTenant` (`SetupService.java:117-146`) calls `instanciarMoldes(tenant.getId())` inside the **same** `@Transactional` method — no separate `@Transactional` on `instanciarMoldes`, so a failure rolls back tenant+admin+roles together. `instanciarMoldes` (line 150-181) reads `roleRepository.findAllByInstanciavelTrue()` and builds one `TenantRole` per molde with `sistema=true`, `moldeId` set, `permissions=new HashSet<>(molde.getPermissions())`. Proven by `SetupServiceInstanciacaoMoldesTest` Caso 1 (fields correct), Caso 3 (no moldes → no TenantRole writes), Caso 6 (save failure propagates, not silenced). The reserved "ALCv" tenant is created exclusively by `DatabaseSeeder.seedTenantPlataforma()` (`Tenant.builder().nome("ALCv").build()`), which never calls `provisionTenant`/`instanciarMoldes` — confirmed by grep and by the scope note in the test file header. Live SQL confirmation against a real provisioned tenant is owed. |
| 4 | Editar um molde depois de já ter sido instanciado não tem qualquer efeito sobre os papéis já copiados (snapshot, não referência viva) (MOLD-03) | ✓ VERIFIED (code) | `TenantRole.java`: `moldeId` is a bare `Integer` column (`@Column(name = "molde_id")`), **zero** `@ManyToOne`/`@OneToOne`/cascade annotations anywhere in the file (grep-confirmed). `instanciarMoldes` copies with `new HashSet<>(molde.getPermissions())` — a genuinely new collection, not a shared reference. `updateMoldes` (`PlatformAdminController.java:250-305`) never reads or writes `TenantRoleRepository` except for the read-only count used to re-project the response. `SetupServiceInstanciacaoMoldesTest.instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva` (Caso 2) is the strongest proof in the phase: it captures the instantiated `TenantRole`, then mutates the **origin molde's** `Set` after instantiation (add+remove), and asserts the captured copy's size/contents are unchanged — this is a genuine post-hoc mutation test, not a same-reference assertion. Live SQL confirmation against a real edited molde + already-provisioned office is owed (the phase's most important guarantee). |

**Score:** 4/4 truths pass all automated/code-level evidence. All 4 have an owed, deliberately-deferred live confirmation step (see Human Verification below) — this is why status is `human_needed`, not `passed`.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Role.java` | `instanciavel` flag, default false | ✓ VERIFIED | `columnDefinition="boolean not null default false"` + `@Builder.Default` |
| `backend/src/main/java/com/lexcv/models/TenantRole.java` | Snapshot entity, non-navigable `moldeId` | ✓ VERIFIED | Bare `Integer moldeId`, no JPA association; `@ManyToMany` own `permissions` set with `@JoinTable` to `t_tenant_role_permission` |
| `backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java` | `countByMoldeId`, tenant-scoped lookups | ✓ VERIFIED | Present, used by controller and by `SetupService` |
| `backend/src/main/java/com/lexcv/services/SetupService.java` | `provisionTenant` + `instanciarMoldes` in same transaction | ✓ VERIFIED | Single `@Transactional` method calling a private helper with no transaction boundary of its own |
| `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` | `GET/PUT/POST /platform/moldes`, class-level PLATAFORMA_ADMIN gate | ✓ VERIFIED | Inherited class gate, 3-layer PLATAFORMA_ADMIN containment (SQL filter + defensive name check + response filter) |
| `backend/migrations/126-add-tenant-role-tables.sql` | Idempotent manual migration, no FK to `t_role` | ✓ VERIFIED | `IF NOT EXISTS` guards throughout, no `FOREIGN KEY`/`REFERENCES t_role`, cataloged in `backend/migrations/README.md` |
| `web/src/app/(dashboard)/plataforma/moldes/page.tsx` | Matrix, 3-layer non-propagation warning, accessibility | ✓ VERIFIED | Banner (persistent), per-column badge, `AlertDialog` confirming only changed moldes; `scope="col"`, `<th scope="row">` + `text-left`, per-checkbox `aria-label` |
| `web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx` | Inline creation panel | ✓ VERIFIED | Zod-validated form, wired to `useCreateMolde` |
| `web/src/hooks/use-platform-moldes.ts` | TanStack Query hooks | ✓ VERIFIED | `useMoldes`/`useUpdateMoldes`/`useCreateMolde`, cache invalidation (no full-page reload) |
| `web/src/types/platform-moldes.ts` | Types mirroring backend contract | ✓ VERIFIED | Matches `MoldesConsolaResponse`/`MoldeCreateRequest`/`MoldesUpdateRequest` DTOs field-for-field |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `SetupService.provisionTenant` | `TenantRoleRepository.save` | `instanciarMoldes` (same tx) | WIRED | No separate `@Transactional`; exception propagates and rolls back tenant+user+roles together (Caso 6) |
| `plataforma/page.tsx` "Gerir Moldes" button | `/plataforma/moldes` route | `<Link href="/plataforma/moldes">` | WIRED | Line 170-172 of `plataforma/page.tsx` |
| `moldes/page.tsx` | `GET /api/v1/platform/moldes` | `useMoldes()` → `apiFetch` | WIRED | Populates matrix and per-molde `escritoriosInstanciados` badge |
| `moldes/page.tsx` "Confirmar e Gravar" | `PUT /api/v1/platform/moldes` | `useUpdateMoldes().mutateAsync` inside `AlertDialogAction` only | WIRED | The blue "Guardar Alterações" button only opens the dialog; the mutation is exclusively triggered from the dialog's confirm action |
| `criar-molde-panel.tsx` submit | `POST /api/v1/platform/moldes` | `useCreateMolde().mutateAsync` via `onSubmit` prop | WIRED | Panel is presentational; page.tsx owns the mutation/toast/close |
| `t_user_role` (authority resolution) | — | untouched | WIRED (fence held) | `git diff` across all Phase 125 commits shows zero changes to `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `User.java`, `AdminController.java`; `provisionTenant` still assigns `Set.of(adminRole)` where `adminRole` is the **global** `ADMIN` role (`roleRepository.findByNome("ADMIN")`), proven by `assertSame(adminRoleGlobal, papelAtribuido)` in `SetupServiceInstanciacaoMoldesTest` Caso 5 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `moldes/page.tsx` matrix | `data.moldes` / `data.permissoes` | `GET /platform/moldes` → `montarConsolaResponse()` → `roleRepository.findAllByInstanciavelTrue()` / `permissionRepository.findAllByReservadaPlataformaFalse()` | Yes — real repository queries, no static/empty return | ✓ FLOWING |
| Per-column badge `escritoriosInstanciados` | `molde.escritoriosInstanciados` | `tenantRoleRepository.countByMoldeId(role.getId())` | Yes — live `COUNT` per molde | ✓ FLOWING |
| Confirmation dialog "N escritórios já têm..." | `moldesAlterados[].escritoriosInstanciados` | Same field, filtered client-side by diff against `appliedData` | Yes — derived from the same live count | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED (backend/PostgreSQL/MinIO must not be started per explicit instruction in this verification run; behavior is instead confirmed via the Mockito/AOP-proxy test suites listed above, which exercise the real Spring Security interceptor and the real service method bodies against captured arguments).

### Probe Execution

No `scripts/*/tests/probe-*.sh` or phase-declared probes found for Phase 125 (`find . -path "*/tests/probe-*.sh"` empty; no probe references in the phase's PLAN/SUMMARY files). Step 7c: N/A.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|--------------|--------|----------|
| MOLD-01 | 125-02, 125-06 | Escritório novo nasce com moldes instanciados como cópia própria | ✓ SATISFIED | `SetupService.instanciarMoldes`, `SetupServiceInstanciacaoMoldesTest` |
| MOLD-02 | 125-03, 125-04, 125-05, 125-06 | `PLATAFORMA_ADMIN` consulta e edita moldes em `/plataforma` | ✓ SATISFIED | `GET/PUT /platform/moldes`, `moldes/page.tsx` |
| MOLD-03 | 125-01, 125-02, 125-03, 125-05, 125-06 | Editar molde não altera papel já instanciado | ✓ SATISFIED | `TenantRole.moldeId` non-navigable, snapshot copy, `updateMoldes` never writes `TenantRoleRepository` |
| MOLD-04 | 125-03, 125-04, 125-06 | `PLATAFORMA_ADMIN` cria molde novo | ✓ SATISFIED | `POST /platform/moldes`, `CriarMoldePanel` |

No orphaned requirements — `REQUIREMENTS.md`'s Moldes e Provisionamento section (MOLD-01..04) maps 1:1 to what plans across the phase declared. (Note: the checkbox markers in `REQUIREMENTS.md` for MOLD-01..04 are still unchecked `[ ]` at time of verification — a documentation-sync gap, not a code gap; ROADMAP.md already marks Phase 125 "Complete".)

### Anti-Patterns Found

None. Scanned all files created/modified by this phase (models, repositories, `SetupService`, `PlatformAdminController`, `DatabaseSeeder`, `moldes/page.tsx`, `criar-molde-panel.tsx`, hooks, types, schemas) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`, empty-return stubs, and hardcoded-empty props. Zero matches (one grep false-positive on the Portuguese word "TODOS" inside a doc-comment, not a marker).

### Authority-Resolution Fence (explicit check per task instructions)

- `git diff` across the full Phase 125 commit range (`9d6dad06`..`7f07d3f8`) touches zero lines in `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `User.java`, `AdminController.java`.
- `t_user_role` / `User.roles` still resolve against the global `Role` entity — untouched.
- `PUT /api/v1/admin/rbac` unchanged; still gated as before Phase 125 (CATL-04/`rbac:manage` is explicitly Phase 127 scope).
- `SetupService.provisionTenant` assigns the initial admin user `Set.of(adminRole)` where `adminRole` comes from `roleRepository.findByNome("ADMIN")` (the **global** role) — never the instantiated `TenantRole`. Proven with `assertSame` (not just `assertEquals`) in `SetupServiceInstanciacaoMoldesTest`.
- **Fence held.** No breach found.

### PLATAFORMA_ADMIN Containment (explicit check per task instructions)

Three independent backend layers plus one frontend layer, each proven by a distinct test:
1. `Role.instanciavel` seeded `false` for `PLATAFORMA_ADMIN` unconditionally on every boot (`DatabaseSeeder.java:483`).
2. SQL-level exclusion via `findAllByInstanciavelTrue()` in both `instanciarMoldes` and `montarConsolaResponse`.
3. Defensive literal-name checks in `instanciarMoldes` (`SetupService.java`), `updateMoldes`, `createMolde`, and `montarConsolaResponse` (`PlatformAdminController.java`) — each proven with a regression scenario where the SQL filter is deliberately made to misbehave in the test double (`instanciarMoldes_plataformaAdminNuncaEInstanciado`, `listMoldes_nuncaListaPlataformaAdminMesmoQueOFiltroSqlRegredisse`, `updateMoldes_comPlataformaAdminDevolve400ENuncaGrava`, `createMolde_recusaPlataformaAdminEmQualquerCaixaComMensagemExataENuncaGrava`).
4. Frontend defense-in-depth filter (`NOME_RESERVADO_PLATAFORMA` in `moldes/page.tsx`).

### Reserved "ALCv" Tenant (explicit check per task instructions)

`DatabaseSeeder.seedTenantPlataforma()` builds the "ALCv" tenant directly (`Tenant.builder().nome("ALCv").build()`) and never calls `provisionTenant`/`instanciarMoldes`; it has no dependency on `TenantRoleRepository`. Confirmed by grep and by the explicit scope note in `SetupServiceInstanciacaoMoldesTest`'s class doc-comment. By construction, not by a runtime guard — this matches the CONTEXT.md decision.

### Health Gates

| Gate | Command | Result |
|------|---------|--------|
| Backend tests | `mvn test` (JDK 23) | **232/232 passing**, BUILD SUCCESS |
| SpotBugs/FindSecBugs | `mvn spotbugs:check` | 0 findings |
| TypeScript | `pnpm exec tsc --noEmit` | Clean, no errors |
| ESLint | `pnpm lint` | 0 errors, 19 pre-existing warnings unrelated to Phase 125 (React Compiler incompatible-library notices, `<img>` vs `next/image`, one unused var in an unrelated file) |
| Frontend build | `pnpm build` | Success — `/plataforma/moldes` present in route manifest as a static route |
| `verify:juizo-origem` | `pnpm run verify:juizo-origem` | PASS |
| `verify:limite-utilizadores` | `pnpm run verify:limite-utilizadores` | PASS (9/9 assertions) |
| `verify:consola-tenants` | `pnpm run verify:consola-tenants` | PASS (12/12) — previously red, repaired by commit `7f07d3f8` |
| `verify:bloqueio-rbac` | `pnpm run verify:bloqueio-rbac` | PASS (12/12) — previously red, repaired by commit `7f07d3f8` |
| `verify:relatorio-utilizacao` | `pnpm run verify:relatorio-utilizacao` | PASS (15/15) — literal updated from absent `"LexCV"` to present `"ALCv"`; confirmed this strengthens rather than weakens the assertion (it was previously passing for the wrong reason) |
| `verify:consola-moldes` | `pnpm run verify:consola-moldes` | PASS (15/15) |

All six `verify:*` gates green. The `web/scripts/verify-*.mjs` repair commit (`7f07d3f8`) was reviewed directly: it only swaps the stale `(LexCV)` literal for the current `(ALCv)` literal in three gate scripts, changes no predicate logic, and — for `verify:relatorio-utilizacao` — changes the check from "does not contain an absent string" (vacuous pass) to "does not contain the current live string" (a real assertion). No assertion was weakened.

### Human Verification Required

The phase's closing plan (125-06, Task 3) contains a `checkpoint:human-verify` gate with a 16-point live checklist (backend+frontend running, browser interaction, SQL queries against `t_tenant_role`/`t_tenant_role_permission`, and an HTTP status check) covering exactly the 4 ROADMAP success criteria plus the authority-fence and accessibility checks. It was **not** executed because this phase ran inside an autonomous run with no human present, and is honestly recorded as pending in `125-06-SUMMARY.md` (not fabricated `CONFIRMADO` verdicts). No `125-HUMAN-UAT.md` file exists yet.

All automated/code-level evidence fully covers the 4 ROADMAP success criteria (see Observable Truths above, all 4 ✓ VERIFIED). The items below are the live confirmation still owed, harvested from the deferred checkpoint (see YAML frontmatter `human_verification` for full detail per item):

1. **MOLD-02 end-to-end** — consult/edit moldes against a real running backend.
2. **MOLD-04 end-to-end** — create a molde against a real running backend, including the reserved-name rejection path.
3. **MOLD-01 via SQL** — provision a real tenant and confirm `t_tenant_role` rows via `SELECT`.
4. **MOLD-03 via SQL** — edit an already-instantiated molde and confirm the existing office's copy is unchanged via `SELECT`.
5. **Authority gate via HTTP** — confirm a tenant ADMIN gets a real 403 from the network tab, not just from a unit-test proxy.
6. **Matrix accessibility via DOM inspector** — confirm `scope="col"`/`scope="row"`/`aria-label` render as authored.

### Gaps Summary

No code-level gaps found. All 4 ROADMAP success criteria for Phase 125 are backed by real, non-stub implementation: the snapshot invariant (MOLD-03) — the single most important guarantee of this phase — is proven by a genuine post-hoc-mutation test, not a same-reference check that could pass by accident; the authority-resolution fence (no change to `t_user_role`, `JwtAuthenticationFilter`, `UserPrincipal`, `User`, `AdminController`, and the initial admin user still receiving the **global** `ADMIN` role) is confirmed by an empty `git diff` on those files across the whole phase and by an `assertSame` test; `PLATAFORMA_ADMIN` containment holds across 3 backend layers + 1 frontend layer, each with its own regression test; and the reserved `ALCv` tenant is excluded by construction (it never calls the instantiation path), not by an easily-bypassed guard.

The reason `status` is `human_needed` rather than `passed` is solely the deliberately-deferred 16-point live UAT checklist (browser + SQL + HTTP), which the executor correctly declined to fabricate. This is recorded as owed, not as a delivery gap — per the explicit instruction accompanying this verification, since it is honestly disclosed in `125-06-SUMMARY.md` and does not indicate any missing or stubbed implementation.

One pre-existing (unrelated to Phase 125) documentation-sync note: `REQUIREMENTS.md` still shows `[ ]` unchecked boxes for MOLD-01..04 even though `ROADMAP.md` marks Phase 125 complete — cosmetic, not a code gap.

---

*Verified: 2026-09-21T01:25:04Z*
*Verifier: Claude (gsd-verifier)*
