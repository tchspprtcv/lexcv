"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Controller, useForm } from "react-hook-form";
import { ArrowLeft, ArrowRight, Plus, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCreateCliente } from "@/hooks/use-clientes";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { getDocumentoTipoOptions, toDocumentoTipo } from "@/lib/cliente-documento-tipo";
import { clienteFormSchema, type ClienteFormValues } from "@/schemas/clientes";
import type { ClienteCreateRequest } from "@/types/clientes";

const textareaClassName =
  "flex min-h-24 w-full border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function ClienteCreatePage() {
  const router = useRouter();
  const create = useCreateCliente();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [pendingTipo, setPendingTipo] = React.useState<"PARTICULAR" | "EMPRESA" | null>(null);
  const canCreateClientes = permissions.can.create("clientes");

  const form = useForm<ClienteFormValues>({
    resolver: zodResolver(clienteFormSchema),
    defaultValues: {
      nome: "",
      nif: "",
      tipo: undefined,
      avencado: false,
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

  function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
    const currentTipo = form.getValues("tipo");
    if (currentTipo && currentTipo !== newTipo) {
      setPendingTipo(newTipo);
    } else {
      form.setValue("tipo", newTipo, { shouldValidate: true });
    }
  }

  function confirmTipoChange() {
    if (!pendingTipo) return;
    const currentDocumentoTipo = form.getValues("documento_tipo");
    if (toDocumentoTipo(currentDocumentoTipo, pendingTipo) === undefined) {
      form.setValue("documento_tipo", "");
      form.setValue("documento_numero", "");
    }
    form.setValue("tipo", pendingTipo, { shouldValidate: true });
    setPendingTipo(null);
  }

  const onSubmit = async (values: ClienteFormValues) => {
    setServerError(null);
    if (!canCreateClientes) {
      setServerError("Não tem permissão para criar clientes");
      return;
    }
    try {
      const documentoTipo = toDocumentoTipo(values.documento_tipo, values.tipo);

      const payload: ClienteCreateRequest = {
        ...values,
        tipo: values.tipo,
        avencado: values.avencado,
        documento_tipo: documentoTipo,
        documentoTipo: documentoTipo,
        documentoNumero: values.documento_numero || undefined,
        ramoAtividade: values.ramo_atividade || undefined,
        detalhesAdicionais: values.detalhes_adicionais || undefined,
      };

      const res = await create.mutateAsync(payload);
      toast.success("Cliente criado com sucesso.");
      router.push(`/clientes/${encodeURIComponent(res.id)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao criar cliente";
      setServerError(msg);
      toast.error(msg);
    }
  };

  if (permissions.isFetched && !canCreateClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar clientes."
        backHref="/clientes"
      />
    );
  }

  const tipoValue = form.watch("tipo");
  const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome";
  const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Novo Cliente</h1>
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link href="/clientes">Clientes</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>Novo Cliente</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Criar um novo cliente.</p>
        </div>

        <Button asChild variant="outline">
          <Link href="/clientes">
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-6" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-2">
              <Label>Tipo de Cliente</Label>
              <Controller
                control={form.control}
                name="tipo"
                render={({ field }) => (
                  <RadioGroup
                    className="flex gap-6"
                    value={field.value ?? ""}
                    onValueChange={(value) => onTipoChange(value as "PARTICULAR" | "EMPRESA")}
                  >
                    <div className="flex items-center gap-2">
                      <RadioGroupItem value="PARTICULAR" id="tipo-particular" />
                      <Label htmlFor="tipo-particular">Particular</Label>
                    </div>
                    <div className="flex items-center gap-2">
                      <RadioGroupItem value="EMPRESA" id="tipo-empresa" />
                      <Label htmlFor="tipo-empresa">Empresa</Label>
                    </div>
                  </RadioGroup>
                )}
              />
              {form.formState.errors.tipo ? (
                <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
              ) : null}
            </div>

            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="nome">{nomeLabel}</Label>
                <Input id="nome" className="max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                {form.formState.errors.nome ? (
                  <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="nif">NIF</Label>
                  <Input id="nif" className="max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                  {form.formState.errors.nif ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="email">Email</Label>
                  <Input id="email" className="max-sm:h-12 max-sm:text-base" {...form.register("email")} />
                  {form.formState.errors.email ? (
                    <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="telefone">Telefone</Label>
                  <Input id="telefone" className="max-sm:h-12 max-sm:text-base" {...form.register("telefone")} />
                  {form.formState.errors.telefone ? (
                    <p className="text-sm text-red-600">{form.formState.errors.telefone.message}</p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="localidade">Localidade</Label>
                  <Input id="localidade" className="max-sm:h-12 max-sm:text-base" {...form.register("localidade")} />
                  {form.formState.errors.localidade ? (
                    <p className="text-sm text-red-600">{form.formState.errors.localidade.message}</p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="morada">{moradaLabel}</Label>
                  <Input id="morada" className="max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
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
                    <NativeSelect
                      id="documento_tipo"
                      size="default"
                      className="w-full"
                      {...form.register("documento_tipo")}
                    >
                      <option value="">Nenhum</option>
                      {getDocumentoTipoOptions(form.watch("tipo")).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </NativeSelect>
                    {form.formState.errors.documento_tipo ? (
                      <p className="text-sm text-red-600">{form.formState.errors.documento_tipo.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="documento_numero">Número do Documento</Label>
                    <Input
                      id="documento_numero"
                      className="max-sm:h-12 max-sm:text-base"
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
                    <NativeSelect
                      id="ramo_atividade"
                      size="default"
                      className="w-full"
                      {...form.register("ramo_atividade")}
                    >
                      <option value="">Selecione o ramo de atividade</option>
                      <option value="Banca">Banca</option>
                      <option value="Telecom">Telecom</option>
                      <option value="Construção">Construção</option>
                      <option value="Serviços">Serviços</option>
                      <option value="Comércio">Comércio</option>
                      <option value="Outros">Outros</option>
                    </NativeSelect>
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

            <div className="flex items-center gap-3">
              <Controller
                control={form.control}
                name="avencado"
                render={({ field }) => (
                  <Switch
                    id="avencado"
                    checked={field.value ?? false}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
              <Label htmlFor="avencado">Avençado</Label>
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                className="max-sm:min-h-[48px]"
                disabled={form.formState.isSubmitting || create.isPending || permissions.isLoading || !canCreateClientes}
              >
                <Plus className="h-4 w-4" />
                {form.formState.isSubmitting || create.isPending ? "A guardar..." : "Criar"}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link href="/clientes">
                  <X className="h-4 w-4" />
                  Cancelar
                </Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Dialog open={!!pendingTipo} onOpenChange={(open) => { if (!open) setPendingTipo(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mudar tipo de cliente</DialogTitle>
            <DialogDescription>
              Mudar o tipo irá limpar os dados de {pendingTipo === "PARTICULAR" ? "Empresa" : "Particular"}. Continuar?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingTipo(null)}>
              <X className="h-4 w-4" />
              Cancelar
            </Button>
            <Button onClick={confirmTipoChange}>
              <ArrowRight className="h-4 w-4" />
              Continuar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
