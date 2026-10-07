# Phase 137 — Spike: bibliotecas (PDF, email, harness de testes, fontes)

**Data:** 2026-10-07
**Projecto descartável:** `spike137` no scratchpad da sessão, parent `spring-boot-starter-parent` 3.4.1, compilado e testado com `mvn -Dmaven.compiler.release=21` (JDK 21.0.11). Nenhum ficheiro do spike entra no repositório além deste documento e das fontes de E.
**Consumidores:** 137-06 (renderer PDF), 137-08/137-09 (adaptador SMTP e configuração), ITs de email.

---

## A. OpenHTMLtoPDF — artefacto, PDFBox, licença, Java 21, advisories

### Evidência

- `https://repo1.maven.org/maven2/io/github/openhtmltopdf/openhtmltopdf-pdfbox/maven-metadata.xml`: `<latest>1.1.87</latest>`, `<release>1.1.87</release>`, `<lastUpdated>20260926103826</lastUpdated>` (fork mantido).
- `https://repo1.maven.org/maven2/com/openhtmltopdf/openhtmltopdf-pdfbox/maven-metadata.xml`: `<latest>1.0.10</latest>` (original, abandonado; PDFBox 2.x).
- POM de `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.87` (SHA-1 do jar `5f20eff1b7010adc5d80f6611a56eeaffd812800`, igual ao `.sha1` publicado):
  - `<licenses>`: "GNU Lesser General Public License (LGPL), version 2.1 or later".
  - dependências: `org.apache.pdfbox:pdfbox:3.0.7`, `org.apache.pdfbox:xmpbox:3.0.7`, `io.github.openhtmltopdf:openhtmltopdf-core:1.1.87`, `de.rototor.pdfbox:graphics2d:3.0.1` (com exclusão de pdfbox).
- `mvn dependency:tree` (Boot 3.4.1, sem gestão de versão do BOM para PDFBox):
  ```
  +- io.github.openhtmltopdf:openhtmltopdf-pdfbox:jar:1.1.87:compile
  |  +- org.apache.pdfbox:pdfbox:jar:3.0.7:compile
  |  |  +- org.apache.pdfbox:pdfbox-io:jar:3.0.7:compile
  |  |  \- org.apache.pdfbox:fontbox:jar:3.0.7:compile
  |  +- org.apache.pdfbox:xmpbox:jar:3.0.7:compile
  |  \- io.github.openhtmltopdf:openhtmltopdf-core:jar:1.1.87:compile
  ```
- Licença PDFBox (POM `pdfbox-parent` 3.0.7 e `Bundle-License` do MANIFEST): Apache 2.0.
- `javap -v` (classe arbitrária de cada jar): `openhtmltopdf-pdfbox` 1.1.87 **major 52**, `openhtmltopdf-core` 1.1.87 **major 52**, `pdfbox` 3.0.7 **major 52**, `fontbox` 3.0.7 **major 52** (Java 8 ≤ 65 = Java 21).
- **Advisories (OSV): NÃO VERIFICADO NESTA SESSÃO.** `POST https://api.osv.dev/v1/query` foi recusado pelo proxy de saída (CONNECT 403, política da organização); as fontes alternativas também estão bloqueadas (`services.nvd.nist.gov` recusado; a API GraphQL e `/advisories` do GitHub não estão disponíveis nesta sessão). Pacotes a consultar: `io.github.openhtmltopdf:openhtmltopdf-pdfbox@1.1.87`, `io.github.openhtmltopdf:openhtmltopdf-core@1.1.87`, `org.apache.pdfbox:pdfbox@3.0.7`, `org.apache.pdfbox:fontbox@3.0.7`, `org.apache.pdfbox:xmpbox@3.0.7`, `de.rototor.pdfbox:graphics2d@3.0.1`, `com.icegreen:greenmail@2.1.14`. Os CVEs PDFBox conhecidos (CVE-2021-27807/27906/31811/31812) afectam apenas a linha 2.0.x < 2.0.24 e não a 3.0.7 — isto é conhecimento prévio, não evidência desta sessão.

### Decisão

