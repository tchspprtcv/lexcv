import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { Cliente, ClienteCreateRequest } from "@/types/clientes";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const query = searchParams.get("q")?.trim();
  const nome = searchParams.get("nome")?.trim();
  const nif = searchParams.get("nif")?.trim();
  const tipo = searchParams.get("tipo")?.trim();
  const ativoRaw = searchParams.get("ativo")?.trim();
  const ativo = ativoRaw === null ? undefined : ativoRaw === "true";
  const localidade = searchParams.get("localidade")?.trim();
  const createdFrom = searchParams.get("createdFrom")?.trim();
  const createdTo = searchParams.get("createdTo")?.trim();

  let clientes = mockDb.clientes.filter((c) => c.tenant_id === ctx.tenant_id);

  if (query) {
    const q = query.toLowerCase();
    clientes = clientes.filter((c) => {
      const nomeMatch = c.nome.toLowerCase().includes(q);
      const nifMatch = (c.nif ?? "").toLowerCase().includes(q);
      const emailMatch = (c.email ?? "").toLowerCase().includes(q);
      const telMatch = (c.telefone ?? "").toLowerCase().includes(q);
      return nomeMatch || nifMatch || emailMatch || telMatch;
    });
  }

  if (nome) {
    const q = nome.toLowerCase();
    clientes = clientes.filter((c) => c.nome.toLowerCase().includes(q));
  }

  if (nif) {
    const q = nif.toLowerCase();
    clientes = clientes.filter((c) => (c.nif ?? "").toLowerCase().includes(q));
  }

  if (tipo) {
    const t = tipo.toLowerCase();
    clientes = clientes.filter((c) => (c.tipo ?? "").toLowerCase() === t);
  }

  if (ativo !== undefined) {
    clientes = clientes.filter((c) => Boolean(c.ativo) === ativo);
  }

  if (localidade) {
    const q = localidade.toLowerCase();
    clientes = clientes.filter((c) => (c.localidade ?? "").toLowerCase().includes(q));
  }

  if (createdFrom) {
    const from = new Date(createdFrom);
    if (!Number.isNaN(from.getTime())) {
      clientes = clientes.filter((c) => new Date(c.created_at).getTime() >= from.getTime());
    }
  }

  if (createdTo) {
    const to = new Date(createdTo);
    if (!Number.isNaN(to.getTime())) {
      clientes = clientes.filter((c) => new Date(c.created_at).getTime() <= to.getTime());
    }
  }

  return NextResponse.json(clientes as Cliente[]);
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = (await req.json().catch(() => null)) as ClienteCreateRequest | null;
  if (!body?.nome?.trim()) {
    return NextResponse.json({ message: "O nome é obrigatório" }, { status: 400 });
  }

  const now = new Date().toISOString();
  const id = crypto.randomUUID();

  const cliente: Cliente = {
    id,
    tenant_id: ctx.tenant_id,
    tipo: body.tipo,
    nome: body.nome.trim(),
    nif: body.nif?.trim() || undefined,
    email: body.email?.trim() || undefined,
    telefone: body.telefone?.trim() || undefined,
    morada: body.morada?.trim() || undefined,
    localidade: body.localidade?.trim() || undefined,
    ativo: body.ativo ?? true,
    created_at: now,
  };

  mockDb.clientes.push(cliente);

  mockDb.contas_correntes.push({
    tenant_id: ctx.tenant_id,
    cliente_id: id,
    saldo: 0,
    updated_at: now,
  });

  return NextResponse.json(cliente, { status: 201 });
}
