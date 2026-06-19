# Requirements — v2.2 Document Storage MinIO

> Milestone goal: Migrar o armazenamento de documentos do filesystem local para MinIO (object storage S3-compatible), atualizar o componente de upload no frontend e configurar o deploy no Hostinger VPS.

---

## v2.2 Requirements

### Backend — MinIO Integration

- [ ] **MIN-01**: O sistema armazena ficheiros de documentos num bucket MinIO via AWS S3 SDK (S3-compatible) em vez do filesystem local
- [ ] **MIN-02**: O utilizador pode fazer download de um documento através de uma URL pré-assinada temporária gerada pelo backend
- [ ] **MIN-03**: Ao apagar um documento, o objeto correspondente é removido do bucket MinIO
- [ ] **MIN-04**: Os objetos são guardados com prefixo tenant-scoped (`{tenant_id}/{documento_id}/{filename}`) para isolamento de dados

### Frontend — Componente de Upload

- [ ] **MIN-05**: O utilizador vê uma barra de progresso durante o upload de um ficheiro
- [ ] **MIN-06**: O botão de download abre uma URL pré-assinada gerada pelo backend (sem proxy do ficheiro pelo Next.js)
- [ ] **MIN-07**: O utilizador pode arrastar e largar um ficheiro na zona de upload além de clicar para selecionar
- [ ] **MIN-08**: Imagens e PDFs mostram uma pré-visualização inline antes de confirmar o upload

### Deploy — MinIO no Hostinger

- [ ] **MIN-09**: O Docker Compose de produção inclui um serviço MinIO com volume persistente no Hostinger VPS
- [ ] **MIN-10**: As credenciais MinIO (`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, nome do bucket) são configuradas via variáveis de ambiente sem valores hardcoded
- [ ] **MIN-11**: O pipeline CI/CD (GitHub Actions) faz deploy e restart do serviço MinIO junto com os restantes serviços
- [ ] **MIN-12**: A consola de administração MinIO está acessível via rota protegida pelo Caddy

---

## Future Requirements (Deferred)

- Migração de ficheiros existentes do filesystem para MinIO — requer script de migração one-shot e janela de manutenção
- Bucket lifecycle policies (expiração automática de ficheiros antigos) — gestão avançada de armazenamento
- Versioning de documentos — múltiplas versões do mesmo ficheiro

---

## Out of Scope

- Acesso direto do frontend ao MinIO (bypassing backend) — quebra o modelo de segurança tenant-scoped
- CDN na frente do MinIO — fora do scope deste VPS
- Multiple buckets por tenant — um bucket partilhado com prefixos é suficiente para isolamento

---

## Traceability

| REQ-ID | Phase | Plan |
|--------|-------|------|
| MIN-01 | Phase 50 | TBD |
| MIN-02 | Phase 50 | TBD |
| MIN-03 | Phase 50 | TBD |
| MIN-04 | Phase 50 | TBD |
| MIN-05 | Phase 51 | TBD |
| MIN-06 | Phase 51 | TBD |
| MIN-07 | Phase 51 | TBD |
| MIN-08 | Phase 51 | TBD |
| MIN-09 | Phase 52 | TBD |
| MIN-10 | Phase 52 | TBD |
| MIN-11 | Phase 52 | TBD |
| MIN-12 | Phase 52 | TBD |
