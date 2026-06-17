import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ProcessoParte, ProcessoParteCreateRequest } from "@/types/processos";

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

  const partes = mockDb.partes
    .filter((p) => p.tenant_id === ctx.tenant_id && p.processo_id === id)
    .slice()
    .sort((a, b) => (a.created_at ?? "").localeCompare(b.created_at ?? ""));

  return NextResponse.json(partes as ProcessoParte[]);
}

export async function POST(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const processo = mockDb.processos.find((p) => p.id === id && p.tenant_id === ctx.tenant_id);
  if (!processo) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ProcessoParteCreateRequest | null;
  if (!body?.nome?.trim()) {
    return NextResponse.json({ message: "O nome é obrigatório" }, { status: 400 });
  }

  const now = new Date().toISOString();
  const parte: ProcessoParte = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    processo_id: id,
    tipo: body.tipo?.trim() || undefined,
    nome: body.nome.trim(),
    nif: body.nif?.trim() || undefined,
    created_at: now,
  };

  mockDb.partes.push(parte);

  return NextResponse.json(parte, { status: 201 });
}

