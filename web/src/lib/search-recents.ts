import type { PesquisaResultadoTipo, ResultadoPesquisa } from "@/types/search";

const RECENTS_KEY = "lexcv:search-recents";
const RECENTS_CAP = 5;
const RECENT_ELIGIBLE_TIPOS: PesquisaResultadoTipo[] = [
  "cliente",
  "processo",
  "documento",
  "parecer",
];

// WR-02 (Phase 112 code review): readRecents() validates every stored item's `tipo` against
// this set on read. sessionStorage persists across soft navigations within an open tab and is
// never versioned, so a tab left open across a deploy that renames/removes a
// PesquisaResultadoTipo value can have a stale, now-invalid `tipo` sitting in storage — without
// this check, TIPO_META[recente.tipo] in the render path would be undefined and crash the page
// (there is no error boundary anywhere in this app to contain it).
const VALID_TIPOS = new Set<PesquisaResultadoTipo>(["cliente", "processo", "documento", "parecer"]);

export function readRecents(): ResultadoPesquisa[] {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const raw = sessionStorage.getItem(RECENTS_KEY) ?? "[]";
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(
      (item): item is ResultadoPesquisa =>
        !!item &&
        typeof item === "object" &&
        VALID_TIPOS.has((item as ResultadoPesquisa).tipo) &&
        typeof (item as ResultadoPesquisa).id === "string" &&
        typeof (item as ResultadoPesquisa).titulo === "string" &&
        typeof (item as ResultadoPesquisa).rota === "string",
    );
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
