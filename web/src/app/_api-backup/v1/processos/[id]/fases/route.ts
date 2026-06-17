import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ProcessoFase, ProcessoFaseCreateRequest } from "@/types/processos";

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

  const fases = mockDb.processo_fases
    .filter((pf) => pf.tenant_id === ctx.tenant_id && pf.processo_id === id)
    .map((pf) => ({
      ...pf,
      fase: mockDb.fases_catalog.find((f) => f.id === pf.fase_id && f.tenant_id === ctx.tenant_id),
    }))
    .slice()
    .sort((a, b) => (a.fase?.ordem ?? 9999) - (b.fase?.ordem ?? 9999));

  return NextResponse.json(fases as ProcessoFase[]);
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

  const body = (await req.json().catch(() => null)) as ProcessoFaseCreateRequest | null;
  if (!body?.fase_id?.trim()) {
    return NextResponse.json({ message: "fase_id é obrigatório" }, { status: 400 });
  }

  const faseCatalog = mockDb.fases_catalog.find((f) => f.id === body.fase_id && f.tenant_id === ctx.tenant_id);
  if (!faseCatalog) {
    return NextResponse.json({ message: "Fase não encontrada" }, { status: 404 });
  }

  const already = mockDb.processo_fases.find(
    (pf) => pf.tenant_id === ctx.tenant_id && pf.processo_id === id && pf.fase_id === body.fase_id,
  );
  if (already) {
    return NextResponse.json({ message: "Fase já associada ao processo" }, { status: 409 });
  }

  const now = new Date().toISOString();
  const item: ProcessoFase = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    processo_id: id,
    fase_id: body.fase_id,
    status: body.status ?? "PENDENTE",
    created_at: now,
    updated_at: now,
    fase: faseCatalog,
  };

  mockDb.processo_fases.push({
    id: item.id,
    tenant_id: item.tenant_id,
    processo_id: item.processo_id,
    fase_id: item.fase_id,
    status: item.status,
    created_at: item.created_at,
    updated_at: item.updated_at,
  });

  return NextResponse.json(item, { status: 201 });
}

