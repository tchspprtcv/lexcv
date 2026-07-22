"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";
import { Loader2, Plus, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { useCreateParecer } from "@/hooks/use-pareceres";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import {
  parecerCreateFormSchema,
  type ParecerCreateFormValues,
} from "@/schemas/pareceres";

const textareaClassName =
  "flex min-h-24 w-full border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 py-2 text-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50 dark:placeholder:text-neutral-400";

export default function ParecerCreatePage() {
  const permissions = usePermissions();
  const canCreatePareceres = permissions.can.create("pareceres");

  if (permissions.isFetched && !canCreatePareceres) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar solicitações de parecer."
        backHref="/pareceres"
      />
    );
  }

  return <ParecerCreateFormContent />;
}

function ParecerCreateFormContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const lockedClienteId = searchParams.get("clienteId");
  const permissions = usePermissions();
  const canCreatePareceres = permissions.can.create("pareceres");

  const [formError, setFormError] = React.useState<string | null>(null);
  const createParecer = useCreateParecer();

  const form = useForm<ParecerCreateFormValues>({
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolver: zodResolver(parecerCreateFormSchema) as any,
    defaultValues: {
      clienteId: lockedClienteId ?? "",
      processoId: undefined,
      descricao: "",
      prazo: undefined,
      prioridade: "MEDIA",
      advogadoId: undefined,
    },
  });

  const clientes = useClientes({});
  const clienteIdValue = form.watch("clienteId");
  const processos = useProcessos(clienteIdValue ? { cliente_id: clienteIdValue } : {});
  const adminUsers = useAdminUsers();

  // Re-sync the locked cliente once the (initially cold) clientes query resolves — mirrors
  // the fix in processos/novo/page.tsx (CR-01 review fix). Without this, a cold-cache mount
  // (e.g. navigating here directly from the cliente ficha's "Novo Parecer" button) leaves the
  // disabled NativeSelect showing its empty placeholder even though `clienteId` is required.
  React.useEffect(() => {
    if (lockedClienteId && clientes.data?.some((c) => c.id === lockedClienteId)) {
      form.setValue("clienteId", lockedClienteId, { shouldValidate: true });
    }
  }, [lockedClienteId, clientes.data, form]);

  // Once the clientes query has actually settled (not pending, not errored), if the locked id
  // still isn't in the list — e.g. the client was deleted or merged away since the "Novo
  // Parecer" link was generated — the re-sync effect above can never fire. Surface that
  // explicitly instead of leaving the field permanently disabled with stale "não pode ser
  // alterado" copy (WR-01 review fix).
  const lockedClienteMissing =
    Boolean(lockedClienteId) &&
    !clientes.isPending &&
    !clientes.isError &&
    !clientes.data?.some((c) => c.id === lockedClienteId);

  // Once the user has actually picked a replacement from the now-enabled select (or the
  // originally-locked id resolves normally), the "cliente já não está disponível" message
  // above must stop being shown — otherwise it sits indefinitely, directly beneath the
  // user's own valid selection (WR-01 round 3 review fix). Only the *message* uses this
  // narrower value; `disabled` above intentionally keeps reading the plain
  // `lockedClienteMissing` so the field never re-locks itself once truly missing.
  const lockedClienteMissingUnresolved = lockedClienteMissing && !clienteIdValue;

  React.useEffect(() => {
    form.setValue("processoId", "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clienteIdValue]);

  const advogados = React.useMemo(
    () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO") && u.ativo !== false),
    [adminUsers.data],
  );

  const onSubmit = async (values: ParecerCreateFormValues) => {
    setFormError(null);
    if (!canCreatePareceres) {
      setFormError("Não tem permissão para criar solicitações de parecer.");
      return;
    }
    try {
      const created = await createParecer.mutateAsync(values);
      toast.success("Solicitação de parecer criada com sucesso.");
      router.push(`/pareceres/${created.id}`);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : "Não foi possível criar a solicitação. Verifique a ligação e tente novamente.";
      setFormError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
            Nova Solicitação de Parecer
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Registe os dados da solicitação para encaminhar ao advogado responsável.
          </p>
        </div>
        <Button asChild variant="outline" className="">
          <Link href="/pareceres">
            <X className="h-4 w-4" />
            Cancelar
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg font-bold">Dados da Solicitação</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)}>
            <div className="space-y-2">
              <Label htmlFor="clienteId">Cliente</Label>
              <NativeSelect
                id="clienteId"
                size="default"
                className="w-full"
                disabled={
                  clientes.isPending ||
                  clientes.isError ||
                  (Boolean(lockedClienteId) && !lockedClienteMissing)
                }
                {...form.register("clienteId")}
              >
                <option value="">{clientes.isPending ? "A carregar..." : "Selecionar cliente"}</option>
                {(clientes.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nome}
                  </option>
                ))}
              </NativeSelect>
              {lockedClienteMissingUnresolved ? (
                <p className="text-sm text-red-600">
                  O cliente associado a este link já não está disponível. Selecione um cliente na lista abaixo ou volte à ficha do cliente.
                </p>
              ) : lockedClienteId && !lockedClienteMissing ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Cliente pré-preenchido a partir da ficha de cliente — não pode ser alterado aqui.
                </p>
              ) : null}
              {clientes.isError ? (
                <p className="text-sm text-red-600">
                  {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar clientes"}
                </p>
              ) : null}
              {form.formState.errors.clienteId ? (
                <p className="text-sm text-red-600">{form.formState.errors.clienteId.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="processoId">Processo (opcional)</Label>
              <NativeSelect id="processoId" size="default" className="w-full" {...form.register("processoId")}>
                <option value="">Nenhum processo associado</option>
                {(processos.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.numero ?? p.titulo ?? p.id}
                  </option>
                ))}
              </NativeSelect>
            </div>

            <div className="space-y-2">
              <Label htmlFor="descricao">Descrição</Label>
              <textarea
                id="descricao"
                className={textareaClassName}
                placeholder="Descreva o pedido de parecer (contexto, questão jurídica, urgência)"
                {...form.register("descricao")}
              />
              {form.formState.errors.descricao ? (
                <p className="text-sm text-red-600">{form.formState.errors.descricao.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="prazo">Prazo (opcional)</Label>
              <Input
                id="prazo"
                type="date"
                className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                {...form.register("prazo")}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="prioridade">Prioridade</Label>
              <NativeSelect id="prioridade" size="default" className="w-full" {...form.register("prioridade")}>
                <option value="ALTA">Alta</option>
                <option value="MEDIA">Média</option>
                <option value="BAIXA">Baixa</option>
              </NativeSelect>
            </div>

            <div className="space-y-2">
              <Label htmlFor="advogadoId">Advogado responsável (opcional)</Label>
              <NativeSelect id="advogadoId" size="default" className="w-full" {...form.register("advogadoId")}>
                <option value="">Atribuir mais tarde</option>
                {advogados.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.nome}
                  </option>
                ))}
              </NativeSelect>
              {adminUsers.isError ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Não foi possível carregar a lista de advogados — pode atribuir mais tarde a partir do parecer.
                </p>
              ) : null}
            </div>

            {formError ? <p className="text-sm text-red-600">{formError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white max-sm:min-h-[48px]"
                disabled={
                  form.formState.isSubmitting ||
                  createParecer.isPending ||
                  !permissions.isFetched ||
                  !canCreatePareceres
                }
              >
                {form.formState.isSubmitting || createParecer.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Plus className="h-4 w-4" />
                )}
                {form.formState.isSubmitting || createParecer.isPending ? "A criar..." : "Criar Solicitação"}
              </Button>
              <Button asChild type="button" variant="outline" className="">
                <Link href="/pareceres">
                  <X className="h-4 w-4" />
                  Cancelar
                </Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
