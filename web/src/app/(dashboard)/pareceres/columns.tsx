"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";
import { MoreVertical } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { formatDate, statusVariant } from "@/lib/pareceres";
import type { ParecerSolicitacao } from "@/types/pareceres";

/**
 * Column definitions for the Pareceres desktop DataTable.
 *
 * `clienteNomeById` is threaded in as a factory argument (built in
 * pareceres/page.tsx from the sibling useClientes hook) since column defs
 * are plain objects and cannot call hooks directly.
 */
export function columns(clienteNomeById: Map<string, string>): ColumnDef<ParecerSolicitacao>[] {
  return [
    {
      accessorKey: "status",
      enableHiding: false,
      meta: { label: "Estado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
      cell: ({ row }) => (
        <Badge variant={statusVariant(row.original.status)} className="rounded-none font-bold tracking-wide">
          {row.original.status}
        </Badge>
      ),
    },
    {
      id: "cliente",
      accessorFn: (s) => clienteNomeById.get(s.clienteId) ?? s.clienteId,
      meta: { label: "Cliente" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Cliente" />,
      cell: ({ row }) => (
        <Link
          href={`/pareceres/${encodeURIComponent(row.original.id)}`}
          className="font-bold text-slate-700 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
        >
          {clienteNomeById.get(row.original.clienteId) ?? row.original.clienteId}
        </Link>
      ),
    },
    {
      accessorKey: "prioridade",
      meta: { label: "Prioridade" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Prioridade" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">
          {row.original.prioridade ?? "—"}
        </span>
      ),
    },
    {
      accessorKey: "prazo",
      meta: { label: "Prazo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Prazo" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">
          {formatDate(row.original.prazo)}
        </span>
      ),
    },
    {
      accessorKey: "createdAt",
      meta: { label: "Criado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Criado" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">
          {formatDate(row.original.createdAt)}
        </span>
      ),
    },
    {
      id: "acoes",
      enableSorting: false,
      enableHiding: false,
      meta: { label: "Ações" },
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title="Ações" className="block text-right" />
      ),
      cell: ({ row }) => (
        <div className="text-right">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                asChild
                size="sm"
                variant="ghost"
                aria-label="Ver detalhes"
                className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                <Link href={`/pareceres/${encodeURIComponent(row.original.id)}`}>
                  <MoreVertical className="h-4 w-4" />
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>Ver detalhes</TooltipContent>
          </Tooltip>
        </div>
      ),
    },
  ];
}
