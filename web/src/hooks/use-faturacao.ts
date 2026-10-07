import {
  type QueryClient,
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { apiFetch, apiFetchFicheiro, isApiError } from "@/lib/api";
import { intervaloAtualizacaoListaComunicacao } from "@/lib/comunicacao-fiscal";
import { deveSondarEntrega } from "@/lib/entrega-email";
import { construirQueryDocumentosFiscais, STATUS_INLINE_EMISSAO } from "@/lib/erros-emissao";
import { guardarFicheiro } from "@/lib/guardar-ficheiro";
import { hasPermission } from "@/lib/permissions";

import type {
  CodigoErroFaturacao,
  ConfiguracaoFiscal,
  ConfiguracaoFiscalPayload,
  DescargaPdfResposta,
  DocumentoFiscalDetalhe,
  DocumentosFiscaisFiltros,
  EmailAutomaticoPayload,
  EstadoEmissao,
  MotivoIsencao,
  NotaCreditoRequest,
  NotaCreditoResposta,
  PaginaDocumentosFiscais,
  PreVisualizacaoFatura,
  PreVisualizacaoNotaCredito,
  ReenviarEmailResposta,
  ReprocessarComunicacaoResposta,
  SerieFiscal,
} from "@/types/faturacao";
import type { PagamentoCreateRequest } from "@/types/financeiro";

// Hooks da aba "Faturação" (Phase 133). As queries recebem `enabled` -- quem chama passa
// `can.manage("financeiro")`, para que um utilizador sem permissão nunca dispare um 403. Isto é
// apenas UX: a autoridade é o `@PreAuthorize` de classe do controller de faturação no backend.
// As mutações suprimem o toast automático para os status que o ecrã renderiza inline (UI-SPEC).

export const FATURACAO_CONFIG_KEY = ["faturacao", "configuracao"] as const;
export const FATURACAO_SERIES_KEY = ["faturacao", "series"] as const;
export const FATURACAO_MOTIVOS_KEY = ["faturacao", "motivos-isencao"] as const;

export function useConfiguracaoFiscal(enabled: boolean) {
  return useQuery({
    queryKey: FATURACAO_CONFIG_KEY,
    queryFn: () => apiFetch<ConfiguracaoFiscal>("/faturacao/configuracao"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useSeriesFiscais(enabled: boolean) {
  return useQuery({
    queryKey: FATURACAO_SERIES_KEY,
    queryFn: () => apiFetch<SerieFiscal[]>("/faturacao/series"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useMotivosIsencao(enabled: boolean) {
  return useQuery({
    queryKey: FATURACAO_MOTIVOS_KEY,
    queryFn: () => apiFetch<MotivoIsencao[]>("/faturacao/motivos-isencao"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: Infinity,
  });
}

/**
 * Invalida a configuração e as séries depois de QUALQUER desfecho da mutação (`onSettled`, WR-04 da
 * revisão): um 409/422 (FATURACAO_JA_EMITIU, NIF_BLOQUEADO, FATURACAO_DESLIGADA,
 * CONFIGURACAO_FISCAL_CONCORRENTE) significa que o ecrã está desatualizado, e só invalidar no
 * sucesso deixava os cards a mostrar o estado que causou a recusa.
 */
function invalidarFaturacao(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: FATURACAO_CONFIG_KEY }),
    queryClient.invalidateQueries({ queryKey: FATURACAO_SERIES_KEY }),
  ]);
}

export function useGuardarConfiguracaoFiscal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ConfiguracaoFiscalPayload) =>
      apiFetch<ConfiguracaoFiscal>(
        "/faturacao/configuracao",
        { method: "PUT", body: JSON.stringify(payload) },
        { semToastParaStatus: [400, 409, 422] },
      ),
    onSettled: () => invalidarFaturacao(queryClient),
  });
}

export function useAtivarFaturacao() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<ConfiguracaoFiscal>(
        "/faturacao/ativar",
        { method: "POST" },
        { semToastParaStatus: [409, 422] },
      ),
    onSettled: () => invalidarFaturacao(queryClient),
  });
}