- **Artefacto:** `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.87` (fork mantido; o `com.openhtmltopdf` 1.0.10 está parado em PDFBox 2.x e não é usado).
- PDFBox/FontBox/XMPBox **3.0.7** (transitivas; sem versão no BOM do Boot).
- Licença: LGPL-2.1-or-later (OpenHTMLtoPDF, uso como biblioteca não modificada via Maven = redistribuível); Apache-2.0 (PDFBox).
- Java 21: compatível (bytecode Java 8).
- **Pendente (verificação humana/CI):** consulta OSV dos pacotes acima antes de o phase ser fechado. A regra de paragem do plano não dispara porque nenhum critério falhou — um deles (advisories) ficou sem verificação por bloqueio de rede. Se a consulta revelar um HIGH/CRITICAL aberto, a dependência é retirada e o utilizador decide (o CONTEXT fixa OpenHTMLtoPDF; nenhuma substituição automática).

---

## B. Smoke render (1.1.87)

### Evidência

XHTML `lang="pt-CV"` com: `<title>`; `@page { @top-center { content: element(cab) } @bottom-center { content: "Página " counter(page) " de " counter(pages) } }`; `#cab { position: running(cab) }`; marca d'água `position: fixed; transform: rotate(-30deg); white-space: nowrap; width: 200mm`; tabela de 80 linhas com `thead`; `<p class="mono">`; `<img src="http://127.0.0.1:9/x.png">`; `<link rel="stylesheet" href="file:///etc/hostname">`; `<img src="data:image/png;base64,...">`. Fontes registadas a partir de `byte[]`. Resolver regista cada pedido e devolve `null`; fábricas de stream HTTP e de protocolos registam e devolvem `null`.

Resultado (`RenderSpikeTest`, verde):

