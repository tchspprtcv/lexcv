import { parseMockJwt } from "./mock-jwt";

export interface RequestAuthContext {
  user_id: string;
  tenant_id: string;
  roles: string[];
}

export function getAuthContext(req: Request): RequestAuthContext | null {
  const header = req.headers.get("authorization");
  if (!header) return null;
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match) return null;
  const token = match[1];
  const payload = parseMockJwt(token);
  if (!payload) return null;
  if (payload.exp * 1000 < Date.now()) return null;

  return {
    user_id: payload.sub,
    tenant_id: payload.tenant_id,
    roles: payload.roles,
  };
}
