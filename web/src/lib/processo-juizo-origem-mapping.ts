// Módulo partilhado, sem dependências de path-alias, para o mapeamento juizo/origem.
// Consumido tanto por src/hooks/use-processos.ts (runtime) como por
// scripts/verify-juizo-origem-roundtrip.mjs (prova de round-trip executável) —
// ambos chamam EXATAMENTE as mesmas funções. Ver PITFALLS.md Pitfall 1.
import type { OrigemProcesso } from "../types/processos";

export interface ApiJuizoOrigem {
  juizo?: string;
  origem?: OrigemProcesso;
}

export interface CreateJuizoOrigem {
  juizo?: string;
  origem?: OrigemProcesso;
}

export interface UpdateJuizoOrigem {
  juizo?: string;
}

export function mapJuizoOrigemFromApi(api: ApiJuizoOrigem): { juizo?: string; origem?: OrigemProcesso } {
  return { juizo: api.juizo, origem: api.origem };
}

// WARNING: disambiguation between create/update relies on the runtime "in"
// operator over the payload's own declared shape. Never spread a full
// Processo/ProcessoApi object into an update payload passed here — object
// spread would carry the `origem` property along regardless of the target
// type's declared shape, silently leaking it back into the update JSON body.
// Only pass genuinely-typed ProcessoUpdateRequest/ProcessoCreateRequest
// literals (WR-03).
export function mapJuizoOrigemToPayload(
  payload: CreateJuizoOrigem | UpdateJuizoOrigem,
): { juizo?: string; origem?: OrigemProcesso } {
  return {
    juizo: payload.juizo,
    origem: "origem" in payload ? payload.origem : undefined,
  };
}
