# Phase 21 Summary

**Completed:** 2026-06-03

## Outcome

- Endpoint `POST /api/v1/clientes/merge` para merge manual
- Página `/clientes/merge` para seleção e execução do merge
- Regra aplicada: manter conta-corrente do principal e copiar apenas campos em falta do duplicado

## Key Files

- Backend: [ClienteMergeRequest.java](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/backend/src/main/java/com/lexcv/dtos/ClienteMergeRequest.java), [ResourceController.java](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/backend/src/main/java/com/lexcv/controllers/ResourceController.java)
- Frontend: [use-clientes.ts](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/hooks/use-clientes.ts), [clientes/merge/page.tsx](file:///c:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/app/(dashboard)/clientes/merge/page.tsx)

