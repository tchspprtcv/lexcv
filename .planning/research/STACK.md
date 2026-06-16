# STACK.md — Stack additions/changes for VPS Deployment

## Docker & Docker Compose
- **Docker Engine**: Necessário para correr os containers na VPS.
- **Docker Compose**: Para orquestração multi-container local na VPS (Frontend, Backend, PostgreSQL, Reverse Proxy).
- **Multi-stage Builds**:
  - **Frontend**: Node.js Alpine build stage -> runner stage otimizado.
  - **Backend**: Maven/JDK build stage -> JRE running stage (eclipse-temurin).

## Base de Dados (PostgreSQL)
- **Postgres Docker Image**: Alpine-based PostgreSQL image para baixo consumo.
- **Data Volumes**: Persistência de dados montando pasta local da VPS no container (`/var/lib/postgresql/data`).

## Servidor Web / Reverse Proxy
- **Caddy ou Nginx**:
  - *Caddy (Recomendado)*: Configuração extremamente simples, geração e renovação automática de SSL Let's Encrypt sem scripts adicionais.
  - *Nginx*: Alternativa clássica, necessita de Certbot para SSL.

## CI/CD Pipeline
- **GitHub Actions**:
  - `appleboy/ssh-action` para executar comandos ssh na VPS.
  - Docker Registry (GitHub Packages / Docker Hub) ou transferência direta via SSH e compilação na VPS para poupar rede/recursos.
