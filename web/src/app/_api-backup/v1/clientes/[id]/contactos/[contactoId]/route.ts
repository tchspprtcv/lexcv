import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ClienteContacto, ClienteContactoUpdateRequest } from "@/types/clientes-contactos";

type HandlerContext = {
  params: Promise<{ id: string; contactoId: string }>;
};

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id, contactoId } = await params;
  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const current = mockDb.clientes_contactos.find(
    (c) => c.id === contactoId && c.tenant_id === ctx.tenant_id && c.cliente_id === id,
  );
  if (!current) {
    return NextResponse.json({ message: "Contacto não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ClienteContactoUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Payload inválido" }, { status: 400 });
  }

  const updated: ClienteContacto = {
    ...current,
    tipo: body.tipo !== undefined ? body.tipo?.trim() || undefined : current.tipo,
    valor: body.valor !== undefined ? body.valor?.trim() || "" : current.valor,
  };

  if (!updated.valor.trim()) {
    return NextResponse.json({ message: "O valor é obrigatório" }, { status: 400 });
  }

  const idx = mockDb.clientes_contactos.findIndex((c) => c.id === contactoId);
  mockDb.clientes_contactos[idx] = updated;

  return NextResponse.json(updated);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id, contactoId } = await params;
  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const idx = mockDb.clientes_contactos.findIndex(
    (c) => c.id === contactoId && c.tenant_id === ctx.tenant_id && c.cliente_id === id,
  );
  if (idx === -1) {
    return NextResponse.json({ message: "Contacto não encontrado" }, { status: 404 });
  }

  mockDb.clientes_contactos.splice(idx, 1);
  return NextResponse.json({ ok: true });
}

