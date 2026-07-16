"use client";

import type { Table } from "@tanstack/react-table";
import { SlidersHorizontal } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";

interface DataTableViewOptionsProps<TData> {
  table: Table<TData>;
}

/**
 * Column-visibility toggle for the shared DataTable pattern.
 *
 * Icon-only trigger (per DSR-03's icon-only-button convention) wrapped in a
 * Tooltip, opening a DropdownMenu listing every hideable column as a
 * DropdownMenuCheckboxItem bound to `column.getIsVisible()` /
 * `column.toggleVisibility()`. Columns with `enableHiding: false` (Ações,
 * primary identity column) never appear here, so the table can never be
 * hidden down to zero useful columns.
 */
export function DataTableViewOptions<TData>({
  table,
}: DataTableViewOptionsProps<TData>) {
  const hideableColumns = table
    .getAllColumns()
    .filter((column) => column.getCanHide());

  return (
    <DropdownMenu>
      <Tooltip>
        <TooltipTrigger asChild>
          <DropdownMenuTrigger asChild>
            <Button
              type="button"
              variant="outline"
              size="sm"
              aria-label="Colunas visíveis"
              className="h-8 w-8 p-0"
            >
              <SlidersHorizontal className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
        </TooltipTrigger>
        <TooltipContent>Colunas visíveis</TooltipContent>
      </Tooltip>
      <DropdownMenuContent align="end" className="w-[180px]">
        {hideableColumns.map((column) => {
          const header = column.columnDef.header;
          const label = typeof header === "string" ? header : column.id;

          return (
            <DropdownMenuCheckboxItem
              key={column.id}
              checked={column.getIsVisible()}
              onCheckedChange={(value) => column.toggleVisibility(!!value)}
            >
              {label}
            </DropdownMenuCheckboxItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
