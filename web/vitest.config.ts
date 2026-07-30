import path from "node:path";

import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    env: {
      // Satisfies the module-level guard in src/lib/api.ts for code paths
      // that import it transitively but never call apiFetch.
      NEXT_PUBLIC_API_BASE_PATH: "/api/v1",
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
