# Feature Research

**Domain:** Faturação eletrónica (eFatura de Cabo Verde, DNRE) numa plataforma de gestão jurídica multi-tenant (LexCV v3.0)
**Researched:** 2026-10-04
**Confidence:** MEDIUM para a estrutura do sistema (tipos, séries, IUD, prazos); LOW-MEDIUM para as regras fiscais finas (retenção, isenções); nenhuma afirmação é HIGH (ver secção 0)

---

## 0. Limites de verificação (ler primeiro)

**Nenhuma fonte primária pôde ser aberta.** Os domínios `efatura.cv`, `mf.gov.cv`, `boe.incv.cv`, `ministeriopublico.cv`, `lobocarmona.com`, `cvtradeinvest.cv` e todos os sites de fornecedores devolveram `EGRESS_BLOCKED` / `403` na política de saída da organização. Não foi contornado (regra do ambiente). Consequências:

- Os factos abaixo vêm de **excertos devolvidos pelo motor de pesquisa** (incluindo excertos das páginas oficiais `efatura.cv/docs/...` e do Manual Técnico v7/v10/v11, que não foram lidos diretamente), cruzados com fornecedores de software homologados pela DNRE (Wisedat, PayTraq, Odoo `l10n_cv_efatura`, Primavera, Vendus, Edicom), escritórios e consultoras (Miranda, VPQ, RFF, PwC, consultoria.cv) e a Ordem dos Advogados (OACV).
- Escala usada: **MEDIUM** = 2+ fontes independentes concordam e apontam para um instrumento legal identificável (mas o texto legal não foi lido); **LOW** = fonte única, inferência, ou fontes em conflito. Não há HIGH porque nada foi confirmado no texto oficial.
- **Contaminação detetada e descartada:** (a) o blog `vendus.cv` replica conteúdo português ("artigo 53.º", "10 mil euros") que **não** é regime cabo-verdiano; (b) um excerto da EY (guia 2026) dá "IVA 19% / 9%" e "Code of taxes on turnover", terminologia que não é de Cabo Verde (provável erro de atribuição do excerto; contradiz PwC e as leis orçamentais). Ambos ignorados.
- **Antes de fechar REQ-IDs fiscais:** o contabilista do utilizador deve confirmar a lista da secção 11-B, e quem tiver acesso à rede deve ler o Manual Técnico v11 (secção 11-C).

---

## 1. Quadro legal (resumo)

| Instrumento | Conteúdo relevante | Confiança |
|-------------|--------------------|-----------|
| Decreto-Lei n.º 79/2020, de 12 de novembro (BO I Série n.º 128; em vigor a 13-nov-2020) | Regime jurídico da Fatura Eletrónica e dos documentos eletrónicos fiscalmente relevantes: emissão, conservação, arquivo, requisitos dos sistemas, fiscalização. (Um excerto diz "28 de dezembro": é a data da Portaria 74/2020.) | MEDIUM |
| Portaria n.º 74/2020, de 28 de dezembro | Comunicação eletrónica e faseamento: importadores 01-jul-2021; grandes contribuintes 01-set-2021; médios 03-jan-2022; **REMPE, categoria B com contabilidade organizada (incl. atos isolados) e categoria C: 02-jun-2022** | MEDIUM |
| Portaria n.º 16/2022, de 3 de maio | Credenciação do emitente, **contingência**, manifestação do destinatário | MEDIUM |
| Manual Técnico da Fatura Eletrónica (v7, v9, v10; **v11.0 é a mais recente**) + XSD + API `services.efatura.cv` | Formato XML próprio, IUD, séries/LED, serviços eletrónicos | MEDIUM (não lido) |
| Código do IVA (Lei n.º 21/VI/2003, de 14-jul, alterada) e Regulamento do IVA | Art. 32.º n.º 5 (elementos da fatura); art. 36.º n.º 4 (fatura ou fatura-recibo obrigatória se o adquirente é sujeito passivo ou a venda é a crédito); art. 7.º (exigibilidade; fatura até ao 5.º dia útil; pagamento antecipado: data do documento = data da receção) | MEDIUM-LOW |
| Código do IRPS (Lei n.º 78/VIII/2014, de 31-dez; BO I Série n.º 81) | Retenção na fonte sobre rendimentos da categoria B. Um excerto cita "art. 47.º": **não confirmado** | MEDIUM (artigo: LOW) |
| REMPE (Lei n.º 70/VIII/2014, de 26-ago; alterada, p.ex. BO I Série n.º 53 de 28-abr-2020 e n.º 23 de 21-mar-2024) | Regime simplificado (TEU), limites e atividades excluídas | MEDIUM |
| Estatuto da OACV (Lei n.º 91/VI/2006, de 9-jan) | Provisões/adiantamentos de honorários e despesas. **Não encontrei regra da OACV sobre recibo/fatura de honorários** | LOW |
| OE 2025 (Lei n.º 45/X/2024) e OE 2026 (Lei n.º 69/X/2025, de 31-dez) | Sem alteração à taxa normal de IVA nem à retenção da categoria B nos resumos PwC; OE 2026: IRPC 21%→20%. Existe um **orçamento retificativo de 2026** promulgado cujo conteúdo fiscal **não foi verificado** | MEDIUM / LOW |

---

## 2. Tipos de documento (pergunta 1)

Códigos de 2 dígitos do campo "tipo de DFE" (também embutidos no IUD). Concordam: Paytraq (01, 02, 05), Odoo `l10n_cv_efatura` (01 a 09) e excertos do manual. **Confiança MEDIUM.**

| Código | Sigla | Documento | Uso | LexCV v3.0 |
|--------|-------|-----------|-----|------------|
| 01 | FTE | Fatura | Cobrança/venda a crédito; obrigatória se o adquirente é sujeito passivo ou a venda é a crédito (CIVA 36.º/4) | Não usar (decisão: sem fatura prévia) |
| **02** | **FRE** | **Fatura-Recibo** | Substitui fatura+recibo; emitida no ato da prestação/pagamento; admite pagamentos antecipados; prova de pagamento | **USAR: um por pagamento** |
| 03 | TVE | Talão de Venda / serviço prestado | Consumidor final (particular que não destina o serviço a atividade profissional); identificação do adquirente opcional | Não (o LexCV exige NIF a todos os clientes) |
| 04 | RCE | Recibo | Pagamento de fatura anterior | Não |
| **05** | **NCE** | **Nota de Crédito** | Retifica/reduz um documento anterior; referencia o original | **USAR: única forma de correção** |
| 06 | NDE | Nota de Débito | Aumenta valor de documento anterior | Não |
| 07 | DTE | Documento de Transporte | Mercadorias | N/A |
| 08 | DVE | Nota de Devolução | Devolução de bens | N/A |
| 09 | - | "Posting Note" (Odoo); designação PT não confirmada | - | N/A |

