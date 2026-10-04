# Stack Research

**Domain:** Faturação eletrónica conforme eFatura de Cabo Verde (DNRE) num backend Spring Boot multi-tenant
**Milestone:** v3.0 "Faturação Eletrónica (eFatura CV)" — apenas o que é NOVO face a `backend/pom.xml`
**Researched:** 2026-10-04
**Confidence:** MÉDIA no global. ALTA nas bibliotecas (metadados do Maven Central, BOM do Spring Boot 3.4.1 e experiências executadas). MÉDIA no modelo técnico eFatura: o domínio `efatura.cv` e subdomínios estavam bloqueados pelo proxy de saída deste ambiente, por isso o conteúdo oficial foi lido (a) por excertos de pesquisa das páginas oficiais e (b) diretamente nos ficheiros do pacote oficial DNRE (XSD, XMLs de exemplo, "Read Me", "XML Fields Map") copiados em repositórios públicos. Nada foi contactado nos servidores da DNRE.

Escala de confiança usada: **ALTA** = lido no artefacto primário ou executado localmente; **MÉDIA** = excerto da página oficial ou fonte terciária coerente com outra independente; **BAIXA** = fonte única/inferência.

---

## 1. Veredito em 8 linhas

1. A eFatura CV é **REST sobre HTTPS com XML** (DFE, namespace `urn:cv:efatura:xsd:v1.0`), **não SOAP**. Envio = `POST` de um ZIP (Deflate) com `{IUD}.xml` para `https://services.efatura.cv/v1/…`. Autorização é **síncrona**. Autenticação OAuth2/OIDC (Keycloak `iam.efatura.cv`, realm `taxpayers`).
2. O XML tem de ser **assinado XAdES-BES (RSA-SHA256, c14n 1.0, SHA-256)** com certificado ICP-CV do emitente. No XSD a assinatura é `minOccurs="0"`, pelo que um XML **não assinado valida contra o XSD oficial** (verificado) — é isto que torna possível a implementação simulada da v3.0 sem certificado.
3. O pacote XSD oficial é público (20 ficheiros, versão técnica 2024-05-27). **Fazer vendor dos XSD em `backend/` e gerar as classes JAXB no build** (`org.jvnet.jaxb:jaxb-maven-plugin:4.0.16`). Verificado: 137 classes, round-trip e validação OK.
4. **Adicionar na v3.0** (5 dependências + 1 plugin + 1 de teste): `jakarta.xml.bind-api` e `jaxb-runtime` (ambos geridos pelo BOM), `jaxb-maven-plugin 4.0.16`, `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.87`, `com.google.zxing:core:3.5.4`, `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`; teste: `greenmail-junit5:2.1.14`.
5. **Não adicionar agora**: biblioteca de assinatura, cliente HTTP, cliente OAuth2. Ficam atrás das interfaces `AssinadorDfe` / `EfaturaGateway`. Biblioteca de assinatura já pré-validada para o "swap": `xades4j 2.4.1` (spike executado: assina, valida contra o XSD e é verificada pelo JDK).
6. A implementação simulada deve ser **estruturalmente distinguível** de produção: o XSD define `RepositoryCode` 1=Principal, 2=Homologação, 3=Teste, e esse dígito vai dentro do IUD. Simulada = `3`.
7. **Risco n.º 1 do marco (não é técnico):** segundo a página oficial, um PDF enviado por email **não é fatura eletrónica**; a validade legal vem da assinatura + autorização da DNRE em tempo real. Um PDF "Fatura-Recibo" enviado a clientes reais com a ligação simulada não tem validade fiscal. Ver §10 e P1 em §12.
8. O "swap" para a ligação real **não é só configuração**: exige homologação do software (LexCV como ISV), credenciais do transmissor, e custódia do certificado ICP-CV de cada emitente. São bloqueios organizacionais, listados em §12.

---

## 2. Modelo técnico eFatura CV (o que ficou estabelecido)

### 2.1 Protocolo, endpoints e autenticação

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Tecnologia | REST sobre HTTP(S); endpoints no formato `https://services.efatura.cv/{versão}/{recurso}` (ex.: `v1`) | https://efatura.cv/docs/manual/servicos-eletronicos/ (excerto) | MÉDIA |
| Recursos conhecidos | `dfe`/`dfes` (pesquisar/emitir DFE) e `leds` (pesquisar/registar/editar LED). Caminhos por omissão usados por SDK de terceiros: `/v1/dfe` e `/v1/event` | excerto acima; https://github.com/Kowts/efatura-cv-php (`EfaturaConfig`) | MÉDIA (a grafia `dfe` vs `dfes` não ficou resolvida) |
| Emissão | Gerar XML por DFE, comprimir em ZIP com algoritmo **Deflate**, `HTTP POST`; o nome do XML dentro do ZIP é o **IUD** do DFE | excerto de https://efatura.cv/docs/manual/servicos-eletronicos/ ; SDK Kowts usa `{IUD}.xml` | MÉDIA |
| Processamento | **Síncrono**: o sistema do contribuinte espera o fim da validação/autorização. Fases: estrutura XML, assinatura (recalcula o hash e compara com `SignatureValue`), tipos de dados, NIF, códigos, taxas de imposto | https://efatura.cv/docs/manual/modelo-conceitual/ e servicos-eletronicos (excertos) | MÉDIA |
| Autenticação | OAuth2/OIDC. Descoberta em `https://iam.efatura.cv/auth/realms/taxpayers/.well-known/openid-configuration`; lista de APIs (Swagger) em `https://services.efatura.cv/api-list/`. Fornecedores de software configuram "Client ID" e "Client Secret" | README de https://github.com/kriolos/kriolos-efatura (módulo `clientapi`); KB Wisedat (excerto) | MÉDIA para a existência; **BAIXA para o tipo de grant** (não verificado) |
| Dois canais | (a) **Plataforma direta** (OAuth + repositório); (b) **Middleware** disponibilizado gratuitamente pela administração fiscal, instalado na rede do contribuinte (chave de transmissor) | https://efatura.cv/docs/manual/ecosistema/ (excerto); https://github.com/Kowts/efatura-cv-php `docs/arquitectura.md` | MÉDIA |
| Ambientes | Existe **ambiente de homologação**; o contribuinte treina nele e depois segue instruções para passar automaticamente a produção. Anfitriões vistos no índice de pesquisa: `tst.efatura.cv`, `dev.efatura.cv` (docs). **URL base da API de teste não verificado** | https://efatura.cv/docs/guides/adesao-pe/ (excerto) | MÉDIA (existência) / BAIXA (URLs) |

