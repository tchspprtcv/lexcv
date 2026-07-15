import { NextResponse, type NextRequest } from "next/server";

import { fetchSetupStatus } from "./src/lib/setup";

const SETUP_PATH = "/setup";

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  try {
    const status = await fetchSetupStatus();

    if (!status.initialized && pathname !== SETUP_PATH) {
      return NextResponse.redirect(new URL(SETUP_PATH, request.url));
    }
  } catch {
    // fail open — um erro transitório do backend nunca deve bloquear a landing pública
    return NextResponse.next();
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|landing-static|favicon.ico|.*\\..*).*)"],
};
