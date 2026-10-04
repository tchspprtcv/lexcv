import { z } from "zod";

import type { MetodoPagamento } from "@/types/faturacao";
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
