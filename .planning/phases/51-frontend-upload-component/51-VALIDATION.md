---
phase: 51
slug: frontend-upload-component
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-19
---

# Phase 51 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pnpm build + pnpm lint (Next.js type-check + ESLint) |
| **Quick run command** | `pnpm build 2>&1 \| tail -20` |
| **Lint command** | `pnpm lint` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `pnpm build`
- **After all tasks:** Run `pnpm lint`
- **Before `/gsd:verify-work`:** Both build and lint must be clean

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Behavior | Automated Command |
|---------|------|------|-------------|----------|-------------------|
| 51-01-01 | 51-01 | 1 | MIN-05, MIN-06, MIN-07 | FileDropZone + XHR progress hook + presigned download hook | `pnpm build` |
| 51-01-02 | 51-01 | 1 | MIN-05, MIN-06, MIN-07, MIN-08 | Upload page with drag-drop, progress bar, preview; detail page with window.open | `pnpm build && pnpm lint` |

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual |
|----------|-------------|------------|
| Progress bar displays real percentage during upload | MIN-05 | Requires live backend + large file |
| Drag file from desktop into upload zone | MIN-07 | Browser interaction required |
| Presigned URL opens file in new tab | MIN-06 | Requires live MinIO instance |
| Image preview renders before submit | MIN-08 | Browser rendering |

---

## Validation Sign-Off

- [x] All tasks have automated verify commands
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
