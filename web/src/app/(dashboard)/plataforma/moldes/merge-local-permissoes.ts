import type { MoldesConsola } from "@/types/platform-moldes";

/**
 * Estado local de edição da matriz permissão × molde: por `moldeId`, o
 * conjunto (chaves técnicas) de permissões atualmente marcadas no ecrã --
 * não necessariamente já gravado no servidor.
 */
export type LocalPermissoes = Record<number, Set<string>>;

/** Inicializa o estado local a partir de um payload da query, sem nenhuma edição por cima. */
export function construirEstadoLocal(data: MoldesConsola): LocalPermissoes {
  const estado: LocalPermissoes = {};
  for (const molde of data.moldes) {
    estado[molde.id] = new Set(molde.permissoes);
  }
  return estado;
}

/**
 * CR-01 (125-REVIEW.md): reconcilia o estado local de edição com um payload
 * fresco da query, SEM apagar edições ainda por gravar.
 *
 * Antes desta função existir, `moldes/page.tsx` reconstruía
 * `localPermissoes` do zero (`construirEstadoLocal(data)`) sempre que a
 * REFERÊNCIA de `data` mudava -- o que acontece não só depois de uma
 * gravação da própria matriz, mas também depois de qualquer invalidação de
 * `MOLDES_LIST_KEY`, incluindo a que `useCreateMolde` dispara ao criar um
 * molde novo (uma ação completamente alheia à matriz). Um operador que
 * tivesse marcado checkboxes sem gravar via "Guardar Alterações" via essas
 * marcações desaparecerem em silêncio assim que criasse um molde novo.
 *
 * Decisão tomada aqui: MERGE, nunca substituição cega.
 *   - Um molde que o operador NUNCA tocou (`touchedMoldeIds` não o contém),
 *     incluindo um molde criado agora mesmo, é sempre inicializado a partir
 *     do payload fresco -- o comportamento original e correto para esse
 *     caso.
 *   - Um molde que o operador tocou mantém o valor LOCAL, mesmo que o valor
 *     do servidor para esse mesmo molde também tenha mudado entretanto
 *     (ex.: outro PLATAFORMA_ADMIN gravou o mesmo molde em paralelo, ou o
 *     próprio operador criou um molde novo com o mesmo id — impossível na
 *     prática, mas o comportamento teria de ser o mesmo). Perder uma edição
 *     não gravada em silêncio é exatamente o defeito que esta função existe
 *     para eliminar; um valor local que fique, por instantes, desalinhado
 *     do servidor é recuperável -- o próprio diff em `moldesAlterados`,
 *     recalculado sempre contra o `data` mais recente, continua a mostrar
 *     exatamente o que vai ser gravado quando o operador confirmar. Apagar
 *     o trabalho do operador não é recuperável.
 *   - Um molde tocado que deixou de existir no payload fresco (sem
 *     mecanismo de apagar nesta fase, mas escrito defensivamente para o
 *     futuro) é simplesmente ignorado -- não há nada para preservar.
 */
export function mesclarEstadoLocal(
  data: MoldesConsola,
  prevLocal: LocalPermissoes | null,
  touchedMoldeIds: ReadonlySet<number>,
): LocalPermissoes {
  const estadoFresco = construirEstadoLocal(data);
  if (!prevLocal) {
    return estadoFresco;
  }
  for (const moldeId of touchedMoldeIds) {
    const edicaoLocal = prevLocal[moldeId];
    if (edicaoLocal && estadoFresco[moldeId] !== undefined) {
      estadoFresco[moldeId] = edicaoLocal;
    }
  }
  return estadoFresco;
}
