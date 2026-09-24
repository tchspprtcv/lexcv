// Prova automatizada e executavel (Node puro, sem dependencias) das 12
// assercoes de origem para a consola de administracao de tenants
// (Phase 120, Plan 05) — /plataforma, columns.tsx, criar-tenant-panel.tsx,
// dashboard-shell.tsx, sidebar-nav.tsx e types/auth.ts.
//
// Le os ficheiros-alvo como texto puro (sem import de modulo, sem
// type-stripping), normaliza removendo comentarios, e testa cada assercao
// com um predicado dedicado. Imprime "PASS <id>" ou "FAIL <id> — <motivo>"
// por assercao; exit 0 se todas passarem, exit 1 caso contrario. Segue o
// estilo de verify-limite-utilizadores-indicator.mjs (Phase 118) — Node
// puro, sem dependencias, mesma tecnica de stripComments e de localizacao
// de bloco por marcador.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano do
// Plan 06, um gate de origem prova estrutura, nao renderizacao):
//   1. Que o tooltip do botao de suspensao desativado realmente aparece no
//      ecra ao passar o rato/focar por teclado.
//   2. Que suspender um tenant corta mesmo, de imediato, uma sessao ja
//      autenticada de um utilizador desse tenant.
//   3. Que os badges (plano/estado) renderizam com as cores certas no
//      browser.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const AUTH_TYPES_PATH = path.join(__dirname, "..", "src", "types", "auth.ts");
const DASHBOARD_SHELL_PATH = path.join(
  __dirname,
  "..",
  "src",
  "components",
  "shared",
  "dashboard-shell.tsx",
);
const SIDEBAR_NAV_PATH = path.join(
  __dirname,
  "..",
  "src",
  "components",
  "shared",
  "sidebar-nav.tsx",
);
const PLATAFORMA_DIR = path.join(__dirname, "..", "src", "app", "(dashboard)", "plataforma");
const PLATAFORMA_PAGE_PATH = path.join(PLATAFORMA_DIR, "page.tsx");
const COLUMNS_PATH = path.join(PLATAFORMA_DIR, "columns.tsx");
const CRIAR_TENANT_PANEL_PATH = path.join(PLATAFORMA_DIR, "criar-tenant-panel.tsx");

const GUARDA_TENANT_RESERVADO_PHRASE =
  "Não é possível suspender o tenant da plataforma (LexCV).";

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, um comentario que mencione um dos tokens procurados
 * tornaria o gate auto-invalidante (falso positivo). Identico ao
 * stripComments de verify-limite-utilizadores-indicator.mjs.
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
 * Devolve null se nao encontrar abertura/fecho.
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

/**
 * Delimita o texto entre dois marcadores literais (o primeiro encontrado a
 * partir do inicio, o segundo procurado so depois do primeiro). Devolve null
 * se algum marcador nao existir na ordem esperada.
 */
function sliceBetweenMarkers(source, startMarker, endMarker) {
  const startIdx = source.indexOf(startMarker);
  if (startIdx === -1) return null;
  const endIdx = source.indexOf(endMarker, startIdx);
  if (endIdx === -1) return null;
  return source.slice(startIdx, endIdx);
}

