---
phase: 38
name: Reverse Proxy e HTTPS
status: ready
gathered: 2026-06-16
---

# Phase 38: Reverse Proxy e HTTPS - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Configurar o Caddy como reverse proxy para receber pedidos externos na VPS e encaminhá-los para os containers adequados com SSL/HTTPS automático via Let's Encrypt.

Phase 37 already:
- Created Caddy in docker-compose.yml (ports 80/443, Caddyfile mounted)
- Created local Caddyfile (routes /api/* → backend:8080, /* → frontend:3000, port 80 only)
- Created docker-compose.prod.yml (caddy service with restart: unless-stopped)

Phase 38 adds:
- Caddyfile.prod with HTTPS/TLS config using Let's Encrypt ACME
- docker-compose.prod.yml caddy service override to mount Caddyfile.prod instead of Caddyfile
- Documentation for VPS firewall requirements

</domain>

<decisions>
## Implementation Decisions

### HTTPS mode
Automatic HTTPS via Caddy's built-in Let's Encrypt ACME integration. Caddy provisions and renews certificates automatically when a real domain is configured. No manual certbot or SSL config needed.

### Domain approach
Domain parameterized via env var `DOMAIN_NAME` in `Caddyfile.prod`. The file uses `{$DOMAIN_NAME}` Caddy placeholder syntax. The root `.env.example` will include `DOMAIN_NAME=yourdomain.com`. At deploy time, operator sets real domain in `.env`.

### Caddyfile.prod location
`Caddyfile.prod` at repository root. `docker-compose.prod.yml` overrides the caddy service volumes to mount `./Caddyfile.prod:/etc/caddy/Caddyfile:ro` instead of `./Caddyfile`.

### HTTP → HTTPS redirect
Caddy handles this automatically when a domain (not just an IP) is configured — no explicit redirect block needed in Caddyfile.prod.

### VPS firewall
Document in a `DEPLOYMENT.md` file: open ports 80/tcp and 443/tcp. Port 80 is required for ACME HTTP-01 challenge. Port 443 is for HTTPS traffic. This is VPS-provider specific (not automated).

</decisions>

<code_context>
## Existing Code Insights

- `Caddyfile` (repo root): local dev config — `:80 { handle /api/* { ... } handle { ... } }` (created in Phase 37)
- `docker-compose.yml`: caddy service mounts `./Caddyfile:/etc/caddy/Caddyfile:ro`, volumes `caddy_data` and `caddy_config` already declared
- `docker-compose.prod.yml`: caddy service has `restart: unless-stopped` but no Caddyfile.prod mount yet
- `.env.example` (root): needs `DOMAIN_NAME` variable added

</code_context>

<specifics>
## Specific Ideas

Caddyfile.prod structure:
```
{$DOMAIN_NAME} {
  handle /api/* {
    reverse_proxy backend:8080
  }
  handle {
    reverse_proxy frontend:3000
  }
}
```

docker-compose.prod.yml caddy service volumes override:
```yaml
caddy:
  volumes:
    - ./Caddyfile.prod:/etc/caddy/Caddyfile:ro
    - caddy_data:/data
    - caddy_config:/config
```

DEPLOYMENT.md should cover:
- Prerequisites (Docker, Docker Compose on VPS)
- Firewall: open 80/tcp and 443/tcp
- Clone repo, copy .env.example to .env, set DOMAIN_NAME + secrets
- Run: docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
- Verify: curl https://yourdomain.com/api/v1/setup/status

</specifics>

<deferred>
## Deferred Ideas

- Wildcard certificates (requires DNS challenge, not needed for single domain)
- IP-based TLS (self-signed) — not needed if domain is available
- Caddy on-prem CA — use Let's Encrypt directly

</deferred>
