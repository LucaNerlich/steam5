# Changelog

All notable changes to this project will be documented in this file.

## [1.9.4] - 2026-06-16

### Fixed

- The daily round and leaderboards no longer get stuck showing yesterday's puzzle after the midnight (UTC) rollover — "today" is now resolved consistently in UTC and cached per day, so a stale round can no longer be served once a new day begins

## [1.9.3] - 2026-06-16

### Changed

- Removed two unused backend dependencies (`spring-boot-starter-data-rest` and `spring-boot-starter-security-oauth2-client`), reducing classpath size and eliminating unnecessary autoconfiguration

### Fixed

- Backend unit tests for `AuthController` now compile and pass after the controller gained a `UserRepository` constructor parameter

## [1.9.2] - 2026-06-16

### Fixed

- An already-answered round no longer briefly flashes the guess form before swapping to your result — the round now shows a placeholder until it knows whether you've answered, then settles directly on the result or the guess form
- For signed-in players, the result shown for a round now reflects what was actually saved to your account, so a guess made while logged out no longer hides the guess form after you sign in

## [1.9.1] - 2026-06-16

### Fixed

- The daily game no longer shows the first visitors after midnight yesterday's round — a stale round now refreshes itself to today's puzzle automatically, and guessing is paused until it does, so submissions no longer fail with an error

## [1.9.0] - 2026-06-15

### Added

- The site header now shows your Steam avatar next to "Profile" once you're signed in

## [1.8.3] - 2026-06-14

### Fixed

- Revisiting an already-answered round no longer causes the page to jump as the result replaces the guess card — the area now reserves a consistent height across mobile, tablet, and desktop

## [1.8.2] - 2026-06-14

### Added

- Automated weekly Dependabot dependency-update PRs for the backend (Gradle/Spring Boot)

## [1.8.1] - 2026-06-14

### Changed

- Round-to-round navigation is now instant — each round is served as a cached, prefetchable page and the next round preloads when you hover a guess button, so switching rounds no longer flashes a loading skeleton
- Shared game/round data is cached for faster loads, while per-user data (your sign-in state and guesses) is never cached

## [1.8.0] - 2026-06-14

### Added

- The sign-in reminder now appears before every round (previously only the first), with an "Ignore this warning" option that permanently dismisses it

### Fixed

- Players whose session ended mid-game (for example after a deployment dropped their login) were silently switched to anonymous play while the header still showed them as signed in, so their guesses stopped counting toward the leaderboard without any indication — the game now detects this, updates the header, and shows a clear notice that you've been signed out and the result was not saved

### Changed

- Sign-in status is now re-checked when you return to the tab or move between rounds, so the header reflects your real session instead of a stale logged-in state
- Added a frontend unit-test suite (Vitest) covering scoring, formatting, storage, and authentication logic

## [1.7.13] - 2026-06-12

### Changed

- Upgraded Spring Boot from 4.0.5 to 4.1.0

## [1.7.12] - 2026-06-12

### Fixed

- Fixed dark mode being lost on leaderboard page refresh — theme preference is now stored in a cookie so the server renders the correct `data-theme` on the initial HTML, preventing a flash of light mode on fully SSR'd pages

## [1.7.11] - 2026-06-08

### Changed

- Visual polish pass: frosted-glass backdrop blur on the fixed header, animated shimmer on skeleton loaders, satisfying press/hover states on guess buttons, layered card shadows, leaderboard row hover highlight, subtle gradient depth on genre pills, and improved hover lift on footer icon buttons

### Fixed

- Removed the persistent right-side gutter that `scrollbar-gutter: stable` added to all pages
- Fixed horizontal layout shift (wiggle) on leaderboard pages when the scrollbar appears or disappears as data loads
- Fixed horizontal layout shift when opening the screenshot lightbox — the fixed header now correctly compensates for the scrollbar disappearing via FancyBox's `--f-scrollbar-compensate` variable
- Fixed missing top gap between the game hero section and the navigation bar

## [1.7.10] - 2026-06-07

### Fixed

- Footer version display simplified to a single `vX.Y.Z` — the separate "Frontend / Backend" labels were redundant since both are always the same version

## [1.7.9] - 2026-06-07

### Changed

- The season countdown in the footer ("X days left in season") now links directly to the current season's detail page

## [1.7.8] - 2026-06-07

### Fixed

- Docker build no longer breaks when the backend version is bumped — the Dockerfile now copies the jar by wildcard and runs it as `app.jar`, so `Dockerfile.coolify` never needs touching on future releases

## [1.7.7] - 2026-06-07

### Changed

