---
phase: LEXCV-90-spotbugs-sast-commit-e-verifica-o
fixed_at: 2026-07-13T14:05:00Z
review_path: .planning/phases/LEXCV-90-spotbugs-sast-commit-e-verifica-o/90-REVIEW.md
iteration: 3
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-90: Code Review Fix Report

**Fixed at:** 2026-07-13T14:05:00Z
**Source review:** .planning/phases/LEXCV-90-spotbugs-sast-commit-e-verifica-o/90-REVIEW.md
**Iteration:** 3

**Summary (this iteration):**
- Findings in scope: 3 (0 critical, 3 warning — `fix_scope: critical_warning`; Info findings IN-01..IN-06 excluded)
- Fixed: 3
- Skipped: 0

This is the third and final iteration of the auto-fix loop for this phase. The iteration-3 review independently re-verified all four iteration-2 findings (CR-01, WR-01, WR-02, WR-03) as still correctly fixed with no regressions, and surfaced three new warnings — all instances of the "fix applied to one path, sibling path left open" pattern: `updateCliente` lacked the `documentoNumero` uniqueness check added only to `createCliente`; `mergeClientes`'s data migration missed the `ParecerSolicitacao.clienteId` FK; and `updateHonorario` had no validation around its `valorTotal` parsing. All three were fixed in this pass, applied inside an isolated git worktree, verified (Tier 1 re-read plus a full `mvn -o -DskipTests compile` after every edit — `BUILD SUCCESS` throughout, no new errors), committed atomically per finding, then fast-forwarded onto `master` during cleanup. No frontend changes were required for any of the three findings.

## Fixed Issues (iteration 3)

