# Phase 136: deferred items (out of scope, found during 136-16)

## 1. Startup aborts after an office role with users is customised (pre-existing, Phases 126/127)

**Found during:** 136-16 Task 2, live stack, step 7 restart.

**Reproduction:**
1. `PUT /api/v1/admin/rbac` adds `financeiro:manage` to the ADVOGADO office role (200).
2. `POST /api/v1/admin/users` creates a user with that office role (201). The user also receives the global ADVOGADO role in `t_user_role`.
3. Restart the backend.

**Result:** startup aborts with `IllegalStateException: 1 utilizador(es) com deriva de autoridade detectada apos a conversao -- emails afectados: [gestor.fat@lexcv.cv] ... permissoes ganhas: [financeiro:manage]`, raised by `MigracaoPapeisEscritorioService.migrar` → `VerificacaoDerivaPapeisService.verificarSemDeriva` from `MigracaoPapeisRunner`.

**Why it happens:** the boot-time conversion check runs on every start. It treats the difference between the global-role permissions ("antes") and the office-role permissions ("depois") as drift. A legitimate office RBAC edit (Phase 127 CATL-04) is exactly such a difference. So any office that customises a role that has users cannot restart the backend.

**Not fixed here:** it is unrelated to eFatura, and the fix touches the role conversion and RBAC design (Phases 126/127). The 136-16 live run worked around it with an environment-only change. I removed `financeiro:manage` from the ADVOGADO office role and gave the test user a direct `t_user_permission` row instead. That row is counted on both sides of the check, so there is no drift.

**Suggested follow-up:** run the drift check only while users are actually being converted (users with no office role yet). Alternatively, compare only converted users, so a converged install never re-checks office edits.
