import type { OfficeRbac } from "@/types/office-rbac";

/**
 * Estado local de edição da matriz permissão × papel: por `papelId` (UUID string do
 * `TenantRole`), o conjunto (chaves técnicas) de permissões atualmente marcadas no ecrã --
 * não necessariamente já gravado no servidor.
 */
export type LocalPermissoesPapeis = Record<string, Set<string>>;

/** Inicializa o estado local a partir de um payload da query, sem nenhuma edição por cima. */
export function construirEstadoLocal(data: OfficeRbac): LocalPermissoesPapeis {
  const estado: LocalPermissoesPapeis = {};
  for (const papel of data.papeis) {
    estado[papel.id] = new Set(papel.permissoes);
  }
  return estado;
}

/**
 * Generalização de `mesclarEstadoLocal` de
 * `plataforma/moldes/merge-local-permissoes.ts` (Phase 125, CR-01 em 125-REVIEW.md) para este
 * ecrã, que tem TRÊS mutações em vez de uma (criar, renomear, apagar um papel) em vez do
 * caso único de moldes (criar). Reconcilia o estado local de edição com um payload fresco da
 * query, SEM apagar edições ainda por gravar.
 *
 * Decisão herdada de moldes, nunca alterada aqui: MERGE, nunca substituição cega.
 *   - Um papel que o operador NUNCA tocou (`touchedPapelIds` não o contém), incluindo um papel
 *     criado agora mesmo, é sempre inicializado a partir do payload fresco.
 *   - Um papel que o operador tocou mantém o valor LOCAL, mesmo que o valor do servidor para
 *     esse mesmo papel também tenha mudado entretanto. Perder uma edição não gravada em
 *     silêncio é exatamente o defeito que esta função existe para eliminar.
 *   - Um papel tocado que deixou de existir no payload fresco (apagado por este mesmo operador
 *     ou por outro, noutra sessão) é simplesmente ignorado -- não há nada para preservar, e
 *     apagar o papel atualmente em edição não pode lançar (throw).
 *
 * Três cláusulas que esta função acrescenta em relação ao caso de moldes (UI-SPEC §6), porque
 * este ecrã introduz renomear e apagar, que moldes não tinha:
 *
 *   1. RENOMEAR um papel DIFERENTE do que está a ser editado nunca apaga a edição não gravada
 *      desse outro papel. Isto vale automaticamente e SÓ PORQUE o mapa acima é indexado por
 *      `id`, nunca por `nome` -- renomear muda exactamente o campo em que uma implementação
 *      descuidada poderia ser tentada a indexar. Esta é a ÚNICA falha nova que este ecrã
 *      introduz relativamente a moldes (que nunca tinha renomear): se algum dia alguém
 *      "simplificar" este mapa para `Record<string /* nome *\/, Set<string>>`, renomear um
 *      papel enquanto outro papel tem edições por gravar passa a perder essas edições em
 *      silêncio -- exactamente o bug que o teste de renomear neste ficheiro existe para
 *      prevenir. O próprio papel renomeado continua endereçável sob a MESMA chave (o seu id
 *      não muda), por isso a sua própria entrada no mapa também sobrevive intacta.
 *   2. APAGAR um papel DIFERENTE do que está a ser editado também não apaga a edição de outro
 *      papel -- mesmo raciocínio de "criar" em moldes, generalizado: a entrada de
 *      `touchedPapelIds` do papel apagado é simplesmente descartada (o papel desaparece do
 *      payload fresco), todas as outras permanecem intocadas.
 *   3. Um papel tocado que desaparece do payload fresco (porque foi apagado, incluindo o
 *      próprio papel que o operador estava a editar) é ignorado em vez de ser "ressuscitado" --
 *      é o que garante que apagar o papel atualmente em edição nunca pode lançar.
 */
export function mesclarEstadoLocal(
  data: OfficeRbac,
  prevLocal: LocalPermissoesPapeis | null,
  touchedPapelIds: ReadonlySet<string>,
): LocalPermissoesPapeis {
  const estadoFresco = construirEstadoLocal(data);
  if (!prevLocal) {
    return estadoFresco;
  }
  for (const papelId of touchedPapelIds) {
    const edicaoLocal = prevLocal[papelId];
    if (edicaoLocal && estadoFresco[papelId] !== undefined) {
      estadoFresco[papelId] = edicaoLocal;
    }
  }
  return estadoFresco;
}
