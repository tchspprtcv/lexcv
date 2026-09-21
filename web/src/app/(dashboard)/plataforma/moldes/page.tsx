"use client";

import * as React from "react";
import Link from "next/link";
import { AlertCircle, ArrowLeft, Loader2, RotateCcw, TriangleAlert } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { useMe } from "@/hooks/use-me";
import { useMoldes } from "@/hooks/use-platform-moldes";
import type { MoldesConsola, MoldeSummary } from "@/types/platform-moldes";

// O nome literal do papel de plataforma nunca pode aparecer como molde nesta
// matriz -- o backend ja garante isto (Role.instanciavel=false para
// PLATAFORMA_ADMIN, Plan 03), mas filtramos aqui tambem em defesa de
// profundidade, espelhando a pratica ja usada pelo proprio RbacTab de nunca
// confiar apenas na garantia do servidor.
const NOME_RESERVADO_PLATAFORMA = "PLATAFORMA_ADMIN";

/**
 * Ecrã `/plataforma/moldes` — consola de gestão dos moldes de papel da
 * plataforma, acessível apenas a PLATAFORMA_ADMIN. Edita a matriz
 * permissão × molde que define o conjunto de permissões que cada escritório
 * novo recebe automaticamente ao ser provisionado (MOLD-02, MOLD-03).
 */
export default function MoldesPlataformaPage() {
  // Guarda de página (defesa em profundidade; a camada autoritativa continua
  // a ser o gate de papel de classe do controlador de administração de
  // plataforma, backend, Phase 119). A ordem abaixo não é arbitrária: o
  // branch de carregamento tem de resolver antes do teste de papel, nunca
  // numa condição só — repete aqui a mesma correção que a consola
  // /plataforma e /plataforma/relatorio já aplicam (WR-03, revisão de código
  // da Phase 120). Sem este branch extra, o conteúdo desta página
  // renderizaria para qualquer utilizador autenticado durante a janela
  // anterior ao primeiro resolve de useMe(), disparando o pedido à API
  // antes de o papel do chamador ser conhecido.
  const me = useMe();

  if (!me.isFetched) {
    return null;
  }

  if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Não tem permissão para aceder à gestão de moldes de papel."
        backHref="/dashboard"
      />
    );
  }

  return <MoldesPlataformaContent />;
}

type LocalPermissoes = Record<number, Set<string>>;

function construirEstadoLocal(data: MoldesConsola): LocalPermissoes {
  const estado: LocalPermissoes = {};
  for (const molde of data.moldes) {
    estado[molde.id] = new Set(molde.permissoes);
  }
  return estado;
}

