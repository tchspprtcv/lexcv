# Requirements: LexCV v2.14 — UI/UX Melhorias

**Defined:** 2026-07-18
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

### Pesquisa Global (SRCH)

- [x] **SRCH-01**: Utilizador pesquisa por texto/identificador e obtém resultados de Clientes, Processos, Documentos e Pareceres do seu tenant, agrupados por tipo
- [x] **SRCH-02**: Resultados priorizam correspondências exatas/prefixo em identificadores estruturados (`numero_cliente`, `numero_processo`, NIF, `documento_numero`) acima de correspondências por substring
- [x] **SRCH-03**: A pesquisa dispara automaticamente com debounce (~300ms) a partir de 2 caracteres
- [x] **SRCH-04**: Cada resultado mostra um subtítulo desambiguador (ex: Cliente → número + NIF) e navega para a rota de detalhe existente ao clicar
- [x] **SRCH-05**: Utilizador abre a pesquisa pelo campo já existente no topbar ou pelo atalho Ctrl+K/⌘K
- [x] **SRCH-06**: Resultados só incluem tipos de entidade para os quais o utilizador tem permissão de visualização (`clientes:view`/`processos:view`/`documentos:view`/`pareceres:view`), verificado por ramo de query, nunca por filtro posterior
- [x] **SRCH-07**: Toda a pesquisa é isolada por tenant, incluindo em cada sub-query por tipo de entidade
- [x] **SRCH-08**: Utilizador vê estados de vazio (sem pesquisa), a carregar, e sem resultados
- [x] **SRCH-09**: Cada grupo de resultados tem um link "Ver todos" que abre a lista completa desse tipo, já filtrada pela pesquisa
- [x] **SRCH-10**: No estado vazio (antes de escrever), o utilizador vê os últimos registos que visitou (não pesquisas anteriores) — guardado apenas no cliente/sessão, nunca no servidor
- [x] **SRCH-11**: O texto correspondente à pesquisa é destacado visualmente em cada resultado

### Processos (PEST)

- [ ] **PEST-01**: Utilizador filtra a lista de Processos por estado através de um controlo dedicado

### Linguagem Visual (ICON, RAD, FICO)

- [ ] **ICON-01**: Todos os botões da aplicação (ações primárias e secundárias) apresentam um ícone consistente com a sua ação
- [ ] **RAD-01**: O token `--radius` global passa de reto (`0`) para arredondado, aplicado de forma consistente em todos os componentes (cartões, botões, inputs, badges, sidebar) em ambos os temas (claro/escuro)
- [ ] **FICO-01**: Botões de ação de filtro (aplicar/limpar/exportar) em todos os módulos (Clientes, Processos, Agenda, Documentos, Financeiro) apresentam-se apenas com ícone, com tooltip ao passar o rato

## v2 Requirements

Nenhum item foi deliberadamente adiado para v2 nesta milestone — os itens de âmbito reduzido (ver Out of Scope) foram excluídos, não adiados.

## Out of Scope

Explicitamente excluído do research (`PITFALLS.md`/`FEATURES.md`) e da conversa de definição de âmbito.

| Feature | Reason |
|---------|--------|
| Pesquisa dentro do conteúdo de documentos (OCR/full-text de ficheiros) | `Documento` só guarda metadados hoje — feature maior e separada, não "pesquisa global" |
| Matching fuzzy/tolerante a erros de escrita (`pg_trgm` similarity) | Baixo valor esperado — este domínio pesquisa sobretudo por identificadores estruturados (NIF, números de processo/cliente), não por nomes livres com erros de escrita; revisitar só com evidência |
| Command palette como lançador de ações (criar registo, navegar para além de resultados) | Dobra o âmbito da funcionalidade; classe de funcionalidade diferente de "pesquisa" |
| Histórico de pesquisas guardado / pesquisas guardadas | Risco de confidencialidade real num posto de trabalho institucional partilhado (revelaria o que um colega pesquisou); Pareceres já tem pesquisa avançada dedicada para esse caso de uso |
| Honorário/Financeiro como tipo de entidade diretamente pesquisável | `Honorario` não tem `tenant_id` próprio (só FK transitiva via `Processo`) — risco de fuga cross-tenant significativamente maior; fora de âmbito desta milestone |
| Índice B-tree em `Processo.numeroProcesso` | Sinalizado pelo research como teto de performance futuro, não bloqueador de v1 — revisitar se o volume de pesquisa justificar |

## Traceability

Mapeamento requisito → fase, atribuído durante a criação do roadmap (100% cobertura, cada requisito mapeado a exatamente uma fase).

| Requirement | Phase | Status |
|-------------|-------|--------|
| SRCH-01 | Phase 111 | Complete |
| SRCH-02 | Phase 111 | Complete |
| SRCH-03 | Phase 112 | Complete |
| SRCH-04 | Phase 112 | Complete |
| SRCH-05 | Phase 112 | Complete |
| SRCH-06 | Phase 111 | Complete |
| SRCH-07 | Phase 111 | Complete |
| SRCH-08 | Phase 112 | Complete |
| SRCH-09 | Phase 112 | Complete |
| SRCH-10 | Phase 112 | Complete |
| SRCH-11 | Phase 112 | Complete |
| PEST-01 | Phase 113 | Pending |
| ICON-01 | Phase 115 | Pending |
| RAD-01 | Phase 114 | Pending |
| FICO-01 | Phase 115 | Pending |

**Coverage:**
- v1 requirements: 15 total
- Mapped to phases: 15
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-18*
*Last updated: 2026-07-18 after roadmap creation (5 phases, 111–115, 100% coverage)*