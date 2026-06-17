import { z } from "zod";

export const strongPasswordPattern =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;

export const setupSchema = z
  .object({
    clientName: z.string().trim().min(1, "O nome da empresa/cliente é obrigatório."),
    adminEmail: z.string().trim().email("Introduza um email válido."),
    adminPassword: z
      .string()
      .regex(
        strongPasswordPattern,
        "A password deve ter 8+ caracteres, maiúscula, minúscula, número e símbolo.",
      ),
    confirmPassword: z.string().min(1, "Confirme a password."),
  })
  .refine((values) => values.adminPassword === values.confirmPassword, {
    message: "As passwords não coincidem.",
    path: ["confirmPassword"],
  });

export type SetupFormValues = z.infer<typeof setupSchema>;
