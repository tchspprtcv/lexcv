import { NextResponse } from "next/server";

import { allocateEventoId, mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Evento, EventoCreateRequest } from "@/types/eventos";

function parseBooleanParam(value: string | null) {
  if (!value) return null;
  const v = value.trim().toLowerCase();
  if (["true", "1", "sim", "yes"].includes(v)) return true;
  if (["false", "0", "nao", "não", "no"].includes(v)) return false;
  return null;
}

function parseDateMs(value: string | null) {
  if (!value) return null;
  const ms = new Date(value).getTime();
  if (Number.isNaN(ms)) return null;
  return ms;
}

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const dataInicio = parseDateMs(searchParams.get("dataInicio"));
  const dataFim = parseDateMs(searchParams.get("dataFim"));
  const processoId = searchParams.get("processoId")?.trim() || null;
  const concluidoParam = searchParams.get("concluido");
  const concluido = concluidoParam === null ? null : parseBooleanParam(concluidoParam);

  if (concluidoParam !== null && concluido === null) {
    return NextResponse.json({ message: "Parâmetro concluido inválido" }, { status: 400 });
  }

  if (searchParams.get("dataInicio") && dataInicio === null) {
    return NextResponse.json({ message: "Parâmetro dataInicio inválido" }, { status: 400 });
  }

  if (searchParams.get("dataFim") && dataFim === null) {
    return NextResponse.json({ message: "Parâmetro dataFim inválido" }, { status: 400 });
  }

  let eventos = mockDb.eventos.filter((e) => e.tenant_id === ctx.tenant_id);

  if (processoId) {
    eventos = eventos.filter((e) => e.processo_id === processoId);
  }

  if (concluido !== null) {
    eventos = eventos.filter((e) => e.concluido === concluido);
  }

  if (dataInicio !== null || dataFim !== null) {
    eventos = eventos.filter((e) => {
      const start = new Date(e.data_inicio).getTime();
      const end = new Date(e.data_fim || e.data_inicio).getTime();
      if (Number.isNaN(start) || Number.isNaN(end)) return false;
      if (dataInicio !== null && end < dataInicio) return false;
      if (dataFim !== null && start > dataFim) return false;
      return true;
    });
  }

  eventos = eventos
    .slice()
    .sort((a, b) => (a.data_inicio ?? "").localeCompare(b.data_inicio ?? ""));

  return NextResponse.json(eventos as Evento[]);
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = (await req.json().catch(() => null)) as EventoCreateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }

  if (!body.titulo?.trim()) {
    return NextResponse.json({ message: "O titulo é obrigatório" }, { status: 400 });
  }

  if (!body.data_inicio?.trim()) {
    return NextResponse.json({ message: "data_inicio é obrigatório" }, { status: 400 });
  }

  if (!body.data_fim?.trim()) {
    return NextResponse.json({ message: "data_fim é obrigatório" }, { status: 400 });
  }

  if (Number.isNaN(new Date(body.data_inicio).getTime())) {
    return NextResponse.json({ message: "data_inicio inválida" }, { status: 400 });
  }

  if (Number.isNaN(new Date(body.data_fim).getTime())) {
    return NextResponse.json({ message: "data_fim inválida" }, { status: 400 });
  }

  const processo_id = body.processo_id?.trim() || undefined;
  if (processo_id) {
    const processo = mockDb.processos.find((p) => p.id === processo_id && p.tenant_id === ctx.tenant_id);
    if (!processo) {
      return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
    }
  }

  const evento: Evento = {
    id: allocateEventoId(),
    tenant_id: ctx.tenant_id,
    processo_id,
    titulo: body.titulo.trim(),
    descricao: body.descricao?.trim() || undefined,
    data_inicio: body.data_inicio,
    data_fim: body.data_fim,
    prioridade: body.prioridade ?? "MEDIA",
    concluido: body.concluido ?? false,
  };

  mockDb.eventos.push(evento);

  return NextResponse.json(evento, { status: 201 });
}
