package org.steam5.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.steam5.domain.GameDate;
import org.steam5.domain.User;
import org.steam5.domain.YearGuess;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.game.year.YearGamePick;
import org.steam5.game.year.YearGamePickRepository;
import org.steam5.game.year.YearGuessEvaluator;
import org.steam5.http.ReviewGameException;
import org.steam5.repository.UserRepository;
import org.steam5.repository.YearGuessRepository;
import org.steam5.repository.details.SteamAppDetailRepository;
import org.steam5.security.CurrentUser;
import org.steam5.service.YearGameStateService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/year-game")
@Validated
public class YearGameStateController {

    private final YearGameStateService service;
    private final SteamAppDetailRepository detailRepository;
    private final YearGuessRepository guessRepository;
    private final UserRepository userRepository;
    private final YearGamePickRepository pickRepository;
    private final Scheduler scheduler;
    private final MeterRegistry meterRegistry;

    private static final String CACHE_LIVE = "public, s-maxage=1800, max-age=60, must-revalidate";
    private static final String CACHE_CONFIG = "public, s-maxage=3600, max-age=300";
    private static final String CACHE_HISTORICAL = "public, max-age=31536000, immutable";
    private static final String PRIVATE_LIVE = "private, max-age=60, must-revalidate";
    private static final String PRIVATE_HISTORICAL = "private, max-age=31536000, immutable";
    private static final String CACHE_ONLY_2XX =
            "#result == null || !#result.statusCode.is2xxSuccessful()";
    private static final String CACHE_ONLY_2XX_NONEMPTY_PICKS =
            CACHE_ONLY_2XX + " || #result.body == null || #result.body.picks.isEmpty()";
    private static final String CACHE_ONLY_2XX_NONEMPTY_LIST =
            CACHE_ONLY_2XX + " || #result.body == null || #result.body.isEmpty()";

    @GetMapping("/days")
    @Cacheable(value = "year-game", key = "'days'", unless = CACHE_ONLY_2XX)
    public ResponseEntity<List<String>> listDays(@RequestParam(value = "limit", defaultValue = "60") int limit,
                                                 @RequestHeader HttpHeaders headers) {
        final int capped = Math.max(1, Math.min(limit, 3650));
        final List<LocalDate> dates = pickRepository.listDistinctPickDates(PageRequest.of(0, capped));
        final List<String> out = dates.stream().map(LocalDate::toString).toList();
        final String etag = weakEtagForStringLists(List.of(out));
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", "public, s-maxage=600, max-age=60")
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", "public, s-maxage=600, max-age=60")
                .body(out);
    }

    @PostMapping("/guess")
    public ResponseEntity<GuessResponse> submitGuess(@RequestBody GuessRequest req) {
        if (req == null || req.appId() == null || req.guessYear() == null) {
            return ResponseEntity.badRequest().build();
        }

        final int actualYear = service.getReleaseYearForApp(req.appId());
        if (actualYear <= 0) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(buildGuessResponse(req.appId(), req.guessYear(), actualYear, 0, null));
    }

