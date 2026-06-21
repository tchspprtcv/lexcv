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
import { useCreateCliente } from "@/hooks/use-clientes";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { clienteFormSchema, type ClienteFormValues } from "@/schemas/clientes";
import type { ClienteCreateRequest } from "@/types/clientes";

const selectClassName =
  "flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-none border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function ClienteCreatePage() {
  const router = useRouter();
  const create = useCreateCliente();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const canCreateClientes = permissions.can.create("clientes");

  const form = useForm<ClienteFormValues>({
    resolver: zodResolver(clienteFormSchema),
    defaultValues: {
      nome: "",
      nif: "",
      tipo: "",
      email: "",
      telefone: "",
      localidade: "",
      morada: "",
      documento_tipo: "",
      documento_numero: "",
      ramo_atividade: "",
      detalhes_adicionais: "",
    },
  });

  const onSubmit = async (values: ClienteFormValues) => {
    setServerError(null);
    if (!canCreateClientes) {
      setServerError("Não tem permissão para criar clientes");
      return;
    }
    try {
      const payload: ClienteCreateRequest = {
        ...values,
        documentoTipo: values.documento_tipo || undefined,
        documentoNumero: values.documento_numero || undefined,
        ramoAtividade: values.ramo_atividade || undefined,
        detalhesAdicionais: values.detalhes_adicionais || undefined,
      };
      
      // Sincronizar NIF se tipo for NIF
      if (values.documento_tipo === "NIF") {
        payload.nif = values.documento_numero;
      }
      
      const res = await create.mutateAsync(payload);
      toast.success("Cliente criado com sucesso.");
      router.push(`/clientes/${encodeURIComponent(res.id)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao criar cliente";
      setServerError(msg);
      toast.error(msg);
    }
  };

  if (!permissions.isLoading && !canCreateClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar clientes."
        backHref="/clientes"
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Novo cliente</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Criar um novo cliente.</p>
        </div>

        <Button asChild variant="outline">
          <Link href="/clientes">Voltar</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-6" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="nome">Nome</Label>
                <Input id="nome" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                {form.formState.errors.nome ? (
                  <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="nif">NIF (Legado)</Label>
                  <Input id="nif" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                  {form.formState.errors.nif ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tipo">Tipo</Label>
                  <Input id="tipo" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("tipo")} placeholder="Ex.: Particular / Empresa" />
                  {form.formState.errors.tipo ? (
                    <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input id="email" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("email")} />
                  {form.formState.errors.email ? (
                    <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="telefone">Telefone</Label>
                  <Input id="telefone" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("telefone")} />
                  {form.formState.errors.telefone ? (
                    <p className="text-sm text-red-600">{form.formState.errors.telefone.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="localidade">Localidade</Label>
                  <Input id="localidade" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("localidade")} />
                  {form.formState.errors.localidade ? (
                    <p className="text-sm text-red-600">{form.formState.errors.localidade.message}</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="morada">Morada</Label>
                  <Input id="morada" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
                  {form.formState.errors.morada ? (
                    <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                  ) : null}
                </div>
              </div>
            </div>

            <hr className="border-neutral-200 dark:border-neutral-800" />

            <div className="space-y-4">
              <h3 className="text-lg font-medium text-neutral-900 dark:text-neutral-100">Informações Adicionais</h3>
              
              <div className="grid gap-6 grid-cols-1 md:grid-cols-2">
                {/* Coluna 1: Tipo de Documento e Número de Documento */}
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="documento_tipo">Tipo de Documento</Label>
                    <select
                      id="documento_tipo"
                      className={selectClassName}
                      {...form.register("documento_tipo")}
                    >
                      <option value="">Nenhum</option>
                      <option value="NIF">NIF</option>
                      <option value="CNI">CNI</option>
                      <option value="PASSAPORTE">Passaporte</option>
                    </select>
                    {form.formState.errors.documento_tipo ? (
                      <p className="text-sm text-red-600">{form.formState.errors.documento_tipo.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="documento_numero">Número do Documento</Label>
                    <Input
                      id="documento_numero"
                      className="rounded-none max-sm:h-12 max-sm:text-base"
                      placeholder="Introduza o número do documento"
                      {...form.register("documento_numero")}
                    />
                    {form.formState.errors.documento_numero ? (
                      <p className="text-sm text-red-600">{form.formState.errors.documento_numero.message}</p>
                    ) : null}
                  </div>
                </div>

                {/* Coluna 2: Ramo de Atividade e Detalhes Adicionais */}
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="ramo_atividade">Ramo de Atividade</Label>
                    <select
                      id="ramo_atividade"
                      className={selectClassName}
                      {...form.register("ramo_atividade")}
                    >
                      <option value="">Selecione o ramo de atividade</option>
                      <option value="Banca">Banca</option>
                      <option value="Telecom">Telecom</option>
                      <option value="Construção">Construção</option>
                      <option value="Serviços">Serviços</option>
                      <option value="Comércio">Comércio</option>
                      <option value="Outros">Outros</option>
                    </select>
                    {form.formState.errors.ramo_atividade ? (
                      <p className="text-sm text-red-600">{form.formState.errors.ramo_atividade.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="detalhes_adicionais">Detalhes Adicionais</Label>
                    <textarea
                      id="detalhes_adicionais"
                      className={textareaClassName}
                      placeholder="Observações e detalhes adicionais sobre o cliente"
                      {...form.register("detalhes_adicionais")}
                    />
                    {form.formState.errors.detalhes_adicionais ? (
                      <p className="text-sm text-red-600">{form.formState.errors.detalhes_adicionais.message}</p>
                    ) : null}
                  </div>
                </div>
              </div>
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                className="max-sm:min-h-[48px]"
                disabled={form.formState.isSubmitting || create.isPending || permissions.isLoading || !canCreateClientes}
              >
                {form.formState.isSubmitting || create.isPending ? "A guardar..." : "Criar"}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link href="/clientes">Cancelar</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
