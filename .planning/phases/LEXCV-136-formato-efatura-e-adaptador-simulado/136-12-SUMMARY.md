---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 12
subsystem: backend-fiscal-config
tags: [fiscal, efatura, configuration, fail-fast, deployment, compose, ci]
requires: ["136-09", "136-10"]
provides:
  - "EfaturaProperties @ConfigurationProperties(app.efatura) record(modo, Transmissao, Outbox{intervalo PT30S, atrasoInicial PT20S, lote 20, lease PT2M}, Simulado{falhasForcadas false})"
  - "EfaturaConfig: @Bean EfaturaGateway (only SimuladoEfaturaGateway; any modo other than exactly SIMULADO aborts startup) and @Bean TransmissaoEfatura (invalid value aborts startup naming the property)"
  - "application.yml app.efatura.* (EFATURA_MODE default SIMULADO, synthetic G9 transmission defaults) and spring.task.scheduling.pool.size 3"
  - "EFATURA_MODE + 4 EFATURA_* transmission variables on .env.example, the three compose files, the CI test step and DEPLOYMENT.md"
affects: [136-13, 136-15, 136-16]
tech-stack:
  added: []
  patterns:
    - "Record @ConfigurationProperties with @DefaultValue on nested record components (nested objects exist even when the block is absent)"
    - "ApplicationContextRunner fail-fast tests asserting the root-cause message"
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaProperties.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaConfig.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/EfaturaConfigTest.java
  modified:
    - backend/src/main/java/com/lexcv/fiscal/efatura/TransmissaoEfatura.java
    - backend/src/main/resources/application.yml
    - backend/.env.example
    - docker-compose.yml
    - docker-compose.hostinger.yml
    - docker-compose.prod.yml
    - .github/workflows/deploy.yml
    - DEPLOYMENT.md
decisions:
  - "The mode is trimmed then compared case-sensitively to SIMULADO: ' SIMULADO ' starts, 'simulado', '', '   ', REAL, PRODUCAO and an absent value abort with \"EFATURA_MODE='<v>' não é suportado neste build: só SIMULADO existe (não há implementação real). Corrija EFATURA_MODE e reinicie.\""
  - "TransmissaoEfatura exposes its four fixed messages as constants (MSG_NIF, MSG_CODIGO, MSG_NOME, MSG_VERSAO) so EfaturaConfig maps each to its property (app.efatura.transmissao.<key> (EFATURA_*)); the IllegalArgumentException is not chained, so the startup root cause names the property"
  - "Synthetic transmission defaults: NIF 999999999, code LEXCVSIM, name LexCV, version 3.0.0 (valid only in SIMULADO, gate G9)"
  - "app.efatura.simulado.falhas-forcadas is not exposed as an env var in compose (T-136-47); a WARN is logged when it is on"
  - "DFE-03 is left unchecked in REQUIREMENTS.md: the port, the single implementation, deployment-level mode and fail-fast exist, but no communication flows through the adapter until the 136-13 processor and the 136-15 job"
metrics:
  duration: "~30 min"
  completed: 2026-10-06
  tasks: 2
  files: 11
---

# Phase 136 Plan 12: Fail-fast EFATURA_MODE and deployment plumbing Summary

The eFatura adapter mode is now a deployment-level setting, `EFATURA_MODE`, and it defaults to SIMULADO.

- **Mode check:** `EfaturaConfig` accepts exactly `SIMULADO` (after trimming, case-sensitive). Any other value aborts startup with an explicit message saying only SIMULADO exists in this build: REAL, PRODUCAO, lower-case, blank or absent.
- **Beans:** the only gateway it can create is `SimuladoEfaturaGateway`, optionally with the forced-failure injector. It also builds the `TransmissaoEfatura` bean from `app.efatura.transmissao.*`, and an invalid value aborts startup naming the property.
- **Scheduler:** the pool now has 3 threads.
- **Deployment surfaces:** `.env.example`, the three compose files, the CI test step and DEPLOYMENT.md all carry the new variables. In compose, `${EFATURA_MODE:-SIMULADO}` also turns a set-but-empty variable into SIMULADO.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | EfaturaProperties + EfaturaConfig (fail-fast) + application.yml | f76ec65 (RED), a3a72ea (GREEN), d3fdb90 (fix) |
| 2 | Deployment plumbing: .env.example, three compose files, deploy.yml, DEPLOYMENT.md | 946bed0 |

## Verification

- **EfaturaConfigTest:** 17 tests, green. They run on `ApplicationContextRunner` with `EfaturaConfig` and `DfeValidador`.
  - **Mode refusals:** REAL, PRODUCAO, `simulado`, `""`, `"   "`, `" SIMULADOX"` and an absent modo all fail. The root-cause message contains `EFATURA_MODE` and `só SIMULADO existe`.
  - **SIMULADO:** yields one `SimuladoEfaturaGateway` with ambiente SIMULADO and one `TransmissaoEfatura` equal to the configured values. `" SIMULADO "` also starts.
  - **Transmission refusals:**
    - `software-codigo=lexcv sim` fails with a message naming `app.efatura.transmissao.software-codigo` and "Código do software".
    - `nif-transmissor=123` fails naming `nif-transmissor`.
    - A missing transmission block fails.
  - **Forced failures:** with `falhas-forcadas=true` the official FRE sample gives `ErroTransitorio`. With the default (false) it gives `AceiteSimulado`.
  - **Outbox defaults:** PT30S, PT20S, 20 and PT2M.
  - **application.yml:** loaded with `YamlPropertySourceLoader`, it has `app.efatura.modo = ${EFATURA_MODE:SIMULADO}`, `spring.task.scheduling.pool.size = 3` and `falhas-forcadas = false`. Feeding its raw `app.efatura.*` values (placeholders resolved by the environment) into the runner starts a SIMULADO context.
