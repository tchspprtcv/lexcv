# Stack Research

**Domain:** Legal Case Management / Scheduling Module
**Researched:** 2026-06-17
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Next.js | 16.2.6 | Frontend Application Router | Modern React framework with server-side generation capability, optimized routing, and built-in CSS/theme support. |
| React | 19.2.4 | UI Component Rendering | Standard UI rendering library; leverages modern state management, concurrent rendering features, and hooks. |
| Spring Boot | 3.4.1 | Backend REST API | Standard enterprise Java framework; provides robust transaction management, Hibernate ORM, and integrated security. |
| PostgreSQL | 16+ | Database Storage | Relational database with full ACID compliance, necessary for strict tenant isolation and relational constraints. |
| TanStack Query | 5.87.4 | Data Fetching & Caching | Robust asynchronous state management for React, eliminating manual useEffect data fetching, caching API responses, and enabling query invalidation. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Day.js | 1.11+ | Date & Time Manipulation | Parsing, validating, manipulating, and formatting dates. Essential for timezone conversions and robust ISO-8601 handling between web/backend. |
| Lucide React | 0.543.0 | Icon Library | High-quality icons for visual calendar indicators, filters, and priority levels. |
| Zod | 4.1.5 | Schema Validation | Defining data schemas on the client side to validate forms and ensure query parameters match expected shapes. |
| React Hook Form | 7.62.0 | Form State Management | Handling form state, validation error state integration with Zod, and performant input changes. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| SpotBugs | Java SAST Scanning | Configured via Maven plugin to detect potential null pointers, bad practices, and bugs. |
| ESLint | TypeScript Linting | Used on the web app to enforce naming conventions, import ordering, and hook rules. |

## Installation

If timezone/formatting issues become too complex for native JS Date wrappers, add `dayjs` to the web package:

```bash
# Web dependency
pnpm add dayjs --filter web
```

Otherwise, core APIs can be handled using native `Intl` and JS Date methods to keep the package lightweight.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Native Date + Helper fns | date-fns | If a wider array of date arithmetic functions (e.g., addDays, isWithinInterval) is required without writing custom utils. |
| datetime-local native | shadcn/ui Calendar | If a custom calendar popup element is needed instead of browser native datetimes (e.g., custom range selection UI). |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Moment.js | Outdated, mutable API, extremely large bundle size that impacts web performance. | Day.js or native JS Intl objects. |
| Local state for API responses | Causes UI/cache desynchronization, requires manual synchronization boilerplate. | TanStack Query (`useQuery` / `useMutation`). |

## Stack Patterns by Variant

**If timezone adjustments are needed:**
- Parse on client using standard browser time, convert to UTC string via `.toISOString()` when sending to backend.
- Backend parses input as `OffsetDateTime` or `ZonedDateTime` to capture the offset, then converts to database-local `LocalDateTime`.

**If strict date validation is needed:**
- Implement Zod validation constraints on the client.
- Add Spring Boot standard `@NotNull`, `@FutureOrPresent` validation annotations in DTOs on the backend, throwing standard binding errors.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| next@16.2.6 | react@19.2.4 | Matches the active packages configured in the web application. |
| spring-boot@3.4.1 | java@23 | Utilizes modern Java 23 features and standard Jakarta Persistence annotations. |

## Sources

- [Next.js v16 Documentation](https://nextjs.org/) — Routing and data fetching conventions.
- [Jackson Datatype JSR310](https://github.com/FasterXML/jackson-modules-java8) — Java 8 date/time serialization details.
- [TanStack Query Docs](https://tanstack.com/query/latest/docs/framework/react/overview) — Invalidation and mutation patterns.

---
*Stack research for: LexCV Scheduling Module Improvements*
*Researched: 2026-06-17*
