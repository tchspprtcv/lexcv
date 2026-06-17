import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ClienteContacto, ClienteContactoCreateRequest } from "@/types/clientes-contactos";

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

  const contactos = mockDb.clientes_contactos
    .filter((c) => c.tenant_id === ctx.tenant_id && c.cliente_id === id)
    .slice()
    .sort((a, b) => (a.created_at ?? "").localeCompare(b.created_at ?? ""));

  return NextResponse.json(contactos as ClienteContacto[]);
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

  const body = (await req.json().catch(() => null)) as ClienteContactoCreateRequest | null;
  if (!body?.valor?.trim()) {
    return NextResponse.json({ message: "O valor é obrigatório" }, { status: 400 });
  }

  const now = new Date().toISOString();
  const contacto: ClienteContacto = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    cliente_id: id,
    tipo: body.tipo?.trim() || undefined,
    valor: body.valor.trim(),
    created_at: now,
  };

  mockDb.clientes_contactos.push(contacto);

  return NextResponse.json(contacto, { status: 201 });
}

