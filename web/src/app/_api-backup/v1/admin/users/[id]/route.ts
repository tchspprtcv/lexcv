import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";

interface RouteParams {
  params: Promise<{
    id: string;
  }>;
}

export async function PUT(req: Request, { params }: RouteParams) {
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

  const { id } = await params;
  const userIndex = mockDb.users.findIndex(
    (u) => u.id === id && u.tenant_id === ctx.tenant_id
  );
  if (userIndex === -1) {
    return NextResponse.json({ message: "Utilizador não encontrado" }, { status: 404 });
  }

  const body = await req.json().catch(() => null);
  if (!body || !body.nome || !body.email || !body.roles) {
    return NextResponse.json(
      { message: "Nome, email e funções (roles) são obrigatórios." },
      { status: 400 }
    );
  }

  // Check if email already exists for a different user
  const emailTaken = mockDb.users.some(
    (u) => u.id !== id && u.email.toLowerCase() === body.email.toLowerCase()
  );
  if (emailTaken) {
    return NextResponse.json(
      { message: "Já existe outro utilizador registado com este endereço de email." },
      { status: 400 }
    );
  }

  const user = mockDb.users[userIndex];
  user.nome = body.nome;
  user.email = body.email;
  user.roles = body.roles;
  user.ativo = body.ativo !== false;
  user.permissions = body.permissions || [];
  
  if (body.telefone !== undefined) user.telefone = body.telefone;
  if (body.avatar_url !== undefined) user.avatar_url = body.avatar_url;
  
  // Update password if a new one is set (admin password reset)
  if (body.password && body.password.trim() !== "") {
    user.password = body.password;
  }

  return NextResponse.json(user);
}

export async function DELETE(req: Request, { params }: RouteParams) {
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

  const { id } = await params;
  
  // Protect self deletion
  if (id === ctx.user_id) {
    return NextResponse.json(
      { message: "Não é permitido apagar a sua própria conta de utilizador administrador." },
      { status: 400 }
    );
  }

  const userIndex = mockDb.users.findIndex(
    (u) => u.id === id && u.tenant_id === ctx.tenant_id
  );
  if (userIndex === -1) {
    return NextResponse.json({ message: "Utilizador não encontrado" }, { status: 404 });
  }

  // Remove the user from mock database
  mockDb.users.splice(userIndex, 1);

  return NextResponse.json({ message: "Utilizador removido com sucesso!" });
}
