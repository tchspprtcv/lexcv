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
import { NativeSelect } from "@/components/ui/native-select";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCreateHonorario } from "@/hooks/use-financeiro";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { honorarioFormSchema, type HonorarioFormValues } from "@/schemas/financeiro";
import type { HonorarioCreateRequest } from "@/types/financeiro";

export default function HonorarioCreatePage() {
  const permissions = usePermissions();
  const canCreateFinanceiro = permissions.can.create("financeiro");

  if (permissions.isFetched && !canCreateFinanceiro) {
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
      processoId: "",
      valorTotal: "",
      descricao: undefined,
      dataAcordo: undefined,
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
        processoId: values.processoId,
        valorTotal: Number(values.valorTotal),
        descricao: values.descricao,
        dataAcordo: values.dataAcordo,
      };
      const res = await create.mutateAsync(payload satisfies HonorarioCreateRequest);
      toast.success("Honorário criado com sucesso.");
      router.push(`/financeiro/${encodeURIComponent(String(res.id))}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao criar honorário";
      setServerError(msg);
      toast.error(msg);
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
              <Label htmlFor="processoId">Processo</Label>
              <NativeSelect id="processoId"
                size="default"
                className="w-full"
                disabled={processos.isPending || processos.isError}
                {...form.register("processoId")}
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
              </NativeSelect>
              {processos.isError ? (
                <p className="text-sm text-red-600">
                  {processos.error instanceof Error
                    ? processos.error.message
                    : "Erro ao carregar processos"}
                </p>
              ) : null}
              {form.formState.errors.processoId ? (
                <p className="text-sm text-red-600">{form.formState.errors.processoId.message}</p>
              ) : null}
            </div>

            <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="valorTotal">Valor total</Label>
                <Input
                  id="valorTotal"
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min="0"
                  className="max-sm:h-12 max-sm:text-base"
                  {...form.register("valorTotal")}
                />
                {form.formState.errors.valorTotal ? (
                  <p className="text-sm text-red-600">{form.formState.errors.valorTotal.message}</p>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="dataAcordo">Data do acordo (opcional)</Label>
                <Input id="dataAcordo" type="date" className="max-sm:h-12 max-sm:text-base" {...form.register("dataAcordo")} />
                {form.formState.errors.dataAcordo ? (
                  <p className="text-sm text-red-600">{form.formState.errors.dataAcordo.message}</p>
                ) : null}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="descricao">Descrição (opcional)</Label>
              <Input id="descricao" className="max-sm:h-12 max-sm:text-base" {...form.register("descricao")} placeholder="Ex.: Acordo extrajudicial" />
              {form.formState.errors.descricao ? (
                <p className="text-sm text-red-600">{form.formState.errors.descricao.message}</p>
              ) : null}
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                className="max-sm:min-h-[48px]"
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
