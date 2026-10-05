import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api";
import {
  COPY_CHAVE_REUTILIZADA,
  COPY_DATA_PAGAMENTO,
  COPY_FALLBACK_CAMPO,
  COPY_GUARDA_CLIENTE,
  COPY_LOCALIDADE,
  COPY_GUARDA_PAGAMENTO,
  COPY_MORADA,
  COPY_NIF,
  COPY_NOME,
  COPY_REDE,
  COPY_SEM_PERMISSAO,
  STATUS_INLINE_EMISSAO,
  COPY_GUARDA_ESTORNO,
  COPY_NC_CHAVE_REUTILIZADA,
  COPY_NC_DATA,
  COPY_NC_DESLIGADA,
  COPY_NC_EXCEDE,
  COPY_NC_INDISPONIVEL,
  COPY_NC_REDE_EMISSAO,
  COPY_NC_REDE_PRE_VISUALIZACAO,
  COPY_NC_SEM_PERMISSAO,
  COPY_NC_VALORES_ALTERADOS,
  COPY_NC_DADOS_EM_FALTA,
  COPY_NC_SOBRE_NC,
  construirQueryDocumentosFiscais,
  desfechoDefinitivo,
  interpretarErroEmissao,
  interpretarErroNotaCredito,
  mensagemGuardaFiscal,
} from "@/lib/erros-emissao";
import {
  modoFormularioPagamento,
  PERMISSAO_EMISSAO_NOTA_CREDITO,
  podeEmitirNotaCredito,
  podeLerDocumentosFiscais,
  podeRegistarPagamentos,
} from "@/hooks/use-faturacao";
import { hasScopedPermission } from "@/lib/permissions";

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
    expect(COPY_LOCALIDADE).toBe(
      "A localidade do cliente não pode ter mais de 100 caracteres. Corrija o cliente e tente de novo.",
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
    ["localidade", COPY_LOCALIDADE],
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

  it("503 FATURACAO_OCUPADA do backend -> rede (repetir com a mesma chave) com a mensagem do backend (IN-02)", () => {
    expect(
      interpretarErroEmissao(
        erro(503, "FATURACAO_OCUPADA", undefined, "A faturação está ocupada. Tente novamente dentro de instantes."),
      ),
    ).toEqual({ tipo: "rede", mensagem: "A faturação está ocupada. Tente novamente dentro de instantes." });
  });

  it("os 5xx da emissão são tratados inline, sem toast duplicado (IN-02)", () => {
    expect(STATUS_INLINE_EMISSAO).toEqual([409, 422, 500, 502, 503, 504]);
  });

  it.each([500, 502, 504])("ApiError %i -> rede com a copy de rede, mesmo com code", (status) => {
    expect(interpretarErroEmissao(erro(status, "FATURACAO_OCUPADA", undefined, "Ocupado"))).toEqual({
      tipo: "rede",
      mensagem: COPY_REDE,
    });
  });

  it("503 sem code (proxy) ou sem mensagem -> rede com a copy de rede", () => {
    expect(interpretarErroEmissao(erro(503, undefined, undefined, "Service Unavailable"))).toEqual({
      tipo: "rede",
      mensagem: COPY_REDE,
    });
    expect(interpretarErroEmissao(erro(503, "FATURACAO_OCUPADA"))).toEqual({ tipo: "rede", mensagem: COPY_REDE });
  });

  it("ApiError 401 -> null (o useMe trata da sessão)", () => {
    expect(interpretarErroEmissao(erro(401))).toBeNull();
  });

  it("ApiError 403 -> banner de permissão (o apiFetch não mostra toast para 403)", () => {
    expect(interpretarErroEmissao(erro(403, undefined, undefined, "Forbidden"))).toEqual({
      tipo: "banner",
      mensagem: COPY_SEM_PERMISSAO,
    });
    expect(COPY_SEM_PERMISSAO).toBe("Não tem permissão para registar pagamentos.");
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

describe("podeRegistarPagamentos / modoFormularioPagamento (WR-03, WR-04)", () => {
  const EDIT = ["financeiro:view", "financeiro:edit"];

  it("exige EXATAMENTE financeiro:edit (manage sem edit não chega, como no backend)", () => {
    expect(podeRegistarPagamentos(["financeiro:edit"])).toBe(true);
    expect(podeRegistarPagamentos(["financeiro:manage", "financeiro:view"])).toBe(false);
    expect(podeRegistarPagamentos(["financeiro:view"])).toBe(false);
    expect(podeRegistarPagamentos(undefined)).toBe(false);
  });

  it("sem financeiro:edit exato -> sem-permissao, seja qual for o estado", () => {
    expect(modoFormularioPagamento(["financeiro:manage"], { isError: false, data: { ativa: false } })).toBe(
      "sem-permissao",
    );
  });

  it("estado conhecido -> ativa / desligada", () => {
    expect(modoFormularioPagamento(EDIT, { isError: false, data: { ativa: true } })).toBe("ativa");
    expect(modoFormularioPagamento(EDIT, { isError: false, data: { ativa: false } })).toBe("desligada");
  });

  it("estado desconhecido nunca é desligada: a carregar ou erro", () => {
    expect(modoFormularioPagamento(EDIT, { isError: false, data: undefined })).toBe("a-carregar");
    expect(modoFormularioPagamento(EDIT, { isError: true, data: undefined })).toBe("erro");
  });

  it("com dados em cache, uma nova leitura falhada mantém o modo conhecido", () => {
    expect(modoFormularioPagamento(EDIT, { isError: true, data: { ativa: true } })).toBe("ativa");
  });
});

describe("podeEmitirNotaCredito (Phase 135, gate exato financeiro:manage)", () => {
  it("é a autoridade exata do backend", () => {
    expect(PERMISSAO_EMISSAO_NOTA_CREDITO).toBe("financeiro:manage");
  });

  it("só financeiro:manage abre o botão", () => {
    expect(podeEmitirNotaCredito(["financeiro:manage"])).toBe(true);
    expect(podeEmitirNotaCredito(["financeiro:view", "financeiro:edit", "financeiro:create"])).toBe(false);
    expect(podeEmitirNotaCredito([])).toBe(false);
    expect(podeEmitirNotaCredito(undefined)).toBe(false);
  });

  it("coincide com hasScopedPermission(financeiro, manage)", () => {
    for (const perms of [["financeiro:manage"], ["financeiro:edit"], ["financeiro:view"], []]) {
      expect(podeEmitirNotaCredito(perms)).toBe(hasScopedPermission(perms, "financeiro", "manage"));
    }
  });
});

describe("interpretarErroNotaCredito (Phase 135, UI-SPEC Surface 1)", () => {
  it("copy exata do UI-SPEC", () => {
    expect(COPY_NC_EXCEDE).toBe(
      "O valor indicado excede o que ainda pode ser creditado nesta fatura-recibo. Reduza o valor ou escolha crédito total.",
    );
    expect(COPY_NC_SOBRE_NC).toBe(
      "Não é possível creditar uma nota de crédito. Emita a nota de crédito sobre a fatura-recibo original.",
    );
    expect(COPY_NC_DESLIGADA).toBe(
      "A faturação está desligada. Ative a faturação nas definições para emitir notas de crédito.",
    );
    expect(COPY_NC_DATA).toBe(
      "A nota de crédito só pode ser emitida com a data de hoje. Atualize a página e tente de novo.",
    );
    expect(COPY_NC_CHAVE_REUTILIZADA).toBe(
      "Este pedido já foi usado com valores diferentes. Reveja os dados e emita a nota de crédito de novo.",
    );
    expect(COPY_NC_INDISPONIVEL).toBe(
      "O serviço de faturação está temporariamente indisponível. Aguarde um momento e tente novamente.",
    );
    expect(COPY_NC_SEM_PERMISSAO).toBe("Não tem permissão para emitir notas de crédito.");
  });

  it("falha de rede usa a copy da fase", () => {
    expect(interpretarErroNotaCredito(new TypeError("Failed to fetch"), "pre-visualizacao")).toEqual({
      tipo: "rede",
      mensagem: "Não foi possível calcular a nota de crédito. Verifique a ligação e tente novamente.",
    });
    expect(interpretarErroNotaCredito(new TypeError("Failed to fetch"), "emissao")).toEqual({
      tipo: "rede",
      mensagem: "Não foi possível emitir a nota de crédito. Verifique a ligação e tente novamente.",
    });
    expect(COPY_NC_REDE_PRE_VISUALIZACAO).not.toBe(COPY_NC_REDE_EMISSAO);
  });

  it("503 (com ou sem code) é serviço indisponível; outros 5xx são rede da fase", () => {
    for (const e of [erro(503, "FATURACAO_OCUPADA", undefined, "ocupada"), erro(503, "SERIE_INDISPONIVEL"), erro(503)]) {
      expect(interpretarErroNotaCredito(e, "emissao")).toEqual({ tipo: "rede", mensagem: COPY_NC_INDISPONIVEL });
    }
    expect(interpretarErroNotaCredito(erro(500), "emissao")).toEqual({ tipo: "rede", mensagem: COPY_NC_REDE_EMISSAO });
    expect(interpretarErroNotaCredito(erro(504), "pre-visualizacao")).toEqual({
      tipo: "rede",
      mensagem: COPY_NC_REDE_PRE_VISUALIZACAO,
    });
  });

  it("409 NC_EXCEDE_ORIGINAL é 'excede' com a mensagem do backend (IN-01) ou a copy do UI-SPEC", () => {
    const e = erro(409, "NC_EXCEDE_ORIGINAL", "valor", "Esta fatura-recibo já foi totalmente creditada.");
    expect(interpretarErroNotaCredito(e, "emissao")).toEqual({
      tipo: "excede",
      mensagem: "Esta fatura-recibo já foi totalmente creditada.",
    });
    expect(interpretarErroNotaCredito(erro(409, "NC_EXCEDE_ORIGINAL", "valor"), "emissao")).toEqual({
      tipo: "excede",
      mensagem: COPY_NC_EXCEDE,
    });
  });

  it("NC_SOBRE_NC e FATURACAO_DESLIGADA são banners definitivos", () => {
    expect(interpretarErroNotaCredito(erro(422, "NC_SOBRE_NC", undefined, "x"), "pre-visualizacao")).toEqual({
      tipo: "banner",
      codigo: "NC_SOBRE_NC",
      mensagem: COPY_NC_SOBRE_NC,
      definitivo: true,
    });
    expect(interpretarErroNotaCredito(erro(409, "FATURACAO_DESLIGADA", undefined, "x"), "emissao")).toEqual({
      tipo: "banner",
      codigo: "FATURACAO_DESLIGADA",
      mensagem: COPY_NC_DESLIGADA,
      definitivo: true,
    });
  });

  it("409 DATA_EMISSAO_ALTERADA é banner com a copy da data", () => {
    expect(interpretarErroNotaCredito(erro(409, "DATA_EMISSAO_ALTERADA", undefined, "x"), "emissao")).toEqual({
      tipo: "banner",
      codigo: "DATA_EMISSAO_ALTERADA",
      mensagem: "A nota de crédito só pode ser emitida com a data de hoje. Atualize a página e tente de novo.",
      definitivo: false,
    });
  });

  it("409 NC_VALORES_ALTERADOS (WR-01) pede nova pré-visualização com a mensagem do backend ou a copy fixa", () => {
    expect(interpretarErroNotaCredito(erro(409, "NC_VALORES_ALTERADOS", undefined, "m"), "emissao")).toEqual({
      tipo: "valores-alterados",
      mensagem: "m",
    });
    expect(interpretarErroNotaCredito(erro(409, "NC_VALORES_ALTERADOS"), "emissao")).toEqual({
      tipo: "valores-alterados",
      mensagem: COPY_NC_VALORES_ALTERADOS,
    });
  });

  it("409 CHAVE_REUTILIZADA usa a copy da NC (não a do pagamento)", () => {
    const r = interpretarErroNotaCredito(erro(409, "CHAVE_REUTILIZADA", undefined, "x"), "emissao");
    expect(r).toEqual({
      tipo: "chave-reutilizada",
      mensagem: "Este pedido já foi usado com valores diferentes. Reveja os dados e emita a nota de crédito de novo.",
    });
  });

  it.each(["tipo", "valor", "motivoCodigo", "motivoTexto"] as const)("422 com campo %s vai para o campo", (campo) => {
    expect(interpretarErroNotaCredito(erro(422, "X", campo, "Mensagem do backend."), "pre-visualizacao")).toEqual({
      tipo: "campo",
      campo,
      mensagem: "Mensagem do backend.",
    });
    expect(interpretarErroNotaCredito(erro(422, "X", campo), "pre-visualizacao")).toEqual({
      tipo: "campo",
      campo,
      mensagem: "Verifique os campos assinalados.",
    });
  });

  it("422 com campo desconhecido é banner não definitivo com a mensagem do backend", () => {
    expect(interpretarErroNotaCredito(erro(422, "CHAVE_IDEMPOTENCIA_OBRIGATORIA", "chaveIdempotencia", "m"), "emissao")).toEqual({
      tipo: "banner",
      codigo: "CHAVE_IDEMPOTENCIA_OBRIGATORIA",
      mensagem: "m",
      definitivo: false,
    });
  });

  it("404 é nao-encontrado; 403 é banner sem permissão; 401 é nulo", () => {
    expect(interpretarErroNotaCredito(erro(404, "DOCUMENTO_FISCAL_NAO_ENCONTRADO"), "emissao")).toEqual({
      tipo: "nao-encontrado",
    });
    expect(interpretarErroNotaCredito(erro(403), "emissao")).toEqual({
      tipo: "banner",
      mensagem: "Não tem permissão para emitir notas de crédito.",
      definitivo: false,
    });
    expect(interpretarErroNotaCredito(erro(401), "emissao")).toBeNull();
  });

  it("404 sem código também fecha; outros 404 (WR-02) são banner definitivo com mensagem", () => {
    expect(interpretarErroNotaCredito(erro(404), "emissao")).toEqual({ tipo: "nao-encontrado" });
    expect(interpretarErroNotaCredito(erro(404, "HONORARIO_NAO_ENCONTRADO", undefined, "m"), "emissao")).toEqual({
      tipo: "banner",
      codigo: "HONORARIO_NAO_ENCONTRADO",
      mensagem: "m",
      definitivo: true,
    });
    expect(interpretarErroNotaCredito(erro(404, "CLIENTE_NAO_ENCONTRADO"), "pre-visualizacao")).toEqual({
      tipo: "banner",
      codigo: "CLIENTE_NAO_ENCONTRADO",
      mensagem: COPY_NC_DADOS_EM_FALTA,
      definitivo: true,
    });
  });

  it("status fora dos inline (ex. 400) devolve nulo: o apiFetch já mostrou o toast", () => {
    expect(interpretarErroNotaCredito(erro(400, undefined, undefined, "x"), "emissao")).toBeNull();
  });

  it("interpretarErroEmissao continua com a copy da fatura-recibo", () => {
    expect(interpretarErroEmissao(erro(409, "CHAVE_REUTILIZADA"))).toEqual({
      tipo: "chave-reutilizada",
      mensagem: COPY_CHAVE_REUTILIZADA,
    });
    expect(interpretarErroEmissao(new TypeError("x"))).toEqual({ tipo: "rede", mensagem: COPY_REDE });
  });
});

describe("mensagemGuardaFiscal: estorno (Phase 135)", () => {
  it("409 PAGAMENTO_ESTORNO tem copy própria", () => {
    expect(COPY_GUARDA_ESTORNO).toBe("Este estorno pertence a uma nota de crédito emitida e não pode ser apagado.");
    expect(mensagemGuardaFiscal(erro(409, "PAGAMENTO_ESTORNO"))).toBe(
      "Este estorno pertence a uma nota de crédito emitida e não pode ser apagado.",
    );
    expect(mensagemGuardaFiscal(erro(409, "PAGAMENTO_ESTORNO", undefined, "Do backend."))).toBe("Do backend.");
  });

  it("a guarda da FR fica igual", () => {
    expect(mensagemGuardaFiscal(erro(409, "PAGAMENTO_FATURADO"))).toBe(COPY_GUARDA_PAGAMENTO);
  });
});
