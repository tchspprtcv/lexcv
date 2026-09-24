---
phase: 129-identidade-e-documentacao
plan: 01
completed: 2026-09-24
---

# Phase 129 Plan 01 Summary

Substituída a marca "ALCv" por "LexCV" em 4 documentos técnicos ativos, no manual do utilizador
(fonte + HTML + PPTX regenerados) e nos scripts que o geram, e em 8 documentos comerciais (+ 6
`.docx` regenerados). `admin@alcv.cv` corrigido para `admin@lexcv.cv` em `CLAUDE.md` e no manual
(o valor semeado real em `DatabaseSeeder.java` já era `admin@lexcv.cv` — documentação estava
desatualizada). Domínio real de produção `alcv.tech`/`www.alcv.tech` mantido inalterado por
decisão explícita (DNS/infra fora de âmbito).

**Deviations from plan:** Nenhuma.

**Verification commands run:**
- `grep -rli -i alcv CLAUDE.md DEPLOYMENT.md backend/migrations/README.md .trae/documents/SPEC.md docs/ business/` — só devolveu os 4 ficheiros com o domínio `alcv.tech` intencionalmente preservado (`DEPLOYMENT.md`, `docs/MANUAL_DO_UTILIZADOR.md`, `docs/html/build.py`, `docs/pptx/build.js`)
- `python3 docs/html/build.py` — sucesso, `MANUAL_DO_UTILIZADOR.html` regenerado (12x "LexCV", 0x "ALCv")
- `node docs/pptx/build.js` — sucesso, `MANUAL_DO_UTILIZADOR.pptx` regenerado (4x "LexCV" nos slides, 0x "ALCv")
- `python3 business/scripts/gerar-docx.py` — sucesso, 6/6 `.docx` regenerados (`verificacao estrutural OK`)
