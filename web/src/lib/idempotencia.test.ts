import { afterEach, describe, expect, it, vi } from "vitest";

import {
  gerarChaveIdempotencia,
  marcarPorResolver,
  pedidoCanonico,
  tentativaParaPedido,
} from "@/lib/idempotencia";

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

describe("ciclo de vida da chave (CR-02)", () => {
  const pedido = {
    honorarioId: 7,
    valorPago: 120000,
    dataPagamento: "2026-10-04",
    metodo: "TRANSFERENCIA",
    retencaoPercentagem: 20,
  };
  let n = 0;
  const gerar = () => `chave-${++n}`;

  it("pedidoCanonico não depende da ordem das propriedades", () => {
    expect(pedidoCanonico({ b: 1, a: { d: 2, c: 3 } })).toBe(pedidoCanonico({ a: { c: 3, d: 2 }, b: 1 }));
    expect(pedidoCanonico({ a: 1, b: undefined })).toBe(pedidoCanonico({ a: 1 }));
  });

  it("sem tentativa anterior gera uma chave nova, ainda sem desfecho por resolver", () => {
    const t = tentativaParaPedido(null, pedido, gerar);
    expect(t.chave).toMatch(/^chave-/);
    expect(t.porResolver).toBe(false);
  });

  it("depois de uma falha ambígua, o MESMO pedido reutiliza a chave mesmo com o diálogo fechado e reaberto", () => {
    const primeira = tentativaParaPedido(null, pedido, gerar);
    const ambigua = marcarPorResolver(primeira);
    const reaberta = tentativaParaPedido(ambigua, { ...pedido }, gerar);
    expect(reaberta.chave).toBe(primeira.chave);
    expect(reaberta.porResolver).toBe(true);
    // E continua a reutilizá-la em reaberturas seguintes, até haver desfecho.
    expect(tentativaParaPedido(reaberta, { ...pedido }, gerar).chave).toBe(primeira.chave);
  });

  it("depois de uma falha ambígua, um pedido DIFERENTE leva uma chave nova", () => {
    const ambigua = marcarPorResolver(tentativaParaPedido(null, pedido, gerar));
    const outra = tentativaParaPedido(ambigua, { ...pedido, valorPago: 100000 }, gerar);
    expect(outra.chave).not.toBe(ambigua?.chave);
    expect(outra.porResolver).toBe(false);
  });

  it("sem falha ambígua (fechar e reabrir sem emitir), reabrir gera uma chave nova", () => {
    const primeira = tentativaParaPedido(null, pedido, gerar);
    expect(tentativaParaPedido(primeira, pedido, gerar).chave).not.toBe(primeira.chave);
  });

  it("depois de um desfecho definitivo (tentativa descartada = null), o mesmo pedido leva chave nova", () => {
    const primeira = tentativaParaPedido(null, pedido, gerar);
    marcarPorResolver(primeira);
    expect(tentativaParaPedido(null, pedido, gerar).chave).not.toBe(primeira.chave);
  });

  it("marcarPorResolver(null) devolve null e não muta a tentativa original", () => {
    expect(marcarPorResolver(null)).toBeNull();
    const t = tentativaParaPedido(null, pedido, gerar);
    const m = marcarPorResolver(t);
    expect(t.porResolver).toBe(false);
    expect(m?.porResolver).toBe(true);
    expect(m?.chave).toBe(t.chave);
  });

  it("por omissão usa gerarChaveIdempotencia", () => {
    expect(tentativaParaPedido(null, pedido).chave).toMatch(UUID_V4);
  });
});
