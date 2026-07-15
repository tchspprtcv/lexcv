import type { NextConfig } from "next";

const backendOrigin = process.env.BACKEND_API_ORIGIN;
if (!backendOrigin) {
  throw new Error("BACKEND_API_ORIGIN is required");
}

const nextConfig: NextConfig = {
  output: "standalone",
  assetPrefix: "/landing-static",
  async rewrites() {
    return [
      { source: "/api/v1/:path*", destination: `${backendOrigin}/api/v1/:path*` },
    ];
  },
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
