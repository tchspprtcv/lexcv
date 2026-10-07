// Prova automatizada e executavel (Node puro, sem dependencias) da UI da Phase 137 (Entrega por
// email, descargas PDF/XML, exportacao mensal e notificacao de falha): gating exato por
// financeiro:edit (reenvio) e financeiro:view (leitura/descarga/exportacao), copy vinculativa do
// 137-UI-SPEC, "frontend burro" (o frontend nunca gera nem transforma CSV), neutralidade visual
// dos badges e dos cartoes, e a Read-Only Guarantee da aba de documentos fiscais do cliente.
//
// Tal como verify-documentos-fiscais.mjs e verify-faturacao.mjs, todas as assercoes sao
// verificacoes LITERAIS sobre o texto bruto dos ficheiros (incluindo comentarios): qualquer token
// proibido faz o gate falhar.
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para os testes e para a verificacao UAT):
//   1. Que os ecras renderizam com o backend real e que os dialogos descarregam ficheiros reais.
//   2. Que o backend recusa 403 a quem nao tem a permissao necessaria.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC = process.env.VERIFY_ENTREGA_RAIZ
  ? path.resolve(process.env.VERIFY_ENTREGA_RAIZ)
  : path.join(__dirname, "..", "src");

const FINANCEIRO = path.join(SRC, "app", "(dashboard)", "financeiro");
const DOCUMENTOS = path.join(FINANCEIRO, "documentos-fiscais");
const CLIENTES = path.join(SRC, "app", "(dashboard)", "clientes");
const SETTINGS = path.join(SRC, "app", "(dashboard)", "settings");

