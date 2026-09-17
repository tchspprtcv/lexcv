# Rubrica de Avaliação — Equipa LexCV

Ficheiro lido pelo `lexcv-avaliador`. Ajusta os pesos aqui — não dentro do agente.

## Escala

| Nota | Significado | Consequência |
|---|---|---|
| 9–10 | Pronto a usar. Nada material a corrigir | Segue |
| 7–8 | Utilizável com correções pontuais | Segue após correção |
| 5–6 | Base válida, mas com lacunas que obrigam a retrabalho | Devolver ao agente |
| 3–4 | Falha no essencial da função | Refazer |
| 0–2 | Inventado, não verificado, ou fora de âmbito | Refazer do zero |

**Anti-inflação:** 10 exige ausência de qualquer achado, incluindo `BAIXO`.
A nota por omissão de um trabalho competente mas banal é **7**, não 9.
Nunca arredondes para cima para ser simpático. Uma nota que não distingue
trabalho bom de trabalho excelente não serve para nada.

## Dimensões comuns (todos os agentes)

| Dimensão | Peso | Pergunta |
|---|---|---|
| Fidelidade à função | 25% | Fez o que lhe compete — e só isso? Invadiu o papel de outro? |
| Ancoragem em evidência | 30% | Cada afirmação tem código, ficheiro ou fonte por trás? |
| Completude do entregável | 20% | Entregou o formato exato que o contrato dele exige? |
| Honestidade sobre lacunas | 15% | Declarou o que não sabe, ou preencheu com plausibilidade? |
| Clareza | 10% | Um humano decide a partir disto sem pedir esclarecimentos? |

## Critérios de reprovação automática (nota ≤ 4, independentemente do resto)

- Número, preço, prazo ou estatística **sem fonte** apresentado como facto
- Afirmação sobre o que o LexCV faz **não verificada** contra código ou `MILESTONES.md`
- Agente a fazer o trabalho de outro (estrategista a redigir documento final,
  editor a inventar conteúdo, revisor a reescrever em vez de reportar)
- Entregável no sítio errado ou em formato diferente do contratado
- Documento jurídico sem a nota de revisão por advogado

## Pesos específicos por agente

| Agente | Dimensão dominante | O que pesa a dobrar |
|---|---|---|
| `lexcv-pesquisador-negocio` | Ancoragem | Classificação `FACTO`/`ESTIMATIVA`/`NÃO VERIFICADO` correta e fonte presente |
| `lexcv-estrategista` | Fidelidade | Opções com custo real; assunções listadas; decisões devolvidas ao humano |
| `lexcv-redator` | Ancoragem | Nenhuma capacidade descrita sem estar entregue; `.md` como fonte |
| `lexcv-editor` | Fidelidade | Melhorou sem inventar; terminologia do domínio |
| `lexcv-revisor-qualidade` | Ancoragem | Cada achado com evidência; não inventou problemas; não reescreveu |
| `lexcv-programador` | Fidelidade | Respeitou a fronteira do GSD; multi-tenancy e RBAC intactos |
| `lexcv-coordenador` | Fidelidade | Não produziu o entregável; toda a tarefa fechada passou pelo portão de revisão; um só próximo passo |
