/**
 * Endereço e assunto do pedido de demonstração.
 *
 * Centralizado porque o mesmo apelo à ação aparece no cabeçalho, no hero, no
 * fim das secções e no rodapé — e porque o dia em que deixar de ser um mailto
 * (formulário, agendamento) muda-se aqui e não em seis sítios.
 */
const EMAIL = "contacto@lexcv.cv";

export function getDemoUrl(origem?: string) {
  const assunto = "Pedido de demonstração — LexCV";
  const corpo = [
    "Bom dia,",
    "",
    "Gostaríamos de conhecer o LexCV.",
    "",
    "Escritório/instituição:",
    "Número de utilizadores previstos:",
    "Melhor horário para falar:",
    "",
  ].join("\n");
  const params = new URLSearchParams({ subject: assunto, body: corpo });
  // A origem fica no fragmento para se saber que secção gerou o contacto, sem
  // poluir o email que o visitante vê.
  return `mailto:${EMAIL}?${params.toString()}${origem ? `#${origem}` : ""}`;
}

export const EMAIL_CONTACTO = EMAIL;
