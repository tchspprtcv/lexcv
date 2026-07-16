"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";
import { MoreVertical } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import type { ParecerSolicitacao, ParecerStatus } from "@/types/pareceres";

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}

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
      header: ({ column }) => <DataTableColumnHeader column={column} title="Prioridade" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">
          {row.original.prioridade ?? "—"}
        </span>
      ),
    },
    {
      accessorKey: "prazo",
      header: ({ column }) => <DataTableColumnHeader column={column} title="Prazo" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">
          {formatDate(row.original.prazo)}
        </span>
      ),
    },
    {
      accessorKey: "createdAt",
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
      header: ({ column }) => <DataTableColumnHeader column={column} title="Ações" />,
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
