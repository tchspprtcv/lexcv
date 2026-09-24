---
phase: 130-frontend-marca-lexcv
gathered: 2026-09-24
status: Ready for planning
mode: Auto-generated during /gsd:autonomous (gsd-sdk unavailable in this container — discuss run inline, condensed)
---

# Phase 130: Frontend — Marca LexCV - Context

## Phase Boundary

Substituir a marca "ALCv" por "LexCV" em todo o texto visível nas apps `web/` e `webpage/`, e nos
3 scripts de verificação manual do frontend.

## Implementation Decisions

- `web/src/app/(dashboard)/plataforma/columns.tsx` exporta `TENANT_RESERVADO = "ALCv"` — este
  literal identifica o tenant reservado no frontend e tem de acompanhar a renomeação que a Phase
  131 fará no backend (`DatabaseSeeder`/`Tenant`). Renomeado nesta fase para "LexCV"; a Phase 131
  fecha o lado backend do mesmo valor. Os 2 scripts de verificação que testam este literal por
  byte-match (`verify-consola-tenants.mjs`, `verify-relatorio-utilizacao.mjs`) foram atualizados em
  conjunto para continuarem a passar.
- `webpage/src/lib/contacto.ts` tinha `EMAIL = "contacto@alcv.cv"`. Mesmo raciocínio da Phase 129
  para `admin@alcv.cv`: o domínio `*.cv` usado em endereços de email neste código já era tratado
  como derivado da marca, não do domínio real de produção `alcv.tech` (o próprio seed do backend já
  usa `admin@lexcv.cv`) — atualizado para `contacto@lexcv.cv`, consistente com esse precedente.
- Domínio real `alcv.tech`/`www.alcv.tech` não aparece em nenhum ficheiro desta fase — nada a
  preservar aqui (já tratado na Phase 129 para os ficheiros onde existe).

## Existing Code Insights

- `web/` e `webpage/` não tinham `.env.local`/`node_modules` neste checkout; ambos foram
  provisionados a partir de `.env.example` só para permitir `pnpm build` como gate de verificação
  desta fase (nenhuma alteração de comportamento, apenas setup local de verificação).
- Os 3 scripts em `web/scripts/verify-*.mjs` são testes de análise estática pura (leem ficheiros
  como texto, sem import de módulo, sem backend) — seguros de correr sem qualquer serviço a correr.

## Specific Ideas

Nenhuma — âmbito e ficheiros já enumerados em REQUIREMENTS.md FRONT-01/02/03.

## Deferred Ideas

Nenhuma.
