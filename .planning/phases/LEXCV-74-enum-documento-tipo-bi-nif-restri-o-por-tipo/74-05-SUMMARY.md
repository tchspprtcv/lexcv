---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
plan: 05
subsystem: api
tags: [spring-boot, validation, cliente, documento-tipo]

# Dependency graph
requires:
  - phase: 74-04
    provides: "Frontend buildClienteFormSchema Zod exemption for legacy documento_tipo (UX-only convenience, not authoritative)"
provides:
  - "Server-side documentoTipoUnchanged exemption in updateCliente that skips isDocumentoTipoValidoParaTipo only when the incoming documentoTipo AND documentoNumero are byte-for-byte identical to the stored entity"
affects: [clientes, cliente-editar]

# Tech tracking
tech-stack:
  added: []
  patterns: ["diff-against-stored-entity exemption for legacy-data tolerance on update endpoints"]

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "Exemption compares incoming payload against the tenant-scoped entity already fetched at the not-found guard (line 262), not against a client-asserted flag -- the server's own persisted state is the sole source of truth for what counts as 'unchanged legacy'."
  - "createCliente left completely untouched -- there is no stored entity to diff against on create, so it stays unconditionally strict per 74-CONTEXT.md line 26."
  - "No JUnit/Spring test harness added -- backend/src/test does not exist in this project and the full Spring context requires a live PostgreSQL per CLAUDE.md; verification uses a static branch-trace fallback (see Verification section), consistent with 74-04's precedent when a runner was unavailable."

patterns-established:
  - "Pattern: Objects.equals(stored.getField(), payload.getField()) across all diff-sensitive fields, ANDed together, to gate an existing validation call with '!unchanged && !isValid(...)' -- reusable for any future legacy-tolerance-on-edit requirement."

requirements-completed: [CLI-24]

# Metrics
duration: 12min
completed: 2026-07-04
---

# Phase 74 Plan 05: Backend legacy documento_tipo update exemption Summary

