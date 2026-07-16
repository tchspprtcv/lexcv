"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { DataTable } from "@/components/shared/data-table/data-table";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { columns } from "./columns";
import { useDeleteDocumento, useDocumentos } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { documentosFiltersFormSchema, type DocumentosFiltersFormValues } from "@/schemas/documentos";
import type { DocumentosListFilters } from "@/types/documentos";

export default function DocumentosPage() {
  const permissions = usePermissions();
  const canViewDocumentos = permissions.can.view("documentos");
  const canCreateDocumentos = permissions.can.create("documentos");
  const canEditDocumentos = permissions.can.edit("documentos");

  const form = useForm<DocumentosFiltersFormValues>({
    resolver: zodResolver(documentosFiltersFormSchema),
    defaultValues: { processo_id: "", cliente_id: "" },
  });

  if (!permissions.isLoading && !canViewDocumentos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar documentos."
        backHref="/dashboard"
      />
    );
  }

  return (
    <DocumentosContent
      canCreateDocumentos={canCreateDocumentos}
      canEditDocumentos={canEditDocumentos}
      form={form}
    />
  );
}

function DocumentosContent({
  canCreateDocumentos,
  canEditDocumentos,
  form,
}: {
  canCreateDocumentos: boolean;
  canEditDocumentos: boolean;
  form: ReturnType<typeof useForm<DocumentosFiltersFormValues>>;
}) {
  const [filters, setFilters] = React.useState<DocumentosListFilters>({});
  const list = useDocumentos(filters);
  const tableColumns = React.useMemo(() => columns(canEditDocumentos), [canEditDocumentos]);

  const onSubmit = (values: DocumentosFiltersFormValues) => {
    setFilters({ processo_id: values.processo_id, cliente_id: values.cliente_id });
  };

  const onClear = () => {
    form.reset({ processo_id: "", cliente_id: "" });
    setFilters({});
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Documentos</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Upload, consulta e gestão de documentos.
          </p>
        </div>

        {canCreateDocumentos ? (
          <Button asChild>
            <Link href="/documentos/novo">Upload</Link>
          </Button>
        ) : null}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filtros</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4 sm:grid-cols-3" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-2">
              <Label htmlFor="processo_id">Processo ID</Label>
              <Input id="processo_id" {...form.register("processo_id")} placeholder="Ex.: 7c8b..." />
              {form.formState.errors.processo_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.processo_id.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="cliente_id">Cliente ID</Label>
              <Input id="cliente_id" {...form.register("cliente_id")} placeholder="Ex.: 1a2b..." />
              {form.formState.errors.cliente_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.cliente_id.message}</p>
              ) : null}
            </div>

            <div className="flex items-end gap-2">
              <Button type="submit" variant="secondary" className="w-full">
                Filtrar
              </Button>
              <Button type="button" variant="outline" className="w-full" onClick={onClear}>
                Limpar
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Resultados</CardTitle>
        </CardHeader>
        <CardContent>
          {list.isPending ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
          ) : list.isError ? (
            <div className="text-sm text-red-600">
              {list.error instanceof Error ? list.error.message : "Erro ao carregar"}
            </div>
          ) : !list.data?.length ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">
              Nenhum documento encontrado.
            </div>
          ) : (
            <>
            <div className="hidden md:block">
              <DataTable columns={tableColumns} data={list.data} />
            </div>
            <div className="md:hidden divide-y divide-neutral-200 dark:divide-neutral-800">
              {list.data.map((d) => (
                <DocumentoMobileCard
                  key={d.id}
                  id={d.id}
                  nome={d.nome}
                  tipo={d.tipo}
                  processoId={d.processo_id}
                  createdAt={d.created_at}
                  canEditDocumentos={canEditDocumentos}
                />
              ))}
            </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DocumentoMobileCard({
  id,
  nome,
  tipo,
  processoId,
  createdAt,
  canEditDocumentos,
}: {
  id: string;
  nome: string;
  tipo?: string;
  processoId?: string;
  createdAt: string;
  canEditDocumentos: boolean;
}) {
  const del = useDeleteDocumento(id);
  const [error, setError] = React.useState<string | null>(null);

  const onDelete = async () => {
    setError(null);
    const ok = window.confirm("Apagar este documento?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Documento apagado com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
      setError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <Link
          href={`/documentos/${encodeURIComponent(id)}`}
          className="font-bold text-slate-900 dark:text-white text-sm leading-tight hover:underline"
        >
          {nome}
        </Link>
        {tipo && (
          <Badge variant="blue" className="rounded-none font-bold text-[10px] flex-shrink-0">
            {tipo}
          </Badge>
        )}
      </div>
      {processoId && (
        <div className="text-[11px] text-neutral-500 dark:text-neutral-400">
          Processo: <span className="font-medium text-neutral-700 dark:text-neutral-300">{processoId}</span>
        </div>
      )}
      <div className="text-[11px] text-neutral-500 dark:text-neutral-400">
        Enviado: {createdAt ? new Date(createdAt).toLocaleDateString("pt-CV") : "—"}
      </div>
      {error ? <div className="text-xs text-red-600">{error}</div> : null}
      <div className="pt-1 flex gap-2 flex-wrap">
        <a
          href={`/api/v1/documentos/${id}/download`}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline min-h-[44px] px-2"
        >
          Download
        </a>
        {canEditDocumentos && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onDelete}
            disabled={del.isPending}
            className="text-red-600 border-red-200 hover:bg-red-50 dark:text-red-400 dark:border-red-800 dark:hover:bg-red-900/20 min-h-[44px]"
          >
            {del.isPending ? "A apagar..." : "Apagar"}
          </Button>
        )}
      </div>
    </div>
  );
}
