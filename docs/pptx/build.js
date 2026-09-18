const pptxgen = require("pptxgenjs");
const { C, F, W, H, M, base, header, shot, rows, cards, callout, sh } = require("./theme");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";          // 13.3 x 7.5 — set before any slide
pres.author = "ALCv";
pres.company = "ALCv — Legal Practice Management Platform";
pres.title = "Manual do Utilizador — Plataforma Jurídica ALCv";

/* ------------------------------------------------------------------ 1. CAPA */
{
  const s = pres.addSlide();
  s.background = { color: C.bgDeep };
  s.addShape("ellipse", { x: 9.3, y: -2.2, w: 6.6, h: 6.6, fill: { color: C.surface }, line: { color: C.surface } });
  s.addShape("ellipse", { x: 10.4, y: -1.1, w: 4.4, h: 4.4, fill: { color: C.bgDeep }, line: { color: C.goldDim, width: 1 } });

  s.addText("ALCv", { x: M, y: 1.30, w: 6, h: 0.5, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 15, bold: true, color: C.gold, charSpacing: 4 });
  s.addText("Manual do Utilizador", { x: M, y: 1.90, w: 8.6, h: 1.0, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 46, bold: true, color: C.text });
  s.addText("Plataforma Jurídica de Gestão Forense — Cabo Verde", { x: M, y: 2.92, w: 8.6, h: 0.5, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 19, italic: true, color: C.ice });

  s.addShape("rect", { x: M, y: 3.72, w: 1.5, h: 0.03, fill: { color: C.gold }, line: { color: C.gold } });

  s.addText(
    "Guia completo dos módulos operacionais — clientes, processos judiciais,\nagenda forense, gestão documental, financeiro e administração.",
    { x: M, y: 3.98, w: 7.4, h: 0.9, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13.5, color: C.muted, lineSpacingMultiple: 1.05 });

  const meta = [
    ["Versão", "1.0 — Edição Oficial"],
    ["Ambiente", "www.alcv.tech"],
    ["Atualização", "Setembro de 2026"],
    ["Público-alvo", "Administradores · Advogados · Técnicos · Assistentes"],
  ];
  meta.forEach(([k, v], i) => {
    const y = 5.20 + i * 0.42;
    s.addText(k.toUpperCase(), { x: M, y, w: 1.75, h: 0.3, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 9.5, bold: true, color: C.gold, charSpacing: 1 });
    s.addText(v, { x: M + 1.85, y, w: 8.5, h: 0.3, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12, color: C.text });
  });
  s.addNotes("Capa. Manual do Utilizador da Plataforma Jurídica ALCv, versão 1.0, Setembro de 2026.");
}

/* ------------------------------------------------------- 2. ÍNDICE GERAL */
{
  const s = base(pres);
  header(s, "Índice geral", "O que este manual cobre");

  const parts = [
    { n: "I",   t: "Fundamentos", items: ["1. Visão Geral e Enquadramento", "2. Acesso, Autenticação e Segurança", "3. Perfis de Utilizador e Matriz RBAC"] },
    { n: "II",  t: "Operação Diária", items: ["4. Dashboard Institucional", "5. Gestão de Clientes"] },
    { n: "III", t: "Processos Judiciais", items: ["6. Gestão de Processos Judiciais", "7. Agenda Forense e Prazos"] },
    { n: "IV",  t: "Suporte Documental e Financeiro", items: ["8. Gestão Documental (MinIO / S3)", "9. Financeiro e Honorários", "10. Pareceres Jurídicos"] },
    { n: "V",   t: "Administração e Prática", items: ["11. Central de Notificações", "12. Definições e Administração", "13. Cenário Simulado Completo"] },
  ];

  // Two columns: parts I–III left, IV–V right.
  const colX = [M, 7.05];
  const colItems = [parts.slice(0, 3), parts.slice(3)];
  colItems.forEach((group, ci) => {
    let y = 1.68;
    group.forEach((p) => {
      s.addShape("roundRect", { x: colX[ci], y, w: 0.52, h: 0.38, rectRadius: 0.06,
        fill: { color: C.gold }, line: { color: C.gold } });
      s.addText(p.n, { x: colX[ci], y, w: 0.52, h: 0.38, isTextBox: true, margin: 0,
        fontFace: F.head, fontSize: 13, bold: true, color: C.bgDeep, align: "center", valign: "middle" });
      s.addText(p.t, { x: colX[ci] + 0.68, y: y + 0.02, w: 4.9, h: 0.34, isTextBox: true, margin: 0,
        fontFace: F.head, fontSize: 17, bold: true, color: C.text });
      y += 0.50;
      p.items.forEach((it) => {
        s.addText(it, { x: colX[ci] + 0.68, y, w: 4.9, h: 0.30, isTextBox: true, margin: 0,
          fontFace: F.body, fontSize: 12.5, color: C.muted });
        y += 0.34;
      });
      y += 0.30;
    });
  });
  s.addNotes("Índice geral, agrupado em cinco partes.");
}

/* ------------------------------------------------------ DIVIDER helper */
function divider(pres, num, part, blurb, chapters) {
  const s = pres.addSlide();
  s.background = { color: C.bgDeep };
  s.addShape("ellipse", { x: 10.0, y: 1.1, w: 5.2, h: 5.2, fill: { color: C.surface }, line: { color: C.surface } });

  s.addText(`PARTE ${num}`, { x: M, y: 2.28, w: 5, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 12, bold: true, color: C.gold, charSpacing: 3 });
  s.addText(part, { x: M, y: 2.68, w: 8.6, h: 0.95, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 38, bold: true, color: C.text });
  s.addText(blurb, { x: M, y: 3.78, w: 7.4, h: 0.75, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 13.5, color: C.muted, lineSpacingMultiple: 1.05 });

  chapters.forEach((c, i) => {
    const x = M + i * 2.55;
    s.addText(c, { x, y: 4.82, w: 2.45, h: 0.5, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 11, bold: true, color: C.ice });
    s.addShape("rect", { x, y: 4.74, w: 0.7, h: 0.025, fill: { color: C.goldDim }, line: { color: C.goldDim } });
  });
  s.addNotes(`Separador da Parte ${num} — ${part}.`);
  return s;
}

/* ============================ PARTE I — FUNDAMENTOS ============================ */
divider(pres, "I", "Fundamentos", 
  "Enquadramento da plataforma, forma de acesso e o modelo de permissões que governa tudo o que cada utilizador pode ver e fazer.",
  ["1 · Visão Geral", "2 · Acesso e Segurança", "3 · Perfis e RBAC"]);

