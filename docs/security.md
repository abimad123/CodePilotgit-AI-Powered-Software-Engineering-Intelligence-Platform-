# Stage 1 security design

This describes the implemented backend. Build, local runtime, and selected security checks passed; this is not a production deployment or penetration assessment.

## Local authentication

- Registration normalizes email with `Locale.ROOT`, rejects duplicates, and stores a BCrypt hash. Password length is 12–72 characters and limited to 72 UTF-8 bytes to avoid BCrypt truncation.
- Login returns a signed HS256 JWT containing subject UUID, issuer, audience, issue/expiry times, and random token ID. The secret is a base64-encoded key of at least 32 random bytes supplied by environment variable. Access lifetime defaults to 15 minutes.
- A 32-byte random refresh token is returned in the JSON body. Only its SHA-256 hash is stored. Refresh rotates the token under a pessimistic database lock; old, expired, revoked, and unknown tokens are rejected. Logout revokes the presented refresh token. Access JWTs remain valid until their short expiry; logout is not immediate access-token revocation.
- Refresh tokens are bearer secrets. Browser clients must keep them out of `localStorage` and URLs. This Stage 1 API uses JSON token delivery, so CSRF protection is disabled for its stateless, header/body-token contract. If browser persistence is required, implement an HttpOnly Secure SameSite cookie flow with CSRF protection before adopting it.
- Spring Security validates JWT signature, issuer, audience, and expiry. Project access is checked through membership on every service operation. OWNER alone can delete a project or manage ADMIN roles; ADMIN can manage MEMBER roles; MEMBER can read.
- CORS uses an environment allowlist and rejects wildcard origins. The request ID filter bounds client IDs and emits a correlation header. ECS structured logs carry metadata, not request bodies or secrets.

## GitHub App

- GitHub App credentials are optional for application startup. An App installation ID supplied by a client is never trusted alone.
- The project OWNER/ADMIN initiates a user OAuth flow for an intended installation. The server stores a hash of a 256-bit random state with user, project, installation ID, and a 10-minute expiry. Completion needs the same authenticated user and project and consumes the state once.
- The backend exchanges the OAuth code for a GitHub user token, checks that the user can access the installation through GitHub, then queries the installation as the App before associating it. User and installation tokens are kept in memory for the request only. App JWTs use RS256 and a configured PKCS#8 RSA private key.
- The user must obtain an installation ID from GitHub before starting this backend flow; an automatic post-installation handoff is not implemented. The ID is verified through the user token before association.
- Repository selection is checked against the installation's repository list. API responses contain safe metadata only. No private keys, tokens, or source content are returned.
- Set GitHub App callback URL to the configured `GITHUB_REDIRECT_URI` (development example: `http://localhost:5173/github/callback`). The frontend callback is not implemented in Stage 1; a developer must manually forward `code` and `state` from the redirected URL to the authenticated completion API when testing live integration.
- The App needs **Metadata: read** and access to selected repositories. Enable the user authorization web flow and configure a client secret. The setup URL and webhook URL are not implemented yet; do not configure a webhook endpoint that this backend does not provide. [GitHub warns that setup URL installation IDs are spoofable](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-setup-url).

## Operational protections and limits

- Production should terminate TLS at a trusted proxy, store secrets in a secret manager, rate-limit auth and GitHub endpoints, and use a key rotation plan. These controls are not implemented in Stage 1.
- GitHub App `GITHUB_PRIVATE_KEY` must be an unencrypted PKCS#8 PEM with literal `\n` sequences when passed through Compose. Convert GitHub's default RSA PEM locally with `openssl pkcs8 -topk8 -nocrypt -in github.pem -out github-pkcs8.pem`; never commit either file.
- No repository code is cloned or executed by this backend.
