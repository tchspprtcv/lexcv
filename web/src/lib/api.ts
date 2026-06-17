import { toast } from "@/hooks/use-toast";

const apiBasePath = process.env.NEXT_PUBLIC_API_BASE_PATH;
if (!apiBasePath) {
  throw new Error("NEXT_PUBLIC_API_BASE_PATH is required");
}

export const API_BASE = apiBasePath;

export async function apiFetch<TResponse>(
  path: string,
  init: RequestInit = {},
): Promise<TResponse> {
  const headers = new Headers(init.headers);

  if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    const body = await res.text().catch(() => "");
    const errorMessage = body || res.statusText || "Falha na comunicação com o servidor.";
    
    // Ignorar toast automático para rotas de auth check (401/403) para evitar spam se a sessão expirar
    // O hook useMe já trata do redirect
    if (res.status !== 401 && res.status !== 403) {
      toast.error(`Erro ${res.status}: ${errorMessage}`);
    }

    throw new Error(`API ${res.status}: ${errorMessage}`);
  }

  if (res.status === 204) {
    return undefined as TResponse;
  }

  return (await res.json()) as TResponse;
}
