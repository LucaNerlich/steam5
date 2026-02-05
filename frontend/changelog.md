# Changelog

Full project history (chronological, grouped by date).

## 2025-08-10 (v0.0.0+2025-08-10)
- 2025-08-10 a93b2f3 Initial commit - Areas: docs; misc; repo config. Key files: LICENSE, README.md. Stats: 3 files, +699/-0.
- 2025-08-10 4416a2a initial backend commit - Areas: backend build/deps; misc; backend config. Key files: backend/gradlew, backend/.gitignore. Stats: 11 files, +479/-0.
- 2025-08-10 6af201b Remove old application properties and add new configuration files. - Areas: IDE config; backend config; backend build/deps. Key files: backend/build.gradle.kts, backend/src/main/resources/application.yml. Stats: 12 files, +182/-6.
- 2025-08-10 d7eca19 start with README.md - Areas: IDE config; db migrations/scripts; docs. Key files: README.md, .vscode/settings.json. Stats: 5 files, +168/-1.
- 2025-08-10 e9de162 start with README.md - Areas: docs. Key files: README.md. Stats: 1 files, +1/-2.
- 2025-08-10 47faa7b - Remove unused configuration files. - Update database connection details. - Add initial schema for Steam apps. - Refactor application structure. - Configure IDEA modules and settings. - Areas: IDE config; misc; backend config. Key files: README.md, backend/.gitignore. Stats: 13 files, +50/-7.
- 2025-08-10 2aae514 Add domain models, repositories, job scheduling, and initial API configuration for Steam app ingestion. - Areas: backend domain models; backend jobs; backend repositories. Key files: backend/src/main/resources/db/migration/V2__add_ingest_state_table.sql, backend/src/main/java/org/steam5/domain/IngestState.java. Stats: 10 files, +348/-0.
- 2025-08-10 a78b272 Removed initial schema files and disabled Flyway. Switched Hibernate DDL to "update" for automatic database structure changes. - Areas: IDE config; db migrations/scripts; backend config. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/db/migration/V1__create_initial_schema.sql. Stats: 9 files, +23/-25.
- 2025-08-10 e2591ec remove flyway - Areas: backend config; backend build/deps; db migrations/scripts. Key files: .vscode/settings.json, backend/build.gradle.kts. Stats: 5 files, +4/-14.
- 2025-08-10 03e70ce load steam appid review counts - Areas: backend jobs; backend repositories; backend config. Key files: backend/src/main/resources/application-dev.yml, backend/src/main/resources/db/steam5.http. Stats: 8 files, +244/-0.
- 2025-08-10 254d76c Introduce `SteamHttpClient` for centralized HTTP client configuration. - Areas: backend services; misc. Key files: backend/src/main/java/org/steam5/http/SteamHttpClient.java, backend/src/main/java/org/steam5/service/SteamAppListFetcher.java. Stats: 3 files, +51/-31.
## 2025-08-12 (v0.0.0+2025-08-12)
- 2025-08-12 3e65947 add example sql query - Areas: IDE config; db migrations/scripts. Key files: backend/src/main/resources/db/by-threshold.sql. Stats: 3 files, +12/-0.
- 2025-08-12 683762c all reviews - Areas: IDE config; db migrations/scripts. Key files: backend/src/main/resources/db/all-reviews.sql. Stats: 3 files, +5/-0.
- 2025-08-12 4a647e0 start with SteamAppDetail entities - Areas: backend domain models. Key files: backend/src/main/java/org/steam5/domain/details/Genre.java, backend/src/main/java/org/steam5/domain/details/Movie.java. Stats: 6 files, +197/-0.
- 2025-08-12 35df96e add repos - Areas: backend repositories; backend domain models. Key files: backend/src/main/java/org/steam5/repository/details/GenreRepository.java, backend/src/main/java/org/steam5/repository/details/MovieRepository.java. Stats: 7 files, +84/-0.
- 2025-08-12 f7e8551 start with quartz job - Areas: backend services; backend config; backend jobs. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/service/Fetcher.java. Stats: 4 files, +45/-0.
- 2025-08-12 c1dfbb9 migrate to quartz jobs - Areas: backend jobs; backend config; backend services. Key files: .gitignore, backend/src/main/resources/application.yml. Stats: 14 files, +138/-124.
- 2025-08-12 8e17eb4 trigger index job once a day - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +4/-3.
- 2025-08-12 09b9f7b extract JsonHttpClient - Areas: backend services; backend jobs; misc. Key files: backend/src/main/java/org/steam5/http/JsonHttpClient.java, backend/src/main/java/org/steam5/job/SteamAppDetailFetchJob.java. Stats: 5 files, +52/-33.
- 2025-08-12 019620a start with details job - Areas: backend jobs; backend services. Key files: backend/src/main/java/org/steam5/job/SteamAppReviewsJob.java, backend/src/main/java/org/steam5/job/SteamAppDetailFetchJob.java. Stats: 6 files, +107/-16.
- 2025-08-12 28bbf13 update readme - Areas: docs. Key files: README.md. Stats: 1 files, +14/-2.
- 2025-08-12 395a26b rename jobs - Areas: backend jobs; backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java, backend/src/main/java/org/steam5/job/{SteamAppListDownloadJob.java => SteamAppListJob.java}. Stats: 3 files, +15/-15.
- 2025-08-12 9256179 initial fe commit - Areas: misc; frontend assets; frontend config/deps. Key files: frontend/README.md, frontend/.gitignore. Stats: 23 files, +6004/-0.
- 2025-08-12 f1e4d43 empty fe project - Areas: frontend routes/pages; misc; frontend assets. Key files: frontend/.editorconfig, frontend/next.config.ts. Stats: 24 files, +276/-343.
- 2025-08-12 a7cec5d update Umami script URL - Areas: frontend components. Key files: frontend/src/components/UmamiAnalytics.tsx. Stats: 1 files, +1/-1.
- 2025-08-12 255c4ba update Umami script URL - Areas: frontend components. Key files: frontend/src/components/UmamiAnalytics.tsx. Stats: 1 files, +1/-1.
- 2025-08-12 88962b7 add dockerfile - Areas: misc. Key files: backend/Dockerfile.coolify. Stats: 1 files, +32/-0.
- 2025-08-12 1ad997e Update `application.yml` for caching and SQL initialization configurations. Introduce environment-specific configuration in `application-coolify.yml` and modify JPA settings in `application-dev.yml`. - Areas: backend config. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/application-dev.yml. Stats: 3 files, +22/-3.
- 2025-08-12 49b9960 Update configuration files: - Areas: backend config. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/application-dev.yml. Stats: 3 files, +27/-27.
- 2025-08-12 0b6c7e8 update dockerfile jar name - Areas: misc. Key files: backend/Dockerfile.coolify. Stats: 1 files, +2/-2.
- 2025-08-12 abe6c25 fix path in df - Areas: misc. Key files: backend/Dockerfile.coolify => Dockerfile.coolify. Stats: 1 files, +1/-1.
- 2025-08-12 f6670c0 Refactor logging configuration for different environments. - Areas: backend config. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/application-dev.yml. Stats: 2 files, +4/-3.
- 2025-08-12 5c9d564 allow spring profile override - Areas: backend config. Key files: backend/src/main/resources/application.yml. Stats: 1 files, +1/-1.
## 2025-08-13 (v0.0.0+2025-08-13)
- 2025-08-13 c83eb38 Review game state (#1) - Areas: backend repositories; backend domain models; backend config. Key files: backend/build.gradle.kts, backend/src/main/resources/application.yml. Stats: 33 files, +882/-61.
- 2025-08-13 0a9bead do not run review game state or detail job for now - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +14/-14.
- 2025-08-13 23ba528 **Commit Message:** - Areas: backend config; backend controllers; backend services. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 3 files, +54/-4.
- 2025-08-13 35b4ba8 ### Implement Bucket Guessing Endpoint and Dynamic Boundaries - Areas: backend config; backend controllers; backend services. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/config/ReviewGameConfig.java. Stats: 5 files, +88/-8.
- 2025-08-13 e2a6f63 Add Caffeine cache for `/buckets` endpoint and update CacheConfig - Areas: backend config; backend controllers. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 2 files, +10/-1.
- 2025-08-13 ff83f7f Add job toggles and optimize daily game pick generation - Areas: backend config; backend services. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 3 files, +42/-26.
- 2025-08-13 7cd3dee Introduce security config for request permissions and refactor domain models. - Areas: backend config; IDE config; backend domain models. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/application-dev.yml. Stats: 6 files, +41/-13.
- 2025-08-13 1508b3b Update domain models with `@JsonIgnore` for lazy-loaded relationships. - Areas: backend domain models. Key files: backend/src/main/java/org/steam5/domain/details/Genre.java, backend/src/main/java/org/steam5/domain/details/Movie.java. Stats: 5 files, +10/-0.
- 2025-08-13 7f44394 Update cache keys and expiration policies, add endpoint for metadata - Areas: backend controllers; backend config; backend repositories. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/config/SecurityConfig.java. Stats: 7 files, +45/-38.
## 2025-08-14 (v0.0.0+2025-08-14)
- 2025-08-14 9ded3e4 Remove `category` from `ReviewGamePick` and update JDBC URL in configuration - Areas: backend config; db migrations/scripts. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/db/migration/V3_remove-category-from-review-pick.sql. Stats: 2 files, +3/-1.
- 2025-08-14 0eeb2c3 Add endpoint for entity count metrics and implement caching - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.http, backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 2 files, +71/-0.
- 2025-08-14 c83a91c Introduce new metrics endpoints with coverage, reviews summary, details breakdown, and picks summary - Areas: backend repositories; backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.http, backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 6 files, +141/-1.
- 2025-08-14 a773e9c Refactor endpoints to remove redundant bucket metadata controller - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.http, backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 4 files, +11/-30.
- 2025-08-14 96546d7 Consolidate metrics endpoints into MetricsController and streamline API - Areas: backend controllers; backend config. Key files: backend/src/main/java/org/steam5/config/SecurityConfig.java, backend/src/main/java/org/steam5/web/MetricsController.http. Stats: 3 files, +22/-1.
- 2025-08-14 6ef06bf Remove outdated caching strategy from `/metrics/index` endpoint - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 1 files, +0/-1.
- 2025-08-14 c231d9c Add support for HTML index endpoint and update database URL - Areas: backend controllers; backend config. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/web/MetricsController.http. Stats: 3 files, +42/-4.
- 2025-08-14 dc5198e Refactor cache keys and expiration policies for review-game endpoints and consolidate caching strategy - Areas: backend config; backend controllers; backend services. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 3 files, +24/-5.
- 2025-08-14 fda70bf Refactor metrics endpoints and update project configuration - Areas: IDE config; backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.http, backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 4 files, +9/-5.
- 2025-08-14 b949ec0 Enable pretty JSON output in application configuration - Areas: backend config. Key files: backend/src/main/resources/application.yml. Stats: 1 files, +1/-0.
- 2025-08-14 b000910 Remove reviews summary endpoint from MetricsController and update project configuration. - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 1 files, +0/-1.
- 2025-08-14 091b68d Refactor `SteamAppDetailService` for developers, publishers, genres lookup-or-create by name/description. Update SQL migration scripts and entities with unique constraints. - Areas: backend domain models; backend repositories; IDE config. Key files: backend/src/main/resources/application-dev.yml, backend/src/main/resources/db/migration/V4_drop-details.sql. Stats: 11 files, +96/-38.
- 2025-08-14 124bc99 Add Steam App Details Endpoint and Secure Access - Areas: backend controllers; backend domain models; backend config. Key files: backend/src/main/java/org/steam5/config/SecurityConfig.java, backend/src/main/java/org/steam5/web/SteamAppDetailController.http. Stats: 5 files, +48/-0.
- 2025-08-14 a31729b Create repositories and domain classes for categories and price details - Areas: backend domain models; backend repositories. Key files: backend/src/main/java/org/steam5/domain/details/Price.java, backend/src/main/java/org/steam5/domain/details/Category.java. Stats: 6 files, +158/-27.
- 2025-08-14 dbfdf3f Refactor `SteamAppDetailController` to ensure transactional consistency - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/SteamAppDetailController.java. Stats: 1 files, +14/-6.
- 2025-08-14 a2c39f4 Update review game configuration to support dynamic percentiles - Areas: backend config; backend controllers; backend repositories. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/web/MetricsController.java. Stats: 5 files, +19/-5.
- 2025-08-14 bb8c91a Update ingest state for Steam app details reset - Areas: db migrations/scripts. Key files: backend/src/main/resources/db/reset-ingest-state.sql. Stats: 1 files, +3/-0.
- 2025-08-14 cd248fc Limit top reviews query, add index fetch, update data source mappings - Areas: IDE config; db migrations/scripts. Key files: backend/src/main/resources/db/fetch-index.sql, backend/src/main/resources/db/{all-reviews.sql => limit-reviews.sql}. Stats: 4 files, +9/-2.
- 2025-08-14 0570647 Enhance error handling and enforce consistent HTTP response processing. Introduce `SteamApiException` for precise status code tracking, modify service methods to throw exceptions on non-2xx responses, and update ingestion jobs to handle errors gracefully, including rate limiting scenarios. - Areas: backend jobs; backend services; misc. Key files: backend/src/main/java/org/steam5/job/SteamAppListJob.java, backend/src/main/java/org/steam5/http/SteamHttpClient.java. Stats: 7 files, +49/-18.
- 2025-08-14 7cf267f Enhanced SteamAppDetailController with HTML endpoint and added client config for host URLs - Areas: backend controllers; db migrations/scripts. Key files: backend/src/main/resources/db/http-client.env.json, backend/src/main/java/org/steam5/web/SteamAppDetailController.java. Stats: 2 files, +82/-2.
- 2025-08-14 af42d74 Refactor SteamAppReviewsFetcher to use fetchForAppId method and update ReviewGameStateService to fetch reviews during processing - Areas: backend services. Key files: backend/src/main/java/org/steam5/service/ReviewGameStateService.java, backend/src/main/java/org/steam5/service/SteamAppReviewsFetcher.java. Stats: 2 files, +13/-2.
- 2025-08-14 f663102 Add global exception handling - Areas: backend controllers; backend services; misc. Key files: backend/src/main/java/org/steam5/http/SteamApiException.java, backend/src/main/java/org/steam5/http/ReviewGameException.java. Stats: 7 files, +81/-42.
- 2025-08-14 e38df89 **Commit Message:** - Areas: backend repositories; backend domain models; backend services. Key files: backend/src/main/java/org/steam5/domain/ExcludedApp.java, backend/src/main/java/org/steam5/domain/DailyPickLock.java. Stats: 8 files, +191/-34.
- 2025-08-14 ef95d1c Update Quartz trigger configuration for daily execution at 00:01 AM - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +3/-1.
- 2025-08-14 27b4410 Update `ReviewGameStateController` to include bucket labels and refactor DTO - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/ReviewGameStateController.http, backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 2 files, +4/-4.
- 2025-08-14 52f7430 - Introduce theme toggle component with persistent storage - Areas: frontend styles; frontend config/deps; docs. Key files: frontend/TODOS.md, frontend/package.json. Stats: 8 files, +129/-11.
- 2025-08-14 2419c91 Add Review Game API types and routes for handling buckets, guesses, and daily reviews - Areas: frontend routes/pages; docs; frontend utilities. Key files: frontend/TODOS.md, frontend/src/types/review-game.ts. Stats: 5 files, +158/-1.
- 2025-08-14 a66d127 **Add Review Guesser Route with Server-Side Rendering** - Areas: frontend routes/pages; IDE config; docs. Key files: frontend/TODOS.md, frontend/app/page.tsx. Stats: 5 files, +44/-4.
- 2025-08-14 62f68a3 Add Footer with ThemeToggle; create Header component - Areas: frontend components; frontend styles; docs. Key files: frontend/TODOS.md, frontend/app/layout.tsx. Stats: 7 files, +83/-12.
- 2025-08-14 2666398 Refactor CSS for header and footer; remove inline styles - Areas: frontend components; frontend styles; docs. Key files: frontend/TODOS.md, frontend/src/styles/footer.css. Stats: 5 files, +36/-10.
- 2025-08-14 05f2517 ### Commit Message - Areas: frontend routes/pages; docs; frontend components. Key files: frontend/TODOS.md, frontend/src/styles/shared.css. Stats: 8 files, +149/-27.
- 2025-08-14 d4ed26c Add FontToggle component with Krypton/Neon font switching - Areas: frontend routes/pages; frontend components; frontend styles. Key files: frontend/app/layout.tsx, frontend/src/styles/globals.css. Stats: 18 files, +76/-3.
- 2025-08-14 a1a0066 Refactor Footer component styles; replace inline styles with CSS class - Areas: frontend components; frontend styles. Key files: frontend/src/styles/footer.css, frontend/src/components/Footer.tsx. Stats: 2 files, +7/-1.
- 2025-08-14 6371d15 Implement "Review Guesser" game - Areas: docs; frontend components; frontend routes/pages. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +77/-43.
- 2025-08-14 92244d0 Lock buttons on guess submission and add 'Share' button with emoji feedback - Areas: docs; frontend components; frontend config/deps. Key files: frontend/TODOS.md, frontend/next.config.ts. Stats: 4 files, +34/-4.
- 2025-08-14 4abad06 Refactor CSS and enhance Review Guesser component styling - Areas: frontend components; frontend styles. Key files: frontend/src/styles/reviewGuesserRound.css, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +27/-9.
- 2025-08-14 72bac12 Lock guess buttons on submission - Areas: docs; frontend components; frontend styles. Key files: frontend/TODOS.md, frontend/src/styles/reviewGuesserRound.css. Stats: 3 files, +26/-5.
- 2025-08-14 c985bfa Add support for sharing game results and persisting round data - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/styles/reviewGuesserRound.css, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +156/-4.
- 2025-08-14 cf3ff88 Add summary of round results with emoji-based feedback in shared output - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +7/-0.
- 2025-08-14 23c6b78 Refactor scoring logic in `ReviewGuesserRound` with linear decay formula and emoji mapping - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +21/-5.
- 2025-08-14 a83ffbf Revise points calculation display in shared results - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +2/-3.
- 2025-08-14 68a9826 Add "How to play" and scoring rules to Review Guesser round page - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/styles/reviewGuesserRound.css, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +30/-1.
- 2025-08-14 151e562 Refactor CSS for image border-radius; add share button functionality with state management - Areas: frontend styles; frontend components. Key files: frontend/src/styles/shared.css, frontend/src/styles/reviewGuesserRound.css. Stats: 3 files, +91/-28.
- 2025-08-14 e6421a7 Add ResetTodayButton to footer for clearing daily progress - Areas: frontend components; frontend styles. Key files: frontend/src/styles/footer.css, frontend/src/styles/globals.css. Stats: 4 files, +56/-5.
- 2025-08-14 8d52195 Refactor Review Guesser components and CSS structure - Areas: frontend components; frontend styles; frontend routes/pages. Key files: frontend/src/components/Footer.tsx, frontend/src/components/Header.tsx. Stats: 9 files, +49/-21.
- 2025-08-14 1211cd5 Refactor ReviewGuesserHero CSS and improve screenshot layout - Areas: frontend styles; frontend components. Key files: frontend/src/styles/shared.css, frontend/src/components/ReviewGuesserHero.tsx. Stats: 3 files, +23/-9.
- 2025-08-14 fade5b9 Display developer and publisher details in ReviewGuesserHero component - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +16/-2.
- 2025-08-14 f344ce6 Fix missing `key` props in developer/publisher lists and remove redundant ESLint comment - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +4/-5.
- 2025-08-14 158bc63 Fix incorrect URL formatting in ReviewGuesserRound shared link text - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +1/-1.
- 2025-08-14 0143d37 Center align review round items in ReviewGuesserRound CSS - Areas: frontend styles. Key files: frontend/src/styles/components/reviewGuesserRound.css. Stats: 1 files, +1/-0.
- 2025-08-14 4165e60 Add lightbox functionality for screenshots in ReviewGuesserHero - Areas: docs; frontend components; frontend styles. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserHero.tsx. Stats: 3 files, +108/-6.
- 2025-08-14 652da95 Refactor screenshot handling in ReviewGuesserHero - Areas: docs; frontend components. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserHero.tsx. Stats: 2 files, +11/-9.
- 2025-08-14 6352847 Add Steam link next to game title in ReviewGuesserHero - Areas: docs; frontend components; frontend styles. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserHero.tsx. Stats: 3 files, +26/-4.
- 2025-08-14 72fa0bc Conditionally render developer and publisher details in ReviewGuesserHero - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +12/-8.
- 2025-08-14 f724341 Add hover effect to guess buttons and update review round layout - Areas: frontend components; frontend styles. Key files: frontend/src/components/ReviewGuesserRound.tsx, frontend/src/styles/components/reviewGuesserRound.css. Stats: 2 files, +10/-2.
- 2025-08-14 4ea70a6 Enhance ReviewGuesserRound styling and add reusable CTA button - Areas: frontend styles; docs; frontend components. Key files: frontend/TODOS.md, frontend/src/styles/shared.css. Stats: 4 files, +23/-3.
- 2025-08-14 a1b8779 Add Steam link button and improve ReviewGuesserRound layout - Areas: frontend styles; frontend components; docs. Key files: frontend/TODOS.md, frontend/src/styles/shared.css. Stats: 6 files, +34/-25.
- 2025-08-14 1ee6802 Update game selection criteria and refactor percentile settings; enhance frontend to backend link clarity - Areas: backend config; backend repositories; backend services. Key files: frontend/TODOS.md, backend/src/main/resources/application.yml. Stats: 4 files, +11/-7.
- 2025-08-14 c8b2fe2 Refactor ReviewGuesserRound layout, add inline sharing, and enhance styles - Areas: frontend components; frontend styles. Key files: frontend/src/styles/shared.css, frontend/src/components/ResetTodayButton.tsx. Stats: 4 files, +66/-22.
- 2025-08-14 ecb8f86 Adjust percentile configuration and improve frontend-backend link styling. - Areas: backend config; backend repositories; backend services. Key files: frontend/src/components/ReviewGuesserRound.tsx, backend/src/main/resources/application.yml. Stats: 5 files, +49/-11.
- 2025-08-14 564c6ca update todo - Areas: docs; frontend config/deps. Key files: frontend/TODOS.md. Stats: 2 files, +7/-4.
- 2025-08-14 0b11787 Update game ID and bucket guess logic; refine review score boundary conditions in service layer. - Areas: backend controllers; backend services. Key files: backend/src/main/java/org/steam5/web/ReviewGameStateController.http, backend/src/main/java/org/steam5/service/ReviewGameStateService.java. Stats: 2 files, +10/-9.
- 2025-08-14 530aa54 ### Commit Message - Areas: docs; frontend components; frontend styles. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +107/-0.
- 2025-08-14 a98851b Improve ReviewGuesserRound styling and score summary display - Areas: frontend components; frontend styles. Key files: frontend/src/components/ReviewGuesserRound.tsx, frontend/src/styles/components/reviewGuesserRound.css. Stats: 2 files, +3/-1.
- 2025-08-14 6a8a469 Refactor and enhance `ReviewGuesserRound` and `ResetTodayButton` - Areas: frontend components; frontend styles. Key files: frontend/src/components/ResetTodayButton.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +59/-31.
- 2025-08-14 ece8982 Enhance button styles and layout responsiveness; update TODOs - Areas: frontend styles; docs. Key files: frontend/TODOS.md, frontend/src/styles/shared.css. Stats: 3 files, +20/-3.
## 2025-08-15 (v0.0.0+2025-08-15)
- 2025-08-15 8e00471 Update game data and response caching strategy; improve dynamic routing - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/[round]/page.tsx, frontend/app/api/review-game/today/route.ts. Stats: 2 files, +12/-5.
- 2025-08-15 bcb3e51 - Update `TODOS.md` to prevent selection of recently released games. - Enhance genre display with 'pills' in frontend UI. - Refactor and improve layout and styling in `ReviewGuesserHero`. - Add responsive CSS for meta information and genres. - Areas: frontend components; docs; frontend styles. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserHero.tsx. Stats: 4 files, +56/-17.
- 2025-08-15 9aa9128 Refactor frontend components: Enhance layout, add inline sharing, and update styling - Areas: frontend components; frontend styles; docs. Key files: frontend/TODOS.md, frontend/src/components/ReviewRules.tsx. Stats: 16 files, +503/-383.
- 2025-08-15 c8b339a Add release date display with localized formatting in `ReviewGuesserHero` - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +9/-0.
- 2025-08-15 1d2b478 Improve clipboard functionality and button styling in `ShareControls` - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +29/-5.
- 2025-08-15 ee17618 Refactor share controls to enhance summary display and improve URLs - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +4/-1.
- 2025-08-15 b2e78e4 Add OpenGraph image generation and improve date formatting - Areas: frontend routes/pages; docs. Key files: frontend/TODOS.md, frontend/app/robots.ts. Stats: 4 files, +46/-12.
- 2025-08-15 fb9d581 Refactor `next.config.ts` to add redirect from `/` to `/review-guesser`, toggle app details feature, and comment out "Play Review Guesser" link - Areas: backend config; frontend components; frontend config/deps. Key files: frontend/next.config.ts, frontend/src/components/Header.tsx. Stats: 3 files, +12/-2.
- 2025-08-15 61a28f2 Add price display to `ReviewGuesserHero` for better clarity - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +22/-0.
- 2025-08-15 3e898bc Steam login (#2) - Areas: frontend components; frontend routes/pages; backend domain models. Key files: frontend/TODOS.md, backend/build.gradle.kts. Stats: 28 files, +850/-11.
- 2025-08-15 7b67699 Refactor user profile enrichment method name in `AuthController.java` - Areas: backend controllers; frontend components. Key files: frontend/src/components/SteamLoginButton.tsx, backend/src/main/java/org/steam5/web/AuthController.java. Stats: 2 files, +16/-13.
- 2025-08-15 ca3babb Refined Steam OpenID Login Logic - Areas: backend config; backend controllers; frontend components. Key files: frontend/src/components/SteamLoginButton.tsx, backend/src/main/resources/application.yml. Stats: 3 files, +25/-4.
- 2025-08-15 34471a1 Update application configuration for improved header handling - Areas: backend config. Key files: backend/src/main/resources/application.yml. Stats: 1 files, +1/-0.
- 2025-08-15 1418995 Refactor Steam callback handling for environment-based redirection - Areas: frontend routes/pages. Key files: frontend/app/api/auth/steam/callback/route.ts. Stats: 1 files, +14/-4.
- 2025-08-15 c81da90 Add favicon and improve Steam callback handling - Areas: frontend routes/pages; docs. Key files: frontend/TODOS.md, frontend/app/icon.svg. Stats: 3 files, +20/-4.
- 2025-08-15 f7ff9bd Leaderboard (#3) - Areas: backend config; backend controllers; backend repositories. Key files: frontend/TODOS.md, frontend/src/components/Header.tsx. Stats: 7 files, +158/-0.
- 2025-08-15 dc8163f Restore prefilled guesses without storing locally - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +3/-27.
- 2025-08-15 47904b0 ### Commit Message - Areas: frontend components; backend controllers; docs. Key files: frontend/TODOS.md, frontend/src/components/ShareControls.tsx. Stats: 6 files, +139/-47.
- 2025-08-15 4f3533b Enhance Guess Submission Logic: Include Detailed DTOs and Total Review Count - Areas: backend controllers; frontend routes/pages. Key files: frontend/app/review-guesser/[round]/page.tsx, backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 2 files, +19/-6.
- 2025-08-15 b0f75c8 Enhance Guess Submission Logic: Include Detailed DTOs and Total Review Count - Areas: docs. Key files: frontend/TODOS.md. Stats: 1 files, +3/-3.
- 2025-08-15 cc0adc8 Restore prefilled guesses and refine leaderboard logic - Areas: frontend routes/pages; backend controllers; backend repositories. Key files: frontend/TODOS.md, frontend/app/routes.ts. Stats: 7 files, +97/-8.
- 2025-08-15 cb3b4e6 ``` Refactor header styling and Steam login button - Areas: frontend components; frontend styles. Key files: frontend/src/components/Header.tsx, frontend/src/components/SteamLoginButton.tsx. Stats: 3 files, +41/-8.
- 2025-08-15 e2e6e93 Refactor navigation links for consistent routing - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/leaderboard/page.tsx, frontend/app/review-guesser/leaderboard/today/page.tsx. Stats: 2 files, +9/-7.
- 2025-08-15 cc51d6e ``` Cleanup transient Steam API rate-limiting entries; update configuration files - Areas: IDE config; backend services; db migrations/scripts. Key files: frontend/TODOS.md, backend/src/main/resources/db/migration/V5_cleanup-excluded-429.sql. Stats: 5 files, +23/-1.
- 2025-08-15 af61d0f Update price formatting logic; mark related TODOs as complete - Areas: docs; frontend components. Key files: frontend/TODOS.md, frontend/src/components/ReviewGuesserHero.tsx. Stats: 2 files, +17/-13.
- 2025-08-15 0dfb618 Cleanup (#4) - Areas: frontend components; frontend utilities; docs. Key files: frontend/TODOS.md, frontend/src/lib/format.ts. Stats: 9 files, +97/-57.
- 2025-08-15 f31ff73 Refine leaderboard logic for cumulative achievements; restore prefilled guesses (#5) - Areas: frontend routes/pages; backend controllers; backend repositories. Key files: frontend/app/review-guesser/leaderboard/page.tsx, frontend/app/review-guesser/leaderboard/today/page.tsx. Stats: 4 files, +88/-13.
- 2025-08-15 72c855e Game info section (#6) - Areas: backend controllers; docs; frontend components. Key files: frontend/TODOS.md, frontend/src/components/GameInfoSection.tsx. Stats: 5 files, +170/-6.
- 2025-08-15 48b3785 Blur hash (#7) - Areas: backend jobs; backend config; IDE config. Key files: frontend/src/types/review-game.ts, frontend/src/components/ReviewGuesserHero.tsx. Stats: 15 files, +401/-4.
- 2025-08-15 e9dc868 Refactor leaderboard rendering into a reusable component - Areas: frontend routes/pages; backend jobs; frontend components. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/app/review-guesser/leaderboard/page.tsx. Stats: 4 files, +59/-85.
- 2025-08-15 e146a4a Improve blur hash computation and add immediate encoding job for screenshots - Areas: backend jobs; backend repositories; backend services. Key files: backend/src/main/java/org/steam5/job/BlurhashJob.java, backend/src/main/java/org/steam5/service/ReviewGameStateService.java. Stats: 3 files, +88/-44.
- 2025-08-15 0a4bc1b **Trigger Jobs to Start Immediately** - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +4/-0.
- 2025-08-15 cf325c6 **Update Leaderboard: Add Avatar Support and Immediate Encoding** - Areas: backend controllers; frontend components; frontend config/deps. Key files: frontend/next.config.ts, frontend/src/components/LeaderboardTable.tsx. Stats: 5 files, +40/-10.
- 2025-08-15 c21bd07 **Update Leaderboard: Add Profile Link Support** - Areas: backend controllers; frontend components; frontend styles. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/src/styles/components/leaderboard.css. Stats: 3 files, +42/-6.
- 2025-08-15 1492c0a remove cl - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/leaderboard/page.tsx. Stats: 1 files, +0/-1.
- 2025-08-15 0678fac update todo - Areas: docs. Key files: frontend/TODOS.md. Stats: 1 files, +28/-4.
- 2025-08-15 c0b3c66 **Update Quartz Job Trigger Time** - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +2/-2.
- 2025-08-15 eaa4668 **Refactor ReviewGuesserHero Component and Update Styles** - Areas: frontend components; frontend styles. Key files: frontend/src/components/ReviewGuesserHero.tsx, frontend/src/styles/components/reviewGuesserHero.css. Stats: 2 files, +7/-3.
- 2025-08-15 40b3ada Revert "**Update Quartz Job Trigger Time**" - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +2/-2.
## 2025-08-16 (v0.0.0+2025-08-16)
- 2025-08-16 29e4133 trying to fix category ids - Areas: IDE config; frontend components. Key files: frontend/src/components/GameInfoSection.tsx. Stats: 2 files, +5/-4.
- 2025-08-16 1fea78f De-duplicate categories - Areas: frontend components. Key files: frontend/src/components/GameInfoSection.tsx. Stats: 1 files, +19/-3.
- 2025-08-16 359b89e De-duplicate categories - Areas: frontend components. Key files: frontend/src/components/GameInfoSection.tsx. Stats: 1 files, +16/-17.
- 2025-08-16 66ffdb6 Design update (#8) - Areas: frontend components; frontend styles. Key files: frontend/src/styles/globals.css, frontend/src/components/ShareControls.tsx. Stats: 7 files, +83/-13.
- 2025-08-16 3718119 disable phone detection - Areas: frontend routes/pages. Key files: frontend/app/layout.tsx. Stats: 1 files, +1/-1.
- 2025-08-16 55be1de add github link to footer - Areas: frontend components; frontend styles. Key files: frontend/src/components/Footer.tsx, frontend/src/styles/components/footer.css. Stats: 2 files, +22/-0.
- 2025-08-16 0bbb9a1 Add theme icons for mode toggle in ThemeToggle component. - Areas: frontend components. Key files: frontend/src/components/ThemeToggle.tsx. Stats: 1 files, +15/-1.
- 2025-08-16 fd4c37b Add icons to FontToggle for font family switching. - Areas: frontend components. Key files: frontend/src/components/FontToggle.tsx. Stats: 1 files, +19/-1.
- 2025-08-16 e9d362f Add title attribute to buttons in FontToggle and ThemeToggle components; adjust footer styles - Areas: frontend components; frontend styles. Key files: frontend/src/components/FontToggle.tsx, frontend/src/components/ThemeToggle.tsx. Stats: 3 files, +54/-3.
- 2025-08-16 e763417 lint fixes - Areas: frontend components; frontend config/deps; frontend routes/pages. Key files: frontend/package.json, frontend/src/styles/globals.css. Stats: 7 files, +15/-16.
- 2025-08-16 44259ff Update avatar host configurations and enhance header styling; refine Steam login button with Next.js Image component - Areas: frontend components; frontend config/deps; frontend styles. Key files: frontend/next.config.ts, frontend/src/components/LeaderboardTable.tsx. Stats: 4 files, +30/-4.
- 2025-08-16 4f5eb31 Add navigation to previous round; improve component structure for review rounds. - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +37/-20.
- 2025-08-16 f714d57 Refactor price display logic for review picks; use concise rendering with Next.js `<Image>` component. - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +14/-12.
- 2025-08-16 213d24f Update review round controls styling and improve responsiveness; streamline ShareControls integration - Areas: frontend components; frontend styles. Key files: frontend/src/components/ReviewGuesserRound.tsx, frontend/src/styles/components/reviewShareControls.css. Stats: 2 files, +62/-27.
## 2025-08-18 (v0.0.0+2025-08-18)
- 2025-08-18 6deb8b9 Refactor blurhash (#9) - Areas: backend jobs; backend config; backend services. Key files: frontend/package.json, backend/build.gradle.kts. Stats: 22 files, +622/-198.
- 2025-08-18 3bccc7a Add new console SQL file and enhance avatar change detection logic. - Areas: IDE config; backend services. Key files: backend/src/main/java/org/steam5/service/SteamUserService.java. Stats: 2 files, +3/-1.
- 2025-08-18 ce4ad98 Update avatar host configurations and enhance leaderboard avatar rendering. - Areas: IDE config; frontend components; frontend config/deps. Key files: frontend/next.config.ts, frontend/src/components/LeaderboardTable.tsx. Stats: 4 files, +30/-12.
- 2025-08-18 ad72961 Remove unused fonts from `krypton` and `neon` configurations; optimize font loading - Areas: frontend routes/pages. Key files: frontend/app/layout.tsx. Stats: 1 files, +2/-11.
- 2025-08-18 d08b2fd update README.md - Areas: docs. Key files: README.md. Stats: 1 files, +41/-43.
- 2025-08-18 5f4b9c2 Align review summary actions to the right; merge server results for immediate completion UX - Areas: frontend components; backend controllers; frontend styles. Key files: frontend/src/components/RoundSummary.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 4 files, +102/-116.
- 2025-08-18 4188d71 Add logic to sync local guesses with backend in `ShareControls` component for localhost environments; introduce client-side auth check in `ReviewGuesserRound` component to conditionally display a sign-in nudge (#10) - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +77/-16.
- 2025-08-18 f553e0c Refactor Steam login URL construction and enhance UI prompt with direct sign-in link - Areas: frontend components. Key files: frontend/src/components/SteamLoginButton.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +17/-9.
- 2025-08-18 adecfd2 Add redirect rule to Next.js configuration and update leaderboard link in `ShareControls`. - Areas: frontend components; frontend config/deps. Key files: frontend/next.config.ts, frontend/src/components/ShareControls.tsx. Stats: 2 files, +11/-3.
- 2025-08-18 966aaf8 Refactor Steam OpenID login URL construction and enhance response verification - Areas: backend controllers. Key files: backend/src/main/java/org/steam5/web/AuthController.java. Stats: 1 files, +21/-28.
- 2025-08-18 8cde0da add missing space between emoji and points - Areas: frontend components. Key files: frontend/src/components/RoundPoints.tsx. Stats: 1 files, +1/-1.
- 2025-08-18 df6371b Introduce Steam App Reviews Refresh Job and configure nightly refresh settings (#11) - Areas: backend config; backend jobs; backend repositories. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/application-dev.yml. Stats: 6 files, +90/-0.
- 2025-08-18 2b898b8 Add lazy loading for Game Info Section using IntersectionObserver and replace direct component with dynamic import - Areas: frontend components; frontend routes/pages. Key files: frontend/src/components/LazyGameInfoSection.tsx, frontend/app/review-guesser/[round]/page.tsx. Stats: 2 files, +45/-2.
- 2025-08-18 fe4a98c lint const instead of let - Areas: frontend components. Key files: frontend/src/components/LazyGameInfoSection.tsx. Stats: 1 files, +1/-1.
- 2025-08-18 a14d8d1 Enhance Review Data Handling with Freshness Check - Areas: backend config; backend services. Key files: backend/src/main/resources/application.yml, backend/src/main/java/org/steam5/config/ReviewGameConfig.java. Stats: 3 files, +24/-2.
- 2025-08-18 d51c125 use var fonts to reduce file size by 10 - Areas: frontend routes/pages. Key files: frontend/app/layout.tsx, frontend/app/fonts/neon/MonaspaceNeonVar.woff2. Stats: 3 files, +2/-4.
- 2025-08-18 7aae193 add review pick algo to README.md - Areas: docs. Key files: README.md. Stats: 1 files, +38/-0.
- 2025-08-18 ce977d8 Archive pages (#12) - Areas: frontend routes/pages; frontend components; frontend styles. Key files: frontend/src/components/Footer.tsx, frontend/src/components/FooterNavRow.tsx. Stats: 14 files, +302/-46.
- 2025-08-18 89f6058 custom list style for archive - Areas: frontend routes/pages; frontend styles. Key files: frontend/src/styles/components/archive.css, frontend/app/review-guesser/archive/[date]/page.tsx. Stats: 2 files, +13/-3.
- 2025-08-18 566b860 add quintile sql scripts - Areas: db migrations/scripts; IDE config. Key files: backend/src/main/resources/db/calculate-quintiles-log-space.sql, backend/src/main/resources/db/calculate-quintiles-by-bucket-count.sql. Stats: 6 files, +186/-0.
- 2025-08-18 9c0dd2a update sql script - Areas: db migrations/scripts. Key files: backend/src/main/resources/db/calculate-quintiles-log-space.sql. Stats: 1 files, +60/-58.
- 2025-08-18 6d5b77b Log buckets (#13) - Areas: frontend routes/pages; backend config; backend services. Key files: README.md, frontend/src/types/review-game.ts. Stats: 26 files, +140/-101.
- 2025-08-18 3c71c19 Refine bucket explanation and adjust game selection logic for daily reviews - Areas: frontend components. Key files: frontend/src/components/ReviewRules.tsx. Stats: 1 files, +17/-0.
- 2025-08-18 ea3c767 Add new data source mappings for console SQL scripts Refactor bucket order logic in LeaderboardController Remove unnecessary filtering logic from LeaderboardController calculation - Areas: IDE config; backend controllers; frontend components. Key files: frontend/src/components/LeaderboardTable.tsx, backend/src/main/java/org/steam5/web/LeaderboardController.java. Stats: 3 files, +37/-26.
- 2025-08-18 7c57894 Add NewsBox component with dynamic news display for review rounds - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/data/news.json, frontend/src/components/NewsBox.tsx. Stats: 5 files, +116/-6.
## 2025-08-19 (v0.0.0+2025-08-19)
- 2025-08-19 5cc1d57 re-add caching to archive pages - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/archive/page.tsx, frontend/app/review-guesser/archive/[date]/page.tsx. Stats: 2 files, +0/-4.
- 2025-08-19 8400888 Bucket strategies (#14) - Areas: backend controllers; IDE config; backend repositories. Key files: frontend/src/data/news.json, frontend/src/components/NewsBox.tsx. Stats: 9 files, +541/-194.
- 2025-08-19 6f0637b Add new news item and refine bucket strategy descriptions - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +8/-1.
- 2025-08-19 bf63858 Update heading text in ReviewGuesserRound component for clarity - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserRound.tsx. Stats: 1 files, +1/-1.
- 2025-08-19 2dedc15 upgrade caching by removing force-dynamic, moving guess calls to clientside and sending cache headers by the backend (#15) - Areas: frontend routes/pages; backend controllers; frontend components. Key files: frontend/app/robots.ts, frontend/app/layout.tsx. Stats: 19 files, +204/-135.
- 2025-08-19 27d3bf5 added more testcases - Areas: misc; frontend config/deps. Key files: frontend/package.json, backend/src/test/java/org/steam5/web/AuthControllerTest.java. Stats: 5 files, +308/-104.
- 2025-08-19 7404e61 Replace static leaderboard data fetching with SWR-based dynamic fetching (#16) - Areas: frontend config/deps; frontend routes/pages; frontend components. Key files: frontend/package.json, frontend/src/components/LeaderboardTable.tsx. Stats: 5 files, +61/-31.
- 2025-08-19 7beea6e Add support for additional font options and dropdown selector - Areas: frontend routes/pages; frontend styles; frontend components. Key files: frontend/app/layout.tsx, frontend/src/styles/globals.css. Stats: 10 files, +89/-36.
- 2025-08-19 78de098 Refine game info section layout and media styling - Areas: frontend styles. Key files: frontend/src/styles/components/gameInfoSection.css. Stats: 1 files, +13/-4.
- 2025-08-19 4c90706 fix app names in share export - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +27/-4.
- 2025-08-19 bb3611d fixed lint - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +28/-24.
- 2025-08-19 2fca929 **Add Review Bucket Calculations and API Endpoints** (#17) - Areas: backend repositories; backend config; backend controllers. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/config/SecurityConfig.java. Stats: 8 files, +413/-1.
- 2025-08-19 7772652 added index sql script (#18) - Areas: IDE config; db migrations/scripts. Key files: backend/src/main/resources/db/migration/V7_add-index.sql. Stats: 3 files, +39/-0.
- 2025-08-19 8ffaeed Create gradle.yml - Areas: repo config. Key files: .github/workflows/gradle.yml. Stats: 1 files, +67/-0.
- 2025-08-19 9845fe1 Update gradle.yml - Areas: repo config. Key files: .github/workflows/gradle.yml. Stats: 1 files, +1/-1.
- 2025-08-19 fd5bb20 Make gradlew executable - Areas: backend build/deps. Key files: backend/gradlew. Stats: 1 files, +0/-0.
- 2025-08-19 f368f34 update gradle.yml - Areas: IDE config; repo config. Key files: .github/workflows/gradle.yml. Stats: 2 files, +10/-3.
- 2025-08-19 d0b09e5 update gradle.yml - Areas: repo config. Key files: .github/workflows/gradle.yml. Stats: 1 files, +1/-1.
- 2025-08-19 01af48c Improve font choice synchronization after hydration - Areas: frontend components; repo config. Key files: .github/workflows/gradle.yml, frontend/src/components/FontToggle.tsx. Stats: 2 files, +47/-76.
- 2025-08-19 3a08c95 Add new news entry for font and theme customization - Areas: IDE config; frontend utilities. Key files: frontend/src/data/news.json. Stats: 2 files, +9/-5.
- 2025-08-19 1031339 Enhanced NewsBox component styling and accessibility; updated guessing interface heading. - Areas: frontend components; frontend styles. Key files: frontend/src/components/NewsBox.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 3 files, +9/-1.
- 2025-08-19 5462355 Refactored archive page for better navigation; integrated styling updates - Areas: frontend routes/pages; frontend styles. Key files: frontend/app/review-guesser/archive/page.tsx, frontend/src/styles/components/archive-list.css. Stats: 2 files, +29/-5.
- 2025-08-19 e2a80d3 Add new archive route and update sitemap logic - Areas: frontend routes/pages. Key files: frontend/app/routes.ts, frontend/app/sitemap.ts. Stats: 2 files, +33/-9.
- 2025-08-19 85bfcb7 Refactor archive list styling for improved structure and readability. - Introduce grid layout and adjust margins in `.archive-list__toc`. - Simplify list item counter content formatting. - Areas: frontend styles. Key files: frontend/src/styles/components/archive-list.css. Stats: 1 files, +8/-2.
## 2025-08-20 (v0.0.0+2025-08-20)
- 2025-08-20 cdc71ae Add weekly leaderboard support (#19) - Areas: frontend routes/pages; backend controllers; backend repositories. Key files: frontend/app/routes.ts, frontend/src/components/LeaderboardTable.tsx. Stats: 8 files, +133/-46.
- 2025-08-20 4ce822a reduce news duration - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +2/-2.
- 2025-08-20 4b103ec update news - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +3/-3.
- 2025-08-20 81b221c Enhance Metadata for SEO and User Experience on Review Guesser Pages (#20) - Areas: frontend routes/pages. Key files: frontend/app/page.tsx, frontend/app/layout.tsx. Stats: 9 files, +191/-1.
- 2025-08-20 b98b456 Update Open Graph and Twitter metadata logic for dynamic image handling in Review Guesser page. - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/[round]/page.tsx. Stats: 1 files, +11/-4.
- 2025-08-20 e87013b upgrade  next - Areas: frontend config/deps. Key files: frontend/package.json. Stats: 2 files, +53/-53.
- 2025-08-20 c45d2a7 Align leaderboard table number alignment to start from left. - Areas: frontend styles. Key files: frontend/src/styles/components/leaderboard.css. Stats: 1 files, +1/-1.
- 2025-08-20 5b8ae42 add cursor rules - Areas: cursor rules/plans; misc. Key files: .cursorignore, .cursorindexingignore. Stats: 25 files, +1850/-0.
- 2025-08-20 bc2548c update cache config - Areas: backend config; IDE config; db migrations/scripts. Key files: backend/src/main/resources/application.yml, backend/src/main/resources/db/migration/V7_add-index.sql. Stats: 4 files, +25/-3.
- 2025-08-20 10196b8 Entity graph (#21) - Areas: backend controllers; backend domain models; misc. Key files: backend/src/main/resources/db/http-client.env.json, backend/src/main/java/org/steam5/web/SteamAppDetailController.java. Stats: 9 files, +303/-108.
- 2025-08-20 4b44ac1 mobile 2col grid for screenshots - Areas: frontend styles. Key files: frontend/src/styles/components/reviewGuesserHero.css. Stats: 1 files, +24/-0.
- 2025-08-20 fd4b5f7 reduce size of items on mobile - Areas: frontend styles; frontend components; IDE config. Key files: frontend/src/styles/globals.css, frontend/src/components/RoundResult.tsx. Stats: 10 files, +86/-22.
- 2025-08-20 9032486 actuator info - Areas: docs. Key files: README.md. Stats: 1 files, +47/-0.
- 2025-08-20 9b76a01 add border top - Areas: frontend styles. Key files: frontend/src/styles/components/reviewGuesserHero.css. Stats: 1 files, +4/-0.
- 2025-08-20 03f8a40 further mobile restyles - Areas: frontend components; frontend styles; frontend routes/pages. Key files: frontend/src/components/Header.tsx, frontend/src/components/NewsBox.tsx. Stats: 8 files, +80/-11.
- 2025-08-20 80bf484 Enhance RoundResult and ReviewGuesserRound components with selected label display and conditional guess controls - Areas: frontend components. Key files: frontend/src/components/RoundResult.tsx, frontend/src/components/ReviewGuesserRound.tsx. Stats: 2 files, +28/-15.
- 2025-08-20 4497212 make header a link - Areas: frontend components. Key files: frontend/src/components/Header.tsx. Stats: 1 files, +3/-1.
## 2025-08-21 (v0.0.0+2025-08-21)
- 2025-08-21 b49fc95 Normalize URLs to HTTPS in `SteamAppDetailService` and set high fetch priority for images in `ReviewGuesserHero`. - Areas: backend domain models; frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx, backend/src/main/java/org/steam5/domain/details/SteamAppDetailService.java. Stats: 2 files, +15/-3.
- 2025-08-21 4c0c18a add rel canonical - Areas: frontend routes/pages. Key files: frontend/app/page.tsx, frontend/app/review-guesser/page.tsx. Stats: 8 files, +27/-0.
- 2025-08-21 99f250f Improve error handling in Review Guesser and refactor ThemeToggle for initial theme setup. - Areas: frontend components; frontend config/deps; frontend routes/pages. Key files: frontend/package.json, frontend/src/components/ThemeToggle.tsx. Stats: 5 files, +270/-15.
- 2025-08-21 6fa53bd Refactor platform flags in `review-game.ts` and improve `GameInfoSection` structure with updated logic and styling. - Areas: frontend components; frontend utilities. Key files: frontend/src/types/review-game.ts, frontend/src/components/GameInfoSection.tsx. Stats: 2 files, +22/-15.
- 2025-08-21 9e0e33c Add canonical link and normalize URLs in SteamAppDetailService; prioritize images in ReviewGuesserHero. - Areas: frontend routes/pages. Key files: frontend/app/head.tsx. Stats: 1 files, +21/-0.
- 2025-08-21 3ff6aba Refactor FontToggle and ThemeToggle for improved state management, update GameInfoSection styles and logic. - Areas: frontend components; frontend styles. Key files: frontend/src/components/FontToggle.tsx, frontend/src/components/ThemeToggle.tsx. Stats: 4 files, +22/-6.
- 2025-08-21 16b2f40 Improve link styling and remove redundant header text alignment - Areas: frontend styles. Key files: frontend/src/styles/globals.css, frontend/src/styles/components/header.css. Stats: 2 files, +4/-1.
- 2025-08-21 86e085a Add Imprint and Privacy pages, update footer navigation links - Areas: frontend routes/pages; frontend components. Key files: frontend/app/routes.ts, frontend/app/imprint/page.tsx. Stats: 4 files, +105/-1.
- 2025-08-21 3716511 Extract LeaderboardSection component and simplify leaderboard page structure - Areas: frontend routes/pages; frontend components. Key files: frontend/src/components/LeaderboardSection.tsx, frontend/app/review-guesser/leaderboard/page.tsx. Stats: 4 files, +43/-33.
- 2025-08-21 ac23a65 Simplify Review Guesser layout and update header styling for improved readability - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/components/Header.tsx, frontend/src/components/ReviewGuesserHero.tsx. Stats: 4 files, +17/-28.
- 2025-08-21 0ffc9e1 - Add Canonical Link: Implemented a canonical link to address duplicate content issues effectively. - Normalize URLs: Updated `SteamAppDetailService` to ensure all URLs are normalized to HTTPS, enhancing security and consistency. - Image Fetch Priority: Set high fetch priority for images in `ReviewGuesserHero` to improve page load times and visual performance. - Areas: frontend utilities; frontend components; frontend styles. Key files: frontend/src/components/ReviewGuesserRound.tsx, frontend/src/lib/hooks/useAuthSignedIn.ts. Stats: 4 files, +133/-115.
- 2025-08-21 5cd4ebc Refactor Review Guesser Components for Clarity and Reusability - Areas: frontend components; frontend styles. Key files: frontend/src/components/RoundSummary.tsx, frontend/src/components/ReviewGuesserHero.tsx. Stats: 7 files, +157/-76.
- 2025-08-21 23d55c4 Disable fetching in `ReviewGuesserRound` if prefilled or all results exist - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/styles/globals.css, frontend/src/components/ReviewGuesserRound.tsx. Stats: 4 files, +59/-3.
## 2025-08-22 (v0.0.0+2025-08-22)
- 2025-08-22 e5f20d6 Add ArchiveOfflineRound and ArchiveResetForDay components for offline play and reset functionality (#22) - Areas: frontend components; frontend styles; frontend routes/pages. Key files: frontend/src/lib/storage.ts, frontend/src/components/ArchiveResetForDay.tsx. Stats: 6 files, +248/-3.
- 2025-08-22 2187b61 Update test for header controller to include headers parameter - Areas: frontend styles; misc; backend services. Key files: frontend/src/styles/globals.css, frontend/src/components/NewsBox.tsx. Stats: 7 files, +71/-19.
- 2025-08-22 9ba2e6c fix testcase - Areas: misc. Key files: backend/src/test/java/org/steam5/web/SteamAppDetailControllerTest.java. Stats: 1 files, +2/-2.
- 2025-08-22 c3fd112 remove off by from ShareControls.tsx - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +2/-2.
- 2025-08-22 0eba520 Fetch app names based on game date in ShareControls.tsx for improved flexibility - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +19/-8.
- 2025-08-22 d10381f update shared box layout - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +5/-5.
- 2025-08-22 91196e2 Improve ShareControls by adding loading state, disabling button during fetch, and ensuring complete rounds for sharing - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +49/-29.
- 2025-08-22 70f672b add empty row in share - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +1/-0.
- 2025-08-22 1fa08c4 remove padding-top, which clashes with the sticky header visuals - Areas: frontend styles. Key files: frontend/src/styles/globals.css. Stats: 1 files, +0/-1.
## 2025-08-23 (v0.0.0+2025-08-23)
- 2025-08-23 1e0ed49 reduce verbose logging and add retry - Areas: backend jobs; misc. Key files: backend/src/main/java/org/steam5/http/SteamHttpClient.java, backend/src/main/java/org/steam5/job/SteamAppReviewsRefreshJob.java. Stats: 2 files, +60/-7.
- 2025-08-23 470f5e5 add leaderboard daily streaks - Areas: backend controllers; backend repositories; frontend components. Key files: frontend/src/components/LeaderboardTable.tsx, backend/src/main/java/org/steam5/web/LeaderboardController.java. Stats: 3 files, +31/-2.
- 2025-08-23 bb401ba make leaderboard sortable - Areas: frontend components; frontend styles. Key files: frontend/src/components/ShareControls.tsx, frontend/src/components/LeaderboardTable.tsx. Stats: 4 files, +143/-20.
- 2025-08-23 80fe3ad sortable columns - Areas: frontend components. Key files: frontend/src/components/LeaderboardTable.tsx. Stats: 1 files, +33/-72.
## 2025-08-24 (v0.0.0+2025-08-24)
- 2025-08-24 115cd1e Update page.tsx - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/leaderboard/page.tsx. Stats: 1 files, +1/-1.
- 2025-08-24 aec4f9d Update page.tsx - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/leaderboard/weekly/page.tsx. Stats: 1 files, +1/-1.
- 2025-08-24 b46b98f Update page.tsx - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/leaderboard/today/page.tsx. Stats: 1 files, +1/-1.
## 2025-08-25 (v0.0.0+2025-08-25)
- 2025-08-25 38e4224 Profile pages (#23) - Areas: backend controllers; frontend styles; backend config. Key files: frontend/TODOS.md, frontend/src/data/news.json. Stats: 9 files, +458/-13.
- 2025-08-25 be1cca7 mobile profile page - Areas: frontend routes/pages; frontend styles. Key files: frontend/app/profile/[steamId]/page.tsx, frontend/src/styles/components/profile.css. Stats: 2 files, +49/-10.
- 2025-08-25 3220177 column alignment - Areas: frontend styles. Key files: frontend/src/styles/components/profile.css. Stats: 1 files, +8/-1.
- 2025-08-25 cdb4569 Highlight selected/actual buckets with correct styling if they match. - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/components/SteamLoginButton.tsx, frontend/app/profile/[steamId]/page.tsx. Stats: 3 files, +22/-6.
## 2025-08-26 (v0.0.0+2025-08-26)
- 2025-08-26 8735c17 update icons - Areas: frontend assets; frontend components; frontend routes/pages. Key files: frontend/app/icon.svg, frontend/public/icon.svg. Stats: 6 files, +55/-16.
- 2025-08-26 197d809 fix clash file name - Areas: frontend assets; frontend components; frontend styles. Key files: frontend/public/icon.svg, frontend/src/components/Header.tsx. Stats: 3 files, +217/-50.
- 2025-08-26 4f805a8 try new icon as og image - Areas: frontend routes/pages. Key files: frontend/app/opengraph-image.tsx. Stats: 1 files, +33/-17.
## 2025-08-27 (v0.0.0+2025-08-27)
- 2025-08-27 c8ef51e filter today from archive - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/archive/page.tsx. Stats: 1 files, +4/-1.
- 2025-08-27 6a0e687 meta opengraph image - Areas: frontend routes/pages. Key files: frontend/app/layout.tsx. Stats: 1 files, +2/-0.
## 2025-08-28 (v0.0.0+2025-08-28)
- 2025-08-28 3224808 Playerprofile (#24) - Areas: frontend routes/pages; frontend components; frontend styles. Key files: frontend/app/opengraph-image.tsx, frontend/src/components/Header.tsx. Stats: 17 files, +658/-54.
- 2025-08-28 87e4e50 remove subline - Areas: frontend components. Key files: frontend/src/components/PerformanceSection.tsx. Stats: 1 files, +0/-1.
- 2025-08-28 585afc2 add three more graphs - Areas: frontend components. Key files: frontend/src/components/PerformanceSection.tsx. Stats: 1 files, +119/-2.
- 2025-08-28 452e756 extract graph components - Areas: frontend components. Key files: frontend/src/components/PerformanceSection.tsx, frontend/src/components/performance/StreaksCard.tsx. Stats: 7 files, +348/-286.
- 2025-08-28 96c4b80 daily streaks - Areas: frontend components. Key files: frontend/src/components/performance/StreaksCard.tsx. Stats: 1 files, +38/-9.
- 2025-08-28 9633735 increase font-size - Areas: frontend components. Key files: frontend/src/components/PerformanceSection.tsx, frontend/src/components/performance/BucketAccuracyBars.tsx. Stats: 4 files, +19/-18.
- 2025-08-28 8d14058 include today in performance section - Areas: frontend routes/pages. Key files: frontend/app/profile/[steamId]/page.tsx. Stats: 1 files, +3/-2.
## 2025-09-02 (v0.0.0+2025-09-02)
- 2025-09-02 21827fb big seo update - Areas: frontend routes/pages. Key files: frontend/app/head.tsx, frontend/app/page.tsx. Stats: 15 files, +157/-18.
- 2025-09-02 9a9eaef restore favicon - Areas: frontend routes/pages. Key files: frontend/app/layout.tsx. Stats: 1 files, +0/-5.
## 2025-09-19 (v0.0.0+2025-09-19)
- 2025-09-19 ebcce8a add setup-local-db.sql - Areas: IDE config; frontend config/deps; misc. Key files: backend/setup-local-db.sql. Stats: 4 files, +3548/-0.
- 2025-09-19 0cabd2a update dependencies, remove themeColor from metadata - Areas: frontend config/deps; frontend routes/pages; misc. Key files: frontend/package.json, frontend/pnpm-workspace.yaml. Stats: 4 files, +279/-302.
## 2025-09-26 (v0.0.0+2025-09-26)
- 2025-09-26 e6430a5 upgrade frontend - Areas: frontend config/deps; IDE config. Key files: frontend/package.json. Stats: 4 files, +159/-5791.
- 2025-09-26 effea6d make table thead sticky - Areas: IDE config; frontend components; frontend routes/pages. Key files: frontend/app/opengraph-image.tsx, frontend/src/components/Header.tsx. Stats: 8 files, +74/-76.
- 2025-09-26 81eed36 add avg points below table - Areas: frontend components; frontend styles. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/src/styles/components/leaderboard.css. Stats: 2 files, +18/-0.
- 2025-09-26 831337a add space - Areas: frontend components. Key files: frontend/src/components/LeaderboardTable.tsx. Stats: 1 files, +1/-1.
## 2025-10-08 (v0.0.0+2025-10-08)
- 2025-10-08 8514073 add always-pick history endpoint with caching and computation logic - Areas: IDE config; backend controllers. Key files: backend/src/main/java/org/steam5/web/ReviewGameStateController.java. Stats: 2 files, +61/-0.
- 2025-10-08 9993948 upgrade backend dependencies - Areas: IDE config; backend build/deps. Key files: backend/build.gradle.kts. Stats: 3 files, +7/-8.
## 2025-10-10 (v0.0.0+2025-10-10)
- 2025-10-10 5e37b38 next 16 - Areas: frontend config/deps. Key files: frontend/package.json, frontend/tsconfig.json. Stats: 5 files, +321/-3088.
- 2025-10-10 e957677 remove vertical scroll from leaderboard - Areas: frontend config/deps; frontend styles. Key files: frontend/tsconfig.json, frontend/src/styles/components/leaderboard.css. Stats: 2 files, +4/-3.
- 2025-10-10 a654fc6 add archivesummary - Areas: frontend routes/pages; frontend styles; IDE config. Key files: frontend/package.json, frontend/src/styles/variables.css. Stats: 14 files, +537/-1.
- 2025-10-10 84b6927 add fancybox - Areas: frontend components; frontend styles. Key files: frontend/src/components/GameInfoSection.tsx, frontend/src/components/ReviewGuesserHero.tsx. Stats: 4 files, +142/-124.
- 2025-10-10 5dd0350 ts ignore - Areas: frontend components. Key files: frontend/src/components/GameInfoSection.tsx, frontend/src/components/ReviewGuesserHero.tsx. Stats: 2 files, +3/-1.
- 2025-10-10 2360f96 fix typo - Areas: frontend components. Key files: frontend/src/components/GameInfoSection.tsx, frontend/src/components/ReviewGuesserHero.tsx. Stats: 2 files, +47/-26.
- 2025-10-10 f4ab0a4 align thead summary archive - Areas: frontend styles. Key files: frontend/src/styles/components/archive-summary.css. Stats: 1 files, +7/-0.
## 2025-10-25 (v0.0.0+2025-10-25)
- 2025-10-25 1509a21 next 16 - Areas: frontend config/deps. Key files: frontend/package.json. Stats: 2 files, +108/-108.
- 2025-10-25 9d6c5cc actually pick youngest 35 rounds - Areas: IDE config; frontend components. Key files: frontend/src/components/PerformanceSection.tsx. Stats: 2 files, +9/-2.
- 2025-10-25 5c9a173 add junie guidelines.md - Areas: misc. Key files: .junie/guidelines.md. Stats: 1 files, +142/-0.
## 2025-10-27 (v0.0.0+2025-10-27)
- 2025-10-27 b073181 add user achievements - Areas: backend controllers; backend repositories; backend services. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/app/api/leaderboard/achievements/route.ts. Stats: 6 files, +161/-1.
- 2025-10-27 09af192 add more achievements - Areas: backend repositories; backend services; frontend components. Key files: frontend/src/components/LeaderboardTable.tsx, backend/src/main/java/org/steam5/service/StatisticsService.java. Stats: 3 files, +126/-19.
- 2025-10-27 4eccb28 fix perfect day calc - Areas: backend repositories; backend services. Key files: backend/src/main/java/org/steam5/service/StatisticsService.java, backend/src/main/java/org/steam5/repository/GuessRepository.java. Stats: 2 files, +4/-4.
- 2025-10-27 8d0bda3 remove limit from queries - Areas: backend repositories; backend services. Key files: backend/src/main/java/org/steam5/service/StatisticsService.java, backend/src/main/java/org/steam5/repository/GuessRepository.java. Stats: 2 files, +16/-21.
## 2025-10-28 (v0.0.0+2025-10-28)
- 2025-10-28 6590162 dynamically display achievemnts - Areas: backend controllers; IDE config; backend config. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/app/api/leaderboard/achievements/route.ts. Stats: 8 files, +210/-17.
- 2025-10-28 11a6b93 update leaderboard caching in nextjs - Areas: frontend components; frontend routes/pages. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/app/api/leaderboard/achievements/route.ts. Stats: 2 files, +28/-4.
## 2025-10-30 (v0.0.0+2025-10-30)
- 2025-10-30 c36db89 trying title attribute for mobile - Areas: frontend styles. Key files: frontend/src/styles/globals.css. Stats: 1 files, +20/-0.
- 2025-10-30 56454bc trying title attribute for mobile - Areas: frontend styles. Key files: frontend/src/styles/globals.css. Stats: 1 files, +2/-2.
- 2025-10-30 2aa1090 doesnt work - Areas: frontend styles. Key files: frontend/src/styles/globals.css. Stats: 1 files, +0/-20.
## 2025-11-04 (v0.0.0+2025-11-04)
- 2025-11-04 b505116 add monthly leaderboard, also last 30 days and last 7 days - Areas: frontend components; frontend routes/pages; backend controllers. Key files: frontend/package.json, frontend/app/routes.ts. Stats: 11 files, +184/-87.
## 2025-11-05 (v0.0.0+2025-11-05)
- 2025-11-05 288f865 add worktrees.json - Areas: cursor rules/plans. Key files: .cursor/worktrees.json. Stats: 1 files, +5/-0.
- 2025-11-05 1c72a55 add donate button - Areas: frontend components; frontend styles. Key files: frontend/src/components/Footer.tsx, frontend/src/components/Header.tsx. Stats: 3 files, +58/-0.
- 2025-11-05 b8832cf mobile hide signed in label - Areas: frontend components. Key files: frontend/src/components/SteamLoginButton.tsx. Stats: 1 files, +1/-1.
## 2025-11-06 (v0.0.0+2025-11-06)
- 2025-11-06 974e41a add donate news block - Areas: frontend components; frontend utilities. Key files: frontend/src/data/news.json, frontend/src/components/Footer.tsx. Stats: 3 files, +10/-0.
- 2025-11-06 9ffa655 add monthly news block - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +7/-0.
- 2025-11-06 d7e86ac add achievement news entry - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +7/-0.
- 2025-11-06 9d9aed4 add game statistics to archive page - Areas: backend controllers; backend repositories; frontend config/deps. Key files: frontend/package.json, frontend/src/data/news.json. Stats: 12 files, +338/-10.
- 2025-11-06 92f7b96 add archive links to top 10 - Areas: backend repositories; backend services; frontend components. Key files: frontend/src/components/GameStatistics.tsx, frontend/src/styles/components/game-statistics.css. Stats: 4 files, +73/-18.
## 2025-11-07 (v0.0.0+2025-11-07)
- 2025-11-07 9bda6d4 add login icon - Areas: frontend config/deps; IDE config; frontend components. Key files: frontend/package.json, frontend/src/components/SteamLoginButton.tsx. Stats: 4 files, +22/-2.
- 2025-11-07 3886703 use icons in header - Areas: frontend styles; frontend components. Key files: frontend/src/components/Header.tsx, frontend/src/styles/components/header.css. Stats: 3 files, +39/-52.
- 2025-11-07 2d831b6 realign icons in header actions - Areas: frontend components; frontend styles. Key files: frontend/src/components/Header.tsx, frontend/src/components/SteamLoginButton.tsx. Stats: 3 files, +7/-6.
- 2025-11-07 4fa4720 restyle mobile header - Areas: frontend components; frontend styles. Key files: frontend/src/components/Header.tsx, frontend/src/components/SteamLoginButton.tsx. Stats: 3 files, +38/-49.
- 2025-11-07 4f16887 remove underline from archive link - Areas: frontend styles. Key files: frontend/src/styles/components/header.css. Stats: 1 files, +0/-7.
- 2025-11-07 6cd923c use arrow icons instead of utfs - Areas: frontend components. Key files: frontend/src/components/RoundResultActions.tsx. Stats: 1 files, +8/-5.
- 2025-11-07 deca87a add share icon - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +3/-2.
- 2025-11-07 d1c6838 remove today date - Areas: frontend components. Key files: frontend/src/components/ReviewGuesserHero.tsx. Stats: 1 files, +1/-1.
## 2025-11-09 (v0.0.0+2025-11-09)
- 2025-11-09 d347731 add sloth and cheetah achievements - Areas: frontend config/deps; backend repositories; backend services. Key files: frontend/package.json, frontend/src/data/news.json. Stats: 6 files, +265/-109.
- 2025-11-09 7315442 fix game-stats darkmode - Areas: frontend styles. Key files: frontend/src/styles/components/game-statistics.css. Stats: 1 files, +4/-7.
- 2025-11-09 7ddf143 update news - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +18/-11.
- 2025-11-09 7c33341 use sloth emoji - Areas: frontend components. Key files: frontend/src/components/LeaderboardTable.tsx. Stats: 1 files, +1/-1.
- 2025-11-09 e77323f sloth emoji - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +2/-2.
## 2025-11-10 (v0.0.0+2025-11-10)
- 2025-11-10 72ce89c show achievement data - Areas: backend services; frontend components; frontend routes/pages. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/src/styles/components/leaderboard.css. Stats: 4 files, +280/-25.
- 2025-11-10 7ecf654 remove leaderboard nextjs caching - Areas: backend services; frontend components; frontend routes/pages. Key files: frontend/src/components/LeaderboardTable.tsx, frontend/app/api/leaderboard/achievements/route.ts. Stats: 3 files, +15/-26.
- 2025-11-10 39a1a05 update cache header - Areas: frontend components. Key files: frontend/src/components/LeaderboardTable.tsx. Stats: 1 files, +1/-1.
- 2025-11-10 c8aef8a add yaak and postman - Areas: api clients. Key files: steam5.postman_collection.json, yaak/yaak.fl_Q2mkb5mJvy.yaml. Stats: 48 files, +1994/-0.
- 2025-11-10 f986b32 add encrypted steamkey for yaak - Areas: api clients. Key files: yaak/yaak.ev_oSVAy5tzxD.yaml, yaak/yaak.wk_ecNXfSaJZU.yaml. Stats: 2 files, +36/-2.
## 2025-11-11 (v0.0.0+2025-11-11)
- 2025-11-11 45e5768 expose server time - Areas: backend controllers; backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java, backend/src/main/java/org/steam5/web/StatisticsController.java. Stats: 3 files, +59/-2.
- 2025-11-11 d4fc768 use client time, show next daily challenge - Areas: frontend components; frontend routes/pages; frontend styles. Key files: frontend/src/components/Footer.tsx, frontend/src/components/LeaderboardTable.tsx. Stats: 6 files, +133/-17.
- 2025-11-11 a86b177 restyle achievement metric section - Areas: frontend components; frontend styles. Key files: frontend/src/components/Footer.tsx, frontend/src/components/LeaderboardTable.tsx. Stats: 5 files, +260/-116.
- 2025-11-11 c42f6a6 add spacemono font - Areas: frontend routes/pages; frontend components; frontend styles. Key files: frontend/app/layout.tsx, frontend/src/styles/globals.css. Stats: 4 files, +22/-2.
## 2025-11-12 (v0.0.0+2025-11-12)
- 2025-11-12 e83c29f handle weekly floating table - Areas: frontend config/deps; IDE config; frontend components. Key files: frontend/package.json, frontend/src/components/LeaderboardTable.tsx. Stats: 4 files, +66/-66.
- 2025-11-12 60840f1 profile show last 30 days - Areas: frontend components. Key files: frontend/src/components/performance/OutcomeMixBar.tsx, frontend/src/components/performance/BucketAccuracyBars.tsx. Stats: 5 files, +59/-20.
## 2025-11-14 (v0.0.0+2025-11-14)
- 2025-11-14 94b0cc6 prep streak posts - Areas: frontend config/deps; IDE config; api clients. Key files: frontend/package.json, yaak/yaak.wk_ecNXfSaJZU.yaml. Stats: 5 files, +141/-74.
- 2025-11-14 9fa557b Merge branch 'main' of github.com:LucaNerlich/steam5 - Merge commit; no file changes recorded.
## 2025-11-25 (v0.0.0+2025-11-25)
- 2025-11-25 771c776 use force-dynamic again - Areas: frontend routes/pages; frontend config/deps. Key files: frontend/package.json, frontend/app/api/leaderboard/all/route.ts. Stats: 7 files, +105/-97.
- 2025-11-25 8b5ff30 code layouting - Areas: frontend components. Key files: frontend/src/components/performance/HitRateVsAverageCard.tsx. Stats: 1 files, +22/-10.
## 2025-11-26 (v0.0.0+2025-11-26)
- 2025-11-26 4845164 spring 3.5.8 - Areas: IDE config; backend build/deps. Key files: backend/build.gradle.kts. Stats: 3 files, +4/-4.
- 2025-11-26 ac748ee upgrade to spring boot 4 - Areas: backend services; backend config; IDE config. Key files: backend/build.gradle.kts, backend/src/main/resources/application.yml. Stats: 10 files, +23/-31.
- 2025-11-26 cc3b9f9 add screenshot and movie blank checks - Areas: IDE config; backend domain models. Key files: backend/src/main/java/org/steam5/domain/details/SteamAppDetailService.java. Stats: 2 files, +5/-4.
- 2025-11-26 eb9cb56 add nullchecks - Areas: backend domain models. Key files: backend/src/main/java/org/steam5/domain/details/SteamAppDetailService.java. Stats: 1 files, +5/-5.
- 2025-11-26 6ce2aad dont show empty movies - Areas: frontend components. Key files: frontend/src/components/GameInfoSection.tsx. Stats: 1 files, +1/-0.
## 2025-11-27 (v0.0.0+2025-11-27)
- 2025-11-27 29d00b5 fix json success compare - Areas: backend domain models; frontend config/deps; IDE config. Key files: frontend/package.json, backend/src/main/java/org/steam5/service/SteamAppDetailsFetcher.java. Stats: 6 files, +509/-314.
## 2025-11-28 (v0.0.0+2025-11-28)
- 2025-11-28 18b1a43 Update ShareControls.tsx - Areas: frontend components. Key files: frontend/src/components/ShareControls.tsx. Stats: 1 files, +1/-1.
## 2025-12-03 (v0.0.0+2025-12-03)
- 2025-12-03 bf1abe0 fix cve - Areas: frontend config/deps. Key files: frontend/package.json. Stats: 2 files, +82/-82.
## 2025-12-05 (v0.0.0+2025-12-05)
- 2025-12-05 4151e70 add survey news - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +7/-0.
- 2025-12-05 e189c37 Squash merge movies-hls into main - Areas: backend domain models; frontend config/deps; backend controllers. Key files: frontend/package.json, frontend/src/types/review-game.ts. Stats: 8 files, +225/-12.
## 2025-12-06 (v0.0.0+2025-12-06)
- 2025-12-06 fb86251 Seasons (#28) - Areas: backend config; backend controllers; frontend styles. Key files: backend/build.gradle.kts, frontend/app/routes.ts. Stats: 38 files, +2126/-74.
- 2025-12-06 78f0ff5 backfill npw - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +4/-4.
- 2025-12-06 8e93cbc finalize seasons - Areas: backend config. Key files: backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 1 files, +10/-9.
- 2025-12-06 0f48c69 update news - Areas: backend config; frontend utilities. Key files: frontend/src/data/news.json, backend/src/main/java/org/steam5/config/QuartzConfig.java. Stats: 2 files, +5/-7.
- 2025-12-06 bf66aa2 fix season metadata url - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/seasons/page.tsx. Stats: 1 files, +3/-4.
- 2025-12-06 31334f7 small seasons css udate - Areas: frontend styles. Key files: frontend/src/styles/components/seasons.css. Stats: 1 files, +5/-4.
## 2025-12-08 (v0.0.0+2025-12-08)
- 2025-12-08 898e3e9 move archive to footer - Areas: frontend components. Key files: frontend/src/components/Footer.tsx, frontend/src/components/Header.tsx. Stats: 2 files, +19/-24.
- 2025-12-08 1597eb7 set seasons news block end - Areas: frontend utilities. Key files: frontend/src/data/news.json. Stats: 1 files, +1/-1.
- 2025-12-08 7ce8ec6 add season detail pages - Areas: frontend routes/pages; frontend styles; backend controllers. Key files: frontend/app/routes.ts, frontend/app/sitemap.ts. Stats: 16 files, +1609/-76.
- 2025-12-08 06c89cf season pill should link - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/seasons/page.tsx. Stats: 1 files, +2/-2.
## 2025-12-10 (v0.0.0+2025-12-10)
- 2025-12-10 e4d2710 add admin token filter - Areas: api clients; backend config; misc. Key files: backend/.env.example, yaak/yaak.fl_tQWJJfKsff.yaml. Stats: 8 files, +162/-2.
- 2025-12-10 4289ee2 add easiest and hardest round to season detail - Areas: frontend routes/pages; api clients; backend controllers. Key files: yaak/yaak.rq_mw7sTmfuWv.yaml, frontend/src/types/seasons.ts. Stats: 7 files, +189/-16.
- 2025-12-10 5ef0e1d redesign card - Areas: api clients; frontend styles. Key files: yaak/yaak.ev_oSVAy5tzxD.yaml, yaak/yaak.rq_mw7sTmfuWv.yaml. Stats: 3 files, +28/-5.
- 2025-12-10 7da4510 update yaak - Areas: api clients. Key files: yaak/yaak.ev_5GCmwrqtBK.yaml, yaak/yaak.ev_dKdQHV4TQf.yaml. Stats: 3 files, +37/-5.
- 2025-12-10 1fd083d fix news data end date - Areas: IDE config; frontend utilities. Key files: frontend/src/data/news.json. Stats: 2 files, +3/-3.
## 2025-12-14 (v0.0.0+2025-12-14)
- 2025-12-14 8765954 style: update link color to primary - Areas: frontend styles. Key files: frontend/src/styles/globals.css. Stats: 1 files, +4/-0.
## 2025-12-16 (v0.0.0+2025-12-16)
- 2025-12-16 02ea69e query files - Areas: IDE config. Key files: .idea/dataSources.xml, .idea/sqldialects.xml. Stats: 5 files, +14/-1.
## 2026-01-07 (v0.0.0+2026-01-07)
- 2026-01-07 58bffca update yaak - use domekeeper as default appid - Areas: api clients. Key files: yaak/yaak.ev_oSVAy5tzxD.yaml. Stats: 1 files, +2/-2.
- 2026-01-07 75e5225 Merge remote-tracking branch 'origin/main' - Merge commit; no file changes recorded.
- 2026-01-07 661b187 update yaak - use domekeeper as default appid - Areas: api clients. Key files: yaak/yaak.ev_oSVAy5tzxD.yaml. Stats: 1 files, +2/-2.
- 2026-01-07 968367a update next - Areas: frontend config/deps. Key files: frontend/package.json. Stats: 2 files, +227/-112.
## 2026-01-17 (v0.0.0+2026-01-17)
- 2026-01-17 1cf5b3a blocking set mode - Areas: frontend config/deps; IDE config; frontend components. Key files: frontend/package.json, frontend/app/layout.tsx. Stats: 5 files, +87/-74.
- 2026-01-17 ef52d04 update skeleton to match page visuals - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/[round]/loading.tsx. Stats: 1 files, +42/-5.
- 2026-01-17 06f66cc fix rolling hitrate graph - Areas: frontend components. Key files: frontend/src/components/performance/RollingHitRateSpark.tsx. Stats: 1 files, +37/-18.
- 2026-01-17 04468a5 try to fix prerender - Areas: frontend routes/pages; frontend assets. Key files: frontend/app/head.tsx, frontend/app/layout.tsx. Stats: 3 files, +32/-7.
- 2026-01-17 07f9c12 seo update - Areas: frontend routes/pages; frontend config/deps; frontend utilities. Key files: frontend/next.config.ts, frontend/app/page.tsx. Stats: 14 files, +216/-52.
- 2026-01-17 b02114d try to fix build error - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/archive/page.tsx, frontend/app/review-guesser/archive/[date]/page.tsx. Stats: 2 files, +49/-27.
- 2026-01-17 6b05e7a spring boot 4.0.1 - Areas: IDE config; backend build/deps; docker. Key files: Dockerfile.coolify, backend/build.gradle.kts. Stats: 3 files, +5/-5.
- 2026-01-17 74d7331 try to fix build issue - Areas: frontend routes/pages. Key files: frontend/app/review-guesser/seasons/page.tsx. Stats: 1 files, +40/-8.
- 2026-01-17 887b052 add caching to leaderboard - Areas: backend config; backend controllers; backend repositories. Key files: backend/src/main/java/org/steam5/config/CacheConfig.java, backend/src/main/java/org/steam5/web/LeaderboardController.java. Stats: 4 files, +122/-15.
## 2026-01-20 (v0.0.0+2026-01-20)
- 2026-01-20 c6911a0 implement login modal info on round 1 (#38) - Areas: frontend components; frontend config/deps; cursor rules/plans. Key files: frontend/package.json, frontend/src/components/GuessButtons.tsx. Stats: 7 files, +332/-62.
