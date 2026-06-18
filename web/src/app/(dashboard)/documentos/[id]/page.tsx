"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useDeleteDocumento, useDocumento, useDownloadDocumento } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";

type PageProps = {
  params: { id: string };
};

export default function DocumentoDetailPage(props: PageProps) {
  const params = React.use(props.params as unknown as Promise<{ id: string }>);
  const id = params.id;
  const permissions = usePermissions();
  const canViewDocumentos = permissions.can.view("documentos");
  const canEditDocumentos = permissions.can.edit("documentos");

  if (!permissions.isLoading && !canViewDocumentos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este documento."
        backHref="/documentos"
      />
    );
  }

  return <DocumentoDetailContent id={id} canEditDocumentos={canEditDocumentos} />;
}

function DocumentoDetailContent({ id, canEditDocumentos }: { id: string; canEditDocumentos: boolean }) {
  const router = useRouter();
  const doc = useDocumento(id);
  const download = useDownloadDocumento(id);
  const del = useDeleteDocumento(id);
  const [serverError, setServerError] = React.useState<string | null>(null);

  const onDownload = async () => {
    setServerError(null);
    try {
      const res = await download.mutateAsync();
      const url = URL.createObjectURL(res.blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = res.filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success("Download iniciado.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao fazer download";
      setServerError(msg);
      toast.error(msg);
    }
  };

  const onDelete = async () => {
    setServerError(null);
    if (!canEditDocumentos) return;
    const ok = window.confirm("Apagar este documento?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Documento apagado com sucesso.");
      router.push("/documentos");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
      setServerError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Documento</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href="/documentos" className="hover:underline">
              Documentos
            </Link>{" "}
            <span>/</span> <span className="text-neutral-900 dark:text-neutral-50">{id}</span>
          </div>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/documentos">Voltar</Link>
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={onDownload}
            disabled={download.isPending || doc.isLoading}
          >
            {download.isPending ? "A preparar..." : "Download"}
          </Button>
          {canEditDocumentos ? (
            <Button type="button" variant="outline" onClick={onDelete} disabled={del.isPending}>
              {del.isPending ? "A apagar..." : "Apagar"}
            </Button>
          ) : null}
        </div>
      </div>

      {doc.isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : doc.isError ? (
        <div className="text-sm text-red-600">
          {doc.error instanceof Error ? doc.error.message : "Erro ao carregar"}
        </div>
      ) : doc.data ? (
        <Card>
          <CardHeader>
            <CardTitle>Metadata</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
              <dt className="text-neutral-500 dark:text-neutral-400">Nome</dt>
              <dd className="col-span-2 font-medium">{doc.data.nome}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Ficheiro</dt>
              <dd className="col-span-2">{doc.data.filename}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Tipo</dt>
              <dd className="col-span-2">{doc.data.tipo ?? "—"}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Content-Type</dt>
              <dd className="col-span-2">{doc.data.content_type}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Tamanho</dt>
              <dd className="col-span-2">{doc.data.size.toLocaleString("pt-CV")} bytes</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Processo</dt>
              <dd className="col-span-2">{doc.data.processo_id ?? "—"}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Cliente</dt>
              <dd className="col-span-2">{doc.data.cliente_id ?? "—"}</dd>

              <dt className="text-neutral-500 dark:text-neutral-400">Criado</dt>
              <dd className="col-span-2">{new Date(doc.data.created_at).toLocaleString("pt-CV")}</dd>
            </dl>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}
          </CardContent>
        </Card>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Documento não encontrado.</div>
      )}
    </div>
  );
}