- PDF de 21 630 bytes, **3 páginas**, `Producer=LexCV`, `Title="Fatura FT 2026/1"` (vem do `<title>`).
- `PDFTextStripper` página a página: a string exacta **"SIMULAÇÃO — SEM VALIDADE FISCAL"** (U+2014, ç, ã) aparece **2 vezes em cada página** (cabeçalho running + marca d'água fixa); `"Descrição"` (thead) presente em todas as páginas; rodapé `"Página N de 3"` correcto em todas.
- Fontes nas resources de todas as páginas: `AAALBU+DejaVuSans`, `AAAPCT+DejaVuSans-Bold`, `AAAAIM+DejaVuSansMono` — **todas `isEmbedded() == true`** (subset).
- URIs que chegaram ao `FSUriResolver`: `file:///etc/hostname` (1x, stylesheet), `http://127.0.0.1:9/x.png` (3x), `data:image/png;base64,...` (3x). **Os três tipos passam pelo resolver**, incluindo `data:`; devolver `null` rejeita-os ("URI resolver rejected loading image at ...", "Unable to load CSS from null").
- Chamadas às fábricas de stream (`useHttpStreamImplementation` / `useProtocolsStreamImplementation`): **zero** — nada foi buscado.
- Achados laterais:
  1. **A repetição do `thead` exige `table { -fs-table-paginate: paginate; }`**; só com `display: table-header-group` o cabeçalho não se repete (falhou na 1.ª execução).
  2. A marca d'água sem `white-space: nowrap` (ou largura suficiente) parte em duas linhas e a string deixa de ser extraível inteira.
  3. O OpenHTMLtoPDF instancia as fontes Standard-14 de recurso do PDFBox (aviso "Using fallback font LiberationSans for base font Symbol"); o `FontMapper` do PDFBox varre as fontes do sistema e escreve `~/.pdfbox.cache`. Nenhuma fonte de sistema acaba no PDF (só as 3 DejaVu embebidas), mas há leitura do sistema de ficheiros. Opção para 137-06: `org.apache.pdfbox.pdmodel.font.FontMappers.set(...)` com um mapper que não varre o sistema, ou `-Dpdfbox.fontcache=<dir gravável>` em contentores com HOME só de leitura.

API exacta usada (copiar em 137-06):

```java
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

PdfRendererBuilder b = new PdfRendererBuilder();
b.useFastMode();
// FSSupplier<InputStream>: BaseRendererBuilder#useFont(FSSupplier<InputStream>, String family, Integer weight, FontStyle, boolean subset)
b.useFont(() -> new ByteArrayInputStream(regular), "DejaVu Sans", 400, FontStyle.NORMAL, true);
b.useFont(() -> new ByteArrayInputStream(bold),    "DejaVu Sans", 700, FontStyle.NORMAL, true);
b.useFont(() -> new ByteArrayInputStream(mono),    "DejaVu Sans Mono", 400, FontStyle.NORMAL, true);
b.useUriResolver((String baseUri, String uri) -> null);                       // FSUriResolver#resolveURI(String,String)
b.useHttpStreamImplementation(url -> null);                                   // FSStreamFactory#getUrl(String) -> FSStream
b.useProtocolsStreamImplementation(url -> null, "file", "jar", "http", "https", "ftp"); // (FSStreamFactory, String...)
b.withProducer("LexCV");                                                      // PdfRendererBuilder#withProducer(String)
b.withHtmlContent(html, null);                                                // (String html, String baseDocumentUri)
b.toStream(out);                                                              // PdfRendererBuilder#toStream(OutputStream)
b.run();                                                                      // throws IOException
```

Também disponíveis (não usados no smoke): `useExternalResourceAccessControl(BiPredicate<String, ExternalResourceType>, ExternalResourceControlPriority)` (RUN_BEFORE_RESOLVING_URI / RUN_AFTER_RESOLVING_URI), `withW3cDocument(org.w3c.dom.Document, String)`, `usePdfAConformance(...)`, `usePdfUaAccessibility(boolean)`. Título do documento: `<title>` no `<head>`.

### Decisão

- 137-06 usa exactamente a API acima; o `ClasspathPdfResolver` (política igual a `ClasspathXsdResolver`: lista branca fixa, normalização com `URI#resolve` sobre base sintética, tudo o resto → `null`) é o `FSUriResolver`; `data:` também é recusado (o template não precisa de imagens embutidas; se precisar, entram pela lista branca do classpath).
- As fábricas HTTP e de protocolos (`file`, `jar`, `http`, `https`, `ftp`) são sempre substituídas por fábricas que devolvem `null` (defesa em profundidade se o resolver tiver um bug).
- O CSS do template inclui `-fs-table-paginate: paginate` na tabela de linhas e `white-space: nowrap` na marca d'água.
- Fontes registadas apenas a partir dos bytes do classpath (`pdf/fonts/`, ver E), nunca `useFont(File, ...)`.

---

## C. Spring Mail 3.4.1 — auto-configuração

### Evidência

`javap -v` em `spring-boot-autoconfigure-3.4.1.jar`:

- `MailSenderAutoConfiguration`: `@ConditionalOnClass({MimeMessage, MimeType, MailSender})`, `@ConditionalOnMissingBean(MailSender)`, `@Conditional(MailSenderCondition)`, `@EnableConfigurationProperties(MailProperties)`, `@Import({MailSenderJndiConfiguration, MailSenderPropertiesConfiguration})`.
- `MailSenderCondition extends AnyNestedCondition` com `HostProperty` e `JndiNameProperty`.
- `MailSenderPropertiesConfiguration`: `@ConditionalOnProperty(prefix = "spring.mail", name = "host")` (sem `havingValue`) e `@ConditionalOnMissingBean(JavaMailSender)` no bean.

`ApplicationContextRunner` com `MailSenderAutoConfiguration` + `MailSenderValidatorAutoConfiguration` (`MailAutoConfigSpikeTest`, verde):

| Caso | Beans `JavaMailSender` |
|------|------------------------|
| (i) sem nenhuma propriedade `spring.mail.*` | **0** |
| (ii) `spring.mail.host=` (string vazia) | **1** (`host=[]`, arranque sem erro) |
| (iii) `spring.mail.host=false` | 0 |
| (iv) `spring.mail.host=smtp.example.cv` | 1 |

O backend não tem hoje nenhuma propriedade `spring.mail.*` nem actuator.

### Decisão

- **Uma string vazia em `spring.mail.host` CRIA um `JavaMailSender`** (o `@ConditionalOnProperty` sem `havingValue` só rejeita `false`). Logo, um `SPRING_MAIL_HOST=` vazio num `.env` produziria um sender sem host.
- Regra para 137-08/137-09: **nunca usar `spring.mail.*`**. As definições SMTP ficam em `app.email.smtp.*` (host, porta, utilizador, palavra-passe, TLS, remetente) e o adaptador constrói um `JavaMailSenderImpl` privado **apenas quando `host` não está em branco** (`StringUtils.hasText`); caso contrário o estado derivado é NAO_CONFIGURADO. Não existe nenhum bean `JavaMailSender` no contexto. Recomendado: um teste de contexto que afirme `getBeanNamesForType(JavaMailSender.class).length == 0` para impedir regressões.

---

## D. Harness de email para ITs e catcher para o UAT ao vivo

### Evidência

- `https://repo1.maven.org/maven2/com/icegreen/greenmail-junit5/maven-metadata.xml`: `<release>2.1.14</release>` (`lastUpdated 20260919132749`). Licença (POM `greenmail-parent` 2.1.14): Apache 2.0. Bytecode major 52.
- `mvn dependency:tree -Dincludes=jakarta.mail,org.eclipse.angus,jakarta.activation` (Boot 3.4.1):
  ```
  +- org.springframework.boot:spring-boot-starter-mail:jar:3.4.1:compile
  |  \- org.eclipse.angus:jakarta.mail:jar:2.0.3:compile
  |     +- jakarta.activation:jakarta.activation-api:jar:2.1.3:compile
  |     \- org.eclipse.angus:angus-activation:jar:2.0.2:runtime
  \- com.icegreen:greenmail-junit5:jar:2.1.14:test
     \- com.icegreen:greenmail:jar:2.1.14:test
        \- jakarta.mail:jakarta.mail-api:jar:2.1.3:test
  ```
  (No scope de teste coexistem `angus:jakarta.mail` 2.0.3, que já contém a API 2.1, e `jakarta.mail-api` 2.1.3 — mesma especificação 2.1; nenhum conflito observado.)
- `GreenMailSpikeTest` (verde): `@RegisterExtension static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)` (porta 3025); `JavaMailSenderImpl` com host `127.0.0.1`, porta `greenMail.getSmtp().getPort()`; `MimeMessageHelper(m, true, "UTF-8")` com 2 anexos (`application/pdf`, `application/xml`); `greenMail.waitForIncomingEmail(5000, 1)` → true; `getReceivedMessages()[0]` com o assunto UTF-8 exacto ("Fatura FT 2026/1 — SIMULAÇÃO") e **2 partes `Part.ATTACHMENT`** com os nomes e bytes enviados.
- Mailpit: `docker pull axllent/mailpit:v1.27.10` → digest `sha256:b1f1be18af530d939a11ee8820b379e0c88eeec204d904bfad68862adced3a5a`. Contentor de teste: SMTP **1025**, HTTP/UI **8025**. Mensagem multipart enviada por SMTP e lida pela REST API:
  - `GET /api/v1/messages` → `{"total":1,"messages":[{"ID":...}]}`
  - `GET /api/v1/message/{ID}` → `Attachments[]` com `PartID`, `FileName`, `ContentType`
  - `GET /api/v1/message/{ID}/part/{PartID}` → bytes do anexo (`%PDF-1.7`)

### Decisão

- **ITs: GreenMail** (`com.icegreen:greenmail-junit5:2.1.14`, scope test) — funciona em processo, sem Docker, e lê anexos via Jakarta Mail.
- **UAT ao vivo: Mailpit** `axllent/mailpit:v1.27.10` (pinado; digest acima), SMTP 1025, UI 8025, endpoints REST acima.

---

## E. Fontes DejaVu 2.37

### Evidência

- Arquivo: `https://github.com/dejavu-fonts/dejavu-fonts/releases/download/version_2_37/dejavu-fonts-ttf-2.37.tar.bz2` (HTTP 200, 5 429 777 bytes), descarregado para um directório vazio próprio no scratchpad.
- SHA-256 do arquivo: `fa9ca4d13871dd122f61258a80d01751d603b4d3ee14095d65453b4e846e17d7`
- Ficheiros extraídos (`dejavu-fonts-ttf-2.37/ttf/`):

| Ficheiro | Bytes | SHA-256 |
|----------|-------|---------|
| DejaVuSans.ttf | 757 076 | `7da195a74c55bef988d0d48f9508bd5d849425c1770dba5d7bfc6ce9ed848954` |
| DejaVuSans-Bold.ttf | 705 684 | `e6476c1b80502924294eed40894c5b18e06c181444ca953e5334262df9c27724` |
| DejaVuSansMono.ttf | 340 712 | `b4a6c3e4faab8773f4ff761d56451646409f29abedd68f05d38c2df667d3c582` |

- Licença: `dejavu-fonts-ttf-2.37/LICENSE` (SHA-256 `7a083b136e64d064794c3419751e5c7dd10d2f64c108fe5ba161eae5e5958a93`), menciona "Bitstream Vera" (licença Bitstream Vera + alterações DejaVu em domínio público; Arev para os glifos Arev).
- As três fontes renderizaram e foram embebidas no smoke de B.

### Decisão

Vendorizar os três TTF e `LICENSE` (como `LICENSE-DejaVu.txt`) em `backend/src/main/resources/pdf/fonts/`, com README de proveniência e `FontesPdfIntegridadeTest` a fixar os três SHA-256 acima (137-01 Task 3).
