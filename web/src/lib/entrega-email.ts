// Phase 137 -- entrega dos documentos fiscais por email. Funções puras (sem React), testadas em
// entrega-email.test.ts: apresentação de cada estado (rótulo, variante neutra, ícone, descrição
// longa), regra de polling, visibilidade e rótulo do "Reenviar email", interpretação dos erros do
// reenvio, das descargas PDF/XML e da exportação mensal, e a copy vinculativa do 137-UI-SPEC.
//
// "Frontend burro" (137-UI-SPEC): o backend calcula o estado apresentado (incluindo
// NAO_CONFIGURADO), o destinatário, as tentativas e se o reenvio é permitido (`reenviavel`); aqui
// só se apresenta o que chega, nunca se re-deriva.
//
// Regras da UI-SPEC: "Enviado" nunca é parafraseado como entrega, receção ou leitura confirmadas;
// nenhum estado usa a cor de acento nem verde/âmbar/vermelho (a gravidade vem do texto); nenhuma
// copy fala de autorização, aprovação ou validação.

import { isApiError } from "@/lib/api";
import { INTERVALO_ATUALIZACAO_MS } from "@/lib/comunicacao-fiscal";
import type { EntregaEmail, EstadoEntregaEmail } from "@/types/faturacao";

/** Chave do ícone lucide; o componente faz o mapeamento (a lib fica sem React). */
export type IconeEntregaEmail = "MailX" | "MailMinus" | "UserX" | "Clock" | "MailCheck" | "TriangleAlert" | "Info";

/** Variantes neutras do Badge: nunca "default" (acento). */
export type VarianteEntregaEmail = "outline" | "secondary";

export interface ApresentacaoEntregaEmail {
  rotulo: string;
  variante: VarianteEntregaEmail;
  icone: IconeEntregaEmail;
  /** Descrição longa; em FALHOU é a variante curta, sem contagem (ver `descricaoEntregaEmail`). */
  descricao: string;
}

const BASE_FALHOU = "O envio do email falhou";
const PODE_REENVIAR = "Pode reenviar o email.";

/** Tabela de badges do 137-UI-SPEC (exaustiva: o compilador obriga a cobrir cada estado). */
export const ESTADOS_ENTREGA_EMAIL: Record<EstadoEntregaEmail, ApresentacaoEntregaEmail> = {
  NAO_CONFIGURADO: {
    rotulo: "Não configurado",
    variante: "outline",
    icone: "MailX",
    descricao:
      "O servidor de email não está configurado nesta instalação. O documento foi emitido normalmente, mas não é " +
      "enviado por email. Peça ao administrador da instalação para configurar o servidor de email.",
  },
  DESLIGADO: {
    rotulo: "Desligado",
    variante: "outline",
    icone: "MailMinus",
    descricao: "O envio automático por email está desligado nas definições de faturação do escritório.",
  },
  SEM_EMAIL: {
    rotulo: "Sem email do cliente",
    variante: "outline",
    icone: "UserX",
    descricao: "O cliente não tem email registado. Adicione um email na ficha do cliente para poder enviar o documento.",
  },
  PENDENTE: {
    rotulo: "Pendente",
    variante: "outline",
    icone: "Clock",
    descricao: "O email será enviado em segundo plano, com o PDF e o XML em anexo.",
  },
  ENVIADO: {
    rotulo: "Enviado",
    variante: "outline",
    icone: "MailCheck",
    descricao: "Email enviado ao cliente com o PDF e o XML em anexo. A receção pelo cliente não é confirmada.",
  },
  FALHOU: {
    rotulo: "Falhou",
    variante: "secondary",
    icone: "TriangleAlert",
    descricao: `${BASE_FALHOU}.`,
  },
};

/** Recurso neutro para qualquer valor que o backend envie e este build não conheça. */
export const ESTADO_ENTREGA_DESCONHECIDO: ApresentacaoEntregaEmail = {
  rotulo: "Estado desconhecido",
  variante: "outline",
  icone: "Info",
  descricao: "O estado do envio por email deste documento não é reconhecido.",
};

