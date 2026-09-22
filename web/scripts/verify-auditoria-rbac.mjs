// Prova automatizada e executavel (Node puro, sem dependencias) da Read-Only Guarantee
// (AUDT-04) e da fiacao aditiva da aba "Auditoria" das Definicoes (Phase 128, Plano 09) --
// web/src/app/(dashboard)/settings/auditoria-tab.tsx, page.tsx, hooks/use-admin.ts e
// lib/auditoria-rbac.ts.
//
// Todas as assercoes sao verificacoes de substring LITERAIS sobre o texto bruto dos ficheiros
// (sem remover comentarios) -- deliberado, ao contrario de verify-papeis-escritorio.mjs: a
// propria acceptance-criteria deste plano exige que ATE um comentario `// useMutation` acrescentado
// a auditoria-tab.tsx faca este gate falhar (Task 2, acceptance criteria), o que um passo de
// stripComments impediria silenciosamente. Os proprios ficheiros-alvo (Task 1) sao escritos para
// nunca conter os tokens proibidos, nem em prosa/comentario nem em codigo -- ver o proprio
// docstring de auditoria-tab.tsx, que descreve estas garantias sem usar os tokens literais.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano da Task 3 deste plano --
// um gate de origem prova estrutura, nao renderizacao):
//   1. Que a aba renderiza as frases correctas com o backend real a correr.
//   2. Que os filtros e o paginador reagem correctamente a cliques no browser.
//   3. Que o rascunho nao gravado do RbacTab sobrevive a troca de separador para Auditoria e de
//      volta (Phase 127 fix, passo 7 do checkpoint humano).

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUDITORIA_TAB_PATH = path.join(
  __dirname,
  "..",
  "src",
  "app",
  "(dashboard)",
  "settings",
  "auditoria-tab.tsx"
);
const SETTINGS_PAGE_PATH = path.join(
  __dirname,
  "..",
  "src",
  "app",
  "(dashboard)",
  "settings",
  "page.tsx"
);
const USE_ADMIN_PATH = path.join(__dirname, "..", "src", "hooks", "use-admin.ts");
const AUDITORIA_LIB_PATH = path.join(__dirname, "..", "src", "lib", "auditoria-rbac.ts");

/**
 * Extrai a fatia de texto entre a declaracao de `useOfficeRbacAuditoria` e o proximo
 * "export function" (ou o fim do ficheiro) -- usado para confirmar que o hook e um `useQuery`
 * puro, sem nenhum hook de gravacao colado ao lado na mesma funcao.
 */
function extractHookBlock(source, marker) {
  const startIdx = source.indexOf(marker);
  if (startIdx === -1) {
    throw new Error(`Marcador '${marker}' nao encontrado no ficheiro`);
  }
  const nextFnIdx = source.indexOf("export function", startIdx + marker.length);
  return nextFnIdx === -1 ? source.slice(startIdx) : source.slice(startIdx, nextFnIdx);
}

