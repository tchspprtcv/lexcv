import { z } from "zod";

// Regra de NIF FISCAL do escritório: 9 dígitos, o primeiro de 1 a 9. É de propósito mais estrita
// do que a regra de NIF de cliente em `schemas/clientes.ts` (`/^\d{9}$/`) -- CONTEXT D "Dados
// fiscais" -- e espelha `ConfiguracaoFiscal.NIF_FISCAL_REGEX` no backend. Este schema é um
// espelho de UX; a fronteira de autoridade é a validação do backend.
export const nifFiscalPattern = /^[1-9]\d{8}$/;

export const MORADA_MAX = 100;

// Comprimentos máximos espelhados de `ConfiguracaoFiscalRequest` (@Size) e das colunas de
// `t_configuracao_fiscal`, para o limite aparecer inline antes da ida ao servidor.
export const FIRMA_MAX = 150;
export const LOCALIDADE_MAX = 100;
export const EMAIL_MAX = 254;
export const TELEFONE_MAX = 32;

const OBRIGATORIO = "Preencha este campo.";

const campoObrigatorio = () => z.string().trim().min(1, OBRIGATORIO);

export const configuracaoFiscalSchema = z
  .object({
    nif: z
      .string()
      .trim()
      .regex(nifFiscalPattern, "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9."),
    firma: campoObrigatorio().max(FIRMA_MAX, "A firma não pode ter mais de 150 caracteres."),
    morada: campoObrigatorio().max(MORADA_MAX, "A morada não pode ter mais de 100 caracteres."),
    localidade: campoObrigatorio().max(
      LOCALIDADE_MAX,
      "A localidade não pode ter mais de 100 caracteres.",
    ),
    emailContacto: campoObrigatorio()
      .max(EMAIL_MAX, "O email não pode ter mais de 254 caracteres.")
      .pipe(z.email("Introduza um email válido.")),
    telefoneContacto: campoObrigatorio().max(
      TELEFONE_MAX,
      "O telefone não pode ter mais de 32 caracteres.",
    ),
    regimeIva: z.enum(["NORMAL", "ISENTO"], { error: OBRIGATORIO }),
    motivoIsencaoCodigo: z.string().trim().nullable(),
  })
  .superRefine((valores, ctx) => {
    if (valores.regimeIva === "ISENTO" && !valores.motivoIsencaoCodigo) {
      ctx.addIssue({
        code: "custom",
        path: ["motivoIsencaoCodigo"],
        message: "Escolha o motivo de isenção.",
      });
    }
  })
  .transform((valores) => ({
    ...valores,
    motivoIsencaoCodigo: valores.regimeIva === "NORMAL" ? null : valores.motivoIsencaoCodigo,
  }));

export type ConfiguracaoFiscalFormInput = z.input<typeof configuracaoFiscalSchema>;

export type ConfiguracaoFiscalFormValues = z.output<typeof configuracaoFiscalSchema>;
