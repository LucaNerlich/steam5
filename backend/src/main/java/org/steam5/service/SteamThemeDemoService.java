package org.steam5.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.http.JsonHttpClient;
import org.steam5.repository.details.SteamAppDetailRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SteamThemeDemoService {

    private static final Set<String> STEAMSPY_GENRES = Set.of(
            "all",
            "strategy",
            "rpg",
            "action",
            "adventure",
            "indie",
            "casual",
            "simulation",
            "sports",
            "racing"
    );

    private final SteamAppDetailRepository detailRepository;
    private final JsonHttpClient jsonHttpClient;
    private final ObjectMapper objectMapper;

    public List<SteamAppDetail> sample(String genre, String category, int limit) {
        final int normalizedLimit = Math.max(1, Math.min(limit, 25));
        List<Long> ids = detailRepository.pickRandomAppIds(
                normalize(genre),
                normalize(category),
                normalizedLimit
        );
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }

        List<SteamAppDetail> details = detailRepository.findAllByAppIdIn(ids);
        Map<Long, SteamAppDetail> byId = new LinkedHashMap<>();
        for (SteamAppDetail detail : details) {
            byId.put(detail.getAppId(), detail);
        }
        List<SteamAppDetail> ordered = new ArrayList<>();
        for (Long id : ids) {
            SteamAppDetail detail = byId.get(id);
            if (detail != null) {
                ordered.add(detail);
            }
        }
        return ordered;
    }

    public List<SteamSpyGame> sampleFromSteam(String genre, int limit) throws IOException {
        final int normalizedLimit = Math.max(1, Math.min(limit, 25));
        final String genreParam = normalizeSteamSpyGenre(genre);

        final String url = UriComponentsBuilder.fromUriString("https://steamspy.com/api.php")
                .queryParam("request", "top100in2weeks")
                .queryParam("genre", genreParam)
                .build(true)
                .toUriString();

        JsonNode response = jsonHttpClient.getJson(url);
        if (response == null || response.isMissingNode() || response.isNull()) {
            return List.of();
        }

        List<SteamSpyGame> games = new ArrayList<>();
        Map<String, JsonNode> responseMap = objectMapper.convertValue(
                response,
                new TypeReference<Map<String, JsonNode>>() {
                });
        for (Entry<String, JsonNode> entry : responseMap.entrySet()) {
            if (games.size() >= normalizedLimit) break;
            JsonNode node = entry.getValue();
            if (node == null || node.isMissingNode()) continue;
            SteamSpyGame game = SteamSpyGame.builder()
                    .appId(node.path("appid").asLong(0))
                    .name(textOrNull(node.path("name")))
                    .genre(textOrNull(node.path("genre")))
                    .scoreRank(node.path("score_rank").asInt(0))
                    .owners(textOrNull(node.path("owners")))
                    .tags(extractTags(node.path("tags")))
                    .build();
            games.add(game);
        }
        return games;
    }

    public Set<String> steamSpyGenres() {
        return new LinkedHashSet<>(STEAMSPY_GENRES);
    }

    private List<String> extractTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isObject()) {
            return List.of();
        }
        Map<String, JsonNode> tagsMap = objectMapper.convertValue(
                tagsNode,
                new TypeReference<Map<String, JsonNode>>() {
                });
        return new ArrayList<>(tagsMap.keySet());
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeSteamSpyGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return "all";
        }
        String candidate = genre.trim().toLowerCase();
        return STEAMSPY_GENRES.contains(candidate) ? candidate : "all";
    }

    @SuppressWarnings("deprecation")
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    @lombok.Builder
    public record SteamSpyGame(Long appId,
                               String name,
                               String genre,
                               String owners,
                               int scoreRank,
                               List<String> tags) {
    }
}


