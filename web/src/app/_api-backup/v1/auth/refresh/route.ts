import { NextResponse } from "next/server";

import { createMockJwt, parseMockJwt } from "@/server/mock-jwt";

export async function POST(req: Request) {
  const body = (await req.json().catch(() => null)) as { refresh_token?: string } | null;
  if (!body?.refresh_token) {
    return NextResponse.json({ message: "refresh_token é obrigatório" }, { status: 400 });
  }

  const payload = parseMockJwt(body.refresh_token);
  if (!payload || payload.exp * 1000 < Date.now()) {
    return NextResponse.json({ message: "Refresh token inválido" }, { status: 401 });
  }

  const now = Math.floor(Date.now() / 1000);
  const access_token = createMockJwt({
    sub: payload.sub,
    tenant_id: payload.tenant_id,
    roles: payload.roles,
    exp: now + 60 * 60,
  });

  return NextResponse.json({
    access_token,
    refresh_token: body.refresh_token,
  });
}
