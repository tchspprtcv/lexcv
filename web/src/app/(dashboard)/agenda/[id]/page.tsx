"use client";

import Link from "next/link";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useEvento, useToggleEventoConcluido } from "@/hooks/use-eventos";
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

export default function EventoDetailPage({ params }: PageProps) {
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
          <Link href="/agenda">Voltar</Link>
        </Button>
      </div>
    );
  }

  if (!permissions.isLoading && !canViewAgenda) {
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
  const evento = useEvento(id);
  const processos = useProcessos();

  const toggle = useToggleEventoConcluido(id);

  const processoLabelById = new Map(
    (processos.data ?? []).map((p) => [p.id, p.numero ?? p.titulo ?? p.id] as const),
  );

  const isLoading = evento.isLoading || processos.isLoading;
  const isError = evento.isError || processos.isError;

  const canToggle = Boolean(evento.data) && canEditAgenda;

  const onToggle = async () => {
    if (!evento.data) return;
    await toggle.mutateAsync(!evento.data.concluido);
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
            <span>/</span> <span className="text-neutral-900 dark:text-neutral-50">{id}</span>
          </div>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/agenda">Voltar</Link>
          </Button>
          {canEditAgenda ? (
            <Button asChild variant="outline">
              <Link href={`/agenda/${encodeURIComponent(String(id))}/editar`}>Editar</Link>
            </Button>
          ) : null}
          {canEditAgenda ? (
            <Button
              type="button"
              disabled={!canToggle || toggle.isPending}
              variant={evento.data?.concluido ? "secondary" : "default"}
              onClick={onToggle}
            >
              {evento.data?.concluido ? "Reabrir" : "Concluir"}
            </Button>
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
