import { afterEach, describe, expect, it, vi } from "vitest";

import { gerarChaveIdempotencia } from "@/lib/idempotencia";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

const cryptoReal = globalThis.crypto;

function semRandomUUID() {
  vi.stubGlobal("crypto", {
    getRandomValues: (a: Uint8Array) => cryptoReal.getRandomValues(a),
  });
}

describe("gerarChaveIdempotencia", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("usa crypto.randomUUID quando existe", () => {
    vi.stubGlobal("crypto", {
      randomUUID: () => "11111111-2222-4333-8444-555555555555",
      getRandomValues: () => {
        throw new Error("não devia ser usado");
      },
    });
    expect(gerarChaveIdempotencia()).toBe("11111111-2222-4333-8444-555555555555");
  });

  it("sem randomUUID (http simples na LAN), gera um UUID v4 com getRandomValues", () => {
    semRandomUUID();
    expect(globalThis.crypto.randomUUID).toBeUndefined();
    expect(gerarChaveIdempotencia()).toMatch(UUID_V4);
  });

  it("o fallback fixa os bits de versão e variante mesmo com bytes todos a 0xff ou 0x00", () => {
    vi.stubGlobal("crypto", { getRandomValues: (a: Uint8Array) => a.fill(0xff) });
    expect(gerarChaveIdempotencia()).toBe("ffffffff-ffff-4fff-bfff-ffffffffffff");
    vi.stubGlobal("crypto", { getRandomValues: (a: Uint8Array) => a.fill(0x00) });
    expect(gerarChaveIdempotencia()).toBe("00000000-0000-4000-8000-000000000000");
  });

  it("1000 chaves do fallback são todas distintas e válidas", () => {
    semRandomUUID();
    const chaves = new Set<string>();
    for (let i = 0; i < 1000; i++) {
      const c = gerarChaveIdempotencia();
      expect(c).toMatch(UUID_V4);
      chaves.add(c);
    }
    expect(chaves.size).toBe(1000);
  });

  it("1000 chaves do caminho nativo são todas distintas", () => {
    const chaves = new Set(Array.from({ length: 1000 }, () => gerarChaveIdempotencia()));
    expect(chaves.size).toBe(1000);
  });
});
