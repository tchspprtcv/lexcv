"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Lock, Pencil, Unlock } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { cn } from "@/lib/utils";
import { tenantInitials } from "@/lib/tenant-initials";
import type { TenantAdminSummary, TenantPlano } from "@/types/platform-admin";

/**
 * Nome literal do tenant reservado da plataforma. Exportado para o page.tsx
 * (Plan 05) reaproveitar o mesmo literal nos cards mobile, em vez de o repetir.
 */
export const TENANT_RESERVADO = "LexCV";

const PLANO_BADGE_VARIANT: Record<TenantPlano, "gray" | "purple" | "amber"> = {
  STARTER: "gray",
  STANDARD: "purple",
  ENTERPRISE: "amber",
};

/**
 * Celula de acoes por linha. Componente separado porque as column defs do
 * TanStack sao objetos simples e nao podem chamar hooks. Todas as acoes sobem
 * por callback ao page.tsx, que e onde vive o estado dos dialogos de edicao
 * e de confirmacao de suspensao/reativacao.
 */
function TenantAcoesCell({
  tenant,
  onEdit,
  onToggleAtivo,
}: {
  tenant: TenantAdminSummary;
  onEdit: (tenant: TenantAdminSummary) => void;
  onToggleAtivo: (tenant: TenantAdminSummary) => void;
}) {
  const suspenderDesativado = tenant.nome === TENANT_RESERVADO && tenant.ativo;

  return (
    <div className="inline-flex items-center gap-1">
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="Editar tenant"
            className="h-9 w-9 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
            onClick={() => onEdit(tenant)}
          >
            <Pencil className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Editar</TooltipContent>
      </Tooltip>

      {suspenderDesativado ? (
        // buttonVariants embute disabled:pointer-events-none, por isso um
        // TooltipTrigger colocado diretamente sobre o Button desativado nunca
        // dispararia — o span tabIndex={0} e o alvo real do tooltip
        // (composicao Phase 118, obrigatoria por rato e por teclado).
        <Tooltip>
          <TooltipTrigger asChild>
            <span tabIndex={0}>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                aria-label="Suspender tenant"
                disabled
                className="h-9 w-9 p-0 text-slate-500"
              >
                <Lock className="h-4 w-4" />
              </Button>
            </span>
          </TooltipTrigger>
          <TooltipContent>Não é possível suspender o tenant da plataforma (LexCV).</TooltipContent>
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
                "h-9 w-9 p-0 text-slate-500 transition-colors",
                tenant.ativo
                  ? "hover:text-red-600 dark:hover:text-red-400"
                  : "hover:text-emerald-600 dark:hover:text-emerald-400",
              )}
              onClick={() => onToggleAtivo(tenant)}
            >
              {tenant.ativo ? <Lock className="h-4 w-4" /> : <Unlock className="h-4 w-4" />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{tenant.ativo ? "Suspender" : "Reativar"}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
}

/**
 * Column defs para a DataTable de tenants. Recebe os callbacks `onEdit` e
 * `onToggleAtivo` do page.tsx (Plan 05) — as column defs nao abrem dialogos
 * por si, apenas notificam o chamador.
 */
export function columns({
  onEdit,
  onToggleAtivo,
}: {
  onEdit: (tenant: TenantAdminSummary) => void;
  onToggleAtivo: (tenant: TenantAdminSummary) => void;
}): ColumnDef<TenantAdminSummary>[] {
  return [
    {
      id: "nome",
      accessorKey: "nome",
      enableHiding: false,
      meta: { label: "Nome" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Nome" />,
      cell: ({ row }) => {
        const tenant = row.original;
        const initials = tenantInitials(tenant.nome);

        return (
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-md bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
              {initials}
            </div>
            <div className="min-w-0">
              <span className="font-bold text-slate-900 dark:text-white">{tenant.nome}</span>
              {tenant.nome === TENANT_RESERVADO ? (
                <div className="mt-1">
                  <Badge variant="outline">Plataforma</Badge>
                </div>
              ) : null}
            </div>
          </div>
        );
      },
    },
    {
      id: "plano",
      accessorKey: "plano",
      meta: { label: "Plano" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Plano" />,
      cell: ({ row }) => {
        const plano = row.original.plano;
        return (
          <Badge variant={PLANO_BADGE_VARIANT[plano]} className="font-bold tracking-wide">
            {plano}
          </Badge>
        );
      },
    },
    {
      id: "utilizadores",
      accessorFn: (tenant) => tenant.utilizadoresAtivos,
      meta: { label: "Utilizadores" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Utilizadores" />,
      cell: ({ row }) => {
        const tenant = row.original;
        const atingiuLimite =
          tenant.limiteUtilizadores !== null && tenant.utilizadoresAtivos >= tenant.limiteUtilizadores;

        return (
          <div className="flex flex-col">
            <span
              className={
                atingiuLimite
                  ? "font-semibold text-red-600 dark:text-red-400"
                  : "text-slate-800 dark:text-slate-200"
              }
            >
              {tenant.limiteUtilizadores !== null
                ? `${tenant.utilizadoresAtivos}/${tenant.limiteUtilizadores}`
                : `${tenant.utilizadoresAtivos} · sem limite`}
            </span>
            {atingiuLimite ? (
              <span className="text-[12px] uppercase tracking-wide text-red-600 dark:text-red-400">
                limite atingido
              </span>
            ) : null}
          </div>
        );
      },
    },
    {
      id: "estado",
      accessorKey: "ativo",
      meta: { label: "Estado" },
      header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
      cell: ({ row }) => {
        const ativo = row.original.ativo;
        return <Badge variant={ativo ? "green" : "red"}>{ativo ? "Ativo" : "Suspenso"}</Badge>;
      },
    },
    {
      id: "acoes",
      enableSorting: false,
      enableHiding: false,
      meta: { label: "Ações" },
      header: ({ column }) => (
        <DataTableColumnHeader column={column} title="Ações" className="block text-right" />
      ),
      cell: ({ row }) => (
        <div className="flex justify-end">
          <TenantAcoesCell tenant={row.original} onEdit={onEdit} onToggleAtivo={onToggleAtivo} />
        </div>
      ),
    },
  ];
}
