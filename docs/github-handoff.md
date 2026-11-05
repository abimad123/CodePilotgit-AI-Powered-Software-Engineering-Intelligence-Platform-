# GitHub App installation handoff contract

The backend supports a two-redirect GitHub App flow. The frontend routes described here are a contract; no frontend callback implementation exists in Stage 1.

## App settings

- Configure **App name** as `GITHUB_APP_NAME` (the GitHub App slug).
- Configure **Setup URL** as the frontend route `http://localhost:5173/github/setup` for local development.
- Configure **Callback URL** as `GITHUB_REDIRECT_URI`, for example `http://localhost:5173/github/callback`.
- Leave **Request user authorization (OAuth) during installation** off. GitHub does not offer a setup URL when that option is on. The backend explicitly starts user authorization after setup.
- The setup and callback URLs must use HTTPS outside localhost. Neither URL is a backend API endpoint because browser redirects do not carry the application's bearer token.

## Frontend sequence

1. An authenticated OWNER or ADMIN sends `POST /api/v1/projects/{projectId}/github/installations/start` with no body. The response is `200 {"installationUrl":"https://github.com/apps/<app-name>/installations/new?state=<random>"}`. Navigate the browser to that URL. The frontend must keep the initiating `projectId` in its own authenticated flow context; it must not infer it from a GitHub query parameter.
2. GitHub redirects to the configured setup URL after installation. Its documented setup parameter is `installation_id`; GitHub documents that `state` supplied on the installation URL is preserved for this flow. The frontend reads `state` and `installation_id` from the setup URL and sends `POST /api/v1/projects/{projectId}/github/installations/setup` with `{"state":"...","installationId":123}` and the initiating user's bearer token. The response is `200 {"authorizationUrl":"https://github.com/login/oauth/authorize?..."}`. Navigate to that URL. If `installation_id` is absent or invalid, the backend returns `400 MISSING_INSTALLATION_ID` and the frontend should show a retry option. Do not associate the ID at this step.
3. GitHub redirects to the configured callback URL after user authorization. The frontend sends `POST /api/v1/projects/{projectId}/github/installations/complete` with `{"state":"...","code":"..."}` and the same user's bearer token. Success is `200 {"installationId":123,"accountLogin":"...","accountType":"Organization"}`. The backend rechecks project role, exchanges the code, verifies the installation in the user's accessible installations and as the App, and only then links it to the initiating project.
4. On user cancellation, call `POST /api/v1/projects/{projectId}/github/installations/cancel` with `{"state":"..."}` if a state was returned. It responds `204` and consumes that state. GitHub may return no setup callback on cancellation; the state also expires after ten minutes. If OAuth returns `error` instead of `code`, do not call `complete`; cancel the state when available and show a failure. Do not assume a `setup_action` or other undocumented parameter exists.
5. Fetch `GET /api/v1/projects/{projectId}/github/installations` to refresh connection status. It returns a JSON array of verified linked installations; an empty array means none is connected.

All backend failures use the [API error envelope](api-conventions.md). Examples: `400 INVALID_STATE` for unknown, expired, consumed, wrong-user or wrong-project state; `400 MISSING_INSTALLATION_ID`; `403 FORBIDDEN` for lost project role; `403 GITHUB_INSTALLATION_DENIED` when the GitHub user cannot access the installation; `502 UPSTREAM_FAILURE` or `504 UPSTREAM_TIMEOUT` for GitHub failures. OAuth codes, access tokens, state, and private keys must not be logged or put in frontend persistent storage.

GitHub's [installation URL documentation](https://docs.github.com/en/apps/sharing-github-apps/sharing-your-github-app) documents `state` on `/apps/<app>/installations/new`. Its [setup URL documentation](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-setup-url) documents `installation_id` and warns that it is spoofable. Its [user access token flow](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app) documents OAuth `code` and `state` on the callback. A live GitHub App installation remains unverified until real App credentials and frontend routes are supplied.
