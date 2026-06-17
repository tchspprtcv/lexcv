# Feature Research

**Domain:** Legal Case Management / Scheduling Module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Period Filters | Users need to navigate months/weeks/days and see only events in that range. | MEDIUM | Needs integration with Query Params and React Query cache. |
| Status Filters | Filter between Pending and Completed events. | LOW | Uses the existing `concluido` API parameter. |
| Category Filters | Distinguish and filter by Event types (Prazos, Audiências, Diligências, Reuniões). | LOW | Categorization currently derived from title on frontend; needs structured mapping. |
| Orderly Date validation | Prevent creating events where End Date is before Start Date. | LOW | Enforced via Zod schema and backend validation filters. |
| Load & Error indicators | Visual indicators (spinners, skeletons, toast warnings) when loading calendar. | LOW | Handled by TanStack Query state and toaster library. |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Unified Calendar View | Merge both general Events (`t_evento`) and Process Deadlines (`t_prazo`) into a single monthly calendar grid. | MEDIUM | Requires combining `useEventos` and `usePrazos` (or fetching deadlines) and normalising their shapes in the UI. |
| Process Context Integration | Allow clicking a calendar event or deadline to immediately navigate to its linked case/process details. | LOW | Links event page process ID back to `/processos/[id]`. |
| Immediate Toggle Status | Clickable checkbox/badge in lists and details to mark a deadline/event completed instantly. | LOW | Triggers mutation with query cache invalidation. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| External Calendar Sync | Sync with Google/Outlook calendars. | High complexity (OAuth, webhooks, refresh tokens, conflict resolution). | Keep local scheduling robust; export to standard `.ics` file instead. |
| Drag-and-Drop Rescheduling | Dragging events in calendar grid to change dates. | High client state complexity, potential database sync/permission errors. | Click-to-edit drawer or form page for standard date updates. |

## Feature Dependencies

```
[Unified Calendar]
    ├──requires──> [General Events Fetch]
    ├──requires──> [Process Deadlines Fetch]
    
[Status/Category Filters] ──enhances──> [Unified Calendar]

[Date Validation] ──prevents──> [Invalid Persistence State]
```

### Dependency Notes

- **Unified Calendar requires General Events and Process Deadlines:** Since deadlines (prazos) and generic events are separate database tables, they must be fetched and mapped to a common calendar item structure.
- **Date Validation prevents Invalid State:** Enforcing that end dates are after start dates prevents UI glitches and database corruption (e.g. negative event duration).

## MVP Definition

### Launch With (v1.9)

Goal: Finalize and improve the scheduling module with solid UI, filters, and validations.

- [x] **Date constraints validation** — ensure `dataFim` >= `dataInicio` on frontend form and backend API.
- [x] **Category and Status filtering** — allow filtering events by type and completion status.
- [x] **Process Filter** — allow filtering events by linked process.
- [x] **Visual UI improvements** — premium design layout, better month grid, visual badges for categories, clear status indicators.
- [x] **Unified deadlines and events display** — optional/differentiating addition of deadlines in calendar view.

### Add After Validation (v1.x)

- [ ] **Week and Day view** — toggle calendar view between Month, Week, and Day grid.
- [ ] **ICS Export** — download an event or list as an `.ics` file for easy import into Outlook/Google.

### Future Consideration (v2+)

- [ ] **Recurring Events** — scheduling events that repeat daily, weekly, or monthly.
- [ ] **Automatic Reminders** — email or push notifications before an event/deadline.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Form Date Order Validation | HIGH | LOW | P1 |
| Status / Category Filter | HIGH | LOW | P1 |
| Visual Grid Improvements | HIGH | MEDIUM | P1 |
| Unified Deadlines in Calendar | HIGH | MEDIUM | P2 |
| ICS Export | MEDIUM | LOW | P3 |

---
*Feature research for: LexCV Scheduling Module Improvements*
*Researched: 2026-06-17*