const FICHEIROS = {
  libEntrega: path.join(SRC, "lib", "entrega-email.ts"),
  badgeEntrega: path.join(SRC, "components", "shared", "entrega-email-badge.tsx"),
  botoesDescarga: path.join(SRC, "components", "shared", "descarregar-documento-botoes.tsx"),
  hooksFaturacao: path.join(SRC, "hooks", "use-faturacao.ts"),
  api: path.join(SRC, "lib", "api.ts"),
  detalhePage: path.join(DOCUMENTOS, "[id]", "page.tsx"),
  entregaCard: path.join(DOCUMENTOS, "[id]", "entrega-email-card.tsx"),
  reenviarDialog: path.join(DOCUMENTOS, "[id]", "reenviar-email.tsx"),
  documentosColumns: path.join(DOCUMENTOS, "columns.tsx"),
  documentosPage: path.join(DOCUMENTOS, "page.tsx"),
  clientePage: path.join(CLIENTES, "[id]", "page.tsx"),
  clienteTab: path.join(CLIENTES, "[id]", "documentos-fiscais-tab.tsx"),
  financeiroPage: path.join(FINANCEIRO, "page.tsx"),
  exportarDialog: path.join(FINANCEIRO, "exportar-mes-dialog.tsx"),
  settingsEmailCard: path.join(SETTINGS, "faturacao-email-card.tsx"),
  notificacaoCategoria: path.join(SRC, "lib", "notificacao-categoria.ts"),
  notificacoesTypes: path.join(SRC, "types", "notificacoes.ts"),
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

function exigeSemPadrao(nome, texto, regex, descricao) {
  const m = texto[nome].match(regex);
  if (m) {
    falhas.push(`${relativo(FICHEIROS[nome])}: contem ${descricao} ${JSON.stringify(m[0])}`);
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

  // (1) Gates exatos (sem fallback amplo).
  exigeContem("hooksFaturacao", texto, 'PERMISSAO_REENVIAR_EMAIL = "financeiro:edit"');
  exigeContem("hooksFaturacao", texto, "hasPermission(permissions, PERMISSAO_REENVIAR_EMAIL)");
  exigeContem("entregaCard", texto, "podeReenviarEmail(permissions)");
  exigeContem("entregaCard", texto, "mostrarReenviar(podeReenviar, e)");
  exigeContem("clientePage", texto, "podeLerDocumentosFiscais(permissions.permissions)");
  exigeContem("financeiroPage", texto, "podeLerDocumentosFiscais(permissions.permissions)");
  exigeNaoContem("entregaCard", texto, "hasScopedPermission");
  exigeNaoContem("reenviarDialog", texto, "hasScopedPermission");
  exigeNaoContem("clienteTab", texto, "hasScopedPermission");
  exigeNaoContem("exportarDialog", texto, "hasScopedPermission");

  // (2) Copy vinculativa do 137-UI-SPEC presente literalmente.
  // Badges e descricoes de estado
  for (const rotulo of [
    "Não configurado",
    "Desligado",
    "Sem email do cliente",
    "Pendente",
    "Enviado",
    "Falhou",
  ]) {
    exigeContem("libEntrega", texto, rotulo);
  }

  // Descricoes longas fixas
  exigeContem(
    "libEntrega",
    texto,
    "O servidor de email não está configurado nesta instalação. O documento foi emitido normalmente, mas não é enviado por email. Peça ao administrador da instalação para configurar o servidor de email.",
  );
  exigeContem(
    "libEntrega",
    texto,
    "O envio automático por email está desligado nas definições de faturação do escritório.",
  );
  exigeContem(
    "libEntrega",
    texto,
    "O cliente não tem email registado. Adicione um email na ficha do cliente para poder enviar o documento.",
  );
  exigeContem(
    "libEntrega",
    texto,
    "O email será enviado em segundo plano, com o PDF e o XML em anexo.",
  );
  exigeContem(
    "libEntrega",
    texto,
    "Email enviado ao cliente com o PDF e o XML em anexo. A receção pelo cliente não é confirmada.",
  );

  // Copy de contagem de tentativas em FALHOU
  exigeContem("libEntrega", texto, "O envio do email falhou após ");
  exigeContem("libEntrega", texto, " tentativa.");
  exigeContem("libEntrega", texto, " tentativas.");
  exigeContem("libEntrega", texto, "O envio do email falhou.");
  exigeContem("libEntrega", texto, " Pode reenviar o email.");
  exigeNaoContem("libEntrega", texto, "falhou após 5 tentativas");
  exigeNaoContem("libEntrega", texto, "tentativa(s)");

  // Botoes, dialogos e cartoes
  exigeContem("libEntrega", texto, "Entrega por email");
  exigeContem("libEntrega", texto, "Reenviar email");
  exigeContem("libEntrega", texto, "Enviar email");
  exigeContem("libEntrega", texto, "Fechar sem reenviar");
  exigeContem("libEntrega", texto, "Email colocado na fila de envio.");
  exigeContem("libEntrega", texto, "Descarregar PDF");
  exigeContem("libEntrega", texto, "Descarregar XML");
  exigeContem("libEntrega", texto, "A preparar PDF...");
  exigeContem("libEntrega", texto, "Exportar mês");
  exigeContem("libEntrega", texto, "Exportar CSV");
  exigeContem("libEntrega", texto, "Ficheiro CSV exportado.");

  // Ecras
  exigeContem("clienteTab", texto, "Documentos fiscais");
  exigeContem("clienteTab", texto, "Ver todos os documentos fiscais deste cliente");
  exigeContem(
    "clienteTab",
    texto,
    "Os documentos fiscais não podem ser apagados nem alterados. As correções são feitas por nota de crédito.",
  );
  exigeContem("financeiroPage", texto, "Exportar honorários (CSV)");
  exigeContem("exportarDialog", texto, "Exportar documentos fiscais do mês");
  exigeContem("exportarDialog", texto, "Fechar sem exportar");
  exigeContem(
    "settingsEmailCard",
    texto,
    "Desligado por omissão. Quando ligado, cada documento aceite na comunicação é enviado ao cliente por email, com o PDF e o XML em anexo.",
  );
  exigeContem("notificacaoCategoria", texto, "Falha de envio de email fiscal");

  // (3) Neutralidade visual (sem green/amber nos badges de entrega).
  exigeSemPadrao(
    "badgeEntrega",
    texto,
    /bg-(blue|green|emerald|amber)|text-(blue|green|emerald|amber)|variant="default"/,
    "cor de acento ou de sucesso no badge de entrega",
  );

  // (4) Vocabulário proibido apenas nos ficheiros criados pela Phase 137.
  const ficheirosNovos137 = [
    "libEntrega",
    "badgeEntrega",
    "botoesDescarga",
    "entregaCard",
    "reenviarDialog",
    "clienteTab",
    "exportarDialog",
  ];

  for (const nome of ficheirosNovos137) {
    exigeSemPadrao(
      nome,
      texto,
      /\bEntregue\b|\bRecebido\b|\bLido\b|Autorizad|Aprovad|Validado pela DNRE/,
      "linguagem de confirmacao enganadora ou de autorizacao",
    );
  }
  exigeNaoContem("settingsEmailCard", texto, "numa versão futura");

  // (5) Seguranca e integridade ("frontend burro" e sem sinks XSS).
  for (const nome of [...ficheirosNovos137, "settingsEmailCard", "documentosColumns", "documentosPage", "detalhePage"]) {
    exigeNaoContem(nome, texto, "dangerouslySetInnerHTML");
  }

  // A aba do cliente nao deve ter operacoes destrutivas nem importar use-documentos.
  for (const token of ["use-documentos", "useDeleteDocumento", "Trash", "Checkbox", "DropdownMenu", "onDelete"]) {
    exigeNaoContem("clienteTab", texto, token);
  }

  // O dialogo de exportacao nao deve construir nem sanitizar CSV no cliente.
  for (const token of ["toCsv", "guardCsvFormula", "guardFormula", "\\uFEFF"]) {
    exigeNaoContem("exportarDialog", texto, token);
  }

  // Hooks: rotas relativas, sem http hardcoded, e presenca de apiFetchFicheiro.
  exigeContem("hooksFaturacao", texto, "encodeURIComponent(documentoId)");
  exigeContem("hooksFaturacao", texto, "encodeURIComponent(mes)");
  exigeNaoContem("hooksFaturacao", texto, "http://");
  exigeNaoContem("hooksFaturacao", texto, "https://");
  exigeContem("api", texto, "export async function apiFetchFicheiro");

  // (6) Notificacoes (Surface 5).
  exigeContem("notificacoesTypes", texto, '"EMAIL_FISCAL_FALHOU"');
  exigeContem("notificacaoCategoria", texto, 'EMAIL_FISCAL_FALHOU: "Falha de envio de email fiscal"');
  exigeContem("notificacaoCategoria", texto, 'EMAIL_FISCAL_FALHOU: "red"');
  exigeContem("notificacaoCategoria", texto, '"EMAIL_FISCAL_FALHOU"');

  if (falhas.length > 0) {
    console.error(`verify:entrega-fiscal FALHOU (${falhas.length}):`);
    for (const f of falhas) console.error(`  - ${f}`);
    process.exit(1);
  }
  console.log(
    "verify:entrega-fiscal OK - gating financeiro:edit/view, copy vinculativa, neutralidade dos badges, aba de cliente só de leitura, frontend burro na exportação e categoria de notificação.",
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
