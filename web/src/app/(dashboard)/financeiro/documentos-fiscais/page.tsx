"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import type { PaginationState, Updater, VisibilityState } from "@tanstack/react-table";
import { ArrowLeft, X } from "lucide-react";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Combobox } from "@/components/shared/combobox";
import { DataTable } from "@/components/shared/data-table/data-table";
import { useClientes } from "@/hooks/use-clientes";
import { podeLerDocumentosFiscais, useDocumentosFiscais } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import type { DocumentosFiscaisFiltros, EstadoComunicacaoFiscal, TipoDocumentoFiscal } from "@/types/faturacao";

import { columns } from "./columns";

// Lista "Documentos fiscais" (134-UI-SPEC Surface 3; D-16, D-17, EMIS-11). Filtros e paginação
// vivem nos search params do URL e são aplicados no servidor; o backend só devolve documentos do
// tenant do utilizador. Página só de leitura: não existe nenhuma ação de alteração.

const TAMANHOS = [10, 20, 50] as const;
const ERRO_PERIODO = "A data final não pode ser anterior à inicial.";
const ISO_DATA = /^\d{4}-\d{2}-\d{2}$/;

function lerInteiro(valor: string | null, omissao: number) {
  if (!valor || !/^\d+$/.test(valor)) return omissao;
  return Number.parseInt(valor, 10);
}

function lerData(valor: string | null) {
  return valor && ISO_DATA.test(valor) ? valor : "";
}

export default function DocumentosFiscaisPage() {
  // useSearchParams obriga a uma fronteira Suspense para o prerender estático (Next 16).
  return (
    <React.Suspense fallback={null}>
      <DocumentosFiscaisConteudo />
    </React.Suspense>
  );
}

