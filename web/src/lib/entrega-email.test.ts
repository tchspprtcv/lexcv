import { describe, expect, it } from "vitest";

import { ApiError } from "@/lib/api";
import * as modulo from "@/lib/entrega-email";
import {
  COPY_DESCARGA_NAO_ENCONTRADO,
  COPY_DESCARGA_PREPARAR,
  COPY_DESCARGA_REDE,
  COPY_EXPORTAR_INDISPONIVEL,
  COPY_EXPORTAR_MES_INVALIDO,
  COPY_EXPORTAR_REDE,
  COPY_REENVIO_COMUNICACAO_NAO_ACEITE,
  COPY_REENVIO_ENVIO_DESLIGADO,
  COPY_REENVIO_ESTADO_INVALIDO,
  COPY_REENVIO_INDISPONIVEL,
  COPY_REENVIO_NAO_ENCONTRADO,
  COPY_REENVIO_REDE,
  COPY_REENVIO_SEM_EMAIL_CLIENTE,
  COPY_REENVIO_SMTP_NAO_CONFIGURADO,
  ESTADOS_ENTREGA_EMAIL,
  ESTADO_ENTREGA_DESCONHECIDO,
  INTERVALO_ATUALIZACAO_ENTREGA_MS,
  apresentacaoEntregaEmail,
  descricaoEntregaEmail,
  deveSondarEntrega,
  interpretarErroDescarga,
  interpretarErroExportacao,
  interpretarErroReenvio,
  mostrarReenviar,
  rotuloReenviar,
  textoTentativas,
} from "@/lib/entrega-email";

function erro(status: number, code?: string) {
  const body: Record<string, string> = { message: "mensagem do backend" };
  if (code) body.code = code;
  return new ApiError(`API ${status}: mensagem do backend`, { status, code, body });
}

describe("ESTADOS_ENTREGA_EMAIL (137-UI-SPEC, tabela de badges)", () => {
  it("tem exatamente os seis estados com rótulo, variante e ícone da UI-SPEC", () => {
    expect(Object.keys(ESTADOS_ENTREGA_EMAIL)).toEqual([
      "NAO_CONFIGURADO",
      "DESLIGADO",
      "SEM_EMAIL",
      "PENDENTE",
      "ENVIADO",
      "FALHOU",
    ]);
    expect(apresentacaoEntregaEmail("NAO_CONFIGURADO")).toMatchObject({
      rotulo: "Não configurado",
      variante: "outline",
      icone: "MailX",
    });
    expect(apresentacaoEntregaEmail("DESLIGADO")).toMatchObject({ rotulo: "Desligado", variante: "outline", icone: "MailMinus" });
    expect(apresentacaoEntregaEmail("SEM_EMAIL")).toMatchObject({
      rotulo: "Sem email do cliente",
      variante: "outline",
      icone: "UserX",
    });
    expect(apresentacaoEntregaEmail("PENDENTE")).toMatchObject({ rotulo: "Pendente", variante: "outline", icone: "Clock" });
    expect(apresentacaoEntregaEmail("ENVIADO")).toMatchObject({ rotulo: "Enviado", variante: "outline", icone: "MailCheck" });
    expect(apresentacaoEntregaEmail("FALHOU")).toMatchObject({
      rotulo: "Falhou",
      variante: "secondary",
      icone: "TriangleAlert",
    });
  });

  it("só FALHOU usa secondary; nenhum estado usa a variante de acento", () => {
    const variantes = Object.entries(ESTADOS_ENTREGA_EMAIL).map(([e, a]) => [e, a.variante]);
    expect(variantes.filter(([, v]) => v === "secondary")).toEqual([["FALHOU", "secondary"]]);
    expect(variantes.every(([, v]) => v === "outline" || v === "secondary")).toBe(true);
  });

  it("um estado desconhecido, nulo ou indefinido cai no recurso neutro", () => {
    for (const e of ["ENTREGUE", "", null, undefined]) {
      expect(apresentacaoEntregaEmail(e)).toBe(ESTADO_ENTREGA_DESCONHECIDO);
    }
    expect(ESTADO_ENTREGA_DESCONHECIDO).toMatchObject({ rotulo: "Estado desconhecido", variante: "outline", icone: "Info" });
    expect(apresentacaoEntregaEmail("toString")).toBe(ESTADO_ENTREGA_DESCONHECIDO);
  });
});

