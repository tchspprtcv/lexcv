---
phase: 116-reposicionamento-institucional-fim-das-referencias-a-nosi
plan: 01
subsystem: content
tags: [copy, seed-data, institutional-positioning, nextjs, spring-boot]

# Dependency graph
requires: []
provides:
  - Card "Ecossistema Cabo Verde" (webpage/) referencia o ecossistema do SIJ, nao a NOSi
  - .trae/documents/SPEC.md referencia o ecossistema do SIJ, nao a NOSi
  - Tenant de demonstracao seedado com identidade generica/ficticia (nome/nif/tipoEntidade/email/telefone), sem associacao a NOSi
  - SIJ-01 verificado formalmente (assertion delimitada a seccao "What This Is" de PROJECT.md)
  - Varrimento residual "nosi" confirmadamente limpo em backend/src, webpage/src, web/src (excl. mock legado), .trae/documents
affects: [v2.15-milestone-close]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - webpage/src/components/trust-section.tsx
    - .trae/documents/SPEC.md
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java

key-decisions:
  - "SIJ-01 ja estava satisfeito antes desta fase (PROJECT.md editado na abertura da milestone v2.15) — Task 3 verificou formalmente via assertion delimitada por awk, sem necessidade de editar PROJECT.md"

patterns-established: []

requirements-completed: [SIJ-01, SIJ-02, SIJ-03, SIJ-04]

# Metrics
duration: ~7min
completed: 2026-07-27
---

# Phase 116 Plan 01: Reposicionamento Institucional — Fim das Referências a NOSi Summary

**Copy institucional (landing pública `trust-section.tsx` + `SPEC.md`) e identidade do tenant de demonstração seedado migradas de "NOSi" para "ecossistema do SIJ (Sistema Judicial de Cabo Verde)"; SIJ-01 verificado formalmente sem necessidade de edição adicional.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-07-27T20:57:23Z
- **Completed:** 2026-07-27T21:04:00Z
- **Tasks:** 3 (2 com commit de código, 1 verificação read-only)
- **Files modified:** 3

## Accomplishments

- Card "Ecossistema Cabo Verde" da landing pública (`webpage/src/components/trust-section.tsx`) e o parágrafo de abertura de `.trae/documents/SPEC.md` passam a usar a mesma formulação canónica: "alinhado ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)"
- Tenant de demonstração seedado (`DatabaseSeeder.java`) generalizado — nome, NIF, tipo de entidade, email e telefone deixam de identificar a NOSi, incluindo o campo `nome` servido publicamente e sem autenticação via `GET /api/v1/branding` (mitigação de disclosure T-116-01)
- SIJ-01 fechado formalmente: assertion delimitada à secção "What This Is" de `.planning/PROJECT.md` confirmou que a edição já tinha sido aplicada na abertura da milestone (fora desta fase) — zero edição adicional necessária
- Varrimento residual "nosi" (case-insensitive) sobre `backend/src`, `webpage/src`, `web/src` (excluindo o mock legado `web/src/server/mock-db.ts`, deliberadamente fora de âmbito) e `.trae/documents` devolveu zero ocorrências
- Backend compila (`mvn -DskipTests compile`, exit 0) e frontend público passa o lint (`pnpm lint`, exit 0, 0 erros)

## Task Commits

Each task was committed atomically:

1. **Task 1: Copy institucional — landing pública e SPEC.md** - `204f138` (feat)
2. **Task 2: Identidade genérica do tenant de demonstração seedado** - `226e89e` (fix)
3. **Task 3: Fecho formal de SIJ-01 e varrimento residual de "nosi"** - sem commit próprio (tarefa read-only; a assertion de SIJ-01 passou à primeira, `.planning/PROJECT.md` não foi tocado — exatamente o ramo esperado pelo plano)

**Plan metadata:** (commit deste SUMMARY + STATE/ROADMAP/REQUIREMENTS, ver final da execução)

_Nenhuma tarefa desta plan usou TDD — plan `type: execute`._

## Files Created/Modified

- `webpage/src/components/trust-section.tsx` - `desc` do card "Ecossistema Cabo Verde" (array `CONFIANCA`, ícone `Building2`) atualizado para referenciar o SIJ
- `.trae/documents/SPEC.md` - primeiro parágrafo da secção "Visão Geral e Requisitos" atualizado para referenciar o SIJ
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - bloco `Tenant.builder()` do tenant seedado: `nome`, `nif`, `tipoEntidade`, `email`, `telefone` generalizados/ficcionalizados

## Decisions Made

- **SIJ-01 já satisfeito antes desta fase.** A seção "What This Is" de `.planning/PROJECT.md` (linha 5) já continha "alinhada ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)" e zero ocorrências de "nosi", confirmado por uma assertion delimitada por `awk` entre `## What This Is` e `## Core Value` (não um `grep` ao ficheiro inteiro, que teria dado falso positivo — `PROJECT.md` contém "NOSi" em 9 outras linhas, todas legítimas: a frase `**Goal:**` da milestone v2.15, os 4 bullets de `**Target features:**`, e os 4 itens do checklist `### Active`, todas descrevendo a própria milestone/requisito, não o produto). `.planning/PROJECT.md` **não foi editado** por esta plan.
- **Observação não acionável registada, sem edição:** `deploy_key.pub` (raiz do repositório) contém o comentário de hostname `govcv\francisco.horta@RAI-GV-NOSI-194` numa chave pública SSH (`ssh-ed25519 ... govcv\francisco.horta@RAI-GV-NOSI-194`). Não é copy do produto nem está em nenhuma das superfícies em âmbito (`backend/src`, `webpage/src`, `web/src`, `.trae/documents`) — confirmado fora de âmbito pelo próprio plano, reportado apenas como observação.

## Deviations from Plan

None - plan executado exatamente como escrito. O ramo condicional de escrita da Task 3 (editar `.planning/PROJECT.md` apenas se a assertion de SIJ-01 falhasse) não foi acionado porque a assertion passou à primeira — exatamente o resultado que o próprio plano já antecipava ("esperado: nao — SIJ-01 ja estava satisfeito").

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Os 4 requisitos da milestone v2.15 (SIJ-01, SIJ-02, SIJ-03, SIJ-04) estão satisfeitos e verificáveis por comando (ver critérios acima).
- Zero ficheiros fora de âmbito alterados: `.figma/`, `.planning/milestones/`, `.planning/RETROSPECTIVE.md` e `web/src/server/mock-db.ts` confirmados intocados por `git status --porcelain`.
- Zero dependências adicionadas, zero migração de schema criada, zero acentos corrompidos (confirmado por inspeção direta do diff de cada ficheiro).
- Milestone v2.15 (fase única) fica pronta para fecho — sem bloqueios conhecidos.

---
*Phase: 116-reposicionamento-institucional-fim-das-referencias-a-nosi*
*Completed: 2026-07-27*