**Resposta direta:** "pagamento recebido = fatura emitida" corresponde à **Fatura-Recibo (02)**; corrige-se com **Nota de Crédito (05)**. Isto é coerente com o CIVA: se há pagamento por prestação ainda não realizada, o documento tem a data da receção [MEDIUM-LOW]. A emissão da FR no mesmo ato do pagamento cumpre também o prazo de 5 dias úteis do art. 7.º [MEDIUM-LOW].

**Natureza do documento legal:** a fatura eletrónica "é um documento de existência apenas digital, emitido, arquivado e conservado eletronicamente, cuja validade jurídica é garantida pela assinatura digital do emissor e pela autorização de uso concedida pela Administração Tributária em tempo real" (texto de `efatura.cv/docs/about/e-fatura/`, visto só como excerto). O DFE legal é o **XML assinado e autorizado**; o PDF é uma representação. [MEDIUM]

---

## 3. Elementos obrigatórios e representação PDF (pergunta 2)

| Elemento | Regra | Confiança | Origem no LexCV / lacuna |
|----------|-------|-----------|--------------------------|
| Emitente: designação, NIF, sede/morada | CIVA reg. 32.º/5; modelo eFatura | MEDIUM | `Tenant` tem `nome`, `nif` (**anulável**); **faltam morada, país e regime de IVA**. A tenant reservada "LexCV" é criada só com `nome` (sem NIF): os dados fiscais da plataforma ainda não existem |
| Adquirente: nome, NIF, morada | idem; para o eFatura o NIF é cabo-verdiano e o país "CV" | MEDIUM / LOW (país) | `Cliente`: nome, nif (9 dígitos, obrigatório desde v2.7), morada. A validação é só "9 dígitos": **não distingue NIF estrangeiro** (p.ex. português, também 9 dígitos) |
| Consumidor final (sem NIF) | Só em TVE/fatura a consumidor final; identificação opcional | MEDIUM | Fora de âmbito: NIF é obrigatório no LexCV |
| Tipo, **série**, **número sequencial**, **data e hora** de emissão | Obrigatórios; CIVA exige data + numeração sequencial | MEDIUM | Fuso `Atlantic/Cape_Verde` já usado no `AlertasDiariosJob` |
| **IUD** (45 caracteres) | Componentes: país (CV), código de repositório, data AAMMDD, NIF do emitente, **LED (5 dígitos, zeros à esquerda)**, tipo (2), número (até 9 dígitos), código aleatório, dígito de controlo. Gerado pelo software emitente. As **posições** variam entre versões do manual (44 vs 45 caracteres quando o LED passou a 5 dígitos) | MEDIUM (componentes) / LOW (posições) | Não fixar posições: ler o manual v11 |
| **QR Code** | Conteúdo: URL `https://services.efatura.cv/v1/dfe/view/{IUD}` (fonte única, Edicom); impresso depois de o documento ser enviado ao eFatura | LOW-MEDIUM | Ver risco de QR "falso" em 4.5 |
| Linhas: descrição, quantidade, preço líquido de IVA, taxa e valor de IVA | CIVA reg. 32.º/5 | MEDIUM | Item único "Honorários - Processo {n.º}" (`Honorario.descricao`) |
| Isenção: **código de motivo** na linha + menção no documento | "Os motivos de isenção devem ser mencionados nas faturas, identificados pelo código" | MEDIUM-LOW | Nenhuma isenção aplicável por defeito (ver 5.1) |
| Totais: base por taxa, IVA, total | - | MEDIUM | Calculado no servidor |
| Pagamento (FRE): meio e valor | A FR exige indicação do pagamento | MEDIUM-LOW | `Pagamento.metodo` é **String livre** (seed: "TRANSFERENCIA", "DINHEIRO"): mapear para a lista de meios do manual |
| Retenção na fonte | Ver 5.2; **elemento XML não verificado** | LOW | Novo campo opcional por pagamento |
| Nota de Crédito: referência ao DFE original + motivo | CIVA: "documentos retificativos" levam data, n.º sequencial, elementos do 32.º/5, **referência à fatura a que respeitam** e menção das alterações. O XML tem elemento de referência (Odoo: atributo `IsOldDocument`) | MEDIUM / LOW (nome do atributo) | Guardar `documentoOrigemId` + IUD do original |
| Assinatura digital (certificado ICP-CV) + autorização DNRE em tempo real | Condição de validade | MEDIUM | **Não implementada em v3.0** |
| "Emitido em contingência, pendente de autorização" | Menção obrigatória quando offline | MEDIUM | Estado modelado, fluxo não construído |
| Identificação do software | Não confirmado no manual; fornecedores mostram "Software Certificado N.º ..." (Vendus: 2230) | LOW | O LexCV não é homologado: ver 4.5 |
| Layout do PDF | **Não encontrei layout obrigatório**; o PDF deve conter todos os elementos acima + IUD + QR | LOW (inferido) | PDF gerado no servidor (necessário para anexar ao email) |

---

## 4. Séries, numeração, prazos, contingência, anulação (pergunta 3)

### 4.1 Séries e LED
- Por cada série existente tem de ser criado na plataforma da DNRE um **LED (Local de Emissão de Documentos)**; o código LED é obrigatório na comunicação de todos os DFE porque **controla a sequência da numeração**. A correspondência LED↔série é **1:1**. No início de cada ano é preciso criar LEDs para as novas séries. [MEDIUM: Wisedat, PayTraq, Odoo]
- Número do documento: até 9 dígitos, ≥ 1, **reinicia a cada ano civil**, **sequencial sem lacunas** (para o número N tem de existir N-1, exceto N=1). [MEDIUM: Wisedat a citar o manual]
- **Recomendação:** uma série por `(emitente, ano, tipo)`, p.ex. `FR2026` e `NC2026`, cada uma com o seu LED futuro. O tipo vai no IUD, logo numerar por tipo é seguro nas duas leituras possíveis da regra. Em v3.0 o LED fica "por registar". [LOW-MEDIUM: modelo a confirmar no manual v11]

### 4.2 Prazos e modo de operação
- **Online (normal):** o sistema do contribuinte envia o XML e **espera pela autorização síncrona**; só depois o documento é válido. [MEDIUM]
- **Contingência (sistema/ligação em baixo):** emite-se offline com a menção "emitido em contingência, pendente de autorização" e comunica-se à DNRE em **5 dias úteis** (Portaria 16/2022). [MEDIUM]
- **Fatura no CIVA:** até ao 5.º dia útil após a exigibilidade; pagamento antecipado de serviço não prestado: data = data da receção. [MEDIUM-LOW]
- **Obrigatoriedade:** profissionais de categoria B com contabilidade organizada (caso dos advogados, ver 5.1) estão obrigados desde **02-jun-2022**. [MEDIUM]

