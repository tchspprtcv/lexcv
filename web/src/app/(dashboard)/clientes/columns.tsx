"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";
import { Eye, Pencil, Printer, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { useDeleteCliente } from "@/hooks/use-clientes";
import { cn } from "@/lib/utils";
import type { Cliente } from "@/types/clientes";

/**
 * Ações cell needs a per-row `useDeleteCliente` hook call, which a plain
 * TanStack column-def `cell` function cannot do (column defs are plain
 * objects, not components). Kept as a small inline component instead,
 * mirroring the previous `ClienteRow` shape.
 */
function ClienteAcoesCell({
  cliente,
  canEditClientes,
}: {
  cliente: Cliente;
  canEditClientes: boolean;
}) {
  const del = useDeleteCliente(cliente.id);

  const onDelete = () => {
    if (del.isPending) return;
    const ok = window.confirm("Remover este cliente?");
    if (!ok) return;
    del.mutate();
  };

  return (
    <div className="inline-flex items-center gap-1">
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            asChild
            size="sm"
            variant="ghost"
            aria-label="Ver detalhes"
            className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
          >
            <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
              <Eye className="h-4 w-4" />
            </Link>
          </Button>
        </TooltipTrigger>
        <TooltipContent>Ver detalhes</TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            asChild
            size="sm"
            variant="ghost"
            aria-label="Imprimir"
            className="h-9 w-9 p-0 text-slate-500 hover:text-purple-600 dark:hover:text-purple-400 transition-colors"
          >
            <Link
              href={`/clientes/${encodeURIComponent(cliente.id)}/ficha`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Printer className="h-4 w-4" />
            </Link>
          </Button>
        </TooltipTrigger>
        <TooltipContent>Imprimir</TooltipContent>
      </Tooltip>
      {canEditClientes ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              asChild
              size="sm"
              variant="ghost"
              aria-label="Editar"
              className="h-9 w-9 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
            >
              <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
                <Pencil className="h-4 w-4" />
              </Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Editar</TooltipContent>
        </Tooltip>
      ) : null}
      {canEditClientes ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              aria-label="Eliminar"
              className="h-9 w-9 p-0 text-slate-500 hover:text-red-600 dark:hover:text-red-400 transition-colors"
              onClick={onDelete}
              disabled={del.isPending}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Eliminar</TooltipContent>
        </Tooltip>
      ) : null}
    </div>
  );
}

/**
 * Column defs for the Clientes desktop DataTable. Takes `canEditClientes`
 * as a factory argument (RBAC flag resolved in page.tsx via usePermissions,
 * threaded in since column defs can't call hooks themselves).
 */
export function columns(canEditClientes: boolean): ColumnDef<Cliente>[] {
  return [
    {
      id: "nome",
      accessorKey: "nome",
      enableHiding: false,
      meta: { label: "Nome / Razão Social" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Nome / Razão Social" />,
      cell: ({ row }) => {
        const cliente = row.original;
        const initials = cliente.nome
          .split(" ")
          .filter(Boolean)
          .slice(0, 2)
          .map((p) => p[0])
          .join("")
          .toUpperCase();

        return (
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-none bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
              {initials}
            </div>
            <div className="min-w-0">
              <Link
                href={`/clientes/${encodeURIComponent(cliente.id)}`}
                className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                {cliente.nome}
              </Link>
              {(cliente.numero_cliente || cliente.avencado) && (
                <div className="flex items-center gap-1 mt-1">
                  {cliente.numero_cliente && (
                    <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px]">
                      {cliente.numero_cliente}
                    </Badge>
                  )}
                  {cliente.avencado && (
                    <Badge variant="green" className="rounded-none font-bold text-[10px]">
                      Avençado
                    </Badge>
                  )}
                </div>
              )}
            </div>
          </div>
        );
      },
    },
    {
      id: "tipo",
      accessorFn: (cliente) => (cliente.tipo ?? "").toUpperCase(),
      meta: { label: "Tipo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Tipo" />,
      cell: ({ row }) => {
        const tipo = (row.original.tipo ?? "").toUpperCase();
        const badgeVariant = tipo === "PARTICULAR" ? "blue" : tipo === "EMPRESA" ? "purple" : "gray";
        return (
          <Badge variant={badgeVariant as "blue" | "purple" | "gray"} className="rounded-none font-bold tracking-wide">
            {tipo || "—"}
          </Badge>
        );
      },
    },
    {
      id: "nif",
      accessorFn: (cliente) =>
        (cliente.documento_tipo || cliente.documentoTipo)
          ? (cliente.documento_numero ?? cliente.documentoNumero ?? "")
          : (cliente.nif ?? ""),
      meta: { label: "NIF" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="NIF" />,
      cell: ({ row }) => {
        const cliente = row.original;
        return (
          <div
            className={cn(
              "text-slate-800 dark:text-slate-200 font-medium",
              !cliente.nif && !cliente.documento_numero && !cliente.documentoNumero && "text-slate-400 dark:text-slate-600",
            )}
          >
            {cliente.documento_tipo || cliente.documentoTipo ? (
              <div className="flex flex-col">
                <span>{cliente.documento_numero ?? cliente.documentoNumero}</span>
                <span className="text-[10px] text-slate-500 dark:text-slate-400 font-normal uppercase">
                  {cliente.documento_tipo ?? cliente.documentoTipo}
                </span>
              </div>
            ) : (
              cliente.nif ?? "—"
            )}
          </div>
        );
      },
    },
    {
      id: "contacto",
      enableSorting: false,
      meta: { label: "Contacto" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Contacto" />,
      cell: ({ row }) => {
        const cliente = row.original;
        return (
          <div>
            <div className="text-sm font-medium text-slate-900 dark:text-slate-200">{cliente.telefone ?? "—"}</div>
            <div className="text-[11px] text-slate-500 dark:text-slate-400">{cliente.email ?? ""}</div>
          </div>
        );
      },
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
        <div className="flex justify-end">
          <ClienteAcoesCell cliente={row.original} canEditClientes={canEditClientes} />
        </div>
      ),
    },
  ];
}
