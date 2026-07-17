"use client";

import { ChevronsUpDown } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

export type ComboboxOption = {
  value: string;
  label: string;
};

export function Combobox({
  id,
  value,
  onChange,
  options,
  placeholder = "Selecionar...",
  searchPlaceholder = "Pesquisar...",
  emptyMessage = "Nenhum resultado encontrado.",
  creatable = false,
  createLabel = (query: string) => `Usar "${query}"`,
  triggerClassName,
}: {
  id?: string;
  value: string | undefined;
  onChange: (v: string) => void;
  options: ComboboxOption[];
  placeholder?: string;
  searchPlaceholder?: string;
  emptyMessage?: string;
  creatable?: boolean;
  createLabel?: (query: string) => string;
  triggerClassName?: string;
}) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");

  const selected = options.find((option) => option.value === value);
  const trimmedQuery = query.trim();
  const hasExactMatch = options.some(
    (option) => option.label.toLowerCase() === trimmedQuery.toLowerCase(),
  );
  const showCreateItem = creatable && trimmedQuery.length > 0 && !hasExactMatch;
  const filtered = options.filter((option) =>
    option.label.toLowerCase().includes(trimmedQuery.toLowerCase()),
  );

  // A freshly-typed tipo that has no match in `options` must still be shown in
  // the trigger (not silently fall back to the placeholder) — only the
  // absence of both a matched label AND a creatable raw value is "empty".
  const showRawValue = !selected && creatable && !!value;
  const triggerLabel = selected?.label ?? (showRawValue ? value : placeholder);
  const isPlaceholder = !selected && !showRawValue;

  function commit(nextValue: string) {
    onChange(nextValue);
    setOpen(false);
    setQuery("");
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn("w-full justify-between font-normal", triggerClassName)}
        >
          <span className={cn("truncate", isPlaceholder && "text-muted-foreground")}>
            {triggerLabel}
          </span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="start">
        <Command shouldFilter={false}>
          <CommandInput
            placeholder={searchPlaceholder}
            value={query}
            onValueChange={setQuery}
          />
          <CommandList>
            <CommandGroup>
              {showCreateItem ? (
                <CommandItem value={trimmedQuery} onSelect={() => commit(trimmedQuery)}>
                  {createLabel(trimmedQuery)}
                </CommandItem>
              ) : null}
              {filtered.map((option) => (
                <CommandItem
                  key={option.value}
                  value={option.label}
                  data-checked={option.value === value}
                  onSelect={() => commit(option.value)}
                >
                  {option.label}
                </CommandItem>
              ))}
            </CommandGroup>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
