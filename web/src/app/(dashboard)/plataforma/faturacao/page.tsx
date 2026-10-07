"use client";

import * as React from "react";
import Link from "next/link";
import { ArrowLeft, Building2, Receipt } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Button } from "@/components/ui/button";
import { useMe } from "@/hooks/use-me";

import { PlatformConfigFiscalCard } from "./config-fiscal-card";
import { PlatformDocumentosTable } from "./documentos-table";
import { RegistarPagamentoDialog } from "./registar-pagamento-dialog";

export default function PlataformaFaturacaoPage() {
  const me = useMe();

  if (!me.isFetched) {
    return null;
  }

  if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Apenas administradores da plataforma têm permissão para aceder à faturação da plataforma LexCV."
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-row flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm" className="h-8 px-2 text-slate-500 hover:text-slate-900">
              <Link href="/plataforma">
                <ArrowLeft className="h-4 w-4 mr-1" />
                Voltar à Plataforma
              </Link>
            </Button>
          </div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight mt-2">
            Faturação da Plataforma
          </h1>
          <div className="mt-1 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
            <span>LexCV</span>
            <span className="mx-2 text-slate-300 dark:text-slate-700">/</span>
            <span className="text-blue-600 dark:text-blue-400">Emissão de Subscrições</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <RegistarPagamentoDialog />
        </div>
      </div>

      <PlatformConfigFiscalCard />

      <PlatformDocumentosTable />
    </div>
  );
}
