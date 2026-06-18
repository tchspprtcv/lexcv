"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCliente } from "@/hooks/use-clientes";
import { useCreatePagamento, useHonorario, useHonorarioPagamentos } from "@/hooks/use-financeiro";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcesso } from "@/hooks/use-processos";
import { pagamentoFormSchema, type PagamentoFormValues } from "@/schemas/financeiro";
import type { PagamentoCreateRequest } from "@/types/financeiro";

type PageProps = {
  params: { id: string };
};

function formatMoneyCVE(v: number) {
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v.includes("T") ? v : `${v}T00:00:00`);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

export default function HonorarioDetailPage(props: PageProps) {
  const params = React.use(props.params as unknown as Promise<{ id: string }>);
  const permissions = usePermissions();
  const honorarioId = Number(params.id);
  const canViewFinanceiro = permissions.can.view("financeiro");
  const canEditFinanceiro = permissions.can.edit("financeiro");
  const canViewClientes = permissions.can.view("clientes");
  const canViewProcessos = permissions.can.view("processos");

  if (!permissions.isLoading && !canViewFinanceiro) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este honorário."
        backHref="/financeiro"
      />
    );
  }

  if (!Number.isFinite(honorarioId) || !Number.isInteger(honorarioId) || honorarioId <= 0) {
    return (
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Honorário</h1>
        <p className="text-sm text-red-600">Id inválido.</p>
        <Button asChild variant="outline">
          <Link href="/financeiro">Voltar</Link>
        </Button>
      </div>
    );
  }

  return (
    <HonorarioDetailContent
      honorarioId={honorarioId}
      rawId={params.id}
      canEditFinanceiro={canEditFinanceiro}
      canViewClientes={canViewClientes}
      canViewProcessos={canViewProcessos}
    />
  );
}

