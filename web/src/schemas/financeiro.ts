import { z } from "zod";

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
  processo_id: z.string().trim().min(1, "O processo é obrigatório"),
  valor_total: moneyString,
  descricao: optionalTrimmedString,
  data_acordo: optionalDateString,
});

export type HonorarioFormValues = z.infer<typeof honorarioFormSchema>;

export const pagamentoFormSchema = z.object({
  valor_pago: moneyString,
  data_pagamento: optionalDateString,
  metodo: optionalTrimmedString,
});

export type PagamentoFormValues = z.infer<typeof pagamentoFormSchema>;
