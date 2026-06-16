# ARCHITECTURE.md — Deployment Architecture

## Container Topology & Networks
Toda a infraestrutura corre dentro de uma rede virtual do Docker para isolamento completo:

```
                  ┌────────────────────────────────┐
                  │          Hostinger VPS         │
                  │                                │
  Público (443) ──┼──>  Reverse Proxy (Caddy/Nginx)│
                  │             │                  │
                  │             ├──> Next.js (3000)│
                  │             │                  │
                  │             └──> Spring (8080) │
                  │                     │          │
                  │                     └──> DB    │
                  │                         (5432) │
                  └────────────────────────────────┘
```

- **Rede Docker (`lexcv-network`)**: Permite que os containers comuniquem usando os nomes dos serviços (ex: `http://backend:8080`) sem expor portas desnecessárias à internet.
- **Portas Expostas**: Apenas as portas `80` (HTTP) e `443` (HTTPS) são expostas publicamente no Reverse Proxy. O PostgreSQL e o Spring Boot ficam protegidos na rede interna.

## Fluxo de CI/CD
1. O programador faz `git push` para o branch principal.
2. O **GitHub Actions** corre testes, compila as imagens Docker (ou copia o código para a VPS) e liga-se via SSH à VPS.
3. Na VPS, é executado `docker-compose pull` ou `docker compose up -d --build` para atualizar o serviço modificado.
