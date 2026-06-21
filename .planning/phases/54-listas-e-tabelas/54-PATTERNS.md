# Phase 54: Listas e Tabelas — Mapa de Padrões

**Mapeado em:** 2026-06-21
**Ficheiros analisados:** 7 (5 páginas + badge + button)
**Análogos encontrados:** 7 / 7

---

## Classificação de Ficheiros

| Ficheiro a modificar | Papel | Fluxo de dados | Análogo mais próximo | Qualidade |
|---|---|---|---|---|
| `web/src/app/(dashboard)/clientes/page.tsx` | página lista | request-response | ele próprio (referência) | exact |
| `web/src/app/(dashboard)/documentos/page.tsx` | página lista | request-response | `financeiro/page.tsx` | exact |
| `web/src/app/(dashboard)/financeiro/page.tsx` | página lista | request-response | `documentos/page.tsx` | exact |
| `web/src/app/(dashboard)/agenda/page.tsx` | página calendário | event-driven | `clientes/page.tsx` (Card pattern) | partial |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | página detalhe c/ tabs | request-response | ele próprio (referência) | exact |
| `web/src/components/ui/badge.tsx` | componente UI | — | ele próprio | exact |
| `web/src/components/ui/button.tsx` | componente UI | — | ele próprio | exact |

---

## Padrões por Ficheiro

### `clientes/page.tsx` — Lista simples com tabela shadcn/Table

**Estrutura da tabela** (linhas 387–403):
```tsx
<div className="overflow-hidden">
  <Table>
    <TableHeader>
      <TableRow className="bg-slate-50/50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Nome / Razão Social</TableHead>
        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Tipo</TableHead>
        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NIF</TableHead>
        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Contacto</TableHead>
        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px] text-right">Ações</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      {clientes.data.map((c) => (
        <ClienteRow key={c.id} cliente={c} canEditClientes={canEditClientes} />
      ))}
    </TableBody>
  </Table>
</div>
```

**Imports da tabela** (linha 12):
```tsx
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
```

**Componente wrapper da tabela** — a tabela está dentro de `<Card><CardContent className="p-0">` (linha 375–420). O wrapper interno é `<div className="overflow-hidden">`, **sem** `overflow-x-auto`.

**Coluna Tipo com Badge** (linhas 469–471):
```tsx
<TableCell>
  <Badge variant={badgeVariant as "blue" | "purple" | "gray"} className="rounded-none font-bold tracking-wide">
    {tipo || "—"}
  </Badge>
</TableCell>
```

**Ações na linha** (linhas 487–511):
```tsx
<TableCell className="text-right">
  <div className="inline-flex items-center gap-1">
    <Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
      <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
        <Eye className="h-4 w-4" />
      </Link>
    </Button>
    {canEditClientes ? (
      <Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors">
        <Link href={`/clientes/${encodeURIComponent(cliente.id)}/editar`}>
          <Pencil className="h-4 w-4" />
        </Link>
      </Button>
    ) : null}
    {canEditClientes ? (
      <Button type="button" size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-red-600 dark:hover:text-red-400 transition-colors" onClick={onDelete} disabled={del.isPending}>
        <Trash2 className="h-4 w-4" />
      </Button>
    ) : null}
  </div>
</TableCell>
```

**Padrão de card de linha (avatar + nome)** — A primeira célula usa um avatar com iniciais:
```tsx
<TableCell>
  <div className="flex items-center gap-3">
    <div className="h-10 w-10 rounded-none bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
      {initials}
    </div>
    <div className="min-w-0">
      <Link href={...} className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
        {cliente.nome}
      </Link>
      <div className="text-[11px] font-medium tracking-wider uppercase text-slate-500 dark:text-slate-400 mt-0.5">ID: #{idShort}</div>
    </div>
  </div>
</TableCell>
```

**Padrão de card existente para KPIs** — Já existe um `<Card>` estilizado (linha 231–258) com `<CardContent className="p-5">`. Este é o estilo de referência para os mobile cards da lista:
```tsx
<Card>
  <CardContent className="p-5">
    <div className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">Label</div>
    <div className="mt-2 text-3xl font-bold text-slate-900 dark:text-white">{valor}</div>
  </CardContent>
</Card>
```

**Estado actual:** Nenhum padrão `hidden md:block` / `block md:hidden` existe. A tabela é o único modo de apresentação.

---

### `documentos/page.tsx` — Lista simples com `<table>` nativo

