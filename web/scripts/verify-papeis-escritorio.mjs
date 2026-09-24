// Prova automatizada e executavel (Node puro, sem dependencias) das
// asseroes de origem para a consola de papeis do proprio escritorio na aba
// "Controlo de Acesso (RBAC)" das Definicoes, e para o seletor de papeis por
// id em Gestao de Utilizadores (Phase 127, Plans 05-08, PAPEL-01..09) —
// web/src/app/(dashboard)/settings/page.tsx, RbacTab e UserManagementTab.
//
// Este ficheiro e a REESCRITA de verify-bloqueio-rbac.mjs (renomeado por
// `git mv`, historia preservada), nao um ficheiro novo — 127-CONTEXT.md
// Decisao 2b e explicita: o gate tem de ser reescrito para provar a NOVA
// garantia (o escritorio PODE editar os seus proprios papeis), nao a
// garantia retirada (o escritorio NAO podia gravar a matriz). O gate antigo
// tinha 12 assercoes escritas para provar que um ADMIN de escritorio nao
// conseguia gravar; esta fase entrega exactamente o oposto. Por assercao
// (127-PATTERNS.md §9):
//   - A02 (ordem das Regras dos Hooks) sobrevive, generalizada de "useMe()
//     precede o primeiro early return" para "TODO hook do bloco precede o
//     primeiro early return" — mais forte que o original, porque a aba
//     reescrita pode ja nao chamar useMe().
//   - A09 (nao-regressao da visibilidade da aba) sobrevive, mas ja NAO verbatim: o WR-02 de
//     127-REVIEW.md apanhou o fallback `|| isAdmin` (comparacao pelo nome literal "ADMIN") como
//     logica morta/enganadora e pediu a sua remocao -- a assercao foi reescrita para provar a
//     garantia CORRIGIDA (visibilidade so por permissao efectiva), preservando a nao-regressao
//     original sem preservar a forma exacta da condicao.
//   - A06/A07/A08 (badge+tooltip "Gerido pela Plataforma") sao apagadas,
//     nao adaptadas — a aparelhagem desapareceu.
//   - A03/A04/A05/A10/A11/A12 sao invertidas ou re-visadas para a nova
//     forma editavel da matriz.
//
// Le os ficheiros-alvo como texto puro (sem import de modulo, sem
// type-stripping), normaliza removendo comentarios, extrai os blocos de
// RbacTab e de UserManagementTab por marcador, e testa cada assercao com um
// predicado dedicado. Imprime "PASS <id>" ou "FAIL <id> — <motivo>" por
// assercao; exit 0 se todas passarem, exit 1 caso contrario. Segue o estilo
// partilhado por verify-consola-tenants.mjs, verify-bloqueio-rbac.mjs (o
// proprio predecessor) e verify-consola-moldes.mjs (o molde estrutural mais
// proximo para uma matriz editavel de colunas dinamicas) — Node puro, sem
// dependencias, mesma tecnica de stripComments, de localizacao de bloco por
// marcador, e do mesmo loop de reporting/exit code.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint humano da
// Task 3 deste plano — um gate de origem prova estrutura, nao renderizacao):
//   1. Que o backend realmente recusa um caller sem `rbac:manage` — isso e
//      um teste backend dedicado (RbacControllerTest e afins), nao um gate
//      de origem frontend.
//   2. Que a matriz renderiza e reage a cliques correctamente no browser,
//      incluindo o fluxo de gravacao, criacao, renomeacao e eliminacao.
//   3. Que renomear o proprio papel de administrador do escritorio nao
//      bloqueia o proprio operador (o passo 9 do checkpoint humano).

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

/**
 * Remove comentarios de um conteudo-fonte TypeScript/TSX antes de o usar em
 * assercoes — sem isto, o proprio cabecalho deste ficheiro ou qualquer
 * comentario do codigo-fonte que mencione um dos tokens procurados tornaria
 * o gate auto-invalidante (falso positivo). Identico ao stripComments de
 * verify-bloqueio-rbac.mjs / verify-consola-moldes.mjs.
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
 * assercao nunca falhar silenciosamente por um bloco vazio/errado. Tecnica
 * mantida byte-for-byte de verify-bloqueio-rbac.mjs — plan 07 preservou
 * deliberadamente a forma literal "function RbacTab()" precisamente para
 * que este marcador continuasse a funcionar.
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

/**
 * Extrai o corpo de UserManagementTab: mesma tecnica de marcador que
 * extractRbacTabBlock, delimitando do inicio da funcao ate ao proximo
 * "\nfunction " — para que as assercoes do picker de papeis do formulario
 * de utilizador (PAPEL-06) nao possam bater acidentalmente com texto de
 * outro sitio do ficheiro (nomeadamente RbacTab, que tambem fala de papeis).
 */
