package org.steam5.domain.details;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class SteamAppDetailService {

    private final SteamAppDetailRepository repository;

    public SteamAppDetailService(final SteamAppDetailRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsertFromJson(final Long appId, final JsonNode data) {
        SteamAppDetail detail = repository.findById(appId).orElseGet(SteamAppDetail::new);
        detail.setAppId(appId);

        detail.setType(asTextOrNull(data, "type"));
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

        // Developers
        for (JsonNode devNode : safeArray(data.path("developers"))) {
            String name = devNode.asText(null);
            if (name != null && !name.isBlank()) {
                detail.getDevelopers().add(new Developer(null, name, detail));
            }
        }

        // Publishers
        for (JsonNode pubNode : safeArray(data.path("publishers"))) {
            String name = pubNode.asText(null);
            if (name != null && !name.isBlank()) {
                detail.getPublisher().add(new Publisher(null, name, detail));
            }
        }

        // Genres
        for (JsonNode genreNode : safeArray(data.path("genres"))) {
            String description = genreNode.path("description").asText(null);
            if (description != null && !description.isBlank()) {
                detail.getGenres().add(new Genre(null, description, detail));
            }
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
}
