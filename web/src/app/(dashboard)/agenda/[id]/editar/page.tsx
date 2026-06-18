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
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useEvento, useUpdateEvento } from "@/hooks/use-eventos";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { eventoFormSchema, type EventoFormValues } from "@/schemas/eventos";
import type { EventoUpdateRequest } from "@/types/eventos";

type PageProps = {
  params: { id: string };
};

const selectClassName =
  "flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

function parseEventoId(id: string) {
  const n = Number(id);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) return null;
  return n;
}

function toDateTimeLocalValue(value: string | undefined) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}`;
}

export default function EventoEditPage(props: PageProps) {
  const params = React.use(props.params as unknown as Promise<{ id: string }>);
  const id = parseEventoId(params.id);
  const permissions = usePermissions();
  const canEditAgenda = permissions.can.edit("agenda");

  if (!id) {
    return (
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Editar evento</h1>
        <p className="text-sm text-red-600">Id inválido.</p>
        <Button asChild variant="outline">
          <Link href="/agenda">Voltar</Link>
        </Button>
      </div>
    );
  }

  if (!permissions.isLoading && !canEditAgenda) {
    return (
      <AccessDeniedState
        description="Não tem permissão para editar eventos."
        backHref={`/agenda/${encodeURIComponent(String(id))}`}
      />
    );
  }

  return <EventoEditContent id={id} />;
}

function EventoEditContent({ id }: { id: number }) {
  const router = useRouter();
  const evento = useEvento(id);
  const update = useUpdateEvento(id);
  const processos = useProcessos();
  const permissions = usePermissions();
  const canEditAgenda = permissions.can.edit("agenda");
  const [serverError, setServerError] = React.useState<string | null>(null);

  const form = useForm<EventoFormValues>({
    resolver: zodResolver(eventoFormSchema),
    defaultValues: {
      processoId: "",
      titulo: "",
      descricao: undefined,
      dataInicio: "",
      dataFim: "",
      prioridade: "MEDIA",
      concluido: false,
      recurrenceRule: "NONE" as const,
    },
  });

  React.useEffect(() => {
    if (!evento.data) return;
    form.reset({
      processoId: evento.data.processoId ?? "",
      tipo: evento.data.tipo ?? "",
      titulo: evento.data.titulo,
      descricao: evento.data.descricao,
      dataInicio: toDateTimeLocalValue(evento.data.dataInicio),
      dataFim: toDateTimeLocalValue(evento.data.dataFim),
      prioridade: evento.data.prioridade,
      concluido: evento.data.concluido,
      recurrenceRule: (evento.data.recurrenceRule as EventoFormValues["recurrenceRule"]) ?? "NONE",
      recurrenceEndDate: evento.data.recurrenceEndDate,
    });
  }, [evento.data, form]);

  const onSubmit = async (values: EventoFormValues) => {
    setServerError(null);
    try {
      const payload: EventoUpdateRequest = {
        processoId: values.processoId || undefined,
        tipo: values.tipo || undefined,
        titulo: values.titulo,
        descricao: values.descricao,
        dataInicio: new Date(values.dataInicio).toISOString().slice(0, 19),
        dataFim: new Date(values.dataFim).toISOString().slice(0, 19),
        prioridade: values.prioridade,
        concluido: values.concluido,
      };
      await update.mutateAsync(payload satisfies EventoUpdateRequest);
      router.push(`/agenda/${encodeURIComponent(String(id))}`);
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Erro ao atualizar evento");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Editar evento</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href={`/agenda/${encodeURIComponent(String(id))}`} className="hover:underline">
              Voltar ao detalhe
            </Link>
          </div>
        </div>

        <Button asChild variant="outline">
          <Link href={`/agenda/${encodeURIComponent(String(id))}`}>Cancelar</Link>
        </Button>
      </div>

      {evento.isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : evento.isError ? (
        <div className="text-sm text-red-600">
          {evento.error instanceof Error ? evento.error.message : "Erro ao carregar evento"}
        </div>
      ) : (
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
                  disabled={processos.isLoading || processos.isError}
                  {...form.register("processoId")}
                >
                  <option value="">
                    {processos.isLoading ? "A carregar..." : processos.isError ? "Erro ao carregar" : "Sem vínculo"}
                  </option>
                  {(processos.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.numero || p.titulo || "Sem número"}
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
                <Label htmlFor="tipo">Categoria</Label>
                <select
                  id="tipo"
                  className={selectClassName}
                  {...form.register("tipo")}
                >
                  <option value="">Sem categoria</option>
                  <option value="AUDIENCIA">Audiência</option>
                  <option value="PRAZO">Prazo Fatal</option>
                  <option value="DILIGENCIA">Diligência</option>
                  <option value="REUNIAO">Reunião</option>
                  <option value="OUTRO">Outro</option>
                </select>
                {form.formState.errors.tipo ? (
                  <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="titulo">Título</Label>
                <Input id="titulo" {...form.register("titulo")} />
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

                <div className="space-y-2">
                  <Label htmlFor="dataInicio">Início</Label>
                  <Input id="dataInicio" type="datetime-local" {...form.register("dataInicio")} />
                  {form.formState.errors.dataInicio ? (
                    <p className="text-sm text-red-600">{form.formState.errors.dataInicio.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="dataFim">Fim</Label>
                  <Input id="dataFim" type="datetime-local" {...form.register("dataFim")} />
                  {form.formState.errors.dataFim ? (
                    <p className="text-sm text-red-600">{form.formState.errors.dataFim.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="descricao">Descrição</Label>
                <textarea id="descricao" className={textareaClassName} {...form.register("descricao")} />
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
                  disabled={form.formState.isSubmitting || update.isPending || permissions.isLoading || !canEditAgenda}
                >
                  {form.formState.isSubmitting || update.isPending ? "A guardar..." : "Guardar"}
                </Button>
                <Button asChild type="button" variant="outline">
                  <Link href={`/agenda/${encodeURIComponent(String(id))}`}>Cancelar</Link>
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
