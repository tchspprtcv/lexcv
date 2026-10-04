"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { usePreVisualizacaoFaturacao } from "@/hooks/use-faturacao";
import { useCreatePagamento } from "@/hooks/use-financeiro";
import { toast } from "@/hooks/use-toast";
import { interpretarErroEmissao, type ErroEmissao } from "@/lib/erros-emissao";
import { gerarChaveIdempotencia } from "@/lib/idempotencia";
import {
  METODOS_PAGAMENTO,
  pagamentoFaturadoFormSchema,
  paraPedidoPagamentoFaturado,
  type PagamentoFaturadoFormInput,
  type PagamentoFaturadoFormValues,
} from "@/schemas/financeiro";
import type { PreVisualizacaoFatura } from "@/types/faturacao";
import type { PagamentoCreateRequest } from "@/types/financeiro";

import { PagamentoFaturadoDialog } from "./pagamento-faturado-dialog";

// Formulário de pagamento com a faturação ATIVA (134-UI-SPEC Surface 1; D-02, D-03, D-04, D-10).
// Fluxo: validar -> pré-visualização no backend -> diálogo de confirmação -> POST /pagamentos com
// a chave de idempotência gerada quando o diálogo abre. Nenhum montante é calculado aqui (D-01):
// o backend é a autoridade e o diálogo só mostra a resposta da pré-visualização.

type Banner = { mensagem: string; linkCliente: boolean };

/** Data de hoje em Cabo Verde, "AAAA-MM-DD" (en-CA formata exatamente assim). */
function hojeCaboVerde() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Atlantic/Cape_Verde" }).format(new Date());
}

function valoresIniciais(): PagamentoFaturadoFormInput {
  return {
    valorPago: "",
    dataPagamento: hojeCaboVerde(),
    metodo: "" as PagamentoFaturadoFormInput["metodo"],
    aplicarRetencao: false,
    retencaoPercentagem: "",
  };
}

const ERRO_CAMPO = "text-xs text-red-600 dark:text-red-400";
const AJUDA = "text-xs text-slate-500 dark:text-slate-400";

