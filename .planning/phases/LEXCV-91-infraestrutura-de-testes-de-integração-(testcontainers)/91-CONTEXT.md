# Phase 91: Infraestrutura de Testes de Integração (Testcontainers) - Context

**Gathered:** 2026-07-13
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped)

<domain>
## Phase Boundary

O backend passa a ter, pela primeira vez, testes de integração reais contra PostgreSQL, cobrindo os dois riscos de maior severidade identificados (query nativa de `Notificacao`, lock de concorrência de `numeroVersao`), com uma decisão explícita sobre a sua execução em CI.

</domain>

<decisions>
## Implementation Decisions

### Stack (research STACK.md/ARCHITECTURE.md — locked, verified against Maven Central)
- Testcontainers `postgresql` module, versão herdada do parent BOM (1.20.4) — NÃO fixar versão explícita, NÃO usar a linha 2.0.x recém-lançada (renomeou artefactos, incompatível com `@ServiceConnection` do Spring Boot 3.4.x — spring-boot#47639)
- `spring-boot-testcontainers` starter + `@ServiceConnection` — auto-wiring do `DataSource`, sem `@DynamicPropertySource` manual
- `@DataJpaTest` (não `@SpringBootTest`) — contorna por construção o bloqueio `MINIO_ENDPOINT` (nunca instancia `MinioConfig`/`SecurityConfig`)
- Imagem `postgres:16-alpine` — corresponde à versão de produção
- H2 explicitamente rejeitado para ambos os riscos: a query nativa depende de inferência de tipo específica do Postgres (`CAST(:param AS ...)`), e o teste de concorrência precisa de semântica MVCC real

### Âmbito dos testes
- Teste de integração cobrindo `NotificacaoRepository.buscarPorFiltros` (query nativa + Pageable, Phase 86)
- Teste de integração cobrindo o lock de concorrência de `ParecerVersao.numeroVersao` (Phase 87)
- Decisão explícita registada (STATE.md/PROJECT.md) sobre se `.github/workflows/deploy.yml` passa a correr `mvn test`/`spotbugs:check` — research (PITFALLS.md) nota que CI hoje não corre testes nem SpotBugs (`Dockerfile` usa `-DskipTests`)

### Claude's Discretion
Decisão de CI (adicionar step ao `deploy.yml` ou registar como escolha deliberada de "local-only") fica ao critério do planeador/executor, documentando a decisão explicitamente conforme TEST-03 exige.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/pom.xml` já gere `spring-boot-dependencies:3.4.1` como parent — Testcontainers BOM já transitivamente importado (1.20.4), confirmado contra o POM real do Maven Central
- Nenhum teste de integração/H2/Testcontainers existe hoje neste backend — greenfield para este padrão

### Established Patterns
- `@DataJpaTest` + `@Testcontainers` + `@ServiceConnection` é o padrão oficial documentado em docs.spring.io para Spring Boot 3.4

### Integration Points
- `NotificacaoRepository.buscarPorFiltros` (nativeQuery=true + Pageable) — primeira combinação deste tipo no projeto
- `ParecerVersao.numeroVersao` — lock de concorrência via constraint de unicidade na BD
- `.github/workflows/deploy.yml` — ponto de decisão de CI

</code_context>

<specifics>
## Specific Ideas

Nenhuma — âmbito técnico definido pela pesquisa da milestone (STACK.md, ARCHITECTURE.md, PITFALLS.md).

</specifics>

<deferred>
## Deferred Ideas

- Teste HTTP end-to-end completo de RBAC/tenant — fora de âmbito desta milestone (ver REQUIREMENTS.md Out of Scope)

</deferred>
