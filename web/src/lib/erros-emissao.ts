// Phase 134 -- interpretação dos erros da emissão da Fatura-Recibo e das guardas de eliminação.
// Funções puras (sem React), testadas em erros-emissao.test.ts. Os ecrãs (planos 12/13) só
// decidem ONDE mostrar cada resultado; o texto vem daqui (copy do 134-UI-SPEC) ou da `message`
// do backend. Nunca se mostra o corpo bruto de uma resposta.

import { isApiError } from "@/lib/api";
import type { DocumentosFiscaisFiltros } from "@/types/faturacao";

export const COPY_DATA_PAGAMENTO =
  "Com a faturação ativa, a data do pagamento tem de ser a de hoje. Escolha a data de hoje ou deixe o campo vazio.";
export const COPY_NIF =
  "O cliente não tem um NIF válido. Corrija o NIF do cliente (9 dígitos, começa por 1 a 9) e tente de novo.";
export const COPY_NOME = "O nome do cliente deve ter entre 3 e 150 caracteres. Corrija o cliente e tente de novo.";
export const COPY_MORADA =
  "A morada do cliente é obrigatória e não pode ter mais de 100 caracteres. Corrija o cliente e tente de novo.";
export const COPY_LOCALIDADE =
  "A localidade do cliente não pode ter mais de 100 caracteres. Corrija o cliente e tente de novo.";
export const COPY_CHAVE_REUTILIZADA =
  "Este pedido já foi usado com valores diferentes. Reveja os dados e registe o pagamento de novo.";
export const COPY_REDE = "Não foi possível emitir a fatura-recibo. Verifique a ligação e tente novamente.";
export const COPY_FALLBACK_CAMPO = "Verifique os campos assinalados.";
export const COPY_SEM_PERMISSAO = "Não tem permissão para registar pagamentos.";

// Surface 5 (guardas 409): o backend devolve exatamente esta copy; estas constantes só servem de
// recurso se o corpo vier sem `message`.
export const COPY_GUARDA_PAGAMENTO = "Este pagamento tem uma fatura-recibo emitida e não pode ser apagado.";
export const COPY_GUARDA_CLIENTE = "Não é possível apagar este cliente porque tem documentos fiscais emitidos.";
export const COPY_GUARDA_PROCESSO = "Não é possível apagar este processo porque tem documentos fiscais emitidos.";
export const COPY_GUARDA_HONORARIO = "Não é possível apagar este honorário porque tem documentos fiscais emitidos.";

/**
 * Status que os pedidos da emissão (pré-visualização e registo COM chave) tratam inline, sem o
 * toast automático do `apiFetch` (IN-02 da revisão): 409/422 como antes, e os 5xx, que já
 * aparecem no diálogo/banner como falha ambígua -- com o toast eram reportados duas vezes.
 */
export const STATUS_INLINE_EMISSAO: readonly number[] = [409, 422, 500, 502, 503, 504];

const COPY_GUARDA_POR_CODIGO: Record<string, string> = {
  PAGAMENTO_FATURADO: COPY_GUARDA_PAGAMENTO,
  CLIENTE_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_CLIENTE,
  PROCESSO_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_PROCESSO,
  HONORARIO_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_HONORARIO,
};

export type CampoFormularioEmissao = "dataPagamento" | "metodo" | "retencaoPercentagem" | "valorPago";

export type CampoAdquirente = "nif" | "nome" | "morada" | "localidade";

export type ErroEmissao =
  | { tipo: "campo"; campo: CampoFormularioEmissao; mensagem: string }
  | { tipo: "adquirente"; campo: CampoAdquirente; mensagem: string }
  | { tipo: "chave-reutilizada"; mensagem: string }
  | { tipo: "banner"; codigo?: string; mensagem: string }
  | { tipo: "rede"; mensagem: string };

const COPY_ADQUIRENTE: Record<CampoAdquirente, string> = {
  nif: COPY_NIF,
  nome: COPY_NOME,
  morada: COPY_MORADA,
  localidade: COPY_LOCALIDADE,
};

const CAMPOS_FORMULARIO: readonly CampoFormularioEmissao[] = [
  "dataPagamento",
  "metodo",
  "retencaoPercentagem",
  "valorPago",
];

function mensagemDoCorpo(body: unknown): string | undefined {
  if (!body || typeof body !== "object") return undefined;
  const m = (body as { message?: unknown }).message;
  return typeof m === "string" && m.trim() ? m : undefined;
}

function eCampoAdquirente(campo: string | undefined): campo is CampoAdquirente {
  return campo === "nif" || campo === "nome" || campo === "morada" || campo === "localidade";
}

function eCampoFormulario(campo: string | undefined): campo is CampoFormularioEmissao {
  return campo !== undefined && (CAMPOS_FORMULARIO as readonly string[]).includes(campo);
}

