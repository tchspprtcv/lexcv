# Requirements: LexCV — v2.13 Refactor UI/UX (shadcn/ui)

**Defined:** 2026-07-15
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requisitos para este milestone. Cada um mapeia para uma fase do roadmap.

### Fundação — CLI e Design Tokens

- [x] **FND-01**: `web/` tem `components.json` formalmente inicializado via `shadcn init -b radix` (mantém paridade de composição `asChild` com os 9 pacotes `@radix-ui/react-*` já em uso)
- [x] **FND-02**: `webpage/` tem `components.json` inicializado com as respostas copiadas manualmente do `web/` (sem re-executar o wizard independentemente, evitando divergência entre as duas apps — não são workspace members reais)
- [x] **FND-03**: `globals.css` de ambas as apps tem o conjunto completo de tokens semânticos do shadcn (`--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`) mesclado aditivamente, com `--background`/`--foreground` restaurados aos valores hex já validados e `--radius`/`--primary` definidos deliberadamente para a identidade institucional (não deixados no default do CLI)
- [x] **FND-04**: Os ~15 primitivos em falta (Select, NativeSelect, Tabs, DropdownMenu, Command, Tooltip, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu, Empty) adicionados via CLI em `web/`
- [x] **FND-05**: Pacotes Radix unificados — `shadcn migrate radix` corrido para que componentes existentes e novos usem o mesmo pacote `radix-ui`, sem estado de ponte dual
- [x] **FND-06**: `react-day-picker` fixado em `9.14.0` (não `@latest`) imediatamente após `add calendar`, devido a bug conhecido não resolvido na v10
- [x] **FND-07**: `tailwindcss-animate` (depreciado) substituído por `tw-animate-css`
- [x] **FND-08**: Sonner adotado como substituto do `Toast` depreciado — `<Toaster />` do `sonner` montado na raiz de `web/` (e `webpage/` se aplicável), `toast.tsx`/`toaster.tsx`/`@radix-ui/react-toast` removidos, chamadas `toast.success()`/`toast.error()` existentes preservadas sem alteração de call-site

### Reconciliação do Design System

- [x] **DSR-01**: Cada um dos 14 componentes hand-rolled existentes (`button`, `dialog`, `alert-dialog`, `card`, `table`, `sheet`, `badge`, `input`, `label`, `popover`, `radio-group`, `switch`, `textarea`) reconciliado individualmente via `add <component> --diff` — nunca overwrite cego — preservando variantes/props customizadas (ex.: variante `gray` do badge)
- [x] **DSR-02**: As 93 ocorrências de import destes 14 componentes (38 ficheiros) continuam a compilar e passar typecheck após a reconciliação
- [x] **DSR-03**: `Tooltip` adicionado a botões icon-only em toda a app (ícones da sidebar colapsada, ações de linha icon-only), com `TooltipProvider` montado uma vez na raiz

### Dashboard

- [x] **DASH-01**: Estados de loading do Dashboard (KPI cards, Atividade Recente) usam `Skeleton` em vez de texto "A carregar..."
- [x] **DASH-02**: Estados vazios do Dashboard usam `Empty` em vez de mensagens ad hoc

### DataTable Partilhada

- [x] **DTB-01**: `@tanstack/react-table` adicionado como dependência; padrão partilhado (`columns.tsx` + `data-table.tsx` + toolbar de filtro + `DataTablePagination`/`DataTableViewOptions`) construído uma vez sobre o `Table` existente
- [x] **DTB-02**: Listas de Clientes, Processos, Pareceres, Financeiro e Documentos migradas para o padrão DataTable partilhado (ordenação por coluna, toolbar de filtro) sem duplicar os filtros já servidos pelo backend via TanStack Query
- [x] **DTB-03**: `Pagination` oficial aplicada em `/notificacoes` e qualquer outra lista paginada no servidor

### Clientes + Processos

