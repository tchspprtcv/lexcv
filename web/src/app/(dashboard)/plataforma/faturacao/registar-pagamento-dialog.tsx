"use client";

import * as React from "react";
import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { useTenantsAdmin } from "@/hooks/use-platform-admin";
import {
  usePlatformConfiguracaoFiscal,
  useRegistarPagamentoSubscricao,
} from "@/hooks/use-platform-faturacao";
import { toast } from "@/hooks/use-toast";

const METODOS = [
  { value: "TRANSFERENCIA", label: "Transferência Bancária" },
  { value: "VINTI4", label: "Vinti4 / Cartão de Débito" },
  { value: "CARTAO_CREDITO", label: "Cartão de Crédito" },
  { value: "NUMERARIO", label: "Numerário / Dinheiro" },
  { value: "CHEQUE", label: "Cheque" },
  { value: "OUTRO", label: "Outro" },
];

const PLANOS = ["STARTER", "STANDARD", "ENTERPRISE"];

export function RegistarPagamentoDialog() {
  const [open, setOpen] = React.useState(false);
  const { data: config } = usePlatformConfiguracaoFiscal();
  const { data: tenants, isLoading: loadingTenants } = useTenantsAdmin();
  const registarPagamento = useRegistarPagamentoSubscricao();

  const hoje = new Date().toISOString().split("T")[0];

  const [tenantId, setTenantId] = React.useState("");
  const [valorPago, setValorPago] = React.useState("5000.00");
  const [dataPagamento, setDataPagamento] = React.useState(hoje);
  const [metodo, setMetodo] = React.useState("TRANSFERENCIA");
  const [periodoInicio, setPeriodoInicio] = React.useState(hoje);
  const [periodoFim, setPeriodoFim] = React.useState(() => {
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    return d.toISOString().split("T")[0];
  });
  const [plano, setPlano] = React.useState("STARTER");
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  // Filtra LexCV tenant reservada
  const offices = React.useMemo(() => {
    return tenants?.filter((t) => t.nome.toLowerCase() !== "lexcv" && t.ativo) ?? [];
  }, [tenants]);

  const handleTenantChange = (id: string) => {
    setTenantId(id);
    const selected = offices.find((t) => t.id === id);
    if (selected?.plano) {
      setPlano(selected.plano);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tenantId) {
      toast.error("Selecione o escritório adquirente.");
      return;
    }
    const val = parseFloat(valorPago);
    if (isNaN(val) || val <= 0) {
      toast.error("Introduza um valor de subscrição válido superior a zero.");
      return;
    }

    setIsSubmitting(true);
    try {
      const chaveIdempotencia = crypto.randomUUID();
      const res = await registarPagamento.mutateAsync({
        tenantId,
        valorPago: val,
        dataPagamento,
        metodo,
        periodoInicio,
        periodoFim,
        plano,
        chaveIdempotencia,
      });

      toast.success(`Fatura-Recibo ${res.numeroFormatado} emitida com sucesso.`);
      setOpen(false);
      setTenantId("");
    } catch {
      toast.error("Não foi possível registar o pagamento de subscrição.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const prontaParaEmitir = Boolean(config?.completa && config?.ativa);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          disabled={!prontaParaEmitir}
          className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
          title={!prontaParaEmitir ? "Configure os dados fiscais da plataforma primeiro" : undefined}
        >
          <Plus className="h-4 w-4" />
          Registar Pagamento de Subscrição
        </Button>
      </DialogTrigger>

      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registar Pagamento & Emitir Fatura de Subscrição</DialogTitle>
          <DialogDescription>
            Regista o pagamento de subscrição de um escritório cliente e emite atomicamente a respetiva Fatura-Recibo da plataforma LexCV.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 py-2">
          <div className="space-y-1.5">
            <Label htmlFor="tenantId" className="text-xs font-medium">
              Escritório Cliente (Adquirente) *
            </Label>
            <NativeSelect
              id="tenantId"
              value={tenantId}
              onChange={(e) => handleTenantChange(e.target.value)}
              disabled={isSubmitting || loadingTenants}
              required
            >
              <NativeSelectOption value="">Selecione o escritório...</NativeSelectOption>
              {offices.map((t) => (
                <NativeSelectOption key={t.id} value={t.id}>
                  {t.nome} ({t.plano})
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="valorPago" className="text-xs font-medium">
                Valor Pago (CVE) *
              </Label>
              <Input
                id="valorPago"
                type="number"
                step="0.01"
                min="0.01"
                value={valorPago}
                onChange={(e) => setValorPago(e.target.value)}
                disabled={isSubmitting}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="metodo" className="text-xs font-medium">
                Método de Pagamento *
              </Label>
              <NativeSelect
                id="metodo"
                value={metodo}
                onChange={(e) => setMetodo(e.target.value)}
                disabled={isSubmitting}
                required
              >
                {METODOS.map((m) => (
                  <NativeSelectOption key={m.value} value={m.value}>
                    {m.label}
                  </NativeSelectOption>
                ))}
              </NativeSelect>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="dataPagamento" className="text-xs font-medium">
                Data do Pagamento *
              </Label>
              <Input
                id="dataPagamento"
                type="date"
                value={dataPagamento}
                onChange={(e) => setDataPagamento(e.target.value)}
                disabled={isSubmitting}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="plano" className="text-xs font-medium">
                Plano Subscrito *
              </Label>
              <NativeSelect
                id="plano"
                value={plano}
                onChange={(e) => setPlano(e.target.value)}
                disabled={isSubmitting}
                required
              >
                {PLANOS.map((p) => (
                  <NativeSelectOption key={p} value={p}>
                    {p}
                  </NativeSelectOption>
                ))}
              </NativeSelect>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="periodoInicio" className="text-xs font-medium">
                Início do Período *
              </Label>
              <Input
                id="periodoInicio"
                type="date"
                value={periodoInicio}
                onChange={(e) => setPeriodoInicio(e.target.value)}
                disabled={isSubmitting}
                required
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="periodoFim" className="text-xs font-medium">
                Fim do Período *
              </Label>
              <Input
                id="periodoFim"
                type="date"
                value={periodoFim}
                onChange={(e) => setPeriodoFim(e.target.value)}
                disabled={isSubmitting}
                required
              />
            </div>
          </div>

          <DialogFooter className="pt-3">
            <DialogClose asChild>
              <Button type="button" variant="outline" disabled={isSubmitting} className="text-xs">
                Cancelar
              </Button>
            </DialogClose>
            <Button
              type="submit"
              disabled={isSubmitting || !tenantId}
              className="bg-blue-600 hover:bg-blue-700 text-white text-xs"
            >
              {isSubmitting ? "A emitir..." : "Emitir Fatura-Recibo"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
