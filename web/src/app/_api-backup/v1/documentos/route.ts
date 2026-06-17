import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Documento } from "@/types/documentos";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const processo_id = searchParams.get("processo_id")?.trim() || undefined;
  const cliente_id = searchParams.get("cliente_id")?.trim() || undefined;

  let documentos = mockDb.documentos.filter((d) => d.tenant_id === ctx.tenant_id);

  if (processo_id) {
    documentos = documentos.filter((d) => d.processo_id === processo_id);
  }

  if (cliente_id) {
    documentos = documentos.filter((d) => d.cliente_id === cliente_id);
  }

  return NextResponse.json(documentos as Documento[]);
}

