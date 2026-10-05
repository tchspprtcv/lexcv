import { describe, expect, it } from "vitest";

import type { ErroNotaCredito } from "@/lib/erros-emissao";
import { tentativaParaPedido } from "@/lib/idempotencia";
import {
  reagirAErroNotaCredito,
} from "@/lib/nota-credito-dialogo";

const PEDIDO = {
  tipo: "PARCIAL" as const,
  valor: 20000,
  motivoCodigo: "CORRECAO_VALOR" as const,
  motivoTexto: "Desconto acordado",
};

describe("chave de idempotência da NC (conteúdo { documentoOrigemId, ...pedido })", () => {
  it("o mesmo pedido sobre a mesma FR reutiliza a chave por resolver; sobre outra FR gera uma nova", () => {
    let n = 0;
    const gerar = () => `chave-${++n}`;
    const a = { ...tentativaParaPedido(null, { documentoOrigemId: "fr-1", ...PEDIDO }, gerar), porResolver: true };
    const mesma = tentativaParaPedido(a, { documentoOrigemId: "fr-1", ...PEDIDO }, gerar);
    const outra = tentativaParaPedido(a, { documentoOrigemId: "fr-2", ...PEDIDO }, gerar);
    expect(mesma.chave).toBe("chave-1");
    expect(outra.chave).toBe("chave-2");
  });

  it("trocar para Total (valor null) muda o pedido e gera uma chave nova", () => {
    let n = 0;
    const gerar = () => `chave-${++n}`;
    const a = { ...tentativaParaPedido(null, { documentoOrigemId: "fr-1", ...PEDIDO }, gerar), porResolver: true };
    const total = tentativaParaPedido(a, { documentoOrigemId: "fr-1", ...PEDIDO, tipo: "TOTAL", valor: null }, gerar);
    expect(total.chave).toBe("chave-2");
  });
});

describe("reagirAErroNotaCredito", () => {
  const casos: Array<[string, ErroNotaCredito | null, "formulario" | "pre-visualizacao", unknown]> = [
    ["null (toast/sessão) não faz nada", null, "pre-visualizacao", { acao: "ignorar" }],
    [
      "campo volta ao passo 1 com o erro no campo",
      { tipo: "campo", campo: "valor", mensagem: "m" },
      "pre-visualizacao",
      { acao: "campo", campo: "valor", mensagem: "m", passo: "formulario" },
    ],
    [
      "excede volta ao passo 1 com banner",
      { tipo: "excede", mensagem: "m" },
      "pre-visualizacao",
      { acao: "banner", mensagem: "m", passo: "formulario", definitivo: false },
    ],
    [
      "chave reutilizada volta ao passo 1 com banner",
      { tipo: "chave-reutilizada", mensagem: "m" },
      "pre-visualizacao",
      { acao: "banner", mensagem: "m", passo: "formulario", definitivo: false },
    ],
    [
      "banner definitivo volta ao passo 1 e troca o rótulo de fecho",
      { tipo: "banner", codigo: "NC_SOBRE_NC", mensagem: "m", definitivo: true },
      "pre-visualizacao",
      { acao: "banner", mensagem: "m", passo: "formulario", definitivo: true },
    ],
    [
      "banner não definitivo fica no passo atual",
      { tipo: "banner", codigo: "DATA_EMISSAO_ALTERADA", mensagem: "m", definitivo: false },
      "pre-visualizacao",
      { acao: "banner", mensagem: "m", passo: "pre-visualizacao", definitivo: false },
    ],
    [
      "rede fica no passo atual (nova tentativa com a mesma chave)",
      { tipo: "rede", mensagem: "m" },
      "pre-visualizacao",
      { acao: "banner", mensagem: "m", passo: "pre-visualizacao", definitivo: false },
    ],
    [
      "rede na pré-visualização fica no passo 1",
      { tipo: "rede", mensagem: "m" },
      "formulario",
      { acao: "banner", mensagem: "m", passo: "formulario", definitivo: false },
    ],
    ["não encontrado fecha o diálogo", { tipo: "nao-encontrado" }, "pre-visualizacao", { acao: "fechar" }],
  ];

  it.each(casos)("%s", (_nome, erro, passo, esperado) => {
    expect(reagirAErroNotaCredito(erro, passo)).toEqual(esperado);
  });
});
