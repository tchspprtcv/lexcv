import { z } from "zod";

// Nomes literais reservados a plataforma -- espelham a guarda de
// `OfficeRolesController#validarNome` (Phase 127, Plano 04). Esta validacao Zod e um espelho
// de UX para dar feedback imediato no formulario; a fronteira real de autoridade e a guarda do
// backend, que recusa a mesma criacao/renomeacao mesmo que este schema, por qualquer razao,
// deixe passar o valor.
const NOME_RESERVADO_PLATAFORMA = "PLATAFORMA_ADMIN";
const NOME_RESERVADO_PLATAFORMA_AUTORIDADE = "ROLE_PLATAFORMA_ADMIN";

function nomeReservado(valor: string): boolean {
  const normalizado = valor.toUpperCase();
  return normalizado === NOME_RESERVADO_PLATAFORMA || normalizado === NOME_RESERVADO_PLATAFORMA_AUTORIDADE;
}

// Ao contrario de `moldes.ts` (que uppercasa o nome do molde porque os nomes de molde sao
// tokens reservados da plataforma como ADMIN/ADVOGADO), este schema NAO transforma o nome: um
// papel de escritorio e nomeado livremente pelo proprio administrador ("Recepção", "Financeiro
// Sénior"), sem convencao de maiusculas a respeitar -- UI-SPEC §7/§8 e
// `OfficeRolesController#validarNome` concordam explicitamente nisto.
const nomePapel = z
  .string()
  .trim()
  .min(1, "O nome do papel é obrigatório.")
  .refine((valor) => !nomeReservado(valor), {
    message: "Este nome está reservado à plataforma e não pode ser usado.",
  });

export const criarPapelSchema = z.object({
  nome: nomePapel,
  permissoes: z.array(z.string()),
});

export type CriarPapelFormValues = z.infer<typeof criarPapelSchema>;

export const renomearPapelSchema = z.object({
  nome: nomePapel,
});

export type RenomearPapelFormValues = z.infer<typeof renomearPapelSchema>;
