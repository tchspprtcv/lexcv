import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";

export async function POST(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Não autorizado" }, { status: 401 });
  }

  const user = mockDb.users.find((u) => u.id === ctx.user_id && u.tenant_id === ctx.tenant_id);
  if (!user) {
    return NextResponse.json({ message: "Utilizador não encontrado" }, { status: 404 });
  }

  const body = await req.json().catch(() => null);
  if (!body || !body.currentPassword || !body.newPassword) {
    return NextResponse.json(
      { message: "Palavra-passe atual e nova palavra-passe são obrigatórias" },
      { status: 400 }
    );
  }

  if (user.password !== body.currentPassword) {
    return NextResponse.json(
      { message: "A palavra-passe atual está incorreta." },
      { status: 400 }
    );
  }

  user.password = body.newPassword;

  return NextResponse.json({ message: "Palavra-passe alterada com sucesso!" });
}
