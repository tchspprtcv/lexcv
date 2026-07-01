import { z } from "zod";

export const parecerStatusSchema = z.enum(["PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO"]);

export const parecerPrioridadeSchema = z.enum(["ALTA", "MEDIA", "BAIXA"]);