- [x] **CLP-01**: Ficha de Cliente (7 separadores) migrada de botões-toggle manuais para `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`, preservando contagem de separadores condicional por RBAC e `overflow-x-auto` em mobile
- [x] **CLP-02**: Ficha de Processo (Partes/Fases/Decisões/Factos/Testemunhas/Documentos) migrada para o mesmo padrão `Tabs`, entregue em conjunto com CLP-01 (nunca isoladamente)
- [x] **CLP-03**: Todos os `<select className={selectClassName}>` nativos em formulários de Clientes/Processos substituídos por `NativeSelect`/`Select`
- [x] **CLP-04**: `Avatar` usado para representar advogados/administrativos/testemunhas em listagens e pickers
- [x] **CLP-05**: Cabeçalhos das fichas de Cliente/Processo usam `Breadcrumb` em vez do `<div>`+`Link`+"/" atual

### Agenda

- [x] **AGD-36**: `Calendar` (shadcn/react-day-picker) usado nos inputs de data dos formulários de Agenda (criar/editar prazo), sem alterar a vista de calendário mensal existente
- [x] **AGD-37**: Filtros de categoria/status da Agenda usam `Select`

### Documentos + Financeiro

- [x] **DOF-01**: Upload de documentos usa `Progress` oficial em vez da UI de progresso customizada existente
- [x] **DOF-02**: Formulários de tipo de documento/honorário/pagamento usam `Select`

### Pareceres

- [x] **PARC-18**: Campos de formulário de Pareceres usam `Select`
- [x] **PARC-19**: Eventos da timeline de Pareceres usam `Tooltip`
- [x] **PARC-20**: Histórico de versionamento usa `Accordion` para colapsar versões antigas

### Notificações / Settings / Setup

- [ ] **NTF-28**: Novo menu de utilizador na topbar usa `DropdownMenu`
- [x] **NTF-29**: Contador de não-lidas do sino trocado do `<span>` manual para `Badge` oficial (o próprio Popover do sino mantém-se — já correto, não é alterado)
- [x] **NTF-30**: Wizard `/setup` usa indicador de progresso linear baseado em `Progress` (sem Stepper de terceiros)

### Landing (webpage/)

- [ ] **LDG-17**: Navegação mobile adicionada ao `SiteHeader` via `Sheet` reutilizado (atualmente zero navegação em mobile — gap funcional real)
- [ ] **LDG-18**: Secções Hero e Contacto reestruturadas com composição `Card`/`Badge`, replicando o padrão já idiomático do `TrustSection`

## v2 Requirements

Reconhecidos mas adiados. Não fazem parte do roadmap atual.

### Dashboard Avançado

- **DASH-V2-01**: `Chart` (Recharts) para tendências do Dashboard — bloqueado por endpoint de série temporal de KPIs inexistente no backend
- **DASH-V2-02**: Cálculo real de delta/tendência nos badges de KPI (hoje hardcoded, ex. `+12%`)

### Documentos

- **DOF-V2-01**: `Combobox` para o campo tipo-de-documento em Documentos Entregues — já funcional hoje via `datalist` nativo, upgrade puramente cosmético (coberto/resolvido pela Fase 107: migração `datalist`→`Combobox` criável em `ClienteDocumentosEntreguesTab`)

## Out of Scope

Explicitamente excluído. Documentado para prevenir scope creep.

| Feature | Reason |
|---------|--------|
| Adoção do bloco oficial `Sidebar`/`SidebarProvider` para substituir `dashboard-shell.tsx`/`bottom-nav.tsx` | Redesenho estrutural do layout institucional já validado contra Figma — decisão explícita de preservar identidade visual, não é um redesign |
| `NavigationMenu` mega-menu na `webpage/` ou dentro do app shell `web/` | Só compensa em `webpage/` se a navegação crescer além de 3 links âncora; usar dentro de `web/` seria uso semanticamente indevido do componente (é para navegação de website, não sidebar de app) |
| Row-selection/ações em massa em qualquer DataTable | Nenhuma ação em massa existe hoje no produto — não inventar UI para uma capacidade inexistente |
| Instalação de skills/pacotes/registries de terceiros (`pnpm dlx skills add shadcn/ui`, shadcnblocks.com, shadcndesign.com, etc.) | Decisão explícita do utilizador — apenas a CLI oficial `shadcn@latest`; ferramentas externas não verificadas representam risco de supply-chain |
| Migração das páginas de login/auth para os blocos oficiais `login-0X` | Fora do âmbito desta pesquisa; risco baixo mas não avaliado — candidato a milestone futura se necessário |
| Criação de `packages/ui` partilhado entre `web/` e `webpage/` (padrão monorepo oficial do shadcn) | `web/`/`webpage/` não são workspace members reais hoje (sem `pnpm-workspace.yaml` raiz); o trabalho prévio necessário (workspace raiz, lockfiles unificados, contextos de build Docker/CI reescritos — tocando o pipeline recém-estabilizado na v2.12/Phase 100) excede em muito o benefício, dado que `webpage/` só tem 2 ficheiros hoje |

