# SUMMARY.md — VPS Deployment Research Synthesis

## Stack Additions
- **Docker Engine + Docker Compose** para orquestração.
- **Reverse Proxy**: Caddy ou Nginx (para SSL automático e encaminhamento de rotas `/` para Next.js e `/api/v1` para Spring Boot).
- **CI/CD**: GitHub Actions para builds remotos e deployment via SSH.

## Feature Table Stakes
- **Persistência de Dados**: Volumes Docker para o PostgreSQL.
- **Configuração Segura**: Ficheiro `.env` na VPS para guardar credenciais de produção.
- **HTTPS Automático**: Certificado SSL gerido pelo Caddy ou Nginx + Certbot.

## Watch Out For (Pitfalls Críticos)
1. **Out of Memory (OOM)**: Evitar compilar código diretamente na VPS Hostinger; usar build steps no GitHub Actions.
2. **Segurança de Rede**: Não expor a porta 5432 do PostgreSQL publicamente.
3. **CORS**: Configurar as origens permitidas no Spring Boot usando variáveis de ambiente.