### 4.3 Anulação, cancelamento e Nota de Crédito
- A plataforma tem uma operação de **cancelamento** de DFE já aceite, referida para documento **ainda não entregue ao destinatário** e com erro (p.ex. NIF). **O prazo não foi encontrado.** [LOW]
- A decisão fixada (só Nota de Crédito) é um subconjunto deliberado: ver AF-02.
- NC: tem de **referenciar o original**. A regra "a NC não pode exceder o original" **não foi encontrada em nenhuma fonte**; implementar como **invariante própria** (soma das NC ≤ original, por componente base/IVA/retenção; não se emite NC sobre NC), não como regra legal verificada. [LOW como regra legal]
- Prazo máximo para emitir NC (ajustes de IVA): não encontrado. [LOW]

### 4.4 Credenciação e certificados (para a ligação real, fora de v3.0)
- O contribuinte tem de estar **credenciado** pela DNRE (registo fiscal atualizado, declaração de início de atividade, atividade ativa, certificado digital) e adere à Plataforma Eletrónica. [MEDIUM]
- Certificado de **assinatura**: pessoa singular com CNI ou assinatura qualificada; pessoa coletiva com **Certificado Qualificado de Representação Coletiva / Selo Eletrónico**. Existe um papel de **"Transmissor"** de DFE (autenticação SSL-EV) distinto do **"Emitente"**, e um **Middleware** da DNRE (GUI em `https://localhost:3443/v1/core/index`) usado por vários ERPs, com modos ONLINE/OFFLINE. [MEDIUM]
- **Implicação de modelo:** credenciais e certificado são **por tenant (emitente)**, e a LexCV poderá ser "transmissor". Reservar a ligação do adaptador a uma configuração por emitente.
- Software de faturação tem de ser **aprovado/homologado pela DNRE** (certificado digital ICP-CV incorporado). [MEDIUM]

### 4.5 Risco crítico: o que o modo simulado NÃO é
Num sistema de **autorização em tempo real**, um documento sem autorização da DNRE **não é um documento fiscal válido**. Logo, com o adaptador simulado:
- as FR e NC geradas em v3.0 **não têm validade fiscal**, e o escritório **continua obrigado** (desde 02-jun-2022) a emitir faturas válidas no seu software homologado;
- um QR/IUD "reais" no PDF apontariam para um DFE inexistente em `services.efatura.cv` e dariam a falsa impressão de conformidade.

Isto torna a **marcação inequívoca de estado fiscal** (TS-13) um requisito de produto, não um detalhe de UI.

### 4.6 Conservação
Livros, registos e documentos de suporte: **10 anos**, salvo prazo especial (regra geral; DL 79/2020 trata de conservação/arquivo eletrónico). [MEDIUM-LOW] Implica: nunca apagar documentos fiscais, guardar o XML canónico e um snapshot dos dados.

---

## 5. Regras fiscais (pergunta 4: entregável-chave)

### 5.1 IVA

| Ponto | Conclusão | Fonte | Confiança |
|-------|-----------|-------|-----------|
| **Taxa normal** | **15%** | PwC Worldwide Tax Summaries (Cabo Verde, Corporate - Other taxes); fornecedores CV; sem alteração em OE 2025/2026 segundo os resumos PwC | MEDIUM |
| Outras taxas | 8% eletricidade/água a consumidores finais (irrelevante); 0% exportações | PwC | MEDIUM |
| **Serviços jurídicos (advogados)** | Tributados à **taxa normal**. **Nenhuma isenção específica encontrada** (o art. 9.º cobre saúde, ensino/apoio social/formação, operações financeiras e seguros, etc.). "Não encontrei" não prova "não existe" | consultoria.cv, Wisedat, PwC; sem excerto explícito sobre advocacia | MEDIUM-LOW |
| **Isenção por pequeno volume de negócios** | Não identificado equivalente ao art. 53.º português; contribuintes pequenos vão para o REMPE. (O blog Vendus CV e um excerto EY "sem limiares" são contaminação/LOW) | - | LOW |
| **REMPE/TEU** | TEU de **4%** sobre o volume de negócios, trimestral, substitui IRPS/IRPC/IVA e outras; micro: ≤ 5.000 contos e/ou ≤ 5 trabalhadores; pequena: 5.000-10.000 contos e/ou 6-10; micro < 1.000 contos isenta de TEU. Fatura emitida **isenta de IVA** com motivo "Tributo Especial Unificado" (**código 20** na tabela Wisedat). Não se pode ter REMPE e retenção em simultâneo | consultoria.cv, PwC, Wisedat | MEDIUM |
| **Advogados no REMPE?** | **Excluídos**: anexo ao art. 2.º exclui a secção M da CAE (consultoria, atividades científicas e técnicas, incl. **atividades jurídicas**); a **OACV** declara publicamente que os profissionais liberais não estão no REMPE e têm de ter contabilidade organizada, e pede um regime simplificado | Porton di Nos Ilhas; OACV; consultoria.cv | MEDIUM |
| **Consequência** | Escritórios e advogados em nome individual: **IVA normal (15%) + contabilidade organizada + e-fatura obrigatória**. REMPE/isenção fica como possibilidade de modelo, não como caminho por defeito | inferência das duas linhas anteriores | MEDIUM |
| Motivos de isenção (tabela Wisedat) | Códigos 1 a 6, 10, 11, 20, 21 (margem, lista anexa, operações internas, exportação, outras, quantias excluídas da base, consignação, não residentes, REMPE, OE). Os **artigos citados seguem numeração portuguesa**, portanto desconfiar | Wisedat | LOW |
| Quantias excluídas da base (código 6) | Reembolso de despesas pagas em nome e por conta do cliente (custas, emolumentos) ficam fora da base de IVA. **Fora de âmbito de v3.0** (ver AF-07) | Wisedat | LOW |

### 5.2 Retenção na fonte (IRPS, categoria B)

| Ponto | Conclusão | Confiança |
|-------|-----------|-----------|
| Quem retém | A entidade que paga serviços (rendimentos da categoria B) e **tem, ou deveria ter, contabilidade organizada**, a **prestadores que são pessoas singulares** (profissionais/empresários em nome individual) | MEDIUM |
| **Taxa** | **20%** do valor do serviço, por conta do imposto anual, se o prestador está em **contabilidade organizada**; **4%** se está no REMPE. Concordam Vendus CV, Wisedat, consultoria.cv, caboverdeexpert, citando o Código do IRPS | MEDIUM |
| **Conflito não resolvido** | **PwC** indica **15%** para a categoria B. Pode ser versão antiga ou outra figura (liberatória/não residentes). **Não resolvido sem o texto do Código do IRPS** | LOW |
| Sociedades | Pagamentos entre **sociedades residentes** geralmente **sem retenção** (PwC). Inferência: uma **sociedade de advogados (IRPC)** não sofre retenção | LOW-MEDIUM |
| Base de cálculo | "Valor do serviço": **com ou sem IVA não verificado**. Por prudência, base = valor tributável **sem IVA** | LOW |
| Entrega pelo pagador | Até ao dia 15 do mês seguinte, com a DPR (Calendário Fiscal 2026); um excerto dá dia 20. **Não é obrigação do LexCV** | LOW |
| Retenção de IVA | Nenhuma evidência de retenção de IVA em CV | LOW |
| Como aparece na FR | Linha "Retenção IRPS (taxa %)" e **valor líquido recebido**; o elemento XML correspondente **não foi verificado** | LOW |

