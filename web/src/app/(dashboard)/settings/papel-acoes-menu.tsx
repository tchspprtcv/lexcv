"use client";

import { Lock, MoreVertical, Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { OfficePapel } from "@/types/office-rbac";

/**
 * Kebab de acoes de um papel do escritorio (coluna da matriz RBAC,
 * `page.tsx`). Puramente de apresentacao -- nao chama nenhum hook de mutacao
 * nem faz nenhum pedido de rede; o chamador decide o que "Renomear" e
 * "Apagar Papel" fazem atraves das props `onRenomear`/`onApagar`.
 *
 * Quando o papel nao pode ser apagado (`podeApagar === false`), o item
 * destrutivo NAO e substituido por um `DropdownMenuItem` desabilitado com um
 * `Tooltip` por cima -- e uma `div` estatica, sempre visivel, explicando o
 * motivo. UI-SPEC §7: um Tooltip que so aparece ao passar o rato sobre um
 * item desabilitado, dentro de um overlay ja aberto, nao e alcancavel de
 * forma fiavel por teclado nem por leitor de ecra (focus handling de
 * overlays aninhados) -- e este e um estado de recusa real, nao decoracao,
 * pelo que precisa de ficar sempre legivel, nao condicionado a hover/foco.
 */
export function PapelAcoesMenu({
  papel,
  onRenomear,
  onApagar,
}: {
  papel: OfficePapel;
  onRenomear: () => void;
  onApagar: () => void;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6"
          aria-label={`Mais ações para o papel ${papel.nome}`}
        >
          <MoreVertical className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={onRenomear}>
          <Pencil className="h-4 w-4" /> Renomear
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        {papel.podeApagar ? (
          <DropdownMenuItem variant="destructive" onSelect={onApagar}>
            <Trash2 className="h-4 w-4" /> Apagar Papel
          </DropdownMenuItem>
        ) : (
          <div className="px-2 py-1.5 text-xs text-slate-500 dark:text-slate-400 flex items-start gap-2">
            <Lock className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
            <span>
              {papel.protegido
                ? "Papel protegido: administrador do escritório."
                : `Atribuído a ${papel.utilizadoresAtribuidos} utilizador${papel.utilizadoresAtribuidos === 1 ? "" : "es"}. Remova-o(s) em Gestão de Utilizadores para poder apagar.`}
            </span>
          </div>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
