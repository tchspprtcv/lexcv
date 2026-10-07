// Prova automatizada e executavel (Node puro, sem dependencias) da UI da Phase 138:
// Plataforma como Emitente de Faturação de Subscrições (SUBS-01..05).
//
// Verifica:
//   1. Gating estrito de PLATAFORMA_ADMIN na consola /plataforma/faturacao.
//   2. Presença de hooks de configuração fiscal da plataforma, registo de pagamentos e emissão de NC.
//   3. Aba de subscrições no Settings do escritório protegida por financeiro:view (SUBS-04).
//   4. Garantia Read-Only do lado do escritório (sem registo de pagamento ou criação de faturas).

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC = process.env.VERIFY_PLATAFORMA_RAIZ
  ? path.resolve(process.env.VERIFY_PLATAFORMA_RAIZ)
  : path.join(__dirname, "..", "src");

const PLATAFORMA = path.join(SRC, "app", "(dashboard)", "plataforma");
const SETTINGS = path.join(SRC, "app", "(dashboard)", "settings");

const FICHEIROS = {
  platformPage: path.join(PLATAFORMA, "faturacao", "page.tsx"),
  platformConfigCard: path.join(PLATAFORMA, "faturacao", "config-fiscal-card.tsx"),
  platformPagamentoDialog: path.join(PLATAFORMA, "faturacao", "registar-pagamento-dialog.tsx"),
  platformNcDialog: path.join(PLATAFORMA, "faturacao", "emitir-nc-dialog.tsx"),
  platformTable: path.join(PLATAFORMA, "faturacao", "documentos-table.tsx"),
  platformHooks: path.join(SRC, "hooks", "use-platform-faturacao.ts"),
  platformTypes: path.join(SRC, "types", "platform-faturacao.ts"),
  settingsPage: path.join(SETTINGS, "page.tsx"),
  subscricaoTab: path.join(SETTINGS, "subscricao-tab.tsx"),
  minhasSubscricoesHooks: path.join(SRC, "hooks", "use-minhas-subscricoes.ts"),
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

  // (1) Plataforma: Gating de PLATAFORMA_ADMIN
  exigeContem("platformPage", texto, 'me.data?.roles?.includes("PLATAFORMA_ADMIN")');
  exigeContem("platformPage", texto, "<AccessDeniedState");

  // (2) Plataforma: Hooks e Endpoints
  exigeContem("platformHooks", texto, "/platform/faturacao/configuracao");
  exigeContem("platformHooks", texto, "/platform/subscricoes/pagamentos");
  exigeContem("platformHooks", texto, "/platform/documentos-fiscais");

  // (3) Plataforma: Diálogos e Ações
  exigeContem("platformPagamentoDialog", texto, "Registar Pagamento de Subscrição");
  exigeContem("platformNcDialog", texto, "Emitir Nota de Crédito");

  // (4) Escritório: Gating e Read-Only
  exigeContem("settingsPage", texto, 'hasFinanceiroView = can.view("financeiro")');
  exigeContem("settingsPage", texto, 'activeTab === "subscricao" && hasFinanceiroView');
  exigeContem("settingsPage", texto, "<SubscricaoTab />");

  // (5) Escritório: Hooks e Endpoints
  exigeContem("minhasSubscricoesHooks", texto, "/faturacao/subscricoes");
  exigeContem("minhasSubscricoesHooks", texto, "/faturacao/subscricoes/${encodeURIComponent(documentoId)}/pdf");
  exigeContem("minhasSubscricoesHooks", texto, "/faturacao/subscricoes/${encodeURIComponent(documentoId)}/xml");

  // (6) Escritório: Read-Only Guarantee (não deve ter formulários de emissão nem endpoints de escrita)
  exigeNaoContem("subscricaoTab", texto, "useRegistarPagamentoSubscricao");
  exigeNaoContem("subscricaoTab", texto, "useEmitirNotaCreditoSubscricao");
  exigeNaoContem("subscricaoTab", texto, "POST");

  if (falhas.length > 0) {
    console.error(`\x1b[31m[FALHA]\x1b[0m ${falhas.length} verificacao(oes) falharam:`);
    for (const f of falhas) {
      console.error(`  - ${f}`);
    }
    process.exit(1);
  }

  console.log("\x1b[32m[SUCESSO]\x1b[0m Todas as verificacoes estaticas de faturacao de plataforma passaram (Phase 138 / SUBS-01..05).");
}

main();
