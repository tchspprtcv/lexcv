import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const eventoPrioridadeSchema = z.enum(["BAIXA", "MEDIA", "ALTA"]);

export const eventoFormSchema = z.object({
  processoId: optionalTrimmedString,
  tipo: optionalTrimmedString,
  titulo: z.string().trim().min(1, "O título é obrigatório"),
  descricao: optionalTrimmedString,
  dataInicio: z
    .string()
    .trim()
    .min(1, "dataInicio é obrigatório")
    .refine((v) => !Number.isNaN(new Date(v).getTime()), "dataInicio inválida"),
  dataFim: z
    .string()
    .trim()
    .min(1, "dataFim é obrigatório")
    .refine((v) => !Number.isNaN(new Date(v).getTime()), "dataFim inválida"),
  prioridade: eventoPrioridadeSchema,
  concluido: z.boolean(),
}).refine((data) => {
  const start = new Date(data.dataInicio).getTime();
  const end = new Date(data.dataFim).getTime();
  return end >= start;
}, {
  message: "A data de fim não pode ser anterior à data de início",
  path: ["dataFim"],
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