function ehEstadoConhecido(estado: string | null | undefined): estado is EstadoEntregaEmail {
  return typeof estado === "string" && Object.prototype.hasOwnProperty.call(ESTADOS_ENTREGA_EMAIL, estado);
}

export function apresentacaoEntregaEmail(estado: string | null | undefined): ApresentacaoEntregaEmail {
  return ehEstadoConhecido(estado) ? ESTADOS_ENTREGA_EMAIL[estado] : ESTADO_ENTREGA_DESCONHECIDO;
}

/** "1 tentativa" para n = 1, "{n} tentativas" nos restantes casos (nunca o literal "(s)"). */
export function textoTentativas(n: number): string {
  return n === 1 ? "1 tentativa" : `${n} tentativas`;
}

export interface ContextoDescricaoEntrega {
  /** Gate exato `podeReenviarEmail(permissions)` (financeiro:edit). */
  podeReenviar: boolean;
  /** `entregaEmail.reenviavel`, tal como chega do backend. */
  reenviavel: boolean;
  /** `entregaEmail.tentativas`; ausente na lista (a linha não traz contagem). */
  tentativas?: number | null;
}

/**
 * Descrição longa (title do badge e linha sob "Estado"). Em FALHOU conta as tentativas reais
 * ("após 1 tentativa." / "após {n} tentativas.") e só acrescenta "Pode reenviar o email." com o
 * gate exato E `reenviavel`; sem contagem (badge da lista) devolve "O envio do email falhou.".
 */
export function descricaoEntregaEmail(estado: string | null | undefined, contexto: ContextoDescricaoEntrega): string {
  if (estado !== "FALHOU") return apresentacaoEntregaEmail(estado).descricao;
  const { tentativas } = contexto;
  if (typeof tentativas !== "number") return ESTADOS_ENTREGA_EMAIL.FALHOU.descricao;
  const base = `${BASE_FALHOU} após ${textoTentativas(tentativas)}.`;
  return contexto.podeReenviar && contexto.reenviavel ? `${base} ${PODE_REENVIAR}` : base;
}

/**
 * O botão "Reenviar email" / "Enviar email" só existe com o gate exato (`podeReenviarEmail`) E
 * `reenviavel === true` vindo do backend. Caso contrário não é renderizado (sem botão desativado).
 */
export function mostrarReenviar(
  podeReenviar: boolean,
  entregaEmail: Pick<EntregaEmail, "reenviavel"> | null | undefined,
): boolean {
  return podeReenviar && entregaEmail?.reenviavel === true;
}

export const COPY_REENVIAR = "Reenviar email";
export const COPY_ENVIAR = "Enviar email";

const ROTULOS_REENVIAR: Record<EstadoEntregaEmail, string> = {
  NAO_CONFIGURADO: COPY_REENVIAR,
  DESLIGADO: COPY_REENVIAR,
  SEM_EMAIL: COPY_ENVIAR,
  PENDENTE: COPY_REENVIAR,
  ENVIADO: COPY_REENVIAR,
  FALHOU: COPY_REENVIAR,
};

/** Rótulo do gatilho e do título/botão do diálogo: "Enviar email" só em SEM_EMAIL. */
export function rotuloReenviar(estado: string | null | undefined): string {
  return ehEstadoConhecido(estado) ? ROTULOS_REENVIAR[estado] : COPY_REENVIAR;
}

/** Intervalo de atualização enquanto algo está pendente (o mesmo da Phase 136). */
export const INTERVALO_ATUALIZACAO_ENTREGA_MS = INTERVALO_ATUALIZACAO_MS;

/** O detalhe volta a ler a cada 15 s enquanto a comunicação OU a entrega estão PENDENTE. */
export function deveSondarEntrega(
  comunicacaoEstado: string | null | undefined,
  entregaEstado: string | null | undefined,
): boolean {
  return comunicacaoEstado === "PENDENTE" || entregaEstado === "PENDENTE";
}

// ---------------------------------------------------------------------------------------------
// Erros do reenvio (Surface 1d, tabela "Inline errors").

export const COPY_REENVIO_ESTADO_INVALIDO =
  "Este email já está a ser enviado. Atualizámos o estado; verifique antes de tentar de novo.";
