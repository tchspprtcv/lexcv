# Gerador da apresentação do Manual do Utilizador

Gera `docs/MANUAL_DO_UTILIZADOR.pptx` (37 slides) a partir do conteúdo de
`docs/MANUAL_DO_UTILIZADOR.md` e das capturas de ecrã em `docs/images/`.

```bash
cd docs/pptx
npm install pptxgenjs     # única dependência
node build.js             # escreve ../MANUAL_DO_UTILIZADOR.pptx
```

- `theme.js` — paleta, tipografia e helpers de layout (`header`, `shot`, `rows`,
  `cards`, `callout`). Os helpers medem a quebra de linha do texto para calcular
  a altura dos cartões, de modo a que títulos com duas linhas não se sobreponham
  às descrições.
- `build.js` — conteúdo e composição de cada slide.

O conteúdo é mantido à mão a partir do manual: ao alterar o `.md`, atualize
também o `build.js`.