### 2.2 Formato do documento (DFE)

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Esquema | XML, `targetNamespace="urn:cv:efatura:xsd:v1.0"`. Raiz `<Dfe Version="1.0" Id="{IUD}" DocumentTypeCode="{n}">`. `Id` é `xs:ID` com `length=45` | XSD oficial (pacote 2024-05-27): https://github.com/Kowts/efatura-cv-php/tree/main/resources/xsd/efatura/2024-05-27 | ALTA para o conteúdo do pacote; MÉDIA quanto a ser a versão mais recente |
| Pacote | 2 pontos de entrada (`EnvelopedSignature.xsd`, `InternallyDetachedSignature.xsd`) + 18 XSD em `common/` (inclui `W3C_XMLDSig`, `ETSI_XAdESv132`, `ETSI_XAdESv141`, ISO países/moedas, UN/ECE `PaymentMeansCode_D19B`, `TaxExemptionReason`) + um XML de exemplo por tipo + "Read Me.txt" (histórico) + "XML Fields Map.txt" (campos e regras). Localização oficial: https://efatura.cv/docs/xsd (não aberta) | listagem do repositório; cópia independente do XSD de entrada em https://github.com/kriolos/kriolos-efatura (idêntica, só difere em espaços) | ALTA (verificada em 2 cópias independentes) |
| Estrutura | `Dfe` = [`IsSpecimen`?] + um de {`Invoice`,`InvoiceReceipt`,`SalesReceipt`,`Receipt`,`CreditNote`,`DebitNote`,`ReturnNote`,`RegistrationNote`,`Transport`} + `Transmission` + `RepositoryCode` + `ds:Signature`. Existe ainda a raiz `Event` (código 99) | XSD `CV_EFatura_MainTypes_v1.0.xsd` | ALTA |
| Fatura-Recibo (código 2) | Cabeçalho (`LedCode`, `Serie`, `DocumentNumber`, `InnerDocumentNumber`?, `IssueDate`, `IssueTime`), `EmitterParty`, **`ReceiverParty` obrigatório**, `PaymentParty`?, `Lines`, `Totals`, `References`?, `Payments`? (lista de `Payment` com `PaymentMeansCode`/`PaymentAmount`/`PaymentDate`), `Delivery`?, `Note`? | `CV_EFatura_InvoiceReceipt_v1.0.xsd` + "XML Fields Map.txt" | ALTA |
| Nota de Crédito (código 5) | Como a FRE mas com **`IssueReasonCode` obrigatório** (valores admitidos para NCE: `2`,`3`,`6`,`7`,`8`,`9` = Art.º 65 n.º x do CIVA; `IN` = indisponível; `DRP` = desconto por rappel + `RappelPeriod`), sem `Payments`. `References/Reference/FiscalDocument` guarda o **IUD do documento original** (`@IsOldDocument` para documentos pré-eFatura). No XSD `References` é opcional; se a regra de negócio o exige não ficou verificado | `CV_EFatura_CreditNote_v1.0.xsd`, `CV_EFatura_Types_v1.0.xsd` (`stIssueReasonCode`), XML de exemplo `5 CreditNote.xml` | ALTA (XSD) / BAIXA (regras de plataforma) |
| `Transmission` (obrigatório) | `IssueMode` (1 Online, 2 Offline, 3 Off), `TransmitterTaxId`, **`Software{Code (≤10), Name, Version}`**, `Contingency` só se `IssueMode≠1` | "XML Fields Map.txt" | ALTA |
| Restrições de formato | `Serie` 1–20 caracteres `[A-Za-z0-9]+([_-][A-Za-z0-9]+)*`; `LedCode` 1–99999; `DocumentNumber` 1–999 999 999; montantes até **5 casas decimais**, percentagens até 3, quantidades até 6; datas `AAAA-MM-DD`, hora `HH:MM:SS`; `Note` 10–500; `Party.Name` 3–150; `AddressDetail` ≤100 (obrigatório em `Address`, que é obrigatório exceto no TVE); `Item.Description` 1–300 e `Item.EmitterIdentification` ≤50 obrigatórios; `Quantity@UnitCode` obrigatório | XSD `Types`/`Elements` + "XML Fields Map.txt" | ALTA |
| Evolução do esquema | 2020-08 versão inicial; 2021-08 namespace fixado e decimais 2→5; 2022-01 série até 20; **2022-02-19 `WithholdingTaxTotalAmount`**; 2023-12 autofaturação (`SelfBilling`); **2024-05-27 último** (`RappelPeriod`/`DRP`). Boolean passou a `false\|true` | "Read Me.txt" do pacote | ALTA |
| Manual técnico | Versões v7, v9, v10 e **v11 referida como a mais recente**; v10/v11 trouxeram IUD com 45 caracteres, LED até 5 dígitos, remoção do modo de emissão do IUD e dígito de controlo por fórmula de Luhn | excertos de https://efatura.cv/docs/manual/ ; PDF v7 https://efatura.cv/assets/files/manual-tecnico-v7-216ba5a0643ea57e50cfbbf26b47e746.pdf ; PDF v10 https://efatura.cv/assets/files/manual-tecnico-da-fatura-eletronica-v10.0-81ac76da0d05ec36abdb626087cda762.pdf (nenhum PDF foi aberto) | MÉDIA |

### 2.3 Assinatura digital

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Obrigatoriedade | Obrigatória por especificação ("Signature {1}" no mapa de campos), embora o XSD a declare `minOccurs="0"` | "XML Fields Map.txt"; `EnvelopedSignature.xsd` | ALTA |
| Perfil | **XAdES-BES, enveloped**: `ds:Signature` como **último filho de `Dfe`**. Alternativa "internally detached" (raiz nova com `ds:Signature` irmã de `Dfe`/`Event`) | XSD `EnvelopedSignature.xsd` / `InternallyDetachedSignature.xsd` | ALTA |
| Algoritmos | Canonicalização `http://www.w3.org/TR/2001/REC-xml-c14n-20010315`; `rsa-sha256`; digests SHA-256; `KeyInfo/X509Data/X509Certificate`; 2.ª `Reference` a `SignedProperties` (`Type=http://uri.etsi.org/01903#SignedProperties`); `xades` v1.3.2 | XML de exemplo `2 InvoiceReceipt.xml`; excerto do manual ("SHA 256 bits", elemento `Signature`) | ALTA (exemplo) |
| Certificado | Cadeia **ICP-CV**. Emitente pessoa coletiva: certificado qualificado de **Representação Coletiva / Selo Eletrónico**. Pessoa singular: CNI (com PIN) ou assinatura qualificada individual. **Transmissor** pessoa coletiva: certificado qualificado de **Autenticação Web (SSL-EV)**. Emitido via portal (menu "Certificado Digital"); fontes de terceiros referem a SISP como emissora | https://efatura.cv/docs/guides/adesao-pe/ (excerto); Primavera/Wisedat (excertos) | MÉDIA |
| Detalhe NÃO resolvido | O exemplo oficial referencia `#IUD` **sem** transformação `enveloped-signature` (os valores do exemplo são fictícios); a assinatura real precisa dessa transformação para ser válida. Se a DNRE exige esta ou outra combinação (e `Id`s fixos como `EmitterPartySignatureId`) não ficou verificado | exemplo vs. spike em §11 | BAIXA |

### 2.4 IUD (Identificador Único do DFE)

45 caracteres, validado por `stDfeId` (regex oficial `CV(\d)(\d{2})(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])([1-9]\d{8})\d{27}`).

| Posição | Campo | Tamanho |
|---------|-------|---------|
| 1–2 | `CV` | 2 |
| 3 | Repositório: **1=Principal, 2=Homologação, 3=Teste** | 1 |
| 4–9 | Data de emissão `AAMMDD` | 6 |
| 10–18 | NIF do emitente (primeiro dígito 1–9) | 9 |
| 19–23 | LED | 5 |
| 24–25 | Tipo de documento (`01`…`09`) | 2 |
| 26–34 | Número do documento | 9 |
| 35–44 | Código aleatório | 10 |
| 45 | Dígito de controlo (Luhn) | 1 |