## Traceability

Que fases cobrem que requisitos. Preenchido durante a criação do roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FND-01 | Phase 101 | Complete |
| FND-02 | Phase 101 | Complete |
| FND-03 | Phase 101 | Complete |
| FND-04 | Phase 101 | Complete |
| FND-05 | Phase 101 | Complete |
| FND-06 | Phase 101 | Complete |
| FND-07 | Phase 101 | Complete |
| FND-08 | Phase 101 | Complete |
| DSR-01 | Phase 102 | Complete |
| DSR-02 | Phase 102 | Complete |
| DSR-03 | Phase 102 | Complete |
| DASH-01 | Phase 103 | Complete |
| DASH-02 | Phase 103 | Complete |
| DTB-01 | Phase 104 | Complete |
| DTB-02 | Phase 104 | Complete |
| DTB-03 | Phase 104 | Complete |
| CLP-01 | Phase 105 | Complete |
| CLP-02 | Phase 105 | Complete |
| CLP-03 | Phase 105 | Complete |
| CLP-04 | Phase 105 | Complete |
| CLP-05 | Phase 105 | Complete |
| AGD-36 | Phase 106 | Complete |
| AGD-37 | Phase 106 | Complete |
| DOF-01 | Phase 107 | Complete |
| DOF-02 | Phase 107 | Complete |
| PARC-18 | Phase 108 | Complete |
| PARC-19 | Phase 108 | Complete |
| PARC-20 | Phase 108 | Complete |
| NTF-28 | Phase 109 | Pending |
| NTF-29 | Phase 109 | Complete |
| NTF-30 | Phase 109 | Complete |
| LDG-17 | Phase 110 | Pending |
| LDG-18 | Phase 110 | Pending |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33 (100%)
- Unmapped: 0

**Phase summary:**
- Phase 101 (Fundação — CLI Init e Design Tokens): FND-01 to FND-08 — first phase, gates everything else
- Phase 102 (Reconciliação do Design System): DSR-01 to DSR-03 — depends on Phase 101; gates every module phase below
- Phase 103 (Módulo Dashboard): DASH-01, DASH-02 — depends on Phase 102; parallelizable with Phase 104
- Phase 104 (Padrão DataTable Partilhado): DTB-01 to DTB-03 — depends on Phase 102; parallelizable with Phase 103
- Phase 105 (Módulos Clientes + Processos): CLP-01 to CLP-05 — depends on Phase 101 and Phase 104 (sequencing, not file dependency)
- Phase 106 (Módulo Agenda): AGD-36, AGD-37 — depends on Phase 102; parallelizable with Phases 103, 105, 107, 108, 109
- Phase 107 (Módulos Documentos + Financeiro): DOF-01, DOF-02 — depends on Phase 102; parallelizable with Phases 103, 105, 106, 108, 109
- Phase 108 (Módulo Pareceres): PARC-18 to PARC-20 — depends on Phase 102; parallelizable with Phases 103, 105, 106, 107, 109
- Phase 109 (Notificações / Settings / Setup Wizard): NTF-28 to NTF-30 — depends on Phase 102; parallelizable with Phases 103, 105, 106, 107, 108
- Phase 110 (Refinamento da Landing webpage/): LDG-17, LDG-18 — depends on Phase 101 only; parallelizable with any of Phases 103–109

Roadmap: 10 fases (101–110), continuando a numeração de fases da v2.12 (última fase: 100). Ver `.planning/ROADMAP.md` para Goal/Depends on/Success Criteria completos de cada fase.

---
*Requirements defined: 2026-07-15*
*Last updated: 2026-07-15 after roadmap creation (100% coverage, 10 phases: 101-110)*
