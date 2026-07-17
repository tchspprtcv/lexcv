"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { useForm } from "react-hook-form";
import { Paperclip } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import { useAdminUsers } from "@/hooks/use-admin";
import { useCliente } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";
import {
  useCreateParecerVersao,
  useDownloadParecerAnexo,
  useEntregarParecer,
  useParecer,
  useParecerVersoes,
} from "@/hooks/use-pareceres";
import type { ParecerVersao } from "@/types/pareceres";
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

  if (permissions.isFetched && !canView) {
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
  const cliente = useCliente(parecer.data?.clienteId ?? "");

  const userNomeById = React.useMemo(
    () => new Map((adminUsers.data ?? []).map((u) => [u.id, u.nome] as const)),
    [adminUsers.data],
  );

  const resolveUserNome = (userId: string) =>
    adminUsers.isLoading ? "—" : userNomeById.get(userId) ?? userId;

  const clienteNome = cliente.isLoading ? "—" : cliente.data?.nome ?? parecer.data?.clienteId ?? "—";

  const isLoading = parecer.isLoading;
  const isError = parecer.isError;

  const me = permissions.data;
  const canEditPareceres = permissions.can.edit("pareceres");
  const isResponsavelOuAdmin =
    Boolean(me?.roles.includes("ADMIN")) ||
    Boolean(parecer.data?.advogadoId && parecer.data.advogadoId === me?.id);
  const isConcluido = parecer.data?.status === "CONCLUIDO";

  const sortedVersoes = React.useMemo(
    () => [...(versoes.data ?? [])].sort((a, b) => b.numeroVersao - a.numeroVersao),
    [versoes.data],
  );
  const defaultOpenVersaoId = isConcluido
    ? (parecer.data?.versaoFinalId ?? sortedVersoes[0]?.id)
    : sortedVersoes[0]?.id;

  function versaoTooltipLabel(versao: ParecerVersao, index: number): string {
    if (isConcluido) {
      return versao.id === defaultOpenVersaoId ? "Versão entregue" : "Versão anterior";
    }
    return index === 0 ? "Versão atual" : "Versão anterior";
  }

  const showNovaVersaoForm =
    permissions.isFetched && canEditPareceres && isResponsavelOuAdmin && !isConcluido;
  const showEntregarTrigger =
    permissions.isFetched &&
    !versoes.isLoading &&
    canEditPareceres &&
    isResponsavelOuAdmin &&
    !isConcluido;

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
        <div className="grid grid-cols-12 gap-6">
          {/* ── Left column: Dados + Nova Versão / Entregue + Entregar ── */}
          <div className="col-span-12 lg:col-span-8 space-y-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg font-bold">Dados</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                  <dt className="text-slate-500 dark:text-slate-400">Cliente</dt>
                  <dd className="col-span-2 font-medium">{clienteNome}</dd>

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

            {!permissions.isFetched ? (
              <Card>
                <CardContent className="py-6">
                  <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12" />
                </CardContent>
              </Card>
            ) : isConcluido ? (
              <ParecerEntregueBlock
                id={id}
                versaoFinalId={parecer.data.versaoFinalId}
                versoes={versoes.data}
                isLoading={versoes.isLoading}
                resolveUserNome={resolveUserNome}
              />
            ) : showNovaVersaoForm ? (
              <NovaVersaoForm solicitacaoId={id} />
            ) : null}

            {showEntregarTrigger ? (
              <EntregarParecerDialog solicitacaoId={id} versoes={sortedVersoes} />
            ) : null}
          </div>

          {/* ── Right column: Histórico de Versões (descending) ── */}
          <div className="col-span-12 lg:col-span-4">
            <Card className="sticky top-6">
              <CardHeader>
                <CardTitle className="text-lg font-bold">Histórico de Versões</CardTitle>
              </CardHeader>
              <CardContent>
                {versoes.isLoading ? (
                  <div className="space-y-4">
                    <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                    <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                  </div>
                ) : versoes.isError ? (
                  <p className="text-red-600 text-sm py-4">
                    Não foi possível carregar as versões. Verifique a ligação e tente novamente.
                  </p>
                ) : !versoes.data?.length ? (
                  <div className="py-12 text-center">
                    <p className="text-sm font-medium text-slate-500">Nenhuma versão ainda</p>
                    <p className="text-xs text-slate-400 mt-1">
                      Aguarda elaboração pelo advogado atribuído.
                    </p>
                  </div>
                ) : (
                  <Accordion type="single" collapsible defaultValue={defaultOpenVersaoId} className="relative">
                    {sortedVersoes.map((versao, index) => {
                      const isLast = index === sortedVersoes.length - 1;
                      const autorNome = versao.criadoPorId ? resolveUserNome(versao.criadoPorId) : "—";
                      return (
                        <div key={versao.id} className="relative flex gap-3">
                          <div className="relative flex flex-col items-center pt-4">
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <span
                                  tabIndex={0}
                                  aria-label={versaoTooltipLabel(versao, index)}
                                  className="h-2.5 w-2.5 rounded-full shrink-0 bg-slate-400 dark:bg-slate-500"
                                />
                              </TooltipTrigger>
                              <TooltipContent>{versaoTooltipLabel(versao, index)}</TooltipContent>
                            </Tooltip>
                            {!isLast ? (
                              <div className="absolute top-3 bottom-0 left-[5px] w-0.5 bg-slate-200 dark:bg-slate-700" />
                            ) : null}
                          </div>
                          <AccordionItem value={versao.id} className="min-w-0 flex-1">
                            <AccordionTrigger>
                              <div className="flex items-center justify-between gap-2 flex-wrap w-full pr-2">
                                <p className="text-sm font-medium text-slate-900 dark:text-white">
                                  Versão {versao.numeroVersao}
                                </p>
                                <p className="text-xs text-slate-500 dark:text-slate-400">
                                  {formatDateTime(versao.createdAt)}
                                </p>
                              </div>
                            </AccordionTrigger>
                            <AccordionContent>
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
                            </AccordionContent>
                          </AccordionItem>
                        </div>
                      );
                    })}
                  </Accordion>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function NovaVersaoForm({ solicitacaoId }: { solicitacaoId: string }) {
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [progresso, setProgresso] = React.useState<number | null>(null);
  const [fileInputKey, setFileInputKey] = React.useState(0);

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
      // Force-remount FileDropZone so its uncontrolled native <input type="file">
      // is guaranteed to reset visually alongside the RHF state (WR-01).
      setFileInputKey((k) => k + 1);
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
              key={fileInputKey}
              onFileChange={(file) =>
                form.setValue("file", createFileList(file), { shouldValidate: true })
              }
              onClear={() =>
                form.setValue("file", undefined as unknown as FileList, { shouldValidate: true })
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

function EntregarParecerDialog({
  solicitacaoId,
  versoes,
}: {
  solicitacaoId: string;
  versoes: ParecerVersao[] | undefined;
}) {
  const [confirmOpen, setConfirmOpen] = React.useState(false);
  const [selectedVersaoIdState, setSelectedVersaoId] = React.useState<string | null>(null);
  const [entregaError, setEntregaError] = React.useState<string | null>(null);

  const defaultVersaoId = versoes && versoes.length > 0 ? versoes[0].id : null;
  const selectedVersaoId = selectedVersaoIdState ?? defaultVersaoId;

  const entregar = useEntregarParecer(solicitacaoId);

  const handleEntregar = async () => {
    if (!selectedVersaoId) return;
    setEntregaError(null);
    try {
      await entregar.mutateAsync({ versaoFinalId: selectedVersaoId });
      toast.success("Parecer entregue com sucesso.");
      setConfirmOpen(false);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : "Não foi possível entregar o parecer. Verifique a ligação e tente novamente.";
      setEntregaError(msg);
      toast.error(msg);
    }
  };

  return (
    <AlertDialog
      open={confirmOpen}
      onOpenChange={(next) => {
        if (entregar.isPending) return;
        setConfirmOpen(next);
      }}
    >
      <AlertDialogTrigger asChild>
        <Button
          type="button"
          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
        >
          Entregar Parecer
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent
        onEscapeKeyDown={(e: KeyboardEvent) => {
          if (entregar.isPending) e.preventDefault();
        }}
      >
        <AlertDialogHeader>
          <AlertDialogTitle>Entregar Parecer</AlertDialogTitle>
          <AlertDialogDescription>
            Esta ação é irreversível. Depois de entregue, o parecer não pode receber novas
            versões nem ser reaberto.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <Label htmlFor="versaoFinalId">Selecione a versão a entregar como final:</Label>
          <NativeSelect id="versaoFinalId"
            size="default"
            className="w-full"
            value={selectedVersaoId ?? ""}
            onChange={(e) => setSelectedVersaoId(e.target.value)}
            disabled={entregar.isPending}
          >
            {(versoes ?? []).map((versao) => (
              <option key={versao.id} value={versao.id}>
                Versão {versao.numeroVersao} — {formatDateTime(versao.createdAt)}
              </option>
            ))}
          </NativeSelect>
        </div>

        {entregaError ? <p className="text-sm text-red-600 px-1">{entregaError}</p> : null}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={entregar.isPending}>Cancelar</AlertDialogCancel>
          <AlertDialogAction
            disabled={entregar.isPending || !selectedVersaoId}
            onClick={(e) => {
              e.preventDefault();
              void handleEntregar();
            }}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {entregar.isPending ? "A entregar..." : "Confirmar Entrega"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function ParecerEntregueBlock({
  id,
  versaoFinalId,
  versoes,
  isLoading,
  resolveUserNome,
}: {
  id: string;
  versaoFinalId: string | undefined;
  versoes: ParecerVersao[] | undefined;
  isLoading: boolean;
  resolveUserNome: (userId: string) => string;
}) {
  const versaoFinal = versoes?.find((v) => v.id === versaoFinalId);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg font-bold">Parecer Entregue</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">A carregar versão final...</p>
        ) : !versaoFinal ? (
          <p className="text-sm text-red-600">Não foi possível localizar a versão final entregue.</p>
        ) : (
          <div className="space-y-3">
            <Badge variant="green" className="rounded-none font-bold tracking-wide">
              CONCLUIDO
            </Badge>
            <p className="text-sm font-medium text-slate-900 dark:text-white">
              Versão {versaoFinal.numeroVersao}
            </p>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Elaborado por {versaoFinal.criadoPorId ? resolveUserNome(versaoFinal.criadoPorId) : "—"} em{" "}
              {formatDateTime(versaoFinal.createdAt)}
            </p>
            <div>
              <AnexoLink
                solicitacaoId={id}
                versaoId={versaoFinal.id}
                caminhoAnexo={versaoFinal.caminhoAnexo}
              />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
