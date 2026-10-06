import { describe, expect, it } from "vitest";

import { PERMISSAO_REPROCESSAR_COMUNICACAO, podeReprocessarComunicacao } from "@/hooks/use-faturacao";
import { ApiError } from "@/lib/api";
import { hasScopedPermission } from "@/lib/permissions";
import {
  COPY_IUD_TESTE,
  COPY_MODO_SIMULADO_LEAD,
  COPY_MODO_SIMULADO_TEXTO,
  COPY_REPROCESSAR,
  COPY_REPROCESSAR_SUCESSO,
  COPY_SEM_COMUNICACAO,
  COPY_SEM_TENTATIVAS,
  COPY_IUD_A_GERAR,
  COPY_TENTATIVAS_AJUDA,
  ESTADOS_COMUNICACAO,
  INTERVALO_ATUALIZACAO_MS,
  ROTULO_AMBIENTE,
  apresentacaoEstadoComunicacao,
  descricaoEstadoComunicacao,
  intervaloAtualizacaoComunicacao,
  intervaloAtualizacaoListaComunicacao,
  interpretarErroReprocessar,
  mostrarBannerModoSimulado,
  mostrarReprocessar,
} from "@/lib/comunicacao-fiscal";

function erro(status: number, code?: string) {
  const body: Record<string, string> = { message: "mensagem do backend" };
  if (code) body.code = code;
  return new ApiError(`API ${status}: mensagem do backend`, { status, code, body });
}

describe("ESTADOS_COMUNICACAO (136-UI-SPEC, tabela de badges)", () => {
  it("tem exatamente os quatro estados com rótulo, variante e ícone neutros", () => {
    expect(Object.keys(ESTADOS_COMUNICACAO)).toEqual(["PENDENTE", "ACEITE_SIMULADO", "REJEITADO", "ERRO"]);
    expect(ESTADOS_COMUNICACAO.PENDENTE).toMatchObject({ rotulo: "Pendente", variante: "outline", icone: "Clock" });
    expect(ESTADOS_COMUNICACAO.ACEITE_SIMULADO).toMatchObject({
      rotulo: "Aceite (simulação)",
      variante: "outline",
      icone: "Info",
    });
    expect(ESTADOS_COMUNICACAO.REJEITADO).toMatchObject({
      rotulo: "Rejeitado",
      variante: "secondary",
      icone: "CircleSlash",
    });
    expect(ESTADOS_COMUNICACAO.ERRO).toMatchObject({ rotulo: "Erro", variante: "secondary", icone: "TriangleAlert" });
  });

  it("descrições longas da UI-SPEC", () => {
    expect(ESTADOS_COMUNICACAO.PENDENTE.descricao).toBe(
      "Comunicação à administração fiscal ainda não efetuada. Será tentada em segundo plano.",
    );
    expect(ESTADOS_COMUNICACAO.ACEITE_SIMULADO.descricao).toBe(
      "Aceite pelo serviço de simulação. Não foi comunicado à administração fiscal e não tem validade fiscal.",
    );
    expect(ESTADOS_COMUNICACAO.REJEITADO.descricao).toBe(
      "O documento foi recusado na validação do formato. Veja a última falha antes de reprocessar: " +
        "se a causa estiver nos dados do documento, o resultado será o mesmo.",
    );
    expect(ESTADOS_COMUNICACAO.ERRO.descricao).toBe(
      "A comunicação falhou após várias tentativas. Pode reprocessar a comunicação.",
    );
  });

  it("nunca usa a variante de acento nem palavras de autorização", () => {
    const proibidas = /\b(autorizad[oa]|aprovad[oa]|validad[oa])\b/i;
    for (const apresentacao of [...Object.values(ESTADOS_COMUNICACAO), apresentacaoEstadoComunicacao("x")]) {
      expect(apresentacao.variante).not.toBe("default");
      const textos = [apresentacao.rotulo, apresentacao.descricao, apresentacao.descricaoSemPermissao];
      for (const texto of textos) {
        expect(texto).not.toMatch(proibidas);
        // "Comunicado" só aparece negado ("Não foi comunicado"), nunca como resultado.
        expect(texto.replace(/não foi comunicado/gi, "")).not.toMatch(/\bcomunicad[oa]\b/i);
      }
    }
  });
});

describe("apresentacaoEstadoComunicacao", () => {
  it.each(["AUTORIZADO", null, undefined, "x", ""])("estado %s -> 'Estado desconhecido' neutro", (estado) => {
    expect(apresentacaoEstadoComunicacao(estado)).toMatchObject({
      rotulo: "Estado desconhecido",
      variante: "outline",
      icone: "Info",
    });
  });

  it("estado conhecido -> a linha do record", () => {
    expect(apresentacaoEstadoComunicacao("ERRO")).toBe(ESTADOS_COMUNICACAO.ERRO);
  });
});

