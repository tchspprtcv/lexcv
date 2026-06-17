import { NextResponse } from "next/server";

import { mockDb, getUserPermissions } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";
import type { MeResponse } from "@/types/auth";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const user = mockDb.users.find((u) => u.id === ctx.user_id && u.tenant_id === ctx.tenant_id);
  if (!user) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const res: MeResponse = {
    id: user.id,
    tenant_id: user.tenant_id,
    nome: user.nome,
    email: user.email,
    roles: user.roles,
    avatar_url: user.avatar_url,
    telefone: user.telefone,
    ativo: user.ativo,
    permissions: getUserPermissions(user),
  };

  return NextResponse.json(res);
}

export async function PUT(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const userIndex = mockDb.users.findIndex((u) => u.id === ctx.user_id && u.tenant_id === ctx.tenant_id);
  if (userIndex === -1) {
    return NextResponse.json({ message: "Unauthorized" }, { status: 401 });
  }

  const body = await req.json();
  
  // Update fields
  const user = mockDb.users[userIndex];
  if (body.nome) user.nome = body.nome;
  if (body.email) user.email = body.email;
  if (body.telefone !== undefined) user.telefone = body.telefone;
  if (body.avatar_url !== undefined) user.avatar_url = body.avatar_url;

  const res: MeResponse = {
    id: user.id,
    tenant_id: user.tenant_id,
    nome: user.nome,
    email: user.email,
    roles: user.roles,
    avatar_url: user.avatar_url,
    telefone: user.telefone,
    ativo: user.ativo,
    permissions: getUserPermissions(user),
  };

  return NextResponse.json(res);
}
