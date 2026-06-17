import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { MeResponse } from "@/types/auth";

export function useMe() {
  const enabled = typeof window !== "undefined" ;

  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: () => apiFetch<MeResponse>("/auth/me"),
    enabled,
    staleTime: 60_000,
  });
}
