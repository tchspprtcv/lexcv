// Phase 136 -- comunicação eFatura (adaptador simulado). Funções puras (sem React), testadas em
// comunicacao-fiscal.test.ts: apresentação de cada estado (rótulo, variante neutra, ícone,
// descrição longa), regra de polling, visibilidade do "Reprocessar comunicação", interpretação
// dos erros do reprocessamento e a copy vinculativa do 136-UI-SPEC. Os componentes (136-08) só
// compõem; nunca re-derivam estados nem inventam texto.
//
// Regra da UI-SPEC: "Aceite (simulação)" nunca é parafraseado como autorização, aprovação ou
// validação; nenhum estado usa a cor de acento nem verde/âmbar/vermelho (a gravidade vem do texto).

import { isApiError } from "@/lib/api";
import type { AmbienteFiscal, ComunicacaoFiscal, EstadoComunicacaoFiscal } from "@/types/faturacao";

/** Chave do ícone lucide; o componente faz o mapeamento (a lib fica sem React). */
export type IconeEstadoComunicacao = "Clock" | "Info" | "CircleSlash" | "TriangleAlert";

/** Variantes neutras do Badge: nunca "default" (acento). */
export type VarianteEstadoComunicacao = "outline" | "secondary";

export interface ApresentacaoEstadoComunicacao {
  rotulo: string;
  variante: VarianteEstadoComunicacao;
  icone: IconeEstadoComunicacao;
  /** Descrição longa (title do badge e linha de ajuda) para quem pode reprocessar. */
  descricao: string;
  /** Mesma descrição para quem NÃO tem `financeiro:edit` exato. */
  descricaoSemPermissao: string;
}

const DESCRICAO_PENDENTE = "Comunicação à administração fiscal ainda não efetuada. Será tentada em segundo plano.";
const DESCRICAO_ACEITE =
  "Aceite pelo serviço de simulação. Não foi comunicado à administração fiscal e não tem validade fiscal.";
const PODE_REPROCESSAR = "Pode reprocessar a comunicação.";
const PECA_PERMISSAO = "Peça a um utilizador com permissão para reprocessar.";
const BASE_REJEITADO = "O documento foi recusado na validação do formato.";
const BASE_ERRO = "A comunicação falhou após várias tentativas.";
// WR-04 (136): o documento é imutável e o reprocessamento reenvia os mesmos dados, por isso uma
// rejeição de formato quase nunca muda ao reprocessar. A copy não pode prometer que o resolve.
const REJEITADO_COM_PERMISSAO =
  "Veja a última falha antes de reprocessar: se a causa estiver nos dados do documento, o resultado será o mesmo.";
const REJEITADO_SEM_PERMISSAO = "Veja a última falha. Para reprocessar, peça a um utilizador com permissão.";

/** Tabela de badges do 136-UI-SPEC (exaustiva: o compilador obriga a cobrir cada estado). */
export const ESTADOS_COMUNICACAO: Record<EstadoComunicacaoFiscal, ApresentacaoEstadoComunicacao> = {
  PENDENTE: {
    rotulo: "Pendente",
    variante: "outline",
    icone: "Clock",
    descricao: DESCRICAO_PENDENTE,
    descricaoSemPermissao: DESCRICAO_PENDENTE,
  },
  ACEITE_SIMULADO: {
    rotulo: "Aceite (simulação)",
    variante: "outline",
    icone: "Info",
    descricao: DESCRICAO_ACEITE,
    descricaoSemPermissao: DESCRICAO_ACEITE,
  },
  REJEITADO: {
    rotulo: "Rejeitado",
    variante: "secondary",
    icone: "CircleSlash",
    descricao: `${BASE_REJEITADO} ${REJEITADO_COM_PERMISSAO}`,
    descricaoSemPermissao: `${BASE_REJEITADO} ${REJEITADO_SEM_PERMISSAO}`,
  },
  ERRO: {
    rotulo: "Erro",
    variante: "secondary",
    icone: "TriangleAlert",
    descricao: `${BASE_ERRO} ${PODE_REPROCESSAR}`,
    descricaoSemPermissao: `${BASE_ERRO} ${PECA_PERMISSAO}`,
  },
};

