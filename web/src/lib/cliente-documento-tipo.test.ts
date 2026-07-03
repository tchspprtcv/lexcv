import { describe, expect, it } from "vitest";
import { getDocumentoTipoOptions, toDocumentoTipo } from "./cliente-documento-tipo";

describe("getDocumentoTipoOptions", () => {
  it("returns CNI, BI, Passaporte for PARTICULAR in that order", () => {
    const options = getDocumentoTipoOptions("PARTICULAR");
    expect(options.map((o) => o.value)).toEqual(["CNI", "BI", "PASSAPORTE"]);
    expect(options.map((o) => o.label)).toEqual(["CNI", "BI", "Passaporte"]);
  });

  it("returns only Registo Comercial for EMPRESA", () => {
    const options = getDocumentoTipoOptions("EMPRESA");
    expect(options).toEqual([{ value: "REG_COMERCIAL", label: "Registo Comercial" }]);
  });

  it("falls back to the PARTICULAR set when tipo is undefined", () => {
    const options = getDocumentoTipoOptions(undefined);
    expect(options.map((o) => o.value)).toEqual(["CNI", "BI", "PASSAPORTE"]);
  });
});

describe("toDocumentoTipo", () => {
  it("accepts a value in the allowed set for the given tipo", () => {
    expect(toDocumentoTipo("CNI", "PARTICULAR")).toBe("CNI");
  });

  it("rejects a value valid for a different tipo", () => {
    expect(toDocumentoTipo("REG_COMERCIAL", "PARTICULAR")).toBeUndefined();
  });

  it("rejects a removed/legacy value (NIF)", () => {
    expect(toDocumentoTipo("NIF", "PARTICULAR")).toBeUndefined();
  });

  it("rejects an undefined value", () => {
    expect(toDocumentoTipo(undefined, "PARTICULAR")).toBeUndefined();
  });
});
