import { describe, expect, it } from "vitest";

import {
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
