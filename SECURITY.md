# Security notes for vitrina+

## Signing key (IMPORTANT)
- The OLD keystore (password `vitrinaplus`) was once committed to this public
  repo. It is **leaked and retired — never sign a public release with it.**
- Releases are now signed with a **private** keystore that is never committed
  (see `.gitignore`: `*.jks`, `keystore/`).
- GitHub Actions receives the key from repository secrets:
  - `VITRINA_KEYSTORE_BASE64` — base64 of the private `.jks` file
  - `VITRINA_STORE_PASSWORD`
  - `VITRINA_KEY_PASSWORD`
- If the private key is lost, the app can never be updated in place again —
  keep an offline backup in at least two safe places.

## Other hardening included
- `android:allowBackup="false"`
- `WRITE_EXTERNAL_STORAGE` capped at API 28 (only needed before Android 10)
- Reddit post links use `https://`
- Backup Restore rejects files larger than 5 MB
- No analytics/trackers; tokens are kept in memory only
