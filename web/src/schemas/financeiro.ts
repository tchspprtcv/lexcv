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
  processoId: z.string().trim().min(1, "O processo é obrigatório"),
  valorTotal: moneyString,
  descricao: optionalTrimmedString,
  dataAcordo: optionalDateString,
});

export type HonorarioFormValues = z.infer<typeof honorarioFormSchema>;

export const pagamentoFormSchema = z.object({
  valorPago: moneyString,
  dataPagamento: optionalDateString,
  metodo: optionalTrimmedString,
});

export type PagamentoFormValues = z.infer<typeof pagamentoFormSchema>;
