# Phase 55: Formulários e Modais - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning
**Source:** Roadmap + autonomous planning (--skip-research)

<domain>
## Phase Boundary

Todos os formulários e diálogos da aplicação ficam utilizáveis em mobile:
- Formulários fluem em coluna única em ecrãs < 768px (sem campos lado a lado)
- Dialogs/modais abrem como bottom-sheet em mobile (deslizando de baixo para cima) ou full-screen
- Todos os inputs, selects e botões de ação têm altura mínima de 48px

Desktop permanece sem qualquer alteração visual.

</domain>

<decisions>
## Implementation Decisions

### FORM-01: Formulários em coluna única mobile
- Padrão: trocar `grid grid-cols-2` por `grid grid-cols-1 md:grid-cols-2` — CSS puro
- Formulários alvo: criação/edição de Clientes, Processos, Eventos, Honorários, Partes (sub-form em processo)
- Não tocar em formulários que já são coluna única (ex: login, setup)
- Procurar `grid-cols-2` em todos os ficheiros de schema/form e corrigir

### FORM-02: Dialogs como bottom-sheet em mobile
- Padrão: no componente shadcn `DialogContent`, adicionar className override para mobile:
  `"sm:max-w-lg sm:rounded-lg max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full"`
- `max-sm:` é o breakpoint Tailwind para "abaixo de 640px" — mais preciso para mobile
- Aplicar ao DialogContent que existe em componentes de criação/edição (ex: ClienteDialog, EventoDialog)
- Não modificar dialogs de confirmação simples (Delete confirm) — são pequenos, centrados está OK

### FORM-03: Touch targets mínimos de 48px
- Padrão: verificar botões de ação principais. Se `h-9` (36px) ou `h-10` (40px), trocar por `h-12` (48px) OU adicionar `min-h-[48px]` se o botão já tiver padding suficiente
- Inputs: o shadcn `Input` tem h-9 por defeito — adicionar `h-12 text-base` aos inputs em mobile com `max-sm:h-12`
- Foco: botões de submit nos formulários e inputs de texto — os mais críticos para touch
- Não modificar botões de tabela/card (já tratados com min-44px no Phase 54) nem ícones de ação pequenos em desktop

### Breakpoints
- `md:` (768px) para grid-cols — consistente com Phases 53 e 54
- `max-sm:` (abaixo de 640px) para bottom-sheet dialogs — mais granular que md para este caso de uso
- `sm:` no shadcn DialogContent já existe para desktop sizing — usar `max-sm:` para override mobile

</decisions>

<code_context>
## Existing Code Insights

### Formulários com grid-cols-2 (a modificar)
- `web/src/components/shared/cliente-form.tsx` ou equivalente — formulários de cliente
- `web/src/app/(dashboard)/clientes/novo/page.tsx` — criação de cliente
- `web/src/app/(dashboard)/processos/novo/page.tsx` — criação de processo
- Verificar via `grep -rn "grid-cols-2" web/src/` para listar todos os formulários

### Dialog components (FORM-02)
- shadcn `DialogContent` em `web/src/components/ui/dialog.tsx` — o primitivo base
- Componentes de dialog: procurar via `grep -rn "DialogContent" web/src/components/` para listar todos
- Estratégia: adicionar className ao DialogContent em cada dialog de formulário (não modificar o primitivo base — afectaria todos os dialogs incluindo confirms)

### shadcn Input (`web/src/components/ui/input.tsx`)
- Tem `h-9` hardcoded — para FORM-03, adicionar `max-sm:h-12 max-sm:text-base` via className override nos formulários (não modificar o primitivo base)

### Ficheiros de schemas Zod
- `web/src/schemas/` — schemas de validação; não precisam de alteração (são agnósticos de apresentação)

</code_context>

<specifics>
## Specific Ideas

- Para bottom-sheet: usar animação `data-[state=open]:animate-in data-[state=open]:slide-in-from-bottom` — já disponível no shadcn dialog
- Para grid único em mobile: `grid grid-cols-1 md:grid-cols-2 gap-4` — trocar apenas o `grid-cols-2` inicial
- Ordem de prioridade: FORM-01 (maior impacto) → FORM-02 (visual) → FORM-03 (acessibilidade)

</specifics>

<deferred>
## Deferred Ideas

- Bottom-sheet com handle drag — complexidade extra; slide-in-from-bottom sem drag é suficiente
- Formulários multi-step com progress indicator — fora de scope
- Teclado virtual em iOS (viewport push-up) — requer viewport meta tag ajuste, fora de scope
- Stepper/wizard de criação de processo — fora de scope

</deferred>
