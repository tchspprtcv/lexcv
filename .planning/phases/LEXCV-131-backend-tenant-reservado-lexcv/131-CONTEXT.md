---
phase: 131-backend-tenant-reservado-lexcv
gathered: 2026-09-24
status: Ready for planning
mode: Auto-generated during /gsd:autonomous (gsd-sdk unavailable in this container — discuss run inline, condensed)
---

# Phase 131: Backend — Tenant Reservado LexCV - Context

## Phase Boundary

Renomear o tenant reservado da plataforma de "ALCv" para "LexCV" em todo o código backend, com uma
migração SQL documentada para renomear a linha já existente em bases de dados já provisionadas.

## Implementation Decisions

- Renomeação por `sed` case-sensitive (`ALCv` → `LexCV`) em todos os 7 ficheiros de produção e 6
  ficheiros de teste listados em REQUIREMENTS.md BACK-01 — inclui strings literais, comentários
  Javadoc e o nome do método de teste
  `migrar_tenantReservadaALCv_...` → `migrar_tenantReservadaLexCV_...`.
- Migração SQL nova `131-rename-tenant-reservado-lexcv.sql`: `UPDATE t_tenant SET nome = 'LexCV'
  WHERE nome = 'ALCv'` — idempotente, preserva id/plano/ativo da linha existente. Sem esta
  migração, uma base de dados já provisionada ficaria com a linha antiga "ALCv" órfã e
  `seedTenantPlataforma()` inseriria uma segunda linha reservada "LexCV" no arranque seguinte
  (mesma classe de risco já documentada no Javadoc de `TenantRepository` sobre seeding
  concorrente).
- Ambiente sem JDK 23 disponível (só JDK 21) — compilação/testes correram com
  `-Dmaven.compiler.release=21` passado só na linha de comando, sem alterar `pom.xml` (que
  continua a declarar `<java.version>23</java.version>`, inalterado — fora do âmbito desta fase,
  é CONFIG-01/Phase 132).

## Existing Code Insights

- `PlatformAdminController`, `MigracaoPapeisEscritorioService` e `DatabaseSeederPlataformaAdminTest`
  cada um define o seu próprio literal/constante `"ALCv"` independentemente — não há uma única
  fonte partilhada no backend (dívida pré-existente, fora do âmbito desta fase corrigir).
- `web/src/app/(dashboard)/plataforma/columns.tsx`'s `TENANT_RESERVADO` (frontend, Phase 130) tem
  de ficar sincronizado com este valor — já tratado na Phase 130.

## Specific Ideas

Nenhuma — âmbito e ficheiros já enumerados em REQUIREMENTS.md BACK-01/BACK-02.

## Deferred Ideas

Nenhuma.