**Quando se aplica na prática ao LexCV:** apenas se (a) o emitente é **advogado em nome individual** (IRPS cat. B, contabilidade organizada) e (b) o cliente que paga é uma **entidade com contabilidade organizada** (tipicamente `Cliente.tipo = Empresa`). Em sociedades de advogados e em clientes Particulares tipicamente **não há retenção**. Um "Particular" que seja empresário em nome individual pode reter: o sistema **não consegue saber**, portanto a retenção tem de ser uma **entrada manual por pagamento** (com sugestão), nunca uma decisão automática (AF-08).

**Exemplo ilustrativo (base a confirmar):** pagamento de 120.000,00 CVE, IVA incluído, retenção 20% sem IVA. Base = 104.347,83; IVA 15% = 15.652,17; retenção = 20.869,57; recebido em dinheiro = 99.130,43. A conta corrente do cliente **é creditada de 120.000,00** (dinheiro + imposto retido pago ao Estado em seu nome).

### 5.3 Subscrição LexCV (plataforma → escritório)
- IVA à **taxa normal 15%** (serviço prestado em CV; um blog de fornecedor sugere que SaaS não tem taxa reduzida). [MEDIUM-LOW]
- **Pré-requisito não satisfeito hoje:** o emitente "LexCV" tem de ter NIF, morada e regime de IVA, e (para a ligação real) estar credenciado. A entidade jurídica que opera a plataforma e o seu estatuto fiscal (residente? sociedade ou pessoa singular? registo de IVA?) **não estão documentados** (secção 11-A).
- Retenção: se o emitente é sociedade, nenhuma; se pessoa singular com contabilidade organizada, o **escritório** reteria 20%; se for **não residente**, o regime muda (retenção de 15% sobre serviços a não residentes segundo PwC; registo de IVA de não residentes). [LOW]
- O escritório (adquirente) precisa de **NIF e nome corretos** na fatura para deduzir o IVA: o gate de dados fiscais do tenant (TS-02) aplica-se também aqui.

### 5.4 Decisões recomendadas para o REQUIREMENTS (o que o utilizador adiou)

| ID | Decisão recomendada | Justificação | Confiança | Quem confirma |
|----|---------------------|--------------|-----------|---------------|
| FISC-1 | Taxa normal **15%** numa **tabela parametrizável com data de vigência**, nunca hardcoded | Estável desde 2015 mas sujeita a lei orçamental anual | MEDIUM | Contabilista |
| FISC-2 | `Pagamento.valorPago` = **total com IVA incluído** (gross-up): base = round(total/1,15; 2), IVA = total − base (soma exata) | Mantém inalterados `valorTotal`, `totalPago` e a conta corrente; evita reinterpretar honorários já acordados | MEDIUM (decisão de produto) | **Utilizador** |
| FISC-3 | Regime de IVA do emitente: enum `NORMAL` (por defeito) / `ISENTO` com código de motivo; **REMPE (código 20) só se o contabilista o confirmar**, porque advogados estão excluídos | Dados já preparados sem refazer o modelo | MEDIUM | Contabilista |
| FISC-4 | **Retenção opcional por pagamento**: checkbox + taxa **editável (sugestão 20%)** + valor retido; base sem IVA; conta corrente creditada do total; aviso se o emitente é sociedade ou o cliente é Particular | Cobre o caso real do advogado individual sem automatizar uma decisão legal | MEDIUM (taxa) / LOW (base) | Contabilista |
| FISC-5 | v3.0 só emite a clientes com **NIF cabo-verdiano**; sem consumidor final, sem não residentes | Evita IVA internacional | MEDIUM | Utilizador |
| FISC-6 | Subscrição: 15%, sem retenção por defeito, emitente = tenant reservada "LexCV" com dados fiscais configuráveis e **emissão bloqueada** até estarem completos | Pré-requisito legal | MEDIUM | **Utilizador** + contabilista |
| FISC-7 | Arredondamento: `BigDecimal` escala 2, `HALF_UP`; NC parcial por gross-up do valor creditado | Regra de implementação própria; o manual pode impor outra | LOW | Manual v11 |

---

## 6. Feature Landscape

### Table Stakes (obrigatório legal ou necessário para cumprir as decisões fixadas)

