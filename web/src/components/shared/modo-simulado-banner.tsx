"use client";

import { Info } from "lucide-react";

import { podeLerDocumentosFiscais, podeRegistarPagamentos, useEstadoEmissao } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import { COPY_MODO_SIMULADO_LEAD, COPY_MODO_SIMULADO_TEXTO } from "@/lib/comunicacao-fiscal";

// Phase 136 (DFE-06, 136-UI-SPEC Surface 3): aviso de página "Modo simulado". O modo vem SÓ do
// backend (GET /faturacao/estado-emissao -> modoComunicacao); só aparece quando é SIMULADO. Não é
// dispensável, não guarda nada no browser e não é fixo no ecrã (desliza com a página).

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";

export function ModoSimuladoBanner() {
  const { permissions } = usePermissions();
  // O endpoint de estado de emissão aceita financeiro:view OU financeiro:edit (exatos).
  const enabled = podeLerDocumentosFiscais(permissions) || podeRegistarPagamentos(permissions);
  const estadoEmissao = useEstadoEmissao(enabled);

  const simulado = estadoEmissao.data?.modoComunicacao === "SIMULADO";
  if (!simulado) return null;

  return (
    <div role="status" className={`flex gap-2 ${NOTICE_CLASSES}`}>
      <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <p>
        <span className="font-semibold">{COPY_MODO_SIMULADO_LEAD}</span> {COPY_MODO_SIMULADO_TEXTO}
      </p>
    </div>
  );
}
