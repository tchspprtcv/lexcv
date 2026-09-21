import { describe, expect, it } from "vitest";

import { construirEstadoLocal, mesclarEstadoLocal } from "./merge-local-permissoes";
import type { MoldesConsola } from "@/types/platform-moldes";

// CR-01 (125-REVIEW.md): "Creating a molde silently discards unsaved
// permission-matrix edits". Reproduz o bug e prova o fix ao nível da função
// pura que `moldes/page.tsx` agora usa para reconciliar `localPermissoes`
// com um payload fresco da query (ver o doc-comment de
// `mesclarEstadoLocal` para a decisão completa).
//
// Antes deste fix, `moldes/page.tsx` fazia isto em vez de chamar
// `mesclarEstadoLocal`:
//
//   if (data && data !== appliedData) {
//     setAppliedData(data);
//     setLocalPermissoes(construirEstadoLocal(data)); // <- substituição cega
//   }
//
// O primeiro `it` abaixo prova que essa substituição cega (reproduzida
// literalmente, chamando `construirEstadoLocal(dataFresca)` sozinho, sem
// merge) perde o toggle não gravado -- falha se alguém reintroduzir esse
// padrão. Os restantes provam que `mesclarEstadoLocal` -- a função que
// substitui esse padrão em `page.tsx` -- não perde a edição.

const payloadInicial: MoldesConsola = {
  permissoes: [
    { key: "clientes:view", nome: "Ver Clientes", descricao: null, modulo: "Clientes" },
    { key: "processos:view", nome: "Ver Processos", descricao: null, modulo: "Processos" },
  ],
  moldes: [
    { id: 1, nome: "ADVOGADO", permissoes: ["clientes:view"], escritoriosInstanciados: 2 },
    { id: 2, nome: "ASSISTENTE", permissoes: [], escritoriosInstanciados: 0 },
  ],
};

describe("CR-01: reset da matriz de moldes nao pode apagar edicoes nao gravadas", () => {
  it("reproduz o bug: substituicao cega (construirEstadoLocal isolado) perde um toggle nao gravado", () => {
    // Estado local depois de o operador marcar "processos:view" no molde
    // ADVOGADO (id 1), SEM gravar.
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[1]!.add("processos:view");
    expect(localComEdicaoNaoGravada[1]!.has("processos:view")).toBe(true);

    // "Criar Molde" dispara uma invalidacao de MOLDES_LIST_KEY; o payload
    // fresco tem uma referencia nova e um 3o molde, mas o molde 1
    // continua com o MESMO conjunto de permissoes gravadas no servidor
    // (["clientes:view"]) -- o toggle do operador nunca foi gravado.
    const payloadFrescoAposCriarMolde: MoldesConsola = {
      ...payloadInicial,
      moldes: [
        ...payloadInicial.moldes,
        { id: 3, nome: "SUPERVISOR", permissoes: [], escritoriosInstanciados: 0 },
      ],
    };

    // Isto e literalmente o que o codigo pre-fix fazia: reconstruir do
    // zero a partir do payload fresco, ignorando `localComEdicaoNaoGravada`.
    const estadoPosBugReproduzido = construirEstadoLocal(payloadFrescoAposCriarMolde);

    // A prova do bug: o toggle nao gravado desapareceu.
    expect(estadoPosBugReproduzido[1]!.has("processos:view")).toBe(false);
  });

  it("mesclarEstadoLocal preserva um toggle nao gravado num molde tocado quando chega um payload fresco", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[1]!.add("processos:view");

    const payloadFrescoAposCriarMolde: MoldesConsola = {
      ...payloadInicial,
      moldes: [
        ...payloadInicial.moldes,
        { id: 3, nome: "SUPERVISOR", permissoes: [], escritoriosInstanciados: 0 },
      ],
    };

    const touchedMoldeIds = new Set([1]);

    const resultado = mesclarEstadoLocal(
      payloadFrescoAposCriarMolde,
      localComEdicaoNaoGravada,
      touchedMoldeIds,
    );

    // O toggle nao gravado sobrevive.
    expect(resultado[1]!.has("processos:view")).toBe(true);
    expect(resultado[1]!.has("clientes:view")).toBe(true);
  });

  it("mesclarEstadoLocal inicializa a partir do payload fresco um molde NUNCA tocado, incluindo um molde novo", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[1]!.add("processos:view");

    const payloadFrescoAposCriarMolde: MoldesConsola = {
      ...payloadInicial,
      moldes: [
        ...payloadInicial.moldes,
        { id: 3, nome: "SUPERVISOR", permissoes: ["clientes:view"], escritoriosInstanciados: 0 },
      ],
    };

    const touchedMoldeIds = new Set([1]); // molde 2 e 3 nunca foram tocados

    const resultado = mesclarEstadoLocal(
      payloadFrescoAposCriarMolde,
      localComEdicaoNaoGravada,
      touchedMoldeIds,
    );

    // Molde 2 (nunca tocado) reflete o payload fresco.
    expect(resultado[2]!.size).toBe(0);
    // Molde 3 (novo, nunca tocado) aparece inicializado a partir do payload fresco.
    expect(resultado[3]).toBeDefined();
    expect(resultado[3]!.has("clientes:view")).toBe(true);
  });

  it("mesclarEstadoLocal com prevLocal null inicializa tudo a partir do payload fresco (primeiro load)", () => {
    const resultado = mesclarEstadoLocal(payloadInicial, null, new Set());
    expect(resultado[1]!.has("clientes:view")).toBe(true);
    expect(resultado[2]!.size).toBe(0);
  });

  it("mesclarEstadoLocal mantem o valor local de um molde tocado mesmo que o valor do servidor para esse molde tambem tenha mudado (decisao: nunca apagar trabalho nao gravado)", () => {
    const localComEdicaoNaoGravada = construirEstadoLocal(payloadInicial);
    localComEdicaoNaoGravada[1]!.delete("clientes:view"); // operador desmarcou, sem gravar

    // Servidor, entretanto, tambem mudou o molde 1 (outro operador gravou).
    const payloadFrescoComMudancaServidor: MoldesConsola = {
      ...payloadInicial,
      moldes: [
        { id: 1, nome: "ADVOGADO", permissoes: ["clientes:view", "processos:view"], escritoriosInstanciados: 2 },
        payloadInicial.moldes[1]!,
      ],
    };

    const resultado = mesclarEstadoLocal(
      payloadFrescoComMudancaServidor,
      localComEdicaoNaoGravada,
      new Set([1]),
    );

    // O valor local (vazio) prevalece -- nunca o do servidor -- para o molde tocado.
    expect(resultado[1]!.size).toBe(0);
  });
});
