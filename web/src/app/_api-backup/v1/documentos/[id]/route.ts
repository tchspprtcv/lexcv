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

  const documento = mockDb.documentos.find((d) => d.id === id && d.tenant_id === ctx.tenant_id);
  if (!documento) {
    return NextResponse.json({ message: "Documento não encontrado" }, { status: 404 });
  }

  return NextResponse.json(documento as Documento);
}

export async function DELETE(req: Request, { params }: HandlerContext) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { id } = await params;

  const idx = mockDb.documentos.findIndex((d) => d.id === id && d.tenant_id === ctx.tenant_id);
  if (idx === -1) {
    return NextResponse.json({ message: "Documento não encontrado" }, { status: 404 });
  }

  mockDb.documentos.splice(idx, 1);
  mockDb.documentos_blobs.delete(id);

  return new NextResponse(null, { status: 204 });
}

