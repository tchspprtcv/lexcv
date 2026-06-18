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
import { useCliente, useUpdateCliente } from "@/hooks/use-clientes";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { clienteFormSchema, type ClienteFormValues } from "@/schemas/clientes";
import type { ClienteUpdateRequest } from "@/types/clientes";

type PageProps = {
  params: Promise<{ id: string }>;
};

export default function ClienteEditPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canEditClientes = permissions.can.edit("clientes");

  if (!permissions.isLoading && !canEditClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para editar clientes."
        backHref={`/clientes/${encodeURIComponent(id)}`}
      />
    );
  }

  return <ClienteEditContent id={id} />;
}

const selectClassName =
  "flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-none border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

function ClienteEditContent({ id }: { id: string }) {
  const router = useRouter();
  const cliente = useCliente(id);
  const update = useUpdateCliente(id);
  const permissions = usePermissions();
  const canEditClientes = permissions.can.edit("clientes");
  const [serverError, setServerError] = React.useState<string | null>(null);

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

  React.useEffect(() => {
    if (!cliente.data) return;
    form.reset({
      nome: cliente.data.nome ?? "",
      nif: cliente.data.nif ?? "",
      tipo: cliente.data.tipo ?? "",
      email: cliente.data.email ?? "",
      telefone: cliente.data.telefone ?? "",
      localidade: cliente.data.localidade ?? "",
      morada: cliente.data.morada ?? "",
      documento_tipo: cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? "",
      documento_numero: cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "",
      ramo_atividade: cliente.data.ramo_atividade ?? cliente.data.ramoAtividade ?? "",
      detalhes_adicionais: cliente.data.detalhes_adicionais ?? cliente.data.detalhesAdicionais ?? "",
    });
  }, [cliente.data, form]);

  const onSubmit = async (values: ClienteFormValues) => {
    setServerError(null);
    try {
      const payload: ClienteUpdateRequest = {
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

      await update.mutateAsync(payload);
      toast.success("Cliente atualizado com sucesso.");
      router.push(`/clientes/${encodeURIComponent(id)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao atualizar cliente";
      setServerError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Editar cliente</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href={`/clientes/${encodeURIComponent(id)}`} className="hover:underline">
              Voltar ao detalhe
            </Link>
          </div>
        </div>

        <Button asChild variant="outline">
          <Link href={`/clientes/${encodeURIComponent(id)}`}>Cancelar</Link>
        </Button>
      </div>

      {cliente.isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : cliente.isError ? (
        <div className="text-sm text-red-600">
          {cliente.error instanceof Error ? cliente.error.message : "Erro ao carregar cliente"}
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Dados</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-6" onSubmit={form.handleSubmit(onSubmit)}>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="nome">Nome</Label>
                  <Input id="nome" className="rounded-none" {...form.register("nome")} />
                  {form.formState.errors.nome ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                  ) : null}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="nif">NIF (Legado)</Label>
                    <Input id="nif" className="rounded-none" {...form.register("nif")} />
                    {form.formState.errors.nif ? (
                      <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="tipo">Tipo</Label>
                    <Input id="tipo" className="rounded-none" {...form.register("tipo")} placeholder="Ex.: Particular / Empresa" />
                    {form.formState.errors.tipo ? (
                      <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
                    ) : null}
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="email">Email</Label>
                    <Input id="email" className="rounded-none" {...form.register("email")} />
                    {form.formState.errors.email ? (
                      <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="telefone">Telefone</Label>
                    <Input id="telefone" className="rounded-none" {...form.register("telefone")} />
                    {form.formState.errors.telefone ? (
                      <p className="text-sm text-red-600">{form.formState.errors.telefone.message}</p>
                    ) : null}
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="localidade">Localidade</Label>
                    <Input id="localidade" className="rounded-none" {...form.register("localidade")} />
                    {form.formState.errors.localidade ? (
                      <p className="text-sm text-red-600">{form.formState.errors.localidade.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="morada">Morada</Label>
                    <Input id="morada" className="rounded-none" {...form.register("morada")} />
                    {form.formState.errors.morada ? (
                      <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                    ) : null}
                  </div>
                </div>
              </div>

              <hr className="border-neutral-200 dark:border-neutral-800" />

              <div className="space-y-4">
                <h3 className="text-lg font-medium text-neutral-900 dark:text-neutral-100">Informações Adicionais</h3>
                
                <div className="grid gap-6 sm:grid-cols-2">
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
                        className="rounded-none"
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
                  disabled={form.formState.isSubmitting || update.isPending || permissions.isLoading || !canEditClientes}
                >
                  {form.formState.isSubmitting || update.isPending ? "A guardar..." : "Guardar"}
                </Button>
                <Button asChild type="button" variant="outline">
                  <Link href={`/clientes/${encodeURIComponent(id)}`}>Cancelar</Link>
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
