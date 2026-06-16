---
phase: 38-reverse-proxy-e-https
plan: "01"
subsystem: infrastructure
tags: [caddy, https, tls, lets-encrypt, docker-compose, deployment]
dependency_graph:
  requires: [37-docker-compose-stack]
  provides: [production-https-proxy, deployment-guide]
  affects: [docker-compose.prod.yml, Caddyfile.prod, .env.example, DEPLOYMENT.md]
tech_stack:
  added: []
  patterns: [caddy-automatic-https, docker-compose-override, acme-http01]
key_files:
  created:
    - Caddyfile.prod
    - DEPLOYMENT.md
  modified:
    - docker-compose.prod.yml
    - .env.example
decisions:
  - "Caddyfile.prod uses {$DOMAIN_NAME} placeholder so DOMAIN_NAME env var controls the site block at runtime"
  - "No explicit TLS block in Caddyfile.prod — Caddy provisions Let's Encrypt automatically when a hostname (not :80) is used"
  - "caddy_data and caddy_config volumes not redeclared in docker-compose.prod.yml — inherited from docker-compose.yml"
metrics:
  duration_minutes: 10
  completed_date: "2026-06-16"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 4
---

# Phase 38 Plan 01: Reverse Proxy e HTTPS Summary

**One-liner:** Production Caddy config with DOMAIN_NAME placeholder enabling automatic Let's Encrypt HTTPS, wired into docker-compose.prod.yml via bind-mount override, plus a step-by-step VPS deployment runbook.

## What Was Built

### Task 1 — Caddyfile.prod + docker-compose.prod.yml + .env.example
Commit: `2310e7c`

- **Caddyfile.prod** created at repo root with `{$DOMAIN_NAME}` site block. Two `handle` directives route `/api/*` to `backend:8080` and `/*` to `frontend:3000`. No explicit TLS block — Caddy auto-provisions Let's Encrypt when a hostname is used. HTTP → HTTPS redirect is also automatic.
- **docker-compose.prod.yml** caddy service updated with `volumes:` override mounting `./Caddyfile.prod:/etc/caddy/Caddyfile:ro` (read-only, T-38-02 mitigation) plus `caddy_data:/data` and `caddy_config:/config` (both pre-declared in docker-compose.yml).
- **.env.example** updated: added `DOMAIN_NAME=yourdomain.com` with explanatory comment; added `REGISTRY` and `IMAGE_TAG` placeholders for CI/CD; added production CORS comment noting `https://your-actual-domain.com` (T-38-05 mitigation).

### Task 2 — DEPLOYMENT.md
Commit: `8f2f2af`

- VPS runbook covering: prerequisites (Ubuntu 22.04+ / Docker Engine 24+), DNS propagation requirement, firewall setup (`ufw allow 80/tcp` and `ufw allow 443/tcp`), clone + env config (DOMAIN_NAME, POSTGRES_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS, SEED_ENABLED), `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`, curl verify step, image registry (CI/CD) usage, update procedure, and certificate auto-renewal note for `caddy_data` volume.

## Deviations from Plan

None — plan executed exactly as written.

## Threat Mitigations Applied

| Threat | Mitigation |
|--------|-----------|
| T-38-02 Tampering | Caddyfile.prod mounted with `:ro` flag in docker-compose.prod.yml |
| T-38-03 Info Disclosure | DEPLOYMENT.md instructs operator to set strong unique secrets; .env already in .gitignore |
| T-38-05 EoP via CORS | .env.example and DEPLOYMENT.md explicitly document production CORS value |

## Known Stubs

None — all configuration is complete and operational. The `DOMAIN_NAME=yourdomain.com` placeholder in `.env.example` is intentional documentation; operators replace it in their `.env` at deploy time.

## Self-Check: PASSED

- Caddyfile.prod: FOUND
- DEPLOYMENT.md: FOUND
- Commit 2310e7c: FOUND
- Commit 8f2f2af: FOUND
