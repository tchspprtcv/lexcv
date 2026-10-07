---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 13
subsystem: deployment configuration (SMTP)
tags: [smtp, env, docker-compose, ci]
requires: [137-09]
provides:
  - SMTP_* documented (commented, placeholders only) in backend/.env.example and root .env.example
  - SMTP_* passthrough with ":-" safe defaults in docker-compose.yml, docker-compose.prod.yml, docker-compose.hostinger.yml
  - CI test job pins SMTP_HOST ""
affects: [OPER-03 verification]
tech-stack:
  added: []
  patterns: [EFATURA_* compose passthrough analog]
key-files:
  created: []
  modified:
    - backend/.env.example
    - .env.example
    - docker-compose.yml
    - docker-compose.prod.yml
    - docker-compose.hostinger.yml
    - .github/workflows/deploy.yml
decisions:
  - "Compose defaults: SMTP_HOST/USERNAME/PASSWORD/FROM empty, SMTP_PORT 587, SMTP_STARTTLS true; empty host = not configured (never a startup failure, per 137-09)"
  - "docker-compose.prod.yml repeats the SMTP block (environment maps merge across -f layers), same reasoning as EFATURA"
metrics:
  duration: ~10min
  completed: 2026-10-07
  tasks: 2
  files: 6
---

# Phase 137 Plan 13: SMTP variables on every deployment surface Summary

The six optional SMTP variables are now documented and passed through on every deployment surface, and the defaults are safe. Both `.env.example` files have a Portuguese comment block. It says an empty `SMTP_HOST` means "Não configurado" while the app still starts and issues documents. It says `SMTP_FROM` is required once a host is set, credentials go only in env vars, and the phase's human checkpoint must pass before a real SMTP server is connected. The six variables follow, commented out with placeholders only, plus a commented Mailpit example (localhost:1025, STARTTLS false). All three compose files pass `SMTP_*` with `:-` defaults. The CI test job sets `SMTP_HOST: ""`.

## Tasks

| # | Task | Commit |
|---|------|--------|
| 1 | .env.example files (backend + root) | 8326204 |
| 2 | three compose files + deploy.yml | 24a86ee |

## Verification

- Task 1 gate: all six names present in both files; no `^SMTP_PASSWORD=.+` line. PASS.
- Task 2 gate: `VAR: "${VAR:-` for all six in each compose file; `SMTP_HOST: ""` in deploy.yml. PASS.
- `docker compose --env-file .env.example config -q` passes for docker-compose.yml, docker-compose.hostinger.yml and docker-compose.yml + docker-compose.prod.yml. In the merged prod config: host/username/password/from `""`, port `"587"`, starttls `"true"`.
- No backend code changed, so no Maven run.

## Deviations from Plan

None. The plan was executed as written.

## Threat Flags

None. No new surface; T-137-52/53/54 are mitigated as planned.

## Self-Check: PASSED
