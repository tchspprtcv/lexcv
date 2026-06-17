import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { createMockJwt } from "@/server/mock-jwt";
import type { LoginRequest, LoginResponse } from "@/types/auth";

export async function POST(req: Request) {
  const body = (await req.json().catch(() => null)) as LoginRequest | null;
  if (!body?.email || !body?.password) {
    return NextResponse.json({ message: "Email e password são obrigatórios" }, { status: 400 });
  }

  const user = mockDb.users.find((u) => u.email.toLowerCase() === body.email.toLowerCase());
  if (!user || user.password !== body.password) {
    return NextResponse.json({ message: "Credenciais inválidas" }, { status: 401 });
  }

  if (user.ativo === false) {
    return NextResponse.json({ message: "Esta conta está desativada. Por favor, contacte o administrador." }, { status: 403 });
  }

  const now = Math.floor(Date.now() / 1000);
  const access_token = createMockJwt({
    sub: user.id,
    tenant_id: user.tenant_id,
    roles: user.roles,
    exp: now + 60 * 60,
  });
  const refresh_token = createMockJwt({
    sub: user.id,
    tenant_id: user.tenant_id,
    roles: user.roles,
    exp: now + 60 * 60 * 24 * 30,
  });

  const res: LoginResponse = {
    user: { id: user.id, nome: user.nome },
    access_token,
    refresh_token,
  };

  return NextResponse.json(res);
}
