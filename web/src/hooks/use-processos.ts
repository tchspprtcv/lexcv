import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { mapJuizoOrigemFromApi, mapJuizoOrigemToPayload } from "@/lib/processo-juizo-origem-mapping";

import type {
  AuditLogEntry,
  ConflictCheckDecisao,
  ConflictCheckDecisaoRequest,
  ConflictCheckResponse,
  Decisao,
  DecisaoCreateRequest,
  DecisaoUpdateRequest,
  Facto,
  FactoCreateRequest,
  FactoUpdateRequest,
  OrigemProcesso,
  Prazo,
  PrazoCreateRequest,
  PrazoRisco,
  Processo,
  ProcessoCreateRequest,
  ProcessoFase,
  ProcessoFaseCreateRequest,
  ProcessoFaseUpdateRequest,
  ProcessoMovimentacao,
  ProcessoMovimentacaoCreateRequest,
  ProcessoParte,
  ProcessoParteCreateRequest,
  ProcessoUpdateRequest,
  Testemunha,
  TestemunhaCreateRequest,
  TestemunhaUpdateRequest,
  TimelineItem,
  TransicaoRequest,
  WorkflowResponse,
} from "@/types/processos";

type ProcessoApi = {
  id: string;
  tenantId?: string;
  tenant_id?: string;
  clienteId?: string;
  cliente_id?: string;
  numeroProcesso?: string;
  numero_processo?: string;
  numero?: string;
  tipoProcesso?: string;
  tipo_processo?: string;
  titulo?: string;
  areaJuridica?: string;
  area_juridica?: string;
  tribunal?: string;
  descricao?: string;
  estado?: string;
  dataInicio?: string;
  data_inicio?: string;
  dataFim?: string;
  data_fim?: string;
  responsavelId?: string;
  responsavel_id?: string;
  responsavel_nome?: string;
  risco_mais_critico?: PrazoRisco;
  tem_prazo_escalonado?: boolean;
  createdAt?: string;
  created_at?: string;
  updatedAt?: string;
  updated_at?: string;
  juizo?: string;
  origem?: OrigemProcesso;
};

type ProcessoApiPayload = {
  clienteId?: string;
  numeroProcesso?: string;
  tipoProcesso?: string;
  areaJuridica?: string;
  tribunal?: string;
  descricao?: string;
  estado?: string;
  dataInicio?: string;
  dataFim?: string;
  legalHold?: boolean;
  dataRetencao?: string;
  juizo?: string;
  origem?: OrigemProcesso;
};

export type ProcessosListFilters = {
  q?: string;
  estado?: string;
  tribunal?: string;
  area_juridica?: string;
  cliente_id?: string;
  sortBy?: "created_at" | "numero" | "estado";
  sortDir?: "asc" | "desc";
};

export function normalizeProcesso(api: ProcessoApi): Processo {
  return {
    id: api.id,
    tenant_id: api.tenant_id ?? api.tenantId ?? "",
    cliente_id: api.cliente_id ?? api.clienteId ?? "",
    numero: api.numero ?? api.numeroProcesso ?? api.numero_processo,
    titulo: api.titulo ?? api.tipoProcesso ?? api.tipo_processo,
    tipo_processo: api.tipoProcesso ?? api.tipo_processo ?? api.titulo,
    area_juridica: api.area_juridica ?? api.areaJuridica,
    tribunal: api.tribunal,
    descricao: api.descricao,
    estado: api.estado,
    data_inicio: api.data_inicio ?? api.dataInicio,
    data_fim: api.data_fim ?? api.dataFim,
    responsavel_id: api.responsavel_id ?? api.responsavelId,
    responsavel_nome: api.responsavel_nome,
    risco_mais_critico: api.risco_mais_critico,
    tem_prazo_escalonado: api.tem_prazo_escalonado,
    ...mapJuizoOrigemFromApi(api),
    created_at: api.created_at ?? api.createdAt ?? "",
    updated_at: api.updated_at ?? api.updatedAt,
  };
}

export function toProcessoApiPayload(payload: ProcessoCreateRequest | ProcessoUpdateRequest): ProcessoApiPayload {
  return {
    clienteId: payload.cliente_id,
    numeroProcesso: payload.numero,
    tipoProcesso: payload.tipo_processo ?? payload.titulo,
    areaJuridica: payload.area_juridica,
    tribunal: payload.tribunal,
    descricao: payload.descricao,
    estado: payload.estado,
    dataInicio: payload.data_inicio,
    dataFim: payload.data_fim,
    // ProcessoCreateRequest doesn't declare legal_hold/data_retencao today; only
    // ProcessoUpdateRequest does. Guard with "in" (mirrors mapJuizoOrigemToPayload)
    // so create requests never send these keys, while update requests always do —
    // otherwise the backend's updateProcesso unconditionally resets legalHold to
    // false / dataRetencao to null on every edit that omits them (CR-01).
    legalHold: "legal_hold" in payload ? payload.legal_hold : undefined,
    dataRetencao: "data_retencao" in payload ? payload.data_retencao : undefined,
    ...mapJuizoOrigemToPayload(payload),
  };
}

