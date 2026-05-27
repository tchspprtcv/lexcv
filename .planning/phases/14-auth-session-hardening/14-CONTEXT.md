# Phase 14: Auth & Session Hardening - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Secure authentication flows and session management
</domain>

<decisions>
## Implementation Decisions

### JWT Storage
- Move JWT access and refresh tokens to `HttpOnly` secure cookies.
- Modify `AuthController` login and refresh endpoints to set `Set-Cookie` headers.

### Rate Limiting
- Implement basic rate limiting (e.g., using a simple in-memory bucket or library) for `/auth/login` to prevent brute-force attacks.

### Token Rotation
- Ensure `/auth/refresh` rotates the refresh token (already implemented partially, but verify cookie rotation).

### Password Complexity
- Enforce strict password complexity during user creation (`AdminController`) and password change (`AuthController`): Minimum 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 special character.

</decisions>

<code_context>
## Existing Code Insights
- Authentication currently returns tokens in JSON body.
- No rate limiting exists.
</code_context>

<specifics>
## Specific Ideas
- Use `ResponseCookie` in Spring to build the cookies.
</specifics>

<deferred>
## Deferred Ideas
None
</deferred>
