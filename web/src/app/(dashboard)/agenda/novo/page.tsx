"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCreateEvento } from "@/hooks/use-eventos";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { eventoFormSchema, type EventoFormValues } from "@/schemas/eventos";
import type { EventoCreateRequest } from "@/types/eventos";
import { AccessDeniedState } from "@/components/shared/access-denied-state";

const selectClassName =
  "flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function EventoCreatePage() {
  const permissions = usePermissions();
  const canCreateAgenda = permissions.can.create("agenda");

  if (!permissions.isLoading && !canCreateAgenda) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar eventos."
        backHref="/agenda"
      />
    );
  }

  return <EventoCreateContent />;
}

function EventoCreateContent() {
  const router = useRouter();
  const create = useCreateEvento();
  const processos = useProcessos();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const canCreateAgenda = permissions.can.create("agenda");

  const form = useForm<EventoFormValues>({
    resolver: zodResolver(eventoFormSchema),
    defaultValues: {
      processoId: undefined,
      titulo: "",
      descricao: undefined,
      dataInicio: "",
      dataFim: "",
      prioridade: "MEDIA",
      concluido: false,
    },
  });

  const onSubmit = async (values: EventoFormValues) => {
    setServerError(null);
    if (!canCreateAgenda) {
      setServerError("Não tem permissão para criar eventos");
      return;
    }
    try {
      const payload: EventoCreateRequest = {
        processoId: values.processoId,
        titulo: values.titulo,
        descricao: values.descricao,
        dataInicio: new Date(values.dataInicio).toISOString().slice(0, 19),
        dataFim: new Date(values.dataFim).toISOString().slice(0, 19),
        prioridade: values.prioridade,
        concluido: values.concluido,
      };
      const res = await create.mutateAsync(payload satisfies EventoCreateRequest);
      router.push(`/agenda/${encodeURIComponent(String(res.id))}`);
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Erro ao criar evento");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Novo evento</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Criar um novo evento.</p>
        </div>

        <Button asChild variant="outline">
          <Link href="/agenda">Voltar</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-2">
              <Label htmlFor="processoId">Processo (opcional)</Label>
              <select
                id="processoId"
                className={selectClassName}
                disabled={processos.isPending || processos.isError}
                {...form.register("processoId")}
              >
                <option value="">
                  {processos.isPending ? "A carregar..." : processos.isError ? "Erro ao carregar" : "Sem vínculo"}
                </option>
                {(processos.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.numero ?? p.titulo ?? p.id}
                  </option>
                ))}
              </select>
              {processos.isError ? (
                <p className="text-sm text-red-600">
                  {processos.error instanceof Error ? processos.error.message : "Erro ao carregar processos"}
                </p>
              ) : null}
              {form.formState.errors.processoId ? (
                <p className="text-sm text-red-600">{form.formState.errors.processoId.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="titulo">Título</Label>
              <Input id="titulo" {...form.register("titulo")} placeholder="Ex.: Audiência preliminar" />
              {form.formState.errors.titulo ? (
                <p className="text-sm text-red-600">{form.formState.errors.titulo.message}</p>
              ) : null}
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <div className="space-y-2">
                <Label htmlFor="prioridade">Prioridade</Label>
                <select id="prioridade" className={selectClassName} {...form.register("prioridade")}>
                  <option value="BAIXA">BAIXA</option>
                  <option value="MEDIA">MEDIA</option>
                  <option value="ALTA">ALTA</option>
                </select>
                {form.formState.errors.prioridade ? (
                  <p className="text-sm text-red-600">{form.formState.errors.prioridade.message}</p>
                ) : null}
              </div>

              <div className="space-y-2 sm:col-span-1">
                <Label htmlFor="dataInicio">Início</Label>
                <Input id="dataInicio" type="datetime-local" {...form.register("dataInicio")} />
                {form.formState.errors.dataInicio ? (
                  <p className="text-sm text-red-600">{form.formState.errors.dataInicio.message}</p>
                ) : null}
              </div>

              <div className="space-y-2 sm:col-span-1">
                <Label htmlFor="dataFim">Fim</Label>
                <Input id="dataFim" type="datetime-local" {...form.register("dataFim")} />
                {form.formState.errors.dataFim ? (
                  <p className="text-sm text-red-600">{form.formState.errors.dataFim.message}</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="descricao">Descrição</Label>
              <textarea
                id="descricao"
                className={textareaClassName}
                placeholder="Notas adicionais (opcional)"
                {...form.register("descricao")}
              />
              {form.formState.errors.descricao ? (
                <p className="text-sm text-red-600">{form.formState.errors.descricao.message}</p>
              ) : null}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="concluido"
                type="checkbox"
                className="h-4 w-4 rounded border border-neutral-300 dark:border-neutral-700"
                {...form.register("concluido")}
              />
              <Label htmlFor="concluido">Concluído</Label>
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                disabled={form.formState.isSubmitting || create.isPending || permissions.isLoading || !canCreateAgenda}
              >
                {form.formState.isSubmitting || create.isPending ? "A guardar..." : "Criar"}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link href="/agenda">Cancelar</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

