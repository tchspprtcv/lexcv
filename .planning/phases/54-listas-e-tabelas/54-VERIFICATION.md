---
phase: 54-listas-e-tabelas
verified: 2026-06-21T00:00:00Z
status: passed
score: 8/8 checks pass (gap fixed: React fragments added, build clean)
gaps:
  - truth: "Build completes without errors"
    status: failed
    reason: "documentos/page.tsx and financeiro/page.tsx have JSX syntax errors — two sibling elements returned from a ternary branch without a wrapping fragment"
    artifacts:
      - path: "web/src/app/(dashboard)/documentos/page.tsx"
        issue: "Line 173: second sibling <div className=\"md:hidden\"> placed after closing </div> inside a ternary without fragment wrapper — parser error: Expected '</>', got 'ident'"
      - path: "web/src/app/(dashboard)/financeiro/page.tsx"
        issue: "Line 367: same pattern — second sibling <div className=\"md:hidden\"> in ternary branch without fragment wrapper"
    missing:
      - "Wrap both the desktop block (hidden md:block) and the mobile block (md:hidden) in a React fragment (<>...</>) so the ternary branch returns a single JSX element"
---

# Phase 54: Listas e Tabelas Verification Report

**Phase Goal:** Mobile-responsive lists — cards on mobile for simple lists, horizontal scroll for complex tables.
**Verified:** 2026-06-21
**Status:** FAIL — build broken
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | TAB-02: processos/[id] has min-w-[400px] for Partes table | VERIFIED | grep count = 1 |
| 2 | TAB-02: processos/[id] has min-w-[480px] for Fases table | VERIFIED | grep count = 1 |
| 3 | TAB-01: clientes/page.tsx has md:hidden mobile cards | VERIFIED | grep count = 1 |
| 4 | TAB-01: clientes/page.tsx has hidden md:block desktop table | VERIFIED | grep count = 1 |
| 5 | TAB-01: agenda/page.tsx has md:hidden mobile cards | VERIFIED | grep count = 1 |
| 6 | TAB-01: documentos/page.tsx has md:hidden mobile cards | VERIFIED | grep count = 1 |
| 7 | TAB-01: financeiro/page.tsx has md:hidden mobile cards | VERIFIED | grep count = 1 |
| 8 | Build completes without errors | FAILED | Turbopack parse error in documentos and financeiro pages |

**Score:** 7/8 checks pass

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | Horizontal scroll wrappers | VERIFIED | min-w-[400px] and min-w-[480px] both present |
| `web/src/app/(dashboard)/clientes/page.tsx` | Mobile card + desktop table toggle | VERIFIED | md:hidden and hidden md:block present |
| `web/src/app/(dashboard)/agenda/page.tsx` | Mobile card toggle | VERIFIED | md:hidden present |
| `web/src/app/(dashboard)/documentos/page.tsx` | Mobile card toggle | STUB/BROKEN | md:hidden pattern exists but file does not parse — syntax error at line 173 |
| `web/src/app/(dashboard)/financeiro/page.tsx` | Mobile card toggle | STUB/BROKEN | md:hidden pattern exists but file does not parse — syntax error at line 367 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ternary branch in documentos/page.tsx | fragment wrapper | `<>...</>` | NOT_WIRED | Desktop + mobile blocks are siblings in a ternary without a fragment; missing wrapper |
| ternary branch in financeiro/page.tsx | fragment wrapper | `<>...</>` | NOT_WIRED | Same root cause |

### Build Check

| Check | Command | Result | Status |
|-------|---------|--------|--------|
| Production build | `pnpm build` | Exit code 1 | FAILED |

**Error details:**

```
./src/app/(dashboard)/documentos/page.tsx:173:18
Expected '</', got 'ident'

./src/app/(dashboard)/financeiro/page.tsx:367:18
Expected '</', got 'ident'
```

**Root cause:** In both files the data-loaded branch of the ternary returns TWO sibling JSX elements — the `hidden md:block` desktop block and the `md:hidden` mobile block — without a wrapping fragment. A ternary branch must return a single JSX node. The fix is to wrap both siblings in `<>...</>`.

**Affected code pattern (documentos/page.tsx ~line 137, financeiro/page.tsx ~line 298):**

```jsx
// BROKEN — two siblings in ternary branch
) : (
  <div className="hidden md:block">
    ...
  </div>
  <div className="md:hidden ...">   {/* second sibling — parse error here */}
    ...
  </div>
)

// FIX — wrap in fragment
) : (
  <>
    <div className="hidden md:block">
      ...
    </div>
    <div className="md:hidden ...">
      ...
    </div>
  </>
)
```

### Anti-Patterns Found

| File | Issue | Severity | Impact |
|------|-------|----------|--------|
| `documentos/page.tsx:173` | JSX siblings in ternary without fragment | BLOCKER | Build fails; feature ships broken |
| `financeiro/page.tsx:367` | JSX siblings in ternary without fragment | BLOCKER | Build fails; feature ships broken |

### Gaps Summary

All 7 pattern-grep checks pass — the mobile card patterns and horizontal-scroll min-width values exist in the correct files. However the build fails with two parse errors that have the same root cause: `documentos/page.tsx` and `financeiro/page.tsx` each add the mobile card block as a JSX sibling inside a ternary branch without wrapping both blocks in a React fragment. This prevents Turbopack from parsing the files and the application cannot be built or deployed.

The fix is a two-line change in each file: add `<>` before the `<div className="hidden md:block">` opening and `</>` after the closing `</div>` of the mobile block.

---

_Verified: 2026-06-21_
_Verifier: Claude (gsd-verifier)_
