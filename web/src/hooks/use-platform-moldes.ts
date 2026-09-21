import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type {
  MoldeCreateRequest,
  MoldesConsola,
  MoldesUpdateRequest,
} from "@/types/platform-moldes";

// Todas as mutacoes abaixo invalidam MOLDES_LIST_KEY em vez de recarregar a
// pagina inteira. O recarregamento total do browser e um artefacto da era
// mock legada e esta explicitamente proibido pelo UI-SPEC desta fase.
// Tambem nao existe nenhuma cache de "detalhe" por molde nesta consola — a
// listagem completa e sempre refeita, por isso nenhuma mutacao escreve
// directamente na cache do TanStack Query fora do fluxo de invalidacao.
const MOLDES_LIST_KEY = ["platform", "moldes", "list"] as const;

export function useMoldes() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: MOLDES_LIST_KEY,
    queryFn: () => apiFetch<MoldesConsola>("/platform/moldes"),
    enabled,
    staleTime: 30_000,
  });
}

export function useUpdateMoldes() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: MoldesUpdateRequest) =>
      apiFetch<MoldesConsola>("/platform/moldes", {
        method: "PUT",
        body: JSON.stringify(payload satisfies MoldesUpdateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: MOLDES_LIST_KEY });
    },
  });
}

export function useCreateMolde() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: MoldeCreateRequest) =>
      apiFetch<{ id: number; nome: string }>("/platform/moldes", {
        method: "POST",
        body: JSON.stringify(payload satisfies MoldeCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: MOLDES_LIST_KEY });
    },
  });
}
