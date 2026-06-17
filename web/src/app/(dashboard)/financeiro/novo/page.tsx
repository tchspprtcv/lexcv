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
import { useCreateHonorario } from "@/hooks/use-financeiro";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { honorarioFormSchema, type HonorarioFormValues } from "@/schemas/financeiro";
import type { HonorarioCreateRequest } from "@/types/financeiro";

const selectClassName =
  "flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

export default function HonorarioCreatePage() {
  const permissions = usePermissions();
  const canCreateFinanceiro = permissions.can.create("financeiro");

  if (!permissions.isLoading && !canCreateFinanceiro) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar honorários."
        backHref="/financeiro"
      />
    );
  }

  return <HonorarioCreateContent />;
}

function HonorarioCreateContent() {
  const router = useRouter();
  const processos = useProcessos();
  const create = useCreateHonorario();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const canCreateFinanceiro = permissions.can.create("financeiro");

  const form = useForm<HonorarioFormValues>({
    resolver: zodResolver(honorarioFormSchema),
    defaultValues: {
      processo_id: "",
      valor_total: "",
      descricao: undefined,
      data_acordo: undefined,
    },
  });

  const onSubmit = async (values: HonorarioFormValues) => {
    setServerError(null);
    if (!canCreateFinanceiro) {
      setServerError("Não tem permissão para criar honorários");
      return;
    }
    try {
      const payload: HonorarioCreateRequest = {
        processo_id: values.processo_id,
        valor_total: Number(values.valor_total),
        descricao: values.descricao,
        data_acordo: values.data_acordo,
      };
      const res = await create.mutateAsync(payload satisfies HonorarioCreateRequest);
      router.push(`/financeiro/${encodeURIComponent(String(res.id))}`);
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Erro ao criar honorário");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Novo honorário</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Criar honorário e associar a um processo.
          </p>
        </div>

        <Button asChild variant="outline">
          <Link href="/financeiro">Voltar</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-2">
              <Label htmlFor="processo_id">Processo</Label>
              <select
                id="processo_id"
                className={selectClassName}
                disabled={processos.isPending || processos.isError}
                {...form.register("processo_id")}
              >
                <option value="">
                  {processos.isPending
                    ? "A carregar..."
                    : processos.isError
                      ? "Erro ao carregar"
                      : "Selecione um processo"}
                </option>
                {(processos.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.numero || p.titulo || "Sem número"}
                  </option>
                ))}
              </select>
              {processos.isError ? (
                <p className="text-sm text-red-600">
                  {processos.error instanceof Error
                    ? processos.error.message
                    : "Erro ao carregar processos"}
                </p>
              ) : null}
              {form.formState.errors.processo_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.processo_id.message}</p>
              ) : null}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="valor_total">Valor total</Label>
                <Input
                  id="valor_total"
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  {...form.register("valor_total")}
                />
                {form.formState.errors.valor_total ? (
                  <p className="text-sm text-red-600">{form.formState.errors.valor_total.message}</p>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="data_acordo">Data do acordo (opcional)</Label>
                <Input id="data_acordo" type="date" {...form.register("data_acordo")} />
                {form.formState.errors.data_acordo ? (
                  <p className="text-sm text-red-600">{form.formState.errors.data_acordo.message}</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="descricao">Descrição (opcional)</Label>
              <Input id="descricao" {...form.register("descricao")} placeholder="Ex.: Acordo extrajudicial" />
              {form.formState.errors.descricao ? (
                <p className="text-sm text-red-600">{form.formState.errors.descricao.message}</p>
              ) : null}
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                disabled={form.formState.isSubmitting || create.isPending || permissions.isLoading || !canCreateFinanceiro}
              >
                {form.formState.isSubmitting || create.isPending ? "A guardar..." : "Criar"}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link href="/financeiro">Cancelar</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
