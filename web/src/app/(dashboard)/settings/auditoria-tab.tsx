"use client";

import * as React from "react";
import { X } from "lucide-react";

import { Combobox, type ComboboxOption } from "@/components/shared/combobox";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Label } from "@/components/ui/label";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { useAdminUsers, useOfficeRbac, useOfficeRbacAuditoria } from "@/hooks/use-admin";
import { usePermissions } from "@/hooks/use-permissions";
import {
  auditoriaCategoriaToLabel,
  auditoriaEventoToSentence,
  auditoriaPermissoesDetalhe,
} from "@/lib/auditoria-rbac";
import type { AuditoriaRbacEntry } from "@/types/auditoria-rbac";

// Aba de leitura "Auditoria" (128-UI-SPEC.md §2-4, AUDT-01/02/03/04). Read-Only Guarantee
// (vinculativa): esta aba NAO chama, importa nem referencia nenhum hook de mutacao contra o
// endpoint de auditoria -- nenhum hook de gravacao do TanStack Query, nenhum menu suspenso por
// linha, nem qualquer chamada de rede que altere dados em todo este ficheiro.
// web/scripts/verify-auditoria-rbac.mjs (Task 2) prova esta ausencia de forma automatizada.

const PAGE_SIZE = 20;

/** Reutilizado verbatim de processos/[id]/page.tsx:190-195 (toLocaleString("pt-CV")). */
function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}

