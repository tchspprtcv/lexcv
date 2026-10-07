"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";

import { Badge } from "@/components/ui/badge";
import { ComunicacaoEstadoBadge } from "@/components/shared/comunicacao-estado-badge";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { DescarregarDocumentoBotoes } from "@/components/shared/descarregar-documento-botoes";
import { EntregaEmailBadge } from "@/components/shared/entrega-email-badge";
import type { DocumentoFiscalResumo } from "@/types/faturacao";

// Colunas da lista "Documentos fiscais" (134-UI-SPEC Surface 3). A ordem vem do servidor (data de
// emissão desc), por isso nenhuma coluna é ordenável no cliente: ordenar só a página atual daria
// uma ordem enganadora. O link do número é a única navegação (acessível por teclado).
//
// Phase 135 (135-UI-SPEC Surface 4): numa Nota de Crédito, por baixo do número, "Corrige {número
// da FR}" vindo do backend (documentoOrigemNumero, nunca construído aqui). O total é mostrado como
// devolvido, sem cor (o vermelho fica só para o estorno na lista de pagamentos).
//
// Phase 136 (136-UI-SPEC Surface 1): a coluna "Comunicação" (logo a seguir a "Tipo") mostra o
// badge neutro partilhado para cada estado; "—" quando o documento ainda não tem comunicação.
//
// Phase 137 (137-UI-SPEC Surface 2): a coluna "Email" (após "Comunicação" e antes de "Total") mostra
// o estado de entrega por email (`estadoEntregaEmail`); a última coluna "Ações" inclui os botões
// compactos de descarga PDF e XML.

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
      <div className="space-y-1">
        <Link
          href={`/financeiro/documentos-fiscais/${encodeURIComponent(row.original.id)}`}
          className="font-mono text-sm text-blue-600 hover:underline dark:text-blue-400"
        >
          {row.original.numeroFormatado}
        </Link>
        {row.original.documentoOrigemNumero ? (
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Corrige {row.original.documentoOrigemNumero}
          </p>
        ) : null}
      </div>
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
    id: "comunicacao",
    accessorKey: "estadoComunicacao",
    enableSorting: false,
    meta: { label: "Comunicação" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Comunicação" />,
    cell: ({ row }) =>
      row.original.estadoComunicacao ? <ComunicacaoEstadoBadge estado={row.original.estadoComunicacao} /> : "—",
  },
  {
    id: "email",
    accessorKey: "estadoEntregaEmail",
    enableSorting: false,
    meta: { label: "Email" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Email" />,
    cell: ({ row }) =>
      row.original.estadoEntregaEmail ? (
        <EntregaEmailBadge estado={row.original.estadoEntregaEmail} />
      ) : (
        "—"
      ),
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
    id: "ambiente",
    accessorKey: "ambiente",
    enableSorting: false,
    meta: { label: "Ambiente" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Ambiente" />,
    cell: ({ row }) =>
      row.original.ambiente === "SIMULADO" ? <Badge variant="outline">Simulado</Badge> : row.original.ambiente,
  },
  {
    id: "acoes",
    enableSorting: false,
    enableHiding: false,
    header: () => <span className="sr-only">Ações</span>,
    cell: ({ row }) => (
      <div className="w-0 whitespace-nowrap text-right">
        <DescarregarDocumentoBotoes
          documentoId={row.original.id}
          numeroFormatado={row.original.numeroFormatado}
          compact
        />
      </div>
    ),
  },
];