- Estrutura e tamanhos: **ALTA** (regex do XSD oficial + SDKs). Os `01`…`09` e o significado de repositório vêm de `stDocumentTypeCode`/`stRepositoryCode` (XSD).
- **Luhn**: calculado sobre os **42 dígitos** entre `CV` e o dígito final; `DV = (10 - soma mod 10) mod 10` com duplicação alternada a partir da direita. Verificado: o IUD do exemplo oficial dá DV=4, igual ao último dígito. Fonte da fórmula: excerto do manual v10 ("fórmula de Luhn") + `Iud.php` do SDK https://github.com/Kowts/efatura-cv-php. O próprio SDK declara não existirem vetores de teste oficiais. **MÉDIA.**
- O IUD é construído **pelo sistema emissor** (não é atribuído pela plataforma) e é o `Id` do XML e o nome do ficheiro no ZIP.
- Códigos de tipo (**ALTA**, comentário do XSD + 2 fontes concordantes): 1 FTE Fatura, **2 FRE Fatura-Recibo**, 3 TVE Talão de Venda, 4 RCE Recibo, **5 NCE Nota de Crédito**, 6 NDE Nota de Débito, 7 DTE Transporte, 8 DVE Devolução, 9 NLE Nota de Lançamento.

### 2.5 DFA (representação impressa) e QR code

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Termo | O antigo "DFEA" chama-se **DFA — Documento Fiscal Auxiliar**; deve conter o IUD e, nos modos Online e Offline, um **QR code** | excerto do manual (modelo conceitual / v10) | MÉDIA |
| Conteúdo do QR | URL `https://services.efatura.cv/v1/dfe/view/{IUD}`. O SDK Kowts usa por omissão `https://pe.efatura.cv/dfe/view` — **conflito não resolvido**; tornar o prefixo configurável | excerto do manual; `EfaturaConfig.php` | MÉDIA / conflito |
| Consulta pública | O DFE pode ser consultado **sem autenticação apenas durante 1 hora** após a emissão; depois exige autenticação. Há também consulta por IUD em https://efatura.cv/consulta | excerto do manual | MÉDIA |
| PDF por email | "Documentos enviados em PDF por email" **não constituem fatura eletrónica** | https://efatura.cv/docs/faqs/ e https://efatura.cv/docs/about/e-fatura/ (excertos) | MÉDIA |
| Conteúdo mínimo do DFA | Lista exata de elementos obrigatórios do DFA **não verificada** (manual v11 não aberto) | — | BAIXA |

### 2.6 Impostos, retenção e isenções (como são CODIFICADOS; regras materiais não pesquisadas aqui)

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Tipos de imposto | `TaxTypeCode` ∈ {`NA`, `IVA`, `IS`, `IR`} (`TEU` removido em 2022-02-19). Por linha, **no máximo 2** elementos `Tax` | XSD `stTaxTypeCode`; "Read Me.txt" | ALTA |
| Cada `Tax` | Exatamente uma entre `TaxPercentage` (0–100, 3 casas) / `TaxAmount` / `TaxExemptionReasonCode`; `NA` exige motivo | "XML Fields Map.txt"; SDK Kowts (`DocumentValidator`) | ALTA |
| Retenção na fonte | Modelada como imposto `IR` por linha; `Totals/WithholdingTaxTotalAmount` = soma dos `IR`, **subtraída a `PayableAmount`** | "Read Me.txt" (2022-02-19) | ALTA |
| Motivos de isenção | **21 códigos** (`TaxExemptionReasonCode` 1–21) com artigos do CIVA, p. ex. 3 = isenções internas (Art.º 9.º), 5 = outras isenções (Art.º 14.º), 20 = IVA sem direito a dedução (REMPE), 21 = isenções do OE | `CV_EFatura_TaxExemptionReason_v1.0.xsd` | ALTA |
| Taxas e aplicabilidade a serviços jurídicos | **Não pesquisado nesta dimensão** (pertence à pesquisa de funcionalidades). Este documento só confirma COMO se codifica | — | — |

### 2.7 Quem é quem, software e base legal

