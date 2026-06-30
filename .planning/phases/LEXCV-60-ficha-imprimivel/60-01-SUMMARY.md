---
phase: LEXCV-60-ficha-imprimivel
plan: 01
subsystem: ui
tags: [nextjs, react, typescript, print-css, rbac]

requires:
  - phase: LEXCV-58-formulario-dinamico
    provides: tipo PARTICULAR/EMPRESA, numero_cliente, avencado, dados_tipo
  - phase: LEXCV-59-procuracao-intake
    provides: descricao_caso, documentos_entregues/a_tratar, deslocacoes, honorarios_propostos, advogados/administrativos sub-resource endpoints
provides:
  - Página /clientes/[id]/ficha imprimível com 8 secções, CSS A4, guard de permissão
  - Cliente interface estendida com idade/sexo/nacionalidade
affects: [ficha-imprimivel, clientes]

tech-stack:
  added: []
  patterns:
    - "CSS de impressão inline via dangerouslySetInnerHTML em página dedicada (não em globals.css)"
    - "Campos opcionais ausentes renderizam linha sublinhada '___________' (font-mono underline) para preenchimento manuscrito"

key-files:
  created:
    - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  modified:
    - web/src/types/clientes.ts

key-decisions:
  - "Usados os hooks useClienteAdvogados/useClienteAdministrativos (já existentes desde Phase 59) em vez de campos string planos advogados/administrativos assumidos pelo plano — o modelo de dados real usa sub-recursos estruturados (ClienteAdvogadoUser[]), não strings livres"
  - "Campos documentos_entregues, documentos_a_tratar, deslocacoes, honorarios_propostos já existiam como tipos estruturados (arrays/objects) da Phase 59; a ficha junta os arrays com vírgula para exibição em texto"
  - "idade/sexo/nacionalidade adicionados como campos planos opcionais no nível superior do Cliente (com fallback para dados_tipo quando tipo PARTICULAR), mantendo compatibilidade com a estrutura dados_tipo já existente"

patterns-established:
  - "Ficha imprimível: nova aba/rota dedicada com CSS @page A4 inline, oculta aside/header/bottom-nav/botão de impressão via @media print"

requirements-completed: [FICH-01, FICH-02]

duration: 25min
completed: 2026-06-30
---

# Phase LEXCV-60 Plan 01: Ficha Cliente Imprimível Summary

**Página /clientes/[id]/ficha com CSS de impressão A4 inline, 8 secções espelhando o formulário físico do escritório, e guard ASVS V4 de permissão clientes:view**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-06-30T12:30:00Z
- **Completed:** 2026-06-30T12:53:45Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Interface `Cliente` (web/src/types/clientes.ts) estendida com `idade`, `sexo`, `nacionalidade` opcionais (demais campos de Phase 57/59 já existiam com tipos estruturados)
- Página `/clientes/[id]/ficha` criada como Client Component com guard de permissão, 8 secções (Identificação, Contactos, Descrição do Caso, Advogados e Administrativos, Documentos, Deslocações, Honorários, Data e Assinaturas), CSS `@page { size: A4; margin: 2cm }` e `@media print` que oculta aside/header/bottom-nav/botão de impressão, botão "Imprimir" que chama `window.print()`

## Task Commits

Each task was committed atomically:

1. **Task 1: Estender interface Cliente com idade/sexo/nacionalidade** - `ae2c8ac` (feat)
2. **Task 2: Criar página de ficha imprimível /clientes/[id]/ficha** - `294ec21` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `web/src/types/clientes.ts` - Adicionados campos opcionais `idade`, `sexo`, `nacionalidade` à interface Cliente
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` - Página de ficha imprimível com guard de permissão, 8 secções e CSS de impressão A4

## Decisions Made
- O codebase real (pós Phase 57/59) já modela `descricao_caso`, `documentos_entregues`, `documentos_a_tratar`, `deslocacoes`, `honorarios_propostos`, `numero_cliente`, `avencado` como campos estruturados (arrays/objects) na interface `Cliente`, diferente do que o plano assumia (campos string planos). Os advogados/administrativos são geridos via sub-recursos (`useClienteAdvogados`/`useClienteAdministrativos`), não como string livre no Cliente. A página de ficha foi adaptada para consumir esses tipos reais em vez de inventar campos paralelos que duplicariam o modelo de dados existente.
- `idade`/`sexo`/`nacionalidade` foram adicionados como campos planos opcionais no nível raiz do Cliente (em vez de forçar leitura exclusiva via `dados_tipo`), com fallback de leitura de `dados_tipo` quando presente, para compatibilidade futura caso o backend exponha ambos os formatos.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug/Stale Plan Assumption] Plano assumia campos string planos (advogados, administrativos, documentos_entregues, etc.) que já foram implementados como tipos estruturados nas Phases 57/59**
- **Found during:** Task 1 (leitura de web/src/types/clientes.ts antes de editar)
- **Issue:** O `<context>` do plano (interfaces "actuais") estava desatualizado — referia-se ao estado da interface Cliente ANTES das Phases 57/59 serem executadas. A interface real já tinha `numero_cliente`, `avencado`, `dados_tipo`, `descricao_caso`, `documentos_entregues: DocumentoEntregue[]`, `documentos_a_tratar: DocumentoATratar[]`, `deslocacoes: Deslocacao[]`, `honorarios_propostos: HonorariosPropostos`. Adicionar campos string duplicados (`advogados?: string`, `honorarios_por_extenso?: string`, etc.) conforme o plano pediria literalmente teria criado dados paralelos/inconsistentes com os tipos estruturados já em uso pelo resto da aplicação (ex.: `ClienteDetailContent` já usa `ClienteAdvogadoUser[]` via hooks).
- **Fix:** Adicionados apenas os 3 campos genuinamente em falta (`idade`, `sexo`, `nacionalidade`) à interface Cliente. A página de ficha consome os tipos estruturados reais: `documentos_entregues`/`documentos_a_tratar`/`deslocacoes` são arrays unidos por vírgula para exibição; `honorarios_propostos.total`/`totalPorExtenso`/`previsao`; advogados/administrativos lidos via `useClienteAdvogados`/`useClienteAdministrativos` (hooks já existentes da Phase 59) e seus nomes unidos por vírgula.
- **Files modified:** web/src/types/clientes.ts, web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
- **Verification:** `tsc --noEmit` (executado via symlink temporário para node_modules do repo principal, removido depois) não reportou erros
- **Committed in:** ae2c8ac, 294ec21

---

**Total deviations:** 1 auto-fixed (Rule 1 — adaptação a modelo de dados real em vez de duplicar campos string já estruturados)
**Impact on plan:** Resultado funcionalmente equivalente ao especificado (8 secções, mesmos rótulos, mesmo comportamento de campo em branco) mas usando os tipos de dados corretos e já estabelecidos pelo codebase, evitando inconsistência de dados duplicados.

## Issues Encountered
- `node_modules` não estava instalado no worktree (`web/`). Para verificar `tsc --noEmit`, foi criado um symlink temporário apontando para `node_modules` do checkout principal do repositório, usado apenas para a verificação e depois descartado (não rastreado pelo git — `git status` permanece limpo). `tsc --noEmit` passou sem erros.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Ficha imprimível funcional e pronta para verificação visual/manual (abrir `/clientes/{id}/ficha`, conferir secções, testar impressão)
- Sem bloqueios conhecidos para fases subsequentes

---
*Phase: LEXCV-60-ficha-imprimivel*
*Completed: 2026-06-30*
