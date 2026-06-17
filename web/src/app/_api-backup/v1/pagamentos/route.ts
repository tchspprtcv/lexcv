import { NextResponse } from "next/server";

import { allocatePagamentoId, mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Pagamento, PagamentoCreateRequest } from "@/types/financeiro";

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = (await req.json().catch(() => null)) as PagamentoCreateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }
  if (typeof body.honorario_id !== "number" || !Number.isFinite(body.honorario_id)) {
    return NextResponse.json({ message: "honorario_id é obrigatório" }, { status: 400 });
  }
  if (typeof body.valor_pago !== "number" || !Number.isFinite(body.valor_pago) || body.valor_pago <= 0) {
    return NextResponse.json({ message: "valor_pago é obrigatório e deve ser > 0" }, { status: 400 });
  }

  const honorario = mockDb.honorarios.find((h) => h.id === body.honorario_id && h.tenant_id === ctx.tenant_id);
  if (!honorario) {
    return NextResponse.json({ message: "Honorário não encontrado" }, { status: 404 });
  }

  const processo = mockDb.processos.find((p) => p.id === honorario.processo_id && p.tenant_id === ctx.tenant_id);
  if (!processo) {
    return NextResponse.json({ message: "Processo do honorário não encontrado" }, { status: 404 });
  }

  const now = new Date().toISOString();
  const today = now.slice(0, 10);
  const data_pagamento = body.data_pagamento?.trim() || today;

  const pagamento: Pagamento = {
    id: allocatePagamentoId(),
    tenant_id: ctx.tenant_id,
    honorario_id: honorario.id,
    valor_pago: body.valor_pago,
    data_pagamento,
    metodo: body.metodo?.trim() || undefined,
  };

  mockDb.pagamentos.push(pagamento);

  let conta = mockDb.contas_correntes.find(
    (c) => c.tenant_id === ctx.tenant_id && c.cliente_id === processo.cliente_id,
  );
  if (!conta) {
    conta = { tenant_id: ctx.tenant_id, cliente_id: processo.cliente_id, saldo: 0, updated_at: now };
    mockDb.contas_correntes.push(conta);
  }

  conta.saldo += pagamento.valor_pago;
  conta.updated_at = now;

  return NextResponse.json(pagamento, { status: 201 });
}

