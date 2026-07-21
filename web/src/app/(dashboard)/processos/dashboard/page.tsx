"use client";

import * as React from "react";
import Link from "next/link";
import { ArrowLeft, Clock, FileText, AlertTriangle, Briefcase, Activity, CheckCircle2 } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useProcessosDashboard } from "@/hooks/use-processos-dashboard";

export default function ProcessosDashboardPage() {
  const [activeTab, setActiveTab] = React.useState<"operacional" | "executivo">("operacional");
  const dashboardData = useProcessosDashboard();

  const data = dashboardData.data;
  const isLoading = dashboardData.isLoading;

  if (isLoading) {
    return <div className="p-8 text-slate-500">A carregar dashboard...</div>;
  }

  if (!data) {
    return <div className="p-8 text-red-500">Erro ao carregar dashboard.</div>;
  }

  return (
    <div className="space-y-6 max-w-[1200px] mx-auto pb-12">
      <div className="flex items-center gap-4">
        <Button asChild variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white">
          <Link href="/processos">
            <ArrowLeft className="h-4 w-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Dashboard de Processos</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Acompanhamento operacional e indicadores executivos</p>
        </div>
      </div>

      <div className="flex gap-4 border-b border-slate-200 dark:border-slate-800">
        <button
          onClick={() => setActiveTab("operacional")}
          className={`px-4 py-2 font-bold tracking-wide uppercase text-sm border-b-2 transition-colors ${
            activeTab === "operacional"
              ? "border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400"
              : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-300"
          }`}
        >
          Operacional
        </button>
        <button
          onClick={() => setActiveTab("executivo")}
          className={`px-4 py-2 font-bold tracking-wide uppercase text-sm border-b-2 transition-colors ${
            activeTab === "executivo"
              ? "border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400"
              : "border-transparent text-slate-500 hover:text-slate-900 dark:hover:text-slate-300"
          }`}
        >
          Executivo
        </button>
      </div>

      {activeTab === "operacional" && (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="grid gap-4 md:grid-cols-2">
            <Link href="/processos?risco=CRITICO" className="block focus:outline-none focus:ring-2 ring-blue-500">
              <Card className="border-slate-200 dark:border-slate-800 hover:border-red-500/50 dark:hover:border-red-500/50 transition-colors group cursor-pointer bg-white dark:bg-[#020617]">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div className="h-12 w-12 rounded-md bg-red-50 dark:bg-red-500/10 flex items-center justify-center border border-red-100 dark:border-red-500/20 group-hover:scale-110 transition-transform">
                      <AlertTriangle className="h-6 w-6 text-red-600 dark:text-red-400" />
                    </div>
                    <Badge variant="red" className="font-bold tracking-wide">Ação Imediata</Badge>
                  </div>
                  <div className="mt-6 text-[12px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">Prazos Críticos (&lt; 7 dias)</div>
                  <div className="mt-2 text-4xl font-bold text-slate-900 dark:text-white">{data.operacional.prazos_criticos_count}</div>
                  <p className="text-sm text-slate-500 mt-2">Clique para ver processos com urgência</p>
                </CardContent>
              </Card>
            </Link>

            <Link href="/processos?inativos=true" className="block focus:outline-none focus:ring-2 ring-blue-500">
              <Card className="border-slate-200 dark:border-slate-800 hover:border-amber-500/50 dark:hover:border-amber-500/50 transition-colors group cursor-pointer bg-white dark:bg-[#020617]">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div className="h-12 w-12 rounded-md bg-amber-50 dark:bg-amber-500/10 flex items-center justify-center border border-amber-100 dark:border-amber-500/20 group-hover:scale-110 transition-transform">
                      <Clock className="h-6 w-6 text-amber-600 dark:text-amber-400" />
                    </div>
                    <Badge variant="amber" className="font-bold tracking-wide">Atenção</Badge>
                  </div>
                  <div className="mt-6 text-[12px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">Processos Inativos (&gt; 30 dias)</div>
                  <div className="mt-2 text-4xl font-bold text-slate-900 dark:text-white">{data.operacional.processos_inativos_count}</div>
                  <p className="text-sm text-slate-500 mt-2">Processos parados requerem movimentação</p>
                </CardContent>
              </Card>
            </Link>
          </div>

          <Card className="border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617]">
            <CardHeader className="border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
              <CardTitle className="flex items-center gap-2 text-lg">
                <Activity className="h-5 w-5 text-blue-600" />
                Backlog por Responsável
              </CardTitle>
            </CardHeader>
            <CardContent className="p-6">
              {data.operacional.backlog_por_responsavel.length === 0 ? (
                <div className="text-center py-8 text-slate-500">Nenhum dado de backlog disponível.</div>
              ) : (
                <div className="space-y-6">
                  {data.operacional.backlog_por_responsavel.map((item) => {
                    const max = Math.max(...data.operacional.backlog_por_responsavel.map(i => i.count));
                    const width = Math.max(5, Math.round((item.count / max) * 100));
                    return (
                      <div key={item.responsavel_id} className="group">
                        <div className="flex justify-between items-end mb-2">
                          <span className="font-bold text-slate-700 dark:text-slate-300 group-hover:text-blue-600 transition-colors">{item.responsavel_nome}</span>
                          <span className="text-sm font-bold text-slate-500 dark:text-slate-400">{item.count} processos</span>
                        </div>
                        <div className="h-4 w-full bg-slate-100 dark:bg-slate-800">
                          <div 
                            className="h-full bg-blue-600 dark:bg-blue-500 transition-all duration-1000 ease-out"
                            style={{ width: `${width}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {activeTab === "executivo" && (
        <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="grid gap-4 md:grid-cols-2">
            <Card className="border-slate-200 dark:border-slate-800 bg-gradient-to-br from-indigo-50 to-white dark:from-indigo-950/30 dark:to-[#020617]">
              <CardContent className="p-6">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-[12px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">Conformidade Documental</div>
                    <div className="mt-4 flex items-baseline gap-2">
                      <span className="text-5xl font-extrabold text-slate-900 dark:text-white">{data.executivo.conformidade_documental}%</span>
                    </div>
                  </div>
                  <div className="h-12 w-12 rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center">
                    <FileText className="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
                  </div>
                </div>
                <div className="mt-6">
                  <div className="h-2 w-full bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
                    <div 
                      className={`h-full ${data.executivo.conformidade_documental >= 80 ? 'bg-green-500' : data.executivo.conformidade_documental >= 50 ? 'bg-amber-500' : 'bg-red-500'}`} 
                      style={{ width: `${data.executivo.conformidade_documental}%` }} 
                    />
                  </div>
                  <p className="text-xs text-slate-500 mt-2 font-medium">Processos ativos com Peça Processual</p>
                </div>
              </CardContent>
            </Card>

            <Card className="border-slate-200 dark:border-slate-800 bg-gradient-to-br from-emerald-50 to-white dark:from-emerald-950/30 dark:to-[#020617]">
              <CardContent className="p-6">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="text-[12px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">Tempo Médio Resolução</div>
                    <div className="mt-4 flex items-baseline gap-2">
                      <span className="text-5xl font-extrabold text-slate-900 dark:text-white">{data.executivo.tempo_medio_resolucao_dias}</span>
                      <span className="text-slate-500 font-bold">dias</span>
                    </div>
                  </div>
                  <div className="h-12 w-12 rounded-full bg-emerald-100 dark:bg-emerald-900/50 flex items-center justify-center">
                    <CheckCircle2 className="h-6 w-6 text-emerald-600 dark:text-emerald-400" />
                  </div>
                </div>
                <p className="text-xs text-slate-500 mt-8 font-medium">Ciclo de vida dos processos encerrados</p>
              </CardContent>
            </Card>
          </div>

          <Card className="border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617]">
            <CardHeader className="border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
              <CardTitle className="flex items-center gap-2 text-lg">
                <Briefcase className="h-5 w-5 text-indigo-600" />
                Exposição por Carteira (Área Jurídica)
              </CardTitle>
            </CardHeader>
            <CardContent className="p-6">
              {data.executivo.exposicao_por_carteira.length === 0 ? (
                <div className="text-center py-8 text-slate-500">Nenhum dado disponível.</div>
              ) : (
                <div className="space-y-4">
                  {data.executivo.exposicao_por_carteira.map((item, idx) => {
                    const colors = [
                      "bg-blue-600",
                      "bg-indigo-500",
                      "bg-violet-500",
                      "bg-fuchsia-500",
                      "bg-rose-500",
                    ];
                    const colorClass = colors[idx % colors.length];
                    return (
                      <div key={item.area_juridica} className="flex items-center gap-4 group">
                        <div className="w-1/3 text-sm font-bold text-slate-700 dark:text-slate-300 truncate">
                          {item.area_juridica}
                        </div>
                        <div className="flex-1">
                          <div className="h-8 w-full bg-slate-100 dark:bg-slate-800 flex items-center">
                            <div 
                              className={`h-full ${colorClass} flex items-center px-3 transition-all duration-1000 ease-out`}
                              style={{ width: `${Math.max(5, item.percentage)}%` }}
                            >
                              <span className="text-[10px] font-bold text-white mix-blend-difference">{item.percentage}%</span>
                            </div>
                          </div>
                        </div>
                        <div className="w-16 text-right text-sm text-slate-500 font-medium">
                          {item.count} proc.
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