function extractUserManagementTabBlock(source) {
  const startIdx = source.indexOf("function UserManagementTab(");
  if (startIdx === -1) {
    throw new Error(
      "Marcador 'function UserManagementTab(' nao encontrado no ficheiro"
    );
  }
  const nextFnIdx = source.indexOf("\nfunction ", startIdx + 1);
  if (nextFnIdx === -1) {
    throw new Error(
      "Marcador de fecho '\\nfunction ' (a seguir a UserManagementTab) nao encontrado"
    );
  }
  return source.slice(startIdx, nextFnIdx);
}

/**
 * Delimita o bloco de um elemento JSX <Tag ...>...</Tag> mais proximo que
 * contem `needle`, procurando a abertura mais recente antes de `needle` e o
 * fecho mais proximo depois. Copiado verbatim de verify-consola-moldes.mjs.
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

const NOTA_PRIMEIRA_FRASE_NORMALIZADA =
  "O papel de administrador do escritório mantém sempre as permissões que o tornam administrador — não é possível removê-las nem apagar esse papel.".replace(
    /\s+/g,
    " "
  );

async function main() {
  const settingsPageRaw = await fs.readFile(SETTINGS_PAGE_PATH, "utf-8");
  const settingsPage = stripComments(settingsPageRaw);
  const rbacBlock = extractRbacTabBlock(settingsPage);
  const userManagementBlock = extractUserManagementTabBlock(settingsPage);

  const assertions = [
    // ---- Sobreviventes (127-PATTERNS.md §9) ----
    {
      id: "hooks-antes-do-primeiro-early-return",
      descricao:
        "no bloco de RbacTab, o indice de TODA chamada a um hook (use[A-Z]...) e menor que o indice de 'if (isLoading)' — generalizacao da antiga A02: a protecao e a ordem, nao um hook especifico, e a aba reescrita pode ja nao chamar useMe()",
      predicate: () => {
        const earlyReturnIdx = rbacBlock.indexOf("if (isLoading)");
        if (earlyReturnIdx === -1) return false;
        const hookRe = /\buse[A-Z]\w*/g;
        let match;
        let encontrouAlgum = false;
        while ((match = hookRe.exec(rbacBlock))) {
          encontrouAlgum = true;
          if (match.index >= earlyReturnIdx) return false;
        }
        return encontrouAlgum;
      },
    },
    {
      // WR-02 (127-REVIEW.md): a antiga A09 provava que a visibilidade da aba sobrevivia
      // verbatim, incluindo o fallback `|| isAdmin` que comparava pelo NOME literal "ADMIN" --
      // exactamente o que PAPEL-04 torna editavel. Essa mesma revisao apanhou o fallback como
      // logica morta/enganadora (nunca um gap de seguranca, porque estava sempre OR'd com a
      // permissao real) e pediu a sua remocao. Esta assercao foi reescrita, nao apagada, para
      // provar a garantia CORRIGIDA: a visibilidade das abas depende SO da permissao efectiva
      // (rbac:manage/users:manage), nunca de um literal de nome de papel -- o que sobrevive de
      // A09 e a garantia de nao-regressao ("a aba continua visivel"), nao a forma exacta da
      // condicao.
      id: "hasrbacmanage-sem-fallback-por-nome",
      descricao:
        'o ficheiro completo (sem comentarios) contem \'const hasRbacManage = can.manage("rbac");\' e \'const hasUsersManage = can.manage("users");\', e NAO contem \'isAdmin\' — substitui a antiga A09: a visibilidade da aba continua a existir, mas deixa de ter um segundo caminho por nome literal de papel (WR-02, 127-REVIEW.md)',
      predicate: () =>
        settingsPage.includes('const hasRbacManage = can.manage("rbac");') &&
        settingsPage.includes('const hasUsersManage = can.manage("users");') &&
        !settingsPage.includes("isAdmin"),
    },

    // ---- Inversoes da garantia retirada ----
    {
      id: "sem-isplatformadmin",
      descricao:
        "o ficheiro completo nao contem 'isPlatformAdmin' — a computacao e todos os ramos que dependiam dela foram removidos, nao adaptados (Decisao 1)",
      predicate: () => !settingsPage.includes("isPlatformAdmin"),
    },
    {
      id: "sem-badge-gerido-pela-plataforma",
      descricao:
        "o ficheiro nao contem 'Gerido pela Plataforma' nem a frase antiga do tooltip — substitui A06/A07/A08, apagadas em vez de adaptadas porque a aparelhagem de badge+tooltip desapareceu",
      predicate: () => {
        const semBadge = !settingsPage.includes("Gerido pela Plataforma");
        const normalizado = settingsPage.replace(/\s+/g, " ");
        const semTooltipAntigo = !normalizado.includes(
          "As regras de acesso por perfil (RBAC) passaram a ser uma configuração fixa e comum a toda a plataforma LexCV"
        );
        return semBadge && semTooltipAntigo;
      },
    },
    {
      id: "guardar-alteracoes-incondicional",
      descricao:
        "o bloco de RbacTab contem 'Guardar Alterações' e o <Button> que o envolve nao contem 'isPlatformAdmin' nem 'PLATAFORMA_ADMIN' — substitui A05: o botao ja nao esta atras de um ternario de papel, so do dirty-check da matriz",
      predicate: () => {
        if (!rbacBlock.includes("Guardar Alterações")) return false;
        const botao = findElementBlockContaining(
          rbacBlock,
          "<Button",
          "</Button>",
          "Guardar Alterações"
        );
        if (!botao) return false;
        return !botao.includes("isPlatformAdmin") && !botao.includes("PLATAFORMA_ADMIN");
      },
    },
    {
      id: "matriz-sem-branch-readonly",
      descricao:
        "o bloco de RbacTab nao contem '!isPlatformAdmin' nem uma variavel 'isDisabled' — substitui A12: ja nao existe nenhum branch so-de-leitura na matriz, o unico disabled e o floor-lock (isLockedFloor)",
      predicate: () =>
        !rbacBlock.includes("!isPlatformAdmin") && !rbacBlock.includes("isDisabled"),
    },
    {
      id: "matriz-bloqueio-por-proveniencia",
      descricao:
        'o bloco contem "protegido" e NAO contem \'role === "ADMIN"\' nem \'=== "ADMIN"\' — substitui A11 e e a assercao mais importante desta reescrita (127-PATTERNS.md §9): um match pelo literal do nome deixaria de proteger o papel assim que o escritorio o renomeasse',
      predicate: () =>
        rbacBlock.includes("protegido") &&
        !rbacBlock.includes('role === "ADMIN"') &&
        !rbacBlock.includes('=== "ADMIN"'),
    },
    {
      id: "gravacao-pelo-hook-de-mutacao",
      descricao:
        'o bloco contem "useOfficeRbac()" e "useSaveOfficeRbac(" e NAO contem \'apiFetch("/admin/rbac"\' — substitui A10 e fecha a lacuna pre-existente em que o ecra contornava o proprio hook de mutacao (127-PATTERNS.md secao 7)',
      predicate: () =>
        rbacBlock.includes("useOfficeRbac()") &&
        rbacBlock.includes("useSaveOfficeRbac(") &&
        !rbacBlock.includes('apiFetch("/admin/rbac"'),
    },

    // ---- Novas garantias desta fase ----
    {
      id: "matriz-scope-col-row",
      descricao:
        'scope="col" presente nos cabecalhos de coluna, scope="row" presente, e a celula <th scope="row"> promovida contem text-left (sem isto um <th> centra o texto por omissao, ao contrario de um <td>)',
      predicate: () => {
        const countScopeCol = (rbacBlock.match(/scope="col"/g) ?? []).length;
        const temScopeRow = rbacBlock.includes('scope="row"');
        const thRowTemTextLeft = /<th scope="row" className="[^"]*text-left[^"]*"/.test(
          rbacBlock
        );
        return countScopeCol >= 1 && temScopeRow && thRowTemTextLeft;
      },
    },
    {
      id: "matriz-aria-label-checkbox",
      descricao:
        "todo checkbox da matriz tem um aria-label template-string que referencia simultaneamente o rotulo da permissao e o nome do papel — o wrapper <label> nao tem texto visivel",
      predicate: () =>
        /aria-label=\{`\$\{permissao\.[a-zA-Z]+\}[\s\S]{0,10}\$\{papel\.nome\}`\}/.test(
          rbacBlock
        ),
    },
    {
      id: "merge-por-id",
      descricao:
        "o bloco chama mesclarEstadoLocal( e o ficheiro importa-a de \"./merge-local-papeis\" — a edicao local nao gravada sobrevive a criar/renomear/apagar outro papel porque o mapa e indexado por id, nunca por nome (UI-SPEC §6)",
      predicate: () =>
        rbacBlock.includes("mesclarEstadoLocal(") &&
        settingsPage.includes('from "./merge-local-papeis"'),
    },
    {
      id: "wiring-painel-criacao",
      descricao:
        'o ficheiro importa CriarPapelPanel de "./criar-papel-panel" e o bloco renderiza-o atras de um booleano aberto/fechado (isFormOpen)',
      predicate: () =>
        settingsPage.includes('from "./criar-papel-panel"') &&
        settingsPage.includes("CriarPapelPanel") &&
        rbacBlock.includes("isFormOpen") &&
        rbacBlock.includes("<CriarPapelPanel"),
    },
    {
      id: "wiring-menu-de-accoes",
      descricao:
        'o ficheiro importa PapelAcoesMenu de "./papel-acoes-menu" e o bloco usa-o (<PapelAcoesMenu)',
      predicate: () =>
        settingsPage.includes('from "./papel-acoes-menu"') &&
        rbacBlock.includes("<PapelAcoesMenu"),
    },
    {
      id: "renomear-em-dialog",
      descricao:
        "o bloco contem o titulo exato do dialogo de renomeacao do Copywriting Contract da UI-SPEC: <DialogTitle>Renomear papel</DialogTitle>",
      predicate: () => rbacBlock.includes("<DialogTitle>Renomear papel</DialogTitle>"),
    },
    {
      id: "apagar-em-alertdialog-destrutivo",
      descricao:
        'o bloco contem o padrao exato do titulo de confirmacao de eliminacao (\'Apagar o papel &quot;\') e a classe destrutiva "bg-red-600 hover:bg-red-700 text-white" no mesmo bloco',
      predicate: () =>
        rbacBlock.includes("Apagar o papel &quot;") &&
        rbacBlock.includes("bg-red-600 hover:bg-red-700 text-white"),
    },
    {
      id: "nota-permanente-do-papel-protegido",
      descricao:
        "a primeira frase da caixa de nota permanente (Copywriting Contract da UI-SPEC), com espacos e as tags <strong> normalizados antes da comparacao — mesma tecnica de normalizacao da antiga A08",
      predicate: () => {
        const normalizado = rbacBlock.replace(/<\/?strong>/g, "").replace(/\s+/g, " ");
        return normalizado.includes(NOTA_PRIMEIRA_FRASE_NORMALIZADA);
      },
    },
    {
      id: "picker-de-papeis-por-id",
      descricao:
        'no bloco de UserManagementTab: o rotulo "Papéis do Escritório" esta presente, "tenantRoleIds" esta presente, e o union fixo de quatro papeis (ADMIN/TECNICO/ADVOGADO/ASSISTENTE) esta ausente (PAPEL-06)',
      predicate: () =>
        userManagementBlock.includes("Papéis do Escritório") &&
        userManagementBlock.includes("tenantRoleIds") &&
        !userManagementBlock.includes('"ADMIN", "TECNICO", "ADVOGADO", "ASSISTENTE"'),
    },
    {
      id: "sem-importacao-de-mock-db",
      descricao:
        "o ficheiro completo nao importa de @/server/mock-db — o ultimo import pre-backend deste ficheiro foi removido nesta fase (CLAUDE.md marca web/src/server/ como codigo mock superado)",
      predicate: () => !settingsPage.includes("@/server/mock-db"),
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
