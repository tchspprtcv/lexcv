"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";

import { Badge } from "@/components/ui/badge";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import type { DocumentoFiscalResumo } from "@/types/faturacao";

// Colunas da lista "Documentos fiscais" (134-UI-SPEC Surface 3). A ordem vem do servidor (data de
// emissão desc), por isso nenhuma coluna é ordenável no cliente: ordenar só a página atual daria
// uma ordem enganadora. O link do número é a única navegação (acessível por teclado).

function formatarData(valor: string) {
  const d = new Date(valor.includes("T") ? valor : `${valor}T00:00:00`);
  if (Number.isNaN(d.getTime())) return valor;
  return d.toLocaleDateString("pt-CV");
}

function formatarCVE(valor: number) {
  return valor.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

export const columns: ColumnDef<DocumentoFiscalResumo>[] = [
  {
    id: "numero",
    accessorKey: "numeroFormatado",
    enableSorting: false,
    enableHiding: false,
    meta: { label: "Número" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Número" />,
    cell: ({ row }) => (
      <Link
        href={`/financeiro/documentos-fiscais/${encodeURIComponent(row.original.id)}`}
        className="font-mono text-sm text-blue-600 hover:underline dark:text-blue-400"
      >
        {row.original.numeroFormatado}
      </Link>
    ),
  },
  {
    id: "data",
    accessorKey: "dataEmissao",
    enableSorting: false,
    meta: { label: "Data" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Data" />,
    cell: ({ row }) => formatarData(row.original.dataEmissao),
  },
  {
    id: "cliente",
    accessorKey: "adquirenteNome",
    enableSorting: false,
    meta: { label: "Cliente" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Cliente" />,
    cell: ({ row }) => row.original.adquirenteNome,
  },
  {
    id: "tipo",
    accessorKey: "tipoRotulo",
    enableSorting: false,
    meta: { label: "Tipo" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Tipo" />,
    cell: ({ row }) => <Badge variant="secondary">{row.original.tipoRotulo}</Badge>,
  },
  {
    id: "total",
    accessorKey: "totalDocumento",
    enableSorting: false,
    meta: { label: "Total" },
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title="Total" className="block text-right" />
    ),
    cell: ({ row }) => (
      <div className="text-right tabular-nums">{formatarCVE(row.original.totalDocumento)}</div>
    ),
  },
  {
    id: "estado",
    accessorKey: "estadoComunicacao",
    enableSorting: false,
    meta: { label: "Estado" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
    cell: ({ row }) =>
      row.original.estadoComunicacao === "PENDENTE" ? (
        <Badge variant="outline">Pendente</Badge>
      ) : (
        "—"
      ),
  },
  {
    id: "ambiente",
    accessorKey: "ambiente",
    enableSorting: false,
    meta: { label: "Ambiente" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Ambiente" />,
    cell: ({ row }) =>
      row.original.ambiente === "SIMULADO" ? <Badge variant="outline">Simulado</Badge> : row.original.ambiente,
  },
];
