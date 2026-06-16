# LexCV VPS Deployment Guide

Step-by-step runbook for deploying LexCV on a fresh Ubuntu/Debian VPS with automatic HTTPS via Caddy and Let's Encrypt.

## Prerequisites

- VPS running Ubuntu 22.04+ or Debian 12+
- Docker Engine 24+ and Docker Compose v2:
  ```
  apt update && apt install -y docker.io docker-compose-plugin
  ```
- A domain name (e.g. `lexcv.example.com`) with an **A record** pointing to the VPS public IP
- DNS must fully propagate before starting the stack — Caddy's ACME HTTP-01 challenge requires the domain to resolve to the VPS

## Firewall

Open required ports before starting the stack. Caddy's ACME HTTP-01 challenge requires port 80 to be reachable from the internet; port 443 serves HTTPS traffic.

```bash
ufw allow 22/tcp   # SSH
ufw allow 80/tcp   # ACME challenge + HTTP redirect
ufw allow 443/tcp  # HTTPS
ufw enable
```

## Deploy Steps

### 1. Clone the repository

```bash
git clone https://github.com/tchspprtcv/lexcv.git
cd lexcv
```

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` — required changes:

| Variable | Value |
|----------|-------|
| `DOMAIN_NAME` | `your-actual-domain.com` |
| `POSTGRES_PASSWORD` | Strong random password |
| `JWT_SECRET` | Base64-encoded random secret, minimum 32 bytes |
| `CORS_ALLOWED_ORIGINS` | `https://your-actual-domain.com` |
| `SEED_ENABLED` | `true` on first run only to seed admin user; set `false` after |

Generate a secure JWT secret:
```bash
openssl rand -base64 48
```

### 3. Start the stack

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 4. Check Caddy logs

```bash
docker compose logs -f caddy
```

Caddy will log certificate provisioning. First startup can take 30–60 seconds while Let's Encrypt issues the certificate.

## Verify

Once the stack is up and the certificate is provisioned:

```bash
curl -I https://your-actual-domain.com/api/v1/setup/status
```

Expected: `HTTP/2 200` with a JSON body. If you see a redirect loop or TLS error, check that:
1. DNS A record points to the correct IP
2. Ports 80 and 443 are open in the firewall
3. `DOMAIN_NAME` in `.env` matches the DNS record exactly

## Image Registry (CI/CD)

Set `REGISTRY` and `IMAGE_TAG` in `.env` to pull pre-built images from GitHub Container Registry instead of building on the VPS:

```
REGISTRY=ghcr.io/your-org
IMAGE_TAG=v1.0.0
```

When both are set, the prod override uses pre-built images (`backend.image` and `frontend.image`). Pull and restart:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Updates

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Certificate Renewal

Caddy renews Let's Encrypt certificates automatically — no cron job or manual intervention needed. Certificates are stored in the `caddy_data` Docker named volume. **Do not delete this volume** or certificates will be re-issued from scratch (subject to Let's Encrypt rate limits).

## GitHub Actions — Required Secrets

Configure the following secrets in the GitHub repository under Settings → Secrets and variables → Actions → New repository secret.

| Secret | Description | Example |
|---|---|---|
| `VPS_HOST` | VPS IP address or hostname | `123.45.67.89` |
| `VPS_USER` | SSH login user on the VPS | `root` or `deploy` |
| `VPS_SSH_KEY` | Private SSH key (no passphrase). Paste the full key including `-----BEGIN...-----END` lines. Generate with `ssh-keygen -t ed25519 -C lexcv-deploy` and add the public key to `~/.ssh/authorized_keys` on the VPS. | (multiline) |
| `VPS_PORT` | SSH port on the VPS | `22` |
| `GHCR_PAT` | GitHub Personal Access Token with `read:packages` scope. Create at github.com → Settings → Developer settings → Personal access tokens (classic). The VPS uses this to `docker login ghcr.io` and pull images. | `ghp_...` |

`GITHUB_TOKEN` is automatically provided by GitHub Actions with `packages: write` permission — no manual setup required.

### Workflow Behavior

| Event | Build images | Push to GHCR | Deploy to VPS |
|---|---|---|---|
| Push to `main` | yes | yes | yes |
| Pull request to `main` | yes | no | no |

Images are tagged with both `:latest` and the git SHA (`:${{ github.sha }}`). The VPS always pulls `:latest`.

### First Deploy Checklist

Before the workflow runs for the first time:
1. All five secrets above are set in GitHub.
2. The deploy user's public SSH key is in `~/.ssh/authorized_keys` on the VPS.
3. `/opt/lexcv` exists on the VPS and contains `docker-compose.yml`, `docker-compose.prod.yml`, `Caddyfile.prod`, and a valid `.env` file (see `.env.example`).
4. Docker and Docker Compose plugin are installed on the VPS (see Phase 38 setup steps).
