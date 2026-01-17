---
name: leaderboard-batching-caching
overview: Batch leaderboard user/streak data to remove N+1 queries and add cache regions with 60s/10m TTLs for leaderboard endpoints.
todos:
  - id: repo-batch-streak-query
    content: Add UserDateRow projection + batch streak query in GuessRepository
    status: completed
  - id: controller-batch-data
    content: Refactor LeaderboardController to batch users and streak dates
    status: completed
  - id: cache-regions-annotations
    content: Add leaderboard caches and @Cacheable annotations
    status: completed
  - id: verify-streak-logic
    content: Update streak calculation to use prefetched date lists
    status: completed
---

# Leaderboard Batching and Caching Plan

## Scope

- Refactor leaderboard aggregation to batch-load users and streak dates in `LeaderboardController`.
- Add a new batch streak query and projection to `GuessRepository`.
- Add two new cache regions in `CacheConfig` and annotate leaderboard endpoints with `@Cacheable`.

## Key Files

- [backend/src/main/java/org/steam5/web/LeaderboardController.java](backend/src/main/java/org/steam5/web/LeaderboardController.java)
- [backend/src/main/java/org/steam5/repository/GuessRepository.java](backend/src/main/java/org/steam5/repository/GuessRepository.java)
- [backend/src/main/java/org/steam5/config/CacheConfig.java](backend/src/main/java/org/steam5/config/CacheConfig.java)

## Plan

- Update `GuessRepository` to add a projection `UserDateRow` and a new JPQL query selecting distinct `steamId`/`gameDate` pairs for a list of steam IDs with `gameDate <= :asOfDate`, ordered by `steamId` and `gameDate` descending. This will enable a single batch streak query.
- Refactor `LeaderboardController` request flow so each endpoint does: (1) fetch guesses, (2) collect unique steam IDs, (3) batch fetch users with `userRepository.findAllById(steamIds)` and map to `Map<String, User>`, (4) batch fetch streak dates once, group to `Map<String, List<LocalDate>>`, (5) build entries using pre-fetched maps. This removes per-user DB calls in loops.
- Adjust `buildEntries(...)` and `getLeaderEntry(...)` to accept pre-fetched `User` and pre-fetched date lists, and refactor `calculateStreak(List<LocalDate> dates, LocalDate asOfDate)` to operate purely in-memory (no repository calls).
- Add leaderboard cache regions in `CacheConfig` using Caffeine:
- `leaderboard-live` with `expireAfterWrite(60, TimeUnit.SECONDS)`.
- `leaderboard-static` with `expireAfterWrite(10, TimeUnit.MINUTES)`.
- Annotate endpoints in `LeaderboardController` with `@Cacheable` using endpoint-specific keys and the endpoint date parameter (e.g., today pick date plus floating flag), and use `unless` guards consistent with existing controllers.
- Keep cache eviction TTL-based only (no scheduled eviction), since 60s/10m TTLs handle freshness; no explicit eviction in submission flow.

## Notes

- As-of date for streaks will follow the endpoint date (today’s pick date) per your guidance.