/* --- 1. Visão geral: pilares --- */
{
  const s = base(pres);
  header(s, "Secção 1 · Visão geral e enquadramento", "Quatro pilares sustentam a plataforma");
  s.addText(
    "O ALCv é uma plataforma integrada de gestão forense desenhada para a realidade de Cabo Verde, cobrindo a tramitação nos tribunais de comarca, tribunais de relação e Supremo Tribunal de Justiça.",
    { x: M, y: 1.54, w: 12.1, h: 0.55, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, color: C.muted, lineSpacingMultiple: 1.0 });

  cards(s, [
    { tag: "Isolamento", t: "Multi-Tenant Estrito", d: "Cada sociedade opera num ambiente com dados, clientes e documentos completamente isolados, por partição lógica de tenant_id." },
    { tag: "Segurança", t: "Nível Bancário", d: "Autenticação por tokens JWT em cookies httpOnly com proteção SameSite=Lax, prevenindo XSS e extração de credenciais via JavaScript." },
    { tag: "Arquivo", t: "Armazenamento Conforme", d: "Peças processuais guardadas em MinIO (S3-compatible), com acesso exclusivo por URLs pré-assinados de validade temporária." },
    { tag: "Contexto", t: "Direito Cabo-Verdiano", d: "Terminologia, prazos e comarcas locais, NIF de 9 dígitos, CNI/BI e moeda nacional em Escudos (CVE, símbolo $)." },
  ], { x: M, y: 2.40, w: 2.92, h: "auto", cols: 4, gapX: 0.29 });

  s.addShape("rect", { x: M, y: 5.52, w: 1.2, h: 0.025, fill: { color: C.gold }, line: { color: C.gold } });
  s.addText(
    "Os quatro pilares não são opcionais nem configuráveis: são a base sobre a qual todos os módulos seguintes assentam.",
    { x: M, y: 5.76, w: 11.0, h: 0.5, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 14, italic: true, color: C.ice });

  s.addNotes("Secção 1 do manual — pilares fundamentais: multi-tenancy, segurança, armazenamento e adequação ao direito cabo-verdiano.");
}

/* --- 2.1 Login --- */
{
  const s = base(pres);
  header(s, "Secção 2.1 · Acesso à plataforma", "Ecrã de autenticação institucional");
  rows(s, [
    { t: "Abra o navegador", d: "Chrome, Edge, Firefox ou Safari em versão atual." },
    { t: "Introduza o endereço", d: "https://www.alcv.tech, ou o subdomínio atribuído ao seu escritório." },
    { t: "Autentique-se", d: "Email profissional registado e palavra-passe definida no convite inicial." },
  ], { x: M, y: 1.80, w: 4.55, gap: 0.92 });

  callout(s, { x: M, y: 4.72, w: 4.55, h: 1.45, label: "Credenciais de exemplo",
    text: "Utilizador: admin@alcv.cv\nPalavra-passe: definida no convite ou na configuração inicial do escritório.", tone: C.ice });

  shot(s, "01_login.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Ecrã de login inicial — autenticação por email profissional e palavra-passe." });
  s.addNotes("Secção 2.1. Três passos de acesso. O ecrã de login é a porta de entrada única da plataforma.");
}

/* --- 2.2 Política de senha --- */
{
  const s = base(pres);
  header(s, "Secção 2.2 · Credenciais e boas práticas", "Políticas de palavra-passe");

  cards(s, [
    { tag: "Confidencialidade", t: "Nunca partilhe a sua senha", d: "Não a comunique a terceiros nem a guarde em notas desprotegidas ou ficheiros sem cifra." },
    { tag: "Deteção", t: "Bloqueio e auditoria", d: "A plataforma bloqueia o acesso após múltiplas tentativas falhadas e regista anomalias de IP nos logs de auditoria." },
    { tag: "Rotação", t: "Alteração periódica", d: "Altere a senha regularmente em Definições › O Meu Perfil, respeitando os padrões de robustez exigidos." },
  ], { x: M, y: 2.52, w: 3.93, h: "auto", cols: 3, gapX: 0.33 });

  callout(s, { x: M, y: 4.62, w: 12.1, h: 1.30, label: "Importante",
    text: "O acesso é feito exclusivamente por JWT em cookies httpOnly. Não existem tokens acessíveis a JavaScript no navegador — o que elimina a classe de ataques que extrai credenciais a partir do código da página. Qualquer tentativa de acesso a uma área sem permissão é recusada pelo servidor, não apenas escondida no interface.",
    tone: C.gold });
  s.addNotes("Secção 2.2. Políticas de senha e o modelo de sessão por cookie httpOnly.");
}

/* --- 3. Matriz RBAC --- */
{
  const s = base(pres);
  header(s, "Secção 3 · Perfis e permissões", "Controlo de acesso baseado em papéis (RBAC)");
  rows(s, [
    { t: "Granularidade por módulo e ação", d: "Cada permissão segue a convenção scope:action — por exemplo clientes:view ou processos:edit." },
    { t: "Quatro papéis de base", d: "ADMIN, ADVOGADO, TECNICO e ASSISTENTE, atribuíveis por utilizador do escritório." },
    { t: "Verificação nas duas camadas", d: "O interface esconde o que não é permitido; o servidor recusa o pedido de forma independente." },
  ], { x: M, y: 1.80, w: 4.55, gap: 0.95 });

  callout(s, { x: M, y: 4.80, w: 4.55, h: 1.35, label: "Nota",
    text: "O perfil ADMIN tem as permissões totais bloqueadas por omissão, para impedir que o escritório se tranque a si próprio fora do sistema.", tone: C.ice });

  shot(s, "20c_rbac_permissoes.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Matriz de regras de acesso — permissões por módulo e ação, geríveis pelo administrador." });
  s.addNotes("Secção 3. A matriz RBAC é a fronteira primária de autorização, a par do isolamento por tenant.");
}

/* --- 3b. Papéis --- */
{
  const s = base(pres);
  header(s, "Secção 3 · Papéis disponíveis", "Quem faz o quê no escritório");
  cards(s, [
    { tag: "Admin", t: "Administrador do Escritório", d: "Acesso total e incondicional: configurações do escritório, gestão de equipa e matriz RBAC." },
    { tag: "Advogado", t: "Advogado Forense / Associado", d: "Gestão de processos, condução de peças, representação em tribunal, audiências, pareceres e termos de honorários." },
    { tag: "Técnico", t: "Técnico Jurídico / Solicitador", d: "Consulta de processos, acompanhamento de prazos, registo de diligências na agenda e consulta do arquivo." },
    { tag: "Assistente", t: "Secretariado / Apoio Administrativo", d: "Registo inicial de clientes (intake), atendimento, anexação de comprovativos e consulta geral da agenda." },
  ], { x: M, y: 1.78, w: 5.90, h: "auto", cols: 2, gapX: 0.30, gapY: 0.28, centerIn: [1.72, 5.85] });

  callout(s, { x: M, y: 5.98, w: 12.1, h: 0.80, label: "Convenção",
    text: "action ∈ { view, create, edit, manage } — manage implica edit, que implica create; todos implicam view.", tone: C.gold });
  s.addNotes("Secção 3. Os quatro papéis seeded e as suas competências operacionais.");
}

