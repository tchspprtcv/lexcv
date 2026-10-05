import { describe, expect, it } from "vitest";

import {
  MOTIVO_TEXTO_MAX,
  MOTIVOS_NOTA_CREDITO,
  TIPOS_CREDITO,
  notaCreditoFormSchema,
  paraPedidoNotaCredito,
  METODOS_PAGAMENTO,
  pagamentoFaturadoFormSchema,
  pagamentoFormSchema,
  paraPedidoPagamentoFaturado,
  rotuloMetodoPagamento,
} from "@/schemas/financeiro";

const MSG_METODO = "Escolha o método de pagamento.";
const MSG_TAXA = "A taxa de retenção deve ser superior a 0 e não superior a 100.";

const base = {
  valorPago: "120000",
  dataPagamento: "",
  metodo: "DINHEIRO",
  aplicarRetencao: false,
  retencaoPercentagem: "",
};

function mensagensDe(dados: unknown, campo: string): string[] {
  const r = pagamentoFaturadoFormSchema.safeParse(dados);
  if (r.success) return [];
  return r.error.issues.filter((i) => i.path[0] === campo).map((i) => i.message);
}

describe("pagamentoFormSchema (faturação desligada, inalterado)", () => {
  it("aceita valor e método livre", () => {
    const r = pagamentoFormSchema.safeParse({ valorPago: "10", metodo: "Transferência" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.metodo).toBe("Transferência");
  });

  it("método continua opcional", () => {
    const r = pagamentoFormSchema.safeParse({ valorPago: "10" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.metodo).toBeUndefined();
  });

  it("não conhece retenção nem chave", () => {
    const r = pagamentoFormSchema.safeParse({ valorPago: "10", retencaoPercentagem: "5" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data).not.toHaveProperty("retencaoPercentagem");
  });
});

describe("METODOS_PAGAMENTO", () => {
  it("tem os cinco métodos com os rótulos do backend", () => {
    expect(METODOS_PAGAMENTO).toEqual([
      { valor: "DINHEIRO", rotulo: "Dinheiro" },
      { valor: "TRANSFERENCIA", rotulo: "Transferência bancária" },
      { valor: "CHEQUE", rotulo: "Cheque" },
      { valor: "CARTAO", rotulo: "Cartão / Multibanco" },
      { valor: "OUTRO", rotulo: "Outro" },
    ]);
  });
});

describe("pagamentoFaturadoFormSchema", () => {
  it("método vazio é recusado com a copy do UI-SPEC", () => {
    expect(mensagensDe({ ...base, metodo: "" }, "metodo")).toEqual([MSG_METODO]);
  });

  it("método desconhecido (PIX) é recusado", () => {
    expect(mensagensDe({ ...base, metodo: "PIX" }, "metodo")).toEqual([MSG_METODO]);
  });

  it.each(["DINHEIRO", "TRANSFERENCIA", "CHEQUE", "CARTAO", "OUTRO"])("método %s é válido", (metodo) => {
    expect(pagamentoFaturadoFormSchema.safeParse({ ...base, metodo }).success).toBe(true);
  });

  it("sem retenção, a taxa é ignorada (mesmo inválida)", () => {
    const r = pagamentoFaturadoFormSchema.safeParse({ ...base, aplicarRetencao: false, retencaoPercentagem: "abc" });
    expect(r.success).toBe(true);
  });

  it.each(["", "0", "-1", "100.01", "12.345", "abc"])(
    "com retenção, a taxa %j é recusada no campo retencaoPercentagem",
    (taxa) => {
      expect(mensagensDe({ ...base, aplicarRetencao: true, retencaoPercentagem: taxa }, "retencaoPercentagem")).toEqual([
        MSG_TAXA,
      ]);
    },
  );

  it("com retenção e taxa ausente é recusada", () => {
    const semTaxa: Partial<typeof base> = { ...base, aplicarRetencao: true };
    delete semTaxa.retencaoPercentagem;
    expect(mensagensDe(semTaxa, "retencaoPercentagem")).toEqual([MSG_TAXA]);
  });

  it.each(["20", "12,5", "0.01", "100", " 7,25 "])("com retenção, a taxa %j é válida", (taxa) => {
    const r = pagamentoFaturadoFormSchema.safeParse({ ...base, aplicarRetencao: true, retencaoPercentagem: taxa });
    expect(r.success).toBe(true);
  });

  it("o valor pago continua obrigatório e positivo", () => {
    expect(mensagensDe({ ...base, valorPago: "" }, "valorPago").length).toBeGreaterThan(0);
    expect(mensagensDe({ ...base, valorPago: "-5" }, "valorPago").length).toBeGreaterThan(0);
  });

  it("data inválida é recusada; vazia é aceite", () => {
    expect(mensagensDe({ ...base, dataPagamento: "não-é-data" }, "dataPagamento")).toEqual(["Data inválida"]);
    expect(pagamentoFaturadoFormSchema.safeParse({ ...base, dataPagamento: "" }).success).toBe(true);
  });
});

describe("paraPedidoPagamentoFaturado", () => {
  function valores(dados: Record<string, unknown>) {
    const r = pagamentoFaturadoFormSchema.safeParse({ ...base, ...dados });
    if (!r.success) throw new Error(JSON.stringify(r.error.issues));
    return r.data;
  }

  it("converte a taxa com vírgula e o valor para número", () => {
    const pedido = paraPedidoPagamentoFaturado(
      valores({ valorPago: "120000.50", dataPagamento: "2026-10-04", metodo: "CHEQUE", aplicarRetencao: true, retencaoPercentagem: "12,5" }),
      7,
    );
    expect(pedido).toEqual({
      honorarioId: 7,
      valorPago: 120000.5,
      dataPagamento: "2026-10-04",
      metodo: "CHEQUE",
      retencaoPercentagem: 12.5,
    });
  });

  it("sem retenção, retencaoPercentagem fica undefined; sem data, dataPagamento fica undefined", () => {
    const pedido = paraPedidoPagamentoFaturado(valores({ retencaoPercentagem: "30" }), 3);
    expect(pedido.retencaoPercentagem).toBeUndefined();
    expect(pedido.dataPagamento).toBeUndefined();
    expect(pedido.metodo).toBe("DINHEIRO");
  });

  it("nunca define a chave de idempotência (é do diálogo)", () => {
    const pedido = paraPedidoPagamentoFaturado(valores({ aplicarRetencao: true, retencaoPercentagem: "10" }), 1);
    expect(pedido).not.toHaveProperty("chaveIdempotencia");
  });
});

describe("rotuloMetodoPagamento (IN-01)", () => {
  it("nomes do enum gravados com a faturação ativa -> rótulo", () => {
    expect(rotuloMetodoPagamento("TRANSFERENCIA")).toBe("Transferência bancária");
    expect(rotuloMetodoPagamento("CARTAO")).toBe("Cartão / Multibanco");
    for (const m of METODOS_PAGAMENTO) expect(rotuloMetodoPagamento(m.valor)).toBe(m.rotulo);
  });

  it("texto livre dos pagamentos legados fica como está; vazio -> travessão", () => {
    expect(rotuloMetodoPagamento("Transferência")).toBe("Transferência");
    expect(rotuloMetodoPagamento(undefined)).toBe("—");
    expect(rotuloMetodoPagamento(null)).toBe("—");
    expect(rotuloMetodoPagamento("")).toBe("—");
  });
});

// ---------------------------------------------------------------------------------------------
// Phase 135 -- formulário da Nota de Crédito (só verificações de UX; o teto é do backend).

const MSG_VALOR_NC = "Indique um valor superior a 0.";
const MSG_MOTIVO_NC = "Escolha o motivo da nota de crédito.";
const MSG_TEXTO_NC = "Descreva o motivo da nota de crédito.";

const baseNc = { tipo: "TOTAL", valor: "", motivoCodigo: "ANULACAO_TOTAL", motivoTexto: "Serviço anulado" };

function mensagensNc(dados: unknown, campo: string): string[] {
  const r = notaCreditoFormSchema.safeParse(dados);
  if (r.success) return [];
  return r.error.issues.filter((i) => i.path[0] === campo).map((i) => i.message);
}

describe("MOTIVOS_NOTA_CREDITO / TIPOS_CREDITO", () => {
  it("motivos pela ordem e com os rótulos do backend", () => {
    expect(MOTIVOS_NOTA_CREDITO).toEqual([
      { valor: "ANULACAO_TOTAL", rotulo: "Anulação total" },
      { valor: "CORRECAO_VALOR", rotulo: "Correção de valor" },
      { valor: "ERRO_DADOS_CLIENTE", rotulo: "Erro nos dados do cliente" },
      { valor: "OUTRO", rotulo: "Outro" },
    ]);
  });

  it("tipos Total e Parcial", () => {
    expect(TIPOS_CREDITO).toEqual([
      { valor: "TOTAL", rotulo: "Total" },
      { valor: "PARCIAL", rotulo: "Parcial" },
    ]);
    expect(MOTIVO_TEXTO_MAX).toBe(200);
  });
});

describe("notaCreditoFormSchema", () => {
  it("o tipo por omissão é TOTAL", () => {
    const r = notaCreditoFormSchema.safeParse({ motivoCodigo: "OUTRO", motivoTexto: "x" });
    expect(r.success).toBe(true);
    if (r.success) expect(r.data.tipo).toBe("TOTAL");
  });

  it("TOTAL ignora o valor", () => {
    expect(notaCreditoFormSchema.safeParse({ ...baseNc, valor: "-1" }).success).toBe(true);
    expect(notaCreditoFormSchema.safeParse({ ...baseNc, valor: undefined }).success).toBe(true);
  });

  it.each(["", "0", "-1", "   ", "abc"])("PARCIAL com valor %j é recusado", (valor) => {
    expect(mensagensNc({ ...baseNc, tipo: "PARCIAL", valor }, "valor")).toEqual([MSG_VALOR_NC]);
  });

  it("PARCIAL sem valor é recusado", () => {
    expect(mensagensNc({ ...baseNc, tipo: "PARCIAL", valor: undefined }, "valor")).toEqual([MSG_VALOR_NC]);
  });

  it.each(["20000", "20000.50", " 15 ", "0.01", "1e3", "12,5", "Infinity", "-0", "999999999999"])(
    "PARCIAL %j segue a mesma regra de formato do valorPago da FR",
    (valor) => {
      const fr = pagamentoFormSchema.safeParse({ valorPago: valor }).success;
      const nc = notaCreditoFormSchema.safeParse({ ...baseNc, tipo: "PARCIAL", valor }).success;
      expect(nc).toBe(fr);
    },
  );

  it("nunca compara o valor com um teto (o backend decide)", () => {
    expect(notaCreditoFormSchema.safeParse({ ...baseNc, tipo: "PARCIAL", valor: "999999999999" }).success).toBe(true);
  });

  it("motivo vazio ou desconhecido é recusado", () => {
    expect(mensagensNc({ ...baseNc, motivoCodigo: "" }, "motivoCodigo")).toEqual([MSG_MOTIVO_NC]);
    expect(mensagensNc({ ...baseNc, motivoCodigo: "XYZ" }, "motivoCodigo")).toEqual([MSG_MOTIVO_NC]);
  });

  it("texto em branco é recusado; 200 aceite; 201 recusado", () => {
    expect(mensagensNc({ ...baseNc, motivoTexto: "   " }, "motivoTexto")).toEqual([MSG_TEXTO_NC]);
    expect(notaCreditoFormSchema.safeParse({ ...baseNc, motivoTexto: "a".repeat(200) }).success).toBe(true);
    expect(mensagensNc({ ...baseNc, motivoTexto: "a".repeat(201) }, "motivoTexto")).toHaveLength(1);
    // Espaços à volta não contam para o limite.
    expect(notaCreditoFormSchema.safeParse({ ...baseNc, motivoTexto: `  ${"a".repeat(200)}  ` }).success).toBe(true);
  });
});

describe("paraPedidoNotaCredito", () => {
  it("TOTAL envia valor null e o texto sem espaços", () => {
    const v = notaCreditoFormSchema.parse({ ...baseNc, valor: "123", motivoTexto: "  Anulado  " });
    const pedido = paraPedidoNotaCredito(v);
    expect(pedido).toEqual({ tipo: "TOTAL", valor: null, motivoCodigo: "ANULACAO_TOTAL", motivoTexto: "Anulado" });
    expect(pedido).not.toHaveProperty("chaveIdempotencia");
  });

  it("PARCIAL envia o valor como número", () => {
    const v = notaCreditoFormSchema.parse({
      tipo: "PARCIAL",
      valor: " 20000.50 ",
      motivoCodigo: "CORRECAO_VALOR",
      motivoTexto: "Valor a mais",
    });
    expect(paraPedidoNotaCredito(v)).toEqual({
      tipo: "PARCIAL",
      valor: 20000.5,
      motivoCodigo: "CORRECAO_VALOR",
      motivoTexto: "Valor a mais",
    });
  });
});
