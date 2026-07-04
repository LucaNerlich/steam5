package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.PlayerSpotlight;
import org.steam5.domain.PlayerSpotlightInsightType;
import org.steam5.domain.StreakCalculator;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks one eligible player each day to feature in a "good vibes" box shown on
 * round 1. Eligibility requires an established, currently-active player (min
 * rounds all-time, played recently) so the spotlight never features a fluke.
 * <p>
 * Selection is a lottery, not a priority ladder: every tier in the fixed-order
 * "competitive pool" (DAY_STREAK, BEST_DAY_EVER, BEAT_THE_ODDS, WELCOME_BACK,
 * MOST_IMPROVED, HOT_STREAK) that has at least one qualifying candidate is
 * added to a list, and one entry is drawn uniformly at random via
 * {@code new Random(today.toEpochDay())}. This means no single ambient tier
 * (e.g. DAY_STREAK, which is easy to qualify for) can dominate just because
 * it's evaluated first — it only gets an edge if it's the ONLY tier that
 * qualifies. When the competitive pool has zero qualifying tiers, two
 * sequential fallbacks are tried in order: WEEKLY_ACHIEVEMENT, then
 * MILESTONE (which always has at least one candidate among the eligible
 * pool, guaranteeing a spotlight is always produced). Ties within a chosen
 * tier are broken by the same date-seeded {@link Random}, so the result is
 * stable for the whole day but rotates daily.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerSpotlightService {

    private static final int MIN_TOTAL_ROUNDS = 70;
    private static final int RECENCY_WINDOW_DAYS = 14;
    private static final int MIN_ROUNDS_IN_RECENCY_WINDOW = 35;
    private static final int MIN_DAY_STREAK = 5;
    private static final int MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER = 2;
    private static final double HARD_ROUND_MAX_AVG_SCORE = 2.0;
    private static final int BEAT_THE_ODDS_MIN_POINTS = 4;
    private static final int WELCOME_BACK_MIN_GAP_DAYS = 4;
    private static final double WELCOME_BACK_MIN_RETURN_DAY_AVG = 3.0;
    private static final int MOST_IMPROVED_WINDOW_DAYS = 30;
    private static final int MOST_IMPROVED_MIN_ROUNDS_PER_WINDOW = 10;
    private static final double MOST_IMPROVED_RELATIVE_THRESHOLD = 1.15;
    private static final double MOST_IMPROVED_ABSOLUTE_DELTA = 0.3;
    private static final int HOT_STREAK_WINDOW_DAYS = 14;
    private static final int MIN_RECENT_ROUNDS_FOR_HOT_STREAK = 5;
    private static final double HOT_STREAK_RELATIVE_THRESHOLD = 1.2; // recent avg must be >= 20% above all-time avg
    private static final double HOT_STREAK_ABSOLUTE_DELTA = 0.3;    // ...and at least this many points better
    private static final List<Long> ROUND_MILESTONES = List.of(100L, 250L, 500L, 1000L);
    private static final List<Long> POINTS_MILESTONES = List.of(1000L, 5000L, 10000L);
    private static final long MILESTONE_TRAILING_WINDOW = 5;

    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final StatisticsService statisticsService;
    private final PlayerSpotlightRepository playerSpotlightRepository;

    /**
     * Computes and persists today's spotlight, if one doesn't already exist.
     * Intended to be called once nightly by PlayerSpotlightJob.
     */
    @Transactional
    public void computeAndPersistForToday() {
        final LocalDate today = GameDate.todayUtc();
        if (playerSpotlightRepository.existsById(today)) {
            log.info("PlayerSpotlight already computed for {}", today);
            return;
        }

        final Optional<PlayerSpotlight> spotlight = compute(today);
        if (spotlight.isPresent()) {
            playerSpotlightRepository.save(spotlight.get());
            log.info("PlayerSpotlight computed for {}: steamId={} insight={}",
                    today, spotlight.get().getSteamId(), spotlight.get().getInsightType());
        } else {
            log.info("No eligible PlayerSpotlight candidate for {}", today);
        }
    }

    public Optional<SpotlightResponse> getTodaySpotlight() {
        return playerSpotlightRepository.findById(GameDate.todayUtc()).map(this::toResponse);
    }

    /** Condensed history (most recent 10) of spotlights a player has been featured in, for their profile page. */
    @Transactional(readOnly = true)
    @Cacheable(value = "player-spotlights", key = "#steamId")
    public List<SpotlightHistoryEntry> listSpotlightsForPlayer(final String steamId) {
        return playerSpotlightRepository.findTop10BySteamIdOrderByGameDateDesc(steamId).stream()
                .map(s -> new SpotlightHistoryEntry(s.getGameDate(), s.getInsightType(), s.getHeadline(),
                        s.getDetail(), s.getStatLabel(), s.getStatValue()))
                .toList();
    }

    private SpotlightResponse toResponse(final PlayerSpotlight spotlight) {
        final User user = userRepository.findById(spotlight.getSteamId()).orElse(null);
        return new SpotlightResponse(
                spotlight.getSteamId(),
                user != null && user.getPersonaName() != null ? user.getPersonaName() : spotlight.getSteamId(),
                user != null ? user.getAvatar() : null,
                spotlight.getInsightType(),
                spotlight.getHeadline(),
                spotlight.getDetail(),
                spotlight.getStatLabel(),
                spotlight.getStatValue()
        );
    }

    private Optional<PlayerSpotlight> compute(final LocalDate today) {
        final List<Candidate> eligible = findEligibleCandidates(today);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        // Batch-prefetch every eligible candidate's guess history in one query, instead of
        // each tier evaluator querying per-candidate — BEST_DAY_EVER, WELCOME_BACK,
        // MOST_IMPROVED, and HOT_STREAK all just filter/aggregate this same in-memory map
        // to their own date window, eliminating what was an N+1 (and 2N for MOST_IMPROVED)
        // pattern across those four tiers.
        final List<String> eligibleIds = eligible.stream().map(Candidate::steamId).toList();
        final Map<String, List<Guess>> historyByPlayer = guessRepository.findBySteamIdIn(eligibleIds).stream()
                .collect(Collectors.groupingBy(Guess::getSteamId));

        // "Competitive pool": every tier here that has >=1 qualifying candidate goes
        // into a lottery, so no single ambient tier (e.g. DAY_STREAK) can dominate
        // just because it's the easiest to qualify for on any given day.
        final LocalDate yesterday = today.minusDays(1);
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEST_DAY_EVER, evaluateBestDayEverTier(eligible, yesterday, historyByPlayer));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEAT_THE_ODDS, evaluateBeatTheOddsTier(eligible, yesterday));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.WELCOME_BACK, evaluateWelcomeBackTier(eligible, historyByPlayer));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.MOST_IMPROVED, evaluateMostImprovedTier(eligible, today, historyByPlayer));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.HOT_STREAK, evaluateHotStreakTier(eligible, today, historyByPlayer));

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

    private List<Candidate> findEligibleCandidates(final LocalDate today) {
        final Map<String, GuessRepository.AllTimeStatsRow> eligibleAllTime = guessRepository.aggregateAllTimeStats()
                .stream()
                .filter(row -> row.getRounds() != null && row.getRounds() >= MIN_TOTAL_ROUNDS)
                .collect(Collectors.toMap(GuessRepository.AllTimeStatsRow::getSteamId, Function.identity()));

        if (eligibleAllTime.isEmpty()) {
            return List.of();
        }

        final List<String> candidateIds = new ArrayList<>(eligibleAllTime.keySet());
        final Map<String, List<LocalDate>> datesByUser = guessRepository
                .findDistinctDatesUpToForUsers(candidateIds, today).stream()
                .collect(Collectors.groupingBy(
                        GuessRepository.UserDateRow::getSteamId,
                        LinkedHashMap::new,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())
                ));

        // Established (>=70 all-time) isn't enough on its own — a long-dormant veteran who
        // banked those rounds long ago, then plays a single light day, would otherwise still
        // qualify. Require genuine current activity: >=35 rounds within the last 14 days. This
        // subsumes the old "played on at least one day in the last 14 days" check (any candidate
        // clearing a >0 round-count floor necessarily has at least one date in the window too),
        // so that separate date check is removed.
        final LocalDate recencyWindowStart = today.minusDays(RECENCY_WINDOW_DAYS - 1L);
        final Map<String, Long> recentRoundsBySteamId = guessRepository
                .findBySteamIdInAndGameDateBetween(candidateIds, recencyWindowStart, today).stream()
                .collect(Collectors.groupingBy(Guess::getSteamId, Collectors.counting()));

        final List<Candidate> eligible = new ArrayList<>();
        for (final String steamId : candidateIds) {
            final long roundsInWindow = recentRoundsBySteamId.getOrDefault(steamId, 0L);
            if (roundsInWindow < MIN_ROUNDS_IN_RECENCY_WINDOW) {
                continue;
            }
            final List<LocalDate> datesDesc = datesByUser.getOrDefault(steamId, List.of());
            eligible.add(new Candidate(steamId, eligibleAllTime.get(steamId), datesDesc));
        }
        return eligible;
    }

    private List<Tiered> evaluateDayStreakTier(final List<Candidate> eligible, final LocalDate today) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final int currentStreak = StreakCalculator.currentStreak(c.datesDesc(), today);
            if (currentStreak < MIN_DAY_STREAK) continue;

            final List<LocalDate> datesAsc = new ArrayList<>(c.datesDesc());
            Collections.reverse(datesAsc);
            final long longest = StreakCalculator.longestStreak(datesAsc);
            final boolean isPersonalBest = currentStreak >= longest;

            final String detail = isPersonalBest
                    ? String.format("Riding a personal-best %d-day streak — playing every single day!", currentStreak)
                    : String.format("On a %d-day streak of playing every day.", currentStreak);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.DAY_STREAK,
                    "On a hot streak!", detail, "Day streak", (double) currentStreak));
        }
        return tier;
    }

    private List<Tiered> evaluateBestDayEverTier(final List<Candidate> eligible, final LocalDate yesterday,
                                                  final Map<String, List<Guess>> historyByPlayer) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
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

    private List<Tiered> evaluateBeatTheOddsTier(final List<Candidate> eligible, final LocalDate yesterday) {
        final List<GuessRepository.RoundAvgScoreRow> rows = guessRepository.findRoundAvgScoresInRange(yesterday, yesterday);
        final Optional<GuessRepository.RoundAvgScoreRow> hardestRound = rows.stream()
                .min(Comparator.comparing(GuessRepository.RoundAvgScoreRow::getAvgScore));
        if (hardestRound.isEmpty() || hardestRound.get().getAvgScore() >= HARD_ROUND_MAX_AVG_SCORE) {
            return List.of();
        }

        final int roundIndex = hardestRound.get().getRoundIndex();
        final double hardAvg = hardestRound.get().getAvgScore();

        // One query for everyone's guess on the hardest round, instead of one query per
        // candidate for findBySteamIdAndGameDateAndRoundIndex.
        final Map<String, Guess> guessByPlayer = guessRepository.findByGameDateAndRoundIndex(yesterday, roundIndex)
                .stream()
                .collect(Collectors.toMap(Guess::getSteamId, Function.identity(), (a, b) -> a));

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final Guess theirGuess = guessByPlayer.get(c.steamId());
            if (theirGuess == null || theirGuess.getPoints() < BEAT_THE_ODDS_MIN_POINTS) continue;

            final String detail = String.format(
                    "Nailed yesterday's toughest round (round %d, %.1f avg pts across all players) with %d points.",
                    roundIndex, hardAvg, theirGuess.getPoints());
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.BEAT_THE_ODDS,
                    "Beat the odds!", detail, "Round points", (double) theirGuess.getPoints()));
        }
        return tier;
    }

    private List<Tiered> evaluateWelcomeBackTier(final List<Candidate> eligible,
                                                  final Map<String, List<Guess>> historyByPlayer) {
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<LocalDate> datesDesc = c.datesDesc();
            if (datesDesc.size() < 2) continue;

            final LocalDate mostRecent = datesDesc.get(0);
            final LocalDate previous = datesDesc.get(1);
            final long gapDays = ChronoUnit.DAYS.between(previous, mostRecent);
            if (gapDays < WELCOME_BACK_MIN_GAP_DAYS) continue;

            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final double returnDayAvg = history.stream()
                    .filter(g -> g.getGameDate().equals(mostRecent))
                    .mapToInt(Guess::getPoints)
                    .average()
                    .orElse(0.0);
            if (returnDayAvg < WELCOME_BACK_MIN_RETURN_DAY_AVG) continue;

            final String detail = String.format(
                    "Took a %d-day break and came back strong, averaging %.1f pts/round on their return.",
                    gapDays, returnDayAvg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.WELCOME_BACK,
                    "Welcome back!", detail, "Days away", (double) gapDays));
        }
        return tier;
    }

    private List<Tiered> evaluateMostImprovedTier(final List<Candidate> eligible, final LocalDate today,
                                                   final Map<String, List<Guess>> historyByPlayer) {
        final LocalDate last30Start = today.minusDays(MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate last30End = today.minusDays(1);
        final LocalDate prior30Start = today.minusDays(2L * MOST_IMPROVED_WINDOW_DAYS);
        final LocalDate prior30End = today.minusDays(MOST_IMPROVED_WINDOW_DAYS + 1L);

        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final List<Guess> last30 = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(last30Start) && !g.getGameDate().isAfter(last30End))
                    .toList();
            final List<Guess> prior30 = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(prior30Start) && !g.getGameDate().isAfter(prior30End))
                    .toList();
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

    private List<Tiered> evaluateWeeklyAchievementTier(final List<Candidate> eligible) {
        final Set<String> eligibleIds = eligible.stream().map(Candidate::steamId).collect(Collectors.toSet());
        final List<Tiered> tier = new ArrayList<>();
        for (final StatisticsService.UserLabel label : statisticsService.getUserAchievementsWeekly()) {
            if (!eligibleIds.contains(label.steamId())) continue;
            final AchievementText text = describeAchievement(label);
            if (text == null) continue;
            tier.add(new Tiered(label.steamId(), PlayerSpotlightInsightType.WEEKLY_ACHIEVEMENT,
                    text.headline(), text.detail(), text.statLabel(), text.statValue()));
        }
        return tier;
    }

    private List<Tiered> evaluateHotStreakTier(final List<Candidate> eligible, final LocalDate today,
                                                final Map<String, List<Guess>> historyByPlayer) {
        final LocalDate windowStart = today.minusDays(HOT_STREAK_WINDOW_DAYS - 1L);
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> history = historyByPlayer.getOrDefault(c.steamId(), List.of());
            final List<Guess> recent = history.stream()
                    .filter(g -> !g.getGameDate().isBefore(windowStart) && !g.getGameDate().isAfter(today))
                    .toList();
            if (recent.size() < MIN_RECENT_ROUNDS_FOR_HOT_STREAK) continue;

            final double allTimeAvg = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;
            if (allTimeAvg <= 0) continue;

            final double recentAvg = recent.stream().mapToInt(Guess::getPoints).average().orElse(0.0);
            final boolean qualifies = recentAvg >= allTimeAvg * HOT_STREAK_RELATIVE_THRESHOLD
                    && (recentAvg - allTimeAvg) >= HOT_STREAK_ABSOLUTE_DELTA;
            if (!qualifies) continue;

            final String detail = String.format(
                    "Averaging %.1f pts/round over the last %d days — well above their usual %.1f.",
                    recentAvg, HOT_STREAK_WINDOW_DAYS, allTimeAvg);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.HOT_STREAK,
                    "In red-hot form!", detail, "Recent avg", recentAvg));
        }
        return tier;
    }

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

    private AchievementText describeAchievement(final StatisticsService.UserLabel label) {
        return switch (label.userAchievement()) {
            case EARLY_BIRD -> new AchievementText("Early bird of the week!",
                    "Plays earlier in the day than anyone else this week.", "Avg time", label.avgMinutes());
            case NIGHT_OWL -> new AchievementText("Night owl of the week!",
                    "Plays later in the day than anyone else this week.", "Avg time", label.avgMinutes());
            case SHARPSHOOTER -> new AchievementText("Sharpshooter of the week!",
                    String.format("Highest average score this week — %.1f pts/round.", label.avgPoints()),
                    "Avg points", label.avgPoints());
            case BULLSEYE -> new AchievementText("Bullseye streak!",
                    String.format("Landed %d perfect round%s this week.", label.perfectRounds(),
                            label.perfectRounds() != null && label.perfectRounds() == 1 ? "" : "s"),
                    "Perfect rounds", label.perfectRounds() != null ? label.perfectRounds().doubleValue() : null);
            case PERFECT_DAY -> new AchievementText("Flawless days!",
                    String.format("Notched %d perfect day%s this week.", label.perfectDays(),
                            label.perfectDays() != null && label.perfectDays() == 1 ? "" : "s"),
                    "Perfect days", label.perfectDays() != null ? label.perfectDays().doubleValue() : null);
            case CHEETAH -> new AchievementText("Quickest hands this week!",
                    "Blazed through rounds faster than anyone else this week.", "Total seconds",
                    label.totalSeconds() != null ? label.totalSeconds().doubleValue() : null);
            // SLOTH ("slowest player") isn't a good-vibes framing — skip it.
            case SLOTH -> null;
        };
    }

    /** Stable-for-the-day pick among candidates tied in the same tier, using the caller's Random. */
    private Tiered pickOne(final List<Tiered> tier, final Random random) {
        final List<Tiered> sorted = tier.stream()
                .sorted(Comparator.comparing(Tiered::steamId))
                .toList();
        return sorted.get(random.nextInt(sorted.size()));
    }

    private PlayerSpotlight toEntity(final LocalDate today, final Tiered tiered) {
        final PlayerSpotlight entity = new PlayerSpotlight();
        entity.setGameDate(today);
        entity.setSteamId(tiered.steamId());
        entity.setInsightType(tiered.insightType());
        entity.setHeadline(tiered.headline());
        entity.setDetail(tiered.detail());
        entity.setStatLabel(tiered.statLabel());
        entity.setStatValue(tiered.statValue());
        return entity;
    }

    private record Candidate(String steamId, GuessRepository.AllTimeStatsRow allTime, List<LocalDate> datesDesc) {
    }

    private record Tiered(String steamId, PlayerSpotlightInsightType insightType, String headline, String detail,
                           String statLabel, Double statValue) {
    }

    private record QualifyingTier(PlayerSpotlightInsightType type, List<Tiered> candidates) {
    }

    private record AchievementText(String headline, String detail, String statLabel, Double statValue) {
    }

    public record SpotlightHistoryEntry(
            LocalDate gameDate,
            PlayerSpotlightInsightType insightType,
            String headline,
            String detail,
            String statLabel,
            Double statValue
    ) {
    }

    public record SpotlightResponse(
            String steamId,
            String personaName,
            String avatar,
            PlayerSpotlightInsightType insightType,
            String headline,
            String detail,
            String statLabel,
            Double statValue
    ) {
    }
}
