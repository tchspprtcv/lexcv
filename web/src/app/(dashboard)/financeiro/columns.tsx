"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";

import { Badge } from "@/components/ui/badge";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { calcHonorarioStatus, formatDate, formatMoneyCVE } from "@/lib/financeiro";
import type { Honorario } from "@/types/financeiro";
import type { Processo } from "@/types/processos";

/**
 * Column definitions for the Financeiro desktop DataTable.
 *
 * This is Financeiro's first-ever adoption of the Table primitive AND the
 * DataTable pattern in one pass -- its previous desktop branch was a bare
 * HTML table with no Table/TableRow/TableCell usage at all. The Estado cell
 * replaces the hand-rolled `statusBadgeClass` span with the real Badge
 * component, reusing the exact green/blue/amber mapping already implied by
 * this screen's own mobile card branch (not the illustrative UI-SPEC copy).
 *
 * `processoById`/`clienteNomeById` are threaded in as factory arguments
 * (built in financeiro/page.tsx from sibling useProcessos/useClientes hooks)
 * since column defs are plain objects and cannot call hooks directly.
 */
export function columns(
  processoById: Map<string, Processo>,
  clienteNomeById: Map<string, string>,
): ColumnDef<Honorario>[] {
  return [
    {
      accessorKey: "id",
      enableHiding: false,
      meta: { label: "Honorário" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Honorário" />,
      cell: ({ row }) => (
        <Link
          href={`/financeiro/${encodeURIComponent(String(row.original.id))}`}
          className="font-medium text-neutral-900 hover:underline dark:text-neutral-50"
        >
          #{row.original.id}
        </Link>
      ),
    },
    {
      id: "processo",
      accessorFn: (h) => {
        const processo = processoById.get(h.processoId);
        return processo ? (processo.numero ?? processo.titulo ?? processo.id) : h.processoId;
      },
      meta: { label: "Processo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Processo" />,
      cell: ({ row }) => {
        const processo = processoById.get(row.original.processoId);
        return processo ? (
          <Link href={`/processos/${encodeURIComponent(processo.id)}`} className="hover:underline">
            {processo.numero ?? processo.titulo ?? processo.id}
          </Link>
        ) : (
          <>{row.original.processoId}</>
        );
      },
    },
    {
      id: "cliente",
      accessorFn: (h) => {
        const clienteId = processoById.get(h.processoId)?.cliente_id;
        return clienteId ? (clienteNomeById.get(clienteId) ?? clienteId) : "";
      },
      meta: { label: "Cliente" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Cliente" />,
      cell: ({ row }) => {
        const clienteId = processoById.get(row.original.processoId)?.cliente_id;
        return clienteId ? (
          <Link href={`/clientes/${encodeURIComponent(clienteId)}`} className="hover:underline">
            {clienteNomeById.get(clienteId) ?? clienteId}
          </Link>
        ) : (
          <>—</>
        );
      },
    },
    {
      accessorKey: "valorTotal",
      meta: { label: "Total" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Total" />,
      cell: ({ row }) => <>{formatMoneyCVE(row.original.valorTotal)}</>,
    },
    {
      accessorKey: "dataAcordo",
      meta: { label: "Data do Acordo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Data do Acordo" />,
      cell: ({ row }) => <>{formatDate(row.original.dataAcordo)}</>,
    },
    {
      id: "estado",
      accessorFn: (h) => calcHonorarioStatus(h.totalPago, h.valorTotal),
      meta: { label: "Estado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
      cell: ({ row }) => {
        const status = calcHonorarioStatus(row.original.totalPago, row.original.valorTotal);
        return (
          <Badge
            variant={status === "Pago" ? "green" : status === "Parcialmente Pago" ? "blue" : "amber"}
            className="rounded-none font-bold"
          >
            {status}
          </Badge>
        );
      },
    },
  ];
}
