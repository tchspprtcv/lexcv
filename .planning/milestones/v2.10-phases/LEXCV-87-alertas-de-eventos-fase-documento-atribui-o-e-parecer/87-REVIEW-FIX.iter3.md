---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
fixed_at: 2026-07-09T12:15:30Z
review_path: .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
iteration: 2
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 87: Code Review Fix Report

**Fixed at:** 2026-07-09T12:15:30Z
**Source review:** .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 4 (1 critical, 3 warning; IN-01 and IN-02 excluded — fix_scope is `critical_warning`)
- Fixed: 4
- Skipped: 0

All fixes were made in an isolated git worktree/branch (`gsd-reviewfix/87-*`) and
fast-forwarded onto `master` (see commit hashes below) — no rebase or history
rewrite. Each fix was verified with a re-read of the modified region (Tier 1)
plus a real compile/typecheck (Tier 2): `mvn -o -q -DskipTests compile`
(offline, exit 0, clean before and after every backend commit) and
`npx tsc --noEmit -p tsconfig.json` for frontend changes (baseline: 3
pre-existing `vitest` module-resolution errors in unrelated `*.test.ts`
files — same 3, same files, before and after every frontend commit). For
CR-01, the existing `NotificacaoServiceTest` suite was additionally read in
full to confirm none of its mocks assert on the old throw-propagation
behavior the fix changes (none do — every existing test mocks the happy
path, so no regression from this change).

## Fixed Issues

### CR-01: ADMIN fan-out is silently skipped whenever the primary recipient is a stale/orphaned user reference

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** `015c433`
**Commit status:** fixed
**Applied fix:** Added `@Slf4j` to `NotificacaoService` and moved the
try/catch isolation from the controller call sites (prior iteration's fix)
down to each individual `criar(...)` call *inside* the service methods, so
one stale/orphaned recipient can never abort the rest of the method:
- `notificarFaseEntrada`: the primary `responsavelId` notification is now
  isolated, so a stale responsável no longer prevents the unconditional
  `notificarAdmins(...)` call below it from running.
- `notificarDocumentoNovo`: each destinatario in the dedup'd iteration is now
  isolated individually, so one stale destinatario no longer skips the
  remaining destinatarios (in iteration order) or the ADMIN fan-out.
- `notificarAdmins` (the actual `for (User admin : ...)` loop): also isolated
  per-admin, closing the identical corollary bug where one stale ADMIN
  reference would have silently blocked notifications to every other ADMIN
  ordered after it. This was explicitly suggested by the review ("apply the
  same per-recipient isolation inside notificarAdmins itself") and directly
  extends the same fix pattern to the fan-out helper shared by all four
  notification triggers.

**Not applied (explicitly optional per the review):** the review's closing
paragraph separately says to "consider" the identical wrapping inside
`notificarProcessoAtribuido` / `notificarParecerAtribuido` "too" as
forward-looking hardening, while stating plainly that neither is reachable
with a stale id *today* (both call sites re-validate the id in the same
request). Left unchanged to keep this fix scoped to the reachable defect;
flagging here in case a future caller changes that.

No new test was added for this fix (the review's Fix section for CR-01 did
not request one, and IN-01 in this same review — out of scope for
`critical_warning` — already tracks the analogous gap for the prior
iteration's WR-02 null-guard). A future review iteration may want an
equivalent regression test for the per-recipient isolation added here.

### WR-01: New tenant-user listing endpoint doesn't filter out deactivated accounts

**Files modified:** `backend/src/main/java/com/lexcv/dtos/UserSummaryResponse.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `web/src/hooks/use-users.ts`, `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** `9fd80a6`
**Commit status:** fixed
**Applied fix:** Added a nullable `ativo` field to `UserSummaryResponse` and
populated it in `listTenantUsers`'s mapping (`.ativo(u.getAtivo())`). Added
the matching optional `ativo?: boolean` field to the frontend
`TenantUserOption` type. Filtered to `u.ativo !== false` at the two
*assignment* call sites only — the "Novo Prazo" responsável `<select>`
(`page.tsx` Novo Prazo dialog) and the "Reatribuir Responsável" `<select>`
(`ReatribuirResponsavelControl`) — while leaving `userNomeById` (the
historical name-lookup map used for e.g. conflict-check `decisorId`
resolution) unfiltered, exactly as the review specified, so deactivated
users' historical actions still resolve to a name.

### WR-02: `useTenantUsers()` is the only query hook missing the SSR `typeof window` guard

**Files modified:** `web/src/hooks/use-users.ts`
**Commit:** `8043f80`
**Commit status:** fixed
**Applied fix:** Added `const enabled = typeof window !== "undefined";` and
passed `enabled` into the `useQuery(...)` options, matching the exact
convention already used by `useAdminUsers` (`use-admin.ts`) and all other
hooks in `web/src/hooks/`.

### WR-03: `atribuirResponsavel` writes no audit-log entry

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `8361ad6`
**Commit status:** fixed
**Applied fix:** Added an `auditLogRepository.save(AuditLog.builder()...)`
call immediately after `processoRepository.save(processo)` and before the
`notificarProcessoAtribuido(...)` call, mirroring
`ParecerController.atribuirAdvogado`'s existing pattern field-for-field
(`acao("processo_atribuir")`, `entidadeTipo("processo")`,
`entidadeId(saved.getId().toString())`, `autorId` from
`SecurityContextHolder`'s `UserPrincipal`, plus `processoId(saved.getId())`
since — unlike the parecer side — a processo id is available here). Both
`AuditLog`/`AuditLogRepository` and `Authentication`/`SecurityContextHolder`/
`UserPrincipal` were already imported and injected in `ResourceController`
(used elsewhere in the same file), so no new imports or fields were needed.

---

_Fixed: 2026-07-09T12:15:30Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
