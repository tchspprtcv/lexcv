"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { useForm } from "react-hook-form";
import { Paperclip } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import { useAdminUsers } from "@/hooks/use-admin";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";
import {
  useCreateParecerVersao,
  useDownloadParecerAnexo,
  useParecer,
  useParecerVersoes,
} from "@/hooks/use-pareceres";
import {
  parecerVersaoCreateFormSchema,
  type ParecerVersaoCreateFormValues,
} from "@/schemas/pareceres";
import type { ParecerStatus } from "@/types/pareceres";

type PageProps = { params: Promise<{ id: string }> };

function createFileList(file: File): FileList {
  const dt = new DataTransfer();
  dt.items.add(file);
  return dt.files;
}

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

  return <ParecerDetailContent id={id} permissions={permissions} />;
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
    } catch {
      // apiFetch already surfaces a toast for this failure; nothing else to do here.
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

function ParecerDetailContent({
  id,
  permissions,
}: {
  id: string;
  permissions: ReturnType<typeof usePermissions>;
}) {
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

  const me = permissions.data;
  const canEditPareceres = permissions.can.edit("pareceres");
  const isResponsavelOuAdmin =
    Boolean(me?.roles.includes("ADMIN")) ||
    Boolean(parecer.data?.advogadoId && parecer.data.advogadoId === me?.id);
  const isConcluido = parecer.data?.status === "CONCLUIDO";
  const showNovaVersaoForm = canEditPareceres && isResponsavelOuAdmin && !isConcluido;

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

          {isConcluido ? (
            <Card>
              <CardContent className="py-6">
                <p className="text-sm font-medium text-slate-900 dark:text-white">
                  Parecer já entregue
                </p>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  Não é possível submeter novas versões após a entrega final.
                </p>
              </CardContent>
            </Card>
          ) : showNovaVersaoForm ? (
            <NovaVersaoForm solicitacaoId={id} />
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function NovaVersaoForm({ solicitacaoId }: { solicitacaoId: string }) {
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [progresso, setProgresso] = React.useState<number | null>(null);

  const versaoUpload = useCreateParecerVersao(solicitacaoId, {
    onProgress: (pct) => setProgresso(pct),
  });

  const form = useForm<ParecerVersaoCreateFormValues>({
    resolver: zodResolver(parecerVersaoCreateFormSchema),
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setServerError(null);
    const file = values.file.item(0);
    if (!file) return;

    try {
      await versaoUpload.mutateAsync({ conteudo: values.conteudo, file });
      setProgresso(null);
      form.reset({ conteudo: "", file: undefined as unknown as FileList });
      toast.success("Nova versão submetida com sucesso.");
    } catch (e) {
      setProgresso(null);
      const msg =
        e instanceof Error
          ? e.message
          : "Não foi possível submeter a versão. Verifique a ligação e tente novamente.";
      setServerError(msg);
      toast.error(msg);
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg font-bold">Nova Versão</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-2">
            <Label htmlFor="conteudo">Resumo da versão</Label>
            <Textarea
              id="conteudo"
              placeholder="Descreva resumidamente o conteúdo desta versão (o parecer completo é submetido como anexo)."
              disabled={form.formState.isSubmitting || versaoUpload.isPending}
              {...form.register("conteudo")}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Este campo é um resumo — o documento completo deve ser enviado como anexo.
            </p>
            {form.formState.errors.conteudo ? (
              <p className="text-sm text-red-600">{form.formState.errors.conteudo.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label>Anexo (obrigatório)</Label>
            <FileDropZone
              onFileChange={(file) =>
                form.setValue("file", createFileList(file), { shouldValidate: true })
              }
              accept="image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.txt"
              disabled={form.formState.isSubmitting || versaoUpload.isPending}
            >
              Arraste um ficheiro para aqui ou clique para selecionar
            </FileDropZone>

            {progresso === null ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Formatos aceites: PDF, Word, imagens.
              </p>
            ) : (
              <div className="space-y-1">
                <div className="flex justify-between text-xs text-neutral-500">
                  <span>A enviar...</span>
                  <span>{progresso}%</span>
                </div>
                <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
                  <div
                    className="h-2 rounded-full bg-blue-600 transition-all"
                    style={{ width: `${progresso}%` }}
                  />
                </div>
              </div>
            )}

            {form.formState.errors.file ? (
              <p className="text-sm text-red-600">{form.formState.errors.file.message}</p>
            ) : null}
          </div>

          {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

          <Button type="submit" disabled={form.formState.isSubmitting || versaoUpload.isPending}>
            {form.formState.isSubmitting || versaoUpload.isPending
              ? "A submeter..."
              : "Submeter Versão"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
