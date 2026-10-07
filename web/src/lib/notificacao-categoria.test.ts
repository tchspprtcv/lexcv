import { describe, expect, it } from "vitest";

import {
  NOTIFICACAO_CATEGORIA_OPTIONS,
  NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS,
  NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS,
  categoriaToBadgeVariant,
  categoriaToLabel,
} from "@/lib/notificacao-categoria";

// Phase 136 (DFE-07, 136-UI-SPEC Surface 4): a falha persistente de comunicação fiscal é uma nova
// categoria, NÃO silenciável (research_resolutions; espelha CategoriaNotificacao.java).
describe("COMUNICACAO_FISCAL_FALHOU", () => {
  it("tem o rótulo e o badge da UI-SPEC", () => {
    expect(categoriaToLabel("COMUNICACAO_FISCAL_FALHOU")).toBe("Falha de comunicação fiscal");
    expect(categoriaToBadgeVariant("COMUNICACAO_FISCAL_FALHOU")).toBe("red");
  });

  it("não é silenciável, tal como PRAZO_VENCIDO", () => {
    expect(NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS).toEqual([
      "PRAZO_VENCIDO",
      "COMUNICACAO_FISCAL_FALHOU",
      "EMAIL_FISCAL_FALHOU",
    ]);
    expect(NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS.map((o) => o.value)).not.toContain(
      "COMUNICACAO_FISCAL_FALHOU",
    );
  });

  it("aparece nas opções do filtro de categoria", () => {
    expect(NOTIFICACAO_CATEGORIA_OPTIONS).toContainEqual({
      value: "COMUNICACAO_FISCAL_FALHOU",
      label: "Falha de comunicação fiscal",
    });
    expect(NOTIFICACAO_CATEGORIA_OPTIONS).toHaveLength(10);
  });
});

// Phase 137 (ENTR-05, 137-UI-SPEC Surface 5): a falha persistente do envio do email fiscal ao
// cliente é uma nova categoria, NÃO silenciável (espelha CategoriaNotificacao.java).
describe("EMAIL_FISCAL_FALHOU", () => {
  it("tem o rótulo e o badge da UI-SPEC", () => {
    expect(categoriaToLabel("EMAIL_FISCAL_FALHOU")).toBe("Falha de envio de email fiscal");
    expect(categoriaToBadgeVariant("EMAIL_FISCAL_FALHOU")).toBe("red");
  });

  it("não é silenciável e aparece no filtro de categoria", () => {
    expect(NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS).toContain("EMAIL_FISCAL_FALHOU");
    expect(NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS.map((o) => o.value)).not.toContain("EMAIL_FISCAL_FALHOU");
    expect(NOTIFICACAO_CATEGORIA_OPTIONS).toContainEqual({
      value: "EMAIL_FISCAL_FALHOU",
      label: "Falha de envio de email fiscal",
    });
  });
});
