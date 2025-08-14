package org.steam5.domain.details;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.repository.details.DeveloperRepository;
import org.steam5.repository.details.GenreRepository;
import org.steam5.repository.details.PublisherRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class SteamAppDetailService {

    private final SteamAppDetailRepository repository;
    private final DeveloperRepository developerRepository;
    private final PublisherRepository publisherRepository;
    private final GenreRepository genreRepository;

    public SteamAppDetailService(final SteamAppDetailRepository repository,
                                 final DeveloperRepository developerRepository,
                                 final PublisherRepository publisherRepository,
                                 final GenreRepository genreRepository) {
        this.repository = repository;
        this.developerRepository = developerRepository;
        this.publisherRepository = publisherRepository;
        this.genreRepository = genreRepository;
    }

    private static Iterable<JsonNode> safeArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> list = new ArrayList<>();
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) list.add(it.next());
        return list;
    }

    private static String asTextOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    private static String joinDlcIds(JsonNode dlcArray) {
        if (dlcArray == null || !dlcArray.isArray() || dlcArray.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : dlcArray) {
            if (!n.canConvertToLong()) continue;
            if (!sb.isEmpty()) sb.append(',');
            sb.append(n.asLong());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @Transactional
    public void upsertFromJson(final Long appId, final JsonNode data) {
        SteamAppDetail detail = repository.findById(appId).orElseGet(SteamAppDetail::new);
        detail.setAppId(appId);

        detail.setType(asTextOrNull(data, "type"));
        detail.setName(asTextOrNull(data, "name"));
        detail.setFree(data.path("is_free").asBoolean(false));
        detail.setDlc(joinDlcIds(data.path("dlc")));
        detail.setShortDescription(asTextOrNull(data, "short_description"));
        detail.setDetailedDescription(asTextOrNull(data, "detailed_description"));
        detail.setAboutTheGame(asTextOrNull(data, "about_the_game"));
        detail.setHeaderImage(asTextOrNull(data, "header_image"));
        detail.setCapsuleImage(asTextOrNull(data, "capsule_image"));
        detail.setWebsite(asTextOrNull(data, "website"));
        detail.setLegalNotice(asTextOrNull(data, "legal_notice"));

        JsonNode platforms = data.path("platforms");
        detail.setWindows(platforms.path("windows").asBoolean(false));
        detail.setMac(platforms.path("mac").asBoolean(false));
        detail.setLinux(platforms.path("linux").asBoolean(false));

        detail.setRecommendations(data.path("recommendations").path("total").asLong(0));
        detail.setReleaseDate(data.path("release_date").path("date").asText(null));
        detail.setBackgroundRaw(asTextOrNull(data, "background_raw"));

        // Clear children for a full refresh
        detail.getDevelopers().clear();
        detail.getPublisher().clear();
        detail.getGenres().clear();
        detail.getScreenshots().clear();
        detail.getMovies().clear();

        // Developers (lookup-or-create by name)
        java.util.Set<String> seenDevelopers = new java.util.HashSet<>();
        for (JsonNode devNode : safeArray(data.path("developers"))) {
            final String rawName = devNode.asText(null);
            final String trimmedName = rawName == null ? null : rawName.trim();
            if (trimmedName == null || trimmedName.isBlank()) continue;
            final String normalizedKey = trimmedName.toLowerCase();
            if (!seenDevelopers.add(normalizedKey)) continue;
            final String finalName = trimmedName;
            Developer dev = developerRepository
                    .findByNameIgnoreCase(finalName)
                    .orElseGet(() -> developerRepository.save(new Developer(null, finalName)));
            detail.getDevelopers().add(dev);
        }

        // Publishers (lookup-or-create by name)
        java.util.Set<String> seenPublishers = new java.util.HashSet<>();
        for (JsonNode pubNode : safeArray(data.path("publishers"))) {
            final String rawName = pubNode.asText(null);
            final String trimmedName = rawName == null ? null : rawName.trim();
            if (trimmedName == null || trimmedName.isBlank()) continue;
            final String normalizedKey = trimmedName.toLowerCase();
            if (!seenPublishers.add(normalizedKey)) continue;
            final String finalName = trimmedName;
            Publisher pub = publisherRepository
                    .findByNameIgnoreCase(finalName)
                    .orElseGet(() -> publisherRepository.save(new Publisher(null, finalName)));
            detail.getPublisher().add(pub);
        }

        // Genres (lookup-or-create by description)
        java.util.Set<String> seenGenres = new java.util.HashSet<>();
        for (JsonNode genreNode : safeArray(data.path("genres"))) {
            final String rawDesc = genreNode.path("description").asText(null);
            final String trimmedDesc = rawDesc == null ? null : rawDesc.trim();
            if (trimmedDesc == null || trimmedDesc.isBlank()) continue;
            final String normalizedKey = trimmedDesc.toLowerCase();
            if (!seenGenres.add(normalizedKey)) continue;
            final String finalDesc = trimmedDesc;
            Genre g = genreRepository
                    .findByDescriptionIgnoreCase(finalDesc)
                    .orElseGet(() -> genreRepository.save(new Genre(null, finalDesc)));
            detail.getGenres().add(g);
        }

        // Screenshots
        for (JsonNode ssNode : safeArray(data.path("screenshots"))) {
            String thumb = ssNode.path("path_thumbnail").asText(null);
            String full = ssNode.path("path_full").asText(null);
            if (thumb != null || full != null) {
                detail.getScreenshots().add(new Screenshot(null, detail, thumb, full));
            }
        }

        // Movies
        for (JsonNode mvNode : safeArray(data.path("movies"))) {
            String name = mvNode.path("name").asText(null);
            String thumbnail = mvNode.path("thumbnail").asText(null);
            String webm = mvNode.path("webm").path("max").asText(null);
            if (webm == null || webm.isBlank()) {
                webm = mvNode.path("webm").path("480").asText(null);
            }
            String mp4 = mvNode.path("mp4").path("max").asText(null);
            if (mp4 == null || mp4.isBlank()) {
                mp4 = mvNode.path("mp4").path("480").asText(null);
            }
            if (name != null || thumbnail != null || webm != null || mp4 != null) {
                detail.getMovies().add(new Movie(null, name, thumbnail, webm, mp4, detail));
            }
        }

        repository.save(detail);
    }
}
