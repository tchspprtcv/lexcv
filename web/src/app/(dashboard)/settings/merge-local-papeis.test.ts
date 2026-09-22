import { describe, expect, it } from "vitest";

import {
  construirEstadoLocal,
  mesclarEstadoLocal,
  papeisComAlteracoesPorGravar,
  rotuloPapeisPorGravar,
} from "./merge-local-papeis";
import type { OfficeRbac } from "@/types/office-rbac";

// Generalização de `plataforma/moldes/merge-local-permissoes.test.ts` (Phase 125, CR-01) para
// este ecrã: prova, ao nível da função pura que `settings/page.tsx` usa para reconciliar
// `localPermissoes` com um payload fresco da query, que edições não gravadas sobrevivem às
// TRÊS mutações deste ecrã (criar, renomear, apagar), não apenas a "criar" como em moldes.
//
// O caso de renomear é o novo (UI-SPEC §6): a suite final abaixo prova que renomear um papel
// DIFERENTE do que tem edições por gravar não as apaga -- e que isso só vale porque o mapa é
// indexado por `id`, nunca por `nome`. Um leitor que "simplifique" `mesclarEstadoLocal` para
// indexar por `nome` faz esse teste falhar.

const payloadInicial: OfficeRbac = {
  permissoes: [
    { key: "clientes:view", nome: "Ver Clientes", descricao: null, modulo: "Clientes" },
    { key: "processos:view", nome: "Ver Processos", descricao: null, modulo: "Processos" },
  ],
  papeis: [
    {
      id: "11111111-1111-1111-1111-111111111111",
      nome: "Recepção",
      sistema: false,
      protegido: false,
      podeApagar: true,
      utilizadoresAtribuidos: 0,
      permissoes: ["clientes:view"],
    },
    {
      id: "22222222-2222-2222-2222-222222222222",
      nome: "Financeiro",
      sistema: false,
      protegido: false,
      podeApagar: true,
      utilizadoresAtribuidos: 0,
      permissoes: [],
    },
  ],
};

const PAPEL_1 = "11111111-1111-1111-1111-111111111111";
const PAPEL_2 = "22222222-2222-2222-2222-222222222222";
const PAPEL_3 = "33333333-3333-3333-3333-333333333333";

describe("merge-local-papeis: reset da matriz de papeis nao pode apagar edicoes nao gravadas", () => {
  it("prevLocal null inicializa tudo a partir do payload fresco (primeiro load)", () => {
    const resultado = mesclarEstadoLocal(payloadInicial, null, new Set());
    expect(resultado[PAPEL_1]!.has("clientes:view")).toBe(true);
    expect(resultado[PAPEL_2]!.size).toBe(0);
  });

  it("um papel NUNCA tocado, incluindo um papel novo criado entretanto, reflete sempre o payload fresco", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[PAPEL_1]!.add("processos:view");

    const payloadFrescoAposCriarPapel: OfficeRbac = {
      ...payloadInicial,
      papeis: [
        ...payloadInicial.papeis,
        {
          id: PAPEL_3,
          nome: "Supervisor",
          sistema: false,
          protegido: false,
          podeApagar: true,
          utilizadoresAtribuidos: 0,
          permissoes: ["clientes:view"],
        },
      ],
    };

    const touchedPapelIds = new Set([PAPEL_1]); // papel 2 e 3 nunca foram tocados

    const resultado = mesclarEstadoLocal(payloadFrescoAposCriarPapel, localComEdicaoNaoGravada, touchedPapelIds);

    // Papel 2 (nunca tocado) reflete o payload fresco.
    expect(resultado[PAPEL_2]!.size).toBe(0);
    // Papel 3 (novo, nunca tocado) aparece inicializado a partir do payload fresco.
    expect(resultado[PAPEL_3]).toBeDefined();
    expect(resultado[PAPEL_3]!.has("clientes:view")).toBe(true);
  });

  it("um papel TOCADO mantém o valor local mesmo que o valor do servidor para esse papel também tenha mudado", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[PAPEL_1]!.add("processos:view");

    // Servidor, entretanto, também mudou o papel 1 (outro operador gravou por cima).
    const payloadFrescoComMudancaServidor: OfficeRbac = {
      ...payloadInicial,
      papeis: [
        { ...payloadInicial.papeis[0]!, permissoes: ["clientes:view"] },
        payloadInicial.papeis[1]!,
      ],
    };

    const resultado = mesclarEstadoLocal(payloadFrescoComMudancaServidor, localComEdicaoNaoGravada, new Set([PAPEL_1]));

    // O valor local (com o toggle não gravado) prevalece sobre o do servidor.
    expect(resultado[PAPEL_1]!.has("processos:view")).toBe(true);
    expect(resultado[PAPEL_1]!.has("clientes:view")).toBe(true);
  });

  it("um papel TOCADO que desaparece do payload fresco (apagado) é ignorado sem lançar", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[PAPEL_2]!.add("processos:view");

    // Payload fresco após apagar o próprio papel 2 (o que estava a ser editado).
    const payloadFrescoAposApagarPapel2: OfficeRbac = {
      ...payloadInicial,
      papeis: [payloadInicial.papeis[0]!],
    };

    expect(() =>
      mesclarEstadoLocal(payloadFrescoAposApagarPapel2, localComEdicaoNaoGravada, new Set([PAPEL_1, PAPEL_2])),
    ).not.toThrow();

    const resultado = mesclarEstadoLocal(
      payloadFrescoAposApagarPapel2,
      localComEdicaoNaoGravada,
      new Set([PAPEL_1, PAPEL_2]),
    );

    // O papel apagado simplesmente não existe no resultado -- nada para preservar.
    expect(resultado[PAPEL_2]).toBeUndefined();
    // O papel restante (não tocado neste teste) reflete o payload fresco.
    expect(resultado[PAPEL_1]!.has("clientes:view")).toBe(true);
  });

  it("renomear um papel DIFERENTE do que tem edições não gravadas não as apaga (o mapa é indexado por id, nunca por nome)", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    // Operador marca uma permissão no papel 2 ("Financeiro"), sem gravar.
    localComEdicaoNaoGravada[PAPEL_2]!.add("processos:view");

    // Entretanto, o papel 1 ("Recepção") é renomeado para "Atendimento ao Público" -- o `nome`
    // muda, o `id` não. As permissões do payload fresco do papel 1 também são exactamente as
    // mesmas de sempre (renomear nunca mexe em permissões, ver PapelRenameRequest no backend).
    const payloadFrescoAposRenomearPapel1: OfficeRbac = {
      ...payloadInicial,
      papeis: [
        { ...payloadInicial.papeis[0]!, nome: "Atendimento ao Público" },
        payloadInicial.papeis[1]!,
      ],
    };

    const resultado = mesclarEstadoLocal(
      payloadFrescoAposRenomearPapel1,
      localComEdicaoNaoGravada,
      new Set([PAPEL_2]), // só o papel 2 tem edição por gravar; o papel 1 nunca foi tocado
    );

    // A edição não gravada do papel 2 sobrevive à renomeação de um papel diferente.
    expect(resultado[PAPEL_2]!.has("processos:view")).toBe(true);
    // O papel renomeado continua endereçável sob a MESMA chave (o seu id).
    expect(resultado[PAPEL_1]!.has("clientes:view")).toBe(true);
  });
});

