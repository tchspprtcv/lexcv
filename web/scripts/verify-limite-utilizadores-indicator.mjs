// Prova automatizada e executável (Node puro, sem dependências) das assercoes de
// origem para o indicador "X/Y utilizadores" na aba "Gestão de Utilizadores"
// (Phase 118, Plan 02).
//
// Le os ficheiros-alvo como texto puro (sem import de modulo, sem type-stripping),
// normaliza removendo comentarios, e testa cada assercao com um predicado dedicado.
// Imprime "PASS <id>" ou "FAIL <id> — <motivo>" por assercao; exit 0 se todas
// passarem, exit 1 caso contrario. Segue o estilo de verify-juizo-origem-roundtrip.mjs
// (o unico outro script de verificacao deste projeto) — Node puro, sem dependencias.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUTH_TYPES_PATH = path.join(__dirname, "..", "src", "types", "auth.ts");
const SETTINGS_PAGE_PATH = path.join(
  __dirname,
  "..",
  "src",
  "app",
  "(dashboard)",
  "settings",
  "page.tsx"
);

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, um comentario que mencione um dos tokens procurados
 * tornaria o gate auto-invalidante (falso positivo).
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
 * Delimita o bloco <Tooltip>...</Tooltip> mais proximo que contem `needle`.
 * Devolve null se nao encontrar abertura/fecho — usado pela assercao
 * span-wrapper-tooltip para nao confundir este Tooltip novo com os 2 Tooltips
 * de Editar/Eliminar ja existentes no mesmo ficheiro.
 */
function findTooltipBlockContaining(source, needle) {
  const needleIdx = source.indexOf(needle);
  if (needleIdx === -1) return null;
  const openIdx = source.lastIndexOf("<Tooltip>", needleIdx);
  if (openIdx === -1) return null;
  const closeIdx = source.indexOf("</Tooltip>", needleIdx);
  if (closeIdx === -1) return null;
  return source.slice(openIdx, closeIdx + "</Tooltip>".length);
}

const TOOLTIP_PHRASE =
  "Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga.";

async function main() {
  const [authTypesRaw, settingsPageRaw] = await Promise.all([
    fs.readFile(AUTH_TYPES_PATH, "utf-8"),
    fs.readFile(SETTINGS_PAGE_PATH, "utf-8"),
  ]);

  const authTypes = stripComments(authTypesRaw);
  const settingsPage = stripComments(settingsPageRaw);

  const assertions = [
    {
      id: "types-auth-tenant-plano",
      descricao:
        "web/src/types/auth.ts contem 'tenant_plano?: string | null;' dentro de MeResponse (com '| null' explicito)",
      predicate: () => authTypes.includes("tenant_plano?: string | null;"),
    },
    {
      id: "types-auth-tenant-limite",
      descricao:
        "web/src/types/auth.ts contem 'tenant_limite_utilizadores?: number | null;' (com '| null' explicito)",
      predicate: () =>
        authTypes.includes("tenant_limite_utilizadores?: number | null;"),
    },
    {
      id: "types-auth-tenant-utilizadores-ativos",
      descricao:
        "web/src/types/auth.ts contem 'tenant_utilizadores_ativos?: number | null;' (com '| null' explicito)",
      predicate: () =>
        authTypes.includes("tenant_utilizadores_ativos?: number | null;"),
    },
    {
      id: "toast-prefix-generico",
      descricao:
        'settings/page.tsx usa replace(/^API \\d{3}: /, "") e ja nao contem "API 400: " hardcoded',
      predicate: () =>
        settingsPage.includes('replace(/^API \\d{3}: /, "")') &&
        !settingsPage.includes("API 400: "),
    },
    {
      id: "use-me-auto-fetch",
      descricao:
        "settings/page.tsx importa useMe de @/hooks/use-me e chama useMe() dentro do proprio ficheiro (auto-fetch, sem prop-drilling)",
      predicate: () => {
        const importsUseMe =
          /import\s*\{[^}]*\buseMe\b[^}]*\}\s*from\s*"@\/hooks\/use-me"/.test(
            settingsPage
          );
        const callsUseMe = /\buseMe\(\)/.test(settingsPage);
        return importsUseMe && callsUseMe;
      },
    },
    {
      id: "contagem-da-fonte-unica",
      descricao:
        "settings/page.tsx contem 'meData?.tenant_utilizadores_ativos' (contagem lida da fonte unica do backend, Phase 124) E NAO contem '.ativo === true' (guarda negativa permanente contra o regresso do filtro client-side) E continua a conter 'user.ativo !== false' (convencao de exibicao do badge da tabela, inalterada)",
      predicate: () =>
        settingsPage.includes("meData?.tenant_utilizadores_ativos") &&
        !settingsPage.includes(".ativo === true") &&
        settingsPage.includes("user.ativo !== false"),
    },
    {
      id: "copy-contract",
      descricao:
        "settings/page.tsx contem as 3 strings de copy verbatim do UI-SPEC: 'utilizadores', '· limite atingido' e a frase completa do tooltip",
      predicate: () =>
        settingsPage.includes("utilizadores") &&
        settingsPage.includes("· limite atingido") &&
        settingsPage.includes(TOOLTIP_PHRASE),
    },
    {
      id: "span-wrapper-tooltip",
      descricao:
        "o bloco <Tooltip> que contem a frase do tooltip tem <TooltipTrigger asChild> seguido (so espacos/quebras de linha) de <span tabIndex={0}>, contem 'disabled', e tabIndex={0} aparece ANTES de disabled (span focavel envolve o botao desativado)",
      predicate: () => {
        const block = findTooltipBlockContaining(settingsPage, TOOLTIP_PHRASE);
        if (!block) return false;
        const adjacencyMatch = block.match(
          /<TooltipTrigger\s+asChild>\s*<span\b([^>]*)>/
        );
        if (!adjacencyMatch) return false;
        const spanAttrs = adjacencyMatch[1];
        const hasTabIndexOnSpan = /\btabIndex=\{0\}/.test(spanAttrs);
        const hasDisabled = /\bdisabled\b/.test(block);
        const tabIndexIdx = block.indexOf("tabIndex={0}");
        const disabledIdx = block.indexOf("disabled");
        const orderOk =
          tabIndexIdx !== -1 && disabledIdx !== -1 && tabIndexIdx < disabledIdx;
        return hasTabIndexOnSpan && hasDisabled && orderOk;
      },
    },
    {
      id: "layout-stack",
      descricao:
        "settings/page.tsx contem 'flex flex-col items-end gap-2' (empilhamento contador-sobre-botao alinhado a direita)",
      predicate: () => settingsPage.includes("flex flex-col items-end gap-2"),
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
