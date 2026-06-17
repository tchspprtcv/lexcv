import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { Evento, EventoCreateRequest, EventoUpdateRequest } from "@/types/eventos";

export type EventosListFilters = {
  dataInicio?: string;
  dataFim?: string;
  processoId?: string;
  concluido?: boolean;
};

function normalizeDateParam(value: string | undefined) {
  if (!value) return undefined;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return undefined;
  return d.toISOString().slice(0, 19);
}

function buildEventosQuery(filters: EventosListFilters) {
  const params = new URLSearchParams();

  const dataInicio = normalizeDateParam(filters.dataInicio);
  const dataFim = normalizeDateParam(filters.dataFim);

  if (dataInicio) params.set("dataInicio", dataInicio);
  if (dataFim) params.set("dataFim", dataFim);
  if (filters.processoId) params.set("processoId", filters.processoId);
  if (filters.concluido !== undefined) params.set("concluido", String(filters.concluido));

  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export function useEventos(filters: EventosListFilters) {
  const enabled = typeof window !== "undefined" ;
  const query = buildEventosQuery(filters);

  return useQuery({
    queryKey: ["eventos", "list", query],
    queryFn: () => apiFetch<Evento[]>(`/eventos${query}`),
    enabled,
    staleTime: 15_000,
  });
}

export function useEvento(id: number | null) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["eventos", "detail", id],
    queryFn: () => apiFetch<Evento>(`/eventos/${encodeURIComponent(String(id))}`),
    enabled,
    staleTime: 15_000,
  });
}

export function useCreateEvento() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: EventoCreateRequest) =>
      apiFetch<Evento>("/eventos", {
        method: "POST",
        body: JSON.stringify(payload satisfies EventoCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["eventos", "list"] });
    },
  });
}

export function useUpdateEvento(id: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: EventoUpdateRequest) =>
      apiFetch<Evento>(`/eventos/${encodeURIComponent(String(id))}`, {
        method: "PUT",
        body: JSON.stringify(payload satisfies EventoUpdateRequest),
      }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["eventos", "list"] }),
        queryClient.setQueryData(["eventos", "detail", id], updated),
      ]);
    },
  });
}

export function useToggleEventoConcluido(id: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (concluido: boolean) =>
      apiFetch<Evento>(`/eventos/${encodeURIComponent(String(id))}`, {
        method: "PUT",
        body: JSON.stringify({ concluido } satisfies EventoUpdateRequest),
      }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["eventos", "list"] }),
        queryClient.setQueryData(["eventos", "detail", id], updated),
      ]);
    },
  });
}

export function useSetEventoConcluido() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { id: number; concluido: boolean }) =>
      apiFetch<Evento>(`/eventos/${encodeURIComponent(String(args.id))}`, {
        method: "PUT",
        body: JSON.stringify({ concluido: args.concluido } satisfies EventoUpdateRequest),
      }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["eventos", "list"] }),
        queryClient.setQueryData(["eventos", "detail", updated.id], updated),
      ]);
    },
  });
}
