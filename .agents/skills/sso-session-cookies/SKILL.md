---
name: sso-session-cookies
description: Use when building the WebView SSO login, the encrypted persistent cookie jar, SsoSession, NeedsLogin state or the re-login flow in the university app.
---

# SSO login and cookie session

Facts (owner-confirmed): SSO redirects to a separate domain, the login form is a plain HTML `<form>`, no captcha, and after login the URL carries a one-time ticket; the main site then sets its own session cookie. Cookie "Expires" shows "Session". Exact hosts and cookie names are in `docs/DISCOVERY.md`; if `TODO`, ask the owner.

## Login flow

1. `SsoSession.login()` opens a visible WebView at the Moodle URL; the redirect chain goes through SSO.
2. The user types credentials into the real SSO page. We never read, intercept, store or autofill the password.
3. When navigation lands back on the Moodle host and the page is the logged-in page, read cookies with `CookieManager.getCookie(url)` for the SSO host and the provider host and store them in `PersistentCookieJar`.
4. Close the WebView, then fetch the main page with OkHttp and extract `sesskey` (skill `moodle-ajax-client`).

## WebView hardening

- Allowed hosts: university + SSO only. Everything else opens in the system browser (`shouldOverrideUrlLoading`).
- JavaScript enabled (the sites need it); **no** `addJavascriptInterface`; `allowFileAccess=false`; no mixed content.
- Never log URLs with query strings (the one-time ticket is in the URL).
- Clear WebView cookies/cache on logout.

## PersistentCookieJar

- Implements OkHttp `CookieJar`, keyed by host (so SSO cookies are shared by all providers on the SSO).
- Persisted as encrypted bytes: AES-GCM with an Android Keystore key (do not use the deprecated `EncryptedSharedPreferences`). Store IV with the ciphertext.
- Drop expired cookies on load. Provide `clear(host)` and `clearAll()`.
- `android:allowBackup="false"` so cookies never reach a cloud backup.

## States

`SsoSession` exposes a flow: `SignedOut`, `Active`, `NeedsLogin`. Each `ProviderSession` has its own `NeedsLogin`. A provider failing never signs the others out.

## Re-login

Triggered by `NeedsLogin`: show a clear banner, open the WebView login (if the SSO cookie is still alive the SSO returns immediately with no password prompt; if not, the user signs in). After success, refresh `sesskey` and re-run the sync for that provider.

## Silent session for a second provider (unverified)

With a live SSO cookie, request the second provider's URL with OkHttp following redirects and see whether it sets its own cookie. Do not build this until a real second system exists and the owner has the `DISCOVERY` facts.

## Tests

Cookie jar round trip (encrypt/decrypt, expiry, host keying), WebView host allow-list logic (as a pure function), state transitions.
