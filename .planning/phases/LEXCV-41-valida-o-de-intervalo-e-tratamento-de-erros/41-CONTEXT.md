# Phase 41: Validação de Intervalo e Tratamento de Erros (Agenda) - Context

**Gathered:** 2026-06-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Implementar validações de formulário robustas para datas e tratamento de erros de parsing de data na API do backend.

</domain>

<decisions>
## Implementation Decisions

### Zod Validation (Frontend)
- Refine `eventoFormSchema` in `web/src/schemas/eventos.ts` to ensure `dataFim` is not before `dataInicio`.
- Return a localized error message `"A data de fim não pode ser anterior à data de início"` associated with the `dataFim` path.

### Backend Validation (Spring Boot)
- Add validation inside `createEvento` and `updateEvento` in `ResourceController.java` to verify that `dataFim` is not before `dataInicio`. Return HTTP 400 (`BAD_REQUEST`) with a JSON error payload containing a clear message.
- Improve `listEventos` to validate parameter date strings. If `dataInicio` or `dataFim` are invalid ISO-8601 strings and throw parsing exceptions, return HTTP 400 with an informative message rather than silently ignoring the failure.

### Frontend Toast Notification
- Improve the `apiFetch` helper in `web/src/lib/api.ts` to inspect if the rejected server response contains a JSON payload with a `message` or `error` field. If so, display that specific message in the toast notifications instead of the raw JSON or general status text.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/schemas/eventos.ts` — contains the `eventoFormSchema`.
- `web/src/lib/api.ts` — contains the `apiFetch` helper with toast error handling.
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — contains the events list, create, and update endpoints.

</code_context>

<specifics>
## Specific Ideas

- Check and ensure that error messages returned by Zod are mapped properly to HTML input helper labels.

</specifics>

<deferred>
## Deferred Ideas

- None.

</deferred>
