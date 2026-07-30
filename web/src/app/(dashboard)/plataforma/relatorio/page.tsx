"use client";

import * as React from "react";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { DataTable } from "@/components/shared/data-table/data-table";
import { useMe } from "@/hooks/use-me";
import { useTenantsAdmin } from "@/hooks/use-platform-admin";
import { tenantInitials } from "@/lib/tenant-initials";

import { TENANT_RESERVADO } from "../columns";
import { relatorioColumns } from "./columns";

/**
 * Ecrã `/plataforma/relatorio` — relatório de utilização por tenant,
 * acessível apenas a utilizadores com o papel PLATAFORMA_ADMIN. Serve de
 * base factual para a faturação manual: mostra, por tenant, o plano, o
 * limite contratado e o número de utilizadores ativos agora, incluindo os
 * tenants suspensos. É um ecrã estritamente de leitura — nenhuma ação de
 * criar, editar, suspender ou eliminar vive aqui.
 */
export default function RelatorioUtilizacaoPage() {
  // Guarda de página (defesa em profundidade; a camada autoritativa continua
  // a ser o gate de papel de classe do controlador de administração de
  // plataforma, backend, Phase 119). A ordem abaixo não é arbitrária: o
  // branch de carregamento tem de resolver antes do teste de papel, nunca
  // numa condição só — repete aqui a mesma correção que a consola /plataforma
  // já aplicou (WR-03, revisão de código da Phase 120). Sem este branch
  // extra, o conteúdo desta página renderizaria para qualquer utilizador
  // autenticado durante a janela anterior ao primeiro resolve de useMe(),
  // disparando o pedido à API antes de o papel do chamador ser conhecido.
  const me = useMe();

  if (!me.isFetched) {
    return null;
  }

  if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Não tem permissão para aceder ao relatório de utilização de tenants."
        backHref="/dashboard"
      />
    );
  }

  return <RelatorioUtilizacaoContent />;
}

function RelatorioUtilizacaoContent() {
  const [searchTerm, setSearchTerm] = React.useState("");
  const tenants = useTenantsAdmin();

  const tenantsFiltrados = React.useMemo(() => {
    const termo = searchTerm.trim().toLowerCase();
    const lista = tenants.data ?? [];
    if (!termo) return lista;
    return lista.filter((t) => t.nome.toLowerCase().includes(termo));
  }, [tenants.data, searchTerm]);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Button
          asChild
          variant="ghost"
          className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white"
        >
          <Link href="/plataforma" aria-label="Voltar">
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-semibold text-slate-900 dark:text-white tracking-tight">
            Relatório de Utilização
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Utilizadores ativos, plano e limite contratado por tenant — base para faturação manual
          </p>
        </div>
      </div>

      <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Utilização por Tenant</CardTitle>
          <CardDescription>Estado atual de todos os tenants registados na plataforma.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Input
            placeholder="Pesquisar tenant por nome..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="bg-slate-50 dark:bg-slate-950"
          />

          {tenants.isLoading ? (
            <div className="p-6 text-sm text-slate-500">A carregar...</div>
          ) : tenants.isError ? (
            <div className="p-6 text-sm text-red-600">
              {tenants.error instanceof Error ? tenants.error.message : "Erro ao carregar tenants."}
            </div>
          ) : (
            <>
              {/* Mobile: cards empilhados */}
              <div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
                {tenantsFiltrados.length === 0 ? (
                  <div className="p-6 text-center text-sm text-slate-500 dark:text-slate-400">
                    Sem resultados para os filtros aplicados.
                  </div>
                ) : (
                  tenantsFiltrados.map((tenant) => {
                    const initials = tenantInitials(tenant.nome);
                    const atingiuLimite =
                      tenant.limiteUtilizadores !== null &&
                      tenant.utilizadoresAtivos >= tenant.limiteUtilizadores;

                    return (
                      <div key={tenant.id} className="p-4">
                        <div className="flex items-center gap-3">
                          <div className="h-10 w-10 rounded-md flex-shrink-0 bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold">
                            {initials}
                          </div>
                          <div className="min-w-0 flex-1">
                            <span className="font-bold text-slate-900 dark:text-white text-sm">
                              {tenant.nome}
                            </span>
                            {tenant.nome === TENANT_RESERVADO ? (
                              <div className="mt-1">
                                <Badge variant="outline">Plataforma</Badge>
                              </div>
                            ) : null}
                          </div>
                          <Badge
                            variant={
                              tenant.plano === "STARTER"
                                ? "gray"
                                : tenant.plano === "STANDARD"
                                  ? "purple"
                                  : "amber"
                            }
                            className="font-bold text-[10px] flex-shrink-0 tracking-wide"
                          >
                            {tenant.plano}
                          </Badge>
                          <Badge
                            variant={tenant.ativo ? "green" : "red"}
                            className="font-bold text-[10px] flex-shrink-0"
                          >
                            {tenant.ativo ? "Ativo" : "Suspenso"}
                          </Badge>
                        </div>
                        <div className="mt-2 pl-[52px]">
                          <span
                            className={
                              atingiuLimite
                                ? "text-sm font-semibold text-red-600 dark:text-red-400"
                                : "text-sm text-slate-600 dark:text-slate-300"
                            }
                          >
                            {tenant.limiteUtilizadores !== null
                              ? `${tenant.utilizadoresAtivos}/${tenant.limiteUtilizadores} utilizadores`
                              : `${tenant.utilizadoresAtivos} utilizadores · sem limite`}
                          </span>
                          {atingiuLimite ? (
                            <span className="ml-2 text-[12px] uppercase tracking-wide text-red-600 dark:text-red-400">
                              limite atingido
                            </span>
                          ) : null}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              {/* Desktop: DataTable */}
              <div className="hidden md:block">
                <DataTable columns={relatorioColumns} data={tenantsFiltrados} getRowId={(t) => t.id} />
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
