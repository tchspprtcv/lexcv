# Gerador do Manual do Utilizador em HTML

Gera `docs/MANUAL_DO_UTILIZADOR.html` a partir de `docs/MANUAL_DO_UTILIZADOR.md`.

```bash
pip install markdown
python3 docs/html/build.py
```

O Markdown é a fonte de verdade: ao alterá-lo, volte a correr o script.

O HTML resultante é **autossuficiente** — as 33 capturas de `docs/images/` são
embebidas em base64 e todo o CSS vai inline, por isso o ficheiro abre sem rede e
pode ser enviado por email ou arquivado tal como está.

O script trata do que uma conversão ingénua deixa passar: negritos e links
inline, tabelas, listas aninhadas, blocos de código, e os avisos do GitHub
(`> [!IMPORTANT]`, `> [!NOTE]`, `> [!WARNING]`), que passam a caixas coloridas.
As âncoras dos títulos seguem as regras do GitHub, para o índice do manual
funcionar nos dois formatos.
