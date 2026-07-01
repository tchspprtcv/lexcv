---
phase: 67-elabora-o-e-versionamento
reviewed: 2026-07-01T00:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - web/src/schemas/pareceres.ts
  - web/src/hooks/use-pareceres.ts
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 67: Code Review Report

**Reviewed:** 2026-07-01
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Reviewed the "Nova Versão" schema, XHR-upload hook, and detail-page RBAC gating added in Phase 67. The RBAC/instance-level check correctly mirrors the backend's `isAdmin || isResponsavel` disjunction, the form is fully omitted (not just disabled) for unauthorized users, the CONCLUIDO banner correctly replaces the form, and no edit/delete affordance exists on the version timeline — immutability is respected. The XHR upload hook is a faithful structural replica of `useUploadDocumentoComProgresso`, and cache invalidation covers all three required query-key namespaces. No Critical issues found. Three Warnings concern stale-file UX after form reset/error-retry, a `me.id` timing gap during the loading window, and the FileDropZone's silent same-file no-op; three Info items are minor robustness/consistency notes.

## Warnings

### WR-01: `form.reset` clears RHF state but not the underlying native file input, causing filename/value desync on repeat submissions

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:298`
**Issue:** After a successful submit, `form.reset({ conteudo: "", file: undefined as unknown as FileList })` clears the RHF-tracked `file` value, but `FileDropZone`'s own `<input type="file">` (in `file-drop-zone.tsx`) is an uncontrolled DOM element whose `.files` still holds the previously-selected file. If the user then drags/clicks to pick the *same* file again to submit a second version, some browsers do not re-fire `onChange` for an unchanged `FileList`-equivalent selection, or the visual state of the drop zone gives no indication a file is/isn't currently attached (it never displays a selected filename either way). This isn't a data-integrity bug (the required-field validation still catches an empty `file`), but it is a UX correctness gap directly relevant to the "successive versions" flow this feature exists for — a user submitting 3 versions in a row with the same attached document could be confused about whether their second submission actually has a file attached, since there's no filename echo.
**Fix:** Consider having `FileDropZone` accept a `value`/`selectedFileName` prop to display the currently-attached file's name, or force-remount the drop zone with a `key={progresso === null ? "idle" : "uploading"}`-style toggle keyed off submission count so the native input is guaranteed to reset visually alongside the form.

### WR-02: `isResponsavelOuAdmin` and `showNovaVersaoForm` are computed against a possibly-stale/loading `me` before `permissions.isLoading` resolves

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:134-140`
**Issue:** `ParecerDetailContent` derives `canEditPareceres`/`isResponsavelOuAdmin`/`showNovaVersaoForm` unconditionally from `permissions.data` without checking `permissions.isLoading`. While `ParecerDetailPage` gates the *view* permission on `!permissions.isLoading && !canView` before rendering `ParecerDetailContent`, that gate only protects the view-scope check — it does not delay rendering until `me` (needed for the instance check) has resolved. During the brief window where `parecer.data` has already loaded (fast query) but `permissions.data` (the `/auth/me` call) has not yet resolved, `me` is `undefined`, so `isResponsavelOuAdmin` evaluates to `false` and the Nova Versão card is (correctly, safely) hidden — this fails closed rather than open, so it isn't a security bug. However it does mean an authorized advogado responsável can see the card flicker in and out on slow networks, which contradicts the "no dead buttons" intent stated in the plan (the card being present then disappearing then reappearing is itself a bit of a "dead button" flash).
**Fix:** Gate the Nova Versão section rendering on `!permissions.isLoading` explicitly (e.g. render a skeleton/placeholder card while `permissions.isLoading`, matching the pattern already used for `parecer.isLoading`), so the card doesn't pop in/out as `/auth/me` resolves after `/pareceres/solicitacoes/{id}`.

### WR-03: `xhr.onload` treats any 2xx as success but never handles `xhr.onabort`/`xhr.timeout`, and provides no request timeout — a hung upload never resolves the mutation nor clears `versaoUpload.isPending`

