# Project Research Summary

**Project:** LexCV
**Domain:** Legal Case Management / Scheduling Module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Executive Summary

The scheduling module (Agenda) is a key feature of LexCV, responsible for managing generic events and process-specific deadlines (prazos). This research outlines the improvements and finalization of this module for milestone v1.9, focusing on visual excellence, casing consistency, robust date parsing, and validation checks.

The primary risk identified is a date parsing mismatch and naming inconsistency between the frontend web application (which uses snake_case and `.toISOString()` with offset suffixes) and the backend Spring Boot API (which expects camelCase fields and uses strict `LocalDateTime.parse` without offset capabilities). Resolving this mismatch is critical for the stability and functionality of the calendar.

Additionally, a significant UX improvement is proposed: unifying both generic calendar events (`t_evento`) and process-specific deadlines (`t_prazo`) into a single chronological calendar grid, letting legal professionals manage all commitments in one place.

## Key Findings

### Recommended Stack

[Detailed research in STACK.md](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/research/STACK.md)

- **Core technologies:** Next.js (16.2.6), React (19.2.4), Spring Boot (3.4.1), PostgreSQL (16+), TanStack Query (5.87.4).
- **Supporting libraries:** React Hook Form and Zod for schema validation; Day.js or custom timezone-stripping helpers for date processing; Lucide React for modern iconography.

### Expected Features

[Detailed research in FEATURES.md](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/research/FEATURES.md)

- **Must have (table stakes):** Casing alignment, period/month navigation filters, status filters (pending vs. completed), category filters (Deadlines, Audiences, Meetings, Diligences), and start-end date validation.
- **Should have (competitive):** Unified calendar view incorporating both events (`t_evento`) and deadlines (`t_prazo`); instant status toggle directly from the list views.
- **Defer (v2+):** External calendar synchronization (Google/Outlook) and drag-and-drop rescheduling.

### Architecture Approach

[Detailed research in ARCHITECTURE.md](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/research/ARCHITECTURE.md)

- **Property Naming Alignment:** Transition the scheduling module from snake_case to camelCase to match default Spring Boot/Jackson serialization.
- **Data Mapping:** Combine the events dataset and the deadlines dataset on the client side, mapping both into a unified `CalendarItem` schema.

### Critical Pitfalls

[Detailed research in PITFALLS.md](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/research/PITFALLS.md)

1. **LocalDateTime Parsing Errors:** Strict `LocalDateTime.parse()` throwing exceptions when offsets are sent. Strip offsets on the frontend or parse with offset-aware types on the backend.
2. **Reversed Date Ranges:** Saving events ending before they start. Implement validation rules on both frontend and backend.
3. **Casing Discrepancies:** Mismatched property names between frontend queries and backend JSON, leading to silent null values. Align frontend to camelCase.
4. **Swallowed Parsing Exceptions:** Empty catches in the controller hiding format errors, bypassing list filters. Raise explicit errors on malformed parameters.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 40: Alignment and Serialisation Hardening
- **Rationale:** We must align client casing conventions to camelCase before adding new features, ensuring zero data loss and fixing existing hidden bugs in get/post flows.
- **Delivers:** Casing refactoring in types, schemas, hooks, and pages; date format helper functions.
- **Avoids:** Casing discrepancies (Pitfall 3) and datetime serialization errors (Pitfall 1).

### Phase 41: Form Constraints and API Validation
- **Rationale:** Prevents corrupt scheduling data from reaching the database, making validation failures transparent to the user.
- **Delivers:** Zod date range checks, Spring Boot controller validations, and robust date parameter parsing on the backend with descriptive bad request responses.
- **Avoids:** Reversed date ranges (Pitfall 2) and swallowed parsing exceptions (Pitfall 4).

### Phase 42: Period Filters, Categories, and Unified Calendar
- **Rationale:** Adds premium user interface filters, structured categories, and aggregates both deadlines and events into one view.
- **Delivers:** Category/status legends and filter selectors, unified data loading, and a visually improved calendar grid.
- **Addresses:** Filters and UX improvements (AGD-32).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Supported by existing app configuration and dependencies. |
| Features | HIGH | Table stakes and differentiators are well understood in legal case management. |
| Architecture | HIGH | Fits the established patterns of other modules in LexCV. |
| Pitfalls | HIGH | Directly addresses the timezone and parsing bugs common in JS/Java integrations. |

**Overall confidence:** HIGH

### Gaps to Address

- **Date Parameter Format:** Verify whether query date parameters should remain ISO strings or be normalized to simple YYYY-MM-DD strings. We will use ISO string formats without offset details (`YYYY-MM-DDTHH:mm:ss`) to avoid LocalDateTime parse issues.

---
*Research completed: 2026-06-17*
*Ready for roadmap: yes*
