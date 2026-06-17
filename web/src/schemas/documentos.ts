import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

export const documentosFiltersFormSchema = z.object({
  processo_id: optionalTrimmedString,
  cliente_id: optionalTrimmedString,
});

export type DocumentosFiltersFormValues = z.infer<typeof documentosFiltersFormSchema>;

const fileListSchema = z.custom<FileList>((value) => value instanceof FileList, {
  message: "O ficheiro é obrigatório",
});

export const documentoUploadFormSchema = z.object({
  file: fileListSchema.refine((files) => files.length === 1, "O ficheiro é obrigatório"),
  nome: optionalTrimmedString,
  tipo: optionalTrimmedString,
  processo_id: optionalTrimmedString,
  cliente_id: optionalTrimmedString,
  confidencialidade: z.enum(["PUBLICO", "INTERNO", "CONFIDENCIAL", "RESTRITO"]).default("PUBLICO").optional(),
  replace_id: optionalTrimmedString,
});

export type DocumentoUploadFormValues = z.infer<typeof documentoUploadFormSchema>;
