# business/ — Trilho de Produto e Negócio

Casa de todos os artefactos **não-código** do LexCV: propostas, especificações para
cliente, contratos, apresentações. O trilho de engenharia vive em `.planning/`
(GSD); este é o trilho paralelo.

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `propostas/` | Propostas comerciais e estratégicas, plano financeiro |
| `especificacoes/` | Especificações funcionais destinadas a cliente |
| `contratos/` | Termo de honorários, ficha de cliente, minutas |
| `apresentacoes/` | Decks comerciais e institucionais |
| `documentacao/` | Documentação de apresentação e evolução do projeto |
| `especificacoes/assets/` | Imagens e infográficos referenciados |

Pesquisa de mercado/regulação vive em `.planning/research/negocio/`, não aqui.

## Regra fundamental: Markdown é a fonte

Todo o documento tem um `.md` versionado como **fonte de verdade**. Os `.docx`,
`.pptx` e `.pdf` são **artefactos derivados**, gerados a partir dele.

```
propostas/proposta-multitenancy-faturacao.md   <- edita-se aqui
propostas/proposta-multitenancy-faturacao.docx <- gerado, nunca editado à mão
```

Foi a ausência desta regra que produziu três cópias divergentes da especificação
do módulo de parecer. Se receberes um `.docx` sem `.md`, extrai o Markdown
primeiro — não edites o binário.

### O gerador é `business/scripts/gerar-docx.py`

Para `documentacao/`, a **única forma sancionada** de produzir os `.docx` é:

```bash
python business/scripts/gerar-docx.py              # regenera os seis
python business/scripts/gerar-docx.py --verificar  # verifica sem escrever
python business/scripts/gerar-docx.py roadmap-tecnologico.md   # só um
```

Requer Python ≥ 3.10 e `python-docx==1.2.0` — ver
`business/scripts/requirements.txt`. **Não usa pandoc** (não existe nesta
máquina) nem Word nem LibreOffice; escreve OOXML directamente.

Regras que decorrem disto:

- **Não editar um `.docx` à mão, nem por OOXML.** Foi assim que entraram três
  defeitos de espaçamento na caixa de aviso do roteiro. Corrige-se o `.md` e
  regenera-se.
- **Não escrever conversores novos.** Os seis `.docx` foram, antes disto,
  gerados por três famílias de script diferentes, nenhuma versionada — daí a
  divergência tipográfica entre eles. Há um conversor, é este.
- Todos os documentos partilham a família **«LexCV Institucional»** (Carta
  retrato, Calibri 11/1.15, rampa de títulos azul-marinho, tabela com cabeçalho
  sombreado e repetido). A especificação vive no topo do script, não numa
  convenção oral.
- A saída é **determinista**: com o mesmo `.md`, o `.docx` sai byte a byte
  igual. Se o binário mudou no `git status`, foi porque o `.md` mudou.

O modo `--verificar` confirma o que é confirmável sem Word: o ficheiro abre,
o OOXML é válido, as contagens de títulos/listas/tabelas batem com o `.md`,
as tabelas são tabelas reais e **não há sequências de espaços múltiplos**.
Não confirma a aparência — isso exige abrir os ficheiros no Word.

## Convenções

- **Nomes:** `kebab-case`, português, sem datas no nome salvo se for uma versão
  histórica congelada (ex.: `especificacao-parecer-juridico-2026-06-30.pdf`).
- **Terminologia:** a mesma do domínio do código — `cliente`, `processo`,
  `parecer`, `honorário`, `evento`, `movimentação`. Ver `CLAUDE.md`.
- **Português de Cabo Verde**, registo formal para documentos de cliente.

## A equipa

Nove agentes, uma responsabilidade cada. Ver `.claude/agents/lexcv-*.md`.

| # | Agente | Responsabilidade única | Entrega |
|---|---|---|---|
| 0 | `lexcv-coordenador` | **Gerir a lista e o portão de revisão** | `.planning/TAREFAS.md` |
| 1 | `lexcv-pesquisador-negocio` | Apurar factos externos com fonte | `.planning/research/negocio/<tema>.md` |
| 2 | `lexcv-estrategista` | Converter factos em opções de decisão | `business/propostas/<slug>.md` |
| 3 | `lexcv-redator` | Primeiro rascunho + artefacto binário | `.md` + `.docx`/`.pptx`/`.pdf` |
| 4 | `lexcv-editor` | Melhorar a forma sem inventar substância | o mesmo `.md`, editado |
| 5 | `lexcv-revisor-qualidade` | Verificar verdade e bloquear | achados + veredicto (não escreve) |
| 6 | `lexcv-programador` | Infra e correções fora do ciclo GSD | alterações no working tree |
| 7 | `lexcv-avaliador` | Dar nota 0–10 ao trabalho dos outros | `.planning/avaliacoes/<data>-<agente>.md` |

O `lexcv-coordenador` despacha os restantes e **nunca produz o entregável**.
Entrar pelo coordenador é a via normal; invocar um agente diretamente salta o
portão de revisão e é decisão consciente tua.

## Fluxo de um documento

```
                    ┌─────────── lexcv-coordenador ───────────┐
                    │  atribui · despacha · fecha             │
                    └───┬─────────────────────────────────▲───┘
                        ▼                                 │
pesquisador ──> estrategista ──> redator ──> editor ──> revisor-qualidade
   factos          opções        rascunho     forma          verdade
                                                                │
                                              ┌─────────────────┘
                                              ▼
                                       lexcv-avaliador  (nota 0–10)
                                              │
                            nota ≥7 e 0 BLOQUEANTE ──> CONCLUÍDA
                            nota <7 ou BLOQUEANTE  ──> DEVOLVIDA
```

O `lexcv-avaliador` é o **portão universal**: passa por lá toda a entrega,
incluindo a do próprio `lexcv-revisor-qualidade`.

**Travas anti-ciclo:** máximo 2 devoluções por tarefa — à terceira o problema
está no enunciado, e a tarefa sobe ao humano como `BLOQUEADA`. E no máximo duas
voltas editor↔revisor dentro da mesma ronda.

## Fronteiras que não se atravessam

- O **coordenador** não escreve documentos nem código. Se produzir o entregável
  em vez de o despachar, falhou — é o modo de falha mais provável do papel.
- O **estrategista** não tem acesso à web. Factos externos entram pelo
  pesquisador, com fonte, ou não entram.
- O **editor** não tem `Write`, só `Edit`. Não cria ficheiros nem conteúdo.
- O **revisor-qualidade** não tem `Edit` nem `Write`. Reporta; nunca corrige.
- O **programador** só tem infraestrutura, scripts e correções pontuais.
  Funcionalidade, `web/`, `webpage/` e backend pertencem ao ciclo GSD.
