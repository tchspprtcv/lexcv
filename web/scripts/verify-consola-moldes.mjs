// Prova automatizada e executavel (Node puro, sem dependencias) das
// assercoes de origem do aviso de nao-propagacao em tres camadas e da
// acessibilidade da matriz da consola de moldes (Phase 125, Plans 05/06) --
// plataforma/page.tsx (ponto de entrada), plataforma/moldes/page.tsx
// (matriz, guarda de pagina, aviso, fluxo de gravacao) e
// plataforma/moldes/criar-molde-panel.tsx (painel de criacao inline).
//
// Le os ficheiros-alvo como texto puro (sem import de modulo, sem
// type-stripping), normaliza removendo comentarios, e testa cada assercao
// com um predicado dedicado. Imprime "PASS <id>" ou "FAIL <id> — <motivo>"
// por assercao; exit 0 se todas passarem, exit 1 caso contrario. Segue o
// estilo de verify-relatorio-utilizacao.mjs e verify-consola-tenants.mjs --
// Node puro, zero dependencias, mesma tecnica de stripComments e de
// localizacao de bloco por marcador, mesmo loop de reporting.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano da
// Task 3 deste plano -- um gate de origem prova estrutura, nao renderizacao):
//   1. Que a rota /plataforma/moldes resolve mesmo no browser.
//   2. Que um clique num checkbox da matriz persiste visualmente.
//   3. Que a contagem de escritorios instanciados por molde esta
//      numericamente correcta face aos dados reais da base de dados.
//   4. Que o AlertDialog de confirmacao abre de facto ao clicar em
//      "Guardar Alterações".
//   5. Que um utilizador sem PLATAFORMA_ADMIN recebe 403 do backend ao vivo
//      ao tentar aceder a /api/v1/platform/moldes.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PLATAFORMA_DIR = path.join(__dirname, "..", "src", "app", "(dashboard)", "plataforma");
const PLATAFORMA_PAGE_PATH = path.join(PLATAFORMA_DIR, "page.tsx");
const MOLDES_DIR = path.join(PLATAFORMA_DIR, "moldes");
const MOLDES_PAGE_PATH = path.join(MOLDES_DIR, "page.tsx");
const CRIAR_MOLDE_PANEL_PATH = path.join(MOLDES_DIR, "criar-molde-panel.tsx");

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, um comentario que mencione um dos tokens procurados
 * tornaria o gate auto-invalidante (falso positivo). Identico ao
 * stripComments de verify-consola-tenants.mjs / verify-relatorio-utilizacao.mjs.
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

/**
 * Delimita o bloco de um elemento JSX <Tag ...>...</Tag> mais proximo que
 * contem `needle`, procurando a abertura mais recente antes de `needle` e o
 * fecho mais proximo depois.
 */
function findElementBlockContaining(source, openTag, closeTag, needle) {
  const needleIdx = source.indexOf(needle);
  if (needleIdx === -1) return null;
  const openIdx = source.lastIndexOf(openTag, needleIdx);
  if (openIdx === -1) return null;
  const closeIdx = source.indexOf(closeTag, needleIdx);
  if (closeIdx === -1) return null;
  return source.slice(openIdx, closeIdx + closeTag.length);
}

