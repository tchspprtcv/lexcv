import { z } from "zod";

export const parecerStatusSchema = z.enum(["PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO"]);

export const parecerPrioridadeSchema = z.enum(["ALTA", "MEDIA", "BAIXA"]);

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const parecerCreateFormSchema = z.object({
  clienteId: z.string().trim().min(1, "Selecione um cliente."),
  processoId: optionalTrimmedString,
  descricao: z
    .string()
    .trim()
    .superRefine((val, ctx) => {
      if (val.length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "Descreva o pedido de parecer.",
        });
        return;
      }
      if (val.length < 10) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "A descrição deve ter pelo menos 10 caracteres.",
        });
      }
    }),
  prazo: optionalTrimmedString,
  prioridade: parecerPrioridadeSchema.default("MEDIA"),
  advogadoId: optionalTrimmedString,
});

export type ParecerCreateFormValues = z.infer<typeof parecerCreateFormSchema>;

const fileListSchema = z.custom<FileList>((value) => value instanceof FileList, {
  message: "É necessário anexar um ficheiro para submeter esta versão.",
});

export const parecerVersaoCreateFormSchema = z.object({
  conteudo: z
    .string()
    .trim()
    .superRefine((val, ctx) => {
      if (val.length === 0 || val.length < 10) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "O resumo deve ter pelo menos 10 caracteres.",
        });
      }
    }),
  file: fileListSchema.refine(
    (files) => files.length === 1,
    "É necessário anexar um ficheiro para submeter esta versão.",
  ),
});

export type ParecerVersaoCreateFormValues = z.infer<typeof parecerVersaoCreateFormSchema>;