export const COPY_REENVIO_SEM_EMAIL_CLIENTE =
  "O cliente continua sem email registado. Adicione um email na ficha do cliente e tente de novo.";
export const COPY_REENVIO_SMTP_NAO_CONFIGURADO =
  "O servidor de email não está configurado nesta instalação. Peça ao administrador da instalação para " +
  "configurar o servidor de email.";
export const COPY_REENVIO_ENVIO_DESLIGADO = "O envio automático por email está desligado nas definições de faturação.";
export const COPY_REENVIO_COMUNICACAO_NAO_ACEITE = "O documento só pode ser enviado depois de aceite na comunicação.";
export const COPY_REENVIO_NAO_ENCONTRADO = "Documento fiscal não encontrado";
export const COPY_REENVIO_INDISPONIVEL =
  "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.";
export const COPY_REENVIO_REDE = "Não foi possível reenviar o email. Verifique a ligação e tente novamente.";

export interface ErroReenvio {
  mensagem: string;
  /** Recusa processada pelo backend: o "Fechar sem reenviar" passa a "Fechar". */
  definitivo: boolean;
  /** 404: fecha o diálogo; a página mostra o estado de não encontrado. */
  fecharDialogo: boolean;
  /** Voltar a ler o documento (o estado mudou entretanto). */
  refrescar: boolean;
}

const RECUSAS_REENVIO: Record<string, ErroReenvio> = {
  ENTREGA_ESTADO_INVALIDO: {
    mensagem: COPY_REENVIO_ESTADO_INVALIDO,
    definitivo: true,
    fecharDialogo: false,
    refrescar: true,
  },
  SEM_EMAIL_CLIENTE: { mensagem: COPY_REENVIO_SEM_EMAIL_CLIENTE, definitivo: true, fecharDialogo: false, refrescar: false },
  SMTP_NAO_CONFIGURADO: {
    mensagem: COPY_REENVIO_SMTP_NAO_CONFIGURADO,
    definitivo: true,
    fecharDialogo: false,
    refrescar: true,
  },
  ENVIO_EMAIL_DESLIGADO: { mensagem: COPY_REENVIO_ENVIO_DESLIGADO, definitivo: true, fecharDialogo: false, refrescar: true },
  COMUNICACAO_NAO_ACEITE: {
    mensagem: COPY_REENVIO_COMUNICACAO_NAO_ACEITE,
    definitivo: true,
    fecharDialogo: false,
    refrescar: false,
  },
};

/**
 * Traduz o erro do POST de reenvio na copy fixa da UI-SPEC (nunca o texto do backend).
 * 401/403 devolvem null: o `apiFetch` já trata (redireção / acesso negado).
 */
export function interpretarErroReenvio(error: unknown): ErroReenvio | null {
  const rede: ErroReenvio = { mensagem: COPY_REENVIO_REDE, definitivo: false, fecharDialogo: false, refrescar: false };
  if (!isApiError(error)) return rede;
  if (error.status === 401 || error.status === 403) return null;
  if (error.status === 404) {
    return { mensagem: COPY_REENVIO_NAO_ENCONTRADO, definitivo: true, fecharDialogo: true, refrescar: true };
  }
  if ((error.status === 409 || error.status === 422) && error.code) {
    const recusa = Object.prototype.hasOwnProperty.call(RECUSAS_REENVIO, error.code)
      ? RECUSAS_REENVIO[error.code]
      : undefined;
    if (recusa) return { ...recusa };
  }
  if (error.status === 409) {
    return { ...RECUSAS_REENVIO.ENTREGA_ESTADO_INVALIDO };
  }
  if (error.status === 503) {
    return { mensagem: COPY_REENVIO_INDISPONIVEL, definitivo: false, fecharDialogo: false, refrescar: false };
  }
  return rede;
}

// ---------------------------------------------------------------------------------------------
// Erros das descargas PDF/XML (Surface 1a: toasts) e da exportação mensal (Surface 4: banner).

export const COPY_DESCARGA_NAO_ENCONTRADO = "Documento fiscal não encontrado.";
export const COPY_DESCARGA_PREPARAR =
  "Não foi possível preparar o ficheiro neste momento. Aguarde um momento e tente novamente.";
