import type { SetupStatusResponse } from "@/types/setup";

const backendOrigin = process.env.BACKEND_API_ORIGIN;

if (!backendOrigin) {
  throw new Error("BACKEND_API_ORIGIN is required");
}

const setupStatusUrl = `${backendOrigin}/api/v1/setup/status`;

export async function fetchSetupStatus(init?: RequestInit): Promise<SetupStatusResponse> {
  const response = await fetch(setupStatusUrl, {
    ...init,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    throw new Error(`Setup status failed with ${response.status}`);
  }

  return (await response.json()) as SetupStatusResponse;
}
