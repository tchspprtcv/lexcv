"use client";

import Link from "next/link";
import * as React from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Download, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { useDeleteDocumento } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import type { Documento } from "@/types/documentos";
import type { Processo } from "@/types/processos";

/**
 * Confidencialidade has no pre-existing Badge mapping anywhere in the
 * codebase (verified against schemas/documentos.ts's 4-value enum and the
 * upload form options — it was plain text in both the mobile and desktop
 * branches before this migration). This exhaustive mapping over all 4 enum
 * values is invented for this column, ordered by increasing sensitivity:
 * PUBLICO (least sensitive, neutral) -> INTERNO (internal-only,
 * informational) -> CONFIDENCIAL (caution) -> RESTRITO (most sensitive,
 * highest restriction). Any unrecognized/legacy value falls back to the
 * same neutral treatment as PUBLICO.
 */
function confidencialidadeVariant(
  confidencialidade: string | undefined,
): "gray" | "blue" | "amber" | "red" {
  switch (confidencialidade ?? "PUBLICO") {
    case "PUBLICO":
      return "gray";
    case "INTERNO":
      return "blue";
    case "CONFIDENCIAL":
      return "amber";
    case "RESTRITO":
      return "red";
    default:
      return "gray";
  }
}

/**
 * Ações cell as its own component (not an inline arrow doing the work
 * itself) so the per-row useDeleteDocumento mutation hook + local error
 * state can be called safely — TanStack column cell definitions are plain
 * objects/functions and cannot call hooks directly, but a component they
 * render into via flexRender can. Preserves the canEditDocumentos RBAC gate
 * and the window.confirm() guard 1:1 from the pre-migration DocumentoRow.
 */
function DocumentoAcoesCell({
  documento,
  canEditDocumentos,
}: {
  documento: Documento;
  canEditDocumentos: boolean;
}) {
  const del = useDeleteDocumento(documento.id);
  const [error, setError] = React.useState<string | null>(null);

  const onDelete = async () => {
    setError(null);
    const ok = window.confirm("Apagar este documento?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Documento apagado com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
      setError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-1">
      <div className="inline-flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button asChild variant="ghost" size="sm" aria-label="Download">
              <a
                href={`/api/v1/documentos/${encodeURIComponent(documento.id)}/download`}
                target="_blank"
                rel="noreferrer"
              >
                <Download className="h-4 w-4" />
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Download</TooltipContent>
        </Tooltip>
        {canEditDocumentos ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="Apagar"
                onClick={onDelete}
                disabled={del.isPending}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>Apagar</TooltipContent>
          </Tooltip>
        ) : null}
      </div>
      {error ? <div className="text-xs text-red-600">{error}</div> : null}
    </div>
  );
}

/**
 * ColumnDef factory for the Documentos DataTable — a factory (not a bare
 * array) because the Ações column needs canEditDocumentos threaded through
 * from the page, and column defs can't call hooks/read props on their own.
 *
 * `processoById`/`clienteNomeById` are threaded in the same way (built in
 * documentos/page.tsx from sibling useProcessos/useClientes hooks) so the
 * Processo/Cliente cells resolve to human-readable labels instead of raw
 * foreign-key ids — mirrors the pattern financeiro/columns.tsx established
 * in this same phase.
 */
export function columns(
  canEditDocumentos: boolean,
  processoById: Map<string, Processo>,
  clienteNomeById: Map<string, string>,
): ColumnDef<Documento>[] {
  return [
    {
      accessorKey: "nome",
      enableHiding: false,
      meta: { label: "Nome" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Nome" />,
      cell: ({ row }) => (
        <Link
          href={`/documentos/${encodeURIComponent(row.original.id)}`}
          className="font-medium text-neutral-900 hover:underline dark:text-neutral-50"
        >
          {row.original.nome}
        </Link>
      ),
    },
    {
      accessorKey: "tipo",
      meta: { label: "Tipo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Tipo" />,
      cell: ({ row }) =>
        row.original.tipo ? (
          <Badge variant="blue" className="font-bold">
            {row.original.tipo}
          </Badge>
        ) : (
          "—"
        ),
    },
    {
      id: "processo",
      accessorFn: (d) => {
        if (!d.processo_id) return "";
        const processo = processoById.get(d.processo_id);
        return processo ? (processo.numero ?? processo.titulo ?? processo.id) : d.processo_id;
      },
      meta: { label: "Processo" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Processo" />,
      cell: ({ row }) => {
        if (!row.original.processo_id) return "—";
        const processo = processoById.get(row.original.processo_id);
        return processo ? (
          <Link href={`/processos/${encodeURIComponent(processo.id)}`} className="hover:underline">
            {processo.numero ?? processo.titulo ?? processo.id}
          </Link>
        ) : (
          <>{row.original.processo_id}</>
        );
      },
    },
    {
      id: "cliente",
      accessorFn: (d) => (d.cliente_id ? (clienteNomeById.get(d.cliente_id) ?? d.cliente_id) : ""),
      meta: { label: "Cliente" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Cliente" />,
      cell: ({ row }) => {
        const clienteId = row.original.cliente_id;
        if (!clienteId) return "—";
        return (
          <Link href={`/clientes/${encodeURIComponent(clienteId)}`} className="hover:underline">
            {clienteNomeById.get(clienteId) ?? clienteId}
          </Link>
        );
      },
    },
    {
      accessorKey: "confidencialidade",
      meta: { label: "Confidencialidade" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Confid." />,
      cell: ({ row }) => {
        const value = row.original.confidencialidade ?? "PUBLICO";
        return (
          <Badge
            variant={confidencialidadeVariant(value)}
            className="font-bold"
          >
            {value}
          </Badge>
        );
      },
    },
    {
      accessorKey: "versao",
      enableSorting: false,
      meta: { label: "Versão" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Ver." />,
      cell: ({ row }) => `v${row.original.versao ?? 1}`,
    },
    {
      accessorKey: "size",
      meta: { label: "Tamanho" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Tamanho" />,
      cell: ({ row }) => `${row.original.size.toLocaleString("pt-CV")} bytes`,
    },
    {
      accessorKey: "created_at",
      meta: { label: "Criado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Criado" />,
      cell: ({ row }) => new Date(row.original.created_at).toLocaleString("pt-CV"),
    },
    {
      id: "acoes",
      enableSorting: false,
      enableHiding: false,
      meta: { label: "Ações" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Ações" />,
      cell: ({ row }) => (
        <DocumentoAcoesCell documento={row.original} canEditDocumentos={canEditDocumentos} />
      ),
    },
  ];
}
