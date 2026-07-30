// Prova automatizada e executavel (Node puro, sem dependencias) das 12
// assercoes de origem para o bloqueio do botao "Guardar Regras" na aba
// "Controlo de Acesso (RBAC)" das Definicoes (Phase 121, ISOL-03, Plan 02) —
// web/src/app/(dashboard)/settings/page.tsx, RbacTab.
//
// Le o ficheiro-alvo como texto puro (sem import de modulo, sem
// type-stripping), normaliza removendo comentarios, extrai o bloco de
// RbacTab por marcador, e testa cada assercao com um predicado dedicado.
// Imprime "PASS <id>" ou "FAIL <id> — <motivo>" por assercao; exit 0 se
// todas passarem, exit 1 caso contrario. Segue o estilo de
// verify-consola-tenants.mjs (Phase 120) e
// verify-limite-utilizadores-indicator.mjs (Phase 118) — Node puro, sem
// dependencias, mesma tecnica de stripComments e de localizacao de bloco
// por marcador.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano do
// Plan 04 — um gate de origem prova estrutura, nao renderizacao):
//   1. Que o Tooltip realmente aparece ao passar o rato E ao focar por
//      teclado sobre o Badge.
//   2. Que o Badge renderiza com as cores neutras certas no browser.
//   3. Que o backend devolve mesmo 403 a um ADMIN de escritorio que chame
//      PUT /admin/rbac diretamente (esse e um teste backend dedicado, nao
//      um gate de origem frontend).

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SETTINGS_PAGE_PATH = path.join(
  __dirname,
  "..",
  "src",
  "app",
  "(dashboard)",
  "settings",
  "page.tsx"
);

const TOOLTIP_PHRASE_NORMALIZADA =
  "As regras de acesso por perfil (RBAC) passaram a ser uma configuração fixa e comum a toda a plataforma LexCV — já não podem ser alteradas a partir de um escritório individual.".replace(
    /\s+/g,
    " "
  );

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, o proprio cabecalho deste ficheiro ou qualquer
 * comentario do codigo-fonte que mencione um dos tokens procurados tornaria
 * o gate auto-invalidante (falso positivo). Identico ao stripComments de
 * verify-consola-tenants.mjs / verify-limite-utilizadores-indicator.mjs.
 */
