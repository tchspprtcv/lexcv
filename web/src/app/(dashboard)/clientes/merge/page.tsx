"use client";

import Link from "next/link";
import * as React from "react";
import { useRouter } from "next/navigation";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useClientes, useMergeClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";

export default function ClientesMergePage() {
  const router = useRouter();
  const permissions = usePermissions();
  const canEditClientes = permissions.can.edit("clientes");

  const clientes = useClientes({});
  const merge = useMergeClientes();

  const [primaryId, setPrimaryId] = React.useState("");
  const [secondaryId, setSecondaryId] = React.useState("");

  if (!permissions.isLoading && !canEditClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para fazer merge de clientes."
        backHref="/clientes"
      />
    );
  }

  const options = (clientes.data ?? []).map((c) => ({
    id: c.id,
    label: `${c.nome}${c.nif ? ` — ${c.nif}` : ""}`,
  }));

  const onMerge = async () => {
    if (!primaryId || !secondaryId) return;
    if (primaryId === secondaryId) {
      toast.error("Seleciona dois clientes diferentes.");
      return;
    }
    const ok = window.confirm(
      "Confirmas o merge? O cliente duplicado será removido. O saldo da conta-corrente e os documentos do cliente duplicado serão transferidos para o cliente principal.",
    );
    if (!ok) return;

    try {
      const res = await merge.mutateAsync({ primaryId, secondaryId });
      const saldoInfo =
        res.merged_saldo && Number(res.merged_saldo) !== 0
          ? `, saldo transferido: ${res.merged_saldo}`
          : "";
      toast.success(
        `Merge concluído. Processos: ${res.moved_processos}, contactos: ${res.moved_contactos}, notas: ${res.moved_notas}, documentos: ${res.moved_documentos}${saldoInfo}.`,
      );
      router.push(`/clientes/${encodeURIComponent(res.primary_id)}`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao fazer merge");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Merge de clientes</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Seleciona um cliente principal e um duplicado. O duplicado será removido.
          </p>
        </div>
        <Button asChild variant="outline">
          <Link href="/clientes">Voltar</Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Seleção</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {clientes.isLoading ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
          ) : clientes.isError ? (
            <div className="text-sm text-red-600">Erro ao carregar clientes.</div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <div className="text-sm font-medium">Cliente principal</div>
                <select
                  value={primaryId}
                  onChange={(e) => setPrimaryId(e.target.value)}
                  className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                >
                  <option value="">Selecionar...</option>
                  {options.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <div className="text-sm font-medium">Cliente duplicado</div>
                <select
                  value={secondaryId}
                  onChange={(e) => setSecondaryId(e.target.value)}
                  className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                >
                  <option value="">Selecionar...</option>
                  {options.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          <div className="flex justify-end">
            <Button
              type="button"
              onClick={onMerge}
              disabled={!primaryId || !secondaryId || merge.isPending}
            >
              Fazer merge
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

