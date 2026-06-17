import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";

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

  const blob = mockDb.documentos_blobs.get(id);
  if (!blob) {
    return NextResponse.json({ message: "Arquivo não encontrado" }, { status: 404 });
  }

  const headers = new Headers();
  const contentType = blob.content_type || documento.content_type || "application/octet-stream";
  headers.set("content-type", contentType);
  headers.set("content-disposition", `attachment; filename="${blob.filename || documento.filename}"`);
  headers.set("content-length", String(blob.bytes.byteLength));

  return new NextResponse(blob.bytes, { status: 200, headers });
}
