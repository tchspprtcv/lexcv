// Prova automatizada e executavel (Node puro, sem dependencias) das 15
// assercoes de origem para o relatorio de utilizacao por tenant e para a
// sua entrada a partir da consola (Phase 122, Plans 01 e 03) —
// plataforma/relatorio/columns.tsx, plataforma/relatorio/page.tsx,
// plataforma/page.tsx e dashboard-shell.tsx (este ultimo apenas para a
// assercao de nao-regressao de navegacao).
//
// Le os ficheiros-alvo como texto puro (sem import de modulo, sem
// type-stripping), normaliza removendo comentarios, e testa cada assercao
// com um predicado dedicado. Imprime "PASS <id>" ou "FAIL <id> — <motivo>"
// por assercao; exit 0 se todas passarem, exit 1 caso contrario. Segue o
// estilo de verify-consola-tenants.mjs (Phase 120) — Node puro, zero
// dependencias, mesma tecnica de stripComments e de localizacao de bloco
// por marcador, mesmo loop de reporting.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano do
// Plan 04 — um gate de origem prova estrutura, nao renderizacao):
//   1. Que a rota /plataforma/relatorio resolve mesmo no browser.
//   2. Que o clique em "Ver Relatório" navega de facto para essa rota.
//   3. Que os badges e as cores (plano/estado) renderizam como esperado.
//   4. Que um utilizador real sem PLATAFORMA_ADMIN recebe mesmo 403 do
//      backend ao tentar aceder aos dados subjacentes.
//   5. Que os numeros de utilizadores ativos mostrados correspondem aos
//      utilizadores reais existentes na base de dados.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PLATAFORMA_DIR = path.join(__dirname, "..", "src", "app", "(dashboard)", "plataforma");
const PLATAFORMA_PAGE_PATH = path.join(PLATAFORMA_DIR, "page.tsx");
const RELATORIO_DIR = path.join(PLATAFORMA_DIR, "relatorio");
const RELATORIO_COLUMNS_PATH = path.join(RELATORIO_DIR, "columns.tsx");
const RELATORIO_PAGE_PATH = path.join(RELATORIO_DIR, "page.tsx");
const DASHBOARD_SHELL_PATH = path.join(
  __dirname,
  "..",
  "src",
  "components",
  "shared",
  "dashboard-shell.tsx",
);

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, um comentario que mencione um dos tokens procurados
 * tornaria o gate auto-invalidante (falso positivo). Identico ao
 * stripComments de verify-consola-tenants.mjs.
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
  const [relatorioColumnsRaw, relatorioPageRaw, plataformaPageRaw, dashboardShellRaw] =
    await Promise.all([
      fs.readFile(RELATORIO_COLUMNS_PATH, "utf-8"),
      fs.readFile(RELATORIO_PAGE_PATH, "utf-8"),
      fs.readFile(PLATAFORMA_PAGE_PATH, "utf-8"),
      fs.readFile(DASHBOARD_SHELL_PATH, "utf-8"),
    ]);

  const relatorioColumns = stripComments(relatorioColumnsRaw);
  const relatorioPage = stripComments(relatorioPageRaw);
  const plataformaPage = stripComments(plataformaPageRaw);
  const dashboardShell = stripComments(dashboardShellRaw);

  const assertions = [
    {
      id: "colunas-array-estatico",
      descricao:
        'relatorio/columns.tsx exporta "export const relatorioColumns: ColumnDef<TenantAdminSummary>[] =" e nao contem "export function columns"',
      predicate: () => {
        const matchesShape = /export const relatorioColumns\s*:\s*ColumnDef<TenantAdminSummary>\[\]\s*=/.test(
          relatorioColumns,
        );
        const hasFactoryLeftover = relatorioColumns.includes("export function columns");
        return matchesShape && !hasFactoryLeftover;
      },
    },
    {
      id: "colunas-quatro-ids",
      descricao:
        'relatorio/columns.tsx tem exatamente 4 ids em (nome|plano|utilizadores|estado) e 0 ids "acoes"',
      predicate: () => {
        const idMatches = relatorioColumns.match(/id: "(nome|plano|utilizadores|estado)"/g) ?? [];
        const acoesMatches = relatorioColumns.match(/id: "acoes"/g) ?? [];
        return idMatches.length === 4 && acoesMatches.length === 0;
      },
    },
    {
      id: "colunas-sem-celula-de-acoes",
      descricao:
        "relatorio/columns.tsx nao contem TenantAcoesCell, TooltipTrigger, TooltipContent, Pencil nem Unlock",
      predicate: () => {
        const bannedTokens = ["TenantAcoesCell", "TooltipTrigger", "TooltipContent", "Pencil", "Unlock"];
        return bannedTokens.every((token) => !relatorioColumns.includes(token));
      },
    },
    {
      id: "colunas-nome-reservado-importado",
      descricao:
        'relatorio/columns.tsx importa de "../columns" e nao redeclara o literal do nome reservado (= "LexCV")',
      predicate: () => {
        const importsFromColumns = /from\s+"\.\.\/columns"/.test(relatorioColumns);
        const hasReservedLiteral = relatorioColumns.includes('= "LexCV"');
        return importsFromColumns && !hasReservedLiteral;
      },
    },
    {
      id: "utilizadores-sem-recalculo",
      descricao:
        'o bloco da column def "utilizadores" usa tenant.utilizadoresAtivos diretamente, sem reduce/filter/map/length',
      predicate: () => {
        const bloco = sliceBetweenMarkers(relatorioColumns, 'id: "utilizadores"', 'id: "estado"');
        if (!bloco) return false;
        const temFonteUnica = bloco.includes("tenant.utilizadoresAtivos");
        const temRecalculo = /\.(reduce|filter|map)\(|\.length/.test(bloco);
        return temFonteUnica && !temRecalculo;
      },
    },
    {
      id: "estado-badge-suspenso",
      descricao:
        'o bloco da column def "estado" contem "Suspenso", "Ativo" e variant={ativo ? "green" : "red"}',
      predicate: () => {
        const startIdx = relatorioColumns.indexOf('id: "estado"');
        if (startIdx === -1) return false;
        const bloco = relatorioColumns.slice(startIdx);
        return (
          bloco.includes('"Suspenso"') &&
          bloco.includes('"Ativo"') &&
          bloco.includes('variant={ativo ? "green" : "red"}')
        );
      },
    },
    {
      id: "relatorio-guard-falha-fechado",
      descricao:
        "RelatorioUtilizacaoPage resolve !me.isFetched antes de AccessDeniedState, e verifica roles?.includes(\"PLATAFORMA_ADMIN\")",
      predicate: () => {
        const bloco = sliceBetweenMarkers(
          relatorioPage,
          "export default function",
          "function RelatorioUtilizacaoContent",
        );
        if (!bloco) return false;
        const idxGuardLoading = bloco.search(/if\s*\(\s*!me\.isFetched\s*\)/);
        const idxAccessDenied = bloco.indexOf("AccessDeniedState");
        const orderOk = idxGuardLoading !== -1 && idxAccessDenied !== -1 && idxGuardLoading < idxAccessDenied;
        const temCheckPapel = bloco.includes('roles?.includes("PLATAFORMA_ADMIN")');
        return orderOk && temCheckPapel;
      },
    },
    {
      id: "relatorio-sem-filtro-de-estado",
      descricao:
        "o memo tenantsFiltrados filtra so por nome (t.nome.toLowerCase().includes(termo)), sem nenhum filtro de ativo",
      predicate: () => {
        const bloco = sliceBetweenMarkers(
          relatorioPage,
          "const tenantsFiltrados",
          "}, [tenants.data, searchTerm]);",
        );
        if (!bloco) return false;
        const temFiltroDeNome = bloco.includes("t.nome.toLowerCase().includes(termo)");
        const semFiltroDeEstado = !bloco.includes("ativo");
        return temFiltroDeNome && semFiltroDeEstado;
      },
    },
    {
      id: "relatorio-h1-semibold",
      descricao: "a tag de abertura do h1 contem font-semibold e nao contem font-bold",
      predicate: () => {
        const match = relatorioPage.match(/<h1\b[^>]*>/);
        if (!match) return false;
        const tag = match[0];
        return tag.includes("font-semibold") && !tag.includes("font-bold");
      },
    },
    {
      id: "relatorio-mobile-mostra-utilizadores",
      descricao:
        "o bloco de cards mobile (md:hidden ... hidden md:block) mostra utilizadoresAtivos, indentado com pl-[52px]",
      predicate: () => {
        const bloco = sliceBetweenMarkers(relatorioPage, "md:hidden", "hidden md:block");
        if (!bloco) return false;
        return bloco.includes("utilizadoresAtivos") && bloco.includes("pl-[52px]");
      },
    },
    {
      id: "relatorio-sem-mutacoes",
      descricao:
        "relatorio/page.tsx nao contem useCreateTenant, useUpdateTenant, useSetTenantAtivo, mutateAsync, AlertDialog, window.location.reload nem csv",
      predicate: () => {
        const bannedExact = [
          "useCreateTenant",
          "useUpdateTenant",
          "useSetTenantAtivo",
          "mutateAsync",
          "AlertDialog",
          "window.location.reload",
        ];
        const semTokensExatos = bannedExact.every((token) => !relatorioPage.includes(token));
        const semCsv = !/csv/i.test(relatorioPage);
        return semTokensExatos && semCsv;
      },
    },
    {
      id: "entrada-ver-relatorio",
      descricao:
        'plataforma/page.tsx contem href="/plataforma/relatorio", "Ver Relatório", variant="outline" e o CardHeader contem flex-wrap',
      predicate: () => {
        const temHref = plataformaPage.includes('href="/plataforma/relatorio"');
        const temCopy = plataformaPage.includes("Ver Relatório");
        const temOutline = plataformaPage.includes('variant="outline"');
        const cardHeaderMatch = plataformaPage.match(/<CardHeader className="([^"]*)"/);
        const temFlexWrapNoCardHeader = !!cardHeaderMatch && cardHeaderMatch[1].includes("flex-wrap");
        return temHref && temCopy && temOutline && temFlexWrapNoCardHeader;
      },
    },
    {
      id: "entrada-ordem-e-criar-tenant-intocado",
      descricao:
        '"Ver Relatório" precede "Criar Tenant"; setIsFormOpen(true) aparece exatamente 1 vez; bg-blue-600 hover:bg-blue-700 text-white preservado',
      predicate: () => {
        const idxVer = plataformaPage.indexOf("Ver Relatório");
        const idxCriar = plataformaPage.indexOf("Criar Tenant");
        const ordemOk = idxVer !== -1 && idxCriar !== -1 && idxVer < idxCriar;
        const countSetIsFormOpen = (plataformaPage.match(/setIsFormOpen\(true\)/g) ?? []).length;
        const temClassesIntactas = plataformaPage.includes("bg-blue-600 hover:bg-blue-700 text-white");
        return ordemOk && countSetIsFormOpen === 1 && temClassesIntactas;
      },
    },
    {
      id: "sem-segundo-item-de-nav",
      descricao: "dashboard-shell.tsx tem 0 ocorrencias de plataforma/relatorio",
      predicate: () => {
        const matches = dashboardShell.match(/plataforma\/relatorio/g) ?? [];
        return matches.length === 0;
      },
    },
    {
      id: "sem-export-csv-nem-kpis",
      descricao:
        "nem relatorio/page.tsx nem relatorio/columns.tsx contem (case-insensitive) csv, download ou Blob",
      predicate: () => {
        const bannedCaseInsensitive = /csv|download|Blob/i;
        return !bannedCaseInsensitive.test(relatorioPage) && !bannedCaseInsensitive.test(relatorioColumns);
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