async function main() {
  const [
    authTypesRaw,
    dashboardShellRaw,
    sidebarNavRaw,
    plataformaPageRaw,
    columnsRaw,
    criarTenantPanelRaw,
  ] = await Promise.all([
    fs.readFile(AUTH_TYPES_PATH, "utf-8"),
    fs.readFile(DASHBOARD_SHELL_PATH, "utf-8"),
    fs.readFile(SIDEBAR_NAV_PATH, "utf-8"),
    fs.readFile(PLATAFORMA_PAGE_PATH, "utf-8"),
    fs.readFile(COLUMNS_PATH, "utf-8"),
    fs.readFile(CRIAR_TENANT_PANEL_PATH, "utf-8"),
  ]);

  const authTypes = stripComments(authTypesRaw);
  const dashboardShell = stripComments(dashboardShellRaw);
  const sidebarNav = stripComments(sidebarNavRaw);
  const plataformaPage = stripComments(plataformaPageRaw);
  const columnsSource = stripComments(columnsRaw);
  const criarTenantPanel = stripComments(criarTenantPanelRaw);

  const assertions = [
    {
      id: "role-union",
      descricao: "web/src/types/auth.ts inclui \"PLATAFORMA_ADMIN\" na uniao Role",
      predicate: () => {
        const match = authTypes.match(/export type Role\s*=\s*([^;]+);/);
        if (!match) return false;
        return match[1].includes('"PLATAFORMA_ADMIN"');
      },
    },
    {
      id: "nav-dois-call-sites",
      descricao:
        "dashboard-shell.tsx tem 2 ocorrencias de nav={navItems} e 0 de nav={NAV}",
      predicate: () => {
        const countNavItems = (dashboardShell.match(/nav=\{navItems\}/g) ?? []).length;
        const countNAV = (dashboardShell.match(/nav=\{NAV\}/g) ?? []).length;
        return countNavItems === 2 && countNAV === 0;
      },
    },
    {
      id: "sidebar-nav-intocado",
      descricao:
        "sidebar-nav.tsx NAO contem PLATAFORMA_ADMIN (o gate por papel vive no shell, nao no componente generico)",
      predicate: () => !sidebarNav.includes("PLATAFORMA_ADMIN"),
    },
    {
      id: "guard-de-pagina",
      descricao:
        'plataforma/page.tsx contem AccessDeniedState e a verificacao roles?.includes("PLATAFORMA_ADMIN")',
      predicate: () =>
        plataformaPage.includes("AccessDeniedState") &&
        plataformaPage.includes('roles?.includes("PLATAFORMA_ADMIN")'),
    },
    {
      id: "sem-reload",
      descricao:
        "nenhum dos 3 ficheiros da consola (page.tsx, columns.tsx, criar-tenant-panel.tsx) contem window.location.reload",
      predicate: () =>
        !plataformaPage.includes("window.location.reload") &&
        !columnsSource.includes("window.location.reload") &&
        !criarTenantPanel.includes("window.location.reload"),
    },
    {
      id: "tooltip-span-wrapper",
      descricao:
        "columns.tsx tem um bloco onde <TooltipTrigger asChild> e seguido de <span tabIndex={0}> que envolve um Button com disabled",
      predicate: () => {
        const block = findTooltipBlockContaining(columnsSource, GUARDA_TENANT_RESERVADO_PHRASE);
        if (!block) return false;
        const adjacencyMatch = block.match(/<TooltipTrigger\s+asChild>\s*<span\b([^>]*)>/);
        if (!adjacencyMatch) return false;
        const spanAttrs = adjacencyMatch[1];
        const hasTabIndexOnSpan = /\btabIndex=\{0\}/.test(spanAttrs);
        const hasDisabled = /\bdisabled\b/.test(block);
        const tabIndexIdx = block.indexOf("tabIndex={0}");
        const disabledIdx = block.indexOf("disabled");
        const orderOk = tabIndexIdx !== -1 && disabledIdx !== -1 && tabIndexIdx < disabledIdx;
        return hasTabIndexOnSpan && hasDisabled && orderOk;
      },
    },
    {
      id: "guarda-tenant-reservado",
      descricao:
        "columns.tsx contem a frase 'Não é possível suspender o tenant da plataforma (LexCV).' byte-identica",
      predicate: () => columnsSource.includes(GUARDA_TENANT_RESERVADO_PHRASE),
    },
    {
      id: "schema-setup-reutilizado",
      descricao:
        'criar-tenant-panel.tsx importa de "@/schemas/setup" e nao declara nenhum regex proprio de password (ausencia de "(?=.*")',
      predicate: () => {
        const importsSetupSchema = /from\s+"@\/schemas\/setup"/.test(criarTenantPanel);
        const hasOwnPasswordRegex = criarTenantPanel.includes("(?=.*");
        return importsSetupSchema && !hasOwnPasswordRegex;
      },
    },
    {
      id: "dialogos-separados",
      descricao:
        "page.tsx contem 'Suspender Tenant' e 'Reativar Tenant' como titulos distintos e o Dialog de edicao nao contem a palavra Suspender",
      predicate: () => {
        const temAmbosTitulos =
          plataformaPage.includes("Suspender Tenant") && plataformaPage.includes("Reativar Tenant");
        const blocoEdicao = sliceBetweenMarkers(
          plataformaPage,
          "function EditarTenantDialog",
          "function AlteracaoEstadoConteudo",
        );
        if (!blocoEdicao) return false;
        return temAmbosTitulos && !blocoEdicao.includes("Suspender");
      },
    },
    {
      id: "mobile-tem-guarda",
      descricao:
        "page.tsx contem tabIndex={0} (a guarda do tenant reservado tambem existe no bloco de cards mobile, nao so nas colunas de desktop)",
      predicate: () => plataformaPage.includes("tabIndex={0}"),
    },
    {
      id: "editar-tenant-plano-com-fallback-nao-nulo",
      descricao:
        "EditarTenantForm em page.tsx seed-a o estado plano via 'tenant.plano ?? PLANO_OPTIONS[0]', para o NativeSelect nunca ficar nao-controlado a partir de um valor null (CR-01)",
      predicate: () => {
        const blocoForm = sliceBetweenMarkers(
          plataformaPage,
          "function EditarTenantForm",
          "function AlteracaoEstadoConteudo",
        );
        if (!blocoForm) return false;
        return /useState<TenantPlano>\(\s*tenant\.plano\s*\?\?\s*PLANO_OPTIONS\[0\]\s*\)/.test(blocoForm);
      },
    },
    {
      id: "guard-de-pagina-falha-fechado-durante-loading",
      descricao:
        "PlataformaPage tem um early-return para !me.isFetched ANTES do early-return de AccessDeniedState, para nao renderizar PlataformaPageContent (e disparar GET /platform/tenants) durante a janela de carregamento de useMe() (WR-03)",
      predicate: () => {
        const blocoPagina = sliceBetweenMarkers(
          plataformaPage,
          "export default function PlataformaPage",
          "function PlataformaPageContent",
        );
        if (!blocoPagina) return false;
        const idxIsFetchedGuard = blocoPagina.search(/if\s*\(\s*!me\.isFetched\s*\)/);
        const idxAccessDenied = blocoPagina.indexOf("AccessDeniedState");
        return idxIsFetchedGuard !== -1 && idxAccessDenied !== -1 && idxIsFetchedGuard < idxAccessDenied;
      },
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