- **Full backend unit suite:** `mvn -Dmaven.compiler.release=21 test` ran 1170 tests with 0 failures and 0 errors, so the new `@Configuration` breaks no existing slice. `NotificacaoRepositoryIT` also passed through failsafe on Testcontainers PostgreSQL.
  - The repo has no full-context `@SpringBootTest`. The jar boot is covered by the live UAT in 136-16.
- **SpotBugs:** `mvn -DskipTests compile spotbugs:check` is clean.
- **Compose:** `docker compose config` was run with a throwaway env file in the scratchpad (not committed), with no `EFATURA_*` exported.
  - `docker-compose.yml`, `docker-compose.yml` + `docker-compose.prod.yml` and `docker-compose.hostinger.yml` all render `EFATURA_MODE: SIMULADO`, `EFATURA_TRANSMISSOR_NIF: "999999999"`, `EFATURA_SOFTWARE_CODIGO: LEXCVSIM`, `EFATURA_SOFTWARE_NOME: LexCV` and `EFATURA_SOFTWARE_VERSAO: 3.0.0`.
  - With `EFATURA_MODE=` (set but empty) the prod merge still renders `EFATURA_MODE: SIMULADO`.
  - The prod merge keeps the base `environment` keys (DB_HOST, MINIO_ENDPOINT and SPRING_JPA_HIBERNATE_DDL_AUTO are still present).
- **Acceptance greps:**
  - `modo: ${EFATURA_MODE:SIMULADO}` appears once.
  - `grep -A3 'scheduling:' | grep -c 'size: 3'` gives 1.
  - `só SIMULADO existe` appears once.
  - `equalsIgnoreCase` appears 0 times.
  - `EFATURA_MODE: "${EFATURA_MODE:-SIMULADO}"` appears once in each of the three compose files, and each has the four transmission lines with `:-` defaults.
  - `^EFATURA_MODE=SIMULADO` in `.env.example` appears once.
  - `EFATURA_MODE: SIMULADO` in `deploy.yml` appears once.
  - No `.env` file is staged.

## Deviations from Plan

**1. [Rule 1 - Bug] Startup root cause did not name the property**
- **Found during:** Task 1 GREEN.
- **Issue:** with the IllegalArgumentException chained as the cause, the root cause of the failed context was the record's message, which does not name the property.
- **Fix:** `TransmissaoEfatura` messages became public constants. `EfaturaConfig` maps each one to its property and throws an IllegalStateException without a cause. The message is fixed text, so nothing is lost.
- **Commit:** a3a72ea.

**2. [Rule 3 - Acceptance] YAML comment placement**
- **Issue:** the first version put the Pitfall 7 comment between `pool:` and `size: 3`, which pushed `size` out of the plan's `grep -A3 'scheduling:'` window.
- **Fix:** the comment moved above `task:`.
- **Commit:** d3fdb90.

**3. [Plan-allowed] DEPLOYMENT.md**
- The backend variable table gained one `EFATURA_MODE` row, because DEPLOYMENT.md lists backend variables.

The values added to `application.yml` have defaults, unlike most of the file's required variables (CLAUDE.md "no defaults"). This follows the plan and research Pattern 6, and the existing `MINIO_PRESIGNED_EXPIRY:3600` / `MINIO_PUBLIC_ENDPOINT` precedent: the mode must default to SIMULADO.

## TDD Gate Compliance

- **Task 1:** RED f76ec65 failed to compile because `EfaturaConfig` and `EfaturaProperties` were missing. GREEN a3a72ea.
- **Task 2:** a config-only task, not TDD.

## Notes for downstream plans

- **136-13:** inject the `EfaturaGateway` and `TransmissaoEfatura` beans.
- **136-15:** the job reads `EfaturaProperties.outbox()`, either through `@Scheduled(fixedDelayString = "${app.efatura.outbox.intervalo}", initialDelayString = "${app.efatura.outbox.atraso-inicial}")` or through the bean.
- **136-16:** booting the jar with `EFATURA_MODE=REAL` should abort with the message above. This is part of the live UAT.

## Threat Flags

None.
- T-136-43: case-sensitive allow-list, with context-runner tests.
- T-136-44: `:-` in all three compose files, and a blank value aborts.
- T-136-45: pool size 3.
- T-136-46 and T-136-47: accepted as planned.

## Self-Check: PASSED

- FOUND: all 3 created and 8 modified files listed in key-files
- FOUND: commits f76ec65, a3a72ea, d3fdb90, 946bed0
