---
phase: 99-webpage-nova-app-next-js-de-landing
reviewed: 2026-07-15T12:13:42Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - webpage/next.config.ts
  - webpage/proxy.ts
  - webpage/src/lib/setup.ts
  - webpage/src/lib/branding.ts
  - webpage/src/lib/backend-origin.ts
  - webpage/src/components/theme-toggle.tsx
  - webpage/src/components/brand-mark.tsx
  - webpage/.env.example
findings:
  critical: 1
  warning: 0
  info: 4
  total: 5
status: issues_found
---

# Phase 99: Code Review Report (Re-review after second fix iteration)

**Reviewed:** 2026-07-15T12:13:42Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

This is iteration-3 (final) of a 3-iteration auto-fix loop. It independently re-verifies `99-REVIEW-FIX.md`'s claim that commit `8aeb1a8` resolved the prior round's WR-01 (`BACKEND_API_ORIGIN` never validated/normalized) via a new shared `getBackendOrigin()` helper in `webpage/src/lib/backend-origin.ts`. Nothing in this section is taken from the fix report at face value — every claim was re-derived from the current source (`git show 8aeb1a8`, direct file reads) and, for the behavioral claims, verified with an executable reproduction, not just read.

**WR-01's literal symptom is genuinely fixed — verified empirically.** I built a faithful Node ESM reproduction of the exact import chain (`backend-origin.mjs` → `setup.mjs` → `proxy.mjs`, mirroring the real files line-for-line) and ran all four inputs the prior review and the fix report discuss:

```
BACKEND_API_ORIGIN="http://localhost:8080"   -> setup URL correct, proxy() try succeeds
BACKEND_API_ORIGIN="http://localhost:8080/"  -> trailing slash stripped correctly, proxy() try succeeds (no double slash)
BACKEND_API_ORIGIN="localhost:8080"          -> throws "must include a scheme..." at import time
(unset)                                       -> throws "is required" at import time
```

The trailing-slash case — the primary example in the prior WR-01 — is cleanly fixed with zero side effects: `raw.replace(/\/+$/, "")` correctly normalizes it and the resulting URL is verified correct. `tsc --noEmit -p webpage/tsconfig.json` and `eslint` on all 8 files both pass clean (re-run independently, not reused from either prior report).

**But the fix introduces a new, more severe problem for the "missing scheme" sub-case, and this is the central finding of this round (CR-01 below).** Both `setup.ts:4` and `branding.ts:4` call `getBackendOrigin()` at module top level — outside any function, therefore outside every try/catch that exists in their callers. `proxy.ts`'s fail-open `try { await fetchSetupStatus() } catch { return NextResponse.next(); }` and `branding.ts`'s own internal `try/catch` around its `fetch()` call both exist specifically so a backend/config problem can never take down this public, unauthenticated marketing site — that intent is stated explicitly in both files' own comments ("fail open — um erro transitório do backend nunca deve bloquear a landing pública"; "nunca crash para um visitante anónimo"). My reproduction proves neither catch block can intercept a throw from `getBackendOrigin()`, because that throw happens during static `import` resolution, before either function body (and its try block) ever executes — this is invariant JS module semantics, not a framework nuance. The practical consequence: a `BACKEND_API_ORIGIN` that's present but missing an `http(s)://` scheme (a materially plausible ops typo — e.g. copying just `host:port` from a runbook) now takes down the *entire* site for *every* visitor (both the `/setup` redirect gate in `proxy.ts` and the actual homepage render in `page.tsx`, confirmed via grep as the only two importers of this code), instead of the prior graceful degradation (broken redirect gate, page still served). That is a worse observable outcome than the bug WR-01 set out to fix, and it directly contradicts this codebase's own explicitly-documented fail-open contract. See CR-01 for full detail, reproduction, and a concrete minimal fix.