**File:** `web/src/hooks/use-pareceres.ts:104-131`
**Issue:** This is copied verbatim from `useUploadDocumentoComProgresso`, which has the same gap, so it's a pre-existing pattern rather than something newly introduced — but it is newly *replicated* here rather than fixed, and the plan explicitly called for replication over abstraction. If the network stalls after headers are sent but before the response completes (or the server never responds), the `XMLHttpRequest` has no `timeout` set, so the promise returned by `mutationFn` never settles, `versaoUpload.isPending` stays `true` forever, and the submit button remains disabled ("A submeter...") indefinitely with no way for the user to retry or recover except reloading the page. Given `pareceres:edit` actions may be blocked by long backend virus-scan/storage operations, this is a realistic stuck-UI scenario.
**Fix:** Add `xhr.timeout = 60_000` (or similar) and an `xhr.ontimeout` handler that rejects with a clear message, so the mutation settles and the UI recovers. Since this mirrors an existing gap in `use-documentos.ts`, consider filing this as a shared follow-up rather than fixing only the pareceres copy (to avoid drift between the two "identical" implementations).

## Info

### IN-01: `conteudo` empty-string branch is unreachable in practice due to the `.trim()` before `.superRefine`, but the code handles it defensively — acceptable, just noting for clarity

**File:** `web/src/schemas/pareceres.ts:46-56`
**Issue:** `conteudo` is `z.string().trim().superRefine(...)`. An empty string after trim (`val.length === 0`) and a too-short string (`val.length < 10`) both produce the *same* message via the single `||` condition, unlike the sibling `parecerCreateFormSchema.descricao` (lines 19-32) which uses two separate `ctx.addIssue` branches with different messages ("Descreva o pedido de parecer." vs "A descrição deve ter pelo menos 10 caracteres."). This is a minor inconsistency between the two schemas' UX (empty field gives the same message as "too short" here, whereas the sibling schema calls out the empty case distinctly) but does not affect correctness — the plan actually specified this single-message form explicitly, so this isn't a deviation, just worth flagging for consistency awareness in future schemas.
**Fix:** No action required; optionally align both schemas to use the same messaging convention in a later cleanup pass.

### IN-02: `AnexoLink`'s empty catch swallows the download-mutation error silently based on an assumption that isn't verified in this file

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:88-95`
**Issue:** The comment `// apiFetch already surfaces a toast for this failure; nothing else to do here.` is asserted but not verified against `apiFetch`'s actual implementation in this review pass — if `apiFetch` ever changes to not auto-toast on this failure path (e.g. a future refactor scoping toast-surfacing to specific status codes), this call site will silently no-op with zero user feedback. This is pre-existing code (not part of the 67-01 diff) but is directly adjacent to and interacts with the new Nova Versão feature's data flow (both read from the same `useParecerVersoes`/timeline rendering block), so flagging for awareness.
**Fix:** Low priority; if touched again, consider an explicit `toast.error` fallback in the catch block rather than relying on an implicit apiFetch contract.

### IN-03: Magic string status literal `"CONCLUIDO"` duplicated across three call sites instead of referencing `parecerStatusSchema` enum values

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:139, 258`
**Issue:** `isConcluido = parecer.data?.status === "CONCLUIDO"` and the JSX render both hardcode the string `"CONCLUIDO"`. `parecerStatusSchema` (`web/src/schemas/pareceres.ts:3`) already defines this as part of a `z.enum([...])`, which would give compile-time protection against typos if referenced (e.g. `parecerStatusSchema.enum.CONCLUIDO`). Not a bug today since the literal is correctly spelled and matches `ParecerStatus` in `types/pareceres.ts`, but a future rename/refactor of the status enum could silently desync this check from the type.
**Fix:** Low priority; consider `parecerStatusSchema.enum.CONCLUIDO` or importing a shared constant if this status literal is used in a fourth place in a later phase (e.g. Phase 68's entrega action).

---

_Reviewed: 2026-07-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
