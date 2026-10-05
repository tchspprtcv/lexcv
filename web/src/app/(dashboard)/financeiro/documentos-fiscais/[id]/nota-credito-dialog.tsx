"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { FilePlus2, Info } from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { useEmitirNotaCredito, usePreVisualizacaoNotaCredito } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import { desfechoDefinitivo, interpretarErroNotaCredito } from "@/lib/erros-emissao";
import { marcarPorResolver, tentativaParaPedido, type TentativaEmissao } from "@/lib/idempotencia";
import {
  reagirAErroNotaCredito,
  type PassoNotaCredito,
  type ReacaoErroNotaCredito,
} from "@/lib/nota-credito-dialogo";
import {
  MOTIVO_TEXTO_MAX,
  MOTIVOS_NOTA_CREDITO,
  notaCreditoFormSchema,
  paraPedidoNotaCredito,
  type NotaCreditoFormInput,
  type NotaCreditoFormValues,
} from "@/schemas/financeiro";
import type { DocumentoFiscalDetalhe, NotaCreditoRequest, PreVisualizacaoNotaCredito } from "@/types/faturacao";

// "Emitir Nota de Crédito" (135-UI-SPEC Surface 1; NCRD-01, NCRD-02): botão + diálogo em dois
// passos (formulário -> pré-visualização calculada no backend -> emissão). Nenhum montante é
// calculado aqui ("frontend burro"): os valores vêm do detalhe da FR e da pré-visualização, e o
// teto (valor ainda creditável) só é verificado pelo backend.
//
// A chave de idempotência pertence ao CONTEÚDO do pedido (FR de origem + tipo + valor + motivo +
// descrição), não ao diálogo (CR-02 da 134): depois de uma falha ambígua (rede/5xx/401/403)
// sobrevive ao fecho do diálogo e o mesmo pedido reutiliza-a; só é descartada depois de um desfecho
// definitivo (sucesso ou recusa 4xx processada) ou quando o pedido muda.

type Banner = { mensagem: string; definitivo: boolean };

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";
const BLOCO = "space-y-2 rounded-md bg-slate-50 p-4 text-sm dark:bg-slate-900";
const ERRO_CAMPO = "text-xs text-red-600 dark:text-red-400";
const AJUDA = "text-xs text-slate-500 dark:text-slate-400";