/* --- 3c. RBAC em ação --- */
{
  const s = base(pres);
  header(s, "Secção 3 · RBAC na prática", "O que acontece sem permissão");
  s.addText(
    "A verificação não é cosmética. Quando um utilizador tenta alcançar uma área fora do seu âmbito — por link direto, por URL colado ou por engano — o pedido é recusado no servidor e o interface apresenta uma recusa explícita, em vez de devolver dados parciais.",
    { x: M, y: 1.78, w: 4.55, h: 2.0, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, color: C.muted, lineSpacingMultiple: 1.05 });

  callout(s, { x: M, y: 3.95, w: 4.55, h: 2.2, label: "Porque isto importa",
    text: "Num escritório de advogados o sigilo profissional não é uma preferência de configuração. Recusar no servidor garante que nenhuma peça, cliente ou valor de honorário chega a sair da base de dados para quem não o pode ver.", tone: C.gold });

  shot(s, "22_plataforma.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Recusa de acesso à consola de administração para um utilizador sem o âmbito exigido." });
  s.addNotes("Secção 3. Demonstração prática da recusa de acesso imposta pelo servidor.");
}

/* ======================== PARTE II — OPERAÇÃO DIÁRIA ======================== */
divider(pres, "II", "Operação Diária",
  "O painel de comando que abre todos os dias e o ciclo de vida dos constituintes do escritório, de particulares a pessoas coletivas.",
  ["4 · Dashboard", "5 · Gestão de Clientes"]);

/* --- 4.1 Dashboard --- */
{
  const s = base(pres);
  header(s, "Secção 4 · Dashboard institucional", "O centro de comando diário");
  shot(s, "02_dashboard.png", { x: M, y: 1.75, w: 6.95,
    caption: "Dashboard institucional — indicadores em tempo real, prazos urgentes e processos recentes." });

  s.addText("Indicadores-chave (KPIs superiores)", { x: 8.15, y: 1.78, w: 4.55, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 16, bold: true, color: C.text });
  rows(s, [
    { t: "Clientes Ativos", d: "Total de clientes com processos ou serviços vigentes, com variação mensal." },
    { t: "Processos em Curso", d: "Ações judiciais e procedimentos extrajudiciais ativos." },
    { t: "Prazos Próximos", d: "Prazos perentórios e audiências nos próximos 7 dias — a vermelho quando urgente." },
    { t: "Honorários do Mês", d: "Total faturado e liquidado no mês corrente, em Escudos (CVE)." },
  ], { x: 8.15, y: 2.28, w: 4.55, gap: 0.90 });
  s.addNotes("Secção 4.1. Os quatro KPIs de topo dão a leitura imediata do estado do escritório.");
}

/* --- 4.2 Monitor de prazos --- */
{
  const s = base(pres);
  header(s, "Secção 4.2 e 4.3 · Monitor executivo", "Prazos urgentes e trilha de atividade");
  shot(s, "02b_dashboard_completo.png", { x: M, y: 1.75, w: 6.95,
    caption: "Metade inferior do dashboard — prazos urgentes, processos recentes e atividade da equipa." });

  rows(s, [
    { t: "Monitor de Prazos Urgentes", d: "Sinaliza em contagem decrescente os atos que exigem intervenção prioritária, com salto direto para a agenda forense." },
    { t: "Processos Recentes", d: "Últimos processos movimentados e o respetivo estado: ATIVO, EM TRIAGEM ou CONCLUÍDO." },
    { t: "Atividade Recente", d: "Documentos submetidos, fases alteradas e eventos concluídos por cada membro da equipa." },
  ], { x: 8.15, y: 1.90, w: 4.55, gap: 1.05 });

  callout(s, { x: 8.15, y: 5.30, w: 4.55, h: 1.45, label: "Leitura recomendada",
    text: "Abrir o dashboard no início do dia e tratar primeiro os prazos urgentes é a prática que melhor mitiga o risco central do escritório: perder um prazo perentório.", tone: C.gold });
  s.addNotes("Secções 4.2 e 4.3 do manual.");
}

/* --- 5.1 Listagem de clientes --- */
{
  const s = base(pres);
  header(s, "Secção 5.1 · Gestão de clientes", "Listagem e filtros de clientes");
  shot(s, "03_clientes_lista.png", { x: M, y: 1.75, w: 6.95,
    caption: "Listagem geral de clientes, com pesquisa rápida, filtros e ações por linha." });

  s.addText("Ações rápidas por linha", { x: 8.15, y: 1.78, w: 4.55, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 16, bold: true, color: C.text });
  rows(s, [
    { t: "Visualizar detalhe", d: "Acesso à página completa do cliente e ao seu histórico." },
    { t: "Imprimir ficha", d: "Emite a ficha cadastral formatada para arquivo físico ou dossiê." },
    { t: "Editar", d: "Atualização de contactos e morada do constituinte." },
    { t: "Eliminar", d: "Apenas para utilizadores autorizados e sem dependências ativas." },
  ], { x: 8.15, y: 2.28, w: 4.55, gap: 0.88 });

  callout(s, { x: 8.15, y: 5.92, w: 4.55, h: 0.80, label: "Pesquisa",
    text: "Por nome, email, telefone ou NIF cabo-verdiano.", tone: C.ice });
  s.addNotes("Secção 5.1. O módulo suporta pessoas singulares (Particulares) e coletivas (Empresas).");
}

/* --- 5.2 Novo cliente --- */
{
  const s = base(pres);
  header(s, "Secção 5.2 · Registo de novo cliente", "Campos do formulário de intake");
  shot(s, "04_clientes_novo.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Formulário de registo — acessível pelo botão «+ Adicionar Novo Cliente»." });

  const campos = [
    ["Tipo de Cliente", "Particular ou Empresa."],
    ["Nome / Razão Social", "Ex.: Maria João Gonçalves, ou Mindelo Marítima, SA."],
    ["NIF", "9 dígitos, conforme a DNRE."],
    ["Contacto e Email", "Para notificações e avisos de audiência."],
    ["Localidade e Morada", "Ilha e concelho — ex.: Achada Santo António, Praia."],
    ["Tipo de Documento", "CNI, BI, Passaporte ou Certidão de Registo Comercial."],
    ["Ramo de Atividade", "Contexto adicional de apoio ao atendimento."],
  ];
  campos.forEach(([k, v], i) => {
    const y = 1.80 + i * 0.63;
    s.addShape("rect", { x: M, y: y + 0.08, w: 0.10, h: 0.10, fill: { color: C.gold }, line: { color: C.gold } });
    s.addText(k, { x: M + 0.26, y, w: 4.30, h: 0.26, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, bold: true, color: C.text });
    s.addText(v, { x: M + 0.26, y: y + 0.25, w: 4.30, h: 0.34, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.95 });
  });
  s.addNotes("Secção 5.2. Campos obrigatórios e opcionais no registo de um constituinte.");
}