**Estrutura da tabela** (linhas 136–169):
```tsx
<div className="overflow-x-auto">
  <table className="w-full text-sm">
    <thead className="text-left text-neutral-500 dark:text-neutral-400">
      <tr className="border-b border-neutral-200 dark:border-neutral-800">
        <th className="py-2 pr-4 font-medium">Nome</th>
        <th className="py-2 pr-4 font-medium">Tipo</th>
        <th className="py-2 pr-4 font-medium">Processo</th>
        <th className="py-2 pr-4 font-medium">Cliente</th>
        <th className="py-2 pr-4 font-medium">Confid.</th>
        <th className="py-2 pr-4 font-medium">Ver.</th>
        <th className="py-2 pr-4 font-medium">Tamanho</th>
        <th className="py-2 pr-4 font-medium">Criado</th>
        <th className="py-2 pr-4 font-medium">Ações</th>
      </tr>
    </thead>
    <tbody>
      {list.data.map((d) => (
        <DocumentoRow key={d.id} ... />
      ))}
    </tbody>
  </table>
</div>
```

**Coluna de ações** (linha 235–239):
```tsx
<td className="py-2 pr-4">
  {canEditDocumentos ? (
    <Button type="button" variant="outline" onClick={onDelete} disabled={del.isPending}>
      {del.isPending ? "A apagar..." : "Apagar"}
    </Button>
  ) : null}
</td>
```

**Estilo de linha** (linha 218):
```tsx
<tr className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
```

**Container da tabela** — dentro de `<Card><CardContent>` (linhas 120–173). O wrapper da tabela é `<div className="overflow-x-auto">`.

**Nota de design:** usa `neutral-*` (não `slate-*`). As colunas são 9 — mais largas do que qualquer outra página, por isso precisa de scroll horizontal mesmo em desktop médio.

**Estado actual:** Já tem `overflow-x-auto`, mas **não** tem `hidden md:block` / `block md:hidden`.

---

### `financeiro/page.tsx` — Lista de honorários com `<table>` nativo

**Estrutura da tabela** (linhas 297–365):
```tsx
<div className="overflow-x-auto">
  <table className="w-full text-sm">
    <thead className="text-left text-neutral-500 dark:text-neutral-400">
      <tr className="border-b border-neutral-200 dark:border-neutral-800">
        <th className="py-2 pr-4 font-medium">Honorário</th>
        <th className="py-2 pr-4 font-medium">Processo</th>
        <th className="py-2 pr-4 font-medium">Cliente</th>
        <th className="py-2 pr-4 font-medium">Total</th>
        <th className="py-2 pr-4 font-medium">Data do acordo</th>
        <th className="py-2 pr-4 font-medium">Estado</th>
      </tr>
    </thead>
    <tbody>
      {filteredList.map((h) => (
        <tr key={h.id} className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
          ...
        </tr>
      ))}
    </tbody>
  </table>
</div>
```

**Badge de estado inline** — não usa o componente `<Badge>`, usa `<span>` com classe inline calculada (linhas 89–95):
```tsx
const statusBadgeClass: Record<HonorarioStatus, string> = {
  Pendente:
    "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400",
  "Parcialmente Pago":
    "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  Pago: "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
};
```

**Nota:** ao adicionar mobile cards, preferir converter para `<Badge>` com as variantes `amber`, `blue`, `green` já definidas em `badge.tsx`.

**Container da tabela** — dentro de `<Card><CardHeader><CardTitle>Honorários</CardTitle></CardHeader><CardContent>` (linhas 271–366). O wrapper da tabela é `<div className="overflow-x-auto">`.

**Estado actual:** Já tem `overflow-x-auto`. Não tem padrão mobile.

---

### `agenda/page.tsx` — Calendário com secção lateral de eventos

**Não tem tabela** — a lista de eventos está numa grid de calendário e numa lista de "Próximos Eventos". O requisito TAB-01 para esta página é transformar essa lista lateral em cards mobile.

**Padrão de card de evento já existente** (linhas 409–432) — este é o padrão de referência para cards mobile em toda a app:
```tsx
<div className={cn(
  "rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] p-4 shadow-sm hover:shadow-md transition-all",
  cat.borderClassName  // ex: "border-l-4 border-l-red-500"
)}>
  <div className="flex items-start justify-between gap-3">
    <div className="text-[10px] font-bold tracking-wider uppercase" style={{ color: cat.titleColor }}>
      {cat.label}
    </div>
    <div className="text-xs font-bold text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-sm">
      {hora}
    </div>
  </div>
  <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white">{e.titulo}</div>
  <div className="mt-1 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
    {processoLabel}
  </div>
</div>
```

**Badge de categoria na agenda** — usa `<Badge variant="secondary">` (linha 402):
```tsx
<Badge variant="secondary" className="rounded-none font-bold">BREVEMENTE</Badge>
```

