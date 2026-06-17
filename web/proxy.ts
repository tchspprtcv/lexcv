import { NextResponse, type NextRequest } from "next/server";

import { fetchSetupStatus } from "./src/lib/setup";

const SETUP_PATH = "/setup";
const LOGIN_PATH = "/login";
const DASHBOARD_PATH = "/dashboard";

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  try {
    const status = await fetchSetupStatus();

    if (!status.initialized && pathname !== SETUP_PATH) {
      return NextResponse.redirect(new URL(SETUP_PATH, request.url));
    }

    if (status.initialized && pathname === SETUP_PATH) {
      const hasAccessToken = Boolean(request.cookies.get("access_token")?.value);
      const destination = hasAccessToken ? DASHBOARD_PATH : LOGIN_PATH;
      return NextResponse.redirect(new URL(destination, request.url));
    }
  } catch {
    return NextResponse.next();
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
