import { NextResponse } from "next/server";

import { allocateHonorarioId, mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Honorario, HonorarioCreateRequest } from "@/types/financeiro";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const processo_id = searchParams.get("processo_id")?.trim();

  let honorarios = mockDb.honorarios.filter((h) => h.tenant_id === ctx.tenant_id);
  if (processo_id) {
    honorarios = honorarios.filter((h) => h.processo_id === processo_id);
  }

  honorarios = honorarios.slice().sort((a, b) => (b.created_at ?? "").localeCompare(a.created_at ?? ""));

  return NextResponse.json(honorarios as Honorario[]);
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = (await req.json().catch(() => null)) as HonorarioCreateRequest | null;
  if (!body?.processo_id?.trim()) {
    return NextResponse.json({ message: "processo_id é obrigatório" }, { status: 400 });
  }
  if (typeof body.valor_total !== "number" || !Number.isFinite(body.valor_total) || body.valor_total <= 0) {
    return NextResponse.json({ message: "valor_total é obrigatório e deve ser > 0" }, { status: 400 });
  }

  const processo = mockDb.processos.find((p) => p.id === body.processo_id && p.tenant_id === ctx.tenant_id);
  if (!processo) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  const now = new Date().toISOString();

  const honorario: Honorario = {
    id: allocateHonorarioId(),
    tenant_id: ctx.tenant_id,
    processo_id: body.processo_id,
    valor_total: body.valor_total,
    descricao: body.descricao?.trim() || undefined,
    data_acordo: body.data_acordo?.trim() || undefined,
    created_at: now,
  };

  mockDb.honorarios.push(honorario);

  return NextResponse.json(honorario, { status: 201 });
}

