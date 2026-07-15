import type { BrandingResponse } from "@/types/branding";
import { getBackendOrigin } from "@/lib/backend-origin";

const backendOrigin = getBackendOrigin();

const FALLBACK: BrandingResponse = { nome: "LexCV", logoDataUrl: null };

export async function fetchBranding(): Promise<BrandingResponse> {
  try {
    const response = await fetch(`${backendOrigin}/api/v1/public/branding`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
      headers: { Accept: "application/json" },
    });
    // 404 (sistema não inicializado — o proxy já terá redirecionado) ou qualquer !ok → fail open
    if (!response.ok) {
      return FALLBACK;
    }
    const data = (await response.json()) as BrandingResponse;
    return { nome: data.nome || "LexCV", logoDataUrl: data.logoDataUrl ?? null };
  } catch {
    // rede/timeout server-side → fail open, nunca crash para um visitante anónimo
    return FALLBACK;
  }
}