async function main() {
  const auditoriaTab = await fs.readFile(AUDITORIA_TAB_PATH, "utf-8");
  const settingsPage = await fs.readFile(SETTINGS_PAGE_PATH, "utf-8");
  const useAdmin = await fs.readFile(USE_ADMIN_PATH, "utf-8");
  const auditoriaLib = await fs.readFile(AUDITORIA_LIB_PATH, "utf-8");

  const MUTACAO_TOKEN = "use" + "Mutation";

  const assertions = [
    // ---- (a) Read-Only Guarantee: nenhuma superficie de mutacao no componente ----
    {
      id: "sem-usemutation",
      descricao: `auditoria-tab.tsx nao contem '${MUTACAO_TOKEN}' (nem em codigo, nem em comentario)`,
      predicate: () => !auditoriaTab.includes(MUTACAO_TOKEN),
    },
    {
      id: "sem-dangerously-set-inner-html",
      descricao: "auditoria-tab.tsx nao contem 'dangerouslySetInnerHTML'",
      predicate: () => !auditoriaTab.includes("dangerouslySetInnerHTML"),
    },
    {
      id: "sem-dropdown-menu",
      descricao: "auditoria-tab.tsx nao contem 'DropdownMenu'",
      predicate: () => !auditoriaTab.includes("DropdownMenu"),
    },
    {
      id: "sem-icone-trash",
      descricao: "auditoria-tab.tsx nao contem 'Trash'",
      predicate: () => !auditoriaTab.includes("Trash"),
    },
    {
      id: "sem-icone-pencil",
      descricao: "auditoria-tab.tsx nao contem 'Pencil'",
      predicate: () => !auditoriaTab.includes("Pencil"),
    },
    {
      id: "sem-metodos-mutadores",
      descricao:
        'auditoria-tab.tsx nao contem nenhum de \'method: "DELETE"\', \'method: "PUT"\', \'method: "POST"\', \'method: "PATCH"\'',
      predicate: () =>
        !auditoriaTab.includes('method: "DELETE"') &&
        !auditoriaTab.includes('method: "PUT"') &&
        !auditoriaTab.includes('method: "POST"') &&
        !auditoriaTab.includes('method: "PATCH"'),
    },

    // ---- (b) wiring da leitura, sem codigo bruto renderizado ----
    {
      id: "usa-hook-de-leitura",
      descricao: "auditoria-tab.tsx contem 'useOfficeRbacAuditoria(' e 'auditoriaEventoToSentence('",
      predicate: () =>
        auditoriaTab.includes("useOfficeRbacAuditoria(") &&
        auditoriaTab.includes("auditoriaEventoToSentence("),
    },
    {
      id: "sem-acao-bruta-no-ecra",
      descricao: "auditoria-tab.tsx nao renderiza '{entry.acao}' diretamente",
      predicate: () => !auditoriaTab.includes("{entry.acao}"),
    },

    // ---- (c) o hook em use-admin.ts e um useQuery puro ----
    {
      id: "hook-e-usequery-puro",
      descricao:
        "use-admin.ts declara 'export function useOfficeRbacAuditoria' e, na fatia ate ao proximo 'export function', contem 'useQuery(' e nao contem o hook de gravacao do TanStack Query",
      predicate: () => {
        if (!useAdmin.includes("export function useOfficeRbacAuditoria")) return false;
        const bloco = extractHookBlock(useAdmin, "export function useOfficeRbacAuditoria");
        return bloco.includes("useQuery(") && !bloco.includes(MUTACAO_TOKEN + "(");
      },
    },

    // ---- (d) fiacao aditiva em page.tsx, Phase 127 intacta ----
    {
      id: "wiring-aditivo-em-page",
      descricao:
        'page.tsx contem \'"auditoria"\', \'activeTab === "auditoria" && hasRbacManage\', \'papeisComAlteracoesPorGravar\' e \'beforeunload\'',
      predicate: () =>
        settingsPage.includes('"auditoria"') &&
        settingsPage.includes('activeTab === "auditoria" && hasRbacManage') &&
        settingsPage.includes("papeisComAlteracoesPorGravar") &&
        settingsPage.includes("beforeunload"),
    },

    // ---- (e) lib/auditoria-rbac.ts mantem os tres fallbacks vinculativos ----
    {
      id: "fallbacks-de-nome-nulo",
      descricao:
        "lib/auditoria-rbac.ts contem as tres frases fixas de fallback ('um administrador removido', 'um utilizador removido', 'um papel removido')",
      predicate: () =>
        auditoriaLib.includes("um administrador removido") &&
        auditoriaLib.includes("um utilizador removido") &&
        auditoriaLib.includes("um papel removido"),
    },
  ];

  let failures = 0;
  for (const assertion of assertions) {
    let pass = false;
    let error = null;
    try {
      pass = assertion.predicate();
    } catch (err) {
      error = err;
    }
    if (pass) {
      console.log(`PASS ${assertion.id}`);
    } else {
      failures += 1;
      const motivo = error ? error.message : assertion.descricao;
      console.log(`FAIL ${assertion.id} — ${motivo}`);
    }
  }

  if (failures === 0) {
    console.log(`\nverify-auditoria-rbac: ${assertions.length}/${assertions.length} PASS`);
  }

  process.exit(failures === 0 ? 0 : 1);
}

main();
