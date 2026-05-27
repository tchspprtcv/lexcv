# 🌐 Base da API

```
/api/v1
```

### 🔐 Autenticação

* Base: JWT (ou Keycloak)
* Header:

```
Authorization: Bearer <token>
```

***

# 🔑 1. AUTH / AUTENTICAÇÃO

## Login

```
POST /api/v1/auth/login
```

**Request**

```json
{
  "email": "user@email.com",
  "password": "123456"
}
```

**Response**

```json
{
  "access_token": "...",
  "refresh_token": "...",
  "user": {
    "id": "...",
    "nome": "..."
  }
}
```

***

## Refresh Token

```
POST /api/v1/auth/refresh
```

***

## Current User

```
GET /api/v1/auth/me
```

***

# 👥 2. USERS

```
GET    /api/v1/users
POST   /api/v1/users
GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}
```

***

# 🔐 3. ROLES & PERMISSIONS

```
GET /api/v1/roles
POST /api/v1/roles

GET /api/v1/permissions
```

***

# 👤 4. CLIENTES

```
GET    /api/v1/clientes
POST   /api/v1/clientes
GET    /api/v1/clientes/{id}
PUT    /api/v1/clientes/{id}
DELETE /api/v1/clientes/{id}
```

### 🔍 Filtros importantes

```
GET /api/v1/clientes?nome=joao&nif=123
```

***

# ⚖️ 5. PROCESSOS

```
GET    /api/v1/processos
POST   /api/v1/processos
GET    /api/v1/processos/{id}
PUT    /api/v1/processos/{id}
DELETE /api/v1/processos/{id}
```

***

## 🔗 Sub-recursos

### Partes

```
GET  /api/v1/processos/{id}/partes
POST /api/v1/processos/{id}/partes
```

***

### Fases

```
GET  /api/v1/processos/{id}/fases
POST /api/v1/processos/{id}/fases
PUT  /api/v1/processos/{id}/fases/{faseId}
```

***

### Movimentações

```
GET  /api/v1/processos/{id}/movimentacoes
POST /api/v1/processos/{id}/movimentacoes
```

***

# 📅 6. AGENDA / EVENTOS

```
GET    /api/v1/eventos
POST   /api/v1/eventos
GET    /api/v1/eventos/{id}
PUT    /api/v1/eventos/{id}
DELETE /api/v1/eventos/{id}
```

***

## 🔍 Filtros críticos (frontend vai usar muito)

```
GET /api/v1/eventos?dataInicio=2026-01-01&dataFim=2026-01-31
GET /api/v1/eventos?processoId=xxx
GET /api/v1/eventos?concluido=false
```

***

# 🔔 7. NOTIFICAÇÕES

```
GET /api/v1/notificacoes
PUT /api/v1/notificacoes/{id}/read
```

***

# 📂 8. DOCUMENTOS

## Upload

```
POST /api/v1/documentos/upload
```

**Multipart/form-data**

***

## CRUD

```
GET    /api/v1/documentos
GET    /api/v1/documentos/{id}
DELETE /api/v1/documentos/{id}
```

***

## Download

```
GET /api/v1/documentos/{id}/download
```

***

## Por processo

```
GET /api/v1/processos/{id}/documentos
```

***

# 🧾 9. MOVIMENTAÇÕES

```
GET    /api/v1/movimentacoes
POST   /api/v1/movimentacoes
```

***

# 💰 10. FINANCEIRO

## Honorários

```
GET    /api/v1/honorarios
POST   /api/v1/honorarios
GET    /api/v1/honorarios/{id}
```

***

## Pagamentos

```
POST /api/v1/pagamentos
GET  /api/v1/honorarios/{id}/pagamentos
```

***

## Conta Corrente

```
GET /api/v1/clientes/{id}/conta-corrente
```

***

# 📊 11. DASHBOARD

## KPIs principais

```
GET /api/v1/dashboard
```

**Response**

```json
{
  "total_clientes": 120,
  "processos_ativos": 45,
  "prazos_vencer": 8,
  "valores_recebidos_mes": 500000
}
```

***

# 📊 12. AUDITORIA

```
GET /api/v1/audit
```

### Filtros

```
GET /api/v1/audit?entidade=PROCESSO
GET /api/v1/audit?userId=xxx
```

***

# ⚙️ 13. MULTI-TENANT (IMPORTANTE)

Não expor no endpoint.

👉 Resolver via:

* JWT contendo `tenant_id`
* Ou header:

```
X-Tenant-ID
```

***

# ⚡ Convenções REST (IMPORTANTE)

### ✅ Padrões usados

* plural: `/clientes`, `/processos`
* sub-recursos hierárquicos
* uso de query params para filtros

***

### ✅ Status codes

* 200 OK
* 201 Created
* 204 No Content
* 400 Bad Request
* 401 Unauthorized
* 404 Not Found

***

# 🚀 Integração com Next.js (dica prática)

No frontend:

```
/services/api.ts
```

* centralizar chamadas
* usar React Query

***

# ✅ Conclusão

Tens agora uma API:

* ✅ Completa para MVP
* ✅ Alinhada com o modelo de dados
* ✅ Pronta para Spring Boot
* ✅ Fácil de consumir no Next.js
* ✅ Preparada para evolução institucional

