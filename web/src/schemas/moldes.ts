import { z } from "zod";

// Nome literal reservado à plataforma -- espelha PAPEL_RESERVADO do backend
// (Plan 03, PlatformAdminController/RoleService). Esta validacao Zod e um
// espelho de UX para dar feedback imediato no formulario; a fronteira real
// de autoridade e a guarda do backend, que recusa a mesma criacao mesmo que
// este schema, por qualquer razao, deixe passar o valor.
const NOME_RESERVADO_PLATAFORMA = "PLATAFORMA_ADMIN";

export const criarMoldeSchema = z.object({
  nome: z
    .string()
    .trim()
    .min(1, "O nome do molde é obrigatório.")
    .refine((valor) => valor.toUpperCase() !== NOME_RESERVADO_PLATAFORMA, {
      message: "Este nome está reservado à plataforma e não pode ser usado como molde.",
    })
    .transform((valor) => valor.toUpperCase()),
  permissoes: z.array(z.string()),
});

export type CriarMoldeFormValues = z.infer<typeof criarMoldeSchema>;
