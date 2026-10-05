// Phase 135 (NCRD-01, NCRD-02; 135-UI-SPEC Surface 1): lógica pura do diálogo "Emitir Nota de
// Crédito" -- para que passo o diálogo vai em cada erro e se o erro é definitivo (o que troca
// "Fechar sem emitir" por "Fechar" no passo 1). A chave de idempotência pertence ao conteúdo
// `{ documentoOrigemId, ...pedido }` (ver o teste) e o seu destino depois de uma falha segue o
// padrão da 134 (`desfechoDefinitivo` ? descartar : `marcarPorResolver`). Fica fora do componente
// para ser testada com vitest (o componente só liga isto ao React).

import { desfechoDefinitivo, type CampoNotaCredito, type ErroNotaCredito } from "@/lib/erros-emissao";
import { gerarChaveIdempotencia, marcarPorResolver, type TentativaEmissao } from "@/lib/idempotencia";

export type PassoNotaCredito = "formulario" | "pre-visualizacao";

export type ReacaoErroNotaCredito =
  | { acao: "ignorar" }
  | { acao: "campo"; campo: CampoNotaCredito; mensagem: string; passo: "formulario" }
  | { acao: "banner"; mensagem: string; passo: PassoNotaCredito; definitivo: boolean }
  | { acao: "fechar" };

/**
 * O que o diálogo faz com um erro já interpretado (`interpretarErroNotaCredito`):
 * - campo: volta ao passo 1 com o erro no campo;
 * - excede / valores alterados / chave reutilizada / banner definitivo: volta ao passo 1 com o
 *   banner (o bloco de contexto é atualizado pela invalidação do detalhe; uma nova pré-visualização
 *   mostra os valores atuais);
 * - rede e banners não definitivos: ficam no passo atual (nova tentativa com a mesma chave);
 * - não encontrado: fecha (a página mostra o estado "não encontrado").
 */
export function reagirAErroNotaCredito(
  erro: ErroNotaCredito | null,
  passoAtual: PassoNotaCredito,
): ReacaoErroNotaCredito {
  if (!erro) return { acao: "ignorar" };
  switch (erro.tipo) {
    case "campo":
      return { acao: "campo", campo: erro.campo, mensagem: erro.mensagem, passo: "formulario" };
    case "excede":
    case "valores-alterados":
    case "chave-reutilizada":
      return { acao: "banner", mensagem: erro.mensagem, passo: "formulario", definitivo: false };
    case "banner":
      return {
        acao: "banner",
        mensagem: erro.mensagem,
        passo: erro.definitivo ? "formulario" : passoAtual,
        definitivo: erro.definitivo,
      };
    case "rede":
      return { acao: "banner", mensagem: erro.mensagem, passo: passoAtual, definitivo: false };
    case "nao-encontrado":
      return { acao: "fechar" };
  }
}

/**
 * Tentativa a usar depois de uma falha na emissão (CR-02 da revisão). NUNCA devolve `null`: o botão
 * "Emitir nota de crédito" continua no passo 2 depois de várias recusas não definitivas
 * (DATA_EMISSAO_ALTERADA, PROCESSO_ALTERADO_TENTE_NOVAMENTE, 409/422 sem campo) e, com a tentativa
 * nula, o clique não fazia nada.
 * - Recusa 4xx processada (`desfechoDefinitivo`): o backend procura a chave antes de qualquer
 *   recusa, por isso esta chave não tem NC associada. Gera-se uma chave NOVA para o MESMO conteúdo:
 *   a nova tentativa é um pedido novo, nunca a repetição de uma recusa.
 * - Falha ambígua (rede, 5xx, 401/403/408/429): mantém a chave e marca-a por resolver.
 */
export function tentativaDepoisDeFalhaNc(
  atual: TentativaEmissao,
  erro: unknown,
  gerar: () => string = gerarChaveIdempotencia,
): TentativaEmissao {
  if (desfechoDefinitivo(erro)) {
    return { pedido: atual.pedido, chave: gerar(), porResolver: false };
  }
  return marcarPorResolver(atual) ?? atual;
}
