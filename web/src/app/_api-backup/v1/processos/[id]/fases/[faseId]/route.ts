import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { ProcessoFase, ProcessoFaseUpdateRequest } from "@/types/processos";

type HandlerContext = {
  params: Promise<{ id: string; faseId: string }>;
};

export async function PUT(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id, faseId } = await params;

  const processo = mockDb.processos.find((p) => p.id === id && p.tenant_id === ctx.tenant_id);
  if (!processo) {
    return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
  }

  const idx = mockDb.processo_fases.findIndex(
    (pf) => pf.id === faseId && pf.processo_id === id && pf.tenant_id === ctx.tenant_id,
  );
  if (idx === -1) {
    return NextResponse.json({ message: "Fase do processo não encontrada" }, { status: 404 });
  }

  const body = (await req.json().catch(() => null)) as ProcessoFaseUpdateRequest | null;
  if (!body) {
    return NextResponse.json({ message: "Body inválido" }, { status: 400 });
  }
  if (body.status !== undefined && !body.status) {
    return NextResponse.json({ message: "status não pode ser vazio" }, { status: 400 });
  }

  const current = mockDb.processo_fases[idx];
  const now = new Date().toISOString();

  const updated: ProcessoFase = {
    ...current,
    status: body.status !== undefined ? body.status : current.status,
    updated_at: now,
    fase: mockDb.fases_catalog.find((f) => f.id === current.fase_id && f.tenant_id === ctx.tenant_id),
  };

  mockDb.processo_fases[idx] = {
    id: updated.id,
    tenant_id: updated.tenant_id,
    processo_id: updated.processo_id,
    fase_id: updated.fase_id,
    status: updated.status,
    created_at: updated.created_at,
    updated_at: updated.updated_at,
  };

  return NextResponse.json(updated);
}

