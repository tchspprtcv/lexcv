import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Processo, ProcessoCreateRequest } from "@/types/processos";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const q = searchParams.get("q")?.trim().toLowerCase();
  const estado = searchParams.get("estado")?.trim().toLowerCase();
  const tribunal = searchParams.get("tribunal")?.trim().toLowerCase();
  const area = searchParams.get("area_juridica")?.trim().toLowerCase();
  const clienteId = searchParams.get("cliente_id")?.trim();
  const sortBy = searchParams.get("sortBy")?.trim() || "created_at";
  const sortDir = (searchParams.get("sortDir")?.trim() || "desc").toLowerCase();

  const processos = mockDb.processos
    .filter((p) => p.tenant_id === ctx.tenant_id)
    .filter((p) => {
      if (!q) return true;
      const clienteNome = mockDb.clientes.find((c) => c.id === p.cliente_id)?.nome ?? "";
      const haystack = [
        p.numero ?? "",
        p.titulo ?? "",
        p.tipo_processo ?? "",
        p.descricao ?? "",
        p.tribunal ?? "",
        p.area_juridica ?? "",
        p.estado ?? "",
        clienteNome,
      ]
        .join(" ")
        .toLowerCase();
      return haystack.includes(q);
    })
    .filter((p) => {
      if (!estado) return true;
      return (p.estado ?? "").toLowerCase() === estado;
    })
    .filter((p) => {
      if (!tribunal) return true;
      return (p.tribunal ?? "").toLowerCase().includes(tribunal);
    })
    .filter((p) => {
      if (!area) return true;
      return (p.area_juridica ?? "").toLowerCase().includes(area);
    })
    .filter((p) => {
      if (!clienteId) return true;
      return p.cliente_id === clienteId;
    })
    .slice()
    .sort((a, b) => {
      const dir = sortDir === "asc" ? 1 : -1;
      if (sortBy === "numero") return dir * (a.numero ?? "").localeCompare(b.numero ?? "");
      if (sortBy === "estado") return dir * (a.estado ?? "").localeCompare(b.estado ?? "");
      if (sortBy === "created_at") return dir * (a.created_at ?? "").localeCompare(b.created_at ?? "");
      return dir * (b.created_at ?? "").localeCompare(a.created_at ?? "");
    });

  return NextResponse.json(processos as Processo[]);
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = (await req.json().catch(() => null)) as ProcessoCreateRequest | null;
  if (!body?.cliente_id?.trim()) {
    return NextResponse.json({ message: "cliente_id é obrigatório" }, { status: 400 });
  }

  const cliente = mockDb.clientes.find((c) => c.id === body.cliente_id && c.tenant_id === ctx.tenant_id);
  if (!cliente) {
    return NextResponse.json({ message: "Cliente não encontrado" }, { status: 404 });
  }

  const now = new Date().toISOString();
  const id = crypto.randomUUID();

  const processo: Processo = {
    id,
    tenant_id: ctx.tenant_id,
    cliente_id: body.cliente_id,
    numero: body.numero?.trim() || undefined,
    titulo: body.titulo?.trim() || undefined,
    descricao: body.descricao?.trim() || undefined,
    estado: body.estado?.trim() || "ATIVO",
    created_at: now,
    updated_at: now,
  };

  mockDb.processos.push(processo);

  return NextResponse.json(processo, { status: 201 });
}

