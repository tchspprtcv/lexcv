# Research: LexCV — Próximo Milestone (Pós v2.13)

**Research date:** 2026-07-18
**Scope:** Análise do NextCRM (demo.nextcrm.io / github.com/pdovhomilja/nextcrm-app) como referência de funcionalidades e padrões aplicáveis ao próximo milestone do LexCV. Contexto: v2.13 (Refactor UI/UX shadcn/ui) acabou de fechar; próximo milestone ainda por definir.

## Nota metodológica

O demo ao vivo (`demo.nextcrm.io`) exige autenticação (Google OAuth ou email OTP com criação de conta) — não autenticámos, por ser uma ação vedada por política (criação de contas). Em alternativa, a análise baseou-se no repositório open-source (MIT, github.com/pdovhomilja/nextcrm-app), cujo README documenta em detalhe todos os módulos, stack e roadmap, cruzado com uma leitura direta do código atual do LexCV (backend + frontend) para mapear gaps reais em vez de hipotéticos.

## O que é o NextCRM

CRM open-source (MIT license, ~650 stars, 237 forks) construído em Next.js 16 + React 19 + TypeScript + PostgreSQL/Prisma 7 + **shadcn/ui** — a mesma stack de componentes UI que o LexCV acabou de adotar na v2.13. Isto significa que os padrões visuais (DataTable, Sheet, Dialog, Command/Combobox, Badge, etc.) são diretamente comparáveis e portáveis, não é preciso "traduzir" de outro design system.

Módulos: núcleo de CRM de vendas (Accounts, Contacts, Leads, Opportunities, Contracts, Targets, Products — não aplicável ao domínio do LexCV), mais um conjunto de módulos horizontais que **são** aplicáveis a qualquer sistema de gestão multi-entidade: Invoices, Activities, Audit Log/History, Unified Search, Reports, Projects (kanban), e uma camada de IA (enrichment agent, vector search, MCP server).

## Inventário de funcionalidades vs. estado atual do LexCV

| Funcionalidade NextCRM | O que faz | Estado atual no LexCV | Gap |
|---|---|---|---|
| **Invoices** | Tipos (Fatura/Nota Crédito/Proforma/Recibo), linhas de item com qtd/preço/desconto/imposto, série auto-numerada (INV-2026-0001), estado DRAFT→ISSUED→PAID/PARCIAL/CANCELLED, pagamentos parciais com saldo automático, PDF server-side, duplicar/cancelar, log de atividade | `Honorario` = só `valorTotal`+`descricao`+`dataAcordo` + lista de `Pagamento` (valor/data/método); único documento é o "Termo de Honorários" imprimível (CSS print, não PDF gerado) | **Grande** — sem numeração de série, sem estado formal, sem linhas de item, sem PDF real, sem emissão de recibo/fatura distinta do termo inicial |
| **Unified/Global Search** | Barra de pesquisa global, resultados agrupados por tipo de entidade, combina keyword + semântico | Existe pesquisa *scoped* (Pareceres tem `ParecerPesquisaController` avançado; Admin tem pesquisa própria) — mas **o campo de pesquisa no topbar (`dashboard-shell.tsx:121-127`) é puramente decorativo**: `<Input placeholder="Pesquisar processos, entidades...">` sem `value`/`onChange`, não está ligado a nada | **Médio-grande, mas barato** — a UI já promete a funcionalidade visualmente; falta só o backend+wiring |
| **Audit Log & History** | Diff engine por campo, soft-delete com restore, página admin global filtrável por todas as entidades | `AuditLog`/`t_audit_log` já existe, mas é estreito: `acao` limitado a 4 valores (`transicao_estado`, `conflict_check_decisao`, `documento_download`, `documento_eliminacao`), `entidade_tipo` a 3 valores (`processo`, `documento`, `conflict_check_decisao`), usado só em `ParecerController`/`ResourceController` | **Médio** — infraestrutura já existe, é uma extensão (mais ações/entidades + página admin), não green-field |
| **CRM Activities** (Notes/Calls/Emails/Meetings/Tasks) | Feed único e paginado, anexável a múltiplas entidades via tabela de junção | LexCV tem `ClienteNota` (só notas, só cliente) e `Movimentacao` (só processo) — dois mecanismos separados, sem "Chamada"/"Reunião" tipados | **Médio** — sobreposição parcial; ganho é unificação, não preencher um vazio total |
| **Reports** (Tremor charts) | Gráficos reais no dashboard | Dashboard mostra só KPI cards numéricos; **sem biblioteca de gráficos instalada** (`recharts`/`tremor` ausentes do `package.json`) | **Médio** |
| **Projects (kanban)** | Boards/secções/tarefas com drag&drop | `FaseProcessual` existe como dado, mas não há vista de board/kanban dos Processos por fase | **Baixo** (nice-to-have visual) |
| **Vector search / AI enrichment / MCP server** | Embeddings pgvector, agente de investigação via browser+Claude, servidor MCP com 127 tools | Nada equivalente | **N/A por agora** — interessante a médio prazo, prematuro sem necessidade de utilizador validada |
| Multi-currency | Lista de moedas gerível, formatação locale-aware | Cabo Verde usa exclusivamente CVE | **Não aplicável** |
| i18n (4 idiomas) | next-intl | Domínio é deliberadamente só Português (CLAUDE.md) | **Não aplicável** |
| Email client (IMAP/SMTP) | Cliente de email embutido | Não existe, não é core ao domínio jurídico | **Não aplicável / scope creep** |
| Leads/Opportunities/Targets/Contracts | Pipeline de vendas | `Processo` já cobre o papel de "entidade central rastreada"; não há pipeline de vendas no domínio jurídico | **Não aplicável** |

