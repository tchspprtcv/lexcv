# Demonstração animada da plataforma

Gravações da animação de introdução que está no hero de `webpage/`
(componente `webpage/src/components/app-preview.tsx`), para usar fora do site:
redes sociais, email, propostas e apresentações.

| Ficheiro | Formato | Tamanho | Onde usar |
| :--- | :--- | ---: | :--- |
| `lexcv-demo-claro.gif` | GIF 900×388, 12 s, loop | 393 KB | Email, LinkedIn, README, sítios com fundo claro |
| `lexcv-demo-escuro.gif` | GIF 900×388, 12 s, loop | 447 KB | O mesmo, sobre fundo escuro |
| `lexcv-demo-claro.mp4` | H.264 900×388, 12 s | 176 KB | X/Twitter, Instagram, anúncios |
| `lexcv-demo-escuro.mp4` | H.264 900×388, 12 s | 171 KB | O mesmo, sobre fundo escuro |

**Prefira o MP4 onde a plataforma o aceitar.** É metade do tamanho, tem melhor
qualidade de cor e a maioria das redes converte GIFs em vídeo à chegada — enviar
GIF só acrescenta uma recodificação pelo meio. O GIF existe para onde não há
vídeo: corpo de email, Markdown do GitHub, alguns CRM.

Os dois temas existem porque a animação segue o tema do site. Escolha o que
contrasta com o fundo onde a peça vai assentar.

## O que mostra

Cinco passos, 2,4 s cada, em ciclo: registo do cliente → abertura do processo
com conflict check → prazos na agenda → documentos versionados → honorários.
Cada passo traz a legenda visível, por isso a peça explica-se sozinha sem som e
sem texto à volta.

## Regravar

Necessário: `ffmpeg`, Node com `playwright`, e a webpage a correr.

```bash
cd webpage && pnpm dev --port 3100     # noutro terminal

cd business/marketing/gerar
npm install playwright                 # se ainda não estiver instalado
rm -rf frames && mkdir frames
TEMA=light node gravar.js && ./montar.sh claro
rm -rf frames && mkdir frames
TEMA=dark  node gravar.js && ./montar.sh escuro
```

Variáveis: `TEMA` (`light`/`dark`), `BASE` (endereço da webpage, por omissão
`http://localhost:3100`) e `CHROMIUM` (caminho para um Chromium já instalado,
se não quiser que o Playwright descarregue o seu).

O `gravar.js` recorta a moldura **e a legenda por baixo** — sem a legenda a peça
fica só um loop de interface, sem mensagem — e falha com erro se o recorte não
couber na viewport, em vez de gravar uma versão cortada em silêncio. Captura a
2× e reduz na montagem, para o texto sair nítido depois da quantização do GIF.

Ao mudar o componente, regrave: estes ficheiros não se atualizam sozinhos.
