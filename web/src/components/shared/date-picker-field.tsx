"use client";

import { CalendarIcon } from "lucide-react";
import { pt } from "date-fns/locale";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

// Do NOT hand the raw string straight to the Date constructor: a bare
// "YYYY-MM-DD" string parses as UTC midnight per the ECMA-262 Date Time
// String Format, while "YYYY-MM-DDTHH:mm" parses as local time — that
// asymmetry shows a day-before off-by-one for any negative-UTC-offset
// timezone (e.g. Cabo Verde, CVT = UTC-01:00) the moment recurrenceEndDate
// is selected. Parsing Y/M/D components directly avoids it for both the
// date-only and date+time variants.
function parseDateOnly(v: string | undefined): Date | undefined {
  if (!v) return undefined;
  const [y, m, d] = v.slice(0, 10).split("-").map(Number);
  if (!y || !m || !d) return undefined;
  return new Date(y, m - 1, d); // local midnight — safe for both date-only and date+time strings
}

export function DatePickerField({
  value,
  onChange,
  withTime = false,
}: {
  value: string | undefined;
  onChange: (v: string) => void;
  withTime?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const dateValue = parseDateOnly(value);
  const timePart = withTime && value ? value.slice(11, 16) : "00:00";

  function commit(nextDate: Date | undefined, nextTime: string) {
    if (!nextDate) return;
    const pad = (n: number) => String(n).padStart(2, "0");
    const datePart = `${nextDate.getFullYear()}-${pad(nextDate.getMonth() + 1)}-${pad(nextDate.getDate())}`;
    onChange(withTime ? `${datePart}T${nextTime}` : datePart);
  }

  return (
    <div className={cn(withTime && "flex gap-2")}>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            className={cn("justify-start text-left font-normal", withTime ? "flex-1 min-w-0" : "w-full")}
          >
            <CalendarIcon className="mr-2 h-4 w-4 opacity-50" />
            {dateValue ? (
              dateValue.toLocaleDateString("pt-CV", { day: "2-digit", month: "2-digit", year: "numeric" })
            ) : (
              <span className="text-muted-foreground">Selecionar data</span>
            )}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="single"
            selected={dateValue}
            onSelect={(d) => {
              commit(d, timePart);
              setOpen(false);
            }}
            locale={pt}
            weekStartsOn={0}
            autoFocus
          />
        </PopoverContent>
      </Popover>
      {withTime ? (
        <Input
          type="time"
          className="w-24 shrink-0"
          value={timePart}
          onChange={(e) => commit(dateValue, e.target.value)}
        />
      ) : null}
    </div>
  );
}
