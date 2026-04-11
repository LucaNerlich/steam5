# Changelog

All notable changes to this project will be documented in this file.

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
