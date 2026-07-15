import type { NextConfig } from "next";

// NOTE: no rewrites() here — every server-side fetch in this app (lib/setup.ts,
// lib/branding.ts) calls BACKEND_API_ORIGIN directly with an absolute URL, so a
// public "/api/v1/:path*" rewrite would only expose the full authenticated
// backend surface through this unauthenticated marketing site's origin without
// being used by any code path (see 99-REVIEW.md WR-02). Add a narrowly-scoped
// rewrite here only if a browser-side (non-server) fetch ever needs one.

const nextConfig: NextConfig = {
  output: "standalone",
  assetPrefix: "/landing-static",
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-XSS-Protection", value: "1; mode=block" },
          { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
          // TODO: 'unsafe-inline' still allows inline-script XSS payloads; migrate to a
          // nonce-/hash-based script-src (see 99-REVIEW.md WR-01) as follow-up.
          { key: "Content-Security-Policy", value: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self';" },
        ],
      },
    ];
  },
};

export default nextConfig;
