import { describe, expect, it } from "vitest";

import {
  auditoriaCategoriaToLabel,
  auditoriaEventoToSentence,
  auditoriaEventoToTexto,
  auditoriaPermissoesDetalhe,
} from "./auditoria-rbac";
import type { AuditoriaRbacEntry } from "@/types/auditoria-rbac";

function baseEntry(overrides: Partial<AuditoriaRbacEntry>): AuditoriaRbacEntry {
  return {
    id: 1,
    timestamp: "2026-09-22T10:00:00Z",
    acao: "papel_criar",
    categoria: "papel",
    autorNome: null,
    alvoId: null,
    alvoNome: null,
    papelId: null,
    papelNome: null,
    nomeAntigo: null,
    nomeNovo: null,
    permissoesAdicionadas: null,
    permissoesRemovidas: null,
    motivo: null,
    ...overrides,
  };
}

describe("auditoriaEventoToSentence / auditoriaEventoToTexto", () => {
  it("papel_criar renders 'Maria Silva criou o papel Financeiro.' with autor and papel as destaque", () => {
    const entry = baseEntry({
      acao: "papel_criar",
      categoria: "papel",
      autorNome: "Maria Silva",
      papelNome: "Financeiro",
    });
    expect(auditoriaEventoToTexto(entry)).toBe("Maria Silva criou o papel Financeiro.");
    const segmentos = auditoriaEventoToSentence(entry);
    expect(segmentos.find((s) => s.texto === "Maria Silva")?.destaque).toBe(true);
    expect(segmentos.find((s) => s.texto === "Financeiro")?.destaque).toBe(true);
  });

  it("papel_renomear renders 'Maria Silva renomeou o papel Antigo para Novo.'", () => {
    const entry = baseEntry({
      acao: "papel_renomear",
      categoria: "papel",
      autorNome: "Maria Silva",
      nomeAntigo: "Antigo",
      nomeNovo: "Novo",
    });
    expect(auditoriaEventoToTexto(entry)).toBe("Maria Silva renomeou o papel Antigo para Novo.");
  });

  it("papel_apagar renders 'Maria Silva apagou o papel Financeiro.'", () => {
    const entry = baseEntry({
      acao: "papel_apagar",
      categoria: "papel",
      autorNome: "Maria Silva",
      papelNome: "Financeiro",
    });
    expect(auditoriaEventoToTexto(entry)).toBe("Maria Silva apagou o papel Financeiro.");
  });

  it("papel_permissoes_alterar renders 'Maria Silva alterou as permissões do papel Financeiro.'", () => {
    const entry = baseEntry({
      acao: "papel_permissoes_alterar",
      categoria: "papel",
      autorNome: "Maria Silva",
      papelNome: "Financeiro",
    });
    expect(auditoriaEventoToTexto(entry)).toBe(
      "Maria Silva alterou as permissões do papel Financeiro.",
    );
  });

  it("papel_atribuir renders 'Maria Silva atribuiu o papel Advogado a João Pires.'", () => {
    const entry = baseEntry({
      acao: "papel_atribuir",
      categoria: "atribuicao",
      autorNome: "Maria Silva",
      papelNome: "Advogado",
      alvoNome: "João Pires",
    });
    expect(auditoriaEventoToTexto(entry)).toBe(
      "Maria Silva atribuiu o papel Advogado a João Pires.",
    );
  });

  it("papel_retirar renders 'Maria Silva retirou o papel Advogado a João Pires.' (CONTEXT.md example, verbatim)", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: "Maria Silva",
      papelNome: "Advogado",
      alvoNome: "João Pires",
    });
    expect(auditoriaEventoToTexto(entry)).toBe(
      "Maria Silva retirou o papel Advogado a João Pires.",
    );
  });

  it("papel_retirar with motivo utilizador_eliminado appends ' — utilizador eliminado.'", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: "Maria Silva",
      papelNome: "Advogado",
      alvoNome: "João Pires",
      motivo: "utilizador_eliminado",
    });
    expect(auditoriaEventoToTexto(entry)).toBe(
      "Maria Silva retirou o papel Advogado a João Pires — utilizador eliminado.",
    );
  });

  it("papel_atribuir with motivo provisionamento has no person subject, even though autorNome is null", () => {
    const entry = baseEntry({
      acao: "papel_atribuir",
      categoria: "atribuicao",
      autorNome: null,
      papelNome: "Administrador",
      alvoNome: "Ana Lopes",
      motivo: "provisionamento",
    });
    expect(auditoriaEventoToTexto(entry)).toBe(
      "A plataforma atribuiu o papel Administrador a Ana Lopes na criação do escritório.",
    );
  });

  it("autorNome null (non-provisioning) falls back to 'um administrador removido', rendered as destaque", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: null,
      papelNome: "Advogado",
      alvoNome: "João Pires",
    });
    const segmentos = auditoriaEventoToSentence(entry);
    const autorSegmento = segmentos.find((s) => s.texto === "um administrador removido");
    expect(autorSegmento).toBeDefined();
    expect(autorSegmento?.destaque).toBe(true);
    expect(auditoriaEventoToTexto(entry)).toBe(
      "um administrador removido retirou o papel Advogado a João Pires.",
    );
  });

  it("alvoNome null falls back to 'um utilizador removido', rendered as destaque", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: "Maria Silva",
      papelNome: "Advogado",
      alvoNome: null,
    });
    const segmentos = auditoriaEventoToSentence(entry);
    const alvoSegmento = segmentos.find((s) => s.texto === "um utilizador removido");
    expect(alvoSegmento).toBeDefined();
    expect(alvoSegmento?.destaque).toBe(true);
  });

  it("papelNome null falls back to 'um papel removido', rendered as destaque", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: "Maria Silva",
      papelNome: null,
      alvoNome: "João Pires",
    });
    const segmentos = auditoriaEventoToSentence(entry);
    const papelSegmento = segmentos.find((s) => s.texto === "um papel removido");
    expect(papelSegmento).toBeDefined();
    expect(papelSegmento?.destaque).toBe(true);
  });

  it("with autor, alvo and papel all null, the joined text never contains null, undefined or the entry's ids", () => {
    const entry = baseEntry({
      acao: "papel_retirar",
      categoria: "atribuicao",
      autorNome: null,
      alvoId: "11111111-1111-1111-1111-111111111111",
      alvoNome: null,
      papelId: "22222222-2222-2222-2222-222222222222",
      papelNome: null,
    });
    const texto = auditoriaEventoToTexto(entry);
    expect(texto).not.toContain("null");
    expect(texto).not.toContain("undefined");
    expect(texto).not.toContain(entry.alvoId as string);
    expect(texto).not.toContain(entry.papelId as string);
    expect(texto).toBe(
      "um administrador removido retirou o papel um papel removido a um utilizador removido.",
    );
  });

  it("an unknown acao renders 'Evento de auditoria não reconhecido.' and never leaks the raw code", () => {
    const entry = baseEntry({ acao: "xyz" });
    const texto = auditoriaEventoToTexto(entry);
    expect(texto).toBe("Evento de auditoria não reconhecido.");
    expect(texto).not.toContain("xyz");
  });
});

