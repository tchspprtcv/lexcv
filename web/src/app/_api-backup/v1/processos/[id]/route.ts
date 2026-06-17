import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Processo, ProcessoUpdateRequest } from "@/types/processos";

type HandlerContext = {
  params: Promise<{ id: string }>;
};

export async function GET(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const processo = mockDb.processos.find((p) => p.id === id && p.tenant_id === ctx.tenant_id);
  if (!processo) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  return NextResponse.json(processo as Processo);
}

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const idx = mockDb.processos.findIndex((p) => p.id === id && p.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ProcessoUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }

  if (body.cliente_id !== undefined && !body.cliente_id.trim()) {
    return NextResponse.json({ message: "cliente_id não pode ser vazio" }, { status: 400 });
  }
  if (body.numero !== undefined && !body.numero.trim()) {
    return NextResponse.json({ message: "numero não pode ser vazio" }, { status: 400 });
  }
  if (body.titulo !== undefined && !body.titulo.trim()) {
    return NextResponse.json({ message: "titulo não pode ser vazio" }, { status: 400 });
  }
  if (body.estado !== undefined && !body.estado.trim()) {
    return NextResponse.json({ message: "estado não pode ser vazio" }, { status: 400 });
  }

  if (body.cliente_id !== undefined) {
    const cliente = mockDb.clientes.find((c) => c.id === body.cliente_id && c.tenant_id === ctx.tenant_id);
    if (!cliente) {
      return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
    }
  }

  const current = mockDb.processos[idx];
  const now = new Date().toISOString();

  const updated: Processo = {
    ...current,
    cliente_id: body.cliente_id !== undefined ? body.cliente_id : current.cliente_id,
    numero: body.numero !== undefined ? body.numero.trim() : current.numero,
    titulo: body.titulo !== undefined ? body.titulo.trim() : current.titulo,
    descricao: body.descricao !== undefined ? body.descricao?.trim() || undefined : current.descricao,
    estado: body.estado !== undefined ? body.estado.trim() : current.estado,
    updated_at: now,
  };

  mockDb.processos[idx] = updated;

  return NextResponse.json(updated);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const idx = mockDb.processos.findIndex((p) => p.id === id && p.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  mockDb.processos.splice(idx, 1);

  for (let i = mockDb.partes.length - 1; i >= 0; i -= 1) {
    const item = mockDb.partes[i];
    if (item.tenant_id === ctx.tenant_id && item.processo_id === id) mockDb.partes.splice(i, 1);
  }

  for (let i = mockDb.processo_fases.length - 1; i >= 0; i -= 1) {
    const item = mockDb.processo_fases[i];
    if (item.tenant_id === ctx.tenant_id && item.processo_id === id) mockDb.processo_fases.splice(i, 1);
  }

  for (let i = mockDb.movimentacoes.length - 1; i >= 0; i -= 1) {
    const item = mockDb.movimentacoes[i];
    if (item.tenant_id === ctx.tenant_id && item.processo_id === id) mockDb.movimentacoes.splice(i, 1);
  }

  return new NextResponse(null, { status: 204 });
}

