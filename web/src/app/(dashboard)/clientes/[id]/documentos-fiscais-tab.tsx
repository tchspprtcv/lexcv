"use client";

import * as React from "react";
import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import type { ColumnDef, PaginationState, Updater } from "@tanstack/react-table";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { ComunicacaoEstadoBadge } from "@/components/shared/comunicacao-estado-badge";
import { DataTable } from "@/components/shared/data-table/data-table";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { DescarregarDocumentoBotoes } from "@/components/shared/descarregar-documento-botoes";
import { EntregaEmailBadge } from "@/components/shared/entrega-email-badge";
import { ModoSimuladoBanner } from "@/components/shared/modo-simulado-banner";
import { useDocumentosFiscais } from "@/hooks/use-faturacao";
import type { DocumentoFiscalResumo } from "@/types/faturacao";

// Phase 137 (ENTR-07; 137-UI-SPEC Surface 3): aba "Documentos fiscais" na ficha do cliente.
// Apenas consulta e descarga de PDF/XML. Sem eliminação, edição, upload, renomeação,
// seleção por checkbox ou menu de linha. Não utiliza use-documentos nem a tabela comum de documentos.

function formatarData(valor: string) {
  const d = new Date(valor.includes("T") ? valor : `${valor}T00:00:00`);
  if (Number.isNaN(d.getTime())) return valor;
  return d.toLocaleDateString("pt-CV");
}

function formatarCVE(valor: number) {
  return valor.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

const colunasCliente: ColumnDef<DocumentoFiscalResumo>[] = [
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
    id: "comunicacao",
    accessorKey: "estadoComunicacao",
    enableSorting: false,
    meta: { label: "Comunicação" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Comunicação" />,
    cell: ({ row }) =>
      row.original.estadoComunicacao ? (
        <ComunicacaoEstadoBadge estado={row.original.estadoComunicacao} />
      ) : (
        "—"
      ),
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

export function ClienteDocumentosFiscaisTab({ clienteId }: { clienteId: string }) {
  const [pagina, setPagina] = React.useState(0);
  const paginacao: PaginationState = React.useMemo(
    () => ({ pageIndex: pagina, pageSize: 10 }),
    [pagina],
  );

  const onPaginationChange = React.useCallback((updater: Updater<PaginationState>) => {
    setPagina((atual) => {
      const proximo = typeof updater === "function" ? updater({ pageIndex: atual, pageSize: 10 }) : updater;
      return proximo.pageIndex;
    });
  }, []);

  const documentos = useDocumentosFiscais(
    {
      clienteId,
      page: pagina,
      size: 10,
    },
    true,
  );

  const linhas = documentos.data?.content ?? [];
  const totalPaginas = documentos.data?.totalPages ?? 0;

  return (
    <div className="space-y-4">
      <ModoSimuladoBanner />

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0">
          <CardTitle className="text-xl font-semibold">Documentos fiscais</CardTitle>
          <Link
            href={`/financeiro/documentos-fiscais?clienteId=${encodeURIComponent(clienteId)}`}
            className="inline-flex items-center gap-1 text-sm text-blue-600 hover:underline dark:text-blue-400"
          >
            Ver todos os documentos fiscais deste cliente
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        </CardHeader>
        <CardContent className="space-y-4">
          {documentos.isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : documentos.isError ? (
            <div className="space-y-3 py-4 text-center">
              <p className="text-sm text-red-600 dark:text-red-400">
                Não foi possível carregar os documentos fiscais deste cliente.
              </p>
              <Button type="button" variant="outline" size="sm" onClick={() => documentos.refetch()}>
                Tentar novamente
              </Button>
            </div>
          ) : linhas.length === 0 && pagina === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>Sem documentos fiscais</EmptyTitle>
                <EmptyDescription>
                  Este cliente ainda não tem faturas-recibo nem notas de crédito emitidas.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <div className="overflow-x-auto">
              <DataTable
                columns={colunasCliente}
                data={linhas}
                getRowId={(d) => d.id}
                manualPagination
                pageCount={totalPaginas}
                pagination={paginacao}
                onPaginationChange={onPaginationChange}
                emptyMessage="Nenhum documento nesta página."
              />
            </div>
          )}

          <p className="text-xs text-slate-500 dark:text-slate-400">
            Os documentos fiscais não podem ser apagados nem alterados. As correções são feitas por nota de crédito.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
