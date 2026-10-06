---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 10
subsystem: backend-fiscal-adapter
tags: [fiscal, efatura, port, adapter, sealed, retry, tdd]
requires: ["136-02", "136-05"]
provides:
  - "EfaturaGateway port: ambiente(), comunicar(PedidoComunicacao) -> ResultadoComunicacao"
  - "ResultadoComunicacao sealed interface: AceiteSimulado(referencia), Rejeitado(codigo, mensagem), ErroTransitorio(codigo, mensagem)"
  - "PedidoComunicacao record(tenantId, documentoFiscalId, ambiente, iud, xml String)"
  - "InjetorFalhas functional interface with NENHUMA and SEMPRE_TRANSITORIA (FALHA_SIMULADA)"
  - "SimuladoEfaturaGateway(DfeValidador, InjetorFalhas): plain final class, not a Spring bean"
  - "EstadoComunicacaoMapper.estadoPara(resultado, ambiente, tentativas) + MAX_TENTATIVAS = 8"
  - "BackoffComunicacao.atraso(tentativas 1..7) -> Duration"
affects: [136-12, 136-13, 136-15]
tech-stack:
  added: []
  patterns:
    - "Sealed result + pattern-matching switch; nested exhaustive switch over AmbienteFiscal without default (compile-time decision point for a future real ambiente)"
    - "Source-gate test walking src/main/java (comment lines stripped) to forbid AUTORIZADO/PRODUCAO identifiers"
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaGateway.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/ResultadoComunicacao.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/PedidoComunicacao.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/InjetorFalhas.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/SimuladoEfaturaGateway.java
    - backend/src/main/java/com/lexcv/services/fiscal/EstadoComunicacaoMapper.java
    - backend/src/main/java/com/lexcv/services/fiscal/BackoffComunicacao.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/SimuladoEfaturaGatewayTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/EstadoComunicacaoMapperTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/BackoffComunicacaoTest.java
  modified: []
decisions:
  - "An invalid XML is Rejeitado with the validator's own code (XSD_INVALIDO, XML_PROIBIDO or XML_ILEGIVEL) and the fixed message 'O documento não cumpre o formato eFatura (linha n).' (without the parenthesis when the line is 0); parser text never leaves the validator"
  - "AceiteSimulado.referencia = 'SIMULADO-' + IUD"
  - "PedidoComunicacao: null -> NullPointerException, blank iud/xml -> IllegalArgumentException"
  - "estadoPara and atraso are public static (136-13's processor lives in the same package, but 136-15's job may not)"
metrics:
  duration: "~20 min"
  completed: 2026-10-06
  tasks: 2
  files: 10
---

# Phase 136 Plan 10: eFatura port, simulated adapter, state mapper and backoff Summary

All fiscal communication now goes through one port, `EfaturaGateway`. Its result is sealed: `AceiteSimulado`, `Rejeitado` or `ErroTransitorio`.

- **Simulated gateway:** `SimuladoEfaturaGateway` is the only implementation. It accepts a document only when `DfeValidador` says its XML is valid, and refuses any ambiente other than SIMULADO. It applies an `InjetorFalhas` first, so tests can force failures. It has no network or filesystem code and is not a Spring bean.
- **State mapper:** `EstadoComunicacaoMapper.estadoPara` is the single result-to-state function.
  - A simulated acceptance can only become `ACEITE_SIMULADO`, through an exhaustive switch over `AmbienteFiscal` with no default.
  - A rejection is `REJEITADO` at once.
  - A transient error is `PENDENTE` until the 8th attempt, then `ERRO`.
- **Backoff:** `BackoffComunicacao` is the fixed table of 30 s, 2 min, 5 min, 15 min, 30 min, 1 h and 3 h.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | Port, sealed result, request record, fault injector, SimuladoEfaturaGateway | 6794553 (RED), a2c3668 (GREEN) |
| 2 | EstadoComunicacaoMapper + BackoffComunicacao | f92f1dc (RED), 03b015c (GREEN) |

## Verification

