"use client";

import Link from "next/link";
import type { ColumnDef } from "@tanstack/react-table";
import { AlertCircle, MoreVertical } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { prazosRiscoToLabel, prazosRiscoToVariant } from "@/lib/prazos";
import type { Processo } from "@/types/processos";

/**
 * Column defs for the Processos desktop DataTable. Takes `clienteNomeById`
 * as a factory argument — column defs are plain objects and cannot call
 * `useClientes` themselves, so the cross-hook lookup Map built in page.tsx
 * is threaded in here.
 */
export function columns(clienteNomeById: Map<string, string>): ColumnDef<Processo>[] {
  return [
    {
      id: "numero",
      accessorFn: (p) => p.numero || p.titulo || "",
      enableHiding: false,
      meta: { label: "Número do Processo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Número do Processo" />,
      cell: ({ row }) => {
        const p = row.original;
        return (
          <div>
            <Link
              href={`/processos/${encodeURIComponent(p.id)}`}
              className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
            >
              {p.numero || p.titulo || "Sem número"}
            </Link>
            <div className="text-[11px] font-medium tracking-wider uppercase text-slate-500 dark:text-slate-400 mt-0.5">
              Entrada: {new Date(p.created_at).toLocaleDateString("pt-CV")}
            </div>
            <div className="text-[11px] font-medium text-slate-400 dark:text-slate-500 mt-0.5">
              Resp.: {p.responsavel_nome ?? "—"}
            </div>
          </div>
        );
      },
    },
    {
      id: "cliente",
      accessorFn: (p) => clienteNomeById.get(p.cliente_id) ?? "",
      meta: { label: "Cliente" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Cliente" />,
      cell: ({ row }) => (
        <span className="font-bold text-slate-700 dark:text-slate-300">
          {clienteNomeById.get(row.original.cliente_id) ?? "—"}
        </span>
      ),
    },
    {
      id: "tribunal",
      accessorFn: (p) => p.tribunal ?? "",
      meta: { label: "Tribunal" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Tribunal" />,
      cell: ({ row }) => (
        <span className="text-slate-500 dark:text-slate-400 font-medium">{row.original.tribunal ?? "—"}</span>
      ),
    },
    {
      id: "area_juridica",
      accessorFn: (p) => p.area_juridica ?? "",
      meta: { label: "Área Jurídica" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Área Jurídica" />,
      cell: ({ row }) => (
        <Badge variant="blue" className="rounded-none font-bold tracking-wide">
          {row.original.area_juridica ?? "—"}
        </Badge>
      ),
    },
    {
      id: "estado",
      accessorFn: (p) => (p.estado ?? "").toUpperCase(),
      meta: { label: "Estado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
      cell: ({ row }) => {
        const p = row.original;
        const estado = (p.estado ?? "").toUpperCase();
        const estadoVariant =
          estado === "ATIVO"
            ? "green"
            : estado === "SUSPENSO"
              ? "amber"
              : estado === "TRIAGEM"
                ? "purple"
                : estado === "CONCLUIDO" || estado === "ENCERRADO"
                  ? "gray"
                  : "secondary";
        const estadoLabel = estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");

        return (
          <div>
            <Badge
              variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"}
              className="rounded-none font-bold tracking-wide"
            >
              {estadoLabel}
            </Badge>
            {/* Prazo risco signal — only render for proximo or vencido */}
            {p.risco_mais_critico && p.risco_mais_critico !== "ok" ? (
              <div className="mt-1 flex items-center gap-1">
                <Badge
                  variant={prazosRiscoToVariant(p.risco_mais_critico)}
                  className="rounded-none font-bold tracking-wide text-[11px]"
                >
                  {prazosRiscoToLabel(p.risco_mais_critico)}
                </Badge>
                {p.tem_prazo_escalonado ? (
                  <AlertCircle className="h-3 w-3 text-amber-500 dark:text-amber-400 inline-block ml-1" />
                ) : null}
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      id: "acoes",
      enableSorting: false,
      enableHiding: false,
      meta: { label: "Ações" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Ações" />,
      cell: ({ row }) => (
        <div className="flex justify-end">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                asChild
                size="sm"
                variant="ghost"
                aria-label="Ver detalhes"
                className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
              >
                <Link href={`/processos/${encodeURIComponent(row.original.id)}`}>
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
