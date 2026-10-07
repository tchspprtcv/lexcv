"use client";

import * as React from "react";
import { Download, FileCode, FileText, MinusCircle, RefreshCw } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useDescarregarPlatformPdf,
  useDescarregarPlatformXml,
  usePlatformDocumentos,
} from "@/hooks/use-platform-faturacao";
import { toast } from "@/hooks/use-toast";
import type { DocumentoFiscalResumo, EstadoComunicacaoFiscal, TipoDocumentoFiscal } from "@/types/faturacao";

import { EmitirNcDialog } from "./emitir-nc-dialog";

function formatCurrency(val: number): string {
  return new Intl.NumberFormat("pt-CV", {
    style: "currency",
    currency: "CVE",
    minimumFractionDigits: 2,
  }).format(val);
}

function EstadoBadge({ estado }: { estado: EstadoComunicacaoFiscal | null | undefined }) {
  if (!estado) return null;
  switch (estado) {
    case "ACEITE_SIMULADO":
      return (
        <Badge variant="secondary" className="bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 text-[10px]">
          Aceite (Simulado)
        </Badge>
      );
    case "PENDENTE":
      return (
        <Badge variant="outline" className="text-amber-600 border-amber-300 bg-amber-50 dark:bg-amber-950/40 text-[10px]">
          Pendente
        </Badge>
      );
    case "REJEITADO":
      return (
        <Badge variant="red" className="text-[10px]">
          Rejeitado
        </Badge>
      );
    case "ERRO":
      return (
        <Badge variant="red" className="text-[10px]">
          Erro
        </Badge>
      );
    default:
      return <Badge variant="outline" className="text-[10px]">{estado}</Badge>;
  }
}

export function PlatformDocumentosTable() {
  const [page, setPage] = React.useState(0);
  const [docParaNc, setDocParaNc] = React.useState<DocumentoFiscalResumo | null>(null);
  const [ncDialogOpen, setNcDialogOpen] = React.useState(false);

  const { data, isLoading, refetch, isFetching } = usePlatformDocumentos({
    page,
    size: 15,
  });

  const descarregarPdf = useDescarregarPlatformPdf();
  const descarregarXml = useDescarregarPlatformXml();

  const handleDownloadPdf = async (doc: DocumentoFiscalResumo) => {
    try {
      await descarregarPdf.mutateAsync({ documentoId: doc.id });
    } catch {
      toast.error("PDF ainda não disponível para este documento.");
    }
  };

  const handleDownloadXml = async (doc: DocumentoFiscalResumo) => {
    try {
      await descarregarXml.mutateAsync({
        documentoId: doc.id,
        numeroFormatado: doc.numeroFormatado,
      });
    } catch {
      toast.error("XML ainda não disponível para este documento.");
    }
  };

  const handleOpenNc = (doc: DocumentoFiscalResumo) => {
    setDocParaNc(doc);
    setNcDialogOpen(true);
  };

  const docs = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <>
      <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <div>
            <CardTitle className="text-lg font-semibold">Documentos Fiscais de Subscrição</CardTitle>
            <CardDescription className="mt-1">
              Faturas-recibo e notas de crédito emitidas pela plataforma LexCV para os escritórios aderentes.
            </CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isFetching}
            className="text-xs h-8 gap-1"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? "animate-spin" : ""}`} />
            Atualizar
          </Button>
        </CardHeader>

        <CardContent>
          {isLoading ? (
            <div className="py-12 text-center text-sm text-slate-500">A carregar documentos...</div>
          ) : docs.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">
              Nenhum documento fiscal de subscrição emitido até ao momento.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="text-[11px] uppercase tracking-wider text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="py-3 px-3 font-semibold">Tipo</th>
                    <th className="py-3 px-3 font-semibold">Número</th>
                    <th className="py-3 px-3 font-semibold">Data Emissão</th>
                    <th className="py-3 px-3 font-semibold">Escritório Adquirente</th>
                    <th className="py-3 px-3 font-semibold">NIF</th>
                    <th className="py-3 px-3 font-semibold text-right">Total</th>
                    <th className="py-3 px-3 font-semibold text-center">eFatura</th>
                    <th className="py-3 px-3 font-semibold text-right">Ações</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {docs.map((doc) => (
                    <tr key={doc.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/50 transition-colors">
                      <td className="py-3 px-3">
                        {doc.tipo === "FR" ? (
                          <Badge variant="outline" className="bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-950/40 dark:text-blue-300 font-mono text-[10px]">
                            FR
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 font-mono text-[10px]">
                            NC
                          </Badge>
                        )}
                      </td>
                      <td className="py-3 px-3 font-mono font-medium text-slate-900 dark:text-white">
                        {doc.numeroFormatado}
                      </td>
                      <td className="py-3 px-3 text-slate-600 dark:text-slate-300">
                        {doc.dataEmissao}
                      </td>
                      <td className="py-3 px-3 font-medium text-slate-800 dark:text-slate-200 max-w-[200px] truncate">
                        {doc.adquirenteNome}
                      </td>
                      <td className="py-3 px-3 font-mono text-slate-600 dark:text-slate-400">
                        {doc.adquirenteNif}
                      </td>
                      <td className="py-3 px-3 text-right font-medium text-slate-900 dark:text-white">
                        {formatCurrency(doc.totalDocumento)}
                      </td>
                      <td className="py-3 px-3 text-center">
                        <EstadoBadge estado={doc.estadoComunicacao} />
                      </td>
                      <td className="py-3 px-3 text-right space-x-1 whitespace-nowrap">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDownloadPdf(doc)}
                          disabled={descarregarPdf.isPending}
                          className="h-7 w-7 p-0 text-slate-600 hover:text-blue-600"
                          title="Descarregar PDF"
                        >
                          <FileText className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDownloadXml(doc)}
                          disabled={descarregarXml.isPending}
                          className="h-7 w-7 p-0 text-slate-600 hover:text-emerald-600"
                          title="Descarregar XML"
                        >
                          <FileCode className="h-3.5 w-3.5" />
                        </Button>
                        {doc.tipo === "FR" && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenNc(doc)}
                            className="h-7 w-7 p-0 text-slate-600 hover:text-red-600"
                            title="Emitir Nota de Crédito"
                          >
                            <MinusCircle className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {totalPages > 1 && (
                <div className="flex items-center justify-between pt-4 border-t border-slate-100 dark:border-slate-800 text-xs">
                  <span className="text-slate-500">
                    Página {page + 1} de {totalPages} ({data?.totalElements ?? 0} documentos)
                  </span>
                  <div className="space-x-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="h-7 text-xs"
                    >
                      Anterior
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                      className="h-7 text-xs"
                    >
                      Seguinte
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      <EmitirNcDialog
        documento={docParaNc}
        open={ncDialogOpen}
        onOpenChange={setNcDialogOpen}
      />
    </>
  );
}
