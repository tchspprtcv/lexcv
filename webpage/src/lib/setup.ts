import type { SetupStatusResponse } from "@/types/setup";
import { getBackendOrigin } from "@/lib/backend-origin";

export async function fetchSetupStatus(init?: RequestInit): Promise<SetupStatusResponse> {
  // Called here (not at module scope) so a misconfigured origin is thrown
  // inside this function's Promise and caught by the caller's fail-open
  // try/catch (proxy.ts) instead of crashing module import — see 99-REVIEW.md CR-01.
  const backendOrigin = getBackendOrigin();
  const setupStatusUrl = `${backendOrigin}/api/v1/setup/status`;

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
