"use client";

import * as React from "react";
import { Lock, Pencil, Plus, Unlock } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTable } from "@/components/shared/data-table/data-table";
import { toast } from "@/hooks/use-toast";
import { useMe } from "@/hooks/use-me";
import { useCreateTenant, useTenantsAdmin } from "@/hooks/use-platform-admin";
import { cn } from "@/lib/utils";
import type { TenantAdminSummary } from "@/types/platform-admin";
import type { SetupInitializeRequest } from "@/types/setup";

import { columns, TENANT_RESERVADO } from "./columns";
import { CriarTenantPanel } from "./criar-tenant-panel";

/**
 * Ecra `/plataforma` — consola de administracao de tenants, acessivel apenas
 * a utilizadores com o papel PLATAFORMA_ADMIN. Compoe as pecas ja construidas
 * pelos planos anteriores desta fase (tipos/hooks, colunas, painel de criacao)
 * num unico ecra: lista com pesquisa, criacao inline, edicao de plano/limite
 * e alternancia de suspenso/ativo.
 */
export default function PlataformaPage() {
  // Guarda de pagina (defesa em profundidade). A camada autoritativa de
  // autorizacao e o gate de papel de classe do controlador de administracao
  // de plataforma (backend, Phase 119) — este guard e apenas espelho de UX,
  // a mesma disciplina de duas camadas que `clientes/page.tsx` ja pratica.
  const me = useMe();

  if (me.isFetched && !me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Não tem permissão para aceder à consola de administração de tenants."
        backHref="/dashboard"
      />
    );
  }

  return <PlataformaPageContent />;
}

function PlataformaPageContent() {
  const [isFormOpen, setIsFormOpen] = React.useState(false);
  const [searchTerm, setSearchTerm] = React.useState("");

  const tenants = useTenantsAdmin();
  const criarTenant = useCreateTenant();

  const tenantsFiltrados = React.useMemo(() => {
    const termo = searchTerm.trim().toLowerCase();
    const lista = tenants.data ?? [];
    if (!termo) return lista;
    return lista.filter((t) => t.nome.toLowerCase().includes(termo));
  }, [tenants.data, searchTerm]);

  // Task 2 liga estes dois handlers aos estados dos dialogos de edicao e de
  // alteracao de estado — aqui ficam vazios apenas para as colunas ja poderem
  // ser instanciadas.
  const onEdit = React.useCallback((_tenant: TenantAdminSummary) => {
    // Task 2: abre o Dialog "Editar Tenant"
  }, []);

  const onToggleAtivo = React.useCallback((_tenant: TenantAdminSummary) => {
    // Task 2: abre o AlertDialog de suspensao/reativacao
  }, []);

  const tenantColumns = React.useMemo(
    () => columns({ onEdit, onToggleAtivo }),
    [onEdit, onToggleAtivo],
  );

  const handleCreateSubmit = async (payload: SetupInitializeRequest) => {
    try {
      await criarTenant.mutateAsync(payload);
      toast.success("Tenant criado com sucesso.");
      setIsFormOpen(false);
    } catch {
      // O wrapper de fetch partilhado ja mostrou o toast com a mensagem do
      // backend (ex.: "Já existe um utilizador com este email."). Mantemos o
      // painel aberto para o operador nao perder o que escreveu.
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
          Administração de Tenants
        </h1>
        <div className="mt-2 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
          <span>LexCV</span>
          <span className="mx-2 text-slate-300 dark:text-slate-700">/</span>
          <span className="text-blue-600 dark:text-blue-400">Consola de Plataforma</span>
        </div>
      </div>

      {isFormOpen ? (
        <CriarTenantPanel
          onCancel={() => setIsFormOpen(false)}
          onSubmit={handleCreateSubmit}
          isSubmitting={criarTenant.isPending}
        />
      ) : (
        <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle className="text-xl font-semibold">Tenants Registados</CardTitle>
              <CardDescription>Lista de organizações com acesso à plataforma LexCV.</CardDescription>
            </div>
            <Button
              onClick={() => setIsFormOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
            >
              <Plus className="h-4 w-4" />
              Criar Tenant
            </Button>
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
                      const initials = tenant.nome
                        .split(" ")
                        .filter(Boolean)
                        .slice(0, 2)
                        .map((w) => w[0])
                        .join("")
                        .toUpperCase();
                      const suspenderDesativado = tenant.nome === TENANT_RESERVADO && tenant.ativo;

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
                          <div className="mt-3 pl-[52px] flex items-center gap-1">
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="ghost"
                                  aria-label="Editar tenant"
                                  className="h-12 w-12 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
                                  onClick={() => onEdit(tenant)}
                                >
                                  <Pencil className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>Editar</TooltipContent>
                            </Tooltip>

                            {suspenderDesativado ? (
                              // buttonVariants embute disabled:pointer-events-none, por isso um
                              // TooltipTrigger colocado diretamente sobre o Button desativado
                              // nunca dispararia — o span tabIndex={0} e o alvo real do tooltip
                              // (composicao Phase 118), tal como em columns.tsx (desktop).
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <span tabIndex={0}>
                                    <Button
                                      type="button"
                                      size="sm"
                                      variant="ghost"
                                      aria-label="Suspender tenant"
                                      disabled
                                      className="h-12 w-12 p-0 text-slate-500"
                                    >
                                      <Lock className="h-4 w-4" />
                                    </Button>
                                  </span>
                                </TooltipTrigger>
                                <TooltipContent>
                                  Não é possível suspender o tenant da plataforma (LexCV).
                                </TooltipContent>
                              </Tooltip>
                            ) : (
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button
                                    type="button"
                                    size="sm"
                                    variant="ghost"
                                    aria-label={tenant.ativo ? "Suspender tenant" : "Reativar tenant"}
                                    className={cn(
                                      "h-12 w-12 p-0 text-slate-500 transition-colors",
                                      tenant.ativo
                                        ? "hover:text-red-600 dark:hover:text-red-400"
                                        : "hover:text-emerald-600 dark:hover:text-emerald-400",
                                    )}
                                    onClick={() => onToggleAtivo(tenant)}
                                  >
                                    {tenant.ativo ? (
                                      <Lock className="h-4 w-4" />
                                    ) : (
                                      <Unlock className="h-4 w-4" />
                                    )}
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>{tenant.ativo ? "Suspender" : "Reativar"}</TooltipContent>
                              </Tooltip>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

                {/* Desktop: DataTable */}
                <div className="hidden md:block">
                  <DataTable columns={tenantColumns} data={tenantsFiltrados} getRowId={(t) => t.id} />
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