export const COPY_DESCARGA_REDE = "Não foi possível descarregar o ficheiro. Verifique a ligação e tente novamente.";

/** Status tratados pela própria descarga (o `apiFetch` não mostra o toast genérico). */
export const STATUS_SEM_TOAST_DESCARGA: readonly number[] = [404, 500, 502, 503, 504];

/** Copy do toast de erro de uma descarga; null em 401/403 (o `apiFetch` já trata). */
export function interpretarErroDescarga(error: unknown): string | null {
  if (!isApiError(error)) return COPY_DESCARGA_REDE;
  if (error.status === 401 || error.status === 403) return null;
  if (error.status === 404) return COPY_DESCARGA_NAO_ENCONTRADO;
  if (error.status === 503) return COPY_DESCARGA_PREPARAR;
  return COPY_DESCARGA_REDE;
}

export const COPY_EXPORTAR_MES_INVALIDO = "O mês escolhido não é válido. Escolha um mês até ao mês atual.";
export const COPY_EXPORTAR_INDISPONIVEL =
  "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.";
export const COPY_EXPORTAR_REDE = "Não foi possível exportar o ficheiro. Verifique a ligação e tente novamente.";

/** Copy do banner de erro da exportação mensal; null em 401/403 (o `apiFetch` já trata). */
export function interpretarErroExportacao(error: unknown): string | null {
  if (!isApiError(error)) return COPY_EXPORTAR_REDE;
  if (error.status === 401 || error.status === 403) return null;
  if (error.status === 422 && error.code === "MES_INVALIDO") return COPY_EXPORTAR_MES_INVALIDO;
  if (error.status === 503) return COPY_EXPORTAR_INDISPONIVEL;
  return COPY_EXPORTAR_REDE;
}

// ---------------------------------------------------------------------------------------------
// Copy vinculativa do 137-UI-SPEC (Copywriting Contract) usada pelos componentes seguintes.

export const COPY_CARD_ENTREGA_TITULO = "Entrega por email";
export const COPY_SEM_ENTREGA = "O envio por email deste documento ainda não foi registado.";
export const COPY_TENTATIVAS_ENTREGA_AJUDA = "O envio é tentado automaticamente até 5 vezes.";
export const COPY_SEM_TENTATIVAS_ENTREGA = "Ainda sem tentativas";
export const COPY_ULTIMA_FALHA_ENTREGA = "Última falha";

// Descargas (Surface 1a).
export const COPY_DESCARREGAR_PDF = "Descarregar PDF";
export const COPY_DESCARREGAR_XML = "Descarregar XML";
export const COPY_A_PREPARAR_PDF = "A preparar PDF...";
export const COPY_A_PREPARAR_XML = "A preparar XML...";

// Diálogo de reenvio (Surface 1d).
export const COPY_REENVIO_DESCRICAO =
  "O documento será enviado ao cliente com o PDF e o XML em anexo. O envio é feito em segundo plano e o " +
  "documento não é alterado.";
export const COPY_REENVIO_CONTADOR = "O contador de tentativas volta a zero.";
export const COPY_REENVIO_JA_ENVIADO = "Este documento já foi enviado. O cliente vai receber um novo email.";
export const COPY_REENVIO_EMAIL_ATUAL = "Será usado o email registado agora na ficha do cliente.";
export const COPY_REENVIO_CLIENTE_SEM_EMAIL = "O cliente não tem email na ficha";
export const COPY_REENVIO_FECHAR_SEM_REENVIAR = "Fechar sem reenviar";
export const COPY_REENVIO_FECHAR = "Fechar";
export const COPY_REENVIO_FECHAR_SR = "Fechar reenvio do email";
export const COPY_A_REENVIAR = "A reenviar...";
export const COPY_A_ENVIAR = "A enviar...";
export const COPY_REENVIO_SUCESSO = "Email colocado na fila de envio.";

// Exportação mensal (Surface 4).
export const COPY_EXPORTAR_MES = "Exportar mês";
export const COPY_EXPORTAR_CSV = "Exportar CSV";
export const COPY_EXPORTAR_SUCESSO = "Ficheiro CSV exportado.";
