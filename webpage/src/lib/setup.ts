import type { SetupStatusResponse } from "@/types/setup";
import { getBackendOrigin } from "@/lib/backend-origin";

const backendOrigin = getBackendOrigin();

const setupStatusUrl = `${backendOrigin}/api/v1/setup/status`;

export async function fetchSetupStatus(init?: RequestInit): Promise<SetupStatusResponse> {
  const response = await fetch(setupStatusUrl, {
    ...init,
    cache: "no-store",
    signal: init?.signal ?? AbortSignal.timeout(3000),
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
