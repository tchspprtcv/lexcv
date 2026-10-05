import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api";
import { interpretarErroNotaCredito, type ErroNotaCredito } from "@/lib/erros-emissao";
import { tentativaParaPedido, type TentativaEmissao } from "@/lib/idempotencia";
import {
  lembrarTentativaNc,
  reagirAErroNotaCredito,
  reiniciarTentativasNcParaTestes,
  tentativaDepoisDeFalhaNc,
  tentativaParaPedidoNc,
} from "@/lib/nota-credito-dialogo";

function erro(status: number, code?: string, message?: string) {
  const body: Record<string, string> = {};
  if (message !== undefined) body.message = message;
  if (code) body.code = code;
  return new ApiError(`API ${status}: ${message ?? ""}`, { status, code, body });
}

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
      "valores alterados (WR-01) volta ao passo 1 para uma nova pré-visualização",
      { tipo: "valores-alterados", mensagem: "m" },
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

describe("tentativaDepoisDeFalhaNc (CR-02: o botão de emissão nunca fica mudo)", () => {
  const base: TentativaEmissao = { pedido: '{"documentoOrigemId":"fr-1"}', chave: "chave-1", porResolver: false };

  it.each([
    ["409 DATA_EMISSAO_ALTERADA", erro(409, "DATA_EMISSAO_ALTERADA", "m")],
    ["409 PROCESSO_ALTERADO_TENTE_NOVAMENTE", erro(409, "PROCESSO_ALTERADO_TENTE_NOVAMENTE", "m")],
    ["409 sem código conhecido", erro(409, "OUTRO_CODIGO", "m")],
    ["422 sem campo", erro(422, "CHAVE_IDEMPOTENCIA_OBRIGATORIA", "m")],
  ])("%s: recusa definitiva gera chave nova para o mesmo conteúdo", (_nome, e) => {
    const t = tentativaDepoisDeFalhaNc(base, e, () => "chave-2");
    expect(t).toEqual({ pedido: base.pedido, chave: "chave-2", porResolver: false });
  });

  it.each([
    ["rede", new TypeError("Failed to fetch")],
    ["500", erro(500)],
    ["503 FATURACAO_OCUPADA", erro(503, "FATURACAO_OCUPADA")],
    ["504 do proxy", erro(504)],
    ["401", erro(401)],
    ["403", erro(403)],
    ["408", erro(408)],
    ["429", erro(429)],
  ])("%s: falha ambígua mantém a chave e marca-a por resolver", (_nome, e) => {
    const t = tentativaDepoisDeFalhaNc(base, e, () => "nunca");
    expect(t).toEqual({ ...base, porResolver: true });
  });

  it("uma chave por resolver continua por resolver depois de outra falha ambígua", () => {
    const porResolver = { ...base, porResolver: true };
    expect(tentativaDepoisDeFalhaNc(porResolver, erro(502), () => "nunca")).toEqual(porResolver);
  });

  // O defeito: depois de uma recusa que fica no passo 2, o botão continuava ativo e o clique não
  // fazia nada porque a tentativa ficava nula. Para qualquer falha da emissão, ou o diálogo sai do
  // passo 2, ou há uma tentativa válida para o próximo clique.
  it.each([
    erro(409, "DATA_EMISSAO_ALTERADA", "m"),
    erro(409, "PROCESSO_ALTERADO_TENTE_NOVAMENTE", "m"),
    erro(409, "OUTRO_CODIGO"),
    erro(422, "X", "m"),
    erro(409, "NC_EXCEDE_ORIGINAL", "m"),
    erro(409, "NC_VALORES_ALTERADOS", "m"),
    erro(409, "CHAVE_REUTILIZADA", "m"),
    erro(409, "FATURACAO_DESLIGADA", "m"),
    erro(403),
    erro(503),
    new TypeError("Failed to fetch"),
  ])("depois de %s o próximo clique em Emitir envia um pedido", (e) => {
    const reacao = reagirAErroNotaCredito(interpretarErroNotaCredito(e, "emissao"), "pre-visualizacao");
    const t = tentativaDepoisDeFalhaNc(base, e, () => "chave-nova");
    const ficaNoPasso2 = reacao.acao === "ignorar" || (reacao.acao === "banner" && reacao.passo === "pre-visualizacao");
    // A tentativa é sempre do mesmo conteúdo e tem chave, por isso `emitir` nunca sai cedo.
    expect(t.pedido).toBe(base.pedido);
    expect(t.chave).toBeTruthy();
    if (ficaNoPasso2 && reacao.acao === "banner") {
      expect(reacao.mensagem).toBeTruthy();
    }
  });
});

