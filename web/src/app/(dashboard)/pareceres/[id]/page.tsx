"use client";

import * as React from "react";
import { Paperclip } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useAdminUsers } from "@/hooks/use-admin";
import { usePermissions } from "@/hooks/use-permissions";
import {
  useDownloadParecerAnexo,
  useParecer,
  useParecerVersoes,
} from "@/hooks/use-pareceres";
import { toast } from "@/hooks/use-toast";
import type { ParecerStatus } from "@/types/pareceres";

type PageProps = { params: Promise<{ id: string }> };

function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}

export default function ParecerDetailPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canView = permissions.can.view("pareceres");

  if (!permissions.isLoading && !canView) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de pareceres."
        backHref="/pareceres"
      />
    );
  }

  return <ParecerDetailContent id={id} />;
}

function AnexoLink({ solicitacaoId, versaoId, caminhoAnexo }: { solicitacaoId: string; versaoId: string; caminhoAnexo?: string }) {
  const download = useDownloadParecerAnexo(solicitacaoId, versaoId);

  if (!caminhoAnexo) {
    return <span className="text-xs text-slate-400 dark:text-slate-500">Sem anexo</span>;
  }

  const onDownload = async () => {
    try {
      const r = await download.mutateAsync();
      window.open(r.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao fazer download";
      toast.error(msg);
    }
  };

  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      className="h-7 rounded-none text-xs font-bold"
      onClick={onDownload}
      disabled={download.isPending}
    >
      <Paperclip className="h-3 w-3" />
      {download.isPending ? "A preparar..." : "Descarregar anexo"}
    </Button>
  );
}

function ParecerDetailContent({ id }: { id: string }) {
  const parecer = useParecer(id);
  const versoes = useParecerVersoes(id);
  const adminUsers = useAdminUsers();

  const userNomeById = React.useMemo(
    () => new Map((adminUsers.data ?? []).map((u) => [u.id, u.nome] as const)),
    [adminUsers.data],
  );

  const resolveUserNome = (userId: string) =>
    adminUsers.isLoading ? "—" : userNomeById.get(userId) ?? userId;

  const isLoading = parecer.isLoading;
  const isError = parecer.isError;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
          Parecer Jurídico
        </h1>
      </div>

      {isLoading ? (
        <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          Não foi possível carregar as solicitações. Verifique a ligação e tente novamente.
        </div>
      ) : parecer.data ? (
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="font-bold">Dados</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                <dt className="text-slate-500 dark:text-slate-400">Cliente</dt>
                <dd className="col-span-2 font-medium">{parecer.data.clienteId}</dd>

                <dt className="text-slate-500 dark:text-slate-400">Advogado</dt>
                <dd className="col-span-2">
                  {parecer.data.advogadoId ? resolveUserNome(parecer.data.advogadoId) : "—"}
                </dd>

                <dt className="text-slate-500 dark:text-slate-400">Estado</dt>
                <dd className="col-span-2">
                  <Badge variant={statusVariant(parecer.data.status)} className="rounded-none font-bold tracking-wide">
                    {parecer.data.status}
                  </Badge>
                </dd>

                <dt className="text-slate-500 dark:text-slate-400">Prioridade</dt>
                <dd className="col-span-2">{parecer.data.prioridade ?? "—"}</dd>

                <dt className="text-slate-500 dark:text-slate-400">Prazo</dt>
                <dd className="col-span-2">{formatDate(parecer.data.prazo)}</dd>

                <dt className="text-slate-500 dark:text-slate-400">Criado</dt>
                <dd className="col-span-2">{formatDateTime(parecer.data.createdAt)}</dd>
              </dl>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="font-bold">Versões</CardTitle>
            </CardHeader>
            <CardContent>
              {versoes.isLoading ? (
                <div className="space-y-4">
                  <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                  <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                </div>
              ) : versoes.isError ? (
                <p className="text-red-600 text-sm py-4">
                  Não foi possível carregar as solicitações. Verifique a ligação e tente novamente.
                </p>
              ) : !versoes.data?.length ? (
                <div className="py-12 text-center">
                  <p className="text-sm font-medium text-slate-500">Nenhuma versão ainda</p>
                  <p className="text-xs text-slate-400 mt-1">
                    Aguarda elaboração pelo advogado atribuído.
                  </p>
                </div>
              ) : (
                <div className="relative">
                  {versoes.data.map((versao, index) => {
                    const isLast = index === versoes.data.length - 1;
                    const autorNome = versao.criadoPorId ? resolveUserNome(versao.criadoPorId) : "—";
                    return (
                      <div key={versao.id} className="relative flex gap-3 py-4">
                        <div className="relative flex flex-col items-center">
                          <span className="h-2.5 w-2.5 rounded-full shrink-0 bg-blue-600" />
                          {!isLast ? (
                            <div className="absolute top-3 bottom-0 left-[5px] w-0.5 bg-slate-200 dark:bg-slate-700" />
                          ) : null}
                        </div>
                        <div className="min-w-0 flex-1 pb-4">
                          <div className="flex items-center justify-between gap-2 flex-wrap">
                            <p className="text-sm font-medium text-slate-900 dark:text-white">
                              Versão {versao.numeroVersao}
                            </p>
                            <p className="text-xs text-slate-500 dark:text-slate-400">
                              {formatDateTime(versao.createdAt)}
                            </p>
                          </div>
                          <p className="text-xs text-slate-400 dark:text-slate-500 italic mt-0.5">
                            {autorNome}
                          </p>
                          {versao.conteudo ? (
                            <p className="text-sm text-slate-600 dark:text-slate-400 mt-1 whitespace-pre-wrap">
                              {versao.conteudo}
                            </p>
                          ) : null}
                          <div className="mt-2">
                            <AnexoLink
                              solicitacaoId={id}
                              versaoId={versao.id}
                              caminhoAnexo={versao.caminhoAnexo}
                            />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}
