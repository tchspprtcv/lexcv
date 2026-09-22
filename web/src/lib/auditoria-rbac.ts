import type { AuditoriaRbacAcao, AuditoriaRbacCategoria, AuditoriaRbacEntry } from "@/types/auditoria-rbac";

// Composição de frases PT-CV para eventos de auditoria RBAC (`/settings`, aba "Auditoria",
// Phase 128). Ficheiro puro (sem `react`), seguindo a convenção `*ToLabel` já estabelecida em
// `lib/tipo-decisao.ts` e `lib/origem-processo.ts` -- um `Record`-keyed lookup, não
// condicionais JSX inline no componente da página (128-UI-SPEC.md, Open Discretion Note 2).
//
// Porque a frase é composta no cliente, não no backend: iterar a redação (pontuação, frase em
// PT-CV) é mais barato de corrigir aqui do que em Java string-building, e mantém o backend
// limitado a resolver ids -> nomes (o que já tem de fazer de qualquer forma para
// `alvoNome`/`autorNome`) em vez de também possuir a gramática da frase.
//
// Regra vinculativa (UI-SPEC §3, "Null-name fallback"): `auditoriaEventoToSentence` NUNCA
// renderiza `null`, `undefined` ou um id bruto -- um nome em falta cai sempre para uma frase
// fixa ("um administrador removido" / "um utilizador removido" / "um papel removido"), com o
// mesmo peso visual (`destaque: true`) de um nome real. E um `acao` desconhecido NUNCA deixa um
// código bruto chegar ao ecrã -- cai para um placeholder claramente marcado em vez disso
// (o exato defeito que este ficheiro existe para prevenir, ver `processos/[id]/page.tsx` que
// ainda renderiza `entry.acao` como um `Badge` de código literal).

export type SegmentoFrase = { texto: string; destaque: boolean };

const AUTOR_REMOVIDO = "um administrador removido";
const ALVO_REMOVIDO = "um utilizador removido";
const PAPEL_REMOVIDO = "um papel removido";
// WR-02 (128-REVIEW.md): distinto de PAPEL_REMOVIDO de propósito. PAPEL_REMOVIDO significa "o
// papel em si já não existe" (correto para papelSegmento, usado por
// papel_criar/papel_apagar/papel_permissoes_alterar/papel_atribuir/papel_retirar, onde um
// papelNome nulo pode mesmo significar que o papel foi entretanto apagado). Num evento
// papel_renomear o papel continua a existir -- só o SNAPSHOT do nome antigo/novo é que falhou a
// resolver (evento legado anterior à coluna `detalhe`, ou um `detalhe` malformado). Reutilizar
// PAPEL_REMOVIDO aqui produziria "Maria Silva renomeou o papel um papel removido para um papel
// removido.", que lê como se o papel tivesse sido apagado -- falso.
const NOME_DESCONHECIDO = "nome desconhecido";
const EVENTO_DESCONHECIDO = "Evento de auditoria não reconhecido.";

function autorSegmento(entry: AuditoriaRbacEntry): SegmentoFrase {
  return { texto: entry.autorNome ?? AUTOR_REMOVIDO, destaque: true };
}

function alvoSegmento(entry: AuditoriaRbacEntry): SegmentoFrase {
  return { texto: entry.alvoNome ?? ALVO_REMOVIDO, destaque: true };
}

function papelSegmento(entry: AuditoriaRbacEntry): SegmentoFrase {
  return { texto: entry.papelNome ?? PAPEL_REMOVIDO, destaque: true };
}

function nomeAntigoSegmento(entry: AuditoriaRbacEntry): SegmentoFrase {
  return { texto: entry.nomeAntigo ?? NOME_DESCONHECIDO, destaque: true };
}

function nomeNovoSegmento(entry: AuditoriaRbacEntry): SegmentoFrase {
  return { texto: entry.nomeNovo ?? NOME_DESCONHECIDO, destaque: true };
}