Notably, `99-REVIEW-FIX.md` itself states the throw is "a build/startup-time error, not a per-request one" as the justification for keeping it outside the catch blocks — this specific factual claim is not substantiated anywhere in that report and my reproduction directly contradicts it for the realistic case where `BACKEND_API_ORIGIN` is (correctly, per this app's own `output: "standalone"` + non-`NEXT_PUBLIC_`-prefixed design) injected at container/process runtime rather than baked in at build time.

**All 4 carried-forward Info items were independently re-confirmed against the current source** (not copy-pasted from the prior report). One line-number correction: IN-02 (branding.ts unvalidated cast) shifted from lines 22-23 to lines 19-20 because the fix commit shortened the file by 3 lines. IN-01 (orphaned `NEXT_PUBLIC_API_BASE_PATH`) was specifically re-checked against the new `backend-origin.ts` file per this round's instructions — confirmed it does **not** reference `NEXT_PUBLIC_API_BASE_PATH` (it only reads `BACKEND_API_ORIGIN`), and a repo-wide grep still returns only the variable's own declaration in `.env.example` — it remains fully orphaned, unchanged in status. IN-03 and IN-04 are byte-for-byte unchanged (their files were not touched by commit `8aeb1a8`, confirmed via `git show --stat`).

**Recommendation for the orchestrator:** CR-01 is a genuine, reproduced, Critical-severity finding (a plausible single-typo config error causes a full outage of a public site, contradicting the app's own stated design contract) discovered on the final allowed iteration. Per this review framework's own rule, BLOCKER/Critical findings must be fixed before this code ships. The fix is small and localized (move one function call from module scope into the two async function bodies — see CR-01's fix), so it should not require a further multi-file iteration if the orchestrator chooses to route it back for one more targeted fix.

## Critical Issues

### CR-01: `getBackendOrigin()` validation runs outside every fail-open catch — a scheme-less `BACKEND_API_ORIGIN` crashes the entire public site instead of degrading gracefully

*(Fresh finding this round; unrelated to the round-1 `CR-01`, which remains resolved.)*

**File:** `webpage/src/lib/backend-origin.ts:18-33`, `webpage/src/lib/setup.ts:2,4`, `webpage/src/lib/branding.ts:2,4`, `webpage/proxy.ts:3,10-19`
**Issue:**

`setup.ts` and `branding.ts` both do this, at module scope, outside any function:

```ts
// setup.ts:2,4                              // branding.ts:2,4
import { getBackendOrigin } from "@/lib/backend-origin";
const backendOrigin = getBackendOrigin();     // throws here if BACKEND_API_ORIGIN is missing/malformed
```

`getBackendOrigin()` throws for two input shapes: entirely unset, or set-but-missing-a-scheme (e.g. `BACKEND_API_ORIGIN=localhost:8080` instead of `http://localhost:8080`). Because this call executes during the module's static `import`, it runs and can throw *before* either consuming function's body — and therefore before its try/catch — ever executes:

- `proxy.ts:3` does `import { fetchSetupStatus } from "./src/lib/setup";` — this import alone triggers `setup.ts`'s module-scope `getBackendOrigin()` call. If it throws, `proxy.ts`'s own module fails to finish loading, so the `try { await fetchSetupStatus(); } catch { return NextResponse.next(); }` block at `proxy.ts:10-19` never gets a chance to run — there is no `proxy()` function to catch anything with, because the module holding it never finished initializing.
- `webpage/src/app/page.tsx:7` does `import { fetchBranding } from "@/lib/branding";` (confirmed via grep as the only other importer of this code besides `proxy.ts`). The identical mechanism applies: `branding.ts`'s own internal `try { ... } catch { return FALLBACK; }` (which correctly protects against network/timeout/JSON errors) cannot protect against this, because `getBackendOrigin()` throws during `page.tsx`'s import of the module, before `fetchBranding()` is ever called.

I independently reproduced this with a faithful line-for-line copy of the real import chain (not the fix report's `node -e` snippets, which only tested `new URL()` semantics in isolation and never exercised the actual try/catch bypass):

```js
// proxy.mjs (mirrors webpage/proxy.ts's structure exactly)
import { fetchSetupStatus } from "./setup.mjs";
export async function proxy() {
  try {
    const status = await fetchSetupStatus();
    return "next()";
  } catch (err) {
    console.log("[proxy] fail-open catch DID fire:", err.message);
    return "next() via catch";
  }
}

// runner.mjs — simulates the framework invoking proxy() per request
try {
  const mod = await import("./proxy.mjs");
  console.log("RESULT:", await mod.proxy());
} catch (err) {
  console.log("Import of proxy.mjs itself threw (proxy()'s internal try/catch NEVER RAN):", err.message);
}
```

Output for `BACKEND_API_ORIGIN="localhost:8080"` (missing scheme):

```
Import of proxy.mjs itself threw (proxy()'s internal try/catch NEVER RAN):
 -> BACKEND_API_ORIGIN must include a scheme (http:// or https://), got: localhost:8080
```

vs. the well-formed and trailing-slash cases, both of which correctly reach and pass through `proxy()`'s own try block:

```
BACKEND_API_ORIGIN="http://localhost:8080"  -> [proxy] fail-open try succeeded, status: http://localhost:8080/api/v1/setup/status
BACKEND_API_ORIGIN="http://localhost:8080/" -> [proxy] fail-open try succeeded, status: http://localhost:8080/api/v1/setup/status
```

This is not a hypothetical: it is precisely one of the two concrete inputs the prior review round used to justify WR-01 (`localhost:8080`, no scheme). The prior bug for that input was "fetch silently never reaches the intended backend, indistinguishable from a transient outage" — a real but *contained* problem (only the `/setup` redirect gate degraded; the marketing site itself still rendered for every visitor). The current fix converts that into a *total* outage of the entire public site — both the redirect gate (`proxy.ts`) and the homepage content itself (`page.tsx`, which renders nothing without `fetchBranding()`) — for a misconfiguration that is materially easier to ship to production undetected than "entirely forgot to set the variable" (which is far more likely to be caught by the very first smoke test in any environment). This also directly contradicts the fail-open design intent stated verbatim in both consumer files' own comments.

`99-REVIEW-FIX.md` (the fix report) frames this as intentional: *"a misconfigured origin still throws at module-load/first-import time (a loud, immediate startup failure) rather than being silently swallowed by either fail-open catch block"* and *"moving the throw inside the fetch call... would have re-introduced the 'swallowed by a catch block' risk this finding is about"* — but "swallowed by the catch block" is exactly this app's own stated design goal for backend/config-related failures (see the comments quoted above), and the report's claim that this is "a build/startup-time error, not a per-request one" is unproven — for this app's own deployment shape (`output: "standalone"`, `BACKEND_API_ORIGIN` deliberately *not* `NEXT_PUBLIC_`-prefixed, i.e. designed to be injected at container/process runtime rather than baked in at build time), the reproduction above shows the throw fires exactly when the module is loaded to serve a real request, not at some separate, decoupled "build" phase.

**Fix:** Move the `getBackendOrigin()` call from module scope into each async function body, so it executes inside the try/catch that already exists for exactly this purpose. This preserves fail-fast semantics (still throws immediately, every time, with the same descriptive message) while restoring the fail-open guarantee for live traffic:

```ts
// setup.ts
import { getBackendOrigin } from "@/lib/backend-origin";

export async function fetchSetupStatus(init?: RequestInit): Promise<SetupStatusResponse> {
  const backendOrigin = getBackendOrigin(); // now runs inside proxy.ts's try/catch
  const response = await fetch(`${backendOrigin}/api/v1/setup/status`, {
    ...init,
    cache: "no-store",
    signal: init?.signal ?? AbortSignal.timeout(3000),
    headers: { Accept: "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) throw new Error(`Setup status failed with ${response.status}`);
  return (await response.json()) as SetupStatusResponse;
}
```

```ts
// branding.ts
export async function fetchBranding(): Promise<BrandingResponse> {
  try {
    const backendOrigin = getBackendOrigin(); // now runs inside this try
    const response = await fetch(`${backendOrigin}/api/v1/public/branding`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
      headers: { Accept: "application/json" },
    });
    if (!response.ok) return FALLBACK;
    const data = (await response.json()) as BrandingResponse;
    return { nome: data.nome || "LexCV", logoDataUrl: data.logoDataUrl ?? null };
  } catch {
    return FALLBACK;
  }
}
```

Once moved, fixing IN-03 (add `console.error` in `proxy.ts`'s catch) becomes a necessary companion, not just a nice-to-have — otherwise a misconfigured origin fails open *silently*, with no signal at all that anything is wrong (the same class of "no observability" gap that let the original CR-01 ship unnoticed in the first place).

## Info

### IN-01: `NEXT_PUBLIC_API_BASE_PATH` in `.env.example` is still dead configuration — confirmed unaffected by the `backend-origin.ts` addition

**File:** `webpage/.env.example:2`
**Issue:** Re-checked specifically for this round: `webpage/src/lib/backend-origin.ts` reads only `process.env.BACKEND_API_ORIGIN` (line 19); it does not reference `NEXT_PUBLIC_API_BASE_PATH` anywhere. A repo-wide grep for `NEXT_PUBLIC_API_BASE_PATH` across `webpage/` (all `.ts`/`.tsx`/`.example` files) still returns exactly one hit — its own declaration in `.env.example:2`. Status unchanged from the prior round: still orphaned, still liable to mislead whoever configures deployment into thinking it's required.
**Fix:** Remove the line from `webpage/.env.example`, or add a one-line comment if it's intentionally reserved for a future browser-side fetch.

### IN-02: `fetchBranding()` casts the response JSON without runtime validation (carried over; line numbers shifted by the CR-01/WR-01 fix)

**File:** `webpage/src/lib/branding.ts:19-20` (previously reported as 22-23; shifted by -3 lines because commit `8aeb1a8` replaced a 5-line inline guard with a 2-line import+call)
**Issue:** `(await response.json()) as BrandingResponse` (line 19) still trusts the response shape completely; `data.nome || "LexCV"` (line 20) would let a non-string truthy `nome` (contract drift, misbehaving intermediary) reach `<span>{nome}</span>` in `brand-mark.tsx:29` and throw ("Objects are not valid as a React child"), crashing SSR for every visitor. Unchanged in substance from the prior round; not touched by commit `8aeb1a8` beyond the line-shift.
**Fix:** `typeof data?.nome === "string" ? data.nome : "LexCV"` before use.

### IN-03: `proxy.ts`'s `catch` still swallows every error silently, with no logging (carried over, unchanged; now also relevant to CR-01)

**File:** `webpage/proxy.ts:16-18`
**Issue:** Still no logging in the `catch {}` block (confirmed byte-for-byte unchanged — `proxy.ts` was not among the files touched by commit `8aeb1a8`). This was already the root gap that let the original CR-01 ship unnoticed. It's now doubly relevant: even after CR-01 above is fixed by moving `getBackendOrigin()` inside `fetchSetupStatus()`, a misconfigured origin would fail open *silently* through this exact catch block unless logging is added here too.
**Fix:** `catch (err) { console.error("[proxy] setup-status check failed, failing open:", err); return NextResponse.next(); }`.

### IN-04: Tenant logo `<img>` uses `alt=""` (carried over, unchanged)

**File:** `webpage/src/components/brand-mark.tsx:25`
**Issue:** Confirmed byte-for-byte unchanged (only `hasLogo`'s regex on lines 14-15 was touched by the earlier WR-05 fix, not this line). The logo conveys institution identity, not decoration; empty `alt` hides that from screen-reader users.
**Fix:** `alt={nome || "Logótipo"}`.

---

_Reviewed: 2026-07-15T12:13:42Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