| ID | Feature | Why Expected | Complexity | Notes (dependências e confiança) |
|----|---------|--------------|------------|----------------------------------|
| TS-01 | **Dados fiscais do emitente** (escritório e plataforma): NIF, designação, morada/sede, país, regime de IVA | Sem emitente completo não há DFE | LOW-MEDIUM | Estende `Tenant` (sem morada/regime; `nif` anulável). Colunas novas em `t_tenant` seguem o padrão `columnDefinition ... default` de `ativo`/`plano` + **migração manual** em `backend/migrations/` |
| TS-02 | **Gate de pré-requisitos** antes de emitir: emitente completo e cliente com NIF válido; erro acionável | Evitar documentos imutáveis inválidos | LOW | Aplica-se aos dois emitentes |
| TS-03 | **Emissão atómica**: pagamento + FR + consumo de número + conta corrente na **mesma transação** (tudo ou nada) | Decisão fixada; numeração sem lacunas | HIGH | `createPagamento` (L3039-3081) **não é `@Transactional`** e engole `DataAccessException` da conta corrente com `log.warn`: **incompatível**. Recusas devolvidas como `ResponseEntity` precisam de `RecusaTransacional.recusar` |
| TS-04 | **Séries e numeração** por `(emitente, ano, tipo)`, sem lacunas, concorrência-segura, `UNIQUE(tenant, série, número)` | Regra do eFatura [MEDIUM] | HIGH | **Não usar `SEQUENCE` do PostgreSQL** (deixa lacunas em rollback): linha-contador com `SELECT ... FOR UPDATE`. Teste com Testcontainers (precedente `ParecerVersaoConcorrenciaIT`) |
| TS-05 | **Cálculo de IVA**: taxa parametrizável, IVA incluído, discriminação por taxa, isenção + código de motivo | CIVA 32.º/5 | MEDIUM | FISC-1/2/3/7 |
| TS-06 | **Retenção na fonte opcional** por pagamento (taxa, base, valor, líquido recebido) | Caso real do advogado individual vs empresa | MEDIUM | FISC-4. Taxa e base são LOW: ficam editáveis |
| TS-07 | **Documento imutável com snapshot** de emitente, adquirente, linhas e impostos | `Cliente` e `Tenant` são editáveis; documento fiscal não muda | MEDIUM | Padrão `@Immutable` + repositório sem remoção já usado na auditoria RBAC (v2.17) |
| TS-08 | **Pagamento faturado não pode ser apagado nem alterado** (409); pagamentos legados mantêm-se como estão, rotulados "sem fatura (anterior à faturação eletrónica)" | Decisão fixada | LOW-MEDIUM | `deletePagamento` (L3156) é `financeiro:manage`. Não existe PUT de pagamento visível |
| TS-09 | **Nota de Crédito** total e parcial, com referência ao original, motivo, soma das NC ≤ original, sem NC sobre NC | Única forma de correção | HIGH | Invariantes próprias (4.3). Uma NC total reverte o pagamento; **re-faturar = novo pagamento** |
| TS-10 | **Efeito da NC** em conta corrente, `Honorario.totalPago`, alerta `HONORARIO_ATRASADO` e KPIs do dashboard | Coerência financeira | MEDIUM-HIGH | `totalPago` é `@Formula` = `SUM(t_pagamento.valor_pago)`: **não vê as NC** sem alteração. Quatro consumidores a atualizar |
| TS-11 | **IUD (45 car.) + LED + QR** no formato exato | Pedido: "formato exato do eFatura" | MEDIUM | Componentes [MEDIUM], posições [LOW]: gerar a partir do manual v11 |
| TS-12 | **Adaptador eFatura** (interface + implementação simulada) com **estado de comunicação por documento** | Decisão fixada | MEDIUM-HIGH | Estados mínimos: `PENDENTE`, `AUTORIZADO`, `REJEITADO`, `CONTINGENCIA`. Configuração por emitente (credenciais/certificado/LED) |
| TS-13 | **Marcação inequívoca de estado fiscal** no PDF e na UI: "não comunicado à DNRE / modo simulado / sem validade fiscal" enquanto o adaptador for simulado | Num sistema de autorização em tempo real, sem autorização não há documento válido (4.5) | LOW | A etiqueta deriva do estado do adaptador, desaparece só com autorização real |
| TS-14 | **PDF no servidor** com todos os elementos (secção 3) | Entregável pedido | MEDIUM-HIGH | O "Termo de Honorários" usa CSS-print no browser, que não serve para anexar a email: capacidade nova no backend |
| TS-15 | **Email SMTP** com PDF, assíncrono **após commit**, com estado (enviado/falhou) e reenvio; cliente sem email não bloqueia a emissão | Primeiro canal SMTP do projeto | MEDIUM-HIGH | Novas variáveis `SMTP_*` **obrigatórias, sem defaults** (`backend/.env.example`, ambos os compose, `deploy.yml`). Email só para documentos fiscais |
| TS-16 | **Pagamento de subscrição** registado por `PLATAFORMA_ADMIN` em `/plataforma` → FR da LexCV ao NIF do escritório | Decisão fixada | MEDIUM-HIGH | `PlatformAdminController` com gate de classe `hasRole('PLATAFORMA_ADMIN')`. O papel tem **zero permissões `scope:action`**. Depende de `Tenant.plano` e de FISC-6 |
| TS-17 | **Listagem, detalhe e download** (PDF/XML) dos documentos fiscais por tenant; reenviar email; ver estado | Operação diária | MEDIUM | Padrão `DataTable` partilhado. RBAC: reutilizar `financeiro:view/edit/manage` (NC = `manage`, sucessor de "Eliminar Lançamentos") **ou** novo scope `faturas:*` (catálogo `t_permission` + `permissions.ts`): decisão para o REQUIREMENTS |
| TS-18 | **Conservação** (sem hard-delete, XML + PDF arquivados) e **auditoria de emissão** (quem/quando) | Obrigação de arquivo (10 anos) | LOW-MEDIUM | Reutilizar `StorageService`/MinIO (`<tenantId>/...`) |
| TS-19 | **Mapeamento de `Pagamento.metodo`** para os meios de pagamento do eFatura | A FR indica o meio de pagamento | LOW | Valores legados em texto livre; lista de códigos a obter do manual |

### Differentiators (valor acima do mínimo)

| ID | Feature | Value Proposition | Complexity | Notes |
|----|---------|-------------------|------------|-------|
| DF-01 | **Pré-visualização e confirmação** antes de emitir (IVA, retenção, total), pois o ato é irreversível | Evita NC por engano | LOW-MEDIUM | Recomendado P1: é o único antídoto barato contra a imutabilidade |
| DF-02 | **Enviar também o XML** do DFE em anexo / download | Pronto para o contabilista e para a migração futura | LOW | Depende de TS-11/TS-14 |
| DF-03 | **Arquivar PDF/XML como `Documento`** na ficha do cliente/processo | Valor jurídico: a fatura aparece junto ao dossiê | MEDIUM | Reutiliza `Documento` + `StorageService` |
| DF-04 | **Resumo mensal** de IVA liquidado e retenções sofridas (CSV) para o contabilista | Apoio à declaração de IVA (dia 20) e ao controlo das retenções | MEDIUM | Reutilizar o export CSV já endurecido contra formula-injection (v2.13). **Não é contabilidade** |
| DF-05 | **Alertas in-app** de email falhado e de documentos pendentes de comunicação | Fecha o ciclo sem email de notificações | LOW-MEDIUM | Reutiliza `Notificacao` (v2.10) |
| DF-06 | **Verificador de lacunas** de numeração por série | Auditabilidade | LOW | Job ou consulta |
| DF-07 | **Checklist de onboarding fiscal** (NIF, morada, regime) no escritório | Reduz bloqueios de TS-02 | LOW | - |
| DF-08 | **Sugestão contextual de retenção** (emitente singular + cliente Empresa), sempre confirmada pelo utilizador | Menos erros | LOW | Mantém a decisão manual (AF-08) |

### Anti-Features (a NÃO construir em v3.0)

