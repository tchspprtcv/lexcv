import { Clock, Info, MailCheck, MailMinus, MailX, TriangleAlert, UserX, type LucideIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { apresentacaoEntregaEmail, type IconeEntregaEmail } from "@/lib/entrega-email";

// Phase 137 (ENTR-04, ENTR-06): badge ÚNICO do estado da entrega por email. Variantes neutras
// (outline/secondary) vindas de lib/entrega-email -- nunca acento nem verde/âmbar/vermelho, nem
// mesmo em "Enviado". O estado é transmitido por texto visível + ícone, nunca só por cor. Um estado
// que este build não conheça cai em "Estado desconhecido". Para `null` (documento sem linha de
// entrega) o chamador mostra "—" em vez do badge.

const ICONES: Record<IconeEntregaEmail, LucideIcon> = {
  MailX,
  MailMinus,
  UserX,
  Clock,
  MailCheck,
  TriangleAlert,
  Info,
};

export function EntregaEmailBadge({
  estado,
  titulo,
}: {
  estado: string | null | undefined;
  /** Descrição já escolhida pelo chamador (ex.: FALHOU com contagem e gate); por omissão a da lib. */
  titulo?: string;
}) {
  const apresentacao = apresentacaoEntregaEmail(estado);
  const Icone = ICONES[apresentacao.icone];

  return (
    <Badge variant={apresentacao.variante} className="gap-1" title={titulo ?? apresentacao.descricao}>
      <Icone className="h-3 w-3" aria-hidden="true" />
      {apresentacao.rotulo}
    </Badge>
  );
}
