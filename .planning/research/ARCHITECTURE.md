# Architecture Research

**Domain:** Legal Case Management / Scheduling Module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                       Frontend (Web)                        │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐  │
│  │ AgendaPage   │  │ EventoForm    │  │ useEventos Hook  │  │
│  └──────┬───────┘  └───────┬───────┘  └────────┬─────────┘  │
│         │                  │                   │            │
├─────────┼──────────────────┼───────────────────┼────────────┤
│         ▼                  ▼                   ▼            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                     apiFetch Utility                  │  │
│  └─────────────────────────┬─────────────────────────────┘  │
└────────────────────────────┼────────────────────────────────┘
                             │ (HTTPS / CORS / JSON)
┌────────────────────────────▼────────────────────────────────┐
│                   Backend (Spring Boot API)                 │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  ResourceController                  │  │
│  └─────────────────────────┬─────────────────────────────┘  │
│                            │                                │
│                            ▼                                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                  JPA Repositories                     │  │
│  │      (EventoRepository / PrazoRepository)             │  │
│  └─────────────────────────┬─────────────────────────────┘  │
├────────────────────────────┼────────────────────────────────┤
│                            ▼                                │
│                      PostgreSQL DB                          │
│                (t_evento / t_prazo tables)                  │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `AgendaPage` | Displays the monthly calendar grid, handles filters, and links to events. | React component using Tailwind CSS. |
| `useEventos` / `usePrazos` | TanStack Query hooks that handle cache, fetching, and mutations. | Calls to `apiFetch` with state mapping. |
| `ResourceController` | Exposes REST endpoints for CRUD operations and filters, applying RBAC security. | Spring Boot `@RestController` with method-level authorization. |
| `Evento` / `Prazo` | Entity models representing generic calendar events and process-specific deadlines. | JPA `@Entity` with tenant separation fields. |

## Recommended Project Structure

```
web/src/
├── app/
│   └── (dashboard)/
│       └── agenda/
│           ├── page.tsx          # Calendar view
│           ├── novo/page.tsx     # Creation page
│           └── [id]/
│               ├── page.tsx      # Detail page
│               └── editar/page.tsx # Editing page
├── hooks/
│   ├── use-eventos.ts            # Fetching & mutations for Evento
│   └── use-prazos.ts             # Fetching & mutations for Prazo
├── schemas/
│   └── eventos.ts                # Zod validation schema (camelCase)
└── types/
    └── eventos.ts                # TypeScript interfaces (camelCase)
```

## Architectural Patterns

### Pattern 1: Property Naming Consistency (camelCase Alignment)
To match Spring Boot Jackson default serialization, all frontend interfaces, validation schemas, API calls, and components should be aligned to camelCase. This avoids property mapping mismatches (e.g. `data_inicio` from mock API vs `dataInicio` in the real backend model).

**Example:**
```typescript
// web/src/types/eventos.ts
export interface Evento {
  id: number;
  tenantId: string;
  processoId?: string;
  titulo: string;
  descricao?: string;
  dataInicio: string; // ISO string without timezone offset
  dataFim: string;   // ISO string without timezone offset
  prioridade: "BAIXA" | "MEDIA" | "ALTA";
  concluido: boolean;
}
```

### Pattern 2: Unified Calendar Interface mapping
Since Deadlines (`t_prazo`) and Events (`t_evento`) are different database models, they should be mapped into a unified UI format on the client side before rendering in the calendar grid.

**Example:**
```typescript
interface UnifiedCalendarItem {
  id: string; // "evento-1" or "prazo-uuid"
  title: string;
  startDate: Date;
  endDate: Date;
  type: "PRAZO" | "EVENTO_BAIXA" | "EVENTO_MEDIA" | "EVENTO_ALTA";
  url: string;
  concluido: boolean;
}
```

## Data Flow

### Request Flow

```
[User Form Submit]
    ↓
[zod Validation (refinement check)]
    ↓
[useCreateEvento Mutation]
    ↓
[apiFetch: POST /api/v1/eventos]
    ↓
[ResourceController: createEvento] -> [Hibernate: INSERT into t_evento] -> [Response]
```

### Key Data Flows

1. **Deadlines & Events Fetching:** The calendar page calls `useEventos` and `usePrazos` (or all deadlines in the current tenant) concurrently.
2. **State Invalidation:** Successful creation or completion toggling of an event invalidates the `["eventos", "list"]` query cache, forcing an immediate refresh without manual page reload.

## Anti-Patterns

### Anti-Pattern 1: Hardcoding Categories based on Title Substrings
**What people do:** Check `e.titulo.toLowerCase().includes("prazo")` in the UI to assign categories.
**Why it's wrong:** Extremely fragile; if the title changes or is in another language, the category mapping fails.
**Do this instead:** Use structured properties. Deadlines come from the `/prazos` endpoint (automatic type `PRAZO`), and events carry their type in the `tipo` database column (e.g. `AUDIENCIA`, `DILIGENCIA`, `REUNIAO`).

### Anti-Pattern 2: Timezone Offsets in LocalDateTime parsing
**What people do:** Parse string inputs containing `Z` or `+HH:MM` using `LocalDateTime.parse()`.
**Why it's wrong:** Throws a `DateTimeParseException` because `LocalDateTime` lacks offset semantics.
**Do this instead:** Parse as `ZonedDateTime` or `OffsetDateTime` on the backend and convert to `LocalDateTime`, or ensure the frontend strips the timezone suffix before posting.

---
*Architecture research for: LexCV Scheduling Module Improvements*
*Researched: 2026-06-17*
