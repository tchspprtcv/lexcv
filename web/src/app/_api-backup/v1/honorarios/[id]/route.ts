import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Honorario } from "@/types/financeiro";

type HandlerContext = {
  params: Promise<{ id: string }>;
};

export async function GET(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;
  const honorarioId = Number(id);

  if (!Number.isFinite(honorarioId)) {
    return NextResponse.json({ message: "ID inválido" }, { status: 400 });
  }

  const honorario = mockDb.honorarios.find((h) => h.id === honorarioId && h.tenant_id === ctx.tenant_id);
  if (!honorario) {
    return NextResponse.json({ message: "Honorário não encontrado" }, { status: 404 });
  }

  return NextResponse.json(honorario as Honorario);
}

