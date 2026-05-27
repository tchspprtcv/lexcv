# Phase 17: Acoes UI com controlo por permissions - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-27
**Phase:** 17-acoes-ui-com-controlo-por-permissions
**Areas discussed:** Matriz UI, Mapa Perms, UX Sem Acesso, Abstracao

---

## Matriz UI

| Option | Description | Selected |
|--------|-------------|----------|
| Tudo visivel do modulo | Cobrir sidebar, CTAs, acoes de tabela, menus, botoes de detalhe/edicao e sub-recursos. | ✓ |
| So acoes principais | Controlar apenas navegacao e acoes maiores. | |
| So menus e CTAs | Cobrir navegacao e botoes principais, sem sub-recursos. | |
| Claude decide | Fechar a matriz a partir do codigo atual. | |

**User's choice:** Tudo visivel do modulo.
**Notes:** Acoes internas de detalhe devem seguir a mesma regra do modulo; menus/dropdowns devem mostrar apenas acoes permitidas.

---

## Mapa Perms

| Option | Description | Selected |
|--------|-------------|----------|
| View/Edit guarda-chuva | `view` para leitura e `edit` para criar/editar/apagar/gerir enquanto nao houver chaves finas. | ✓ |
| Separar ja create/edit | Preparar `create` e `edit` separados desde ja. | |
| Priorizar gerir | Tratar `manage` como o padrao principal em todos os modulos. | |
| Claude decide | Fechar o mapeamento pela compatibilidade atual. | |

**User's choice:** View/Edit guarda-chuva.
**Notes:** Se `create` existir, a permissao mais especifica vence para acoes de novo; `manage` sobrepoe `edit` nas areas administrativas.

---

## UX Sem Acesso

| Option | Description | Selected |
|--------|-------------|----------|
| Esconder por padrao | Acoes proibidas nao aparecem no UI. | ✓ |
| Disabled por padrao | Acoes aparecem desativadas com indicacao visual. | |
| Sempre combinar | Misturar hide e disabled em quase todos os contextos. | |
| Claude decide | Fechar a UX pela melhor consistencia visual. | |

**User's choice:** Esconder por padrao.
**Notes:** Mesmo em fluxos ja abertos, a preferencia continua a ser esconder; paginas abertas por URL sem permissao devem bloquear com estado claro.

---

## Abstracao

| Option | Description | Selected |
|--------|-------------|----------|
| Helper central + uso local | Criar util/hook comum e consumir localmente nas paginas/componentes. | ✓ |
| So inline por pagina | Cada ecrã usa `includes()` diretamente. | |
| Wrapper de componentes | Encapsular tudo em componentes guard. | |
| Claude decide | Escolher a abstracao mais equilibrada. | |

**User's choice:** Helper central + uso local.
**Notes:** A adocao deve ser cross-modulo e o mapeamento semantico deve ficar concentrado num util de auth.

---

## Claude's Discretion

- Nome final do helper, assinatura das funcoes e estrategia de rollout incremental.

## Deferred Ideas

- Nenhuma ideia fora de escopo foi levantada nesta discussao.
