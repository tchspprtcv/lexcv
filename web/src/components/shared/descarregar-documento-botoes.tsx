"use client";

import { FileCode, FileDown, Loader2 } from "lucide-react";
import * as React from "react";

import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { useDescarregarPdf, useDescarregarXml } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import { interpretarErroDescarga } from "@/lib/entrega-email";

export interface DescarregarDocumentoBotoesProps {
  documentoId: string;
  numeroFormatado: string;
  compact?: boolean;
}

export function DescarregarDocumentoBotoes({
  documentoId,
  numeroFormatado,
  compact = false,
}: DescarregarDocumentoBotoesProps) {
  const descarregarPdf = useDescarregarPdf();
  const descarregarXml = useDescarregarXml();

  const onDescarregarPdf = () => {
    descarregarPdf.mutate(
      { documentoId },
      {
        onError: (error) => {
          const mensagem = interpretarErroDescarga(error);
          if (mensagem) toast.error(mensagem);
        },
      },
    );
  };

  const onDescarregarXml = () => {
    descarregarXml.mutate(
      { documentoId, numeroFormatado },
      {
        onError: (error) => {
          const mensagem = interpretarErroDescarga(error);
          if (mensagem) toast.error(mensagem);
        },
      },
    );
  };

  if (compact) {
    return (
      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={`Descarregar PDF de ${numeroFormatado}`}
              aria-busy={descarregarPdf.isPending}
              disabled={descarregarPdf.isPending}
              onClick={onDescarregarPdf}
            >
              {descarregarPdf.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <FileDown className="h-4 w-4" aria-hidden="true" />
              )}
            </Button>
          </TooltipTrigger>
          <TooltipContent>Descarregar PDF</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={`Descarregar XML de ${numeroFormatado}`}
              aria-busy={descarregarXml.isPending}
              disabled={descarregarXml.isPending}
              onClick={onDescarregarXml}
            >
              {descarregarXml.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <FileCode className="h-4 w-4" aria-hidden="true" />
              )}
            </Button>
          </TooltipTrigger>
          <TooltipContent>Descarregar XML</TooltipContent>
        </Tooltip>
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button
        type="button"
        variant="outline"
        disabled={descarregarPdf.isPending}
        onClick={onDescarregarPdf}
      >
        {descarregarPdf.isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            <span>A preparar PDF...</span>
          </>
        ) : (
          <>
            <FileDown className="h-4 w-4" aria-hidden="true" />
            <span>Descarregar PDF</span>
          </>
        )}
      </Button>

      <Button
        type="button"
        variant="outline"
        disabled={descarregarXml.isPending}
        onClick={onDescarregarXml}
      >
        {descarregarXml.isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            <span>A preparar XML...</span>
          </>
        ) : (
          <>
            <FileCode className="h-4 w-4" aria-hidden="true" />
            <span>Descarregar XML</span>
          </>
        )}
      </Button>
    </div>
  );
}