| ID | Feature | Why Requested | Why Problematic | Alternative |
|----|---------|---------------|-----------------|-------------|
| AF-01 | Emitir Fatura (01), Recibo (04), Talão de Venda (03), Nota de Débito (06), transporte, devolução | "O eFatura tem esses tipos" | Contradiz "uma FR por pagamento, sem fatura prévia"; mais séries, mais regras | Só FR (02) e NC (05); código de tipo extensível |
| AF-02 | **Cancelamento direto** de DFE na plataforma; edição de documentos emitidos; "rascunho editável" | A DNRE tem operação de cancelamento (limitada a docs não entregues) | Conflita com imutabilidade; prazo do cancelamento desconhecido | Só NC. Reavaliar quando houver ligação real |
| AF-03 | Ligação real: assinatura XML ICP-CV, gestão de certificados, middleware DNRE, **modo contingência**, credenciação e homologação de software | Parece "completar" o eFatura | Fora do marco (sem credenciais/certificado); homologação da LexCV é processo próprio | Modelar estados (`CONTINGENCIA`) e configuração por emitente; não construir fluxos |
| AF-04 | Manifestação do destinatário (Portaria 16/2022); autofaturação (em vigor desde 01-jan-2024) | Existem no ecossistema | Não pertencem a "pagamento recebido = FR" | Diferir |
| AF-05 | Faturação retroativa de pagamentos antigos; permitir "registar pagamento sem fatura" | Atalho/compatibilidade | Retroatividade excluída; pagamento sem fatura abre via de evasão | Legado rotulado; novo pagamento **sempre** gera FR |
| AF-06 | Clientes não residentes, consumidor final sem NIF, regimes especiais (margem, consignação, exportação) | Clientes da diáspora | IVA internacional e identificação distinta; NIF de 9 dígitos não distingue origem | Só NIF CV (FISC-5); sinalizar para revisão |
| AF-07 | Despesas reembolsáveis/provisões para custas na fatura de honorários | Prática comum (Estatuto OACV prevê provisões) | Tratamento fiscal distinto (fora da base de IVA); risco de faturar como honorário o que é conta-cliente | Diferir; ver pergunta 11-A.5 |
| AF-08 | **Motor automático** que decide retenção ou isenção | Menos cliques | Pode produzir conclusão legal errada e imutável | Campo manual com sugestão (DF-08) |
| AF-09 | Taxa de IVA hardcoded | Simplicidade | Muda por lei orçamental | Tabela com vigência (FISC-1) |
| AF-10 | Cobrança online (Vinti4/SISP); faturas de subscrição geradas automaticamente | "SaaS tem recorrência" | Fora de âmbito: pagamento é manual | Registo manual pelo `PLATAFORMA_ADMIN` |
| AF-11 | Contabilidade, **SAF-T (CV)**, declaração periódica de IVA ou DPR | "Já há os dados" | O LexCV emite documentos, não faz contabilidade; SAF-T é entregue **a pedido** da Administração (excerto, LOW) | Export CSV simples (DF-04) |
| AF-12 | Usar o canal de email para notificações, marketing ou lembretes | Canal já existe | Email é **exclusivo de documentos fiscais** (decisão explícita do marco) | Notificações continuam in-app |
| AF-13 | Gerar o número no frontend ou com sequence de BD | Parece simples | Lacunas e duplicados | Contador transacional no servidor (TS-04) |

---

## 7. Feature Dependencies

```
TS-01 Dados fiscais ──> TS-02 Gate ──> TS-03 Emissão atómica ──> TS-04 Séries/numeração
                                            │                         │
                                            ├──> TS-05 IVA ───────────┤
                                            ├──> TS-06 Retenção       │
                                            └──> TS-07 Snapshot imutável ──> TS-08 Bloqueio de apagar
                                                         │
                       TS-09 Nota de Crédito ────────────┘ ──> TS-10 Efeitos (conta corrente, totalPago, alertas, KPIs)
                       TS-11 IUD/QR/LED ──> TS-12 Adaptador simulado ──> TS-13 Marca de estado fiscal
                       TS-14 PDF ──> TS-15 Email ; TS-14 ──> DF-02, DF-03
TS-01/02/03/05/07/11/12/14 ──> TS-16 Subscrição (plataforma como 2.º emitente)
TS-17 Listagem/RBAC depende de TS-07 e do catálogo de permissões

DF-01 Pré-visualização ──enhances──> TS-03
AF-05 (pagamento sem fatura) ──conflicts──> TS-03
AF-02 (cancelamento/edição) ──conflicts──> TS-07, TS-08
```

### Notas de dependência (factos verificados no código)

- **`createPagamento`** (`ResourceController` L3039-3081): grava o `Pagamento` e depois atualiza `ContaCorrente.saldo` num `try/catch` que **só faz `log.warn`** de `DataAccessException`, sem `@Transactional`. A emissão atómica obriga a um caminho transacional novo (serviço), não a remendar este método. O padrão `RecusaTransacional.recusar(...)` (Fase 128) existe precisamente porque um `ResponseEntity` de erro devolvido dentro de `@Transactional` **faz commit**.
- **`Pagamento` não tem `tenant_id`** (isola-se via honorário → processo): o documento fiscal tem de ter `tenant_id` próprio e `UNIQUE(tenant_id, serie, numero)`.
- **`Honorario.totalPago`** é `@Formula` sobre `t_pagamento`: uma NC não o altera. Consumidores a rever: alerta `HONORARIO_ATRASADO` (`AlertasDiariosJob`), KPIs financeiros do dashboard, estado/badges em Financeiro.
- **RBAC**: `financeiro:view/edit/manage`; criar pagamento = `edit`, apagar = `manage` (rótulo "Eliminar Lançamentos Financeiros"). Backend e frontend têm de concordar (`permissions.ts`).
- **`/plataforma`**: `PlatformAdminController` com `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`; o papel tem zero permissões `scope:action`, portanto os endpoints de subscrição usam o gate de papel, não `hasAuthority`.
- **Infra reutilizável:** MinIO `StorageService`, Testcontainers (CI), `@Immutable`, `Notificacao`, fuso `Atlantic/Cape_Verde`, `DataTable`, export CSV endurecido.
- **Infra nova:** SMTP (variáveis obrigatórias, dev precisa de um sink local de email), geração de PDF no servidor, tabelas fiscais (série, documento, linha, comunicação) com migração documentada.

---

## 8. MVP Definition

### Launch With (v3.0)

- [ ] TS-01, TS-02: dados fiscais e gate: sem eles nada emite
- [ ] TS-03, TS-04, TS-05, TS-07, TS-08: núcleo: FR atómica, numeração, IVA, imutabilidade
- [ ] TS-06: retenção opcional (campo manual), por ser o caso real do advogado individual
- [ ] TS-09, TS-10: NC e os seus efeitos financeiros (sem TS-10 a NC deixa o sistema incoerente)
- [ ] TS-11, TS-12, TS-13: IUD/QR, adaptador simulado, marcação de estado fiscal (esta última é inegociável)
- [ ] TS-14, TS-15: PDF + email
- [ ] TS-16: subscrição da plataforma
- [ ] TS-17, TS-18, TS-19: consulta, arquivo/auditoria, mapa de meios de pagamento
- [ ] DF-01: pré-visualização antes de emitir

### Add After Validation (v3.x)

