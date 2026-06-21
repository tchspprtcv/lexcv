# Phase 55: Formulários e Modais - Pattern Map

**Mapped:** 2026-06-21
**Files analyzed:** 14 ficheiros com grid-cols-2 + 5 DialogContent com conteúdo de formulário
**Analogs found:** 14 / 14 (todos existem no codebase — são os próprios alvos de modificação)

---

## File Classification

| Ficheiro a Modificar | Role | Data Flow | Padrão Alvo | Tarefa |
|----------------------|------|-----------|-------------|--------|
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | form-page | request-response | FORM-01 | 4× `grid gap-4 sm:grid-cols-2` → `grid gap-4 grid-cols-1 md:grid-cols-2` |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | form-page | request-response | FORM-01 | 4× `grid gap-4 sm:grid-cols-2` → `grid gap-4 grid-cols-1 md:grid-cols-2` |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | form-page | request-response | FORM-01 | 3× `grid gap-4 sm:grid-cols-2` → `grid gap-4 grid-cols-1 md:grid-cols-2` |
| `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` | form-page | request-response | FORM-01 | 4× `grid gap-4 sm:grid-cols-2` → `grid gap-4 grid-cols-1 md:grid-cols-2` |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | detail-page | request-response | FORM-01 + FORM-02 | linha 1218 grid-cols-2; dialogs linhas 672 e 819 |
| `web/src/app/(dashboard)/financeiro/novo/page.tsx` | form-page | request-response | FORM-01 | linha 135 `grid gap-4 sm:grid-cols-2` |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | detail-page | request-response | FORM-01 + FORM-02 | linha 452 grid-cols-2; dialog linha 271 |
| `web/src/app/(dashboard)/clientes/page.tsx` | list-page | request-response | FORM-01 | linha 345 filtros `sm:grid-cols-2` |
| `web/src/app/(dashboard)/processos/page.tsx` | list-page | request-response | FORM-01 | linha 135 filtros `sm:grid-cols-2` |
| `web/src/app/(dashboard)/settings/page.tsx` | settings-page | request-response | FORM-01 | linhas 484, 558 grids |
| `web/src/components/profile/user-profile-form.tsx` | component | request-response | FORM-01 | linha 134 `grid gap-4 sm:grid-cols-2` |
| `web/src/components/profile/user-password-form.tsx` | component | request-response | FORM-01 | linha 114 `grid gap-4 sm:grid-cols-2` |
| `web/src/components/ui/dialog.tsx` | ui-primitive | — | FORM-02 (NÃO modificar base) | referência apenas — ver decisão FORM-02 |
| `web/src/components/ui/input.tsx` | ui-primitive | — | FORM-03 (NÃO modificar base) | referência apenas — ver decisão FORM-03 |

---

## FORM-01: Lista Completa de Ficheiros com `grid-cols-2` (alvos de mudança)

### Ficheiros de formulário com `sm:grid-cols-2` (padrão que precisa de correcção)

Estes usam `sm:grid-cols-2` que significa 2 colunas já a partir de 640px — demasiado cedo para mobile.
A correcção é trocar por `grid-cols-1 md:grid-cols-2` (2 colunas só a partir de 768px).

| Ficheiro | Linhas | Ocorrências |
|----------|--------|-------------|
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | 117, 135, 152, 175 | 4× |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | 154, 172, 189, 212 | 4× |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | 278, 327, 370 | 3× |
| `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` | 161, 179, 205, 223 | 4× |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | 1218 | 1× (dentro de dialog) |
| `web/src/app/(dashboard)/financeiro/novo/page.tsx` | 135 | 1× |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | 452 | 1× (dentro de card) |
| `web/src/app/(dashboard)/clientes/page.tsx` | 345 | 1× (filtros) |
| `web/src/app/(dashboard)/processos/page.tsx` | 135 | 1× (filtros) |
| `web/src/app/(dashboard)/settings/page.tsx` | 484 | 1× |
| `web/src/components/profile/user-profile-form.tsx` | 134 | 1× |
| `web/src/components/profile/user-password-form.tsx` | 114 | 1× |
| `web/src/app/setup/page.tsx` | 162 | 1× (setup wizard — verificar se mobile é relevante) |