describe("descricaoEntregaEmail (137-UI-SPEC, descrições longas)", () => {
  const sem = { podeReenviar: false, reenviavel: false };

  it("os cinco estados sem contagem seguem a tabela palavra a palavra", () => {
    expect(descricaoEntregaEmail("NAO_CONFIGURADO", sem)).toBe(
      "O servidor de email não está configurado nesta instalação. O documento foi emitido normalmente, mas não é " +
        "enviado por email. Peça ao administrador da instalação para configurar o servidor de email.",
    );
    expect(descricaoEntregaEmail("DESLIGADO", sem)).toBe(
      "O envio automático por email está desligado nas definições de faturação do escritório.",
    );
    expect(descricaoEntregaEmail("SEM_EMAIL", sem)).toBe(
      "O cliente não tem email registado. Adicione um email na ficha do cliente para poder enviar o documento.",
    );
    expect(descricaoEntregaEmail("PENDENTE", sem)).toBe(
      "O email será enviado em segundo plano, com o PDF e o XML em anexo.",
    );
    expect(descricaoEntregaEmail("ENVIADO", sem)).toBe(
      "Email enviado ao cliente com o PDF e o XML em anexo. A receção pelo cliente não é confirmada.",
    );
  });

  it("FALHOU conta as tentativas reais e só promete o reenvio com gate e reenviavel", () => {
    expect(descricaoEntregaEmail("FALHOU", { podeReenviar: true, reenviavel: true, tentativas: 5 })).toBe(
      "O envio do email falhou após 5 tentativas. Pode reenviar o email.",
    );
    expect(descricaoEntregaEmail("FALHOU", { podeReenviar: false, reenviavel: true, tentativas: 1 })).toBe(
      "O envio do email falhou após 1 tentativa.",
    );
    expect(descricaoEntregaEmail("FALHOU", { podeReenviar: true, reenviavel: false, tentativas: 2 })).toBe(
      "O envio do email falhou após 2 tentativas.",
    );
    expect(descricaoEntregaEmail("FALHOU", { podeReenviar: true, reenviavel: true, tentativas: 1 })).toBe(
      "O envio do email falhou após 1 tentativa. Pode reenviar o email.",
    );
  });

  it("FALHOU sem contagem (título do badge da lista) usa a variante curta", () => {
    expect(descricaoEntregaEmail("FALHOU", sem)).toBe("O envio do email falhou.");
    expect(descricaoEntregaEmail("FALHOU", { ...sem, tentativas: null })).toBe("O envio do email falhou.");
  });

  it("nunca escreve o literal (s) nem um 5 fixo para outra contagem", () => {
    for (const n of [1, 2, 3, 4, 6]) {
      const d = descricaoEntregaEmail("FALHOU", { podeReenviar: true, reenviavel: true, tentativas: n });
      expect(d).not.toContain("(s)");
      expect(d).not.toContain("5 tentativas");
    }
  });

  it("textoTentativas é singular só para 1", () => {
    expect(textoTentativas(1)).toBe("1 tentativa");
    expect(textoTentativas(3)).toBe("3 tentativas");
    expect(textoTentativas(0)).toBe("0 tentativas");
  });

  it("um estado desconhecido usa a descrição neutra", () => {
    expect(descricaoEntregaEmail("X", sem)).toBe(ESTADO_ENTREGA_DESCONHECIDO.descricao);
  });
});

describe("reenvio (gate exato + reenviavel do backend)", () => {
  it("mostrarReenviar exige a permissão E o reenviavel do backend", () => {
    expect(mostrarReenviar(true, { reenviavel: true })).toBe(true);
    expect(mostrarReenviar(false, { reenviavel: true })).toBe(false);
    expect(mostrarReenviar(true, { reenviavel: false })).toBe(false);
    expect(mostrarReenviar(true, null)).toBe(false);
    expect(mostrarReenviar(true, undefined)).toBe(false);
  });

  it("rotuloReenviar: Enviar para SEM_EMAIL, Reenviar para FALHOU e ENVIADO", () => {
    expect(rotuloReenviar("SEM_EMAIL")).toBe("Enviar email");
    expect(rotuloReenviar("FALHOU")).toBe("Reenviar email");
    expect(rotuloReenviar("ENVIADO")).toBe("Reenviar email");
  });
});

