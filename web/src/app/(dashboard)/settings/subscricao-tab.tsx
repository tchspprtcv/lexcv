"use client";

import * as React from "react";
import { Download, FileCode, FileText, Info, RefreshCw, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useMe } from "@/hooks/use-me";
import {
  useDescarregarMinhaSubscricaoPdf,
  useDescarregarMinhaSubscricaoXml,
  useMinhasSubscricoes,
} from "@/hooks/use-minhas-subscricoes";
import { toast } from "@/hooks/use-toast";
import type { DocumentoFiscalResumo, EstadoComunicacaoFiscal } from "@/types/faturacao";

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

export function SubscricaoTab() {
  const [page, setPage] = React.useState(0);
  const { data: me } = useMe();
  const { data, isLoading, refetch, isFetching } = useMinhasSubscricoes({ page, size: 10 });

  const descarregarPdf = useDescarregarMinhaSubscricaoPdf();
  const descarregarXml = useDescarregarMinhaSubscricaoXml();

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

  const docs = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="space-y-6">
      {/* Information Banner */}
      <div className="rounded-xl border border-blue-200 bg-blue-50/50 p-4 dark:border-blue-900 dark:bg-blue-950/30">
        <div className="flex gap-3">
          <Info className="h-5 w-5 text-blue-600 dark:text-blue-400 shrink-0 mt-0.5" />
          <div className="text-sm">
            <h4 className="font-semibold text-blue-900 dark:text-blue-200">
              Faturação de Subscrição LexCV
            </h4>
            <p className="text-blue-700 dark:text-blue-300 mt-0.5">
              Os documentos fiscais (faturas-recibo e notas de crédito) relativos à mensalidade ou anuidade da sua subscrição LexCV são emitidos pela plataforma e certificados de acordo com as normas da Direção Nacional de Receitas do Estado (DNRE).
            </p>
          </div>
        </div>
      </div>

      {/* Subscription Summary Card */}
      <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
              <CardTitle className="text-lg font-semibold">Subscrição do Escritório</CardTitle>
            </div>
            <Badge variant="secondary" className="bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 font-medium text-xs">
              Subscrição Ativa
            </Badge>
          </div>
          <CardDescription>
            Detalhes da subscrição associada à entidade jurídica deste escritório.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-3 bg-slate-50 dark:bg-slate-900/80 rounded-lg border border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400 block font-medium">Titular da Conta</span>
              <span className="text-slate-900 dark:text-white font-semibold text-sm mt-0.5 block">
                {me?.tenant_nome || "Escritório LexCV"}
              </span>
            </div>
            <div className="p-3 bg-slate-50 dark:bg-slate-900/80 rounded-lg border border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400 block font-medium">Plano Atual</span>
              <span className="text-slate-900 dark:text-white font-semibold text-sm mt-0.5 block">
                {me?.tenant_plano || "Plano Base"}
              </span>
            </div>
            <div className="p-3 bg-slate-50 dark:bg-slate-900/80 rounded-lg border border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400 block font-medium">Utilizadores Ativos</span>
              <span className="text-slate-900 dark:text-white font-semibold text-sm mt-0.5 block">
                {me?.tenant_utilizadores_ativos ?? 1} {me?.tenant_limite_utilizadores ? `/ ${me.tenant_limite_utilizadores}` : ""}
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Subscription Documents Table */}
      <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <div>
            <CardTitle className="text-lg font-semibold">Histórico de Faturas e Recibos</CardTitle>
            <CardDescription className="mt-1">
              Consulte e descarregue os documentos fiscais originais (PDF e XML assinado) emitidos pela plataforma.
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
              Ainda não existem faturas de subscrição emitidas para este escritório.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="text-[11px] uppercase tracking-wider text-slate-500 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="py-3 px-3 font-semibold">Tipo</th>
                    <th className="py-3 px-3 font-semibold">Número</th>
                    <th className="py-3 px-3 font-semibold">Data Emissão</th>
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
    </div>
  );
}