const CONSTRUTORES: Record<AuditoriaRbacAcao, (entry: AuditoriaRbacEntry) => SegmentoFrase[]> = {
  papel_criar: (entry) => [
    autorSegmento(entry),
    { texto: " criou o papel ", destaque: false },
    papelSegmento(entry),
    { texto: ".", destaque: false },
  ],
  papel_renomear: (entry) => [
    autorSegmento(entry),
    { texto: " renomeou o papel ", destaque: false },
    nomeAntigoSegmento(entry),
    { texto: " para ", destaque: false },
    nomeNovoSegmento(entry),
    { texto: ".", destaque: false },
  ],
  papel_apagar: (entry) => [
    autorSegmento(entry),
    { texto: " apagou o papel ", destaque: false },
    papelSegmento(entry),
    { texto: ".", destaque: false },
  ],
  papel_permissoes_alterar: (entry) => [
    autorSegmento(entry),
    { texto: " alterou as permissões do papel ", destaque: false },
    papelSegmento(entry),
    { texto: ".", destaque: false },
  ],
  papel_atribuir: (entry) => {
    if (entry.motivo === "provisionamento") {
      // Decisão 4 (128-CONTEXT.md): o evento de provisionamento não tem autor humano
      // (`autorNome` é nulo por desenho, não por dado em falta) -- por isso o sujeito da frase
      // é "A plataforma", NUNCA o fallback "um administrador removido" que se aplicaria a um
      // autor humano apagado.
      return [
        { texto: "A plataforma", destaque: false },
        { texto: " atribuiu o papel ", destaque: false },
        papelSegmento(entry),
        { texto: " a ", destaque: false },
        alvoSegmento(entry),
        { texto: " na criação do escritório.", destaque: false },
      ];
    }
    return [
      autorSegmento(entry),
      { texto: " atribuiu o papel ", destaque: false },
      papelSegmento(entry),
      { texto: " a ", destaque: false },
      alvoSegmento(entry),
      { texto: ".", destaque: false },
    ];
  },
  papel_retirar: (entry) => [
    autorSegmento(entry),
    { texto: " retirou o papel ", destaque: false },
    papelSegmento(entry),
    { texto: " a ", destaque: false },
    alvoSegmento(entry),
    {
      texto:
        entry.motivo === "utilizador_eliminado" ? " — utilizador eliminado." : ".",
      destaque: false,
    },
  ],
};

/**
 * Converte um evento de auditoria RBAC resolvido numa frase PT-CV, segmentada em `destaque`
 * (nome do autor/alvo/papel, `font-semibold` no Plano 09) e texto normal. Um `acao`
 * desconhecido -- não coberto por `CONSTRUTORES` -- nunca cai silenciosamente para imprimir o
 * código bruto: devolve o placeholder `EVENTO_DESCONHECIDO`, apanhando um futuro `acao` novo na
 * função de mapeamento em vez de deixar um código vazar para o ecrã.
 */
export function auditoriaEventoToSentence(entry: AuditoriaRbacEntry): SegmentoFrase[] {
  const conhecido = Object.prototype.hasOwnProperty.call(CONSTRUTORES, entry.acao);
  if (!conhecido) {
    return [{ texto: EVENTO_DESCONHECIDO, destaque: false }];
  }
  return CONSTRUTORES[entry.acao as AuditoriaRbacAcao](entry);
}

/** Junta os segmentos de `auditoriaEventoToSentence` num texto simples (testes, aria-labels). */
export function auditoriaEventoToTexto(entry: AuditoriaRbacEntry): string {
  return auditoriaEventoToSentence(entry)
    .map((segmento) => segmento.texto)
    .join("");
}

export function auditoriaCategoriaToLabel(categoria: AuditoriaRbacCategoria): string {
  return categoria === "papel" ? "Papel" : "Atribuição";
}

/**
 * Sub-linha de detalhe de uma mudança de permissões ("Acrescentou: ... · Retirou: ...", UI-SPEC
 * Copywriting Contract). As listas guardam CHAVES técnicas (`scope:action`); `rotulos` é o mapa
 * chave -> rótulo legível vindo do catálogo (`useOfficeRbac().data.permissoes`). Se uma chave já
 * não existir no catálogo (papel apagado, permissão descontinuada), mostra a própria chave em
 * vez de a omitir -- nunca "nada".
 */
export function auditoriaPermissoesDetalhe(
  entry: Pick<AuditoriaRbacEntry, "permissoesAdicionadas" | "permissoesRemovidas">,
  rotulos: Record<string, string>,
): string | null {
  const adicionadas = entry.permissoesAdicionadas ?? [];
  const removidas = entry.permissoesRemovidas ?? [];

  if (adicionadas.length === 0 && removidas.length === 0) {
    return null;
  }

  const partes: string[] = [];
  if (adicionadas.length > 0) {
    partes.push(`Acrescentou: ${adicionadas.map((chave) => rotulos[chave] ?? chave).join(", ")}`);
  }
  if (removidas.length > 0) {
    partes.push(`Retirou: ${removidas.map((chave) => rotulos[chave] ?? chave).join(", ")}`);
  }
  return partes.join(" · ");
}
