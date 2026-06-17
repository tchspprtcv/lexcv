import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ClienteNota, ClienteNotaCreateRequest } from "@/types/clientes-notas";

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

  const notas = mockDb.clientes_notas
    .filter((n) => n.tenant_id === ctx.tenant_id && n.cliente_id === id)
    .slice()
    .sort((a, b) => (b.updated_at ?? b.created_at).localeCompare(a.updated_at ?? a.created_at));

  return NextResponse.json(notas as ClienteNota[]);
}

export async function POST(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;
  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ClienteNotaCreateRequest | null;
  if (!body?.conteudo?.trim()) {
    return NextResponse.json({ message: "O conteúdo é obrigatório" }, { status: 400 });
  }

  const now = new Date().toISOString();
  const nota: ClienteNota = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    cliente_id: id,
    titulo: body.titulo?.trim() || undefined,
    conteudo: body.conteudo.trim(),
    created_at: now,
    updated_at: now,
  };

  mockDb.clientes_notas.push(nota);

  return NextResponse.json(nota, { status: 201 });
}