/* --- 5.3 Ficha do cliente --- */
{
  const s = base(pres);
  header(s, "Secção 5.3 · Ficha do cliente", "Dossiê oficial e visão de detalhe");
  shot(s, "05b_cliente_ficha.png", { x: M, y: 1.75, w: 5.90,
    caption: "Ficha oficial do cliente, formatada para impressão e arquivo em dossiê." });
  shot(s, "05_cliente_detalhe.png", { x: 6.80, y: 1.75, w: 5.90,
    caption: "Detalhe do cliente com separadores e posição de conta-corrente." });

  const secs = [
    ["Identificação e Contactos", "Dados fiscais e residenciais validados."],
    ["Saldo de Conta-Corrente", "Posição financeira em Escudos (CVE / $)."],
    ["Estado da Procuração", "Alerta visual quando ainda não assinada ou digitalizada."],
    ["Advogados Responsáveis", "Patrono e equipa jurídica encarregue do constituinte."],
  ];
  secs.forEach(([k, v], i) => {
    const x = M + i * 3.09;
    s.addText(k, { x, y: 6.04, w: 2.90, h: 0.26, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 11.5, bold: true, color: C.gold });
    s.addText(v, { x, y: 6.30, w: 2.90, h: 0.60, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.95 });
  });
  s.addNotes("Secção 5.3. As quatro secções da ficha do cliente.");
}

/* --- 5.4 Merge --- */
{
  const s = base(pres);
  header(s, "Secção 5.4 · Unificação de registos", "Merge de clientes duplicados");
  rows(s, [
    { t: "Selecione o Cliente Principal", d: "O registo que manterá os dados de contacto oficiais." },
    { t: "Selecione o Cliente Duplicado", d: "Os processos, honorários e histórico serão migrados para o principal." },
    { t: "Confirme a operação", d: "As referências cruzadas são atualizadas atomicamente na base de dados." },
  ], { x: M, y: 1.80, w: 4.55, gap: 0.92 });

  callout(s, { x: M, y: 4.72, w: 4.55, h: 1.45, label: "Atenção — operação irreversível",
    text: "Após a confirmação o registo duplicado é removido em definitivo. Verifique a seleção antes de confirmar.", tone: C.red });

  shot(s, "06_clientes_merge.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Ferramenta de merge — consolida o histórico de registos criados em duplicado." });
  s.addNotes("Secção 5.4. Casos típicos: o mesmo cliente inserido por assistentes diferentes sob nomes ligeiramente distintos.");
}

/* ===================== PARTE III — PROCESSOS JUDICIAIS ===================== */
divider(pres, "III", "Processos Judiciais",
  "O coração operacional da plataforma: abertura com verificação de conflito de interesses, tramitação em separadores e a agenda que protege os prazos.",
  ["6 · Processos", "7 · Agenda Forense"]);

/* --- 6.1 Listagem de processos --- */
{
  const s = base(pres);
  header(s, "Secção 6.1 · Carteira forense", "Listagem de processos judiciais");
  shot(s, "07_processos_lista.png", { x: M, y: 1.75, w: 6.95,
    caption: "Listagem de processos — a carteira forense completa do escritório." });

  rows(s, [
    { t: "Identificação do Processo", d: "Número judicial atribuído pelo tribunal, data de entrada e advogado responsável." },
    { t: "Cliente e Tribunal", d: "Constituinte e comarca — ex.: Tribunal da Comarca da Praia." },
    { t: "Área Jurídica", d: "Cível, Penal, Laboral, Família ou Fiscal/Aduaneiro." },
    { t: "Estado", d: "Badges dinâmicos: ATIVO, EM TRIAGEM, SUSPENSO ou ENCERRADO." },
  ], { x: 8.15, y: 1.85, w: 4.55, gap: 0.95 });
  s.addNotes("Secção 6.1. O módulo reflete a praxe processual civil, penal e laboral de Cabo Verde.");
}

/* --- 6.2 Wizard --- */
{
  const s = base(pres);
  header(s, "Secção 6.2 · Abertura de processo", "Wizard em três etapas");
  shot(s, "08_processos_novo.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Etapa 1 do assistente de abertura — recolha de dados de intake." });

  const etapas = [
    ["Etapa 1", "Intake", "Cliente, tipo de processo e origem (petição inicial, notificação avulsa, distribuição externa), tribunal, área jurídica, datas e descrição da pretensão."],
    ["Etapa 2", "Conflict Check", "Verificação deontológica automática de conflito de interesses, com decisão de um advogado ou administrador."],
    ["Etapa 3", "Abertura Formal", "Registo concluído, atribuição do número interno e notificação à equipa."],
  ];
  etapas.forEach(([tag, t, d], i) => {
    const y = 1.80 + i * 1.50;
    s.addShape("roundRect", { x: M, y, w: 4.55, h: 1.30, rectRadius: 0.05,
      fill: { color: C.surface }, line: { color: C.line, width: 1 }, shadow: sh({ blur: 10, offset: 3, opacity: 0.3 }) });
    s.addText(tag.toUpperCase(), { x: M + 0.24, y: y + 0.16, w: 3, h: 0.22, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 9.5, bold: true, color: C.gold, charSpacing: 1.1 });
    s.addText(t, { x: M + 0.24, y: y + 0.40, w: 4.07, h: 0.28, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 14, bold: true, color: C.text });
    s.addText(d, { x: M + 0.24, y: y + 0.68, w: 4.07, h: 0.55, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.93 });
  });
  s.addNotes("Secção 6.2. A verificação de conflito é obrigatória e precede a abertura formal.");
}

/* --- 6.2b Conflict check --- */
{
  const s = base(pres);
  header(s, "Secção 6.2 · Conflict check", "Verificação deontológica de conflito de interesses");
  s.addText(
    "O sistema analisa automaticamente se a parte contrária ou os intervenientes já constam como clientes do escritório, ou figuram em processos com pretensões incompatíveis. O decisor qualificado — advogado ou administrador — avalia o resultado e emite uma de três decisões.",
    { x: M, y: 1.54, w: 12.1, h: 0.62, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, color: C.muted, lineSpacingMultiple: 1.0 });

  const niveis = [
    { tag: "Decisão", t: "SEM CONFLITO", d: "Nada obsta à constituição de mandato. O processo segue para abertura formal.", col: C.green },
    { tag: "Decisão", t: "COM CONFLITO SANÁVEL", d: "Existe uma sobreposição que pode ser ultrapassada — por exemplo com consentimento informado das partes envolvidas.", col: C.gold },
    { tag: "Decisão", t: "IMPEDITIVO", d: "A formalização do processo é automaticamente bloqueada pelo sistema. O escritório não pode aceitar o mandato.", col: C.red },
  ];
  niveis.forEach((n, i) => {
    const x = M + i * 4.13;
    s.addShape("roundRect", { x, y: 2.34, w: 3.80, h: 2.70, rectRadius: 0.05,
      fill: { color: C.surface }, line: { color: n.col, width: 1.25 }, shadow: sh({ blur: 10, offset: 3, opacity: 0.3 }) });
    s.addText(n.tag.toUpperCase(), { x: x + 0.26, y: 2.56, w: 3.28, h: 0.22, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 9.5, bold: true, color: n.col, charSpacing: 1.1 });
    s.addText(n.t, { x: x + 0.26, y: 2.82, w: 3.28, h: 0.60, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 16, bold: true, color: C.text });
    s.addText(n.d, { x: x + 0.26, y: 3.48, w: 3.28, h: 1.30, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 11.5, color: C.muted, lineSpacingMultiple: 0.98 });
  });

  callout(s, { x: M, y: 5.32, w: 12.1, h: 0.95, label: "Regra de negócio",
    text: "Assinalado um conflito de nível impeditivo, a formalização do processo é bloqueada pelo sistema — não é uma recomendação que o utilizador possa contornar.", tone: C.red });
  s.addNotes("Secção 6.2, etapa 2. Três resultados possíveis da verificação de conflito.");
}

