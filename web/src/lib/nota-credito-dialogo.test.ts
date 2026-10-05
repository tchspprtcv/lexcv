import { describe, expect, it } from "vitest";

import type { ErroNotaCredito } from "@/lib/erros-emissao";
import { ApiError } from "@/lib/api";
import { tentativaParaPedido } from "@/lib/idempotencia";
import {
  pedidoChaveNotaCredito,
  reagirAErroNotaCredito,
  rotuloFecharNotaCredito,
  tentativaDepoisDeFalha,
} from "@/lib/nota-credito-dialogo";

const PEDIDO = {
  tipo: "PARCIAL" as const,
  valor: 20000,
  motivoCodigo: "CORRECAO_VALOR" as const,
  motivoTexto: "Desconto acordado",
};

function apiError(status: number, code?: string): ApiError {
  return new ApiError("x", { status, code, body: { message: "x", code } });
}

describe("pedidoChaveNotaCredito", () => {
  it("inclui o documento de origem no conteúdo a que a chave pertence", () => {
    expect(pedidoChaveNotaCredito("fr-1", PEDIDO)).toEqual({ documentoOrigemId: "fr-1", ...PEDIDO });
  });

  it("o mesmo pedido sobre outra FR gera uma chave nova mesmo com a tentativa por resolver", () => {
    let n = 0;
    const gerar = () => `chave-${++n}`;
    const a = { ...tentativaParaPedido(null, pedidoChaveNotaCredito("fr-1", PEDIDO), gerar), porResolver: true };
    const mesma = tentativaParaPedido(a, pedidoChaveNotaCredito("fr-1", PEDIDO), gerar);
    const outra = tentativaParaPedido(a, pedidoChaveNotaCredito("fr-2", PEDIDO), gerar);
    expect(mesma.chave).toBe("chave-1");
    expect(outra.chave).toBe("chave-2");
  });

  it("não leva chave de idempotência", () => {
    expect(pedidoChaveNotaCredito("fr-1", PEDIDO)).not.toHaveProperty("chaveIdempotencia");
  });
});

describe("tentativaDepoisDeFalha", () => {
  const tentativa = { pedido: "{}", chave: "k", porResolver: false };

  it("recusa 4xx processada (409/422/404) descarta a chave", () => {
    for (const s of [404, 409, 422]) {
      expect(tentativaDepoisDeFalha(tentativa, apiError(s))).toBeNull();
    }
  });

  it("falha ambígua (rede, 5xx, 401/403/408/429) mantém a chave e marca-a por resolver", () => {
    for (const e of [new TypeError("fetch"), apiError(500), apiError(503), apiError(401), apiError(403),
      apiError(408), apiError(429)]) {
      expect(tentativaDepoisDeFalha(tentativa, e)).toEqual({ ...tentativa, porResolver: true });
    }
  });

  it("sem tentativa continua sem tentativa", () => {
    expect(tentativaDepoisDeFalha(null, apiError(500))).toBeNull();
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

describe("rotuloFecharNotaCredito", () => {
  it("passo 1 mostra 'Fechar sem emitir' e 'Fechar' depois de um erro definitivo", () => {
    expect(rotuloFecharNotaCredito(false)).toBe("Fechar sem emitir");
    expect(rotuloFecharNotaCredito(true)).toBe("Fechar");
  });
});
