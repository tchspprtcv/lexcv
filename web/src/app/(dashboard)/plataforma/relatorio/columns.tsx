"use client";

import type { ColumnDef } from "@tanstack/react-table";

import { Badge } from "@/components/ui/badge";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { tenantInitials } from "@/lib/tenant-initials";
import type { TenantAdminSummary, TenantPlano } from "@/types/platform-admin";

import { TENANT_RESERVADO } from "../columns";

const PLANO_BADGE_VARIANT: Record<TenantPlano, "gray" | "purple" | "amber"> = {
  STARTER: "gray",
  STANDARD: "purple",
  ENTERPRISE: "amber",
};

/**
 * Colunas do relatório de utilização por tenant (`/plataforma/relatorio`),
 * um ecrã estritamente de leitura. Ao contrário dos restantes ficheiros de
 * colunas deste projeto — todos organizados à volta de uma função que recebe
 * callbacks de linha —, este exporta diretamente um array: não existe aqui
 * nenhuma ação de linha para receber, por ser o primeiro ecrã de listagem
 * do codebase sem qualquer escrita associada.
 *
 * As quatro células abaixo replicam, célula a célula, as da consola de
 * administração de tenants, de propósito — para que os mesmos dados nunca
 * apareçam com um aspeto diferente consoante o ecrã onde são vistos.
 *
 * A coluna de utilizadores em particular mostra o valor tal como chega da
 * API, sem nenhuma soma ou contagem feita aqui: este relatório existe
 * precisamente para expor essa contagem, e perderia sentido se recalculasse,
 * em paralelo, um número que já vem pronto do backend.
 */
export const relatorioColumns: ColumnDef<TenantAdminSummary>[] = [
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
];
