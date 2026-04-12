# Authentication Flow — Steam5

> **Audience:** New developers onboarding to the Steam5 project who need a complete, code-level understanding of how
> authentication works end-to-end.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Diagram](#2-architecture-diagram)
3. [Key Files Reference](#3-key-files-reference)
4. [Environment Variables & Configuration](#4-environment-variables--configuration)
5. [Steam OpenID 2.0 Deep Dive](#5-steam-openid-20-deep-dive)
    - 5.1 [Login Initiation — `GET /api/auth/steam/login`](#51-login-initiation--get-apiauthsteamlogin)
    - 5.2 [Callback & Verification —
      `GET /api/auth/steam/callback`](#52-callback--verification--get-apiauthsteamcallback)
6. [JWT Token System](#6-jwt-token-system)
    - 6.1 [Key Derivation](#61-key-derivation)
    - 6.2 [Token Generation](#62-token-generation)
    - 6.3 [Token Verification](#63-token-verification)
7. [Cookie Mechanics](#7-cookie-mechanics)
8. [User Profile Creation (First Login / Upsert)](#8-user-profile-creation-first-login--upsert)
9. [Spring Security Configuration](#9-spring-security-configuration)
    - 9.1 [Filter Chain](#91-filter-chain)
    - 9.2 [Rate Limit Filter](#92-rate-limit-filter)
    - 9.3 [Admin Token Filter](#93-admin-token-filter)
10. [Frontend Auth State Management](#10-frontend-auth-state-management)
    - 10.1 [Server-Side Initial Auth Resolution](#101-server-side-initial-auth-resolution)
    - 10.2 [React AuthContext](#102-react-authcontext)
    - 10.3 [Steam Login Button & CSRF State](#103-steam-login-button--csrf-state)
11. [Next.js API Route Layer](#11-nextjs-api-route-layer)
12. [Token Usage in Authenticated API Requests](#12-token-usage-in-authenticated-api-requests)
13. [Logout Flow](#13-logout-flow)
14. [Admin Authentication](#14-admin-authentication)
15. [Security Properties Summary](#15-security-properties-summary)
16. [Testing & Verification](#16-testing--verification)

---

## 1. Overview

Steam5 uses **Steam OpenID 2.0** for authentication. There are **no local user passwords** — identity is entirely
delegated to Steam. After Steam verifies a user, the backend issues a signed **JWT** which the frontend stores as an *
*HttpOnly cookie** (`s5_token`).

| Concern          | Solution                                                    |
|------------------|-------------------------------------------------------------|
| Identity / login | Steam OpenID 2.0                                            |
| Session token    | Stateless JWT (HMAC-SHA256)                                 |
| Token storage    | HttpOnly, SameSite=Lax cookie                               |
| Passwords        | None — Steam handles credentials                            |
| Token expiry     | 30 days                                                     |
| Login CSRF       | State nonce cookie (`s5_state`)                             |
| Rate limiting    | `AuthRateLimitFilter` — 60 req/min per IP on `/api/auth/**` |
| Admin access     | Separate shared-secret header (`X-Admin-Token`)             |

The architecture is a **two-tier proxy**: the browser talks to the **Next.js** frontend server (App Router), which in
turn talks to the **Spring Boot** backend API. The frontend never exposes the JWT to client-side JavaScript.

---

## 2. Architecture Diagram

```mermaid
sequenceDiagram
    actor User as Browser / User
    participant FE as Next.js Frontend
    participant BE as Spring Boot Backend
    participant Steam as Steam OpenID 2.0
%% ──── Login Initiation ────
    User ->> FE: Click "Sign in through Steam"
    FE ->> FE: generate state = crypto.randomUUID()
    FE ->> FE: set cookie: s5_state={state} (max-age 300s, SameSite=Lax)
    Note over FE: Constructs:<br/>{BACKEND}/api/auth/steam/login?redirect={callback}&state={state}
    User ->> BE: GET /api/auth/steam/login?redirect=...&state=...
    BE ->> BE: Validate redirect is within trusted origin
    BE ->> BE: Embed state in return_to URL
    BE ->> BE: Build OpenID 2.0 checkid_setup URL
    Note over BE: openid.return_to={callback}?state={state}<br/>openid.realm={origin}
    BE -->> User: 302 Redirect → https://steamcommunity.com/openid/login
    User ->> Steam: OpenID 2.0 authentication request
    Steam -->> User: Steam login page
    User ->> Steam: Enters Steam credentials
    Steam -->> User: 302 Redirect → {return_to}?state={state}&openid.*=...
%% ──── CSRF State Verification ────
    User ->> FE: GET /api/auth/steam/callback?state={state}&openid.*=...
    FE ->> FE: stateFromUrl vs s5_state cookie — must match
    Note over FE: Mismatch → redirect to ?auth=csrf_error
%% ──── OpenID Assertion Verification ────
    FE ->> BE: GET /api/auth/steam/callback?state={state}&openid.*=... (proxy)
    BE ->> BE: Build check_authentication POST body<br/>(copies openid.* only, always uses hardcoded OPENID_ENDPOINT)
    BE ->> Steam: POST https://steamcommunity.com/openid/login<br/>(openid.mode=check_authentication)
    Steam -->> BE: 200 ns:sreg\nis_valid:true
    BE ->> BE: Assert is_valid:true
    BE ->> BE: Extract SteamID64 via regex on openid.claimed_id
    BE -->> BE: updateUserProfile(steamId) [async — does not block login]
    Note over BE: Steam API call runs in background thread pool
    BE ->> BE: generateToken(steamId) → signed JWT
    BE -->> FE: 200 { steamId, token }
    FE ->> FE: Set-Cookie: s5_token={token} (HttpOnly, SameSite=Lax)
    FE ->> FE: Clear s5_state cookie (maxAge=0)
    FE -->> User: 302 Redirect → /review-guesser/1?auth=ok
%% ──── Subsequent Requests ────
    User ->> FE: Any page load (s5_token cookie sent automatically)
    FE ->> FE: resolveAuth() — read s5_token from cookies (server-side)
    FE ->> BE: GET /api/auth/validate [Authorization: Bearer {jwt}]
    BE ->> BE: verifyToken() — parse & verify HMAC signature + expiry
    BE -->> FE: 200 { valid: true, steamId }
    FE ->> FE: Pass initialAuth to <AuthProvider>
    FE -->> User: Render page with auth state
%% ──── Logout ────
    User ->> FE: GET /api/auth/logout
    FE ->> Browser: Clear-Site-Data: "cookies" (Chrome/Firefox)
    FE ->> Browser: Set-Cookie: s5_token=""; maxAge=0 (Safari fallback)
    FE-->>User: 302 Redirect → /review-guesser/1
```

---

## 3. Key Files Reference

| File                                                                 | Layer    | Purpose                                                                   |
|----------------------------------------------------------------------|----------|---------------------------------------------------------------------------|
| `backend/src/main/java/org/steam5/web/AuthController.java`           | Backend  | Login redirect, OpenID callback, JWT validate endpoints                   |
| `backend/src/main/java/org/steam5/service/AuthTokenService.java`     | Backend  | JWT generation and verification (HMAC-SHA256)                             |
| `backend/src/main/java/org/steam5/security/AuthRateLimitFilter.java` | Backend  | Per-IP rate limiter for all `/api/auth/**` endpoints                      |
| `backend/src/main/java/org/steam5/security/AdminTokenFilter.java`    | Backend  | Spring Security filter for `X-Admin-Token` header                         |
| `backend/src/main/java/org/steam5/config/SecurityConfig.java`        | Backend  | Spring Security filter chain, endpoint protection rules                   |
| `backend/src/main/java/org/steam5/domain/User.java`                  | Backend  | User JPA entity (steamId as PK, no password field)                        |
| `backend/src/main/java/org/steam5/service/SteamUserService.java`     | Backend  | Async upsert of user profile from Steam GetPlayerSummaries API            |
| `backend/src/main/resources/application.yml`                         | Backend  | Auth config (`auth.jwtSecret`, `auth.redirectBase`, etc.)                 |
| `frontend/app/layout.tsx`                                            | Frontend | Root layout — server-side `resolveAuth()`, wraps app in `<AuthProvider>`  |
| `frontend/app/api/auth/steam/callback/route.ts`                      | Frontend | Next.js API route — CSRF state check, proxies to backend, sets cookie     |
| `frontend/app/api/auth/me/route.ts`                                  | Frontend | Next.js API route — checks token validity (used by SWR polling)           |
| `frontend/app/api/auth/logout/route.ts`                              | Frontend | Next.js API route — clears cookie, redirects to home                      |
| `frontend/src/contexts/AuthContext.tsx`                              | Frontend | React Context providing `isSignedIn`, `steamId`, `refreshAuth()`          |
| `frontend/src/components/SteamLoginButton.tsx`                       | Frontend | Login button — generates CSRF state, builds login URL                     |
| `frontend/src/components/AuthLogoutLink.tsx`                         | Frontend | Logout link (only visible when authenticated)                             |
| `frontend/app/review-guesser/[round]/actions.ts`                     | Frontend | Server Action — reads cookie and sends `Authorization: Bearer` to backend |

---

## 4. Environment Variables & Configuration

### Backend (`application.yml`)

```yaml
auth:
  jwtSecret: ${AUTH_JWT_SECRET:change-me-please-change-me-32-bytes-min}
  redirectBase: ${REDIRECT_BASE:http://localhost:3000}

admin:
  api-token: ${ADMIN_API_TOKEN:}
```

| Variable          | Default                                   | Required            | Description                                                                                                                                   |
|-------------------|-------------------------------------------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `AUTH_JWT_SECRET` | `change-me-please-change-me-32-bytes-min` | **Yes (in prod)**   | HMAC key for signing JWTs. Must be ≥ 32 bytes. Startup throws `IllegalStateException` if shorter; logs a `warn` if it looks like the default. |
| `REDIRECT_BASE`   | `http://localhost:3000`                   | Yes                 | Trusted frontend origin. The `redirect` parameter on `/api/auth/steam/login` is rejected unless its origin matches this value.                |
| `ADMIN_API_TOKEN` | _(empty)_                                 | For admin endpoints | Shared secret for `X-Admin-Token` header. If empty, all admin calls return 401.                                                               |

> **Generating a production secret:**
> ```bash
> openssl rand -base64 48
> ```
> Set the output as `AUTH_JWT_SECRET` in `.env` (see `.env.example`). The default value ships in this repo and must *
*never** be used in production.

### Frontend (`.env.local` / runtime environment)

| Variable                 | Default                 | Description                                                                                                          |
|--------------------------|-------------------------|----------------------------------------------------------------------------------------------------------------------|
| `NEXT_PUBLIC_API_DOMAIN` | `http://localhost:8080` | Backend API base URL. Used to construct all `fetch()` calls to the Spring Boot server.                               |
| `NEXT_PUBLIC_DOMAIN`     | `https://steam5.org`    | Frontend public origin. Used to construct the OpenID `redirect` callback URL and determine the `Secure` cookie flag. |

---

## 5. Steam OpenID 2.0 Deep Dive

Steam OpenID 2.0 is an **identity federation protocol**. The user proves their identity to Steam (they know their Steam
password), and Steam asserts that identity back to the application via a signed redirect. The application never handles
or sees the user's Steam password.

This is **not** OAuth 2.0. There are no `access_token` or `refresh_token` concepts — only an identity assertion (who is
this user?).

### 5.1 Login Initiation — `GET /api/auth/steam/login`

**File:** `backend/src/main/java/org/steam5/web/AuthController.java`

```java

@GetMapping("/steam/login")
public ResponseEntity<Void> startLogin(
        @RequestParam(value = "redirect", required = false) String redirect,
        @RequestParam(value = "state", required = false) String state) {

    // Validate redirect against trusted origin — falls back to default if absent or untrusted
    final String baseReturnTo = (redirect == null || redirect.isBlank() || !isAllowedRedirect(redirect))
            ? defaultRedirectBase + "/api/auth/steam/callback"
            : redirect;

    // If the frontend supplied a CSRF state token, embed it in return_to so
    // Steam carries it back in the redirect and the callback can verify it.
    final String returnTo = (state != null && SAFE_STATE_PATTERN.matcher(state).matches())
            ? baseReturnTo + (baseReturnTo.contains("?") ? "&" : "?") + "state=" + enc(state)
            : baseReturnTo;

    final String realm = deriveOriginSafe(returnTo, defaultRedirectBase);
    final String url = OPENID_ENDPOINT + "?openid.ns=" + enc("http://specs.openid.net/auth/2.0")
            + "&openid.mode=checkid_setup"
            + "&openid.return_to=" + enc(returnTo)
            + "&openid.realm=" + enc(realm)
            + "&openid.identity=" + enc("http://specs.openid.net/auth/2.0/identifier_select")
            + "&openid.claimed_id=" + enc("http://specs.openid.net/auth/2.0/identifier_select");
    return ResponseEntity.status(302).location(URI.create(url)).build();
}
```

**`isAllowedRedirect()` — open-redirect prevention:**

```java
private boolean isAllowedRedirect(String redirect) {
    try {
        URI uri = URI.create(redirect);
        URI base = URI.create(defaultRedirectBase);
        return uri.getScheme() != null
                && uri.getScheme().equals(base.getScheme())
                && uri.getHost() != null
                && uri.getHost().equals(base.getHost())
                && uri.getPort() == base.getPort();
    } catch (Exception e) {
        return false;
    }
}
```

The `redirect` parameter must share the exact scheme, host, and port of the configured `defaultRedirectBase`. Any other
URL is silently replaced with the default callback.

The constructed redirect to Steam looks like:

```
https://steamcommunity.com/openid/login
  ?openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0
  &openid.mode=checkid_setup
  &openid.return_to=https%3A%2F%2Fsteam5.org%2Fapi%2Fauth%2Fsteam%2Fcallback%3Fstate%3Dabc123
  &openid.realm=https%3A%2F%2Fsteam5.org
  &openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select
  &openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select
```

Note that `state=abc123` is embedded inside `openid.return_to` — Steam preserves all existing query parameters when it
redirects back, so the state arrives at the Next.js callback intact.

| Parameter                               | Value                              | Meaning                                                      |
|-----------------------------------------|------------------------------------|--------------------------------------------------------------|
| `openid.ns`                             | `http://specs.openid.net/auth/2.0` | OpenID 2.0 namespace                                         |
| `openid.mode`                           | `checkid_setup`                    | Interactive login (shows Steam login UI)                     |
| `openid.return_to`                      | callback URL + `?state=...`        | Where Steam redirects back after login; includes state token |
| `openid.realm`                          | Origin of `return_to`              | Trusted domain — must match `return_to` origin               |
| `openid.identity` / `openid.claimed_id` | `identifier_select`                | Tells Steam to let the user pick their identity              |

### 5.2 Callback & Verification — `GET /api/auth/steam/callback`

**File:** `backend/src/main/java/org/steam5/web/AuthController.java`

This endpoint receives Steam's assertion redirect and **verifies it is genuine** before trusting it.

#### Step 1: CSRF State Verification (Next.js layer)

**File:** `frontend/app/api/auth/steam/callback/route.ts`

Before the backend is called, the Next.js route verifies the state token:

```typescript
const stateFromUrl = url.searchParams.get('state');
const stateFromCookie = req.cookies.get('s5_state')?.value;
if (stateFromCookie) {
    // Cookie was set → state param must be present and must match
    if (!stateFromUrl || stateFromUrl !== stateFromCookie) {
        console.error('[steam5] CSRF state mismatch — possible login-CSRF attack');
        return NextResponse.redirect(new URL('/review-guesser/1?auth=csrf_error', base));
    }
}
```

If the browser's `s5_state` cookie is present (it always will be for logins initiated via the login button) and the URL
param doesn't match, the request is rejected before any backend call is made.

#### Step 2: Build `check_authentication` request body

```java
private static String buildCheckAuthBody(Map<String, String> params) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    // Copy ALL openid.* params back to Steam, EXCEPT mode
    for (Map.Entry<String, String> e : params.entrySet()) {
        final String key = e.getKey();
        if (key.startsWith("openid.") && !"openid.mode".equals(key)) {
            form.add(key, e.getValue());
        }
    }
    // Replace mode with check_authentication
    form.add("openid.mode", "check_authentication");
    // ... URL-encode and join with &
}
```

The non-`openid.*` params (including `state`) are intentionally excluded — only the OpenID assertion parameters are
forwarded to Steam.

#### Step 3: POST to Steam — always the hardcoded endpoint

```java
// The check_authentication request always goes to the hardcoded constant.
// Never use params.get("openid.op_endpoint") — that would let an attacker
// point the backend at a server they control (SSRF + authentication bypass).
final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OPENID_ENDPOINT))   // ← hardcoded constant, not from request params
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.ACCEPT, "text/plain")
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .header(HttpHeaders.USER_AGENT, "steam5-auth/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
final HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
```

`HTTP_CLIENT` is a `static final` field — a single reused instance with connection pooling and a 5-second connect
timeout. Steam's response body is plain-text key-value:

```
ns:http://specs.openid.net/auth/2.0
is_valid:true
```

#### Step 4: Assert `is_valid:true`

```java
if(res.statusCode() !=200||resBody ==null||!resBody.

contains("is_valid:true")){
        return ResponseEntity.

status(401).

body(Map.of("error", "invalid_openid"));
        }
```

#### Step 5: Extract SteamID64

```java
private static final Pattern STEAM_ID_PATTERN =
        Pattern.compile("https://steamcommunity.com/openid/id/([0-9]{17})");

final Matcher m = STEAM_ID_PATTERN.matcher(params.get("openid.claimed_id"));
final String steamId = m.group(1);  // e.g. "76561198012345678"
```

#### Step 6: Async profile update + token issuance

```java
// Profile enrichment is best-effort and non-blocking — runs in the Spring async thread pool
steamUserService.updateUserProfile(steamId);

final String token = tokenService.generateToken(steamId);
return ResponseEntity.

ok(Map.of("steamId", steamId, "token",token));
```

---

## 6. JWT Token System

**File:** `backend/src/main/java/org/steam5/service/AuthTokenService.java`

The project uses the [jjwt](https://github.com/jwtk/jjwt) library (`io.jsonwebtoken:jjwt-api` + `jjwt-impl` +
`jjwt-jackson`).

### 6.1 Key Derivation

```java
public AuthTokenService(
        @Value("${auth.jwtSecret:change-me-please-change-me-32-bytes-min}") String secret) {
    // The @Value default lets the app boot locally with no env var set.
    // Startup guard: throw immediately if the secret is too short.
    if (secret == null || secret.length() < 32) {
        throw new IllegalStateException(
                "auth.jwtSecret must be at least 32 characters. " +
                        "Generate a strong secret with: openssl rand -base64 48");
    }
    if (secret.startsWith("change-me")) {
        log.warn("auth.jwtSecret is the default insecure value — set AUTH_JWT_SECRET in production.");
    }
    // Key length determines HMAC algorithm:
    //   ≥ 32 bytes → HS256
    //   ≥ 48 bytes → HS384
    //   ≥ 64 bytes → HS512
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
}
```

The `@Value` default allows the app to boot locally without any environment variable configured. The startup guard still
enforces a minimum key length and warns loudly if the well-known default is detected, so a misconfigured production
deployment fails fast rather than silently issuing forgeable tokens. The derived key is held in memory only — never
logged or written to disk.

### 6.2 Token Generation

```java
public String generateToken(String steamId) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(60L * 60L * 24L * 30L); // 30 days

    return Jwts.builder()
            .subject(steamId)          // "sub" claim — SteamID64
            .issuedAt(Date.from(now))  // "iat" claim
            .expiration(Date.from(exp))// "exp" claim
            .signWith(key)
            .compact();
}
```

A decoded JWT payload:

```json
{
  "sub": "76561198012345678",
  "iat": 1712880000,
  "exp": 1715472000
}
```

The compact form is `header.payload.signature` — each part is base64url-encoded.

### 6.3 Token Verification

```java
public String verifyToken(String token) {
    try {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                // Atomically: decode header+payload, recompute HMAC-SHA256,
                // compare with signature (timing-safe internally), verify exp.
                .getPayload()
                .getSubject();          // returns steamId, or throws on any failure
    } catch (Exception e) {
        log.debug("Token verification failed", e);
        return null;
    }
}
```

---

## 7. Cookie Mechanics

**File:** `frontend/app/api/auth/steam/callback/route.ts`

After the backend returns `{ steamId, token }`, the Next.js callback route sets the token cookie and clears the CSRF
state cookie in the same response:

```typescript
// Set the session cookie
resp.cookies.set('s5_token', data.token, {
    httpOnly: true,           // Not readable by JS → prevents XSS theft
    sameSite: 'lax',          // Not sent on cross-site POSTs → mitigates CSRF
    secure: base.startsWith('https'),  // HTTPS-only in production
    path: '/',
    maxAge: 60 * 60 * 24 * 30,        // 30 days — matches JWT expiry
});

// Clear the CSRF state cookie — it's single-use
clearStateCookie(resp, base);
```

| Cookie     | Attributes                                           | Purpose                                                                       |
|------------|------------------------------------------------------|-------------------------------------------------------------------------------|
| `s5_token` | `HttpOnly; SameSite=Lax; Secure; Path=/; MaxAge=30d` | JWT session token — never readable by JavaScript                              |
| `s5_state` | `SameSite=Lax; Secure; Path=/; MaxAge=300`           | Short-lived CSRF nonce — set client-side by login button, cleared on callback |

**Why SameSite=Lax for `s5_token`:** `Strict` would break the redirect from Steam back to the app (Steam is a different
origin, and `Strict` prevents cookies from being sent on any cross-origin navigation). `Lax` allows cookies on top-level
navigations (safe redirects) while blocking them on cross-site sub-resource loads.

**Clearing cookies on logout** (`frontend/app/api/auth/logout/route.ts`):

The logout response uses two complementary mechanisms:

```typescript
// Chrome / Firefox: clears every cookie on this origin in one shot.
// More thorough than manually expiring individual cookies.
resp.headers.set('Clear-Site-Data', '"cookies"');

// Safari fallback: Clear-Site-Data is not supported in Safari, so we
// also explicitly expire s5_token via Set-Cookie.
resp.cookies.set('s5_token', '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: base.startsWith('https'),
    path: '/',
    maxAge: 0,
});
```

| Mechanism                         | Coverage                   | Browser support                |
|-----------------------------------|----------------------------|--------------------------------|
| `Clear-Site-Data: "cookies"`      | All cookies for the origin | Chrome, Firefox                |
| `Set-Cookie: s5_token=; maxAge=0` | `s5_token` specifically    | All browsers (Safari fallback) |

`"storage"` is intentionally excluded from `Clear-Site-Data` — localStorage holds the user's anonymous game state, which
should survive a logout and is only cleared on the next login (handled by `clearAll()` in `SteamLoginButton`).

---

## 8. User Profile Creation (First Login / Upsert)

**File:** `backend/src/main/java/org/steam5/service/SteamUserService.java`

There is **no explicit registration endpoint**. A user record is created (or updated) automatically every time they log
in. The Steam API call runs **asynchronously** — it does not add latency to the login response.

```java

@Async          // runs in Spring's default async thread pool
@Transactional  // each invocation gets its own transaction
public void updateUserProfile(String steamId) {
    final User existing = userRepository.findById(steamId).orElse(null);

    final String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/"
            + "?key=" + apiKey + "&steamids=" + steamId;
    final String body = steamHttpClient.get(url);  // has retry/backoff logic

    final JsonNode player = objectMapper.readTree(body)   // Spring-injected ObjectMapper
            .path("response").path("players").get(0);

    final User user = existing != null ? existing : new User();
    user.setSteamId(steamId);
    user.setPersonaName(player.path("personaname").asText(null));
    // ... more fields ...
    userRepository.save(user);

    // If avatar changed, trigger async blurhash regeneration
    if (avatarChanged || avatarFullChanged) {
        eventPublisher.publishEvent(new BlurhashEncodeRequested(...));
    }
}
```

`@EnableAsync` must be present on the application class (`Steam5Application.java`) for `@Async` to take effect.

### User Entity (`User.java`)

```java

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"steam_id"}))
public class User {

    @Id
    @Column(name = "steam_id", nullable = false, length = 32)
    private String steamId;          // SteamID64, e.g. "76561198012345678"

    private String personaName;
    private String profileUrl;
    private String avatar;
    private String avatarFull;
    private String blurhashAvatar;
    private String blurdataAvatar;
    // ...

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;  // set by @PrePersist, never via field initializer

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

Key design decisions:

- **`steamId` is the primary key** — SteamID64 is globally unique and stable; no surrogate UUID needed.
- **No password field** — identity is proven by Steam.
- **`createdAt` has no field initializer** — `@PrePersist` is the single authoritative place it is set. The previous
  field-initializer (`= OffsetDateTime.now()`) was dead code because `@PrePersist` always ran before it mattered.

---

## 9. Spring Security Configuration

### 9.1 Filter Chain

**File:** `backend/src/main/java/org/steam5/config/SecurityConfig.java`

```java

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                               AdminTokenFilter adminTokenFilter,
                                               AuthRateLimitFilter authRateLimitFilter) {
    http
            .csrf(AbstractHttpConfigurer::disable)  // SameSite=Lax + stateless JWT replaces CSRF tokens
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/review-game/**").permitAll()
                    .requestMatchers("/api/details/**").permitAll()
                    .requestMatchers("/api/metrics/**").permitAll()
                    .requestMatchers("/api/cache/**").permitAll()
                    .requestMatchers("/api/leaderboard/**").permitAll()
                    .requestMatchers("/api/profile/**").permitAll()
                    .requestMatchers("/api/admin/**").authenticated()     // requires ROLE_ADMIN via AdminTokenFilter
                    .requestMatchers("/api/seasons/**").permitAll()
                    .requestMatchers("/api/stats/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()          // auth endpoints are public
                    .anyRequest().authenticated()
            )
            // Both custom filters anchor to BasicAuthenticationFilter — the only
            // registered Spring Security filter we can legally use as a reference.
            // Custom filters (like AdminTokenFilter itself) have no registered order
            // and cannot be used as anchor points; doing so throws at startup.
            // Registering authRateLimitFilter first places it earlier in the chain:
            //   AuthRateLimitFilter → AdminTokenFilter → BasicAuthenticationFilter → …
            .addFilterBefore(authRateLimitFilter, BasicAuthenticationFilter.class)
            .addFilterBefore(adminTokenFilter, BasicAuthenticationFilter.class)
            .httpBasic(basic -> {
            })
            .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
}
```

**Filter execution order on a request to `/api/auth/**`:**

1. `AuthRateLimitFilter` — check IP request count; return 429 if exceeded
2. `AdminTokenFilter` — skipped (path doesn't start with `/api/admin/`)
3. Spring Security authorization — `permitAll()` for `/api/auth/**`
4. Controller handler

### 9.2 Rate Limit Filter

**File:** `backend/src/main/java/org/steam5/security/AuthRateLimitFilter.java`

```java

@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    // Caffeine cache: per-IP counter, expires 1 minute after first request in the window
    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only applies to /api/auth/**; skips OPTIONS (CORS preflight)
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(...) {
        final String ip = request.getRemoteAddr();  // resolved correctly by forward-headers-strategy=framework
        final AtomicInteger count = requestCounts.get(ip, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

Uses Caffeine (already on the classpath) — no new dependency. The cache is bounded to 10 000 entries to prevent
unbounded memory growth during a distributed IP flood.

### 9.3 Admin Token Filter

**File:** `backend/src/main/java/org/steam5/security/AdminTokenFilter.java`

```java

@Override
protected void doFilterInternal(...) {
    if (!StringUtils.hasText(expectedToken)) {
        response.sendError(SC_UNAUTHORIZED, "Unauthorized");
        return;
    }

    final String providedToken = request.getHeader("X-Admin-Token");

    // Constant-time comparison prevents timing-oracle attacks that could reveal
    // the token character-by-character via response latency differences.
    if (!StringUtils.hasText(providedToken)
            || !MessageDigest.isEqual(
            expectedToken.getBytes(StandardCharsets.UTF_8),
            providedToken.getBytes(StandardCharsets.UTF_8))) {
        response.sendError(SC_UNAUTHORIZED, "Unauthorized");
        return;
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new UsernamePasswordAuthenticationToken(
            "admin-token", null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    ));
    SecurityContextHolder.setContext(context);
    filterChain.doFilter(request, response);
}
```

---

## 10. Frontend Auth State Management

### 10.1 Server-Side Initial Auth Resolution

**File:** `frontend/app/layout.tsx`

Every full-page render performs a **server-side** auth check before HTML is sent to the browser:

```typescript
async function resolveAuth() {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return {isSignedIn: false};

    // JWT sent in Authorization header — not as a URL query parameter (fixes log exposure)
    // cache: 'no-store' — never serve a stale positive result after logout
    const res = await fetch(
        `${BACKEND_ORIGIN}/api/auth/validate`,
        {
            headers: {'accept': 'application/json', 'authorization': `Bearer ${token}`},
            cache: 'no-store',
        }
    );
    if (!res.ok) return {isSignedIn: false};

    const data = await res.json();
    return {isSignedIn: Boolean(data?.valid), steamId: data?.steamId || null};
}
```

`cache: 'no-store'` replaced the previous `next: { revalidate: 60 }`. The old cache meant a logout could leave the
server-rendered page showing the user as authenticated for up to 60 seconds on the next load. With `no-store`, the
validation is always fresh.

### 10.2 React AuthContext

**File:** `frontend/src/contexts/AuthContext.tsx`

```typescript
export function AuthProvider({children, initialAuth}) {
    const [auth, setAuth] = useState<AuthState>(initialAuth);

    const {data, mutate} = useSWR<AuthState>('/api/auth/me', authFetcher, {
        fallbackData: initialAuth,    // no loading flash — use server-provided initial state
        revalidateOnFocus: false,
        dedupingInterval: 30000,      // deduplicate requests within 30s
        errorRetryCount: 2,
    });

    useEffect(() => {
        if (data) setAuth(data);
    }, [data]);

    const refreshAuth = useCallback(() => void mutate(), [mutate]);

    return (
        <AuthContext.Provider value = {
    { ...
        auth, refreshAuth
    }
}>
    {
        children
    }
    </AuthContext.Provider>
)
    ;
}
```

Exported hooks:

```typescript
export function useAuth(): AuthContextType          // full state + refreshAuth()
export function useAuthSignedIn(): boolean | null   // true/false/null(loading)
```

### 10.3 Steam Login Button & CSRF State

**File:** `frontend/src/components/SteamLoginButton.tsx`

```typescript
const onClick = () => {
    // Generate a random CSRF nonce and store it in a short-lived cookie.
    // The nonce flows: button cookie → login URL param → return_to URL → Steam redirect →
    // callback URL param → verified against cookie → cleared.
    const state = crypto.randomUUID().replace(/-/g, '');
    const secure = location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `s5_state=${state}; path=/; max-age=300; samesite=lax${secure}`;
    window.location.href = `${buildSteamLoginUrl()}&state=${encodeURIComponent(state)}`;
};
```

`crypto.randomUUID()` produces a 122-bit cryptographically random value. The hyphens are stripped to produce a 32-char
alphanumeric string, which matches `SAFE_STATE_PATTERN` on the backend.

On successful login, `clearAll()` is called to wipe any `localStorage` game state from anonymous sessions:

```typescript
useEffect(() => {
    if (isSignedIn && !clearedRef.current) {
        clearedRef.current = true;
        try {
            clearAll();
        } catch { /* ignore */
        }
    }
}, [isSignedIn]);
```

---

## 11. Next.js API Route Layer

The Next.js API routes act as a **thin proxy/adapter** between the browser and the Spring Boot backend:

- The JWT lives in an HttpOnly cookie — JavaScript can't read it.
- The Next.js server can read cookies and forward them as `Authorization: Bearer` headers.
- Auth-critical logic (state verification, cookie setting/clearing) runs server-side.

### `GET /api/auth/steam/callback`

**File:** `frontend/app/api/auth/steam/callback/route.ts`

```typescript
export async function GET(req: NextRequest) {
    const url = new URL(req.url);
    const base = resolveBase(req);   // handles X-Forwarded-Host from CDN

    // 1. CSRF state verification
    const stateFromUrl = url.searchParams.get('state');
    const stateFromCookie = req.cookies.get('s5_state')?.value;
    if (stateFromCookie) {
        if (!stateFromUrl || stateFromUrl !== stateFromCookie) {
            const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=csrf_error', base));
            clearStateCookie(resp, base);
            return resp;
        }
    }

    // 2. Proxy all openid.* params to Spring Boot backend for verification
    const qs = url.searchParams.toString();
    const res = await fetch(`${BACKEND_ORIGIN}/api/auth/steam/callback?${qs}`, {
        headers: {accept: 'application/json'}
    });

    if (!res.ok) { /* redirect to auth=failed */
    }

    // 3. Set session cookie + clear CSRF state cookie in same response
    const data = await res.json() as { steamId: string; token: string };
    const resp = NextResponse.redirect(new URL('/review-guesser/1?auth=ok', base));
    resp.cookies.set('s5_token', data.token, {
        httpOnly: true, sameSite: 'lax', secure: base.startsWith('https'),
        path: '/', maxAge: 60 * 60 * 24 * 30,
    });
    clearStateCookie(resp, base);
    return resp;
}
```

### `GET /api/auth/me`

**File:** `frontend/app/api/auth/me/route.ts`

```typescript
export async function GET() {
    const token = (await cookies()).get('s5_token')?.value;
    if (!token) return NextResponse.json({signedIn: false});

    // JWT in Authorization header — not in the URL
    const res = await fetch(`${BACKEND_ORIGIN}/api/auth/validate`, {
        headers: {authorization: `Bearer ${token}`},
        cache: 'no-store',
    });
    const data = await res.json();
    return NextResponse.json({signedIn: Boolean(data.valid), steamId: data.steamId});
}
```

### `GET /api/auth/logout`

**File:** `frontend/app/api/auth/logout/route.ts`

```typescript
export async function GET(req: NextRequest) {
    const resp = NextResponse.redirect(new URL('/review-guesser/1', base));
    // Broad sweep for Chrome/Firefox; Safari falls back to the explicit Set-Cookie below.
    resp.headers.set('Clear-Site-Data', '"cookies"');
    resp.cookies.set('s5_token', '', {
        httpOnly: true, sameSite: 'lax', secure: base.startsWith('https'),
        path: '/', maxAge: 0,
    });
    return resp;
}
```

---

## 12. Token Usage in Authenticated API Requests

The browser never sends the JWT directly. The Next.js server reads it from the HttpOnly cookie and forwards it as a
standard `Authorization: Bearer` header.

### Server Actions

**File:** `frontend/app/review-guesser/[round]/actions.ts`

```typescript
'use server';

export async function submitGuessAction(_prev, formData: FormData) {
    const token = (await cookies()).get('s5_token')?.value;

    const url = token
        ? `${backend}/api/review-game/guess-auth`  // authenticated: saved to history
        : `${backend}/api/review-game/guess`;       // anonymous: not persisted

    const headers: Record<string, string> = {'content-type': 'application/json'};
    if (token) headers['authorization'] = `Bearer ${token}`;

    const res = await fetch(url, {method: 'POST', headers, body: JSON.stringify(...)});
}
```

### Server-Side Data Fetching

```typescript
// e.g. frontend/app/api/review-game/my/today/route.ts
const token = (await cookies()).get('s5_token')?.value;
const res = await fetch(`${BACKEND_ORIGIN}/api/review-game/my/today`, {
    headers: {authorization: `Bearer ${token}`},
    cache: 'no-store',
});
```

---

## 13. Logout Flow

```mermaid
sequenceDiagram
    actor User
    participant FE as Next.js Frontend
    participant Browser
    User ->> FE: Click logout link (href="/api/auth/logout")
    FE ->> FE: GET /api/auth/logout
    FE ->> Browser: Clear-Site-Data: "cookies" + Set-Cookie: s5_token=""; maxAge=0
    FE-->>User: 302 Redirect → /review-guesser/1
Browser->>Browser: Delete all origin cookies (or just s5_token on Safari)
Browser->>FE: GET /review-guesser/1 (no s5_token cookie)
FE->>FE: resolveAuth() → isSignedIn: false (cache: no-store)
FE-->>User: Page rendered as unauthenticated
```

`AuthLogoutLink` uses a plain `<a>` tag (not Next.js `<Link>`) to avoid prefetching the logout route.

---

## 14. Admin Authentication

Admin authentication is **completely separate** from user authentication and does not use Steam or JWTs.

```
Request: GET /api/admin/seasons/backfill
Header:  X-Admin-Token: {ADMIN_API_TOKEN}
```

The `AdminTokenFilter`:

1. Only activates for `/api/admin/**` paths.
2. Compares the header value to `admin.api-token` using `MessageDigest.isEqual()` (constant-time).
3. If matched: injects `ROLE_ADMIN` into the `SecurityContext`.
4. If not matched (or token unconfigured): returns `401 Unauthorized`.

```bash
curl -H "X-Admin-Token: my-secret-admin-token" \
     https://steam5.org/api/admin/seasons/backfill
```

---

## 15. Security Properties Summary

| Property                           | Implementation                                                 | Threat Mitigated                                                |
|------------------------------------|----------------------------------------------------------------|-----------------------------------------------------------------|
| No passwords stored                | Steam OpenID — identity delegated to Steam                     | Credential database breaches                                    |
| HttpOnly cookie                    | `httpOnly: true` on `s5_token`                                 | XSS cannot steal the token via `document.cookie`                |
| SameSite=Lax                       | `sameSite: 'lax'` on `s5_token`                                | CSRF — cross-site sub-resource requests don't carry the cookie  |
| HTTPS-only cookie                  | `secure: true` in production                                   | Man-in-the-middle interception                                  |
| HMAC signature on JWT              | `Keys.hmacShaKeyFor(secret)` + `signWith(key)`                 | Token forgery without the secret                                |
| JWT expiry                         | `exp` claim, 30 days                                           | Stolen tokens expire automatically                              |
| Open-redirect prevention           | `isAllowedRedirect()` validates scheme + host + port           | Redirecting users to attacker-controlled sites after Steam auth |
| SSRF prevention                    | `OPENID_ENDPOINT` constant, never from request params          | Backend proxied to attacker server for `check_authentication`   |
| Login CSRF state nonce             | `s5_state` cookie + `state` URL param, verified in callback    | Login CSRF (tricking user into authenticating as attacker)      |
| Rate limiting                      | `AuthRateLimitFilter` — 60 req/min/IP on `/api/auth/**`        | Token enumeration, DoS amplification on validate endpoint       |
| Constant-time token compare        | `MessageDigest.isEqual()` in `AdminTokenFilter`                | Timing oracle on admin token                                    |
| JWT not in URL                     | `Authorization: Bearer` header on `/api/auth/validate`         | Token in server logs / browser history / Referer headers        |
| Secret strength enforcement        | Startup `IllegalStateException` if `auth.jwtSecret` < 32 chars | Weak/default secret in production                               |
| OpenID assertion replay prevention | `check_authentication` POST to Steam with all params           | Replaying old OpenID assertions                                 |
| `followRedirects(NEVER)`           | Java `HttpClient` config in callback                           | Redirect-based attacks during OpenID verification               |

**Remaining limitations** (known, accepted):

- **No token revocation** — a stolen JWT remains valid until its 30-day expiry. Mitigation: short expiry relative to
  most session systems; add a Redis blocklist if revocation is needed.
- **No token refresh** — users re-authenticate after 30 days (acceptable for a casual gaming app).
- **Rate limiter is in-process** — a distributed load-balanced deployment needs a shared counter (Redis). The current
  Caffeine implementation is per-instance.

---

## 16. Testing & Verification

### Manual curl walkthrough

**Start the login flow:**

```bash
curl -v "http://localhost:8080/api/auth/steam/login?redirect=http://localhost:3000/api/auth/steam/callback&state=teststate12345678" 2>&1 | grep Location
# → Location: https://steamcommunity.com/openid/login?...&openid.return_to=...%3Fstate%3Dteststate12345678...
```

**Validate an existing token (Authorization header, not query param):**

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9...."

curl -s -H "Authorization: Bearer ${TOKEN}" \
     http://localhost:8080/api/auth/validate | jq .
# → { "valid": true, "steamId": "76561198012345678" }

curl -s -H "Authorization: Bearer bad.token.here" \
     http://localhost:8080/api/auth/validate | jq .
# → { "valid": false }  (HTTP 401)

# No header → 401
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/validate
# → 401
```

**Check auth state from the Next.js layer:**

```bash
curl -s -H "Cookie: s5_token=${TOKEN}" http://localhost:3000/api/auth/me | jq .
# → { "signedIn": true, "steamId": "76561198012345678" }
```

**Test rate limiting:**

```bash
for i in $(seq 1 65); do
  curl -s -o /dev/null -w "%{http_code}\n" \
       -H "Authorization: Bearer ${TOKEN}" \
       http://localhost:8080/api/auth/validate
done
# First 60 → 200 (or 401 for invalid token)
# Requests 61+ → 429
```

**Test logout:**

```bash
curl -v -H "Cookie: s5_token=${TOKEN}" http://localhost:3000/api/auth/logout 2>&1 \
  | grep -E "Location|Set-Cookie"
# → Set-Cookie: s5_token=; Max-Age=0; Path=/; HttpOnly; SameSite=Lax
# → Location: http://localhost:3000/review-guesser/1
```

**Test admin endpoint:**

```bash
# Without token → 401
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/admin/seasons/backfill
# → 401

# With correct token
curl -s -w "%{http_code}" \
     -H "X-Admin-Token: your-admin-token" \
     http://localhost:8080/api/admin/seasons/backfill
```

**Test open-redirect is blocked:**

```bash
# Untrusted redirect → silently falls back to default callback
curl -v "http://localhost:8080/api/auth/steam/login?redirect=https://evil.example/harvest" 2>&1 \
  | grep Location
# → Location: https://steamcommunity.com/openid/login?...&openid.return_to=http%3A%2F%2Flocalhost%3A3000%2Fapi%2Fauth%2Fsteam%2Fcallback...
# Note: return_to is the default, NOT evil.example
```

### Inspecting a JWT manually

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3NjU2MTE5ODAx..."

# Decode the payload (middle segment)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .
# → { "sub": "76561198012345678", "iat": 1712880000, "exp": 1715472000 }

# Check expiry
EXP=$(echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .exp)
echo "Expires: $(date -d @$EXP)"
```

> Decoding the payload locally does **not** validate the signature. Only the backend's `AuthTokenService.verifyToken()`
> can do that.
