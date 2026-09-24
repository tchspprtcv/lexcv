#!/usr/bin/env python3
"""Gera docs/MANUAL_DO_UTILIZADOR.html a partir de docs/MANUAL_DO_UTILIZADOR.md.

As capturas de docs/images/ sao embebidas em base64 e todo o CSS vai inline, para
o manual ser um unico ficheiro que se abre sem rede.
"""
import base64
import mimetypes
import re
import unicodedata
from pathlib import Path

import markdown

DOCS = Path(__file__).resolve().parent.parent
MD = DOCS / "MANUAL_DO_UTILIZADOR.md"
OUT = DOCS / "MANUAL_DO_UTILIZADOR.html"

ALERTAS = {
    "IMPORTANT": ("alert-important", "Importante"),
    "NOTE": ("alert-note", "Nota"),
    "WARNING": ("alert-warning", "Atenção"),
    "TIP": ("alert-note", "Sugestão"),
    "CAUTION": ("alert-warning", "Cuidado"),
}


def slug(texto: str) -> str:
    """Ancora ao estilo GitHub: minusculas, acentos preservados, cada espaco -> um hifen.

    Sequencias de espacos NAO sao colapsadas: "MinIO / S3" perde a barra e
    fica "minio--s3", que e a ancora que o indice do manual ja usa.
    """
    t = texto.strip().lower()
    t = re.sub(r"[^\w\s-]", "", t, flags=re.UNICODE)
    return re.sub(r"\s", "-", t)


def converter_alertas(texto: str) -> str:
    """> [!WARNING] ... -> <div class="alert-box alert-warning">."""
    linhas = texto.split("\n")
    saida, i = [], 0
    while i < len(linhas):
        m = re.match(r"^>\s*\[!(\w+)\]\s*$", linhas[i])
        if not m:
            saida.append(linhas[i])
            i += 1
            continue
        classe, rotulo = ALERTAS.get(m.group(1).upper(), ("alert-note", m.group(1).title()))
        i += 1
        corpo = []
        while i < len(linhas) and linhas[i].startswith(">"):
            corpo.append(re.sub(r"^>\s?", "", linhas[i]))
            i += 1
        # uma lista colada ao paragrafo anterior nao e reconhecida como lista
        normalizado = []
        for linha in corpo:
            if re.match(r"^\s*(?:[-*+]|\d+\.)\s", linha) and normalizado and normalizado[-1].strip():
                if not re.match(r"^\s*(?:[-*+]|\d+\.)\s", normalizado[-1]):
                    normalizado.append("")
            normalizado.append(linha)
        interno = markdown.markdown("\n".join(normalizado), extensions=["extra", "sane_lists"])
        saida.append(
            f'<div class="alert-box {classe}">'
            f'<p class="alert-label">{rotulo}</p>{interno}</div>'
        )
    return "\n".join(saida)


def embeber_imagens(html: str) -> tuple[str, int, list[str]]:
    faltam: list[str] = []
    contador = 0

    def repl(m):
        nonlocal contador
        prefixo, src, sufixo = m.group(1), m.group(2), m.group(3)
        if src.startswith(("http://", "https://", "data:")):
            return m.group(0)
        caminho = (DOCS / src.lstrip("./")).resolve()
        if not caminho.is_file():
            faltam.append(src)
            return m.group(0)
        tipo = mimetypes.guess_type(caminho.name)[0] or "image/png"
        dados = base64.b64encode(caminho.read_bytes()).decode("ascii")
        contador += 1
        return f'{prefixo}data:{tipo};base64,{dados}{sufixo}'

    return re.sub(r'(<img[^>]*?src=")([^"]+)(")', repl, html), contador, faltam


def numerar_ancoras(html: str) -> str:
    """Da a cada <h2>/<h3>/<h4> o id no formato que o indice do manual usa."""
    def repl(m):
        nivel, atributos, texto = m.group(1), m.group(2), m.group(3)
        if "id=" in atributos:
            return m.group(0)
        limpo = re.sub(r"<[^>]+>", "", texto)
        return f'<h{nivel}{atributos} id="{slug(limpo)}">{texto}</h{nivel}>'

    return re.sub(r"<h([2-4])([^>]*)>(.*?)</h\1>", repl, html, flags=re.DOTALL)


