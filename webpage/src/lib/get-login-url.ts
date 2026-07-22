/**
 * Resolves the target URL for the "Entrar" call sites across the public
 * webpage.
 *
 * Returns `{NEXT_PUBLIC_WEB_APP_URL}/login` (trailing slashes trimmed) when
 * the var is set — used in local dev, where `webpage` runs isolated from
 * the `web` app with no Caddy proxy in front of it, so a relative `/login`
 * 404s. Falls back to the relative `/login` when unset, which is the
 * correct production case: Caddy already proxies `/login` to the `web`
 * app there.
 *
 * Deliberately no-fail when unset — unlike getBackendOrigin(), an unset
 * NEXT_PUBLIC_WEB_APP_URL is the expected production condition, not a
 * config error, so this helper always returns a defined value instead of
 * raising.
 */
export function getLoginUrl(): string {
  const configured = process.env.NEXT_PUBLIC_WEB_APP_URL;
  return configured ? configured.replace(/\/+$/, "") + "/login" : "/login";
}
