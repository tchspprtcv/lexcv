import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

const optionalEmail = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .refine((v) => !v || z.string().email().safeParse(v).success, "Email inválido")
  .optional();

export const clienteFormSchema = z
  .object({
    tipo: z.enum(["PARTICULAR", "EMPRESA"]).optional(),
    avencado: z.boolean().optional(),
    dados_tipo: z
      .object({
        // Particular
        idade: z.number().int().positive().optional(),
        sexo: optionalTrimmedString,
        nacionalidade: optionalTrimmedString,
        // Empresa
        nome_comercial: optionalTrimmedString,
        sede: optionalTrimmedString,
        representante_legal: optionalTrimmedString,
        cargo: optionalTrimmedString,
      })
      .optional(),
    nome: z.string().trim().min(1, "O nome é obrigatório"),
    nif: optionalTrimmedString,
    email: optionalEmail,
    telefone: optionalTrimmedString,
    morada: optionalTrimmedString,
    localidade: optionalTrimmedString,
    ativo: z.boolean().optional(),
    documento_tipo: optionalTrimmedString,
    documento_numero: optionalTrimmedString,
    ramo_atividade: optionalTrimmedString,
    detalhes_adicionais: z
      .string()
      .trim()
      .max(255, "Os detalhes adicionais não podem exceder 255 caracteres")
      .optional()
      .or(z.literal("")),
    descricao_caso: z.string().trim().optional(),
    honorarios_propostos: z
      .object({
        total: z.number().optional(),
        totalPorExtenso: z.string().trim().optional(),
        previsao: z.string().trim().optional(),
      })
      .optional(),
  })
  .superRefine((data, ctx) => {
    if (data.documento_tipo && !data.documento_numero) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Número de documento é obrigatório se o tipo estiver selecionado",
        path: ["documento_numero"],
      });
    }
    if (data.documento_tipo === "NIF" && data.documento_numero) {
      const isDigitsOnly = /^\d+$/.test(data.documento_numero);
      if (data.documento_numero.length !== 9 || !isDigitsOnly) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "NIF de Cabo Verde deve ter exatamente 9 dígitos",
          path: ["documento_numero"],
        });
      }
    }
    if (data.tipo === "EMPRESA") {
      if (!data.dados_tipo?.nome_comercial?.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "Nome comercial é obrigatório para Empresa",
          path: ["dados_tipo", "nome_comercial"],
        });
      }
      if (!data.dados_tipo?.representante_legal?.trim()) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "Representante legal é obrigatório para Empresa",
          path: ["dados_tipo", "representante_legal"],
        });
      }
    }
  });

export type ClienteFormValues = z.infer<typeof clienteFormSchema>;
