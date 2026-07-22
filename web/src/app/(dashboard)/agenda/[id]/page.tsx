"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { ArrowLeft, CheckCircle2, Pencil, RotateCcw, Trash2 } from "lucide-react";

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
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useEvento, useToggleEventoConcluido, useDeleteEvento, useDeleteEventoInstance } from "@/hooks/use-eventos";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { AccessDeniedState } from "@/components/shared/access-denied-state";

type PageProps = {
  params: { id: string };
};

function parseEventoId(id: string) {
  const n = Number(id);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) return null;
  return n;
}

function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}

export default function EventoDetailPage(props: PageProps) {
  const params = React.use(props.params as unknown as Promise<{ id: string }>);
  const id = parseEventoId(params.id);
  const permissions = usePermissions();
  const canViewAgenda = permissions.can.view("agenda");
  const canEditAgenda = permissions.can.edit("agenda");

  if (!id) {
    return (
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Evento</h1>
        <p className="text-sm text-red-600">Id inválido.</p>
        <Button asChild variant="outline">
          <Link href="/agenda">
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>
        </Button>
      </div>
    );
  }

  if (permissions.isFetched && !canViewAgenda) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este evento."
        backHref="/agenda"
      />
    );
  }

  return <EventoDetailContent id={id} canEditAgenda={canEditAgenda} />;
}

function EventoDetailContent({ id, canEditAgenda }: { id: number; canEditAgenda: boolean }) {
  const router = useRouter();
  const evento = useEvento(id);
  const processos = useProcessos();

  const toggle = useToggleEventoConcluido(id);
  const del = useDeleteEvento(id);
  const delInstance = useDeleteEventoInstance(id);
  const [confirmOpen, setConfirmOpen] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState<string | null>(null);

  const processoLabelById = new Map(
    (processos.data ?? []).map((p) => [p.id, p.numero || p.titulo || "Sem número"] as const),
  );

  const isLoading = evento.isLoading || processos.isLoading;
  const isError = evento.isError || processos.isError;

  const canToggle = Boolean(evento.data) && canEditAgenda;

  const onToggle = async () => {
    if (!evento.data) return;
    try {
      await toggle.mutateAsync(!evento.data.concluido);
      toast.success(evento.data.concluido ? "Evento reaberto com sucesso." : "Evento concluído com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao atualizar estado do evento.");
    }
  };

  const isDeleting = del.isPending || delInstance.isPending;

  const handleDeleteSeries = async () => {
    setDeleteError(null);
    try {
      await del.mutateAsync();
      toast.success("Evento apagado com sucesso.");
      router.push("/agenda");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar evento";
      setDeleteError(msg);
      toast.error(msg);
    }
  };

  const handleDeleteInstance = async () => {
    if (!evento.data) return;
    setDeleteError(null);
    try {
      await delInstance.mutateAsync({ date: evento.data.dataInicio.slice(0, 10) });
      toast.success("Instância de evento apagada com sucesso.");
      router.push("/agenda");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar instância";
      setDeleteError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Evento</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href="/agenda" className="hover:underline">
              Agenda
            </Link>{" "}
            <span>/</span>{" "}
            <span className="text-neutral-900 dark:text-neutral-50">{evento.data?.titulo ?? "…"}</span>
          </div>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/agenda">
              <ArrowLeft className="h-4 w-4" />
              Voltar
            </Link>
          </Button>
          {canEditAgenda ? (
            <Button asChild variant="outline">
              <Link href={`/agenda/${encodeURIComponent(String(id))}/editar`}>
                <Pencil className="h-4 w-4" />
                Editar
              </Link>
            </Button>
          ) : null}
          {canEditAgenda ? (
            <Button
              type="button"
              disabled={!canToggle || toggle.isPending}
              variant={evento.data?.concluido ? "secondary" : "default"}
              onClick={onToggle}
            >
              {evento.data?.concluido ? (
                <>
                  <RotateCcw className="h-4 w-4" />
                  Reabrir
                </>
              ) : (
                <>
                  <CheckCircle2 className="h-4 w-4" />
                  Concluir
                </>
              )}
            </Button>
          ) : null}
          {canEditAgenda && evento.data ? (
            <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
              <AlertDialogTrigger asChild>
                <Button type="button" variant="secondary" className="border border-red-300 text-red-600 hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950" disabled={isDeleting}>
                  <Trash2 className="h-4 w-4" />
                  Apagar
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Apagar evento</AlertDialogTitle>
                  <AlertDialogDescription>
                    {evento.data.recurrenceRule
                      ? "Este evento faz parte de uma série recorrente. Deseja apagar apenas esta instância ou toda a série?"
                      : "Tem a certeza que deseja apagar este evento? Esta ação não pode ser revertida."}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                {deleteError ? (
                  <p className="text-sm text-red-600 px-1">{deleteError}</p>
                ) : null}
                <AlertDialogFooter>
                  <AlertDialogCancel disabled={isDeleting}>Cancelar</AlertDialogCancel>
                  {evento.data.recurrenceRule ? (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        disabled={isDeleting}
                        onClick={handleDeleteInstance}
                      >
                        <Trash2 className="h-4 w-4" />
                        {delInstance.isPending ? "A apagar..." : "Apagar esta instância"}
                      </Button>
                      <AlertDialogAction
                        disabled={isDeleting}
                        onClick={(e) => { e.preventDefault(); void handleDeleteSeries(); }}
                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                      >
                        {del.isPending ? "A apagar..." : "Apagar toda a série"}
                      </AlertDialogAction>
                    </>
                  ) : (
                    <AlertDialogAction
                      disabled={isDeleting}
                      onClick={(e) => { e.preventDefault(); void handleDeleteSeries(); }}
                      className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                    >
                      {del.isPending ? "A apagar..." : "Apagar evento"}
                    </AlertDialogAction>
                  )}
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          {evento.error instanceof Error
            ? evento.error.message
            : processos.error instanceof Error
              ? processos.error.message
              : "Erro ao carregar"}
        </div>
      ) : evento.data ? (
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Dados</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                <dt className="text-neutral-500 dark:text-neutral-400">Título</dt>
                <dd className="col-span-2 font-medium">{evento.data.titulo}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Processo</dt>
                <dd className="col-span-2">
                  {evento.data.processoId
                    ? processoLabelById.get(evento.data.processoId) ?? evento.data.processoId
                    : "—"}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Prioridade</dt>
                <dd className="col-span-2">{evento.data.prioridade}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Início</dt>
                <dd className="col-span-2">{formatDateTime(evento.data.dataInicio)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Fim</dt>
                <dd className="col-span-2">{formatDateTime(evento.data.dataFim)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Estado</dt>
                <dd className="col-span-2">{evento.data.concluido ? "Concluído" : "Pendente"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Descrição</dt>
                <dd className="col-span-2 whitespace-pre-wrap">{evento.data.descricao ?? "—"}</dd>
              </dl>
            </CardContent>
          </Card>
        </div>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Evento não encontrado.</div>
      )}
    </div>
  );
}