### Ficheiros com `grid-cols-2` SEM prefixo responsivo (NÃO são formulários — IGNORAR)

Estes usam grid fixo de 2 colunas para elementos de UI que não são campos de formulário:

| Ficheiro | Linha | Contexto | Acção |
|----------|-------|----------|-------|
| `web/src/app/(dashboard)/agenda/page.tsx` | 475 | Card de estatísticas (Audiências / Urgentes) — display, não form | IGNORAR |
| `web/src/app/(dashboard)/financeiro/page.tsx` | 186 | KPI cards `grid grid-cols-2 sm:grid-cols-4` | IGNORAR |
| `web/src/app/(dashboard)/settings/page.tsx` | 558 | Grid de permissões `grid-cols-2 sm:grid-cols-4` — checkboxes, não inputs | AVALIAR |
| `web/src/app/(dashboard)/dashboard/page.tsx` | 218 | KPI cards — layout, não form | IGNORAR |
| `web/src/app/(dashboard)/processos/dashboard/page.tsx` | 66, 139 | Dashboard cards | IGNORAR |

---

## FORM-02: Lista Completa de Ficheiros com `DialogContent` e Conteúdo de Formulário

### Dialogs COM formulário (precisam de bottom-sheet em mobile)

| Ficheiro | Linhas DialogContent | Conteúdo | Acção FORM-02 |
|----------|---------------------|----------|---------------|
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | 271–323 | Formulário de edição de honorário (valor, data, descrição) | Adicionar className bottom-sheet |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | 673–715 | Formulário de justificativa de transição (textarea) | Adicionar className bottom-sheet |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | 820–907 | Formulário de novo prazo (descrição, data, prioridade, responsável) | Adicionar className bottom-sheet |

### Dialogs de CONFIRMAÇÃO SIMPLES (NÃO precisam de bottom-sheet — pequenos, centrados OK)

| Ficheiro | Linhas | Tipo | Acção |
|----------|--------|------|-------|
| `web/src/app/(dashboard)/agenda/[id]/page.tsx` | 172–214 | `AlertDialogContent` — confirmar exclusão de evento | IGNORAR |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | 333–349 | `AlertDialogContent` — apagar honorário | IGNORAR |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | 524–540 | `AlertDialogContent` — apagar pagamento | IGNORAR |

---

## Pattern Assignments

### FORM-01: Substituição de `sm:grid-cols-2` → `grid-cols-1 md:grid-cols-2`

**Analog:** `web/src/app/(dashboard)/clientes/novo/page.tsx` (linhas 117–133)

**Padrão actual a substituir:**
```tsx
{/* ANTES — 2 colunas já a 640px, demasiado cedo para mobile */}
<div className="grid gap-4 sm:grid-cols-2">
  <div className="space-y-2">
    <Label htmlFor="nif">NIF (Legado)</Label>
    <Input id="nif" className="rounded-none" {...form.register("nif")} />
    {form.formState.errors.nif ? (
      <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
    ) : null}
  </div>
  <div className="space-y-2">
    <Label htmlFor="tipo">Tipo</Label>
    <Input id="tipo" className="rounded-none" {...form.register("tipo")} />
    {form.formState.errors.tipo ? (
      <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
    ) : null}
  </div>
</div>
```

**Padrão corrigido (FORM-01):**
```tsx
{/* DEPOIS — coluna única em mobile, 2 colunas apenas a partir de md (768px) */}
<div className="grid gap-4 grid-cols-1 md:grid-cols-2">
  {/* interior não muda */}
</div>
```

**Regra de substituição:** `"grid gap-4 sm:grid-cols-2"` → `"grid gap-4 grid-cols-1 md:grid-cols-2"`

Para `gap-6` em vez de `gap-4`: `"grid gap-6 sm:grid-cols-2"` → `"grid gap-6 grid-cols-1 md:grid-cols-2"`

---

