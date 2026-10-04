import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api";
import {
  COPY_CHAVE_REUTILIZADA,
  COPY_DATA_PAGAMENTO,
  COPY_FALLBACK_CAMPO,
  COPY_GUARDA_CLIENTE,
  COPY_GUARDA_PAGAMENTO,
  COPY_MORADA,
  COPY_NIF,
  COPY_NOME,
  COPY_REDE,
  construirQueryDocumentosFiscais,
  desfechoDefinitivo,
  interpretarErroEmissao,
  mensagemGuardaFiscal,
} from "@/lib/erros-emissao";
import { podeLerDocumentosFiscais } from "@/hooks/use-faturacao";

function erro(status: number, code?: string, campo?: string, message?: string) {
  const body: Record<string, string> = {};
  if (message !== undefined) body.message = message;
  if (code) body.code = code;
  if (campo) body.campo = campo;
  return new ApiError(`API ${status}: ${message ?? ""}`, { status, code, campo, body });
}

describe("copy do UI-SPEC", () => {
  it("as constantes têm o texto exato", () => {
    expect(COPY_DATA_PAGAMENTO).toBe(
      "Com a faturação ativa, a data do pagamento tem de ser a de hoje. Escolha a data de hoje ou deixe o campo vazio.",
    );
    expect(COPY_NIF).toBe(
      "O cliente não tem um NIF válido. Corrija o NIF do cliente (9 dígitos, começa por 1 a 9) e tente de novo.",
    );
    expect(COPY_NOME).toBe("O nome do cliente deve ter entre 3 e 150 caracteres. Corrija o cliente e tente de novo.");
    expect(COPY_MORADA).toBe(
      "A morada do cliente é obrigatória e não pode ter mais de 100 caracteres. Corrija o cliente e tente de novo.",
    );
    expect(COPY_CHAVE_REUTILIZADA).toBe(
      "Este pedido já foi usado com valores diferentes. Reveja os dados e registe o pagamento de novo.",
    );
    expect(COPY_REDE).toBe("Não foi possível emitir a fatura-recibo. Verifique a ligação e tente novamente.");
    expect(COPY_FALLBACK_CAMPO).toBe("Verifique os campos assinalados.");
  });
});

describe("interpretarErroEmissao", () => {
  it("422 DATA_PAGAMENTO_RETROATIVA -> erro no campo dataPagamento com a copy do UI-SPEC", () => {
    expect(interpretarErroEmissao(erro(422, "DATA_PAGAMENTO_RETROATIVA", "dataPagamento", "x"))).toEqual({
      tipo: "campo",
      campo: "dataPagamento",
      mensagem: COPY_DATA_PAGAMENTO,
    });
  });

  it.each([
    ["nif", COPY_NIF],
    ["nome", COPY_NOME],
    ["morada", COPY_MORADA],
  ] as const)("422 ADQUIRENTE_INCOMPLETO campo %s -> banner do adquirente", (campo, copy) => {
    expect(interpretarErroEmissao(erro(422, "ADQUIRENTE_INCOMPLETO", campo, "backend"))).toEqual({
      tipo: "adquirente",
      campo,
      mensagem: copy,
    });
  });

  it("422 ADQUIRENTE_INCOMPLETO sem campo conhecido -> banner com a mensagem do backend", () => {
    expect(interpretarErroEmissao(erro(422, "ADQUIRENTE_INCOMPLETO", undefined, "Dados do cliente em falta."))).toEqual({
      tipo: "banner",
      codigo: "ADQUIRENTE_INCOMPLETO",
      mensagem: "Dados do cliente em falta.",
    });
  });

  it("409 CHAVE_REUTILIZADA -> chave-reutilizada", () => {
    expect(interpretarErroEmissao(erro(409, "CHAVE_REUTILIZADA", undefined, "backend"))).toEqual({
      tipo: "chave-reutilizada",
      mensagem: COPY_CHAVE_REUTILIZADA,
    });
  });

  it.each([
    ["METODO_PAGAMENTO_INVALIDO", "metodo"],
    ["RETENCAO_INVALIDA", "retencaoPercentagem"],
    ["VALOR_PAGO_INVALIDO", "valorPago"],
  ] as const)("422 %s -> erro no campo %s com a mensagem do backend", (code, campo) => {
    expect(interpretarErroEmissao(erro(422, code, campo, "Mensagem do backend."))).toEqual({
      tipo: "campo",
      campo,
      mensagem: "Mensagem do backend.",
    });
  });

  it("422 de campo sem mensagem -> fallback 'Verifique os campos assinalados.'", () => {
    expect(interpretarErroEmissao(erro(422, "RETENCAO_INVALIDA", "retencaoPercentagem"))).toEqual({
      tipo: "campo",
      campo: "retencaoPercentagem",
      mensagem: COPY_FALLBACK_CAMPO,
    });
  });

  it.each([
    [409, "FATURACAO_DESLIGADA"],
    [422, "CONFIGURACAO_FISCAL_INCOMPLETA"],
    [409, "PROCESSO_ALTERADO_TENTE_NOVAMENTE"],
    [409, "DATA_EMISSAO_ALTERADA"],
    [422, "CHAVE_IDEMPOTENCIA_OBRIGATORIA"],
  ] as const)("%i %s -> banner com a mensagem do backend", (status, code) => {
    expect(interpretarErroEmissao(erro(status, code, undefined, `Mensagem ${code}.`))).toEqual({
      tipo: "banner",
      codigo: code,
      mensagem: `Mensagem ${code}.`,
    });
  });

  it("falha de rede (TypeError do fetch) -> rede", () => {
    expect(interpretarErroEmissao(new TypeError("Failed to fetch"))).toEqual({ tipo: "rede", mensagem: COPY_REDE });
  });

  it.each([500, 502, 503])("ApiError %i -> rede", (status) => {
    expect(interpretarErroEmissao(erro(status, "FATURACAO_OCUPADA", undefined, "Ocupado"))).toEqual({
      tipo: "rede",
      mensagem: COPY_REDE,
    });
  });

  it.each([401, 403])("ApiError %i -> null (o apiFetch já tratou)", (status) => {
    expect(interpretarErroEmissao(erro(status))).toBeNull();
  });

  it("outros status (400/404) -> null: o toast do apiFetch já os mostrou", () => {
    expect(interpretarErroEmissao(erro(400, undefined, undefined, "x"))).toBeNull();
    expect(interpretarErroEmissao(erro(404, "HONORARIO_OBRIGATORIO", undefined, "x"))).toBeNull();
  });
});