function buildProcessosSearch(filters: ProcessosListFilters) {
  const sp = new URLSearchParams();
  if (filters.q?.trim()) sp.set("q", filters.q.trim());
  if (filters.estado?.trim()) sp.set("estado", filters.estado.trim());
  if (filters.tribunal?.trim()) sp.set("tribunal", filters.tribunal.trim());
  if (filters.area_juridica?.trim()) sp.set("area_juridica", filters.area_juridica.trim());
  if (filters.cliente_id?.trim()) sp.set("cliente_id", filters.cliente_id.trim());
  if (filters.sortBy?.trim()) sp.set("sortBy", filters.sortBy);
  if (filters.sortDir?.trim()) sp.set("sortDir", filters.sortDir);
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useProcessos(filters: ProcessosListFilters = {}) {
  const enabled = typeof window !== "undefined" ;
  const q = filters.q?.trim() ?? "";
  const estado = filters.estado?.trim() ?? "";
  const tribunal = filters.tribunal?.trim() ?? "";
  const area = filters.area_juridica?.trim() ?? "";
  const clienteId = filters.cliente_id?.trim() ?? "";
  const sortBy = filters.sortBy ?? "created_at";
  const sortDir = filters.sortDir ?? "desc";

  return useQuery({
    queryKey: ["processos", "list", q, estado, tribunal, area, clienteId, sortBy, sortDir],
    queryFn: async () => {
      const response = await apiFetch<ProcessoApi[]>(
        `/processos${buildProcessosSearch({
          q,
          estado,
          tribunal,
          area_juridica: area,
          cliente_id: clienteId,
          sortBy,
          sortDir,
        })}`,
      );
      return response.map(normalizeProcesso);
    },
    enabled,
    staleTime: 30_000,
  });
}

export function useProcesso(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["processos", "detail", id],
    queryFn: async () => {
      const response = await apiFetch<ProcessoApi>(`/processos/${encodeURIComponent(id)}`);
      return normalizeProcesso(response);
    },
    enabled,
    staleTime: 30_000,
  });
}

export function useCreateProcesso() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: ProcessoCreateRequest) => {
      const response = await apiFetch<ProcessoApi>("/processos", {
        method: "POST",
        body: JSON.stringify(toProcessoApiPayload(payload satisfies ProcessoCreateRequest)),
      });
      return normalizeProcesso(response);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "list"] });
    },
  });
}

