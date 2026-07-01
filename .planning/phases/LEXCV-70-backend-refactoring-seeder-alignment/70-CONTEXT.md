# Phase 70: Backend refactoring & Seeder Alignment - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss determined no grey areas)

<domain>
## Phase Boundary

O backend armazena a identificação do cliente exclusivamente em colunas planas (sem `dados_tipo` JSON), suporta `REG_COMERCIAL` como tipo de documento para Empresa, e o DatabaseSeeder gera dados de seed consistentes com o novo modelo. Cobre: adicionar `REG_COMERCIAL` a `DocumentoTipo`, remover `dadosTipo` e o conversor JSON de `Cliente`, e corrigir o `DatabaseSeeder` (CLI-06, CLI-09).

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Fase de infraestrutura pura (refactoring de backend, sem comportamento visível ao utilizador) — todas as escolhas de implementação ficam ao critério do Claude, guiadas pelo goal do ROADMAP, requisitos CLI-06/CLI-09, e convenções existentes do codebase (JPA entities em `backend/.../models/`, seeders em `backend/.../seed/DatabaseSeeder.java`).

</decisions>

<code_context>
## Existing Code Insights

Contexto de código será levantado durante a investigação do plan-phase (entidade `Cliente`, enum `DocumentoTipo`, `DatabaseSeeder.java`, e quaisquer conversores JPA `@Convert` relacionados a `dados_tipo`).

</code_context>

<specifics>
## Specific Ideas

Nenhuma — fase de infraestrutura. Seguir requisitos CLI-06 (remover `dados_tipo` JSON, aplanar colunas) e CLI-09 (tipo `REG_COMERCIAL` em `documento_tipo` com número em `documento_numero` para Empresa).

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