    @PostMapping("/guess-auth")
    @Transactional
    public ResponseEntity<GuessResponse> submitGuessAuthenticated(@CurrentUser String steamId,
                                                                  @RequestBody GuessRequest req) {
        if (req == null || req.appId() == null || req.guessYear() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        ensureUserExists(steamId);

        final int actualYear = service.getReleaseYearForApp(req.appId());
        if (actualYear <= 0) {
            return ResponseEntity.badRequest().build();
        }

        final RoundContext round = resolveRound(req.appId());
        if (round == null) {
            return ResponseEntity.badRequest().build();
        }

        final Optional<YearGuess> existingOpt = guessRepository.findBySteamIdAndGameDateAndRoundIndex(
                steamId, round.date(), round.roundIndex());
        if (existingOpt.isPresent() && existingOpt.get().isCompleted()) {
            final YearGuess completed = existingOpt.get();
            return ResponseEntity.ok(buildGuessResponse(
                    completed.getAppId(),
                    completed.getGuessedYear(),
                    completed.getActualYear(),
                    completed.getHintsUsed(),
                    completed
            ));
        }

        final YearGuess progress = existingOpt.orElseGet(() -> new YearGuess(
                null,
                steamId,
                round.date(),
                round.roundIndex(),
                req.appId(),
                null,
                actualYear,
                0,
                null,
                false,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        ));

        final int distance = YearGuessEvaluator.distance(req.guessYear(), actualYear);
        final boolean correct = YearGuessEvaluator.isExactMatch(req.guessYear(), actualYear);
        progress.setGuessedYear(req.guessYear());
        progress.setUpdatedAt(OffsetDateTime.now());

        if (correct) {
            final int points = YearGuessEvaluator.scoreExactGuess(progress.getHintsUsed(), service.getConfig());
            progress.setCompleted(true);
            progress.setPoints(points);
            progress.setBestDistance(0);
            persistProgress(progress, existingOpt.isEmpty());
            incrementGuessCounter(true);
            return ResponseEntity.ok(buildGuessResponse(req.appId(), req.guessYear(), actualYear, progress.getHintsUsed(), progress));
        }

        final int bestDistance = progress.getBestDistance() == null
                ? distance
                : Math.min(progress.getBestDistance(), distance);
        progress.setBestDistance(bestDistance);
        persistProgress(progress, existingOpt.isEmpty());
        incrementGuessCounter(false);
        return ResponseEntity.ok(buildGuessResponse(req.appId(), req.guessYear(), actualYear, progress.getHintsUsed(), progress));
    }

    @PostMapping("/hint")
    @Transactional
    public ResponseEntity<HintResponse> revealHint(@CurrentUser String steamId,
                                                   @RequestBody HintRequest req) {
        if (req == null || req.appId() == null || req.hintLevel() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }
        if (req.hintLevel() < 1 || req.hintLevel() > YearGuessEvaluator.MAX_HINTS) {
            return ResponseEntity.badRequest().build();
        }

        ensureUserExists(steamId);

        final RoundContext round = resolveRound(req.appId());
        if (round == null) {
            return ResponseEntity.badRequest().build();
        }

        final YearGuess progress = guessRepository.findBySteamIdAndGameDateAndRoundIndex(
                steamId, round.date(), round.roundIndex()).orElse(null);
        if (progress == null || progress.isCompleted()) {
            return ResponseEntity.badRequest().build();
        }
        if (progress.getBestDistance() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (req.hintLevel() <= progress.getHintsUsed()) {
            final String content = service.buildHintContent(req.hintLevel(), req.appId());
            final int maxPoints = YearGuessEvaluator.maxPointsForHintsUsed(progress.getHintsUsed(), service.getConfig());
            return ResponseEntity.ok(new HintResponse(req.hintLevel(), content, progress.getHintsUsed(), maxPoints));
        }
        if (req.hintLevel() != progress.getHintsUsed() + 1) {
            return ResponseEntity.badRequest().build();
        }
        if (!YearGuessEvaluator.isHintUnlocked(req.hintLevel(), progress.getBestDistance(), service.getConfig())) {
            return ResponseEntity.badRequest().build();
        }

        progress.setHintsUsed(req.hintLevel());
        progress.setUpdatedAt(OffsetDateTime.now());
        guessRepository.save(progress);

        final String content = service.buildHintContent(req.hintLevel(), req.appId());
        final int maxPoints = YearGuessEvaluator.maxPointsForHintsUsed(progress.getHintsUsed(), service.getConfig());
        return ResponseEntity.ok(new HintResponse(req.hintLevel(), content, progress.getHintsUsed(), maxPoints));
    }

    @GetMapping("/today/details")
    @Cacheable(value = "year-game", key = "'today-details:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_LIST)
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails(@RequestHeader HttpHeaders headers) {
        final List<YearGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(YearGamePick::getAppId).toList();
        final List<SteamAppDetail> details = service.sanitizeForGameplay(detailRepository.findAllByAppIdIn(appIds));
        final String etag = weakEtagForPicks(details);
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", CACHE_LIVE)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_LIVE)
                .body(details);
    }

    @GetMapping("/my/today")
    public ResponseEntity<List<MyGuessDto>> myToday(@CurrentUser String steamId) {
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        final List<YearGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final Set<Long> currentAppIds = picks.stream().map(YearGamePick::getAppId).collect(java.util.stream.Collectors.toSet());

        final List<MyGuessDto> dtos = guessRepository.findAllForDay(steamId, date).stream()
                .filter(g -> currentAppIds.contains(g.getAppId()))
                .map(this::toMyGuessDto)
                .toList();

        return ResponseEntity.ok()
                .header("Cache-Control", PRIVATE_LIVE)
                .body(dtos);
    }

    @GetMapping("/my/day/{date}")
    public ResponseEntity<List<MyGuessDto>> myDay(@PathVariable("date") String date,
                                                  @CurrentUser String steamId) {
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        final List<MyGuessDto> dtos = guessRepository.findAllForDay(steamId, day).stream()
                .map(this::toMyGuessDto)
                .toList();

        final boolean isToday = day.equals(GameDate.todayUtc());
        final String cacheControl = isToday ? PRIVATE_LIVE : PRIVATE_HISTORICAL;
        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .body(dtos);
    }

    @GetMapping("/my/history")
    public ResponseEntity<List<MyGuessDto>> myHistory(@RequestParam(value = "from", required = false) String from,
                                                      @RequestParam(value = "to", required = false) String to,
                                                      @CurrentUser String steamId) {
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        final LocalDate start;
        final LocalDate end;
        try {
            start = from == null || from.isBlank() ? LocalDate.of(1970, 1, 1) : LocalDate.parse(from);
            end = to == null || to.isBlank() ? GameDate.todayUtc() : LocalDate.parse(to);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        if (end.isBefore(start)) {
            return ResponseEntity.badRequest().build();
        }

        final List<MyGuessDto> dtos = guessRepository.findBySteamIdBetween(steamId, start, end).stream()
                .map(this::toMyGuessDto)
                .toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "private, s-maxage=600, max-age=300")
                .body(dtos);
    }

    @GetMapping("/today")
    @Cacheable(value = "year-game", key = "'today-picks:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_PICKS)
    public ResponseEntity<YearGameStateDto> getToday(@RequestHeader HttpHeaders headers) {
        final List<YearGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        return buildStateResponse(picks, headers, date, CACHE_LIVE, CACHE_LIVE);
    }

    @GetMapping("/day/{date}")
    @Cacheable(value = "year-game", key = "'picks:' + #date", unless = CACHE_ONLY_2XX)
    public ResponseEntity<YearGameStateDto> getByDate(@PathVariable("date") String date,
                                                      @RequestHeader HttpHeaders headers) {
        final LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        final List<YearGamePick> picks = pickRepository.findByPickDate(day);
        if (picks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final boolean isToday = day.equals(GameDate.todayUtc());
        final String cacheControl = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
        return buildStateResponse(picks, headers, day, cacheControl, cacheControl);
    }

    @GetMapping("/hints/meta")
    @Cacheable(value = "one-day", key = "'year-hints-meta'")
    public ResponseEntity<HintMetaResponse> hintMeta(@RequestHeader HttpHeaders headers) {
        final List<YearGameStateService.HintTierMeta> tiers = service.getHintTiers();
        final String etag = weakEtagForStringLists(
                tiers.stream()
                        .map(tier -> List.of(
                                Integer.toString(tier.level()),
                                tier.label(),
                                tier.description(),
                                Integer.toString(tier.maxPoints())
                        ))
                        .toList()
        );
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", CACHE_CONFIG)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_CONFIG)
                .body(new HintMetaResponse(
                        service.getConfig().getHintDistanceThresholds(),
                        tiers
                ));
    }

    @GetMapping("/next-challenge-time")
    public ResponseEntity<Map<String, Object>> getNextChallengeTime() {
        final Map<String, Object> result = new HashMap<>();
        final OffsetDateTime now = OffsetDateTime.now();

        try {
            final var trigger = scheduler.getTrigger(new TriggerKey("YearGameStateJob_Trigger"));
            if (trigger == null) {
                throw new SchedulerException("Trigger not found");
            }
            final Date nextFireTime = trigger.getNextFireTime();
            if (nextFireTime != null) {
                result.put("nextChallengeTime", OffsetDateTime.ofInstant(
                        nextFireTime.toInstant(),
                        java.time.ZoneOffset.UTC
                ).toString());
            } else {
                final OffsetDateTime todayAt020 = now.toLocalDate().atStartOfDay().atOffset(now.getOffset()).plusMinutes(2);
                result.put("nextChallengeTime", (now.isBefore(todayAt020) ? todayAt020 : todayAt020.plusDays(1)).toString());
            }
        } catch (SchedulerException e) {
            final java.time.ZonedDateTime nowUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
            final java.time.ZonedDateTime todayAt020Utc = nowUtc.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).plusMinutes(2);
            final java.time.ZonedDateTime nextChallengeUtc = nowUtc.isBefore(todayAt020Utc) ? todayAt020Utc : todayAt020Utc.plusDays(1);
            result.put("nextChallengeTime", nextChallengeUtc.toOffsetDateTime().toString());
        }

        result.put("serverTimezoneOffset", now.getOffset().getTotalSeconds() / 60);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=60, max-age=30")
                .body(result);
    }

    @GetMapping("/archive/month")
    @Cacheable(value = "year-game", key = "'archive-month:' + #month", unless = CACHE_ONLY_2XX)
    public ResponseEntity<List<ArchiveMonthDay>> archiveMonth(@RequestParam("month") String month,
                                                              @RequestHeader HttpHeaders headers) {
        final YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        final LocalDate from = yearMonth.atDay(1);
        final LocalDate to = yearMonth.plusMonths(1).atDay(1);
        final List<YearGamePickRepository.MonthlyArchivePickRow> rows =
                pickRepository.listMonthlyArchivePicks(from, to);

        final Map<String, List<ArchiveMonthDayPick>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            final String date = row.getPickDate().toString();
            grouped.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(new ArchiveMonthDayPick(row.getAppId(), row.getName()));
        }
        final List<ArchiveMonthDay> out = grouped.entrySet().stream()
                .map(entry -> new ArchiveMonthDay(entry.getKey(), entry.getValue()))
                .toList();

        final boolean isCurrentMonth = yearMonth.equals(YearMonth.now());
        final String cacheControl = isCurrentMonth
                ? "public, s-maxage=86400, max-age=3600"
                : "public, max-age=31536000, immutable";
        final String etag = weakEtagForStringLists(
                out.stream()
                        .map(day -> {
                            final ArrayList<String> lines = new ArrayList<>(day.picks().size() + 1);
                            lines.add(day.date());
                            for (ArchiveMonthDayPick pick : day.picks()) {
                                lines.add(pick.appId() + ":" + pick.name());
                            }
                            return lines;
                        })
                        .toList()
        );
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", cacheControl)
                .body(out);
    }

    @GetMapping("/archive/random")
    public ResponseEntity<Map<String, String>> randomArchiveDate() {
        final Optional<LocalDate> date = pickRepository.findRandomArchiveDate(GameDate.todayUtc());
        if (date.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("date", date.get().toString()));
    }

    public record YearGameStateDto(LocalDate date, List<YearGameStateService.HintTierMeta> hintTiers,
                                   List<SteamAppDetail> picks) {
    }

    public record HintMetaResponse(List<Integer> distanceThresholds,
                                   List<YearGameStateService.HintTierMeta> tiers) {
    }

    public record GuessRequest(Long appId, Integer guessYear) {
    }

    public record GuessResponse(Long appId,
                                Integer guessYear,
                                boolean correct,
                                Integer distance,
                                Integer releaseYear,
                                int hintsUsed,
                                int maxPoints,
                                List<Integer> unlockableHintLevels,
                                Integer points) {
    }

    public record HintRequest(Long appId, Integer hintLevel) {
    }

    public record HintResponse(int hintLevel, String content, int hintsUsed, int maxPoints) {
    }

    public record MyGuessDto(int roundIndex,
                             Long appId,
                             Integer guessedYear,
                             Integer actualYear,
                             int hintsUsed,
                             Integer bestDistance,
                             List<Integer> unlockableHintLevels,
                             boolean completed,
                             int points) {
    }

    public record ArchiveMonthDayPick(Long appId, String name) {
    }

    public record ArchiveMonthDay(String date, List<ArchiveMonthDayPick> picks) {
    }

    private record RoundContext(LocalDate date, int roundIndex) {
    }

    private ResponseEntity<YearGameStateDto> buildStateResponse(final List<YearGamePick> picks,
                                                                final HttpHeaders headers,
                                                                final LocalDate date,
                                                                final String hitCacheControl,
                                                                final String missCacheControl) {
        final List<Long> appIds = picks.stream().map(YearGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailRepository.findAllByAppIdIn(appIds);
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail detail : fetched) {
            byId.put(detail.getAppId(), detail);
        }
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(service::sanitizeForGameplay)
                .toList();

        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match");
        }

        final String etag = weakEtagForPicks(details);
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", hitCacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", missCacheControl)
                .body(new YearGameStateDto(date, service.getHintTiers(), details));
    }

    private GuessResponse buildGuessResponse(final Long appId,
                                             final Integer guessYear,
                                             final int actualYear,
                                             final int hintsUsed,
                                             final YearGuess progress) {
        final int distance = YearGuessEvaluator.distance(guessYear, actualYear);
        final boolean correct = YearGuessEvaluator.isExactMatch(guessYear, actualYear);
        final int bestDistance = progress != null && progress.getBestDistance() != null
                ? progress.getBestDistance()
                : distance;
        final List<Integer> unlockable = correct
                ? List.of()
                : YearGuessEvaluator.unlockableHintLevels(bestDistance, hintsUsed, service.getConfig());
        final Integer releaseYear = correct ? actualYear : null;
        final Integer points = correct ? YearGuessEvaluator.scoreExactGuess(hintsUsed, service.getConfig()) : null;
        return new GuessResponse(
                appId,
                guessYear,
                correct,
                distance,
                releaseYear,
                hintsUsed,
                YearGuessEvaluator.maxPointsForHintsUsed(hintsUsed, service.getConfig()),
                unlockable,
                points
        );
    }

    private MyGuessDto toMyGuessDto(final YearGuess guess) {
        final Integer bestDistance = guess.getBestDistance();
        final List<Integer> unlockable = guess.isCompleted() || bestDistance == null
                ? List.of()
                : YearGuessEvaluator.unlockableHintLevels(bestDistance, guess.getHintsUsed(), service.getConfig());
        return new MyGuessDto(
                guess.getRoundIndex(),
                guess.getAppId(),
                guess.getGuessedYear(),
                guess.isCompleted() ? guess.getActualYear() : null,
                guess.getHintsUsed(),
                bestDistance,
                unlockable,
                guess.isCompleted(),
                guess.getPoints()
        );
    }

    private RoundContext resolveRound(final Long appId) {
        final List<YearGamePick> picks = service.generateDailyPicks();
        if (picks.isEmpty()) {
            return null;
        }
        final List<Long> pickOrder = picks.stream().map(YearGamePick::getAppId).toList();
        final int foundIdx = pickOrder.indexOf(appId);
        if (foundIdx < 0) {
            return null;
        }
        return new RoundContext(picks.getFirst().getPickDate(), foundIdx + 1);
    }

    private void ensureUserExists(final String steamId) {
        if (!userRepository.existsById(steamId)) {
            try {
                final User user = new User();
                user.setSteamId(steamId);
                user.setCreatedAt(OffsetDateTime.now());
                userRepository.save(user);
            } catch (DataIntegrityViolationException ignored) {
                // concurrent create
            }
        }
    }

    private void persistProgress(final YearGuess progress, final boolean insert) {
        try {
            guessRepository.save(progress);
        } catch (DataIntegrityViolationException ex) {
            if (!insert) {
                throw ex;
            }
            final Optional<YearGuess> existing = guessRepository.findBySteamIdAndGameDateAndRoundIndex(
                    progress.getSteamId(), progress.getGameDate(), progress.getRoundIndex());
            if (existing.isPresent()) {
                final YearGuess row = existing.get();
                row.setGuessedYear(progress.getGuessedYear());
                row.setBestDistance(progress.getBestDistance());
                row.setHintsUsed(progress.getHintsUsed());
                row.setCompleted(progress.isCompleted());
                row.setPoints(progress.getPoints());
                row.setUpdatedAt(OffsetDateTime.now());
                guessRepository.save(row);
            }
        }
    }

    private void incrementGuessCounter(final boolean correct) {
        Counter.builder("steam5.year.guesses")
                .description("Authenticated year-game guesses persisted, by exact-match outcome")
                .tag("outcome", correct ? "correct" : "incorrect")
                .register(meterRegistry)
                .increment();
    }

    private static String weakEtagForPicks(final List<SteamAppDetail> details) {
        try {
            final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (SteamAppDetail detail : details) {
                md.update(java.nio.charset.StandardCharsets.UTF_8.encode(String.valueOf(detail.getAppId())));
                if (detail.getName() != null) {
                    md.update(detail.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int screenshotCount = 0;
                if (detail.getScreenshots() != null) {
                    for (var screenshot : detail.getScreenshots()) {
                        if (screenshotCount++ >= 2) {
                            break;
                        }
                        if (screenshot.getPathFull() != null) {
                            md.update(screenshot.getPathFull().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            return digestToWeakEtag(md.digest());
        } catch (Exception ignored) {
            log.debug("ETag generation failed for year-game picks", ignored);
            return null;
        }
    }

    private static String weakEtagForStringLists(final List<? extends List<String>> lists) {
        try {
            final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (List<String> list : lists) {
                if (list == null) {
                    continue;
                }
                for (String value : list) {
                    if (value != null) {
                        md.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    md.update((byte) '\n');
                }
                md.update((byte) '\u0000');
            }
            return digestToWeakEtag(md.digest());
        } catch (Exception ignored) {
            log.debug("ETag generation failed for year-game string lists", ignored);
            return null;
        }
    }

    private static String digestToWeakEtag(final byte[] digest) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return "W/\"" + sb + "\"";
    }
}