/* --- 6.3 Detalhe do processo --- */
{
  const s = base(pres);
  header(s, "Secção 6.3 · Ambiente do processo", "Dados do processo e conflict check");
  shot(s, "09_processo_detalhe.png", { x: M, y: 1.75, w: 6.95,
    caption: "Detalhe do processo — bloco de dados e resultado homologado do conflict check." });

  rows(s, [
    { t: "Bloco de Dados", d: "Número, tipo, área jurídica, tribunal, juízo, origem, cliente, estado e datas de início e fim." },
    { t: "Legal Hold e Retenção", d: "Marcação de retenção legal e respetiva data, para efeitos de conservação probatória." },
    { t: "Conflict Check Homologado", d: "Nível, decisor e data da decisão ficam visíveis de forma permanente no processo." },
    { t: "Gerar Termo de Honorários", d: "Ação direta na barra do processo — ver secção 6.4." },
  ], { x: 8.15, y: 1.85, w: 4.55, gap: 0.95 });
  s.addNotes("Secção 6.3. Vista principal do processo, a partir da qual se acede aos oito separadores operacionais.");
}

/* --- 6.3b Os 8 separadores --- */
{
  const s = base(pres);
  header(s, "Secção 6.3 · Estrutura do processo", "Os oito separadores operacionais");
  s.addText(
    "Aberto um processo, o utilizador dispõe de um ambiente de trabalho com oito separadores de controlo, cada um responsável por uma dimensão da tramitação.",
    { x: M, y: 1.52, w: 12.1, h: 0.40, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12.5, color: C.muted });

  const tabs = [
    { tag: "tab=timeline",    t: "Timeline Unificada", d: "Feed cronológico auditável de movimentações, juntadas, alterações de fase, despachos e audiências." },
    { tag: "tab=partes",      t: "Gestão de Partes", d: "Autor/requerente, réu/requerido, terceiros intervenientes e advogados contrários." },
    { tag: "tab=fases",       t: "Fases Processuais", d: "Estado do processo ao longo das fases do Código de Processo Civil ou Penal." },
    { tag: "tab=decisoes",    t: "Decisões Judiciais", d: "Despachos, sentenças e acórdãos, com anexação do ficheiro digitalizado em PDF." },
    { tag: "tab=factos",      t: "Factos Relevantes", d: "Cronologia dos factos controvertidos e provados, para organizar a matéria de facto." },
    { tag: "tab=testemunhas", t: "Rol de Testemunhas", d: "Testemunhas próprias e arroladas pela contraparte, com contactos e resumo do depoimento." },
    { tag: "tab=documentos",  t: "Documentos do Processo", d: "Peças, procurações com poderes forenses, certidões e comprovativos de taxa de justiça." },
    { tag: "tab=auditoria",   t: "Trilha de Auditoria", d: "Registo imutável de criações, edições, anexações e transições de estado." },
  ];
  cards(s, tabs, { x: M, y: 2.08, w: 2.92, h: "auto", cols: 4, gapX: 0.29, gapY: 0.28,
                   titleSize: 13, descSize: 10.5 });
  s.addNotes("Secção 6.3. Os oito separadores do ambiente de processo.");
}

/* --- 6.3 Separadores em detalhe: quatro pares de capturas reais --- */
{
  const pares = [
    { titulo: "Timeline e Partes", notas: "Separadores timeline e partes.", itens: [
      { f: "09_tab_timeline.png", cap: "Timeline — feed cronológico do processo.",
        t: "Timeline Unificada", d: "Decisão de conflict check e movimentações, com filtros por tipo e intervalo de datas." },
      { f: "09_tab_partes.png", cap: "Partes — intervenientes do litígio.",
        t: "Gestão de Partes", d: "Autor, réu e mandatários da contraparte, cada um com tipo e NIF." } ] },
    { titulo: "Fases e Decisões", notas: "Separadores fases e decisoes.", itens: [
      { f: "09_tab_fases.png", cap: "Fases — estado da tramitação.",
        t: "Fases Processuais", d: "Cada fase com estado editável, guardado individualmente." },
      { f: "09_tab_decisoes.png", cap: "Decisões — despachos e sentenças.",
        t: "Decisões Judiciais", d: "Data, tipo e resumo de cada decisão, com anexação do PDF digitalizado." } ] },
    { titulo: "Factos e Testemunhas", notas: "Separadores factos e testemunhas.", itens: [
      { f: "09_tab_factos.png", cap: "Factos — cronologia da matéria de facto.",
        t: "Factos Relevantes", d: "Factos datados e ordenados, para sustentar a argumentação em audiência." },
      { f: "09_tab_testemunhas.png", cap: "Testemunhas — rol do processo.",
        t: "Rol de Testemunhas", d: "Testemunhas de cada lado, com contacto e resumo do depoimento." } ] },
    { titulo: "Documentos e Auditoria", notas: "Separadores documentos e auditoria.", itens: [
      { f: "09_tab_documentos.png", cap: "Documentos — peças vinculadas ao processo.",
        t: "Documentos do Processo", d: "Arquivo das peças vinculadas: procurações, certidões e comprovativos. A lista surge vazia enquanto nada tiver sido carregado." },
      { f: "09_tab_auditoria.png", cap: "Auditoria — registo imutável de operações.",
        t: "Trilha de Auditoria", d: "Data, operação, entidade afetada e autor de cada alteração." } ] },
  ];

  pares.forEach((par) => {
    const s = base(pres);
    header(s, "Secção 6.3 · Separadores do processo", par.titulo);
    par.itens.forEach((it, i) => {
      const x = M + i * 6.20;
      shot(s, it.f, { x, y: 1.70, w: 5.90, caption: it.cap });
      s.addText(it.t, { x, y: 6.00, w: 5.90, h: 0.28, isTextBox: true, margin: 0, valign: "top",
        fontFace: F.body, fontSize: 13, bold: true, color: C.gold });
      s.addText(it.d, { x, y: 6.28, w: 5.90, h: 0.62, isTextBox: true, margin: 0, valign: "top",
        fontFace: F.body, fontSize: 11, color: C.muted, lineSpacingMultiple: 0.95 });
    });
    s.addNotes("Secção 6.3 do manual — " + par.notas);
  });
}

