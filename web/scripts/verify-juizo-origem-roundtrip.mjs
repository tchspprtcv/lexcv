// Prova automatizada e executável (Node puro, sem dependências) do round-trip
// juizo/origem descrito em PITFALLS.md Pitfall 1.
//
// Este script importa DIRETAMENTE `../src/lib/processo-juizo-origem-mapping.ts`
// — o MESMO módulo partilhado que `web/src/hooks/use-processos.ts` usa em
// runtime dentro de `normalizeProcesso`/`toProcessoApiPayload`. Não é uma
// cópia/reimplementação: se o mapeamento real quebrar, este script quebra
// também, porque é literalmente o mesmo código a correr.
//
// Node >= 22 faz type-stripping nativo de `.ts` sem flags nem dependências
// (confirmado empiricamente nesta máquina em Node v24.15.0) — o único
// obstáculo real seria um path-alias `@/...`, e o módulo partilhado não usa
// nenhum (só um import relativo type-only, elidido em runtime).

import assert from "node:assert";
import {
  mapJuizoOrigemFromApi,
  mapJuizoOrigemToPayload,
} from "../src/lib/processo-juizo-origem-mapping.ts";

// (a) mapJuizoOrigemFromApi com juizo/origem definidos -> devolve valores inalterados
{
  const result = mapJuizoOrigemFromApi({
    juizo: "1º Juízo Cível",
    origem: "PETICAO_INICIAL",
  });
  assert.strictEqual(result.juizo, "1º Juízo Cível");
  assert.strictEqual(result.origem, "PETICAO_INICIAL");
}

// (b) mapJuizoOrigemToPayload sobre um "create" com juizo+origem definidos -> ambos definidos
{
  const result = mapJuizoOrigemToPayload({
    juizo: "1º Juízo Cível",
    origem: "PETICAO_INICIAL",
  });
  assert.strictEqual(result.juizo, "1º Juízo Cível");
  assert.strictEqual(result.origem, "PETICAO_INICIAL");
}

// (c) mapJuizoOrigemToPayload sobre um "update" (sem propriedade origem) -> origem estritamente undefined
{
  const updatePayload = { juizo: "2º Juízo Cível" };
  const result = mapJuizoOrigemToPayload(updatePayload);
  assert.strictEqual(result.juizo, "2º Juízo Cível");
  assert.strictEqual(result.origem, undefined);
  assert.ok(!("origem" in updatePayload), "updatePayload não deve ter a propriedade origem");
}

// (d) round-trip completo: fromApi -> construir payload de create -> toPayload -> valores sobrevivem
{
  const apiResponse = {
    juizo: "3º Juízo Cível",
    origem: "NOTIFICACOES_AVULSAS",
  };
  const fromApi = mapJuizoOrigemFromApi(apiResponse);

  const createRequest = { juizo: fromApi.juizo, origem: fromApi.origem };
  const toPayload = mapJuizoOrigemToPayload(createRequest);

  assert.strictEqual(toPayload.juizo, "3º Juízo Cível");
  assert.strictEqual(toPayload.origem, "NOTIFICACOES_AVULSAS");
}

console.log("PASS");
