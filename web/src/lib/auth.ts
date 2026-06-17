import { API_BASE } from "./api";

export async function clearTokens() {
  if (typeof window === "undefined") return;
  try {
    await fetch(`${API_BASE}/auth/logout`, { method: "POST", credentials: "include" });
  } catch (e) {
    console.error("Erro ao fazer logout", e);
  }
}
