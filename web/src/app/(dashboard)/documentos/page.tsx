"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDeleteDocumento, useDocumentos } from "@/hooks/use-documentos";
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
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-left text-neutral-500 dark:text-neutral-400">
                  <tr className="border-b border-neutral-200 dark:border-neutral-800">
                    <th className="py-2 pr-4 font-medium">Nome</th>
                    <th className="py-2 pr-4 font-medium">Tipo</th>
                    <th className="py-2 pr-4 font-medium">Processo</th>
                    <th className="py-2 pr-4 font-medium">Cliente</th>
                    <th className="py-2 pr-4 font-medium">Confid.</th>
                    <th className="py-2 pr-4 font-medium">Ver.</th>
                    <th className="py-2 pr-4 font-medium">Tamanho</th>
                    <th className="py-2 pr-4 font-medium">Criado</th>
                    <th className="py-2 pr-4 font-medium">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {list.data.map((d) => (
                    <DocumentoRow
                      key={d.id}
                      id={d.id}
                      nome={d.nome}
                      tipo={d.tipo}
                      processoId={d.processo_id}
                      clienteId={d.cliente_id}
                      confidencialidade={d.confidencialidade}
                      versao={d.versao}
                      size={d.size}
                      createdAt={d.created_at}
                      canEditDocumentos={canEditDocumentos}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DocumentoRow({
  id,
  nome,
  tipo,
  processoId,
  clienteId,
  confidencialidade,
  versao,
  size,
  createdAt,
  canEditDocumentos,
}: {
  id: string;
  nome: string;
  tipo?: string;
  processoId?: string;
  clienteId?: string;
  confidencialidade?: string;
  versao: number;
  size: number;
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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro ao apagar documento");
    }
  };

  return (
    <tr className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
      <td className="py-2 pr-4">
        <Link
          href={`/documentos/${encodeURIComponent(id)}`}
          className="font-medium text-neutral-900 hover:underline dark:text-neutral-50"
        >
          {nome}
        </Link>
        {error ? <div className="text-xs text-red-600">{error}</div> : null}
      </td>
      <td className="py-2 pr-4">{tipo ?? "—"}</td>
      <td className="py-2 pr-4">{processoId ?? "—"}</td>
      <td className="py-2 pr-4">{clienteId ?? "—"}</td>
      <td className="py-2 pr-4">{confidencialidade ?? "PUBLICO"}</td>
      <td className="py-2 pr-4">v{versao ?? 1}</td>
      <td className="py-2 pr-4">{size.toLocaleString("pt-CV")} bytes</td>
      <td className="py-2 pr-4">{new Date(createdAt).toLocaleString("pt-CV")}</td>
      <td className="py-2 pr-4">
        {canEditDocumentos ? (
          <Button type="button" variant="outline" onClick={onDelete} disabled={del.isPending}>
            {del.isPending ? "A apagar..." : "Apagar"}
          </Button>
        ) : null}
      </td>
    </tr>
  );
}
