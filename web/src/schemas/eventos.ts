import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const eventoPrioridadeSchema = z.enum(["BAIXA", "MEDIA", "ALTA"]);

// Base object kept refinement-free so both the create form (full schema,
// with recurrence validation) and the edit form (recurrence fields omitted)
// can each derive from it. zod v4 disallows .omit() on a schema that already
// carries .refine()/.superRefine() checks ("cannot be used on object schemas
// containing refinements"), so the shared shape must not have any refinement
// attached to it directly.
const eventoBaseObjectSchema = z.object({
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
  recurrenceRule: z.enum(["NONE", "DAILY", "WEEKLY", "MONTHLY"]).optional(),
  recurrenceEndDate: optionalTrimmedString,
});

function requireDataFimNotBeforeDataInicio<
  T extends { dataInicio: string; dataFim: string },
>(data: T) {
  const start = new Date(data.dataInicio).getTime();
  const end = new Date(data.dataFim).getTime();
  return end >= start;
}

const dataFimRefinementOptions = {
  message: "A data de fim não pode ser anterior à data de início",
  path: ["dataFim"],
};

export const eventoFormSchema = eventoBaseObjectSchema
  .refine(requireDataFimNotBeforeDataInicio, dataFimRefinementOptions)
  .superRefine((data, ctx) => {
    if (data.recurrenceRule && data.recurrenceRule !== "NONE" && !data.recurrenceEndDate) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "A data de fim da recorrência é obrigatória",
        path: ["recurrenceEndDate"],
      });
    }
  });

export type EventoFormValues = z.infer<typeof eventoFormSchema>;

// The edit form has no UI for recurrenceRule/recurrenceEndDate (by design —
// recurrence is only set at creation time), but its default values still
// carry those fields through from the loaded evento. Validating against the
// full eventoFormSchema would require recurrenceEndDate whenever
// recurrenceRule !== "NONE" (see superRefine above), which the user has no
// way to satisfy or even see in this form. Omit both fields from the base
// shape before validation so such records remain editable; the dataFim >=
// dataInicio check still applies since both fields remain in this form.
export const eventoEditFormSchema = eventoBaseObjectSchema
  .omit({
    recurrenceRule: true,
    recurrenceEndDate: true,
  })
  .refine(requireDataFimNotBeforeDataInicio, dataFimRefinementOptions);

export type EventoEditFormValues = z.infer<typeof eventoEditFormSchema>;

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
