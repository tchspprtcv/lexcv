import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ClienteContaCorrenteResponse } from "@/types/clientes";

type HandlerContext = {
  params: Promise<{ id: string }>;
};

export async function GET(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  let conta = mockDb.contas_correntes.find((c) => c.cliente_id === id && c.tenant_id === ctx.tenant_id);
  if (!conta) {
    const now = new Date().toISOString();
    conta = { tenant_id: ctx.tenant_id, cliente_id: id, saldo: 0, updated_at: now };
    mockDb.contas_correntes.push(conta);
  }

  const res: ClienteContaCorrenteResponse = {
    cliente_id: conta.cliente_id,
    saldo: conta.saldo,
    updated_at: conta.updated_at,
  };

  return NextResponse.json(res);
}
