import { NextResponse } from "next/server";

import { mockDb } from "@/server/mock-db";
import { getAuthContext } from "@/server/request-auth";

export async function GET(req: Request) {
  const ctx = getAuthContext(req);
  if (!ctx) {
    return NextResponse.json({ message: "Não autorizado" }, { status: 401 });
  }

  // Admin access validation
  if (!ctx.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "Acesso negado. Apenas administradores podem aceder." },
      { status: 403 }
    );
  }

  return NextResponse.json({
    rolePermissions: mockDb.rolePermissions,
    systemPermissions: mockDb.systemPermissions,
  });
}

export async function PUT(req: Request) {
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
  if (!body || !body.rolePermissions) {
    return NextResponse.json(
      { message: "Mapeamento rolePermissions é obrigatório" },
      { status: 400 }
    );
  }

  // Update in-memory database configuration
  mockDb.rolePermissions = body.rolePermissions;

  return NextResponse.json({
    rolePermissions: mockDb.rolePermissions,
    message: "Permissões de perfis (RBAC) atualizadas com sucesso!",
  });
}
