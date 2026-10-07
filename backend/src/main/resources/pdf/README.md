# Fontes dos PDF fiscais — DejaVu 2.37

## Finalidade

Os três ficheiros em `fonts/` são as **únicas** fontes usadas pelo renderer de PDF fiscal
(OpenHTMLtoPDF, Phase 137 / 137-06). São embebidas (subset) em todos os PDF gerados, para que os
acentos portugueses (ç, ã, é, …) e o travessão (U+2014, ex. "SIMULAÇÃO — SEM VALIDADE FISCAL")
apareçam e sejam extraíveis em qualquer leitor. Nunca são usadas fontes do sistema nem fontes
remotas: o renderer regista apenas estes três nomes, a partir dos bytes do classpath
(`pdf/fonts/DejaVuSans.ttf`, `pdf/fonts/DejaVuSans-Bold.ttf`, `pdf/fonts/DejaVuSansMono.ttf`), e
qualquer outro recurso pedido pelo HTML é recusado pelo `ClasspathPdfResolver`.

| Família CSS | Peso | Ficheiro |
|-------------|------|----------|
| `DejaVu Sans` | 400 | `DejaVuSans.ttf` |
| `DejaVu Sans` | 700 | `DejaVuSans-Bold.ttf` |
| `DejaVu Sans Mono` | 400 | `DejaVuSansMono.ttf` |

## Proveniência

- **Origem:** release oficial DejaVu Fonts **2.37** (tag `version_2_37`),
  `https://github.com/dejavu-fonts/dejavu-fonts/releases/download/version_2_37/dejavu-fonts-ttf-2.37.tar.bz2`.
- **Obtido:** 2026-10-07; os ficheiros foram extraídos de `dejavu-fonts-ttf-2.37/ttf/` e
  `dejavu-fonts-ttf-2.37/LICENSE` e copiados byte a byte.
- **SHA-256 do arquivo:** `fa9ca4d13871dd122f61258a80d01751d603b4d3ee14095d65453b4e846e17d7`

## Manifesto SHA-256

| Ficheiro | Bytes | SHA-256 |
|----------|-------|---------|
| `fonts/DejaVuSans.ttf` | 757076 | `7da195a74c55bef988d0d48f9508bd5d849425c1770dba5d7bfc6ce9ed848954` |
| `fonts/DejaVuSans-Bold.ttf` | 705684 | `e6476c1b80502924294eed40894c5b18e06c181444ca953e5334262df9c27724` |
| `fonts/DejaVuSansMono.ttf` | 340712 | `b4a6c3e4faab8773f4ff761d56451646409f29abedd68f05d38c2df667d3c582` |
| `fonts/LICENSE-DejaVu.txt` | — | `7a083b136e64d064794c3419751e5c7dd10d2f64c108fe5ba161eae5e5958a93` |

Qualquer alteração a uma das três fontes faz falhar o build
(`src/test/java/com/lexcv/fiscal/pdf/FontesPdfIntegridadeTest.java`). O `.gitattributes` na raiz
marca `fonts/` como binário (`-text`), para que nenhum checkout altere os bytes.

## Licença

`fonts/LICENSE-DejaVu.txt` é o ficheiro `LICENSE` do arquivo oficial, sem alterações:

- As fontes derivam das **Bitstream Vera** (© Bitstream): licença que permite usar, copiar,
  modificar, redistribuir e vender, incluindo embeber em documentos, desde que o aviso de
  copyright acompanhe as cópias das fontes e que versões modificadas mudem de nome.
- As alterações DejaVu estão no **domínio público**; os glifos importados das fontes Arev
  (© Tavmjong Bah) seguem uma licença equivalente.
- Usamos os ficheiros sem modificação; o aviso viaja com eles no classpath e no jar.

## Substituição

Para mudar de versão: descarregar o novo arquivo oficial para um directório vazio, verificar o
seu hash, copiar os três TTF e o `LICENSE`, e actualizar este README e o manifesto em
`FontesPdfIntegridadeTest` no mesmo commit. O renderer não deve passar a carregar outros nomes
sem actualizar esta lista.
