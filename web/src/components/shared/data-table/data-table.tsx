"use client";

import * as React from "react";
import {
  type ColumnDef,
  type OnChangeFn,
  type PaginationState,
  type SortingState,
  type VisibilityState,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { DataTableViewOptions } from "@/components/shared/data-table/data-table-view-options";
import { DataTablePagination } from "@/components/shared/data-table/data-table-pagination";

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  getRowId?: (row: TData) => string;
  /**
   * Paginação no servidor (opcional, Phase 134). Com `manualPagination`, `data` é só a página
   * atual, `pageCount` vem do servidor e `pagination`/`onPaginationChange` são controlados por
   * quem chama. Sem estas props o comportamento é exatamente o de sempre (paginação no cliente).
   */
  manualPagination?: boolean;
  pageCount?: number;
  pagination?: PaginationState;
  onPaginationChange?: OnChangeFn<PaginationState>;
  /** Visibilidade inicial das colunas (ex.: esconder uma coluna em ecrãs pequenos). */
  initialColumnVisibility?: VisibilityState;
  /** Texto da linha vazia; por omissão o texto de sempre. */
  emptyMessage?: string;
}

/**
 * Generic, reusable DataTable composition shared by the 5 list screens
 * (Clientes, Processos, Pareceres, Financeiro, Documentos).
 *
 * Configures `useReactTable` with the core, sorted, and pagination row
 * models ONLY -- client-side sort/paginate over an already-fetched,
 * already-server-filtered array. Row filtering is deliberately never
 * configured here: filtering stays 100% owned by each screen's existing
 * use-* hook + useState filters (locked architecture decision,
 * 104-CONTEXT.md).
 *
 * Renders exclusively through the reconciled Table/TableHeader/TableBody/
 * TableRow/TableHead/TableCell primitives -- never a bare HTML table
 * element, including for Financeiro/Documentos which had no Table
 * adoption at all before this phase.
 */
export function DataTable<TData, TValue>({
  columns,
  data,
  getRowId,
  manualPagination = false,
  pageCount,
  pagination,
  onPaginationChange,
  initialColumnVisibility,
  emptyMessage = "Sem resultados para os filtros aplicados.",
}: DataTableProps<TData, TValue>) {
  const [sorting, setSorting] = React.useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] =
    React.useState<VisibilityState>(initialColumnVisibility ?? {});

  const table = useReactTable({
    data,
    columns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    initialState: {
      pagination: {
        pageSize: 10,
      },
    },
    ...(manualPagination
      ? {
          manualPagination: true,
          pageCount: pageCount ?? -1,
          onPaginationChange,
          state: {
            sorting,
            columnVisibility,
            ...(pagination ? { pagination } : {}),
          },
        }
      : {
          getPaginationRowModel: getPaginationRowModel(),
          state: {
            sorting,
            columnVisibility,
          },
        }),
  });

  return (
    <div>
      <div className="flex items-center justify-end px-6 py-3">
        <DataTableViewOptions table={table} />
      </div>

      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <TableHead key={header.id}>
                  {header.isPlaceholder
                    ? null
                    : flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                </TableHead>
              ))}
            </TableRow>
          ))}
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.length ? (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell
                colSpan={columns.length}
                className="h-24 text-center text-muted-foreground"
              >
                {emptyMessage}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>

      <DataTablePagination table={table} />
    </div>
  );
}
