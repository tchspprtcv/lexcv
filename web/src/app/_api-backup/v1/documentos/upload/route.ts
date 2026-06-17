import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Documento, DocumentoUploadResponse } from "@/types/documentos";

function asNonEmptyString(value: FormDataEntryValue | null): string | undefined {
  if (!value) return undefined;
  if (typeof value !== "string") return undefined;
  const v = value.trim();
  return v ? v : undefined;
}

function inferContentType(file: File): string {
  if (file.type?.trim()) return file.type;

  const name = file.name.toLowerCase();
  if (name.endsWith(".pdf")) return "application/pdf";
  if (name.endsWith(".png")) return "image/png";
  if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
  if (name.endsWith(".txt")) return "text/plain; charset=utf-8";

  return "application/octet-stream";
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const form = await req.formData().catch(() => null);
  if (!form) {
    return NextResponse.json({ message: "multipart/form-data inválido" }, { status: 400 });
  }

  const fileEntry = form.get("file");
  if (!fileEntry || typeof fileEntry === "string") {
    return NextResponse.json({ message: "O campo 'file' é obrigatório" }, { status: 400 });
  }

  const processo_id = asNonEmptyString(form.get("processo_id"));
  const cliente_id = asNonEmptyString(form.get("cliente_id"));
  const tipo = asNonEmptyString(form.get("tipo"));
  const nome = asNonEmptyString(form.get("nome"));

  let resolvedClienteId = cliente_id;

  if (processo_id) {
    const processo = mockDb.processos.find((p) => p.id === processo_id && p.tenant_id === ctx.tenant_id);
    if (!processo) {
      return NextResponse.json({ message: "Processo não encontrado" }, { status: 404 });
    }

    resolvedClienteId = resolvedClienteId ?? processo.cliente_id;

    if (cliente_id && processo.cliente_id !== cliente_id) {
      return NextResponse.json(
        { message: "cliente_id não corresponde ao cliente do processo informado" },
        { status: 400 },
      );
    }
  }

  if (resolvedClienteId) {
    const cliente = mockDb.clientes.find((c) => c.id === resolvedClienteId && c.tenant_id === ctx.tenant_id);
    if (!cliente) {
      return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
    }
  }

  const buffer = await fileEntry.arrayBuffer();
  const content_type = inferContentType(fileEntry);
  const filename = fileEntry.name || "upload.bin";
  const id = crypto.randomUUID();
  const now = new Date().toISOString();

  const documento: Documento = {
    id,
    tenant_id: ctx.tenant_id,
    processo_id,
    cliente_id: resolvedClienteId,
    tipo,
    nome: nome ?? filename,
    filename,
    content_type,
    size: buffer.byteLength,
    versao: 1,
    created_at: now,
  };

  mockDb.documentos.push(documento);
  mockDb.documentos_blobs.set(id, { bytes: buffer, content_type: documento.content_type, filename });

  return NextResponse.json(documento as DocumentoUploadResponse, { status: 201 });
}
