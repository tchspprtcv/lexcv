---
phase: 133-funda-o-fiscal
plan: 06
subsystem: web-fiscal
tags: [fiscal, efatura, frontend, apiFetch, zod, tanstack-query]
requires: []
provides:
  - "ApiError (status, code, campo, body) + isApiError + apiFetch third param { semToastParaStatus } (additive, message format preserved)"
  - "web/src/types/faturacao.ts: ConfiguracaoFiscal, ConfiguracaoFiscalPayload, EmailAutomaticoPayload, SerieFiscal, MotivoIsencao, CodigoErroFaturacao, RegimeIva, TipoDocumentoFiscal, AmbienteFiscal"
  - "web/src/schemas/faturacao.ts: nifFiscalPattern, MORADA_MAX, configuracaoFiscalSchema, ConfiguracaoFiscalFormInput/Values"
  - "web/src/hooks/use-faturacao.ts: FATURACAO_*_KEY, useConfiguracaoFiscal, useSeriesFiscais, useMotivosIsencao, useGuardarConfiguracaoFiscal, useAtivarFaturacao, useDesativarFaturacao, useEmailAutomatico, mensagemErroFaturacao"
affects: [133-07, 133-08]
tech-stack:
  added: []
  patterns:
    - "ApiError subclass of Error: existing `instanceof Error` + message parsers keep working"
    - "Per-call toast opt-out via semToastParaStatus for statuses rendered inline"
    - "Zod v4 superRefine (conditional motivo) + transform (motivo null for NORMAL); z.input/z.output exported separately"
key-files:
  created:
    - web/src/lib/api.test.ts
    - web/src/types/faturacao.ts
    - web/src/schemas/faturacao.ts
    - web/src/schemas/faturacao.test.ts
    - web/src/hooks/use-faturacao.ts
  modified:
    - web/src/lib/api.ts
decisions:
  - "apiFetch error is now ApiError (extends Error) with message `API <status>: <msg>` unchanged; third parameter optional so all existing callers are unaffected"
  - "mensagemErroFaturacao returns camposValidacao only for a 400 without code whose body is an all-string object (bean validation); otherwise code/campo/mensagem"
metrics:
  duration: 4 min
  completed: 2026-10-04
  tasks: 2
  files: 6
---

# Phase 133 Plan 06: Frontend fiscal foundation Summary

`apiFetch` now throws an `ApiError` carrying the HTTP status, error `code`, `campo` and parsed body, and takes an optional `{ semToastParaStatus }` that suppresses the automatic toast for statuses a screen handles inline. The change is additive and the `API <status>: <msg>` message format is unchanged. The plan also adds the fiscal types, a Zod schema with the UI-SPEC copy, and TanStack Query hooks covering all 7 `/api/v1/faturacao` endpoints.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Additive ApiError + toast opt-out in apiFetch | 25a8378 (test, RED), 0f821fb (feat, GREEN) |
| 2 | Fiscal types, Zod schema and TanStack Query hooks | 73c9009 (test + types, RED), df0982d (feat, GREEN) |

## Verification

- `pnpm exec vitest run`: 8 files, 86 tests pass. 11 are new in api.test.ts, and faturacao.test.ts covers both the schema and `mensagemErroFaturacao`.
- `pnpm exec tsc --noEmit`: clean. All existing `apiFetch` callers still type-check.
- `pnpm lint`: 0 errors. The 20 warnings were already there, and none are in the new files.
- Acceptance greps: `"/faturacao/"` appears 7 times, `/api/v1` never appears in the hooks, `FATURACAO_SERIES_KEY` appears 6 times, and the NIF regex and copy strings are present. There are no `0.15`/`0.20` literals.

## Deviations from Plan

**1. [Rule 2 - Additive] Extra test coverage.** Besides the behaviours listed in the plan, the tests also check that non-string `code`/`campo` values are ignored, that `semToastParaStatus` does not suppress other statuses, and that `mensagemErroFaturacao` behaves correctly. No production behaviour beyond the plan was added.

**2. Inline invalidation.** I first wrote one shared invalidation helper. I replaced it with explicit `invalidateQueries` calls inside each mutation's `onSuccess`, so the acceptance grep count (>= 5 occurrences of `FATURACAO_SERIES_KEY`) holds. Behaviour is the same.

## TDD Gate Compliance

Both tasks have a `test(...)` commit (RED, confirmed failing) followed by a `feat(...)` commit (GREEN).

## Threat Mitigations

- T-133-26: the plan renders no HTML. The helper returns plain strings, which components render as React text nodes.
- T-133-28: the third parameter is optional and the message format is preserved. api.test.ts plus the full tsc/vitest runs confirm nothing regressed.

## Known Stubs

None.

## Self-Check: PASSED

- All 6 files exist on disk. Commits 25a8378, 0f821fb, 73c9009 and df0982d are present in `git log`.