export function useDesativarFaturacao() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<ConfiguracaoFiscal>(
        "/faturacao/desativar",
        { method: "POST" },
        { semToastParaStatus: [409] },
      ),
    onSettled: () => invalidarFaturacao(queryClient),
  });
}

export function useEmailAutomatico() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: EmailAutomaticoPayload) =>
      apiFetch<ConfiguracaoFiscal>(
        "/faturacao/email-automatico",
        { method: "PUT", body: JSON.stringify(payload) },
        { semToastParaStatus: [409, 422] },
      ),
    onSettled: () => invalidarFaturacao(queryClient),
  });
}

export interface ErroFaturacao {
  codigo?: CodigoErroFaturacao;
  campo?: string;
  mensagem?: string;
  camposValidacao?: Record<string, string>;
}

function camposDeValidacao(body: unknown): Record<string, string> | undefined {
  if (!body || typeof body !== "object" || Array.isArray(body)) return undefined;
  const entradas = Object.entries(body as Record<string, unknown>);
  if (entradas.length === 0 || !entradas.every(([, v]) => typeof v === "string")) return undefined;
  return Object.fromEntries(entradas) as Record<string, string>;
}

/**
 * Converte um erro de mutação de faturação numa estrutura que os componentes mapeiam para copy
 * inline/campos sem analisar strings. Para um 400 de bean validation (sem `code`), devolve o
 * mapa campo -> mensagem em `camposValidacao`.
 */
export function mensagemErroFaturacao(error: unknown): ErroFaturacao {
  if (!isApiError(error)) return {};

  if (error.status === 400 && !error.code) {
    const campos = camposDeValidacao(error.body);
    if (campos && !("message" in campos)) return { camposValidacao: campos };
  }

  const body = error.body as { message?: unknown } | undefined;
  const mensagem = body && typeof body.message === "string" ? body.message : undefined;

  const resultado: ErroFaturacao = {};
  if (error.code) resultado.codigo = error.code as CodigoErroFaturacao;
  if (error.campo) resultado.campo = error.campo;
  if (mensagem) resultado.mensagem = mensagem;
  return resultado;
}

// ---------------------------------------------------------------------------------------------
// Phase 134 -- estado de emissão, pré-visualização e documentos fiscais (DocumentoFiscalController).
// Adições apenas: os hooks e chaves da Phase 133 acima ficam como estavam. Caminhos relativos ao
// apiFetch (o prefixo da API vem de NEXT_PUBLIC_API_BASE_PATH).

export const ESTADO_EMISSAO_KEY = ["faturacao", "estado-emissao"] as const;
export const DOCUMENTOS_FISCAIS_KEY = ["documentos-fiscais"] as const;

/** Autoridade exigida pelo backend para ler o estado de emissão e os documentos fiscais. */
export const PERMISSAO_LEITURA_FISCAL = "financeiro:view";

/**
 * Gate de leitura fiscal: exige EXATAMENTE `financeiro:view`, tal como o `@PreAuthorize` do
 * backend. O fallback do frontend (`hasScopedPermission`, edit/manage => view) não serve aqui:
 * para um papel personalizado com edit sem view o ecrã mostraria dados que o backend recusa com
 * 403 (ver 134-09-SUMMARY). Quem chama passa o resultado como `enabled` dos hooks abaixo.
 */
export function podeLerDocumentosFiscais(permissions: readonly string[] | undefined): boolean {
  return hasPermission(permissions, PERMISSAO_LEITURA_FISCAL);
}

/** Autoridade EXATA exigida pelo backend para registar um pagamento (e pré-visualizar a FR). */
export const PERMISSAO_REGISTO_PAGAMENTO = "financeiro:edit";

/**
 * Gate do registo de pagamentos (WR-04 da revisão): exige EXATAMENTE `financeiro:edit`, como o
 * `@PreAuthorize` de `POST /pagamentos` e de `POST /faturacao/pre-visualizacao`. O fallback do
 * frontend (manage => edit) mostraria um formulário que o backend recusa sempre com 403.
 */
export function podeRegistarPagamentos(permissions: readonly string[] | undefined): boolean {
  return hasPermission(permissions, PERMISSAO_REGISTO_PAGAMENTO);
}