- [ ] DF-02 XML em anexo; DF-03 arquivo no dossiê; DF-05 alertas; DF-06 verificador de lacunas
- [ ] DF-04 resumo mensal IVA/retenções, quando o primeiro escritório pedir

### Future Consideration (marco de ligação real)

- [ ] Credenciais/certificado por emitente, assinatura XML, autorização real, contingência, cancelamento DFE, homologação da LexCV
- [ ] Clientes não residentes, despesas/provisões, SAF-T a pedido

---

## 9. Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| TS-03/04 Emissão atómica + numeração | HIGH | HIGH | P1 |
| TS-05 IVA + TS-06 retenção | HIGH | MEDIUM | P1 |
| TS-07/08 Imutabilidade + bloqueio | HIGH | LOW-MEDIUM | P1 |
| TS-09/10 NC + efeitos | HIGH | HIGH | P1 |
| TS-11/12/13 IUD, adaptador, estado fiscal | HIGH | MEDIUM-HIGH | P1 |
| TS-14/15 PDF + email | HIGH | MEDIUM-HIGH | P1 |
| TS-16 Subscrição plataforma | HIGH | MEDIUM-HIGH | P1 |
| TS-01/02/17/18/19 | HIGH | LOW-MEDIUM | P1 |
| DF-01 Pré-visualização | MEDIUM | LOW-MEDIUM | P1 |
| DF-02/03/05/06 | MEDIUM | LOW-MEDIUM | P2 |
| DF-04 Resumo mensal | MEDIUM | MEDIUM | P2 |
| DF-07/08 | LOW | LOW | P3 |

**Priority key:** P1 = necessário para o lançamento; P2 = a seguir; P3 = futuro.

**Ordem sugerida pelas dependências (insumo para o roadmap):** (1) dados fiscais do emitente + modelo documental/série/imutabilidade; (2) FR atómica do escritório com IVA/retenção e bloqueio de apagar; (3) NC e efeitos financeiros; (4) IUD/QR + adaptador simulado + estado fiscal; (5) PDF + email; (6) subscrição da plataforma (reutiliza tudo, só muda o emitente e o adquirente).

---

## 10. Competitor Feature Analysis

Análise limitada: funcionalidades inferidas de páginas de ajuda de fornecedores (via excertos). Não pesquisei concorrentes de gestão jurídica em Cabo Verde.

| Feature | Vendus / PayTraq / Wisedat | Odoo `l10n_cv_efatura` | Our Approach |
|---------|----------------------------|------------------------|--------------|
| Séries + LED por série, anual | Sim (LED criado na plataforma e registado no software) | Sim (LED com 5 dígitos) | Série `(emitente, ano, tipo)` + LED por registar |
| Tipos 01-05 (+DVE) | Sim | 01-09 | Só 02 e 05 |
| QR/IUD impresso após envio | Sim (ativar no modelo) | Sim | Formato exato, marcado "não comunicado" enquanto simulado |
| Online/offline | Sim, via Middleware DNRE | ONLINE/OFFLINE | Estados modelados, fluxo diferido |
| Certificado digital / credenciação | Obrigatório (ICP-CV) | Idem | Fora de v3.0, configuração por emitente |
| Motivos de isenção | Tabela de códigos | Parametrização | Campo de motivo + regime NORMAL por defeito |
| Email com PDF | Sim | - | PDF por email, exclusivo de documentos fiscais |
| Ligação a gestão de processos/honorários | Não | Não | **Diferenciador do LexCV**: FR nasce do pagamento do honorário, NC reverte a conta corrente do cliente |

---

## 11. Perguntas em aberto

### A. Para o utilizador (decisões de produto)
1. **`Honorario.valorTotal` está com ou sem IVA?** Recomendo "IVA incluído" (FISC-2). Se for "acrescido", o valor do pagamento passa a ser base e o total da FR sobe 15%.
2. **Os PDFs de v3.0 podem ser entregues como "a fatura"?** Não têm validade fiscal sem autorização da DNRE (4.5): os escritórios têm de continuar a emitir faturas válidas no software homologado até haver ligação real. Confirmar a comunicação ao cliente e o texto de TS-13.
3. **Quem é o emitente "LexCV"?** NIF, morada, residente em CV?, sociedade ou pessoa singular?, registo de IVA? Define 5.3 (IVA, retenção, credenciação).
4. **Clientes estrangeiros ou da diáspora** existem na carteira dos escritórios-alvo? (impacta FISC-5/AF-06).
5. **Os escritórios registam como `Pagamento` provisões para despesas/custas?** Se sim, seriam faturados como honorários (erro fiscal).
6. **RBAC:** reutilizar `financeiro:*` ou criar `faturas:*`?

### B. Para o contabilista / DNRE (fiscal): bloqueiam REQ-IDs finos
1. **Retenção na fonte categoria B: 20% (fontes CV) ou 15% (PwC)?** Base com ou sem IVA? Artigo exato do Código do IRPS.
2. **Confirmar** que advogados (singulares e sociedades) estão fora do REMPE (anexo ao art. 2.º da Lei 70/VIII/2014) e em **IVA normal 15%**; confirmar que **não há isenção de IVA** para serviços jurídicos.
3. **Existe isenção por pequeno volume de negócios** fora do REMPE?
4. Sociedades de advogados (IRPC): sem retenção? Particular que é empresário em nome individual: retém?
5. Houve **alteração fiscal no orçamento retificativo de 2026**?
6. Prazo máximo de emissão de **Nota de Crédito**/regularização de IVA; conservação (10 anos) e fundamento.
7. Obrigação de **SAF-T (CV)** para um sistema de faturação (a pedido).

### C. Verificar contra o Manual Técnico v11 (necessita acesso a `efatura.cv`)
1. Posições exatas do IUD e geração do código aleatório/dígito de controlo.
2. Modelo de série/LED (por tipo? por ano?), formato do código de série.
3. Elemento XML da **retenção na fonte** e do **meio de pagamento**; lista de códigos de meios de pagamento.
4. Regras do **cancelamento** (prazo) e da **NC** (referência, limites).
5. Casas decimais e regras de arredondamento.
6. Identificação do software impressa no documento; qualquer exigência de homologação antes da produção.
7. Formato exato do QR e se existe ambiente de testes (homologação) acessível a software de terceiros.

---

## Sources

Todas consultadas só como **excerto de pesquisa** (páginas não abertas; ver secção 0).

