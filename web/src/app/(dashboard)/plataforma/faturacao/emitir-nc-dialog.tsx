"use client";

import * as React from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { useEmitirNotaCreditoSubscricao } from "@/hooks/use-platform-faturacao";
import { toast } from "@/hooks/use-toast";
import type { DocumentoFiscalResumo } from "@/types/faturacao";
import type { MotivoNotaCredito } from "@/types/platform-faturacao";

const MOTIVOS_NC: { value: MotivoNotaCredito; label: string }[] = [
  { value: "ANULACAO_TOTAL", label: "Anulação Total da Fatura" },
  { value: "CORRECAO_VALOR", label: "Correção de Valor / Desconto Concedido" },
  { value: "ERRO_DADOS_CLIENTE", label: "Erro nos Dados do Escritório Adquirente" },
  { value: "OUTRO", label: "Outro Motivo Justificado" },
];

interface EmitirNcDialogProps {
  documento: DocumentoFiscalResumo | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function EmitirNcDialog({ documento, open, onOpenChange }: EmitirNcDialogProps) {
  const [motivoCodigo, setMotivoCodigo] = React.useState<MotivoNotaCredito>("ANULACAO_TOTAL");
  const [motivoTexto, setMotivoTexto] = React.useState("");
  const [valorTotal, setValorTotal] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  const emitirNc = useEmitirNotaCreditoSubscricao(documento?.id ?? "");

  React.useEffect(() => {
    if (documento) {
      setValorTotal(documento.totalDocumento.toFixed(2));
      setMotivoTexto("");
      setMotivoCodigo("ANULACAO_TOTAL");
    }
  }, [documento]);

  if (!documento) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!motivoTexto.trim()) {
      toast.error("Introduza a justificação da Nota de Crédito.");
      return;
    }

    const val = parseFloat(valorTotal);
    if (isNaN(val) || val <= 0 || val > documento.totalDocumento) {
      toast.error(`O valor a creditar deve estar entre 0.01 e ${documento.totalDocumento.toFixed(2)} CVE.`);
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await emitirNc.mutateAsync({
        motivoCodigo,
        motivoTexto: motivoTexto.trim(),
        valorTotal: val,
        chaveIdempotencia: crypto.randomUUID(),
      });

      toast.success(`Nota de Crédito ${res.numeroFormatado} emitida com sucesso.`);
      onOpenChange(false);
    } catch {
      toast.error("Não foi possível emitir a Nota de Crédito.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Emitir Nota de Crédito</DialogTitle>
          <DialogDescription>
            Emissão de Nota de Crédito de retificação ou anulação sobre a fatura de subscrição{" "}
            <span className="font-semibold text-slate-900 dark:text-white">{documento.numeroFormatado}</span>{" "}
            emitida para <span className="font-semibold text-slate-900 dark:text-white">{documento.adquirenteNome}</span>.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 py-2">
          <div className="space-y-1.5">
            <Label htmlFor="motivoCodigo" className="text-xs font-medium">
              Motivo Oficial da Retificação *
            </Label>
            <NativeSelect
              id="motivoCodigo"
              value={motivoCodigo}
              onChange={(e) => setMotivoCodigo(e.target.value as MotivoNotaCredito)}
              disabled={isSubmitting}
              required
            >
              {MOTIVOS_NC.map((m) => (
                <NativeSelectOption key={m.value} value={m.value}>
                  {m.label}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="valorTotal" className="text-xs font-medium">
              Valor a Creditar (CVE) * (Máx.: {documento.totalDocumento.toFixed(2)})
            </Label>
            <Input
              id="valorTotal"
              type="number"
              step="0.01"
              min="0.01"
              max={documento.totalDocumento}
              value={valorTotal}
              onChange={(e) => setValorTotal(e.target.value)}
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="motivoTexto" className="text-xs font-medium">
              Justificação / Descrição *
            </Label>
            <Input
              id="motivoTexto"
              maxLength={200}
              placeholder="Ex.: Reembolso de subscrição por anulação de serviço..."
              value={motivoTexto}
              onChange={(e) => setMotivoTexto(e.target.value)}
              disabled={isSubmitting}
              required
            />
          </div>

          <DialogFooter className="pt-3">
            <DialogClose asChild>
              <Button type="button" variant="outline" disabled={isSubmitting} className="text-xs">
                Cancelar
              </Button>
            </DialogClose>
            <Button
              type="submit"
              disabled={isSubmitting}
              className="bg-red-600 hover:bg-red-700 text-white text-xs"
            >
              {isSubmitting ? "A emitir..." : "Emitir Nota de Crédito"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