- Footer now shows the current frontend and backend version numbers
- Internal refactors with no behaviour change: backend origin constant centralised in one utility; `PerformanceSection` interface made honest (accepts pre-flattened rounds); `RoundShareSummary` pass-through removed; Steam OpenID helpers extracted to a testable utility class; effective-response fallback logic extracted to a pure function

## [1.7.6] - 2026-06-07

### Changed

- Internal refactors with no behaviour change: `RoundResult` / `StoredDay` types are now imported from their canonical location rather than redeclared in two components; award-metric formatting and placement-tier logic each consolidated to a single shared utility; `ordinal` moved to `lib/format`; UTC "today" anchor centralised in `GameDate`; `SeasonController` view-building extracted into a dedicated `SeasonResponseMapper`

## [1.7.5] - 2026-06-07

### Changed

- Achievements table now uses the same visual style as the regular leaderboard — consistent row layout, avatar size, name link treatment, and horizontal scroll behaviour

## [1.7.4] - 2026-06-07

### Changed

- Internal refactors with no behaviour change: bucket-label parsing, guess-stat aggregation, and streak calculation each centralised in one domain class; achievement-awarding loop in `StatisticsService` collapsed from seven duplicated blocks to a single driven pattern; `LeaderboardTable` achievements section extracted into a standalone `AchievementsTable` component backed by a shared `lib/achievements.ts` utility

## [1.7.3] - 2026-06-07

### Fixed

- Guesses from a previous set of daily rounds no longer appear on the new games when the day's picks are regenerated — submitted results are now matched to the current pick both in the browser and at the `/my/today` API, instead of being keyed only by round number

### Changed

- Internal refactors for maintainability, with no change to behaviour: authentication-token extraction is now handled by a single resolver (`@CurrentUser`), cache eviction is centralised in one module, daily-pick generation is split into focused steps, the round scoring formula is shared rather than duplicated, and arrow-key round navigation moved into a reusable hook

## [1.7.2] - 2026-06-07

### Fixed

- Leaderboard pages now render fully server-side with data baked into the HTML — the loading skeleton no longer appears on initial page load
- Frontend SWR poll intervals and Next.js API route cache TTLs are now aligned with backend Caffeine cache TTLs (60 s for today, 10 min for weekly/season/all-time), eliminating redundant backend requests
- All-time and monthly leaderboard API routes were incorrectly bypassing the Next.js cache (`force-dynamic`); they now use a 10-minute ISR TTL matching the backend
- Profile page was missing a top gap between the fixed header and the page content

## [1.7.1] - 2026-06-02

### Fixed

- Stale daily rounds are no longer served from caches — live round endpoints now use shorter, revalidating cache lifetimes, and logging in or out explicitly refreshes the cached round data
- Per-user responses (your guesses and token validation) can no longer be stored by shared/CDN caches, so one player's data is never served to another
- Conditional `304 Not Modified` responses are no longer cached and replayed to requests that did not send a matching ETag

### Changed

- Upgraded Next.js
- Hardened npm against supply-chain attacks via `.npmrc` settings

## [1.7.0] - 2026-05-16

### Added

- Host metrics via a `node-exporter` sidecar in the monitoring stack — CPU, memory, load, disk usage/IO, network, file descriptors
- Container metrics via a `cAdvisor` sidecar — per-container CPU/memory/network/filesystem IO, restarts, OOM events
- PostgreSQL metrics via a `postgres-exporter` sidecar — connections vs `max_connections`, transactions, deadlocks, cache hit ratio, rows fetched/inserted/updated/deleted, database size, longest-running query
- Domain instrumentation in the backend — Steam API request rate and latency by endpoint/outcome, dedicated 429 rate-limit counter, rate-limiter wait time, ingestion coverage and freshness gauges, daily-pick generation success/size, authenticated-guess counter by correct/incorrect
- Four new Grafana dashboards: `Steam5 — Infra (host)`, `Steam5 — Infra (containers)`, `Steam5 — PostgreSQL`, `Steam5 — Domain`
- Prometheus alert rules in `monitoring/prometheus/alert_rules.yml` covering backend down, 5xx rate, Quartz job failures, daily-picks missing, HikariCP saturation, Steam API rate-limiting, host disk/memory, Postgres connections and deadlocks (visible in Prometheus UI; no Alertmanager wired up yet)

### Changed

