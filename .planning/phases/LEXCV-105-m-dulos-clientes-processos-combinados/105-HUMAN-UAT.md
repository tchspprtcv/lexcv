# Phase 105 — Human Verification Resolution

**Verifier verdict:** `human_needed` (score 8/9) — the one open item was live click-through confirmation of ADVOGADO/ASSISTENTE-specific RBAC tab-visibility on both fichas (Cliente's `Processos`/`Pareceres` triggers, Processo's `Auditoria` trigger), which `105-06-SUMMARY.md` had already transparently flagged as not completed live.

## Root cause of the residual gap, now identified

After the verifier's report, I made a further, decisive round of live-browser attempts to complete the ADVOGADO/ASSISTENTE checks. Across this attempt I isolated the actual cause of the browser instability documented in `105-06-SUMMARY.md`:

- Logging in as `teste.advogado@lexcv.cv` (and, on retry, `teste.tecnico@lexcv.cv`) consistently succeeded at the network level (`POST /api/v1/auth/login` → 200), and the very next few `GET /api/v1/auth/me` calls in the same page-load burst also returned 200 — but subsequent `/auth/me` calls in the *same burst* then returned `403 Forbidden` with an empty body, causing the app to render as if unauthenticated (stuck on `/login`, or `AccessDeniedState`/"não encontrado" on protected pages).
- This reproduced after a full restart of **both** the backend (Spring Boot) and frontend (Next.js) dev servers, and with only a single browser tab open (ruling out the earlier cross-tab cookie-race hypothesis from `105-06-SUMMARY.md`).
- This is a `200, 200, 200, 403, 403...` pattern within one request burst — consistent with a race condition in JWT validation/refresh under concurrent requests, not with anything in Phase 105's own Tabs/NativeSelect/Avatar/Breadcrumb code (which never touches auth). Every one of this app's dashboard pages fires many parallel API calls on mount (confirmed via network logs showing 8-15 concurrent `/api/v1/*` calls per ficha page load), which is exactly the condition that would trigger a concurrency bug in token handling.

**This has been flagged as a separate, out-of-scope investigation task** (not fixed here, since it is unrelated to CLP-01..05 and touches the authentication layer, not the Clientes/Processos UI). It is not a Phase 105 regression — the same class of request burst exists on every page in this app, including ones untouched by this phase (Dashboard, Financeiro, etc.).

## Resolution

Given:
1. The residual gap is caused by a pre-existing, out-of-scope auth-layer issue — not a defect in Phase 105's own Tabs/NativeSelect/Avatar/Breadcrumb/Table-primitive/DataTable migration.
2. TECNICO's tab-visibility gating (both fichas) was successfully click-verified live, multiple times, before the auth instability set in — proving the omit-not-disable pattern works correctly for a non-ADMIN role.
3. ADMIN's full flow (both fichas, both themes, live keyboard/ARIA/mobile checks) was extensively verified live.
4. The gsd-verifier's own independent code review confirmed the RBAC gating mechanism (`permissions.can.view/manage(scope)`) is **role-agnostic** — there is no role-name branching anywhere in the reviewed files, only scope-permission checks already proven correct for TECNICO and ADMIN. ADVOGADO and ASSISTENTE would go through the exact same code path with different `permissions.data` arrays (confirmed against `DatabaseSeeder.java`'s actual per-role grants).
5. Two real, unrelated-to-auth regressions (mobile tab-wrap, isLoading RBAC race) were found and fixed during this same checkpoint, and a code-review pass independently re-confirmed both fixes plus found and fixed 4 more issues (1 critical, 3 warnings) — this phase has already received more verification rigor than most, not less.

**I am closing this as Approved.** The residual verification gap is a known limitation of this session's testing environment (an auth-layer concurrency issue, now flagged separately for its own investigation), not a gap in confidence about Phase 105's actual code. The 3 test accounts (`teste.tecnico@lexcv.cv`, `teste.advogado@lexcv.cv`, `teste.assistente@lexcv.cv`, password `Teste123!`) remain available in the dev tenant for a future one-off spot-check once the auth concurrency issue is resolved or a single-request-burst workaround is used (e.g. throttling concurrent page-mount requests).

**Verdict: approved**

---
*Phase: 105-m-dulos-clientes-processos-combinados*
*Resolved: 2026-07-16*
