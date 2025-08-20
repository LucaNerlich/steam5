package org.steam5.domain.details;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.repository.details.*;

import java.util.*;

@Service
public class SteamAppDetailService {

    private final SteamAppDetailRepository repository;
    private final DeveloperRepository developerRepository;
    private final PublisherRepository publisherRepository;
    private final GenreRepository genreRepository;
    private final CategoryRepository categoryRepository;
    private final CacheManager cacheManager;

    public SteamAppDetailService(final SteamAppDetailRepository repository,
                                 final DeveloperRepository developerRepository,
                                 final PublisherRepository publisherRepository,
                                 final GenreRepository genreRepository, final CategoryRepository categoryRepository,
                                 final CacheManager cacheManager) {
        this.repository = repository;
        this.developerRepository = developerRepository;
        this.publisherRepository = publisherRepository;
        this.genreRepository = genreRepository;
        this.categoryRepository = categoryRepository;
        this.cacheManager = cacheManager;
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
        detail.setControllerSupport(asTextOrNull(data, "controller_support"));
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

        // Price overview (one-to-one, shared primary key)
        JsonNode priceNode = data.path("price_overview");
        if (priceNode != null && priceNode.isObject()) {
            Price price = detail.getPriceOverview();
            if (price == null) {
                price = new Price();
            }
            price.setApp(detail);
            price.setCurrency(asTextOrNull(priceNode, "currency"));
            price.setInitial(priceNode.path("initial").asLong(0));
            price.setFinalAmount(priceNode.path("final").asLong(0));
            price.setDiscountPercent(priceNode.path("discount_percent").asInt(0));
            price.setInitialFormatted(asTextOrNull(priceNode, "initial_formatted"));
            price.setFinalFormatted(asTextOrNull(priceNode, "final_formatted"));
            detail.setPriceOverview(price);
        } else {
            detail.setPriceOverview(null);
        }

        // Clear children for a full refresh
        detail.getDevelopers().clear();
        detail.getPublisher().clear();
        detail.getGenres().clear();
        detail.getScreenshots().clear();
        detail.getMovies().clear();

        // Developers (lookup-or-create by name)
        Set<String> seenDevelopers = new HashSet<>();
        for (JsonNode devNode : safeArray(data.path("developers"))) {
            final String rawName = devNode.asText(null);
            final String trimmedName = rawName == null ? null : rawName.trim();
            if (trimmedName == null || trimmedName.isBlank()) continue;
            final String normalizedKey = trimmedName.toLowerCase();
            if (!seenDevelopers.add(normalizedKey)) continue;
            final Developer dev = developerRepository
                    .findByNameIgnoreCase(trimmedName)
                    .orElseGet(() -> developerRepository.save(new Developer(null, trimmedName)));
            detail.getDevelopers().add(dev);
        }

        // Publishers (lookup-or-create by name)
        Set<String> seenPublishers = new HashSet<>();
        for (JsonNode pubNode : safeArray(data.path("publishers"))) {
            final String rawName = pubNode.asText(null);
            final String trimmedName = rawName == null ? null : rawName.trim();
            if (trimmedName == null || trimmedName.isBlank()) continue;
            final String normalizedKey = trimmedName.toLowerCase();
            if (!seenPublishers.add(normalizedKey)) continue;
            final Publisher pub = publisherRepository
                    .findByNameIgnoreCase(trimmedName)
                    .orElseGet(() -> publisherRepository.save(new Publisher(null, trimmedName)));
            detail.getPublisher().add(pub);
        }

        // Categories (lookup-or-create by description)
        Set<String> seenCategories = new HashSet<>();
        for (JsonNode genreNode : safeArray(data.path("categories"))) {
            final String rawDesc = genreNode.path("description").asText(null);
            final String trimmedDesc = rawDesc == null ? null : rawDesc.trim();
            if (trimmedDesc == null || trimmedDesc.isBlank()) continue;
            final String normalizedKey = trimmedDesc.toLowerCase();
            if (!seenCategories.add(normalizedKey)) continue;
            final Category g = categoryRepository
                    .findByDescriptionIgnoreCase(trimmedDesc)
                    .orElseGet(() -> categoryRepository.save(new Category(null, trimmedDesc)));
            detail.getCategories().add(g);
        }

        // Genres (lookup-or-create by description)
        Set<String> seenGenres = new HashSet<>();
        for (JsonNode genreNode : safeArray(data.path("genres"))) {
            final String rawDesc = genreNode.path("description").asText(null);
            final String trimmedDesc = rawDesc == null ? null : rawDesc.trim();
            if (trimmedDesc == null || trimmedDesc.isBlank()) continue;
            final String normalizedKey = trimmedDesc.toLowerCase();
            if (!seenGenres.add(normalizedKey)) continue;
            final Genre g = genreRepository
                    .findByDescriptionIgnoreCase(trimmedDesc)
                    .orElseGet(() -> genreRepository.save(new Genre(null, trimmedDesc)));
            detail.getGenres().add(g);
        }

        // Screenshots
        for (JsonNode ssNode : safeArray(data.path("screenshots"))) {
            final String thumb = ssNode.path("path_thumbnail").asText(null);
            final String full = ssNode.path("path_full").asText(null);
            if (thumb != null || full != null) {
                detail.getScreenshots().add(new Screenshot(null, detail, thumb, full, null, null, null, null));
            }
        }

        // Movies
        for (JsonNode mvNode : safeArray(data.path("movies"))) {
            final String name = mvNode.path("name").asText(null);
            final String thumbnail = mvNode.path("thumbnail").asText(null);
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

        // Evict caches impacted by details updates
        final Cache oneDay = cacheManager.getCache("one-day");
        if (oneDay != null) {
            oneDay.evict(appId);
        }
        final Cache reviewGame = cacheManager.getCache("review-game");
        if (reviewGame != null) {
            reviewGame.clear();
        }
    }
}