describe("auditoriaPermissoesDetalhe", () => {
  it("joins 'Acrescentou:' and 'Retirou:' with catalogue labels, falling back to the key", () => {
    const detalhe = auditoriaPermissoesDetalhe(
      {
        permissoesAdicionadas: ["financeiro:view"],
        permissoesRemovidas: ["clientes:edit"],
      },
      { "financeiro:view": "Ver financeiro" },
    );
    expect(detalhe).toBe("Acrescentou: Ver financeiro · Retirou: clientes:edit");
  });

  it("returns null when both lists are empty or null", () => {
    expect(
      auditoriaPermissoesDetalhe(
        { permissoesAdicionadas: [], permissoesRemovidas: [] },
        {},
      ),
    ).toBeNull();
    expect(
      auditoriaPermissoesDetalhe(
        { permissoesAdicionadas: null, permissoesRemovidas: null },
        {},
      ),
    ).toBeNull();
  });
});

describe("auditoriaCategoriaToLabel", () => {
  it("maps 'papel' to 'Papel'", () => {
    expect(auditoriaCategoriaToLabel("papel")).toBe("Papel");
  });

  it("maps 'atribuicao' to 'Atribuição'", () => {
    expect(auditoriaCategoriaToLabel("atribuicao")).toBe("Atribuição");
  });
});
