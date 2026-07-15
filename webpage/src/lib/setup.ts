import type { SetupStatusResponse } from "@/types/setup";

const apiBasePath = process.env.NEXT_PUBLIC_API_BASE_PATH;

if (!apiBasePath) {
  throw new Error("NEXT_PUBLIC_API_BASE_PATH is required");
}

const setupStatusUrl = `${apiBasePath}/setup/status`;

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
