# Phase 87: Alertas de Eventos — Fase, Documento, Atribuição e Parecer - Context

**Gathered:** 2026-07-08
**Status:** Ready for planning

<domain>
## Phase Boundary

O sistema notifica automaticamente o destinatário certo sempre que um processo muda de fase, um novo documento é adicionado, um processo é atribuído/reatribuído através de um novo formulário dedicado, ou um parecer é atribuído a um advogado. Esta é a primeira fase da milestone com comportamento genuinamente visível ao utilizador — inclui um novo formulário/UI de reatribuição de responsável na ficha do processo (não apenas backend). Depende de Phase 86 (`NotificacaoService.criar(...)` já existe e é o único ponto de escrita) e reutiliza a fase 85 apenas indiretamente (não recalcula risco aqui).

</domain>

<decisions>
## Implementation Decisions

### Reatribuição de Responsável (Processo)
- Botão "Reatribuir" junto ao campo Responsável no card Dados da ficha do processo, abrindo um `Dialog` com seletor de utilizador — mesmo padrão visual "Adicionar" já usado em Partes/Fases/Decisões/Factos/Testemunhas.
- Novo endpoint backend gated por `processos:manage` (não `processos:edit`) — reatribuir o responsável é uma ação de gestão distinta da edição geral de campos do processo. Validar que o novo `responsavelId` pertence ao tenant, exatamente como já acontece em `createProcesso`.
- `AlertDialog` de confirmação antes de submeter a reatribuição (ação sensível) — mirror do padrão já usado em "Entregar Parecer".
- A notificação de atribuição NÃO inclui o nome de quem fez a atribuição — mensagem simples "Foi-lhe atribuído o processo {numeroProcesso}".
- O novo responsável (e ADMIN) recebe a notificação imediatamente após a reatribuição ter sucesso.

### Notificação de Entrada de Fase
- Mensagem inclui o nome da nova fase: "O processo {numeroProcesso} entrou na fase {nomeFase}".
- Link da notificação aponta para a aba "Fases" da ficha do processo (não a vista geral) — usar o mesmo padrão de deep-link por aba que a UI da ficha já suporta (ex.: query param ou hash de aba).
- Disparado em `createProcessoFase` (o ponto onde uma fase é adicionada/entra em vigor). Notifica o `responsavelId` do processo + ADMIN.
- Nota de risco pré-existente (não corrigir nesta fase, apenas não agravar): `createProcessoFase` não desativa fases anteriores automaticamente — múltiplas fases podem ficar `ativa=true` simultaneamente. O gatilho de notificação deve disparar sempre que `createProcessoFase` é chamado, sem tentar resolver esse bug pré-existente.

### Notificação de Novo Documento
- Quando o documento está ligado a um `processoId`: notifica o `responsavelId` desse processo (+ ADMIN).
- Quando o documento está ligado APENAS a um `clienteId` (sem processo): notifica a equipa de advogados/administrativos do cliente via `ClienteAdvogado`/`ClienteAdministrativo` (+ ADMIN) — ver Key Decision já registada em PROJECT.md sobre esta distinção.
- Quando o documento está ligado a AMBOS cliente e processo: notifica APENAS o responsável do processo (processo tem precedência) — equipa do cliente não é duplicadamente notificada.
- O ator que fez o upload é sempre excluído da própria notificação (actor exclusion).
- Disparado no endpoint de upload de documento existente (`ResourceController`, ponto de criação de `Documento`).

### Notificação de Parecer Atribuído
- Dispara em AMBOS os pontos: `ParecerController.createSolicitacao` quando `advogadoId` já vem definido na criação, E `ParecerController.atribuirAdvogado` numa reatribuição posterior.
- Reatribuição notifica APENAS o novo advogado — o advogado anterior NÃO é notificado (consistente com REQUIREMENTS.md Out of Scope: "Notificar o responsável anterior quando um processo é reatribuído").
- O ator (quem atribuiu) é sempre excluído da própria notificação.

### Claude's Discretion
- Formato exato do texto/título de cada notificação (para além do conteúdo semântico já decidido acima) — usar frases curtas e diretas em português, consistentes com o tom já usado no resto da aplicação.
- Nome exato do novo endpoint de reatribuição (ex.: `PUT /processos/{id}/atribuir` vs. reaproveitar `PUT /processos/{id}` com um campo `responsavelId` mutável) — decidir com base no padrão já usado por `ParecerController.atribuirAdvogado` (endpoint dedicado) vs. simplicidade de reaproveitar `updateProcesso`.
- Estrutura exata do link de deep-link para a aba Fases (query param, hash, ou rota separada) — seguir o que já existe hoje no componente da ficha do processo.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NotificacaoService.criar(...)` (Phase 86) — único ponto de escrita para novas notificações, já com validação de input e fan-out de ADMIN.
- `ParecerController.atribuirAdvogado` — endpoint já existente, atualmente sem qualquer efeito de notificação (NOTF-05/06/07 foram deferidas em v2.6 exatamente por falta desta infraestrutura — esta fase fecha esse gap).
- `ClienteAdvogado`/`ClienteAdministrativo` — tabelas de junção já existentes (v2.4) para a equipa de um cliente, confirmadas via grep nesta milestone.
- Padrão Dialog "Adicionar" já usado consistentemente em Partes/Fases/Decisões/Factos/Testemunhas na ficha do processo (Phase 84).
- `createProcesso` já valida `responsavelId` pertence ao tenant — reaproveitar a mesma lógica de validação para a reatribuição.

### Established Patterns
- `createProcessoFase` (ResourceController) — ponto de criação de fase, sem chamada a AuditLog nem a qualquer serviço de notificação hoje.
- Upload de documento — endpoint genérico que aceita `clienteId`/`processoId` opcionais (ambos nullable), já validados quanto a posse de tenant desde a Phase 79.
- `Processo.responsavelId` — hoje só definível em `createProcesso`; não existe endpoint de reatribuição (esta fase cria o primeiro).

### Integration Points
- 4 pontos de gatilho distintos: `createProcessoFase`, endpoint de upload de documento, novo endpoint de reatribuição de processo + `createProcesso` (atribuição inicial), `ParecerController.createSolicitacao` + `atribuirAdvogado`.
- Todos chamam `NotificacaoService.criar(...)` (Phase 86) — nenhum grava diretamente no repositório.
- Consumido por Phase 89 (sino/página) para exibição — esta fase não constrói nenhuma UI de consumo de notificações, só a UI de reatribuição em si.

</code_context>

<specifics>
## Specific Ideas

Nenhuma adicional além do já capturado nas Decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Notificar toda a equipa de advogados/administrativos de um processo (não só o responsável único) — fora de âmbito, ver REQUIREMENTS.md v2/Out of Scope.
- Corrigir o bug pré-existente de `createProcessoFase` não desativar fases anteriores — fora de âmbito desta fase, apenas notado como risco a não agravar.

</deferred>
