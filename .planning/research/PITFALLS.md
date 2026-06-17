# Pitfalls Research

**Domain:** Legal Case Management / Scheduling Module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: Timezone Offset / LocalDateTime Parsing Mismatch

**What goes wrong:**
Frontend formats dates using `.toISOString()`, which appends timezone offset details (e.g. `2026-06-17T17:00:00.000Z`). The backend attempts to parse this directly into a `LocalDateTime` field (or uses `LocalDateTime.parse` on query params), which throws a `DateTimeParseException` because `LocalDateTime` lacks offset semantics.

**Why it happens:**
JavaScript standardises on `.toISOString()` for serialization. Developers assume `LocalDateTime` in Java is a drop-in replacement for ISO-8601 strings, but it rejects timezone offsets/suffixes.

**How to avoid:**
On the frontend, strip the timezone offset suffix before sending payloads to the API, or format dates as local ISO strings (e.g. `yyyy-MM-ddTHH:mm:ss`).
On the backend, handle query parameters using robust parsers that fall back to timezone-aware types (`OffsetDateTime` or `ZonedDateTime`) and then convert to `LocalDateTime`.

**Warning signs:**
Saving a new event throws an HTTP 400 Bad Request (Jackson parsing failure), or dates are silently saved as null, leading to database constraint violations.

**Phase to address:** Phase 1 (Data Layer & Serialisation Alignment)

---

### Pitfall 2: Invalid Date Range Constraints (End Date before Start Date)

**What goes wrong:**
Users create events or deadlines where the End Date (`dataFim`) is scheduled before the Start Date (`dataInicio`). Neither frontend schemas nor backend controllers validate this order, leading to corrupted timeline representations.

**Why it happens:**
Omission of validation rules in both the Zod form schemas and the Spring Boot controller logic.

**How to avoid:**
Add a refinement check to the Zod schema on the frontend:
```typescript
.refine((data) => new Date(data.dataFim) >= new Date(data.dataInicio), {
  message: "A data de fim deve ser igual ou posterior à data de início",
  path: ["dataFim"],
})
```
Implement a model validation check on the backend, returning HTTP 400 if the date constraint is violated.

**Warning signs:**
Negative event durations, calendar grid drawing glitches, or empty timeline cards due to reversed chronological sorting.

**Phase to address:** Phase 2 (Form Hardening and API Validation)

---

### Pitfall 3: Casing Discrepancies (snake_case vs camelCase)

**What goes wrong:**
The frontend uses snake_case keys (`data_inicio`, `data_fim`, `processo_id`, `tenant_id`) to interact with the API, but the Spring Boot backend default Jackson configuration expects camelCase keys (`dataInicio`, `dataFim`, `processoId`, `tenantId`). The parameters are discarded, resulting in null values in the database.

**Why it happens:**
The initial Next.js mock API was designed in snake_case, but the subsequent Spring Boot REST API was implemented in standard camelCase.

**How to avoid:**
Refactor the Evento type definitions, forms, schemas, and query hooks in the frontend to camelCase. This matches the naming convention established for other entities like `ConflictCheckDecisao` and `Prazo` in v1.7.

**Warning signs:**
Event fields like `dataInicio` or `processoId` are null in database tables after form submission, or the calendar page shows no events because `e.data_inicio` is undefined.

**Phase to address:** Phase 1 (Data Layer & Serialisation Alignment)

---

### Pitfall 4: Swallowed Parsing Exceptions in Request Parameters

**What goes wrong:**
Query parameter parsing in `ResourceController.java` (`listEventos`) catches parsing exceptions and silently ignores them:
```java
try {
    LocalDateTime start = LocalDateTime.parse(dataInicio, DateTimeFormatter.ISO_DATE_TIME);
    // ...
} catch (Exception ignored) {}
```
If the parameters contain offset formats (e.g. `2026-06-17T00:00:00.000Z`), the filter is swallowed, and the API returns all events, bypassing range filters.

**Why it happens:**
Empty catch blocks used as defensive coding shortcuts, hiding formatting errors.

**How to avoid:**
Replace empty catches with proper logging or throw an explicit validation exception (returning HTTP 400) to notify the client of malformed date queries.

**Warning signs:**
The calendar displays events from previous/future months despite navigation filters.

**Phase to address:** Phase 2 (Form Hardening and API Validation)

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Swallowing Date Exceptions | Avoids API crashes on bad inputs. | Silently returns incorrect datasets, making debugging difficult. | Never. |
| Hardcoding categories in frontend | Quick UI visualization without altering schemas. | Fragile to title edits, lacks structural categorization. | Acceptable only in initial MVP; must be resolved for production. |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Static Month Grid navigation | Page feels slow and requires full refetching. | Implement pre-fetching of adjacent months or smooth transitions. |
| No Category Legend Filter | Cannot hide irrelevant events (e.g. meetings) to focus on deadlines. | Interactive legends that act as checkboxes to toggle calendar item visibility. |

## "Looks Done But Isn't" Checklist

- [ ] **Event Details View:** Looks complete, but might fail to link back to the Case/Process details if the ID type is mismatching.
- [ ] **Date Picker Input:** Looks complete, but defaults to client local timezone offset which fails backend validations. Ensure offset is removed before submitting.

---
*Pitfalls research for: LexCV Scheduling Module Improvements*
*Researched: 2026-06-17*
