# Phase 100: Infraestrutura — Routing e Deployment - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Encaminhar o container `webpage` (construído na Phase 99) em todos os ambientes de configuração (dev, prod, Hostinger), servido em `/`, sem quebrar nenhuma rota existente (`/login`, `/dashboard`, `/setup`, `/api/*`) que continua a ir para `web/`(`frontend`)/`backend`. Verificado com um `docker compose up` completo, não apenas `pnpm dev`/`mvn test` isolados.

Fase de infraestrutura pura — os 5 success criteria do ROADMAP.md são inteiramente técnicos (serviço arranca, ficheiros de config atualizados, chunks não colidem, CI publica artefacto, smoke test confirma resolução de rede interna). Discuss-phase interativo foi saltado (sem áreas cinzentas de produto/UX a decidir) — ver `<decisions>` abaixo para o racional.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Todas as escolhas de implementação ficam ao critério do planner/executor, guiadas por:
1. **`.planning/research/ARCHITECTURE.md`** (521 linhas, escrito nesta mesma sessão durante a pesquisa de milestone) — contém um plano de arquitetura já completo e concreto para esta fase exata: diffs exatos para os 3 ficheiros Caddy, os 3 ficheiros docker-compose, e `deploy.yml`; secção "Integration Points" tem o "antes → depois" literal de cada ficheiro. **Ler este ficheiro primeiro, por inteiro, antes de planear.**
2. As success criteria explícitas do ROADMAP.md (5 itens, ver `roadmap.get-phase 100`)
3. Convenções já estabelecidas nos ficheiros existentes (Caddyfile, Caddyfile.prod, docker-compose*.yml, deploy.yml)

### Lições de Git History — MANDATÓRIAS, não discricionárias
Estas não são áreas cinzentas — são restrições rígidas derivadas de 4 commits de correção de bugs em produção **nesta mesma sessão**, na área exata que esta fase volta a tocar:
- **`docker-compose.hostinger.yml`'s heredoc inline** (`entrypoint: sh -c "echo '...' > Caddyfile"`) é o **caminho de produção real** — `Caddyfile.prod` NÃO é montado/usado no Hostinger. As 3 fontes de configuração Caddy (`Caddyfile`, `Caddyfile.prod`, o heredoc) são independentes, não DRY — o novo bloco `@webpage` tem de ser aplicado às 3, manualmente, e cada uma verificada.
- **Zero caracteres `$` dentro do heredoc de `docker-compose.hostinger.yml`** — Docker Compose interpola `$VAR`/`${VAR}` em TODO o YAML antes do Caddy sequer ver o conteúdo, incluindo dentro do heredoc. Os commits `67e2120` (domínio) e `534fa92` (hash bcrypt `$2a$10$...`) already partiram Caddy no arranque por causa disto. Qualquer novo bloco `handle`/`reverse_proxy` adicionado ao heredoc usa apenas literais hardcoded (`webpage:3000`, não `{$WEBPAGE_HOST}`).
- `Caddyfile.prod` É um ficheiro real montado — pode continuar a usar `{$DOMAIN_NAME}` nativo do Caddy em segurança; não sofre do bug acima.
- Verificação exige um `docker compose up` real, nunca apenas leitura estática de um dos 3 ficheiros — os bugs anteriores só se manifestaram em runtime.

