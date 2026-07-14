# Phase 97: Auditoria de Milestone — Dívida Técnica e UAT Pendente - Context

**Gathered:** 2026-07-14
**Status:** Ready for planning

<domain>
## Phase Boundary

A milestone termina com isolamento de tenant verificado nas superfícies novas, UAT ao vivo pendente das fases 75/76/79/81/82/84/85/89 fechado ou explicitamente contornado, dívidas menores conhecidas corrigidas, e uma auditoria fresca ao código sem gaps não documentados.

</domain>

<decisions>
## Implementation Decisions

### AUD-04 (MINIO_ENDPOINT) — RESOLVIDO nesta sessão, não apenas contornado
- O bloqueio `MINIO_ENDPOINT` está genuinamente resolvido para este ambiente local: `backend/.env` (gitignored, não commitado) agora tem `MINIO_ENDPOINT=http://localhost:9000` + credenciais, apontando para um container `lexcv_minio` que o utilizador arrancou deliberadamente para este efeito
- O backend liga-se a um serviço Postgres NATIVO local (Windows service, porta 5432 — distinto do Postgres do docker-compose partilhado na porta 5433, que continua parado) — já tinha o schema desta milestone completo (confirma que este é um ambiente de dev já usado em sessões anteriores)
- **Backend a correr:** `http://localhost:8080` (via `mvn spring-boot:run`, processo já ativo neste sistema)
- **Frontend a correr:** `http://localhost:3000` (via `pnpm dev`, servido pelo preview tool)
- Ambos os processos ficam ativos durante esta fase — os executores (mesmo em worktrees isolados) podem aceder-lhes diretamente via rede (portas não são isoladas por git worktree, só o filesystem é), sem precisar de arrancar as suas próprias instâncias
- Login: `admin@lexcv.cv` com a password que o próprio utilizador introduziu manualmente no browser (nunca vista/manuseada pelo Claude, por regra de segurança) — sessão já autenticada existe no browser pane

### Verificação ao vivo já confirmada nesta sessão (não repetir)
- **NOTF-24**: toggle de silenciamento em `/settings` → aba "Notificações" testado ao vivo (silenciar "Nova fase", confirmar `SILENCIADA`, reativar) — persistência real confirmada
- **AGD-35**: `GET /api/v1/eventos` confirmado a devolver `"risco":"vencido"` corretamente calculado para um evento de teste existente
- **NOTF-25 (parcial)**: criada uma nova Fase via `POST /processos/{id}/fases` → gerou notificação `FASE_ENTRADA` real, corretamente entregue via fan-out ADMIN (destinatário = utilizador atual, já que o processo de teste não tem responsável nem equipa atribuída)
- **NOTF-26**: notificação real adiada por 3 dias via UI (`/notificacoes`) → confirmado: `unread-count` cai para 0, item mostra "Adiado até 17/07" no histórico, badge do sino desaparece

### Dados de teste criados nesta sessão (aceitar como estão, não reverter)
- Nova Fase "Fase de Verificação NOTF-25" no processo `1/2026` (cliente Tech Support CV) — registo histórico de auditoria jurídica, não apagar (mesma convenção de outros dados de teste já existentes nesta BD: "Test Client", "Test Client 2", "TEste Evento")
- Uma notificação `FASE_ENTRADA` adiada 3 dias associada a essa fase

### Âmbito de AUD-02 (fecho de UAT em 8 fases: 75, 76, 79, 81, 82, 84, 85, 89)
- Dado o ambiente ao vivo agora disponível, tentar fechar o máximo possível destes UAT gaps com verificação real (browser + API), não apenas releitura de código
- Priorizar pelo que for mais rápido/de maior valor a confirmar (ex.: fluxos simples de CRUD/visualização) sobre cenários que exigem setup extenso de dados
- Onde não for possível fechar com confiança dentro do tempo razoável desta fase, documentar explicitamente o que foi e não foi verificado, e porquê — não deixar a incerteza implícita

### Âmbito de AUD-01 (isolamento de tenant)
- Auditoria de código focada nas superfícies novas desta milestone: `NotificacaoPreferencia` (Phase 93), `resolverEquipaCliente` (Phase 95), `snoozedUntil`/queries (Phase 96) — todas já revistas e corrigidas durante os code-reviews de fase, mas esta é a oportunidade de uma verificação cross-cutting final
- Verificação ao vivo de isolamento cross-tenant exigiria um segundo tenant/utilizador — avaliar se compensa criar um durante a fase, ou se a auditoria de código já dá confiança suficiente

### Claude's Discretion
- Estrutura exata de plans/waves para cobrir AUD-01 a AUD-05 — dado o âmbito amplo e heterogéneo (auditoria de código + UAT ao vivo + limpeza de dívida técnica), várias plans paralelas por categoria de item provavelmente fazem mais sentido do que uma única plan monolítica
- Nível de detalhe do relatório final desta fase (pode ficar em `97-SUMMARY.md`/`97-VERIFICATION.md` em vez de um documento novo, seguindo o padrão já estabelecido)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Ambiente local totalmente funcional: backend (`localhost:8080`) + frontend (`localhost:3000`) + Postgres nativo + MinIO Docker — primeira vez nesta milestone que isto foi possível
- `.claude/launch.json` já tem as configurações `web`/`backend` prontas a usar

### Established Patterns
- Cada fase anterior desta milestone já documentou os seus próprios itens de dívida técnica menor em `STATE.md`/`PROJECT.md` — esta fase consolida e fecha, não descobre do zero

### Integration Points
- `.planning/STATE.md` (secção "Pending Todos"/"Deferred Items") — lista completa dos itens AUD-02/03 a fechar
- `.planning/PROJECT.md` (Out of Scope, Key Decisions) — contexto histórico de cada dívida técnica

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

None — esta é a fase final da milestone, sem itens a diferir para depois dela dentro do próprio v2.11.

</deferred>