/** Modo do cartão "Adicionar pagamento" (WR-03 da revisão). */
export type ModoFormularioPagamento = "sem-permissao" | "a-carregar" | "erro" | "ativa" | "desligada";

/**
 * Decide que formulário de pagamento mostrar. "Desconhecido" nunca cai no formulário antigo: só
 * `desligada` o mostra com o botão ativo; `a-carregar` mostra-o com o botão desativado
 * (134-UI-SPEC); `erro` mostra um estado de erro com "Tentar novamente". Com dados em cache, uma
 * nova leitura falhada não esconde o formulário (o backend volta a verificar ao registar).
 */
export function modoFormularioPagamento(
  permissions: readonly string[] | undefined,
  estado: { isError: boolean; data?: Pick<EstadoEmissao, "ativa"> | undefined },
): ModoFormularioPagamento {
  if (!podeRegistarPagamentos(permissions)) return "sem-permissao";
  if (estado.data) return estado.data.ativa ? "ativa" : "desligada";
  return estado.isError ? "erro" : "a-carregar";
}

/**
 * GET /faturacao/estado-emissao (financeiro:view OU financeiro:edit, WR-03 da revisão): se a
 * faturação está ativa e a taxa sugerida.
 */
export function useEstadoEmissao(enabled: boolean) {
  return useQuery({
    queryKey: ESTADO_EMISSAO_KEY,
    queryFn: () => apiFetch<EstadoEmissao>("/faturacao/estado-emissao"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

/** POST /faturacao/pre-visualizacao (financeiro:edit): nada é gravado; 409/422/5xx tratados inline. */
export function usePreVisualizacaoFaturacao() {
  return useMutation({
    mutationFn: (payload: PagamentoCreateRequest) =>
      apiFetch<PreVisualizacaoFatura>(
        "/faturacao/pre-visualizacao",
        { method: "POST", body: JSON.stringify(payload) },
        { semToastParaStatus: STATUS_INLINE_EMISSAO },
      ),
  });
}

/** GET /documentos-fiscais com filtros e paginação no servidor (D-17). */
export function useDocumentosFiscais(filtros: DocumentosFiscaisFiltros, enabled: boolean) {
  return useQuery({
    queryKey: [...DOCUMENTOS_FISCAIS_KEY, "list", filtros] as const,
    queryFn: () =>
      apiFetch<PaginaDocumentosFiscais>(`/documentos-fiscais?${construirQueryDocumentosFiscais(filtros)}`),
    enabled: enabled && typeof window !== "undefined",
    placeholderData: keepPreviousData,
    staleTime: 15_000,
    // Phase 136: atualiza a cada 15 s só enquanto alguma linha visível está PENDENTE (regra única
    // da lib, testada; IN-07 da revisão).
    refetchInterval: (query) => intervaloAtualizacaoListaComunicacao(query.state.data?.content),
    refetchIntervalInBackground: false,
  });
}

/** GET /documentos-fiscais/{id}: o 404 é um estado do ecrã (sem toast, sem novas tentativas). */
export function useDocumentoFiscal(id: string, enabled: boolean) {
  return useQuery({
    queryKey: [...DOCUMENTOS_FISCAIS_KEY, "detail", id] as const,
    queryFn: () =>
      apiFetch<DocumentoFiscalDetalhe>(
        `/documentos-fiscais/${encodeURIComponent(id)}`,
        {},
        { semToastParaStatus: [404] },
      ),
    enabled: enabled && Boolean(id) && typeof window !== "undefined",
    retry: (tentativas, error) => !(isApiError(error) && error.status === 404) && tentativas < 3,
    staleTime: 60_000,
    // Phase 136 + Phase 137: atualiza a cada 15 s só enquanto a comunicação OU a entrega estão PENDENTE.
    refetchInterval: (query) =>
      deveSondarEntrega(
        query.state.data?.comunicacao?.estado,
        query.state.data?.entregaEmail?.estado,
      )
        ? 15_000
        : false,
    refetchIntervalInBackground: false,
  });
}

// ---------------------------------------------------------------------------------------------
// Phase 136 -- Comunicação fiscal: gate exato e reprocessamento manual.

/** Autoridade EXATA exigida pelo backend para reprocessar a comunicação de um documento fiscal. */
export const PERMISSAO_REPROCESSAR_COMUNICACAO = "financeiro:edit";

/**
 * Gate do botão "Reprocessar comunicação": exige EXATAMENTE `financeiro:edit`, como o
 * `@PreAuthorize` do endpoint de reprocessamento (136-UI-SPEC checker
 * clarification). O fallback do frontend (manage => edit) mostraria um botão que o backend recusa.
 */
export function podeReprocessarComunicacao(permissions: readonly string[] | undefined): boolean {
  return hasPermission(permissions, PERMISSAO_REPROCESSAR_COMUNICACAO);
}

/** Status tratados inline no diálogo de reprocessamento (401/403 mantêm o comportamento do apiFetch). */
const STATUS_INLINE_REPROCESSAR: readonly number[] = [404, 409, 422, 500, 502, 503, 504];

/**
 * POST do reprocessamento da comunicação (financeiro:edit). Invalida em `onSettled`: um 409 significa
 * estado desatualizado, e o reprocessamento pode resolver uma notificação de falha.
 */
export function useReprocessarComunicacao(documentoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<ReprocessarComunicacaoResposta>(
        `/documentos-fiscais/${encodeURIComponent(documentoId)}/comunicacao/reprocessar`,
        { method: "POST" },
        { semToastParaStatus: STATUS_INLINE_REPROCESSAR },
      ),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: ["notificacoes"] }),
      ]);
    },
  });
}

