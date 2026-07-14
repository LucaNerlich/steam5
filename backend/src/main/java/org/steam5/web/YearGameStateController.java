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
    @Cacheable(value = "year-game", key = "#req.appId + ':' + #req.bucketGuess")
    public ResponseEntity<GuessResponse> submitGuess(@RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }

        final int releaseYear = service.getReleaseYearForApp(req.appId);
        final String actual = service.inferBucket(releaseYear);
        final boolean ok = YearGuessEvaluator.isCorrectForLabel(req.bucketGuess, releaseYear);
        return ResponseEntity.ok(new GuessResponse(req.appId, releaseYear, actual, ok));
    }

    @GetMapping("/today/details")
    @Cacheable(value = "year-game", key = "'today-details:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_LIST)
    public ResponseEntity<List<SteamAppDetail>> getTodayDetails(@RequestHeader HttpHeaders headers) {
        final List<YearGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(YearGamePick::getAppId).toList();
        final List<SteamAppDetail> details = detailRepository.findAllByAppIdIn(appIds);
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
    public ResponseEntity<?> myToday(@CurrentUser String steamId) {
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

        final List<YearGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        final Set<Long> currentAppIds = new HashSet<>();
        for (YearGamePick pick : picks) {
            currentAppIds.add(pick.getAppId());
        }

        final var guesses = guessRepository.findAllForDay(steamId, date).stream()
                .filter(g -> currentAppIds.contains(g.getAppId()))
                .toList();

        final Map<Long, Integer> releaseYearByAppId = lookupReleaseYearByAppId(guesses);
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                releaseYearByAppId.getOrDefault(g.getAppId(), 0)
        )).toList();

        return ResponseEntity.ok()
                .header("Cache-Control", PRIVATE_LIVE)
                .body(dtos);
    }

    @GetMapping("/my/day/{date}")
    public ResponseEntity<?> myDay(@PathVariable("date") String date,
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

        final var guesses = guessRepository.findAllForDay(steamId, day);
        final Map<Long, Integer> releaseYearByAppId = lookupReleaseYearByAppId(guesses);
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                releaseYearByAppId.getOrDefault(g.getAppId(), 0)
        )).toList();

        final boolean isToday = day.equals(GameDate.todayUtc());
        final String cacheControl = isToday ? PRIVATE_LIVE : PRIVATE_HISTORICAL;

        return ResponseEntity.ok()
                .header("Cache-Control", cacheControl)
                .body(dtos);
    }

    @GetMapping("/my/history")
    public ResponseEntity<?> myHistory(@RequestParam(value = "from", required = false) String from,
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

        final var guesses = guessRepository.findBySteamIdBetween(steamId, start, end);
        final Map<Long, Integer> releaseYearByAppId = lookupReleaseYearByAppId(guesses);
        final var dtos = guesses.stream().map(g -> new MyGuessDto(
                g.getRoundIndex(),
                g.getAppId(),
                g.getSelectedBucket(),
                g.getActualBucket(),
                releaseYearByAppId.getOrDefault(g.getAppId(), 0)
        )).toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "private, s-maxage=600, max-age=300")
                .body(dtos);
    }

    @GetMapping("/today")
    @Cacheable(value = "year-game", key = "'today-picks:' + T(org.steam5.domain.GameDate).todayUtc()", unless = CACHE_ONLY_2XX_NONEMPTY_PICKS)
    public ResponseEntity<YearGameStateDto> getToday(@RequestHeader HttpHeaders headers) {
        final List<YearGamePick> picks = service.generateDailyPicks();
        final List<Long> appIds = picks.stream().map(YearGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailRepository.findAllByAppIdIn(appIds);
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail detail : fetched) {
            byId.put(detail.getAppId(), detail);
        }
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match");
        }

        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
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
                .body(new YearGameStateDto(date, service.getBucketLabels(), service.getBucketTitles(), details));
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
                final OffsetDateTime nextChallenge = OffsetDateTime.ofInstant(
                        nextFireTime.toInstant(),
                        java.time.ZoneOffset.UTC
                );
                result.put("nextChallengeTime", nextChallenge.toString());
            } else {
                final OffsetDateTime todayAt020 = now.toLocalDate().atStartOfDay().atOffset(now.getOffset()).plusMinutes(2);
                final OffsetDateTime nextChallenge = now.isBefore(todayAt020) ? todayAt020 : todayAt020.plusDays(1);
                result.put("nextChallengeTime", nextChallenge.toString());
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

    @PostMapping("/guess-auth")
    @Transactional
    public ResponseEntity<GuessResponse> submitGuessAuthenticated(@CurrentUser String steamId,
                                                                  @RequestBody GuessRequest req) {
        if (req == null || req.appId == null || req.bucketGuess == null) {
            return ResponseEntity.badRequest().build();
        }
        if (steamId == null) {
            return ResponseEntity.status(401).build();
        }

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

        final int releaseYear = service.getReleaseYearForApp(req.appId);
        final String computedActual = service.inferBucket(releaseYear);

        final List<YearGamePick> picks = service.generateDailyPicks();
        final LocalDate date = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final List<Long> pickOrder = picks.stream().map(YearGamePick::getAppId).toList();
        final int foundIdx = pickOrder.indexOf(req.appId);
        if (foundIdx < 0) {
            return ResponseEntity.badRequest().body(new GuessResponse(req.appId, releaseYear, computedActual, false));
        }
        final int roundIndex = foundIdx + 1;
        final int points = YearGuessEvaluator.scorePoints(service.getBucketLabels(), req.bucketGuess, computedActual);

        final var existingOpt = guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex);
        if (existingOpt.isPresent()) {
            final var guess = existingOpt.get();
            final boolean alreadyOk = guess.getActualBucket() != null
                    && guess.getActualBucket().equals(guess.getSelectedBucket());
            return ResponseEntity.ok(new GuessResponse(guess.getAppId(), releaseYear, guess.getActualBucket(), alreadyOk));
        }

        try {
            guessRepository.save(new YearGuess(null, steamId, date, roundIndex, req.appId, req.bucketGuess,
                    computedActual, points, OffsetDateTime.now()));
            Counter.builder("steam5.year.guesses")
                    .description("Authenticated year-game guesses persisted, by exact-match outcome")
                    .tag("outcome", req.bucketGuess.equals(computedActual) ? "correct" : "incorrect")
                    .register(meterRegistry)
                    .increment();
        } catch (DataIntegrityViolationException ignored) {
            final var existing = guessRepository.findBySteamIdAndGameDateAndRoundIndex(steamId, date, roundIndex);
            if (existing.isPresent()) {
                final var guess = existing.get();
                final boolean alreadyOk = guess.getActualBucket() != null
                        && guess.getActualBucket().equals(guess.getSelectedBucket());
                return ResponseEntity.ok(new GuessResponse(guess.getAppId(), releaseYear, guess.getActualBucket(), alreadyOk));
            }
        }

        final boolean ok = YearGuessEvaluator.isCorrectForLabel(req.bucketGuess, releaseYear);
        return ResponseEntity.ok(new GuessResponse(req.appId, releaseYear, computedActual, ok));
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
        final List<Long> appIds = picks.stream().map(YearGamePick::getAppId).toList();
        final List<SteamAppDetail> fetched = detailRepository.findAllByAppIdIn(appIds);
        final Map<Long, SteamAppDetail> byId = new HashMap<>();
        for (SteamAppDetail detail : fetched) {
            byId.put(detail.getAppId(), detail);
        }
        final List<SteamAppDetail> details = appIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        if (appIds.size() != details.size()) {
            throw new ReviewGameException(500, "Number of appIds and details don't match for day " + date);
        }
        final boolean isToday = day.equals(GameDate.todayUtc());
        final String etag = weakEtagForPicks(details);
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            final String cacheControl = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", cacheControl)
                    .build();
        }
        final String cacheControl = isToday ? CACHE_LIVE : CACHE_HISTORICAL;
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", cacheControl)
                .body(new YearGameStateDto(day, service.getBucketLabels(), service.getBucketTitles(), details));
    }

    @GetMapping("/buckets")
    @Cacheable(value = "one-day", key = "'year-buckets'")
    public ResponseEntity<BucketMeta> buckets(@RequestHeader HttpHeaders headers) {
        final List<String> labels = service.getBucketLabels();
        final List<String> titles = service.getBucketTitles();
        final String etag = weakEtagForStringLists(List.of(labels, titles));
        if (etag != null && headers.getIfNoneMatch().contains(etag)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("Cache-Control", CACHE_CONFIG)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("Cache-Control", CACHE_CONFIG)
                .body(new BucketMeta(labels, titles));
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

    @GetMapping("/history/always-pick")
    @Cacheable(value = "year-game", key = "'always-pick-hist:' + (#from != null ? #from : 'min') + ':' + (#to != null ? #to : 'max')", unless = CACHE_ONLY_2XX)
    public ResponseEntity<HistoricalAlwaysPickResponse> alwaysPickHistory(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        final LocalDate start = from == null || from.isBlank()
                ? LocalDate.of(1970, 1, 1)
                : LocalDate.parse(from);
        final LocalDate end = to == null || to.isBlank() ? GameDate.todayUtc() : LocalDate.parse(to);
        if (end.isBefore(start)) {
            return ResponseEntity.badRequest().build();
        }
        final var result = computeAlwaysPickForRange(start, end);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600")
                .body(new HistoricalAlwaysPickResponse(start.toString(), end.toString(), result.scores()));
    }

    public record YearGameStateDto(LocalDate date, List<String> buckets, List<String> bucketTitles,
                                   List<SteamAppDetail> picks) {
    }

    public record BucketMeta(List<String> buckets, List<String> bucketTitles) {
    }

    public record GuessRequest(Long appId, String bucketGuess) {
    }

    public record GuessResponse(Long appId, int releaseYear, String actualBucket, boolean correct) {
    }

    public record MyGuessDto(int roundIndex, Long appId, String selectedBucket, String actualBucket, int releaseYear) {
    }

    public record AlwaysPickScore(int bucketIndex, String bucketLabel, double avgPoints, int rounds) {
    }

    public record ArchiveMonthDayPick(Long appId, String name) {
    }

    public record ArchiveMonthDay(String date, List<ArchiveMonthDayPick> picks) {
    }

    public record HistoricalAlwaysPickResponse(String from, String to, List<AlwaysPickScore> scores) {
    }

    private record AlwaysPickComputation(List<AlwaysPickScore> scores) {
    }

    private AlwaysPickComputation computeAlwaysPickForRange(LocalDate start, LocalDate end) {
        final List<String> labels = service.getBucketLabels();
        final ArrayList<Long> appIds = new ArrayList<>();
        final List<YearGamePick> picksInRange = pickRepository.findByPickDateBetween(start, end);
        final Map<LocalDate, List<YearGamePick>> picksByDate = new HashMap<>();
        for (YearGamePick pick : picksInRange) {
            picksByDate.computeIfAbsent(pick.getPickDate(), ignored -> new ArrayList<>()).add(pick);
        }
        picksByDate.values().forEach(picks -> picks.sort(
                Comparator.comparing(YearGamePick::getCreatedAt).thenComparing(YearGamePick::getId)
        ));

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            final List<YearGamePick> picks = picksByDate.getOrDefault(cursor, List.of());
            for (YearGamePick pick : picks) {
                appIds.add(pick.getAppId());
            }
            cursor = cursor.plusDays(1);
        }

        final List<String> actualBuckets = appIds.stream()
                .map(id -> service.inferBucket(service.getReleaseYearForApp(id)))
                .toList();
        final int rounds = actualBuckets.size();
        final ArrayList<AlwaysPickScore> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            final String selected = labels.get(i);
            int sum = 0;
            for (String actual : actualBuckets) {
                sum += YearGuessEvaluator.scorePoints(labels, selected, actual);
            }
            final double avg = rounds == 0 ? 0.0 : ((double) sum) / rounds;
            out.add(new AlwaysPickScore(i + 1, selected, avg, rounds));
        }
        return new AlwaysPickComputation(out);
    }

    private Map<Long, Integer> lookupReleaseYearByAppId(final List<YearGuess> guesses) {
        final Set<Long> appIds = new HashSet<>();
        for (YearGuess guess : guesses) {
            if (guess.getAppId() != null) {
                appIds.add(guess.getAppId());
            }
        }
        if (appIds.isEmpty()) {
            return Map.of();
        }

        final Map<Long, Integer> releaseYearByAppId = new HashMap<>();
        for (SteamAppDetail detail : detailRepository.findAllByAppIdIn(appIds)) {
            releaseYearByAppId.put(detail.getAppId(), service.getReleaseYearForApp(detail.getAppId()));
        }
        return releaseYearByAppId;
    }

    private static String weakEtagForPicks(final List<SteamAppDetail> details) {
        try {
            final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (SteamAppDetail detail : details) {
                md.update(java.nio.charset.StandardCharsets.UTF_8.encode(String.valueOf(detail.getAppId())));
                if (detail.getName() != null) {
                    md.update(detail.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                if (detail.getReleaseDate() != null) {
                    md.update(detail.getReleaseDate().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