function HonorarioDetailContent({
  honorarioId,
  rawId,
  canEditFinanceiro,
  canViewClientes,
  canViewProcessos,
}: {
  honorarioId: number;
  rawId: string;
  canEditFinanceiro: boolean;
  canViewClientes: boolean;
  canViewProcessos: boolean;
}) {
  const honorario = useHonorario(honorarioId);
  const processo = useProcesso(honorario.data?.processoId ?? "");
  const cliente = useCliente(processo.data?.cliente_id ?? "");

  const pagamentos = useHonorarioPagamentos(honorarioId);
  const createPagamento = useCreatePagamento();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);

  const form = useForm<PagamentoFormValues>({
    resolver: zodResolver(pagamentoFormSchema),
    defaultValues: { valorPago: "", dataPagamento: undefined, metodo: undefined },
  });

  const onSubmitPagamento = async (values: PagamentoFormValues) => {
    setServerError(null);
    if (!canEditFinanceiro) {
      setServerError("Não tem permissão para registar pagamentos");
      return;
    }
    try {
      const payload: PagamentoCreateRequest = {
        honorarioId: honorarioId,
        valorPago: Number(values.valorPago),
        dataPagamento: values.dataPagamento,
        metodo: values.metodo,
      };
      await createPagamento.mutateAsync(payload satisfies PagamentoCreateRequest);
      form.reset({ valorPago: "", dataPagamento: undefined, metodo: undefined });
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Erro ao adicionar pagamento");
    }
  };

  const isLoading =
    honorario.isLoading || processo.isLoading || cliente.isLoading || pagamentos.isLoading;
  const isError = honorario.isError || processo.isError || cliente.isError || pagamentos.isError;

  const totalPago = (pagamentos.data ?? []).reduce((acc, p) => acc + (p.valorPago ?? 0), 0);
  const restante = honorario.data ? Math.max(0, honorario.data.valorTotal - totalPago) : 0;

  const clienteId = processo.data?.cliente_id;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Honorário</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href="/financeiro" className="hover:underline">
              Financeiro
            </Link>{" "}
            <span>/</span> <span className="text-neutral-900 dark:text-neutral-50">#{rawId}</span>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href="/financeiro">Voltar</Link>
          </Button>
          {clienteId && canViewClientes ? (
            <Button asChild variant="outline">
              <Link href={`/clientes/${encodeURIComponent(clienteId)}`}>Conta-corrente do cliente</Link>
            </Button>
          ) : null}
          {honorario.data?.processoId && canViewProcessos ? (
            <Button asChild>
              <Link href={`/processos/${encodeURIComponent(honorario.data.processoId)}`}>Ver processo</Link>
            </Button>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          {honorario.error instanceof Error
            ? honorario.error.message
            : processo.error instanceof Error
              ? processo.error.message
              : cliente.error instanceof Error
                ? cliente.error.message
                : pagamentos.error instanceof Error
                  ? pagamentos.error.message
                  : "Erro ao carregar"}
        </div>
      ) : !honorario.data ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Honorário não encontrado.</div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Resumo</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                <dt className="text-neutral-500 dark:text-neutral-400">Processo</dt>
                <dd className="col-span-2">
                  {processo.data ? (
                    <Link
                      href={`/processos/${encodeURIComponent(processo.data.id)}`}
                      className="font-medium hover:underline"
                    >
                      {processo.data.numero || processo.data.titulo || "Sem número"}
                    </Link>
                  ) : (
                    honorario.data.processoId
                  )}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Cliente</dt>
                <dd className="col-span-2">
                  {clienteId && canViewClientes ? (
                    <Link
                      href={`/clientes/${encodeURIComponent(clienteId)}`}
                      className="font-medium hover:underline"
                    >
                      {cliente.data?.nome ?? clienteId}
                    </Link>
                  ) : (
                    "—"
                  )}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Total</dt>
                <dd className="col-span-2 font-medium">{formatMoneyCVE(honorario.data.valorTotal)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Pago</dt>
                <dd className="col-span-2">{formatMoneyCVE(totalPago)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Restante</dt>
                <dd className="col-span-2">{formatMoneyCVE(restante)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Acordo</dt>
                <dd className="col-span-2">{formatDate(honorario.data.dataAcordo)}</dd>
              </dl>

              {honorario.data.descricao ? (
                <div className="text-sm text-neutral-500 dark:text-neutral-400">{honorario.data.descricao}</div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Adicionar pagamento</CardTitle>
            </CardHeader>
            <CardContent>
              {canEditFinanceiro ? (
                <form className="space-y-4" onSubmit={form.handleSubmit(onSubmitPagamento)}>
                  <div className="space-y-2">
                    <Label htmlFor="valorPago">Valor pago</Label>
                    <Input
                      id="valorPago"
                      type="number"
                      inputMode="decimal"
                      step="0.01"
                      min="0"
                      {...form.register("valorPago")}
                    />
                    {form.formState.errors.valorPago ? (
                      <p className="text-sm text-red-600">{form.formState.errors.valorPago.message}</p>
                    ) : null}
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-2">
                      <Label htmlFor="dataPagamento">Data (opcional)</Label>
                      <Input id="dataPagamento" type="date" {...form.register("dataPagamento")} />
                      {form.formState.errors.dataPagamento ? (
                        <p className="text-sm text-red-600">{form.formState.errors.dataPagamento.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="metodo">Método (opcional)</Label>
                      <Input id="metodo" {...form.register("metodo")} placeholder="Ex.: Transferência" />
                      {form.formState.errors.metodo ? (
                        <p className="text-sm text-red-600">{form.formState.errors.metodo.message}</p>
                      ) : null}
                    </div>
                  </div>

                  {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

                  <Button
                    type="submit"
                    disabled={form.formState.isSubmitting || createPagamento.isPending || permissions.isLoading || !canEditFinanceiro}
                  >
                    {form.formState.isSubmitting || createPagamento.isPending ? "A guardar..." : "Adicionar"}
                  </Button>
                </form>
              ) : (
                <p className="text-sm text-neutral-500 dark:text-neutral-400">
                  Não tem permissão para registar pagamentos neste honorário.
                </p>
              )}
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Pagamentos</CardTitle>
            </CardHeader>
            <CardContent>
              {!pagamentos.data?.length ? (
                <div className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum pagamento registado.</div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="text-left text-neutral-500 dark:text-neutral-400">
                      <tr className="border-b border-neutral-200 dark:border-neutral-800">
                        <th className="py-2 pr-4 font-medium">Data</th>
                        <th className="py-2 pr-4 font-medium">Valor</th>
                        <th className="py-2 pr-4 font-medium">Método</th>
                        <th className="py-2 pr-4 font-medium">ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(pagamentos.data ?? []).map((p) => (
                        <tr
                          key={p.id}
                          className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                        >
                          <td className="py-2 pr-4">{formatDate(p.dataPagamento)}</td>
                          <td className="py-2 pr-4">{formatMoneyCVE(p.valorPago)}</td>
                          <td className="py-2 pr-4">{p.metodo ?? "—"}</td>
                          <td className="py-2 pr-4">#{p.id}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
