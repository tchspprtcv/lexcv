// Prova automatizada e executavel (Node puro, sem dependencias) da UI da Phase 134 (Fatura-Recibo
// nos honorarios): gating por financeiro:view exato, imutabilidade do detalhe, copy vinculativa do
// 134-UI-SPEC, "frontend burro" (nenhuma conta de dinheiro nem constante de taxa no cliente) e os
// contratos dos hooks (opt-out de toast, caminhos relativos ao apiFetch).
//
// Tal como verify-faturacao.mjs, todas as assercoes sao verificacoes LITERAIS sobre o texto bruto
// dos ficheiros (sem remover comentarios): ate um comentario com um token proibido faz o gate
// falhar.
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
    "gerarChaveIdempotencia",
    "usePreVisualizacaoFaturacao",
    "chaveIdempotencia: chave",
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
  for (const nome of ["form", "dialog", "columns", "detalhe", "lista", "pagamentosCard"]) {
    for (const [regex, descricao] of padroes) exigeSemPadrao(nome, texto, regex, descricao);
  }
  exigeSemPadrao("schema", texto, /\b0\.(15|20)\b/, "constante de taxa");

  // (e) Hooks: endpoints, opt-out de toast, caminhos relativos ao apiFetch.
  exigeContem("hooksFaturacao", texto, "/faturacao/pre-visualizacao");
  exigeContem("hooksFaturacao", texto, "/faturacao/estado-emissao");
  exigeContem("hooksFaturacao", texto, "/documentos-fiscais");
  exigeContem("hooksFaturacao", texto, "semToastParaStatus");
  exigeNaoContem("hooksFaturacao", texto, "/api/v1");
  exigeContem("hooksFinanceiro", texto, "semToastParaStatus: [409, 422]");
  exigeContem("hooksFinanceiro", texto, "DOCUMENTOS_FISCAIS_KEY");
  exigeNaoContem("hooksFinanceiro", texto, "/api/v1");
  exigeContem("idempotencia", texto, "getRandomValues");
  exigeContem("erros", texto, "interpretarErroEmissao");
  exigeContem("erros", texto, "mensagemGuardaFiscal");

  if (falhas.length > 0) {
    console.error(`verify:documentos-fiscais FALHOU (${falhas.length}):`);
    for (const f of falhas) console.error(`  - ${f}`);
    process.exit(1);
  }
  console.log(
    "verify:documentos-fiscais OK - gating financeiro:view, detalhe imutável, copy, sem contas de dinheiro no cliente e hooks.",
  );
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