- JVM dashboard now shows process CPU usage, process uptime, and open vs max file descriptors
- HTTP-server dashboard now shows scrape-target up status, 5xx rate, 5xx ratio, and a 5xx-by-URI table
- HikariCP dashboard now shows average connection creation time and max acquire/usage/creation times
- Prometheus config template now declares `rule_files` and additional scrape jobs (`node`, `cadvisor`, `postgres`)
- Monitoring README documents the new exporters, alert rules table, env vars, and a Postgres exporter setup section with the `pg_monitor` SQL snippet

## [1.6.0] - 2026-04-20

### Added
- Enabled Umami session replay by default via `recorder.js` with `data-sample-rate`, `data-mask-level`, and `data-max-duration` attributes
- Support overriding the Umami base URL via `NEXT_PUBLIC_UMAMI_URL`; session replay tunables via `NEXT_PUBLIC_UMAMI_SAMPLE_RATE`, `NEXT_PUBLIC_UMAMI_MASK_LEVEL`, and `NEXT_PUBLIC_UMAMI_MAX_DURATION`

## [1.5.1] - 2026-04-14

### Added

- Flops column in the season leaderboard table, showing zero-point rounds per player

## [1.4.1] - 2026-04-13

### Fixed

- Average points per round position chart on the profile page no longer starts at R2; fixed off-by-one error where the
  already 1-based `roundIndex` was incorrectly incremented again in the frontend
- Best & worst bucket callout and accuracy-by-bucket bar chart on the profile page are now fully responsive; replaced
  fixed-size SVG layouts with native HTML/CSS flexbox that reflows correctly on narrow viewports
- Improvement trend chart now shows concrete context: subtitle with round counts ("First 10 vs last 10 of 30 rounds"),
  specific "First N" / "Last N" dot labels, and date ranges beneath each data point

### Improved

- Play activity heatmap legend now reads "Avg pts/round: 0 … 5" instead of the vague "Less / More"; added a subtitle
  explaining color intensity and native hover tooltips showing the exact date and average on each cell

### Removed

- Perfect days card removed from the profile performance section (leaderboard achievement is unaffected)

## [1.4.0] - 2026-04-13

### Added

