# FEATURES.md — Deployment Features & Capabilities

## Table Stakes (Requisitos Mínimos)
- **Persistência de Dados**: Garantir que os dados do PostgreSQL não se perdem no restart do container.
- **Segurança de Variáveis**: Gestão de passwords e chaves via `.env` (nunca commitados em git).
- **Reverse Proxy**: Encaminhamento seguro do tráfego das portas 80/443 para os containers respetivos.
- **SSL / HTTPS**: Configuração de certificados automáticos para o domínio futuro.

## Differentiators (Melhorias Otimizadas)
- **CI/CD Automatizado**: Ao fazer push para o branch `main`, o build e deploy acontecem de forma automática.
- **Zero-downtime Relativo**: Docker Compose atualiza containers em background.
- **Backups Automáticos**: Script de cron job na VPS para exportar backups do Postgres para armazenamento seguro.

## Anti-Features (O que NÃO fazer)
- **PostgreSQL sem volumes**: Perda total de dados se o container for apagado.
- **Secrets expostos**: Guardar passwords no repositório ou em ficheiros expostos publicamente.
