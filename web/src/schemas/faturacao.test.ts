import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api";
import { mensagemErroFaturacao } from "@/hooks/use-faturacao";
import { configuracaoFiscalSchema, nifFiscalPattern } from "@/schemas/faturacao";

const valido = {
  nif: "512345678",
  firma: "Escritório Lda",
  morada: "Rua 1, Plateau",
  localidade: "Praia",
  emailContacto: "geral@escritorio.cv",
  telefoneContacto: "+238 999 99 99",
  regimeIva: "NORMAL" as const,
  motivoIsencaoCodigo: null as string | null,
};

function mensagensDe(resultado: ReturnType<typeof configuracaoFiscalSchema.safeParse>, campo: string) {
  if (resultado.success) return [];
  return resultado.error.issues.filter((i) => i.path[0] === campo).map((i) => i.message);
}

const MSG_NIF = "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9.";

describe("configuracaoFiscalSchema", () => {
  it("aceita um payload NORMAL válido", () => {
    const r = configuracaoFiscalSchema.safeParse(valido);
    expect(r.success).toBe(true);
  });

  it("transforma motivoIsencaoCodigo em null para NORMAL", () => {
    const r = configuracaoFiscalSchema.safeParse({ ...valido, motivoIsencaoCodigo: "5" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.motivoIsencaoCodigo).toBeNull();
  });

  it.each(["012345678", "51234567", "51234567a"])("rejeita NIF %s", (nif) => {
    const r = configuracaoFiscalSchema.safeParse({ ...valido, nif });
    expect(r.success).toBe(false);
    expect(mensagensDe(r, "nif")).toContain(MSG_NIF);
  });

  it("faz trim do NIF", () => {
    const r = configuracaoFiscalSchema.safeParse({ ...valido, nif: " 512345678 " });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.nif).toBe("512345678");
  });

  it.each(["firma", "morada", "localidade", "telefoneContacto", "emailContacto"])(
    "exige %s",
    (campo) => {
      const r = configuracaoFiscalSchema.safeParse({ ...valido, [campo]: "" });
      expect(r.success).toBe(false);
      expect(mensagensDe(r, campo)).toContain("Preencha este campo.");
    },
  );

  it.each([
    ["firma", 200, "A firma não pode ter mais de 200 caracteres."],
    ["localidade", 100, "A localidade não pode ter mais de 100 caracteres."],
    ["telefoneContacto", 32, "O telefone não pode ter mais de 32 caracteres."],
  ])("limita %s a %i caracteres (espelho do backend)", (campo, max, mensagem) => {
    expect(configuracaoFiscalSchema.safeParse({ ...valido, [campo]: "a".repeat(max) }).success).toBe(
      true,
    );
    const r = configuracaoFiscalSchema.safeParse({ ...valido, [campo]: "a".repeat(max + 1) });
    expect(r.success).toBe(false);
    expect(mensagensDe(r, campo)).toContain(mensagem);
  });

  it("limita o email a 254 caracteres", () => {
    const r = configuracaoFiscalSchema.safeParse({
      ...valido,
      emailContacto: `${"a".repeat(246)}@silva.cv`,
    });
    expect(r.success).toBe(false);
    expect(mensagensDe(r, "emailContacto")).toContain("O email não pode ter mais de 254 caracteres.");
  });

  it("limita a morada a 100 caracteres", () => {
    expect(configuracaoFiscalSchema.safeParse({ ...valido, morada: "a".repeat(100) }).success).toBe(true);
    const r = configuracaoFiscalSchema.safeParse({ ...valido, morada: "a".repeat(101) });
    expect(mensagensDe(r, "morada")).toContain("A morada não pode ter mais de 100 caracteres.");
  });

  it("valida o email", () => {
    const r = configuracaoFiscalSchema.safeParse({ ...valido, emailContacto: "x@" });
    expect(mensagensDe(r, "emailContacto")).toContain("Introduza um email válido.");
  });

  it.each(["", null])("ISENTO sem motivo (%s) é rejeitado", (motivo) => {
    const r = configuracaoFiscalSchema.safeParse({
      ...valido,
      regimeIva: "ISENTO",
      motivoIsencaoCodigo: motivo,
    });
    expect(r.success).toBe(false);
    expect(mensagensDe(r, "motivoIsencaoCodigo")).toContain("Escolha o motivo de isenção.");
  });

  it("ISENTO com motivo é aceite", () => {
    const r = configuracaoFiscalSchema.safeParse({
      ...valido,
      regimeIva: "ISENTO",
      motivoIsencaoCodigo: "5",
    });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.motivoIsencaoCodigo).toBe("5");
  });

  it("nifFiscalPattern é mais estrito que a regra de cliente", () => {
    expect(nifFiscalPattern.test("012345678")).toBe(false);
    expect(nifFiscalPattern.test("112345678")).toBe(true);
  });
});

describe("mensagemErroFaturacao", () => {
  it("extrai code, campo e mensagem de um ApiError de recusa", () => {
    const e = new ApiError("API 409: O NIF não pode ser alterado.", {
      status: 409,
      code: "NIF_BLOQUEADO",
      campo: "nif",
      body: { message: "O NIF não pode ser alterado.", code: "NIF_BLOQUEADO", campo: "nif" },
    });
    expect(mensagemErroFaturacao(e)).toEqual({
      codigo: "NIF_BLOQUEADO",
      campo: "nif",
      mensagem: "O NIF não pode ser alterado.",
    });
  });

  it("devolve camposValidacao para 400 de bean validation", () => {
    const body = { nif: "NIF inválido", morada: "Muito longa" };
    const e = new ApiError(`API 400: ${JSON.stringify(body)}`, { status: 400, body });
    expect(mensagemErroFaturacao(e).camposValidacao).toEqual(body);
  });

  it("devolve objeto vazio para erros que não são ApiError", () => {
    expect(mensagemErroFaturacao(new Error("x"))).toEqual({});
  });
});