const DESCRICAO_DESCONHECIDO = "O estado da comunicação deste documento não é reconhecido.";

/** Recurso neutro para qualquer valor que o backend envie e este build não conheça. */
export const ESTADO_DESCONHECIDO: ApresentacaoEstadoComunicacao = {
  rotulo: "Estado desconhecido",
  variante: "outline",
  icone: "Info",
  descricao: DESCRICAO_DESCONHECIDO,
  descricaoSemPermissao: DESCRICAO_DESCONHECIDO,
};

function ehEstadoConhecido(estado: string | null | undefined): estado is EstadoComunicacaoFiscal {
  return typeof estado === "string" && Object.prototype.hasOwnProperty.call(ESTADOS_COMUNICACAO, estado);
}

export function apresentacaoEstadoComunicacao(estado: string | null | undefined): ApresentacaoEstadoComunicacao {
  return ehEstadoConhecido(estado) ? ESTADOS_COMUNICACAO[estado] : ESTADO_DESCONHECIDO;
}

/** Descrição longa; em ERRO/REJEITADO depende do gate exato `podeReprocessarComunicacao`. */
export function descricaoEstadoComunicacao(estado: string | null | undefined, podeReprocessar: boolean): string {
  const apresentacao = apresentacaoEstadoComunicacao(estado);
  return podeReprocessar ? apresentacao.descricao : apresentacao.descricaoSemPermissao;
}

/** Intervalo de atualização enquanto a comunicação está pendente (136-UI-SPEC "Short-lived updates"). */
export const INTERVALO_ATUALIZACAO_MS = 15000;

/** `refetchInterval` do detalhe: 15 s só enquanto o estado visível é PENDENTE. */
export function intervaloAtualizacaoComunicacao(estado: string | null | undefined): number | false {
  return estado === "PENDENTE" ? INTERVALO_ATUALIZACAO_MS : false;
}

/** `refetchInterval` da lista: 15 s só se alguma linha visível estiver PENDENTE. */
export function intervaloAtualizacaoListaComunicacao(
  linhas: readonly { estadoComunicacao: string | null }[] | null | undefined,
): number | false {
  return linhas?.some((l) => l.estadoComunicacao === "PENDENTE") ? INTERVALO_ATUALIZACAO_MS : false;
}

/**
 * O botão "Reprocessar comunicação" só existe quando TUDO se verifica: gate exato
 * (`podeReprocessarComunicacao`), estado ERRO ou REJEITADO, e modo SIMULADO reportado pelo
 * backend. Caso contrário não é renderizado (sem botão desativado nem dica).
 */
export function mostrarReprocessar(
  podeReprocessar: boolean,
  comunicacao: Pick<ComunicacaoFiscal, "estado"> | null | undefined,
  modo: string | null | undefined,
): boolean {
  if (!podeReprocessar || modo !== "SIMULADO" || !comunicacao) return false;
  return comunicacao.estado === "ERRO" || comunicacao.estado === "REJEITADO";
}

/**
 * WR-07 (136): o banner "Modo simulado" é uma divulgação de segurança, por isso falha FECHADO no
 * sentido oposto ao do botão: aparece enquanto o estado carrega, quando o pedido falha ou quando o
 * campo falta (ou vem vazio), e só desaparece se o backend reportar EXPLICITAMENTE outro modo (que
 * este build nem consegue arrancar).
 */
export function mostrarBannerModoSimulado(modoComunicacao: string | null | undefined): boolean {
  const modo = typeof modoComunicacao === "string" ? modoComunicacao.trim() : "";
  return modo === "" || modo === "SIMULADO";
}

// ---------------------------------------------------------------------------------------------
// Erros do reprocessamento (Surface 2, tabela "Inline errors").

export const COPY_REPROCESSAR_ESTADO_INVALIDO =
  "Este documento já não está em erro ou rejeitado. Atualizámos o estado; verifique antes de tentar de novo.";
export const COPY_REPROCESSAR_MODO = "O reprocessamento não está disponível neste ambiente.";
export const COPY_REPROCESSAR_NAO_ENCONTRADO = "Documento fiscal não encontrado";
export const COPY_REPROCESSAR_INDISPONIVEL =
  "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.";
