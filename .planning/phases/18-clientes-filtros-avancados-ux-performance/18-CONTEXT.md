# Phase 18: Clientes — Filtros avançados e UX/Performance - Context

**Gathered:** 2026-06-03
**Status:** Ready for execution

## Boundary

- Evoluir o módulo de Clientes para suportar filtros avançados (tipo, estado/ativo, localidade, intervalo de datas) e pesquisa multi-campo (nome/NIF/telefone/email) com debounce.
- Atualizar contrato de dados (frontend + backend + mock) para incluir campos necessários (`email`, `telefone`, `morada`, `localidade`, `ativo`) e permitir filtragem.

## Constraints

- Frontend: React Query para data fetching; RHF + Zod para forms.
- RBAC/UI: ações e páginas já devem respeitar permissions efetivas (Phase 17).