describe("deveSondarEntrega (polling de 15 s)", () => {
  it("só enquanto a comunicação ou a entrega estão PENDENTE", () => {
    expect(deveSondarEntrega("PENDENTE", null)).toBe(true);
    expect(deveSondarEntrega("ACEITE_SIMULADO", "PENDENTE")).toBe(true);
    expect(deveSondarEntrega("ACEITE_SIMULADO", "ENVIADO")).toBe(false);
    expect(deveSondarEntrega(null, undefined)).toBe(false);
    expect(deveSondarEntrega("ERRO", "FALHOU")).toBe(false);
    expect(INTERVALO_ATUALIZACAO_ENTREGA_MS).toBe(15000);
  });
});

describe("interpretarErroReenvio (137-UI-SPEC 1d)", () => {
  it("cada código mapeia para a copy fixa e o comportamento da tabela", () => {
    expect(interpretarErroReenvio(erro(409, "ENTREGA_ESTADO_INVALIDO"))).toEqual({
      mensagem: COPY_REENVIO_ESTADO_INVALIDO,
      definitivo: true,
      fecharDialogo: false,
      refrescar: true,
    });
    expect(interpretarErroReenvio(erro(422, "SEM_EMAIL_CLIENTE"))).toEqual({
      mensagem: COPY_REENVIO_SEM_EMAIL_CLIENTE,
      definitivo: true,
      fecharDialogo: false,
      refrescar: false,
    });
    expect(interpretarErroReenvio(erro(422, "SMTP_NAO_CONFIGURADO"))).toEqual({
      mensagem: COPY_REENVIO_SMTP_NAO_CONFIGURADO,
      definitivo: true,
      fecharDialogo: false,
      refrescar: true,
    });
    expect(interpretarErroReenvio(erro(422, "ENVIO_EMAIL_DESLIGADO"))).toEqual({
      mensagem: COPY_REENVIO_ENVIO_DESLIGADO,
      definitivo: true,
      fecharDialogo: false,
      refrescar: true,
    });
    expect(interpretarErroReenvio(erro(422, "COMUNICACAO_NAO_ACEITE"))).toEqual({
      mensagem: COPY_REENVIO_COMUNICACAO_NAO_ACEITE,
      definitivo: true,
      fecharDialogo: false,
      refrescar: false,
    });
    expect(interpretarErroReenvio(erro(404, "DOCUMENTO_FISCAL_NAO_ENCONTRADO"))).toEqual({
      mensagem: COPY_REENVIO_NAO_ENCONTRADO,
      definitivo: true,
      fecharDialogo: true,
      refrescar: true,
    });
    expect(interpretarErroReenvio(erro(503))).toEqual({
      mensagem: COPY_REENVIO_INDISPONIVEL,
      definitivo: false,
      fecharDialogo: false,
      refrescar: false,
    });
  });

  it("rede e outros 5xx: copy genérica com nova tentativa", () => {
    for (const e of [new TypeError("Failed to fetch"), erro(500), erro(502)]) {
      expect(interpretarErroReenvio(e)).toEqual({
        mensagem: COPY_REENVIO_REDE,
        definitivo: false,
        fecharDialogo: false,
        refrescar: false,
      });
    }
  });

  it("401/403 ficam com o apiFetch", () => {
    expect(interpretarErroReenvio(erro(401))).toBeNull();
    expect(interpretarErroReenvio(erro(403))).toBeNull();
  });

  it("a copy é exatamente a da UI-SPEC e nunca o texto do backend", () => {
    expect(COPY_REENVIO_ESTADO_INVALIDO).toBe(
      "Este email já está a ser enviado. Atualizámos o estado; verifique antes de tentar de novo.",
    );
    expect(COPY_REENVIO_SEM_EMAIL_CLIENTE).toBe(
      "O cliente continua sem email registado. Adicione um email na ficha do cliente e tente de novo.",
    );
    expect(COPY_REENVIO_SMTP_NAO_CONFIGURADO).toBe(
      "O servidor de email não está configurado nesta instalação. Peça ao administrador da instalação para " +
        "configurar o servidor de email.",
    );
    expect(COPY_REENVIO_ENVIO_DESLIGADO).toBe(
      "O envio automático por email está desligado nas definições de faturação.",
    );
    expect(COPY_REENVIO_COMUNICACAO_NAO_ACEITE).toBe("O documento só pode ser enviado depois de aceite na comunicação.");
    expect(COPY_REENVIO_NAO_ENCONTRADO).toBe("Documento fiscal não encontrado");
    expect(COPY_REENVIO_INDISPONIVEL).toBe(
      "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.",
    );
    expect(COPY_REENVIO_REDE).toBe("Não foi possível reenviar o email. Verifique a ligação e tente novamente.");
    expect(interpretarErroReenvio(erro(422, "SEM_EMAIL_CLIENTE"))?.mensagem).not.toContain("backend");
  });
});

