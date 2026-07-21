import type { PesquisaResultadoTipo, ResultadoPesquisa } from "@/types/search";

const RECENTS_KEY = "lexcv:search-recents";
const RECENTS_CAP = 5;
const RECENT_ELIGIBLE_TIPOS: PesquisaResultadoTipo[] = [
  "cliente",
  "processo",
  "documento",
  "parecer",
];

export function readRecents(): ResultadoPesquisa[] {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const raw = sessionStorage.getItem(RECENTS_KEY) ?? "[]";
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed as ResultadoPesquisa[];
  } catch {
    return [];
  }
}

export function pushRecent(item: ResultadoPesquisa): void {
  if (typeof window === "undefined") {
    return;
  }

  if (!RECENT_ELIGIBLE_TIPOS.includes(item.tipo)) {
    return;
  }

  try {
    const current = readRecents();
    const deduped = current.filter(
      (recent) => !(recent.tipo === item.tipo && recent.id === item.id),
    );
    const next = [item, ...deduped].slice(0, RECENTS_CAP);
    sessionStorage.setItem(RECENTS_KEY, JSON.stringify(next));
  } catch {
    // sessionStorage quota/serialization failures are best-effort only — never throw into the UI.
  }
}
