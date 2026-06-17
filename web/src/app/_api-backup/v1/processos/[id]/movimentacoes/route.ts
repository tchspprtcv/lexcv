import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ProcessoMovimentacao, ProcessoMovimentacaoCreateRequest } from "@/types/processos";

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

  const itens = mockDb.movimentacoes
    .filter((m) => m.tenant_id === ctx.tenant_id && m.processo_id === id)
    .slice()
    .sort((a, b) => (b.data ?? "").localeCompare(a.data ?? ""));

  return NextResponse.json(itens as ProcessoMovimentacao[]);
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

  const body = (await req.json().catch(() => null)) as ProcessoMovimentacaoCreateRequest | null;
  if (!body?.titulo?.trim()) {
    return NextResponse.json({ message: "O título é obrigatório" }, { status: 400 });
  }

  const now = new Date().toISOString();
  const data = body.data?.trim() || now;
  if (Number.isNaN(new Date(data).getTime())) {
    return NextResponse.json({ message: "data inválida" }, { status: 400 });
  }

  const item: ProcessoMovimentacao = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    processo_id: id,
    titulo: body.titulo.trim(),
    descricao: body.descricao?.trim() || undefined,
    data,
    created_at: now,
  };

  mockDb.movimentacoes.push(item);

  return NextResponse.json(item, { status: 201 });
}

