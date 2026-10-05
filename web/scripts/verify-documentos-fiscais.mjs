// Prova automatizada e executavel (Node puro, sem dependencias) da UI da Phase 134 (Fatura-Recibo
// nos honorarios): gating por financeiro:view exato, imutabilidade do detalhe, copy vinculativa do
// 134-UI-SPEC, "frontend burro" (nenhuma conta de dinheiro nem constante de taxa no cliente) e os
// contratos dos hooks (opt-out de toast, caminhos relativos ao apiFetch).
//
// Tal como verify-faturacao.mjs, todas as assercoes sao verificacoes LITERAIS sobre o texto bruto
// dos ficheiros (sem remover comentarios): ate um comentario com um token proibido faz o gate
// falhar.
//
// Phase 135 (Nota de Credito, 135-UI-SPEC Surfaces 1-2): o dialogo "Emitir Nota de Credito"
// (nota-credito-dialog.tsx) concentra os pedidos da NC via hooks, por isso o detalhe continua sem
// mutacoes; o gate fixa a copy vinculativa do dialogo, o ciclo de vida da chave (CR-02), o gate
// EXATO financeiro:manage, a ausencia de contas de dinheiro e de dangerouslySetInnerHTML (o texto
// livre do motivo e renderizado so como texto React).
//
// O QUE ESTE GATE NAO CONSEGUE PROVAR (fica para a verificacao ponta a ponta, 134-HUMAN-UAT.md):
//   1. Que os ecras renderizam com o backend real e que o dialogo emite uma unica vez.
//   2. Que o backend recusa 403/404 a quem nao tem permissao ou e de outro escritorio.

import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const SRC = path.join(__dirname, "..", "src");
const FINANCEIRO = path.join(SRC, "app", "(dashboard)", "financeiro");
const HONORARIO = path.join(FINANCEIRO, "[id]");
const DOCUMENTOS = path.join(FINANCEIRO, "documentos-fiscais");

