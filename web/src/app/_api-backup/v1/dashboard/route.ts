import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { DashboardKpis } from "@/types/dashboard";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const clientes = mockDb.clientes.filter((c) => c.tenant_id === ctx.tenant_id);
  const processos = mockDb.processos.filter((p) => p.tenant_id === ctx.tenant_id);
  const eventos = mockDb.eventos.filter((e) => e.tenant_id === ctx.tenant_id);
  const pagamentos = mockDb.pagamentos.filter((p) => p.tenant_id === ctx.tenant_id);

  const processos_ativos = processos.filter((p) => (p.estado ?? "").toUpperCase() !== "ENCERRADO").length;

  const now = new Date();
  const nowMs = now.getTime();
  const next7Ms = nowMs + 7 * 24 * 60 * 60 * 1000;
  const prazos_vencer = eventos.filter((e) => {
    if (e.concluido) return false;
    const start = new Date(e.data_inicio).getTime();
    return start >= nowMs && start <= next7Ms;
  }).length;

  const year = now.getUTCFullYear();
  const month = now.getUTCMonth();
  const valores_recebidos_mes = pagamentos.reduce((acc, p) => {
    const [y, m] = p.data_pagamento.split("-").map((x) => Number(x));
    if (y === year && m - 1 === month) return acc + p.valor_pago;
    return acc;
  }, 0);

  const res: DashboardKpis = {
    total_clientes: clientes.length,
    processos_ativos,
    prazos_vencer,
    valores_recebidos_mes,
  };

  return NextResponse.json(res);
}