// ---------------------------------------------------------------------------------------------
// Phase 135 -- Nota de Crédito: gate exato, pré-visualização e emissão. Caminhos relativos ao
// apiFetch. Os valores vêm sempre do backend ("frontend burro", 135-UI-SPEC).

/** Autoridade EXATA exigida pelo backend para pré-visualizar e emitir uma Nota de Crédito. */
export const PERMISSAO_EMISSAO_NOTA_CREDITO = "financeiro:manage";

/**
 * Gate do botão "Emitir Nota de Crédito": exige EXATAMENTE `financeiro:manage`, a autoridade do
 * `@PreAuthorize` dos endpoints de NC (CONTEXT "frontend hasScopedPermission exato"). É igual a
 * `hasScopedPermission(perms, "financeiro", "manage")`, porque não há ação mais forte do que
 * `manage` na cadeia de fallback; usar `hasPermission` deixa-o explícito, como os gates da 134.
 */
export function podeEmitirNotaCredito(permissions: readonly string[] | undefined): boolean {
  return hasPermission(permissions, PERMISSAO_EMISSAO_NOTA_CREDITO);
}

function caminhoNotasCredito(documentoId: string): string {
  return `/documentos-fiscais/${encodeURIComponent(documentoId)}/notas-credito`;
}

/** Status tratados inline no diálogo da NC: os da emissão e o 404 (documento inexistente). */
const STATUS_INLINE_NOTA_CREDITO: readonly number[] = [...STATUS_INLINE_EMISSAO, 404];

/** POST .../notas-credito/pre-visualizacao (financeiro:manage): nada é gravado. */
export function usePreVisualizacaoNotaCredito(documentoId: string) {
  return useMutation({
    mutationFn: (payload: NotaCreditoRequest) =>
      apiFetch<PreVisualizacaoNotaCredito>(
        `${caminhoNotasCredito(documentoId)}/pre-visualizacao`,
        { method: "POST", body: JSON.stringify(payload) },
        { semToastParaStatus: STATUS_INLINE_NOTA_CREDITO },
      ),
  });
}

/**
 * POST .../notas-credito (financeiro:manage). Invalida em `onSettled` (não só no sucesso): um erro
 * também pode significar estado desatualizado (NC já emitida com a mesma chave, valor creditável
 * alterado por outra NC, faturação desligada). O estorno muda o total pago do honorário, a conta
 * corrente e o KPI mensal, por isso essas caches também são invalidadas.
 */