async function main() {
  const [plataformaPageRaw, moldesPageRaw, criarMoldePanelRaw] = await Promise.all([
    fs.readFile(PLATAFORMA_PAGE_PATH, "utf-8"),
    fs.readFile(MOLDES_PAGE_PATH, "utf-8"),
    fs.readFile(CRIAR_MOLDE_PANEL_PATH, "utf-8"),
  ]);

  const plataformaPage = stripComments(plataformaPageRaw);
  const moldesPage = stripComments(moldesPageRaw);
  const criarMoldePanel = stripComments(criarMoldePanelRaw);

  const assertions = [
    {
      id: "entrada-gerir-moldes",
      descricao:
        'plataforma/page.tsx contem href="/plataforma/moldes" e "Gerir Moldes", antes de href="/plataforma/relatorio"',
      predicate: () => {
        const temHref = plataformaPage.includes('href="/plataforma/moldes"');
        const temCopy = plataformaPage.includes("Gerir Moldes");
        const idxMoldes = plataformaPage.indexOf('href="/plataforma/moldes"');
        const idxRelatorio = plataformaPage.indexOf('href="/plataforma/relatorio"');
        const ordemOk = idxMoldes !== -1 && idxRelatorio !== -1 && idxMoldes < idxRelatorio;
        return temHref && temCopy && ordemOk;
      },
    },
    {
      id: "guarda-de-pagina-falha-fechado",
      descricao:
        "moldes/page.tsx resolve !me.isFetched antes de AccessDeniedState/verificacao de PLATAFORMA_ADMIN (WR-03)",
      predicate: () => {
        const idxIsFetched = moldesPage.search(/if\s*\(\s*!me\.isFetched\s*\)/);
        const idxAccessDenied = moldesPage.indexOf("AccessDeniedState");
        const idxRoleCheck = moldesPage.indexOf('includes("PLATAFORMA_ADMIN")');
        return (
          idxIsFetched !== -1 &&
          idxAccessDenied !== -1 &&
          idxRoleCheck !== -1 &&
          idxIsFetched < idxRoleCheck
        );
      },
    },
    {
      id: "aviso-camada1-banner",
      descricao:
        'existe um bloco com bg-amber-50 e TriangleAlert contendo "Alterar um molde não atualiza escritórios já criados.", e ShieldAlert nao existe no ficheiro',
      predicate: () => {
        const idxBanner = moldesPage.indexOf("bg-amber-50");
        if (idxBanner === -1) return false;
        const janela = moldesPage.slice(idxBanner, idxBanner + 800);
        const temTriangleAlert = janela.includes("TriangleAlert");
        const temTexto = janela.includes("Alterar um molde não atualiza escritórios já criados.");
        const semShieldAlert = !moldesPage.includes("ShieldAlert");
        return temTriangleAlert && temTexto && semShieldAlert;
      },
    },
    {
      id: "aviso-camada2-badge-contagem",
      descricao:
        "existe um Badge cuja variante deriva de escritoriosInstanciados (co-ocorrencia com amber/gray), e escritoriosInstanciados aparece dentro do <thead>",
      predicate: () => {
        const temVariantDerivada =
          /escritoriosInstanciados[\s\S]{0,80}"amber"[\s\S]{0,40}"gray"/.test(moldesPage);
        const bloco = sliceBetweenMarkers(moldesPage, "<thead", "</thead>");
        if (!bloco) return false;
        return temVariantDerivada && bloco.includes("escritoriosInstanciados");
      },
    },
    {
      id: "aviso-camada3-alertdialog-gravacao",
      descricao:
        'existem AlertDialog, AlertDialogAction e AlertDialogCancel; "Confirmar e Gravar" existe; bg-amber-600 aparece exatamente 1 vez, dentro do bloco do AlertDialog',
      predicate: () => {
        const temComponentes =
          moldesPage.includes("AlertDialog") &&
          moldesPage.includes("AlertDialogAction") &&
          moldesPage.includes("AlertDialogCancel");
        const temTexto = moldesPage.includes("Confirmar e Gravar");
        const countAmber600 = (moldesPage.match(/bg-amber-600/g) ?? []).length;
        const blocoAlertDialog = sliceBetweenMarkers(moldesPage, "<AlertDialog ", "</AlertDialog>");
        const amberDentroDoBloco = !!blocoAlertDialog && blocoAlertDialog.includes("bg-amber-600");
        return temComponentes && temTexto && countAmber600 === 1 && amberDentroDoBloco;
      },
    },
    {
      id: "aviso-camada3-mutacao-so-via-alertdialogaction",
      descricao:
        'o <Button> "Guardar Alterações" (CardHeader) nao chama mutateAsync diretamente -- so abre o AlertDialog; a AlertDialogAction invoca handleConfirmarGravacao, que e quem chama atualizarMoldes.mutateAsync',
      predicate: () => {
        const botaoGuardar = findElementBlockContaining(
          moldesPage,
          "<Button",
          "</Button>",
          "Guardar Alterações",
        );
        if (!botaoGuardar) return false;
        const botaoSemMutacao = !botaoGuardar.includes("mutateAsync");
        const abreDialogo = botaoGuardar.includes("setIsConfirmOpen(true)");

        const blocoAction = sliceBetweenMarkers(
          moldesPage,
          "<AlertDialogAction",
          "</AlertDialogAction>",
        );
        const actionChamaHandler = !!blocoAction && blocoAction.includes("handleConfirmarGravacao");

        const handlerChamaMutacao = moldesPage.includes("atualizarMoldes.mutateAsync");

        return botaoSemMutacao && abreDialogo && actionChamaHandler && handlerChamaMutacao;
      },
    },
    {
      id: "matriz-scope-col-row",
      descricao:
        'scope="col" aparece nos 2 cabecalhos de coluna (rotulo + por molde) e scope="row" presente; a celula promovida a <th scope="row"> contem text-left',
      predicate: () => {
        const countScopeCol = (moldesPage.match(/scope="col"/g) ?? []).length;
        const temScopeRow = moldesPage.includes('scope="row"');
        const thRowTemTextLeft = /<th scope="row" className="[^"]*text-left[^"]*"/.test(moldesPage);
        return countScopeCol >= 2 && temScopeRow && thRowTemTextLeft;
      },
    },
    {
      id: "matriz-aria-label-checkbox",
      descricao:
        "existe um aria-label template-string por checkbox que referencia simultaneamente o rotulo da permissao e o nome do molde",
      predicate: () =>
        /aria-label=\{`\$\{permissao\.[a-zA-Z]+\}[\s\S]{0,10}\$\{molde\.nome\}`\}/.test(moldesPage),
    },
    {
      id: "matriz-colspan-dinamico",
      descricao:
        'colSpan={5} (contagem fixa) ausente; existe um colSpan derivado do numero de moldes (moldesFiltrados.length)',
      predicate: () => {
        const semColSpanFixo = !moldesPage.includes("colSpan={5}");
        const temColSpanDerivado = /colSpan=\{moldesFiltrados\.length[^}]*\}/.test(moldesPage);
        return semColSpanFixo && temColSpanDerivado;
      },
    },
    {
      id: "sem-regressoes-rbactab",
      descricao:
        'moldes/page.tsx nao contem isAdminRow nem "Gerido pela Plataforma" (nenhuma linha desta matriz e imutavel)',
      predicate: () => !moldesPage.includes("isAdminRow") && !moldesPage.includes("Gerido pela Plataforma"),
    },
    {
      id: "sem-mock-db-nem-navegacao-nova",
      descricao:
        "nenhum dos 3 ficheiros importa @/server/mock-db, NavigationMenu ou um bloco Sidebar",
      predicate: () => {
        const bannedTokens = ["@/server/mock-db", "NavigationMenu", "Sidebar"];
        return [moldesPage, plataformaPage, criarMoldePanel].every((source) =>
          bannedTokens.every((token) => !source.includes(token)),
        );
      },
    },
    {
      id: "painel-criacao-zod-e-fechar",
      descricao:
        'criar-molde-panel.tsx contem zodResolver, aria-label="Fechar" e max-h-72',
      predicate: () =>
        criarMoldePanel.includes("zodResolver") &&
        criarMoldePanel.includes('aria-label="Fechar"') &&
        criarMoldePanel.includes("max-h-72"),
    },
    {
      id: "painel-criacao-sem-mutacao-propria",
      descricao:
        "criar-molde-panel.tsx nao contem useCreateMolde, useMutation, toast. nem Dialog (painel inline orientado por props, sem mutacao propria)",
      predicate: () => {
        const bannedTokens = ["useCreateMolde", "useMutation", "toast.", "Dialog"];
        return bannedTokens.every((token) => !criarMoldePanel.includes(token));
      },
    },
    {
      id: "wiring-pagina-usa-criarmoldepanel",
      descricao:
        "moldes/page.tsx importa e usa CriarMoldePanel, com um botao Criar Molde no CardHeader e no estado vazio",
      predicate: () => {
        const countUsage = (moldesPage.match(/CriarMoldePanel/g) ?? []).length;
        const countCriarMolde = (moldesPage.match(/Criar Molde/g) ?? []).length;
        return countUsage >= 2 && countCriarMolde >= 2;
      },
    },
    {
      id: "wiring-hook-criacao-e-toast",
      descricao:
        'moldes/page.tsx usa useCreateMolde/criarMolde.mutateAsync e mostra o toast de sucesso "criado com sucesso"',
      predicate: () =>
        moldesPage.includes("useCreateMolde") &&
        moldesPage.includes("criarMolde.mutateAsync") &&
        moldesPage.includes("criado com sucesso"),
    },
    {
      id: "matriz-sem-branch-readonly",
      descricao:
        "a matriz nao tem branch de leitura-apenas (isDisabled) -- todo o viewer desta pagina e PLATAFORMA_ADMIN e pode editar",
      predicate: () => !moldesPage.includes("isDisabled"),
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
