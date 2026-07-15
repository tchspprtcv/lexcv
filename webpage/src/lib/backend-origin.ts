/**
 * Fail-fast validation/normalization for BACKEND_API_ORIGIN, shared by
 * lib/setup.ts and lib/branding.ts.
 *
 * Evaluated once at module load (each caller assigns the result to a
 * top-level const, outside any try/catch and outside the async fetch
 * functions), so a misconfigured origin is a loud startup failure instead
 * of a silent runtime no-op — see 99-REVIEW.md WR-01: an unnormalized
 * trailing slash produces a double-slash path (`http://host//api/v1/...`)
 * that never matches the backend's route, and a missing scheme (e.g.
 * "localhost:8080") parses "successfully" via `new URL()` with a bogus
 * protocol and no real host, so `fetch()` quietly never reaches the
 * intended backend. Both symptoms are indistinguishable from "backend is
 * transiently down" once caught by the existing generic `catch` blocks in
 * proxy.ts/branding.ts, reproducing CR-01's exact "redirect gate never
 * fires" outcome via config instead of code.
 */
export function getBackendOrigin(): string {
  const raw = process.env.BACKEND_API_ORIGIN;

  if (!raw) {
    throw new Error("BACKEND_API_ORIGIN is required");
  }

  if (!/^https?:\/\//i.test(raw)) {
    throw new Error(
      `BACKEND_API_ORIGIN must include a scheme (http:// or https://), got: ${raw}`,
    );
  }

  // Strip trailing slash(es) so callers can safely concatenate a leading-slash path.
  return raw.replace(/\/+$/, "");
}
