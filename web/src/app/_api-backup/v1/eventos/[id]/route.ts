import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Evento, EventoUpdateRequest } from "@/types/eventos";

type HandlerContext = {
  params: Promise<{ id: string }>;
};

function parseEventoId(id: string) {
  const n = Number(id);
  if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) return null;
  return n;
}

export async function GET(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;
  const eventoId = parseEventoId(id);
  if (!eventoId) {
    return NextResponse.json({ message: "Id inválido" }, { status: 400 });
  }

  const evento = mockDb.eventos.find((e) => e.id === eventoId && e.tenant_id === ctx.tenant_id);
  if (!evento) {
    return NextResponse.json({ message: "Evento não encontrado" }, { status: 404 });
  }

  return NextResponse.json(evento as Evento);
}

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;
  const eventoId = parseEventoId(id);
  if (!eventoId) {
    return NextResponse.json({ message: "Id inválido" }, { status: 400 });
  }

  const idx = mockDb.eventos.findIndex((e) => e.id === eventoId && e.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Evento não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as EventoUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }

  if (body.titulo !== undefined && !body.titulo.trim()) {
    return NextResponse.json({ message: "O titulo não pode ser vazio" }, { status: 400 });
  }

  if (body.data_inicio !== undefined && Number.isNaN(new Date(body.data_inicio).getTime())) {
    return NextResponse.json({ message: "data_inicio inválida" }, { status: 400 });
  }

  if (body.data_fim !== undefined && Number.isNaN(new Date(body.data_fim).getTime())) {
    return NextResponse.json({ message: "data_fim inválida" }, { status: 400 });
  }

  const processo_id_raw = body.processo_id;
  const processo_id = processo_id_raw === null ? undefined : processo_id_raw?.trim() || undefined;
  if (processo_id_raw !== undefined && processo_id) {
    const processo = mockDb.processos.find((p) => p.id === processo_id && p.tenant_id === ctx.tenant_id);
    if (!processo) {
      return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
    }
  }

  const descricao_raw = body.descricao;
  const descricao = descricao_raw === null ? undefined : descricao_raw?.trim() || undefined;

  const current = mockDb.eventos[idx];
  const updated: Evento = {
    ...current,
    processo_id: body.processo_id !== undefined ? processo_id : current.processo_id,
    titulo: body.titulo !== undefined ? body.titulo.trim() : current.titulo,
    descricao: body.descricao !== undefined ? descricao : current.descricao,
    data_inicio: body.data_inicio !== undefined ? body.data_inicio : current.data_inicio,
    data_fim: body.data_fim !== undefined ? body.data_fim : current.data_fim,
    prioridade: body.prioridade !== undefined ? body.prioridade : current.prioridade,
    concluido: body.concluido !== undefined ? body.concluido : current.concluido,
  };

  mockDb.eventos[idx] = updated;

  return NextResponse.json(updated);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;
  const eventoId = parseEventoId(id);
  if (!eventoId) {
    return NextResponse.json({ message: "Id inválido" }, { status: 400 });
  }

  const idx = mockDb.eventos.findIndex((e) => e.id === eventoId && e.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Evento não encontrado" }, { status: 404 });
  }

  mockDb.eventos.splice(idx, 1);

  return new NextResponse(null, { status: 204 });
}
