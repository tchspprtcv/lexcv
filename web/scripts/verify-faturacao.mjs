// Prova automatizada e executavel (Node puro, sem dependencias) da aba "Faturação" das
// Definicoes (Phase 133, Plano 08): gating por financeiro:manage, Read-Only Guarantee das séries,
// copy vinculativa do 133-UI-SPEC e ausencia de constantes de taxa (IVA/retencao) no frontend.
//
// Tal como verify-auditoria-rbac.mjs, todas as assercoes sao verificacoes de substring LITERAIS
// sobre o texto bruto dos ficheiros (sem remover comentarios): ate um comentario com um token
// proibido acrescentado a faturacao-series-card.tsx faz este gate falhar.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para o checkpoint end-to-end da Task 3):
//   1. Que a aba renderiza com o backend real e que os dialogos reagem a cliques.
//   2. Que o backend recusa 403 a quem nao tem financeiro:manage.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC = path.join(__dirname, "..", "src");
const SETTINGS = path.join(SRC, "app", "(dashboard)", "settings");

const FICHEIROS = {
  page: path.join(SETTINGS, "page.tsx"),
  tab: path.join(SETTINGS, "faturacao-tab.tsx"),
  form: path.join(SETTINGS, "faturacao-dados-form.tsx"),
  series: path.join(SETTINGS, "faturacao-series-card.tsx"),
  ativacao: path.join(SETTINGS, "faturacao-ativacao-card.tsx"),
  email: path.join(SETTINGS, "faturacao-email-card.tsx"),
  hooks: path.join(SRC, "hooks", "use-faturacao.ts"),
  schema: path.join(SRC, "schemas", "faturacao.ts"),
  types: path.join(SRC, "types", "faturacao.ts"),
};

const falhas = [];

function relativo(p) {
  return path.relative(path.join(__dirname, ".."), p);
}

function exigeContem(nome, texto, token) {
  if (!texto[nome].includes(token)) {
    falhas.push(`${relativo(FICHEIROS[nome])}: falta ${JSON.stringify(token)}`);
  }
}

function exigeNaoContem(nome, texto, token) {
  if (texto[nome].includes(token)) {
    falhas.push(`${relativo(FICHEIROS[nome])}: contem token proibido ${JSON.stringify(token)}`);
  }
}

async function main() {
  const texto = {};
  for (const [nome, ficheiro] of Object.entries(FICHEIROS)) {
    try {
      texto[nome] = await fs.readFile(ficheiro, "utf8");
    } catch (error) {
      falhas.push(`${relativo(ficheiro)}: nao foi possivel ler (${error.message})`);
      texto[nome] = "";
    }
  }

  // (1) Gating da aba em page.tsx.
  exigeContem("page", texto, '"faturacao"');
  exigeContem("page", texto, 'can.manage("financeiro")');
  exigeContem("page", texto, 'activeTab === "faturacao" && hasFinanceiroManage');

  // (2) Read-Only Guarantee das séries.
  for (const token of [
    "useMutation",
    "useGuardar",
    "useAtivar",
    "useDesativar",
    "useEmailAutomatico",
    "<Button",
    "<Input",
  ]) {
    exigeNaoContem("series", texto, token);
  }

  // (3) Copy vinculativa do UI-SPEC.
  exigeContem(
    "tab",
    texto,
    "Os documentos emitidos nesta versão são simulados e não têm validade fiscal.",
  );
  exigeContem("ativacao", texto, "Ativar faturação?");
  exigeContem("ativacao", texto, "Desativar faturação?");
  exigeContem("ativacao", texto, "Preencha e guarde os dados fiscais para ativar a faturação.");
  exigeContem(
    "ativacao",
    texto,
    "A faturação está ativa e não pode ser desligada porque já foram emitidos documentos.",
  );
  exigeContem("ativacao", texto, 'variant="destructive"');
  exigeContem("email", texto, "Compreendo que os documentos simulados não têm validade fiscal");
  exigeContem("email", texto, "Ligar envio automático");
  exigeContem("email", texto, "aceiteDeclaracao: true");
  exigeContem("form", texto, "Guardar dados fiscais");
  exigeContem("schema", texto, "O NIF deve ter 9 dígitos");

  // (4) Sem constantes de taxa no frontend: as taxas vivem em t_parametro_fiscal no backend.
  const taxa = /\b0\.(15|20)\b/;
  for (const nome of ["tab", "form", "series", "ativacao", "email", "hooks", "schema", "types"]) {
    const m = texto[nome].match(taxa);
    if (m) {
      falhas.push(`${relativo(FICHEIROS[nome])}: contem constante de taxa ${JSON.stringify(m[0])}`);
    }
  }

  // (5) Hooks: opt-out de toast e caminhos relativos ao apiFetch (sem /api/v1 hardcoded).
  exigeContem("hooks", texto, "semToastParaStatus");
  exigeNaoContem("hooks", texto, "/api/v1");

  if (falhas.length > 0) {
    console.error(`verify:faturacao FALHOU (${falhas.length}):`);
    for (const f of falhas) console.error(`  - ${f}`);
    process.exit(1);
  }
  console.log("verify:faturacao OK - gating, copy, séries só de leitura e sem constantes de taxa.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