**Estado actual:** Sem tabela, sem `hidden md:block`. O calendário principal usa `grid-cols-7` fixo — não é candidato a scroll horizontal (é a interface esperada).

---

### `processos/[id]/page.tsx` — Página de detalhe com tabs

**Tab "partes" — tabela** (linhas 1253–1275):
```tsx
<div className="overflow-x-auto">
  <table className="w-full text-sm">
    <thead className="text-left text-neutral-500 dark:text-neutral-400">
      <tr className="border-b border-neutral-200 dark:border-neutral-800">
        <th className="py-2 pr-4 font-medium">Tipo</th>
        <th className="py-2 pr-4 font-medium">Nome</th>
        <th className="py-2 pr-4 font-medium">NIF</th>
      </tr>
    </thead>
    <tbody>
      {partes.data.map((p) => (
        <tr key={p.id} className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
          <td className="py-2 pr-4">{p.tipo ?? "—"}</td>
          <td className="py-2 pr-4 font-medium">{p.nome}</td>
          <td className="py-2 pr-4">{p.nif ?? "—"}</td>
        </tr>
      ))}
    </tbody>
  </table>
</div>
```

**Tab "fases" — tabela** (linhas 1331–1379):
```tsx
<div className="overflow-x-auto">
  <table className="w-full text-sm">
    <thead className="text-left text-neutral-500 dark:text-neutral-400">
      <tr className="border-b border-neutral-200 dark:border-neutral-800">
        <th className="py-2 pr-4 font-medium">Fase</th>
        <th className="py-2 pr-4 font-medium">Status</th>
        <th className="py-2 pr-4 font-medium">Ações</th>
      </tr>
    </thead>
    <tbody>
      {fases.data.map((f) => (
        <tr key={f.id} className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
          <td className="py-2 pr-4 font-medium">{f.fase?.nome ?? f.fase_id}</td>
          <td className="py-2 pr-4">
            <select className={selectClassName} value={...} onChange={...}>...</select>
          </td>
          <td className="py-2 pr-4">
            <Button type="button" size="sm" variant="outline" onClick={() => onUpdateFaseStatus(f.id)} disabled={...}>
              Guardar
            </Button>
          </td>
        </tr>
      ))}
    </tbody>
  </table>
</div>
```

**A tabela de Movimentações não está visível via tab separado** — está embutida dentro da Timeline (tab "timeline"). Pesquisa por `movimentacoes.data.map` não encontrou um render direto em tabela; as movimentações são renderizadas como itens de timeline, não numa tabela separada.

**Container das tabs** — a zona de conteúdo das tabs começa em `<div className="space-y-4">` e cada tab renderiza diretamente um ou mais `<Card>` sem wrapper adicional.

**Tabs de navegação** (linhas 910–941) — botões `<Button variant={tab === "..." ? "secondary" : "outline"}>`. Ao adicionar scroll horizontal, o wrapper `<div className="overflow-x-auto">` já existe em Partes e Fases.

---

## Padrões Partilhados (cross-cutting)

### Badge — componente `@/components/ui/badge`

**Fonte:** `web/src/components/ui/badge.tsx` (linhas 6–29)

Variantes disponíveis (usar directamente em vez de `<span>` com classe inline):

```tsx
// Variantes de cor:
"default"    // bg-neutral-900
"secondary"  // bg-neutral-100
"outline"    // border apenas
"blue"       // bg-blue-100 text-blue-700
"green"      // bg-emerald-100 text-emerald-700
"amber"      // bg-amber-100 text-amber-700
"red"        // bg-red-100 text-red-700
"purple"     // bg-purple-100 text-purple-700
"gray"       // bg-neutral-100 text-neutral-700
```

Uso padrão nos cards mobile:
```tsx
<Badge variant="blue" className="rounded-none font-bold tracking-wide">{tipo}</Badge>
```

Uso alternativo inline em Financeiro (a migrar para Badge):
```tsx
// ANTES (financeiro/page.tsx linha 355):
<span className={statusBadgeClass[status]}>{status}</span>

// DEPOIS (manter consistência com badge.tsx):
<Badge variant={status === "Pago" ? "green" : status === "Parcialmente Pago" ? "blue" : "amber"} className="rounded-none font-bold">
  {status}
</Badge>
```

### Button — componente `@/components/ui/button`

**Fonte:** `web/src/components/ui/button.tsx` (linhas 7–31)

Tamanhos: `default` (h-9), `sm` (h-8), `lg` (h-10), `icon` (h-9 w-9).
Variantes: `default`, `secondary`, `outline`, `ghost`, `link`.

Padrão para ações ícone (usado em clientes):
```tsx
<Button size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
  <Eye className="h-4 w-4" />
</Button>
```

