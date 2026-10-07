# Phase 139: Fecho — Isolamento, Segurança, Migrações e UAT

**Goal**: O marco fecha com prova de que nenhum dado fiscal atravessa tenants, as instalações existentes arrancam com as novas tabelas em ambos os modos de esquema, as credenciais não vazam e a análise estática continua limpa.
**Depends on**: Phase 138 (e todas as anteriores do Milestone v3.0)
**Requirements**: OPER-01, OPER-02, OPER-03, OPER-04

---

## Deliverables Plan
1. **139-01-PLAN.md (OPER-01)**: Multi-tenant isolation verification across 2 offices and platform (backend + database queries).
2. **139-02-PLAN.md (OPER-02)**: DB migrations idempotency, Flyway/manual script alignment, and `ddl-auto=update` & `validate` compatibility verification.
3. **139-03-PLAN.md (OPER-03)**: Environment variables and secrets hygiene audit (.env.example, docker-compose.yml, deploy.yml, no leakage in logs/exceptions).
4. **139-04-PLAN.md (OPER-04)**: SpotBugs + FindSecBugs SAST zero-finding clean run without adding custom exclusions.
5. **139-05-PLAN.md (OPER-01..04)**: Final full-suite execution (unit, IT, vitest, verify scripts, production build) and milestone verification record.