describe("tentativas por resolver fora do componente (WR-03)", () => {
  class ArmazenamentoFalso {
    dados = new Map<string, string>();
    getItem(k: string) {
      return this.dados.get(k) ?? null;
    }
    setItem(k: string, v: string) {
      this.dados.set(k, v);
    }
  }
  let armazenamento: ArmazenamentoFalso;

  beforeEach(() => {
    armazenamento = new ArmazenamentoFalso();
    vi.stubGlobal("window", { sessionStorage: armazenamento });
    reiniciarTentativasNcParaTestes();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    reiniciarTentativasNcParaTestes();
  });

  const conteudo = (documentoOrigemId: string, valor = 20000) => ({ documentoOrigemId, ...PEDIDO, valor });

  it("uma chave por resolver sobrevive ao desmontar do diálogo (estado do componente perdido)", () => {
    // Primeira montagem: pré-visualização, emissão, falha ambígua (504).
    const t1 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1");
    const depois = tentativaDepoisDeFalhaNc(t1, erro(504), () => "nunca");
    lembrarTentativaNc("fr-1", depois);

    // O diálogo desmonta (estado `null`) e volta a montar; o mesmo conteúdo reutiliza a chave.
    const t2 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-2");
    expect(t2.chave).toBe("chave-1");
    expect(t2.porResolver).toBe(true);
  });

  it("sobrevive a um recarregamento (só o sessionStorage fica)", () => {
    const t1 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1");
    lembrarTentativaNc("fr-1", tentativaDepoisDeFalhaNc(t1, new TypeError("Failed to fetch")));
    reiniciarTentativasNcParaTestes();

    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-2").chave).toBe("chave-1");
  });

  it("outro conteúdo ou outra FR gera chave nova, sem apagar a guardada", () => {
    const t1 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1");
    lembrarTentativaNc("fr-1", tentativaDepoisDeFalhaNc(t1, erro(500)));

    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1", 100), null, () => "chave-2").chave).toBe("chave-2");
    expect(tentativaParaPedidoNc("fr-2", conteudo("fr-2"), null, () => "chave-3").chave).toBe("chave-3");
    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-4").chave).toBe("chave-1");
  });

  it("o sucesso esquece a chave do conteúdo", () => {
    const t1 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1");
    const porResolver = tentativaDepoisDeFalhaNc(t1, erro(500));
    lembrarTentativaNc("fr-1", porResolver);
    lembrarTentativaNc("fr-1", porResolver, true);

    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-2").chave).toBe("chave-2");
  });

  it("uma recusa definitiva esquece a chave por resolver do conteúdo", () => {
    const t1 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1");
    lembrarTentativaNc("fr-1", tentativaDepoisDeFalhaNc(t1, erro(500)));
    const t2 = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "nunca");
    lembrarTentativaNc("fr-1", tentativaDepoisDeFalhaNc(t2, erro(409, "DATA_EMISSAO_ALTERADA"), () => "chave-3"));

    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-4").chave).toBe("chave-4");
  });

  it("armazenamento corrompido ou indisponível nunca lança", () => {
    armazenamento.setItem("lexcv:nota-credito:tentativas-por-resolver", "{nao é json");
    reiniciarTentativasNcParaTestes();
    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-1").chave).toBe("chave-1");

    vi.stubGlobal("window", {
      get sessionStorage(): Storage {
        throw new Error("SecurityError");
      },
    });
    reiniciarTentativasNcParaTestes();
    const t = tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-2");
    expect(() => lembrarTentativaNc("fr-1", { ...t, porResolver: true })).not.toThrow();
    expect(tentativaParaPedidoNc("fr-1", conteudo("fr-1"), null, () => "chave-3").chave).toBe("chave-2");
  });
});
