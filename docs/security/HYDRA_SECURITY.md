# HYDRA security model

HYDRA treats imported projects, repositories and generated artifacts as untrusted input.

- ZIP/JAR/WAR/EAR traversal is bounded by entry count, per-entry size, total expanded size, nesting depth, compression ratio and read time.
- Archive paths are normalized and traversal/absolute paths are rejected.
- External project recovery performs static inspection only and does not load or execute imported classes/scripts.
- External processes are invoked through bounded policies with explicit arguments, timeouts and output limits.
- Repository credentials stay in backend-managed credential stores and are redacted from actionable errors.
- Remote URLs with embedded credentials are rejected.
- Destructive project operations validate path boundaries before deletion.
- Publication and injection flows expose preview/validation before mutation and preserve rollback where the target supports it.

## External repository registration and OAuth

HYDRA separates **public application registration data** from **confidential credentials**.
The browser can request a registration sheet for GitHub, GitLab, Microsoft/OneDrive,
Google Drive or Dropbox containing the provider console URL, application type,
public client/application identifier label, exact callback URI, PKCE requirement and
requested scopes. The sheet is returned with `Cache-Control: no-store`, can be copied
or downloaded, and intentionally contains no password, access token, refresh token,
client-secret value or device code.

Confidential OAuth values stay server-side. When a provider flow needs a client
secret, the UI shows only the environment-variable name in which the administrator
must provision it. Provider setup persisted from the UI contains public client IDs,
Microsoft organization selection, scopes and flow preferences only. User sign-in,
passwords, passkeys and MFA are handled on the provider's own authorization page.
HYDRA validates `state`, uses PKCE S256, fixes the callback server-side and does not
accept arbitrary redirect targets.

The default permission/flow posture is deliberately conservative:

- GitHub uses browser authorization with PKCE by default; Device Flow is an explicit
  option for constrained/headless installations. Hosted browser flow keeps the OAuth
  client secret only on the server.
- GitLab uses authorization code with PKCE by default; Device Authorization is an
  explicit alternative where the GitLab deployment supports it.
- OneDrive uses its application folder and `Files.ReadWrite.AppFolder` by default.
  Broader file access must be selected deliberately. Hosted Web registrations keep
  their secret server-side; local loopback/public-client and device flows do not
  require a secret value in the browser.
- Google Drive defaults to `drive.file`, limiting HYDRA to files the user explicitly
  opens with or creates through the application. Hosted Web clients keep the Google
  client secret on the HYDRA server.
- Dropbox uses authorization code with PKCE and the UI recommends App Folder content
  access unless the installation genuinely needs existing files outside HYDRA's
  application folder.

Provider tokens are stored only in the backend credential vault. Network calls use
fixed provider endpoints or validated configured endpoints, bounded timeouts and
response sizes, and error reporting passes through credential redaction.

## Frontend dependency audit policy

Release validation installs exactly the dependency graph committed in `package-lock.json`
with `npm ci`; it does not run `npm audit fix` automatically because that command may
change the reviewed lockfile and make the tested distribution differ from the packaged
one. The Windows release gate blocks HIGH/CRITICAL advisories in production dependencies
(`npm audit --omit=dev --audit-level=high`) and blocks CRITICAL advisories anywhere in the
complete development/test graph while still printing lower-severity development advisories
for explicit review. Dependency upgrades are made as reviewed source changes and then run
through the normal TypeScript, lint, build, Vitest, accessibility and packaging gates.