export const COPY_REPROCESSAR_REDE =
  "Não foi possível reprocessar a comunicação. Verifique a ligação e tente novamente.";

export interface ErroReprocessar {
  mensagem: string;
  /** Recusa processada pelo backend: o "Fechar sem reprocessar" passa a "Fechar". */
  definitivo: boolean;
  /** 404: fecha o diálogo; a página mostra o estado de não encontrado. */
  fecharDialogo: boolean;
  /** Voltar a ler o documento (o estado mudou entretanto). */
  refrescar: boolean;
}

/**
 * Traduz o erro do POST de reprocessamento na copy fixa da UI-SPEC (nunca o texto do backend).
 * 401/403 devolvem null: o `apiFetch` já trata (redireção / acesso negado).
 */
export function interpretarErroReprocessar(error: unknown): ErroReprocessar | null {
  const rede: ErroReprocessar = {
    mensagem: COPY_REPROCESSAR_REDE,
    definitivo: false,
    fecharDialogo: false,
    refrescar: false,
  };
  if (!isApiError(error)) return rede;
  if (error.status === 401 || error.status === 403) return null;
  if (error.status === 404) {
    return { mensagem: COPY_REPROCESSAR_NAO_ENCONTRADO, definitivo: true, fecharDialogo: true, refrescar: true };
  }
  if (error.status === 409 || error.status === 422) {
    if (error.code === "COMUNICACAO_ESTADO_INVALIDO") {
      return { mensagem: COPY_REPROCESSAR_ESTADO_INVALIDO, definitivo: true, fecharDialogo: false, refrescar: true };
    }
    return { mensagem: COPY_REPROCESSAR_MODO, definitivo: true, fecharDialogo: false, refrescar: false };
  }
  if (error.status === 503) {
    return { mensagem: COPY_REPROCESSAR_INDISPONIVEL, definitivo: false, fecharDialogo: false, refrescar: false };
  }
  return rede;
}

// ---------------------------------------------------------------------------------------------
// Copy vinculativa do 136-UI-SPEC (Copywriting Contract) usada pelos componentes da 136-08.

export const ROTULO_AMBIENTE: Record<AmbienteFiscal, string> = {
  SIMULADO: "Teste (simulado)",
};

export const COPY_CARD_TITULO = "Comunicação fiscal";
export const COPY_IUD_TESTE = "Ambiente de teste — sem validade fiscal";
export const COPY_MODO_SIMULADO_LEAD = "Modo simulado.";
export const COPY_MODO_SIMULADO_TEXTO =
  "Os documentos são comunicados a um serviço de simulação, não à administração fiscal (DNRE). " +
  "Não têm validade fiscal. Continue a emitir os seus documentos válidos no software de faturação homologado.";
export const COPY_SEM_COMUNICACAO = "A comunicação deste documento ainda não foi registada.";
export const COPY_SEM_TENTATIVAS = "Ainda sem tentativas";
export const COPY_IUD_A_GERAR = "A gerar...";
export const COPY_TENTATIVAS_AJUDA = "A comunicação é retentada automaticamente até 8 vezes.";
export const COPY_ULTIMA_FALHA = "Última falha";
export const COPY_REPROCESSAR = "Reprocessar comunicação";
export const COPY_REPROCESSAR_A_DECORRER = "A reprocessar...";
export const COPY_REPROCESSAR_SUCESSO = "Comunicação reposta como pendente.";

// Diálogo de confirmação (Surface 2).
export const COPY_DIALOGO_DESCRICAO =
  "A comunicação deste documento será reposta como pendente e voltará a ser tentada em segundo plano. " +
  "O documento não é alterado.";
export const COPY_DIALOGO_CONTADOR = "O contador de tentativas volta a zero.";
export const COPY_DIALOGO_REJEITADO =
  "Se o documento foi rejeitado por um problema nos dados, o resultado pode ser o mesmo.";
export const COPY_DIALOGO_FECHAR_SEM_REPROCESSAR = "Fechar sem reprocessar";
export const COPY_DIALOGO_FECHAR = "Fechar";
export const COPY_DIALOGO_FECHAR_SR = "Fechar reprocessamento da comunicação";
