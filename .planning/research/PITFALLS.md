# PITFALLS.md — Deployment Pitfalls & Prevention

## 1. Out Of Memory (OOM) na VPS
- **Problema**: VPSs de entrada (1GB ou 2GB RAM) frequentemente crasham ou congelam se tentarmos compilar a aplicação Next.js ou Spring Boot diretamente no servidor via `docker-compose build`.
- **Prevenção**: Compilar as imagens Docker no GitHub Actions runner e puxá-las pré-compiladas (via Registry) ou compilar localmente antes de enviar, evitando consumo excessivo de RAM no servidor de produção.

## 2. Exposição de Portas Críticas
- **Problema**: Expor a porta `5432` do PostgreSQL publicamente expõe a base de dados a ataques de força bruta.
- **Prevenção**: Não mapear a porta `5432` para o host de forma pública no `docker-compose.yml` (evitar `5432:5432`), permitindo o acesso apenas a partir da rede interna do Docker.

## 3. Desalinhamento de CORS no Backend
- **Problema**: O frontend e o backend correm sob o mesmo domínio (mas em caminhos/portas diferentes) ou domínios distintos. Sem configuração explícita de CORS no Spring Boot, os pedidos do browser falharão.
- **Prevenção**: Configurar os domínios permitidos no Spring Boot através do `WebMvcConfigurer` ou via variáveis de ambiente injetadas no container.

## 4. Falha na Persistência de Dados
- **Problema**: Reiniciar ou atualizar o container do PostgreSQL apaga todas as tabelas e dados se a pasta `/var/lib/postgresql/data` não estiver mapeada para um volume ou diretório do host.
- **Prevenção**: Mapeamento explícito de volumes no `docker-compose.yml`.