/* --- 6.3c Fases processuais --- */
{
  const s = base(pres);
  header(s, "Secção 6.3 · Fases processuais", "Tramitação segundo o Código de Processo");
  s.addText(
    "O separador de fases acompanha o estado do processo ao longo da tramitação, desde a distribuição até à fase de recursos.",
    { x: M, y: 1.52, w: 12.1, h: 0.40, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12.5, color: C.muted });

  const fases = ["Distribuição", "Citação / Notificação", "Articulados", "Audiência Prévia e Saneamento",
                 "Instrução e Produção de Prova", "Julgamento", "Sentença / Decisão Final", "Recursos"];
  const sub = ["Entrada no tribunal", "Chamamento das partes", "Contestação, réplica", "Delimitação do litígio",
               "Recolha e exame de prova", "Discussão e julgamento", "Decisão do tribunal", "Reapreciação superior"];

  fases.forEach((f, i) => {
    const x = M + (i % 4) * 3.09;
    const y = 2.20 + Math.floor(i / 4) * 2.10;
    s.addShape("roundRect", { x, y, w: 2.86, h: 1.78, rectRadius: 0.05,
      fill: { color: C.surface }, line: { color: C.line, width: 1 }, shadow: sh({ blur: 10, offset: 3, opacity: 0.3 }) });
    s.addShape("ellipse", { x: x + 0.24, y: y + 0.22, w: 0.44, h: 0.44, fill: { color: C.gold }, line: { color: C.gold } });
    s.addText(String(i + 1), { x: x + 0.24, y: y + 0.22, w: 0.44, h: 0.44, isTextBox: true, margin: 0,
      fontFace: F.head, fontSize: 14, bold: true, color: C.bgDeep, align: "center", valign: "middle" });
    s.addText(f, { x: x + 0.24, y: y + 0.78, w: 2.38, h: 0.52, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, bold: true, color: C.text, lineSpacingMultiple: 0.92 });
    s.addText(sub[i], { x: x + 0.24, y: y + 1.30, w: 2.38, h: 0.30, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10, color: C.muted });
  });
  s.addNotes("Secção 6.3, separador de fases. Oito fases da tramitação.");
}

/* --- 6.4 Termo de honorários --- */
{
  const s = base(pres);
  header(s, "Secção 6.4 · Termo de honorários", "Emissão do documento contratual");
  shot(s, "09b_processo_termo_honorarios.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Emissão do termo de honorários a partir da barra de ações do processo." });

  s.addText(
    "O botão «Gerar Termo de Honorários» emite o documento contratual de prestação de serviços jurídicos, para assinatura com o constituinte.",
    { x: M, y: 1.80, w: 4.55, h: 0.85, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13, color: C.muted, lineSpacingMultiple: 1.03 });

  callout(s, { x: M, y: 2.80, w: 4.55, h: 2.35, label: "Regra de negócio mandatória",
    text: "Se o valor dos honorários ainda não tiver sido lançado no módulo financeiro, o sistema avisa:\n\n«O valor dos honorários ainda não foi preenchido. Preencha o valor em Financeiro antes de gerar o termo.»",
    tone: C.gold });

  s.addText(
    "Assim se garante que nenhum documento vinculativo é impresso sem os valores devidamente acordados.",
    { x: M, y: 5.32, w: 4.55, h: 0.70, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12, italic: true, color: C.ice, lineSpacingMultiple: 1.0 });
  s.addNotes("Secção 6.4. A dependência do módulo financeiro é intencional — impede termos sem valor acordado.");
}

/* --- 6.5 Dashboard de processos --- */
{
  const s = base(pres);
  header(s, "Secção 6.5 · Dashboard de processos", "Produtividade e carga processual");
  shot(s, "10_processos_dashboard.png", { x: M, y: 1.75, w: 6.95,
    caption: "Dashboard operacional, acessível em Processos › Dashboard." });

  rows(s, [
    { t: "Prazos Críticos (< 7 dias)", d: "Ações com prazos iminentes que necessitam de peça urgente." },
    { t: "Processos Inativos (> 30 dias)", d: "Ações paradas sem movimentação, que requerem contacto com a secretaria judicial." },
    { t: "Backlog por Responsável", d: "Distribuição da carga de trabalho entre advogados e associados do escritório." },
  ], { x: 8.15, y: 1.95, w: 4.55, gap: 1.05 });

  callout(s, { x: 8.15, y: 5.40, w: 4.55, h: 1.10, label: "Uso executivo",
    text: "É a vista indicada para a reunião semanal de equipa: mostra onde está o risco e como está distribuído o trabalho.", tone: C.ice });
  s.addNotes("Secção 6.5. Vista de gestão, distinta da vista operacional de cada processo.");
}

/* --- 7.1 Agenda --- */
{
  const s = base(pres);
  header(s, "Secção 7.1 · Agenda forense", "Visão mensal e código de cores");
  shot(s, "11_agenda.png", { x: M, y: 1.75, w: 6.95,
    caption: "Agenda forense institucional — vista mensal com eventos categorizados por cor." });

  s.addText("Categorização visual dos eventos", { x: 8.15, y: 1.78, w: 4.55, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 16, bold: true, color: C.text });

  const cats = [
    ["E05A5A", "Prazos Críticos / Fatais", "Contestações, recursos, alegações, pagamento de custas."],
    ["5A8FE0", "Audiências", "Julgamentos, inquirições de testemunhas, tentativas de conciliação."],
    ["E0913F", "Diligências", "Conservatórias, cartórios, ministério público, Polícia Nacional."],
    ["57C08A", "Reuniões", "Consultas jurídicas presenciais ou virtuais com clientes."],
  ];
  cats.forEach(([col, t, d], i) => {
    const y = 2.30 + i * 0.95;
    s.addShape("ellipse", { x: 8.15, y: y + 0.03, w: 0.26, h: 0.26, fill: { color: col }, line: { color: col } });
    s.addText(t, { x: 8.15 + 0.42, y, w: 4.13, h: 0.28, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 13.5, bold: true, color: C.text });
    s.addText(d, { x: 8.15 + 0.42, y: y + 0.28, w: 4.13, h: 0.55, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.93 });
  });
  s.addNotes("Secção 7.1. A agenda existe para mitigar o principal risco da prática forense: a perda de prazos perentórios.");
}

/* --- 7.2 Novo evento --- */
{
  const s = base(pres);
  header(s, "Secção 7.2 · Marcação de evento", "Prazos fatais, audiências e diligências");
  shot(s, "12_agenda_novo.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Formulário de agendamento, aberto pelo botão «+ Novo Evento»." });

  rows(s, [
    { t: "Vinculação a Processo", d: "Associe o evento ao número judicial e ele aparece na timeline do processo." },
    { t: "Prioridade", d: "Defina como BAIXA, MÉDIA ou ALTA." },
    { t: "Data e Hora de Início e Fim", d: "Registo temporal com notificações automáticas." },
    { t: "Recorrência", d: "Para reuniões de acompanhamento ou avenças com periodicidade fixa." },
  ], { x: M, y: 1.85, w: 4.55, gap: 0.95 });

  callout(s, { x: M, y: 5.62, w: 4.55, h: 1.08, label: "Boa prática",
    text: "Vincular sempre ao processo — é o que mantém a timeline completa.", tone: C.gold });
  s.addNotes("Secção 7.2. Campos do formulário de novo evento.");
}