**updateCliente now skips per-tipo documento_tipo/documento_numero validation only when both fields are resent byte-for-byte unchanged from the stored entity, closing the last Phase 74 gap (Truth #10 / CLI-24) so the "guarde sem alterar este campo" banner promise holds end-to-end.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-04T00:00:00Z (approx, single-session execution)
- **Completed:** 2026-07-04
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- Added a `documentoTipoUnchanged` boolean guard in `updateCliente` built from `Objects.equals` on both `documentoTipo` and `documentoNumero` against the fetched `cliente` entity.
- Wrapped the existing `isDocumentoTipoValidoParaTipo` call so it only runs when the field actually changed, closing the backend half of the CLI-24 gap (the frontend half was closed in 74-04).
- Confirmed `createCliente` and `isDocumentoTipoValidoParaTipo`'s body remain byte-for-byte unchanged -- create path stays unconditionally strict.
- Backend compiles clean (`mvn -q -DskipTests compile` exits 0).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add unchanged-value exemption to updateCliente validation** - `be4f788` (fix)
2. **Task 2: Prove the exemption with end-to-end/static verification** - no source commit (verification-only task, recorded below; confirmed via `git status --short` clean after this task)

**Plan metadata:** this SUMMARY.md commit (docs)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `updateCliente` now computes `documentoTipoUnchanged` (Objects.equals on documentoTipo + documentoNumero vs. the fetched entity) and guards the `isDocumentoTipoValidoParaTipo` call with `!documentoTipoUnchanged &&`. `createCliente` and the private validator's body are untouched.

## Decisions Made
- Compared incoming payload against the tenant-scoped entity already fetched by the not-found guard rather than introducing any new lookup or client-supplied "was legacy" flag -- keeps the tenant-isolation guarantee intact (see Threat Model T-74-05-02 in the plan) and gives no new attack surface for smuggling a genuinely new invalid value (T-74-05-01).
- Did not scaffold a JUnit/Spring integration test: this repo has no `backend/src/test` directory, and a full Spring context needs a live PostgreSQL per `CLAUDE.md`'s backend setup requirements. Scaffolding a new test harness for a one-line guard was explicitly called out of scope in the plan. Used the plan's documented fallback (static branch-trace) instead, matching the precedent set by 74-04's SUMMARY when its runner was similarly unavailable.

## Deviations from Plan

None - plan executed exactly as written. Task 2's plan explicitly anticipated the "no live backend/DB reachable" branch and pre-authorized the static-trace fallback; this environment had no `backend/.env`, no reachable Docker daemon, and nothing listening on port 8080, so that documented fallback was used rather than being an improvised deviation.

## Issues Encountered

**Environment has no live backend/PostgreSQL available for the curl-based verification described in Task 2(a)/(b)/(c):**
- `backend/.env` does not exist in this worktree.
- Docker Desktop's Linux engine pipe is not reachable (`docker ps` fails with "failed to connect to the docker API").
- Nothing is listening on `localhost:8080` (`curl` to `/api/v1/auth/login` timed out / connection refused).

Per the plan's explicit fallback instruction, this was recorded here and the static branch-trace below was used as the verification evidence instead of live curl output.

## Verification

**No live backend/DB reachable in this environment** (see Issues Encountered above) — using the plan's documented static branch-trace fallback. The trace below is against the actual committed code (`backend/src/main/java/com/lexcv/controllers/ResourceController.java`, lines 259-305 post-fix):

```java
boolean documentoTipoUnchanged = java.util.Objects.equals(cliente.getDocumentoTipo(), payload.getDocumentoTipo())
        && java.util.Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero());

if (!documentoTipoUnchanged && !isDocumentoTipoValidoParaTipo(payload.getTipo(), payload.getDocumentoTipo(), payload.getDocumentoNumero())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "Tipo de documento inválido para o tipo de cliente selecionado"));
}
```

**Case (a) — unchanged legacy → expect HTTP 200:**
Setup: stored `cliente` has `tipo=PARTICULAR`, `documentoTipo=REG_COMERCIAL`, `documentoNumero="X"` (simulating pre-existing legacy data now invalid for PARTICULAR under Phase 74's per-tipo rules). PUT payload resends `documentoTipo=REG_COMERCIAL`, `documentoNumero="X"` unchanged.
Trace: `Objects.equals(cliente.getDocumentoTipo(), payload.getDocumentoTipo())` → `Objects.equals(REG_COMERCIAL, REG_COMERCIAL)` → `true`. `Objects.equals(cliente.getDocumentoNumero(), payload.getDocumentoNumero())` → `Objects.equals("X", "X")` → `true`. Both `true` → `documentoTipoUnchanged = true`. The `if` condition is `!true && ...` → short-circuits to `false` — `isDocumentoTipoValidoParaTipo` is **never called**. Execution falls through to the field copies and `clienteRepository.save(cliente)`, returning `ResponseEntity.ok(saved)` → **HTTP 200**. Matches expected outcome.

**Case (b) — changed to still-invalid → expect HTTP 400:**
Setup (variant 1, documentoTipo changed): same stored cliente as (a). PUT payload changes `documentoTipo` to `CNI` (still invalid for a value that would need to stay REG_COMERCIAL-tolerant, or any other value not equal to the stored one) while `tipo` stays `PARTICULAR`.
Trace: `Objects.equals(REG_COMERCIAL, CNI)` → `false` → `documentoTipoUnchanged = false` regardless of the documentoNumero comparison (short-circuit `&&`). The `if` condition becomes `!false && !isDocumentoTipoValidoParaTipo(...)` → `true && !isDocumentoTipoValidoParaTipo("PARTICULAR", CNI, "X")`. Since `CNI` is actually in the PARTICULAR-allowed set (`{CNI, BI, PASSAPORTE}`), this specific value would pass — so to exercise the true-400 path, use a documentoTipo change that is genuinely invalid for the tipo, e.g. changing to `REG_COMERCIAL` while `tipo` is being kept `PARTICULAR` is the ambiguous case; the plan's literal case is best exercised as: stored `documentoTipo=BI` (valid, non-legacy), PUT changes to `REG_COMERCIAL` for a `PARTICULAR` client → `Objects.equals(BI, REG_COMERCIAL)=false` → `documentoTipoUnchanged=false` → validation runs → `isDocumentoTipoValidoParaTipo("PARTICULAR", REG_COMERCIAL, ...)` → `Set.of(CNI,BI,PASSAPORTE).contains(REG_COMERCIAL)` → `false` → `!false` → `true` → **HTTP 400** with `"Tipo de documento inválido para o tipo de cliente selecionado"`. Matches expected outcome.
Setup (variant 2, documentoNumero changed only): stored `documentoTipo=REG_COMERCIAL`/`documentoNumero="X"` (legacy on PARTICULAR). PUT keeps `documentoTipo=REG_COMERCIAL` but changes `documentoNumero` to `"Y"`.
Trace: `Objects.equals(REG_COMERCIAL, REG_COMERCIAL)=true` but `Objects.equals("X","Y")=false` → AND short-circuits to `documentoTipoUnchanged=false` → validation runs → `isDocumentoTipoValidoParaTipo("PARTICULAR", REG_COMERCIAL, "Y")` → `REG_COMERCIAL` not in `{CNI,BI,PASSAPORTE}` → `false` → **HTTP 400**. Matches expected outcome (an actively-edited documentoNumero forfeits the legacy tolerance even though documentoTipo itself didn't change).

**Case (c) — create path unaffected → expect HTTP 400:**
`createCliente` (lines 218-247) was not modified by this plan. Its guard remains:
```java
if (!isDocumentoTipoValidoParaTipo(cliente.getTipo(), cliente.getDocumentoTipo(), cliente.getDocumentoNumero())) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)...
}
```
unconditional, with no `documentoTipoUnchanged`-style guard (confirmed by `grep -n "documentoTipoUnchanged" ResourceController.java` returning only the two occurrences inside `updateCliente`, lines 271 and 274 — none inside `createCliente`, lines 218-247). POST `/api/v1/clientes` with `tipo=PARTICULAR`, `documentoTipo=REG_COMERCIAL` → `isDocumentoTipoValidoParaTipo` returns `false` → **HTTP 400**. Matches expected outcome; create path fully strict, no regression.

**Static verification commands executed (recorded, not curl):**
```
cd backend && mvn -q -DskipTests compile   → exit 0
grep -n "documentoTipoUnchanged" ResourceController.java
  → 271:  boolean documentoTipoUnchanged = java.util.Objects.equals(...)
  → 274:  if (!documentoTipoUnchanged && !isDocumentoTipoValidoParaTipo(...))
grep -c "documentoTipoUnchanged" ResourceController.java  → 2 (unchanged after Task 2, no source edits)
```

Summary: unchanged→200 (traced), changed-invalid→400 (traced, both documentoTipo-change and documentoNumero-change variants), create-invalid→400 (traced, code path confirmed untouched). All three required outcomes are demonstrated by direct code inspection of the committed diff.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 74's sole remaining gap (74-VERIFICATION.md Truth #10 / CLI-24) is closed on the backend side; combined with 74-04's frontend fix, the "guarde sem alterar este campo para manter o valor legado" banner promise now holds end-to-end (client Zod exemption + server Objects.equals exemption both agree on when to tolerate legacy documento_tipo).
- Recommended (non-blocking) follow-up for a future testing phase: once `backend/src/test` exists and a live PostgreSQL is available in CI/dev, add a `@SpringBootTest` or `@WebMvcTest` covering the three cases traced above (unchanged→200, changed→400, create-invalid→400) as permanent regression protection — currently there is no automated test for this guard, only this static trace and the 74-05-PLAN.md behavior spec.
- No blockers for milestone v2.8 to proceed to Phase 75.

---
*Phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo*
*Completed: 2026-07-04*
