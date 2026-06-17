# Requirements: LexCV Scheduling Module (v1.9)

## Scoped Requirements

These requirements are targeted for completion in the current milestone (v1.9).

### Category: Conformity and Error Handling (AGD-31)

- [x] **AGD-31-01**: The frontend must display detailed visual feedback (via toaster or inline) when API validation errors or request failures occur during event creation, updates, or completion toggling.
- [x] **AGD-31-02**: The agenda dashboard and details pages must render clean loading indicators (spinners or skeletons) and error states during data fetching.

### Category: Visual and UX Improvements (AGD-32)

- [x] **AGD-32-01**: The calendar view must provide interactive filters for Event Category (Prazos, Audiências, Diligências, Reuniões) and Completion Status (Pendente, Concluído).
- [x] **AGD-32-02**: The calendar view must display process-specific deadlines (`t_prazo`) alongside generic calendar events (`t_evento`) unified in a single grid layout.
- [x] **AGD-32-03**: The calendar view must provide a filter selector to limit displayed events to a single Process.

### Category: Persistence and Validation Consistency (AGD-33)

- [x] **AGD-33-01**: The frontend typescript interfaces, validation schemas, query parameters, and components must use camelCase naming conventions (`dataInicio`, `dataFim`, `processoId`, `tenantId`) to match the Spring Boot Jackson default serialization.
- [x] **AGD-33-02**: Both the frontend form validation (Zod) and the backend REST API must validate that the event's end date (`dataFim`) is greater than or equal to the start date (`dataInicio`), returning a clear validation message otherwise.
- [x] **AGD-33-03**: The backend REST API must validate query string date parameters (`dataInicio` / `dataFim`) robustly, returning HTTP 400 Bad Request if formatting is invalid instead of silently swallowing parsing exceptions.

## Future Requirements (Deferred)

- **AGD-FW-01**: Integration with Microsoft Outlook and Google Calendar using OAuth 2.0.
- **AGD-FW-02**: Interactive drag-and-drop of calendar events to reschedule directly from the grid.
- **AGD-FW-03**: Support for recurring events (daily, weekly, monthly, custom).
- **AGD-FW-04**: Automatic email notifications and browser push alerts before deadlines/events.

## Out of Scope

- Synchronizing events dynamically with external institutional calendar servers (NOSi).
- SMS notification dispatch (out of scope for MVP).

## Traceability

This section maps requirements to phase implementation plans.

| Requirement | Mapped Phase | Status |
|-------------|--------------|--------|
| **AGD-31-01** | Phase 41 | Satisfied |
| **AGD-31-02** | Phase 42 | Satisfied |
| **AGD-32-01** | Phase 42 | Satisfied |
| **AGD-32-02** | Phase 42 | Satisfied |
| **AGD-32-03** | Phase 42 | Satisfied |
| **AGD-33-01** | Phase 40 | Satisfied |
| **AGD-33-02** | Phase 41 | Satisfied |
| **AGD-33-03** | Phase 41 | Satisfied |

---
*Requirements updated: 2026-06-17*