CSS = """
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: #020617; color: #e2e8f0; margin: 0;
      padding: 1.5rem; line-height: 1.7; -webkit-font-smoothing: antialiased;
    }
    @media (min-width: 768px) { body { padding: 3rem; } }
    .wrap { max-width: 56rem; margin: 0 auto; }

    header.doc { border-bottom: 1px solid #1e293b; padding-bottom: 1.5rem; margin-bottom: 2rem; }
    .pill { display: inline-block; background: #2563eb; color: #fff; font-size: .75rem;
            font-weight: 600; padding: .25rem .625rem; border-radius: .25rem;
            letter-spacing: .05em; text-transform: uppercase; }
    .pill-meta { color: #94a3b8; font-size: .875rem; margin-left: .75rem; }
    header.doc h1 { font-size: 2.25rem; font-weight: 800; color: #fff; margin: .75rem 0 .5rem;
                    letter-spacing: -.025em; line-height: 1.2; }
    header.doc p { color: #94a3b8; margin: 0; }

    h2 { font-size: 1.75rem; font-weight: 700; color: #fff; margin: 3rem 0 1rem;
         padding-bottom: .5rem; border-bottom: 1px solid #1e293b; scroll-margin-top: 1rem; }
    h3 { font-size: 1.3rem; font-weight: 600; color: #f1f5f9; margin: 2rem 0 .75rem; scroll-margin-top: 1rem; }
    h4 { font-size: 1.05rem; font-weight: 600; color: #cbd5e1; margin: 1.5rem 0 .5rem; scroll-margin-top: 1rem; }
    p { margin: .85rem 0; }
    strong { color: #fff; font-weight: 650; }
    em { color: #cbd5e1; }
    a { color: #60a5fa; text-decoration: none; }
    a:hover { text-decoration: underline; }
    hr { border: 0; border-top: 1px solid #1e293b; margin: 2.5rem 0; }

    ul, ol { margin: .85rem 0; padding-left: 1.5rem; }
    li { margin: .4rem 0; }
    li::marker { color: #64748b; }

    code { background: #0f172a; border: 1px solid #1e293b; border-radius: .25rem;
           padding: .1rem .35rem; font-size: .875em; color: #93c5fd;
           font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
    pre { background: #0f172a; border: 1px solid #1e293b; border-radius: .5rem;
          padding: 1rem; overflow-x: auto; }
    pre code { background: none; border: 0; padding: 0; }

    table { width: 100%; border-collapse: collapse; margin: 1.25rem 0; font-size: .925rem;
            display: block; overflow-x: auto; }
    th, td { border: 1px solid #1e293b; padding: .6rem .75rem; text-align: left; vertical-align: top; }
    th { background: #0f172a; color: #fff; font-weight: 600; }
    tr:nth-child(even) td { background: rgba(15, 23, 42, .45); }

    img { border-radius: .5rem; border: 1px solid #1e293b;
          box-shadow: 0 10px 15px -3px rgba(0,0,0,.3); margin: 1rem 0 1.5rem;
          max-width: 100%; height: auto; display: block; }

    .alert-box { border-left: 4px solid; padding: 1rem 1.25rem; border-radius: .375rem; margin: 1.25rem 0; }
    .alert-box p { margin: .5rem 0; }
    .alert-box > p:last-child { margin-bottom: 0; }
    .alert-label { font-weight: 700; text-transform: uppercase; letter-spacing: .05em;
                   font-size: .75rem; margin-top: 0 !important; }
    .alert-important { border-color: #3b82f6; background: rgba(59,130,246,.1); }
    .alert-important .alert-label { color: #60a5fa; }
    .alert-note { border-color: #10b981; background: rgba(16,185,129,.1); }
    .alert-note .alert-label { color: #34d399; }
    .alert-warning { border-color: #f59e0b; background: rgba(245,158,11,.1); }
    .alert-warning .alert-label { color: #fbbf24; }

    footer.doc { border-top: 1px solid #1e293b; margin-top: 3rem; padding-top: 1.5rem;
                 color: #64748b; font-size: .875rem; }
"""


def main() -> None:
    texto = MD.read_text(encoding="utf-8")

    # O cabecalho da pagina ja mostra titulo, versao e ambiente: corta o H1 e o
    # bloco de metadados do Markdown para nao os repetir.
    texto = re.sub(r"\A#\s+.*?\n(?:^>.*\n)*", "", texto, flags=re.MULTILINE)
    # o cabecalho da pagina ja tem separador proprio
    texto = re.sub(r"\A\s*(?:-{3,}\s*\n)+", "", texto)
    texto = converter_alertas(texto)

    corpo = markdown.markdown(
        texto,
        extensions=["extra", "sane_lists", "attr_list", "md_in_html"],
    )
    corpo = numerar_ancoras(corpo)
    corpo, n_imgs, faltam = embeber_imagens(corpo)

    html = f"""<!DOCTYPE html>
<html lang="pt">

<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Manual do Utilizador — LexCV</title>
  <style>{CSS}  </style>
</head>

<body>
  <div class="wrap">
    <header class="doc">
      <div><span class="pill">Oficial</span><span class="pill-meta">Versão 1.0 • Cabo Verde</span></div>
      <h1>Manual do Utilizador — Plataforma Jurídica LexCV</h1>
      <p>Guia ilustrado com capturas de ecrã da plataforma
         <a href="https://www.alcv.tech" target="_blank" rel="noopener">www.alcv.tech</a>.</p>
    </header>
{corpo}
    <footer class="doc">© 2026 LexCV. Todos os direitos reservados.</footer>
  </div>
</body>

</html>
"""
    OUT.write_text(html, encoding="utf-8")
    print(f"escrito: {OUT}")
    print(f"  imagens embebidas: {n_imgs}")
    print(f"  em falta: {faltam if faltam else 'nenhuma'}")
    print(f"  tamanho: {OUT.stat().st_size / 1048576:.2f} MB")


if __name__ == "__main__":
    main()