function DocumentosFiscaisConteudo() {
  const permissions = usePermissions();
  const podeLer = podeLerDocumentosFiscais(permissions.permissions);
  const canViewClientes = permissions.can.view("clientes");

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const clienteId = searchParams.get("clienteId") ?? "";
  const de = lerData(searchParams.get("de"));
  const ate = lerData(searchParams.get("ate"));
  const tipo: TipoDocumentoFiscal | "" = searchParams.get("tipo") === "FR" ? "FR" : "";
  const estado: EstadoComunicacaoFiscal | "" = searchParams.get("estado") === "PENDENTE" ? "PENDENTE" : "";
  const pagina = Math.max(lerInteiro(searchParams.get("page"), 1), 1) - 1;
  const tamanhoLido = lerInteiro(searchParams.get("size"), 10);
  const tamanho = (TAMANHOS as readonly number[]).includes(tamanhoLido) ? tamanhoLido : 10;

  const periodoInvalido = Boolean(de && ate && ate < de);
  const filtrosAtivos = Boolean(clienteId || de || ate || tipo || estado);

  const filtros: DocumentosFiscaisFiltros = { clienteId, de, ate, tipo, estado, page: pagina, size: tamanho };
  const documentos = useDocumentosFiscais(filtros, podeLer && !periodoInvalido);

  const atualizarUrl = React.useCallback(
    (alteracoes: Record<string, string | number | null>, reiniciarPagina: boolean) => {
      const params = new URLSearchParams(searchParams.toString());
      for (const [chave, valor] of Object.entries(alteracoes)) {
        if (valor === null || valor === "") params.delete(chave);
        else params.set(chave, String(valor));
      }
      if (reiniciarPagina) params.delete("page");
      const qs = params.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const mudarFiltro = (chave: string, valor: string) => atualizarUrl({ [chave]: valor }, true);

  const limparFiltros = () =>
    atualizarUrl({ clienteId: null, de: null, ate: null, tipo: null, estado: null }, true);

  // Se o servidor passar a ter menos páginas do que a selecionada, volta à última válida.
  const totalPaginas = documentos.data?.totalPages;
  React.useEffect(() => {
    if (totalPaginas !== undefined && totalPaginas > 0 && pagina >= totalPaginas) {
      atualizarUrl({ page: totalPaginas }, false);
    }
  }, [totalPaginas, pagina, atualizarUrl]);

  const paginacao: PaginationState = { pageIndex: pagina, pageSize: tamanho };
  const onPaginationChange = (updater: Updater<PaginationState>) => {
    const seguinte = typeof updater === "function" ? updater(paginacao) : updater;
    if (seguinte.pageSize !== tamanho) {
      atualizarUrl({ size: seguinte.pageSize === 10 ? null : seguinte.pageSize }, true);
    } else if (seguinte.pageIndex !== pagina) {
      atualizarUrl({ page: seguinte.pageIndex === 0 ? null : seguinte.pageIndex + 1 }, false);
    }
  };

  if (!permissions.isFetched) {
    return null;
  }

  if (!podeLer) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar os documentos fiscais."
        backHref="/financeiro"
      />
    );
  }

  const linhas = documentos.data?.content ?? [];

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold">Documentos fiscais</h1>
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link href="/financeiro">Financeiro</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>Documentos fiscais</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Faturas-recibo emitidas pelo escritório. Os documentos são simulados e não têm validade fiscal.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link href="/financeiro">
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {canViewClientes ? (
          <FiltroCliente valor={clienteId} onChange={(v) => mudarFiltro("clienteId", v)} />
        ) : null}

        <div className="space-y-2 sm:col-span-2">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="filtro-de">De</Label>
              <Input id="filtro-de" type="date" value={de} onChange={(e) => mudarFiltro("de", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="filtro-ate">Até</Label>
              <Input
                id="filtro-ate"
                type="date"
                value={ate}
                aria-invalid={periodoInvalido ? true : undefined}
                aria-describedby={periodoInvalido ? "filtro-periodo-erro" : undefined}
                onChange={(e) => mudarFiltro("ate", e.target.value)}
              />
            </div>
          </div>
          {periodoInvalido ? (
            <p id="filtro-periodo-erro" className="text-xs text-red-600 dark:text-red-400">
              {ERRO_PERIODO}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="filtro-tipo">Tipo</Label>
          <NativeSelect
            id="filtro-tipo"
            className="w-full"
            value={tipo}
            onChange={(e) => mudarFiltro("tipo", e.target.value)}
          >
            <NativeSelectOption value="">Todos</NativeSelectOption>
            <NativeSelectOption value="FR">Fatura-Recibo</NativeSelectOption>
          </NativeSelect>
        </div>

        <div className="space-y-2">
          <Label htmlFor="filtro-estado">Estado</Label>
          <NativeSelect
            id="filtro-estado"
            className="w-full"
            value={estado}
            onChange={(e) => mudarFiltro("estado", e.target.value)}
          >
            <NativeSelectOption value="">Todos</NativeSelectOption>
            <NativeSelectOption value="PENDENTE">Pendente</NativeSelectOption>
          </NativeSelect>
        </div>

        {filtrosAtivos ? (
          <div className="flex items-end">
            <Button type="button" variant="outline" onClick={limparFiltros}>
              <X className="h-4 w-4" />
              Limpar filtros
            </Button>
          </div>
        ) : null}
      </div>

      <Card>
        <CardContent className="p-0">
          {periodoInvalido ? null : documentos.isPending ? (
            <div className="space-y-2 p-6" aria-label="A carregar...">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : documentos.isError ? (
            <div className="space-y-3 p-6">
              <p className="text-sm text-red-600 dark:text-red-400">
                Não foi possível carregar os documentos fiscais. Tente novamente.
              </p>
              <Button type="button" variant="outline" onClick={() => documentos.refetch()}>
                Tentar novamente
              </Button>
            </div>
          ) : linhas.length === 0 && pagina === 0 ? (
            filtrosAtivos ? (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>Nenhum documento encontrado</EmptyTitle>
                  <EmptyDescription>Nenhum documento corresponde aos filtros. Altere ou limpe os filtros.</EmptyDescription>
                </EmptyHeader>
                <EmptyContent>
                  <Button type="button" variant="outline" onClick={limparFiltros}>
                    Limpar filtros
                  </Button>
                </EmptyContent>
              </Empty>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>Ainda não há documentos fiscais</EmptyTitle>
                  <EmptyDescription>
                    Os documentos aparecem aqui quando registar um pagamento de honorários com a faturação ativa.
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )
          ) : (
            <TabelaDocumentos
              linhas={linhas}
              totalPaginas={documentos.data?.totalPages ?? 0}
              paginacao={paginacao}
              onPaginationChange={onPaginationChange}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function TabelaDocumentos({
  linhas,
  totalPaginas,
  paginacao,
  onPaginationChange,
}: {
  linhas: NonNullable<ReturnType<typeof useDocumentosFiscais>["data"]>["content"];
  totalPaginas: number;
  paginacao: PaginationState;
  onPaginationChange: (updater: Updater<PaginationState>) => void;
}) {
  // Montada só no cliente (os dados chegam depois da hidratação): esconder "Ambiente" abaixo de md.
  const [visibilidadeInicial] = React.useState<VisibilityState>(() => ({
    ambiente: typeof window === "undefined" || window.matchMedia("(min-width: 768px)").matches,
  }));

  return (
    <div className="overflow-x-auto">
      <DataTable
        columns={columns}
        data={linhas}
        getRowId={(d) => d.id}
        manualPagination
        pageCount={totalPaginas}
        pagination={paginacao}
        onPaginationChange={onPaginationChange}
        initialColumnVisibility={visibilidadeInicial}
        emptyMessage="Nenhum documento nesta página."
      />
    </div>
  );
}

function FiltroCliente({ valor, onChange }: { valor: string; onChange: (v: string) => void }) {
  const clientes = useClientes({});
  const opcoes = React.useMemo(
    () => [
      { value: "", label: "Todos os clientes" },
      ...(clientes.data ?? []).map((c) => ({ value: c.id, label: c.nome })),
    ],
    [clientes.data],
  );

  return (
    <div className="space-y-2">
      <Label htmlFor="filtro-cliente">Cliente</Label>
      <Combobox
        id="filtro-cliente"
        value={valor}
        onChange={onChange}
        options={opcoes}
        placeholder="Todos os clientes"
        searchPlaceholder="Pesquisar cliente por nome..."
        emptyMessage="Nenhum cliente encontrado."
        loading={clientes.isPending}
      />
    </div>
  );
}
