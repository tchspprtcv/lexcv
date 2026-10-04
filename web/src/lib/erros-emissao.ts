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
export const COPY_CHAVE_REUTILIZADA =
  "Este pedido já foi usado com valores diferentes. Reveja os dados e registe o pagamento de novo.";
export const COPY_REDE = "Não foi possível emitir a fatura-recibo. Verifique a ligação e tente novamente.";
export const COPY_FALLBACK_CAMPO = "Verifique os campos assinalados.";

// Surface 5 (guardas 409): o backend devolve exatamente esta copy; estas constantes só servem de
// recurso se o corpo vier sem `message`.
export const COPY_GUARDA_PAGAMENTO = "Este pagamento tem uma fatura-recibo emitida e não pode ser apagado.";
export const COPY_GUARDA_CLIENTE = "Não é possível apagar este cliente porque tem documentos fiscais emitidos.";
export const COPY_GUARDA_PROCESSO = "Não é possível apagar este processo porque tem documentos fiscais emitidos.";
export const COPY_GUARDA_HONORARIO = "Não é possível apagar este honorário porque tem documentos fiscais emitidos.";

const COPY_GUARDA_POR_CODIGO: Record<string, string> = {
  PAGAMENTO_FATURADO: COPY_GUARDA_PAGAMENTO,
  CLIENTE_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_CLIENTE,
  PROCESSO_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_PROCESSO,
  HONORARIO_COM_DOCUMENTOS_FISCAIS: COPY_GUARDA_HONORARIO,
};

export type CampoFormularioEmissao = "dataPagamento" | "metodo" | "retencaoPercentagem" | "valorPago";

export type CampoAdquirente = "nif" | "nome" | "morada";

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
  return campo === "nif" || campo === "nome" || campo === "morada";
}

function eCampoFormulario(campo: string | undefined): campo is CampoFormularioEmissao {
  return campo !== undefined && (CAMPOS_FORMULARIO as readonly string[]).includes(campo);
}

/**
 * Converte o erro da pré-visualização ou do registo de um pagamento faturado no que o formulário
 * deve mostrar. Devolve `null` quando não há nada a mostrar inline: 401/403 e os status fora de
 * `semToastParaStatus` (o `apiFetch` já mostrou o toast).
 */
export function interpretarErroEmissao(error: unknown): ErroEmissao | null {
  if (!isApiError(error)) {
    // fetch() rejeita com TypeError quando o pedido não chega ao servidor.
    return { tipo: "rede", mensagem: COPY_REDE };
  }
  if (error.status === 401 || error.status === 403) return null;
  if (error.status >= 500) return { tipo: "rede", mensagem: COPY_REDE };
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
