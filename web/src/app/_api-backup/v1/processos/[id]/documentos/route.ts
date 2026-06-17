import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Documento } from "@/types/documentos";

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

  const documentos = mockDb.documentos.filter((d) => d.tenant_id === ctx.tenant_id && d.processo_id === id);
  return NextResponse.json(documentos as Documento[]);
}

