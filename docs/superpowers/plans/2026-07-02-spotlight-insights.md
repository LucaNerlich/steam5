# Player Spotlight: Expanded Insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four new Player Spotlight insight tiers (`BEST_DAY_EVER`, `BEAT_THE_ODDS`, `WELCOME_BACK`, `MOST_IMPROVED`) and replace the current strict priority ladder with a lottery among the "competitive" tiers so the ambient `DAY_STREAK` tier stops dominating the daily pick.

**Architecture:** `PlayerSpotlightService.compute()` gathers every tier in a fixed "competitive pool" that has ≥1 qualifying candidate that day, then draws one tier uniformly at random (date-seeded) before picking a candidate within it (existing tie-break, same `Random` instance). If no competitive tier qualifies, it falls through to the existing `WEEKLY_ACHIEVEMENT` then `MILESTONE` fallbacks, unchanged. All new tiers reuse existing `GuessRepository` queries — no schema changes.

**Tech Stack:** Spring Boot / Java 21 backend (Gradle), JUnit 5 + Mockito for tests, Next.js/TypeScript frontend.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-02-spotlight-insights-design.md` — read it before starting if anything below is ambiguous.
- Competitive pool, in the fixed evaluation order used for the lottery's deterministic tier list: `DAY_STREAK`, `BEST_DAY_EVER`, `BEAT_THE_ODDS`, `WELCOME_BACK`, `MOST_IMPROVED`, `HOT_STREAK`.
- Fallbacks below the pool, unchanged order: `WEEKLY_ACHIEVEMENT`, then `MILESTONE`.
- Lottery mechanism: build the list of qualifying tiers in the fixed order above; if non-empty, `final Random random = new Random(today.toEpochDay());` then `random.nextInt(qualifying.size())` picks the tier, and the **same** `random` instance is then passed into `pickOne(...)` to pick the candidate. `WEEKLY_ACHIEVEMENT`/`MILESTONE` each get their own fresh `new Random(today.toEpochDay())` when used (matches current behavior — they're not part of the lottery).
- "Yesterday"-based tiers use `today.minusDays(1)`, never `today` — the nightly job runs at 00:15 UTC, before any of today's rounds exist.
- Thresholds (exact values, from the spec):
  - `BEST_DAY_EVER`: yesterday's point total > every prior day's total; requires ≥2 prior days played.
  - `BEAT_THE_ODDS`: yesterday's hardest round (lowest avg score across all players, min 5 players, from `findRoundAvgScoresInRange`) must have avg score < 2.0; candidate qualifies if their own points on that round were ≥4.
  - `WELCOME_BACK`: gap between the two most recent distinct play-dates ≥4 days; average points on the return day ≥3.0.
  - `MOST_IMPROVED`: last-30-day avg ≥15% relatively higher AND ≥0.3 points higher than the prior-30-day avg (days 31–60 back); requires ≥10 rounds in each window.
  - `MILESTONE` text polish: round-count milestones `{100, 250, 500, 1000}`, lifetime-point milestones `{1000, 5000, 10000}`, "close enough" window = within 5.
- **Mockito trap (applies to every test step in this plan):** never build a mock (or call `when(...)` on one) as an inline argument to another pending `when(...).thenReturn(...)` call — it corrupts Mockito's ongoing-stubbing state and throws `UnfinishedStubbingException`. Always assign mocks to a local variable first, complete their stubbing, and only then pass the variable into an outer `when(...).thenReturn(...)`.
- Test command: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"` for the fast loop, `sh gradlew test` for full regression.

---

### Task 1: Refactor selection to a lottery (no new tiers yet)

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`

**Interfaces:**
- Produces: `pickOne(List<Tiered> tier, Random random)` (signature change from `pickOne(List<Tiered> tier, LocalDate today)`) — later tasks call this with an externally-supplied `Random`.
- Produces: `private record QualifyingTier(PlayerSpotlightInsightType type, List<Tiered> candidates)` and `private void addIfQualifying(List<QualifyingTier> qualifying, PlayerSpotlightInsightType type, List<Tiered> candidates)` — later tasks call `addIfQualifying` once per new tier.
- Consumes: nothing new; `evaluateDayStreakTier`, `evaluateHotStreakTier`, `evaluateWeeklyAchievementTier`, `evaluateMilestoneTier` keep their current signatures for this task.

This task only changes *how* the existing three real tiers (`DAY_STREAK`, `HOT_STREAK`, `WEEKLY_ACHIEVEMENT`) and the `MILESTONE` fallback are wired together. No new insight types yet — this isolates the riskiest structural change so it can be verified against the full existing test suite before adding new tiers on top.

- [ ] **Step 1: Replace `pickOne` to accept an external `Random`**

Find this method in `PlayerSpotlightService.java`:

```java
    /** Stable-for-the-day, rotating-by-date pick among candidates tied in the same tier. */
    private Tiered pickOne(final List<Tiered> tier, final LocalDate today) {
        final List<Tiered> sorted = tier.stream()
                .sorted(Comparator.comparing(Tiered::steamId))
                .toList();
        final Random random = new Random(today.toEpochDay());
        return sorted.get(random.nextInt(sorted.size()));
    }