describe("descricaoEstadoComunicacao", () => {
  it("ERRO e REJEITADO mudam com a permissão exata de reprocessar", () => {
    expect(descricaoEstadoComunicacao("ERRO", true)).toBe(
      "A comunicação falhou após várias tentativas. Pode reprocessar a comunicação.",
    );
    expect(descricaoEstadoComunicacao("ERRO", false)).toBe(
      "A comunicação falhou após várias tentativas. Peça a um utilizador com permissão para reprocessar.",
    );
    // WR-04 (136): REJEITADO nunca promete que reprocessar resolve (o snapshot é imutável).
    expect(descricaoEstadoComunicacao("REJEITADO", true)).toBe(
      "O documento foi recusado na validação do formato. Veja a última falha antes de reprocessar: " +
        "se a causa estiver nos dados do documento, o resultado será o mesmo.",
    );
    expect(descricaoEstadoComunicacao("REJEITADO", false)).toBe(
      "O documento foi recusado na validação do formato. Veja a última falha. " +
        "Para reprocessar, peça a um utilizador com permissão.",
    );
    expect(descricaoEstadoComunicacao("REJEITADO", true)).not.toContain("Pode reprocessar");
  });

  it("PENDENTE e ACEITE_SIMULADO não dependem da permissão", () => {
    for (const pode of [true, false]) {
      expect(descricaoEstadoComunicacao("PENDENTE", pode)).toBe(ESTADOS_COMUNICACAO.PENDENTE.descricao);
      expect(descricaoEstadoComunicacao("ACEITE_SIMULADO", pode)).toBe(
        ESTADOS_COMUNICACAO.ACEITE_SIMULADO.descricao,
      );
    }
  });
});

describe("intervaloAtualizacaoComunicacao (polling só enquanto PENDENTE)", () => {
  it("PENDENTE -> 15 s", () => {
    expect(INTERVALO_ATUALIZACAO_MS).toBe(15000);
    expect(intervaloAtualizacaoComunicacao("PENDENTE")).toBe(15000);
  });

  it.each(["ACEITE_SIMULADO", "REJEITADO", "ERRO", null, undefined])("%s -> sem polling", (estado) => {
    expect(intervaloAtualizacaoComunicacao(estado)).toBe(false);
  });

  it("lista: 15 s só se alguma linha estiver PENDENTE", () => {
    expect(intervaloAtualizacaoListaComunicacao([{ estadoComunicacao: "ERRO" }, { estadoComunicacao: "PENDENTE" }])).toBe(
      15000,
    );
    expect(intervaloAtualizacaoListaComunicacao([{ estadoComunicacao: "ERRO" }, { estadoComunicacao: null }])).toBe(
      false,
    );
    expect(intervaloAtualizacaoListaComunicacao([])).toBe(false);
    expect(intervaloAtualizacaoListaComunicacao(undefined)).toBe(false);
  });
});

describe("mostrarReprocessar", () => {
  it("só ERRO/REJEITADO, com permissão exata e modo SIMULADO", () => {
    expect(mostrarReprocessar(true, { estado: "ERRO" }, "SIMULADO")).toBe(true);
    expect(mostrarReprocessar(true, { estado: "REJEITADO" }, "SIMULADO")).toBe(true);
    expect(mostrarReprocessar(true, { estado: "PENDENTE" }, "SIMULADO")).toBe(false);
    expect(mostrarReprocessar(true, { estado: "ACEITE_SIMULADO" }, "SIMULADO")).toBe(false);
    expect(mostrarReprocessar(false, { estado: "ERRO" }, "SIMULADO")).toBe(false);
    expect(mostrarReprocessar(true, { estado: "ERRO" }, null)).toBe(false);
    expect(mostrarReprocessar(true, { estado: "ERRO" }, undefined)).toBe(false);
    expect(mostrarReprocessar(true, { estado: "ERRO" }, "REAL")).toBe(false);
    expect(mostrarReprocessar(true, null, "SIMULADO")).toBe(false);
    expect(mostrarReprocessar(true, undefined, "SIMULADO")).toBe(false);
  });
});

