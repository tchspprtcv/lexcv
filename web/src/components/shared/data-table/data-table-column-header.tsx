"use client";

import * as React from "react";
import type { Column } from "@tanstack/react-table";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface DataTableColumnHeaderProps<TData, TValue>
  extends React.HTMLAttributes<HTMLDivElement> {
  column: Column<TData, TValue>;
  title: string;
}

/**
 * Sortable header cell for the shared DataTable pattern.
 *
 * When the column supports sorting, renders a ghost button that cycles the
 * 3-state sort (unsorted -> asc -> desc -> unsorted) via
 * `column.toggleSorting()`. When the column does not support sorting, renders
 * a plain label with no button/icon.
 *
 * The title always renders with the mandated 12px/600 uppercase muted
 * typography (the Label role), closing the pre-existing 3-way inconsistency
 * across Clientes/Processos/Pareceres (previously an off-scale small/heavy
 * treatment) and Financeiro/Documentos (raw <th>, a third weight value).
 */
export function DataTableColumnHeader<TData, TValue>({
  column,
  title,
  className,
}: DataTableColumnHeaderProps<TData, TValue>) {
  const labelClassName =
    "text-xs font-semibold uppercase tracking-wider text-muted-foreground";

  if (!column.getCanSort()) {
    return <span className={cn(labelClassName, className)}>{title}</span>;
  }

  const sorted = column.getIsSorted();

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className={cn("h-auto gap-1.5 p-0 hover:bg-transparent", className)}
      onClick={() => column.toggleSorting(sorted === "asc")}
    >
      <span className={labelClassName}>{title}</span>
      {sorted === "asc" ? (
        <ArrowUp className="h-3.5 w-3.5 text-muted-foreground" />
      ) : sorted === "desc" ? (
        <ArrowDown className="h-3.5 w-3.5 text-muted-foreground" />
      ) : (
        <ChevronsUpDown className="h-3.5 w-3.5 text-muted-foreground" />
      )}
    </Button>
  );
}