```

Replace it with:

```java
    /** Stable-for-the-day pick among candidates tied in the same tier, using the caller's Random. */
    private Tiered pickOne(final List<Tiered> tier, final Random random) {
        final List<Tiered> sorted = tier.stream()
                .sorted(Comparator.comparing(Tiered::steamId))
                .toList();
        return sorted.get(random.nextInt(sorted.size()));
    }
```

- [ ] **Step 2: Replace `compute()` with the lottery-driven version**

Find this method:

```java
    private Optional<PlayerSpotlight> compute(final LocalDate today) {
        final List<Candidate> eligible = findEligibleCandidates(today);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        final List<Tiered> dayStreakTier = evaluateDayStreakTier(eligible, today);
        if (!dayStreakTier.isEmpty()) {
            return Optional.of(toEntity(today, pickOne(dayStreakTier, today)));
        }

        final List<Tiered> achievementTier = evaluateWeeklyAchievementTier(eligible);
        if (!achievementTier.isEmpty()) {
            return Optional.of(toEntity(today, pickOne(achievementTier, today)));
        }

        final List<Tiered> hotStreakTier = evaluateHotStreakTier(eligible, today);
        if (!hotStreakTier.isEmpty()) {
            return Optional.of(toEntity(today, pickOne(hotStreakTier, today)));
        }

        // Guaranteed fallback: always show someone among the eligible pool.
        final List<Tiered> milestoneTier = evaluateMilestoneTier(eligible);
        return Optional.of(toEntity(today, pickOne(milestoneTier, today)));
    }
```

Replace it with:

```java
    private Optional<PlayerSpotlight> compute(final LocalDate today) {
        final List<Candidate> eligible = findEligibleCandidates(today);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        // "Competitive pool": every tier here that has >=1 qualifying candidate goes
        // into a lottery, so no single ambient tier (e.g. DAY_STREAK) can dominate
        // just because it's the easiest to qualify for on any given day.
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.HOT_STREAK, evaluateHotStreakTier(eligible, today));

        if (!qualifying.isEmpty()) {
            final Random random = new Random(today.toEpochDay());
            final QualifyingTier chosen = qualifying.get(random.nextInt(qualifying.size()));
            return Optional.of(toEntity(today, pickOne(chosen.candidates(), random)));
        }

        final List<Tiered> achievementTier = evaluateWeeklyAchievementTier(eligible);
        if (!achievementTier.isEmpty()) {
            return Optional.of(toEntity(today, pickOne(achievementTier, new Random(today.toEpochDay()))));
        }

        // Guaranteed fallback: always show someone among the eligible pool.
        final List<Tiered> milestoneTier = evaluateMilestoneTier(eligible);
        return Optional.of(toEntity(today, pickOne(milestoneTier, new Random(today.toEpochDay()))));
    }

    private void addIfQualifying(final List<QualifyingTier> qualifying, final PlayerSpotlightInsightType type,
                                  final List<Tiered> candidates) {
        if (!candidates.isEmpty()) {
            qualifying.add(new QualifyingTier(type, candidates));
        }
    }
```

- [ ] **Step 3: Add the `QualifyingTier` record**

Find the `Tiered` record near the bottom of the file:

```java
    private record Tiered(String steamId, PlayerSpotlightInsightType insightType, String headline, String detail,
                           String statLabel, Double statValue) {
    }
```

Add this new record directly after it:

```java

    private record QualifyingTier(PlayerSpotlightInsightType type, List<Tiered> candidates) {
    }
```

- [ ] **Step 4: Run the existing test suite to confirm no regression**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 7 existing tests pass unchanged. (They pass because in every existing test's fixture, at most one of `DAY_STREAK`/`HOT_STREAK` ever qualifies for a given eligible pool, so the lottery has ≤1 entry and picks it deterministically — see the spec's "Edge cases" section.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/service/PlayerSpotlightService.java
git commit -m "$(cat <<'EOF'
refactor(spotlight): switch competitive tiers to a date-seeded lottery

DAY_STREAK sat at priority #1 and qualified for any player on an active
streak, so it would win on most days since regulars tend to maintain
one. Competitive tiers now go into a lottery instead of a strict
ladder; WEEKLY_ACHIEVEMENT and MILESTONE remain sequential fallbacks
below it, unchanged. No new tiers yet — mechanical refactor only.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add the `BEST_DAY_EVER` tier

**Files:**
- Modify: `backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java`
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`
- Modify: `backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java`

**Interfaces:**
- Consumes: `Candidate` record (`steamId()`, `allTime()`, `datesDesc()`) from Task 1's file; `GuessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc(String steamId)` (existing repo method, returns `List<Guess>`).
- Produces: `private List<Tiered> evaluateBestDayEverTier(List<Candidate> eligible, LocalDate yesterday)` — called from `compute()`.