const FICHEIROS = {
  honorarioPage: path.join(HONORARIO, "page.tsx"),
  form: path.join(HONORARIO, "pagamento-faturado-form.tsx"),
  dialog: path.join(HONORARIO, "pagamento-faturado-dialog.tsx"),
  pagamentosCard: path.join(HONORARIO, "pagamentos-card.tsx"),
  lista: path.join(DOCUMENTOS, "page.tsx"),
  columns: path.join(DOCUMENTOS, "columns.tsx"),
  detalhe: path.join(DOCUMENTOS, "[id]", "page.tsx"),
  notaCreditoDialog: path.join(DOCUMENTOS, "[id]", "nota-credito-dialog.tsx"),
  financeiroPage: path.join(FINANCEIRO, "page.tsx"),
  hooksFaturacao: path.join(SRC, "hooks", "use-faturacao.ts"),
  hooksFinanceiro: path.join(SRC, "hooks", "use-financeiro.ts"),
  erros: path.join(SRC, "lib", "erros-emissao.ts"),
  idempotencia: path.join(SRC, "lib", "idempotencia.ts"),
  schema: path.join(SRC, "schemas", "financeiro.ts"),
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

  // (a) Gating: as duas paginas e o botao de entrada usam o gate EXATO financeiro:view
  //     (podeLerDocumentosFiscais, igual ao @PreAuthorize do backend -- ver 134-11/134-13).
  exigeContem("hooksFaturacao", texto, 'PERMISSAO_LEITURA_FISCAL = "financeiro:view"');
  exigeContem("hooksFaturacao", texto, "hasPermission(permissions, PERMISSAO_LEITURA_FISCAL)");
  for (const nome of ["lista", "detalhe", "financeiroPage"]) {
    exigeContem(nome, texto, "podeLerDocumentosFiscais(permissions.permissions)");
  }
  exigeContem("lista", texto, "<AccessDeniedState");
  exigeContem("detalhe", texto, "<AccessDeniedState");
  exigeContem("financeiroPage", texto, "/financeiro/documentos-fiscais");
  exigeContem("financeiroPage", texto, "Documentos fiscais");

  // (b) Imutabilidade do detalhe (EMIS-08).
  exigeContem("detalhe", texto, "Documento imutável: não pode ser alterado nem apagado.");
  exigeContem("detalhe", texto, "Simulação — sem validade fiscal");
  exigeContem("detalhe", texto, "Documento fiscal não encontrado");
  for (const token of [
    "useMutation",
    'method: "DELETE"',
    'method: "PUT"',
    'method: "PATCH"',
    'method: "POST"',
    ">Apagar",
    ">Editar",
    "Anular",
  ]) {
    exigeNaoContem("detalhe", texto, token);
  }
  exigeNaoContem("lista", texto, "useMutation");
  exigeNaoContem("columns", texto, "useMutation");

  // (c) Copy vinculativa da emissao e da lista de pagamentos.
  for (const token of [
    "Confirmar fatura-recibo",
    "Emitir fatura-recibo",
    "Voltar e editar",
    "Documento simulado: não tem validade fiscal.",
    "Simulação — sem validade fiscal",
    "Fechar pré-visualização",
    "A conta corrente do cliente é creditada do total.",
  ]) {
    exigeContem("dialog", texto, token);
  }
  for (const token of [
    "Registar pagamento",
    "Aplicar retenção na fonte",
    "Método de pagamento *",
    // CR-02 da revisao: a chave pertence ao pedido e sobrevive ao fecho do dialogo enquanto o
    // desfecho de uma tentativa estiver por resolver (lib/idempotencia.ts).
    "tentativaParaPedido",
    "marcarPorResolver",
    "desfechoDefinitivo",
    "usePreVisualizacaoFaturacao",
    "chaveIdempotencia: tentativa.chave",
    "Pagamento registado e fatura-recibo ${numero} emitida.",
  ]) {
    exigeContem("form", texto, token);
  }
  exigeContem("pagamentosCard", texto, "Sem documento fiscal");
  exigeContem("pagamentosCard", texto, "Pagamento faturado: não pode ser apagado.");
  exigeContem("pagamentosCard", texto, "mensagemGuardaFiscal");
  exigeContem("pagamentosCard", texto, "pagamento-${p.id}");
  // Ramo com a faturacao desligada fica como estava (CFG-03).
  exigeContem("honorarioPage", texto, "useEstadoEmissao");
  exigeContem("honorarioPage", texto, "Adicionar");
  exigeContem("honorarioPage", texto, "Pagamento registado com sucesso.");
  exigeContem("honorarioPage", texto, "pagamentoFormSchema");
  exigeContem("lista", texto, "Ainda não há documentos fiscais");
  exigeContem("lista", texto, "Nenhum documento encontrado");

  // (d) Frontend burro: nenhuma constante de taxa nem aritmetica de dinheiro nos ecras fiscais.
  const padroes = [
    [/\b0\.(15|20)\b/, "constante de taxa"],
    [/\b1\.15\b/, "fator de IVA"],
    [/\*\s*0\./, "multiplicacao por fracao"],
    [/\/\s*100\b/, "divisao por 100"],
  ];
  for (const nome of ["form", "dialog", "columns", "detalhe", "lista", "pagamentosCard", "notaCreditoDialog"]) {
    for (const [regex, descricao] of padroes) exigeSemPadrao(nome, texto, regex, descricao);
  }
  exigeSemPadrao("schema", texto, /\b0\.(15|20)\b/, "constante de taxa");

  // (e) Hooks: endpoints, opt-out de toast, caminhos relativos ao apiFetch.
  exigeContem("hooksFaturacao", texto, "/faturacao/pre-visualizacao");
  exigeContem("hooksFaturacao", texto, "/faturacao/estado-emissao");
  exigeContem("hooksFaturacao", texto, "/documentos-fiscais");
  exigeContem("hooksFaturacao", texto, "semToastParaStatus");
  exigeNaoContem("hooksFaturacao", texto, "/api/v1");
  // IN-02 da revisao: o pedido faturado (com chave) trata 409/422/5xx inline; o legado fica igual.
  exigeContem("hooksFinanceiro", texto, "semToastParaStatus: payload.chaveIdempotencia ? STATUS_INLINE_EMISSAO : [409, 422]");
  exigeContem("erros", texto, "export const STATUS_INLINE_EMISSAO: readonly number[] = [409, 422, 500, 502, 503, 504];");
  exigeContem("hooksFinanceiro", texto, "DOCUMENTOS_FISCAIS_KEY");
  exigeNaoContem("hooksFinanceiro", texto, "/api/v1");
  exigeContem("idempotencia", texto, "getRandomValues");
  exigeContem("idempotencia", texto, "export function tentativaParaPedido");
  exigeContem("idempotencia", texto, "export function marcarPorResolver");
  exigeContem("erros", texto, "interpretarErroEmissao");
  exigeContem("erros", texto, "mensagemGuardaFiscal");

  // (f) Phase 135 -- Nota de Credito (135-UI-SPEC Surfaces 1-2).
  for (const token of [
    "Emitir Nota de Crédito",
    "Pré-visualizar nota de crédito",
    "Emitir nota de crédito",
    "Voltar e editar",
    "Fechar sem emitir",
    "Valor ainda creditável",
    "Valor creditável restante",
    "Total a creditar",
    "Depois de emitida, a nota de crédito não pode ser alterada nem apagada.",
    "Nota de crédito ${nc.numeroFormatado} emitida.",
    // CR-02: a chave pertence ao conteudo do pedido, incluindo a FR de origem.
    "tentativaParaPedido",
    "documentoOrigemId: documento.id",
    "chaveIdempotencia: tentativa.chave",
    "marcarPorResolver",
    "desfechoDefinitivo",
    "emitindoRef",
    "useEmitirNotaCredito",
    "usePreVisualizacaoNotaCredito",
  ]) {
    exigeContem("notaCreditoDialog", texto, token);
  }
  for (const token of [
    "Notas de crédito",
    "Valor ainda creditável",
    "Ainda não foram emitidas notas de crédito para esta fatura-recibo.",
    "Ver fatura-recibo original",
    "Esta fatura-recibo já foi totalmente creditada.",
    "Ver estorno",
    "podeEmitirNotaCredito(permissions.permissions)",
    "<NotaCreditoDialog",
  ]) {
    exigeContem("detalhe", texto, token);
  }
  for (const nome of ["detalhe", "notaCreditoDialog"]) {
    exigeNaoContem(nome, texto, "dangerouslySetInnerHTML");
  }
  exigeNaoContem("notaCreditoDialog", texto, "/api/v1");
  exigeContem("hooksFaturacao", texto, 'PERMISSAO_EMISSAO_NOTA_CREDITO = "financeiro:manage"');
  exigeContem("hooksFaturacao", texto, "hasPermission(permissions, PERMISSAO_EMISSAO_NOTA_CREDITO)");
  exigeContem("hooksFaturacao", texto, "notas-credito");

  if (falhas.length > 0) {
    console.error(`verify:documentos-fiscais FALHOU (${falhas.length}):`);
    for (const f of falhas) console.error(`  - ${f}`);
    process.exit(1);
  }
  console.log(
    "verify:documentos-fiscais OK - gating financeiro:view, detalhe imutável, copy, sem contas de dinheiro no cliente, hooks e Nota de Crédito (financeiro:manage).",
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
