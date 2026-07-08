---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
fixed_at: 2026-07-08T21:49:55Z
review_path: .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 86: Code Review Fix Report

**Fixed at:** 2026-07-08T21:49:55Z
**Source review:** .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (0 critical, 5 warning — `fix_scope: critical_warning`; the 5 Info findings were left out of scope by design)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01: `NotificacaoService.criar()` — the sole write chokepoint — performs no validation of its own inputs

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`, `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Commit:** `1b095af`
**Applied fix:** Extended the reviewer's suggested snippet to cover all three gaps named in the Issue text, not just the two shown in the abbreviated code sample:
- Null-check on `tenantId`/`destinatarioId`, plus a tenant-ownership check via `userRepository.findById(destinatarioId).filter(u -> tenantId.equals(u.getTenantId()))`, throwing `IllegalArgumentException` on mismatch (the exact snippet given in REVIEW.md).
- Added (beyond the literal snippet, since the DB schema confirmed exactly which fields are `NOT NULL`): blank-checks on `categoria`/`titulo`/`mensagem`/`entidadeTipo`/`entidadeId` via a `requireNonBlank` helper.
- Added length checks against the `VARCHAR(255)` columns (`categoria`, `titulo`, `entidadeTipo`, `entidadeId`, `linkUrl`) via a `requireMaxLength` helper.

**Necessary follow-on change:** the new `userRepository.findById(...)` call inside `criar()` would have broken two pre-existing tests (`criar_doisDestinatariosDistintos_...` and `notificarComFanOutAdmin_...`), which call `criar()`/`notificarAdmins()` directly without stubbing that method — Mockito's default answer for an unstubbed `Optional`-returning method is `Optional.empty()`, which would trip the new `orElseThrow`. Added the required `userRepository.findById(...)` stubs (and `tenantId(TENANT_ID)` on the `admin1`/`admin2` test fixtures) to keep both tests passing under the stricter validation. This is a direct, required consequence of the WR-01 production fix, not a scope expansion — verified by running the full `NotificacaoServiceTest` suite (5/5 passing) both before committing this fix and again after WR-02/WR-03 landed.

**Note:** `mvn -o test-compile` (whole backend) and `mvn -o test -Dtest=NotificacaoServiceTest` (5/5 passing) were run as an additional Java-specific verification layer beyond the mandatory Tier 1 re-read, since Java isn't covered by the Tier 2 syntax-check table.

### WR-02: Fan-out test doesn't assert the property it claims to prove

**Files modified:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Commit:** `2f48092`
**Applied fix:** Added the missing assertion to `notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant`, exactly as suggested: captures the two saved `Notificacao` rows' `destinatarioId` values and asserts they equal `[admin1.getId(), admin2.getId()]` in order, closing the gap where a regression fanning out to the wrong user would previously still pass. Verified via `mvn -o test -Dtest=NotificacaoServiceTest` (5/5 passing).

### WR-03: `Notificacao.tenantId` / `destinatarioId` are mutable via class-level `@Setter` with no guard

**Files modified:** `backend/src/main/java/com/lexcv/models/Notificacao.java`
**Commit:** `35afb15`
**Applied fix:** Removed the class-level `@Setter` and confirmed via a full-tree grep (`\.set(TenantId|DestinatarioId|Categoria|Titulo|Mensagem|EntidadeTipo|EntidadeId|LinkUrl|CreatedAt|Id|Lida)\(` across `backend/src`) that `setLida(...)` is the *only* setter ever called on a `Notificacao` instance anywhere in the codebase. Applied `@Setter` only to the `lida` field rather than to every field except the 4 named in the review's example — this is stricter than the literal suggestion (which would have kept setters on 7 of 11 fields) and carries zero functional risk, since no caller needs the other setters. Verified with `mvn -o test-compile` (whole backend, BUILD SUCCESS — confirms no other file was relying on a removed setter) and `mvn -o test -Dtest=NotificacaoServiceTest` (5/5 passing).

### WR-04: Hardcoded ADMIN permission fallback in `UserPrincipal` was not updated for `notificacoes:view`

**Files modified:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java`
**Commit:** `46b6c9d`
**Applied fix:** Added `"notificacoes:view"` to the hardcoded ADMIN permission list in `UserPrincipal.create()`, plus the suggested comment (`// Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list.`) pointing back at the authoritative source, verified against `DatabaseSeeder.seedRbac()` (line 303) to confirm the exact scope key. The REVIEW.md finding offered a second option (deleting the hardcoded list entirely, since `dbPermissions` from the DB already carries the complete role-derived set on every restart after the first). I deliberately chose the reviewer's fully-coded, lower-risk option instead: a structural removal of this security-fallback pattern is a bigger call on a class used for every authenticated request's permission resolution, and is better left for an explicit human decision rather than an automated fixer picking the more invasive of two reviewer-endorsed alternatives. Verified with `mvn -o test-compile` (BUILD SUCCESS).

### WR-05: `NotificacaoController.listar` has no validation on `page`/`size`, turning bad input into a 500

**Files modified:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java`
**Commit:** `aa8dd78`
**Applied fix:** Added the exact bounds check suggested: returns `400 Bad Request` with a clear message when `page < 0 || size < 1`, before `PageRequest.of(page, size)` can throw an uncaught `IllegalArgumentException` that previously fell through to `GlobalExceptionHandler`'s catch-all 500. Verified with `mvn -o test-compile` (BUILD SUCCESS); no dedicated controller test exists yet for this endpoint.

## Skipped Issues

None — all 5 in-scope findings were fixed.

## Verification Summary

- Full backend test suite (`mvn -o test`, all test classes): **20/20 tests passing**, 0 failures, 0 errors — run once after all 5 fixes were committed, as a final sanity check given `UserPrincipal.java` and `Notificacao.java` are used broadly across the codebase.
- `mvn -o test-compile` (whole backend, main + test sources): BUILD SUCCESS after each Java-file fix.
- Out-of-scope findings (IN-01 through IN-05, all Info-severity) were intentionally left untouched per `fix_scope: critical_warning`.

---

_Fixed: 2026-07-08T21:49:55Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
