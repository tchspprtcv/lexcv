import { z } from "zod";

import type { MetodoPagamento, MotivoNotaCredito, NotaCreditoRequest, TipoCredito } from "@/types/faturacao";
import type { PagamentoCreateRequest } from "@/types/financeiro";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

const moneyString = z
  .string()
  .trim()
  .min(1, "O valor é obrigatório")
  .refine((v) => {
    const n = Number(v);
    return Number.isFinite(n) && n > 0;
  }, "O valor deve ser um número > 0");

const optionalDateString = optionalTrimmedString.refine((v) => {
  if (!v) return true;
  return !Number.isNaN(new Date(v).getTime());
}, "Data inválida");

export const honorarioFormSchema = z.object({
  processoId: z.string().trim().min(1, "O processo é obrigatório"),
  valorTotal: moneyString,
  descricao: optionalTrimmedString,
  dataAcordo: optionalDateString,
});

export type HonorarioFormValues = z.infer<typeof honorarioFormSchema>;

export const honorarioUpdateSchema = z.object({
  valorTotal: moneyString,
  descricao: optionalTrimmedString,
  dataAcordo: optionalDateString,
});

export type HonorarioUpdateFormValues = z.infer<typeof honorarioUpdateSchema>;

export const pagamentoFormSchema = z.object({
  valorPago: moneyString,
  dataPagamento: optionalDateString,
  metodo: optionalTrimmedString,
});

export type PagamentoFormValues = z.infer<typeof pagamentoFormSchema>;

// ---------------------------------------------------------------------------------------------
// Phase 134 (D-03, D-04, D-19): formulário de pagamento com a faturação ATIVA. O schema acima
// (`pagamentoFormSchema`, faturação desligada) fica exatamente como estava (CFG-03). Este schema é
// só um espelho de UX: o backend volta a validar tudo e é quem calcula os valores (D-01). Nenhuma
// taxa está fixada aqui -- a sugerida chega em runtime de `useEstadoEmissao`.

/** Métodos aceites com a faturação ativa; os rótulos são os de `MetodoPagamento.rotulo()`. */
export const METODOS_PAGAMENTO = [
  { valor: "DINHEIRO", rotulo: "Dinheiro" },
  { valor: "TRANSFERENCIA", rotulo: "Transferência bancária" },
  { valor: "CHEQUE", rotulo: "Cheque" },
  { valor: "CARTAO", rotulo: "Cartão / Multibanco" },
  { valor: "OUTRO", rotulo: "Outro" },
] as const satisfies readonly { valor: MetodoPagamento; rotulo: string }[];

/**
 * Rótulo do método de um pagamento (IN-01 da revisão): com a faturação ativa o backend grava o
 * nome do enum (`TRANSFERENCIA`, `CARTAO`...); os pagamentos legados guardam texto livre, que é
 * mostrado tal como está.
 */
export function rotuloMetodoPagamento(metodo: string | null | undefined): string {
  if (!metodo) return "—";
  return METODOS_PAGAMENTO.find((m) => m.valor === metodo)?.rotulo ?? metodo;
}

const VALORES_METODO = ["DINHEIRO", "TRANSFERENCIA", "CHEQUE", "CARTAO", "OUTRO"] as const satisfies readonly MetodoPagamento[];

const MSG_METODO_OBRIGATORIO = "Escolha o método de pagamento.";
const MSG_TAXA_RETENCAO = "A taxa de retenção deve ser superior a 0 e não superior a 100.";

// Número positivo com vírgula ou ponto decimal e no máximo duas casas.
const taxaPattern = /^\d+(?:[.,]\d{1,2})?$/;

/** Converte a taxa digitada ("12,5" ou "12.5") num número; undefined se o formato não servir. */
function taxaRetencaoComoNumero(valor: string | undefined): number | undefined {
  const t = (valor ?? "").trim();
  if (!taxaPattern.test(t)) return undefined;
  return Number(t.replace(",", "."));
}

export const pagamentoFaturadoFormSchema = z
  .object({
    valorPago: moneyString,
    dataPagamento: optionalDateString,
    metodo: z.enum(VALORES_METODO, { error: MSG_METODO_OBRIGATORIO }),
    aplicarRetencao: z.boolean(),
    retencaoPercentagem: z.string().trim().optional(),
  })
  .superRefine((valores, ctx) => {
    if (!valores.aplicarRetencao) return;
    const taxa = taxaRetencaoComoNumero(valores.retencaoPercentagem);
    if (taxa === undefined || !(taxa > 0 && taxa <= 100)) {
      ctx.addIssue({ code: "custom", path: ["retencaoPercentagem"], message: MSG_TAXA_RETENCAO });
    }
  });

export type PagamentoFaturadoFormInput = z.input<typeof pagamentoFaturadoFormSchema>;