- [ ] **Step 1: Add the enum constant**

In `PlayerSpotlightInsightType.java`, change:

```java
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
```

to:

```java
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
```

- [ ] **Step 2: Add a `Guess` builder helper and a default repository stub to the test**

In `PlayerSpotlightServiceTest.java`, add this import alongside the existing ones:

```java
import org.steam5.domain.Guess;
```

In `setUp()`, add one more default stub after the existing `findBySteamIdBetween` line:

```java
        when(guessRepository.findBySteamIdBetween(anyString(), any(), any())).thenReturn(List.of());
        when(guessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc(anyString())).thenReturn(List.of());
```

Add this helper method next to `dateRow`/`consecutiveDaysEnding`:

```java
    private Guess guess(String steamId, LocalDate date, int points) {
        final Guess g = new Guess();
        g.setSteamId(steamId);
        g.setGameDate(date);
        g.setPoints(points);
        return g;
    }
```

- [ ] **Step 3: Write the failing test**

Add to `PlayerSpotlightServiceTest.java`:

```java
    @Test
    void bestDayEverTierWinsWhenYesterdayIsANewPersonalRecord() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow recordBreaker = allTimeRow("recordBreaker", 100, 2.0);
        stubAllTimeStats(recordBreaker);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("recordBreaker", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final List<Guess> history = List.of(
                guess("recordBreaker", yesterday.minusDays(10), 8),
                guess("recordBreaker", yesterday.minusDays(5), 12),
                guess("recordBreaker", yesterday, 24)
        );
        when(guessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc("recordBreaker")).thenReturn(history);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("recordBreaker", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.BEST_DAY_EVER, captor.getValue().getInsightType());
    }
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest.bestDayEverTierWinsWhenYesterdayIsANewPersonalRecord"`
Expected: FAIL — falls through to `MILESTONE` instead (`BEST_DAY_EVER` doesn't exist as a code path yet), so `assertEquals(PlayerSpotlightInsightType.BEST_DAY_EVER, ...)` fails.

- [ ] **Step 5: Implement `evaluateBestDayEverTier` and wire it into `compute()`**

In `PlayerSpotlightService.java`, add this constant next to the other tier constants near the top of the class:

```java
    private static final int MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER = 2;
```

Add the new method next to `evaluateDayStreakTier`:

```java
    private List<Tiered> evaluateBestDayEverTier(final List<Candidate> eligible, final LocalDate yesterday) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = guessRepository.findBySteamIdOrderByGameDateDescRoundIndexAsc(c.steamId());
            final Map<LocalDate, Integer> dailyTotals = history.stream()
                    .collect(Collectors.groupingBy(Guess::getGameDate, Collectors.summingInt(Guess::getPoints)));

            final Integer yesterdayTotal = dailyTotals.get(yesterday);
            if (yesterdayTotal == null) continue;

            final List<Integer> priorTotals = dailyTotals.entrySet().stream()
                    .filter(e -> !e.getKey().equals(yesterday))
                    .map(Map.Entry::getValue)
                    .toList();
            if (priorTotals.size() < MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER) continue;

            final int previousBest = Collections.max(priorTotals);
            if (yesterdayTotal <= previousBest) continue;

            final String detail = String.format(
                    "Scored %d points yesterday — a new personal best, beating their previous high of %d.",
                    yesterdayTotal, previousBest);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.BEST_DAY_EVER,
                    "Best day ever!", detail, "Yesterday's points", (double) yesterdayTotal));
        }
        return tier;
    }
```

In `compute()`, add a `yesterday` local and one more `addIfQualifying` call. Change:

```java
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.HOT_STREAK, evaluateHotStreakTier(eligible, today));
```

to:

```java
        final LocalDate yesterday = today.minusDays(1);
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEST_DAY_EVER, evaluateBestDayEverTier(eligible, yesterday));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.HOT_STREAK, evaluateHotStreakTier(eligible, today));
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 8 tests pass (7 existing + the new one).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java \
        backend/src/main/java/org/steam5/service/PlayerSpotlightService.java \
        backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java
git commit -m "$(cat <<'EOF'
feat(spotlight): add BEST_DAY_EVER insight tier

Fires when yesterday's point total beats every one of the player's
prior days, using their full guess history.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Add the `BEAT_THE_ODDS` tier

**Files:**
- Modify: `backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java`
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`
- Modify: `backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java`

**Interfaces:**
- Consumes: `GuessRepository.findRoundAvgScoresInRange(LocalDate start, LocalDate end)` → `List<RoundAvgScoreRow>` (fields: `getGameDate()`, `getRoundIndex()`, `getAppId()`, `getAvgScore()`, `getPlayerCount()`, `getAppName()`); `GuessRepository.findBySteamIdAndGameDateAndRoundIndex(String steamId, LocalDate date, int roundIndex)` → `Optional<Guess>` (both existing repo methods).
- Produces: `private List<Tiered> evaluateBeatTheOddsTier(List<Candidate> eligible, LocalDate yesterday)` — called from `compute()`.

- [ ] **Step 1: Add the enum constant**

In `PlayerSpotlightInsightType.java`, add `BEAT_THE_ODDS` after `BEST_DAY_EVER`:

```java
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    BEAT_THE_ODDS,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
```

- [ ] **Step 2: Add a default repository stub**

In `PlayerSpotlightServiceTest.java`'s `setUp()`, add:

```java
        when(guessRepository.findRoundAvgScoresInRange(any(), any())).thenReturn(List.of());
```

Add this import if not already present: `import java.util.Optional;`

- [ ] **Step 3: Write the failing test**

```java
    @Test
    void beatTheOddsTierWinsWhenCandidateAcedTheHardestRoundOfTheDay() {
        final LocalDate yesterday = today.minusDays(1);
        final GuessRepository.AllTimeStatsRow oddsBeater = allTimeRow("oddsBeater", 100, 2.0);
        stubAllTimeStats(oddsBeater);

        final List<GuessRepository.UserDateRow> dates = consecutiveDaysEnding("oddsBeater", yesterday, 1);
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        final GuessRepository.RoundAvgScoreRow hardRound = mock(GuessRepository.RoundAvgScoreRow.class);
        when(hardRound.getGameDate()).thenReturn(yesterday);
        when(hardRound.getRoundIndex()).thenReturn(3);
        when(hardRound.getAvgScore()).thenReturn(1.2);
        when(hardRound.getPlayerCount()).thenReturn(20L);
        when(guessRepository.findRoundAvgScoresInRange(yesterday, yesterday)).thenReturn(List.of(hardRound));

        final Guess theirGuess = guess("oddsBeater", yesterday, 5);
        theirGuess.setRoundIndex(3);
        when(guessRepository.findBySteamIdAndGameDateAndRoundIndex("oddsBeater", yesterday, 3))
                .thenReturn(Optional.of(theirGuess));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("oddsBeater", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.BEAT_THE_ODDS, captor.getValue().getInsightType());
    }
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest.beatTheOddsTierWinsWhenCandidateAcedTheHardestRoundOfTheDay"`
Expected: FAIL — falls through to `MILESTONE` since `BEAT_THE_ODDS` isn't wired up yet.

- [ ] **Step 5: Implement `evaluateBeatTheOddsTier` and wire it into `compute()`**

Add these constants next to `MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER`:

```java
    private static final double HARD_ROUND_MAX_AVG_SCORE = 2.0;
    private static final int BEAT_THE_ODDS_MIN_POINTS = 4;
```

Add the method next to `evaluateBestDayEverTier`:

```java
    private List<Tiered> evaluateBeatTheOddsTier(final List<Candidate> eligible, final LocalDate yesterday) {
        final List<GuessRepository.RoundAvgScoreRow> rows = guessRepository.findRoundAvgScoresInRange(yesterday, yesterday);
        final Optional<GuessRepository.RoundAvgScoreRow> hardestRound = rows.stream()
                .min(Comparator.comparing(GuessRepository.RoundAvgScoreRow::getAvgScore));
        if (hardestRound.isEmpty() || hardestRound.get().getAvgScore() >= HARD_ROUND_MAX_AVG_SCORE) {
            return List.of();
        }

        final int roundIndex = hardestRound.get().getRoundIndex();
        final double hardAvg = hardestRound.get().getAvgScore();

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final Optional<Guess> theirGuess =
                    guessRepository.findBySteamIdAndGameDateAndRoundIndex(c.steamId(), yesterday, roundIndex);
            if (theirGuess.isEmpty() || theirGuess.get().getPoints() < BEAT_THE_ODDS_MIN_POINTS) continue;

            final String detail = String.format(
                    "Nailed yesterday's toughest round (round %d, %.1f avg pts across all players) with %d points.",
                    roundIndex, hardAvg, theirGuess.get().getPoints());
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.BEAT_THE_ODDS,
                    "Beat the odds!", detail, "Round points", (double) theirGuess.get().getPoints()));
        }
        return tier;
    }
```

In `compute()`, add one more `addIfQualifying` call, right after the `BEST_DAY_EVER` line:

```java
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEAT_THE_ODDS, evaluateBeatTheOddsTier(eligible, yesterday));
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 9 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java \
        backend/src/main/java/org/steam5/service/PlayerSpotlightService.java \
        backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java
git commit -m "$(cat <<'EOF'
feat(spotlight): add BEAT_THE_ODDS insight tier

Fires when a player scored >=4 points on yesterday's hardest round
(lowest game-wide average, <2.0/5) across all players.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Add the `WELCOME_BACK` tier

**Files:**
- Modify: `backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java`
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`
- Modify: `backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java`

**Interfaces:**
- Consumes: `Candidate.datesDesc()` (already fetched, unbounded history) and `GuessRepository.findAllForDay(String steamId, LocalDate date)` → `List<Guess>` (existing repo method).
- Produces: `private List<Tiered> evaluateWelcomeBackTier(List<Candidate> eligible)` — called from `compute()`.

- [ ] **Step 1: Add the enum constant**

```java
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    BEAT_THE_ODDS,
    WELCOME_BACK,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
```

- [ ] **Step 2: Add a default repository stub**

In `setUp()`:

```java
        when(guessRepository.findAllForDay(anyString(), any())).thenReturn(List.of());
```

- [ ] **Step 3: Write the failing test**

```java
    @Test
    void welcomeBackTierWinsWhenCandidateReturnedAfterAGapAndPlayedWell() {
        final LocalDate mostRecent = today.minusDays(1);
        final LocalDate beforeGap = mostRecent.minusDays(6); // gap of 6 days, >= the 4-day threshold

        final GuessRepository.AllTimeStatsRow returner = allTimeRow("returner", 100, 2.0);
        stubAllTimeStats(returner);

        final List<GuessRepository.UserDateRow> dates = new ArrayList<>();
        dates.add(dateRow("returner", mostRecent));
        dates.add(dateRow("returner", beforeGap));
        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today))).thenReturn(dates);

        when(guessRepository.findAllForDay("returner", mostRecent)).thenReturn(List.of(
                guess("returner", mostRecent, 4),
                guess("returner", mostRecent, 3)
        ));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("returner", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.WELCOME_BACK, captor.getValue().getInsightType());
    }
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest.welcomeBackTierWinsWhenCandidateReturnedAfterAGapAndPlayedWell"`
Expected: FAIL — falls through to `MILESTONE`.

- [ ] **Step 5: Implement `evaluateWelcomeBackTier` and wire it into `compute()`**

Add this import to `PlayerSpotlightService.java`:

```java
import java.time.temporal.ChronoUnit;
```

Add these constants:

```java
    private static final int WELCOME_BACK_MIN_GAP_DAYS = 4;
    private static final double WELCOME_BACK_MIN_RETURN_DAY_AVG = 3.0;
```

Add the method next to `evaluateBeatTheOddsTier`:

```java
    private List<Tiered> evaluateWelcomeBackTier(final List<Candidate> eligible) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<LocalDate> datesDesc = c.datesDesc();
            if (datesDesc.size() < 2) continue;

            final LocalDate mostRecent = datesDesc.get(0);
            final LocalDate previous = datesDesc.get(1);
            final long gapDays = ChronoUnit.DAYS.between(previous, mostRecent);
            if (gapDays < WELCOME_BACK_MIN_GAP_DAYS) continue;

            final List<Guess> returnDayGuesses = guessRepository.findAllForDay(c.steamId(), mostRecent);
            final double returnDayAvg = returnDayGuesses.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            if (returnDayAvg < WELCOME_BACK_MIN_RETURN_DAY_AVG) continue;

            final String detail = String.format(
                    "Took a %d-day break and came back strong, averaging %.1f pts/round on their return.",
                    gapDays, returnDayAvg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.WELCOME_BACK,
                    "Welcome back!", detail, "Days away", (double) gapDays));
        }
        return tier;
    }
```

In `compute()`, add one more `addIfQualifying` call, right after the `BEAT_THE_ODDS` line:

```java
        addIfQualifying(qualifying, PlayerSpotlightInsightType.WELCOME_BACK, evaluateWelcomeBackTier(eligible));
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 10 tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java \
        backend/src/main/java/org/steam5/service/PlayerSpotlightService.java \
        backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java
git commit -m "$(cat <<'EOF'
feat(spotlight): add WELCOME_BACK insight tier

Fires when a player's two most recent play-dates have a >=4 day gap
and they averaged >=3.0 pts/round on their return day.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Add the `MOST_IMPROVED` tier

**Files:**
- Modify: `backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java`
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`
- Modify: `backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java`

**Interfaces:**
- Consumes: `GuessRepository.findBySteamIdBetween(String steamId, LocalDate start, LocalDate end)` (existing, already used by `evaluateHotStreakTier`).
- Produces: `private List<Tiered> evaluateMostImprovedTier(List<Candidate> eligible, LocalDate today)` — called from `compute()`.

- [ ] **Step 1: Add the enum constant**

```java
public enum PlayerSpotlightInsightType {
    DAY_STREAK,
    BEST_DAY_EVER,
    BEAT_THE_ODDS,
    WELCOME_BACK,
    MOST_IMPROVED,
    WEEKLY_ACHIEVEMENT,
    HOT_STREAK,
    MILESTONE
}
```

- [ ] **Step 2: Write the failing test**

No new default stub needed — `findBySteamIdBetween` already defaults to `List.of()` from Task 1's predecessor setup.

```java
    @Test
    void mostImprovedTierWinsWhenRecentFormIsClearlyBetterThanBefore() {
        final GuessRepository.AllTimeStatsRow improver = allTimeRow("improver", 100, 2.0);
        stubAllTimeStats(improver);

        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today)))
                .thenReturn(consecutiveDaysEnding("improver", today, 1));

        final LocalDate last30Start = today.minusDays(30);
        final LocalDate last30End = today.minusDays(1);
        final LocalDate prior30Start = today.minusDays(60);
        final LocalDate prior30End = today.minusDays(31);

        final List<Guess> last30 = new ArrayList<>();
        for (int i = 0; i < 15; i++) last30.add(guess("improver", last30Start.plusDays(i), 4));
        final List<Guess> prior30 = new ArrayList<>();
        for (int i = 0; i < 15; i++) prior30.add(guess("improver", prior30Start.plusDays(i), 2));

        when(guessRepository.findBySteamIdBetween("improver", last30Start, last30End)).thenReturn(last30);
        when(guessRepository.findBySteamIdBetween("improver", prior30Start, prior30End)).thenReturn(prior30);

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals("improver", captor.getValue().getSteamId());
        assertEquals(PlayerSpotlightInsightType.MOST_IMPROVED, captor.getValue().getInsightType());
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest.mostImprovedTierWinsWhenRecentFormIsClearlyBetterThanBefore"`
Expected: FAIL — falls through to `MILESTONE`.

- [ ] **Step 4: Implement `evaluateMostImprovedTier` and wire it into `compute()`**

Add these constants:

```java
    private static final int MOST_IMPROVED_WINDOW_DAYS = 30;
    private static final int MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW = 10;
    private static final double MOST_IMPROVED_RELATIVE_THRESHOLD = 1.15;
    private static final double MOST_IMPROVED_ABSOLUTE_DELTA = 0.3;
```

Add the method next to `evaluateWelcomeBackTier`:

```java
    private List<Tiered> evaluateMostImprovedTier(final List<Candidate> eligible, final LocalDate today) {
        final LocalDate last30Start = today.minusDays(MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate last30End = today.minusDays(1);
        final LocalDate prior30Start = today.minusDays(2L * MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate prior30End = today.minusDays(MOST_IMPROVED_WINDOW_DAYS + 1L);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> last30 = guessRepository.findBySteamIdBetween(c.steamId(), last30Start, last30End);
            final List<Guess> prior30 = guessRepository.findBySteamIdBetween(c.steamId(), prior30Start, prior30End);
            if (last30.size() < MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW
                    || prior30.size() < MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW) {
                continue;
            }

            final double last30Avg = last30.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final double prior30Avg = prior30.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final boolean qualifies = last30Avg >= prior30Avg * MOST_IMPROVED_RELATIVE_THRESHOLD
                    && (last30Avg - prior30Avg) >= MOST_IMPROVED_ABSOLUTE_DELTA;
            if (!qualifies) continue;

            final String detail = String.format(
                    "Leveled up: averaging %.1f pts/round over the last %d days, up from %.1f the month before.",
                    last30Avg, MOST_IMPROVED_WINDOW_DAYS, prior30Avg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MOST_IMPROVED,
                    "Most improved!", detail, "Last-30d avg", last30Avg));
        }
        return tier;
    }
```

In `compute()`, add one more `addIfQualifying` call, right after the `WELCOME_BACK` line and before the `HOT_STREAK` line:

```java
        addIfQualifying(qualifying, PlayerSpotlightInsightType.MOST_IMPROVED, evaluateMostImprovedTier(eligible, today));
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 11 tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/PlayerSpotlightInsightType.java \
        backend/src/main/java/org/steam5/service/PlayerSpotlightService.java \
        backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java
git commit -m "$(cat <<'EOF'
feat(spotlight): add MOST_IMPROVED insight tier

Fires when a player's last-30-day average is >=15% and >=0.3 points
higher than their prior-30-day average, with >=10 rounds in each
window to avoid noise.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Polish the `MILESTONE` fallback text

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/PlayerSpotlightService.java`
- Modify: `backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java`

**Interfaces:**
- Consumes: `Candidate.allTime()` → `GuessRepository.AllTimeStatsRow` (`getRounds()`, `getTotalPoints()`, `getAvgPoints()`, all existing).
- Produces: no new public surface — `evaluateMilestoneTier`'s generated `detail` text changes for candidates near a milestone; signature unchanged.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void milestoneTierUsesNiceNumberFramingWhenRoundsAreCloseToAMilestone() {
        final GuessRepository.AllTimeStatsRow almostCentury = allTimeRow("almostCentury", 98, 2.0);
        when(almostCentury.getTotalPoints()).thenReturn(400L);
        stubAllTimeStats(almostCentury);

        when(guessRepository.findDistinctDatesUpToForUsers(anyList(), eq(today)))
                .thenReturn(consecutiveDaysEnding("almostCentury", today, 1));

        service.computeAndPersistForToday();

        final ArgumentCaptor<PlayerSpotlight> captor = ArgumentCaptor.forClass(PlayerSpotlight.class);
        verify(playerSpotlightRepository).save(captor.capture());
        assertEquals(PlayerSpotlightInsightType.MILESTONE, captor.getValue().getInsightType());
        assertEquals("Closing in on 100 rounds — only 2 away!", captor.getValue().getDetail());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest.milestoneTierUsesNiceNumberFramingWhenRoundsAreCloseToAMilestone"`
Expected: FAIL — current text is `"Has played 98 rounds and counting, averaging 2.0 pts/round."`.

- [ ] **Step 3: Replace `evaluateMilestoneTier` with the nice-number version**

Find this method:

```java
    private List<Tiered> evaluateMilestoneTier(final List<Candidate> eligible) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final double avgPoints = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;
            final String detail = String.format(
                    "Has played %d rounds and counting, averaging %.1f pts/round.",
                    c.allTime().getRounds(), avgPoints);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MILESTONE,
                    "A steady presence!", detail, "Rounds played", (double) c.allTime().getRounds()));
        }
        return tier;
    }
```

Replace it with:

```java
    private static final List<Long> ROUND_MILESTONES = List.of(100L, 250L, 500L, 1000L);
    private static final List<Long> POINTS_MILESTONES = List.of(1000L, 5000L, 10000L);
    private static final long MILESTONE_TRAILING_WINDOW = 5;

    private List<Tiered> evaluateMilestoneTier(final List<Candidate> eligible) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final long rounds = c.allTime().getRounds();
            final long totalPoints = c.allTime().getTotalPoints() != null ? c.allTime().getTotalPoints() : 0L;
            final double avgPoints = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;

            final String detail = nearestMilestoneDetail(rounds, ROUND_MILESTONES, "rounds")
                    .or(() -> nearestMilestoneDetail(totalPoints, POINTS_MILESTONES, "lifetime points"))
                    .orElseGet(() -> String.format("Has played %d rounds and counting, averaging %.1f pts/round.",
                            rounds, avgPoints));

            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MILESTONE,
                    "A steady presence!", detail, "Rounds played", (double) rounds));
        }
        return tier;
    }

    private Optional<String> nearestMilestoneDetail(final long value, final List<Long> milestones, final String unitLabel) {
        return milestones.stream()
                .filter(milestone -> Math.abs(value - milestone) <= MILESTONE_TRAILING_WINDOW)
                .findFirst()
                .map(milestone -> value >= milestone
                        ? String.format("Just crossed %,d %s — now at %,d!", milestone, unitLabel, value)
                        : String.format("Closing in on %,d %s — only %,d away!", milestone, unitLabel, milestone - value));
    }
```

**Important:** the constants `ROUND_MILESTONES`, `POINTS_MILESTONES`, and `MILESTONE_TRAILING_WINDOW` must be moved up next to the other `private static final` constants near the top of the class (Java requires field declarations before use is not actually required, but keep all tier constants grouped together for readability — this is a style nit, not a compile requirement).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && sh gradlew test --tests "org.steam5.service.PlayerSpotlightServiceTest"`
Expected: `BUILD SUCCESSFUL`, all 12 tests pass.

- [ ] **Step 5: Run the full backend test suite**

Run: `cd backend && sh gradlew test`
Expected: `BUILD SUCCESSFUL` (confirms nothing outside `PlayerSpotlightServiceTest` was affected).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/service/PlayerSpotlightService.java \
        backend/src/test/java/org/steam5/service/PlayerSpotlightServiceTest.java
git commit -m "$(cat <<'EOF'
feat(spotlight): use nice-number framing for the MILESTONE fallback

Leads with a round-count or lifetime-points milestone (100/250/500/
1000 rounds; 1000/5000/10000 points) when the player is within 5 of
one, instead of always showing the generic "has played N rounds" text.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Frontend support for the four new insight types

**Files:**
- Modify: `frontend/src/components/PlayerSpotlight.tsx`
- Modify: `frontend/src/styles/components/playerSpotlight.css`

**Interfaces:**
- Consumes: the backend's `PlayerSpotlightInsightType` enum values (now includes `BEST_DAY_EVER`, `BEAT_THE_ODDS`, `WELCOME_BACK`, `MOST_IMPROVED`), serialized as the `insightType` string field of `GET /api/stats/spotlight/today`.

- [ ] **Step 1: Extend the `InsightType` union and lookup maps**

In `PlayerSpotlight.tsx`, find:

```tsx
type InsightType = "DAY_STREAK" | "WEEKLY_ACHIEVEMENT" | "HOT_STREAK" | "MILESTONE";
```

Replace with:

```tsx
type InsightType =
    | "DAY_STREAK"
    | "BEST_DAY_EVER"
    | "BEAT_THE_ODDS"
    | "WELCOME_BACK"
    | "MOST_IMPROVED"
    | "WEEKLY_ACHIEVEMENT"
    | "HOT_STREAK"
    | "MILESTONE";
```

Find:

```tsx
const INSIGHT_MODIFIER: Record<InsightType, string> = {
    DAY_STREAK: "day-streak",
    WEEKLY_ACHIEVEMENT: "weekly-achievement",
    HOT_STREAK: "hot-streak",
    MILESTONE: "milestone",
};

const INSIGHT_EMOJI: Record<InsightType, string> = {
    DAY_STREAK: "🔥",
    WEEKLY_ACHIEVEMENT: "🏅",
    HOT_STREAK: "📈",
    MILESTONE: "⭐",
};
```

Replace with:

```tsx
const INSIGHT_MODIFIER: Record<InsightType, string> = {
    DAY_STREAK: "day-streak",
    BEST_DAY_EVER: "best-day-ever",
    BEAT_THE_ODDS: "beat-the-odds",
    WELCOME_BACK: "welcome-back",
    MOST_IMPROVED: "most-improved",
    WEEKLY_ACHIEVEMENT: "weekly-achievement",
    HOT_STREAK: "hot-streak",
    MILESTONE: "milestone",
};

const INSIGHT_EMOJI: Record<InsightType, string> = {
    DAY_STREAK: "🔥",
    BEST_DAY_EVER: "🏆",
    BEAT_THE_ODDS: "🎯",
    WELCOME_BACK: "👋",
    MOST_IMPROVED: "📊",
    WEEKLY_ACHIEVEMENT: "🏅",
    HOT_STREAK: "📈",
    MILESTONE: "⭐",
};
```

- [ ] **Step 2: Add CSS accent classes for the four new types**

In `playerSpotlight.css`, find:

```css
.player-spotlight--milestone {
    border-color: var(--color-border);
}
```

Add these four rules directly after it:

```css
.player-spotlight--best-day-ever {
    border-color: color-mix(in srgb, var(--color-score-close) 45%, var(--color-border));
}

.player-spotlight--beat-the-odds {
    border-color: color-mix(in srgb, var(--color-score-near) 45%, var(--color-border));
}

.player-spotlight--welcome-back {
    border-color: color-mix(in srgb, var(--color-success) 35%, var(--color-border));
}

.player-spotlight--most-improved {
    border-color: color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
}
```

- [ ] **Step 3: Type-check the frontend**

Run: `cd frontend && npx tsc --noEmit -p tsconfig.json`
Expected: no output (no type errors).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/PlayerSpotlight.tsx frontend/src/styles/components/playerSpotlight.css
git commit -m "$(cat <<'EOF'
feat(spotlight): render the four new insight types on the frontend

Adds emoji + accent-color mappings for BEST_DAY_EVER, BEAT_THE_ODDS,
WELCOME_BACK, and MOST_IMPROVED, matching the existing card styling
conventions.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Full regression, manual verification against real dev data, push

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && sh gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Manually verify against real dev data**

Create a temporary throwaway test file `backend/src/test/java/org/steam5/ManualPlayerSpotlightVerification.java`:

```java
package org.steam5;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.steam5.domain.GameDate;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.service.PlayerSpotlightService;

@SpringBootTest
class ManualPlayerSpotlightVerification {

    @Autowired
    private PlayerSpotlightService playerSpotlightService;

    @Autowired
    private PlayerSpotlightRepository playerSpotlightRepository;

    @Test
    void computeAgainstRealDevData() {
        playerSpotlightRepository.deleteAll();
        playerSpotlightService.computeAndPersistForToday();
        playerSpotlightRepository.findById(GameDate.todayUtc())
                .ifPresentOrElse(
                        s -> System.out.println("MANUAL_VERIFY_RESULT: " + s),
                        () -> System.out.println("MANUAL_VERIFY_RESULT: <none - no eligible candidate>")
                );
    }
}
```

Run: `cd backend && sh gradlew test --tests "org.steam5.ManualPlayerSpotlightVerification" -i 2>&1 | grep MANUAL_VERIFY_RESULT`
Expected: one line printing a `PlayerSpotlight(...)` with a plausible `insightType` for a real dev-database player (the specific tier depends on current dev data — any of the 8 tiers is a valid outcome).

Delete the temporary file afterward: `rm backend/src/test/java/org/steam5/ManualPlayerSpotlightVerification.java` (do not commit it).

- [ ] **Step 3: Manually verify the API + frontend rendering**

Start the backend: `cd backend && sh gradlew bootRun` (background it; wait for "Started Steam5Application" in the log).
Run: `curl -s http://localhost:8080/api/stats/spotlight/today`
Expected: JSON body with an `insightType` field matching one of the 8 enum values, and non-empty `headline`/`detail`.

Start the frontend: `cd frontend && npm run dev` (background it).
Run: `curl -s http://localhost:3000/review-guesser/1 | grep -o 'player-spotlight--[a-z-]*'`
Expected: one `player-spotlight--<modifier>` class matching the tier from the API response (e.g. `player-spotlight--best-day-ever`).

Stop both dev servers when done (`pkill -f "org.steam5.Steam5Application"`, `pkill -f "next dev"`).

- [ ] **Step 4: Push**

```bash
git push
```

Expected: pushes all commits from Tasks 1–7 (this task has no commit of its own — verification only) to the current branch's upstream.