describe("interpretarErroExportacao (137-UI-SPEC Surface 4)", () => {
  it("422 MES_INVALIDO, 503 e rede/5xx", () => {
    expect(interpretarErroExportacao(erro(422, "MES_INVALIDO"))).toBe(COPY_EXPORTAR_MES_INVALIDO);
    expect(interpretarErroExportacao(erro(503))).toBe(COPY_EXPORTAR_INDISPONIVEL);
    expect(interpretarErroExportacao(erro(500))).toBe(COPY_EXPORTAR_REDE);
    expect(interpretarErroExportacao(new TypeError("Failed to fetch"))).toBe(COPY_EXPORTAR_REDE);
    expect(interpretarErroExportacao(erro(403))).toBeNull();
    expect(COPY_EXPORTAR_MES_INVALIDO).toBe("O mês escolhido não é válido. Escolha um mês até ao mês atual.");
    expect(COPY_EXPORTAR_INDISPONIVEL).toBe(
      "O serviço está temporariamente indisponível. Aguarde um momento e tente novamente.",
    );
    expect(COPY_EXPORTAR_REDE).toBe("Não foi possível exportar o ficheiro. Verifique a ligação e tente novamente.");
  });
});

describe("interpretarErroDescarga (137-UI-SPEC Surface 1a)", () => {
  it("404, 503 e outros", () => {
    expect(interpretarErroDescarga(erro(404, "DOCUMENTO_FISCAL_NAO_ENCONTRADO"))).toBe(COPY_DESCARGA_NAO_ENCONTRADO);
    for (const code of ["FICHEIRO_INDISPONIVEL", "STORAGE_INDISPONIVEL", "FALHA_PDF"]) {
      expect(interpretarErroDescarga(erro(503, code))).toBe(COPY_DESCARGA_PREPARAR);
    }
    expect(interpretarErroDescarga(erro(500))).toBe(COPY_DESCARGA_REDE);
    expect(interpretarErroDescarga(new TypeError("Failed to fetch"))).toBe(COPY_DESCARGA_REDE);
    expect(interpretarErroDescarga(erro(401))).toBeNull();
    expect(COPY_DESCARGA_NAO_ENCONTRADO).toBe("Documento fiscal não encontrado.");
    expect(COPY_DESCARGA_PREPARAR).toBe(
      "Não foi possível preparar o ficheiro neste momento. Aguarde um momento e tente novamente.",
    );
    expect(COPY_DESCARGA_REDE).toBe("Não foi possível descarregar o ficheiro. Verifique a ligação e tente novamente.");
  });
});

describe("vocabulário proibido (T-137-66)", () => {
  function textos(valor: unknown): string[] {
    if (typeof valor === "string") return [valor];
    if (valor && typeof valor === "object") return Object.values(valor).flatMap(textos);
    return [];
  }

  it("nenhuma string do módulo parafraseia Enviado nem fala de autorização", () => {
    const todas = [
      ...textos(modulo),
      ...["NAO_CONFIGURADO", "DESLIGADO", "SEM_EMAIL", "PENDENTE", "ENVIADO", "FALHOU"].map((e) =>
        descricaoEntregaEmail(e, { podeReenviar: true, reenviavel: true, tentativas: 2 }),
      ),
    ];
    expect(todas.length).toBeGreaterThan(30);
    for (const t of todas) {
      for (const proibida of ["Entregue", "Recebido", "Lido", "Autorizado", "Aprovado", "Validado"]) {
        expect(t).not.toContain(proibida);
      }
    }
  });
});
