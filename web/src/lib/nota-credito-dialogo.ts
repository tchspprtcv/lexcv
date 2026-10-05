// Phase 135 (NCRD-01, NCRD-02; 135-UI-SPEC Surface 1): lógica pura do diálogo "Emitir Nota de
// Crédito" -- para que passo o diálogo vai em cada erro e se o erro é definitivo (o que troca
// "Fechar sem emitir" por "Fechar" no passo 1). A chave de idempotência pertence ao conteúdo
// `{ documentoOrigemId, ...pedido }` (ver o teste) e o seu destino depois de uma falha segue o
// padrão da 134 (`desfechoDefinitivo` ? descartar : `marcarPorResolver`). Fica fora do componente
// para ser testada com vitest (o componente só liga isto ao React).

import { desfechoDefinitivo, type CampoNotaCredito, type ErroNotaCredito } from "@/lib/erros-emissao";
import {
  gerarChaveIdempotencia,
  marcarPorResolver,
  pedidoCanonico,
  tentativaParaPedido,
  type TentativaEmissao,
} from "@/lib/idempotencia";

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

// ---------------------------------------------------------------------------------------------
// WR-03 da revisão: tentativas por resolver FORA do componente.
//
// O `NotaCreditoDialog` só é montado enquanto a FR tem valor creditável e a faturação não está
// desligada, e as duas condições mudam com a invalidação que segue cada tentativa de emissão. Uma
// chave por resolver guardada só no estado do componente perdia-se nesse desmontar (e também numa
// navegação ou recarregamento): o mesmo pedido levava uma chave nova e uma PARCIAL que tivesse
// feito commit podia ser emitida duas vezes. As tentativas por resolver ficam aqui, por FR de
// origem e por conteúdo do pedido, em memória e no `sessionStorage` (sobrevive a navegações e
// recarregamentos no mesmo separador; falhas do armazenamento nunca chegam à UI).

const CHAVE_ARMAZENAMENTO = "lexcv:nota-credito:tentativas-por-resolver";
/** Limite por FR: as mais antigas saem primeiro. */
const MAXIMO_POR_DOCUMENTO = 10;

type TentativasPorDocumento = Record<string, TentativaEmissao[]>;

let memoria: TentativasPorDocumento | null = null;

function eTentativa(v: unknown): v is TentativaEmissao {
  if (!v || typeof v !== "object") return false;
  const t = v as Record<string, unknown>;
  return typeof t.pedido === "string" && typeof t.chave === "string" && t.porResolver === true;
}

function armazenamento(): Storage | null {
  try {
    return typeof window !== "undefined" && window.sessionStorage ? window.sessionStorage : null;
  } catch {
    return null;
  }
}

function carregar(): TentativasPorDocumento {
  if (memoria) return memoria;
  const resultado: TentativasPorDocumento = {};
  try {
    const bruto = armazenamento()?.getItem(CHAVE_ARMAZENAMENTO);
    const lido: unknown = bruto ? JSON.parse(bruto) : null;
    if (lido && typeof lido === "object" && !Array.isArray(lido)) {
      for (const [doc, lista] of Object.entries(lido as Record<string, unknown>)) {
        if (Array.isArray(lista)) {
          const validas = lista.filter(eTentativa);
          if (validas.length > 0) resultado[doc] = validas;
        }
      }
    }
  } catch {
    // Armazenamento corrompido ou inacessível: começa vazio.
  }
  memoria = resultado;
  return resultado;
}

function gravar(tentativas: TentativasPorDocumento) {
  memoria = tentativas;
  try {
    armazenamento()?.setItem(CHAVE_ARMAZENAMENTO, JSON.stringify(tentativas));
  } catch {
    // Quota/serialização: a cópia em memória continua a servir neste separador.
  }
}

/** Tentativa para `pedido` sobre a FR: reutiliza a chave por resolver guardada para este conteúdo. */
export function tentativaParaPedidoNc(
  documentoOrigemId: string,
  pedido: unknown,
  atual: TentativaEmissao | null,
  gerar: () => string = gerarChaveIdempotencia,
): TentativaEmissao {
  const canonico = pedidoCanonico(pedido);
  const guardada = (carregar()[documentoOrigemId] ?? []).find((t) => t.pedido === canonico);
  return tentativaParaPedido(guardada ?? atual, pedido, gerar);
}

/**
 * Regista o estado de uma tentativa depois de uma resposta: por resolver fica guardada (sobrevive
 * ao desmontar do diálogo); resolvida (sucesso ou recusa definitiva) sai do armazenamento.
 */
export function lembrarTentativaNc(documentoOrigemId: string, tentativa: TentativaEmissao, resolvida = false) {
  const todas = { ...carregar() };
  const outras = (todas[documentoOrigemId] ?? []).filter((t) => t.pedido !== tentativa.pedido);
  const lista = tentativa.porResolver && !resolvida ? [...outras, tentativa].slice(-MAXIMO_POR_DOCUMENTO) : outras;
  if (lista.length > 0) {
    todas[documentoOrigemId] = lista;
  } else {
    delete todas[documentoOrigemId];
  }
  gravar(todas);
}

/** Só para testes: esquece a cópia em memória (o `sessionStorage` é relido na próxima leitura). */
export function reiniciarTentativasNcParaTestes() {
  memoria = null;
}
