---
status: partial
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
source: [87-VERIFICATION.md]
started: 2026-07-09T16:51:25Z
updated: 2026-07-09T16:51:25Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Fluxo de reatribuição de responsável + deep-link ?tab=fases (Plan 87-04, Task 4)
expected: Ver os 8 passos detalhados no retorno do executor (87-04-SUMMARY.md): botão "Reatribuir" visível só com processos:manage; Dialog→AlertDialog de dois passos; nome do responsável atualiza sem F5; notificação PROCESSO_ATRIBUIDO criada para o novo responsável + ADMIN; `/processos/{id}?tab=fases` abre diretamente na aba Fases. Não foi possível testar ao vivo nesta sessão — o backend não arranca (MINIO_ENDPOINT não substituído a partir de backend/.env, bloqueando a instanciação do S3Client/StorageService — sem relação com o que esta milestone construiu).
result: [pending]

### 2. Correção de perda de dados em ParecerController.updateSolicitacao
expected: Uma atualização parcial de uma solicitação de parecer (payload sem `prazo`/`prioridade`) já não deve apagar esses campos para `null`; deve preservar os valores existentes quando omitidos no payload.
result: [pending]

### 3. Lock de concorrência em numeroVersao (ParecerVersaoRepository)
expected: Duas criações de versão de parecer em simultâneo para a mesma solicitação não devem produzir `numeroVersao` duplicado (agora usa `@Lock(PESSIMISTIC_WRITE)` em vez de `synchronized` da JVM).
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