export function useUpdateProcesso(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: ProcessoUpdateRequest) => {
      const response = await apiFetch<ProcessoApi>(`/processos/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(toProcessoApiPayload(payload satisfies ProcessoUpdateRequest)),
      });
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", id], updated),
      ]);
    },
  });
}

export function useDeleteProcesso(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/processos/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.removeQueries({ queryKey: ["processos", "detail", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "partes", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "fases", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "movimentacoes", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "decisoes", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "testemunhas", id] }),
        queryClient.removeQueries({ queryKey: ["processos", "factos", id] }),
      ]);
    },
  });
}

export function useProcessoPartes(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["processos", "partes", id],
    queryFn: () => apiFetch<ProcessoParte[]>(`/processos/${encodeURIComponent(id)}/partes`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddProcessoParte(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProcessoParteCreateRequest) =>
      apiFetch<ProcessoParte>(`/processos/${encodeURIComponent(id)}/partes`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ProcessoParteCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "partes", id] });
    },
  });
}

export function useProcessoFases(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["processos", "fases", id],
    queryFn: () => apiFetch<ProcessoFase[]>(`/processos/${encodeURIComponent(id)}/fases`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddProcessoFase(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProcessoFaseCreateRequest) =>
      apiFetch<ProcessoFase>(`/processos/${encodeURIComponent(id)}/fases`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ProcessoFaseCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "fases", id] });
    },
  });
}

export function useUpdateProcessoFaseStatus(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { faseId: number; payload: ProcessoFaseUpdateRequest }) =>
      apiFetch<ProcessoFase>(
        `/processos/${encodeURIComponent(id)}/fases/${encodeURIComponent(String(args.faseId))}`,
        {
          method: "PUT",
          body: JSON.stringify(args.payload satisfies ProcessoFaseUpdateRequest),
        },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<ProcessoFase[] | undefined>(["processos", "fases", id], (current) => {
        if (!current) return current;
        return current.map((item) => (item.id === updated.id ? updated : item));
      });
    },
  });
}

export function useProcessoMovimentacoes(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["processos", "movimentacoes", id],
    queryFn: () =>
      apiFetch<ProcessoMovimentacao[]>(`/processos/${encodeURIComponent(id)}/movimentacoes`),
    enabled,
    staleTime: 10_000,
  });
}

export function useAddProcessoMovimentacao(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProcessoMovimentacaoCreateRequest) =>
      apiFetch<ProcessoMovimentacao>(`/processos/${encodeURIComponent(id)}/movimentacoes`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ProcessoMovimentacaoCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", id] });
      await queryClient.invalidateQueries({ queryKey: ["processos", "timeline", id] });
    },
  });
}

export function useDecisoes(id: string) {
  const enabled = typeof window !== "undefined" && Boolean(id);

  return useQuery({
    queryKey: ["processos", "decisoes", id],
    queryFn: () => apiFetch<Decisao[]>(`/processos/${encodeURIComponent(id)}/decisoes`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddDecisao(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DecisaoCreateRequest) => {
      const form = new FormData();
      form.set("data", payload.data);
      form.set("tipo", payload.tipo);
      if (payload.resumo?.trim()) form.set("resumo", payload.resumo.trim());
      if (payload.file) form.set("file", payload.file);
      return apiFetch<Decisao>(`/processos/${encodeURIComponent(id)}/decisoes`, {
        method: "POST",
        body: form,
      });
    },
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "decisoes", id] });
      if (created.documentoId) {
        await queryClient.invalidateQueries({ queryKey: ["documentos", "list"] });
      }
    },
  });
}

export function useUpdateDecisao(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { decisaoId: number; payload: DecisaoUpdateRequest }) =>
      apiFetch<Decisao>(
        `/processos/${encodeURIComponent(id)}/decisoes/${encodeURIComponent(String(args.decisaoId))}`,
        { method: "PUT", body: JSON.stringify(args.payload satisfies DecisaoUpdateRequest) },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Decisao[] | undefined>(["processos", "decisoes", id], (current) => {
        if (!current) return current;
        return current.map((item) => (item.id === updated.id ? updated : item));
      });
    },
  });
}

export function useDeleteDecisao(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (decisaoId: number) =>
      apiFetch<void>(
        `/processos/${encodeURIComponent(id)}/decisoes/${encodeURIComponent(String(decisaoId))}`,
        { method: "DELETE" },
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "decisoes", id] }),
        queryClient.invalidateQueries({ queryKey: ["documentos", "list"] }),
      ]);
    },
  });
}

export function useTestemunhas(id: string) {
  const enabled = typeof window !== "undefined" && Boolean(id);

  return useQuery({
    queryKey: ["processos", "testemunhas", id],
    queryFn: () => apiFetch<Testemunha[]>(`/processos/${encodeURIComponent(id)}/testemunhas`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddTestemunha(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: TestemunhaCreateRequest) =>
      apiFetch<Testemunha>(`/processos/${encodeURIComponent(id)}/testemunhas`, {
        method: "POST",
        body: JSON.stringify(payload satisfies TestemunhaCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "testemunhas", id] });
    },
  });
}

export function useUpdateTestemunha(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { testemunhaId: number; payload: TestemunhaUpdateRequest }) =>
      apiFetch<Testemunha>(
        `/processos/${encodeURIComponent(id)}/testemunhas/${encodeURIComponent(String(args.testemunhaId))}`,
        { method: "PUT", body: JSON.stringify(args.payload satisfies TestemunhaUpdateRequest) },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Testemunha[] | undefined>(["processos", "testemunhas", id], (current) => {
        if (!current) return current;
        return current.map((item) => (item.id === updated.id ? updated : item));
      });
    },
  });
}

export function useDeleteTestemunha(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (testemunhaId: number) =>
      apiFetch<void>(
        `/processos/${encodeURIComponent(id)}/testemunhas/${encodeURIComponent(String(testemunhaId))}`,
        { method: "DELETE" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "testemunhas", id] });
    },
  });
}

export function useFactos(id: string) {
  const enabled = typeof window !== "undefined" && Boolean(id);

  return useQuery({
    queryKey: ["processos", "factos", id],
    queryFn: () => apiFetch<Facto[]>(`/processos/${encodeURIComponent(id)}/factos`),
    enabled,
    staleTime: 15_000,
  });
}

export function useAddFacto(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: FactoCreateRequest) =>
      apiFetch<Facto>(`/processos/${encodeURIComponent(id)}/factos`, {
        method: "POST",
        body: JSON.stringify(payload satisfies FactoCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "factos", id] });
    },
  });
}

export function useUpdateFacto(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { factoId: number; payload: FactoUpdateRequest }) =>
      apiFetch<Facto>(
        `/processos/${encodeURIComponent(id)}/factos/${encodeURIComponent(String(args.factoId))}`,
        { method: "PUT", body: JSON.stringify(args.payload satisfies FactoUpdateRequest) },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Facto[] | undefined>(["processos", "factos", id], (current) => {
        if (!current) return current;
        return current.map((item) => (item.id === updated.id ? updated : item));
      });
    },
  });
}

export function useDeleteFacto(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (factoId: number) =>
      apiFetch<void>(
        `/processos/${encodeURIComponent(id)}/factos/${encodeURIComponent(String(factoId))}`,
        { method: "DELETE" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "factos", id] });
    },
  });
}

export function useCreateIntake() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: ProcessoCreateRequest) => {
      const response = await apiFetch<ProcessoApi>("/processos/intake", {
        method: "POST",
        body: JSON.stringify(toProcessoApiPayload(payload satisfies ProcessoCreateRequest)),
      });
      return normalizeProcesso(response);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["processos", "list"] });
    },
  });
}

export function useRunConflictCheck(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiFetch<ConflictCheckResponse>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check`,
        { method: "POST" },
      );
      return response;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["processos", "conflict-check", processoId],
      });
    },
  });
}