function MoldesPlataformaContent() {
  const moldes = useMoldes();

  // Estado local de edição, mais a referência do último payload já aplicado
  // a ele. Quando a query devolve dados novos (referência diferente), o
  // estado local é reinicializado a partir do payload fresco -- este é o
  // padrão de ajuste de estado em render já estabelecido neste codebase
  // (comparar uma referência do payload aplicado e reinicializar quando
  // mudou), não `useEffect` com `setState`, que o ESLint deste projecto
  // rejeita (`react-hooks/set-state-in-effect`). Como `data` só muda de
  // referência quando o TanStack Query recebe um payload genuinamente novo
  // (fetch inicial ou invalidação pós-gravação), isto nunca apaga
  // silenciosamente uma edição local não gravada a meio de um render normal.
  const [appliedData, setAppliedData] = React.useState<MoldesConsola | null>(null);
  const [localPermissoes, setLocalPermissoes] = React.useState<LocalPermissoes | null>(null);

  const data = moldes.data ?? null;
  if (data && data !== appliedData) {
    setAppliedData(data);
    setLocalPermissoes(construirEstadoLocal(data));
  }

  const moldesFiltrados = React.useMemo<MoldeSummary[]>(() => {
    if (!data) return [];
    return data.moldes.filter((m) => m.nome !== NOME_RESERVADO_PLATAFORMA);
  }, [data]);

  const modulos = React.useMemo(() => {
    if (!data) return [];
    return Array.from(new Set(data.permissoes.map((p) => p.modulo)));
  }, [data]);

  const handleToggle = (moldeId: number, permKey: string) => {
    setLocalPermissoes((prev) => {
      if (!prev) return prev;
      const atual = prev[moldeId] ?? new Set<string>();
      const seguinte = new Set(atual);
      if (seguinte.has(permKey)) {
        seguinte.delete(permKey);
      } else {
        seguinte.add(permKey);
      }
      return { ...prev, [moldeId]: seguinte };
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button
          asChild
          variant="ghost"
          className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white"
        >
          <Link href="/plataforma" aria-label="Voltar">
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-semibold text-slate-900 dark:text-white tracking-tight">
            Moldes de Papel da Plataforma
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Defina os conjuntos de permissões que cada escritório novo recebe automaticamente ao
            ser provisionado.
          </p>
        </div>
      </div>

      <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
          <div>
            <CardTitle className="text-xl font-semibold">Matriz de Moldes</CardTitle>
            <CardDescription>
              Permissões atribuídas a cada molde. Escritórios já provisionados não são afetados
              por alterações feitas aqui.
            </CardDescription>
          </div>
          {/* Slot para o botão "Guardar Alterações" -- acrescentado no
              Plan 05 Task 2 junto do fluxo de gravação/AlertDialog. O botão
              de criação de molde é do Plan 06 e não pertence a este slot. */}
        </CardHeader>

        <CardContent className="space-y-4">
          {/* Banner de não-propagação -- camada 1 de 3 do aviso, permanente e
              nunca condicionado a nenhum estado de gravação. */}
          <div className="p-4 bg-amber-50 dark:bg-amber-500/10 text-slate-700 dark:text-slate-300 text-xs border border-amber-200 dark:border-amber-500/30 rounded-md flex items-start gap-2.5">
            <TriangleAlert className="h-4 w-4 mt-0.5 text-amber-600 dark:text-amber-400 flex-shrink-0" />
            <div>
              <strong>Alterar um molde não atualiza escritórios já criados.</strong> Cada
              escritório recebe, no momento em que é provisionado, uma cópia própria e
              independente de cada molde (snapshot) — editar o molde depois disso não muda essa
              cópia. Só os escritórios provisionados a partir de agora recebem o conjunto de
              permissões que gravar aqui.
            </div>
          </div>

          {moldes.isLoading ? (
            <div className="flex justify-center items-center h-48">
              <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
            </div>
          ) : moldes.isError ? (
            <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
              <AlertCircle className="h-6 w-6 text-red-500" />
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Não foi possível carregar os moldes de papel.
              </p>
              <Button variant="outline" size="sm" onClick={() => moldes.refetch()}>
                <RotateCcw className="h-4 w-4" />
                Tentar novamente
              </Button>
            </div>
          ) : !data || moldesFiltrados.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>Nenhum molde definido</EmptyTitle>
                <EmptyDescription>
                  Crie o primeiro molde para que os próximos escritórios provisionados nasçam com
                  papéis prontos a atribuir.
                </EmptyDescription>
              </EmptyHeader>
              <EmptyContent />
            </Empty>
          ) : (
            <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-md">
              <table className="w-full text-sm text-left border-collapse">
                <thead className="bg-slate-100/80 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th
                      scope="col"
                      className="p-3 font-semibold text-slate-600 dark:text-slate-400 min-w-[280px]"
                    >
                      Módulo / Permissão
                    </th>
                    {moldesFiltrados.map((molde) => (
                      <th
                        key={molde.id}
                        scope="col"
                        className="p-3 font-bold text-center text-slate-700 dark:text-slate-300 text-xs tracking-wider"
                      >
                        <div className="flex flex-col items-center gap-1">
                          <span>{molde.nome}</span>
                          <Badge
                            variant={molde.escritoriosInstanciados > 0 ? "amber" : "gray"}
                            className="text-[10px] font-semibold"
                          >
                            {molde.escritoriosInstanciados > 0
                              ? `${molde.escritoriosInstanciados} escritório${molde.escritoriosInstanciados === 1 ? "" : "s"}`
                              : "0 escritórios"}
                          </Badge>
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                  {modulos.map((mod) => {
                    const permissoesDoModulo = data.permissoes.filter((p) => p.modulo === mod);
                    return (
                      <React.Fragment key={mod}>
                        <tr className="bg-slate-50 dark:bg-slate-900/50">
                          <td
                            colSpan={moldesFiltrados.length + 1}
                            className="px-3 py-2 text-xs font-bold text-slate-500 uppercase tracking-widest"
                          >
                            {mod}
                          </td>
                        </tr>
                        {permissoesDoModulo.map((permissao) => (
                          <tr
                            key={permissao.key}
                            className="hover:bg-slate-50/30 dark:hover:bg-slate-900/10 transition-colors"
                          >
                            <th scope="row" className="p-3 text-left">
                              <div className="font-medium text-slate-900 dark:text-slate-100">
                                {permissao.nome}
                              </div>
                              <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                                {permissao.descricao}
                              </div>
                            </th>
                            {moldesFiltrados.map((molde) => {
                              const isChecked =
                                localPermissoes?.[molde.id]?.has(permissao.key) ?? false;
                              return (
                                <td key={molde.id} className="p-3 text-center">
                                  <label className="inline-flex items-center justify-center cursor-pointer p-2">
                                    <input
                                      type="checkbox"
                                      checked={isChecked}
                                      onChange={() => handleToggle(molde.id, permissao.key)}
                                      aria-label={`${permissao.nome} — ${molde.nome}`}
                                      className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-slate-300 dark:border-slate-800 rounded transition-all cursor-pointer"
                                    />
                                  </label>
                                </td>
                              );
                            })}
                          </tr>
                        ))}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
