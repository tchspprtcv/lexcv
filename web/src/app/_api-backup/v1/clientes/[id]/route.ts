import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Cliente, ClienteUpdateRequest } from "@/types/clientes";

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

  return NextResponse.json(cliente as Cliente);
}

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const idx = mockDb.clientes.findIndex((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ClienteUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }

  if (body.nome !== undefined && !body.nome.trim()) {
    return NextResponse.json({ message: "O nome não pode ser vazio" }, { status: 400 });
  }

  const current = mockDb.clientes[idx];
  const updated: Cliente = {
    ...current,
    tipo: body.tipo !== undefined ? body.tipo : current.tipo,
    nome: body.nome !== undefined ? body.nome.trim() : current.nome,
    nif: body.nif !== undefined ? body.nif?.trim() || undefined : current.nif,
    email: body.email !== undefined ? body.email?.trim() || undefined : current.email,
    telefone: body.telefone !== undefined ? body.telefone?.trim() || undefined : current.telefone,
    morada: body.morada !== undefined ? body.morada?.trim() || undefined : current.morada,
    localidade: body.localidade !== undefined ? body.localidade?.trim() || undefined : current.localidade,
    ativo: body.ativo !== undefined ? body.ativo : current.ativo,
  };

  mockDb.clientes[idx] = updated;

  return NextResponse.json(updated);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const idx = mockDb.clientes.findIndex((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  mockDb.clientes.splice(idx, 1);

  const ccIdx = mockDb.contas_correntes.findIndex((c) => c.cliente_id === id && c.tenant_id === ctx.tenant_id);
  if (ccIdx !== -1) {
    mockDb.contas_correntes.splice(ccIdx, 1);
  }

  return new NextResponse(null, { status: 204 });
}