| Facto | Detalhe | Fonte | Confiança |
|-------|---------|-------|-----------|
| Emitente vs Transmissor | O XML distingue o **emitente** (`EmitterParty`, assina) do **transmissor** (`Transmission/TransmitterTaxId`, comunica). Permite que um SaaS comunique em nome de vários emitentes. Para a LexCV: transmissor = NIF do operador da plataforma, em ambos os casos de emissão | XSD/mapa de campos; guia de adesão (papéis de certificado) | MÉDIA |
| Software | Cada DFE declara o software (`Code`, `Name`, `Version`). Os contribuintes têm de emitir com **software aprovado/acreditado pela administração fiscal**; existe lista de fornecedores homologados (Vendus, Wisedat, Primavera, …). Procedimento de homologação para um novo ISV e atribuição de `Software.Code`: **não verificados**; contacto indicado nas fontes: helpdesk@efatura.cv | excertos efatura.cv/FAQs, Wisedat, Vendus | MÉDIA / BAIXA |
| Alternativas oficiais | **Emissor Público** (aplicação gratuita em https://pe.efatura.cv para pequenos contribuintes) e **Middleware** gratuito | https://efatura.cv/docs/manual/ecosistema/ (excerto) | MÉDIA |
| Modos / contingência | Online, Offline (sem ligação à DNRE mas com sistema próprio), Off (sem sistema). Em contingência os DFE têm de ser transmitidos em **5 dias úteis** | https://efatura.cv/docs/manual/modelo-conceitual/ (excerto) | MÉDIA |
| Base legal | **Decreto-Lei n.º 79/2020** (regime jurídico da fatura eletrónica; fontes divergem na data: 12 de novembro vs 28 de dezembro de 2020), **Portaria n.º 62/2020** de 16-12-2020 (logótipo), **Portaria n.º 56/2023** (autofaturação, desde 01-01-2024). Obrigação faseada: 2021-07 importadores, 2021-09 grandes contribuintes, 2022-01 médios, 2022-07 REMPE | Miranda Advogados, Edicom, Vatupdate, índice do BO em mf.gov.cv (excertos; PDFs não abertos) | MÉDIA |

---

## 3. Stack recomendada para a v3.0

### 3.1 Core (novo no `pom.xml`)

| Tecnologia | Versão | Finalidade | Porquê |
|------------|--------|-----------|--------|
| `jakarta.xml.bind:jakarta.xml.bind-api` | **4.0.2** (gerida pelo BOM 3.4.1) | API JAXB (namespaces `jakarta.*`) | Hoje só chega por Hibernate com `scope=runtime` (verificado no POM do hibernate-core 6.6.4) — o código gerado precisa dela em **compile**. Sem `<version>`. |
| `org.glassfish.jaxb:jaxb-runtime` | **4.0.5** (gerida) | Implementação JAXB para marshal/unmarshal | Já presente transitivamente; declarar explicitamente para a fixar. |
| `org.jvnet.jaxb:jaxb-maven-plugin` | **4.0.16** (2026-06) | Gerar classes a partir dos XSD da DNRE | Linhagem Jakarta (4.x só suporta JAXB 4); Maven ≥3.1, JDK ≥11, testado em 11/17/21 (README de https://github.com/highsource/jaxb-tools). Ver §10: não definir `generatePackage` global. |
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | **1.1.87** (2026-09-26) | HTML/CSS → PDF no servidor (DFA) | Puro Java (PDFBox 3.0.7), **sem binários nativos**, LGPL-2.1+, Java 8+, suporta PDF/A e PDF/UA (README), manutenção ativa. Ver comparação em §6. Excluir `commons-logging`. |
| `com.google.zxing:core` | **3.5.4** (2025-11) | QR code do IUD | Apache-2.0, padrão de facto. Só o módulo `core` (ver §7). Projeto em modo de manutenção, estável. |
| `org.springframework.boot:spring-boot-starter-mail` | **3.4.1** (gerida) | Envio SMTP | Traz `spring-context-support` 6.2.1 e `org.eclipse.angus:jakarta.mail:2.0.3`. Propriedades `spring.mail.*` verificadas nos metadados do Boot 3.4.1. |
| `org.springframework.boot:spring-boot-starter-thymeleaf` | **3.4.1** (gerida → Thymeleaf 3.1.3.RELEASE) | Templates XHTML do PDF e do corpo do email | Um só motor para PDF e email, escaping automático. Todos os controladores do projeto são `@RestController`, logo o `ViewResolver` autoconfigurado é inofensivo (verificado). |

### 3.2 Bibliotecas de apoio

| Biblioteca | Versão | Finalidade | Quando usar |
|-----------|--------|-----------|-------------|
| `java.util.zip` (JDK) | — | ZIP/Deflate com `{IUD}.xml` | No `EfaturaGateway` real; o gateway simulado pode guardar o mesmo ZIP para provar o formato. |
| `java.security.SecureRandom` (JDK) | — | Código aleatório de 10 dígitos do IUD | Sempre. `java.util.Random` dispara FindSecBugs `PREDICTABLE_RANDOM` (padrão confirmado no jar `findsecbugs-plugin-1.14.0`). |
| `MessageDigest` SHA-256 (JDK) | — | Impressão digital do XML canónico guardado | Para provar imutabilidade do documento fiscal (independente da assinatura XAdES). |
| `javax.xml.validation` (JDK) | — | Validar o XML contra o XSD antes de "comunicar" | Sempre (ver §10 sobre carregar XSD dentro do fat jar). |
| Fontes TTF livres (p. ex. Liberation Sans / DejaVu, em `src/main/resources/fonts/`) | — | Embutir fontes no PDF | Recomendado: PDF/A exige fontes embutidas e a imagem de runtime é Alpine. Sem fontes embutidas o PDF usa Helvetica/Times padrão (verificado: acentos portugueses OK em WinAnsi). |

### 3.3 Ferramentas de desenvolvimento e teste

| Ferramenta | Versão | Finalidade | Notas |
|-----------|--------|-----------|-------|
| `com.icegreen:greenmail-junit5` | **2.1.14** (2026-09), Apache-2.0 | SMTP embebido para testes | Compatível com Jakarta Mail 2.1/Angus 2.0.x (o BOM fixa 2.0.3). Traz `junit:junit` 4 em compile transitivo — apenas teste. Alternativa: simular `JavaMailSender` em testes unitários. |
| Servidor SMTP de desenvolvimento (Mailpit ou equivalente) | — | Ver o email em desenvolvimento local | Não pesquisado em detalhe; qualquer sumidouro SMTP serve (porta 1025). |
| XMLs de exemplo da DNRE (`1 Invoice…` a `99 Event`) | pacote 2024-05-27 | Fixtures de teste "ouro" | Validam o XSD vendorizado; fazer o download a partir de efatura.cv e comparar com a cópia pública. Confirmar termos de redistribuição (ambos os SDKs de terceiros incluem-nos; o NOTICE do SDK Kowts diz que se destinam só a validação local). |
| `spotbugs-exclude.xml` | existente | Excluir código gerado | Ver §9. |

---

## 4. Pré-selecionado para o "swap" real (NÃO adicionar na v3.0)

| Necessidade futura | Escolha pré-validada | Alternativas | Porquê / ressalvas |
|--------------------|----------------------|--------------|--------------------|
| Assinatura XAdES-BES | **`com.googlecode.xades4j:xades4j:2.4.1`** (2026-05; **LGPL-3.0** pelo repositório; Java 11+). Traz Apache Santuario `xmlsec 4.0.4`, BouncyCastle, Guice 7.0.0 e `jakarta.xml.bind-api` 4.0.5 | (a) **EU DSS 6.5** (`dss-xades`, LGPL-2.1, Java 8+): completo (eIDAS, validação) mas dezenas de módulos — excessivo para um perfil. (b) **JSR-105 do JDK + XAdES manual**: zero dependências, mas obriga a construir `QualifyingProperties` à mão. (c) Apache Santuario `xmlsec 4.0.4` sozinho (Apache-2.0): só XML-DSig, sem construtor XAdES | **Spike executado** (§11): assinou o FRE de exemplo, estrutura equivalente à da DNRE, XML assinado **continua válido no XSD** e foi verificado independentemente pelo JSR-105 do JDK. Rever a licença LGPL-3.0 antes de adotar. |
| Chave privada do emitente | `KeyStore` PKCS#12 do JDK | HSM / cofre externo | A custódia por escritório é a decisão difícil (P2, §12). Nenhuma biblioteca nova. |
| Cliente HTTP | **Spring `RestClient`** (já em `spring-web` 6.2.1) com `JdkClientHttpRequestFactory` (JDK `HttpClient`) | `HttpComponentsClientHttpRequestFactory` + `httpclient5` 5.4.1 (gerido) se for preciso pool/proxy | Zero dependências novas. mTLS (se o transmissor SSL-EV o exigir; **não verificado**) faz-se com `SSLContext` a partir de `KeyStore` do JDK. |
| Token OAuth2 | Classe própria (~40 linhas): `POST` ao endpoint de token, cache por emitente até `expires_in` | `spring-security-oauth2-client` | Credenciais são por emitente/ISV (dinâmicas), não por `ClientRegistration` estática; evita autoconfiguração nova sobre o `SecurityConfig` JWT-em-cookie. |

---

## 5. Instalação (`backend/pom.xml`)

```xml
<properties>
    <!-- existentes: java.version, jjwt.version -->
    <openhtmltopdf.version>1.1.87</openhtmltopdf.version>
    <zxing.version>3.5.4</zxing.version>
    <jaxb-maven-plugin.version>4.0.16</jaxb-maven-plugin.version>
    <greenmail.version>2.1.14</greenmail.version>
</properties>

<dependencies>
    <!-- JAXB: versões geridas pelo spring-boot-starter-parent 3.4.1 (4.0.2 / 4.0.5) -->
    <dependency>
        <groupId>jakarta.xml.bind</groupId>
        <artifactId>jakarta.xml.bind-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.glassfish.jaxb</groupId>
        <artifactId>jaxb-runtime</artifactId>
    </dependency>

    <!-- PDF: sem nativos. commons-logging colide com spring-jcl -> excluir (verificado: funciona) -->
    <dependency>
        <groupId>io.github.openhtmltopdf</groupId>
        <artifactId>openhtmltopdf-pdfbox</artifactId>
        <version>${openhtmltopdf.version}</version>
        <exclusions>
            <exclusion>
                <groupId>commons-logging</groupId>
                <artifactId>commons-logging</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <!-- QR: apenas core (o módulo javase traz jcommander + jai-imageio-core para 15 linhas de código) -->
    <dependency>
        <groupId>com.google.zxing</groupId>
        <artifactId>core</artifactId>
        <version>${zxing.version}</version>
    </dependency>

    <!-- Email + templates -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>

    <!-- Teste -->
    <dependency>
        <groupId>com.icegreen</groupId>
        <artifactId>greenmail-junit5</artifactId>
        <version>${greenmail.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build><plugins>
    <plugin>
        <groupId>org.jvnet.jaxb</groupId>
        <artifactId>jaxb-maven-plugin</artifactId>
        <version>${jaxb-maven-plugin.version}</version>
        <executions><execution><goals><goal>generate</goal></goals></execution></executions>
        <configuration>
            <schemaDirectory>src/main/resources/xsd/efatura</schemaDirectory>
            <schemaIncludes><include>EnvelopedSignature.xsd</include></schemaIncludes>
            <bindingDirectory>src/main/resources/xsd/bindings</bindingDirectory>
            <bindingIncludes><include>*.xjb</include></bindingIncludes>
            <args><arg>-no-header</arg></args>
            <!-- NÃO definir <generatePackage>: ver §10 (colisão de ObjectFactory entre XAdES 1.3.2 e 1.4.1) -->
        </configuration>
    </plugin>
</plugins></build>
```

Ficheiro de bindings (verificado; o `schemaLocation` é relativo ao `.xjb`):

```xml
<jaxb:bindings version="3.0" xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb" xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <jaxb:bindings schemaLocation="../efatura/EnvelopedSignature.xsd" node="/xs:schema">
    <jaxb:schemaBindings><jaxb:package name="com.lexcv.fiscal.efatura.xsd"/></jaxb:schemaBindings>
  </jaxb:bindings>
</jaxb:bindings>
```

Configuração (convenção do projeto: `application.yml` lê tudo de variáveis de ambiente, sem defaults):

```yaml
spring:
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true   # default é false -> permitiria downgrade sem TLS
      mail.smtp.connectiontimeout: 5000   # default do Angus Mail é INFINITO
      mail.smtp.timeout: 10000
      mail.smtp.writetimeout: 10000
      # mail.smtp.ssl.checkserveridentity: default já é true (confirmado no javadoc do Angus Mail 2.0.3) — não desligar
```

---

## 6. Alternativas consideradas

| Categoria | Recomendado | Alternativa | Quando a alternativa faz sentido |
|-----------|-------------|-------------|----------------------------------|
| PDF | **openhtmltopdf-pdfbox 1.1.87** | **OpenPDF 3.0.5 + `openpdf-html`** (MPL-2.0 OU LGPL-2.1+; **Java 21+**; classes em `org.openpdf.*`, renomeadas na 3.0; `ITextRenderer` em `org.openpdf.pdf`) — testado, também gera o PDF com acentos, QR e rodapé "Página x de y" | Se a equipa preferir o ecossistema OpenPDF. **Ressalva verificada:** `openpdf` 3.0.5 arrasta `brotli4j` + `native-linux-x86_64` (biblioteca nativa) usado só por `BrotliFilter` (leitura de PDF); excluir `com.aayushatharva.brotli4j` — testado: o PDF continua a ser gerado. |
| PDF | openhtmltopdf | JasperReports 7.0.8 (LGPL) | Só se houvesse designer visual de relatórios e dezenas de modelos. Para 2 modelos (fatura, nota de crédito) é excessivo. |
| PDF | openhtmltopdf | PDFBox 3.0.8 direto (Apache-2.0) | Se a licença LGPL for rejeitada: layout manual, bem mais código. |
| Assinatura | (adiada) xades4j 2.4.1 | EU DSS 6.5; JSR-105 manual | Ver §4. |
| Templates | Thymeleaf | FreeMarker 2.3.33 (gerido) | Equivalente; Thymeleaf só pesa mais por ser "natural templating" (XHTML válido por construção, o que o renderizador exige). |
| Geração XML | **JAXB gerado a partir do XSD** | Construir DOM/StAX à mão | Só para um subconjunto minúsculo. A ordem estrita dos elementos e as 5 casas decimais tornam o manual propenso a erro; o XSD oficial fica como árbitro. |
| Origem das classes | Gerar no build a partir dos XSD vendorizados | Copiar o módulo `kriolos-efatura-datamodel` | Ver §7: usa `javax.xml.bind` 2.3 e Java 11. |

---

## 7. O que NÃO usar

| Evitar | Porquê | Usar em vez disso |
|--------|--------|-------------------|
| **iText** (`itext-core` 9.8.0 e linhas anteriores) | Licença **AGPL-3.0**: confirmada no POM do `itext-core` 9.8.0; que as linhas 5.x/7.x também são AGPL é conhecimento geral, não reverificado aqui. Obrigaria a publicar o código da plataforma SaaS ou a comprar licença comercial | openhtmltopdf / OpenPDF |
| `com.openhtmltopdf:*` (grupo antigo) | Último release **1.0.10 em 2021-09**, PDFBox 2.x; abandonado a favor do fork | `io.github.openhtmltopdf:*` 1.1.87 |
| JasperReports | Excessivo; muitas dependências transitivas; modelo `.jrxml` desnecessário | openhtmltopdf |
| Chrome headless / Playwright / Gotenberg para PDF | Acrescenta um runtime de browser (ou um serviço novo) a uma imagem `eclipse-temurin:23-jre-alpine`; as páginas CSS-print de `web/` dependem de sessão de browser e não servem para emissão atómica no servidor | openhtmltopdf |
| `com.google.zxing:javase` | Traz `jcommander` + `jai-imageio-core` (≈0,7 MB) para uma função de 15 linhas; usar `BitMatrix` + `ImageIO` do JDK | `zxing:core` apenas |
| `java.util.Random` / `Math.random()` no IUD | FindSecBugs `PREDICTABLE_RANDOM`; o código aleatório do IUD não deve ser previsível | `SecureRandom` |
| `javax.xml.bind` / JAXB 2.x / `cxf-xjc-plugin` 3.2.1 | O módulo Java do `kriolos-efatura` (Apache-2.0, "INCUBATION", 18 commits, Java 11) usa esta pilha; no Boot 3 é incompatível com os namespaces `jakarta.*` | `jaxb-maven-plugin` 4.0.16 |
| `<generatePackage>` global no plugin JAXB | Força todos os namespaces (eFatura, XMLDSig, XAdES 1.3.2 e 1.4.1) para um só pacote e **falha com "Two declarations cause a collision in the ObjectFactory class"** (reproduzido). `-XautoNameResolution` **não** resolve este caso | Pacotes por namespace por omissão + `.xjb` só para o pacote eFatura |
| EU DSS na v3.0 | Não há certificado nem validação eIDAS a fazer; arrasta dezenas de módulos | `AssinadorDfe` com implementação nula agora; xades4j depois |
| `spring-security-oauth2-client` | Autoconfiguração nova sobre o `SecurityFilterChain` JWT-cookie; registo estático incompatível com credenciais por emitente | Cliente de token próprio |
| SDKs de terceiros como dependência (`kriolos-efatura`, `laravel-efatura`, `efatura-cv-php`) | Nenhum é oficial nem de produção (o README do `efatura-cv-php` diz "não é oficial da DNRE"; `kriolos` é "incubação"); um é PHP, o outro Java 11/javax | Usar só como referência cruzada |
| Gerar XML por concatenação de strings | Escaping e ordem de elementos; a plataforma rejeita por estrutura/tipos | JAXB + validação XSD |
| `commons-logging` no classpath (via PDFBox) | Duplica as classes de `spring-jcl` | Exclusão no POM (§5) |
| Bibliotecas de pagamento/gateway (Vinti4/SISP) | Fora do âmbito decidido | — |

---

## 8. Padrões de stack por variante

**Modo SIMULADO (v3.0, por omissão)**
- `RepositoryCode=3` (Teste) e IUD gerado com o algoritmo real. Gateway simulado devolve estados por documento (p. ex. `GERADO_SIMULADO`) e não toca na rede.
- `AssinadorDfe` nulo → XML sem `ds:Signature` (válido no XSD, §11).
- PDF e email com aviso visível "EMISSÃO SIMULADA — sem validade fiscal". O QR **não** deve apontar para o URL real de produção (apontaria para um DFE inexistente).
- Porquê: o XSD já prevê um repositório de teste; usá-lo torna impossível confundir um documento simulado com um de produção apenas olhando para o IUD.

**Modo HOMOLOGAÇÃO / PRODUÇÃO (marco futuro)**
- `RepositoryCode` 2/1, `AssinadorDfe` = xades4j, `EfaturaGateway` = `RestClient` + token OAuth2 + ZIP Deflate. Nada do que está a montante (modelo, numeração, JAXB, PDF, email) muda.
- Se a opção for o **Middleware** da DNRE em vez da plataforma direta: segunda implementação do mesmo `EfaturaGateway` (autenticação por chave de transmissor).

**Emitente = escritório vs plataforma**
- Mesmo pipeline. Muda a origem dos dados do emitente, a série/numeração e o disparo (pagamento de honorários em `ResourceController` vs pagamento de subscrição em `PlatformAdminController`, gated `hasRole('PLATAFORMA_ADMIN')`). Transmissor = NIF da plataforma nos dois casos.

---

## 9. Compatibilidade de versões

| Pacote | Compatível com | Notas |
|--------|----------------|-------|
| Spring Boot 3.4.1 (BOM) | Spring Framework 6.2.1, Hibernate 6.6.4, Jakarta EE 10 | BOM gere: `jakarta-xml-bind` 4.0.2, `glassfish-jaxb` 4.0.5, `angus-mail` 2.0.3 (`jakarta-mail` API 2.1.3), `thymeleaf` 3.1.3.RELEASE, `httpclient5` 5.4.1, `freemarker` 2.3.33, `commons-codec` 1.17.1. **Não gere** openhtmltopdf, zxing, greenmail, xades4j. O Maven Central já mostra Boot 4.1.x como corrente: o Boot 3.4.1 está atrás, mas todas as escolhas acima são Jakarta EE 10 e não dependem de APIs removidas. |
| `jaxb-maven-plugin` 4.0.16 | JAXB 4.0 / JDK ≥11 / Maven ≥3.1 | Testado por mim em **JDK 21**; o `Dockerfile` compila com `maven:3.9-eclipse-temurin-23` — validar `mvn generate-sources` em JDK 23 no primeiro plano (BAIXA→MÉDIA). |
| openhtmltopdf 1.1.87 | PDFBox 3.0.7 (existe 3.0.8), Java 8+ | Exclusão de `commons-logging` verificada. HTML5 cru não é suportado (o parser é XML): usar templates XHTML (ou jsoup). Evitar `<!DOCTYPE … XHTML 1.0 Strict>` com URL para não gerar mensagens de entidade externa; `useUriResolver` deve negar tudo exceto `data:`/classpath (evita SSRF por `<img src>` controlado por utilizador). |
| OpenPDF 3.0.5 | Java 21+, pacote `org.openpdf` | Só se for a alternativa escolhida. |
| xades4j 2.4.1 | xmlsec 4.0.4, BC, Guice 7.0.0, `jakarta.xml.bind-api` 4.0.5 | O BOM fixa 4.0.2 (mesma linha 4.0.x, API compatível; confirmar na adoção). |
| GreenMail 2.1.14 | Jakarta Mail 2.1 / Angus 2.0.x | Declara Angus 2.0.5; o BOM impõe 2.0.3. |
| Imagem de runtime `eclipse-temurin:23-jre-alpine` | PDF/QR sem fontes de sistema | Fontes embutidas no PDF; `BufferedImage`/`ImageIO` em modo headless. Fontes de terceiros indicam que as imagens Alpine oficiais já incluem `fontconfig`/`ttf-dejavu` (MÉDIA). **Não testado na imagem real** (sem daemon Docker aqui): smoke test em CI. |

---

## 10. Pontos de integração com o backend existente (e armadilhas descobertas)

1. **`createPagamento` não é `@Transactional` e engole falhas.** `ResourceController.createPagamento` (≈ linha 3041) guarda o pagamento e depois atualiza o saldo dentro de `try/catch (DataAccessException)` que apenas faz `log.warn`; `deletePagamento` (≈ 3156) também não é transacional (o último `@Transactional` do ficheiro está na linha 2074). Emitir a FR "na mesma operação" exige mover a lógica para um `@Service` transacional e **remover** esse catch no caminho fiscal; senão a fatura e o saldo podem divergir em silêncio.
2. **Numeração sem lacunas:** não reutilizar o padrão `MAX(numero)+1` em bloco `synchronized` do `numero_cliente` (não é seguro com várias instâncias nem sem lacunas). Usar linha de série com lock pessimista (`SELECT … FOR UPDATE`/`@Lock(PESSIMISTIC_WRITE)`) na mesma transação do pagamento. O XSD limita `DocumentNumber` a 999 999 999. O SDK de terceiros Kowts particiona a sequência por **NIF do transmissor × ano × LED × tipo** (escolha desse SDK, não uma regra verificada da DNRE; o pedido do projeto é "por série e por emitente" — BAIXA, ver Q9 em §12).
3. **Imutabilidade:** reutilizar o padrão da v2.17 (`@Immutable` + repositório estreitado sem métodos de remoção, como em `AuditLog`). Guardar o XML canónico em BD com o seu SHA-256; PDF no MinIO.
4. **`StorageService.upload(tenantId, documentoId, filename, …)`** assume um `Documento`. Acrescentar uma sobrecarga/prefixo `fiscal/` (chave `<tenantId>/fiscal/<docId>/…`) em vez de criar linhas `Documento` falsas; downloads continuam por URL pré-assinado.
5. **Email só depois do commit:** `@TransactionalEventListener(phase = AFTER_COMMIT)` + executor próprio. **`@EnableAsync` não existe hoje** (só `SchedulingConfig` com `@EnableScheduling`): criar `AsyncConfig` com `ThreadPoolTaskExecutor` limitado. Estado de envio por documento + reenvio por `@Scheduled` seguindo o padrão do `AlertasDiariosJob` (tenant passado por parâmetro, `Throwable` isolado por camada). Os timeouts SMTP têm de ser explícitos (§5).
6. **Configuração:** acrescentar `MAIL_*`, e propriedades eFatura (modo, software `Code/Name/Version`, NIF do transmissor, prefixo do URL do QR) a `backend/.env.example`, aos dois compose e a `.github/workflows/deploy.yml`. Os ITs existentes são `@DataJpaTest` (não instanciam `JavaMailSender`/MinIO), logo placeholders novos não os partem; qualquer `@SpringBootTest` novo precisaria de todo o ambiente — preferir testes unitários + `@DataJpaTest`.
7. **Migrações:** não há Flyway/Liquibase; tabelas e constraints novas (unicidade de numeração, índice por IUD) precisam de script em `backend/migrations/` e entrada no checklist do `README.md` (o projeto já sofreu um `500` por script em falta).
8. **SpotBugs/FindSecBugs 1.14.0 (padrões confirmados no jar):**
   - Excluir o código gerado por pacote em `spotbugs-exclude.xml` (categórica, ao contrário das exclusões atuais, que são caso a caso): `com.lexcv.fiscal.efatura.xsd` (52 classes), `org.etsi.uri._01903.v1_3`, `org.etsi.uri._01903.v1_4`, `org.w3._2000._09.xmldsig_`, `un.unece.uncefact.*`.
   - `ENTITY_MASS_ASSIGNMENT`: os novos endpoints recebem **DTOs/records**, nunca entidades JPA em `@RequestBody`.
   - `XXE_DOCUMENT`/`XXE_SAXPARSER`/`XXE_XMLREADER`: construir `SchemaFactory`/`DocumentBuilderFactory` com `FEATURE_SECURE_PROCESSING` e `disallow-doctype-decl`.
   - `PREDICTABLE_RANDOM` (IUD), `SMTP_HEADER_INJECTION` (nunca colocar texto livre do utilizador no assunto/cabeçalhos sem validar), `INSECURE_SMTP_SSL`, `HARD_CODE_PASSWORD`/`HARD_CODE_KEY` (futuras palavras-passe de keystore).
9. **XSD dentro do fat jar (verificado):** `SchemaFactory.newSchema(URL)` com `ACCESS_EXTERNAL_SCHEMA="file"` passa em testes (URLs `file:`) mas **falha no `java -jar`** (URLs `jar:nested:` → "'nested' access is not allowed"). Solução testada e mais segura: `LSResourceResolver` que carrega do classpath, com `ACCESS_EXTERNAL_SCHEMA=""`. Carregar o `Schema` no arranque (fail-fast) e validar uma vez no pipeline de CI contra o jar empacotado.
10. **Datas e fuso:** `IssueDate`/`IssueTime` são `xs:date`/`xs:time` (`AAAA-MM-DD`, `HH:MM:SS`); o JAXB mapeia para `XMLGregorianCalendar`. Emitir a hora **sem fuso** (`FIELD_UNDEFINED`) usando `Atlantic/Cape_Verde` (o `AlertasDiariosJob` já tem este fuso) e um `Clock` injetável; o `AAMMDD` do IUD usa a data local.
11. **Dados já existentes vs. restrições do XSD:** NIF do emitente no IUD tem de ser `[1-9]\d{8}` (a validação atual do LexCV aceita qualquer 9 dígitos); `ReceiverParty` exige `Name` 3–150 e `Address/AddressDetail` ≤100 (`Cliente.morada` pode estar vazia ou ser mais longa); `Tenant` tem `nif/nome/email/telefone` mas **não** morada nem regime de IVA. `Pagamento` não tem `tenant_id` (isolamento transitivo via honorário→processo): as entidades fiscais novas devem ter `tenant_id` próprio.
12. **Dockerfile:** o estágio de build corre `mvn package` depois de copiar `src/` — a geração JAXB corre aí sem alterações; no runtime Alpine não há fontes garantidas → embutir TTF.

---

## 11. Experiências executadas (evidência; JDK 21 local, Boot 3.4.1 BOM, scratchpad fora do repositório)

| # | Experiência | Resultado | Confiança |
|---|-------------|-----------|-----------|
| E1 | `jaxb-maven-plugin` 4.0.16 sobre `EnvelopedSignature.xsd` + 19 XSD comuns | Falha com `generatePackage` global (colisão `ObjectFactory` XAdES 1.3.2/1.4.1). **Sucesso sem `generatePackage`**: 137 classes em 6 pacotes; com `.xjb` o pacote eFatura passa a `com.lexcv.fiscal.efatura.xsd` | ALTA |
| E2 | Validação com `javax.xml.validation` dos XMLs oficiais `2 InvoiceReceipt.xml` e `5 CreditNote.xml` | Válidos **como publicados** e **sem `ds:Signature`**; round-trip JAXB (unmarshal+marshal com schema) OK | ALTA |
| E3 | Tipos gerados | Montantes `BigDecimal`; `Totals.withholdingTaxTotalAmount` existe; datas `XMLGregorianCalendar`; `documentNumber`/`ledCode` `int`; `documentTypeCode` `BigInteger` | ALTA |
| E4 | Luhn sobre os 42 dígitos entre `CV` e o fim do IUD do exemplo oficial | DV calculado = 4 = DV do exemplo | MÉDIA (exemplo com valores fictícios; 10 % de coincidência possível, mas coerente com 2 fontes) |
| E5 | Thymeleaf 3.1.3 → XHTML → `openhtmltopdf-pdfbox` 1.1.87 e `openpdf-html` 3.0.5, com QR (ZXing 3.5.4, PNG 220 px embutido como `data:`) e acentos | Ambos geram PDF válido, texto extraível ("Honorários", "ação", "Página 1 de 1"); OpenPDF também com `brotli4j` removido | ALTA |
| E6 | `openhtmltopdf` com `commons-logging` excluído e `spring-jcl` no classpath | PDF gerado | ALTA |
| E7 | **xades4j 2.4.1**: assinar o FRE de exemplo (sem a assinatura fictícia) com certificado PKCS#12 autoassinado | `ds:Signature` anexado como último filho de `Dfe`; c14n 1.0 + rsa-sha256 + SHA-256; `Reference #IUD` com transformação `enveloped-signature`; `SignedProperties` com `SigningTime` e `SigningCertificate`; **XML assinado válido no XSD**; **verificação independente pelo JSR-105 do JDK: VÁLIDA** | ALTA (certificado de teste; não é ICP-CV) |
| E8 | Carregamento do XSD em fat jar Spring Boot (`java -jar`) | Estratégia `file`-only falha; `access=all` e resolvedor de classpath funcionam | ALTA |
| E9 | Padrões FindSecBugs no jar `findsecbugs-plugin-1.14.0` | Existem `PREDICTABLE_RANDOM`, `XXE_*`, `SMTP_HEADER_INJECTION`, `INSECURE_SMTP_SSL`, `HARD_CODE_*`, `ENTITY_MASS_ASSIGNMENT`; não há padrão específico para Thymeleaf | ALTA |
| E10 | Propriedades `spring.mail.*` e `spring.thymeleaf.*` nos metadados do `spring-boot-autoconfigure` 3.4.1; defaults de timeouts/STARTTLS/`checkserveridentity` no javadoc do Angus Mail 2.0.3 | Confirmadas (timeouts por omissão infinitos; `checkserveridentity=true`; `starttls.required=false`) | ALTA |

Não executado: build em JDK 23, execução na imagem Alpine, ligação à DNRE, assinatura com certificado ICP-CV.

---

## 12. Perguntas em aberto (não verificadas em fonte primária)

| # | Prioridade | Pergunta | Porquê importa | Como resolver |
|---|-----------|----------|----------------|---------------|
| Q1 | **P1** | O pacote XSD 2024-05-27 e o manual v11 são os vigentes? | Os requisitos de campos dependem da versão | Abrir https://efatura.cv/docs/xsd e o manual v11 num ambiente com acesso; comparar com a cópia vendorizada |
| Q2 | **P1** | Estatuto legal de FR **simulada** enviada por email a clientes reais; texto/aviso obrigatório no PDF | A FAQ oficial diz que PDF por email não é fatura eletrónica | Confirmar com a DNRE/contabilista antes de ativar o envio a clientes reais; manter aviso "SIMULADA" e `RepositoryCode=3` |
| Q3 | P2 | Pode um SaaS (transmissor) assinar em nome de cada escritório, ou cada emitente tem de ter certificado ICP-CV de Selo Eletrónico e a LexCV a custódia da chave? Custo/prazo do certificado por escritório? | Define toda a arquitetura de chaves do swap | Contactar DNRE/SISP; avaliar Emissor Público/Middleware como alternativas |
| Q4 | P2 | Homologação do software (LexCV como ISV): procedimento, atribuição de `Software.Code`, credenciais OAuth (por ISV ou por contribuinte), tipo de grant, URL e credenciais do ambiente de testes | Bloqueia a ligação real | helpdesk@efatura.cv (indicado em fontes de terceiros); portal de adesão |
| Q5 | P2 | Assinatura: transformações exigidas na `Reference` ao documento (o exemplo oficial não tem `enveloped-signature`), `Id`s fixos, `SignatureProductionPlace`/`DataObjectFormat` obrigatórios? | Uma assinatura válida pode ainda ser rejeitada | Teste no ambiente de homologação |
| Q6 | P2 | Host do QR (`services.efatura.cv/v1/dfe/view/` vs `pe.efatura.cv/dfe/view`); janela de 1 h de consulta pública | Conteúdo do QR no DFA | Manual v11 |
| Q7 | P2 | Dígito Luhn: confirmar com vetores oficiais (o SDK declara não os ter) | IUD inválido é rejeitado | Manual v11 / homologação |
| Q8 | P2 | NCE: `References` ao IUD original é obrigatório por regra? Que `IssueReasonCode` usar para anulação total/parcial ("IN" vs Art.º 65 n.º x)? Como estorna uma FR (documento com pagamento)? | Requisitos da Nota de Crédito | Manual v11 + contabilista |
| Q9 | P2 | Âmbito da sequência (NIF × LED × tipo × série × ano?), registo de LED (`leds`), tratamento de números não usados (raiz `Event`, código 99) | Modelo de numeração e imutabilidade | Manual v11 |
| Q10 | P3 | Valores de `UnitCode` para serviços; semântica de `IsSpecimen` (só aceita `"true"`); tolerância de arredondamento/reconciliação de totais; regras de `Contacts` | Validações do construtor de DFE | Coluna "Regras" do mapa de campos + homologação |
| Q11 | P3 | Conteúdo mínimo obrigatório do DFA em papel/PDF | PDF "com todos os elementos legais" | Manual v11 / legislação |
| Q12 | P3 | Data exata do Decreto-Lei n.º 79/2020 (12-11 ou 28-12-2020) e texto integral | Citação legal | Boletim Oficial (mf.gov.cv) |
| Q13 | P3 | PDF/QR na imagem `eclipse-temurin:23-jre-alpine`; `mvn generate-sources` em JDK 23 | Risco de ambiente | Smoke test em CI |
| Q14 | — | **Regras materiais**: taxa de IVA, isenção/enquadramento de serviços jurídicos, retenção na fonte aplicável | Não pesquisado nesta dimensão (§2.6 só mostra a codificação) | Pesquisa de funcionalidades/domínio |

---

## 13. Fontes

**Oficial DNRE / efatura.cv (conteúdo lido via excertos de pesquisa; o domínio estava bloqueado — MÉDIA):**
- https://efatura.cv/docs/manual/modelo-conceitual/ — modos de emissão, síncrono, contingência 5 dias úteis, DFA/IUD
- https://efatura.cv/docs/manual/servicos-eletronicos/ (espelho: https://dev.efatura.cv/docs/next/manual/servicos-eletronicos/) — REST, formato do endpoint, ZIP/Deflate, fases de validação
- https://efatura.cv/docs/manual/documentos-fiscais/ — tipos e grupos de campos
- https://efatura.cv/docs/manual/ecosistema/ — PE, Middleware, Emissor Público, ambiente de homologação
- https://efatura.cv/docs/guides/adesao-pe/ — registo, tipos de certificado (Selo Eletrónico, SSL-EV, CNI)
- https://efatura.cv/docs/about/e-fatura/ e https://efatura.cv/docs/faqs/ — definição, PDF por email não é fatura eletrónica
- https://efatura.cv/docs/xsd — localização oficial dos XSD (não aberta)
- Manuais técnicos v7 (https://efatura.cv/assets/files/manual-tecnico-v7-216ba5a0643ea57e50cfbbf26b47e746.pdf) e v10 (https://efatura.cv/assets/files/manual-tecnico-da-fatura-eletronica-v10.0-81ac76da0d05ec36abdb626087cda762.pdf); v11 referida como a mais recente em https://efatura.cv/docs/manual/ (nenhum PDF aberto)
- Pedido de certificado (listado, não lido): https://efatura.cv/assets/files/Pedido-de-Certificado-digital-Tipos-de-Documentos-ad61285789c5e8a3684beb614506f983.pdf
- Índice do Boletim Oficial (Portaria 62/2020): mf.gov.cv (apenas o título do resultado de pesquisa)

**Cópias públicas do pacote oficial e SDKs (lidos diretamente — ALTA para o conteúdo, MÉDIA quanto a ser o mais recente):**
- https://github.com/Kowts/efatura-cv-php — `resources/xsd/efatura/2024-05-27/` (XSD, XMLs de exemplo, "Read Me.txt", "XML Fields Map.txt"), `NOTICE`, `docs/assinatura.md`, `docs/arquitectura.md`, `src/Domain/Iud.php`, `DocumentType.php`, `TaxType.php`, `src/Config/EfaturaConfig.php` (MIT, "não oficial")
- https://github.com/kriolos/kriolos-efatura — README `clientapi` (URLs `iam.efatura.cv` e `services.efatura.cv/api-list`), cópia independente de `EnvelopedSignature.xsd` (Apache-2.0, incubação)
- https://github.com/akira-io/laravel-efatura — confirma IUD/XML/middleware (apenas página inicial lida)

**Terciárias (excertos; BAIXA–MÉDIA):** Wisedat (https://www.wisedat.pt/kb/emissao-dfe-cabo-verde/), Miranda Advogados (alerta sobre o DL 79/2020), Edicom, Primavera BSS, Vendus, Vatupdate, EGDCV (https://governacaodigital.gov.cv/fatura-eletronica-e-fatura).

**Bibliotecas (ALTA):**
- Spring Boot 3.4.1 BOM: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.4.1/spring-boot-dependencies-3.4.1.pom
- Metadados e POMs no Maven Central: `openpdf` 3.0.5, `openpdf-html` 3.0.5, `io.github.openhtmltopdf:openhtmltopdf-pdfbox` 1.1.87, `com.google.zxing:core` 3.5.4, `org.apache.santuario:xmlsec` 4.0.4, `eu.europa.ec.joinup.sd-dss:dss-xades` 6.5, `com.googlecode.xades4j:xades4j` 2.4.1, `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16, `com.icegreen:greenmail-junit5` 2.1.14, `com.itextpdf:itext-core` 9.8.0 (AGPL v3), `net.sf.jasperreports:jasperreports` 7.0.8
- https://github.com/LibrePDF/OpenPDF (3.0.5, Java 21+, MPL-2.0 OR LGPL-2.1+, pacote `org.openpdf`), https://github.com/openhtmltopdf/openhtmltopdf (LGPL-2.1+, PDF/A, PDF/UA), https://github.com/highsource/jaxb-tools, https://github.com/luisgoncalves/xades4j (LGPL-3.0, XAdES-BES), https://github.com/esig/dss (6.5, LGPL-2.1), https://github.com/zxing/zxing (Apache-2.0, manutenção)
- Javadoc `org.eclipse.angus:jakarta.mail:2.0.3` (propriedades SMTP) e `findsecbugs-plugin-1.14.0.jar` (padrões)
- Context7 e `find-sec-bugs.github.io` não estiveram acessíveis (403/bloqueio de saída); substituídos pelos artefactos acima.

---
*Stack research for: faturação eletrónica eFatura CV (LexCV v3.0)*
*Researched: 2026-10-04*