describe("interpretarErroReprocessar (136-UI-SPEC Surface 2, erros inline)", () => {
  it.each([409, 422])("%i COMUNICACAO_ESTADO_INVALIDO -> definitivo e refresca o documento", (status) => {
    expect(interpretarErroReprocessar(erro(status, "COMUNICACAO_ESTADO_INVALIDO"))).toEqual({
      mensagem:
        "Este documento já não está em erro ou rejeitado. Atualizámos o estado; verifique antes de tentar de novo.",
      definitivo: true,
      fecharDialogo: false,
      refrescar: true,
    });
  });

  it.each([
    [409, "MODO_NAO_SUPORTADO"],
    [422, "MODO_NAO_SUPORTADO"],
    [409, "OUTRO_CODIGO"],
    [422, undefined],
  ])("%i %s -> modo não suportado, definitivo", (status, code) => {
    expect(interpretarErroReprocessar(erro(status, code))).toEqual({
      mensagem: "O reprocessamento não está disponível neste ambiente.",
      definitivo: true,
      fecharDialogo: false,
      refrescar: false,
    });
  });

  it("404 fecha o diálogo (a página mostra o estado de não encontrado)", () => {
    expect(interpretarErroReprocessar(erro(404, "DOCUMENTO_FISCAL_NAO_ENCONTRADO"))).toMatchObject({
      mensagem: "Documento fiscal não encontrado",
      fecharDialogo: true,
      definitivo: true,
    });
  });

  it("503 -> serviço indisponível, pode tentar de novo", () => {
    expect(interpretarErroReprocessar(erro(503))).toEqual({
      mensagem: "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.",
      definitivo: false,
      fecharDialogo: false,
      refrescar: false,
    });
  });

  it.each([new TypeError("Failed to fetch"), erro(500), erro(502), erro(504), new Error("x")])(
    "falha de rede ou outro 5xx -> copy de rede, não definitivo",
    (e) => {
      expect(interpretarErroReprocessar(e)).toEqual({
        mensagem: "Não foi possível reprocessar a comunicação. Verifique a ligação e tente novamente.",
        definitivo: false,
        fecharDialogo: false,
        refrescar: false,
      });
    },
  );

  it.each([401, 403])("%i -> null (o apiFetch trata)", (status) => {
    expect(interpretarErroReprocessar(erro(status))).toBeNull();
  });
});

describe("copy do 136-UI-SPEC", () => {
  it("ambiente, IUD e banner", () => {
    expect(ROTULO_AMBIENTE.SIMULADO).toBe("Teste (simulado)");
    expect(COPY_IUD_TESTE).toBe("Ambiente de teste — sem validade fiscal");
    expect(COPY_MODO_SIMULADO_LEAD).toBe("Modo simulado.");
    expect(COPY_MODO_SIMULADO_TEXTO).toBe(
      "Os documentos são comunicados a um serviço de simulação, não à administração fiscal (DNRE). " +
        "Não têm validade fiscal. Continue a emitir os seus documentos válidos no software de faturação homologado.",
    );
  });

  it("card e reprocessamento", () => {
    expect(COPY_SEM_COMUNICACAO).toBe("A comunicação deste documento ainda não foi registada.");
    expect(COPY_SEM_TENTATIVAS).toBe("Ainda sem tentativas");
    expect(COPY_IUD_A_GERAR).toBe("A gerar...");
    expect(COPY_TENTATIVAS_AJUDA).toBe("A comunicação é retentada automaticamente até 8 vezes.");
    expect(COPY_REPROCESSAR).toBe("Reprocessar comunicação");
    expect(COPY_REPROCESSAR_SUCESSO).toBe("Comunicação reposta como pendente.");
  });
});

describe("podeReprocessarComunicacao (gate EXATO financeiro:edit, checker clarification)", () => {
  it("exige exatamente financeiro:edit, como o @PreAuthorize do backend", () => {
    expect(PERMISSAO_REPROCESSAR_COMUNICACAO).toBe("financeiro:edit");
    expect(podeReprocessarComunicacao(["financeiro:edit"])).toBe(true);
    expect(podeReprocessarComunicacao(["financeiro:view", "financeiro:edit"])).toBe(true);
    expect(podeReprocessarComunicacao(["financeiro:manage"])).toBe(false);
    expect(podeReprocessarComunicacao(["financeiro:view"])).toBe(false);
    expect(podeReprocessarComunicacao([])).toBe(false);
    expect(podeReprocessarComunicacao(undefined)).toBe(false);
  });

  it("o fallback do frontend diria o contrário para manage -- por isso existe o gate exato", () => {
    expect(hasScopedPermission(["financeiro:manage"], "financeiro", "edit")).toBe(true);
    expect(podeReprocessarComunicacao(["financeiro:manage"])).toBe(false);
  });
});

describe("mostrarBannerModoSimulado (WR-07: falha fechado)", () => {
  it.each([undefined, null, "", "  ", "SIMULADO"])("modo %s -> mostra o banner", (modo) => {
    expect(mostrarBannerModoSimulado(modo)).toBe(true);
  });

  it.each(["PRODUCAO", "REAL"])("modo explícito %s -> não mostra", (modo) => {
    expect(mostrarBannerModoSimulado(modo)).toBe(false);
  });
});