export function PagamentoFaturadoForm({
  honorarioId,
  clienteId,
  taxaRetencaoSugerida,
  canViewClientes,
}: {
  honorarioId: number;
  clienteId: string | undefined;
  taxaRetencaoSugerida: number | null;
  canViewClientes: boolean;
}) {
  const preVisualizar = usePreVisualizacaoFaturacao();
  const createPagamento = useCreatePagamento();

  const form = useForm<PagamentoFaturadoFormInput, unknown, PagamentoFaturadoFormValues>({
    resolver: zodResolver(pagamentoFaturadoFormSchema),
    defaultValues: valoresIniciais(),
  });

  const [banner, setBanner] = React.useState<Banner | null>(null);
  const [dialogoAberto, setDialogoAberto] = React.useState(false);
  const [preVisualizacao, setPreVisualizacao] = React.useState<PreVisualizacaoFatura | null>(null);
  const [pedido, setPedido] = React.useState<PagamentoCreateRequest | null>(null);
  const [chave, setChave] = React.useState<string | null>(null);
  const [emitindo, setEmitindo] = React.useState(false);
  const [erroRede, setErroRede] = React.useState<string | null>(null);
  // Guarda síncrona contra duplo clique (o estado só é visível no render seguinte).
  const emitindoRef = React.useRef(false);
  const bannerRef = React.useRef<HTMLDivElement>(null);

  const aplicarRetencao = useWatch({ control: form.control, name: "aplicarRetencao" });
  const erros = form.formState.errors;

  const fecharDialogo = () => {
    setDialogoAberto(false);
    setChave(null);
    setErroRede(null);
  };

  /** Coloca o erro da pré-visualização/emissão no sítio certo (campo ou banner). */
  const mostrarErro = (erro: ErroEmissao | null) => {
    if (!erro) return;
    if (erro.tipo === "campo") {
      form.setError(erro.campo, { type: "server", message: erro.mensagem });
      form.setFocus(erro.campo);
      return;
    }
    setBanner({ mensagem: erro.mensagem, linkCliente: erro.tipo === "adquirente" });
  };

  // O foco passa para o banner quando aparece um erro (UI-SPEC: focus no campo ou no banner).
  React.useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  const onSubmit = async (values: PagamentoFaturadoFormValues) => {
    setBanner(null);
    const novoPedido = paraPedidoPagamentoFaturado(values, honorarioId);
    try {
      const resposta = await preVisualizar.mutateAsync(novoPedido);
      setPedido(novoPedido);
      setPreVisualizacao(resposta);
      setChave(gerarChaveIdempotencia());
      setErroRede(null);
      setDialogoAberto(true);
    } catch (e) {
      mostrarErro(interpretarErroEmissao(e));
    }
  };

  const emitir = async () => {
    if (emitindoRef.current || !pedido || !chave) return;
    emitindoRef.current = true;
    setEmitindo(true);
    setErroRede(null);
    try {
      const pagamento = await createPagamento.mutateAsync({ ...pedido, chaveIdempotencia: chave });
      fecharDialogo();
      setPreVisualizacao(null);
      setPedido(null);
      form.reset(valoresIniciais());
      const numero = pagamento.documentoFiscal?.numeroFormatado;
      toast.success(
        numero
          ? `Pagamento registado e fatura-recibo ${numero} emitida.`
          : "Pagamento registado com sucesso.",
      );
    } catch (e) {
      const erro = interpretarErroEmissao(e);
      if (erro?.tipo === "rede") {
        // O diálogo fica aberto e a nova tentativa reutiliza a MESMA chave (D-10).
        setErroRede(erro.mensagem);
      } else {
        fecharDialogo();
        mostrarErro(erro);
      }
    } finally {
      emitindoRef.current = false;
      setEmitindo(false);
    }
  };

  const aCalcular = preVisualizar.isPending;

  return (
    <>
      <form className="space-y-4" noValidate onSubmit={form.handleSubmit(onSubmit)}>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="fat-valorPago">Valor pago</Label>
            <Input
              id="fat-valorPago"
              type="number"
              inputMode="decimal"
              step="0.01"
              min="0"
              aria-invalid={erros.valorPago ? true : undefined}
              aria-describedby={erros.valorPago ? "fat-valorPago-erro fat-valorPago-ajuda" : "fat-valorPago-ajuda"}
              {...form.register("valorPago")}
            />
            <p id="fat-valorPago-ajuda" className={AJUDA}>
              Valor com IVA incluído.
            </p>
            {erros.valorPago ? (
              <p id="fat-valorPago-erro" className={ERRO_CAMPO}>
                {erros.valorPago.message}
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="fat-dataPagamento">Data do pagamento</Label>
            <Input
              id="fat-dataPagamento"
              type="date"
              aria-invalid={erros.dataPagamento ? true : undefined}
              aria-describedby={
                erros.dataPagamento ? "fat-dataPagamento-erro fat-dataPagamento-ajuda" : "fat-dataPagamento-ajuda"
              }
              {...form.register("dataPagamento")}
            />
            <p id="fat-dataPagamento-ajuda" className={AJUDA}>
              Com a faturação ativa, a data tem de ser a de hoje.
            </p>
            {erros.dataPagamento ? (
              <p id="fat-dataPagamento-erro" className={ERRO_CAMPO}>
                {erros.dataPagamento.message}
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="fat-metodo">Método de pagamento *</Label>
            <NativeSelect
              id="fat-metodo"
              className="w-full"
              aria-invalid={erros.metodo ? true : undefined}
              aria-describedby={erros.metodo ? "fat-metodo-erro" : undefined}
              {...form.register("metodo")}
            >
              <NativeSelectOption value="">Escolha…</NativeSelectOption>
              {METODOS_PAGAMENTO.map((m) => (
                <NativeSelectOption key={m.valor} value={m.valor}>
                  {m.rotulo}
                </NativeSelectOption>
              ))}
            </NativeSelect>
            {erros.metodo ? (
              <p id="fat-metodo-erro" className={ERRO_CAMPO}>
                {erros.metodo.message}
              </p>
            ) : null}
          </div>

          <div className="flex items-center gap-2 sm:col-span-2">
            <Controller
              control={form.control}
              name="aplicarRetencao"
              render={({ field }) => (
                <Checkbox
                  id="fat-aplicarRetencao"
                  checked={field.value}
                  onCheckedChange={(marcado) => {
                    const ativo = marcado === true;
                    field.onChange(ativo);
                    if (ativo && !form.getValues("retencaoPercentagem") && taxaRetencaoSugerida != null) {
                      form.setValue("retencaoPercentagem", String(taxaRetencaoSugerida));
                    }
                  }}
                  onBlur={field.onBlur}
                />
              )}
            />
            <Label htmlFor="fat-aplicarRetencao">Aplicar retenção na fonte</Label>
          </div>

          {aplicarRetencao ? (
            <div className="space-y-2">
              <Label htmlFor="fat-retencaoPercentagem">Taxa de retenção (%)</Label>
              <Input
                id="fat-retencaoPercentagem"
                inputMode="decimal"
                className="w-full"
                aria-invalid={erros.retencaoPercentagem ? true : undefined}
                aria-describedby={
                  erros.retencaoPercentagem
                    ? "fat-retencao-erro fat-retencao-ajuda"
                    : "fat-retencao-ajuda"
                }
                {...form.register("retencaoPercentagem")}
              />
              <p id="fat-retencao-ajuda" className={AJUDA}>
                Calculada sobre o valor sem IVA.
              </p>
              {erros.retencaoPercentagem ? (
                <p id="fat-retencao-erro" className={ERRO_CAMPO}>
                  {erros.retencaoPercentagem.message}
                </p>
              ) : null}
            </div>
          ) : null}
        </div>

        {banner ? (
          <div ref={bannerRef} tabIndex={-1} role="alert" className="space-y-1 text-sm text-red-600 outline-none dark:text-red-400">
            <p>{banner.mensagem}</p>
            {banner.linkCliente && clienteId && canViewClientes ? (
              <Link
                href={`/clientes/${encodeURIComponent(clienteId)}`}
                className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
              >
                Abrir cliente
              </Link>
            ) : null}
          </div>
        ) : null}

        <Button type="submit" disabled={aCalcular || emitindo}>
          <Plus className="h-4 w-4" />
          {aCalcular ? "A calcular..." : "Registar pagamento"}
        </Button>
      </form>

      <PagamentoFaturadoDialog
        open={dialogoAberto}
        preVisualizacao={preVisualizacao}
        emitindo={emitindo}
        erroRede={erroRede}
        onVoltar={fecharDialogo}
        onEmitir={emitir}
      />
    </>
  );
}