## Recomendações priorizadas para o próximo milestone

### Alto valor — gap real e concreto, alinhado ao core do produto

1. **Faturação/Recibos de Honorários** — maior gap identificado. Adotar o padrão de série numerada (ex. `REC-2026-0001`), estado formal (`RASCUNHO → EMITIDO → PAGO/PARCIAL/CANCELADO`), linhas de item (mesmo que simples: descrição + valor, sem imposto complexo — Cabo Verde não precisa do motor de IVA multi-taxa do NextCRM), e **PDF gerado server-side** para Recibo/Fatura, distinto do "Termo de Honorários" atual (que é um documento de proposta inicial, não um recibo de pagamento). Isto é trabalho nuclear para um escritório de advocacia — falta um documento fiscal/comprovativo real por pagamento.
2. **Pesquisa Global funcional** — ligar a barra já existente no topbar a um endpoint de pesquisa cross-entity (clientes, processos, documentos, pareceres) com resultados agrupados. Custo-benefício alto: a UI já "promete" isto, é a peça mais barata de implementar da lista.

### Médio valor — vale considerar

3. **Auditoria generalizada + página admin** — estender `AuditLog` (mais tipos de ação/entidade: criação/edição/eliminação de cliente, processo, honorário) e criar uma página `/admin/auditoria` filtrável. Encaixa bem no domínio jurídico (rastreabilidade "quem alterou o quê e quando" é uma exigência natural de compliance para um escritório).
4. **Dashboard com gráficos reais** — adicionar `recharts` (mais leve que Tremor, já no ecossistema React comum) para mostrar processos por fase, honorários recebidos por mês, prazos cumpridos vs. vencidos — substituindo/complementando os KPI cards atuais.

### Baixa prioridade — explorar mais tarde

5. **Vista Kanban de Processos por Fase** — açúcar visual sobre dados que já existem (`FaseProcessual`).
6. **Assistência de IA** (ex.: apoio à redação de Pareceres, resumo automático de processo) — interessante dado o ecossistema já usa Claude para construir o produto, mas é uma exploração própria, não deve ser mesclada com o milestone de faturação/auditoria.

### Não recomendado

Multi-currency, i18n multi-idioma, cliente de email embutido, e todo o núcleo de CRM de vendas (Leads/Opportunities/Targets) — não correspondem ao domínio de gestão jurídica institucional do LexCV.

## Próximos passos sugeridos

- Formalizar via `/gsd:new-milestone`, usando os itens de "Alto valor" (Faturação + Pesquisa Global) como candidatos principais de requirements — são o par com melhor relação gap-real/esforço.
- Alternativa: aprofundar primeiro o desenho de dados da Faturação (séries, estados, geração de PDF) antes de comprometer o roadmap, dado ser o item de maior superfície.
