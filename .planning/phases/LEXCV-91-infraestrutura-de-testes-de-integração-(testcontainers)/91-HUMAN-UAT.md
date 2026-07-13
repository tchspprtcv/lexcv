---
status: partial
phase: 91-infraestrutura-de-testes-de-integração-(testcontainers)
source: [91-VERIFICATION.md]
started: 2026-07-13T15:50:00.000Z
updated: 2026-07-13T15:50:00.000Z
---

## Current Test

[awaiting human/CI testing — blocked by local sandbox Docker Desktop 4.80 npipe / Testcontainers 1.20.4 docker-java incompatibility, confirmed reproducible, not a code defect]

## Tests

### 1. NotificacaoRepositoryIT passes against real postgres:16-alpine
expected: All 4 test methods in `NotificacaoRepositoryIT` (tenant/destinatario scoping, both CAST-null-guarded filters, Pageable ordering/totals) pass when run with a working Docker daemon (e.g. `mvn -B verify` on a GitHub Actions `ubuntu-latest` runner, or any local machine without this sandbox's npipe issue).
result: [pending]

### 2. ParecerVersaoConcorrenciaIT passes against real postgres:16-alpine
expected: Both test methods (two-thread PESSIMISTIC_WRITE lock race asserting numeroVersao set {1,2}; DB unique-constraint backstop via saveAndFlush inside assertThrows) pass when run with a working Docker daemon.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps

Both items are blocked by the same confirmed environment limitation (Docker Desktop 4.80 / Testcontainers 1.20.4 npipe transport incompatibility in this specific Windows sandbox — independently reproduced by the orchestrator, not just claimed by the executor). Code compiles, is correctly wired into Surefire/Failsafe separation, and is gated in CI (`.github/workflows/deploy.yml` `test` job, Phase 91-03). Expected to resolve on the next push to `master` via GitHub Actions `ubuntu-latest`, which does not have this Windows-specific issue. Analogous to this project's documented `MINIO_ENDPOINT` blocker pattern.