### FORM-02: Bottom-sheet em mobile para DialogContent com formulário

**Analog:** `web/src/app/(dashboard)/processos/[id]/page.tsx` (linha 673) — já tem className override

**DialogContent actual (primitivo base — `web/src/components/ui/dialog.tsx` linha 41):**
```tsx
className={cn(
  "fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 bg-white p-6 shadow-2xl duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] dark:bg-[#020617] rounded-none",
  className,
)}
```

**NÃO modificar o primitivo base** — afectaria todos os dialogs incluindo confirms.

**Padrão a aplicar em cada DialogContent de formulário:**
```tsx
{/* ANTES */}
<DialogContent>

{/* DEPOIS — bottom-sheet em mobile, centrado em desktop */}
<DialogContent className="max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-full max-sm:data-[state=open]:slide-in-from-bottom max-sm:data-[state=closed]:slide-out-to-bottom">
```

Para dialogs que já têm className (ex: `processos/[id]/page.tsx` linha 673 `className="rounded-none shadow-2xl"`):
```tsx
{/* ANTES */}
<DialogContent className="rounded-none shadow-2xl">

{/* DEPOIS */}
<DialogContent className="rounded-none shadow-2xl max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-full max-sm:data-[state=open]:slide-in-from-bottom max-sm:data-[state=closed]:slide-out-to-bottom">
```

---

### FORM-03: Touch targets mínimos de 48px

**Input actual — `web/src/components/ui/input.tsx` linha 11:**
- Altura actual: `h-9` = 36px (abaixo do mínimo de 48px para touch)
- NÃO modificar o primitivo base

**Padrão a aplicar nos inputs de formulários mobile:**
```tsx
{/* ANTES */}
<Input id="nome" className="rounded-none" {...form.register("nome")} />

{/* DEPOIS — h-12 (48px) em mobile, h-9 (36px) mantido em desktop */}
<Input id="nome" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
```

**Button actual — `web/src/components/ui/button.tsx` linha 20:**
```
default: "h-9 px-4 py-2"   → 36px (abaixo do mínimo)
sm:      "h-8 ..."          → 32px
lg:      "h-10 ..."         → 40px (ainda abaixo)
icon:    "h-9 w-9"          → 36px
```

**Padrão a aplicar nos botões de submit de formulários:**
```tsx
{/* ANTES */}
<Button type="submit" disabled={...}>Guardar</Button>

{/* DEPOIS — min-h-[48px] garante touch target sem quebrar layout desktop */}
<Button type="submit" disabled={...} className="max-sm:min-h-[48px]">Guardar</Button>
```

---

## Shared Patterns

### Breakpoints a usar (consistente com CONTEXT.md)
- `md:` (768px) para grid de formulários (FORM-01)
- `max-sm:` (abaixo de 640px) para bottom-sheet e touch targets (FORM-02, FORM-03)

### Estrutura típica de campo de formulário (não muda — apenas o grid wrapper muda)
```tsx
<div className="space-y-2">
  <Label htmlFor="campo">Rótulo</Label>
  <Input id="campo" className="rounded-none" {...form.register("campo")} />
  {form.formState.errors.campo ? (
    <p className="text-sm text-red-600">{form.formState.errors.campo.message}</p>
  ) : null}
</div>
```

### Formulários de página inteira vs. dialogs
- **Páginas de criação/edição** (`/novo`, `/editar`): apenas FORM-01 (grid) + FORM-03 (touch targets)
- **Dialogs com formulário**: FORM-01 (se tiver grid interno) + FORM-02 (bottom-sheet) + FORM-03

---

## Sem Analog (ficheiros novos sem equivalente)

Nenhum. Todos os ficheiros desta fase já existem — são modificações de ficheiros existentes, não criações.

---

## Metadata

**Scope de pesquisa:** `web/src/` (todo o frontend)
**Ficheiros com `grid-cols-2` encontrados:** 38 ocorrências em 14 ficheiros únicos
**DialogContent encontrados:** 5 instâncias de uso (excluindo definição e AlertDialog)
**Data de extracção:** 2026-06-21
