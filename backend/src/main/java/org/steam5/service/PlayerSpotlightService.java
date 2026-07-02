package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Selection is "best story wins": the highest-priority tier
 * ({@link PlayerSpotlightInsightType}) that has at least one qualifying
 * candidate is used, with a date-seeded random pick among ties so the same
 * story holds for the whole day but rotates daily.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerSpotlightService {

    private static final int MIN_TOTAL_ROUNDS = 70;
    private static final int RECENCY_WINDOW_DAYS = 14;
    private static final int MIN_DAY_STREAK = 5;
    private static final int MIN_PRIOR_DAYS_FOR_BEST_DAY_EVER = 2;
    private static final int HOT_STREAK_WINDOW_DAYS = 14;
    private static final int MIN_RECENT_ROUNDS_FOR_HOT_STREAK = 5;
    private static final double HOT_STREAK_RELATIVE_THRESHOLD = 1.2; // recent avg must be >= 20% above all-time avg
    private static final double HOT_STREAK_ABSOLUTE_DELTA = 0.3;    // ...and at least this many points better

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

        // "Competitive pool": every tier here that has >=1 qualifying candidate goes
        // into a lottery, so no single ambient tier (e.g. DAY_STREAK) can dominate
        // just because it's the easiest to qualify for on any given day.
        final LocalDate yesterday = today.minusDays(1);
        final List<QualifyingTier> qualifying = new ArrayList<>();
        addIfQualifying(qualifying, PlayerSpotlightInsightType.DAY_STREAK, evaluateDayStreakTier(eligible, today));
        addIfQualifying(qualifying, PlayerSpotlightInsightType.BEST_DAY_EVER, evaluateBestDayEverTier(eligible, yesterday));
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

        final LocalDate recencyCutoff = today.minusDays(RECENCY_WINDOW_DAYS);
        final List<Candidate> eligible = new ArrayList<>();
        for (final String steamId : candidateIds) {
            final List<LocalDate> datesDesc = datesByUser.getOrDefault(steamId, List.of());
            if (datesDesc.isEmpty() || datesDesc.get(0).isBefore(recencyCutoff)) {
                continue;
            }
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

    private List<Tiered> evaluateHotStreakTier(final List<Candidate> eligible, final LocalDate today) {
        final LocalDate windowStart = today.minusDays(HOT_STREAK_WINDOW_DAYS - 1L);
        final List<Tiered> tier = new ArrayList<>();
        for (final Candidate c : eligible) {
            final List<Guess> recent = guessRepository.findBySteamIdBetween(c.steamId(), windowStart, today);
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
            final double avgPoints = c.allTime().getAvgPoints() != null ? c.allTime().getAvgPoints() : 0.0;
            final String detail = String.format(
                    "Has played %d rounds and counting, averaging %.1f pts/round.",
                    c.allTime().getRounds(), avgPoints);
            tier.add(new Tiered(c.steamId(), PlayerSpotlightInsightType.MILESTONE,
                    "A steady presence!", detail, "Rounds played", (double) c.allTime().getRounds()));
        }
        return tier;
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
