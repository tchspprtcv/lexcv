---
phase: 129-identidade-e-documentacao
gathered: 2026-09-24
status: Ready for planning
mode: Auto-generated during /gsd:autonomous (gsd-sdk unavailable in this container — discuss run inline, condensed)
---

# Phase 129: Identidade e Documentação - Context

## Phase Boundary

Substituir a marca "ALCv" por "LexCV" em toda a documentação técnica ativa (`CLAUDE.md`,
`DEPLOYMENT.md`, `backend/migrations/README.md`, `.trae/documents/SPEC.md`), no manual do
utilizador (`docs/MANUAL_DO_UTILIZADOR.md`/`.html`/`.pptx` e os scripts que os geram) e na
documentação comercial (`business/README.md`, `business/documentacao/*`, `business/propostas/*`,
`business/scripts/gerar-docx.py`).

## Implementation Decisions

Decisões já travadas durante `/gsd:new-milestone` (ver `.planning/PROJECT.md` Current Milestone e
`.planning/REQUIREMENTS.md`):

- **Domínio real de produção `alcv.tech`/`www.alcv.tech`** (em `DEPLOYMENT.md`,
  `MANUAL_DO_UTILIZADOR.md`, `docs/html/build.py`, `docs/pptx/build.js`) fica **inalterado** — é
  uma decisão de DNS/infraestrutura separada da renomeação de marca no código, confirmada pelo
  utilizador.
- **Email do admin seed**: `CLAUDE.md` e o manual documentavam `admin@alcv.cv`, mas o valor real
  semeado por `DatabaseSeeder.java` já é `admin@lexcv.cv` — corrigido como parte desta fase (não é
  uma decisão de âmbito, é uma correção de documentação desatualizada face ao código real).
- **Arquivo histórico** (`.planning/milestones/*`, `.planning/research/*`, `MILESTONES.md`,
  `RETROSPECTIVE.md`) fica fora de âmbito de todo o marco v2.18, incluindo desta fase.
- **Exemplo genérico de domínio** em `DEPLOYMENT.md` (`alcv.example.com`, não o domínio real)
  atualizado para `lexcv.example.com` — é apenas um placeholder ilustrativo, distinto do domínio
  real de produção.

## Existing Code Insights

- Título/identidade em `.planning/PROJECT.md` já atualizados durante o bootstrap do marco
  (`/gsd:new-milestone`) — não repetir aqui.
- `docs/html/build.py` requer o pacote Python `markdown`; `docs/pptx/build.js` requer o pacote npm
  `pptxgenjs` (nenhum dos dois está declarado num `package.json`/`requirements.txt` versionado
  neste repositório — foram instalados ad-hoc para regenerar os artefactos derivados).
- `business/scripts/gerar-docx.py` regenera apenas os 6 `.docx` de `business/documentacao/` a
  partir dos `.md` correspondentes (fonte de verdade documentada no próprio script). Os `.docx` em
  `business/propostas/`, `business/especificacoes/`, `business/contratos/` não são gerados por este
  script e não continham "ALCv" — confirmado por inspeção do XML interno, sem alteração necessária.

## Specific Ideas

Nenhuma — âmbito e ficheiros já enumerados em REQUIREMENTS.md IDENT-01/02/03.

## Deferred Ideas

Nenhuma.
