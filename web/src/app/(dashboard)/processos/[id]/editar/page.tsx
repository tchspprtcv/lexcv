"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcesso, useUpdateProcesso } from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import { processoFormSchema, type ProcessoFormValues } from "@/schemas/processos";
import type { ProcessoUpdateRequest } from "@/types/processos";

type PageProps = {
  params: Promise<{ id: string }>;
};

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function ProcessoEditPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canEditProcessos = permissions.can.edit("processos");

  if (!permissions.isLoading && !canEditProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para editar processos."
        backHref={`/processos/${encodeURIComponent(id)}`}
      />
    );
  }

  return <ProcessoEditContent id={id} />;
}

function ProcessoEditContent({ id }: { id: string }) {
  const router = useRouter();
  const processo = useProcesso(id);
  const update = useUpdateProcesso(id);
  const clientes = useClientes({});
  const permissions = usePermissions();
  const canEditProcessos = permissions.can.edit("processos");
  const [serverError, setServerError] = React.useState<string | null>(null);

  const form = useForm<ProcessoFormValues>({
    resolver: zodResolver(processoFormSchema),
    defaultValues: {
      cliente_id: "",
      numero: undefined,
      titulo: undefined,
      tipo_processo: undefined,
      area_juridica: undefined,
      tribunal: undefined,
      descricao: undefined,
      estado: undefined,
      data_inicio: undefined,
      data_fim: undefined,
      legal_hold: false,
      data_retencao: undefined,
      juizo: undefined,
    },
  });

  React.useEffect(() => {
    if (!processo.data) return;
    form.reset({
      cliente_id: processo.data.cliente_id,
      numero: processo.data.numero,
      titulo: processo.data.titulo,
      tipo_processo: processo.data.tipo_processo,
      area_juridica: processo.data.area_juridica,
      tribunal: processo.data.tribunal,
      descricao: processo.data.descricao,
      estado: processo.data.estado,
      data_inicio: processo.data.data_inicio,
      data_fim: processo.data.data_fim,
      legal_hold: processo.data.legal_hold ?? false,
      data_retencao: processo.data.data_retencao,
      juizo: processo.data.juizo,
    });
  }, [processo.data, form]);

  const onSubmit = async (values: ProcessoFormValues) => {
    setServerError(null);
    try {
      await update.mutateAsync(values satisfies ProcessoUpdateRequest);
      toast.success("Processo atualizado com sucesso.");
      router.push(`/processos/${encodeURIComponent(id)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao atualizar processo";
      setServerError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link href="/processos">Processos</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link href={`/processos/${encodeURIComponent(id)}`}>{processo.data?.numero ?? "…"}</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Editar</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Editar processo</h1>
        </div>

        <Button asChild variant="outline">
          <Link href={`/processos/${encodeURIComponent(id)}`}>Cancelar</Link>
        </Button>
      </div>

      {processo.isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : processo.isError ? (
        <div className="text-sm text-red-600">
          {processo.error instanceof Error ? processo.error.message : "Erro ao carregar processo"}
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Dados</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
              <div className="space-y-2">
                <Label htmlFor="cliente_id">Cliente</Label>
                <NativeSelect
                  id="cliente_id"
                  size="default"
                  className="w-full"
                  disabled={clientes.isLoading || clientes.isError}
                  {...form.register("cliente_id")}
                >
                  <option value="">{clientes.isLoading ? "A carregar..." : "Selecionar cliente"}</option>
                  {(clientes.data ?? []).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nome}
                    </option>
                  ))}
                </NativeSelect>
                {clientes.isError ? (
                  <p className="text-sm text-red-600">
                    {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar clientes"}
                  </p>
                ) : null}
                {form.formState.errors.cliente_id ? (
                  <p className="text-sm text-red-600">{form.formState.errors.cliente_id.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="numero">Número</Label>
                  <Input id="numero" {...form.register("numero")} placeholder="Ex.: 123/2026" />
                  {form.formState.errors.numero ? (
                    <p className="text-sm text-red-600">{form.formState.errors.numero.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="estado">Estado</Label>
                  <Input id="estado" {...form.register("estado")} placeholder="Ex.: ATIVO" />
                  {form.formState.errors.estado ? (
                    <p className="text-sm text-red-600">{form.formState.errors.estado.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="tipo_processo">Tipo do processo</Label>
                  <Input id="tipo_processo" {...form.register("tipo_processo")} placeholder="Ex.: Ação cível" />
                  {form.formState.errors.tipo_processo ? (
                    <p className="text-sm text-red-600">{form.formState.errors.tipo_processo.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="area_juridica">Área jurídica</Label>
                  <Input id="area_juridica" {...form.register("area_juridica")} placeholder="Ex.: Contratos" />
                  {form.formState.errors.area_juridica ? (
                    <p className="text-sm text-red-600">{form.formState.errors.area_juridica.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="tribunal">Tribunal</Label>
                <Input id="tribunal" {...form.register("tribunal")} placeholder="Ex.: Tribunal da Comarca da Praia" />
                {form.formState.errors.tribunal ? (
                  <p className="text-sm text-red-600">{form.formState.errors.tribunal.message}</p>
                ) : null}
              </div>

              <div className="space-y-2">
                <Label htmlFor="juizo">Juízo</Label>
                <Input
                  id="juizo"
                  className="rounded-none"
                  {...form.register("juizo")}
                  placeholder="Ex.: 1º Juízo Cível"
                />
                {form.formState.errors.juizo ? (
                  <p className="text-sm text-red-600">{form.formState.errors.juizo.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="data_inicio">Data de início</Label>
                  <Input id="data_inicio" type="date" {...form.register("data_inicio")} />
                  {form.formState.errors.data_inicio ? (
                    <p className="text-sm text-red-600">{form.formState.errors.data_inicio.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="data_fim">Data de fim</Label>
                  <Input id="data_fim" type="date" {...form.register("data_fim")} />
                  {form.formState.errors.data_fim ? (
                    <p className="text-sm text-red-600">{form.formState.errors.data_fim.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <div className="flex items-center space-x-2 pt-8">
                    <input
                      type="checkbox"
                      id="legal_hold"
                      className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                      {...form.register("legal_hold")}
                    />
                    <Label htmlFor="legal_hold">Legal Hold (Bloquear eliminação de docs)</Label>
                  </div>
                  {form.formState.errors.legal_hold ? (
                    <p className="text-sm text-red-600">{form.formState.errors.legal_hold.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="data_retencao">Data de Retenção</Label>
                  <Input id="data_retencao" type="date" {...form.register("data_retencao")} />
                  {form.formState.errors.data_retencao ? (
                    <p className="text-sm text-red-600">{form.formState.errors.data_retencao.message}</p>
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

              {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

              <div className="flex gap-2">
                <Button
                  type="submit"
                  disabled={form.formState.isSubmitting || update.isPending || permissions.isLoading || !canEditProcessos}
                >
                  {form.formState.isSubmitting || update.isPending ? "A guardar..." : "Guardar"}
                </Button>
                <Button asChild type="button" variant="outline">
                  <Link href={`/processos/${encodeURIComponent(id)}`}>Cancelar</Link>
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