export type PagamentoFaturadoFormValues = z.output<typeof pagamentoFaturadoFormSchema>;

/**
 * Corpo de `POST /pagamentos` (e da pré-visualização) a partir do formulário validado. Não define
 * `chaveIdempotencia`: a chave pertence ao diálogo de confirmação (D-10).
 */
export function paraPedidoPagamentoFaturado(
  valores: PagamentoFaturadoFormValues,
  honorarioId: number,
): PagamentoCreateRequest {
  return {
    honorarioId,
    valorPago: Number(valores.valorPago),
    dataPagamento: valores.dataPagamento,
    metodo: valores.metodo,
    retencaoPercentagem: valores.aplicarRetencao
      ? taxaRetencaoComoNumero(valores.retencaoPercentagem)
      : undefined,
  };
}

// ---------------------------------------------------------------------------------------------
// Phase 135 (NCRD-01, 135-UI-SPEC Surface 1): formulário da Nota de Crédito. Só verificações de
// UX (valor > 0 quando Parcial, motivo escolhido, descrição 1..200); o teto cumulativo e todos os
// montantes são do backend -- nunca se compara o valor com o valor creditável aqui.

/** Motivos pela ordem do UI-SPEC; os rótulos são os de `MotivoNotaCredito.rotulo()`. */
export const MOTIVOS_NOTA_CREDITO = [
  { valor: "ANULACAO_TOTAL", rotulo: "Anulação total" },
  { valor: "CORRECAO_VALOR", rotulo: "Correção de valor" },
  { valor: "ERRO_DADOS_CLIENTE", rotulo: "Erro nos dados do cliente" },
  { valor: "OUTRO", rotulo: "Outro" },
] as const satisfies readonly { valor: MotivoNotaCredito; rotulo: string }[];

export const TIPOS_CREDITO = [
  { valor: "TOTAL", rotulo: "Total" },
  { valor: "PARCIAL", rotulo: "Parcial" },
] as const satisfies readonly { valor: TipoCredito; rotulo: string }[];

/** Igual a `ValidacaoNotaCredito.MOTIVO_TEXTO_MAX` / `motivo_texto VARCHAR(200)`. */
export const MOTIVO_TEXTO_MAX = 200;

const VALORES_MOTIVO_NC = [
  "ANULACAO_TOTAL",
  "CORRECAO_VALOR",
  "ERRO_DADOS_CLIENTE",
  "OUTRO",
] as const satisfies readonly MotivoNotaCredito[];
const VALORES_TIPO_CREDITO = ["TOTAL", "PARCIAL"] as const satisfies readonly TipoCredito[];

const MSG_VALOR_CREDITO = "Indique um valor superior a 0.";
const MSG_MOTIVO_NC = "Escolha o motivo da nota de crédito.";
const MSG_MOTIVO_TEXTO_NC = "Descreva o motivo da nota de crédito.";
const MSG_MOTIVO_TEXTO_LONGO = `Descreva o motivo da nota de crédito (no máximo ${MOTIVO_TEXTO_MAX} caracteres).`;

export const notaCreditoFormSchema = z
  .object({
    tipo: z.enum(VALORES_TIPO_CREDITO).default("TOTAL"),
    valor: z.string().optional(),
    motivoCodigo: z.enum(VALORES_MOTIVO_NC, { error: MSG_MOTIVO_NC }),
    motivoTexto: z.string().trim().min(1, MSG_MOTIVO_TEXTO_NC).max(MOTIVO_TEXTO_MAX, MSG_MOTIVO_TEXTO_LONGO),
  })
  .superRefine((valores, ctx) => {
    if (valores.tipo !== "PARCIAL") return;
    // A mesma regra de formato do `valorPago` da FR (moneyString), com a copy da NC.
    if (!moneyString.safeParse(valores.valor ?? "").success) {
      ctx.addIssue({ code: "custom", path: ["valor"], message: MSG_VALOR_CREDITO });
    }
  });

export type NotaCreditoFormInput = z.input<typeof notaCreditoFormSchema>;

export type NotaCreditoFormValues = z.output<typeof notaCreditoFormSchema>;

/**
 * Corpo da pré-visualização e da emissão a partir do formulário validado. Não define
 * `chaveIdempotencia`: a chave pertence ao conteúdo do pedido (`lib/idempotencia.ts`, CR-02).
 */
export function paraPedidoNotaCredito(valores: NotaCreditoFormValues): NotaCreditoRequest {
  return {
    tipo: valores.tipo,
    valor: valores.tipo === "PARCIAL" ? Number((valores.valor ?? "").trim()) : null,
    motivoCodigo: valores.motivoCodigo,
    motivoTexto: valores.motivoTexto.trim(),
  };
}
