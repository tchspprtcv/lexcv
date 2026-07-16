"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Controller, useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { DatePickerField } from "@/components/shared/date-picker-field";
import { useCreateEvento } from "@/hooks/use-eventos";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { eventoFormSchema, type EventoFormValues } from "@/schemas/eventos";
import type { EventoCreateRequest } from "@/types/eventos";
import { AccessDeniedState } from "@/components/shared/access-denied-state";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function EventoCreatePage() {
  const permissions = usePermissions();
  const canCreateAgenda = permissions.can.create("agenda");

  if (permissions.isFetched && !canCreateAgenda) {
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
      recurrenceRule: "NONE",
      recurrenceEndDate: undefined,
    },
  });

  const recurrenceRule = form.watch("recurrenceRule");

  const onSubmit = async (values: EventoFormValues) => {
    setServerError(null);
    if (!canCreateAgenda) {
      setServerError("Não tem permissão para criar eventos");
      return;
    }
    try {
      const payload: EventoCreateRequest = {
        processoId: values.processoId,
        tipo: values.tipo,
        titulo: values.titulo,
        descricao: values.descricao,
        dataInicio: `${values.dataInicio}:00`,
        dataFim: `${values.dataFim}:00`,
        prioridade: values.prioridade,
        concluido: values.concluido,
        ...(values.recurrenceRule && values.recurrenceRule !== "NONE"
          ? { recurrenceRule: values.recurrenceRule, recurrenceEndDate: values.recurrenceEndDate }
          : {}),
      };
      const res = await create.mutateAsync(payload satisfies EventoCreateRequest);
      toast.success("Evento criado com sucesso.");
      router.push(`/agenda/${encodeURIComponent(String(res.id))}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao criar evento";
      setServerError(msg);
      toast.error(msg);
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
              <NativeSelect
                id="processoId"
                size="default"
                className="w-full"
                disabled={processos.isPending || processos.isError}
                {...form.register("processoId")}
              >
                <option value="">
                  {processos.isPending ? "A carregar..." : processos.isError ? "Erro ao carregar" : "Sem vínculo"}
                </option>
                {(processos.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.numero || p.titulo || "Sem número"}
                  </option>
                ))}
              </NativeSelect>
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
              <NativeSelect
                id="tipo"
                size="default"
                className="w-full"
                {...form.register("tipo")}
              >
                <option value="">Sem categoria</option>
                <option value="AUDIENCIA">Audiência</option>
                <option value="PRAZO">Prazo Fatal</option>
                <option value="DILIGENCIA">Diligência</option>
                <option value="REUNIAO">Reunião</option>
                <option value="OUTRO">Outro</option>
              </NativeSelect>
              {form.formState.errors.tipo ? (
                <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
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
                <NativeSelect id="prioridade" size="default" className="w-full" {...form.register("prioridade")}>
                  <option value="BAIXA">BAIXA</option>
                  <option value="MEDIA">MEDIA</option>
                  <option value="ALTA">ALTA</option>
                </NativeSelect>
                {form.formState.errors.prioridade ? (
                  <p className="text-sm text-red-600">{form.formState.errors.prioridade.message}</p>
                ) : null}
              </div>

              <div className="space-y-2 sm:col-span-1">
                <Label htmlFor="dataInicio">Início</Label>
                <Controller
                  control={form.control}
                  name="dataInicio"
                  render={({ field }) => (
                    <DatePickerField id="dataInicio" value={field.value} onChange={field.onChange} withTime />
                  )}
                />
                {form.formState.errors.dataInicio ? (
                  <p className="text-sm text-red-600">{form.formState.errors.dataInicio.message}</p>
                ) : null}
              </div>

              <div className="space-y-2 sm:col-span-1">
                <Label htmlFor="dataFim">Fim</Label>
                <Controller
                  control={form.control}
                  name="dataFim"
                  render={({ field }) => (
                    <DatePickerField id="dataFim" value={field.value} onChange={field.onChange} withTime />
                  )}
                />
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

            <div className="space-y-2">
              <Label htmlFor="recurrenceRule">Recorrência</Label>
              <NativeSelect
                id="recurrenceRule"
                size="default"
                className="w-full"
                {...form.register("recurrenceRule")}
              >
                <option value="NONE">Nenhuma</option>
                <option value="DAILY">Diária</option>
                <option value="WEEKLY">Semanal</option>
                <option value="MONTHLY">Mensal</option>
              </NativeSelect>
              {form.formState.errors.recurrenceRule ? (
                <p className="text-sm text-red-600">{form.formState.errors.recurrenceRule.message}</p>
              ) : null}
            </div>

            {recurrenceRule && recurrenceRule !== "NONE" ? (
              <div className="space-y-2">
                <Label htmlFor="recurrenceEndDate">Fim da recorrência</Label>
                <Controller
                  control={form.control}
                  name="recurrenceEndDate"
                  render={({ field }) => (
                    <DatePickerField id="recurrenceEndDate" value={field.value} onChange={field.onChange} />
                  )}
                />
                {form.formState.errors.recurrenceEndDate ? (
                  <p className="text-sm text-red-600">{form.formState.errors.recurrenceEndDate.message}</p>
                ) : null}
              </div>
            ) : null}

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

