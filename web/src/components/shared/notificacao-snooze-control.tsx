"use client";

import { Clock } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { useSnoozeNotificacao } from "@/hooks/use-notificacoes";
import { toast } from "@/hooks/use-toast";
import { NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS } from "@/lib/notificacao-categoria";
import type { Notificacao } from "@/types/notificacoes";

const SNOOZE_PRESETS: { value: "1" | "3" | "7"; label: string }[] = [
  { value: "1", label: "1 dia" },
  { value: "3", label: "3 dias" },
  { value: "7", label: "7 dias" },
];

/**
 * Reusable Popover + RadioGroup snooze control (NOTF-26). Self-hides for
 * categories in NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS (currently only
 * PRAZO_VENCIDO — mirrors the backend's 400 on snooze()). This category
 * check is the control's ONLY visibility gate: it must render regardless of
 * `notificacao.lida` (an already-read notification is still snoozeable), so
 * callers must NOT wrap it in a `{!lida && (...)}` conditional.
 */
export function NotificacaoSnoozeControl({ notificacao }: { notificacao: Notificacao }) {
  const [open, setOpen] = React.useState(false);
  const [selected, setSelected] = React.useState<"1" | "3" | "7">("1");
  const snooze = useSnoozeNotificacao();

  if (NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS.includes(notificacao.categoria)) {
    return null;
  }

  const handleConfirm = async () => {
    const dias = Number(selected) as 1 | 3 | 7;
    try {
      await snooze.mutateAsync({ id: notificacao.id, dias });
      toast.success(`Notificação adiada por ${dias} dias.`);
      setOpen(false);
    } catch {
      // Erro já reportado pelo toast automático do apiFetch.
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0"
          aria-label="Adiar notificação"
          title="Adiar notificação"
        >
          <Clock className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-56 p-3" align="end">
        <p className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-2">
          Adiar notificação
        </p>
        <RadioGroup
          value={selected}
          onValueChange={(value) => setSelected(value as "1" | "3" | "7")}
          className="mb-3"
        >
          {SNOOZE_PRESETS.map((preset) => {
            const inputId = `notificacao-snooze-${notificacao.id}-${preset.value}`;
            return (
              <div key={preset.value} className="flex items-center gap-2">
                <RadioGroupItem value={preset.value} id={inputId} />
                <Label htmlFor={inputId} className="text-sm font-normal cursor-pointer">
                  {preset.label}
                </Label>
              </div>
            );
          })}
        </RadioGroup>
        <Button
          type="button"
          size="sm"
          className="w-full"
          disabled={snooze.isPending}
          onClick={handleConfirm}
        >
          {snooze.isPending ? "A adiar..." : "Adiar"}
        </Button>
      </PopoverContent>
    </Popover>
  );
}

export default NotificacaoSnoozeControl;
