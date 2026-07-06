import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const processoFormSchema = z.object({
  cliente_id: z.string().trim().min(1, "O cliente é obrigatório"),
  numero: optionalTrimmedString,
  titulo: optionalTrimmedString,
  tipo_processo: optionalTrimmedString,
  area_juridica: optionalTrimmedString,
  tribunal: optionalTrimmedString,
  descricao: optionalTrimmedString,
  estado: optionalTrimmedString,
  data_inicio: optionalTrimmedString,
  data_fim: optionalTrimmedString,
  legal_hold: z.boolean().optional(),
  data_retencao: optionalTrimmedString,
});

export type ProcessoFormValues = z.infer<typeof processoFormSchema>;

export const processoParteFormSchema = z.object({
  tipo: optionalTrimmedString,
  nome: z.string().trim().min(1, "O nome é obrigatório"),
  nif: optionalTrimmedString,
});

export type ProcessoParteFormValues = z.infer<typeof processoParteFormSchema>;

export const processoFaseStatusSchema = z.enum(["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA"]);

export const processoFaseFormSchema = z.object({
  nome: z.string().trim().min(1, "O nome da fase é obrigatório"),
});

export type ProcessoFaseFormValues = z.infer<typeof processoFaseFormSchema>;

export const processoMovimentacaoFormSchema = z.object({
  titulo: z.string().trim().min(1, "O título é obrigatório"),
  descricao: optionalTrimmedString,
  data: optionalTrimmedString,
});

export type ProcessoMovimentacaoFormValues = z.infer<typeof processoMovimentacaoFormSchema>;

export const conflictNivelEnum = z.enum([
  "sem_conflito",
  "potencial",
  "sanavel",
  "impeditivo",
]);

export const conflictCheckDecisaoFormSchema = z
  .object({
    nivel: conflictNivelEnum,
    justificativa: optionalTrimmedString,
    referenciaEvidencia: optionalTrimmedString,
  })
  .superRefine((data, ctx) => {
    if (
      (data.nivel === "potencial" || data.nivel === "sanavel") &&
      !data.justificativa
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Justificativa é obrigatória para este nível de conflito",
        path: ["justificativa"],
      });
    }
  });

export type ConflictCheckDecisaoFormValues = z.infer<
  typeof conflictCheckDecisaoFormSchema
>;

export const transicaoJustificativaFormSchema = z.object({
  justificativa: z
    .string()
    .trim()
    .min(10, "Justificativa deve ter pelo menos 10 caracteres"),
});

export type TransicaoJustificativaFormValues = z.infer<
  typeof transicaoJustificativaFormSchema
>;

export const prazoFormSchema = z.object({
  descricao: z.string().trim().min(1, "A descrição é obrigatória"),
  dataLimite: z.string().trim().min(1, "A data limite é obrigatória"),
  prioridade: z.enum(["ALTA", "MEDIA", "BAIXA"]).default("MEDIA"),
  responsavelId: optionalTrimmedString,
});

export type PrazoFormValues = z.infer<typeof prazoFormSchema>;
