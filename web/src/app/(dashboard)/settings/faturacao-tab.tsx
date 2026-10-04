"use client";

import { Info } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Badge } from "@/components/ui/badge";
import { useConfiguracaoFiscal } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";

import { FaturacaoDadosForm } from "./faturacao-dados-form";
import { FaturacaoSeriesCard } from "./faturacao-series-card";

// Aba "Faturação" em Definições (133-UI-SPEC "Screen Structure", CFG-01/02/05). A visibilidade
// por `can.manage("financeiro")` é apenas UX -- a autoridade é o `@PreAuthorize` de classe do
// controller de faturação no backend (hasAuthority('financeiro:manage')).
// Ordem fixa dos blocos: cabeçalho, aviso de simulação, dados fiscais, ativação e email
// (Plan 08), séries de numeração.

export function FaturacaoTab() {
  // Regra dos Hooks: todos os hooks abaixo tem de ser chamados incondicionalmente. Os returns
  // condicionais (a carregar / acesso negado) ficam DEPOIS de todos os hooks, e as queries
  // recebem `enabled = podeGerir`, por isso um utilizador sem permissão nunca dispara um 403.
  const { isFetched, can } = usePermissions();
  const podeGerir = can.manage("financeiro");
  const configuracao = useConfiguracaoFiscal(podeGerir);

  if (!isFetched) {
    return <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>;
  }

  if (!podeGerir) {
    return (
      <AccessDeniedState description="Não tem permissão para gerir a faturação do escritório." />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-slate-900 dark:text-white">Faturação</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Dados fiscais do escritório, ativação da faturação e séries de numeração. Só
            utilizadores com permissão de gestão financeira vêem este separador.
          </p>
        </div>
        {configuracao.data ? (
          configuracao.data.ativa ? (
            <Badge variant="secondary">Faturação ativa</Badge>
          ) : (
            <Badge variant="outline">Faturação desligada</Badge>
          )
        ) : null}
      </div>

      <div
        role="note"
        className="flex items-start gap-2 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
      >
        <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <p>
          Os documentos emitidos nesta versão são simulados e não têm validade fiscal. Continue a
          emitir as suas faturas válidas no software de faturação homologado.
        </p>
      </div>

      <FaturacaoDadosForm configuracao={configuracao} />

      <FaturacaoSeriesCard habilitado={podeGerir} />
    </div>
  );
}
