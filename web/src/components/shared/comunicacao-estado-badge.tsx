import { CircleSlash, Clock, Info, TriangleAlert, type LucideIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { apresentacaoEstadoComunicacao, type IconeEstadoComunicacao } from "@/lib/comunicacao-fiscal";

// Phase 136 (DFE-04, DFE-06): badge ÚNICO do estado da comunicação eFatura. Variantes neutras
// (outline/secondary) vindas de lib/comunicacao-fiscal; o estado é transmitido por texto visível +
// ícone, nunca só por cor. Um estado que este build não conheça cai em "Estado desconhecido".

const ICONES: Record<IconeEstadoComunicacao, LucideIcon> = {
  Clock,
  Info,
  CircleSlash,
  TriangleAlert,
};

export function ComunicacaoEstadoBadge({
  estado,
  descricao,
}: {
  estado: string | null | undefined;
  /** Descrição longa já escolhida pelo chamador (ex.: sem permissão); por omissão a da lib. */
  descricao?: string;
}) {
  const apresentacao = apresentacaoEstadoComunicacao(estado);
  const Icone = ICONES[apresentacao.icone];

  return (
    <Badge variant={apresentacao.variante} className="gap-1" title={descricao ?? apresentacao.descricao}>
      <Icone className="h-3 w-3" aria-hidden="true" />
      {apresentacao.rotulo}
    </Badge>
  );
}