/**
 * Converte o erro da pré-visualização ou do registo de um pagamento faturado no que o formulário
 * deve mostrar. Devolve `null` quando não há nada a mostrar inline: 401 (o `useMe` trata da
 * sessão) e os status fora de `semToastParaStatus` (o `apiFetch` já mostrou o toast). O 403 é um
 * banner (WR-04 da revisão): o `apiFetch` NÃO mostra toast para 401/403, por isso sem ele o clique
 * em "Registar pagamento" não dava nenhum sinal.
 */
export function interpretarErroEmissao(error: unknown): ErroEmissao | null {
  if (!isApiError(error)) {
    // fetch() rejeita com TypeError quando o pedido não chega ao servidor.
    return { tipo: "rede", mensagem: COPY_REDE };
  }
  if (error.status === 401) return null;
  if (error.status === 403) return { tipo: "banner", mensagem: COPY_SEM_PERMISSAO };
  if (error.status >= 500) {
    // IN-02 da revisão: o 503 com `code` é uma recusa do próprio backend (FATURACAO_OCUPADA,
    // SERIE_INDISPONIVEL) com mensagem para o utilizador; continua a ser "rede" (repetir com a
    // mesma chave é o comportamento certo), mas com o texto do backend.
    const doBackend = error.status === 503 && error.code ? mensagemDoCorpo(error.body) : undefined;
    return { tipo: "rede", mensagem: doBackend ?? COPY_REDE };
  }
  if (error.status !== 409 && error.status !== 422) return null;

  const mensagem = mensagemDoCorpo(error.body);

  if (error.code === "DATA_PAGAMENTO_RETROATIVA") {
    return { tipo: "campo", campo: "dataPagamento", mensagem: COPY_DATA_PAGAMENTO };
  }
  if (error.code === "ADQUIRENTE_INCOMPLETO" && eCampoAdquirente(error.campo)) {
    return { tipo: "adquirente", campo: error.campo, mensagem: COPY_ADQUIRENTE[error.campo] };
  }
  if (error.code === "CHAVE_REUTILIZADA") {
    return { tipo: "chave-reutilizada", mensagem: COPY_CHAVE_REUTILIZADA };
  }
  if (error.status === 422 && eCampoFormulario(error.campo)) {
    return { tipo: "campo", campo: error.campo, mensagem: mensagem ?? COPY_FALLBACK_CAMPO };
  }

  const banner: ErroEmissao = { tipo: "banner", mensagem: mensagem ?? COPY_FALLBACK_CAMPO };
  if (error.code) banner.codigo = error.code;
  return banner;
}

/**
 * O pedido de emissão teve um desfecho DEFINITIVO no backend (CR-02 da revisão)? Só uma recusa
 * 4xx processada conta: o backend procura a chave de idempotência antes de qualquer outra recusa,
 * por isso um 4xx garante que esta chave não tem pagamento associado. Rede em baixo, 5xx (incluindo
 * o 504 do proxy, com o backend ainda a trabalhar), 401/403 (o pedido nem chegou à emissão), 408 e
 * 429 deixam o desfecho de uma tentativa anterior por resolver.
 */
export function desfechoDefinitivo(error: unknown): boolean {
  if (!isApiError(error)) return false;
  const { status } = error;
  return status >= 400 && status < 500 && status !== 401 && status !== 403 && status !== 408 && status !== 429;
}

/**
 * Mensagem inline de uma guarda de eliminação (409 PAGAMENTO_FATURADO ou *_COM_DOCUMENTOS_FISCAIS,
 * Surface 5), ou `null` para qualquer outro erro (que segue o tratamento habitual).
 */
export function mensagemGuardaFiscal(error: unknown): string | null {
  if (!isApiError(error) || error.status !== 409 || !error.code) return null;
  const copy = COPY_GUARDA_POR_CODIGO[error.code];
  if (!copy && !error.code.endsWith("_COM_DOCUMENTOS_FISCAIS")) return null;
  return mensagemDoCorpo(error.body) ?? copy ?? COPY_FALLBACK_CAMPO;
}

const ORDEM_FILTROS = ["clienteId", "de", "ate", "tipo", "estado"] as const;

/** Query string de GET /documentos-fiscais, com ordem fixa; filtros vazios são omitidos. */
export function construirQueryDocumentosFiscais(filtros: DocumentosFiscaisFiltros): string {
  const params = new URLSearchParams();
  for (const chave of ORDEM_FILTROS) {
    const valor = filtros[chave]?.trim();
    if (valor) params.append(chave, valor);
  }
  params.append("page", String(filtros.page));
  params.append("size", String(filtros.size));
  return params.toString();
}
