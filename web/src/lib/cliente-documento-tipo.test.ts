import { describe, expect, it } from "vitest";
import {
  getDocumentoTipoLabel,
  getDocumentoTipoOptions,
  toDocumentoTipo,
} from "./cliente-documento-tipo";

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

describe("getDocumentoTipoLabel", () => {
  it("translates REG_COMERCIAL to Registo Comercial", () => {
    expect(getDocumentoTipoLabel("REG_COMERCIAL")).toBe("Registo Comercial");
  });

  it("translates PASSAPORTE to Passaporte", () => {
    expect(getDocumentoTipoLabel("PASSAPORTE")).toBe("Passaporte");
  });

  it("translates BI and CNI to themselves", () => {
    expect(getDocumentoTipoLabel("BI")).toBe("BI");
    expect(getDocumentoTipoLabel("CNI")).toBe("CNI");
  });

  it("returns undefined for null, undefined and empty string", () => {
    expect(getDocumentoTipoLabel(null)).toBeUndefined();
    expect(getDocumentoTipoLabel(undefined)).toBeUndefined();
    expect(getDocumentoTipoLabel("")).toBeUndefined();
  });

  it("returns an unknown/legacy value verbatim instead of hiding it", () => {
    expect(getDocumentoTipoLabel("LEGACY_UNKNOWN")).toBe("LEGACY_UNKNOWN");
  });
});
