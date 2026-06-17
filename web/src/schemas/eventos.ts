import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const eventoPrioridadeSchema = z.enum(["BAIXA", "MEDIA", "ALTA"]);

export const eventoFormSchema = z.object({
  processo_id: optionalTrimmedString,
  titulo: z.string().trim().min(1, "O título é obrigatório"),
  descricao: optionalTrimmedString,
  data_inicio: z
    .string()
    .trim()
    .min(1, "data_inicio é obrigatório")
    .refine((v) => !Number.isNaN(new Date(v).getTime()), "data_inicio inválida"),
  data_fim: z
    .string()
    .trim()
    .min(1, "data_fim é obrigatório")
    .refine((v) => !Number.isNaN(new Date(v).getTime()), "data_fim inválida"),
  prioridade: eventoPrioridadeSchema,
  concluido: z.boolean(),
});

export type EventoFormValues = z.infer<typeof eventoFormSchema>;

const optionalConcluidoValue = z
  .string()
  .trim()
  .optional()
  .transform((v) => (v?.length ? v : undefined))
  .pipe(z.enum(["true", "false"]).optional());

export const eventoFiltroSchema = z.object({
  dataInicio: optionalTrimmedString,
  dataFim: optionalTrimmedString,
  processoId: optionalTrimmedString,
  concluido: optionalConcluidoValue,
});

export type EventoFiltroFormValues = z.input<typeof eventoFiltroSchema>;
export type EventoFiltroValues = z.output<typeof eventoFiltroSchema>;