### Ambiente de Verificação Local
- Docker Desktop foi iniciado nesta sessão especificamente para permitir a verificação `docker compose up` exigida pelo success criterion #1/#3/#5 desta fase — confirmar que está operacional antes de depender dele para verificação.
- **Fora de âmbito para esta fase (e para qualquer execução autónoma):** fazer deploy real para o VPS Hostinger em produção (SSH, `docker compose pull && up -d` no servidor real). Esta fase entrega e verifica os FICHEIROS DE CONFIGURAÇÃO (Caddyfile, docker-compose.hostinger.yml, deploy.yml) localmente via `docker compose up` no ambiente de dev — nunca uma execução real contra a infraestrutura de produção partilhada. Isso requer autorização explícita do utilizador, fora do que este ciclo autónomo cobre.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/Dockerfile` — padrão de referência para o novo `webpage/Dockerfile` (3-stage: deps→builder→runner, `node:22-alpine`, corepack/pnpm, non-root `appuser`, `EXPOSE 3000`, `ENTRYPOINT ["node","server.js"]` a partir de `.next/standalone`)
- `docker-compose.yml`'s serviço `frontend` (linhas 76-89) — padrão exato a espelhar para o novo serviço `webpage` (build context, env vars, depends_on, networks, ports)
- `docker-compose.prod.yml`'s override do `frontend` — padrão para o override `webpage` (image do GHCR, resource limits)
- `docker-compose.hostinger.yml`'s bloco `frontend` completo (sem `ports:` — internal-only) — padrão para o bloco `webpage` no Hostinger
- `.github/workflows/deploy.yml`'s step de build/push do `frontend` (linhas 97-110) — espelhar para um 3º step `webpage`

### Established Patterns
- Serviço de frontend chama-se `frontend` em todos os 3 compose files, nunca `web` (apesar do diretório se chamar `web/`) — o novo serviço chama-se `webpage` (mesmo nome do diretório, sem ambiguidade adicional)
- Roteamento Caddy é `handle` sequencial mutuamente exclusivo, nunca `handle_path` (que stripa o prefixo — Next.js 15+ já serve assets corretamente no `assetPrefix` sem stripping, confirmado contra a doc local instalada)
- Portas dev já mapeadas: postgres 5433, minio 9000/9001, backend 8089, frontend 3003, caddy 80/443 — próxima porta livre para `webpage` é 3004
- Hostinger só expõe a porta do `caddy` (80/443) — todos os outros serviços são internal-only via `lexcv_net`

### Integration Points
- `Caddyfile` (dev) — adicionar bloco `@webpage`/`handle @webpage` entre `/api/*` e o catch-all
- `Caddyfile.prod` — mesmo bloco, entre o bloco `/minio-console*` e o catch-all
- `docker-compose.hostinger.yml`'s heredoc — mesmo bloco, literais hardcoded, zero `$`
- `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml` — novo serviço `webpage`, e adicionar `webpage` ao `depends_on` do serviço `caddy` nos 3 ficheiros
- `.github/workflows/deploy.yml` — novo step de build/push para `ghcr.io/tchspprtcv/lexcv/webpage`
- `webpage/Dockerfile` — não existe ainda, criar nesta fase

</code_context>

<specifics>
## Specific Ideas

- ARCHITECTURE.md já tem os diffs exatos "antes → depois" para todos os 6 ficheiros a modificar (3 Caddy + 3 compose) mais o novo Dockerfile e o novo step de CI — usar como base direta do plano, não reinventar
- Smoke test do success criterion #5 (fetch server-side de setup-status resolve contra a rede Docker interna, não faz "hairpin" via domínio público) é especificamente sobre confirmar que `webpage`'s `BACKEND_API_ORIGIN=http://backend:8080` (nome de serviço Docker, não `localhost`/domínio público) funciona corretamente quando todo o stack corre via `docker compose up` — este é o único item que só pode ser provado com o stack completo a correr, nunca com servidores de dev isolados

</specifics>

<deferred>
## Deferred Ideas

- Favicon/OG-image/robots.txt próprios da `webpage/` — já fora de âmbito desde a Phase 99 (REQUIREMENTS.md Out of Scope); a limitação de roteamento documentada em ARCHITECTURE.md (`webpage/public/*` não é alcançável através do catch-all) permanece um "Known Limitation" aceite, não um bug a corrigir nesta fase
- Deploy real para o VPS Hostinger em produção — fora de âmbito desta execução autónoma; requer autorização explícita do utilizador

</deferred>