/* ============ PARTE IV — SUPORTE DOCUMENTAL E FINANCEIRO ============ */
divider(pres, "IV", "Suporte Documental e Financeiro",
  "O arquivo eletrónico das peças forenses, a gestão de honorários e conta-corrente em Escudos, e o circuito de pareceres jurídicos.",
  ["8 · Documentos", "9 · Financeiro", "10 · Pareceres"]);

/* --- 8. Documentos --- */
{
  const s = base(pres);
  header(s, "Secção 8 · Gestão documental", "Repositório central e upload de peças");
  shot(s, "13_documentos.png", { x: M, y: 1.75, w: 5.90,
    caption: "Repositório central de documentos do escritório." });
  shot(s, "14_documentos_novo.png", { x: 6.80, y: 1.75, w: 5.90,
    caption: "Formulário de upload, com classificação e campo de versionamento." });

  const feats = [
    ["Arrastar e Largar", "Envie ficheiros PDF, Word ou imagens com facilidade."],
    ["Confidencialidade", "Marque cada ficheiro como Público ou Restrito."],
    ["Versionamento Seguro", "O campo «ID a substituir» envia uma versão corrigida sem apagar o histórico."],
    ["Armazenamento em Objeto", "Nada é gravado no disco do servidor web — só URLs temporários."],
  ];
  feats.forEach(([k, v], i) => {
    const x = M + i * 3.09;
    s.addText(k, { x, y: 6.04, w: 2.90, h: 0.26, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 11.5, bold: true, color: C.gold });
    s.addText(v, { x, y: 6.30, w: 2.90, h: 0.62, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.95 });
  });
  s.addNotes("Secção 8. Os ficheiros vivem em MinIO (S3-compatible), servidos por URLs pré-assinados com expiração.");
}

/* --- 9. Financeiro --- */
{
  const s = base(pres);
  header(s, "Secção 9 · Financeiro e honorários", "Painel de controlo e conta-corrente");
  shot(s, "15_financeiro.png", { x: M, y: 1.75, w: 6.95,
    caption: "Painel financeiro — faturação, recebimentos e dívida em Escudos (CVE)." });

  s.addText("Painel de resumo financeiro", { x: 8.15, y: 1.78, w: 4.55, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 16, bold: true, color: C.text });
  rows(s, [
    { t: "Total Faturado", d: "Volume global de honorários contratualizados." },
    { t: "Total Recebido", d: "Já liquidado pelos clientes — numerário ou transferência (Vinti4, BCA, BCN)." },
    { t: "Em Dívida", d: "Saldo devedor pendente de regularização." },
    { t: "Receita do Mês", d: "Faturação e recebimentos no período de referência." },
  ], { x: 8.15, y: 2.28, w: 4.55, gap: 0.90 });
  s.addNotes("Secção 9. Moeda nacional: Escudo cabo-verdiano, CVE, símbolo $.");
}

/* --- 9b. Novo honorário --- */
{
  const s = base(pres);
  header(s, "Secção 9 · Acordo de honorários", "Lançamento de um novo honorário");
  shot(s, "16_financeiro_novo.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Lançamento de honorário associado a um processo." });

  rows(s, [
    { t: "Financeiro › + Novo Honorário", d: "Ponto de entrada único para acordos de honorários." },
    { t: "Selecione o processo", d: "O honorário fica vinculado ao processo correspondente." },
    { t: "Preencha o valor total", d: "Exemplo: 250.000$00 CVE." },
    { t: "Indique a data do acordo", d: "E as condições de parcelamento no campo de descrição." },
  ], { x: M, y: 1.85, w: 4.55, gap: 0.92 });

  callout(s, { x: M, y: 5.62, w: 4.55, h: 0.92, label: "Exemplo de parcelamento",
    text: "«50% na entrada da petição, 50% na data da audiência de julgamento.»", tone: C.ice });
  s.addNotes("Secção 9. Sem este lançamento, o termo de honorários da secção 6.4 não pode ser gerado.");
}

/* --- 10. Pareceres --- */
{
  const s = base(pres);
  header(s, "Secção 10 · Pareceres jurídicos", "Consultoria e emissão de pareceres");
  shot(s, "17_pareceres.png", { x: M, y: 1.75, w: 5.90,
    caption: "Listagem e gestão de pareceres jurídicos." });
  shot(s, "18_pareceres_novo.png", { x: 6.80, y: 1.75, w: 5.90,
    caption: "Registo de nova solicitação de parecer." });

  const fluxo = [
    ["Entrada do Pedido", "O assistente ou advogado regista a solicitação: cliente, questão jurídica e prazo."],
    ["Atribuição", "Indicação do advogado especialista responsável pela pesquisa e redação."],
    ["Elaboração e Revisão", "Fundamentos jurídicos, legislação cabo-verdiana e doutrina."],
    ["Homologação e Entrega", "Emissão do parecer final e notificação ao cliente."],
  ];
  fluxo.forEach(([k, v], i) => {
    const x = M + i * 3.09;
    s.addShape("ellipse", { x, y: 5.94, w: 0.32, h: 0.32, fill: { color: C.gold }, line: { color: C.gold } });
    s.addText(String(i + 1), { x, y: 5.94, w: 0.32, h: 0.32, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 11.5, bold: true, color: C.bgDeep, align: "center", valign: "middle" });
    s.addText(k, { x: x + 0.44, y: 5.94, w: 2.46, h: 0.28, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12, bold: true, color: C.text });
    s.addText(v, { x: x + 0.44, y: 6.22, w: 2.46, h: 0.62, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 10, color: C.muted, lineSpacingMultiple: 0.93 });
  });
  s.addNotes("Secção 10. Fluxo de trabalho do parecer, em quatro etapas.");
}

/* ============ PARTE V — ADMINISTRAÇÃO E PRÁTICA ============ */
divider(pres, "V", "Administração e Prática",
  "Como a equipa é notificada, como o escritório se administra a si próprio, e um caso prático completo para formação.",
  ["11 · Notificações", "12 · Administração", "13 · Cenário Simulado"]);

/* --- 11. Notificações --- */
{
  const s = base(pres);
  header(s, "Secção 11 · Central de notificações", "Alertas de prazos e alterações");
  shot(s, "19_notificacoes.png", { x: 5.75, y: 1.75, w: 6.95,
    caption: "Central de notificações — alertas de eventos em atraso e alterações nos processos." });

  rows(s, [
    { t: "Alertas de Evento em Atraso", d: "Destacados a vermelho, para intervenção imediata." },
    { t: "Filtros por Estado", d: "Separação entre notificações Não Lidas e Lidas." },
    { t: "Ação em Lote", d: "Botão «Marcar todas como lidas» para limpeza do painel após conferência." },
  ], { x: M, y: 1.85, w: 4.55, gap: 1.00 });

  callout(s, { x: M, y: 5.20, w: 4.55, h: 1.30, label: "Objetivo",
    text: "Assegurar que nenhum membro do escritório perde eventos críticos ou alterações efetuadas nos processos que acompanha.", tone: C.gold });
  s.addNotes("Secção 11.");
}