function stripComments(source) {
  let out = source;
  // Blocos de comentario JSX: {/* ... */} (remove incluindo as chavetas envolventes)
  out = out.replace(/\{\s*\/\*[\s\S]*?\*\/\s*\}/g, "");
  // Blocos de comentario remanescentes: /* ... */
  out = out.replace(/\/\*[\s\S]*?\*\//g, "");
  // Linhas de comentario de linha (// ...) ou linhas de continuacao tipo JSDoc (* ...)
  out = out
    .split("\n")
    .filter((line) => {
      const trimmed = line.trim();
      return !trimmed.startsWith("//") && !trimmed.startsWith("*");
    })
    .join("\n");
  return out;
}

/**
 * Extrai o corpo de RbacTab: do indice de "function RbacTab()" (no ficheiro
 * ja sem comentarios) ate ao proximo "\nfunction " a seguir a esse indice
 * (na pratica, o inicio de "function NotificationPreferencesTab()"). Lanca
 * erro claro se algum dos dois marcadores nao for encontrado, para uma
 * assercao nunca falhar silenciosamente por um bloco vazio/errado.
 */
function extractRbacTabBlock(source) {
  const startIdx = source.indexOf("function RbacTab()");
  if (startIdx === -1) {
    throw new Error("Marcador 'function RbacTab()' nao encontrado no ficheiro");
  }
  const nextFnIdx = source.indexOf("\nfunction ", startIdx + 1);
  if (nextFnIdx === -1) {
    throw new Error(
      "Marcador de fecho '\\nfunction ' (a seguir a RbacTab) nao encontrado"
    );
  }
  return source.slice(startIdx, nextFnIdx);
}

async function main() {
  const settingsPageRaw = await fs.readFile(SETTINGS_PAGE_PATH, "utf-8");
  const settingsPage = stripComments(settingsPageRaw);
  const rbacBlock = extractRbacTabBlock(settingsPage);

  const assertions = [
    {
      id: "A01-usemme-dentro-do-rbactab",
      descricao: "o bloco de RbacTab contem uma chamada a useMe()",
      predicate: () => rbacBlock.includes("useMe()"),
    },
    {
      id: "A02-hook-antes-do-primeiro-early-return",
      descricao:
        "no bloco, o indice de useMe() e menor que o indice de 'if (isLoading)' — a violacao mais provavel desta alteracao (Regra dos Hooks)",
      predicate: () => {
        const useMeIdx = rbacBlock.indexOf("useMe()");
        const earlyReturnIdx = rbacBlock.indexOf("if (isLoading)");
        return useMeIdx !== -1 && earlyReturnIdx !== -1 && useMeIdx < earlyReturnIdx;
      },
    },
    {
      id: "A03-isplatformadmin-com-isfetched-e-papel",
      descricao:
        'existe uma linha que comeca por "const isPlatformAdmin" e que contem simultaneamente me.isFetched e roles?.includes("PLATAFORMA_ADMIN")',
      predicate: () => {
        const linha = rbacBlock
          .split("\n")
          .find((l) => l.trim().startsWith("const isPlatformAdmin"));
        if (!linha) return false;
        return (
          linha.includes("me.isFetched") &&
          linha.includes('roles?.includes("PLATAFORMA_ADMIN")')
        );
      },
    },
    {
      id: "A04-isfetched-antes-do-papel",
      descricao:
        "nessa mesma linha, o indice de me.isFetched e menor que o indice de PLATAFORMA_ADMIN (fail-closed durante a janela de carregamento: o estado por omissao e o Badge, nunca o botao)",
      predicate: () => {
        const linha = rbacBlock
          .split("\n")
          .find((l) => l.trim().startsWith("const isPlatformAdmin"));
        if (!linha) return false;
        const isFetchedIdx = linha.indexOf("me.isFetched");
        const papelIdx = linha.indexOf("PLATAFORMA_ADMIN");
        return isFetchedIdx !== -1 && papelIdx !== -1 && isFetchedIdx < papelIdx;
      },
    },
    {
      id: "A05-guardar-regras-sob-condicao",
      descricao:
        "no bloco, o indice de 'isPlatformAdmin ?' e menor que o indice de 'Guardar Regras' (o botao esta dentro do ramo verdadeiro do ternario, nao incondicional)",
      predicate: () => {
        const condIdx = rbacBlock.indexOf("isPlatformAdmin ?");
        const buttonTextIdx = rbacBlock.indexOf("Guardar Regras");
        return condIdx !== -1 && buttonTextIdx !== -1 && condIdx < buttonTextIdx;
      },
    },
    {
      id: "A06-badge-outline-com-rotulo",
      descricao:
        'no bloco, variant="outline" e "Gerido pela Plataforma" existem ambos, e o rotulo aparece depois de "isPlatformAdmin ?" (esta no ramo falso do ternario)',
      predicate: () => {
        const condIdx = rbacBlock.indexOf("isPlatformAdmin ?");
        const outlineIdx = rbacBlock.indexOf('variant="outline"');
        const rotuloIdx = rbacBlock.indexOf("Gerido pela Plataforma");
        return (
          condIdx !== -1 && outlineIdx !== -1 && rotuloIdx !== -1 && rotuloIdx > condIdx
        );
      },
    },
    {
      id: "A07-span-tabindex-dentro-do-tooltiptrigger",
      descricao:
        "no bloco, os indices de 'TooltipTrigger asChild', '<span tabIndex={0}' e '<Badge' aparecem por essa ordem estrita (o Badge nao e nativamente focavel, o wrapper e obrigatorio para o Tooltip abrir por teclado)",
      predicate: () => {
        const triggerIdx = rbacBlock.indexOf("TooltipTrigger asChild");
        const spanIdx = rbacBlock.indexOf("<span tabIndex={0}");
        const badgeIdx = rbacBlock.indexOf("<Badge");
        return (
          triggerIdx !== -1 &&
          spanIdx !== -1 &&
          badgeIdx !== -1 &&
          triggerIdx < spanIdx &&
          spanIdx < badgeIdx
        );
      },
    },
    {
      id: "A08-texto-exato-do-tooltip",
      descricao:
        "o bloco, com espacos normalizados, contem a frase exata do UI-SPEC para o TooltipContent, tambem ela normalizada",
      predicate: () => {
        const normalizado = rbacBlock.replace(/\s+/g, " ");
        return normalizado.includes(TOOLTIP_PHRASE_NORMALIZADA);
      },
    },
    {
      id: "A09-hasrbacmanage-inalterado",
      descricao:
        'o ficheiro completo (sem comentarios) contem \'const hasRbacManage = can.manage("rbac") || isAdmin;\' — guarda de nao-regressao para o non-goal de nao alargar a visibilidade do separador',
      predicate: () =>
        settingsPage.includes('const hasRbacManage = can.manage("rbac") || isAdmin;'),
    },
    {
      id: "A10-handlesave-e-leitura-intactos",
      descricao:
        'o bloco contem apiFetch("/admin/rbac", method: "PUT" e useAdminRbac() — o caminho de gravacao/leitura fica inalcancavel via UI para quem perde o botao, mas nao e apagado nem reescrito',
      predicate: () =>
        rbacBlock.includes('apiFetch("/admin/rbac"') &&
        rbacBlock.includes('method: "PUT"') &&
        rbacBlock.includes("useAdminRbac()"),
    },
    {
      id: "A11-matriz-admin-imutavel-inalterada",
      descricao:
        'o bloco contem \'if (role === "ADMIN") return;\' — guarda de nao-regressao para o non-goal de nao adicionar nenhum disabled novo a matriz',
      predicate: () => rbacBlock.includes('if (role === "ADMIN") return;'),
    },
    {
      id: "A12-matriz-so-leitura-para-nao-plataforma",
      descricao:
        'o bloco contem \'if (!isPlatformAdmin) return;\' em handleCheckboxChange E \'const isDisabled = isAdminRow || !isPlatformAdmin;\' — WR-01 (121-REVIEW.md): a matriz deixa de aceitar cliques (mesmo so localmente) para quem nao pode gravar, sem forcar checked=true nas colunas nao-ADMIN',
      predicate: () =>
        rbacBlock.includes("if (!isPlatformAdmin) return;") &&
        rbacBlock.includes(
          "const isDisabled = isAdminRow || !isPlatformAdmin;"
        ),
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

  process.exit(failures === 0 ? 0 : 1);
}

main();