function formatarCVE(valor: number) {
  return valor.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

function valoresIniciais(): NotaCreditoFormInput {
  return {
    tipo: "TOTAL",
    valor: "",
    motivoCodigo: "" as NotaCreditoFormInput["motivoCodigo"],
    motivoTexto: "",
  };
}

export function NotaCreditoDialog({
  documento,
  tituloPaginaRef,
}: {
  documento: DocumentoFiscalDetalhe;
  /** `h1` da página: recebe o foco quando a FR fica totalmente creditada (o botão desaparece). */
  tituloPaginaRef?: React.RefObject<HTMLHeadingElement | null>;
}) {
  const preVisualizar = usePreVisualizacaoNotaCredito(documento.id);
  const emitirNc = useEmitirNotaCredito(documento.id);

  const form = useForm<NotaCreditoFormInput, unknown, NotaCreditoFormValues>({
    resolver: zodResolver(notaCreditoFormSchema),
    defaultValues: valoresIniciais(),
  });

  const [aberto, setAberto] = React.useState(false);
  const [passo, setPasso] = React.useState<PassoNotaCredito>("formulario");
  const [banner, setBanner] = React.useState<Banner | null>(null);
  const [preVisualizacao, setPreVisualizacao] = React.useState<PreVisualizacaoNotaCredito | null>(null);
  const [pedido, setPedido] = React.useState<NotaCreditoRequest | null>(null);
  const [tentativa, setTentativa] = React.useState<TentativaEmissao | null>(null);
  const [emitindo, setEmitindo] = React.useState(false);
  // Guarda síncrona contra duplo clique (o estado só é visível no render seguinte).
  const emitindoRef = React.useRef(false);
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const bannerRef = React.useRef<HTMLDivElement>(null);
  const focarTituloPaginaRef = React.useRef(false);

  const tipo = useWatch({ control: form.control, name: "tipo" });
  const motivoTexto = useWatch({ control: form.control, name: "motivoTexto" }) ?? "";
  const erros = form.formState.errors;

  // O foco passa para o banner quando aparece um erro (UI-SPEC: foco no campo ou no banner).
  React.useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  // Ao entrar na pré-visualização o foco vai para o título; o botão de emissão nunca recebe foco
  // automático (um Enter acidental não emite o documento).
  React.useEffect(() => {
    if (aberto && passo === "pre-visualizacao") tituloRef.current?.focus();
  }, [aberto, passo]);

  const abrir = () => {
    setBanner(null);
    setPasso("formulario");
    setAberto(true);
  };

  // Fechar o diálogo NÃO descarta a tentativa: se o desfecho estiver por resolver, o próximo envio
  // do mesmo pedido tem de levar a mesma chave.
  const fechar = () => {
    if (emitindoRef.current) return;
    setAberto(false);
  };

  const aplicar = (reacao: ReacaoErroNotaCredito) => {
    switch (reacao.acao) {
      case "ignorar":
        return;
      case "fechar":
        setAberto(false);
        return;
      case "campo":
        setPasso(reacao.passo);
        form.setError(reacao.campo, { type: "server", message: reacao.mensagem });
        // O campo só existe depois de o passo 1 voltar a ser renderizado.
        setTimeout(() => form.setFocus(reacao.campo), 0);
        return;
      case "banner":
        setPasso(reacao.passo);
        setBanner({ mensagem: reacao.mensagem, definitivo: reacao.definitivo });
        return;
    }
  };

  const onPreVisualizar = async (values: NotaCreditoFormValues) => {
    setBanner(null);
    const novoPedido = paraPedidoNotaCredito(values);
    try {
      const resposta = await preVisualizar.mutateAsync(novoPedido);
      setPedido(novoPedido);
      setPreVisualizacao(resposta);
      // A chave pertence ao conteúdo, incluindo a FR de origem.
      const pedidoChave = { documentoOrigemId: documento.id, ...novoPedido };
      setTentativa((anterior) => tentativaParaPedido(anterior, pedidoChave));
      setPasso("pre-visualizacao");
    } catch (e) {
      aplicar(reagirAErroNotaCredito(interpretarErroNotaCredito(e, "pre-visualizacao"), "formulario"));
    }
  };

  const emitir = async () => {
    if (emitindoRef.current || !pedido || !tentativa || !preVisualizacao) return;
    emitindoRef.current = true;
    setEmitindo(true);
    setBanner(null);
    try {
      // WR-01: os valores confirmados seguem no corpo, mas FORA da chave (que é do conteúdo `pedido`).
      const nc = await emitirNc.mutateAsync({
        ...pedido,
        chaveIdempotencia: tentativa.chave,
        totalEsperado: preVisualizacao.total,
        valorCreditavelEsperado: preVisualizacao.valorCreditavelAntes,
      });
      setTentativa(null);
      setPreVisualizacao(null);
      setPedido(null);
      form.reset(valoresIniciais());
      // Totalmente creditada: o botão desaparece, por isso o foco vai para o h1 da página.
      focarTituloPaginaRef.current = nc.valorCreditavelRestante === 0;
      emitindoRef.current = false;
      setAberto(false);
      toast.success(`Nota de crédito ${nc.numeroFormatado} emitida.`);
    } catch (e) {
      // Recusa 4xx processada: a chave não tem NC associada e pode ser descartada. Qualquer outra
      // falha (rede, 5xx, 401/403/408/429) deixa o desfecho por resolver e mantém a chave.
      setTentativa((atual) => (desfechoDefinitivo(e) ? null : marcarPorResolver(atual)));
      aplicar(reagirAErroNotaCredito(interpretarErroNotaCredito(e, "emissao"), "pre-visualizacao"));
    } finally {
      emitindoRef.current = false;
      setEmitindo(false);
    }
  };

  const bloquearFecho = (e: Event) => {
    if (emitindo) e.preventDefault();
  };

  const aCalcular = preVisualizar.isPending;
  const p = passo === "pre-visualizacao" ? preVisualizacao : null;
  const isento = p?.regimeIva === "ISENTO";

  const bannerVisivel = banner ? (
    <div
      ref={bannerRef}
      tabIndex={-1}
      role="alert"
      className="text-sm text-red-600 outline-none dark:text-red-400"
    >
      {banner.mensagem}
    </div>
  ) : null;

  return (
    <>
      <Button asChild variant="outline">
        <button ref={triggerRef} type="button" onClick={abrir}>
          <FilePlus2 className="h-4 w-4" aria-hidden="true" />
          Emitir Nota de Crédito
        </button>
      </Button>

      <Dialog
        open={aberto}
        onOpenChange={(abrirDialogo) => {
          // Enquanto a emissão está em curso o diálogo não pode ser fechado (Esc, overlay ou X).
          if (!abrirDialogo) fechar();
        }}
      >
        <DialogContent
          closeLabel={
            p ? "Fechar pré-visualização da nota de crédito" : "Fechar emissão de nota de crédito"
          }
          className="max-h-[85vh] overflow-y-auto"
          onEscapeKeyDown={bloquearFecho}
          onPointerDownOutside={bloquearFecho}
          onInteractOutside={bloquearFecho}
          onCloseAutoFocus={(e) => {
            e.preventDefault();
            if (focarTituloPaginaRef.current) {
              focarTituloPaginaRef.current = false;
              tituloPaginaRef?.current?.focus();
            } else {
              triggerRef.current?.focus();
            }
          }}
        >
          {p ? (
            <>
              <DialogHeader>
                <DialogTitle ref={tituloRef} tabIndex={-1} className="text-xl font-semibold outline-none">
                  Emitir Nota de Crédito
                </DialogTitle>
                <DialogDescription className="text-sm text-slate-500 dark:text-slate-400">
                  Depois de emitida, a nota de crédito não pode ser alterada nem apagada.
                </DialogDescription>
              </DialogHeader>

              <div className={NOTICE_CLASSES}>
                Documento simulado: não tem validade fiscal. Continue a emitir os seus documentos válidos no
                software de faturação homologado.
              </div>

              <div>
                <Badge variant="outline" className="gap-1">
                  <Info className="h-3 w-3" aria-hidden="true" />
                  Simulação — sem validade fiscal
                </Badge>
              </div>

              <div className={BLOCO}>
                <p className="font-semibold">Adquirente</p>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
                  <dt className="text-slate-500 dark:text-slate-400">Nome</dt>
                  <dd>{p.adquirenteNome}</dd>
                  <dt className="text-slate-500 dark:text-slate-400">NIF</dt>
                  <dd className="font-mono">{p.adquirenteNif}</dd>
                  <dt className="text-slate-500 dark:text-slate-400">Morada</dt>
                  <dd>{p.adquirenteMorada}</dd>
                </dl>
              </div>

              <p className="text-sm">
                Corrige a fatura-recibo <span className="font-mono">{p.documentoOrigemNumero}</span>
              </p>
              <p className="text-sm break-words">
                {p.motivoRotulo} — {p.motivoTexto}
              </p>

              <div className={BLOCO}>
                <dl className="grid grid-cols-[1fr_auto] gap-2">
                  <dt>Base tributável</dt>
                  <dd className="text-right tabular-nums">{formatarCVE(p.base)}</dd>
                  {isento ? (
                    <>
                      <dt>IVA</dt>
                      <dd className="text-right">Isento</dd>
                      <dt>Motivo de isenção</dt>
                      <dd className="text-right">
                        {p.motivoIsencaoCodigo} — {p.motivoIsencaoDescricao}
                      </dd>
                    </>
                  ) : (
                    <>
                      <dt>IVA ({p.taxaIva}%)</dt>
                      <dd className="text-right tabular-nums">{formatarCVE(p.iva)}</dd>
                    </>
                  )}
                  {p.retencao > 0 ? (
                    <>
                      <dt>Retenção na fonte ({p.taxaRetencao}%)</dt>
                      <dd className="text-right tabular-nums">{formatarCVE(p.retencao)}</dd>
                    </>
                  ) : null}
                </dl>
                <Separator />
                <dl className="grid grid-cols-[1fr_auto] gap-2 font-semibold">
                  <dt>Total a creditar</dt>
                  <dd className="text-right tabular-nums">{formatarCVE(p.total)}</dd>
                  <dt>Valor creditável restante</dt>
                  <dd className="text-right tabular-nums">{formatarCVE(p.valorCreditavelDepois)}</dd>
                </dl>
                <p className={AJUDA}>
                  O valor creditado sai do total pago do honorário, que volta a ficar por pagar na parte creditada.
                  Será registado um estorno nos pagamentos.
                </p>
              </div>

              {bannerVisivel}

              <DialogFooter className="gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={emitindo}
                  onClick={() => {
                    setBanner(null);
                    setPasso("formulario");
                  }}
                >
                  Voltar e editar
                </Button>
                <Button type="button" onClick={emitir} disabled={emitindo}>
                  {emitindo ? "A emitir..." : "Emitir nota de crédito"}
                </Button>
              </DialogFooter>
            </>
          ) : (
            <form className="space-y-4" noValidate onSubmit={form.handleSubmit(onPreVisualizar)}>
              <DialogHeader>
                <DialogTitle className="text-xl font-semibold">Emitir Nota de Crédito</DialogTitle>
                <DialogDescription className="text-sm text-slate-500 dark:text-slate-400">
                  Corrija esta fatura-recibo emitindo uma nota de crédito. O documento original não é alterado.
                </DialogDescription>
              </DialogHeader>

              <div className={BLOCO}>
                <dl className="grid grid-cols-[1fr_auto] gap-2">
                  <dt>Fatura-recibo</dt>
                  <dd className="text-right font-mono">{documento.numeroFormatado}</dd>
                  <dt>Total da fatura</dt>
                  <dd className="text-right tabular-nums">{formatarCVE(documento.totalDocumento)}</dd>
                  <dt className="font-semibold">Valor ainda creditável</dt>
                  <dd className="text-right font-semibold tabular-nums">
                    {formatarCVE(documento.valorCreditavelRestante ?? 0)}
                  </dd>
                </dl>
                <p className={AJUDA}>O valor total das notas de crédito não pode exceder o total da fatura.</p>
              </div>

              <fieldset className="space-y-2">
                <legend className="text-sm font-semibold">Tipo de crédito</legend>
                <Controller
                  control={form.control}
                  name="tipo"
                  render={({ field }) => (
                    <RadioGroup
                      value={field.value}
                      onValueChange={(valor) => {
                        field.onChange(valor);
                        if (valor === "TOTAL") {
                          // Pedido mudou: o valor digitado é descartado (e a chave será outra).
                          form.setValue("valor", "");
                          form.clearErrors("valor");
                        }
                      }}
                      aria-invalid={erros.tipo ? true : undefined}
                    >
                      <div className="flex items-start gap-2">
                        <RadioGroupItem id="nc-tipo-total" value="TOTAL" className="mt-0.5" />
                        <div className="space-y-1">
                          <Label htmlFor="nc-tipo-total">Total</Label>
                          <p className={AJUDA}>Credita todo o valor ainda creditável.</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <RadioGroupItem id="nc-tipo-parcial" value="PARCIAL" />
                        <Label htmlFor="nc-tipo-parcial">Parcial</Label>
                      </div>
                    </RadioGroup>
                  )}
                />
                {erros.tipo ? <p className={ERRO_CAMPO}>{erros.tipo.message}</p> : null}
              </fieldset>

              {tipo === "PARCIAL" ? (
                <div className="space-y-2">
                  <Label htmlFor="nc-valor">Valor a creditar (CVE) *</Label>
                  <Input
                    id="nc-valor"
                    inputMode="decimal"
                    aria-invalid={erros.valor ? true : undefined}
                    aria-describedby={erros.valor ? "nc-valor-erro nc-valor-ajuda" : "nc-valor-ajuda"}
                    {...form.register("valor")}
                  />
                  <p id="nc-valor-ajuda" className={AJUDA}>
                    Valor com IVA incluído.
                  </p>
                  {erros.valor ? (
                    <p id="nc-valor-erro" className={ERRO_CAMPO}>
                      {erros.valor.message}
                    </p>
                  ) : null}
                </div>
              ) : null}

              <div className="space-y-2">
                <Label htmlFor="nc-motivo">Motivo *</Label>
                <NativeSelect
                  id="nc-motivo"
                  className="w-full"
                  aria-invalid={erros.motivoCodigo ? true : undefined}
                  aria-describedby={erros.motivoCodigo ? "nc-motivo-erro" : undefined}
                  {...form.register("motivoCodigo")}
                >
                  <NativeSelectOption value="">Escolha o motivo</NativeSelectOption>
                  {MOTIVOS_NOTA_CREDITO.map((m) => (
                    <NativeSelectOption key={m.valor} value={m.valor}>
                      {m.rotulo}
                    </NativeSelectOption>
                  ))}
                </NativeSelect>
                {erros.motivoCodigo ? (
                  <p id="nc-motivo-erro" className={ERRO_CAMPO}>
                    {erros.motivoCodigo.message}
                  </p>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="nc-motivoTexto">Descrição do motivo *</Label>
                <Textarea
                  id="nc-motivoTexto"
                  rows={3}
                  maxLength={MOTIVO_TEXTO_MAX}
                  aria-invalid={erros.motivoTexto ? true : undefined}
                  aria-describedby={
                    erros.motivoTexto
                      ? "nc-motivoTexto-erro nc-motivoTexto-ajuda nc-motivoTexto-contador"
                      : "nc-motivoTexto-ajuda nc-motivoTexto-contador"
                  }
                  {...form.register("motivoTexto")}
                />
                <div className="flex items-start justify-between gap-4">
                  <p id="nc-motivoTexto-ajuda" className={AJUDA}>
                    Fica registada no documento.
                  </p>
                  <p id="nc-motivoTexto-contador" className={`${AJUDA} tabular-nums`}>
                    {motivoTexto.length}/{MOTIVO_TEXTO_MAX}
                  </p>
                </div>
                {erros.motivoTexto ? (
                  <p id="nc-motivoTexto-erro" className={ERRO_CAMPO}>
                    {erros.motivoTexto.message}
                  </p>
                ) : null}
              </div>

              {bannerVisivel}

              <DialogFooter className="gap-2">
                <Button type="button" variant="outline" onClick={fechar}>
                  {/* Checker clarification do 135-UI-SPEC: "Fechar" depois de um erro definitivo. */}
                  {banner?.definitivo ? "Fechar" : "Fechar sem emitir"}
                </Button>
                <Button type="submit" disabled={aCalcular}>
                  {aCalcular ? "A calcular..." : "Pré-visualizar nota de crédito"}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
