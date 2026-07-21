"use client";

import * as React from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Download } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { useDeleteDocumento, useDownloadDocumento } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import type { Documento } from "@/types/documentos";

/**
 * Confidencialidade has no pre-existing Badge mapping outside
 * documentos/columns.tsx (Phase 104) — reused verbatim here (same enum,
 * same ordering by increasing sensitivity) rather than reinventing a
 * mapping for this processo-scoped tab.
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
 * WR-01: the backend serializes the raw Documento entity with its Java
 * field names (tamanho, createdAt), not the shape declared on the shared
 * frontend `Documento` type (size, created_at) — same wire-shape
 * workaround the pre-migration ProcessoDocumentoRow applied, carried
 * verbatim into the Tamanho/Criado column accessors below.
 */
function wireSizeAndDate(documento: Documento): { tamanho: number; criadoEm: string | undefined } {
  const wireDocumento = documento as unknown as { tamanho?: number; createdAt?: string };
  return { tamanho: wireDocumento.tamanho ?? 0, criadoEm: wireDocumento.createdAt };
}

/**
 * Ações cell as its own component (not an inline arrow) so the per-row
 * useDeleteDocumento/useDownloadDocumento mutations can be called safely —
 * TanStack column cell definitions are plain objects/functions and cannot
 * call hooks directly, but a component they render into via flexRender can.
 * Ports ProcessoDocumentoRow's existing download/delete/window.confirm
 * logic 1:1 (including the canEditDocumentos RBAC gate on the Apagar
 * action).
 */
function DocumentoAcoesCell({
  documento,
  canEditDocumentos,
}: {
  documento: Documento;
  canEditDocumentos: boolean;
}) {
  const del = useDeleteDocumento(documento.id);
  const download = useDownloadDocumento(documento.id);

  const onDelete = async () => {
    const ok = window.confirm("Apagar este documento?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Documento apagado com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao apagar documento");
    }
  };

  const onDownload = async () => {
    try {
      const res = await download.mutateAsync();
      window.open(res.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao gerar link de download.");
    }
  };

  return (
    <div className="inline-flex items-center gap-1">
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label="Download"
            onClick={onDownload}
            disabled={download.isPending}
          >
            <Download className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Download</TooltipContent>
      </Tooltip>
      {canEditDocumentos ? (
        <Button type="button" variant="outline" onClick={onDelete} disabled={del.isPending}>
          {del.isPending ? "A apagar..." : "Apagar"}
        </Button>
      ) : null}
    </div>
  );
}

/**
 * ColumnDef factory for the processo-scoped Documentos DataTable — modeled
 * directly on documentos/columns.tsx's columns() factory (Phase 104), minus
 * the "Processo" column (redundant: every row here is already scoped to
 * this one processo via useDocumentos({ processo_id: processoId })) and
 * minus the "Cliente" column (documents uploaded from this tab only ever
 * carry processo_id, never cliente_id — a Cliente column would always
 * render "—", so it is dropped rather than kept as dead weight).
 */
export function columns(canEditDocumentos: boolean): ColumnDef<Documento>[] {
  return [
    {
      accessorKey: "nome",
      enableHiding: false,
      meta: { label: "Nome" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Nome" />,
      cell: ({ row }) => <span className="font-medium">{row.original.nome}</span>,
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
      accessorKey: "confidencialidade",
      meta: { label: "Confidencialidade" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Confid." />,
      cell: ({ row }) => {
        const value = row.original.confidencialidade ?? "PUBLICO";
        return (
          <Badge variant={confidencialidadeVariant(value)} className="font-bold">
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
      id: "tamanho",
      accessorFn: (d) => wireSizeAndDate(d).tamanho,
      meta: { label: "Tamanho" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Tamanho" />,
      cell: ({ row }) => `${wireSizeAndDate(row.original).tamanho.toLocaleString("pt-CV")} bytes`,
    },
    {
      id: "criado",
      accessorFn: (d) => wireSizeAndDate(d).criadoEm ?? "",
      meta: { label: "Criado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Criado" />,
      cell: ({ row }) => {
        const criadoEm = wireSizeAndDate(row.original).criadoEm;
        return criadoEm ? new Date(criadoEm).toLocaleString("pt-CV") : "—";
      },
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