export function useEmitirNotaCredito(documentoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: NotaCreditoRequest) =>
      apiFetch<NotaCreditoResposta>(
        caminhoNotasCredito(documentoId),
        { method: "POST", body: JSON.stringify(payload) },
        { semToastParaStatus: STATUS_INLINE_NOTA_CREDITO },
      ),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos"] }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "detail"] }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
        queryClient.invalidateQueries({ queryKey: ["dashboard", "kpis"] }),
      ]);
    },
  });
}

// ---------------------------------------------------------------------------------------------
// Phase 137 -- Entrega por email, descargas PDF/XML e exportação mensal.

/** Autoridade EXATA exigida pelo backend para reenviar o email de um documento fiscal. */
export const PERMISSAO_REENVIAR_EMAIL = "financeiro:edit";

/**
 * Gate do botão "Reenviar email": exige EXATAMENTE `financeiro:edit`, como o `@PreAuthorize`
 * do endpoint de reenvio (`POST /documentos-fiscais/{id}/email/reenviar`). O fallback do frontend
 * (manage => edit) mostraria um botão que o backend recusa com 403.
 */
export function podeReenviarEmail(permissions: readonly string[] | undefined | null): boolean {
  return hasPermission(permissions ?? undefined, PERMISSAO_REENVIAR_EMAIL);
}

/** Status tratados inline no diálogo de reenvio (401/403 mantêm o comportamento do apiFetch). */
const STATUS_INLINE_REENVIAR_EMAIL: readonly number[] = [404, 409, 422, 500, 502, 503, 504];

/**
 * POST do reenvio do email fiscal (financeiro:edit). Invalida em `onSettled`: um 409 significa
 * estado desatualizado, e o reenvio pode criar um novo episódio e resolver notificações.
 */
export function useReenviarEmail(documentoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<ReenviarEmailResposta>(
        `/documentos-fiscais/${encodeURIComponent(documentoId)}/email/reenviar`,
        { method: "POST" },
        { semToastParaStatus: STATUS_INLINE_REENVIAR_EMAIL },
      ),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: ["notificacoes"] }),
      ]);
    },
  });
}

/**
 * GET /documentos-fiscais/{id}/pdf (financeiro:view). Obtém o URL pré-assinado e redireciona
 * para iniciar a transferência no browser.
 */
export function useDescarregarPdf() {
  return useMutation({
    mutationFn: async ({ documentoId }: { documentoId: string }) => {
      const res = await apiFetch<DescargaPdfResposta>(
        `/documentos-fiscais/${encodeURIComponent(documentoId)}/pdf`,
        {},
        { semToastParaStatus: [404, 503] },
      );
      if (typeof window !== "undefined" && res.url) {
        window.location.assign(res.url);
      }
      return res;
    },
  });
}

/**
 * GET /documentos-fiscais/{id}/xml (financeiro:view). Obtém o blob XML e descarrega com o nome
 * fornecido pelo servidor ou com o fallback derivado do número formatado.
 */
export function useDescarregarXml() {
  return useMutation({
    mutationFn: async ({
      documentoId,
      numeroFormatado,
    }: {
      documentoId: string;
      numeroFormatado: string;
    }) => {
      const res = await apiFetchFicheiro(
        `/documentos-fiscais/${encodeURIComponent(documentoId)}/xml`,
        {},
        { semToastParaStatus: [404, 503] },
      );
      const fallback = `${numeroFormatado.replace(/[/ ]/g, "-")}.xml`;
      guardarFicheiro(res.blob, res.nomeFicheiro || fallback);
      return res;
    },
  });
}

/**
 * GET /documentos-fiscais/exportacao-mensal?mes=AAAA-MM (financeiro:view). Descarrega o ficheiro CSV
 * com o resumo do mês.
 */
export function useExportarMesCsv() {
  return useMutation({
    mutationFn: async ({ mes }: { mes: string }) => {
      const res = await apiFetchFicheiro(
        `/documentos-fiscais/exportacao-mensal?mes=${encodeURIComponent(mes)}`,
        {},
        { semToastParaStatus: [422, 500, 502, 503, 504] },
      );
      const fallback = `relatorio-fiscal-${mes}.csv`;
      guardarFicheiro(res.blob, res.nomeFicheiro || fallback);
      return res;
    },
  });
}
