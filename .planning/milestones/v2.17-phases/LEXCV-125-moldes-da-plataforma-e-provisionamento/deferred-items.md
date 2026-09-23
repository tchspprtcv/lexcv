# Deferred Items — Phase 125

Items discovered during execution that are out of scope for the current plan
(pre-existing, unrelated to the files this plan touches) and therefore not
auto-fixed, per the Scope Boundary rule.

## 125-06: Pre-existing gate drift from the LexCV → ALCv rename

**Found during:** Plan 06, Task 2 verification (`<verification>` block asks to
re-run `verify:consola-tenants` and `verify:bloqueio-rbac` as non-regression
checks).

**Issue:** Both gates fail on `master`/this branch already, independent of
this plan:

- `pnpm run verify:consola-tenants` → `FAIL tooltip-span-wrapper` and
  `FAIL guarda-tenant-reservado`. The gate's `GUARDA_TENANT_RESERVADO_PHRASE`
  constant (`web/scripts/verify-consola-tenants.mjs:52-53`) still hardcodes
  `"Não é possível suspender o tenant da plataforma (LexCV)."`, but the actual
  component (`web/src/app/(dashboard)/plataforma/columns.tsx:81`) already
  reads `"Não é possível suspender o tenant da plataforma (ALCv)."` — the
  product was renamed from LexCV to ALCv after Phase 120 shipped this gate,
  and the gate's literal was never updated to match.
- `pnpm run verify:bloqueio-rbac` → `FAIL A08-texto-exato-do-tooltip`, same
  root cause: an old product-name literal baked into the gate no longer
  matches the current `RbacTab` markup (`web/src/app/(dashboard)/settings/page.tsx`).

**Why not fixed here:** Neither `web/scripts/verify-consola-tenants.mjs`,
`web/scripts/verify-bloqueio-rbac.mjs`, `web/src/app/(dashboard)/plataforma/columns.tsx`,
nor `web/src/app/(dashboard)/settings/page.tsx` are in this plan's
`files_modified` list, and none of Plan 06's tasks touch them. Per the
Scope Boundary rule, only issues directly caused by the current task's
changes are auto-fixed; this is a pre-existing rename-drift bug that
predates this plan.

**Suggested follow-up:** A future phase (or a quick standalone fix) should
update the two gate scripts' hardcoded `(LexCV)` literal to `(ALCv)`, and
re-verify `A08`'s exact tooltip text against the UI-SPEC.
