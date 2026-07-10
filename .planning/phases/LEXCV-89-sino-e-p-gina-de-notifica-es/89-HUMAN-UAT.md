---
status: partial
phase: 89-sino-e-p-gina-de-notifica-es
source: [89-04-PLAN.md]
started: 2026-07-10T00:00:00Z
updated: 2026-07-10T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Sino — contador (NOTF-08)
expected: Badge do sino mostra o número de não-lidas (cap "9+"). Gerar uma notificação nova incrementa o contador dentro de ~30s sem refresh manual; mudar de aba e voltar atualiza imediatamente ao refocar (`refetchOnWindowFocus: true`, override do default global `false`).
result: [pending]

### 2. Sino — lista (NOTF-09)
expected: Abrir o sino mostra header "Notificações", até 10 notificações recentes (qualquer estado de leitura), cada uma com badge de categoria (cor conforme o mapa) e link. Empty state "Sem notificações por agora." quando não há nenhuma.
result: [pending]

### 3. Sino — clique fundido marcar+navegar (NOTF-10)
expected: Clicar numa notificação com link navega para a entidade (processo/agenda/parecer/honorário) E marca como lida numa só ação, sem confirmação; ao voltar, o contador desceu de imediato. Uma notificação sem link (linkUrl null) não navega mas permite marcar como lida via `Check` inline.
result: [pending]

### 4. Sino — marcar todas (NOTF-11)
expected: Clicar "Marcar todas como lidas" no dropdown mostra toast de sucesso, contador vai a 0, botão fica desativado.
result: [pending]

### 5. Página /notificacoes — acesso e layout (NOTF-12)
expected: "Ver todas as notificações" no footer do sino navega para /notificacoes; h1 "Notificações" + subtítulo, lista mais-recentes-primeiro; sem item de Notificações na sidebar nem no bottom-nav (acesso só pelo sino).
result: [pending]

### 6. Página — filtros (NOTF-13)
expected: Select de categoria e chips "Todas"/"Não lidas"/"Lidas" filtram a lista; mudar um filtro volta à página 0; filtros sem resultados mostram "Nenhuma notificação encontrada para os filtros selecionados." + "Limpar filtros".
result: [pending]

### 7. Página — marcar-uma standalone e paginação (NOTF-10)
expected: Numa linha não-lida, o botão `Check` marca como lida sem navegar (ponto azul desaparece, título deixa de estar a bold). Com >20 notificações, "Anterior"/"Seguinte" + "Página X de Y", desativados nos limites.
result: [pending]

### 8. Cross-surface — invalidação partilhada
expected: Marcar algo como lido numa superfície (sino ou página) reflete-se de imediato na outra (contador do sino desce ao marcar na página; marcar-todas no sino esvazia as não-lidas na página após refetch) — prova da invalidação de prefixo `["notificacoes"]`.
result: [pending]

### 9. RBAC — gate notificacoes:view
expected: Utilizador sem o scope `notificacoes:view` vê `AccessDeniedState` em vez do conteúdo da página /notificacoes.
result: [pending]

## Summary

total: 9
passed: 0
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps

Backend não arranca nesta sessão — `MINIO_ENDPOINT` não substituído a partir de `backend/.env` (`IllegalArgumentException: Illegal character in path at index 1: ${MINIO_ENDPOINT}` em `MinioConfig.s3Client()`), bloqueando toda a inicialização do contexto Spring (não é específico de notificações). Mesmo bloqueio ambiental já documentado em `87-HUMAN-UAT.md`/`87-04-SUMMARY.md` — sem relação com o código construído nas Phases 85-89. Confirmar estes 9 passos numa sessão futura com credenciais MinIO funcionais fecha tanto a Phase 89 como a milestone v2.10 por completo.