### WR-01: `updateCliente` has no `documentoNumero` uniqueness check, unlike `createCliente` — an uncaught `DataIntegrityViolationException` surfaces as a generic 500 with a raw exception message

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 590e677
**Applied fix:** Added a `documentoNumeroChanged` check (comparing the stored value against the payload's) mirroring `createCliente`'s existing check: if the value changed to a non-null number already used by another client in the same tenant (`clienteRepository.findByTenantIdAndDocumentoNumero`), the request now returns `409 CONFLICT` with `"Já existe um cliente com este número de documento"` before any setters run — closing the same constraint-exposure gap iteration-2's WR-03 fix left open for the update path.

### WR-02: `mergeClientes` still doesn't migrate `ParecerSolicitacao.clienteId` — the same orphaned-FK defect class CR-01 fixed for `Documento`/`ClienteAdvogado`/`ClienteAdministrativo`, left open for a different table

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`
**Commit:** a31c201
**Applied fix:** Added `ParecerSolicitacaoRepository.findByTenantIdAndClienteId(tenantId, clienteId)` and wired `ParecerSolicitacaoRepository` into `ResourceController` (Lombok `@RequiredArgsConstructor` picks up the new final field automatically; confirmed no test directly instantiates `ResourceController` with positional constructor args). `mergeClientes` now migrates every `ParecerSolicitacao` referencing `secondaryId` onto `savedPrimary.getId()` before the secondary `Cliente` row is deleted, and reports the count as `moved_pareceres` in the response map alongside the existing `moved_*`/`merged_saldo` fields, for the same operator-visibility reason CR-01 added `moved_documentos`. `ParecerSolicitacao.java` and `ParecerController.java` were read for context (field name, nullability, existing tenant-scoping convention) but intentionally not modified — they were cited by the review as load-bearing but out of this finding's file scope.

### WR-03: `updateHonorario` doesn't validate `valorTotal` before parsing — a malformed value throws an uncaught `NumberFormatException`, surfaced as a generic 500

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** c4a0e61
**Applied fix:** Wrapped the `new BigDecimal(body.get("valorTotal").toString())` parse in a `try/catch (NumberFormatException)` mirroring the adjacent `dataAcordo` handling, returning `400` with `"valorTotal inválido"` instead of letting the exception propagate to the generic `500` catch-all. Also added a non-negative check (`valorTotal.compareTo(BigDecimal.ZERO) < 0` → `400` `"valorTotal não pode ser negativo"`), addressing the review's explicit note that no such check existed, consistent with the `CR-04` convention already applied to `Pagamento.valorPago`.

## Skipped Issues

None — all in-scope findings were fixed.

---

## Previous Iterations (context, not re-verified changes)

Iteration 1 fixed 9 findings (4 critical, 5 warning) from the original review; iteration 2 independently re-verified all 9 as still correctly fixed (one regression found and fixed: WR-02) and fixed 4 more findings (1 critical, 3 warning). Iteration 3 (this report) independently re-verified all 4 iteration-2 findings as still correctly fixed with no regressions, and fixed the 3 new warnings it surfaced. Full history:

**Iteration 1** (commits `a9dc00d`..`c0f8ce4`):
- CR-01 (`a9dc00d`): added `@Transactional` to `mergeClientes`.
- CR-02 (`cd4e9a7`): fixed TOCTOU race in `createCliente`'s `numeroSequencial` assignment; added DB unique constraint.
- CR-03 (`87e3ac0`): added tenant-ownership validation for `Processo.clienteId` on create/update.
- CR-04 (`1399703`): rejected null/non-positive `valorPago` before persisting `Pagamento`.
- WR-01 (`169f128`): closed `MultipartFile` `InputStream`s via try-with-resources.
- WR-02 (`1460121`): reordered storage-object deletion to occur only after DB commit.
- WR-03 (`366a206`): returned `400` for missing `processoId`/`honorarioId` in request body.
- WR-04 (`5b62b8d`): corrected `spotbugs-exclude.xml` audit-trail description mismatch.
- WR-05 (`c0f8ce4`): narrowed broad `catch (Exception)` to `catch (DataAccessException)` around conta-corrente updates; guarded null `valorPago` in `deletePagamento`.

**Iteration 2** (commits `69965f8`..`126079e`):
- CR-01 (`69965f8`): `mergeClientes` no longer discards the secondary's account balance or orphans its documents/lawyer-assignment links; added `moved_documentos`/`merged_saldo` to the response and surfaced them in the frontend hook/page.
- WR-01 (`ffcadae`): `createProcesso`/`createProcessoIntake`/`createParte` now validate presence of required (`nullable = false`) fields before persisting.
- WR-02 (`2289e10`): `updateProcesso` no longer nulls the mandatory `clienteId` FK when the payload omits it (regression from iteration-1's own CR-03 fix); made `clienteId` presence mandatory so the tenant-ownership check can no longer be bypassed. **Flagged for human verification** per the logic-bug limitation — confirm `PUT /processos/{id}` with `clienteId` omitted/null returns `400` and never persists, and that a cross-tenant `clienteId` still correctly returns `400`.
- WR-03 (`126079e`): `Cliente.nome` now carries `@NotBlank`; added `ClienteRepository.findByTenantIdAndDocumentoNumero` and an explicit uniqueness check in `createCliente` (`409`) so the `DataIntegrityViolationException` catch around `save()` is reserved for the `numero_sequencial` race.

**Iteration 3** (commits `590e677`, `c4a0e61`, `a31c201`): see "Fixed Issues (iteration 3)" above.

## Remaining Known Items (out of `critical_warning` scope, not fixed by any iteration)

The following Info-level findings were carried forward unfixed across all three review iterations (out of `fix_scope: critical_warning`) and remain outstanding for the user's awareness — no further automated re-review will run after this iteration:

- **IN-01:** `UserPrincipal.getRoles()`/`getPermissions()` expose the live mutable `Set` instead of an unmodifiable view (`UserPrincipal.java:19-25, 64-67`).
- **IN-02:** Hardcoded ADMIN permission list in `UserPrincipal.java:34-47` duplicates `DatabaseSeeder.seedRbac()`'s `permKeys` list — currently in sync but drift-prone.
- **IN-03:** Misleading no-op `default -> { break; }` in `listEventos`'s recurrence-expansion loop (`ResourceController.java:2439-2448`).
- **IN-04:** Magic number `3600` (presigned URL TTL) duplicated at `ResourceController.java:420` and `:2795`.
- **IN-05:** `spotbugs-maven-plugin`/`dependency-check-maven` are configured in `backend/pom.xml:146-170` but not bound to any lifecycle phase or CI step — `.github/workflows/deploy.yml` runs no `mvn test`/`spotbugs:check`/`dependency-check:check` gating `build-and-push`.
- **IN-06 (new this iteration):** `mergeClientes`'s response doesn't report how many `ClienteAdvogado`/`ClienteAdministrativo` links were migrated vs. dropped as duplicates (`ResourceController.java:872-898`; `web/src/hooks/use-clientes.ts:129-139`; `web/src/app/(dashboard)/clientes/merge/page.tsx:52-58`) — the same visibility gap CR-01 closed for `ContaCorrente`/`Documento`.

---

_Fixed: 2026-07-13T14:05:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