// 127-UI-REVIEW.md, Top 3 Priority Fixes #1: o único sinal de "há edições por gravar" era o
// botão "Guardar Alterações" passar de desvanecido a azul sólido. `papeisComAlteracoesPorGravar`
// é a mesma comparação de conjuntos que já decidia o disabled/enabled desse botão, extraída de
// RbacTab (settings/page.tsx) para ficar coberta por um teste fora do componente React;
// `rotuloPapeisPorGravar` é o texto do novo indicador de contagem introduzido a par do botão.
describe("papeisComAlteracoesPorGravar: deteção de edições locais por gravar", () => {
  it("localPermissoes null não reporta nenhum papel alterado", () => {
    expect(papeisComAlteracoesPorGravar(payloadInicial.papeis, null)).toEqual([]);
  });

  it("um papel cujo conjunto local é idêntico ao gravado não é reportado", () => {
    const local = construirEstadoLocal(payloadInicial);
    expect(papeisComAlteracoesPorGravar(payloadInicial.papeis, local)).toEqual([]);
  });

  it("um papel com uma permissão adicionada localmente é reportado", () => {
    const local = construirEstadoLocal(payloadInicial);
    local[PAPEL_1]!.add("processos:view");

    const resultado = papeisComAlteracoesPorGravar(payloadInicial.papeis, local);

    expect(resultado.map((papel) => papel.id)).toEqual([PAPEL_1]);
  });

  it("um papel com uma permissão removida localmente é reportado, mesmo com o mesmo tamanho de conjunto que outro papel alterado", () => {
    const local = construirEstadoLocal(payloadInicial);
    local[PAPEL_1]!.delete("clientes:view"); // fica vazio, tamanho 0

    const resultado = papeisComAlteracoesPorGravar(payloadInicial.papeis, local);

    expect(resultado.map((papel) => papel.id)).toEqual([PAPEL_1]);
  });

  it("vários papéis alterados em simultâneo são todos reportados", () => {
    const local = construirEstadoLocal(payloadInicial);
    local[PAPEL_1]!.add("processos:view");
    local[PAPEL_2]!.add("clientes:view");

    const resultado = papeisComAlteracoesPorGravar(payloadInicial.papeis, local);

    expect(resultado.map((papel) => papel.id).sort()).toEqual([PAPEL_1, PAPEL_2].sort());
  });
});

describe("rotuloPapeisPorGravar: pluralização do indicador junto de 'Guardar Alterações'", () => {
  it("singular para exatamente 1 papel", () => {
    expect(rotuloPapeisPorGravar(1)).toBe("1 papel com alterações por gravar");
  });

  it("plural para 0 ou mais de 1 papel", () => {
    expect(rotuloPapeisPorGravar(0)).toBe("0 papéis com alterações por gravar");
    expect(rotuloPapeisPorGravar(2)).toBe("2 papéis com alterações por gravar");
    expect(rotuloPapeisPorGravar(11)).toBe("11 papéis com alterações por gravar");
  });
});
