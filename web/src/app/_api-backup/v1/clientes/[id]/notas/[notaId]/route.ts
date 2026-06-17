import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ClienteNota, ClienteNotaUpdateRequest } from "@/types/clientes-notas";

type HandlerContext = {
  params: Promise<{ id: string; notaId: string }>;
};

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id, notaId } = await params;
  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const current = mockDb.clientes_notas.find(
    (n) => n.id === notaId && n.tenant_id === ctx.tenant_id && n.cliente_id === id,
  );
  if (!current) {
    return NextResponse.json({ message: "Nota não encontrada" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ClienteNotaUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Payload inválido" }, { status: 400 });
  }

  const updated: ClienteNota = {
    ...current,
    titulo: body.titulo !== undefined ? body.titulo?.trim() || undefined : current.titulo,
    conteudo: body.conteudo !== undefined ? body.conteudo?.trim() || "" : current.conteudo,
    updated_at: new Date().toISOString(),
  };

  if (!updated.conteudo.trim()) {
    return NextResponse.json({ message: "O conteúdo é obrigatório" }, { status: 400 });
  }

  const idx = mockDb.clientes_notas.findIndex((n) => n.id === notaId);
  mockDb.clientes_notas[idx] = updated;

  return NextResponse.json(updated);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id, notaId } = await params;
  const cliente = mockDb.clientes.find((c) => c.id === id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const idx = mockDb.clientes_notas.findIndex(
    (n) => n.id === notaId && n.tenant_id === ctx.tenant_id && n.cliente_id === id,
  );
  if (idx === -1) {
    return NextResponse.json({ message: "Nota não encontrada" }, { status: 404 });
  }

  mockDb.clientes_notas.splice(idx, 1);
  return NextResponse.json({ ok: true });
}

