# Phase 118: Frontend — Indicador de Utilizadores no Limite - Context

**Gathered:** 2026-07-29
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

The `UserManagementTab` in `web/src/app/(dashboard)/settings/page.tsx` shows an "X/Y utilizadores" indicator and disables "Novo Utilizador" at the limit. X (active user count) is already derivable client-side from the existing `useAdminUsers()` list. Y (`limiteUtilizadores`) is **not yet exposed to the frontend by any endpoint** — this is a real gap found during research, not covered by Phase 117's backend-only scope, and it blocks this phase's own success criteria. It must be closed here, minimally, by extending an existing endpoint rather than introducing a new one.

</domain>

<decisions>
## Implementation Decisions

### Backend gap: exposing `limiteUtilizadores` to the frontend
- `GET /api/v1/auth/me` (`AuthController.getMe`, `backend/src/main/java/com/lexcv/controllers/AuthController.java:146-175`) already fetches the caller's `Tenant` row (`tenantRepository.findById(principal.getTenantId())`) and maps `tenant_nome`/`tenant_logo_data_url` onto `UserResponse` — this is the exact established pattern for "tenant-level data visible to every authenticated user, rendered contextually by role-gated UI." Add `tenant_plano` and `tenant_limite_utilizadores` the same way, in the same `tenantRepository.findById(...).ifPresent(...)` block
- Precedent: `tenant_nome` is already sent to every authenticated role (not just ADMIN) and only rendered where relevant (app shell branding). Sending `tenant_limite_utilizadores` to all roles the same way is consistent — it is a plan-capacity number, not sensitive data, and the display itself stays gated to the User Management tab (already ADMIN/`users:manage`-only)
- Do NOT create a new endpoint for this — extending `/auth/me` reuses an already-fetched `Tenant` row (zero extra query) and matches CLAUDE.md's "mirror scope:action RBAC" guidance without adding a new authorization surface
- `MeResponse` (`web/src/types/auth.ts:17-28`) gets two new optional fields following the exact naming/optionality convention of `tenant_nome?`/`tenant_logo_data_url?`: `tenant_plano?: string` and `tenant_limite_utilizadores?: number | null`
- `null` means "sem limite" (Enterprise) — the frontend must treat `null`/`undefined` identically (no indicator shown, "Novo Utilizador" never disabled by this rule) — never treat `0`/`null` as "zero seats"

### X/Y Indicator — Behavior
- X = `users.filter(u => u.ativo).length` from the already-loaded `useAdminUsers()` data — no new fetch
- Y = `me.tenant_limite_utilizadores` from the existing `useMe()`/auth hook (whatever already consumes `MeResponse` in this codebase — reuse it, do not add a parallel fetch)
- When Y is `null`/`undefined`: show no "X/Y" indicator at all (or "X utilizadores" without a limit, Claude's discretion on exact copy) — never disable "Novo Utilizador"
- When Y is a number: show "X/Y utilizadores" near the "Novo Utilizador" button (same `CardHeader` row, `settings/page.tsx:351-364`), disable the button when `X >= Y`, with a tooltip or adjacent text explaining why (per ROADMAP Success Criteria 2)
- The backend 409 (already implemented and tested in Phase 117, including the reactivation-bypass fix) remains the authoritative enforcement — this indicator is UX-only, never trusted as the sole gate. If a 409 is returned anyway (stale client state, e.g. two tabs open), show it as a clear toast (`sonner`, already used throughout this file via `toast.error(...)`) using the backend's own message, not a hardcoded frontend string

### UI Placement and Style
- Reuse existing primitives already in this file: `Card`/`CardHeader`/`CardTitle`/`CardDescription`, `Button`, `Tooltip` (already the established pattern for icon-only/disabled-state explanations per PROJECT.md Key Decisions — `TooltipProvider delayDuration={700}`)
- No new component library additions — this is a small addition to the existing `CardHeader` row, not a new screen

### Claude's Discretion
- Exact wording of the "no limit" state (omit indicator vs. show "X utilizadores" unlimited) — pick whichever reads more naturally in context, follow existing Portuguese copy style in this file
- Whether the count/limit is computed inline in `UserManagementTab` or extracted to a tiny helper — implementation detail, no behavioral difference
- Whether `MeResponse`'s new fields also get consumed anywhere else (e.g. app shell) — out of scope unless trivially free; the phase's own success criteria only require the Settings tab

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useAdminUsers()` hook already provides the full user list with `ativo` — X is already free
- `Tooltip`/`TooltipProvider` already used in this codebase for disabled-button explanations (sidebar logout, row actions across Clientes/Processos/Pareceres per PROJECT.md v2.13 Key Decisions)
- `toast.error(...)`/`toast.success(...)` (Sonner) already the established pattern for this exact file's error/success messaging

### Established Patterns
- `AuthController.getMe` already the single source of "tenant-level data for the current session" — extend, don't duplicate
- Two-layer enforcement pattern (backend authoritative + frontend UX mirror) already used project-wide for RBAC (`hasScopedPermission` mirrors `@PreAuthorize`) — same pattern applies here: frontend indicator mirrors, backend 409 enforces

### Integration Points
- `backend/src/main/java/com/lexcv/controllers/AuthController.java` (`getMe`, lines 146-175) — add 2 fields to the existing `tenantRepository.findById(...).ifPresent(...)` block
- `web/src/types/auth.ts` (`MeResponse`) — add 2 optional fields
- `web/src/app/(dashboard)/settings/page.tsx` (`UserManagementTab`, `CardHeader` at lines 351-364) — the actual indicator + button-disable logic

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured above and in ROADMAP.md's own Success Criteria for Phase 118.

</specifics>

<deferred>
## Deferred Ideas

- Editing `plano`/`limiteUtilizadores` from the UI — Phase 120 (PROV-04), explicitly out of scope here
- Showing the indicator anywhere outside the User Management tab (e.g. dashboard, app shell) — not required by this phase's success criteria, not pursued unless trivially free

</deferred>
