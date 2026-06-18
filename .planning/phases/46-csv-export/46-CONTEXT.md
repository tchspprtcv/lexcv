# Phase 46: CSV Export - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona um botão "Exportar CSV" na página financeiro que gera e descarrega um ficheiro `.csv` com os honorários atualmente visíveis (respeitando os filtros ativos da Phase 45). Implementação 100% frontend — sem endpoint backend, sem biblioteca externa.

**Fora do âmbito desta fase:** exportação PDF, exportação Excel (.xlsx), exportação com totais/somas por categoria.

</domain>

<decisions>
## Implementation Decisions

### Geração CSV
- Geração client-side pura: `Blob` + `URL.createObjectURL` + `<a download>` click trick — zero dependências externas
- Campos do CSV (por FIN-17): `id`, `processo`, `cliente`, `valorTotal`, `totalPago`, `estado`, `dataAcordo`
- `processo` = `processo.numero ?? processo.titulo ?? processoId` (usar `processoById` Map já existente)
- `cliente` = `clienteNomeById.get(clienteId) ?? ""` (usar Map já existente)
- `estado` = `calcHonorarioStatus(h.totalPago, h.valorTotal)` (função já existente)
- Encoding: UTF-8 com BOM (`﻿`) para compatibilidade com Excel em Windows
- Separador: vírgula (`,`); valores com vírgulas ou aspas devem ser escapados com `""`
- Nome do ficheiro: `honorarios-YYYY-MM-DD.csv` com data atual

### Integração com Filtros (Phase 45)
- Exporta `filteredList` (não `honorarios.data`) — respeita os filtros activos
- O botão "Exportar CSV" só aparece quando há dados (`filteredList.length > 0`)
- Botão posicionado no header da página (ao lado de "Novo honorário")

### Claude's Discretion
- Função helper `exportHonorariosCsv(rows, processoById, clienteNomeById)` extraída para dentro do componente ou como função pura no topo do ficheiro
- Escaping de campos CSV (se simplificar: substituir `"` por `""` e envolver em `"` se contiver vírgula/newline/aspas)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `filteredList` — já calculado em `page.tsx` após Phase 45; exportar exactamente esta lista
- `processoById: Map<string, Processo>` — já calculado em `FinanceiroContent`
- `clienteNomeById: Map<string, string>` — já calculado em `FinanceiroContent`
- `calcHonorarioStatus` — já existe em `page.tsx`
- `formatDate` — já existe; pode reutilizar ou implementar inline para o CSV

### Integration Points
- `web/src/app/(dashboard)/financeiro/page.tsx` — único ficheiro a modificar
- Não requer novos hooks, tipos, schemas ou componentes

</code_context>

<specifics>
## Specific Ideas

```typescript
function exportCsv(rows: Honorario[], processoById: Map<string, Processo>, clienteNomeById: Map<string, string>) {
  const header = ["ID","Processo","Cliente","Valor Total","Total Pago","Estado","Data do Acordo"];
  const escape = (v: string) => v.includes(",") || v.includes('"') || v.includes("\n") ? `"${v.replace(/"/g, '""')}"` : v;
  const lines = rows.map(h => {
    const proc = processoById.get(h.processoId);
    const clienteId = proc?.cliente_id ?? "";
    return [
      String(h.id),
      proc ? (proc.numero ?? proc.titulo ?? h.processoId) : h.processoId,
      clienteNomeById.get(clienteId) ?? "",
      String(h.valorTotal),
      String(h.totalPago),
      calcHonorarioStatus(h.totalPago, h.valorTotal),
      h.dataAcordo ?? "",
    ].map(escape).join(",");
  });
  const csv = "﻿" + [header.join(","), ...lines].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `honorarios-${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
```

</specifics>

<deferred>
## Deferred Ideas

- Exportação PDF de honorários — fora do âmbito v2.0
- Exportação Excel (.xlsx) — requer biblioteca externa, fora do âmbito
- Somas e subtotais no CSV — fora do âmbito
- Exportação de pagamentos individuais — fora do âmbito

</deferred>