/* --- 12. Definições --- */
{
  const s = base(pres);
  header(s, "Secção 12 · Definições e administração", "Perfil pessoal e gestão da equipa");
  shot(s, "20_configuracoes.png", { x: M, y: 1.75, w: 5.90,
    caption: "Definições de sistema — separador «O Meu Perfil»." });
  shot(s, "20b_gestao_utilizadores.png", { x: 6.80, y: 1.75, w: 5.90,
    caption: "Gestão de utilizadores e credenciais do escritório." });

  const fn = [
    ["O Meu Perfil", "Nome, email de notificações, telefone e fotografia."],
    ["Segurança", "Alteração da palavra-passe com padrões seguros."],
    ["Gestão de Utilizadores", "«+ Novo Utilizador», papéis e estado Ativo/Inativo."],
    ["Controlo de Acesso", "Matriz global de direitos, para auditoria interna."],
  ];
  fn.forEach(([k, v], i) => {
    const x = M + i * 3.09;
    s.addText(k, { x, y: 6.04, w: 2.90, h: 0.26, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 11.5, bold: true, color: C.gold });
    s.addText(v, { x, y: 6.30, w: 2.90, h: 0.62, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 10.5, color: C.muted, lineSpacingMultiple: 0.95 });
  });
  s.addNotes("Secção 12. Funcionalidades de gestão disponíveis ao administrador do escritório.");
}

/* --- 13. Cenário simulado --- */
{
  const s = base(pres);
  header(s, "Secção 13 · Cenário simulado", "Caso prático completo — contexto Cabo Verde");
  s.addText(
    "Para efeitos de formação e demonstração, considere o seguinte caso realista, construído sobre os dados de exemplo da plataforma.",
    { x: M, y: 1.52, w: 12.1, h: 0.40, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 12.5, color: C.muted });

  const blocos = [
    { tag: "Sociedade", t: "Escritório Santiago, RL", linhas: [
      ["Denominação", "Escritório de Advocacia & Consultoria Jurídica Santiago, RL"],
      ["Sede", "Av. Cidade de Lisboa, Fazenda, Praia — Santiago"],
      ["NIF", "254.896.321"],
      ["Telefone", "+238 261 4000"]] },
    { tag: "Cliente", t: "Edemilson Pereira", linhas: [
      ["Código", "CLI-0004"],
      ["NIF", "23330938"],
      ["Documento", "CNI n.º 19890122M001Z"],
      ["Morada", "São Filipe, Praia — Ilha de Santiago"],
      ["Contacto", "+238 74774788"]] },
    { tag: "Processo", t: "Proc. 7373", linhas: [
      ["Entrada", "13/09/2026"],
      ["Tribunal", "Comarca da Praia — 1.º Juízo Criminal"],
      ["Natureza", "Processo Penal / Defesa Forense"],
      ["Conflict Check", "SEM CONFLITO (homologado)"],
      ["Honorário", "180.000$00 CVE"]] },
  ];

  blocos.forEach((b, i) => {
    const x = M + i * 4.13;
    s.addShape("roundRect", { x, y: 2.10, w: 3.80, h: 4.28, rectRadius: 0.05,
      fill: { color: C.surface }, line: { color: C.line, width: 1 }, shadow: sh({ blur: 10, offset: 3, opacity: 0.3 }) });
    s.addText(b.tag.toUpperCase(), { x: x + 0.26, y: 2.32, w: 3.28, h: 0.22, isTextBox: true, margin: 0,
      fontFace: F.body, fontSize: 9.5, bold: true, color: C.gold, charSpacing: 1.1 });
    s.addText(b.t, { x: x + 0.26, y: 2.58, w: 3.28, h: 0.36, isTextBox: true, margin: 0,
      fontFace: F.head, fontSize: 17, bold: true, color: C.text });
    b.linhas.forEach(([k, v], j) => {
      const y = 3.06 + j * 0.64;
      s.addText(k.toUpperCase(), { x: x + 0.26, y, w: 3.28, h: 0.20, isTextBox: true, margin: 0,
        fontFace: F.body, fontSize: 8.5, bold: true, color: C.ice, charSpacing: 0.8 });
      s.addText(v, { x: x + 0.26, y: y + 0.20, w: 3.28, h: 0.42, isTextBox: true, margin: 0,
        fontFace: F.body, fontSize: 10.5, color: C.text, lineSpacingMultiple: 0.93 });
    });
  });
  s.addNotes("Secção 13. Cenário simulado: sociedade, cliente e processo, com próxima audiência agendada com prazo fatal ativo.");
}

/* --- Encerramento --- */
{
  const s = pres.addSlide();
  s.background = { color: C.bgDeep };
  s.addShape("ellipse", { x: -1.8, y: 3.6, w: 5.6, h: 5.6, fill: { color: C.surface }, line: { color: C.surface } });

  s.addText("Em resumo", { x: 4.6, y: 1.28, w: 8.1, h: 0.40, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 12, bold: true, color: C.gold, charSpacing: 3 });
  s.addText("Um sistema, do intake à sentença", { x: 4.6, y: 1.72, w: 8.1, h: 0.95, isTextBox: true, margin: 0,
    fontFace: F.head, fontSize: 32, bold: true, color: C.text });

  const pontos = [
    "O cliente entra uma vez e serve todos os módulos — processos, agenda, documentos e conta-corrente.",
    "Nenhum processo abre sem verificação de conflito de interesses; nenhum termo de honorários sai sem valor acordado.",
    "Cada ficheiro vive em armazenamento de objeto, servido por ligação temporária, nunca no disco do servidor.",
    "Cada operação fica registada na trilha de auditoria do processo, de forma imutável.",
  ];
  pontos.forEach((p, i) => {
    const y = 2.92 + i * 0.78;
    s.addShape("ellipse", { x: 4.6, y: y + 0.06, w: 0.18, h: 0.18, fill: { color: C.gold }, line: { color: C.gold } });
    s.addText(p, { x: 5.02, y, w: 7.68, h: 0.66, isTextBox: true, margin: 0, valign: "top",
      fontFace: F.body, fontSize: 13, color: C.text, lineSpacingMultiple: 1.0 });
  });

  s.addText("ALCv · www.alcv.tech", { x: 4.6, y: 6.28, w: 5, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 12, bold: true, color: C.gold });
  s.addText("© 2026 ALCv. Todos os direitos reservados.", { x: 8.6, y: 6.28, w: 4.1, h: 0.32, isTextBox: true, margin: 0,
    fontFace: F.body, fontSize: 10.5, color: C.muted, align: "right" });
  s.addNotes("Encerramento.");
}

const OUT = require("path").join(__dirname, "..", "MANUAL_DO_UTILIZADOR.pptx");
pres.writeFile({ fileName: OUT }).then(() => console.log("Escrito:", OUT));