export function AuditoriaTab() {
  // Regra dos Hooks: todos os hooks abaixo tem de ser chamados incondicionalmente. Este
  // componente nao tem early return de isLoading/isError a nivel de topo -- os estados de
  // carregamento/erro/vazio sao tratados dentro do Card "Resultados" (128-UI-SPEC.md §2), nunca
  // trocando o componente inteiro -- por isso nao ha risco de um hook novo cair depois de um
  // return condicional, mas a disciplina e mantida de qualquer forma.
  const { can } = usePermissions();
  const hasUsersManage = can.manage("users");

  const [utilizadorAlvoId, setUtilizadorAlvoId] = React.useState("");
  const [papelId, setPapelId] = React.useState("");
  const [page, setPage] = React.useState(0);

  // GET /admin/users e protegido por users:manage, nao rbac:manage (128-UI-SPEC.md, Task 1
  // action) -- um administrador que so tenha rbac:manage nao deve ver um toast 403 ao abrir esta
  // aba, por isso a query fica desativada e o filtro de utilizador mostra apenas "Todos os
  // utilizadores". Limitacao conhecida, documentada no SUMMARY deste plano: (a) um caller so com
  // rbac:manage nao consegue filtrar por utilizador alvo especifico; (b) mesmo com
  // users:manage, um utilizador entretanto eliminado (hard delete, AdminController.deleteUser)
  // deixa de aparecer nesta lista e por isso nao e selecionavel no filtro, embora os seus
  // eventos antigos continuem visiveis na lista nao filtrada (o nome fica gravado em
  // `alvoNome`, resolvido no momento da escrita).
  const adminUsers = useAdminUsers({ enabled: hasUsersManage });
  const officeRbac = useOfficeRbac();
  const auditoria = useOfficeRbacAuditoria({
    utilizadorAlvoId: utilizadorAlvoId || undefined,
    papelId: papelId || undefined,
    page,
    size: PAGE_SIZE,
  });

  const rotulosPermissoes = React.useMemo(() => {
    const mapa: Record<string, string> = {};
    for (const perm of officeRbac.data?.permissoes ?? []) {
      mapa[perm.key] = perm.nome;
    }
    return mapa;
  }, [officeRbac.data?.permissoes]);

  const utilizadorOptions: ComboboxOption[] = React.useMemo(
    () => [
      { value: "", label: "Todos os utilizadores" },
      ...(adminUsers.data ?? []).map((u) => ({ value: u.id, label: u.nome })),
    ],
    [adminUsers.data],
  );

  const papeis = officeRbac.data?.papeis ?? [];

  // Clamp de `page` de volta ao intervalo valido sempre que o servidor reporta menos paginas do
  // que a actualmente seleccionada -- mesmo padrao de notificacoes/page.tsx:80-86 (estado
  // ajustado durante o render, nunca useEffect + setState, que o ESLint deste projecto rejeita
  // via react-hooks/set-state-in-effect).
  const [lastTotalPages, setLastTotalPages] = React.useState(auditoria.data?.totalPages);
  if (auditoria.data && auditoria.data.totalPages !== lastTotalPages) {
    setLastTotalPages(auditoria.data.totalPages);
    if (auditoria.data.totalPages > 0 && page >= auditoria.data.totalPages) {
      setPage(auditoria.data.totalPages - 1);
    }
  }

  const hasFilters = utilizadorAlvoId !== "" || papelId !== "";

  const onUtilizadorAlvoChange = (value: string) => {
    setUtilizadorAlvoId(value);
    setPage(0);
  };

  const onPapelChange = (value: string) => {
    setPapelId(value);
    setPage(0);
  };

  // "Limpar filtros" reinicia apenas o ESTADO DOS FILTROS (e a pagina, para nao ficar preso numa
  // pagina que deixou de existir) -- nunca o registo em si. Read-Only Guarantee (128-UI-SPEC.md):
  // este botao nao pode ser confundivel com "limpar o log".
  const onLimparFiltros = () => {
    setUtilizadorAlvoId("");
    setPapelId("");
    setPage(0);
  };

  return (
    <div className="space-y-6">
      {/* 128-UI-REVIEW.md (correccao 1): a tabela de tipografia do 128-UI-SPEC.md declara tres
          CardTitle, incluindo "Auditoria de Atribuicoes de Papeis", mas o diagrama de estrutura
          da mesma spec so desenha dois cartoes -- contradicao interna. Resolvida a favor do
          cabecalho: todas as outras abas das Definicoes identificam o proprio ecra no primeiro
          titulo que mostram ("Utilizadores Registados", "Papeis do Escritorio"), e sem ele esta
          aba abria em "Filtros", sem dizer filtros de que. */}
      <div className="space-y-1">
        <h2 className="text-xl font-semibold">Auditoria de Atribuições de Papéis</h2>
        <p className="text-sm text-slate-500">
          Registo permanente de quem alterou papéis e permissões neste escritório, e quando. Só de
          consulta: nenhum registo pode ser editado ou apagado.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Filtros</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="auditoria-utilizador-alvo-filter">Utilizador alvo</Label>
            <Combobox
              id="auditoria-utilizador-alvo-filter"
              value={utilizadorAlvoId}
              onChange={onUtilizadorAlvoChange}
              options={utilizadorOptions}
              placeholder="Todos os utilizadores"
              searchPlaceholder="Pesquisar utilizador por nome..."
              loading={hasUsersManage && adminUsers.isPending}
              disabled={!hasUsersManage}
            />
            {/* 128-UI-REVIEW.md (correccao 3): a lista de utilizadores exige users:manage, por
                isso um administrador so com rbac:manage -- publico legitimo desta aba -- via um
                filtro com uma unica opcao e nenhuma explicacao. A limitacao mantem-se (evita um
                403 ao abrir a aba), mas deixa de ser silenciosa. */}
            {!hasUsersManage ? (
              <p className="text-sm text-slate-500">
                Filtrar por utilizador exige a permissão de gestão de utilizadores. Os eventos de
                todos os utilizadores continuam listados abaixo.
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="auditoria-papel-filter">Papel</Label>
            <select
              id="auditoria-papel-filter"
              className="h-10 w-full bg-white dark:bg-[#020617] border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={papelId}
              onChange={(e) => onPapelChange(e.target.value)}
            >
              <option value="">Todos os papéis</option>
              {papeis.map((papel) => (
                <option key={papel.id} value={papel.id}>
                  {papel.nome}
                </option>
              ))}
            </select>
          </div>

          {hasFilters ? (
            <div className="sm:col-span-2">
              <Button type="button" variant="ghost" size="sm" onClick={onLimparFiltros}>
                <X className="h-4 w-4" />
                Limpar filtros
              </Button>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Resultados</CardTitle>
        </CardHeader>
        <CardContent>
          {auditoria.isPending ? (
            <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>
          ) : auditoria.isError ? (
            <div className="text-sm text-red-600">
              Não foi possível carregar o registo de auditoria. Tente novamente.
            </div>
          ) : !auditoria.data?.content.length ? (
            hasFilters ? (
              <div className="space-y-2 py-6 text-center">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Nenhum evento encontrado para os filtros selecionados.
                </p>
                <Button type="button" variant="ghost" size="sm" onClick={onLimparFiltros}>
                  <X className="h-4 w-4" />
                  Limpar filtros
                </Button>
              </div>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>Sem eventos registados</EmptyTitle>
                  <EmptyDescription>
                    As alterações de papéis e atribuições do seu escritório vão aparecer aqui
                    assim que acontecerem.
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )
          ) : (
            <>
              <div className="divide-y divide-slate-100 dark:divide-slate-800">
                {auditoria.data.content.map((entry) => (
                  <AuditoriaRow
                    key={entry.id}
                    entry={entry}
                    rotulosPermissoes={rotulosPermissoes}
                  />
                ))}
              </div>
              {auditoria.data.totalPages > 1 ? (
                <Pagination className="pt-4">
                  <PaginationContent className="w-full justify-between">
                    <PaginationItem>
                      <PaginationPrevious
                        text="Anterior"
                        href="#"
                        aria-disabled={page === 0}
                        className={page === 0 ? "pointer-events-none opacity-50" : undefined}
                        onClick={(e) => {
                          e.preventDefault();
                          if (page === 0) return;
                          setPage((p) => Math.max(0, p - 1));
                        }}
                      />
                    </PaginationItem>
                    <PaginationItem>
                      <span className="px-2 text-sm text-slate-500 dark:text-slate-400">
                        Página {page + 1} de {auditoria.data.totalPages}
                      </span>
                    </PaginationItem>
                    <PaginationItem>
                      <PaginationNext
                        text="Seguinte"
                        href="#"
                        aria-disabled={page + 1 >= auditoria.data.totalPages}
                        className={
                          page + 1 >= auditoria.data.totalPages
                            ? "pointer-events-none opacity-50"
                            : undefined
                        }
                        onClick={(e) => {
                          e.preventDefault();
                          if (page + 1 >= auditoria.data.totalPages) return;
                          setPage((p) => p + 1);
                        }}
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * Uma linha do registo -- a frase e composta por `auditoriaEventoToSentence` (lib puro, Plano
 * 08) em segmentos { texto, destaque }, cada um renderizado como filho de texto de um `<span>`
 * (nunca HTML nao escapado), com `font-semibold` apenas quando `destaque` e verdadeiro.
 * Sem nenhuma accao por linha (nenhum menu, lapis ou caixote) -- a unica superficie interactiva
 * desta aba sao os dois filtros e o paginador acima da lista (Read-Only Guarantee).
 */
function AuditoriaRow({
  entry,
  rotulosPermissoes,
}: {
  entry: AuditoriaRbacEntry;
  rotulosPermissoes: Record<string, string>;
}) {
  const segmentos = auditoriaEventoToSentence(entry);
  const detalhe = auditoriaPermissoesDetalhe(entry, rotulosPermissoes);

  return (
    <div className="flex items-start gap-3 py-3 border-b border-slate-100 dark:border-slate-800">
      <span className="text-xs text-slate-500 dark:text-slate-400 w-40 shrink-0 pt-0.5">
        {formatDateTime(entry.timestamp)}
      </span>
      <div className="flex-1 min-w-0 space-y-1">
        <p className="text-sm text-slate-900 dark:text-slate-100">
          {segmentos.map((segmento, index) => (
            <span key={index} className={segmento.destaque ? "font-semibold" : undefined}>
              {segmento.texto}
            </span>
          ))}
        </p>
        {detalhe ? (
          <p className="text-xs text-slate-500 dark:text-slate-400">{detalhe}</p>
        ) : null}
      </div>
      <Badge variant="outline" className="shrink-0">
        {auditoriaCategoriaToLabel(entry.categoria)}
      </Badge>
    </div>
  );
}
