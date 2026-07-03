import { describe, expect, it } from "vitest";

import { buildClienteFormSchema } from "./clientes";

/**
 * Regression coverage for the CR-01 gap closed in 74-04-PLAN.md: a cliente
 * loaded with a legacy/invalid `documento_tipo` for its `tipo` must be
 * saveable when left untouched, while every other membership rule stays
 * strict (new invalid values rejected, create path has no exemption).
 */
function baseRecord(overrides: Record<string, unknown> = {}) {
  return {
    nome: "Teste",
    nif: "123456789",
    tipo: "PARTICULAR" as const,
    ...overrides,
  };
}

describe("buildClienteFormSchema legacy documento_tipo exemption", () => {
  it("A: exempts the loaded legacy value from the per-tipo membership check", () => {
    const result = buildClienteFormSchema("REG_COMERCIAL").safeParse(
      baseRecord({ documento_tipo: "REG_COMERCIAL", documento_numero: "X" }),
    );
    expect(result.success).toBe(true);
  });

  it("B: still rejects a genuinely new invalid documento_tipo (not the exempted legacy value)", () => {
    const result = buildClienteFormSchema("REG_COMERCIAL").safeParse(
      baseRecord({ documento_tipo: "PASSAPORTE_INVALIDO", documento_numero: "X" }),
    );
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((issue) => issue.path);
      expect(paths).toContainEqual(["documento_tipo"]);
    }
  });

  it("C: no legacy exemption on the create path (no argument)", () => {
    const result = buildClienteFormSchema().safeParse(
      baseRecord({ documento_tipo: "REG_COMERCIAL", documento_numero: "X" }),
    );
    expect(result.success).toBe(false);
  });

  it("D: a valid in-set value still passes alongside the exemption", () => {
    const result = buildClienteFormSchema("REG_COMERCIAL").safeParse(
      baseRecord({ documento_tipo: "CNI", documento_numero: "X" }),
    );
    expect(result.success).toBe(true);
  });
});
