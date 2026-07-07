import { describe, expect, it } from "vitest";

import { normalizeProcesso, toProcessoApiPayload } from "./use-processos";
import type { ProcessoCreateRequest, ProcessoUpdateRequest } from "@/types/processos";

// Spec vitest durável do round-trip juizo/origem — não executável nesta repo hoje
// (sem test runner instalado, precedente `web/src/lib/cliente-documento-tipo.test.ts`,
// confirmado ainda válido em 82-VERIFICATION.md). Existe como contrato de
// comportamento preciso para quando um runner for eventualmente adicionado.
//
// A prova executável real (Node puro, sem reimplementação) vive em
// `web/scripts/verify-juizo-origem-roundtrip.mjs`, que importa diretamente
// `web/src/lib/processo-juizo-origem-mapping.ts` — o MESMO módulo que
// `normalizeProcesso`/`toProcessoApiPayload` delegam a lógica de juizo/origem.
// Ver PITFALLS.md Pitfall 1.

describe("use-processos round-trip: juizo/origem", () => {
  it("normalizeProcesso mapeia juizo/origem de uma resposta camelCase inalterados", () => {
    const apiResponse = {
      id: "proc-1",
      tenantId: "tenant-1",
      clienteId: "cliente-1",
      juizo: "1º Juízo Cível",
      origem: "PETICAO_INICIAL" as const,
      createdAt: "2026-01-01T00:00:00Z",
    };

    const processo = normalizeProcesso(apiResponse);

    expect(processo.juizo).toBe("1º Juízo Cível");
    expect(processo.origem).toBe("PETICAO_INICIAL");
  });

  it("toProcessoApiPayload sobre ProcessoCreateRequest com juizo+origem definidos produz payload com ambos definidos", () => {
    const createRequest: ProcessoCreateRequest = {
      cliente_id: "cliente-1",
      juizo: "1º Juízo Cível",
      origem: "PETICAO_INICIAL",
    };

    const payload = toProcessoApiPayload(createRequest);

    expect(payload.juizo).toBe("1º Juízo Cível");
    expect(payload.origem).toBe("PETICAO_INICIAL");
  });

  it("toProcessoApiPayload sobre ProcessoUpdateRequest (sem origem) produz payload com origem estritamente undefined", () => {
    const updateRequest: ProcessoUpdateRequest = {
      juizo: "2º Juízo Cível",
    };

    const payload = toProcessoApiPayload(updateRequest);

    expect(payload.juizo).toBe("2º Juízo Cível");
    expect(payload.origem).toBeUndefined();
  });

  it("round-trip completo: normalizeProcesso -> ProcessoCreateRequest -> toProcessoApiPayload preserva juizo/origem", () => {
    const apiResponse = {
      id: "proc-1",
      tenantId: "tenant-1",
      clienteId: "cliente-1",
      juizo: "3º Juízo Cível",
      origem: "NOTIFICACOES_AVULSAS" as const,
      createdAt: "2026-01-01T00:00:00Z",
    };

    const processo = normalizeProcesso(apiResponse);

    const createRequest: ProcessoCreateRequest = {
      cliente_id: processo.cliente_id,
      juizo: processo.juizo,
      origem: processo.origem,
    };

    const payload = toProcessoApiPayload(createRequest);

    expect(payload.juizo).toBe("3º Juízo Cível");
    expect(payload.origem).toBe("NOTIFICACOES_AVULSAS");
  });
});