describe("mensagemGuardaFiscal", () => {
  it.each([
    "PAGAMENTO_FATURADO",
    "CLIENTE_COM_DOCUMENTOS_FISCAIS",
    "PROCESSO_COM_DOCUMENTOS_FISCAIS",
    "HONORARIO_COM_DOCUMENTOS_FISCAIS",
  ])("409 %s -> mensagem do backend", (code) => {
    expect(mensagemGuardaFiscal(erro(409, code, undefined, `Recusa ${code}`))).toBe(`Recusa ${code}`);
  });

  it("409 de guarda sem mensagem no corpo -> copy da Surface 5", () => {
    expect(mensagemGuardaFiscal(erro(409, "PAGAMENTO_FATURADO"))).toBe(COPY_GUARDA_PAGAMENTO);
    expect(mensagemGuardaFiscal(erro(409, "CLIENTE_COM_DOCUMENTOS_FISCAIS"))).toBe(COPY_GUARDA_CLIENTE);
  });

  it("outros erros -> null", () => {
    expect(mensagemGuardaFiscal(erro(409, "CHAVE_REUTILIZADA", undefined, "x"))).toBeNull();
    expect(mensagemGuardaFiscal(erro(422, "PAGAMENTO_FATURADO", undefined, "x"))).toBeNull();
    expect(mensagemGuardaFiscal(erro(409, undefined, undefined, "Não é possível eliminar um honorário"))).toBeNull();
    expect(mensagemGuardaFiscal(new TypeError("Failed to fetch"))).toBeNull();
    expect(mensagemGuardaFiscal(undefined)).toBeNull();
  });
});

describe("construirQueryDocumentosFiscais", () => {
  it("só página e tamanho", () => {
    expect(construirQueryDocumentosFiscais({ page: 0, size: 10 })).toBe("page=0&size=10");
  });

  it("todos os filtros, na ordem fixa e codificados", () => {
    expect(
      construirQueryDocumentosFiscais({
        clienteId: "a b&c",
        de: "2026-01-01",
        ate: "2026-12-31",
        tipo: "FR",
        estado: "PENDENTE",
        page: 2,
        size: 25,
      }),
    ).toBe("clienteId=a+b%26c&de=2026-01-01&ate=2026-12-31&tipo=FR&estado=PENDENTE&page=2&size=25");
  });

  it("strings vazias ou só espaços são omitidas", () => {
    expect(
      construirQueryDocumentosFiscais({ clienteId: "", de: " ", ate: "", tipo: "", estado: "", page: 1, size: 10 }),
    ).toBe("page=1&size=10");
  });
});

describe("podeLerDocumentosFiscais", () => {
  it("exige exatamente financeiro:view, como o backend (sem o fallback edit/manage => view)", () => {
    expect(podeLerDocumentosFiscais(["financeiro:view"])).toBe(true);
    expect(podeLerDocumentosFiscais(["financeiro:edit"])).toBe(false);
    expect(podeLerDocumentosFiscais(["financeiro:manage"])).toBe(false);
    expect(podeLerDocumentosFiscais([])).toBe(false);
    expect(podeLerDocumentosFiscais(undefined)).toBe(false);
  });
});

describe("desfechoDefinitivo (CR-02)", () => {
  it.each([400, 404, 409, 422])("recusa %i processada pelo backend é definitiva", (status) => {
    expect(desfechoDefinitivo(erro(status, "X"))).toBe(true);
  });

  it.each([401, 403, 408, 429, 500, 502, 503, 504])("status %i deixa o desfecho por resolver", (status) => {
    expect(desfechoDefinitivo(erro(status))).toBe(false);
  });

  it("falha de rede (TypeError do fetch) deixa o desfecho por resolver", () => {
    expect(desfechoDefinitivo(new TypeError("Failed to fetch"))).toBe(false);
  });
});