- `/llms.txt` route following the [llmstxt.org](https://llmstxt.org/) proposal, helping LLMs understand the site

## [1.3.4] - 2026-04-13

### Fixed

- Profile page no longer overflows horizontally on mobile; added proper overflow containment to CSS grid containers, performance chart cards, and award grid

## [1.3.3] - 2026-04-12

### Fixed

- Removed dead profile link from the "Profile Stats Expanded" news post

## [1.3.2] - 2026-04-12

### Fixed

- Best & worst bucket callout now requires at least 20 rounds per bucket, filtering out price ranges from the original
  bucket setup that lack sufficient data for a meaningful comparison

## [1.3.1] - 2026-04-12

### Fixed

- "Points by round" chart now correctly shows individual dots for each round within a day; previously R1/R2/R3 on the
  same date were drawn on top of each other and appeared as a single point
- "Recent performance" heading now sits outside the card grid, matching the visual style of the "Season awards" section

### Added

- News post announcing the seven new profile performance charts

## [1.3.0] - 2026-04-12

### Added

- Calendar heatmap showing 365 days of play activity with color intensity by average points earned
- Round position chart showing average points scored per round position (R1, R2, R3)
- Day-of-week chart showing average points by day of the week (Mon–Sun)
- Improvement trend card comparing early vs recent round averages with a directional indicator
- Perfect days counter showing how many days all rounds scored maximum points, with a circular progress ring
- Guess bias badge summarising whether the player tends to overshoot or undershoot the actual price range, with a
  stacked breakdown bar
- Best and worst bucket callout highlighting the price ranges with the highest and lowest hit rates

## [1.2.5] - 2026-04-12

### Fixed

- Outcome mix chart (Too High / Too Low) now compares bucket labels numerically instead of lexicographically, fixing
  misclassification for high-count buckets like "2001–10000" vs "10001–50000"
- Current daily streak now correctly resets to 0 when the player's last play date is more than one day ago; previously
  it showed the last trailing run regardless of how long ago it ended

### Changed

- Points-by-round chart x-axis now uses proportional calendar positioning so gaps in play days are visible as gaps in
  the chart; date labels show real dates (e.g. "Apr 1 → Apr 15 → Apr 30") instead of "older / mid / newer"
- Hit-rate comparison card label updated to "All players (all‑time)" to accurately reflect that the global baseline uses
  all-time data while the user's bar shows only the last 30 days

## [1.2.4] - 2026-04-12

### Fixed

- Fixed invalid heading hierarchy in the game-info section: "Technical Info" was incorrectly marked as `<h2>` (same
  level as the section title); changed to `<h3>`

## [1.2.3] - 2026-04-12

### Fixed

- `POST /api/admin/seasons/{id}/finalize` now returns HTTP 409 Conflict if the season is already finalized, preventing
  accidental re-finalization that would delete and recreate all awards
- `AdminTokenFilter` now logs a startup warning when `ADMIN_API_TOKEN` is not configured (mirrors the JWT secret
  validation pattern)
- Steam login callback now validates that the backend response contains both `steamId` and `token` before setting the
  session cookie; a malformed response now redirects to an error page instead of silently setting the cookie to
  `"undefined"`
- Public profile endpoint (`/api/profile/{steamId}`) now caps the `days` history at the most recent 365 days, preventing
  unbounded responses for long-tenured players

## [1.2.2] - 2026-04-12

### Fixed

- `GET /api/review-game/my/history` no longer loads the entire guesses table into memory; now queries only the
  requesting user's rows via a dedicated indexed query
- `GET /api/review-game/my/history` returns HTTP 400 instead of 500 when `from`/`to` query parameters contain an invalid
  date format
- All-time leaderboard streak now shows the correct active streak for users who have not yet played today (one-day
  grace: yesterday counts as today for streak continuity)
- Removed unreachable code path in `SeasonFinalizerJob` that could never fire because `ensureSeasonForDate` guarantees
  the current season covers today

## [1.2.1] - 2026-04-12

### Changed

- Logout response now includes `Clear-Site-Data: "cookies"` header for a broad, spec-compliant cookie sweep on Chrome
  and Firefox; the explicit `Set-Cookie: s5_token=; maxAge=0` is kept as a fallback for Safari

## [1.2.0] - 2026-04-12

### Security

- Fixed SSRF vulnerability: OpenID assertion verification now always uses the hardcoded Steam endpoint, ignoring any
  attacker-supplied `openid.op_endpoint` parameter
- Fixed open redirect: the `redirect` parameter on the login endpoint is validated against the trusted frontend origin
  before use
- Added login-CSRF protection via a random state nonce (`s5_state` cookie) generated by the login button and verified in
  the callback
- JWT tokens are now passed via `Authorization: Bearer` header instead of URL query parameters, preventing token
  exposure in server access logs and browser history
- Admin token comparison is now constant-time (`MessageDigest.isEqual`) to prevent timing-oracle attacks
- Added per-IP rate limiting (60 req/min) on all `/api/auth/**` endpoints via `AuthRateLimitFilter`
- JWT secret is validated at startup: the application refuses to start if the secret is shorter than 32 characters, and
  logs a warning if the well-known default value is detected

### Changed

- User profile enrichment (Steam `GetPlayerSummaries` API call) now runs asynchronously, removing it from the login
  response critical path
- `SteamUserService` now uses the Spring-managed `ObjectMapper` bean instead of a bare `new ObjectMapper()` instance
- `HttpClient` for OpenID verification is now a reused static field instead of being constructed on every login callback
  request
- `AUTH_JWT_SECRET` environment variable documented in `.env.example` and wired into `application.yml`

### Fixed

- Pinned explicit versions for `caffeine`, `jjwt`, and `springboot4-dotenv` dependencies in `build.gradle.kts`
- Removed unreachable `createdAt` null-check in `SteamUserService` and redundant field initializer in `User` entity

### Documentation

- Added comprehensive `AUTHENTICATION_FLOW.md` covering the full Steam OpenID 2.0 flow, JWT internals, cookie mechanics,
  CSRF protection, rate limiting, and verification examples with Mermaid sequence diagrams

## [1.1.1] - 2026-04-11

### Changed

- Updated Spring Boot to 4.0.5
- Removed hardcoded dependency versions in favor of Spring Boot BOM management

## [1.1.0] - 2026-04-11

### Added

- Monthly archive endpoint and frontend integration for game picks
- Leaderboard layout with dynamic headers and toggle navigation
- Keyboard navigation for round switching (arrow keys)
- Scroll to top on round change
- View transitions for page navigation
- Enhanced scoring display with round points and result dialogs
- Improved guess submission UI with helper text
- DataSourcePoolLoggingConfig for HikariCP connection pool logging
- Database query performance improvements and repository indexing

### Fixed

- Auth login modal no longer fires on every round for unauthenticated users (only round 1)
- Backend and frontend stability fixes
- Dotenv dependency in Gradle build
- Previous round link condition in result actions
- Privacy Policy content updated for clarity and compliance

### Changed

- Upgraded to Gradle 9
- Improved leaderboard and round result component layouts and responsiveness
- Streamlined and simplified scoring logic
- Updated database connection pool settings
