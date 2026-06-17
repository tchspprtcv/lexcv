import { NextResponse } from "next/server";
import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ProcessosDashboardData, DashboardBacklogItem, ExposicaoCarteiraItem } from "@/types/dashboard";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  // const { searchParams } = new URL(req.url);
  // (We could add filters like date range later)

  // Fetch all processes for the tenant
  const tenantProcessos = mockDb.processos.filter((p) => p.tenant_id === ctx.tenant_id);
  
  // 1. Operational Metrics
  
  // Backlog por Responsável (Using fake names or mapping if "responsavel_id" was added to real backend)
  const backlogMap = new Map<string, { id: string; nome: string; count: number }>();
  
  tenantProcessos.forEach((p) => {
    if (p.estado !== "ENCERRADO") {
      const id = ((p as unknown) as Record<string, string>).responsavel_id || "unassigned";
      const nome = ((p as unknown) as Record<string, string>).responsavel_nome || "Não atribuído";
      const existing = backlogMap.get(id);
      if (existing) {
        existing.count++;
      } else {
        backlogMap.set(id, { id, nome, count: 1 });
      }
    }
  });

  const backlog_por_responsavel: DashboardBacklogItem[] = Array.from(backlogMap.values()).map(b => ({
    responsavel_id: b.id,
    responsavel_nome: b.nome,
    count: b.count,
  })).sort((a, b) => b.count - a.count);

  // Processos Inativos (No movements in 30 days)
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
  
  let processos_inativos_count = 0;
  tenantProcessos.forEach(p => {
    if (p.estado === "ENCERRADO") return;
    
    // Find latest movimentacao for this process
    const movs = mockDb.movimentacoes.filter(m => m.processo_id === p.id && m.tenant_id === ctx.tenant_id);
    let lastActivityDate = new Date(p.created_at);
    if (movs.length > 0) {
      // Sort by date descending
      movs.sort((a, b) => new Date(b.data).getTime() - new Date(a.data).getTime());
      lastActivityDate = new Date(movs[0].data);
    }

    if (lastActivityDate < thirtyDaysAgo) {
      processos_inativos_count++;
    }
  });

  // Prazos Críticos (Expires in next 7 days and not completed)
  const today = new Date();
  const sevenDaysFromNow = new Date();
  sevenDaysFromNow.setDate(sevenDaysFromNow.getDate() + 7);
  
  let prazos_criticos_count = 0;
  mockDb.eventos.forEach(e => {
    if (e.tenant_id === ctx.tenant_id && !e.concluido) {
      const dueDate = new Date(e.data_fim);
      if (dueDate >= today && dueDate <= sevenDaysFromNow) {
        prazos_criticos_count++;
      }
    }
  });

  // 2. Executive Metrics

  // Exposicao por Carteira (Area Juridica)
  const carteiraMap = new Map<string, number>();
  let totalActive = 0;
  
  tenantProcessos.forEach(p => {
    if (p.estado !== "ENCERRADO") {
      totalActive++;
      const area = p.area_juridica || "Não classificada";
      carteiraMap.set(area, (carteiraMap.get(area) || 0) + 1);
    }
  });

  const exposicao_por_carteira: ExposicaoCarteiraItem[] = Array.from(carteiraMap.entries()).map(([area, count]) => ({
    area_juridica: area,
    count,
    percentage: totalActive > 0 ? Math.round((count / totalActive) * 100) : 0,
  })).sort((a, b) => b.count - a.count);

  // Conformidade Documental (Percentage of active processes with at least 1 document)
  let processesWithDocs = 0;
  tenantProcessos.forEach(p => {
    if (p.estado !== "ENCERRADO") {
      const hasDocs = mockDb.documentos.some(d => d.processo_id === p.id && d.tenant_id === ctx.tenant_id);
      if (hasDocs) {
        processesWithDocs++;
      }
    }
  });
  const conformidade_documental = totalActive > 0 ? Math.round((processesWithDocs / totalActive) * 100) : 0;

  // Tempo Medio de Resolucao
  let totalDays = 0;
  let closedCount = 0;
  tenantProcessos.forEach(p => {
    if (p.estado === "ENCERRADO" && p.updated_at) {
      const created = new Date(p.created_at);
      const updated = new Date(p.updated_at);
      const diffTime = Math.abs(updated.getTime() - created.getTime());
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
      totalDays += diffDays;
      closedCount++;
    }
  });
  
  const tempo_medio_resolucao_dias = closedCount > 0 ? Math.round(totalDays / closedCount) : 0;

  const data: ProcessosDashboardData = {
    operacional: {
      backlog_por_responsavel,
      prazos_criticos_count,
      processos_inativos_count,
    },
    executivo: {
      conformidade_documental,
      exposicao_por_carteira,
      tempo_medio_resolucao_dias,
    }
  };

  return NextResponse.json(data);
}
