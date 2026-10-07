"use client";

import * as React from "react";
import { Download, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useExportarMesCsv } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import {
  COPY_EXPORTAR_SUCESSO,
  interpretarErroExportacao,
} from "@/lib/entrega-email";

// Phase 137 (RELF-01; 137-UI-SPEC Surface 4): diálogo de exportação mensal de documentos fiscais em CSV.
// O ficheiro é gerado integralmente pelo backend (o frontend nunca constrói CSV).

function obterMesesCaboVerde(): { mesAtual: string; mesAnterior: string } {
  const agora = new Date();
  const formatador = new Intl.DateTimeFormat("pt-CV", {
    timeZone: "Atlantic/Cape_Verde",
    year: "numeric",
    month: "2-digit",
  });
  const partes = formatador.formatToParts(agora);
  const ano = parseInt(partes.find((p) => p.type === "year")?.value ?? "2026", 10);
  const mes = parseInt(partes.find((p) => p.type === "month")?.value ?? "01", 10);

  const mesAtual = `${ano}-${String(mes).padStart(2, "0")}`;

  let anoAnt = ano;
  let mesAnt = mes - 1;
  if (mesAnt === 0) {
    mesAnt = 12;
    anoAnt -= 1;
  }
  const mesAnterior = `${anoAnt}-${String(mesAnt).padStart(2, "0")}`;

  return { mesAtual, mesAnterior };
}

interface ExportarMesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  triggerRef?: React.RefObject<HTMLButtonElement | null>;
}

export function ExportarMesDialog({
  open,
  onOpenChange,
  triggerRef,
}: ExportarMesDialogProps) {
  const exportarMutation = useExportarMesCsv();

  const { mesAtual, mesAnterior } = React.useMemo(() => obterMesesCaboVerde(), []);
  const [mes, setMes] = React.useState(mesAnterior);
  const [erroValidacao, setErroValidacao] = React.useState<string | null>(null);
  const [erroServidor, setErroServidor] = React.useState<string | null>(null);

  const tituloRef = React.useRef<HTMLHeadingElement>(null);

  // Reset state when opening
  React.useEffect(() => {
    if (open) {
      setMes(mesAnterior);
      setErroValidacao(null);
      setErroServidor(null);
    }
  }, [open, mesAnterior]);

  const isPending = exportarMutation.isPending;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isPending) return;

    if (!mes || mes.trim() === "") {
      setErroValidacao("Escolha um mês.");
      return;
    }
    if (mes > mesAtual) {
      setErroValidacao("Escolha um mês até ao mês atual.");
      return;
    }

    setErroValidacao(null);
    setErroServidor(null);

    try {
      await exportarMutation.mutateAsync({ mes });
      toast.success(COPY_EXPORTAR_SUCESSO);
      onOpenChange(false);
      triggerRef?.current?.focus();
    } catch (err) {
      const msg = interpretarErroExportacao(err);
      if (msg) {
        setErroServidor(msg);
      }
    }
  };

  const handleOpenChange = (proximoAberto: boolean) => {
    if (isPending) return;
    onOpenChange(proximoAberto);
    if (!proximoAberto) {
      triggerRef?.current?.focus();
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        onEscapeKeyDown={(e) => {
          if (isPending) e.preventDefault();
        }}
        onPointerDownOutside={(e) => {
          if (isPending) e.preventDefault();
        }}
        onOpenAutoFocus={(e) => {
          e.preventDefault();
          tituloRef.current?.focus();
        }}
        closeLabel="Fechar exportação do mês"
      >
        <DialogHeader>
          <DialogTitle ref={tituloRef} tabIndex={-1} className="outline-none">
            Exportar documentos fiscais do mês
          </DialogTitle>
          <DialogDescription>
            Gera um ficheiro CSV com as faturas-recibo e notas de crédito emitidas no mês, para entregar ao contabilista.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="exportar-mes">Mês</Label>
            <Input
              id="exportar-mes"
              type="month"
              value={mes}
              max={mesAtual}
              disabled={isPending}
              aria-invalid={erroValidacao ? true : undefined}
              aria-describedby={
                erroValidacao ? "exportar-mes-erro" : "exportar-mes-ajuda"
              }
              onChange={(e) => {
                setMes(e.target.value);
                if (erroValidacao) setErroValidacao(null);
              }}
            />
            {erroValidacao ? (
              <p id="exportar-mes-erro" className="text-xs text-red-600 dark:text-red-400">
                {erroValidacao}
              </p>
            ) : null}
            <p id="exportar-mes-ajuda" className="text-xs text-slate-500 dark:text-slate-400">
              Formato para Excel: separador ponto e vírgula, vírgula decimal e datas dd/mm/aaaa. As notas de crédito aparecem com valores negativos e a última linha tem os totais. Os documentos são simulados e não têm validade fiscal.
            </p>
          </div>

          {erroServidor ? (
            <div
              role="alert"
              className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-900/50 dark:bg-red-950/50 dark:text-red-400"
            >
              {erroServidor}
            </div>
          ) : null}

          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              type="button"
              variant="outline"
              disabled={isPending}
              onClick={() => handleOpenChange(false)}
            >
              Fechar sem exportar
            </Button>
            <Button type="submit" disabled={isPending} className="gap-1.5">
              {isPending ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  A exportar...
                </>
              ) : (
                <>
                  <Download className="h-4 w-4" aria-hidden />
                  Exportar CSV
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