export function useRegistarDecisaoConflito(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: ConflictCheckDecisaoRequest) => {
      const response = await apiFetch<ConflictCheckDecisao>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check/decisao`,
        {
          method: "POST",
          body: JSON.stringify(payload),
        },
      );
      return response;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["processos", "conflict-check", processoId],
        }),
        queryClient.invalidateQueries({
          queryKey: ["processos", "detail", processoId],
        }),
      ]);
    },
  });
}

export function useConflictCheckDecisao(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "conflict-check", processoId],
    queryFn: () =>
      apiFetch<ConflictCheckDecisao | null>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check/decisao`,
      ),
    enabled,
    staleTime: 15_000,
  });
}

export function useFormalizarProcesso(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiFetch<ProcessoApi>(
        `/processos/${encodeURIComponent(processoId)}/formalizar`,
        { method: "POST" },
      );
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
      ]);
    },
  });
}

export function useWorkflow(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "workflow", processoId],
    queryFn: () =>
      apiFetch<WorkflowResponse>(
        `/processos/${encodeURIComponent(processoId)}/workflow`,
      ),
    enabled,
    staleTime: 15_000,
  });
}

export function useExecutarTransicao(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (args: { acao: string; payload?: TransicaoRequest }) => {
      const response = await apiFetch<ProcessoApi>(
        `/processos/${encodeURIComponent(processoId)}/transicao/${args.acao}`,
        {
          method: "POST",
          body: JSON.stringify(args.payload ?? {}),
        },
      );
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
        queryClient.invalidateQueries({ queryKey: ["processos", "workflow", processoId] }),
        queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", processoId] }),
        queryClient.invalidateQueries({ queryKey: ["processos", "timeline", processoId] }),
      ]);
    },
  });
}

export function usePrazos(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "prazos", processoId],
    queryFn: () =>
      apiFetch<Prazo[]>(
        `/processos/${encodeURIComponent(processoId)}/prazos`,
      ),
    enabled,
    staleTime: 15_000,
  });
}

export function useAllPrazos() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: ["prazos", "list"],
    queryFn: () => apiFetch<Prazo[]>("/prazos"),
    enabled,
    staleTime: 15_000,
  });
}

export function useCreatePrazo(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PrazoCreateRequest) =>
      apiFetch<Prazo>(
        `/processos/${encodeURIComponent(processoId)}/prazos`,
        {
          method: "POST",
          body: JSON.stringify(payload satisfies PrazoCreateRequest),
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["processos", "prazos", processoId],
      });
    },
  });
}

export function useTogglePrazoConcluido(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { prazoId: string; concluido: boolean }) =>
      apiFetch<Prazo>(
        `/processos/${encodeURIComponent(processoId)}/prazos/${encodeURIComponent(args.prazoId)}/concluido`,
        {
          method: "PATCH",
          body: JSON.stringify({ concluido: args.concluido }),
        },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Prazo[] | undefined>(
        ["processos", "prazos", processoId],
        (current) => {
          if (!current) return current;
          return current.map((p) => (p.id === updated.id ? updated : p));
        },
      );
      // Invalidate list so risco_mais_critico and tem_prazo_escalonado badges refresh
      await queryClient.invalidateQueries({ queryKey: ["processos", "list"] });
    },
  });
}

export function useTimeline(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "timeline", processoId],
    queryFn: () =>
      apiFetch<TimelineItem[]>(
        `/processos/${encodeURIComponent(processoId)}/timeline`,
      ),
    enabled,
    staleTime: 15_000,
  });
}

export function useAuditLog(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "audit", processoId],
    queryFn: () =>
      apiFetch<AuditLogEntry[]>(
        `/processos/${encodeURIComponent(processoId)}/audit`,
      ),
    enabled,
    staleTime: 30_000,
  });
}
