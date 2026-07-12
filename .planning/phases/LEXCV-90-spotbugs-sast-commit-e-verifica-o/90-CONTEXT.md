# Phase 90: SpotBugs/SAST — Commit e Verificação - Context

**Gathered:** 2026-07-12
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped)

<domain>
## Phase Boundary

A análise SpotBugs/FindSecBugs corre sem erros contra bytecode JDK 23, com as versões atualizadas, o ficheiro de exclusões e as correções defensivas já presentes no working tree devidamente comprometidos ao repositório.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Fase de infraestrutura pura — todas as escolhas de implementação ficam ao critério do executor. Usar o objetivo e critérios de sucesso do ROADMAP.md como especificação.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/pom.xml` já tem os bumps de versão SpotBugs (4.10.2.0) / FindSecBugs (1.14.0) no working tree, não commitados (`git status` mostra `M backend/pom.xml`)
- `backend/spotbugs-exclude.xml` já existe no working tree como ficheiro novo não rastreado (`?? backend/spotbugs-exclude.xml`), com um conjunto de exclusões já revisado
- Correções defensivas já aplicadas e não commitadas em `UserPrincipal.java`, `ConflictCheckResponse.java`, `WorkflowResponse.java`, `ResourceController.java` (`git status` no início da sessão)

### Established Patterns
- Nenhum pattern novo — esta fase é verificação + commit do trabalho já existente, não nova engenharia (research: STACK.md/PITFALLS.md)

### Integration Points
- Nenhum — mudança isolada a `backend/pom.xml` + `backend/spotbugs-exclude.xml` + os 4 ficheiros de source já modificados

</code_context>

<specifics>
## Specific Ideas

Tratar como "inventariar e commitar o trabalho já existente", não uma investigação do zero (research SUMMARY.md, Phase 90 rationale). Correr `mvn spotbugs:check` para reconfirmar antes de commitar.

</specifics>

<deferred>
## Deferred Ideas

None — discussão não necessária, fase de infraestrutura.

</deferred>