**Oficiais (excertos):**
- [efatura.cv: O que é a e-fatura?](https://efatura.cv/docs/about/e-fatura/) · [Documentos Fiscais](https://efatura.cv/docs/manual/documentos-fiscais/) · [Modelo Conceitual](https://efatura.cv/docs/manual/modelo-conceitual/) · [Serviços Eletrónicos (APIs)](https://efatura.cv/docs/manual/servicos-eletronicos/) · [Adesão à Plataforma Eletrónica](https://efatura.cv/docs/guides/adesao-pe/) · [FAQs](https://efatura.cv/docs/faqs/conceitos/) · [Legislação](https://efatura.cv/docs/legislacao/decretolei/)
- [Manual Técnico v7](https://efatura.cv/assets/files/manual-tecnico-v7-216ba5a0643ea57e50cfbbf26b47e746.pdf) · [Manual Técnico v10](https://efatura.cv/assets/files/manual-tecnico-da-fatura-eletronica-v10.0-81ac76da0d05ec36abdb626087cda762.pdf) · [Socialização a contribuintes (mai-2022)](https://efatura.cv/assets/files/socializacao_contribuintes-245bec345d7e4324a74db2b832235d2f.pdf)
- [EGDCV: Regulamentação da fatura eletrónica](https://governacaodigital.gov.cv/regulamentacao-fatura-eletronica/) · [BO I Série n.º 128, 12-nov-2020 (DL 79/2020)](https://maa.gov.cv/images/BO_12-11-2020_128-desbloqueado.pdf)
- [Código do IRPS, Lei 78/VIII/2014 (BO n.º 81)](https://boe.incv.cv/Bulletins/Download/1952) · [Lei 70/VIII/2014 REMPE, alteração de 2020](https://consultores.cv/wp-content/uploads/simple-file-list/REMPE/Lei-no-70-VIII-2014-REMPE-Alterac%E2%95%9Eo-mais-recente.pdf) · [Porton di Nos Ilhas: Requisitos do REMPE](https://portondinosilhas.gov.cv/portonprd/porton.igrp_portal.load_doc?p=AAB8CFBCAEC4CDCAC7C8C4CEC8C6C9) · [Lei 69/X/2025 (OE 2026)](https://boe.incv.cv/Bulletins/View?id=89137)
- [Código do IVA CV (Lobo Vasques, 2022)](https://lobocarmona.com/storage/74/CIVA_Cabo-Verde-12.01.2022.pdf) · [Lei 21/VI/2003 (MF)](https://www.mf.gov.cv/documents/54571/591130/IVA.pdf)
- [OACV: sugere novo regime fiscal para profissionais liberais](https://oacv.cv/index.php?Itemid=161&catid=8&id=50%3Aoacv-sugere-criacao-de-um-novo-regime-fiscal-aplicavel-aos-profissionais-liberais&option=com_content&view=article) · [Estatuto da OACV (Lei 91/VI/2006)](https://www.oacv.cv/images/phocagallery/PDF/legislacao/estoacv.pdf)

**Consultoras e escritórios:**
- [PwC Tax Summaries CV: Other taxes (IVA 15%)](https://taxsummaries.pwc.com/cabo-verde/corporate/other-taxes) · [Withholding taxes](https://taxsummaries.pwc.com/cabo-verde/corporate/withholding-taxes) · [Individual: taxes on personal income](https://taxsummaries.pwc.com/cabo-verde/individual/taxes-on-personal-income) · [Flash OE 2025](https://www.pwc.pt/pt/pwcinforfisco/flash/cabo-verde/cabo-verde-orcamento-do-estado-2025.html) · [Flash OE 2026](https://www.pwc.pt/pt/pwcinforfisco/flash/cabo-verde/cabo-verde-orcamento-estado-2026.html)
- [Miranda Advogados: Regime Jurídico da Fatura Eletrónica](https://www.mirandalawfirm.com/pt/conhecimento-media/publications/alerts/aprovado-regime-juridico-da-fatura-eletronica-e-dos-documentos-eletronicos-fiscalmente-relevantes) · [VPQ: Portaria 16/2022](https://www.vpqadvogados.com/xms/files/RECURSOS/Newsletters/Regulamentacao_do_regime_juridico_que_institui_a_fatura_eletronica_e_os_documentos_eletronicos_fiscalmente_relevantes_-_Legal_Alert_VPQ_-PT-.pdf)
- [consultoria.cv: Guia REMPE e TEU](https://consultoria.cv/en/guia-simplificado-do-rempe-e-teu-em-cabo-verde/) · [REMPE vs Contabilidade Organizada](https://consultoria.cv/en/rempe-vs-contabilidade-organizada-o-regime-fiscal-em-cabo-verde/)
- [Câmara do Comércio: Manual de Faturas (CIVA 32.º/5, 36.º)](https://www.camara.cv/wp-content/uploads/2018/12/MANUAL-DE-FATURAS-1.0-FINAL.pdf) · [INOVE: Faturação eletrónica e SAF-T CV](https://inove.cv/nota-tecnica-faturacao-eletronica-e-standard-audit-file-for-tax-purposes-cabo-verde-saft-cv-tudo-o-que-deve-saber/)

**Fornecedores homologados (funcionalidades, LED, códigos):**
- [Wisedat: Regras de emissão](https://www.wisedat.pt/regras-de-emissao-de-faturas-cabo-verde/) · [Comunicação de DFE](https://www.wisedat.pt/kb/emissao-dfe-cabo-verde/) · [Motivos de isenção (LOW)](https://www.wisedat.pt/motivos-de-isencao-de-iva-cabo-verde/) · [REMPE](https://www.wisedat.pt/kb/rempe-cabo-verde/) · [Tudo sobre a E-Fatura CV](https://www.wisedat.pt/tudo-o-que-deve-saber-sobre-a-e-fatura-cabo-verde/)
- [PayTraq: configurar DNRE eFatura](https://help.paytraq.cv/como-configurar-para-trabalhar-com-dnre-efatura/) · [Odoo l10n_cv_efatura](https://apps.odoo.com/apps/modules/16.0/l10n_cv_efatura) · [Edicom (QR/IUD)](https://edicom.pt/blog/como-cumprir-fatura-eletronica-cabo-verde) · [Primavera](https://roa.primaverabss.com/pt/pagina/fatura-eletronica/) · [Vendus CV: retenção na fonte](https://www.vendus.cv/blog/retencao-fonte/)

**Descartados por contaminação/baixa fiabilidade:** [EY VAT Guide 2026](https://www.ey.com/en_gl/technical/tax-guides/worldwide-vat-gst-and-sales-tax-guide) (19%/9% não é CV); blog Vendus CV sobre "artigo 53.º" (regime português); [PayPro Global SaaS](https://payproglobal.com/saas-sales-tax/cape-verde/) (apenas LOW, blog de fornecedor).

**Código do projeto verificado:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (L3039-3081, L3156-3183), `models/Pagamento.java`, `models/Honorario.java`, `models/Tenant.java`, `controllers/RecusaTransacional.java`, `controllers/PlatformAdminController.java`.

---
*Feature research for: faturação eletrónica eFatura CV em plataforma de gestão jurídica multi-tenant (LexCV v3.0)*
*Researched: 2026-10-04*
