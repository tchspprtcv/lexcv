---
phase: 132-configuracao
gathered: 2026-09-24
status: Ready for planning
mode: Auto-generated during /gsd:autonomous (gsd-sdk unavailable in this container — discuss run inline, condensed)
---

# Phase 132: Configuração - Context

## Phase Boundary

Os ficheiros de configuração e build do projeto (Maven, Docker Compose) referem "LexCV".

## Implementation Decisions

- Único ficheiro de configuração com o token de marca "ALCv" era `backend/pom.xml`
  (`<description>`). Nenhum `docker-compose*.yml` continha o token de marca — só
  `docker-compose.hostinger.yml` tem `alcv.tech, www.alcv.tech` no bloco Caddy do `entrypoint`,
  que é o mesmo domínio real de produção preservado por decisão explícita desde a Phase 129.

## Existing Code Insights

- `backend/pom.xml` declara `<java.version>23</java.version>` — inalterado por esta fase (âmbito é
  só a `<description>`); o ambiente desta sessão não tem JDK 23 disponível, verificação de
  `mvn package` correu com `-Dmaven.compiler.release=21` na linha de comando, sem tocar no `pom.xml`.

## Specific Ideas

Nenhuma — âmbito e ficheiros já enumerados em REQUIREMENTS.md CONFIG-01.

## Deferred Ideas

Nenhuma.
