import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, isApiError } from "@/lib/api";

import type {
  CodigoErroFaturacao,
  ConfiguracaoFiscal,
  ConfiguracaoFiscalPayload,
  EmailAutomaticoPayload,
  MotivoIsencao,
  SerieFiscal,
} from "@/types/faturacao";

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

export function useGuardarConfiguracaoFiscal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ConfiguracaoFiscalPayload) =>
      apiFetch<ConfiguracaoFiscal>(
        "/faturacao/configuracao",
        { method: "PUT", body: JSON.stringify(payload) },
        { semToastParaStatus: [400, 409, 422] },
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: FATURACAO_CONFIG_KEY }),
        queryClient.invalidateQueries({ queryKey: FATURACAO_SERIES_KEY }),
      ]);
    },
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
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: FATURACAO_CONFIG_KEY }),
        queryClient.invalidateQueries({ queryKey: FATURACAO_SERIES_KEY }),
      ]);
    },
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
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: FATURACAO_CONFIG_KEY }),
        queryClient.invalidateQueries({ queryKey: FATURACAO_SERIES_KEY }),
      ]);
    },
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
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: FATURACAO_CONFIG_KEY }),
        queryClient.invalidateQueries({ queryKey: FATURACAO_SERIES_KEY }),
      ]);
    },
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