- **SimuladoEfaturaGatewayTest:** 9 tests, green.
  - The official FRE sample is accepted, with a reference of `SIMULADO-<IUD>`.
  - RepositoryCode 4 gives `Rejeitado("XSD_INVALIDO", "O documento não cumpre o formato eFatura (linha n).")`, and the message does not echo the value.
  - An XXE DOCTYPE gives `Rejeitado("XML_PROIBIDO")`, with no parser text.
  - `SEMPRE_TRANSITORIA` gives `ErroTransitorio("FALHA_SIMULADA", "Falha simulada do serviço de comunicação.")` even for valid XML.
  - A custom injector's `Rejeitado` is returned as is, and `NENHUMA` injects nothing.
  - `ambiente()` is SIMULADO.
  - The `PedidoComunicacao` null and blank refusals work, and the constructor refuses null dependencies.
- **EstadoComunicacaoMapperTest:** 24 tests, green.
  - The full matrix: `AceiteSimulado` with tentativas 1..8 gives `ACEITE_SIMULADO`. `Rejeitado` with 1, 2, 7, 8 and 9 gives `REJEITADO`. `ErroTransitorio` gives `PENDENTE` for 1..7 and `ERRO` for 8 and 9.
  - `MAX_TENTATIVAS` is 8.
  - **Source gate:** it walks every `.java` file under `src/main/java` with comment lines stripped. No match for `\bAUTORIZADO\s*\(`, `\.AUTORIZADO\b`, `\bPRODUCAO\s*\(` or `\.PRODUCAO\b`. The only file whose code lines contain `AUTORIZADO` is `ComunicacaoFiscal.java`, inside its `@Check`.
- **BackoffComunicacaoTest:** 13 tests, green.
  - The 7 table rows are correct, and every attempt below the maximum has a positive delay.
  - The table is deterministic.
  - -1, 0, 8 and 9 throw IllegalArgumentException.
- **SpotBugs:** `mvn -DskipTests compile spotbugs:check` is clean.
- **Acceptance greps:**
  - `sealed interface ResultadoComunicacao` appears once.
  - Network APIs in the gateway appear 0 times.
  - `@Component|@Service` in the gateway appears 0 times.
  - `MAX_TENTATIVAS = 8` appears once.
  - `default ->` in the mapper appears 0 times.

## Deviations from Plan

**1. [Rule 2 - Hardening] Fixed message for every validator failure code**
- **Change:** besides `XSD_INVALIDO`, the gateway also maps `XML_PROIBIDO` and `XML_ILEGIVEL` to `Rejeitado` with that code and the same fixed format message.
- **Test:** the DOCTYPE case covers it.
- **Commit:** a2c3668.

The `AMBIENTE_INCOMPATIVEL` branch exists as the plan requires, but it is not reachable by a test. `AmbienteFiscal` has only SIMULADO, so no request can carry another ambiente.

## TDD Gate Compliance

- **Task 1:** RED 6794553 failed to compile because the classes were missing. GREEN a2c3668.
- **Task 2:** RED f92f1dc failed to compile. GREEN 03b015c.

## Notes for downstream plans

- **136-12:** create the bean with `new SimuladoEfaturaGateway(validador, falhasForcadas ? InjetorFalhas.SEMPRE_TRANSITORIA : InjetorFalhas.NENHUMA)`.
- **136-13:** `estado = EstadoComunicacaoMapper.estadoPara(resultado, item.ambiente(), item.tentativas())`.
- **136-13:** set `proxima` only when the state is PENDENTE, as `clock.instant().plus(BackoffComunicacao.atraso(item.tentativas()))`. `atraso` throws for tentativas >= 8, but that case is always ERRO.

## Threat Flags

None.
- T-136-36: sealed result, exhaustive switch and source gate.
- T-136-37: no network code, confirmed by grep.
- T-136-38: deterministic backoff, 8 attempts, REJEITADO terminal.
- T-136-39: accepted. The injector is chosen by 136-12.

## Self-Check: PASSED

- FOUND: all 10 files listed in key-files
- FOUND: commits 6794553, a2c3668, f92f1dc, 03b015c