Padrão para ação única por linha (usado em documentos/fases):
```tsx
<Button type="button" variant="outline" size="sm" onClick={onAction} disabled={isPending}>
  Texto
</Button>
```

### Card — componente `@/components/ui/card`

**Fonte:** usado em todos os ficheiros analisados. Import padrão:
```tsx
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
```

Padrão para card de lista (estilo Clientes — mais elaborado):
```tsx
<Card className="border-slate-200 dark:border-slate-800">
  <CardContent className="p-0">
    {/* tabela ou lista mobile */}
  </CardContent>
</Card>
```

Padrão para card de lista (estilo Documentos/Financeiro — mais simples):
```tsx
<Card>
  <CardHeader>
    <CardTitle>Título</CardTitle>
  </CardHeader>
  <CardContent>
    {/* tabela ou lista mobile */}
  </CardContent>
</Card>
```

Padrão para card de item mobile (baseado em agenda/page.tsx linhas 409–432):
```tsx
<div className="rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] p-4 shadow-sm">
  <div className="flex items-start justify-between gap-3">
    <div className="font-bold text-slate-900 dark:text-white text-sm">{titulo}</div>
    <Badge variant="..." className="rounded-none font-bold">{estado}</Badge>
  </div>
  <div className="mt-2 space-y-1">
    <div className="text-[11px] text-slate-500 dark:text-slate-400">Campo: {valor}</div>
  </div>
  <div className="mt-3 flex items-center gap-2">
    <Button asChild size="sm" variant="ghost" className="h-8 w-8 p-0">
      <Link href={href}><Eye className="h-4 w-4" /></Link>
    </Button>
  </div>
</div>
```

### Padrão de visibilidade responsiva (a criar — não existe ainda)

Nenhuma das páginas tem `hidden md:block` ou `block md:hidden`. O padrão a introduzir:

```tsx
{/* Tabela — visível apenas em md+ */}
<div className="hidden md:block">
  <div className="overflow-x-auto">
    <table ...>...</table>
  </div>
</div>

{/* Cards mobile — visível apenas abaixo de md */}
<div className="md:hidden space-y-3">
  {data.map((item) => (
    <MobileCard key={item.id} item={item} />
  ))}
</div>
```

Para tabelas complexas (Partes, Fases em Processos), onde o scroll horizontal é a solução TAB-02:
```tsx
{/* Sem divisão hidden/block — scroll horizontal sempre disponível */}
<div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
  <table className="w-full min-w-[480px] text-sm">
    ...
  </table>
</div>
```

---

## Resumo por requisito

### TAB-01 — Mobile cards para listas simples

| Página | Tabela actual | Colunas | Wrapper | Acção |
|---|---|---|---|---|
| `clientes/page.tsx` | `<Table>` shadcn | Nome, Tipo, NIF, Contacto, Ações | `<div className="overflow-hidden">` dentro de `<CardContent className="p-0">` | Adicionar `<div className="md:hidden">` com cards + `<div className="hidden md:block">` na tabela |
| `documentos/page.tsx` | `<table>` nativo | Nome, Tipo, Processo, Cliente, Confid., Ver., Tamanho, Criado, Ações | `<div className="overflow-x-auto">` dentro de `<CardContent>` | Adicionar divisão hidden/block + mobile cards |
| `financeiro/page.tsx` | `<table>` nativo | Honorário, Processo, Cliente, Total, Data do acordo, Estado | `<div className="overflow-x-auto">` dentro de `<CardContent>` | Adicionar divisão hidden/block + mobile cards |
| `agenda/page.tsx` | sem tabela | — | secção lateral de eventos | Lista de eventos já tem card-like; refinar para mobile |

### TAB-02 — Scroll horizontal para tabelas complexas

| Página | Secção | Colunas | Wrapper actual | Acção |
|---|---|---|---|---|
| `processos/[id]/page.tsx` | tab "partes" | Tipo, Nome, NIF | `<div className="overflow-x-auto">` | Adicionar `min-w-[400px]` à `<table>` |
| `processos/[id]/page.tsx` | tab "fases" | Fase, Status, Ações | `<div className="overflow-x-auto">` | Adicionar `min-w-[480px]` + `-mx-4 px-4 sm:mx-0 sm:px-0` no wrapper |

---

## Ficheiros sem análogo

Nenhum — todos os ficheiros têm análogos directos ou são os próprios ficheiros de referência.

---

## Metadados

**Scope de busca:** `web/src/app/(dashboard)`, `web/src/components/ui`
**Ficheiros lidos:** 7
**Data de mapeamento:** 2026-06-21
