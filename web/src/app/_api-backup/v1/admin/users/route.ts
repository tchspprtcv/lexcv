import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Não autorizado" }, { status: 401 });
  }

  if (!ctx.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "Acesso negado. Apenas administradores podem aceder." },
      { status: 403 }
    );
  }

  // Filter users by current tenant
  const tenantUsers = mockDb.users.filter((u) => u.tenant_id === ctx.tenant_id);

  return NextResponse.json(tenantUsers);
}

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Não autorizado" }, { status: 401 });
  }

  if (!ctx.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "Acesso negado. Apenas administradores podem aceder." },
      { status: 403 }
    );
  }

  const body = await req.json().catch(() => null);
  if (!body || !body.nome || !body.email || !body.password || !body.roles) {
    return NextResponse.json(
      { message: "Nome, email, password e funções (roles) são obrigatórios." },
      { status: 400 }
    );
  }

  // Check if email already exists
  const emailExists = mockDb.users.some(
    (u) => u.email.toLowerCase() === body.email.toLowerCase()
  );
  if (emailExists) {
    return NextResponse.json(
      { message: "Já existe um utilizador registado com este endereço de email." },
      { status: 400 }
    );
  }

  const newUser = {
    id: crypto.randomUUID(),
    tenant_id: ctx.tenant_id,
    nome: body.nome,
    email: body.email,
    password: body.password,
    roles: body.roles,
    telefone: body.telefone || "",
    avatar_url: body.avatar_url || "",
    ativo: body.ativo !== false, // default to true
    permissions: body.permissions || [],
  };

  mockDb.users.push(newUser);

  return NextResponse.json(newUser, { status: 201 });
}
